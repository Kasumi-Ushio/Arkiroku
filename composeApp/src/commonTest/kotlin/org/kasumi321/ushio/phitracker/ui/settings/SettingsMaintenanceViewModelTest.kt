package org.kasumi321.ushio.phitracker.ui.settings

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import okio.FileSystem
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataUpdater
import org.kasumi321.ushio.phitracker.data.logging.CrashReportExporter
import org.kasumi321.ushio.phitracker.data.logging.LogFileStore
import org.kasumi321.ushio.phitracker.data.logging.RuntimeLogExporter
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdateCoordinator
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import org.kasumi321.ushio.phitracker.data.session.SessionCoordinatorImpl
import org.kasumi321.ushio.phitracker.domain.usecase.CheckForUpdateUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.ui.ViewModelTestLifecycle
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.api_error_missing_fields
import phitracker.composeapp.generated.resources.settings_api_test_failed
import phitracker.composeapp.generated.resources.settings_api_test_ok
import phitracker.composeapp.generated.resources.settings_api_test_partial
import phitracker.composeapp.generated.resources.settings_b30_cache_partial_failed

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsMaintenanceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelLifecycle = ViewModelTestLifecycle()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() {
        viewModelLifecycle.tearDown(dispatcher)
    }

    @Test
    fun apiValidationFailureAndSuccessPreserveMessages(): Unit = runTest(dispatcher) {
        val repository = FakePhigrosRepository()
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()
        viewModel.testApiConnection()
        advanceUntilIdle()
        assertEquals(UiText.Res(Res.string.api_error_missing_fields), viewModel.uiState.value.apiTestMessage)
        viewModel.setApiPlatform("qq")
        viewModel.setApiPlatformId("42")
        viewModel.setApiUserId("7")
        advanceUntilIdle()
        repository.apiStatus = Result.success(JsonObject(emptyMap()))
        repository.bind = Result.success(JsonObject(emptyMap()))
        viewModel.testApiConnection()
        advanceUntilIdle()
        assertEquals(UiText.Res(Res.string.settings_api_test_ok), viewModel.uiState.value.apiTestMessage)
    }

    @Test
    fun apiInitialTestFailureReportsDisconnectedState(): Unit = runTest(dispatcher) {
        val repository = FakePhigrosRepository().apply { apiStatus = Result.failure(IllegalStateException("offline")) }
        val viewModel = createViewModel(repository = repository)
        viewModel.setApiPlatform("qq")
        viewModel.setApiPlatformId("42")
        viewModel.setApiUserId("7")
        advanceUntilIdle()
        viewModel.testApiConnection()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isApiTesting)
        assertEquals(UiText.Res(Res.string.settings_api_test_failed, "offline"), viewModel.uiState.value.apiTestMessage)
    }

    @Test
    fun apiBindFailureReportsConnectedAccountLookupFailure(): Unit = runTest(dispatcher) {
        val repository = FakePhigrosRepository().apply {
            apiStatus = Result.success(JsonObject(emptyMap()))
            bind = Result.failure(IllegalStateException("not bound"))
        }
        val viewModel = createViewModel(repository = repository)
        viewModel.setApiPlatform("qq")
        viewModel.setApiPlatformId("42")
        viewModel.setApiUserId("7")
        advanceUntilIdle()
        viewModel.testApiConnection()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isApiTesting)
        assertEquals(UiText.Res(Res.string.settings_api_test_partial, "not bound"), viewModel.uiState.value.apiTestMessage)
    }

    @Test
    fun partialArtworkCacheCompletesAllItemsAndReportsFailure(): Unit = runTest(dispatcher) {
        val repository = FakePhigrosRepository().apply { cachedSave = b30FixtureSave() }
        val cache = RecordingArtworkCache()
        val viewModel = createViewModel(
            repository = repository,
            artworkCache = cache,
            thumbnailPreloader = object : IllustrationThumbnailPreloader {
                override suspend fun preload(url: String) = if (url.contains("song-b")) Result.failure<Unit>(IllegalStateException("decode failed")) else Result.success(Unit)
            }
        )
        advanceUntilIdle()
        var cacheResult: Result<Unit>? = null
        viewModel.cacheB30StandardArtwork { cacheResult = it }
        advanceUntilIdle()
        assertTrue(requireNotNull(cacheResult).isFailure)
        assertEquals(2, cache.standardDownloads.size)
        assertEquals(2, viewModel.uiState.value.b30ArtworkCacheCompleted)
        assertEquals(UiText.Res(Res.string.settings_b30_cache_partial_failed, 1), viewModel.uiState.value.b30ArtworkCacheError)
    }

    @Test
    fun highResolutionArtworkClearRemovesStoredAndDisplayCacheEntries(): Unit = runTest(dispatcher) {
        val cache = RecordingArtworkCache()
        var clearedUrls: List<String> = emptyList()
        val viewModel = createViewModel(artworkCache = cache, clearCacheUrls = { clearedUrls = it })
        advanceUntilIdle()
        var clearResult: Result<Unit>? = null
        viewModel.clearHighResCache { clearResult = it }
        advanceUntilIdle()
        assertTrue(requireNotNull(clearResult).isSuccess)
        assertTrue(cache.clearedAllStandard)
        assertEquals(2, clearedUrls.size)
    }

    @Test
    fun runtimeAndCrashExportsClearAndActionsEmitNoDuplicates(): Unit = runTest(dispatcher) {
        val viewModel = createViewModel(prepareLogStore = { store ->
            store.fileSystem.write(store.prepareRuntimeLogDir() / "runtime.log") { writeUtf8("runtime-entry") }
            store.fileSystem.write(store.crashLogPath()) { writeUtf8("crash-entry") }
        })
        advanceUntilIdle()
        assertTrue(viewModel.exportRuntimeLogText().contains("runtime-entry"))
        assertTrue(viewModel.exportCrashLogText().contains("crash-entry"))
        assertTrue(viewModel.clearAllLogs())
        assertFalse(viewModel.uiState.value.hasRuntimeLogs)
        assertFalse(viewModel.uiState.value.hasCrashLogs)
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch(dispatcher) { viewModel.events.collect(events::add) }
        runCurrent()
        viewModel.logout()
        runCurrent()
        assertEquals(listOf<SettingsEvent>(SettingsEvent.LoggedOut), events)
        viewModel.resetIllustrationDownload()
        runCurrent()
        assertEquals(listOf(SettingsEvent.LoggedOut, SettingsEvent.RestartRequested), events)
    }

    @Test
    fun emptyArtworkAndSongFailureRemainOwnedBySettings(): Unit = runTest(dispatcher) {
        val viewModel = createViewModel(updaterResult = Result.failure(IllegalStateException("offline")))
        advanceUntilIdle()
        var result: Result<Unit>? = null
        viewModel.cacheB30StandardArtwork { result = it }
        advanceUntilIdle()
        assertTrue(requireNotNull(result).isFailure)
        viewModel.updateSongData()
        advanceUntilIdle()
        assertEquals(UiText.Raw("offline"), viewModel.uiState.value.updateDataError)
        viewModel.dismissUpdateDataError()
        assertEquals(null, viewModel.uiState.value.updateDataError)
    }

    private class FakeCollectibleDataUpdater : CollectibleDataUpdater(
        HttpClient(MockEngine { error("unused") }),
        PlatformPaths("/tmp/settings-test", "/tmp/settings-cache"),
        CollectibleDataProvider(TestAssets)
    ) {
        override suspend fun checkUpstreamChanged(): Result<Boolean> = Result.success(false)
        override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> =
            Result.success(Unit)
    }

    private fun createViewModel(
        repository: FakePhigrosRepository = FakePhigrosRepository(),
        updaterResult: Result<Unit> = Result.success(Unit),
        artworkCache: StandardArtworkCache = EmptyArtworkCache,
        thumbnailPreloader: IllustrationThumbnailPreloader = object : IllustrationThumbnailPreloader { override suspend fun preload(url: String) = Result.success(Unit) },
        clearCacheUrls: suspend (List<String>) -> Unit = {},
        prepareLogStore: (LogFileStore) -> Unit = {}
    ): SettingsViewModel {
        val provider = SongDataProvider(TestAssets)
        val updater = object : SongDataUpdater(HttpClient(MockEngine { error("unused") }), PlatformPaths("/tmp/settings-test", "/tmp/settings-cache"), provider) {
            override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> = updaterResult
        }
        val root = "/tmp/settings-maintenance-${kotlin.random.Random.nextInt()}"
        val store = LogFileStore(FileSystem.SYSTEM, "$root/runtime".toPath(), "$root/crash".toPath()).also(prepareLogStore)
        return SettingsViewModel(
            repository, SessionCoordinatorImpl(repository), FakeSettingsRepository(), CheckForUpdateUseCase(repository), GetB30UseCase(repository), provider,
            SongDataUpdateCoordinator(
                songDataUpdater = updater,
                songDataProvider = provider,
                illustrationProvider = IllustrationProvider(),
                artworkFileCache = artworkCache,
                collectibleDataUpdater = FakeCollectibleDataUpdater(),
                thumbnailPreloader = thumbnailPreloader,
                clearCacheUrls = clearCacheUrls
            ),
            IllustrationProvider(), artworkCache, RuntimeLogExporter(store), CrashReportExporter(store), TipsProvider(TestAssets),
            thumbnailPreloader, clearCacheUrls, {}, {}
        ).let(viewModelLifecycle::track)
    }
}
