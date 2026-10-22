package org.kasumi321.ushio.phitracker.data

import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that verify resource text decoding behaves correctly with non-ASCII content.
 *
 * These tests use a FakeTextAssetReader that returns well-formed UTF-8 strings
 * directly (simulating what a correctly-implemented platform reader should produce).
 * They guard against regressions where:
 * - Tips text contains mojibake
 * - Song metadata lookups fail for non-ASCII song IDs
 * - B30 calculation drops records with non-ASCII song IDs
 */
class ResourceDecodingTest {

    private val fakeReader = FakeTextAssetReader(fakeAssets)

    @Test
    fun chineseAndJapaneseTipsSurviveWithoutMojibake() {
        val provider = TipsProvider(fakeReader)
        val seen = mutableSetOf<String>()
        repeat(30) { seen.add(provider.getRandomTip()) }

        for (tip in seen) {
            assertFalse(tip.contains('\ufffd'), "Tip contains replacement character U+FFFD: $tip")
            assertFalse(tip.contains('å'), "Tip contains mojibake marker 'å': $tip")
            assertFalse(tip.contains("ä¸"), "Tip contains mojibake marker 'ä¸': $tip")
        }

        assertTrue(seen.any { it.contains("欢迎") }, "Expected Chinese '欢迎' in tips, got: $seen")
        assertTrue(seen.any { it.contains("もぺもぺ") }, "Expected Japanese 'もぺもぺ' in tips, got: $seen")
    }

    @Test
    fun songDataProviderReturnsFixtureSongCount() {
        val provider = SongDataProvider(fakeReader)
        assertEquals(3, provider.getSongs().size)
    }

    @Test
    fun nonAsciiSongIdsPresentInDifficultyAndNameMaps() {
        val provider = SongDataProvider(fakeReader)
        val difficulties = provider.getDifficultyMap()
        val names = provider.getSongNameMap()

        for (songId in listOf(
            "光.姜米條.0",
            "もぺもぺ.LeaF.0",
            "混乱Confusion.OnlyMyBlackScore.0"
        )) {
            assertTrue(difficulties.containsKey(songId), "Difficulty map missing non-ASCII songId: $songId")
            assertTrue(names.containsKey(songId), "Name map missing non-ASCII songId: $songId")
        }
    }

    @Test
    fun rksCalculatorKeepsNonAsciiRecords() {
        val provider = SongDataProvider(fakeReader)
        val difficulties = provider.getDifficultyMap()
        val names = provider.getSongNameMap()

        val records = mapOf(
            "光.姜米條.0" to SongRecord(
                songId = "光.姜米條.0",
                levels = mapOf(Difficulty.IN to LevelRecord(1_000_000, 100f, true))
            ),
            "もぺもぺ.LeaF.0" to SongRecord(
                songId = "もぺもぺ.LeaF.0",
                levels = mapOf(Difficulty.IN to LevelRecord(950_000, 95f, true))
            ),
            "混乱Confusion.OnlyMyBlackScore.0" to SongRecord(
                songId = "混乱Confusion.OnlyMyBlackScore.0",
                levels = mapOf(Difficulty.IN to LevelRecord(900_000, 90f, false))
            )
        )

        val (b30, allRecords) = RksCalculator.getB30AndAllRecords(records, difficulties, names)

        val b30Ids = b30.map { it.songId }.toSet()
        for (songId in listOf(
            "光.姜米條.0",
            "もぺもぺ.LeaF.0",
            "混乱Confusion.OnlyMyBlackScore.0"
        )) {
            assertTrue(b30Ids.contains(songId), "B30 missing non-ASCII songId: $songId")
        }
        assertEquals(3, allRecords.size, "allRecords should contain all 3 song records")
    }

    private companion object {
        val fakeAssets: Map<String, String> = mapOf(
            "tips.txt" to """
                |Tip: 欢迎使用 Arkiroku！
                |Tip: もぺもぺで遊ぼう！
                |Tip: 混乱Confusionは難しい
            """.trimMargin(),
            "info.csv" to (
                "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n" +
                    "光.姜米條\t光\t姜米條\tIllustrator1\tC1\tC2\tC3\tC4\t1.0\t2.0\t3.0\t4.0\n" +
                    "もぺもぺ.LeaF\tもぺもぺ\tLeaF\tIllustrator2\tC1\tC2\tC3\t\t2.0\t3.0\t4.0\t\n" +
                    "混乱Confusion.OnlyMyBlackScore\t混乱Confusion\tOnlyMyBlackScore\tIllustrator3\tC1\tC2\tC3\t\t3.0\t4.0\t5.0\t"
            ),
            "infolist.json" to """{"光.姜米條":{"bpm":"180","length":"120","chapter":"C1"},"もぺもぺ.LeaF":{"bpm":"200","length":"130","chapter":"C2"},"混乱Confusion.OnlyMyBlackScore":{"bpm":"150","length":"140","chapter":"C3"}}""",
            "notesInfo.json" to """{"光.姜米條":{"EZ":{"t":[1,2,3,4]},"HD":{"t":[5,6,7,8]},"IN":{"t":[9,10,11,12]},"AT":{"t":[13,14,15,16]}},"もぺもぺ.LeaF":{"EZ":{"t":[1,1,1,1]},"HD":{"t":[2,2,2,2]},"IN":{"t":[3,3,3,3]}},"混乱Confusion.OnlyMyBlackScore":{"EZ":{"t":[4,4,4,4]},"HD":{"t":[5,5,5,5]},"IN":{"t":[6,6,6,6]}}}"""
        )
    }

    /**
     * A [TextAssetReader] that serves pre-defined strings.
     * Simulates the behaviour of a correctly-implemented platform reader
     * that always decodes resources as valid UTF-8.
     */
    private class FakeTextAssetReader(
        private val assets: Map<String, String>
    ) : TextAssetReader {
        override fun readText(name: String): String {
            return assets[name] ?: error("Test asset not found: $name")
        }
    }
}
