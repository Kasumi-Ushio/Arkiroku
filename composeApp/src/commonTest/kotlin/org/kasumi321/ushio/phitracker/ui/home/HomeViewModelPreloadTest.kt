package org.kasumi321.ushio.phitracker.ui.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.database.RecordDao
import org.kasumi321.ushio.phitracker.data.database.RecordEntity
import org.kasumi321.ushio.phitracker.data.logging.CrashReportExporter
import org.kasumi321.ushio.phitracker.data.logging.LogFileStore
import org.kasumi321.ushio.phitracker.data.logging.RuntimeLogExporter
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataUpdater
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadCoordinator
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadSession
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdateCoordinator
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.B30ChartTagBatch
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagSongData
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.Summary
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.ViewModelTestLifecycle
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.b30_tag_analysis_failed
import phitracker.composeapp.generated.resources.songs_preload_partial_failed
import phitracker.composeapp.generated.resources.tools_api_query_failed

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPreloadTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelLifecycle = ViewModelTestLifecycle()

    private val testPlatformPaths = PlatformPaths("/tmp/test", "/tmp/test_cache")
    private val testSongDataProvider = SongDataProvider(FakeTextAssetReader, testPlatformPaths)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        viewModelLifecycle.tearDown(dispatcher)
    }

    @Test
    fun preloadAttemptsEveryLowUrlBeforePersistingDone(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false)
        val preloader = RecordingPreloader()
        val artworkCache = RecordingStandardArtworkCache()
        val viewModel = createViewModel(settings, preloader, artworkFileCache = artworkCache)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.songs.showPreloadDialog)

        viewModel.startPreloadIllustrations()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "https://example.test/illLow/song-a.png",
                "https://example.test/illLow/song-b.png"
            ),
            preloader.urls.sorted()
        )
        assertTrue(settings.preloadDone)
        assertTrue(viewModel.uiState.value.songs.illustrationReady)
        assertFalse(viewModel.uiState.value.songs.showPreloadDialog)
        assertEquals(2, viewModel.uiState.value.songs.preloadCompleted)
        assertEquals(1f, viewModel.uiState.value.songs.preloadProgress)
        assertEquals(listOf("song-a.0", "song-b.0"), artworkCache.downloadedThumbnails.map { it.first }.sorted())
    }

    @Test
    fun completedMarkerWithMissingThumbnailsRequestsResync(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val artworkCache = RecordingStandardArtworkCache(thumbnailsPresent = false)
        val viewModel = createViewModel(
            settingsRepository = settings,
            preloader = RecordingPreloader(),
            artworkFileCache = artworkCache
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.songs.showPreloadDialog)
        assertTrue(viewModel.uiState.value.songs.illustrationReady)
    }

    @Test
    fun preloadRequestedByOnboardingAutoStartsWithoutManualConfirmation(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false, illustrationPreloadRequested = true)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        // No manual startPreloadIllustrations() call: the init-time check
        // consumed the onboarding flag and ran the download by itself.
        assertEquals(2, preloader.urls.size)
        assertTrue(settings.preloadDone)
        assertFalse(settings.illustrationPreloadRequested.first())
        assertFalse(viewModel.uiState.value.songs.showPreloadDialog)
    }

    @Test
    fun preloadDeclinedByOnboardingSuppressesDialogPermanently(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false, illustrationPreloadDeclined = true)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.songs.showPreloadDialog)
        assertTrue(viewModel.uiState.value.songs.illustrationReady)
        assertTrue(preloader.urls.isEmpty())
    }

    @Test
    fun declinedPreloadKeepsLowIllustrationBlankWithoutRemoteFallback(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, illustrationPreloadDeclined = true)
        val viewModel = createViewModel(settings, artworkFileCache = RecordingStandardArtworkCache())
        advanceUntilIdle()

        assertNull(viewModel.getLowIllustrationUrl("song-a.0"))
    }

    @Test
    fun declinedPreloadStillReturnsLocallyCachedThumbnail(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, illustrationPreloadDeclined = true)
        val artworkCache = RecordingStandardArtworkCache(cachedThumbnails = mapOf("song-a.0" to "/local/song-a.png"))
        val viewModel = createViewModel(settings, artworkFileCache = artworkCache)
        advanceUntilIdle()

        assertEquals("/local/song-a.png", viewModel.getLowIllustrationUrl("song-a.0"))
    }

    @Test
    fun notDeclinedKeepsRemoteFallbackForMissingThumbnail(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createViewModel(settings, artworkFileCache = RecordingStandardArtworkCache())
        advanceUntilIdle()

        assertEquals("https://example.test/illLow/song-a.png", viewModel.getLowIllustrationUrl("song-a.0"))
    }

    @Test
    fun failedPreloadDoesNotPersistDoneOrBlockHome(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false)
        val preloader = RecordingPreloader(failOnUrl = "https://example.test/illLow/song-b.png")
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        viewModel.startPreloadIllustrations()
        advanceUntilIdle()

        assertFalse(settings.preloadDone)
        assertTrue(viewModel.uiState.value.songs.illustrationReady)
        assertFalse(viewModel.uiState.value.songs.showPreloadDialog)
        assertEquals(UiText.Res(Res.string.songs_preload_partial_failed), viewModel.uiState.value.sync.error)
        assertEquals(2, viewModel.uiState.value.songs.preloadCompleted)
        assertEquals(1f, viewModel.uiState.value.songs.preloadProgress)
    }

    @Test
    fun preloadCardShowsOnlyForBackgroundRuns() {
        assertFalse(SongsUiState().shouldShowPreloadCard())
        assertTrue(
            SongsUiState(isPreloading = true, showPreloadDialog = false).shouldShowPreloadCard()
        )
        assertFalse(
            SongsUiState(isPreloading = true, showPreloadDialog = true).shouldShowPreloadCard()
        )
        assertFalse(
            SongsUiState(isPreloading = false, showPreloadDialog = false).shouldShowPreloadCard()
        )
    }

    @Test
    fun currentSongIsExposedWhilePreloadingAndClearedAfter(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false)
        val cache = ControlledThumbnailCache()
        val viewModel = createViewModel(settings, artworkFileCache = cache)
        advanceUntilIdle()

        viewModel.startPreloadIllustrations()
        runCurrent()
        // The coordinator only reports progress (and the current song) when an
        // attempt finishes; nothing has finished while both downloads are gated.
        assertNull(viewModel.uiState.value.songs.preloadCurrentSong)
        assertFalse(viewModel.uiState.value.songs.shouldShowPreloadCard())

        cache.complete("song-a.0")
        runCurrent()
        val current = viewModel.uiState.value.songs.preloadCurrentSong
        assertNotNull(current)
        assertTrue(current in setOf("Song A", "Song B"))

        cache.complete("song-b.0")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.songs.preloadCurrentSong)
    }

    @Test
    fun minimizingKeepsPreloadRunningAndDoesNotReopenTheDialog(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false)
        val cache = GatedSuccessThumbnailCache()
        val viewModel = createViewModel(settings, artworkFileCache = cache)
        advanceUntilIdle()

        viewModel.startPreloadIllustrations()
        runCurrent()
        assertTrue(viewModel.uiState.value.songs.isPreloading)
        assertFalse(viewModel.uiState.value.songs.shouldShowPreloadCard())

        viewModel.minimizePreload()
        assertFalse(viewModel.uiState.value.songs.showPreloadDialog)
        assertTrue(viewModel.uiState.value.songs.isPreloading)
        assertTrue(viewModel.uiState.value.songs.shouldShowPreloadCard())

        cache.release()
        advanceUntilIdle()

        assertTrue(settings.preloadDone)
        assertFalse(viewModel.uiState.value.songs.isPreloading)
        assertFalse(viewModel.uiState.value.songs.showPreloadDialog)
        assertFalse(viewModel.uiState.value.songs.shouldShowPreloadCard())
        assertEquals(1f, viewModel.uiState.value.songs.preloadProgress)
    }

    @Test
    fun cancellingFromTheDialogStopsTheRunAndDeclines(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false)
        val cache = GatedSuccessThumbnailCache()
        val viewModel = createViewModel(settings, artworkFileCache = cache)
        advanceUntilIdle()

        viewModel.startPreloadIllustrations()
        runCurrent()
        assertTrue(viewModel.uiState.value.songs.isPreloading)

        viewModel.cancelPreload()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.songs.isPreloading)
        assertFalse(viewModel.uiState.value.songs.showPreloadDialog)
        assertFalse(viewModel.uiState.value.songs.shouldShowPreloadCard())
        assertNull(viewModel.uiState.value.songs.preloadCurrentSong)
        assertEquals(0f, viewModel.uiState.value.songs.preloadProgress)
        assertTrue(settings.illustrationPreloadDeclined.first())
        assertFalse(settings.preloadDone)
    }

    @Test
    fun tabTransitionsDoNotRestartActiveHomeWorkOrResetSiblingState(): Unit = runTest(dispatcher) {
        val repository = ControlledHomeWorkRepository()
        val artworkCache = ControlledThumbnailCache()
        val viewModel = createViewModel(
            settingsRepository = FakeSettingsRepository(preloadDone = false, autoCheckUpdate = false),
            artworkFileCache = artworkCache,
            repository = repository
        )
        val tabs = HomeTabState()
        advanceUntilIdle()

        viewModel.startPreloadIllustrations()
        viewModel.refresh()
        viewModel.fetchApiRankByPosition(7)
        runCurrent()

        assertEquals(1, repository.syncCallCount)
        assertEquals(1, repository.rankByPositionCallCount)
        assertEquals(2, repository.networkCallCount)
        assertEquals(2, artworkCache.requests.size)
        assertTrue(viewModel.uiState.value.sync.isSyncing)
        assertTrue(viewModel.uiState.value.songs.isPreloading)

        repeat(3) {
            tabs.select(HomeTab.Index)
            tabs.select(HomeTab.Tools)
            tabs.select(HomeTab.Profile)
            tabs.select(HomeTab.B30)
        }
        assertEquals(HomeTab.B30, tabs.selected)
        assertEquals(1, repository.syncCallCount)
        assertEquals(1, repository.rankByPositionCallCount)
        assertEquals(2, artworkCache.requests.size)

        artworkCache.complete("song-a.0")
        repository.completeRankByPosition()
        runCurrent()
        assertEquals(1, viewModel.uiState.value.songs.preloadCompleted)
        assertEquals(0.5f, viewModel.uiState.value.songs.preloadProgress)
        assertEquals(UiText.Res(Res.string.tools_api_query_failed), viewModel.uiState.value.tools.apiRankByPosition.message)

        repeat(2) {
            tabs.select(HomeTab.Tools)
            tabs.select(HomeTab.Index)
        }
        assertEquals(1, repository.syncCallCount)
        assertEquals(1, repository.rankByPositionCallCount)
        assertEquals(2, repository.networkCallCount)
        assertEquals(2, artworkCache.requests.size)
        assertTrue(viewModel.uiState.value.sync.isSyncing)
        assertEquals(0.5f, viewModel.uiState.value.songs.preloadProgress)

        repository.completeSync()
        artworkCache.complete("song-b.0")
        advanceUntilIdle()
        assertEquals(UiText.Res(Res.string.songs_preload_partial_failed), viewModel.uiState.value.sync.error)
        assertEquals(1f, viewModel.uiState.value.songs.preloadProgress)
        assertEquals(UiText.Res(Res.string.tools_api_query_failed), viewModel.uiState.value.tools.apiRankByPosition.message)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.sync.error)
        assertEquals(1f, viewModel.uiState.value.songs.preloadProgress)
        assertEquals(UiText.Res(Res.string.tools_api_query_failed), viewModel.uiState.value.tools.apiRankByPosition.message)
    }

    @Test
    fun getLowIllustrationUrlReturnsIllLowPath(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val artworkCache = RecordingStandardArtworkCache()
        val viewModel = createViewModel(settings, preloader, artworkFileCache = artworkCache)
        val url = viewModel.getLowIllustrationUrl("song-a")
        assertEquals("https://example.test/illLow/song-a.png", url)
        assertTrue(
            artworkCache.downloadedThumbnails.isEmpty(),
            "A thumbnail cache miss must only return the remote fallback; it must not start a download"
        )
    }

    @Test
    fun illustrationUrisPreferLocalThumbnailAndStandardArtworkCaches(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val artworkCache = RecordingStandardArtworkCache(
            cachedThumbnails = mapOf("song-a" to "/cache/thumbnail/song-a.png"),
            cachedStandard = mapOf("song-a" to "/cache/standard/song-a.png")
        )
        val viewModel = createViewModel(settings, preloader, artworkFileCache = artworkCache)

        assertEquals("/cache/thumbnail/song-a.png", viewModel.getLowIllustrationUrl("song-a"))
        assertEquals("/cache/standard/song-a.png", viewModel.getCachedOrStandardIllustrationUri("song-a"))
        assertTrue(artworkCache.downloadedThumbnails.isEmpty())
        assertTrue(artworkCache.downloaded.isEmpty())
    }

    @Test
    fun repositoryPersistenceReadsPopulateStatsAndOrderedSnapshots(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false)
        val repository = FakePhigrosRepository().apply {
            clearCounts = mapOf(Difficulty.EZ to 2, Difficulty.HD to 3, Difficulty.IN to 4, Difficulty.AT to 1)
            fullComboCount = 5
            phiCount = 2
            snapshots = listOf(
                syncSnapshot(id = 2L, timestamp = 2_000L, dataCount = 1),
                syncSnapshot(id = 1L, timestamp = 1_000L, dataCount = 1)
            )
            historyBySnapshot = mapOf(
                2L to listOf(syncHistory(snapshotId = 2L, songId = "song-a.0", difficulty = "IN", timestamp = 2_001L)),
                1L to listOf(syncHistory(snapshotId = 1L, songId = "song-b.0", difficulty = "HD", timestamp = 1_001L))
            )
        }

        val viewModel = createViewModel(settings, repository = repository)
        advanceUntilIdle()

        assertEquals(mapOf("EZ" to 2, "HD" to 3, "IN" to 4, "AT" to 1), viewModel.uiState.value.profile.clearCounts)
        assertEquals(5, viewModel.uiState.value.profile.fcCount)
        assertEquals(2, viewModel.uiState.value.profile.phiCount)
        assertEquals(listOf(2L, 1L), viewModel.uiState.value.tools.syncSnapshots.map { it.id })
        assertEquals(listOf("song-a.0", "song-b.0"), viewModel.uiState.value.profile.recentSyncedRecords.map { it.songId })
        assertEquals(0, repository.syncCallCount)
        assertEquals(0, repository.networkCallCount)
    }

    @Test
    fun repositoryPersistenceReadsKeepEmptyStateAndSkipUnknownDifficulty(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false)
        val repository = FakePhigrosRepository().apply {
            snapshots = listOf(syncSnapshot(id = 1L, timestamp = 1_000L, dataCount = 1))
            historyBySnapshot = mapOf(
                1L to listOf(syncHistory(snapshotId = 1L, songId = "song-a.0", difficulty = "UNKNOWN"))
            )
        }

        val viewModel = createViewModel(settings, repository = repository)
        advanceUntilIdle()

        assertEquals(emptyMap(), viewModel.uiState.value.profile.clearCounts)
        assertEquals(0, viewModel.uiState.value.profile.fcCount)
        assertEquals(0, viewModel.uiState.value.profile.phiCount)
        assertTrue(viewModel.uiState.value.profile.recentSyncedRecords.isEmpty())
        assertNull(viewModel.uiState.value.profile.lastSyncedRecord)
        assertEquals(0, repository.syncCallCount)
        assertEquals(0, repository.networkCallCount)
    }

    @Test
    fun repositoryFakeTracksNetworkSeparatelyFromSync(): Unit = runTest(dispatcher) {
        val repository = FakePhigrosRepository()

        runCatching { repository.validateToken("token", Server.CN) }
        repository.apiTest()
        repository.fetchLatestRelease(includePreRelease = false)
        runCatching { repository.syncSave("token", Server.CN, SyncMode.Refresh) }

        assertEquals(1, repository.syncCallCount)
        assertEquals(4, repository.networkCallCount)
    }

    @Test
    fun recentEffectiveSyncHistoryLoadsAllEntriesFromLatestThreeEffectiveSnapshots(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false)
        val snapshots = listOf(
            syncSnapshot(id = 4L, timestamp = 4_000L, dataCount = 2),
            syncSnapshot(id = 3L, timestamp = 3_000L, dataCount = 0),
            syncSnapshot(id = 2L, timestamp = 2_000L, dataCount = 1),
            syncSnapshot(id = 1L, timestamp = 1_000L, dataCount = 1)
        )
        val history = mapOf(
            4L to listOf(
                syncHistory(snapshotId = 4L, songId = "song-a.0", difficulty = "IN", score = 950_000, accuracy = 95f, isFullCombo = false, timestamp = 4_001L),
                syncHistory(snapshotId = 4L, songId = "song-b.0", difficulty = "HD", score = 940_000, accuracy = 94f, isFullCombo = true, timestamp = 4_002L)
            ),
            3L to emptyList(),
            2L to listOf(syncHistory(snapshotId = 2L, songId = "song-a.0", difficulty = "EZ", score = 900_000, accuracy = 90f, isFullCombo = false, timestamp = 2_001L)),
            1L to listOf(syncHistory(snapshotId = 1L, songId = "song-b.0", difficulty = "IN", score = 910_000, accuracy = 91f, isFullCombo = false, timestamp = 1_001L))
        )
        val repository = FakePhigrosRepository().apply {
            this.snapshots = snapshots
            historyBySnapshot = history
        }
        val viewModel = createViewModel(settingsRepository = settings, repository = repository)
        advanceUntilIdle()

        assertEquals(4_000L, viewModel.uiState.value.profile.lastSyncTime)
        assertEquals(
            listOf("song-a.0", "song-b.0", "song-a.0", "song-b.0"),
            viewModel.uiState.value.profile.recentSyncedRecords.map { it.songId },
            "History should include every entry from the latest three effective snapshots"
        )
        assertEquals("song-a.0", viewModel.uiState.value.profile.lastSyncedRecord?.songId)
        assertEquals(Difficulty.IN, viewModel.uiState.value.profile.lastSyncedRecord?.difficulty)
        assertEquals(0, repository.syncCallCount)
        assertEquals(0, repository.networkCallCount)
    }

    @Test
    fun noChangeSyncDoesNotInsertSnapshotOrHistory(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val existingRecords = listOf(
            RecordEntity(songId = "song-a", difficulty = "IN", score = 950_000, accuracy = 95f, isFullCombo = false, updatedAt = 1_000L)
        )
        val recordDao = StatefulRecordDao(initialRecords = existingRecords, postSyncRecords = existingRecords)
        val repository = FakePhigrosRepositoryForSync(
            syncResult = Result.success(saveWithRks(15.5f)),
            recordDao = recordDao
        )
        val logFileStore = createTestLogFileStore()

        val viewModel = HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = testSongDataProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settings,
            illustrationPreloadCoordinator = preloadCoordinator(preloader = preloader),
            songDataUpdateCoordinator = fakeSongDataUpdateCoordinator()
        ).let(viewModelLifecycle::track)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.sync.isSyncing, "Sync should be complete")
        assertEquals(null, viewModel.uiState.value.sync.error, "Sync should have no error")
        assertEquals(2_000L, viewModel.uiState.value.profile.lastSyncTime)
        assertEquals(listOf(SyncMode.Refresh), repository.syncModes)
        assertEquals(0, recordDao.getAllRecordsOnceCallCount, "Home must not re-read records after sync")
        assertEquals(emptyList(), viewModel.uiState.value.profile.recentSyncedRecords)
        assertNull(viewModel.uiState.value.profile.lastSyncedRecord)
        assertEquals(emptyList(), repository.getSyncSnapshotsOnce())
    }

    @Test
    fun noChangeSyncRetainsRecentEffectiveHistoryOnScreen(): Unit = runTest(dispatcher) {
        // A no-change refresh must keep showing the latest effective history
        // instead of clearing the section (issue #12, display side).
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val existingRecords = listOf(
            RecordEntity(songId = "song-a", difficulty = "IN", score = 950_000, accuracy = 95f, isFullCombo = false, updatedAt = 1_000L)
        )
        val recordDao = StatefulRecordDao(initialRecords = existingRecords, postSyncRecords = existingRecords)
        val repository = FakePhigrosRepositoryForSync(
            syncResult = Result.success(saveWithRks(15.5f)),
            recordDao = recordDao
        ).apply {
            snapshots = listOf(syncSnapshot(id = 1L, timestamp = 1_500L, dataCount = 1))
            historyBySnapshot = mapOf(
                1L to listOf(
                    syncHistory(snapshotId = 1L, songId = "song-a.0", difficulty = "IN", score = 950_000, accuracy = 95f, isFullCombo = false, timestamp = 1_501L)
                )
            )
        }
        val logFileStore = createTestLogFileStore()

        val viewModel = HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = testSongDataProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settings,
            illustrationPreloadCoordinator = preloadCoordinator(preloader = preloader),
            songDataUpdateCoordinator = fakeSongDataUpdateCoordinator()
        ).let(viewModelLifecycle::track)
        advanceUntilIdle()

        assertEquals(
            listOf("song-a.0"),
            viewModel.uiState.value.profile.recentSyncedRecords.map { it.songId },
            "History should be visible before the no-change refresh"
        )

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.sync.isSyncing, "Sync should be complete")
        assertEquals(2_000L, viewModel.uiState.value.profile.lastSyncTime)
        assertEquals(listOf(SyncMode.Refresh), repository.syncModes)
        assertEquals(
            listOf("song-a.0"),
            viewModel.uiState.value.profile.recentSyncedRecords.map { it.songId },
            "A no-change sync must keep showing the latest effective history"
        )
        assertEquals("song-a.0", viewModel.uiState.value.profile.lastSyncedRecord?.songId)
    }

    @Test
    fun changedSyncConsumesAtomicResultWithoutWritingPersistence(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val committedSnapshot = syncSnapshot(id = 1L, timestamp = 2_000L, dataCount = 1)
        val committedHistory = syncHistory(
            snapshotId = 1L,
            songId = "song-a.0",
            difficulty = "IN",
            score = 950_000,
            accuracy = 95f,
            isFullCombo = true,
            timestamp = 2_000L
        )
        val initialRecords = listOf(
            RecordEntity(songId = "song-a", difficulty = "IN", score = 900_000, accuracy = 90f, isFullCombo = false, updatedAt = 1_000L)
        )
        val postSyncRecords = listOf(
            RecordEntity(songId = "song-a", difficulty = "IN", score = 950_000, accuracy = 95f, isFullCombo = true, updatedAt = 2_000L)
        )
        val recordDao = StatefulRecordDao(initialRecords = initialRecords, postSyncRecords = postSyncRecords)
        val cachedSave = saveWithRecord(
            songId = "song-a.0",
            difficulty = Difficulty.IN,
            score = 990_000,
            accuracy = 99f,
            isFullCombo = false
        )
        val repository = FakePhigrosRepositoryForSync(
            syncResult = Result.success(saveWithRks(15.5f)),
            recordDao = recordDao,
            initialCachedSave = cachedSave,
            changedEntryCount = 1,
            snapshotCreated = true
        ).apply {
            snapshots = listOf(committedSnapshot)
            historyBySnapshot = mapOf(1L to listOf(committedHistory))
        }
        val logFileStore = createTestLogFileStore()

        val viewModel = HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = testSongDataProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settings,
            illustrationPreloadCoordinator = preloadCoordinator(preloader = preloader),
            songDataUpdateCoordinator = fakeSongDataUpdateCoordinator()
        ).let(viewModelLifecycle::track)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.sync.isSyncing, "Sync should be complete")
        assertEquals(null, viewModel.uiState.value.sync.error, "Sync should have no error")
        assertEquals(listOf(SyncMode.Refresh), repository.syncModes)
        assertEquals(2_000L, viewModel.uiState.value.profile.lastSyncTime)
        assertEquals(listOf("song-a.0"), viewModel.uiState.value.profile.recentSyncedRecords.map { it.songId })
        assertEquals(listOf(1L), repository.getSyncSnapshotsOnce().map { it.id })
    }

    @Test
    fun b30TagAnalysisLoadsOncePerContentAndRetryRefetches(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false, apiEnabled = true)
        val repository = FakePhigrosRepository().apply {
            cachedSave = saveWithRecord("song-a.0", Difficulty.IN, 990_000, 99f, isFullCombo = false)
        }
        val viewModel = createViewModel(settings, repository = repository)
        awaitB30(viewModel)

        val keys = viewModel.uiState.value.b30.b30.map { it.songId to it.difficulty }
        assertTrue(keys.isNotEmpty())
        assertEquals(listOf(keys), repository.b30ChartTagRequests)
        assertEquals(UiText.Res(Res.string.b30_tag_analysis_failed), viewModel.uiState.value.b30.tagAnalysis.error)

        // Given a successful batch for the current B30 content
        repository.b30ChartTags = Result.success(b30TagBatchFixture(keys))

        // When retrying after a failure
        viewModel.retryTagAnalysis()
        advanceUntilIdle()

        // Then the analysis is computed from the batch
        val analysis = viewModel.uiState.value.b30.tagAnalysis.analysis
        assertNotNull(analysis)
        assertFalse(analysis.insufficient)
        assertEquals(140, analysis.totalVotes)
        val chartRks = viewModel.uiState.value.b30.b30.single().rks
        assertEquals(chartRks, analysis.averageRks, absoluteTolerance = 1e-5f)
        assertEquals(listOf("高速", "连打", "多指"), analysis.strong.map { it.name })
        assertEquals(listOf(40, 30, 25), analysis.strong.map { it.votes })
        assertEquals(listOf("爆发", "停顿", "散打"), analysis.weak.map { it.name })
        assertEquals(listOf(10, 15, 20), analysis.weak.map { it.votes })
        assertEquals(listOf("配置", "手法"), analysis.categories.map { it.name })
        assertEquals(2, repository.b30ChartTagRequests.size)
    }

    @Test
    fun b30TagAnalysisLoadsWhileApiSwitchIsOff(): Unit = runTest(dispatcher) {
        // The chartsTag read endpoints are public, so the analysis must load
        // even when the score-API switch is off.
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false, apiEnabled = false)
        val repository = FakePhigrosRepository().apply {
            cachedSave = saveWithRecord("song-a.0", Difficulty.IN, 990_000, 99f, isFullCombo = false)
        }

        val viewModel = createViewModel(settings, repository = repository)
        awaitB30(viewModel)

        assertTrue(viewModel.uiState.value.b30.b30.isNotEmpty())
        assertEquals(1, repository.b30ChartTagRequests.size)
        assertEquals(UiText.Res(Res.string.b30_tag_analysis_failed), viewModel.uiState.value.b30.tagAnalysis.error)
    }

    /**
     * The B30 collect reacts to the cached-save flow on a real background
     * thread which is not driven by the test scheduler, so poll in real time
     * before advancing virtual time.
     */
    private suspend fun TestScope.awaitB30(viewModel: HomeViewModel) {
        withContext(Dispatchers.Default) {
            // TimeSource.Monotonic and delay are KMP-common; System.currentTimeMillis
            // and Thread.sleep are JVM-only and would break the iOS test compile.
            val deadline = TimeSource.Monotonic.markNow()
            while (viewModel.uiState.value.b30.b30.isEmpty() && deadline.elapsedNow() < 5.seconds) {
                delay(10)
            }
        }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.b30.b30.isNotEmpty(), "B30 should populate from the cached save")
    }

    private fun b30TagBatchFixture(keys: List<Pair<String, Difficulty>>): B30ChartTagBatch {
        val votes = mapOf("高速" to 40, "连打" to 30, "多指" to 25, "散打" to 20, "停顿" to 15, "爆发" to 10)
        val categories = listOf(
            ChartTagTreeNode(id = 1L, name = "配置", description = null, sortOrder = 0, voteCount = 70),
            ChartTagTreeNode(id = 2L, name = "手法", description = null, sortOrder = 1, voteCount = 30)
        )
        return B30ChartTagBatch(
            tags = keys.associate { (songId, difficulty) -> songId to mapOf(difficulty to votes) },
            categories = keys.associate { (songId, difficulty) -> songId to mapOf(difficulty to categories) }
        )
    }

    private fun createViewModel(
        settingsRepository: FakeSettingsRepository,
        preloader: IllustrationThumbnailPreloader = RecordingPreloader(),
        artworkFileCache: StandardArtworkCache = RecordingStandardArtworkCache(),
        songDataProvider: SongDataProvider = testSongDataProvider,
        appVersionName: String = "",
        repository: PhigrosRepository = FakePhigrosRepository(),
        songDataUpdater: FakeSongDataUpdater = FakeSongDataUpdater()
    ): HomeViewModel {
        val illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") }
        val coordinator = preloadCoordinator(
            songDataProvider = songDataProvider,
            artworkFileCache = artworkFileCache,
            preloader = preloader
        )
        return HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = songDataProvider,
            illustrationProvider = illustrationProvider,
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settingsRepository,
            artworkFileCache = artworkFileCache,
            illustrationPreloadCoordinator = coordinator,
            appVersionNameProvider = { appVersionName },
            songDataUpdateCoordinator = fakeSongDataUpdateCoordinator(
                songDataProvider = songDataProvider,
                artworkFileCache = artworkFileCache,
                updater = songDataUpdater
            ),
            preloadSession = IllustrationPreloadSession(
                coordinator = coordinator,
                settingsRepository = settingsRepository,
                scope = CoroutineScope(dispatcher)
            )
        ).let(viewModelLifecycle::track)
    }

    private fun fakeSongDataUpdateCoordinator(
        songDataProvider: SongDataProvider = testSongDataProvider,
        artworkFileCache: StandardArtworkCache = RecordingStandardArtworkCache(),
        updater: FakeSongDataUpdater = FakeSongDataUpdater()
    ): SongDataUpdateCoordinator = SongDataUpdateCoordinator(
        songDataUpdater = updater,
        songDataProvider = songDataProvider,
        illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
        artworkFileCache = artworkFileCache,
        collectibleDataUpdater = FakeCollectibleDataUpdater(),
        thumbnailPreloader = RecordingPreloader(),
        clearCacheUrls = {}
    )

    private class FakeCollectibleDataUpdater : CollectibleDataUpdater(
        io.ktor.client.HttpClient(),
        PlatformPaths("/tmp/test", "/tmp/test_cache"),
        CollectibleDataProvider(FakeTextAssetReader, PlatformPaths("/tmp/test", "/tmp/test_cache"))
    ) {
        override suspend fun checkUpstreamChanged(): Result<Boolean> = Result.success(false)
        override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> =
            Result.success(Unit)
    }

    private fun preloadCoordinator(
        songDataProvider: SongDataProvider = testSongDataProvider,
        artworkFileCache: StandardArtworkCache = RecordingStandardArtworkCache(),
        preloader: IllustrationThumbnailPreloader = RecordingPreloader()
    ): IllustrationPreloadCoordinator = IllustrationPreloadCoordinator(
        songDataProvider = songDataProvider,
        illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
        artworkFileCache = artworkFileCache,
        thumbnailPreloader = preloader
    )

    private class RecordingPreloader(
        private val failOnUrl: String? = null
    ) : IllustrationThumbnailPreloader {
        val urls: MutableList<String> = mutableListOf()

        override suspend fun preload(url: String): Result<Unit> {
            urls += url
            if (url == failOnUrl) return Result.failure(IllegalStateException("preload failed"))
            return Result.success(Unit)
        }
    }

    private class ControlledHomeWorkRepository : FakePhigrosRepository() {
        private val syncGate = CompletableDeferred<Unit>()
        private val rankGate = CompletableDeferred<Unit>()
        var rankByPositionCallCount: Int = 0
            private set

        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode
        ): Result<SyncSaveResult> {
            syncCallCount++
            networkCallCount++
            syncGate.await()
            return Result.failure(IllegalStateException("sync failed"))
        }

        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> {
            rankByPositionCallCount++
            networkCallCount++
            rankGate.await()
            return Result.failure(IllegalStateException("rank failed"))
        }

        fun completeSync() {
            syncGate.complete(Unit)
        }

        fun completeRankByPosition() {
            rankGate.complete(Unit)
        }
    }

    private class GatedSuccessThumbnailCache : StandardArtworkCache {
        private val gate = CompletableDeferred<Unit>()
        val requests = mutableListOf<String>()

        fun release() {
            gate.complete(Unit)
        }

        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
            requests += songId
            gate.await()
            return url
        }

        override fun getThumbnailIfPresent(songId: String): String? = null
        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = false
        override fun clearThumbnails(songIds: Iterable<String>) = Unit
        override fun clearAllThumbnails() = Unit
        override suspend fun getOrDownloadStandard(songId: String, url: String): String = url
        override fun getStandardIfPresent(songId: String): String? = null
        override fun clearStandard(songIds: Iterable<String>) = Unit
        override fun clearAllStandard() = Unit
    }

    private class ControlledThumbnailCache : StandardArtworkCache {
        private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val requests = mutableListOf<String>()

        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
            requests += songId
            gates.getOrPut(songId) { CompletableDeferred() }.await()
            if (songId == "song-b.0") error("thumbnail failed")
            return url
        }

        fun complete(songId: String) {
            gates.getValue(songId).complete(Unit)
        }

        override fun getThumbnailIfPresent(songId: String): String? = null
        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = false
        override fun clearThumbnails(songIds: Iterable<String>) = Unit
        override fun clearAllThumbnails() = Unit
        override suspend fun getOrDownloadStandard(songId: String, url: String): String = url
        override fun getStandardIfPresent(songId: String): String? = null
        override fun clearStandard(songIds: Iterable<String>) = Unit
        override fun clearAllStandard() = Unit
    }

    private class RecordingStandardArtworkCache(
        private var thumbnailsPresent: Boolean = true,
        private val cachedThumbnails: Map<String, String> = emptyMap(),
        private val cachedStandard: Map<String, String> = emptyMap()
    ) : StandardArtworkCache {
        val downloaded: MutableList<Pair<String, String>> = mutableListOf()
        val downloadedThumbnails: MutableList<Pair<String, String>> = mutableListOf()
        val clearedThumbnails: MutableList<String> = mutableListOf()
        val clearedStandard: MutableList<String> = mutableListOf()
        var clearAllCalled: Boolean = false

        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
            downloadedThumbnails += songId to url
            // Keep existing preloader assertions focused on request coverage.
            return url
        }

        override fun getThumbnailIfPresent(songId: String): String? = cachedThumbnails[songId]

        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = thumbnailsPresent

        override fun clearThumbnails(songIds: Iterable<String>) {
            clearedThumbnails += songIds
        }

        override fun clearAllThumbnails() {
            clearAllCalled = true
        }

        override suspend fun getOrDownloadStandard(songId: String, url: String): String {
            downloaded += songId to url
            return "/cache/standard/$songId.png"
        }

        override fun getStandardIfPresent(songId: String): String? = cachedStandard[songId]

        override fun clearStandard(songIds: Iterable<String>) {
            clearedStandard += songIds
        }

        override fun clearAllStandard() {
            clearAllCalled = true
        }
    }

    private class FakeSettingsRepository(
        preloadDone: Boolean,
        autoCheckUpdate: Boolean = true,
        autoCheckSongDataUpdate: Boolean = true,
        includePreRelease: Boolean = false,
        apiEnabled: Boolean = false,
        useApiData: Boolean = false,
        apiUserId: String = "",
        apiPlatform: String = "",
        apiPlatformId: String = "",
        illustrationPreloadRequested: Boolean = false,
        illustrationPreloadDeclined: Boolean = false
    ) : SettingsRepository {
        override val themeMode: Flow<Int> = flowOf(0)
        override val amoledBlack: Flow<Boolean> = flowOf(false)
        override suspend fun setAmoledBlack(enabled: Boolean) = Unit
        override val themeColorSource: Flow<String> = flowOf("system")
        override val seedColorArgb: Flow<Int> = flowOf(-10011977)
        override val themeImageSeedColorArgb: Flow<Int?> = flowOf(null)
        override val themeImageUri: Flow<String?> = flowOf(null)
        override val paletteStyleName: Flow<String> = flowOf("TonalSpot")
        override val showB30Overflow: Flow<Boolean> = flowOf(false)
        override val overflowCount: Flow<Int> = flowOf(9)
        override val b30CardStyle: Flow<String> = flowOf("classic")
        override suspend fun setB30CardStyle(style: String) = Unit
        override val b30ThemeFollowGlobal: Flow<Boolean> = flowOf(true)
        override val b30ExportThemeMode: Flow<Int?> = flowOf(null)
        override suspend fun setB30ThemeFollowGlobal(follow: Boolean) = Unit
        override suspend fun setB30ExportThemeMode(mode: Int) = Unit
        override val hazeBlurEnabled: Flow<Boolean> = flowOf(true)
        override val hazeBlurStrength: Flow<Float> = flowOf(0.75f)
        var preloadDone = preloadDone
            private set

        override suspend fun setThemeMode(mode: Int) = Unit
        override val appLanguage: Flow<String> = flowOf("system")
        override suspend fun setAppLanguage(language: String) = Unit
        override suspend fun setThemeColorSource(source: String) = Unit
        override suspend fun setSeedColorArgb(argb: Int) = Unit
        override suspend fun setThemeImageColor(uri: String?, seedColorArgb: Int) = Unit
        override suspend fun clearThemeImageColor() = Unit
        override suspend fun setPaletteStyleName(name: String) = Unit
        override suspend fun setShowB30Overflow(show: Boolean) = Unit
        override suspend fun setOverflowCount(count: Int) = Unit
        override suspend fun setHazeBlurEnabled(enabled: Boolean) = Unit
        override suspend fun setHazeBlurStrength(strength: Float) = Unit
        override suspend fun getPreloadDone(): Boolean = preloadDone
        override suspend fun setPreloadDone(done: Boolean) {
            preloadDone = done
        }

        override val avatarUri: Flow<String?> = flowOf(null)
        override suspend fun setAvatarUri(uri: String?) = Unit
        private val _gameUpdateCache = MutableStateFlow<String?>(null)
        val gameUpdateCacheWrites = mutableListOf<String?>()
        override val gameUpdateInfoCache: Flow<String?> = _gameUpdateCache.asStateFlow()
        override suspend fun setGameUpdateInfoCache(cache: String?) {
            gameUpdateCacheWrites += cache
            _gameUpdateCache.value = cache
        }
        override val moneyString: Flow<String> = flowOf("")
        override suspend fun setMoneyString(money: String) = Unit
        private val _includePreRelease = MutableStateFlow(includePreRelease)
        override val includePreRelease: Flow<Boolean> = _includePreRelease.asStateFlow()
        private val _autoCheckUpdate = MutableStateFlow(autoCheckUpdate)
        override val autoCheckUpdate: Flow<Boolean> = _autoCheckUpdate.asStateFlow()
        private val _autoCheckSongDataUpdate = MutableStateFlow(autoCheckSongDataUpdate)
        override val autoCheckSongDataUpdate: Flow<Boolean> = _autoCheckSongDataUpdate.asStateFlow()
        var autoCheckUpdateSetValue: Boolean? = null
            private set
        override suspend fun setIncludePreRelease(enabled: Boolean) {
            _includePreRelease.value = enabled
        }
        override suspend fun setAutoCheckUpdate(enabled: Boolean) {
            autoCheckUpdateSetValue = enabled
            _autoCheckUpdate.value = enabled
        }
        override suspend fun setAutoCheckSongDataUpdate(enabled: Boolean) {
            _autoCheckSongDataUpdate.value = enabled
        }
        override val apiEnabled: Flow<Boolean> = flowOf(apiEnabled)
        override suspend fun setApiEnabled(enabled: Boolean) = Unit
        override val useApiData: Flow<Boolean> = flowOf(useApiData)
        override suspend fun setUseApiData(useApiData: Boolean) = Unit
        override val apiId: Flow<String> = flowOf(apiUserId)
        override suspend fun setApiId(apiId: String) = Unit
        override val apiPlatform: Flow<String> = flowOf(apiPlatform)
        override suspend fun setApiPlatform(platform: String) = Unit
        override val apiPlatformId: Flow<String> = flowOf(apiPlatformId)
        override suspend fun setApiPlatformId(platformId: String) = Unit
        override val apiToken: Flow<String> = flowOf("")
        override suspend fun setApiToken(apiToken: String) = Unit
        override val crashNotificationGuideShown: Flow<Boolean> = flowOf(false)
        override suspend fun setCrashNotificationGuideShown(shown: Boolean) = Unit
        override val onboardingCompleted: Flow<Boolean> = flowOf(true)
        override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
        override val onboardingRerunRequested: Flow<Boolean> = flowOf(false)
        override suspend fun setOnboardingRerunRequested(requested: Boolean) = Unit
        private val _illustrationPreloadRequested = MutableStateFlow(illustrationPreloadRequested)
        override val illustrationPreloadRequested: Flow<Boolean> = _illustrationPreloadRequested.asStateFlow()
        override suspend fun setIllustrationPreloadRequested(requested: Boolean) {
            _illustrationPreloadRequested.value = requested
        }
        private val _illustrationPreloadDeclined = MutableStateFlow(illustrationPreloadDeclined)
        override val illustrationPreloadDeclined: Flow<Boolean> = _illustrationPreloadDeclined.asStateFlow()
        override suspend fun setIllustrationPreloadDeclined(declined: Boolean) {
            _illustrationPreloadDeclined.value = declined
        }
        private val _chartVoteKeys = MutableStateFlow<Set<String>>(emptySet())
        override val chartVoteKeys: Flow<Set<String>> = _chartVoteKeys.asStateFlow()
        override suspend fun recordChartVote(chartVoteKey: String) {
            _chartVoteKeys.value = _chartVoteKeys.value + chartVoteKey
        }
    }

    private open class FakePhigrosRepository : PhigrosRepository {
        var syncCallCount: Int = 0
        var networkCallCount: Int = 0
        var clearCounts: Map<Difficulty, Int> = emptyMap()
        var fullComboCount: Int = 0
        var phiCount: Int = 0
        var snapshots: List<SyncSnapshot> = emptyList()
        var songHistory: Map<String, List<SongSyncHistoryEntry>> = emptyMap()
        var historyBySnapshot: Map<Long, List<SongSyncHistoryEntry>> = emptyMap()
        var b30ChartTags: Result<B30ChartTagBatch> = Result.failure(UnsupportedOperationException())
        val b30ChartTagRequests = mutableListOf<List<Pair<String, Difficulty>>>()
        var cachedSave: Save? = emptySave()
        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> {
            networkCallCount++
            error("Not needed for this test")
        }

        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode
        ): Result<SyncSaveResult> {
            syncCallCount++
            networkCallCount++
            error("Not needed for this test")
        }

        private fun <T> networkResult(result: Result<T>): Result<T> {
            networkCallCount++
            return result
        }

        override suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int> = clearCounts
        override suspend fun getTotalFullComboCount(): Int = fullComboCount
        override suspend fun getTotalPhiCount(): Int = phiCount
        override fun observeSyncSnapshots(): Flow<List<SyncSnapshot>> = flowOf(snapshots)
        override suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot> = snapshots
        override fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>> = flowOf(songHistory[songId].orEmpty())
        override suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry> = historyBySnapshot[snapshotId].orEmpty()

        override fun getCachedSave(): Flow<Save?> = MutableStateFlow(cachedSave)
        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun saveSessionToken(token: String, server: Server) = Unit
        override suspend fun getSessionToken(): Pair<String, Server>? = Pair("fake-token", Server.CN)
        override suspend fun clearData() = Unit
        override suspend fun clearTokenSync() = Unit

        override suspend fun apiTest(): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))
        override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))
        override suspend fun getSongApiDetail(key: org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey): Result<org.kasumi321.ushio.phitracker.domain.model.SongApiDetail> =
            Result.failure(IllegalStateException("Song detail is route-owned"))
        override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))
        override suspend fun apiGetSaveHistory(platform: String, platformId: String, apiUserId: String, request: List<String>): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))
        override suspend fun apiGetRankByUser(platform: String, platformId: String, apiUserId: String): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))
        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))

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

        override suspend fun getB30ChartTags(records: List<BestRecord>): Result<B30ChartTagBatch> {
            b30ChartTagRequests += records.map { it.songId to it.difficulty }
            return b30ChartTags
        }

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

        open var fetchLatestReleaseCallCount = 0
        val fetchLatestReleaseIncludePreReleaseValues = mutableListOf<Boolean>()
        open var fetchLatestReleaseResult: Result<ReleaseInfo> =
            Result.failure(IllegalStateException("Not configured"))

        override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<ReleaseInfo> {
            fetchLatestReleaseCallCount++
            fetchLatestReleaseIncludePreReleaseValues.add(includePreRelease)
            return networkResult(fetchLatestReleaseResult)
        }

        var gameUpdateInfoResult: Result<GameUpdateInfo> =
            Result.failure(IllegalStateException("Not configured"))
        override suspend fun fetchGameUpdateInfo(): Result<GameUpdateInfo> = gameUpdateInfoResult
    }

    private class FakeRecordDao : RecordDao {
        override suspend fun insertAll(records: List<RecordEntity>) = Unit
        override fun getAllRecords(): Flow<List<RecordEntity>> = flowOf(emptyList())
        override suspend fun getAllRecordsOnce(): List<RecordEntity> = emptyList()
        override suspend fun getRecordsBySong(songId: String): List<RecordEntity> = emptyList()
        override suspend fun deleteAll() = Unit
        override suspend fun getRecordCount(): Int = 0
        override suspend fun getDistinctSongCount(): Int = 0
        override suspend fun getClearCountByDifficulty(difficulty: String): Int = 0
        override suspend fun getTotalFcCount(): Int = 0
        override suspend fun getTotalPhiCount(): Int = 0
    }

    private class FakeSongDataUpdater(
        httpClient: io.ktor.client.HttpClient = io.ktor.client.HttpClient(),
        paths: PlatformPaths = PlatformPaths("/tmp/test", "/tmp/test_cache"),
        songDataProvider: SongDataProvider = SongDataProvider(FakeTextAssetReader, PlatformPaths("/tmp/test", "/tmp/test_cache")),
        private val onUpdate: suspend ((Int, Int, String) -> Unit) -> Result<Unit> = { onProgress ->
            onProgress(SongDataUpdater.FILE_NAMES.size, SongDataUpdater.FILE_NAMES.size, "完成")
            Result.success(Unit)
        }
    ) : SongDataUpdater(
        httpClient = httpClient,
        paths = paths,
        songDataProvider = songDataProvider
    ) {
        var updateCalled = false
            private set
        var upstreamChanged = false
        var upstreamCheckCallCount = 0
            private set

        override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> {
            updateCalled = true
            return onUpdate(onProgress)
        }

        override suspend fun checkUpstreamChanged(): Result<Boolean> {
            upstreamCheckCallCount++
            return Result.success(upstreamChanged)
        }
    }

    private class StatefulRecordDao(
        initialRecords: List<RecordEntity>,
        private val postSyncRecords: List<RecordEntity>
    ) : RecordDao {
        var getAllRecordsOnceCallCount = 0
            private set
        private var currentRecords = initialRecords.toMutableList()

        override suspend fun insertAll(records: List<RecordEntity>) {
            currentRecords.clear()
            currentRecords.addAll(records)
        }

        override fun getAllRecords(): Flow<List<RecordEntity>> = flowOf(currentRecords.toList())
        override suspend fun getAllRecordsOnce(): List<RecordEntity> {
            val records = if (getAllRecordsOnceCallCount == 0) currentRecords.toList() else postSyncRecords.toList()
            getAllRecordsOnceCallCount++
            return records
        }

        override suspend fun getRecordsBySong(songId: String): List<RecordEntity> = currentRecords.filter { it.songId == songId }
        override suspend fun deleteAll() { currentRecords.clear() }
        override suspend fun getRecordCount(): Int = currentRecords.size
        override suspend fun getDistinctSongCount(): Int = currentRecords.map { it.songId }.distinct().size
        override suspend fun getClearCountByDifficulty(difficulty: String): Int = 0
        override suspend fun getTotalFcCount(): Int = currentRecords.count { it.isFullCombo }
        override suspend fun getTotalPhiCount(): Int = currentRecords.count { it.accuracy >= 100f }
    }

    private class FakePhigrosRepositoryForSync(
        private val syncResult: Result<Save>,
        private val recordDao: StatefulRecordDao,
        private val initialCachedSave: Save? = emptySave(),
        private val committedAt: Long = 2_000L,
        private val changedEntryCount: Int = 0,
        private val snapshotCreated: Boolean = false
    ) : FakePhigrosRepository() {
        val syncModes = mutableListOf<SyncMode>()
        override fun getCachedSave(): Flow<Save?> = flowOf(initialCachedSave)

        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode
        ): Result<SyncSaveResult> {
            syncModes += mode
            return syncResult.map { save ->
                SyncSaveResult(save, committedAt, changedEntryCount, snapshotCreated)
            }
        }
    }

    private object FakeTextAssetReader : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "tips.txt" -> "Tip: test"
            "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\nsong-a\tSong A\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0\nsong-b\tSong B\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0"
            "infolist.json" -> "{}"
            "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    private class MutableSongDataReader(
        private var songIds: List<String>
    ) : TextAssetReader {
        fun replaceSongs(newSongIds: List<String>) {
            songIds = newSongIds
        }

        override fun readText(name: String): String = when (name) {
            "tips.txt" -> "Tip: test"
            "info.csv" -> buildString {
                appendLine("id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT")
                songIds.forEach { songId -> appendLine("$songId\tSong $songId\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0") }
            }.trimEnd()
            "infolist.json" -> "{}"
            "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    // --- Multi-chapter filter tests (Phase F) ---

    @Test
    fun multiChapterFilterStacking(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Initially no chapter filter — all 3 songs visible
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size,
            "All songs should be visible with no chapter filter")

        // Toggle "Single" — songs in "Single" chapter: song-a.0 and song-c.0
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.songs.filteredSongs.size,
            "2 songs in Single chapter")
        assertTrue(viewModel.uiState.value.songs.filteredSongs.all { it.chapter == "Single" })

        // Toggle also "Collection" — now both chapters active
        viewModel.toggleChapter("Collection")
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size,
            "Stacking both chapters shows all 3 songs")
        assertEquals(setOf("Single", "Collection"), viewModel.uiState.value.songs.selectedChapters)
    }

    @Test
    fun toggleChapterOff(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Toggle both chapters
        viewModel.toggleChapter("Single")
        viewModel.toggleChapter("Collection")
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size,
            "Both chapters active")
        assertEquals(setOf("Single", "Collection"), viewModel.uiState.value.songs.selectedChapters)

        // Toggle off "Single" — only "Collection" remains
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.songs.filteredSongs.size,
            "Only Collection chapter songs after toggling Single off")
        assertTrue(viewModel.uiState.value.songs.filteredSongs.all { it.chapter == "Collection" })
        assertEquals(setOf("Collection"), viewModel.uiState.value.songs.selectedChapters)
    }

    @Test
    fun clearChaptersRestoresAllSongs(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Apply chapter filter
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.songs.filteredSongs.size,
            "Filtered to Single chapter")
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isNotEmpty())

        // Clear chapters
        viewModel.clearChapters()
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size,
            "All songs restored after clearing chapters")
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isEmpty(),
            "selectedChapters should be empty after clear")
    }

    @Test
    fun resetFiltersRestoresAllSongs(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Apply multiple filters: chapter, difficulty, level range
        viewModel.toggleChapter("Single")
        viewModel.filterByDifficulty(Difficulty.HD)
        viewModel.filterByLevelRange(3, 10)
        advanceUntilIdle()

        // Verify filtering is active
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isNotEmpty())
        assertNotNull(viewModel.uiState.value.songs.selectedDifficulty)

        // Reset
        viewModel.resetFilters()
        advanceUntilIdle()

        // All filters cleared
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isEmpty())
        assertEquals(null, viewModel.uiState.value.songs.selectedDifficulty)
        // Chapter fixture constants span 1.0..4.0, so reset restores the
        // data-derived bounds rather than a hardcoded range.
        assertEquals(1, viewModel.uiState.value.songs.minLevel)
        assertEquals(4, viewModel.uiState.value.songs.maxLevel)
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size,
            "Reset should restore all songs")
    }

    @Test
    fun emptyChaptersMatchesAllSongs(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // selectedChapters starts empty — all songs match
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isEmpty())
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size)

        // Filter by difficulty only — chapters still empty, difficulty narrows
        viewModel.filterByDifficulty(Difficulty.IN)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isEmpty())
        // All 3 songs have IN=3.0, inside the data-derived default bounds 1..4
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size)
    }

    @Test
    fun chapterFilterStacksWithSearch(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Apply chapter filter first
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.songs.filteredSongs.size)

        // Add search query that narrows further
        viewModel.searchSongs("Song A")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.songs.filteredSongs.size,
            "Search + chapter filter should stack")
        assertEquals("song-a.0", viewModel.uiState.value.songs.filteredSongs.first().id)
    }

    // --- Dynamic level-range bounds (Issue 17) ---

    @Test
    fun levelBoundsDerivedFromSongDataConstants(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Chapter fixture constants span 1.0..4.0 → floor 1, ceil 4
        assertEquals(1, viewModel.uiState.value.songs.levelMinBound)
        assertEquals(4, viewModel.uiState.value.songs.levelMaxBound)
        // Untouched default selection snapped to the data-derived bounds
        assertEquals(1, viewModel.uiState.value.songs.minLevel)
        assertEquals(4, viewModel.uiState.value.songs.maxLevel)
    }

    @Test
    fun levelBoundsSnapUntouchedSelectionWhenDataWidens(): Unit = runTest(dispatcher) {
        val reader = LevelBoundsAssetReader(levelBoundsCsv("4.0"))
        val provider = SongDataProvider(reader, testPlatformPaths)
        val viewModel = createChapterFilterViewModel(FakeSettingsRepository(preloadDone = true), provider)
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.songs.levelMaxBound)

        // Simulated song-data update adding a 16.5-constant chart
        reader.infoCsv = levelBoundsCsv("16.5")
        provider.invalidateCache()
        advanceUntilIdle()

        // Bounds recompute (ceil 16.5 = 17); the untouched 1..4 selection snaps to them
        assertEquals(1, viewModel.uiState.value.songs.levelMinBound)
        assertEquals(17, viewModel.uiState.value.songs.levelMaxBound)
        assertEquals(1, viewModel.uiState.value.songs.minLevel)
        assertEquals(17, viewModel.uiState.value.songs.maxLevel)
        assertEquals(1, viewModel.uiState.value.songs.filteredSongs.size)
    }

    @Test
    fun levelBoundsClampExplicitSelectionWhenDataNarrows(): Unit = runTest(dispatcher) {
        val reader = LevelBoundsAssetReader(levelBoundsCsv("16.5"))
        val provider = SongDataProvider(reader, testPlatformPaths)
        val viewModel = createChapterFilterViewModel(FakeSettingsRepository(preloadDone = true), provider)
        advanceUntilIdle()

        // Explicit selection inside the initial 1..17 bounds
        viewModel.filterByLevelRange(2, 16)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.songs.minLevel)
        assertEquals(16, viewModel.uiState.value.songs.maxLevel)

        // Data update drops the widest chart: 1.0..3.0 → bounds 1..3
        reader.infoCsv = levelBoundsCsv("3.0")
        provider.invalidateCache()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.songs.levelMinBound)
        assertEquals(3, viewModel.uiState.value.songs.levelMaxBound)
        assertEquals(2, viewModel.uiState.value.songs.minLevel, "min stays inside the new bounds")
        assertEquals(3, viewModel.uiState.value.songs.maxLevel, "max clamps into the new bounds")
    }

    private class LevelBoundsAssetReader(
        var infoCsv: String
    ) : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "tips.txt" -> "Tip: test"
            "info.csv" -> infoCsv
            "infolist.json" -> "{}"
            "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    private fun levelBoundsCsv(maxConstant: String): String =
        "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n" +
            "song-a\tSong A\tComposer\tIllus\t\t\t\t\t1.0\t2.0\t3.0\t$maxConstant"

    @Test
    fun apiToolResultPreservesRows() {
        val rows = listOf(
            ApiToolRow(UiText.Raw("标签 A"), UiText.Raw("值 A")),
            ApiToolRow(UiText.Raw("标签 B"), UiText.Raw("值 B"))
        )
        val result = ApiToolResult(message = UiText.Raw("ok"), rows = rows)
        assertEquals(2, result.rows.size)
        assertEquals(UiText.Raw("标签 A"), result.rows[0].label)
        assertEquals(UiText.Raw("值 A"), result.rows[0].value)
        assertEquals(UiText.Raw("ok"), result.message)
    }

    @Test
    fun getSuggestUseCaseProducesCorrectItems(): Unit = runTest(dispatcher) {
        val useCase = GetSuggestUseCase()
        val diffMap = mapOf(
            "song-a" to mapOf(
                Difficulty.EZ to 1.0f,
                Difficulty.HD to 5.0f,
                Difficulty.IN to 10.0f,
                Difficulty.AT to 14.0f
            ),
            "song-b" to mapOf(
                Difficulty.EZ to 2.0f,
                Difficulty.HD to 6.0f,
                Difficulty.IN to 11.0f,
                Difficulty.AT to 15.0f
            )
        )
        val nameMap = mapOf("song-a" to "Song A", "song-b" to "Song B")

        // Build 20 B30 records — minimum for beta5 threshold (index 19)
        val b30 = (1..20).map { i ->
            org.kasumi321.ushio.phitracker.domain.model.BestRecord(
                songId = "song-$i",
                songName = "Song $i",
                difficulty = Difficulty.IN,
                score = 900_000 + i * 1000,
                accuracy = 90f + i * 0.3f,
                isFullCombo = false,
                chartConstant = 10.0f,
                rks = 6.0f + i * 0.1f,
                isPhi = false
            )
        }
        // threshold = b30[19].rks = 6.0 + 20*0.1 = 8.0

        // Records: song-a IN has acc 85 (below threshold), song-b IN has acc 95 (above)
        val records = mapOf(
            "song-a" to SongRecord(
                songId = "song-a",
                levels = mapOf(Difficulty.IN to LevelRecord(850_000, 85f, false))
            ),
            "song-b" to SongRecord(
                songId = "song-b",
                levels = mapOf(Difficulty.IN to LevelRecord(950_000, 95f, true))
            )
        )

        val suggestions = useCase(b30, records, diffMap, nameMap, limit = 30)

        // song-a IN should be suggested (acc 85, cc 10: rks = 4.44 < 8.0 threshold)
        val songaSuggest = suggestions.find { it.songId == "song-a" && it.difficulty == Difficulty.IN }
        assertNotNull(songaSuggest, "song-a IN should be suggested")
        assertEquals("Song A", songaSuggest.songName)
        assertEquals(85f, songaSuggest.currentAcc)
        assertTrue(songaSuggest.targetAcc > 85f)
        assertEquals(10.0f, songaSuggest.chartConstant)
        assertFalse(songaSuggest.isFullCombo)

        // song-b IN has currentRks = ((95-55)/45)^2 * 11 = 8.69 >= threshold 8.0 → NOT suggested
        assertNull(suggestions.find { it.songId == "song-b" && it.difficulty == Difficulty.IN },
            "song-b IN should NOT be suggested (currentRks >= threshold)")
    }

    private fun createChapterFilterViewModel(
        settingsRepository: FakeSettingsRepository,
        songDataProvider: SongDataProvider = SongDataProvider(ChapterTestAssetReader, testPlatformPaths)
    ): HomeViewModel {
        val repository = FakePhigrosRepository()
        val illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") }
        val logFileStore = createTestLogFileStore()
        return HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = songDataProvider,
            illustrationProvider = illustrationProvider,
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settingsRepository,
            illustrationPreloadCoordinator = preloadCoordinator(songDataProvider = songDataProvider),
            songDataUpdateCoordinator = fakeSongDataUpdateCoordinator(songDataProvider = songDataProvider)
        ).let(viewModelLifecycle::track)
    }

    private object ChapterTestAssetReader : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "tips.txt" -> "Tip: test"
            "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\nsong-a\tSong A\tComposer\tIllus\t\t\t\t\t1.0\t2.0\t3.0\t4.0\nsong-b\tSong B\tComposer\tIllus\t\t\t\t\t1.0\t2.0\t3.0\t4.0\nsong-c\tSong C\tComposer\tIllus\t\t\t\t\t1.0\t2.0\t3.0\t4.0"
            "infolist.json" -> """{"song-a":{"chapter":"Single"},"song-b":{"chapter":"Collection"},"song-c":{"chapter":"Single"}}"""
            "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    // ---- Phase G: auto-update core tests ----

    @Test
    fun startupAutoCheckEnabledFetchesAndSetsAvailable(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = true)
        val repository = FakePhigrosRepository().apply {
            fetchLatestReleaseResult = Result.success(
                ReleaseInfo(
                    tagName = "v9.9.9",
                    htmlUrl = "https://example.test/release/v9.9.9",
                    prerelease = false,
                    body = "New version available"
                )
            )
        }
        val viewModel = createViewModel(
            settings, RecordingPreloader(),
            appVersionName = "0.1.0",
            repository = repository
        )
        advanceUntilIdle()

        assertEquals(1, repository.fetchLatestReleaseCallCount,
            "Startup auto-check should call fetchLatestRelease exactly once")
        assertEquals(
            listOf(false),
            repository.fetchLatestReleaseIncludePreReleaseValues,
            "Startup auto-check should use includePreRelease=false by default"
        )
        val state = viewModel.uiState.value.sync.updateCheckState
        assertTrue(state is UpdateCheckState.Available,
            "Should be Available when a newer version is returned")
        val available = state
        assertEquals("v9.9.9", available.version)
        assertEquals("https://example.test/release/v9.9.9", available.htmlUrl)
        assertEquals("New version available", available.body)
    }

    @Test
    fun startupAutoCheckDisabledLeavesIdleAndSkipsFetch(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false)
        val repository = FakePhigrosRepository()
        val viewModel = createViewModel(
            settings, RecordingPreloader(),
            appVersionName = "0.1.0",
            repository = repository
        )
        advanceUntilIdle()

        assertEquals(0, repository.fetchLatestReleaseCallCount,
            "Should NOT call fetchLatestRelease when auto-check is disabled")
        assertTrue(viewModel.uiState.value.sync.updateCheckState is UpdateCheckState.Idle,
            "UpdateCheckState should remain Idle")
    }

    @Test
    fun startupSongDataAutoCheckEnabledProbesUpstreamIndependentlyOfAppUpdate(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false)
        val repository = FakePhigrosRepository()
        val updater = FakeSongDataUpdater().apply { upstreamChanged = true }
        val viewModel = createViewModel(
            settings, RecordingPreloader(),
            appVersionName = "0.1.0",
            repository = repository,
            songDataUpdater = updater
        )
        advanceUntilIdle()

        assertEquals(0, repository.fetchLatestReleaseCallCount,
            "App update check stays gated by autoCheckUpdate=false")
        assertEquals(1, updater.upstreamCheckCallCount,
            "Song data upstream probe should run once when its own toggle is on")
        assertTrue(viewModel.uiState.value.songs.songDataUpdateAvailable,
            "Song data update availability should be surfaced when upstream changed")
    }

    @Test
    fun startupSongDataAutoCheckDisabledSkipsUpstreamProbe(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckSongDataUpdate = false)
        val updater = FakeSongDataUpdater().apply { upstreamChanged = true }
        val viewModel = createViewModel(
            settings, RecordingPreloader(),
            appVersionName = "0.1.0",
            repository = FakePhigrosRepository(),
            songDataUpdater = updater
        )
        advanceUntilIdle()

        assertEquals(0, updater.upstreamCheckCallCount,
            "Should NOT probe upstream when the song data auto-check toggle is off")
        assertFalse(viewModel.uiState.value.songs.songDataUpdateAvailable)
    }

    @Test
    fun gameUpdateInfoFallsBackToCachedValueWhenFetchFails(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val cachedJson = """{"version":"3.20.0","versionCode":154,"date":"2026-09-05","changelog":"缓存日志"}"""
        settings.setGameUpdateInfoCache(cachedJson)
        settings.gameUpdateCacheWrites.clear()
        val repository = FakePhigrosRepository().apply {
            gameUpdateInfoResult = Result.failure(IllegalStateException("offline"))
        }
        val viewModel = createViewModel(settings, RecordingPreloader(), repository = repository)
        advanceUntilIdle()

        assertEquals(
            GameUpdateInfo("3.20.0", 154L, "2026-09-05", "缓存日志"),
            viewModel.uiState.value.sync.gameUpdateInfo
        )
        assertTrue(settings.gameUpdateCacheWrites.isEmpty(), "失败的请求不应改写缓存")
    }

    @Test
    fun gameUpdateInfoFetchSuccessWritesCache(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val info = GameUpdateInfo("3.20.0", 154L, "2026-09-05", "修复已知问题")
        val repository = FakePhigrosRepository().apply {
            gameUpdateInfoResult = Result.success(info)
        }
        val viewModel = createViewModel(settings, RecordingPreloader(), repository = repository)
        advanceUntilIdle()

        assertEquals(info, viewModel.uiState.value.sync.gameUpdateInfo)
        assertEquals(1, settings.gameUpdateCacheWrites.size)
        assertEquals(
            """{"version":"3.20.0","versionCode":154,"date":"2026-09-05","changelog":"修复已知问题"}""",
            settings.gameUpdateCacheWrites.single()
        )
    }

    @Test
    fun gameUpdateInfoUnchangedFetchDoesNotRewriteCache(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val cachedJson = """{"version":"3.20.0","versionCode":154,"date":"2026-09-05","changelog":"修复已知问题"}"""
        settings.setGameUpdateInfoCache(cachedJson)
        settings.gameUpdateCacheWrites.clear()
        val repository = FakePhigrosRepository().apply {
            gameUpdateInfoResult = Result.success(GameUpdateInfo("3.20.0", 154L, "2026-09-05", "修复已知问题"))
        }
        val viewModel = createViewModel(settings, RecordingPreloader(), repository = repository)
        advanceUntilIdle()

        assertEquals(
            GameUpdateInfo("3.20.0", 154L, "2026-09-05", "修复已知问题"),
            viewModel.uiState.value.sync.gameUpdateInfo
        )
        assertTrue(settings.gameUpdateCacheWrites.isEmpty(), "内容未变化时不应重写缓存")
    }

    private companion object {
        fun createTestLogFileStore(): LogFileStore {
            val testDir = "/tmp/phi_tracker_test_${kotlin.random.Random.nextInt()}"
            return LogFileStore(
                fileSystem = FileSystem.SYSTEM,
                runtimeLogDir = "$testDir/runtime_logs".toPath(),
                crashLogDir = "$testDir/crash_logs".toPath()
            )
        }

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

        fun saveWithRks(rks: Float): Save = Save(
            gameRecord = emptyMap(),
            gameProgress = GameProgress(
                isFirstRun = false, legacyChapterFinished = false,
                alreadyShowCollectionTip = false, alreadyShowAutoUnlockINTip = false,
                completed = "", songUpdateInfo = 0, challengeModeRank = 0,
                money = emptyList(), unlockFlagOfSpasmodic = 0,
                unlockFlagOfIgallta = 0, unlockFlagOfRrharil = 0,
                flagOfSongRecordKey = 0, randomVersionUnlocked = null,
                chapter8UnlockBegin = null, chapter8UnlockSecondPhase = null,
                chapter8Passed = null, chapter8SongUnlocked = null
            ),
            user = UserSettings(showPlayerId = false, selfIntro = "", avatar = "", background = ""),
            summary = Summary(
                saveVersion = 1, challengeModeRank = 0,
                rks = rks, gameVersion = 1, avatar = "",
                progress = emptyList()
            )
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

        fun syncSnapshot(
            id: Long,
            timestamp: Long,
            dataCount: Int,
            rks: Float = 15f,
            nickname: String = "N"
        ): SyncSnapshot = SyncSnapshot(
            id = id,
            timestamp = timestamp,
            rks = rks,
            nickname = nickname,
            dataCount = dataCount,
            lastSyncedSongId = null,
            lastSyncedDifficulty = null,
            lastSyncedScore = null,
            lastSyncedAccuracy = null
        )

        fun syncHistory(
            snapshotId: Long,
            songId: String,
            difficulty: String,
            score: Int = 900_000,
            accuracy: Float = 90f,
            isFullCombo: Boolean = false,
            timestamp: Long = 1_000L
        ): SongSyncHistoryEntry = SongSyncHistoryEntry(
            id = 0L,
            snapshotId = snapshotId,
            songId = songId,
            difficulty = difficulty,
            score = score,
            accuracy = accuracy,
            isFullCombo = isFullCombo,
            timestamp = timestamp
        )
    }
}
