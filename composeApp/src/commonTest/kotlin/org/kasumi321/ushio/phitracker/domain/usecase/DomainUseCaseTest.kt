package org.kasumi321.ushio.phitracker.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.B30ChartTagBatch
import org.kasumi321.ushio.phitracker.domain.model.ChartTagSongData
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainUseCaseTest {
    @Test
    fun singleRksMatchesBeta1Thresholds() {
        assertEquals(0f, RksCalculator.calculateSingleRks(69.99f, 15f))
        assertEquals(15f, RksCalculator.calculateSingleRks(100f, 15f))

        val expected = ((95f - 55f) / 45f) * ((95f - 55f) / 45f) * 15f
        assertClose(expected, RksCalculator.calculateSingleRks(95f, 15f))
    }

    @Test
    fun targetAccSnapsToCeilingForBinarySearchResidue() {
        // A target RKS a hair below the chart constant (as produced by the
        // player-RKS binary search) must invert to exactly 100% so the forward
        // RKS prints 15.2000 instead of 15.1999 (issue #13).
        val snapped = RksCalculator.calculateTargetAcc(15.19995f, 15.2f)
        assertEquals(100f, snapped)
        assertEquals(15.2f, RksCalculator.calculateSingleRks(snapped!!, 15.2f))

        // A legitimate two-decimal target just below the constant keeps its
        // real accuracy instead of being swallowed by the snap.
        val genuine = RksCalculator.calculateTargetAcc(15.19f, 15.2f)
        assertTrue(genuine != null && genuine < 100f && genuine > 99.9f)

        assertEquals(null, RksCalculator.calculateTargetAcc(17f, 15f))
    }

    @Test
    fun b30ListKeepsPhiFirstThenTopRecords() {
        val records = mapOf(
            "song-a" to SongRecord(
                songId = "song-a",
                levels = mapOf(Difficulty.IN to LevelRecord(1_000_000, 100f, true))
            ),
            "song-b" to SongRecord(
                songId = "song-b",
                levels = mapOf(Difficulty.IN to LevelRecord(990_000, 99f, true))
            ),
            "song-c" to SongRecord(
                songId = "song-c",
                levels = mapOf(Difficulty.IN to LevelRecord(1_000_000, 100f, true))
            )
        )
        val difficulties = mapOf(
            "song-a" to mapOf(Difficulty.IN to 15f),
            "song-b" to mapOf(Difficulty.IN to 16f),
            "song-c" to mapOf(Difficulty.IN to 14f)
        )
        val names = mapOf("song-a" to "A", "song-b" to "B", "song-c" to "C")

        val (b30, allRecords) = RksCalculator.getB30AndAllRecords(records, difficulties, names)

        assertEquals(3, allRecords.size)
        assertEquals(listOf("song-a", "song-c"), b30.take(2).map { it.songId })
        assertTrue(b30.take(2).all { it.isPhi })
        assertTrue(b30.drop(2).all { !it.isPhi })
    }

    @Test
    fun searchMatchesNameIdAndComposerThenSortsByName() {
        val songs = mapOf(
            "alpha.0" to SongInfo("alpha.0", "Alpha", "Composer Z", "", emptyMap()),
            "beta.0" to SongInfo("beta.0", "Beta", "Alice", "", emptyMap()),
            "gamma.0" to SongInfo("gamma.0", "Gamma", "Composer Z", "", emptyMap())
        )

        assertEquals(listOf("Alpha", "Gamma"), SearchSongUseCase()("composer z", songs).map { it.name })
        assertEquals(listOf("Beta"), SearchSongUseCase()("beta.0", songs).map { it.name })
        assertEquals(3, SearchSongUseCase()("", songs).size)
    }

    @Test
    fun searchMatchesNicknamesCaseInsensitively() {
        val songs = mapOf(
            "alpha.0" to SongInfo(
                "alpha.0", "Alpha", "C", "", emptyMap(),
                nicknames = listOf("FirstNick", "second-nick")
            ),
            "beta.0" to SongInfo("beta.0", "Beta", "C", "", emptyMap())
        )

        assertEquals(listOf("Alpha"), SearchSongUseCase()("firstnick", songs).map { it.name })
        assertEquals(listOf("Alpha"), SearchSongUseCase()("SECOND-NICK", songs).map { it.name })
        assertEquals(listOf("Beta"), SearchSongUseCase()("beta", songs).map { it.name })
    }

    @Test
    fun searchWildcardMatchesNicknames() {
        val songs = mapOf(
            "alpha.0" to SongInfo(
                "alpha.0", "Alpha", "C", "", emptyMap(),
                nicknames = listOf("NickSuffix")
            ),
            "beta.0" to SongInfo("beta.0", "Beta", "C", "", emptyMap())
        )

        assertEquals(listOf("Alpha"), SearchSongUseCase()("Nick*", songs).map { it.name })
        assertEquals(emptyList(), SearchSongUseCase()("Gamma*", songs).map { it.name })
    }

    @Test
    fun searchWildcardAsteriskMatchesNonSpaceChars() {
        val songs = mapOf(
            "alpha.0" to SongInfo("alpha.0", "Alpha-Beta", "C", "", emptyMap()),
            "beta.0" to SongInfo("beta.0", "Beta", "C", "", emptyMap()),
            "gamma.0" to SongInfo("gamma.0", "Alpha Gamma Delta", "C", "", emptyMap()),
            "delta.0" to SongInfo("delta.0", "AlphaBeta", "C", "", emptyMap())
        )

        // Alpha* -> regex Alpha\S+ (Alpha followed by >=1 non-whitespace)
        // "Alpha Gamma Delta" fails because space after Alpha is whitespace, \S+ requires non-whitespace
        val result = SearchSongUseCase()("Alpha*", songs)
        assertEquals(listOf("Alpha-Beta", "AlphaBeta"), result.map { it.name })
    }

    @Test
    fun searchWildcardQuestionMatchesSingleNonSpace() {
        val songs = mapOf(
            "a.0" to SongInfo("a.0", "AB", "C", "", emptyMap()),
            "b.0" to SongInfo("b.0", "AXB", "C", "", emptyMap()),
            "c.0" to SongInfo("c.0", "A B", "C", "", emptyMap())
        )

        val result = SearchSongUseCase()("A?B", songs)
        assertEquals(listOf("AXB"), result.map { it.name })
    }

    @Test
    fun searchWildcardSpaceMatchesWhitespace() {
        val songs = mapOf(
            "a.0" to SongInfo("a.0", "Alpha Beta", "C", "", emptyMap()),
            "b.0" to SongInfo("b.0", "AlphaBeta", "C", "", emptyMap()),
            "c.0" to SongInfo("c.0", "Alpha  Beta", "C", "", emptyMap())
        )

        // "Alpha Beta" has no wildcards (*/?), so it's a contains match.
        // Lowercase contains "alpha beta" matches "Alpha Beta" but not "Alpha  Beta" (double space).
        val result = SearchSongUseCase()("Alpha Beta", songs)
        assertEquals(listOf("Alpha Beta"), result.map { it.name })
    }

    @Test
    fun searchBlankQueryReturnsAllSongs() {
        val songs = mapOf(
            "a.0" to SongInfo("a.0", "A", "", "", emptyMap()),
            "b.0" to SongInfo("b.0", "B", "", "", emptyMap())
        )
        assertEquals(2, SearchSongUseCase()("", songs).size)
        assertEquals(2, SearchSongUseCase()("   ", songs).size)
    }

    @Test
    fun searchConsecutiveAsterisksReturnsAllSongsImmediately() {
        val songs = mapOf(
            "alpha.0" to SongInfo("alpha.0", "Alpha", "C", "", emptyMap()),
            "beta.0" to SongInfo("beta.0", "Beta", "C", "", emptyMap()),
            "gamma.0" to SongInfo("gamma.0", "Gamma", "C", "", emptyMap())
        )
        assertEquals(3, SearchSongUseCase()("**", songs).size)
        assertEquals(3, SearchSongUseCase()("***", songs).size)
    }

    @Test
    fun searchConsecutiveAsterisksWithSpacesStillUsesRegex() {
        val songs = mapOf(
            "alpha.0" to SongInfo("alpha.0", "Alpha Beta", "C", "", emptyMap()),
            "beta.0" to SongInfo("beta.0", "Beta", "C", "", emptyMap())
        )
        val result = SearchSongUseCase()("* *", songs)
        assertEquals(listOf("Alpha Beta"), result.map { it.name })
    }

    @Test
    fun b57ReturnsUpTo57Records() {
        val records = (0 until 60).associate { i ->
            val songId = "song-$i"
            songId to SongRecord(
                songId = songId,
                levels = mapOf(Difficulty.IN to LevelRecord(1_000_000 - i, (99.9f - i * 0.1f).coerceAtLeast(70f), false))
            )
        }
        val difficulties = (0 until 60).associate { i ->
            "song-$i" to mapOf(Difficulty.IN to (15f - i * 0.1f).coerceAtLeast(1f))
        }
        val names = (0 until 60).associate { i -> "song-$i" to "Song $i" }

        val (b30, allRecords) = RksCalculator.getB30AndAllRecords(records, difficulties, names)
        assertEquals(60, allRecords.size)
        assertEquals(57, b30.size)
    }

    @Test
    fun suggestItemPopulatesIsFullCombo() {
        val b30 = mutableListOf(
            createBestRecord("best.0", Difficulty.IN, 15f, 100f, 15f),
            createBestRecord("best.1", Difficulty.HD, 14f, 99.5f, 14f)
        )
        // Need 20 B30 records minimum for beta5 threshold (index 19)
        repeat(18) { i ->
            b30.add(createBestRecord("pad-$i", Difficulty.IN, 10f, 92f, 12f))
        }

        val records = mapOf(
            "song.0" to SongRecord(
                songId = "song.0",
                levels = mapOf(Difficulty.IN to LevelRecord(950_000, 95f, true))
            ),
            "song.1" to SongRecord(
                songId = "song.1",
                levels = mapOf(Difficulty.IN to LevelRecord(900_000, 90f, false))
            )
        )
        val difficulties = mapOf(
            "song.0" to mapOf(Difficulty.IN to 15f),
            "song.1" to mapOf(Difficulty.IN to 15f)
        )
        val names = mapOf("song.0" to "Song 0", "song.1" to "Song 1")

        val useCase = GetSuggestUseCase()
        val results = useCase(b30, records, difficulties, names)

        assertEquals(2, results.size)
        assertEquals(true, results.first { it.songId == "song.0" }.isFullCombo)
        assertEquals(950_000, results.first { it.songId == "song.0" }.currentScore)
        assertEquals(false, results.first { it.songId == "song.1" }.isFullCombo)
    }

    @Test
    fun levelBoundsFloorAndCeilChartConstants() {
        val songs = listOf(
            SongInfo("a.0", "A", "", "", mapOf(Difficulty.IN to 7.4f)),
            SongInfo("b.0", "B", "", "", mapOf(Difficulty.EZ to 1.2f, Difficulty.AT to 17.6f))
        )

        val bounds = GetSongLevelBoundsUseCase()(songs)
        assertEquals(1, bounds?.min, "min bound floors the smallest constant")
        assertEquals(18, bounds?.max, "max bound ceils the largest constant (17.6)")
        assertEquals(
            null,
            GetSongLevelBoundsUseCase()(emptyList()),
            "No chart constants → no bounds"
        )

        val degenerate = GetSongLevelBoundsUseCase()(
            listOf(SongInfo("c.0", "C", "", "", mapOf(Difficulty.IN to 15f)))
        )
        assertEquals(15, degenerate?.min)
        assertEquals(15, degenerate?.max)
    }

    @Test
    fun maxDisplayRksAveragesTop3AndTop27Constants() {
        // 30 charts at 15.0 → phi3 and B27 are all 15.0 → ceiling is exactly 15
        val flat = List(30) { 15f }
        assertClose(15f, RksCalculator.calculateMaxDisplayRks(flat))

        // Uneven catalog: phi slots double-count the top constants
        val constants = listOf(16f, 15f, 14f) + List(27) { 10f }
        val expected = ((16f + 15f + 14f) + (16f + 15f + 14f + 24 * 10f)) / 30f
        assertClose(expected, RksCalculator.calculateMaxDisplayRks(constants))
    }

    @Test
    fun maxDisplayRksHandlesSmallAndEmptyCatalogs() {
        assertEquals(0f, RksCalculator.calculateMaxDisplayRks(emptyList()))

        // Fewer than 30 charts: the slots just take what exists
        val tiny = listOf(12f, 10f)
        val expected = ((12f + 10f) + (12f + 10f)) / 30f
        assertClose(expected, RksCalculator.calculateMaxDisplayRks(tiny))
    }

    @Test
    fun suggestTargetCapTracksMaxChartConstant() {
        val useCase = GetSuggestUseCase()
        val currentB30 = (0 until 20).map { i ->
            BestRecord("b-$i", "B $i", Difficulty.IN, 900_000, 90f, false, 10f, 8f)
        }
        // Widest constant 17.6 → cap 18f: 17.5 was rejected by the old
        // hardcoded 17f limit, while 18.5 is unachievable in this catalog.
        val difficulties = mapOf("candidate" to mapOf(Difficulty.IN to 17.6f))
        val names = mapOf("candidate" to "Candidate")

        assertTrue(
            useCase(
                currentB30 = currentB30,
                records = emptyMap(),
                difficulties = difficulties,
                songNames = names,
                targetMode = SuggestTargetMode.SingleChartRks,
                targetRks = 17.5f
            ).isNotEmpty(),
            "A target within the widest chart constant must be accepted"
        )
        assertTrue(
            useCase(
                currentB30 = currentB30,
                records = emptyMap(),
                difficulties = difficulties,
                songNames = names,
                targetMode = SuggestTargetMode.SingleChartRks,
                targetRks = 18.5f
            ).isEmpty(),
            "A target above the widest chart constant must be rejected"
        )
    }

    @Test
    fun suggestSingleChartTargetUsesProvidedRks() {
        val useCase = GetSuggestUseCase()
        val currentB30 = (0 until 20).map { i ->
            BestRecord("b-$i", "B $i", Difficulty.IN, 900_000, 90f, false, 10f, 8f)
        }
        val records = mapOf(
            "candidate" to SongRecord(
                songId = "candidate",
                levels = mapOf(Difficulty.IN to LevelRecord(900_000, 90f, false))
            )
        )
        val difficulties = mapOf("candidate" to mapOf(Difficulty.IN to 16f))
        val names = mapOf("candidate" to "Candidate")

        val result = useCase(
            currentB30 = currentB30,
            records = records,
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.SingleChartRks,
            targetRks = 15f
        )

        assertEquals(1, result.size)
        assertClose(15f, result.single().potentialRks, tolerance = 0.001f)
        assertTrue(result.single().targetAcc > 98f)
    }

    @Test
    fun suggestItemCarriesDisplayRksImpact() {
        // Candidate at acc 90 cc 16: current single RKS ≈ 9.679. Pushing it to
        // single RKS 15 lifts the pool contribution by 15 - 9.679.
        val useCase = GetSuggestUseCase()
        val currentB30 = (0 until 20).map { i ->
            BestRecord("b-$i", "B $i", Difficulty.IN, 900_000, 90f, false, 10f, 8f)
        }
        val records = mapOf(
            "candidate" to SongRecord(
                songId = "candidate",
                levels = mapOf(Difficulty.IN to LevelRecord(900_000, 90f, false))
            )
        )
        val difficulties = mapOf("candidate" to mapOf(Difficulty.IN to 16f))
        val names = mapOf("candidate" to "Candidate")

        val item = useCase(
            currentB30 = currentB30,
            records = records,
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.SingleChartRks,
            targetRks = 15f
        ).single()

        assertClose(15f / 30f, item.newDisplayRks, tolerance = 0.001f)
        assertClose((15f - 9.679f) / 30f, item.deltaRks, tolerance = 0.001f)
    }

    @Test
    fun suggestItemImpactCountsApCandidateInPhiAndB27Slots() {
        // 26 charts at acc 95 cc 10 (single RKS ≈ 7.901 each) plus a candidate
        // at acc 90 cc 16 (≈ 9.679). Pushing the candidate to AP adds it to
        // BOTH the phi3 and the B27 slots: contribution moves from
        // 26 * 7.901 + 9.679 ≈ 215.111 to 16 + (26 * 7.901 + 16) ≈ 237.432.
        val useCase = GetSuggestUseCase()
        val currentB30 = (0 until 20).map { i ->
            BestRecord("b-$i", "B $i", Difficulty.IN, 900_000, 90f, false, 10f, 8f)
        }
        val records = mutableMapOf<String, SongRecord>()
        val difficulties = mutableMapOf<String, Map<Difficulty, Float>>()
        val names = mutableMapOf<String, String>()
        repeat(26) { i ->
            val songId = "base-$i"
            records[songId] = SongRecord(songId, mapOf(Difficulty.IN to LevelRecord(900_000, 95f, false)))
            difficulties[songId] = mapOf(Difficulty.IN to 10f)
            names[songId] = "Base $i"
        }
        records["candidate"] = SongRecord(
            "candidate",
            mapOf(Difficulty.IN to LevelRecord(900_000, 90f, false))
        )
        difficulties["candidate"] = mapOf(Difficulty.IN to 16f)
        names["candidate"] = "Candidate"

        val result = useCase(
            currentB30 = currentB30,
            records = records,
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.PlayerDisplayRks,
            targetRks = 7.8f
        )

        val candidate = result.single { it.songId == "candidate" }
        assertClose(0.744f, candidate.deltaRks, tolerance = 0.001f)
        assertClose(7.914f, candidate.newDisplayRks, tolerance = 0.001f)
    }

    @Test
    fun suggestItemBelowB30ThresholdHasZeroImpact() {
        // 30 charts at acc 99 cc 10 (single RKS ≈ 9.560) fill all 27 best
        // slots; a candidate pushed only to single RKS 5 cannot enter the pool.
        val useCase = GetSuggestUseCase()
        val records = (0 until 30).associate { i ->
            "base-$i" to SongRecord("base-$i", mapOf(Difficulty.IN to LevelRecord(990_000, 99f, false)))
        }
        val difficulties = (0 until 30).associate { i ->
            "base-$i" to mapOf(Difficulty.IN to 10f)
        } + mapOf("candidate" to mapOf(Difficulty.IN to 16f))
        val names = difficulties.keys.associateWith { it }

        val item = useCase(
            currentB30 = emptyList(),
            records = records,
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.SingleChartRks,
            targetRks = 5f
        ).single { it.songId == "candidate" }

        assertClose(0f, item.deltaRks, tolerance = 0.0001f)
        assertClose(9.5605f * 27f / 30f, item.newDisplayRks, tolerance = 0.001f)
    }

    @Test
    fun suggestPlayerTargetKeepsPhiAndB27AsSeparateContributionSlots() {
        val useCase = GetSuggestUseCase()
        val currentB30 = (0 until 20).map { i ->
            BestRecord("b-$i", "B $i", Difficulty.IN, 900_000, 90f, false, 10f, 8f)
        }
        val records = mutableMapOf<String, SongRecord>()
        val difficulties = mutableMapOf<String, Map<Difficulty, Float>>()
        val names = mutableMapOf<String, String>()
        repeat(26) { i ->
            val songId = "base-$i"
            records[songId] = SongRecord(songId, mapOf(Difficulty.IN to LevelRecord(900_000, 95f, false)))
            difficulties[songId] = mapOf(Difficulty.IN to 10f)
            names[songId] = "Base $i"
        }
        records["candidate"] = SongRecord(
            "candidate",
            mapOf(Difficulty.IN to LevelRecord(900_000, 90f, false))
        )
        difficulties["candidate"] = mapOf(Difficulty.IN to 16f)
        names["candidate"] = "Candidate"

        val result = useCase(
            currentB30 = currentB30,
            records = records,
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.PlayerDisplayRks,
            targetRks = 7.8f
        )

        val candidate = result.single { it.songId == "candidate" }
        assertClose(16f, candidate.potentialRks, tolerance = 0.001f)
        assertClose(100f, candidate.targetAcc, tolerance = 0.001f)
    }

    @Test
    fun suggestPlayerTargetReturnsAllHelpfulCharts() {
        val useCase = GetSuggestUseCase()
        val currentB30 = (0 until 20).map { i ->
            BestRecord("b-$i", "B $i", Difficulty.IN, 900_000, 90f, false, 10f, 0f)
        }
        val difficulties = (0 until 40).associate { i ->
            "candidate-$i" to mapOf(Difficulty.IN to 17f)
        }
        val names = (0 until 40).associate { i -> "candidate-$i" to "Candidate $i" }

        val result = useCase(
            currentB30 = currentB30,
            records = emptyMap(),
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.PlayerDisplayRks,
            targetRks = 0.5f,
            limit = 30
        )

        assertEquals(40, result.size)
    }

    @Test
    fun suggestPlayerTargetIncludesChartsThatHelpButCannotSoloReachTarget() {
        val useCase = GetSuggestUseCase()
        // 30 AP'd charts at chart-constant 10 → displayed RKS 10, contribution 300.
        val records = mutableMapOf<String, SongRecord>()
        val difficulties = mutableMapOf<String, Map<Difficulty, Float>>()
        val names = mutableMapOf<String, String>()
        repeat(30) { i ->
            val id = "base-$i"
            records[id] = SongRecord(id, mapOf(Difficulty.IN to LevelRecord(1_000_000, 100f, true)))
            difficulties[id] = mapOf(Difficulty.IN to 10f)
            names[id] = "Base $i"
        }
        // Unplayed chart-constant 12 chart: AP'ing it lifts the contribution to 304 (a
        // real step toward the target) but cannot by itself reach 10.4 * 30 = 312. The
        // old "must close the whole gap alone" gate hid it; it must now be suggested.
        difficulties["candidate"] = mapOf(Difficulty.IN to 12f)
        names["candidate"] = "Candidate"
        // The player-target gate rejects targets above the catalog's theoretical
        // ceiling ((top3 + top27 constants, phi double-counted) / 30). With only the
        // charts above the ceiling is ≈10.13 < 10.4, so add three more unplayed
        // 12-constant charts to lift the ceiling to ≈10.47 without touching the
        // 300-contribution premise.
        repeat(3) { i ->
            difficulties["filler-$i"] = mapOf(Difficulty.IN to 12f)
            names["filler-$i"] = "Filler $i"
        }

        val result = useCase(
            currentB30 = emptyList(),
            records = records,
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.PlayerDisplayRks,
            targetRks = 10.4f
        )

        val candidate = result.firstOrNull { it.songId == "candidate" }
            ?: error("A chart that helps but can't alone reach the target must be suggested")
        assertClose(100f, candidate.targetAcc, tolerance = 0.001f)
        assertTrue(
            result.none { it.songId.startsWith("base-") },
            "Already-maxed (AP) charts must not be suggested"
        )
    }

    @Test
    fun suggestBeta5ThresholdBehavior() {
        val useCase = GetSuggestUseCase()
        val diffs = mapOf("candidate" to mapOf(Difficulty.IN to 10f))
        val names = mapOf("candidate" to "Candidate")

        // 1. Less than 20 B30 records → empty
        val tiny = (0 until 19).map { i ->
            BestRecord("t-$i", "", Difficulty.IN, 0, 0f, false, 0f, 0f)
        }
        assertTrue(useCase(tiny, emptyMap(), diffs, names).isEmpty(),
            "<20 B30 records must return empty")

        // 2. Exactly 20 records: threshold = currentB30[19].rks
        val nonPhi = (0 until 20).map { i ->
            BestRecord("np-$i", "NP $i", Difficulty.IN, 1_000_000, 100f, false, 10f, 10f - i * 0.2f, isPhi = false)
        }
        val expectedThreshold = nonPhi[19].rks  // 10.0 - 19*0.2 = 6.2

        // Candidate below threshold (acc 80, cc 10: rks = ((80-55)/45)^2 * 10 = 3.09 < 6.2) → suggested
        val belowRecords = mapOf(
            "candidate" to SongRecord(
                "candidate", levels = mapOf(Difficulty.IN to LevelRecord(800_000, 80f, false))
            )
        )
        val below = useCase(nonPhi, belowRecords, diffs, names)
        assertTrue(below.isNotEmpty(), "Candidate below threshold should be suggested")

        // Candidate above threshold (acc 100, cc 10: rks = 10.0 >= 6.2) → NOT suggested
        val aboveRecords = mapOf(
            "candidate" to SongRecord(
                "candidate", levels = mapOf(Difficulty.IN to LevelRecord(1_000_000, 100f, false))
            )
        )
        val above = useCase(nonPhi, aboveRecords, diffs, names)
        assertTrue(above.isEmpty(), "Candidate above or at threshold should NOT be suggested")

        // 3. Phi record at index 19 sets the threshold (phi NOT filtered before threshold selection)
        val phiAt19 = nonPhi.toMutableList()
        phiAt19[19] = BestRecord("phi-19", "Phi 19", Difficulty.IN, 1_000_000, 100f, true, 10f, 8f, isPhi = true)
        // Now threshold = 8f. Candidate acc 94, cc 10: rks = ((94-55)/45)^2 * 10 = 7.51 < 8 → suggested
        val mediumRecords = mapOf(
            "candidate" to SongRecord(
                "candidate", levels = mapOf(Difficulty.IN to LevelRecord(940_000, 94f, false))
            )
        )
        // With original threshold 6.2: acc 94 rks=7.51 >= 6.2 → NOT suggested → empty
        assertTrue(useCase(nonPhi, mediumRecords, diffs, names).isEmpty(),
            "Without phi, threshold 6.2 excludes acc 94 candidate")
        // With phi-raised threshold 8f: acc 94 rks=7.51 < 8 → suggested
        assertTrue(useCase(phiAt19, mediumRecords, diffs, names).isNotEmpty(),
            "Phi at index 19 raises threshold to 8f, making acc 94 candidate suggestable")
    }

    @Test
    fun useCasesDelegateToRepository(): Unit = runTest {
        val save = emptySave()
        val repository = FakePhigrosRepository(save)

        assertEquals(Pair(emptyList(), emptyList()), GetB30UseCase(repository)(emptyMap(), emptyMap()).first())
    }

    private fun emptySave(): Save = Save(
        gameRecord = emptyMap(),
        gameProgress = GameProgress(
            isFirstRun = false,
            legacyChapterFinished = false,
            alreadyShowCollectionTip = false,
            alreadyShowAutoUnlockINTip = false,
            completed = "",
            songUpdateInfo = 0,
            challengeModeRank = 0,
            money = emptyList(),
            unlockFlagOfSpasmodic = 0,
            unlockFlagOfIgallta = 0,
            unlockFlagOfRrharil = 0,
            flagOfSongRecordKey = 0,
            randomVersionUnlocked = null,
            chapter8UnlockBegin = null,
            chapter8UnlockSecondPhase = null,
            chapter8Passed = null,
            chapter8SongUnlocked = null
        ),
        user = UserSettings(showPlayerId = false, selfIntro = "", avatar = "", background = ""),
        summary = null
    )

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.0001f) {
        assertTrue(abs(expected - actual) < tolerance, "Expected $expected but was $actual")
    }

    private fun createBestRecord(
        songId: String,
        difficulty: Difficulty,
        chartConstant: Float,
        accuracy: Float,
        rks: Float
    ): BestRecord = BestRecord(
        songId = songId,
        songName = songId,
        difficulty = difficulty,
        score = 1_000_000,
        accuracy = accuracy,
        isFullCombo = false,
        chartConstant = chartConstant,
        rks = rks
    )

    private class FakePhigrosRepository(
        private val save: Save
    ) : PhigrosRepository {
        var lastSyncRequest: Triple<String, Server, SyncMode>? = null

        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> {
            error("Not needed for this test")
        }

        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode
        ): Result<SyncSaveResult> {
            lastSyncRequest = Triple(sessionToken, server, mode)
            return Result.success(SyncSaveResult(save, 1L, 0, false))
        }

        override fun getCachedSave(): Flow<Save?> = flowOf(save)

        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)

        override suspend fun saveSessionToken(token: String, server: Server) = Unit

        override suspend fun getSessionToken(): Pair<String, Server>? = null

        override suspend fun clearData() = Unit

        override suspend fun clearTokenSync() = Unit

        override suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int> = emptyMap()
        override suspend fun getTotalFullComboCount(): Int = 0
        override suspend fun getTotalPhiCount(): Int = 0
        override fun observeSyncSnapshots(): Flow<List<SyncSnapshot>> = flowOf(emptyList())
        override suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot> = emptyList()
        override fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>> = flowOf(emptyList())
        override suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry> = emptyList()

        override suspend fun apiTest(): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun getSongApiDetail(key: org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey): Result<org.kasumi321.ushio.phitracker.domain.model.SongApiDetail> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetSaveHistory(platform: String, platformId: String, apiUserId: String, request: List<String>): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetRankByUser(platform: String, platformId: String, apiUserId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun getChartTagTree(): Result<List<ChartTagTreeNode>> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getChartTags(songId: String, difficulty: Difficulty): Result<ChartTagSongData> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getMyChartTagVotes(
            songId: String,
            difficulty: Difficulty,
            platform: String,
            platformId: String,
            apiUserId: String,
            apiToken: String?
        ): Result<Set<String>> = Result.failure(UnsupportedOperationException())

        override suspend fun getB30ChartTags(records: List<BestRecord>): Result<B30ChartTagBatch> =
            Result.failure(UnsupportedOperationException())

        override suspend fun voteChartTags(
            songId: String,
            difficulty: Difficulty,
            primaryTags: List<String>,
            secondaryTags: List<String>,
            platform: String,
            platformId: String,
            apiUserId: String,
            apiToken: String
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun proposeSongAlias(songId: String, alias: String, note: String?): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<ReleaseInfo> =
            Result.failure(IllegalStateException("Not implemented in Phase E"))

        override suspend fun fetchGameUpdateInfo(): Result<GameUpdateInfo> =
            Result.failure(UnsupportedOperationException())
    }
}
