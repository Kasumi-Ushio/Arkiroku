package org.kasumi321.ushio.phitracker.data.collectible

import io.ktor.client.HttpClient
import okio.FileSystem
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.common.StagedFileSetUpdate
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.platformFileSystem

open class CollectibleDataUpdater(
        httpClient: HttpClient,
        paths: PlatformPaths,
        private val collectibleDataProvider: CollectibleDataProvider,
        fs: FileSystem = platformFileSystem()
) {
    companion object {
        const val BASE_URL =
                "https://gh-proxy.org/https://raw.githubusercontent.com/RinLin-NYA/Phigros_Resource/info/"

        /** 上游仓库中的版本文件名与本地存储名不同，避免与其他数据文件的版本标记混淆 */
        private const val REMOTE_VERSION_FILE = "version.txt"
        private const val LOCAL_VERSION_FILE = "collection_version.txt"

        val FILE_NAMES = listOf(
            LOCAL_VERSION_FILE,
            "collection_zh_cn.csv",
            "collection_zh_tw.csv",
            "collection_en.csv",
            "collection_ja.csv",
            "collection_ko.csv"
        )

        fun remoteName(fileName: String): String =
                if (fileName == LOCAL_VERSION_FILE) REMOTE_VERSION_FILE else fileName
    }

    private val staged = StagedFileSetUpdate(
            fs = fs,
            httpClient = httpClient,
            baseUrl = BASE_URL,
            persistentDir = paths.filesDir.toPath() / "collectible_data",
            stagingDir = paths.cacheDir.toPath() / "collectible_data_staging",
            backupDir = paths.filesDir.toPath() / "collectible_data_backup",
            legacyBackupDir = paths.cacheDir.toPath() / "collectible_data_backup",
            fileNames = FILE_NAMES,
            remoteName = ::remoteName,
            invalidateCache = { collectibleDataProvider.invalidateCache() }
    )

    /**
     * Lightweight upstream-change probe: downloads the upstream version.txt
     * only and compares it against the currently effective local copy
     * (downloaded file wins over the bundled fallback). A retained recovery
     * scene always reports a change. Full downloads stay in [updateAll].
     */
    open suspend fun checkUpstreamChanged(): Result<Boolean> =
            staged.checkUpstreamChanged { collectibleDataProvider.currentVersionText() }

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
