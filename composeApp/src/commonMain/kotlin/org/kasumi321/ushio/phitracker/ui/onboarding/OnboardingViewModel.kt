package org.kasumi321.ushio.phitracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.setAppLocale
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import org.kasumi321.ushio.phitracker.ui.utils.resolve
import org.kasumi321.ushio.phitracker.ui.utils.toCatalogRepairErrorText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.settings_api_token_save_failed
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairEntryPoint
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairFailure
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairSession
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairStartResult
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairStatus
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadRunStatus
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadSession
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdateCoordinator
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.ui.b30.B30ExportCardStyle
import org.kasumi321.ushio.phitracker.ui.settings.SettingsConstants

enum class OnboardingStep {
    Welcome,
    Language,
    LoginChoice,
    SongDataUpdate,
    IllustrationPreload,
    B30Style,
    AutoUpdate,
    Completion
}

enum class OnboardingLoginChoice {
    Undecided,
    Login,
    Guest
}

enum class OnboardingPreloadChoice {
    Undecided,
    Download,
    Skip
}

/** State machine of the inline song database check/update in its own step. */
enum class OnboardingSongDataStatus {
    Idle,
    Checking,
    UpToDate,
    UpdateAvailable,
    Updating,
    Updated,
    Failed
}

/** State machine of the inline illustration download in the preload step. */
enum class OnboardingPreloadStatus {
    Idle,
    Running,
    Succeeded,
    Failed
}

data class OnboardingUiState(
    /**
     * Wizard steps for the current state. Constant for now; later phases may
     * insert conditional steps (e.g. a B30 style step when already logged in),
     * so navigation always goes through this list instead of enum ordinals.
     */
    val steps: List<OnboardingStep> = listOf(
        OnboardingStep.Language,
        OnboardingStep.LoginChoice,
        OnboardingStep.IllustrationPreload,
        OnboardingStep.AutoUpdate
    ),
    val stepIndex: Int = 0,
    val appLanguage: String = SettingsConstants.LANGUAGE_SYSTEM,
    val themeMode: Int = 0,
    val paletteStyleName: String = "TonalSpot",
    val hazeBlurEnabled: Boolean = true,
    val hazeBlurStrength: Float = 0.75f,
    val loginChoice: OnboardingLoginChoice = OnboardingLoginChoice.Undecided,
    /** True once the inline login flow in the login step reports success. */
    val loggedIn: Boolean = false,
    val apiEnabled: Boolean = false,
    val apiUserId: String = "",
    val apiPlatform: String = "",
    val apiPlatformId: String = "",
    val apiToken: String = "",
    val b30CardStyle: B30ExportCardStyle = B30ExportCardStyle.Classic,
    val b30ThemeFollowGlobal: Boolean = true,
    val b30ExportThemeMode: Int? = null,
    val b30Records: List<BestRecord> = emptyList(),
    val preloadChoice: OnboardingPreloadChoice = OnboardingPreloadChoice.Undecided,
    val preloadStatus: OnboardingPreloadStatus = OnboardingPreloadStatus.Idle,
    val preloadCompleted: Int = 0,
    val preloadTotal: Int = 0,
    val preloadCurrentSong: String? = null,
    /** Total song count, disclosed before the inline illustration download starts. */
    val illustrationTotalCount: Int = 0,
    val songDataStatus: OnboardingSongDataStatus = OnboardingSongDataStatus.Idle,
    val songDataCompleted: Int = 0,
    val songDataTotal: Int = 0,
    val songDataCurrentFile: String? = null,
    val songDataPartialIllustrationFailures: Int = 0,
    val autoCheckUpdate: Boolean = true,
    val autoCheckSongDataUpdate: Boolean = true,
    val includePreRelease: Boolean = false,
    /** True when a picked language only applies after a cold restart (iOS). */
    val restartRequiredOnFinish: Boolean = false,
    val isFinishing: Boolean = false,
    val finished: Boolean = false
) {
    val step: OnboardingStep
        get() = steps.getOrElse(stepIndex) { OnboardingStep.Language }

    val canAdvance: Boolean
        get() = when (step) {
            OnboardingStep.Welcome -> true
            OnboardingStep.Language -> true
            // Gate: the login step must be completed (or explicitly skipped as
            // a guest) before moving on — "continue without login" is the skip
            // action, not an accidental tap on Next.
            OnboardingStep.LoginChoice -> loggedIn
            // Only the update run itself blocks advancing; checked/up-to-date/
            // failed states may all be walked past.
            OnboardingStep.SongDataUpdate -> songDataStatus != OnboardingSongDataStatus.Updating
            OnboardingStep.IllustrationPreload -> preloadChoice != OnboardingPreloadChoice.Undecided
            OnboardingStep.B30Style -> true
            OnboardingStep.AutoUpdate -> true
            OnboardingStep.Completion -> true
        }
}

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val illustrationPreloadSession: IllustrationPreloadSession,
    private val getB30UseCase: GetB30UseCase,
    private val songDataProvider: SongDataProvider,
    private val songDataUpdateCoordinator: SongDataUpdateCoordinator,
    private val catalogRepairSession: CatalogRepairSession,
    private val illustrationUriResolver: IllustrationUriResolver,
    private val applyLocale: (String?) -> Boolean = ::setAppLocale,
    private val platformMessage: suspend (UiText) -> Unit = { showPlatformMessage(it.resolve()) }
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var illustrationPreloadDeclined = false

    init {
        observeCatalogRepair()
        viewModelScope.launch {
            val language = settingsRepository.appLanguage.first()
            val autoUpdate = settingsRepository.autoCheckUpdate.first()
            val autoSongDataUpdate = settingsRepository.autoCheckSongDataUpdate.first()
            val preRelease = settingsRepository.includePreRelease.first()
            val themeMode = settingsRepository.themeMode.first()
            val paletteStyleName = settingsRepository.paletteStyleName.first()
            val hazeBlurEnabled = settingsRepository.hazeBlurEnabled.first()
            val hazeBlurStrength = settingsRepository.hazeBlurStrength.first()
            val apiEnabled = settingsRepository.apiEnabled.first()
            val apiUserId = settingsRepository.apiId.first()
            val apiPlatform = settingsRepository.apiPlatform.first()
            val apiPlatformId = settingsRepository.apiPlatformId.first()
            val apiToken = settingsRepository.apiToken.first()
            val b30CardStyle = B30ExportCardStyle.fromStorageKey(settingsRepository.b30CardStyle.first())
            val b30ThemeFollowGlobal = settingsRepository.b30ThemeFollowGlobal.first()
            val b30ExportThemeMode = settingsRepository.b30ExportThemeMode.first()
            _uiState.update {
                refreshed(
                    it.copy(
                        appLanguage = language,
                        autoCheckUpdate = autoUpdate,
                        autoCheckSongDataUpdate = autoSongDataUpdate,
                        includePreRelease = preRelease,
                        themeMode = themeMode,
                        paletteStyleName = paletteStyleName,
                        hazeBlurEnabled = hazeBlurEnabled,
                        hazeBlurStrength = hazeBlurStrength,
                        apiEnabled = apiEnabled,
                        apiUserId = apiUserId,
                        apiPlatform = apiPlatform,
                        apiPlatformId = apiPlatformId,
                        apiToken = apiToken,
                        b30CardStyle = b30CardStyle,
                        b30ThemeFollowGlobal = b30ThemeFollowGlobal,
                        b30ExportThemeMode = b30ExportThemeMode
                    )
                )
            }
        }
        viewModelScope.launch {
            settingsRepository.illustrationPreloadDeclined.collect { declined ->
                illustrationPreloadDeclined = declined
            }
        }
        viewModelScope.launch {
            // The session lives outside this view-model, so its state is the
            // single source of truth when the user leaves this step and comes
            // back (or when a run started on a previous visit is still going).
            illustrationPreloadSession.state.collect { session ->
                val status = when (session.status) {
                    IllustrationPreloadRunStatus.Idle -> OnboardingPreloadStatus.Idle
                    IllustrationPreloadRunStatus.Running -> OnboardingPreloadStatus.Running
                    IllustrationPreloadRunStatus.Succeeded -> OnboardingPreloadStatus.Succeeded
                    IllustrationPreloadRunStatus.Failed -> OnboardingPreloadStatus.Failed
                }
                _uiState.update {
                    refreshed(
                        it.copy(
                            preloadStatus = status,
                            preloadChoice = if (session.status == IllustrationPreloadRunStatus.Idle) {
                                it.preloadChoice
                            } else {
                                OnboardingPreloadChoice.Download
                            },
                            preloadCompleted = session.completed,
                            preloadTotal = session.total,
                            preloadCurrentSong = session.currentSong
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            // Continuous collection for the conditional B30 style step's
            // preview: the inline login syncs AFTER the wizard has started, so
            // a one-shot snapshot would stay empty. Guests simply keep the
            // empty list (their wizard hides the step anyway). A failed flow
            // is swallowed and treated as "no data" — the VM (and with it the
            // collection) dies with the wizard.
            runCatching {
                _uiState.update {
                    refreshed(it.copy(illustrationTotalCount = songDataProvider.getSongs().size))
                }
                getB30UseCase(
                    songDataProvider.getDifficultyMap(),
                    songDataProvider.getSongNameMap()
                ).catch { }.collect { (b30, _) ->
                    _uiState.update { refreshed(it.copy(b30Records = b30)) }
                }
            }
        }
    }

    fun selectLanguage(language: String) {
        viewModelScope.launch {
            AppLogger.event("onboarding", "language_selected", mapOf("language" to language))
            settingsRepository.setAppLanguage(language)
            val restartRequired = applyLocale(language.takeIf { it != SettingsConstants.LANGUAGE_SYSTEM })
            _uiState.update {
                it.copy(
                    appLanguage = language,
                    restartRequiredOnFinish = it.restartRequiredOnFinish || restartRequired
                )
            }
        }
    }

    fun selectLoginChoice(choice: OnboardingLoginChoice) {
        _uiState.update { refreshed(it.copy(loginChoice = choice)) }
        // "Continue without login" is the sanctioned way past the gated login
        // step, so it advances directly; the Next button stays disabled until
        // the inline login reports success.
        if (choice == OnboardingLoginChoice.Guest) {
            _uiState.update { state ->
                refreshed(state.copy(stepIndex = (state.stepIndex + 1).coerceAtMost(state.steps.lastIndex)))
            }
        }
    }

    /** Called by the login step once the inline login flow reports isLoggedIn. */
    fun onLoggedIn() {
        if (_uiState.value.loggedIn) return
        AppLogger.event("onboarding", "inline_login_succeeded")
        _uiState.update { refreshed(it.copy(loggedIn = true)) }
    }

    fun selectPreloadChoice(choice: OnboardingPreloadChoice) {
        _uiState.update { it.copy(preloadChoice = choice) }
    }

    /**
     * Start (or retry) the illustration download. The shared session owns the
     * coroutine, so leaving this step (or the wizard) no longer cancels it.
     */
    fun startIllustrationPreload() {
        if (_uiState.value.preloadStatus == OnboardingPreloadStatus.Running) return
        AppLogger.event("onboarding", "preload_started")
        _uiState.update { refreshed(it.copy(preloadChoice = OnboardingPreloadChoice.Download)) }
        illustrationPreloadSession.start()
    }

    /**
     * Cancel a running download or give up after a failure. Both are treated
     * as "later": the home re-prompt is permanently suppressed and the step
     * resolves to Skip. The UI state is resolved synchronously (the flag write
     * happens in the app-scoped session) so the next tap is never left behind
     * a stale Running/busy state.
     */
    fun cancelIllustrationPreload() {
        val status = _uiState.value.preloadStatus
        if (status != OnboardingPreloadStatus.Running && status != OnboardingPreloadStatus.Failed) return
        AppLogger.event("onboarding", "preload_cancelled")
        illustrationPreloadSession.cancel()
        _uiState.update {
            refreshed(
                it.copy(
                    preloadStatus = OnboardingPreloadStatus.Idle,
                    preloadChoice = OnboardingPreloadChoice.Skip,
                    preloadCompleted = 0,
                    preloadTotal = 0,
                    preloadCurrentSong = null
                )
            )
        }
    }

    /**
     * The preload step's "later" action: stop any run, persist the declined
     * marker through the shared session, and advance immediately. This is also
     * the recovery path after [cancelIllustrationPreload].
     */
    fun skipIllustrationPreload() {
        AppLogger.event("onboarding", "preload_skipped")
        illustrationPreloadSession.decline()
        _uiState.update { state ->
            refreshed(
                state.copy(
                    preloadStatus = OnboardingPreloadStatus.Idle,
                    preloadChoice = OnboardingPreloadChoice.Skip,
                    preloadCompleted = 0,
                    preloadTotal = 0,
                    preloadCurrentSong = null,
                    stepIndex = (state.stepIndex + 1).coerceAtMost(state.steps.lastIndex)
                )
            )
        }
    }

    fun setApiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            AppLogger.event("onboarding", "api_enabled_changed", mapOf("enabled" to enabled.toString()))
            settingsRepository.setApiEnabled(enabled)
            _uiState.update { refreshed(it.copy(apiEnabled = enabled)) }
        }
    }

    fun setApiUserId(userId: String) {
        viewModelScope.launch {
            settingsRepository.setApiId(userId)
            _uiState.update { refreshed(it.copy(apiUserId = userId)) }
        }
    }

    fun setApiPlatform(platform: String) {
        viewModelScope.launch {
            settingsRepository.setApiPlatform(platform)
            _uiState.update { refreshed(it.copy(apiPlatform = platform)) }
        }
    }

    fun setApiPlatformId(platformId: String) {
        viewModelScope.launch {
            settingsRepository.setApiPlatformId(platformId)
            _uiState.update { refreshed(it.copy(apiPlatformId = platformId)) }
        }
    }

    fun setApiToken(token: String) {
        viewModelScope.launch {
            settingsRepository.setApiToken(token)
                .onSuccess {
                    _uiState.update { refreshed(it.copy(apiToken = token)) }
                }
                .onFailure {
                    _uiState.update { refreshed(it.copy(apiToken = settingsRepository.apiToken.first())) }
                    platformMessage(UiText.Res(Res.string.settings_api_token_save_failed))
                }
        }
    }

    fun selectB30CardStyle(style: B30ExportCardStyle) {
        viewModelScope.launch {
            AppLogger.event("onboarding", "b30_card_style_selected", mapOf("style" to style.storageKey))
            settingsRepository.setB30CardStyle(style.storageKey)
            _uiState.update { refreshed(it.copy(b30CardStyle = style)) }
        }
    }

    fun setB30ThemeFollowGlobal(follow: Boolean) {
        viewModelScope.launch {
            AppLogger.event("onboarding", "b30_theme_follow_global_changed", mapOf("follow" to follow.toString()))
            settingsRepository.setB30ThemeFollowGlobal(follow)
            _uiState.update { refreshed(it.copy(b30ThemeFollowGlobal = follow)) }
        }
    }

    fun setB30ExportThemeMode(mode: Int) {
        viewModelScope.launch {
            AppLogger.event("onboarding", "b30_export_theme_mode_changed", mapOf("mode" to mode.toString()))
            settingsRepository.setB30ExportThemeMode(mode)
            _uiState.update { refreshed(it.copy(b30ExportThemeMode = mode)) }
        }
    }

    /**
     * Probe the upstream index (song `info.csv` + collectible set). Runs
     * whenever the user is on the index step, independent of the auto-check
     * toggle — being on the step IS the explicit request. Safe to re-enter: a
     * failed or up-to-date result can be probed again (retry).
     */
    fun checkSongDataUpdate() {
        val status = _uiState.value.songDataStatus
        if (status == OnboardingSongDataStatus.Checking || status == OnboardingSongDataStatus.Updating) return
        viewModelScope.launch {
            _uiState.update { refreshed(it.copy(songDataStatus = OnboardingSongDataStatus.Checking)) }
            songDataUpdateCoordinator.checkUpstreamChanged().fold(
                onSuccess = { changed ->
                    _uiState.update {
                        refreshed(
                            it.copy(
                                songDataStatus = if (changed) OnboardingSongDataStatus.UpdateAvailable
                                else OnboardingSongDataStatus.UpToDate
                            )
                        )
                    }
                },
                onFailure = {
                    _uiState.update { refreshed(it.copy(songDataStatus = OnboardingSongDataStatus.Failed)) }
                }
            )
        }
    }

    /**
     * Download the complete index inline (song files + collectible CSVs) and
     * reconcile added/removed song illustrations, mirroring the home
     * pull-to-refresh path; refreshes the disclosed song count on success.
     */
    private fun observeCatalogRepair() {
        viewModelScope.launch {
            var handledTerminalGeneration = catalogRepairSession.state.value.let {
                if (it.status == CatalogRepairStatus.Running || it.status == CatalogRepairStatus.Cancelling) {
                    -1L
                } else {
                    it.resultGeneration
                }
            }
            catalogRepairSession.state.collect { repair ->
                if (repair.resultOwner != CatalogRepairEntryPoint.Onboarding) {
                    if (_uiState.value.songDataStatus == OnboardingSongDataStatus.Updating) {
                        _uiState.update { refreshed(it.copy(songDataStatus = OnboardingSongDataStatus.Idle)) }
                    }
                    return@collect
                }
                val status = when (repair.status) {
                    CatalogRepairStatus.Running, CatalogRepairStatus.Cancelling ->
                        OnboardingSongDataStatus.Updating
                    CatalogRepairStatus.Succeeded -> {
                        if (handledTerminalGeneration == repair.resultGeneration) return@collect
                        handledTerminalGeneration = repair.resultGeneration
                        OnboardingSongDataStatus.Updated
                    }
                    CatalogRepairStatus.Failed -> {
                        if (handledTerminalGeneration == repair.resultGeneration) return@collect
                        handledTerminalGeneration = repair.resultGeneration
                        OnboardingSongDataStatus.Failed
                    }
                    CatalogRepairStatus.Degraded -> {
                        if (handledTerminalGeneration == repair.resultGeneration) return@collect
                        handledTerminalGeneration = repair.resultGeneration
                        OnboardingSongDataStatus.Updated
                    }
                    CatalogRepairStatus.Idle -> OnboardingSongDataStatus.Idle
                }
                if (status == OnboardingSongDataStatus.Updated) songDataProvider.invalidateCache()
                _uiState.update {
                    refreshed(
                        it.copy(
                            songDataStatus = status,
                            songDataCompleted = repair.completed,
                            songDataTotal = repair.total,
                            songDataCurrentFile = repair.currentItem,
                            songDataPartialIllustrationFailures = repair.partialIllustrationFailures,
                            illustrationTotalCount = if (status == OnboardingSongDataStatus.Updated) {
                                songDataProvider.getSongs().size
                            } else {
                                it.illustrationTotalCount
                            }
                        )
                    )
                }
            }
        }
    }

    fun startSongDataUpdate() {
        if (_uiState.value.songDataStatus == OnboardingSongDataStatus.Checking) return
        if (_uiState.value.songDataStatus == OnboardingSongDataStatus.Updating) return
        if (catalogRepairSession.startUpdate(CatalogRepairEntryPoint.Onboarding) ==
            CatalogRepairStartResult.AlreadyRunning
        ) {
            viewModelScope.launch {
                platformMessage(CatalogRepairFailure.Busy.toCatalogRepairErrorText())
            }
        }
    }

    fun cancelSongDataUpdate() {
        if (catalogRepairSession.state.value.resultOwner == CatalogRepairEntryPoint.Onboarding) {
            catalogRepairSession.cancelRunning()
        }
        _uiState.update { refreshed(it.copy(songDataStatus = OnboardingSongDataStatus.Idle)) }
    }

    /**
     * Thumbnail URI for the B30 style preview. Mirrors the app-wide gating:
     * players who declined the preload keep local-only lookups (blank cards)
     * instead of triggering on-demand downloads.
     */
    fun getLowIllustrationUrl(songId: String): String? {
        if (illustrationPreloadDeclined) return illustrationUriResolver.lowLocalUri(songId)
        return illustrationUriResolver.lowUri(songId)
    }

    fun selectThemeMode(mode: Int) {
        viewModelScope.launch {
            AppLogger.event("onboarding", "theme_mode_selected", mapOf("mode" to mode.toString()))
            settingsRepository.setThemeMode(mode)
            _uiState.update { refreshed(it.copy(themeMode = mode)) }
        }
    }

    fun selectPaletteStyle(styleName: String) {
        viewModelScope.launch {
            AppLogger.event(
                "onboarding",
                "palette_style_selected",
                mapOf("paletteStyle" to styleName)
            )
            settingsRepository.setPaletteStyleName(styleName)
            _uiState.update { refreshed(it.copy(paletteStyleName = styleName)) }
        }
    }

    fun setHazeBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            AppLogger.event("onboarding", "haze_blur_toggled", mapOf("enabled" to enabled.toString()))
            settingsRepository.setHazeBlurEnabled(enabled)
            _uiState.update { refreshed(it.copy(hazeBlurEnabled = enabled)) }
        }
    }

    fun setHazeBlurStrength(strength: Float) {
        viewModelScope.launch {
            settingsRepository.setHazeBlurStrength(strength)
            _uiState.update { refreshed(it.copy(hazeBlurStrength = strength)) }
        }
    }

    fun setAutoCheckUpdate(enabled: Boolean) {
        viewModelScope.launch {
            AppLogger.event("onboarding", "auto_check_update_changed", mapOf("enabled" to enabled.toString()))
            settingsRepository.setAutoCheckUpdate(enabled)
            _uiState.update { it.copy(autoCheckUpdate = enabled) }
        }
    }

    fun setAutoCheckSongDataUpdate(enabled: Boolean) {
        viewModelScope.launch {
            AppLogger.event(
                "onboarding",
                "auto_check_song_data_update_changed",
                mapOf("enabled" to enabled.toString())
            )
            settingsRepository.setAutoCheckSongDataUpdate(enabled)
            _uiState.update { it.copy(autoCheckSongDataUpdate = enabled) }
        }
    }

    fun setIncludePreRelease(enabled: Boolean) {
        viewModelScope.launch {
            AppLogger.event("onboarding", "include_pre_release_changed", mapOf("enabled" to enabled.toString()))
            settingsRepository.setIncludePreRelease(enabled)
            _uiState.update { it.copy(includePreRelease = enabled) }
        }
    }

    fun nextStep() {
        _uiState.update { state ->
            if (!state.canAdvance) return@update state
            refreshed(state.copy(stepIndex = (state.stepIndex + 1).coerceAtMost(state.steps.lastIndex)))
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            // The preload session owns any running download, so stepping back
            // is safe: progress resumes from the shared state on return.
            refreshed(state.copy(stepIndex = (state.stepIndex - 1).coerceAtLeast(0)))
        }
    }

    /**
     * Single funnel for state mutations that keeps [OnboardingUiState.steps]
     * derived from the rest of the state, so a future conditional step only
     * needs to change [buildSteps]. Rebuilding may shrink the list, so the
     * index is re-clamped against it.
     */
    private fun refreshed(state: OnboardingUiState): OnboardingUiState {
        val steps = buildSteps(state)
        return state.copy(steps = steps, stepIndex = state.stepIndex.coerceAtMost(steps.lastIndex))
    }

    private fun buildSteps(state: OnboardingUiState): List<OnboardingStep> = buildList {
        add(OnboardingStep.Welcome)
        add(OnboardingStep.Language)
        add(OnboardingStep.LoginChoice)
        add(OnboardingStep.SongDataUpdate)
        add(OnboardingStep.IllustrationPreload)
        // Only shown to players who completed the inline login: the preview
        // needs the freshly synced B30 cache.
        if (state.loggedIn) add(OnboardingStep.B30Style)
        add(OnboardingStep.AutoUpdate)
        add(OnboardingStep.Completion)
    }

    fun finish() {
        val state = _uiState.value
        if (state.isFinishing || state.finished) return
        // The login step never blocks finishing: an unfinished or skipped
        // inline login simply means "continue without login".
        if (state.preloadChoice == OnboardingPreloadChoice.Undecided) return
        _uiState.update { it.copy(isFinishing = true) }
        viewModelScope.launch {
            when (state.preloadChoice) {
                // A Download choice may still be running in the app-scoped
                // session; finishing must neither cancel it nor write flags —
                // the session owns preloadDone/declined and keeps running.
                OnboardingPreloadChoice.Download -> Unit
                OnboardingPreloadChoice.Skip ->
                    settingsRepository.setIllustrationPreloadDeclined(true)
                OnboardingPreloadChoice.Undecided -> Unit
            }
            settingsRepository.setOnboardingCompleted(true)
            AppLogger.event(
                "onboarding",
                "finished",
                mapOf(
                    "loginChoice" to state.loginChoice.name,
                    "loggedIn" to state.loggedIn.toString(),
                    "preloadChoice" to state.preloadChoice.name,
                    "autoCheckUpdate" to state.autoCheckUpdate.toString(),
                    "autoCheckSongDataUpdate" to state.autoCheckSongDataUpdate.toString(),
                    "includePreRelease" to state.includePreRelease.toString(),
                    "restartRequired" to state.restartRequiredOnFinish.toString()
                )
            )
            _uiState.update { it.copy(isFinishing = false, finished = true) }
        }
    }
}
