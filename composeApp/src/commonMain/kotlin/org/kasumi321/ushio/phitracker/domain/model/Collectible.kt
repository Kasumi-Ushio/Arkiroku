package org.kasumi321.ushio.phitracker.domain.model

/**
 * Opaque upstream category enumeration. Values outside the known set map to
 * [UNKNOWN] so newer upstream data never crashes the reader.
 */
enum class CollectibleCategory {
    MAIN, SOUVENIR, BOLD, KEY, NONSENSE, NAZO, UNKNOWN;

    companion object {
        fun fromCsv(value: String): CollectibleCategory =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * One sub-entry of a collectible. A collectible with several `sub_index`
 * values in the CSV becomes one [CollectibleItem] with multiple parts.
 * [content] keeps the literal two-character `\n` sequences from the CSV;
 * the display layer replaces them with real line breaks.
 */
data class CollectiblePart(
    val subIndex: Int,
    val name: String,
    val date: String,
    val supervisor: String,
    val category: CollectibleCategory,
    val content: String,
    val properties: String
)

/**
 * A collectible row group identified by ([chapterFolderKey], [key]). The same
 * [key] may appear in several chapters and stays a distinct item in each.
 * List titles take part 1's name ([displayName]).
 */
data class CollectibleItem(
    val key: String,
    val chapterFolderKey: String,
    val parts: List<CollectiblePart>
) {
    val displayName: String get() = parts.firstOrNull()?.name ?: key
}

/**
 * A display chapter. [folderKey] is the zh_cn folder name and doubles as the
 * structural identity (see [CollectibleCovers]); [displayName]/[displaySub]
 * are localized. [coverSongId] is always resolved, never null — unknown
 * chapters fall back to [CollectibleCovers.DEFAULT_COVER_SONG_ID].
 */
data class CollectibleChapter(
    val folderKey: String,
    val displayName: String,
    val displaySub: String,
    val coverSongId: String
)

/**
 * Hardcoded zh_cn folder -> chapter cover (illustration cleanId) mapping.
 * Every entry was verified against the upstream info.csv by the plan author;
 * chapters missing from the table (future upstream chapters) resolve to
 * [DEFAULT_COVER_SONG_ID]. Lookups never return null and never validate the
 * id against SongDataProvider — the bundled info.csv predates chapter 9.
 */
object CollectibleCovers {
    const val DEFAULT_COVER_SONG_ID = "LeaveAllBehind.rider"

    private val COVERS: Map<String, String> = mapOf(
        "额外章节" to "RubbishSorting.CtymaxvsNceS",
        "冰封世界" to "Dlyrotz.Likey",
        "废土之下" to "光.姜米條",
        "幽蓝边界" to "HumaN.SOTUI",
        "管道迷宫" to "SultanRage.MonstDeath",
        "霓虹灯牌" to "Spasmodic.姜米條颶風元力上人",
        "方舟蜃景" to "Igallta.SeURa",
        "时钟链接" to "Rrharil.TeamGrimoire",
        "凌日潮汐" to "DistortedFate.Sakuzyo",
        "穹顶孤舟" to "WhatdoyouwantmorethanaHappyending.Apo11oHALOprogramft安月名莉子大瀬良あい",
        "忘忧宫" to "MiracleForestVIPMix.Rinthlive",
        "弭刻日" to "Demiurge.Rabpit",
        "盗乐行" to "FreakyUndulationsNobleKnightsofTune.JehezukielKURORAK",
        "无相乡" to "DerRichter.Ωμεγα",
        "极星卫" to "SATELLITE.かめりあ"
    )

    fun coverSongId(folderKey: String): String = COVERS[folderKey] ?: DEFAULT_COVER_SONG_ID
}

/**
 * Bundled CSV language set (upstream ships `collection_<suffix>.csv` for each).
 * ja/ko files are already packed but the UI does not expose them yet.
 */
enum class CollectibleLanguage(val csvSuffix: String) {
    ZH_CN("zh_cn"),
    ZH_TW("zh_tw"),
    EN("en"),
    JA("ja"),
    KO("ko");

    companion object {
        /**
         * Maps the app-language setting value ("system"/"en"/"zh-Hans"/
         * "zh-Hant") to a bundled CSV language. "system" (or blank) is
         * resolved through [systemTag], which must return the current
         * effective locale tag (see PlatformLocale.currentLanguageTag).
         * Anything unrecognized falls back to [EN].
         */
        fun fromAppLanguage(appLanguage: String, systemTag: () -> String): CollectibleLanguage {
            val tag = if (appLanguage.isBlank() || appLanguage == APP_LANGUAGE_SYSTEM) {
                systemTag()
            } else {
                appLanguage
            }
            return fromTag(tag)
        }

        /** Maps a BCP 47-ish language tag to a CSV language. */
        fun fromTag(tag: String): CollectibleLanguage {
            val normalized = tag.lowercase()
            return when {
                normalized.startsWith("zh-hant") ||
                    normalized.startsWith("zh-hk") ||
                    normalized.startsWith("zh-mo") -> ZH_TW
                normalized.startsWith("zh") -> ZH_CN
                else -> EN
            }
        }

        private const val APP_LANGUAGE_SYSTEM = "system"
    }
}
