package org.kasumi321.ushio.phitracker.ui.settings

import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import okio.FileSystem
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.logging.CrashReportExporter
import org.kasumi321.ushio.phitracker.data.logging.LogFileStore
import org.kasumi321.ushio.phitracker.data.logging.RuntimeLogExporter
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataUpdater
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdateCoordinator
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.data.session.SessionCoordinatorImpl
import org.kasumi321.ushio.phitracker.domain.usecase.CheckForUpdateUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.ViewModelTestLifecycle
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelLifecycle = ViewModelTestLifecycle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() {
        viewModelLifecycle.tearDown(dispatcher)
    }

    @Test
    fun requestOnboardingRerunClearsCompletedFlagAndSetsMarker() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setOnboardingCompleted(true)
        val vm = createViewModel(settings = settings)
        advanceUntilIdle()

        vm.requestOnboardingRerun()
        advanceUntilIdle()

        assertEquals(false, settings.onboardingCompleted.first())
        assertEquals(true, settings.onboardingRerunRequested.first())
    }

    @Test
    fun projectionsUpdateImmediatelyAndSurviveDestinationRecreation(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val first = createViewModel(settings = settings)
        advanceUntilIdle()

        first.setThemeMode(2)
        first.setAmoledBlack(true)
        first.setThemeColorSource("image")
        first.setSeedColorArgb(123)
        first.setThemeImageColor("content://theme", 456)
        first.setPaletteStyleName("Vibrant")
        first.setShowB30Overflow(true)
        first.setOverflowCount(17)
        first.setApiEnabled(true)
        first.setUseApiData(true)
        first.setApiUserId(" api-7 ")
        first.setApiPlatform(" qq ")
        first.setApiPlatformId(" 42 ")
        first.setIncludePreRelease(true)
        first.setAutoCheckUpdate(false)
        first.setAutoCheckSongDataUpdate(false)
        first.setCrashNotificationGuideShown()
        advanceUntilIdle()

        val recreated = createViewModel(settings = settings)
        advanceUntilIdle()
        assertEquals(2, recreated.uiState.value.themeMode)
        assertTrue(recreated.uiState.value.amoledBlack)
        assertEquals("image", recreated.uiState.value.themeColorSource)
        assertEquals(123, recreated.uiState.value.seedColorArgb)
        assertEquals("content://theme", recreated.uiState.value.themeImageUri)
        assertEquals(456, recreated.uiState.value.themeImageSeedColorArgb)
        assertEquals("Vibrant", recreated.uiState.value.paletteStyleName)
        assertTrue(recreated.uiState.value.showB30Overflow)
        assertEquals(17, recreated.uiState.value.overflowCount)
        assertTrue(recreated.uiState.value.apiEnabled)
        assertTrue(recreated.uiState.value.useApiData)
        assertEquals("api-7", recreated.uiState.value.apiUserId)
        assertEquals("qq", recreated.uiState.value.apiPlatform)
        assertEquals("42", recreated.uiState.value.apiPlatformId)
        assertTrue(recreated.uiState.value.includePreRelease)
        assertFalse(recreated.uiState.value.autoCheckUpdate)
        assertFalse(recreated.uiState.value.autoCheckSongDataUpdate)
        assertTrue(recreated.uiState.value.crashNotificationGuideShown)
    }

    @Test
    fun manualUpdateStateDoesNotMutateAnotherDestinationOwner(): Unit = runTest(dispatcher) {
        val repository = FakePhigrosRepository().apply {
            release = Result.success(ReleaseInfo("v2.0.0", "https://example.test", false, "notes"))
        }
        val manual = createViewModel(repository = repository)
        val independent = createViewModel(repository = repository)
        advanceUntilIdle()

        manual.checkForUpdate("1.0.0")
        advanceUntilIdle()

        assertTrue(manual.uiState.value.updateCheckState is UpdateCheckState.Available)
        assertTrue(independent.uiState.value.updateCheckState is UpdateCheckState.Idle)
        manual.dismissUpdateResult()
        assertTrue(manual.uiState.value.updateCheckState is UpdateCheckState.Idle)
    }

    @Test
    fun manualUpdateReportsNoUpdateAndDismissesToIdle(): Unit = runTest(dispatcher) {
        val repository = FakePhigrosRepository().apply {
            release = Result.success(ReleaseInfo("v1.0.0", "https://example.test/current", false, null))
        }
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.checkForUpdate("1.0.0")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.updateCheckState is UpdateCheckState.NoUpdate)
        viewModel.dismissUpdateResult()
        assertTrue(viewModel.uiState.value.updateCheckState is UpdateCheckState.Idle)
    }

    @Test
    fun manualUpdateReportsErrorAndDismissesToIdle(): Unit = runTest(dispatcher) {
        val repository = FakePhigrosRepository().apply {
            release = Result.failure(IllegalStateException("rate limited"))
        }
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.checkForUpdate("1.0.0")
        advanceUntilIdle()

        assertEquals(UpdateCheckState.Error("rate limited"), viewModel.uiState.value.updateCheckState)
        viewModel.dismissUpdateResult()
        assertTrue(viewModel.uiState.value.updateCheckState is UpdateCheckState.Idle)
    }

    @Test
    fun cancelledDestinationOwnerStopsObservingBeforeRecreation(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val original = createViewModel(settings = settings)
        advanceUntilIdle()
        original.viewModelScope.cancel()

        settings.setThemeMode(3)
        advanceUntilIdle()

        assertEquals(0, original.uiState.value.themeMode)
        val recreated = createViewModel(settings = settings)
        advanceUntilIdle()
        assertEquals(3, recreated.uiState.value.themeMode)
    }

    @Test
    fun collectibleVersionRefreshesAfterSuccessfulIndexUpdate(): Unit = runTest(dispatcher) {
        var version = "v1"
        val viewModel = createViewModel(collectibleDataVersionProvider = { version })
        advanceUntilIdle()
        assertEquals("v1", viewModel.uiState.value.collectibleDataVersion)

        version = "v2"
        viewModel.updateSongData()
        advanceUntilIdle()

        assertEquals("v2", viewModel.uiState.value.collectibleDataVersion)
    }

    private class FakeCollectibleDataUpdater : CollectibleDataUpdater(
        HttpClient(MockEngine { error("unused") }),
        PlatformPaths("/tmp/settings-test", "/tmp/settings-test-cache"),
        CollectibleDataProvider(TestAssets)
    ) {
        override suspend fun checkUpstreamChanged(): Result<Boolean> = Result.success(false)
        override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> =
            Result.success(Unit)
    }

    private fun createViewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        repository: FakePhigrosRepository = FakePhigrosRepository(),
        updaterResult: Result<Unit> = Result.success(Unit),
        artworkCache: StandardArtworkCache = EmptyArtworkCache,
        thumbnailPreloader: IllustrationThumbnailPreloader = object : IllustrationThumbnailPreloader {
            override suspend fun preload(url: String): Result<Unit> = Result.success(Unit)
        },
        clearCacheUrls: suspend (List<String>) -> Unit = {},
        prepareLogStore: (LogFileStore) -> Unit = {},
        collectibleDataVersionProvider: () -> String = { "" }
    ): SettingsViewModel {
        val provider = SongDataProvider(TestAssets)
        val paths = PlatformPaths("/tmp/settings-test", "/tmp/settings-test-cache")
        val updater = object : SongDataUpdater(HttpClient(MockEngine { error("unused") }), paths, provider) {
            override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> {
                onProgress(1, FILE_NAMES.size, "info.csv")
                return updaterResult
            }
        }
        val root = "/tmp/settings-vm-${kotlin.random.Random.nextInt()}"
        val store = LogFileStore(FileSystem.SYSTEM, "$root/runtime".toPath(), "$root/crash".toPath())
        prepareLogStore(store)
        return SettingsViewModel(
            repository = repository,
            sessionCoordinator = SessionCoordinatorImpl(repository),
            settingsRepository = settings,
            checkForUpdateUseCase = CheckForUpdateUseCase(repository),
            getB30UseCase = GetB30UseCase(repository),
            songDataProvider = provider,
            songDataUpdateCoordinator = SongDataUpdateCoordinator(
                songDataUpdater = updater,
                songDataProvider = provider,
                illustrationProvider = IllustrationProvider(),
                artworkFileCache = artworkCache,
                collectibleDataUpdater = FakeCollectibleDataUpdater(),
                thumbnailPreloader = thumbnailPreloader,
                clearCacheUrls = clearCacheUrls
            ),
            illustrationProvider = IllustrationProvider(),
            artworkFileCache = artworkCache,
            runtimeLogExporter = RuntimeLogExporter(store),
            crashReportExporter = CrashReportExporter(store),
            tipsProvider = TipsProvider(TestAssets),
            thumbnailPreloader = thumbnailPreloader,
            clearCacheUrls = clearCacheUrls,
            clearAllCache = {},
            platformMessage = {},
            collectibleDataVersionProvider = collectibleDataVersionProvider
        ).let(viewModelLifecycle::track)
    }

}
