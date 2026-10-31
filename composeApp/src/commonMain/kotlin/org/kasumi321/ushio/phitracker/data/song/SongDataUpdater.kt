package org.kasumi321.ushio.phitracker.data.song

import io.ktor.client.HttpClient
import okio.FileSystem
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.common.StagedFileSetUpdate
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.platformFileSystem

open class SongDataUpdater(
        httpClient: HttpClient,
        paths: PlatformPaths,
        private val songDataProvider: SongDataProvider,
        fs: FileSystem = platformFileSystem()
) {
    companion object {
        private const val BASE_URL =
                "https://gh-proxy.org/https://raw.githubusercontent.com/Catrong/phi-plugin/main/resources/info/"
        // Upstream merged difficulty constants into info.csv and removed difficulty.csv.
        val FILE_NAMES = listOf("info.csv", "infolist.json", "notesInfo.json", "nicklist.yaml")
    }

    private val staged = StagedFileSetUpdate(
            fs = fs,
            httpClient = httpClient,
            baseUrl = BASE_URL,
            persistentDir = paths.filesDir.toPath() / "song_data",
            stagingDir = paths.cacheDir.toPath() / "song_data_staging",
            backupDir = paths.filesDir.toPath() / "song_data_backup",
            legacyBackupDir = paths.cacheDir.toPath() / "song_data_backup",
            fileNames = FILE_NAMES,
            invalidateCache = { songDataProvider.invalidateCache() }
    )

    /**
     * Lightweight upstream-change probe: downloads info.csv only and compares
     * it against the currently effective copy (downloaded file wins over the
     * bundled fallback). A retained recovery scene always reports a change.
     * Full downloads stay in [updateAll].
     */
    open suspend fun checkUpstreamChanged(): Result<Boolean> =
            staged.checkUpstreamChanged { songDataProvider.currentInfoCsv() }

    open fun hasPendingRecovery(): Boolean = staged.hasPendingRecovery()

    open fun hasPendingRepair(): Boolean = staged.hasPendingRepair()

    open fun isUsingBundledFallback(): Boolean = staged.isUsingBundledFallback()

    open suspend fun recoverPendingUpdate(): Result<Unit> = staged.recoverPendingUpdate()

    open suspend fun resetPendingUpdate(): Result<Unit> = staged.resetPendingUpdate()

    open suspend fun markRepairRequired(): Result<Unit> = staged.markRepairRequired()

    open suspend fun useBundledFallback(): Result<Unit> = staged.useBundledFallback()

    open suspend fun updateAll(
            onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Result<Unit> = staged.updateAll(onProgress)
}
