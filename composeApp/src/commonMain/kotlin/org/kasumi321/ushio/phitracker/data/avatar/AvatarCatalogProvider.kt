package org.kasumi321.ushio.phitracker.data.avatar

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.createFileThenAssetReader
import org.kasumi321.ushio.phitracker.data.platform.createTextAssetReader
import org.kasumi321.ushio.phitracker.domain.model.GameAvatar

/**
 * Reads the effective avatar catalog, preferring the transactionally updated
 * file over the bundled copy. The parsed list is immutable and cached until
 * the updater (or a caller repairing local data) invalidates it.
 */
class AvatarCatalogProvider(
    assetReader: TextAssetReader = createTextAssetReader(),
    paths: PlatformPaths? = null
) {
    private val reader = paths?.let {
        createFileThenAssetReader(assetReader, it, subdirectory = DATA_DIRECTORY)
    } ?: assetReader

    private var cachedAvatars: List<GameAvatar>? = null
    private var cachedById: Map<String, GameAvatar>? = null
    private val invalidations = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val cacheInvalidations = invalidations.asSharedFlow()

    fun getAvatars(): List<GameAvatar> = cachedAvatars ?: parseCatalog(currentCatalogText()).also {
        cachedAvatars = it
        cachedById = it.associateBy(GameAvatar::id)
    }

    fun findById(id: String): GameAvatar? {
        getAvatars()
        return cachedById?.get(id)
    }

    /** The exact effective TSV used for upstream change detection. */
    fun currentCatalogText(): String = reader.readText(CATALOG_FILE)

    fun invalidateCache() {
        cachedAvatars = null
        cachedById = null
        invalidations.tryEmit(Unit)
    }

    internal fun validateCatalogText(text: String) {
        parseCatalog(text)
    }

    private fun parseCatalog(text: String): List<GameAvatar> {
        require(text.isNotEmpty()) { "Avatar catalog is empty" }
        val content = if (text.startsWith('\uFEFF')) text.substring(1) else text
        require(content.isNotEmpty()) { "Avatar catalog is empty" }

        // A single terminal line ending is conventional and does not form a
        // record. Every other line, including blank lines, is strict TSV.
        val lines = content.split('\n').let { parts ->
            if (parts.lastOrNull()?.isEmpty() == true) parts.dropLast(1) else parts
        }
        require(lines.isNotEmpty()) { "Avatar catalog is empty" }

        val avatars = ArrayList<GameAvatar>(lines.size)
        val ids = HashSet<String>(lines.size)
        val keys = HashSet<String>(lines.size)
        lines.forEachIndexed { index, rawLine ->
            val line = if (rawLine.endsWith('\r')) rawLine.dropLast(1) else rawLine
            require(line.isNotEmpty()) { "Avatar catalog line ${index + 1} is blank" }
            val columns = line.split('\t')
            require(columns.size == 2) { "Avatar catalog line ${index + 1} must have two columns" }
            val id = columns[0]
            val key = columns[1]
            require(id.isNotBlank()) { "Avatar catalog line ${index + 1} has an empty id" }
            require(key.isNotBlank()) { "Avatar catalog line ${index + 1} has an empty resource key" }
            require(isSafeResourceKey(key)) {
                "Avatar catalog line ${index + 1} has an unsafe resource key"
            }
            require(ids.add(id)) { "Duplicate avatar id in catalog" }
            require(keys.add(key)) { "Duplicate avatar resource key in catalog" }
            avatars += GameAvatar(id = id, resourceKey = key)
        }
        require(avatars.isNotEmpty()) { "Avatar catalog is empty" }
        return avatars
    }

    private fun isSafeResourceKey(key: String): Boolean =
        key != "." && key != ".." &&
            key.indexOf('/') < 0 && key.indexOf('\\') < 0 && key.indexOf('\u0000') < 0

    companion object {
        const val CATALOG_FILE = "avatar_catalog.tsv"
        const val DATA_DIRECTORY = "avatar_data"
    }
}
