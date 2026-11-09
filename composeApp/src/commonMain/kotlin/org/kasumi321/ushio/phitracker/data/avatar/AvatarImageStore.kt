package org.kasumi321.ushio.phitracker.data.avatar

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLPath
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.platform.BinaryAssetReader
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.createBinaryAssetReader
import org.kasumi321.ushio.phitracker.data.platform.platformFileSystem
import org.kasumi321.ushio.phitracker.domain.model.GameAvatar

data class AvatarImageReconcileResult(
    val downloadedKeys: Set<String> = emptySet(),
    val deletedKeys: Set<String> = emptySet(),
    val failedKeys: Set<String> = emptySet()
) {
    val isFullySuccessful: Boolean get() = failedKeys.isEmpty()
}

/**
 * Owns all local game-avatar PNGs. Persistent pool files are shared by every
 * account; selected avatars are separate per-account copies so later pool
 * reconciliation can never change or remove an in-use profile image.
 */
class AvatarImageStore(
    private val httpClient: HttpClient,
    paths: PlatformPaths,
    private val assetReader: BinaryAssetReader = createBinaryAssetReader(),
    private val fs: FileSystem = platformFileSystem()
) {
    private val poolDir = paths.filesDir.toPath() / "avatar_pool"
    private val managedAccountDir = paths.filesDir.toPath() / "avatar_builtin"
    private val poolStagingDir = paths.cacheDir.toPath() / "avatar_pool_staging"
    private val bundledDir = paths.cacheDir.toPath() / "avatar_bundled"
    private val writeMutex = Mutex()

    /** Pool first, then an on-demand materialized bundled PNG, then HTTP preview. */
    suspend fun previewUri(avatar: GameAvatar): String {
        requireSafeKey(avatar.resourceKey)
        validPngFile(poolPath(avatar.resourceKey))?.let { return it.toString() }
        try {
            materializeBundled(avatar.resourceKey)?.let { return it.toString() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // A preview is non-authoritative; selection still goes through
            // ensureLocal and therefore can never persist this HTTP fallback.
        }
        return remoteUrl(avatar.resourceKey)
    }

    /** Returns only an app-local valid PNG, downloading it into the pool if needed. */
    suspend fun ensureLocal(avatar: GameAvatar): String {
        val key = avatar.resourceKey
        requireSafeKey(key)
        validPngFile(poolPath(key))?.let { return it.toString() }
        try {
            materializeBundled(key)?.let { return it.toString() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Missing/corrupt bundled data can be repaired from the canonical
            // remote image without making the catalog unavailable.
        }
        downloadToPool(key)
        return validPngFile(poolPath(key))?.toString()
            ?: error("Avatar image was not stored locally: $key")
    }

    /** Copies the selected image under a collision-resistant owner+key name. */
    suspend fun createAccountAvatar(avatar: GameAvatar, owner: String): String {
        val source = ensureLocal(avatar).toPath()
        val target = managedAccountDir / accountFileName(owner, avatar.resourceKey)
        validPngFile(target)?.let { return it.toString() }
        val bytes = fs.read(source) { readByteArray() }
        requireValidPng(bytes)
        writePngAtomically(target, bytes)
        return validPngFile(target)?.toString()
            ?: error("Account avatar was not stored locally")
    }

    /** Deletes only direct children of avatar_builtin; arbitrary URIs are ignored. */
    fun deleteManagedAccountAvatar(uri: String?) {
        if (uri.isNullOrEmpty()) return
        val candidate = runCatching { uri.toPath() }.getOrNull() ?: return
        if (candidate.parent != managedAccountDir || candidate == managedAccountDir) return
        if (fs.exists(candidate) && fs.metadata(candidate).isRegularFile) {
            fs.delete(candidate)
        }
    }

    /**
     * Reconciles the shared pool against the committed catalog. Missing
     * downloads run with at most six in flight; obsolete deletion is attempted
     * even when some downloads fail. Cancellation is never converted into a
     * per-image failure, and a later instance can repeat this operation from
    * on-disk state alone.
     */
    suspend fun reconcile(
        requiredKeys: Set<String>,
        onProgress: (completed: Int, total: Int, currentKey: String) -> Unit = { _, _, _ -> }
    ): AvatarImageReconcileResult {
        requiredKeys.forEach(::requireSafeKey)
        val poolKeys = listPoolKeys()
        val validPoolKeys = poolKeys.filterTo(mutableSetOf()) { validPngFile(poolPath(it)) != null }
        val missing = (requiredKeys - validPoolKeys).filterNot { hasValidBundledImage(it) }.sorted()
        val obsolete = (poolKeys - requiredKeys).sorted()
        val total = missing.size + obsolete.size
        val downloaded = linkedSetOf<String>()
        val deleted = linkedSetOf<String>()
        val failed = linkedSetOf<String>()
        val resultMutex = Mutex()
        var completed = 0

        coroutineScope {
            val semaphore = Semaphore(6)
            missing.map { key ->
                launch {
                    semaphore.withPermit {
                        resultMutex.withLock { onProgress(completed, total, key) }
                        try {
                            if (downloadToPool(key)) {
                                resultMutex.withLock { downloaded += key }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            resultMutex.withLock { failed += key }
                        }
                        resultMutex.withLock {
                            completed++
                            onProgress(completed, total, key)
                        }
                    }
                }
            }.forEach { it.join() }
        }

        for (key in obsolete) {
            resultMutex.withLock { onProgress(completed, total, key) }
            try {
                val path = poolPath(key)
                if (fs.exists(path)) fs.delete(path)
                deleted += key
            } catch (_: Throwable) {
                failed += key
            }
            completed++
            onProgress(completed, total, key)
        }
        return AvatarImageReconcileResult(downloaded, deleted, failed)
    }

    private suspend fun downloadToPool(key: String): Boolean {
        requireSafeKey(key)
        val target = poolPath(key)
        if (validPngFile(target) != null) return false
        val response = httpClient.get(remoteUrl(key))
        require(response.status.isSuccess()) { "Avatar download failed with ${response.status}" }
        val bytes: ByteArray = response.body()
        requireValidPng(bytes)
        writeMutex.withLock {
            if (validPngFile(target) != null) return false
            writePngAtomicallyLocked(target, bytes, poolStagingDir)
        }
        return true
    }

    private suspend fun materializeBundled(key: String): Path? {
        val target = bundledDir / assetName(key)
        validPngFile(target)?.let { return it }
        val bytes = assetReader.readBytes(assetName(key))
        requireValidPng(bytes)
        writeMutex.withLock {
            validPngFile(target)?.let { return it }
            writePngAtomicallyLocked(target, bytes)
        }
        return target
    }

    private fun hasValidBundledImage(key: String): Boolean {
        if (validPngFile(bundledDir / assetName(key)) != null) return true
        return try {
            isValidPng(assetReader.readBytes(assetName(key)))
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun writePngAtomically(target: Path, bytes: ByteArray) {
        requireValidPng(bytes)
        writeMutex.withLock { writePngAtomicallyLocked(target, bytes) }
    }

    /** Caller holds [writeMutex]. */
    private fun writePngAtomicallyLocked(
        target: Path,
        bytes: ByteArray,
        stagingDir: Path? = null
    ) {
        val parent = target.parent ?: error("Avatar target has no parent")
        fs.createDirectories(parent)
        val staging = stagingDir?.also { fs.createDirectories(it) } ?: parent
        val temporary = staging / "${target.name}.tmp"
        try {
            if (fs.exists(temporary)) fs.delete(temporary)
            fs.write(temporary) { write(bytes) }
            requireValidPng(fs.read(temporary) { readByteArray() })
            fs.atomicMove(temporary, target)
        } finally {
            if (fs.exists(temporary)) runCatching { fs.delete(temporary) }
        }
    }

    private fun validPngFile(path: Path): Path? = try {
        if (fs.exists(path) && fs.metadata(path).isRegularFile &&
            isValidPng(fs.read(path) { readByteArray(PNG_SIGNATURE.size.toLong()) })
        ) path else null
    } catch (_: Throwable) {
        null
    }

    private fun listPoolKeys(): Set<String> {
        if (!fs.exists(poolDir)) return emptySet()
        return fs.list(poolDir).mapNotNull { path ->
            if (!fs.metadata(path).isRegularFile) return@mapNotNull null
            val name = path.name
            if (!name.startsWith(POOL_PREFIX) || !name.endsWith(PNG_SUFFIX)) {
                return@mapNotNull null
            }
            name.removePrefix(POOL_PREFIX).removeSuffix(PNG_SUFFIX)
                .takeIf { it.isNotBlank() && isSafeKey(it) }
        }.toSet()
    }

    private fun poolPath(key: String): Path = poolDir / assetName(key)

    private fun assetName(key: String): String = "$POOL_PREFIX$key$PNG_SUFFIX"

    private fun remoteUrl(key: String): String = "$IMAGE_BASE_URL${key.encodeURLPath()}$PNG_SUFFIX"

    private fun accountFileName(owner: String, key: String): String {
        val digest = "$owner\u0000$key".encodeUtf8().sha256().hex()
        val readableKey = key.map { char ->
            if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.') char else '_'
        }.joinToString("").take(48)
        return "account-$digest-$readableKey.png"
    }

    private fun requireSafeKey(key: String) {
        require(key.isNotBlank() && isSafeKey(key)) { "Unsafe avatar resource key" }
    }

    private fun isSafeKey(key: String): Boolean =
        key != "." && key != ".." &&
            key.indexOf('/') < 0 && key.indexOf('\\') < 0 && key.indexOf('\u0000') < 0

    private companion object {
        const val IMAGE_BASE_URL =
            "https://gh-proxy.org/https://raw.githubusercontent.com/RinLin-NYA/Phigros_Resource/avatar/"
        const val POOL_PREFIX = "avatar."
        const val PNG_SUFFIX = ".png"
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        fun isValidPng(bytes: ByteArray): Boolean =
            bytes.size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }

        fun requireValidPng(bytes: ByteArray) {
            require(bytes.isNotEmpty()) { "Avatar PNG is empty" }
            require(isValidPng(bytes)) { "Avatar image has an invalid PNG signature" }
        }
    }
}
