package org.kasumi321.ushio.phitracker.ui.onboarding

import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import okio.buffer
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataUpdater
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.platformFileSystem
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadCoordinator
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadSession
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdateCoordinator
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.ui.ViewModelTestLifecycle
import org.kasumi321.ushio.phitracker.ui.b30.B30ExportCardStyle
import org.kasumi321.ushio.phitracker.ui.settings.EmptyArtworkCache
import org.kasumi321.ushio.phitracker.ui.settings.FakePhigrosRepository
import org.kasumi321.ushio.phitracker.ui.settings.FakeSettingsRepository
import org.kasumi321.ushio.phitracker.ui.settings.TestAssets
import org.kasumi321.ushio.phitracker.ui.settings.b30FixtureSave
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModelLifecycle = ViewModelTestLifecycle()

    private val songDataProvider = SongDataProvider(
        TestAssets,
        PlatformPaths("/tmp/onboarding_vm_test", "/tmp/onboarding_vm_test_cache")
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = viewModelLifecycle.tearDown(dispatcher)

    private fun viewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        coordinator: IllustrationPreloadCoordinator = preloadCoordinator(),
        repository: FakePhigrosRepository = FakePhigrosRepository(),
        songDataProvider: SongDataProvider = this.songDataProvider,
        songDataUpdateCoordinator: SongDataUpdateCoordinator = songDataCoordinator(songDataProvider),
        illustrationUriResolver: IllustrationUriResolver = previewResolver(),
        applyLocale: (String?) -> Boolean = { false },
        preloadSession: IllustrationPreloadSession? = null
    ): OnboardingViewModel = viewModelLifecycle.track(
        OnboardingViewModel(
            settingsRepository = settings,
            illustrationPreloadSession = preloadSession ?: IllustrationPreloadSession(
                coordinator = coordinator,
                settingsRepository = settings,
                scope = CoroutineScope(dispatcher)
            ),
            getB30UseCase = GetB30UseCase(repository),
            songDataProvider = songDataProvider,
            songDataUpdateCoordinator = songDataUpdateCoordinator,
            illustrationUriResolver = illustrationUriResolver,
            applyLocale = applyLocale
        )
    )

    private fun songDataCoordinator(
        provider: SongDataProvider,
        updater: SongDataUpdater = FakeSongDataUpdater(provider),
        artworkCache: StandardArtworkCache = EmptyArtworkCache
    ): SongDataUpdateCoordinator = SongDataUpdateCoordinator(
        songDataUpdater = updater,
        songDataProvider = provider,
        illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
        artworkFileCache = artworkCache,
        collectibleDataUpdater = FakeCollectibleDataUpdater(),
        thumbnailPreloader = SuccessPreloader(),
        clearCacheUrls = {}
    )

    private class FakeCollectibleDataUpdater : CollectibleDataUpdater(
        HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }),
        PlatformPaths(filesDir = "/tmp/unused-collectible", cacheDir = "/tmp/unused-collectible"),
        CollectibleDataProvider(assetReader = object : TextAssetReader {
            override fun readText(name: String): String = error("unused")
        })
    ) {
        override suspend fun checkUpstreamChanged(): Result<Boolean> = Result.success(false)
        override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> =
            Result.success(Unit)
    }

    private fun preloadCoordinator(
        preloader: IllustrationThumbnailPreloader = SuccessPreloader()
    ): IllustrationPreloadCoordinator = IllustrationPreloadCoordinator(
        songDataProvider = songDataProvider,
        illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
        artworkFileCache = EmptyArtworkCache,
        thumbnailPreloader = preloader
    )

    private fun previewResolver(
        artworkCache: StandardArtworkCache = EmptyArtworkCache
    ): IllustrationUriResolver = IllustrationUriResolver(
        artworkCache,
        IllustrationProvider().apply { setBaseUrl("https://example.test") }
    )

    @Test
    fun initialStateLoadsStoredLanguageAndAutoUpdate() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setAppLanguage("en")
        settings.setAutoCheckUpdate(false)

        val vm = viewModel(settings)
        advanceUntilIdle()

        assertEquals("en", vm.uiState.value.appLanguage)
        assertFalse(vm.uiState.value.autoCheckUpdate)
        assertEquals(OnboardingStep.Welcome, vm.uiState.value.step)
        assertTrue(vm.uiState.value.canAdvance)
        assertFalse(vm.uiState.value.finished)
        assertFalse(vm.uiState.value.loggedIn)
    }

    @Test
    fun initialStateLoadsStoredUpdateCheckSettings() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setAutoCheckUpdate(false)
        settings.setAutoCheckSongDataUpdate(false)
        settings.setIncludePreRelease(true)

        val vm = viewModel(settings)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.autoCheckUpdate)
        assertFalse(vm.uiState.value.autoCheckSongDataUpdate)
        assertTrue(vm.uiState.value.includePreRelease)
    }

    @Test
    fun updateCheckSettersPersistToRepositoryAndUpdateState() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)
        advanceUntilIdle()

        vm.setAutoCheckUpdate(false)
        vm.setAutoCheckSongDataUpdate(false)
        vm.setIncludePreRelease(true)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.autoCheckUpdate)
        assertFalse(settings.autoCheckUpdate.first())
        assertFalse(vm.uiState.value.autoCheckSongDataUpdate)
        assertFalse(settings.autoCheckSongDataUpdate.first())
        assertTrue(vm.uiState.value.includePreRelease)
        assertTrue(settings.includePreRelease.first())
    }

    @Test
    fun selectLanguagePersistsAndFlagsRestartWhenPlatformRequires() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings, applyLocale = { true })
        advanceUntilIdle()

        vm.selectLanguage("zh-Hant")
        advanceUntilIdle()

        assertEquals("zh-Hant", vm.uiState.value.appLanguage)
        assertEquals("zh-Hant", settings.appLanguage.first())
        assertTrue(vm.uiState.value.restartRequiredOnFinish)
    }

    @Test
    fun selectLanguageSystemPassesNullToPlatform() = runTest(dispatcher) {
        val received = mutableListOf<String?>()
        val vm = viewModel(applyLocale = { received += it; false })
        advanceUntilIdle()

        vm.selectLanguage("system")
        advanceUntilIdle()

        assertEquals(listOf<String?>(null), received)
        assertFalse(vm.uiState.value.restartRequiredOnFinish)
    }

    @Test
    fun loginStepBlocksNextStepUntilLoggedIn() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.nextStep()
        vm.nextStep()
        assertEquals(OnboardingStep.LoginChoice, vm.uiState.value.step)
        // Gate: Next stays disabled until the inline login reports success.
        assertFalse(vm.uiState.value.canAdvance)
        vm.nextStep()
        assertEquals(OnboardingStep.LoginChoice, vm.uiState.value.step)

        // Both the freshly logged-in and the checkExistingToken-restored path
        // funnel through onLoggedIn, which reopens the gate.
        vm.onLoggedIn()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canAdvance)

        vm.nextStep()
        assertEquals(OnboardingStep.SongDataUpdate, vm.uiState.value.step)
    }

    @Test
    fun selectingGuestAdvancesToNextStep() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.nextStep()
        vm.nextStep()
        vm.selectLoginChoice(OnboardingLoginChoice.Guest)

        assertEquals(OnboardingLoginChoice.Guest, vm.uiState.value.loginChoice)
        assertEquals(OnboardingStep.SongDataUpdate, vm.uiState.value.step)
    }

    @Test
    fun previousStepNeverGoesBeforeFirstStep() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.previousStep()
        assertEquals(OnboardingStep.Welcome, vm.uiState.value.step)

        vm.nextStep()
        vm.previousStep()
        assertEquals(OnboardingStep.Welcome, vm.uiState.value.step)
    }

    @Test
    fun stepNavigationClampsToStepListBounds() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(7, vm.uiState.value.steps.size)
        vm.previousStep()
        assertEquals(0, vm.uiState.value.stepIndex)

        vm.selectLoginChoice(OnboardingLoginChoice.Guest)
        vm.selectPreloadChoice(OnboardingPreloadChoice.Skip)
        vm.onLoggedIn()
        repeat(10) { vm.nextStep() }
        assertEquals(OnboardingStep.Completion, vm.uiState.value.step)
        assertEquals(vm.uiState.value.steps.lastIndex, vm.uiState.value.stepIndex)

        vm.previousStep()
        assertEquals(OnboardingStep.AutoUpdate, vm.uiState.value.step)
    }

    @Test
    fun initialStateLoadsStoredAppearanceSettings() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setThemeMode(2)
        settings.setPaletteStyleName("Vibrant")
        settings.setHazeBlurEnabled(false)
        settings.setHazeBlurStrength(1.25f)

        val vm = viewModel(settings)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.themeMode)
        assertEquals("Vibrant", vm.uiState.value.paletteStyleName)
        assertFalse(vm.uiState.value.hazeBlurEnabled)
        assertEquals(1.25f, vm.uiState.value.hazeBlurStrength)
    }

    @Test
    fun appearanceSettersPersistToRepositoryAndUpdateState() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)
        advanceUntilIdle()

        vm.selectThemeMode(3)
        vm.selectPaletteStyle("Rainbow")
        vm.setHazeBlurEnabled(false)
        vm.setHazeBlurStrength(1.5f)
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.themeMode)
        assertEquals(3, settings.themeMode.first())
        assertEquals("Rainbow", vm.uiState.value.paletteStyleName)
        assertEquals("Rainbow", settings.paletteStyleName.first())
        assertFalse(vm.uiState.value.hazeBlurEnabled)
        assertFalse(settings.hazeBlurEnabled.first())
        assertEquals(1.5f, vm.uiState.value.hazeBlurStrength)
        assertEquals(1.5f, settings.hazeBlurStrength.first())
    }

    @Test
    fun finishWithGuestAndSkipMarksOnboardingDoneAndPreloadDeclined() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)
        advanceUntilIdle()

        vm.selectLoginChoice(OnboardingLoginChoice.Guest)
        vm.selectPreloadChoice(OnboardingPreloadChoice.Skip)
        vm.finish()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.finished)
        assertTrue(settings.preloadDoneWrites.isEmpty())
        assertTrue(settings.illustrationPreloadDeclined.first())
        assertFalse(settings.illustrationPreloadRequested.first())
        assertTrue(settings.onboardingCompleted.first())
    }

    @Test
    fun finishWithDownloadChoiceDoesNotRewritePreloadFlags() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)
        advanceUntilIdle()

        vm.selectPreloadChoice(OnboardingPreloadChoice.Download)
        vm.finish()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.finished)
        // The preload step downloads inline and owns the flags; finish must not
        // set the legacy auto-start flag nor touch the decline flag.
        assertTrue(settings.preloadDoneWrites.isEmpty())
        assertFalse(settings.illustrationPreloadRequested.first())
        assertFalse(settings.illustrationPreloadDeclined.first())
        assertTrue(settings.onboardingCompleted.first())
    }

    @Test
    fun preloadDownloadCompletesAndPersistsDoneWithoutRequestedFlag() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setPreloadDone(false)
        val vm = viewModel(settings)
        advanceUntilIdle()

        vm.startIllustrationPreload()
        advanceUntilIdle()

        assertEquals(OnboardingPreloadStatus.Succeeded, vm.uiState.value.preloadStatus)
        assertEquals(OnboardingPreloadChoice.Download, vm.uiState.value.preloadChoice)
        assertEquals(2, vm.uiState.value.preloadCompleted)
        assertEquals(2, vm.uiState.value.preloadTotal)
        assertEquals("Song B", vm.uiState.value.preloadCurrentSong)
        assertTrue(settings.getPreloadDone())
        assertFalse(settings.illustrationPreloadRequested.first())
        assertFalse(settings.illustrationPreloadDeclined.first())
        assertTrue(vm.uiState.value.canAdvance)
    }

    @Test
    fun preloadCancelDeclinesResolvesToSkipAndAllowsAdvance() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setPreloadDone(false)
        val vm = viewModel(settings, coordinator = preloadCoordinator(GatedPreloader()))
        advanceUntilIdle()
        vm.onLoggedIn()
        vm.nextStep()
        vm.nextStep()
        vm.nextStep()
        vm.nextStep()
        assertEquals(OnboardingStep.IllustrationPreload, vm.uiState.value.step)

        vm.startIllustrationPreload()
        runCurrent()

        assertEquals(OnboardingPreloadStatus.Running, vm.uiState.value.preloadStatus)
        assertTrue(vm.uiState.value.canAdvance, "a running download must not block the wizard")

        vm.cancelIllustrationPreload()
        advanceUntilIdle()

        assertEquals(OnboardingPreloadStatus.Idle, vm.uiState.value.preloadStatus)
        assertEquals(OnboardingPreloadChoice.Skip, vm.uiState.value.preloadChoice)
        assertTrue(settings.illustrationPreloadDeclined.first())
        assertFalse(settings.getPreloadDone())
        assertTrue(vm.uiState.value.canAdvance)
    }

    @Test
    fun preloadFailureAllowsRetryThenSucceeds() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setPreloadDone(false)
        // Two songs fail in the first round, succeed on retry.
        val vm = viewModel(settings, coordinator = preloadCoordinator(FailingPreloader(failuresLeft = 2)))
        advanceUntilIdle()
        vm.onLoggedIn()
        vm.nextStep()
        vm.nextStep()
        vm.nextStep()
        vm.nextStep()
        assertEquals(OnboardingStep.IllustrationPreload, vm.uiState.value.step)

        vm.startIllustrationPreload()
        advanceUntilIdle()

        assertEquals(OnboardingPreloadStatus.Failed, vm.uiState.value.preloadStatus)
        assertTrue(vm.uiState.value.canAdvance, "a failed download must not block the wizard")
        assertFalse(settings.illustrationPreloadDeclined.first())
        assertFalse(settings.getPreloadDone())

        vm.startIllustrationPreload()
        advanceUntilIdle()

        assertEquals(OnboardingPreloadStatus.Succeeded, vm.uiState.value.preloadStatus)
        assertTrue(settings.getPreloadDone())
        assertTrue(vm.uiState.value.canAdvance)
    }

    @Test
    fun preloadFailedThenSkippedDeclinesAndResolvesToSkip() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setPreloadDone(false)
        val vm = viewModel(settings, coordinator = preloadCoordinator(FailingPreloader(failuresLeft = 99)))
        advanceUntilIdle()

        vm.startIllustrationPreload()
        advanceUntilIdle()
        assertEquals(OnboardingPreloadStatus.Failed, vm.uiState.value.preloadStatus)

        vm.cancelIllustrationPreload()
        advanceUntilIdle()

        assertEquals(OnboardingPreloadStatus.Idle, vm.uiState.value.preloadStatus)
        assertEquals(OnboardingPreloadChoice.Skip, vm.uiState.value.preloadChoice)
        assertTrue(settings.illustrationPreloadDeclined.first())
        assertTrue(vm.uiState.value.canAdvance)
    }

    @Test
    fun preloadKeepsRunningInBackgroundWhenLeavingTheStep() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setPreloadDone(false)
        val vm = viewModel(settings, coordinator = preloadCoordinator(GateSecondPreloader()))
        advanceUntilIdle()
        vm.onLoggedIn()

        repeat(4) { vm.nextStep() }
        assertEquals(OnboardingStep.IllustrationPreload, vm.uiState.value.step)

        vm.startIllustrationPreload()
        advanceUntilIdle()
        assertEquals(OnboardingPreloadStatus.Running, vm.uiState.value.preloadStatus)
        assertEquals(1, vm.uiState.value.preloadCompleted)
        assertEquals(2, vm.uiState.value.preloadTotal)

        // Leaving the step neither cancels the shared session nor resets its
        // progress; coming back restores the same running state.
        vm.nextStep()
        assertEquals(OnboardingStep.B30Style, vm.uiState.value.step)
        advanceUntilIdle()
        assertEquals(OnboardingPreloadStatus.Running, vm.uiState.value.preloadStatus)
        assertEquals(1, vm.uiState.value.preloadCompleted)

        vm.previousStep()
        assertEquals(OnboardingStep.IllustrationPreload, vm.uiState.value.step)
        assertEquals(OnboardingPreloadStatus.Running, vm.uiState.value.preloadStatus)
        assertEquals(1, vm.uiState.value.preloadCompleted)
        assertEquals(2, vm.uiState.value.preloadTotal)
        assertFalse(settings.getPreloadDone())

        vm.cancelIllustrationPreload()
        advanceUntilIdle()
        assertTrue(settings.illustrationPreloadDeclined.first())
    }

    @Test
    fun preloadSessionSurvivesViewModelRecreation() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setPreloadDone(false)
        val coordinator = preloadCoordinator(GateSecondPreloader())
        val session = IllustrationPreloadSession(
            coordinator = coordinator,
            settingsRepository = settings,
            scope = CoroutineScope(dispatcher)
        )
        val first = viewModel(settings, coordinator = coordinator, preloadSession = session)
        advanceUntilIdle()

        first.startIllustrationPreload()
        advanceUntilIdle()
        assertEquals(OnboardingPreloadStatus.Running, first.uiState.value.preloadStatus)
        assertEquals(1, first.uiState.value.preloadCompleted)

        // Simulate the wizard destination going away: only the view-model
        // scope is cancelled, the app-scoped session must keep running.
        first.viewModelScope.cancel()
        advanceUntilIdle()

        val recreated = viewModel(settings, coordinator = coordinator, preloadSession = session)
        advanceUntilIdle()
        assertEquals(OnboardingPreloadStatus.Running, recreated.uiState.value.preloadStatus)
        assertEquals(1, recreated.uiState.value.preloadCompleted)
        assertEquals(2, recreated.uiState.value.preloadTotal)
        assertEquals(OnboardingPreloadChoice.Download, recreated.uiState.value.preloadChoice)

        session.decline()
        advanceUntilIdle()
        assertTrue(settings.illustrationPreloadDeclined.first())
        assertFalse(settings.getPreloadDone())
    }

    @Test
    fun cancelThenSkipAdvancesToTheNextStep() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setPreloadDone(false)
        val vm = viewModel(settings, coordinator = preloadCoordinator(GatedPreloader()))
        advanceUntilIdle()
        vm.onLoggedIn()
        repeat(4) { vm.nextStep() }
        assertEquals(OnboardingStep.IllustrationPreload, vm.uiState.value.step)

        vm.startIllustrationPreload()
        runCurrent()
        assertEquals(OnboardingPreloadStatus.Running, vm.uiState.value.preloadStatus)

        vm.cancelIllustrationPreload()
        advanceUntilIdle()
        assertEquals(OnboardingPreloadStatus.Idle, vm.uiState.value.preloadStatus)
        assertEquals(OnboardingPreloadChoice.Skip, vm.uiState.value.preloadChoice)

        // Cancelling resolves the step but stays put; the explicit "later"
        // action is what advances.
        assertEquals(OnboardingStep.IllustrationPreload, vm.uiState.value.step)

        vm.skipIllustrationPreload()
        advanceUntilIdle()
        assertEquals(OnboardingStep.B30Style, vm.uiState.value.step)
        assertTrue(settings.illustrationPreloadDeclined.first())
        assertFalse(settings.getPreloadDone())
    }

    @Test
    fun finishIsAllowedWhilePreloadRunsAndLeavesTheSessionAlive() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setPreloadDone(false)
        val vm = viewModel(settings, coordinator = preloadCoordinator(GatedPreloader()))
        advanceUntilIdle()
        vm.onLoggedIn()
        repeat(4) { vm.nextStep() }
        vm.startIllustrationPreload()
        runCurrent()
        assertEquals(OnboardingPreloadStatus.Running, vm.uiState.value.preloadStatus)

        vm.nextStep()
        vm.nextStep()
        vm.nextStep()
        assertEquals(OnboardingStep.Completion, vm.uiState.value.step)
        vm.finish()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.finished)
        assertFalse(settings.getPreloadDone())
        assertFalse(settings.illustrationPreloadDeclined.first())
    }

    @Test
    fun stepListExcludesB30StyleWhenLoggedOut() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(7, vm.uiState.value.steps.size)
        assertEquals(OnboardingStep.Welcome, vm.uiState.value.steps.first())
        assertFalse(OnboardingStep.B30Style in vm.uiState.value.steps)
        assertEquals(OnboardingStep.Completion, vm.uiState.value.steps.last())
    }

    @Test
    fun stepListInsertsB30StyleAfterPreloadWhenLoggedIn() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onLoggedIn()
        advanceUntilIdle()

        assertEquals(
            listOf(
                OnboardingStep.Welcome,
                OnboardingStep.Language,
                OnboardingStep.LoginChoice,
                OnboardingStep.SongDataUpdate,
                OnboardingStep.IllustrationPreload,
                OnboardingStep.B30Style,
                OnboardingStep.AutoUpdate,
                OnboardingStep.Completion
            ),
            vm.uiState.value.steps
        )
    }

    @Test
    fun loggedInChangeClampsStepIndexIntoExpandedList() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        // Stand on the gated login step, then log in: the B30 step is inserted
        // while the index stays in bounds and the gate reopens.
        vm.nextStep()
        vm.nextStep()
        assertEquals(OnboardingStep.LoginChoice, vm.uiState.value.step)
        assertFalse(vm.uiState.value.canAdvance)

        vm.onLoggedIn()
        advanceUntilIdle()

        assertEquals(8, vm.uiState.value.steps.size)
        assertTrue(vm.uiState.value.stepIndex <= vm.uiState.value.steps.lastIndex)
        assertTrue(vm.uiState.value.canAdvance)
        assertEquals(OnboardingStep.LoginChoice, vm.uiState.value.step)

        vm.nextStep()
        assertEquals(OnboardingStep.SongDataUpdate, vm.uiState.value.step)
    }

    @Test
    fun finishTriggersFromCompletionStep() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)
        advanceUntilIdle()

        vm.onLoggedIn()
        vm.selectPreloadChoice(OnboardingPreloadChoice.Skip)
        repeat(7) { vm.nextStep() }
        assertEquals(OnboardingStep.Completion, vm.uiState.value.step)

        vm.finish()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.finished)
        assertTrue(settings.illustrationPreloadDeclined.first())
        assertTrue(settings.onboardingCompleted.first())
    }

    @Test
    fun illustrationTotalCountExposedForPreloadDisclosure() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.illustrationTotalCount)
    }

    @Test
    fun songDataStepReportsUpToDateWhenUpstreamUnchanged() = runTest(dispatcher) {
        val updater = FakeSongDataUpdater(songDataProvider, upstreamChanged = false)
        val vm = viewModel(songDataUpdateCoordinator = songDataCoordinator(songDataProvider, updater))
        advanceUntilIdle()
        vm.onLoggedIn()
        vm.nextStep()
        vm.nextStep()
        vm.nextStep()
        assertEquals(OnboardingStep.SongDataUpdate, vm.uiState.value.step)

        vm.checkSongDataUpdate()
        advanceUntilIdle()

        assertEquals(OnboardingSongDataStatus.UpToDate, vm.uiState.value.songDataStatus)
        assertEquals(1, updater.checkCalls)
        // Checked states never block advancing.
        assertTrue(vm.uiState.value.canAdvance)
    }

    @Test
    fun songDataUpdateBlocksNextWhileRunningAndRefreshesSongCountOnSuccess() = runTest(dispatcher) {
        val suffix = kotlin.random.Random.nextInt(Int.MAX_VALUE)
        val paths = PlatformPaths("/tmp/onboarding_sd_$suffix", "/tmp/onboarding_sd_cache_$suffix")
        val provider = SongDataProvider(TestAssets, paths)
        val updater = FakeSongDataUpdater(
            provider,
            paths,
            upstreamChanged = true,
            writeThreeSongCsv = true
        )
        updater.updateGate = CompletableDeferred()
        val vm = viewModel(
            songDataProvider = provider,
            songDataUpdateCoordinator = songDataCoordinator(provider, updater)
        )
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.illustrationTotalCount)

        vm.onLoggedIn()
        vm.nextStep()
        vm.nextStep()
        vm.nextStep()
        assertEquals(OnboardingStep.SongDataUpdate, vm.uiState.value.step)

        vm.checkSongDataUpdate()
        advanceUntilIdle()
        assertEquals(OnboardingSongDataStatus.UpdateAvailable, vm.uiState.value.songDataStatus)

        vm.startSongDataUpdate()
        runCurrent()

        assertEquals(OnboardingSongDataStatus.Updating, vm.uiState.value.songDataStatus)
        assertFalse(vm.uiState.value.canAdvance)
        vm.nextStep()
        assertEquals(OnboardingStep.SongDataUpdate, vm.uiState.value.step)

        updater.updateGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(OnboardingSongDataStatus.Updated, vm.uiState.value.songDataStatus)
        assertTrue(vm.uiState.value.canAdvance)
        // The disclosed preload count now reflects the re-read song database.
        assertEquals(3, vm.uiState.value.illustrationTotalCount)
        assertEquals(Triple(4, 4, "info.csv"), updater.lastProgress)
    }

    @Test
    fun songDataCheckFailureAllowsRetry() = runTest(dispatcher) {
        val updater = FakeSongDataUpdater(songDataProvider, failCheck = true)
        val vm = viewModel(songDataUpdateCoordinator = songDataCoordinator(songDataProvider, updater))
        advanceUntilIdle()

        vm.checkSongDataUpdate()
        advanceUntilIdle()
        assertEquals(OnboardingSongDataStatus.Failed, vm.uiState.value.songDataStatus)
        assertTrue(vm.uiState.value.canAdvance)

        updater.failCheck = false
        updater.upstreamChanged = true
        vm.checkSongDataUpdate()
        advanceUntilIdle()

        assertEquals(OnboardingSongDataStatus.UpdateAvailable, vm.uiState.value.songDataStatus)
        assertEquals(2, updater.checkCalls)
    }

    @Test
    fun songDataUpdateFailureFallsBackToFailed() = runTest(dispatcher) {
        val updater = FakeSongDataUpdater(songDataProvider, upstreamChanged = true, failUpdate = true)
        val vm = viewModel(songDataUpdateCoordinator = songDataCoordinator(songDataProvider, updater))
        advanceUntilIdle()

        vm.startSongDataUpdate()
        advanceUntilIdle()

        assertEquals(OnboardingSongDataStatus.Failed, vm.uiState.value.songDataStatus)
        assertTrue(vm.uiState.value.canAdvance)
    }

    @Test
    fun songDataUpdateSkipsIllustrationSyncButStillRefreshesSongCount() = runTest(dispatcher) {
        val suffix = kotlin.random.Random.nextInt(Int.MAX_VALUE)
        val paths = PlatformPaths("/tmp/onboarding_sd_skip_$suffix", "/tmp/onboarding_sd_skip_cache_$suffix")
        val provider = SongDataProvider(TestAssets, paths)
        val updater = FakeSongDataUpdater(
            provider,
            paths,
            upstreamChanged = true,
            writeThreeSongCsv = true
        )
        val recordingCache = RecordingArtworkCache()
        val vm = viewModel(
            songDataProvider = provider,
            songDataUpdateCoordinator = songDataCoordinator(provider, updater, recordingCache)
        )
        advanceUntilIdle()

        vm.checkSongDataUpdate()
        advanceUntilIdle()
        vm.startSongDataUpdate()
        advanceUntilIdle()

        assertEquals(OnboardingSongDataStatus.Updated, vm.uiState.value.songDataStatus)
        assertEquals(3, vm.uiState.value.illustrationTotalCount)
        // Wizard path: no per-diff thumbnail downloads, and no cache cleanup
        // either — nothing already on disk is touched.
        assertTrue(recordingCache.thumbnailDownloads.isEmpty())
        assertTrue(recordingCache.clearedThumbnailIds.isEmpty())
    }

    @Test
    fun b30StyleSettersPersistToRepositoryAndUpdateState() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)
        advanceUntilIdle()

        vm.selectB30CardStyle(B30ExportCardStyle.Poster)
        vm.setB30ThemeFollowGlobal(false)
        vm.setB30ExportThemeMode(3)
        advanceUntilIdle()

        assertEquals(B30ExportCardStyle.Poster, vm.uiState.value.b30CardStyle)
        assertEquals("score_focus", settings.b30CardStyle.first())
        assertFalse(vm.uiState.value.b30ThemeFollowGlobal)
        assertFalse(settings.b30ThemeFollowGlobal.first())
        assertEquals(3, vm.uiState.value.b30ExportThemeMode)
        assertEquals(3, settings.b30ExportThemeMode.first())
    }

    @Test
    fun b30RecordsCollectedFromCachedSave() = runTest(dispatcher) {
        val repository = FakePhigrosRepository()
        repository.cachedSave = b30FixtureSave()

        val vm = viewModel(repository = repository)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.b30Records.size)
    }

    @Test
    fun b30RecordsRefreshWhenCachedSaveArrivesAfterLogin() = runTest(dispatcher) {
        val repository = FakePhigrosRepository()
        val vm = viewModel(repository = repository)
        advanceUntilIdle()

        // The inline login sync lands in the cache after the wizard started.
        assertTrue(vm.uiState.value.b30Records.isEmpty())

        repository.cachedSave = b30FixtureSave()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.b30Records.size)
    }

    @Test
    fun previewIllustrationProviderFallsBackToRemoteUnlessPreloadDeclined() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)
        advanceUntilIdle()

        // Not declined: remote fallback, mirroring the app-wide gating.
        assertEquals("https://example.test/illLow/song-a.png", vm.getLowIllustrationUrl("song-a.0"))

        settings.setIllustrationPreloadDeclined(true)
        advanceUntilIdle()

        // Declined: local-only and nothing cached → blank.
        assertNull(vm.getLowIllustrationUrl("song-a.0"))
    }

    @Test
    fun previewIllustrationProviderReturnsCachedThumbnailWhenDeclined() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setIllustrationPreloadDeclined(true)
        val vm = viewModel(
            settings,
            illustrationUriResolver = previewResolver(ThumbnailCache("/local/song-a.png"))
        )
        advanceUntilIdle()

        assertEquals("/local/song-a.png", vm.getLowIllustrationUrl("song-a.0"))
    }

    @Test
    fun preloadReportsCurrentSongNameAndClearsItOnCancel() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setPreloadDone(false)
        // First song completes; the second one stalls, leaving the download
        // running so the state can be observed mid-flight.
        val vm = viewModel(settings, coordinator = preloadCoordinator(GateSecondPreloader()))
        advanceUntilIdle()
        vm.onLoggedIn()
        vm.nextStep()
        vm.nextStep()
        vm.nextStep()
        vm.nextStep()

        vm.startIllustrationPreload()
        runCurrent()

        assertEquals("Song A", vm.uiState.value.preloadCurrentSong)

        vm.cancelIllustrationPreload()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.preloadCurrentSong)
    }

    private class SuccessPreloader : IllustrationThumbnailPreloader {
        override suspend fun preload(url: String): Result<Unit> = Result.success(Unit)
    }

    private class FailingPreloader(
        private var failuresLeft: Int
    ) : IllustrationThumbnailPreloader {
        override suspend fun preload(url: String): Result<Unit> =
            if (failuresLeft-- > 0) Result.failure(IllegalStateException("preload failed"))
            else Result.success(Unit)
    }

    private class GatedPreloader : IllustrationThumbnailPreloader {
        override suspend fun preload(url: String): Result<Unit> {
            awaitCancellation()
        }
    }

    private class GateSecondPreloader : IllustrationThumbnailPreloader {
        private var calls = 0

        override suspend fun preload(url: String): Result<Unit> {
            if (calls++ == 0) return Result.success(Unit)
            awaitCancellation()
        }
    }

    private class ThumbnailCache(
        private val thumbnailUri: String?
    ) : StandardArtworkCache {
        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String = url
        override fun getThumbnailIfPresent(songId: String): String? = thumbnailUri
        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = false
        override fun clearThumbnails(songIds: Iterable<String>) = Unit
        override fun clearAllThumbnails() = Unit
        override suspend fun getOrDownloadStandard(songId: String, url: String): String = url
        override fun getStandardIfPresent(songId: String): String? = null
        override fun clearStandard(songIds: Iterable<String>) = Unit
        override fun clearAllStandard() = Unit
    }

    private class RecordingArtworkCache : StandardArtworkCache {
        val thumbnailDownloads = mutableListOf<Pair<String, String>>()
        val clearedThumbnailIds = mutableListOf<String>()

        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
            thumbnailDownloads += songId to url
            return url
        }

        override fun getThumbnailIfPresent(songId: String): String? = null
        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = false
        override fun clearThumbnails(songIds: Iterable<String>) {
            clearedThumbnailIds += songIds
        }
        override fun clearAllThumbnails() = Unit
        override suspend fun getOrDownloadStandard(songId: String, url: String): String = url
        override fun getStandardIfPresent(songId: String): String? = null
        override fun clearStandard(songIds: Iterable<String>) = Unit
        override fun clearAllStandard() = Unit
    }

    private class FakeSongDataUpdater(
        songDataProvider: SongDataProvider,
        private val paths: PlatformPaths = PlatformPaths(
            "/tmp/onboarding_sd_default_${kotlin.random.Random.nextInt(Int.MAX_VALUE)}",
            "/tmp/onboarding_sd_default_cache_${kotlin.random.Random.nextInt(Int.MAX_VALUE)}"
        ),
        var upstreamChanged: Boolean = false,
        var failCheck: Boolean = false,
        var failUpdate: Boolean = false,
        var writeThreeSongCsv: Boolean = false
    ) : SongDataUpdater(io.ktor.client.HttpClient(), paths, songDataProvider) {
        var checkCalls = 0
            private set
        var updateCalls = 0
            private set
        var lastProgress: Triple<Int, Int, String>? = null
            private set
        /** When set, updateAll suspends here so tests can observe mid-update state. */
        var updateGate: CompletableDeferred<Unit>? = null

        override suspend fun checkUpstreamChanged(): Result<Boolean> {
            checkCalls++
            return if (failCheck) Result.failure(IllegalStateException("offline"))
            else Result.success(upstreamChanged)
        }

        override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> {
            updateCalls++
            if (failUpdate) return Result.failure(IllegalStateException("download failed"))
            onProgress(FILE_NAMES.size, FILE_NAMES.size, "info.csv")
            lastProgress = Triple(FILE_NAMES.size, FILE_NAMES.size, "info.csv")
            updateGate?.await()
            if (writeThreeSongCsv) {
                val dir = paths.filesDir.toPath() / "song_data"
                platformFileSystem().createDirectories(dir)
                val sink = platformFileSystem().sink(dir / "info.csv").buffer()
                try {
                    sink.writeUtf8(THREE_SONG_CSV)
                } finally {
                    sink.close()
                }
            }
            return Result.success(Unit)
        }

        private companion object {
            val THREE_SONG_CSV =
                "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n" +
                    "song-a\tSong A\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0\n" +
                    "song-b\tSong B\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0\n" +
                    "song-c\tSong C\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0"
        }
    }

    @Test
    fun finishAllowedWithoutAnyLoginChoiceAndNotDoubleApplied() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)
        advanceUntilIdle()

        vm.finish()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.finished)
        assertFalse(settings.onboardingCompleted.first())

        vm.selectPreloadChoice(OnboardingPreloadChoice.Skip)
        vm.finish()
        vm.finish()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.finished)
        assertTrue(settings.illustrationPreloadDeclined.first())
    }

    @Test
    fun onLoggedInSetsLoggedInStateOnlyOnce() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loggedIn)

        vm.onLoggedIn()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.loggedIn)
    }

    @Test
    fun initialStateLoadsStoredApiSettings() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setApiEnabled(true)
        settings.setApiId("user-9")
        settings.setApiPlatform("tg")
        settings.setApiPlatformId("88")
        settings.setApiToken("tok")

        val vm = viewModel(settings)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.apiEnabled)
        assertEquals("user-9", vm.uiState.value.apiUserId)
        assertEquals("tg", vm.uiState.value.apiPlatform)
        assertEquals("88", vm.uiState.value.apiPlatformId)
        assertEquals("tok", vm.uiState.value.apiToken)
    }

    @Test
    fun apiSettersPersistToRepositoryAndUpdateState() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings)
        advanceUntilIdle()

        vm.setApiEnabled(true)
        vm.setApiPlatform("tg")
        vm.setApiPlatformId(" 123 ")
        vm.setApiUserId("user-1")
        vm.setApiToken("secret")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.apiEnabled)
        assertTrue(settings.apiEnabled.first())
        assertEquals("tg", vm.uiState.value.apiPlatform)
        assertEquals("tg", settings.apiPlatform.first())
        // The state echoes the raw input; the repository trims on write.
        assertEquals(" 123 ", vm.uiState.value.apiPlatformId)
        assertEquals("123", settings.apiPlatformId.first())
        assertEquals("user-1", vm.uiState.value.apiUserId)
        assertEquals("user-1", settings.apiId.first())
        assertEquals("secret", vm.uiState.value.apiToken)
        assertEquals("secret", settings.apiToken.first())
    }
}
