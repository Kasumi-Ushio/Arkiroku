package org.kasumi321.ushio.phitracker.data.song

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataUpdater
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.CoilIllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.clearImageCacheUrls
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

/**
 * Shared song-data update flow used by the settings entry, the songs tab
 * pull-to-refresh and the onboarding wizard: checks and downloads the song
 * data files AND the collectible CSV set (one update covers both datasets),
 * then reconciles the illustration cache for added/removed songs, reporting
 * per-phase progress. Each dataset keeps its own updater discipline:
 * staging → backup → atomic commit → cache invalidation only after its own
 * full success, so one dataset's failure never commits the other.
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
        val addedSongNames: List<String>,
        val removedCount: Int
    )

    /**
     * Combined upstream-change probe: true when EITHER the song data
     * (info.csv compare) or the collectible data (collection_version.txt
     * compare) changed upstream. A failure in either probe fails the whole
     * check, so a broken network is never misread as "up to date".
     */
    suspend fun checkUpstreamChanged(): Result<Boolean> = runCatching {
        val songsChanged = songDataUpdater.checkUpstreamChanged().getOrThrow()
        val collectiblesChanged = collectibleDataUpdater.checkUpstreamChanged().getOrThrow()
        songsChanged || collectiblesChanged
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
        val oldSongIds = songDataProvider.getSongs().keys.toSet()
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
            return runCatching {
                val newSongIds = songDataProvider.getSongs().keys.toSet()
                val songNames = songDataProvider.getSongNameMap()
                UpdateOutcome(
                    addedSongNames = (newSongIds - oldSongIds).sorted().map { songNames[it] ?: it },
                    removedCount = (oldSongIds - newSongIds).size
                )
            }
        }
        return runCatching {
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
                        val result = runCatching {
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
        if (failures > 0) throw IllegalStateException("图鉴信息已更新，但部分曲绘未能下载，可稍后在设置中重试")
        return UpdateOutcome(
            addedSongNames = added.map { songNames[it] ?: it },
            removedCount = removed.size
        )
    }
}
