package org.kasumi321.ushio.phitracker.data.common

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.markExcludedFromPlatformBackup

inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

data class RollbackFailure(val fileName: String, val cause: Throwable)

class StagedRollbackException(
    val primary: Throwable,
    val failures: List<RollbackFailure>,
    val backupDir: Path
) : Exception(
    "commit failed and rollback was incomplete for ${failures.map { it.fileName }}; backup retained",
    primary
) {
    init {
        failures.forEach { addSuppressed(it.cause) }
    }
}

/**
 * Raised when a retained transaction scene cannot be recovered automatically:
 * the commit had started (a COMMITTING state exists) but the manifest or the
 * backup set is missing, truncated or otherwise unreadable. The scene is kept
 * untouched — no data is deleted or rewritten — until the user resets it via
 * [StagedFileSetUpdate.resetPendingUpdate]. `reason` is a short human-readable
 * cause category, not a raw parse dump.
 */
class StagedUpdateBlockedException(
    val reason: String,
    val backupDir: Path,
    cause: Throwable? = null
) : Exception(
    "上一次图鉴信息更新未完成，无法自动恢复（$reason）。请重置数据更新后重试。",
    cause
)

/**
 * A download or upstream probe failed before any local file was rewritten.
 * The message is deliberately generic: UI maps the type to a localized,
 * actionable text and never shows transport details.
 */
class CatalogDownloadException(cause: Throwable? = null) :
    Exception("catalog download failed", cause)

/**
 * A local file operation that must be resolved before the catalog can be
 * served (for example setting the downloaded dataset aside).
 */
class CatalogStorageException(cause: Throwable? = null) :
    Exception("catalog storage operation failed", cause)

/**
 * Staged download → backup → atomic commit engine shared by the song and
 * collectible data updaters.
 *
 * The retained scene in [backupDir] is self-describing:
 * - `prestate.txt` is written last, after every backup copy is on disk, via
 *   temp-file + atomicMove. Its strict format is
 *   `PHI-PRESTATE-V1\n<count>\n<size>\t<name>\n...`; empty, truncated, unknown
 *   or extra fields are invalid, never "no files existed".
 * - `transaction.state` is written atomically with `COMMITTING` right before
 *   the first commit move and with `COMMITTED` after the last one.
 *
 * [legacyBackupDir] is the old cacheDir location. Old versions wrote no state
 * and a plain one-filename-per-line `prestate.txt`; a non-empty scene there is
 * treated as COMMITTING and migrated in place, while an empty/malformed scene
 * (or one with missing backups) fails closed. A new-format scene without
 * `transaction.state` still means the commit never started and is inert.
 *
 * Recovery rules:
 * - new scene, no state → the commit never started; the (possibly incomplete)
 *   backup is inert and is discarded without touching persistent data;
 * - COMMITTING/legacy + valid manifest/backup → preflight, then restore every
 *   listed file through a same-directory temp + atomicMove (never
 *   delete-first); files not listed are removed only after every restore
 *   succeeded;
 * - COMMITTING/legacy + invalid manifest/backup or an unknown state → fail
 *   closed with [StagedUpdateBlockedException], keeping the scene for reset;
 * - COMMITTED → the new data is authoritative; the stale backup is discarded,
 *   never rolled back.
 *
 * [resetPendingUpdate] drops the scene but persists a repair marker under
 * [persistentDir]; [updateAll] clears it only after a fully successful commit,
 * and [checkUpstreamChanged] treats it as pending work. Any rewrite of
 * persistent files — recovery or an incomplete runtime rollback — invalidates
 * the provider cache immediately, independent of what the caller does next.
 *
 * [useBundledFallback] is the explicit offline escape hatch: it persists a
 * repair marker AND a bundled-fallback marker, drops the retained scene and
 * moves every downloaded file into a quarantine directory outside
 * [persistentDir]. The provider then reads the bundled assets only, so a mixed
 * downloaded set can never be served. The operation is idempotent (a crash
 * between moves is completed by the next call) and is undone by the next fully
 * successful [updateAll], which clears both markers and deletes the quarantine.
 * Scene/backup/quarantine paths are marked excluded from platform backups so a
 * new device never replays a COMMITTING scene.
 */
class StagedFileSetUpdate(
    private val fs: FileSystem,
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val persistentDir: Path,
    private val stagingDir: Path,
    private val backupDir: Path,
    private val legacyBackupDir: Path? = null,
    private val fileNames: List<String>,
    private val remoteName: (String) -> String = { it },
    private val invalidateCache: suspend () -> Unit,
    private val commitAttempt: (fileName: String, move: () -> Unit) -> Unit = { _, move -> move() },
) {
    /** Persisted under [persistentDir] so clearing the cache cannot erase the repair obligation. */
    private val repairMarker: Path = persistentDir / REPAIR_MARKER

    /** Persisted under [persistentDir]; active until a full update replaces the dataset. */
    private val fallbackMarker: Path = persistentDir / FALLBACK_MARKER

    /**
     * Set-aside downloaded files. It lives next to — never inside —
     * [persistentDir], so the provider cannot read a quarantined file as part
     * of the active dataset.
     */
    private val quarantineDir: Path
        get() {
            val parent = persistentDir.parent ?: error("persistent directory has no parent")
            return parent / "${persistentDir.name}_quarantine"
        }

    /**
     * True when a reset was requested but the following full download has not
     * completed yet. Kept in persistent storage so a process death right after
     * reset still forces the next update to run.
     */
    fun hasPendingRepair(): Boolean = try {
        fs.exists(repairMarker)
    } catch (_: Throwable) {
        // An unreadable marker is treated as pending: never report "up to date".
        true
    }

    /**
     * True while the app serves the bundled catalog because the downloaded
     * dataset was quarantined. A repair obligation stays pending at the same
     * time, so a later update still restores the downloaded catalog.
     */
    fun isUsingBundledFallback(): Boolean = try {
        fs.exists(fallbackMarker)
    } catch (_: Throwable) {
        false
    }

    /**
     * True when a retained transaction scene exists. The caller should run
     * [recoverPendingUpdate] before reading the dataset so the on-disk files
     * and the provider cache reflect the recovered state.
     */
    fun hasPendingRecovery(): Boolean =
        fs.exists(backupDir) || legacyBackupDir?.let { fs.exists(it) } == true

    /**
     * Resolves a retained scene. Safe to call when there is none. A blocked
     * scene returns [StagedUpdateBlockedException] and is left untouched.
     */
    suspend fun recoverPendingUpdate(): Result<Unit> = resultOf {
        fs.createDirectories(persistentDir)
        markExcludedFromPlatformBackup(persistentDir.toString())
        recoverRetainedScene()
    }

    /**
     * Discards a retained scene without restoring it and clears staging
     * residue. Persistent data files are not touched. A repair marker is
     * persisted first, so the caller must follow with a full update; the
     * marker is cleared only by a fully successful [updateAll].
     */
    suspend fun resetPendingUpdate(): Result<Unit> = resultOf {
        // Fail closed: if the marker cannot be persisted, keep the scene so the
        // caller cannot silently skip the required full download.
        persistRepairMarker()
        deleteRetainedScene()
        fs.deleteRecursivelyBestEffort(stagingDir)
        invalidateCacheSafely()
    }

    /**
     * Re-persists the repair obligation without touching data. Used when a
     * multi-dataset run failed after one dataset committed and discharged its
     * marker: the remaining work must survive process death.
     */
    suspend fun markRepairRequired(): Result<Unit> = resultOf {
        persistRepairMarker()
    }

    /**
     * User-approved offline degradation: serve the bundled catalog only.
     * The repair obligation is persisted BEFORE any file moves, so a crash
     * cannot silently drop it; the operation is idempotent and can be re-run
     * at startup to finish a partially quarantined set. Downloaded files are
     * never deleted by this call — they are set aside and replaced by the next
     * successful [updateAll].
     */
    suspend fun useBundledFallback(): Result<Unit> = resultOf {
        persistRepairMarker()
        markBundledFallback()
        deleteRetainedScene()
        fs.deleteRecursivelyBestEffort(stagingDir)
        quarantineDownloadedFiles()
        invalidateCacheSafely()
    }

    suspend fun checkUpstreamChanged(currentCopy: () -> String?): Result<Boolean> = resultOf {
        // A retained scene or an unfinished reset always needs the update flow
        // (recovery and the full download happen before anything is reported
        // up to date), so never probe the network while one exists.
        if (hasPendingRecovery() || hasPendingRepair()) return@resultOf true
        val url = "$baseUrl${remoteName(fileNames.first())}"
        val upstream = fetchText(url)
        upstream.trim() != currentCopy()?.trim()
    }

    suspend fun updateAll(
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Result<Unit> {
        var preserveBackup = false
        var ownsBackup = false
        return try {
            resultOf {
                fs.createDirectories(persistentDir)
                val stagingParent = stagingDir.parent ?: error("staging directory has no parent")
                fs.createDirectories(stagingParent)
                cleanupRestoreTemporaries()

                if (hasPendingRecovery()) {
                    try {
                        recoverRetainedScene()
                    } catch (failure: Throwable) {
                        // Never delete a scene this run could not resolve.
                        preserveBackup = hasPendingRecovery()
                        throw failure
                    }
                    if (hasPendingRecovery()) {
                        preserveBackup = true
                        throw StagedUpdateBlockedException(
                            "recovery did not consume the retained backup",
                            backupDir
                        )
                    }
                }

                fs.deleteRecursivelyBestEffort(stagingDir)
                fs.createDirectories(stagingDir)

                for ((index, fileName) in fileNames.withIndex()) {
                    onProgress(index, fileNames.size, fileName)
                    downloadTo(stagingDir / fileName, "$baseUrl${remoteName(fileName)}")
                }

                val existedBefore = fileNames.filter { fs.exists(persistentDir / it) }
                fs.deleteRecursivelyBestEffort(backupDir)
                fs.createDirectories(backupDir)
                markExcludedFromPlatformBackup(backupDir.toString())
                ownsBackup = true
                val manifestEntries = existedBefore.map { fileName ->
                    fs.copy(persistentDir / fileName, backupDir / fileName)
                    val size = fs.metadataOrNull(backupDir / fileName)?.size
                        ?: error("backup copy of $fileName has no size")
                    ManifestEntry(fileName, size)
                }
                // Publish the manifest only after every backup copy landed;
                // the temp+atomicMove write makes a torn manifest unreadable
                // rather than silently empty.
                writeAtomically(backupDir / PRESTATE_MANIFEST, renderPrestateManifest(manifestEntries))
                writeAtomically(backupDir / TRANSACTION_STATE, STATE_COMMITTING + "\n")

                try {
                    for (fileName in fileNames) {
                        val stagedFile = stagingDir / fileName
                        val targetFile = persistentDir / fileName
                        commitAttempt(fileName) { fs.atomicMove(stagedFile, targetFile) }
                    }
                } catch (commitError: Throwable) {
                    val rollbackFailures = rollbackCommit(existedBefore)
                    if (rollbackFailures.isNotEmpty()) {
                        preserveBackup = true
                        invalidateCacheSafely()
                        if (commitError is CancellationException) {
                            rollbackFailures.forEach { commitError.addSuppressed(it.cause) }
                            throw commitError
                        }
                        throw StagedRollbackException(commitError, rollbackFailures, backupDir)
                    }
                    throw commitError
                }

                try {
                    writeAtomically(backupDir / TRANSACTION_STATE, STATE_COMMITTED + "\n")
                } catch (stateFailure: Throwable) {
                    // All files are committed; keep the cache coherent even
                    // though the run reports failure.
                    invalidateCacheSafely()
                    throw stateFailure
                }

                invalidateCache()
                // The dataset is fully committed; only now may a reset's repair
                // obligation be discharged. A failed delete stays fail-closed:
                // the update is reported as failed and the next check/update
                // keeps downloading instead of claiming "up to date".
                clearRepairMarker()
                // Fresh files supersede any bundled-fallback degradation; the
                // quarantined copy is no longer needed.
                clearBundledFallback()
                onProgress(fileNames.size, fileNames.size, "完成")
            }
        } finally {
            fs.deleteRecursivelyBestEffort(stagingDir)
            if (ownsBackup && !preserveBackup) fs.deleteRecursivelyBestEffort(backupDir)
        }
    }

    private fun readTransactionState(): String? {
        val stateFile = backupDir / TRANSACTION_STATE
        if (!fs.exists(stateFile)) return null
        return try {
            fs.read(stateFile) { readUtf8() }.trim()
        } catch (failure: Throwable) {
            throw StagedUpdateBlockedException("transaction state is unreadable", backupDir, failure)
        }
    }

    private suspend fun recoverRetainedScene() {
        cleanupRestoreTemporaries()
        if (fs.exists(backupDir)) {
            // Upgraded installs may already hold a scene written before the
            // exclusion hook existed; re-apply it before touching the scene.
            markExcludedFromPlatformBackup(backupDir.toString())
            when (val state = readTransactionState()) {
                null -> discardInertBackup()
                STATE_COMMITTED -> discardCommittedBackup()
                STATE_COMMITTING -> restorePendingCommit()
                else -> throw StagedUpdateBlockedException(
                    "unknown transaction state '${state.take(32)}'",
                    backupDir
                )
            }
            // A legacy scene only exists before the first new-format update;
            // once the new scene resolved it is stale residue.
            deleteLegacySceneBestEffort()
            return
        }
        val legacy = legacyBackupDir?.takeIf { fs.exists(it) } ?: return
        markExcludedFromPlatformBackup(legacy.toString())
        restoreLegacyScene(legacy)
    }

    private fun discardInertBackup() {
        // No state means the commit never started: a partial copy set is inert.
        AppLogger.w(TAG) { "discarding an uncommitted backup scene at $backupDir" }
        fs.deleteRecursively(backupDir)
    }

    private suspend fun discardCommittedBackup() {
        // COMMITTED means every new file is in place; rolling back would undo
        // a successful commit. Re-invalidate in case the process died before
        // the original cache invalidation ran.
        fs.deleteRecursively(backupDir)
        invalidateCacheSafely()
    }

    private suspend fun restorePendingCommit() {
        val manifest = readPrestateManifest()
            ?: throw StagedUpdateBlockedException("prestate manifest is missing or invalid", backupDir)
        verifyBackupFiles(manifest.entries, backupDir)
        restoreEntries(manifest.entries, backupDir)
        fs.deleteRecursively(backupDir)
    }

    /**
     * Old versions kept the backup under cacheDir and wrote a plain
     * newline-separated filename list as `prestate.txt`, without any state
     * file. The old writer could have started a commit, so the scene is
     * treated conservatively like COMMITTING rather than inert.
     * An empty, malformed or incomplete old scene is ambiguous and must fail
     * closed instead of silently discarding the only backup.
     */
    private suspend fun restoreLegacyScene(dir: Path) {
        if (fs.exists(dir / TRANSACTION_STATE)) {
            throw StagedUpdateBlockedException(
                "legacy backup contains an unexpected transaction state",
                dir
            )
        }
        val manifestFile = dir / PRESTATE_MANIFEST
        if (!fs.exists(manifestFile)) {
            throw StagedUpdateBlockedException("legacy prestate manifest is missing", dir)
        }
        val text = try {
            fs.read(manifestFile) { readUtf8() }
        } catch (failure: Throwable) {
            throw StagedUpdateBlockedException("legacy prestate manifest is unreadable", dir, failure)
        }
        val names = parseLegacyManifest(text)
            ?: throw StagedUpdateBlockedException("legacy prestate manifest is empty or invalid", dir)
        val entries = names.map { name ->
            val metadata = fs.metadataOrNull(dir / name)
            if (metadata == null || !metadata.isRegularFile) {
                throw StagedUpdateBlockedException("legacy backup file '$name' is missing", dir)
            }
            val size = metadata.size
                ?: throw StagedUpdateBlockedException("legacy backup file '$name' has no size", dir)
            ManifestEntry(name, size)
        }
        restoreEntries(entries, dir)
        fs.deleteRecursively(dir)
    }

    private fun parseLegacyManifest(text: String): List<String>? {
        // Old writer used joinToString("\n") with no trailing newline. Every
        // line must be a known file name; empty or duplicated entries make the
        // scene ambiguous (especially an empty file, which cannot be told
        // apart from a truncated manifest).
        if (text.isEmpty()) return null
        val lines = text.split('\n')
        if (lines.isEmpty() || lines.any { it.isEmpty() }) return null
        if (lines.size > fileNames.size) return null
        val seen = mutableSetOf<String>()
        for (name in lines) {
            if (name !in fileNames || !seen.add(name)) return null
        }
        return seen.toList()
    }

    private fun verifyBackupFiles(entries: List<ManifestEntry>, sourceDir: Path) {
        val incomplete = entries.firstOrNull { entry ->
            val metadata = fs.metadataOrNull(sourceDir / entry.name)
            metadata == null || !metadata.isRegularFile || metadata.size != entry.size
        }
        if (incomplete != null) {
            throw StagedUpdateBlockedException(
                "backup file '${incomplete.name}' is missing or truncated",
                sourceDir
            )
        }
    }

    /**
     * Restores every manifest entry atomically, then removes files the
     * pre-state did not contain. Rewrites invalidate the provider cache even
     * when the surrounding update later fails.
     */
    private suspend fun restoreEntries(entries: List<ManifestEntry>, sourceDir: Path) {
        preflightRestore(entries, sourceDir)

        var changed = false
        val failures = mutableListOf<RollbackFailure>()
        for (entry in entries) {
            try {
                if (restoreFile(sourceDir / entry.name, persistentDir / entry.name)) changed = true
            } catch (failure: Throwable) {
                failures += RollbackFailure(entry.name, failure)
            }
        }
        if (failures.isNotEmpty()) {
            if (changed) invalidateCacheSafely()
            throw StagedRollbackException(
                IllegalStateException("previous update recovery failed"),
                failures,
                sourceDir
            )
        }

        val listed = entries.map { it.name }.toSet()
        for (fileName in fileNames) {
            if (fileName in listed) continue
            val target = persistentDir / fileName
            if (!fs.exists(target)) continue
            try {
                fs.delete(target)
                changed = true
            } catch (failure: Throwable) {
                failures += RollbackFailure(fileName, failure)
            }
        }
        if (failures.isNotEmpty()) {
            if (changed) invalidateCacheSafely()
            throw StagedRollbackException(
                IllegalStateException("previous update recovery failed while removing newly committed files"),
                failures,
                sourceDir
            )
        }

        if (changed) invalidateCacheSafely()
    }

    private fun persistRepairMarker() {
        if (fs.exists(repairMarker)) return
        writeAtomically(repairMarker, REPAIR_MARKER_CONTENT)
        markExcludedFromPlatformBackup(repairMarker.toString())
        if (!fs.exists(repairMarker)) {
            throw IllegalStateException("failed to persist the repair marker at $repairMarker")
        }
    }

    private fun clearRepairMarker() {
        if (!fs.exists(repairMarker)) return
        fs.delete(repairMarker)
        if (fs.exists(repairMarker)) {
            throw IllegalStateException("failed to clear the repair marker at $repairMarker")
        }
    }

    private fun markBundledFallback() {
        if (!fs.exists(fallbackMarker)) {
            writeAtomically(fallbackMarker, FALLBACK_MARKER_CONTENT)
        }
        markExcludedFromPlatformBackup(fallbackMarker.toString())
        if (!fs.exists(fallbackMarker)) {
            throw IllegalStateException("failed to persist the bundled fallback marker")
        }
    }

    private fun clearBundledFallback() {
        if (fs.exists(fallbackMarker)) {
            fs.delete(fallbackMarker)
            if (fs.exists(fallbackMarker)) {
                throw IllegalStateException("failed to clear the bundled fallback marker")
            }
        }
        fs.deleteRecursivelyBestEffort(quarantineDir)
    }

    /**
     * Moves every downloaded file out of [persistentDir]. Individual atomic
     * renames keep the operation restartable: a crash between files leaves the
     * remaining ones in place, and the next [useBundledFallback] (or the
     * startup gate) finishes the job before anyone reads the dataset.
     */
    private fun quarantineDownloadedFiles() {
        val dir = quarantineDir
        fs.createDirectories(dir)
        markExcludedFromPlatformBackup(dir.toString())
        var firstFailure: Throwable? = null
        for (fileName in fileNames) {
            val source = persistentDir / fileName
            if (!fs.exists(source)) continue
            try {
                val target = dir / fileName
                if (fs.exists(target)) fs.delete(target)
                fs.atomicMove(source, target)
            } catch (error: Throwable) {
                if (firstFailure == null) firstFailure = error
            }
        }
        val remaining = fileNames.filter { fs.exists(persistentDir / it) }
        if (firstFailure != null || remaining.isNotEmpty()) {
            throw CatalogStorageException(firstFailure)
        }
    }

    private fun deleteRetainedScene() {
        var failure: Throwable? = null
        for (dir in listOfNotNull(backupDir, legacyBackupDir)) {
            try {
                if (fs.exists(dir)) fs.deleteRecursively(dir)
                if (fs.exists(dir)) {
                    throw IllegalStateException("failed to reset the pending update scene at $dir")
                }
            } catch (error: Throwable) {
                if (failure == null) failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun deleteLegacySceneBestEffort() {
        val dir = legacyBackupDir ?: return
        runCatching { if (fs.exists(dir)) fs.deleteRecursively(dir) }
    }

    private fun readPrestateManifest(): PrestateManifest? {
        val manifestFile = backupDir / PRESTATE_MANIFEST
        if (!fs.exists(manifestFile)) return null
        val text = try {
            fs.read(manifestFile) { readUtf8() }
        } catch (_: Throwable) {
            return null
        }
        return parsePrestateManifest(text)
    }

    private fun parsePrestateManifest(text: String): PrestateManifest? {
        val rawLines = text.split('\n')
        val lines = if (rawLines.isNotEmpty() && rawLines.last().isEmpty()) rawLines.dropLast(1) else rawLines
        if (lines.size < 2 || lines.any { it.isEmpty() }) return null
        if (lines[0] != PRESTATE_MAGIC) return null
        val count = lines[1].toIntOrNull() ?: return null
        if (count < 0 || count > fileNames.size) return null
        if (lines.size != count + 2) return null
        val entries = mutableListOf<ManifestEntry>()
        val seen = mutableSetOf<String>()
        for (index in 0 until count) {
            val parts = lines[index + 2].split('\t')
            if (parts.size != 2) return null
            val size = parts[0].toLongOrNull() ?: return null
            if (size < 0) return null
            val name = parts[1]
            if (name !in fileNames || !seen.add(name)) return null
            entries += ManifestEntry(name, size)
        }
        return PrestateManifest(entries)
    }

    private fun renderPrestateManifest(entries: List<ManifestEntry>): String = buildString {
        append(PRESTATE_MAGIC).append('\n')
        append(entries.size).append('\n')
        for (entry in entries) {
            append(entry.size).append('\t').append(entry.name).append('\n')
        }
    }

    /**
     * Verifies every backup is readable and every target can be replaced
     * before the first destructive-adjacent operation runs. Failures here keep
     * persistent files and the backup scene exactly as they were.
     */
    private fun preflightRestore(entries: List<ManifestEntry>, sourceDir: Path) {
        val failures = mutableListOf<RollbackFailure>()
        for (entry in entries) {
            try {
                val backup = sourceDir / entry.name
                val metadata = fs.metadataOrNull(backup)
                    ?: throw IllegalStateException("backup missing for ${entry.name}")
                if (!metadata.isRegularFile || metadata.size != entry.size) {
                    throw IllegalStateException("backup size mismatch for ${entry.name}")
                }
                fs.read(backup) { readByteArray() }
                val target = persistentDir / entry.name
                val parent = target.parent ?: throw IllegalStateException("target has no parent: $target")
                fs.createDirectories(parent)
                if (fs.exists(target) && fs.metadataOrNull(target)?.isRegularFile != true) {
                    throw IllegalStateException("target is not a regular file: $target")
                }
            } catch (failure: Throwable) {
                failures += RollbackFailure(entry.name, failure)
            }
        }
        if (failures.isNotEmpty()) {
            throw StagedRollbackException(
                IllegalStateException("previous update recovery preflight failed"),
                failures,
                sourceDir
            )
        }
    }

    /**
     * Replaces [target] with the content of [backup] through a same-directory
     * temp file and atomicMove. The current target is never deleted first, so
     * a failure leaves the existing (possibly already committed) file intact.
     * Returns true when the file was actually rewritten.
     */
    private fun restoreFile(backup: Path, target: Path): Boolean {
        val backupMetadata = fs.metadataOrNull(backup)
            ?: throw IllegalStateException("backup missing for ${target.name}")
        if (!backupMetadata.isRegularFile) {
            throw IllegalStateException("backup is not a regular file: $backup")
        }
        val targetMetadata = fs.metadataOrNull(target)
        if (targetMetadata != null &&
            targetMetadata.isRegularFile &&
            targetMetadata.size == backupMetadata.size &&
            sameContent(backup, target)
        ) {
            return false
        }
        val parent = target.parent ?: throw IllegalStateException("target has no parent: $target")
        fs.createDirectories(parent)
        val temp = parent / (target.name + RESTORE_TEMP_SUFFIX)
        if (fs.exists(temp)) fs.delete(temp)
        fs.copy(backup, temp)
        try {
            fs.atomicMove(temp, target)
        } finally {
            if (fs.exists(temp)) {
                runCatching { fs.delete(temp) }
            }
        }
        return true
    }

    private fun sameContent(first: Path, second: Path): Boolean = try {
        fs.read(first) { readByteArray() }.contentEquals(fs.read(second) { readByteArray() })
    } catch (_: Throwable) {
        false
    }

    private fun cleanupRestoreTemporaries() {
        for (fileName in fileNames) {
            val temp = persistentDir / (fileName + RESTORE_TEMP_SUFFIX)
            if (fs.exists(temp)) {
                runCatching { fs.delete(temp) }
            }
        }
    }

    private fun writeAtomically(target: Path, content: String) {
        val parent = target.parent ?: error("target has no parent: $target")
        fs.createDirectories(parent)
        val temp = parent / (target.name + TEMP_SUFFIX)
        fs.write(temp) { writeUtf8(content) }
        fs.atomicMove(temp, target)
    }

    private suspend fun downloadTo(destination: Path, url: String) {
        val bytes = fetchBytes(url)
        val sink = fs.sink(destination).buffer()
        try {
            sink.write(bytes)
        } finally {
            sink.close()
        }
    }

    private suspend fun fetchText(url: String): String = try {
        val response = fetch(url)
        response.body()
    } catch (error: CancellationException) {
        throw error
    } catch (error: CatalogDownloadException) {
        throw error
    } catch (error: Throwable) {
        throw CatalogDownloadException(error)
    }

    private suspend fun fetchBytes(url: String): ByteArray = try {
        val response = fetch(url)
        response.body()
    } catch (error: CancellationException) {
        throw error
    } catch (error: CatalogDownloadException) {
        throw error
    } catch (error: Throwable) {
        throw CatalogDownloadException(error)
    }

    /**
     * Transport failures (offline, DNS, timeout, non-2xx) all collapse into
     * [CatalogDownloadException] so UI never has to interpret platform
     * exception messages.
     */
    private suspend fun fetch(url: String): HttpResponse {
        val response = try {
            httpClient.get(url)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw CatalogDownloadException(error)
        }
        if (!response.status.isSuccess()) {
            throw CatalogDownloadException()
        }
        return response
    }

    private fun rollbackCommit(existedBefore: List<String>): List<RollbackFailure> {
        val rollbackFailures = mutableListOf<RollbackFailure>()
        for (fileName in fileNames) {
            if (fileName !in existedBefore) continue
            try {
                val target = persistentDir / fileName
                val backup = backupDir / fileName
                if (!fs.exists(backup)) {
                    throw IllegalStateException("backup missing for previously existing file")
                }
                restoreFile(backup, target)
            } catch (failure: Throwable) {
                rollbackFailures += RollbackFailure(fileName, failure)
            }
        }
        // Files created by this commit are handled only after every restore
        // succeeded; otherwise their removal would lose the newer data while
        // the old set is still incomplete.
        if (rollbackFailures.isEmpty()) {
            for (fileName in fileNames) {
                if (fileName in existedBefore) continue
                try {
                    val target = persistentDir / fileName
                    if (fs.exists(target)) fs.delete(target)
                } catch (failure: Throwable) {
                    rollbackFailures += RollbackFailure(fileName, failure)
                }
            }
        }
        if (rollbackFailures.isNotEmpty()) {
            AppLogger.w(TAG) {
                "rollback incomplete for: ${rollbackFailures.map { it.fileName }.joinToString()}; backup retained"
            }
        }
        return rollbackFailures
    }

    private suspend fun invalidateCacheSafely() {
        withContext(NonCancellable) {
            runCatching { invalidateCache() }.onFailure { failure ->
                AppLogger.w(TAG) { "cache invalidation failed: ${failure.message}" }
            }
        }
    }

    private data class ManifestEntry(val name: String, val size: Long)

    private data class PrestateManifest(val entries: List<ManifestEntry>)

    private companion object {
        const val TAG = "StagedFileSetUpdate"
        const val PRESTATE_MANIFEST = "prestate.txt"
        const val TRANSACTION_STATE = "transaction.state"
        const val REPAIR_MARKER = "repair-required.marker"
        const val REPAIR_MARKER_CONTENT = "repair-required\n"
        const val FALLBACK_MARKER = "bundled-fallback.marker"
        const val FALLBACK_MARKER_CONTENT = "bundled-fallback\n"
        const val PRESTATE_MAGIC = "PHI-PRESTATE-V1"
        const val STATE_COMMITTING = "COMMITTING"
        const val STATE_COMMITTED = "COMMITTED"
        const val TEMP_SUFFIX = ".tmp"
        const val RESTORE_TEMP_SUFFIX = ".restore.tmp"
    }
}

private fun FileSystem.deleteRecursivelyBestEffort(dir: Path) {
    runCatching { if (exists(dir)) deleteRecursively(dir) }
}
