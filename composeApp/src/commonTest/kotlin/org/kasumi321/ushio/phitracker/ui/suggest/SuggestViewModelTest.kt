package org.kasumi321.ushio.phitracker.ui.suggest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.B30ChartTagBatch
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagSongData
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.ui.ViewModelTestLifecycle
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.suggest_target_invalid

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelLifecycle = ViewModelTestLifecycle()

    private val testSongDataProvider = SongDataProvider(
        SuggestTestAssetReader,
        PlatformPaths("/tmp/test_suggest", "/tmp/test_suggest_cache")
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        viewModelLifecycle.tearDown(dispatcher)
    }

    @Test
    fun emptyB30YieldsEmptySuggestions(): Unit = runTest(dispatcher) {
        val viewModel = createViewModel(FakeSuggestRepository(cachedSave = emptySave()))
        awaitUiState(viewModel) { !it.isLoading }

        assertTrue(viewModel.uiState.value.hasSaveData)
        assertTrue(viewModel.uiState.value.items.isEmpty(),
            "Empty B30 should yield empty suggestions")
    }

    @Test
    fun noCachedSaveYieldsEmptySuggestions(): Unit = runTest(dispatcher) {
        val viewModel = createViewModel(FakeSuggestRepository(cachedSave = null))
        awaitUiState(viewModel) { !it.isLoading }

        assertFalse(viewModel.uiState.value.hasSaveData)
        assertTrue(viewModel.uiState.value.items.isEmpty(),
            "No cached save should yield empty suggestions")
    }

    @Test
    fun insufficientB30YieldsEmptySuggestions(): Unit = runTest(dispatcher) {
        // Only 1 record — below the 20 minimum for the no-input auto mode
        val viewModel = createViewModel(
            FakeSuggestRepository(cachedSave = saveWithRecord("song-a", Difficulty.IN, 950_000, 95f, false))
        )
        awaitUiState(viewModel) { !it.isLoading }

        assertTrue(viewModel.uiState.value.items.isEmpty(),
            "B30 with < 20 records should yield empty suggestions")
    }

    @Test
    fun suggestTargetInputKeepsIllegalTextAndReportsExplicitError(): Unit = runTest(dispatcher) {
        val viewModel = createViewModel(
            FakeSuggestRepository(cachedSave = saveWithRecord("song-a", Difficulty.IN, 950_000, 95f, false))
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.setTargetInput("16.123")
        awaitUiState(viewModel) { it.targetError != null }

        assertEquals("16.123", viewModel.uiState.value.targetInput)
        assertEquals(
            UiText.Res(Res.string.suggest_target_invalid, "15.00"),
            viewModel.uiState.value.targetError
        )

        viewModel.setTargetInput("abc")
        awaitUiState(viewModel) { it.targetInput == "abc" && it.targetError != null }

        assertEquals("abc", viewModel.uiState.value.targetInput)
        assertEquals(
            UiText.Res(Res.string.suggest_target_invalid, "15.00"),
            viewModel.uiState.value.targetError
        )
    }

    @Test
    fun validTargetInputProducesSuggestions(): Unit = runTest(dispatcher) {
        val viewModel = createViewModel(
            FakeSuggestRepository(cachedSave = saveWithRecord("song-a", Difficulty.IN, 950_000, 95f, false))
        )
        awaitUiState(viewModel) { !it.isLoading }

        viewModel.setTargetInput("12")
        awaitUiState(viewModel) { it.targetInput == "12" && it.items.isNotEmpty() }

        assertTrue(viewModel.uiState.value.items.isNotEmpty(),
            "A reachable target should produce suggestions")
        assertEquals(null, viewModel.uiState.value.targetError)
    }

    @Test
    fun targetInputAboveCatalogMaxConstantIsRejected(): Unit = runTest(dispatcher) {
        val viewModel = createViewModel(
            FakeSuggestRepository(cachedSave = saveWithRecord("song-a", Difficulty.IN, 950_000, 95f, false))
        )
        awaitUiState(viewModel) { !it.isLoading }

        // Fixture catalog tops out at constant 15.0, so the display-RKS ceiling
        // is exactly 15.0 as well
        viewModel.setTargetInput("15.5")
        awaitUiState(viewModel) { it.targetError != null }

        assertEquals(
            UiText.Res(Res.string.suggest_target_invalid, "15.00"),
            viewModel.uiState.value.targetError
        )
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    private fun createViewModel(repository: FakeSuggestRepository): SuggestViewModel {
        return SuggestViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            getSuggestUseCase = GetSuggestUseCase(),
            songDataProvider = testSongDataProvider
        ).let(viewModelLifecycle::track)
    }

    /**
     * The suggestion sweep runs inside withContext(Dispatchers.Default), which
     * executes on a real background thread not driven by the test scheduler, so
     * poll in real time and advance virtual time in between until the expected
     * state shows up.
     */
    private suspend fun TestScope.awaitUiState(
        viewModel: SuggestViewModel,
        condition: (SuggestUiState) -> Boolean
    ) {
        val deadline = TimeSource.Monotonic.markNow()
        var satisfied = false
        while (deadline.elapsedNow() < 5.seconds) {
            withContext(Dispatchers.Default) { delay(10) }
            advanceUntilIdle()
            if (condition(viewModel.uiState.value)) {
                satisfied = true
                break
            }
        }
        assertTrue(satisfied, "UI state condition not met within timeout")
    }

    private object SuggestTestAssetReader : TextAssetReader {
        // song-a/song-b carry varied constants; filler-c* are flat 15.0 charts so
        // the catalog holds >= 30 charts and the dynamic display-RKS ceiling
        // (top3 + top27 at AP, phi double-counted) lands exactly on 15.0.
        override fun readText(name: String): String = when (name) {
            "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n" +
                "song-a\tSong A\tComposer\tIllus\t\t\t\t\t1.0\t5.0\t10.0\t14.0\n" +
                "song-b\tSong B\tComposer\tIllus\t\t\t\t\t2.0\t6.0\t11.0\t15.0\n" +
                (1..7).joinToString("\n") { i ->
                    "filler-$i\tFiller $i\tComposer\tIllus\t\t\t\t\t15.0\t15.0\t15.0\t15.0"
                }
            "infolist.json" -> """{"song-a":{"chapter":"Single"},"song-b":{"chapter":"Single"}}"""
            "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    private class FakeSuggestRepository(var cachedSave: Save?) : PhigrosRepository {
        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> =
            error("Not needed for this test")
        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode
        ): Result<SyncSaveResult> = error("Not needed for this test")

        override suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int> = emptyMap()
        override suspend fun getTotalFullComboCount(): Int = 0
        override suspend fun getTotalPhiCount(): Int = 0
        override fun observeSyncSnapshots(): Flow<List<SyncSnapshot>> = flowOf(emptyList())
        override suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot> = emptyList()
        override fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>> =
            flowOf(emptyList())
        override suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry> =
            emptyList()

        override fun getCachedSave(): Flow<Save?> = flowOf(cachedSave)
        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun saveSessionToken(token: String, server: Server) = Unit
        override suspend fun getSessionToken(): Pair<String, Server>? = null
        override suspend fun clearData() = Unit
        override suspend fun clearTokenSync() = Unit
        override suspend fun resetCredentialStorage(): Result<Unit> =
            Result.failure(UnsupportedOperationException("credential reset is not used by this test"))

        override suspend fun apiTest(): Result<JsonObject> =
            Result.failure(IllegalStateException("Not needed for this test"))
        override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not needed for this test"))
        override suspend fun getSongApiDetail(
            key: org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey
        ): Result<org.kasumi321.ushio.phitracker.domain.model.SongApiDetail> =
            Result.failure(IllegalStateException("Not needed for this test"))
        override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> =
            Result.failure(IllegalStateException("Not needed for this test"))
        override suspend fun apiGetSaveHistory(
            platform: String,
            platformId: String,
            apiUserId: String,
            request: List<String>
        ): Result<JsonObject> = Result.failure(IllegalStateException("Not needed for this test"))
        override suspend fun apiGetRankByUser(
            platform: String,
            platformId: String,
            apiUserId: String
        ): Result<JsonObject> = Result.failure(IllegalStateException("Not needed for this test"))
        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> =
            Result.failure(IllegalStateException("Not needed for this test"))

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
            Result.failure(IllegalStateException("Not needed for this test"))
        override suspend fun fetchGameUpdateInfo(): Result<GameUpdateInfo> =
            Result.failure(IllegalStateException("Not needed for this test"))
    }

    private companion object {
        fun emptySave(): Save = Save(
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

        fun saveWithRecord(
            songId: String,
            difficulty: Difficulty,
            score: Int,
            accuracy: Float,
            isFullCombo: Boolean
        ): Save = emptySave().copy(
            gameRecord = mapOf(
                songId to SongRecord(
                    songId = songId,
                    levels = mapOf(difficulty to LevelRecord(score, accuracy, isFullCombo))
                )
            )
        )
    }
}
