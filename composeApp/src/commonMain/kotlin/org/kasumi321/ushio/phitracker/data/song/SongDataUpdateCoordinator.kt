package org.kasumi321.ushio.phitracker.data.song

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.kasumi321.ushio.phitracker.data.common.resultOf
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataUpdater
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.CoilIllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.clearImageCacheUrls
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

/** Another entry point's update is already running; the caller keeps its own UI state. */
class UpdateAlreadyInProgressException : Exception("a song data update is already in progress")

/**
 * Both datasets committed, but some newly added illustrations could not be
 * downloaded. The data itself is authoritative (never rolled back); callers
 * report a partial success so the user can retry the artwork later.
 */
class IllustrationSyncPartialException(
    val failures: Int,
    val addedSongNames: List<String>
) : Exception("图鉴信息已更新，但部分曲绘未能下载，可稍后在设置中重试")

/**
 * Shared song-data update flow used by the settings entry, the songs tab
 * pull-to-refresh and the onboarding wizard: checks and downloads the song
 * data files AND the collectible CSV set (one update covers both datasets),
 * then reconciles the illustration cache for added/removed songs, reporting
 * per-phase progress. Each dataset keeps its own updater discipline:
 * staging → backup → atomic commit → cache invalidation only after its own
 * full success. A later dataset or illustration failure is reported without
 * rolling back an already committed dataset.
 */
class SongDataUpdateCoordinator(
    private val songDataUpdater: SongDataUpdater,
    private val songDataProvider: SongDataProvider,
    private val illustrationProvider: IllustrationProvider,
    private val artworkFileCache: StandardArtworkCache,
    private val collectibleDataUpdater: CollectibleDataUpdater,
    private val thumbnailPreloader: IllustrationThumbnailPreloader = CoilIllustrationThumbnailPreloader,
    private val clearCacheUrls: suspend (List<String>) -> Unit = ::clearImageCacheUrls
) {
    data class IllustrationSyncProgress(
        val completed: Int,
        val total: Int,
        val currentSongName: String
    )

    data class UpdateOutcome(
        val addedSongNames: List<String>
    )

    /**
     * Serializes updates across ALL entry points (home pull-to-refresh,
     * Settings, onboarding). The ViewModels' own in-flight flags are UI state
     * only; this mutex is the correctness boundary — a second entry arriving
     * while an update runs fails fast instead of queueing a duplicate run or
     * racing the shared staging/backup directories.
     */
    private val updateMutex = Mutex()

    /**
     * Combined upstream-change probe: true when EITHER the song data
     * (info.csv compare) or the collectible data (collection_version.txt
     * compare) changed upstream. A pending recovery scene always reports a
     * change so the update flow gets a chance to resolve it. A failure in
     * either probe fails the whole check, so a broken network is never
     * misread as "up to date".
     */
    suspend fun checkUpstreamChanged(): Result<Boolean> = updateMutex.withLock {
        resultOf {
            if (hasPendingRecovery() || hasPendingRepair()) return@resultOf true
            val songsChanged = songDataUpdater.checkUpstreamChanged().getOrThrow()
            val collectiblesChanged = collectibleDataUpdater.checkUpstreamChanged().getOrThrow()
            songsChanged || collectiblesChanged
        }
    }

    /** True when either dataset retains a transaction scene to recover. */
    fun hasPendingRecovery(): Boolean =
        songDataUpdater.hasPendingRecovery() || collectibleDataUpdater.hasPendingRecovery()

    /** True when a reset still owes a full download (survives process death). */
    fun hasPendingRepair(): Boolean =
        songDataUpdater.hasPendingRepair() || collectibleDataUpdater.hasPendingRepair()

    /**
     * True while either dataset serves the bundled catalog because its
     * downloaded copy was quarantined by an explicit offline fallback.
     */
    fun isUsingBundledCatalogFallback(): Boolean =
        songDataUpdater.isUsingBundledFallback() || collectibleDataUpdater.isUsingBundledFallback()

    /**
     * Re-persists the repair obligation for both datasets. A partially failed
     * run may already have committed (and discharged the marker of) one
     * dataset; keeping both markers is what makes "not recovered until both
     * complete" survive process death. Failures are reported to the caller but
     * never erase an existing obligation.
     */
    suspend fun markRepairRequired(): Result<Unit> = updateMutex.withLock {
        var firstFailure: Throwable? = null
        songDataUpdater.markRepairRequired().onFailure {
            if (firstFailure == null) firstFailure = it
        }
        collectibleDataUpdater.markRepairRequired().onFailure {
            if (firstFailure == null) firstFailure = it
        }
        firstFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    /**
     * User-approved offline degradation: both datasets move their downloaded
     * files aside and read the bundled assets until a later full update
     * succeeds. Both datasets are attempted even when one fails; the first
     * failure is reported so the caller keeps the blocking UI instead of
     * showing a half-bundled/half-downloaded catalog.
     */
    suspend fun useBundledCatalogFallback(): Result<Unit> = updateMutex.withLock {
        var firstFailure: Throwable? = null
        songDataUpdater.useBundledFallback().onFailure {
            if (firstFailure == null) firstFailure = it
        }
        collectibleDataUpdater.useBundledFallback().onFailure {
            if (firstFailure == null) firstFailure = it
        }
        firstFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    /**
     * Resolves retained scenes for both datasets before any caller reads
     * [songDataProvider]. The mutex excludes a concurrent update; both
     * datasets are attempted even when one fails, and the first failure is
     * reported. A failed dataset keeps its scene untouched.
     */
    suspend fun recoverPendingUpdate(): Result<Unit> = updateMutex.withLock {
        recoverPendingUpdateLocked()
    }

    private suspend fun recoverPendingUpdateLocked(): Result<Unit> {
        var firstFailure: Throwable? = null
        songDataUpdater.recoverPendingUpdate().onFailure {
            if (firstFailure == null) firstFailure = it
        }
        collectibleDataUpdater.recoverPendingUpdate().onFailure {
            if (firstFailure == null) firstFailure = it
        }
        return firstFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    /**
     * Explicit recovery escape hatch: discards both retained scenes and
     * staging residue without restoring them, then immediately runs the full
     * download (illustration reconciliation included by default, so a reset
     * leaves the catalog fully usable). A persisted repair marker keeps the
     * obligation alive if this process dies before a later update succeeds;
     * the marker is cleared only by a fully successful dataset update.
     * Returns the update outcome, so a reset can never be reported as complete
     * without fresh data.
     */
    suspend fun resetPendingUpdate(
        syncIllustrations: Boolean = false,
        onFileProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
        onIllustrationProgress: (IllustrationSyncProgress) -> Unit = {}
    ): Result<UpdateOutcome> = updateMutex.withLock {
        val resetFailure = resetPendingUpdateLocked()
        if (resetFailure != null) {
            Result.failure(resetFailure)
        } else {
            // The pre-reset files may be mixed or unreadable; never let an
            // unreadable baseline abort the repair, and treat every song as
            // newly added so illustrations are reconciled for the whole set.
            updateLocked(
                syncIllustrations = syncIllustrations,
                onFileProgress = onFileProgress,
                onIllustrationProgress = onIllustrationProgress,
                tolerateUnreadableBaseline = true
            )
        }
    }

    private suspend fun resetPendingUpdateLocked(): Throwable? {
        var firstFailure: Throwable? = null
        songDataUpdater.resetPendingUpdate().onFailure {
            if (firstFailure == null) firstFailure = it
        }
        collectibleDataUpdater.resetPendingUpdate().onFailure {
            if (firstFailure == null) firstFailure = it
        }
        return firstFailure
    }

    /**
     * Run the full data update: the four song files, then the six
     * collectible CSVs (progress indices continue across both phases, so the
     * combined download reads as one 10-file bar), then reconcile the
     * illustration cache for added/removed songs.
     *
     * [syncIllustrations] is false only for callers that immediately download
     * every thumbnail through [IllustrationPreloadCoordinator] anyway; it then
     * skips the per-diff downloads (and the removal cleanup) — nothing already
     * on disk is touched. Callers that need the in-use behavior follow the
     * default, which matches the home pull-to-refresh and Settings paths.
     */
    suspend fun update(
        syncIllustrations: Boolean = true,
        onFileProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
        onIllustrationProgress: (IllustrationSyncProgress) -> Unit = {}
    ): Result<UpdateOutcome> {
        if (!updateMutex.tryLock()) {
            return Result.failure(UpdateAlreadyInProgressException())
        }
        try {
            return updateLocked(syncIllustrations, onFileProgress, onIllustrationProgress)
        } finally {
            updateMutex.unlock()
        }
    }

    /** Caller must hold [updateMutex]; reuses the unlocked recovery path. */
    private suspend fun updateLocked(
        syncIllustrations: Boolean,
        onFileProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
        onIllustrationProgress: (IllustrationSyncProgress) -> Unit = {},
        tolerateUnreadableBaseline: Boolean = false
    ): Result<UpdateOutcome> {
        // Resolve any retained scene before the provider is read: a mixed
        // or half-committed on-disk state must not leak into oldSongIds.
        recoverPendingUpdateLocked().getOrElse { return Result.failure(it) }
        val oldSongIds = resultOf { songDataProvider.getSongs().keys.toSet() }.getOrElse { error ->
            // A reset may have left an unreadable (mixed) baseline; the
            // obligation is still a full download, so proceed with an empty
            // baseline instead of aborting before any data is fetched.
            if (tolerateUnreadableBaseline) emptySet() else return Result.failure(error)
        }
        val songResult = songDataUpdater.updateAll(onFileProgress)
        if (songResult.isFailure) {
            return Result.failure(songResult.exceptionOrNull() ?: RuntimeException("unknown"))
        }
        val songFileCount = SongDataUpdater.FILE_NAMES.size
        val collectibleResult = collectibleDataUpdater.updateAll { index, count, fileName ->
            onFileProgress(songFileCount + index, songFileCount + count, fileName)
        }
        if (collectibleResult.isFailure) {
            // Song data is already committed (its own discipline completed);
            // report the failure so the UI says so, but never touch the
            // collectible dataset's partial state.
            return Result.failure(collectibleResult.exceptionOrNull() ?: RuntimeException("unknown"))
        }
        if (!syncIllustrations) {
            return resultOf {
                val newSongIds = songDataProvider.getSongs().keys.toSet()
                val songNames = songDataProvider.getSongNameMap()
                UpdateOutcome(
                    addedSongNames = (newSongIds - oldSongIds).sorted().map { songNames[it] ?: it }
                )
            }
        }
        return resultOf {
            reconcileIllustrationCache(
                oldSongIds,
                songDataProvider.getSongs().keys.toSet(),
                onIllustrationProgress
            )
        }
    }

    private suspend fun reconcileIllustrationCache(
        oldSongIds: Set<String>,
        newSongIds: Set<String>,
        onProgress: (IllustrationSyncProgress) -> Unit
    ): UpdateOutcome {
        val added = (newSongIds - oldSongIds).sorted()
        val removed = (oldSongIds - newSongIds).sorted()
        val songNames = songDataProvider.getSongNameMap()
        var failures = 0
        var completed = 0
        val mutex = Mutex()
        coroutineScope {
            val semaphore = Semaphore(6)
            added.map { songId ->
                launch {
                    semaphore.withPermit {
                        val name = songNames[songId] ?: songId
                        mutex.withLock {
                            onProgress(IllustrationSyncProgress(completed, added.size, name))
                        }
                        val result = resultOf {
                            val variants = illustrationProvider.variantDifficulties(songId)
                            if (variants.isEmpty()) {
                                val localUri = artworkFileCache.getOrDownloadThumbnail(songId, illustrationProvider.getLowUrl(songId))
                                thumbnailPreloader.preload(localUri).getOrThrow()
                            } else {
                                for (difficulty in variants) {
                                    val localUri = artworkFileCache.getOrDownloadThumbnail(
                                        illustrationProvider.cacheKey(songId, difficulty),
                                        illustrationProvider.getLowUrl(songId, difficulty)
                                    )
                                    thumbnailPreloader.preload(localUri).getOrThrow()
                                }
                            }
                        }
                        mutex.withLock {
                            completed++
                            if (result.isFailure) failures++
                        }
                    }
                }
            }.forEach { it.join() }
        }
        if (removed.isNotEmpty()) {
            clearCacheUrls(removed.flatMap { songId ->
                // Per-difficulty songs only ever populate variant slots, so
                // clear those (plus the legacy flat URLs, which pre-variant
                // installs may still have warm in Coil).
                val difficulties = illustrationProvider.variantDifficulties(songId)
                val difficultyList: List<Difficulty?> = difficulties.ifEmpty { listOf(null) }
                difficultyList.flatMap { difficulty ->
                    listOf(
                        illustrationProvider.getLowUrl(songId, difficulty),
                        illustrationProvider.getStandardUrl(songId, difficulty),
                        illustrationProvider.getBlurUrl(songId, difficulty)
                    )
                }
            })
            val removedCacheKeys = removed.flatMap { songId ->
                illustrationProvider.variantDifficulties(songId)
                    .map { illustrationProvider.cacheKey(songId, it) }
                    .ifEmpty { listOf(songId) }
            }
            artworkFileCache.clearThumbnails(removedCacheKeys)
            artworkFileCache.clearStandard(removedCacheKeys)
        }
        AppLogger.event("cache", "song_data_illustration_reconcile", mapOf("added" to added.size.toString(), "addedSuccess" to (added.size - failures).toString(), "addedFailure" to failures.toString(), "removed" to removed.size.toString()))
        val addedSongNames = added.map { songNames[it] ?: it }
        if (failures > 0) throw IllustrationSyncPartialException(failures, addedSongNames)
        return UpdateOutcome(addedSongNames = addedSongNames)
    }
}
