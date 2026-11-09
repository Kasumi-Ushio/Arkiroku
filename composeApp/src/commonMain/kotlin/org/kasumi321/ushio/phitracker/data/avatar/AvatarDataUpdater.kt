package org.kasumi321.ushio.phitracker.data.avatar

import io.ktor.client.HttpClient
import okio.FileSystem
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.common.StagedFileSetUpdate
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.platformFileSystem

/** Transactional updater for the single avatar catalog TSV. */
open class AvatarDataUpdater(
    httpClient: HttpClient,
    paths: PlatformPaths,
    private val catalogProvider: AvatarCatalogProvider,
    fs: FileSystem = platformFileSystem()
) {
    companion object {
        const val BASE_URL =
            "https://gh-proxy.org/https://raw.githubusercontent.com/RinLin-NYA/Phigros_Resource/info/"
        val FILE_NAMES = listOf(AvatarCatalogProvider.CATALOG_FILE)

        fun remoteName(fileName: String): String {
            require(fileName == AvatarCatalogProvider.CATALOG_FILE) {
                "Unknown avatar catalog file: $fileName"
            }
            return "tmp.tsv"
        }
    }

    private val staged = StagedFileSetUpdate(
        fs = fs,
        httpClient = httpClient,
        baseUrl = BASE_URL,
        persistentDir = paths.filesDir.toPath() / AvatarCatalogProvider.DATA_DIRECTORY,
        stagingDir = paths.cacheDir.toPath() / "avatar_data_staging",
        backupDir = paths.filesDir.toPath() / "avatar_data_backup",
        legacyBackupDir = paths.cacheDir.toPath() / "avatar_data_backup",
        fileNames = FILE_NAMES,
        remoteName = ::remoteName,
        invalidateCache = { catalogProvider.invalidateCache() },
        validateStaged = { stagingDir ->
            val text = fs.read(stagingDir / AvatarCatalogProvider.CATALOG_FILE) { readUtf8() }
            catalogProvider.validateCatalogText(text)
        }
    )

    open suspend fun checkUpstreamChanged(): Result<Boolean> =
        staged.checkUpstreamChanged { catalogProvider.currentCatalogText() }

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
