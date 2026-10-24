package org.kasumi321.ushio.phitracker.data.collectible

import org.kasumi321.ushio.phitracker.data.logging.AppLogger

/**
 * One data row of `collection_<lang>.csv`. Row identity is
 * (folder, key, sub_index); the same key may repeat across folders.
 * [content] preserves the literal two-character `\n` sequences from the CSV —
 * the display layer replaces them with real line breaks.
 */
internal data class CollectionCsvRow(
    val folder: String,
    val folderSub: String,
    val cover: String,
    val key: String,
    val subIndex: Int,
    val name: String,
    val date: String,
    val supervisor: String,
    val category: String,
    val content: String,
    val properties: String
)

/**
 * Minimal RFC-4180 reader for the upstream collection CSVs (Python
 * `csv.writer` output): comma separators, `"..."` quoted fields, doubled
 * `""` quotes, commas/newlines inside quoted fields, LF or CRLF record
 * terminators, and a final record without a trailing newline. Quoting is
 * honored only at field start, matching the generator.
 *
 * No external CSV dependency exists in the version catalog by design.
 */
internal object CollectionCsvParser {

    private const val TAG = "CollectibleCsv"
    private const val COLUMN_COUNT = 11

    private val HEADER = listOf(
        "folder", "folder_sub", "cover", "key", "sub_index", "name",
        "date", "supervisor", "category", "content", "properties"
    )

    fun parse(text: String): List<CollectionCsvRow> {
        val records = parseRecords(text)
        if (records.isEmpty()) return emptyList()
        val data = if (records.first() == HEADER) records.drop(1) else records
        return data.mapNotNull { row ->
            if (row.size < COLUMN_COUNT) {
                AppLogger.w(TAG) { "Skipping malformed collection row with ${row.size} columns (expected $COLUMN_COUNT)" }
                return@mapNotNull null
            }
            CollectionCsvRow(
                folder = row[0],
                folderSub = row[1],
                cover = row[2],
                key = row[3],
                subIndex = row[4].trim().toIntOrNull() ?: 0,
                name = row[5],
                date = row[6],
                supervisor = row[7],
                category = row[8],
                content = row[9],
                properties = row.getOrElse(10) { "" }
            )
        }
    }

    /** Raw RFC-4180 record split, without header handling or column mapping. */
    fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotedField = false
        var atFieldStart = true

        fun endField() {
            fields.add(current.toString())
            current.clear()
            atFieldStart = true
        }

        fun endRecord() {
            endField()
            records.add(fields)
            fields = mutableListOf()
        }

        var i = 0
        val length = text.length
        while (i < length) {
            val ch = text[i]
            when {
                inQuotedField -> when {
                    ch == '"' && i + 1 < length && text[i + 1] == '"' -> {
                        current.append('"')
                        i++
                    }
                    ch == '"' -> inQuotedField = false
                    else -> current.append(ch)
                }
                atFieldStart && ch == '"' -> {
                    inQuotedField = true
                    atFieldStart = false
                }
                ch == ',' -> endField()
                ch == '\n' -> endRecord()
                ch == '\r' -> Unit // CRLF: the LF (or end of input) terminates the record
                else -> {
                    current.append(ch)
                    atFieldStart = false
                }
            }
            i++
        }
        if (fields.isNotEmpty() || current.isNotEmpty()) endRecord()
        return records
    }
}
