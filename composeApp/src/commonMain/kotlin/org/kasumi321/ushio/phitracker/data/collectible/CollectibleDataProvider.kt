package org.kasumi321.ushio.phitracker.data.collectible

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.createFileThenAssetReader
import org.kasumi321.ushio.phitracker.data.platform.createTextAssetReader
import org.kasumi321.ushio.phitracker.domain.model.CollectibleCategory
import org.kasumi321.ushio.phitracker.domain.model.CollectibleChapter
import org.kasumi321.ushio.phitracker.domain.model.CollectibleCovers
import org.kasumi321.ushio.phitracker.domain.model.CollectibleItem
import org.kasumi321.ushio.phitracker.domain.model.CollectibleLanguage
import org.kasumi321.ushio.phitracker.domain.model.CollectiblePart

/**
 * Reads the bundled/downloaded `collection_<lang>.csv` set and builds the
 * collectible index. zh_cn is the structural base: chapters and items are
 * keyed by the zh_cn folder name, and other languages align BY ROW INDEX
 * with per-row key+sub_index validation — a mismatch (or a missing localized
 * CSV) falls back to the zh_cn row and logs a warning, never crashing.
 * Localized fields that are empty fall back to zh_cn per field.
 *
 * Downloaded copies in `filesDir/collectible_data/` win over the bundled
 * assets; snapshots are cached per language and rebuilt after
 * [invalidateCache] (which the updater calls only after a fully committed
 * download, mirroring SongDataProvider).
 */
class CollectibleDataProvider(
    private val assetReader: TextAssetReader = createTextAssetReader(),
    private val paths: PlatformPaths? = null
) {
    private val snapshots = mutableMapOf<CollectibleLanguage, CollectibleSnapshot>()
    private val invalidations = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val cacheInvalidations = invalidations.asSharedFlow()

    private val reader: TextAssetReader =
        paths?.let { createFileThenAssetReader(assetReader, it, subdirectory = "collectible_data") }
            ?: assetReader

    fun getChapters(language: CollectibleLanguage): List<CollectibleChapter> =
        snapshot(language).chapters

    fun getItems(language: CollectibleLanguage): List<CollectibleItem> =
        snapshot(language).items

    fun getItem(folderKey: String, key: String, language: CollectibleLanguage): CollectibleItem? =
        snapshot(language).itemsById[folderKey to key]

    fun invalidateCache() {
        snapshots.clear()
        invalidations.tryEmit(Unit)
    }

    /** The currently effective collection_version.txt text (downloaded copy wins over bundled). */
    fun currentVersionText(): String =
        runCatching { reader.readText(VERSION_FILE).trim() }.getOrDefault("")

    private fun snapshot(language: CollectibleLanguage): CollectibleSnapshot =
        snapshots.getOrPut(language) { buildSnapshot(language) }

    private fun buildSnapshot(language: CollectibleLanguage): CollectibleSnapshot {
        val base = CollectionCsvParser.parse(reader.readText(csvName(CollectibleLanguage.ZH_CN)))
        require(base.isNotEmpty()) { "Bundled zh_cn collection CSV is empty or missing" }

        val overlay = if (language == CollectibleLanguage.ZH_CN) {
            emptyList()
        } else {
            runCatching { CollectionCsvParser.parse(reader.readText(csvName(language))) }
                .getOrElse { error ->
                    AppLogger.w(TAG, error) { "Failed to read ${csvName(language)}, falling back to zh_cn" }
                    emptyList()
                }
        }

        val rows = base.mapIndexed { index, zh -> mergeRow(index, zh, overlay.getOrNull(index), language) }

        val chapters = rows.groupBy { it.folderKey }.map { (folderKey, chapterRows) ->
            CollectibleChapter(
                folderKey = folderKey,
                displayName = chapterRows.first().folderDisplay,
                displaySub = chapterRows.first().folderSubDisplay,
                coverSongId = CollectibleCovers.coverSongId(folderKey)
            )
        }

        val items = rows.groupBy { it.folderKey to it.key }.map { (id, itemRows) ->
            CollectibleItem(
                key = id.second,
                chapterFolderKey = id.first,
                parts = itemRows.sortedBy { it.subIndex }.map { row ->
                    CollectiblePart(
                        subIndex = row.subIndex,
                        name = row.name,
                        date = row.date,
                        supervisor = row.supervisor,
                        category = row.category,
                        content = row.content,
                        properties = row.properties
                    )
                }
            )
        }

        return CollectibleSnapshot(
            chapters = chapters,
            items = items,
            itemsById = items.associateBy { it.chapterFolderKey to it.key }
        )
    }

    /**
     * Aligns one localized row with its zh_cn base row. The localized row is
     * only trusted when its (key, sub_index) matches; then every still-empty
     * localized field falls back to zh_cn individually.
     */
    private fun mergeRow(
        index: Int,
        zh: CollectionCsvRow,
        localized: CollectionCsvRow?,
        language: CollectibleLanguage
    ): MergedRow {
        val aligned = localized != null &&
            localized.key == zh.key && localized.subIndex == zh.subIndex
        if (localized != null && !aligned) {
            AppLogger.w(TAG) {
                "Row $index of ${csvName(language)} (key=${localized.key}, sub=${localized.subIndex}) " +
                    "does not match zh_cn (key=${zh.key}, sub=${zh.subIndex}); falling back to zh_cn"
            }
        }
        val src = if (aligned) localized else zh
        fun field(localizedValue: String, zhValue: String) = localizedValue.ifEmpty { zhValue }
        val category = CollectibleCategory.fromCsv(src.category)
            .takeIf { it != CollectibleCategory.UNKNOWN }
            ?: CollectibleCategory.fromCsv(zh.category)
        return MergedRow(
            folderKey = zh.folder,
            folderDisplay = field(src.folder, zh.folder),
            folderSubDisplay = field(src.folderSub, zh.folderSub),
            key = zh.key,
            subIndex = zh.subIndex,
            name = field(src.name, zh.name),
            date = field(src.date, zh.date),
            supervisor = field(src.supervisor, zh.supervisor),
            category = category,
            content = field(src.content, zh.content),
            properties = field(src.properties, zh.properties)
        )
    }

    private fun csvName(language: CollectibleLanguage): String = "collection_${language.csvSuffix}.csv"

    private data class MergedRow(
        val folderKey: String,
        val folderDisplay: String,
        val folderSubDisplay: String,
        val key: String,
        val subIndex: Int,
        val name: String,
        val date: String,
        val supervisor: String,
        val category: CollectibleCategory,
        val content: String,
        val properties: String
    )

    private data class CollectibleSnapshot(
        val chapters: List<CollectibleChapter>,
        val items: List<CollectibleItem>,
        val itemsById: Map<Pair<String, String>, CollectibleItem>
    )

    private companion object {
        const val TAG = "CollectibleData"
        const val VERSION_FILE = "collection_version.txt"
    }
}
