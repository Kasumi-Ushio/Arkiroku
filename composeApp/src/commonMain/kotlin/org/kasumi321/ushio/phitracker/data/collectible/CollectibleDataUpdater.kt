package org.kasumi321.ushio.phitracker.data.collectible

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.platformFileSystem

open class CollectibleDataUpdater(
        private val httpClient: HttpClient,
        private val paths: PlatformPaths,
        private val collectibleDataProvider: CollectibleDataProvider
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

    /**
     * Lightweight upstream-change probe: downloads the upstream version.txt
     * only and compares it against the currently effective local copy
     * (downloaded file wins over the bundled fallback). Full downloads stay
     * in [updateAll].
     */
    open suspend fun checkUpstreamChanged(): Result<Boolean> = runCatching {
        val url = "$BASE_URL${remoteName(FILE_NAMES.first())}"
        val response = httpClient.get(url)
        if (!response.status.isSuccess()) {
            throw RuntimeException("Download failed (${response.status}): $url")
        }
        val upstream: String = response.body()
        upstream.trim() != collectibleDataProvider.currentVersionText()
    }

    open suspend fun updateAll(
            onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Result<Unit> {
        val fs = platformFileSystem()
        val collectibleDataDir = paths.filesDir.toPath() / "collectible_data"
        val cacheDir = paths.cacheDir.toPath()
        val stagingDir = cacheDir / "collectible_data_staging"
        val backupDir = cacheDir / "collectible_data_backup"

        fs.createDirectories(collectibleDataDir)
        fs.createDirectories(cacheDir)
        // Clean up stale directories from prior runs
        fs.deleteRecursivelyBestEffort(stagingDir)
        fs.deleteRecursivelyBestEffort(backupDir)
        fs.createDirectories(stagingDir)

        return runCatching {
            // Phase 1: download all files to staging — failure leaves persistent files untouched
            for ((index, fileName) in FILE_NAMES.withIndex()) {
                onProgress(index, FILE_NAMES.size, fileName)
                val url = "$BASE_URL${remoteName(fileName)}"
                val response = httpClient.get(url)
                if (!response.status.isSuccess()) {
                    throw RuntimeException("Download failed (${response.status}): $url")
                }
                val bytes: ByteArray = response.body()
                val stagedFile = stagingDir / fileName
                val sink = fs.sink(stagedFile).buffer()
                try {
                    sink.write(bytes)
                } finally {
                    sink.close()
                }
            }

            // Phase 2a: back up existing persistent files
            backupExistingFiles(fs, collectibleDataDir, backupDir)

            // Phase 2b: commit staged files to persistent directory
            try {
                for (fileName in FILE_NAMES) {
                    val stagedFile = stagingDir / fileName
                    val targetFile = collectibleDataDir / fileName
                    fs.atomicMove(stagedFile, targetFile)
                }
            } catch (commitError: Exception) {
                // Phase 2c: restore original files from backup
                restoreFromBackup(fs, backupDir, collectibleDataDir)
                throw commitError
            }

            // Invalidate cache only after persistent files are fully updated
            collectibleDataProvider.invalidateCache()
            onProgress(FILE_NAMES.size, FILE_NAMES.size, "完成")
        }
                .also {
                    // Best-effort cleanup — must not hide the primary failure
                    fs.deleteRecursivelyBestEffort(stagingDir)
                    fs.deleteRecursivelyBestEffort(backupDir)
                }
    }
}

// ── private helpers ────────────────────────────────────────────────

private fun backupExistingFiles(fs: FileSystem, sourceDir: okio.Path, backupDir: okio.Path) {
    fs.createDirectories(backupDir)
    for (fileName in CollectibleDataUpdater.FILE_NAMES) {
        val source = sourceDir / fileName
        if (fs.exists(source)) {
            fs.copy(source, backupDir / fileName)
        }
    }
}

private fun restoreFromBackup(fs: FileSystem, backupDir: okio.Path, targetDir: okio.Path) {
    for (fileName in CollectibleDataUpdater.FILE_NAMES) {
        val backup = backupDir / fileName
        if (fs.exists(backup)) {
            fs.atomicMove(backup, targetDir / fileName)
        }
    }
}

private fun FileSystem.deleteRecursivelyBestEffort(dir: okio.Path) {
    runCatching { if (exists(dir)) deleteRecursively(dir) }
}
