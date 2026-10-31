package org.kasumi321.ushio.phitracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.math.roundToLong
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.NoOpStandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.getAppMetadata
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairEntryPoint
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairFailure
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairSession
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairStage
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairStartResult
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairStatus
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadCoordinator
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadRunStatus
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadSession
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdateCoordinator
import org.kasumi321.ushio.phitracker.data.song.toCatalogRepairFailure
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.session.SessionCoordinator
import org.kasumi321.ushio.phitracker.domain.session.SessionNotLoggedInException
import org.kasumi321.ushio.phitracker.domain.session.SessionTokenRejectedException
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSongLevelBoundsUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.AnalyzeB30TagsUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.BuildB30RksHistogramUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.CheckForUpdateUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.FetchGameUpdateInfoUseCase
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.update.toUpdateCheckState
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import org.kasumi321.ushio.phitracker.ui.utils.toCatalogRepairErrorText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.settings_catalog_using_bundled
import phitracker.composeapp.generated.resources.settings_update_partial_illustrations
import phitracker.composeapp.generated.resources.api_error_missing_fields
import phitracker.composeapp.generated.resources.b30_tag_analysis_failed
import phitracker.composeapp.generated.resources.songs_preload_partial_failed
import phitracker.composeapp.generated.resources.songs_songdata_checking
import phitracker.composeapp.generated.resources.songs_songdata_downloading
import phitracker.composeapp.generated.resources.songs_songdata_syncing_illustrations
import phitracker.composeapp.generated.resources.songs_songdata_up_to_date
import phitracker.composeapp.generated.resources.songs_songdata_updated
import phitracker.composeapp.generated.resources.songs_songdata_updated_count
import phitracker.composeapp.generated.resources.sync_error_session_expired
import phitracker.composeapp.generated.resources.sync_login_required
import phitracker.composeapp.generated.resources.tools_api_invalid_rank
import phitracker.composeapp.generated.resources.tools_api_invalid_rks
import phitracker.composeapp.generated.resources.tools_api_match_closest
import phitracker.composeapp.generated.resources.tools_api_match_exact
import phitracker.composeapp.generated.resources.tools_api_query_failed
import phitracker.composeapp.generated.resources.tools_api_row_match_status
import phitracker.composeapp.generated.resources.tools_api_row_my_rank
import phitracker.composeapp.generated.resources.tools_api_row_player
import phitracker.composeapp.generated.resources.tools_api_row_requested_rank
import phitracker.composeapp.generated.resources.tools_api_row_returned_rank
import phitracker.composeapp.generated.resources.tools_api_row_target_rks
import phitracker.composeapp.generated.resources.tools_api_row_total
import phitracker.composeapp.generated.resources.tools_api_row_users_above
import phitracker.composeapp.generated.resources.tools_api_summary_part_closest
import phitracker.composeapp.generated.resources.tools_api_summary_part_player
import phitracker.composeapp.generated.resources.tools_api_summary_part_rks
import phitracker.composeapp.generated.resources.tools_api_summary_rank_by_position
import phitracker.composeapp.generated.resources.tools_api_summary_rank_by_user
import phitracker.composeapp.generated.resources.tools_api_summary_rks_rank
import phitracker.composeapp.generated.resources.tools_api_unknown_player

data class ApiToolResult(
    val isLoading: Boolean = false,
    val message: UiText? = null,
    val rows: List<ApiToolRow> = emptyList()
)

data class ApiToolRow(
    val label: UiText,
    val value: UiText
)

private val GameUpdateJson = Json { ignoreUnknownKeys = true }

class HomeViewModel(
    private val repository: PhigrosRepository,
    private val getB30UseCase: GetB30UseCase,
    private val sessionCoordinator: SessionCoordinator,
    private val searchSongUseCase: SearchSongUseCase,
    private val getSongLevelBoundsUseCase: GetSongLevelBoundsUseCase = GetSongLevelBoundsUseCase(),
    private val songDataProvider: SongDataProvider,
    private val illustrationProvider: IllustrationProvider,
    private val tipsProvider: TipsProvider,
    private val settingsRepository: SettingsRepository,
    private val artworkFileCache: StandardArtworkCache = NoOpStandardArtworkCache,
    private val illustrationPreloadCoordinator: IllustrationPreloadCoordinator,
    private val checkForUpdateUseCase: CheckForUpdateUseCase = CheckForUpdateUseCase(repository),
    private val appVersionNameProvider: () -> String = { getAppMetadata().versionName },
    private val analyzeB30TagsUseCase: AnalyzeB30TagsUseCase = AnalyzeB30TagsUseCase(),
    private val fetchGameUpdateInfoUseCase: FetchGameUpdateInfoUseCase = FetchGameUpdateInfoUseCase(repository),
    private val songDataUpdateCoordinator: SongDataUpdateCoordinator,
    private val catalogRepairSession: CatalogRepairSession,
    // Koin passes the app-scoped singleton; the default keeps direct test
    // constructions working (they never start this session).
    private val preloadSession: IllustrationPreloadSession =
        IllustrationPreloadSession(illustrationPreloadCoordinator, settingsRepository),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var b30Job: Job? = null
    private var songDataRefreshJob: Job? = null
    private var tagAnalysisJob: Job? = null
    private var tagAnalysisKey: String? = null
    private var lastAnalysisRecords: List<BestRecord>? = null
    private var observedSessionToken: String? = null
    private var accountAccessGeneration = 0L
    private var illustrationPreloadDeclined = false

    init {
        loadSongs()
        observeCatalogRepair()
        observeB30()
        observeUserProfile()
        checkIllustrationState()
        viewModelScope.launch {
            // The app-scoped session may have been started by onboarding (or
            // survive a minimized home dialog). Its state is the single source
            // for progress and completion; a minimized dialog is never reopened.
            preloadSession.state.collect { session ->
                when (session.status) {
                    IllustrationPreloadRunStatus.Idle ->
                        updateSongs { it.copy(isPreloading = false, preloadCurrentSong = null) }

                    IllustrationPreloadRunStatus.Running ->
                        updateSongs {
                            it.copy(
                                isPreloading = true,
                                preloadCompleted = session.completed,
                                preloadTotal = session.total,
                                preloadCurrentSong = session.currentSong,
                                preloadProgress = if (session.total > 0) {
                                    session.completed.toFloat() / session.total
                                } else {
                                    0f
                                }
                            )
                        }

                    IllustrationPreloadRunStatus.Succeeded ->
                        updateSongs {
                            it.copy(
                                isPreloading = false,
                                showPreloadDialog = false,
                                illustrationReady = true,
                                preloadProgress = 1f,
                                preloadCurrentSong = null
                            )
                        }

                    IllustrationPreloadRunStatus.Failed -> {
                        updateSongs {
                            it.copy(
                                isPreloading = false,
                                showPreloadDialog = false,
                                illustrationReady = true,
                                preloadCurrentSong = null
                            )
                        }
                        updateSync { it.copy(error = UiText.Res(Res.string.songs_preload_partial_failed)) }
                    }
                }
            }
        }
        viewModelScope.launch {
            songDataProvider.cacheInvalidations.collect {
                loadSongs()
                observeB30()
            }
        }

        // Observe settings flows
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(themeMode = mode)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeColorSource.collect { source ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(colorSource = source)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.seedColorArgb.collect { argb ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(seedColorArgb = argb)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeImageSeedColorArgb.collect { argb ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(imageSeedColorArgb = argb)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.paletteStyleName.collect { style ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(paletteStyleName = style)) }
            }
        }

        viewModelScope.launch {
            settingsRepository.showB30Overflow.collect { show ->
                updateB30 { it.copy(showB30Overflow = show) }
            }
        }
        viewModelScope.launch {
            settingsRepository.overflowCount.collect { count ->
                updateB30 { it.copy(overflowCount = count) }
            }
        }
        viewModelScope.launch {
            settingsRepository.illustrationPreloadDeclined.collect { declined ->
                illustrationPreloadDeclined = declined
            }
        }
        // Tool tab: observe sync snapshots
        viewModelScope.launch {
            repository.observeSyncSnapshots().collect { list ->
                updateTools { it.copy(syncSnapshots = list) }
            }
        }
        viewModelScope.launch {
            repository.observeSessionToken().collect { tokenPair ->
                observedSessionToken = tokenPair?.first
                updateTools {
                    it.copy(sessionToken = observedSessionToken.takeIf { _uiState.value.profile.hasAccountAccess })
                }
            }
        }
        // Observe PhiPlugin API settings
        viewModelScope.launch {
            settingsRepository.apiEnabled.collect { enabled ->
                updateTools { it.copy(apiEnabled = enabled) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.useApiData.collect { useApiData ->
                updateTools { it.copy(useApiData = useApiData) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.apiId.collect { apiUserId ->
                updateTools { it.copy(apiUserId = apiUserId) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.apiPlatform.collect { platform ->
                updateTools { it.copy(apiPlatform = platform) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.apiPlatformId.collect { platformId ->
                updateTools { it.copy(apiPlatformId = platformId) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            loadRecentEffectiveSyncHistory()
            loadStats()
        }
        viewModelScope.launch {
            val shouldAutoCheckUpdate = settingsRepository.autoCheckUpdate.first()
            if (shouldAutoCheckUpdate) {
                checkForUpdate(appVersionNameProvider())
            }
            val shouldAutoCheckSongData = settingsRepository.autoCheckSongDataUpdate.first()
            if (shouldAutoCheckSongData) {
                val upstreamResult = songDataUpdateCoordinator.checkUpstreamChanged()
                upstreamResult.fold(
                    onSuccess = { changed ->
                        if (changed) updateSongs { it.copy(songDataUpdateAvailable = true) }
                    },
                    onFailure = { error ->
                        AppLogger.event("data", "song_data_upstream_check_failed", mapOf("error" to (error.message ?: "unknown")), throwable = error)
                    }
                )
            }
        }
        loadGameUpdateInfo()
    }

    private fun updateProfile(transform: (ProfileUiState) -> ProfileUiState) {
        _uiState.update { it.copy(profile = transform(it.profile)) }
    }

    private fun updateSongs(transform: (SongsUiState) -> SongsUiState) {
        _uiState.update { it.copy(songs = transform(it.songs)) }
    }

    private fun updateB30(transform: (B30UiState) -> B30UiState) {
        _uiState.update { it.copy(b30 = transform(it.b30)) }
    }

    private fun updateTools(transform: (ToolsUiState) -> ToolsUiState) {
        _uiState.update { it.copy(tools = transform(it.tools)) }
    }

    private fun updateSync(transform: (SyncUiState) -> SyncUiState) {
        _uiState.update { it.copy(sync = transform(it.sync)) }
    }

    private suspend fun loadStats() {
        val clearCounts = repository.getClearCountsByDifficulty().mapKeys { (difficulty, _) -> difficulty.name }
        val fcCount = repository.getTotalFullComboCount()
        val phiCount = repository.getTotalPhiCount()
        updateProfile {
            it.copy(
                clearCounts = clearCounts,
                fcCount = fcCount,
                phiCount = phiCount
            )
        }
    }

    private fun loadSongs() {
        viewModelScope.launch {
            try {
                val songs = songDataProvider.getSongs().values.toList().sortedBy { it.name }
                val chapters = songs.map { it.chapter }.filter { it.isNotBlank() }.distinct().sorted()
                val bounds = getSongLevelBoundsUseCase(songs)
                updateSongs { state ->
                    if (bounds == null) {
                        state.copy(allSongs = songs, filteredSongs = songs, availableChapters = chapters)
                    } else {
                        // A selection still equal to the previous bounds is the untouched
                        // default: snap it to the new bounds. An explicit selection only
                        // gets clamped into them (e.g. after a song-data update).
                        val untouched = state.minLevel == state.levelMinBound &&
                            state.maxLevel == state.levelMaxBound
                        val newMin = if (untouched) bounds.min else state.minLevel.coerceIn(bounds.min, bounds.max)
                        val newMax = if (untouched) bounds.max else state.maxLevel.coerceIn(bounds.min, bounds.max)
                        state.copy(
                            allSongs = songs,
                            filteredSongs = songs,
                            availableChapters = chapters,
                            levelMinBound = bounds.min,
                            levelMaxBound = bounds.max,
                            minLevel = newMin,
                            maxLevel = newMax
                        )
                    }
                }
                applyFilters()
                AppLogger.event("data", "song_data_loaded", mapOf("count" to songs.size.toString()))
            } catch (e: Exception) {
                AppLogger.event("data", "song_data_load_failed", mapOf("error" to (e.message ?: "unknown")), throwable = e)
            }
        }
    }

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
                if (repair.resultOwner != CatalogRepairEntryPoint.Home) {
                    clearSongDataRefresh()
                    return@collect
                }
                when (repair.status) {
                    CatalogRepairStatus.Running, CatalogRepairStatus.Cancelling -> updateSongs {
                        val progress = if (repair.total > 0) {
                            repair.completed.toFloat() / repair.total
                        } else {
                            null
                        }
                        it.copy(
                            isSongDataRefreshing = true,
                            songDataStatusText = when (repair.stage) {
                                CatalogRepairStage.Checking -> UiText.Res(Res.string.songs_songdata_checking)
                                CatalogRepairStage.Files -> UiText.Res(
                                    Res.string.songs_songdata_downloading,
                                    repair.currentItem,
                                    repair.completed,
                                    repair.total
                                )
                                CatalogRepairStage.Illustrations -> UiText.Res(
                                    Res.string.songs_songdata_syncing_illustrations,
                                    repair.currentItem,
                                    repair.completed,
                                    repair.total
                                )
                            },
                            songDataProgressFraction = progress,
                            songDataMessage = null
                        )
                    }

                    CatalogRepairStatus.Succeeded -> if (handledTerminalGeneration != repair.resultGeneration) {
                        handledTerminalGeneration = repair.resultGeneration
                        val addedCount = repair.addedSongNames.size
                        finishSongDataRefresh(
                            when {
                                repair.partialIllustrationFailures > 0 -> UiText.Res(
                                    Res.string.settings_update_partial_illustrations,
                                    repair.partialIllustrationFailures
                                )
                                addedCount > 0 -> UiText.Res(Res.string.songs_songdata_updated_count, addedCount)
                                else -> UiText.Res(Res.string.songs_songdata_updated)
                            }
                        )
                    }

                    CatalogRepairStatus.Failed -> if (handledTerminalGeneration != repair.resultGeneration) {
                        handledTerminalGeneration = repair.resultGeneration
                        finishSongDataRefresh(repair.failure.toCatalogRepairErrorText())
                    }

                    CatalogRepairStatus.Degraded -> if (handledTerminalGeneration != repair.resultGeneration) {
                        handledTerminalGeneration = repair.resultGeneration
                        finishSongDataRefresh(UiText.Res(Res.string.settings_catalog_using_bundled))
                    }

                    CatalogRepairStatus.Idle -> clearSongDataRefresh()
                }
            }
        }
    }

    fun refreshSongData() {
        if (_uiState.value.songs.isSongDataRefreshing) return
        songDataRefreshJob = viewModelScope.launch {
            updateSongs {
                it.copy(
                    isSongDataRefreshing = true,
                    songDataStatusText = UiText.Res(Res.string.songs_songdata_checking),
                    songDataProgressFraction = null,
                    songDataMessage = null
                )
            }
            val upstreamResult = songDataUpdateCoordinator.checkUpstreamChanged()
            val changed = upstreamResult.getOrElse { error ->
                finishSongDataRefresh(error.toCatalogRepairFailure().toCatalogRepairErrorText())
                return@launch
            }
            if (!changed) {
                finishSongDataRefresh(UiText.Res(Res.string.songs_songdata_up_to_date))
                return@launch
            }
            if (catalogRepairSession.startUpdate(CatalogRepairEntryPoint.Home) ==
                CatalogRepairStartResult.AlreadyRunning
            ) {
                finishSongDataRefresh(CatalogRepairFailure.Busy.toCatalogRepairErrorText())
            }
        }
    }

    fun cancelSongDataRefresh() {
        songDataRefreshJob?.cancel()
        songDataRefreshJob = null
        if (catalogRepairSession.state.value.resultOwner == CatalogRepairEntryPoint.Home) {
            catalogRepairSession.cancelRunning()
        }
        clearSongDataRefresh()
    }

    private fun clearSongDataRefresh() {
        updateSongs {
            it.copy(
                isSongDataRefreshing = false,
                songDataStatusText = null,
                songDataProgressFraction = null
            )
        }
    }

    private fun finishSongDataRefresh(message: UiText) {
        songDataRefreshJob = null
        updateSongs {
            it.copy(
                isSongDataRefreshing = false,
                songDataUpdateAvailable = false,
                songDataStatusText = null,
                songDataProgressFraction = null,
                songDataMessage = message
            )
        }
    }

    fun clearSongDataMessage() {
        updateSongs { it.copy(songDataMessage = null) }
    }

    private fun observeB30() {
        b30Job?.cancel()
        b30Job = viewModelScope.launch {
            val diffMap = songDataProvider.getDifficultyMap()
            val nameMap = songDataProvider.getSongNameMap()

            getB30UseCase(diffMap, nameMap)
                .stateIn(viewModelScope, SharingStarted.Eagerly, Pair(emptyList(), emptyList()))
                .collect { (b30, allRecords) ->
                    val computedRks = RksCalculator.calculateDisplayRks(b30)
                    // Same slot selection as the B30 export histogram and
                    // the tag analysis: only the 3 phi slots plus the 27
                    // best slots count toward RKS.
                    val histogram = if (b30.isEmpty()) {
                        null
                    } else {
                        BuildB30RksHistogramUseCase()(
                            b30.filter { it.isPhi }.take(3) + b30.filter { !it.isPhi }.take(27)
                        )
                    }
                    _uiState.update { state ->
                        state.copy(
                            b30 = state.b30.copy(
                            b30 = b30,
                            allRecords = allRecords,
                                displayRks = if (state.b30.displayRks == 0f) computedRks else state.b30.displayRks,
                                histogram = histogram
                            ),
                            sync = state.sync.copy(isLoading = false)
                        )
                    }
                    refreshTagAnalysis(b30)
                }
        }
    }

    /**
     * Recomputes the B30 chart-tag cluster analysis when the B30 content
     * changes. The chartsTag read endpoints are public, so this runs
     * regardless of the score-API switch. Skips duplicate refreshes keyed
     * by the (song, difficulty) list so recomposition does not refetch.
     */
    private fun refreshTagAnalysis(b30: List<BestRecord>) {
        if (b30.isEmpty()) {
            tagAnalysisJob?.cancel()
            tagAnalysisKey = null
            lastAnalysisRecords = null
            updateB30 { it.copy(tagAnalysis = B30TagAnalysisState()) }
            return
        }
        val records = b30.filter { it.isPhi }.take(3) + b30.filter { !it.isPhi }.take(27)
        val key = records.joinToString("\u0000") { "${it.songId}:${it.difficulty.name}" }
        if (key == tagAnalysisKey) return
        tagAnalysisKey = key
        lastAnalysisRecords = records
        tagAnalysisJob?.cancel()
        updateB30 { it.copy(tagAnalysis = it.tagAnalysis.copy(isLoading = true, error = null)) }
        tagAnalysisJob = viewModelScope.launch {
            val result = repository.getB30ChartTags(records)
            if (tagAnalysisKey != key) return@launch
            result.fold(
                onSuccess = { batch ->
                    updateB30 {
                        it.copy(
                            tagAnalysis = B30TagAnalysisState(
                                analysis = analyzeB30TagsUseCase(records, batch)
                            )
                        )
                    }
                },
                onFailure = {
                    updateB30 {
                        it.copy(
                            tagAnalysis = B30TagAnalysisState(
                                error = UiText.Res(Res.string.b30_tag_analysis_failed)
                            )
                        )
                    }
                }
            )
        }
    }

    fun retryTagAnalysis() {
        tagAnalysisKey = null
        lastAnalysisRecords?.let { refreshTagAnalysis(_uiState.value.b30.b30) }
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            repository.getUserProfile().collect { profile ->
                if (profile == null) {
                    accountAccessGeneration++
                    tagAnalysisJob?.cancel()
                    tagAnalysisKey = null
                    lastAnalysisRecords = null
                    _uiState.update { it.withoutAccountAccess() }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            profile = state.profile.copy(
                                hasAccountAccess = true,
                                nickname = profile.nickname,
                                challengeModeRank = profile.challengeModeRank,
                                moneyString = profile.moneyString,
                                avatarUri = profile.avatarUri
                            ),
                            b30 = state.b30.copy(
                                displayRks = if (profile.rks > 0f) profile.rks else state.b30.displayRks
                            ),
                            tools = state.tools.copy(sessionToken = observedSessionToken)
                        )
                    }
                }
            }
        }
    }

    /**
     * Check both the completion marker and the durable thumbnail set. The old
     * implementation trusted the marker alone even though it only warmed
     * Coil's evictable cache, which made later launches randomly re-download
     * individual illustrations.
     */
    private fun checkIllustrationState() {
        viewModelScope.launch {
            if (preloadSession.state.value.status == IllustrationPreloadRunStatus.Running) {
                // The onboarding wizard left a download running in the shared
                // session; don't prompt for a second, competing run.
                updateSongs { it.copy(illustrationReady = true) }
                return@launch
            }
            val alreadyDone = settingsRepository.getPreloadDone()
            val songIds = songDataProvider.getSongs().keys
            // Per-difficulty variants are part of the durable set too, so
            // installs that preloaded before variants existed get the repair
            // dialog once instead of a permanently blank detail header. Those
            // songs have no flat slot: every resolution lands on a variant.
            val requiredCacheKeys = songIds.flatMap { songId ->
                illustrationProvider.variantDifficulties(songId)
                    .map { illustrationProvider.cacheKey(songId, it) }
                    .ifEmpty { listOf(songId) }
            }
            val thumbnailsPresent = artworkFileCache.hasAllThumbnails(requiredCacheKeys)
            if (alreadyDone && thumbnailsPresent) {
                updateSongs { it.copy(illustrationReady = true) }
                return@launch
            }
            AppLogger.event(
                "cache",
                "thumbnail_sync_required",
                mapOf(
                    "completionMarker" to alreadyDone.toString(),
                    "assetsPresent" to thumbnailsPresent.toString(),
                    "songCount" to songIds.size.toString()
                )
            )
            if (settingsRepository.illustrationPreloadDeclined.first()) {
                // Declined in the onboarding wizard: never nag again; the
                // Settings redownload action clears this flag.
                updateSongs { it.copy(illustrationReady = true) }
                return@launch
            }
            updateSongs { it.copy(showPreloadDialog = true, illustrationReady = true) }
            if (settingsRepository.illustrationPreloadRequested.first()) {
                // The onboarding wizard already asked for the download;
                // start it immediately instead of waiting for another tap
                // on the dialog. Consume the flag so a partial failure
                // falls back to the manual dialog on the next launch.
                settingsRepository.setIllustrationPreloadRequested(false)
                startPreloadIllustrations()
            }
        }
    }

    /**
     * Start the shared preload session and show progress. The session owns the
     * job, so closing the dialog (minimize) does not interrupt the download.
     */
    fun startPreloadIllustrations() {
        updateSongs {
            it.copy(
                showPreloadDialog = true,
                isPreloading = true,
                preloadTotal = 0,
                preloadCompleted = 0,
                preloadProgress = 0f,
                preloadCurrentSong = null
            )
        }
        preloadSession.start()
    }

    /** Close the progress dialog without interrupting the shared session. */
    fun minimizePreload() {
        updateSongs { it.copy(showPreloadDialog = false) }
    }

    /**
     * Reopen the progress dialog from the compact Profile card. A completed
     * run has nothing left to show, so only a running session qualifies.
     */
    fun reopenPreloadDialog() {
        if (!_uiState.value.songs.isPreloading) return
        updateSongs { it.copy(showPreloadDialog = true) }
    }

    /** Cancel the download and reset progress; the session writes the declined marker. */
    fun cancelPreload() {
        preloadSession.cancel()
        updateSongs {
            it.copy(
                showPreloadDialog = false,
                isPreloading = false,
                preloadTotal = 0,
                preloadCompleted = 0,
                preloadProgress = 0f,
                preloadCurrentSong = null,
                illustrationReady = true
            )
        }
    }

    /**
     * Skip preload — mark as handled, no more dialog, illustrations load on-demand.
     */
    fun dismissPreload() {
        viewModelScope.launch {
            settingsRepository.setPreloadDone(true)
            updateSongs {
                it.copy(showPreloadDialog = false, illustrationReady = true)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            updateSync { it.copy(isSyncing = true, error = null) }
            AppLogger.event("sync", "refresh_started")
            try {
                val result = sessionCoordinator.refresh()
                if (result.isSuccess) {
                    val syncResult = result.getOrThrow()

                    if (syncResult.snapshotCreated) {
                        _uiState.update { state ->
                            state.copy(
                                sync = state.sync.copy(isSyncing = false),
                                profile = state.profile.copy(lastSyncTime = syncResult.committedAt)
                            )
                        }
                        loadRecentEffectiveSyncHistory()
                        AppLogger.event(
                            "sync",
                            "refresh_success",
                            mapOf(
                                "changedEntries" to syncResult.changedEntryCount.toString(),
                                "displayRks" to _uiState.value.b30.displayRks.toString()
                            )
                        )
                    } else {
                        // No new changes: keep the latest effective history on
                        // screen instead of clearing it; only the last-sync
                        // timestamp advances to this run.
                        loadRecentEffectiveSyncHistory()
                        _uiState.update { state ->
                            state.copy(
                                sync = state.sync.copy(isSyncing = false),
                                profile = state.profile.copy(lastSyncTime = syncResult.committedAt)
                            )
                        }
                        AppLogger.event("sync", "refresh_success", mapOf("changedEntries" to "0"))
                    }
                    updateProfile { it.copy(lastSyncTime = syncResult.committedAt) }
                    // Refresh stats
                    loadStats()
                    if (_uiState.value.tools.apiEnabled && _uiState.value.tools.useApiData) {
                        refreshApiToolData()
                    }
                } else {
                    updateSync {
                        it.copy(
                            isSyncing = false,
                            error = mapRefreshError(result.exceptionOrNull())
                        )
                    }
                    AppLogger.event("sync", "refresh_failed", mapOf("error" to (result.exceptionOrNull()?.message ?: "unknown")), throwable = result.exceptionOrNull())
                }
            } catch (e: CancellationException) {
                // Logout cancels an in-flight refresh; the session UI is about
                // to be torn down, so this must not surface as an error.
                throw e
            } catch (e: Exception) {
                updateSync {
                    it.copy(isSyncing = false, error = e.message?.let(UiText::Raw))
                }
                AppLogger.event("sync", "refresh_failed", mapOf("error" to (e.message ?: "unknown")), throwable = e)
            }
        }
    }

    private fun mapRefreshError(throwable: Throwable?): UiText? = when (throwable) {
        is SessionNotLoggedInException -> UiText.Res(Res.string.sync_login_required)
        is SessionTokenRejectedException -> UiText.Res(Res.string.sync_error_session_expired)
        null -> null
        else -> throwable.message?.let(UiText::Raw)
    }

    private suspend fun loadSyncRecordsForSnapshot(snapshotId: Long) {
        val songs = songDataProvider.getSongs()
        val recentHistory = repository.getSyncHistoryForSnapshot(snapshotId)
        val recentRecords = recentHistory.mapNotNull { entry ->
            val difficulty = runCatching { Difficulty.valueOf(entry.difficulty) }.getOrNull()
                ?: return@mapNotNull null
            val song = songs[entry.songId]
            val chartConstant = song?.difficulties?.get(difficulty) ?: 0f
            val rks = RksCalculator.calculateSingleRks(entry.accuracy, chartConstant)
            BestRecord(
                songId = entry.songId,
                songName = song?.name ?: entry.songId,
                difficulty = difficulty,
                score = entry.score,
                accuracy = entry.accuracy,
                isFullCombo = entry.isFullCombo,
                chartConstant = chartConstant,
                rks = rks,
                isPhi = entry.accuracy >= 100f
            )
        }

        updateProfile {
            it.copy(
                recentSyncedRecords = recentRecords,
                lastSyncedRecord = recentRecords.firstOrNull()
            )
        }
    }

    private suspend fun loadRecentEffectiveSyncHistory(limit: Int = 3) {
        val songs = songDataProvider.getSongs()
        val snapshots = repository.getSyncSnapshotsOnce()
        val effectiveSnapshots = mutableListOf<Pair<SyncSnapshot, List<SongSyncHistoryEntry>>>()

        for (snapshot in snapshots) {
            val entries = repository.getSyncHistoryForSnapshot(snapshot.id)
            if (entries.isNotEmpty()) {
                effectiveSnapshots.add(snapshot to entries)
            }
            if (effectiveSnapshots.size >= limit) break
        }

        val recentRecords = effectiveSnapshots.flatMap { (_, entries) -> entries }.mapNotNull { entry ->
            val difficulty = runCatching { Difficulty.valueOf(entry.difficulty) }.getOrNull()
                ?: return@mapNotNull null
            val song = songs[entry.songId]
            val chartConstant = song?.difficulties?.get(difficulty) ?: 0f
            val rks = RksCalculator.calculateSingleRks(entry.accuracy, chartConstant)
            BestRecord(
                songId = entry.songId,
                songName = song?.name ?: entry.songId,
                difficulty = difficulty,
                score = entry.score,
                accuracy = entry.accuracy,
                isFullCombo = entry.isFullCombo,
                chartConstant = chartConstant,
                rks = rks,
                isPhi = entry.accuracy >= 100f
            )
        }

        updateProfile {
            // Home summary uses the first entry of the newest effective sync snapshot,
            // while the history list keeps every entry from the latest three effective snapshots.
            it.copy(
                lastSyncTime = effectiveSnapshots.firstOrNull()?.first?.timestamp,
                recentSyncedRecords = recentRecords,
                lastSyncedRecord = recentRecords.firstOrNull()
            )
        }
    }

    fun searchSongs(query: String) {
        updateSongs { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun toggleChapter(chapter: String) {
        updateSongs { state ->
            val newChapters = state.selectedChapters.toMutableSet().apply {
                if (contains(chapter)) remove(chapter) else add(chapter)
            }
            state.copy(selectedChapters = newChapters)
        }
        applyFilters()
    }

    fun clearChapters() {
        updateSongs {
            it.copy(selectedChapters = emptySet())
        }
        applyFilters()
    }

    fun filterByDifficulty(diff: Difficulty?) {
        updateSongs { it.copy(selectedDifficulty = diff) }
        applyFilters()
    }

    fun filterByLevelRange(min: Int, max: Int) {
        updateSongs { it.copy(minLevel = min, maxLevel = max) }
        applyFilters()
    }

    fun toggleFilterSheet(show: Boolean) {
        updateSongs { it.copy(showFilterSheet = show) }
    }

    fun resetFilters() {
        val (minBound, maxBound) = _uiState.value.songs.let { it.levelMinBound to it.levelMaxBound }
        updateSongs {
            it.copy(
                selectedChapters = emptySet(),
                selectedDifficulty = null,
                minLevel = minBound,
                maxLevel = maxBound
            )
        }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value.songs
        val allSongsMap = songDataProvider.getSongs()
        val searchResults = if (state.searchQuery.isNotBlank()) {
            searchSongUseCase(state.searchQuery, allSongsMap)
        } else {
            state.allSongs
        }

        val chapters = state.selectedChapters
        val diff = state.selectedDifficulty
        val minLvl = state.minLevel.toFloat()
        val maxLvl = state.maxLevel.toFloat() + 0.99f

        val filtered = searchResults.filter { song ->
            val matchesChapter = chapters.isEmpty() || song.chapter in chapters
            val matchesLevelAndDiff = if (diff != null) {
                val cc = song.difficulties[diff]
                cc != null && cc >= minLvl && cc <= maxLvl
            } else {
                song.difficulties.values.any { cc -> cc >= minLvl && cc <= maxLvl }
            }
            matchesChapter && matchesLevelAndDiff
        }

        updateSongs { it.copy(filteredSongs = filtered) }
    }

    fun getLowIllustrationUrl(songId: String): String? {
        // Players who declined the preload keep blank thumbnails instead of
        // silently re-downloading in lists; the Settings re-download action
        // clears the flag and restores the remote fallback below. The null
        // difficulty lands per-difficulty songs on their top variant slot.
        val cacheKey = illustrationProvider.cacheKey(songId, null)
        if (illustrationPreloadDeclined) return artworkFileCache.getThumbnailIfPresent(cacheKey)
        return artworkFileCache.getThumbnailIfPresent(cacheKey)
            ?: illustrationProvider.getLowUrl(songId)
    }

    fun getCachedOrStandardIllustrationUri(songId: String): String {
        return artworkFileCache.getStandardIfPresent(illustrationProvider.cacheKey(songId, null))
            ?: illustrationProvider.getStandardUrl(songId)
    }

    fun getRandomTip(): String {
        return tipsProvider.getRandomTip()
    }

    fun clearError() {
        updateSync { it.copy(error = null) }
    }

    fun setAvatarUri(uri: String?) {
        viewModelScope.launch {
            AppLogger.event("settings", "avatar_changed", mapOf("uriPresent" to (uri != null).toString()))
            repository.setAvatarUri(uri)
        }
    }

    fun fetchApiRankByUser() {
        val state = _uiState.value.tools
        if (!state.apiEnabled || !state.useApiData) return
        val platform = state.apiPlatform.trim()
        val platformId = state.apiPlatformId.trim()
        val apiUserId = state.apiUserId.trim()
        if (platform.isBlank() || platformId.isBlank() || apiUserId.isBlank()) {
            updateTools { it.copy(apiRankByUser = ApiToolResult(message = UiText.Res(Res.string.api_error_missing_fields))) }
            return
        }

        val generation = accountAccessGeneration
        viewModelScope.launch {
            updateTools { it.copy(apiRankByUser = ApiToolResult(isLoading = true)) }
            val result = repository.apiGetRankByUser(platform, platformId, apiUserId)
            if (generation != accountAccessGeneration) return@launch
            if (result.isFailure) {
                updateTools {
                    it.copy(
                        apiRankByUser = ApiToolResult(
                            message = UiText.Res(Res.string.tools_api_query_failed)
                        )
                    )
                }
                return@launch
            }
            val json = result.getOrNull()
            val data = json?.get("data")?.asObject()
            val total = data?.get("totDataNum")?.asInt()
            val users = data?.get("users")?.asArray().orEmpty()
            val meObj = data?.get("me")?.asObject()
            val meFromUsers = users.firstOrNull { it.asObject()?.get("me")?.asBoolean() == true }?.asObject()

            val meRank = meObj?.get("rank")?.asInt()
                ?: meObj?.get("index")?.asInt()
                ?: meObj?.get("save")?.asObject()?.get("rank")?.asInt()
                ?: meFromUsers?.get("index")?.asInt()

            val mePlayerId = meObj?.get("save")?.asObject()?.get("saveInfo")?.asObject()?.get("PlayerId")?.asString()
                ?: meObj?.get("save")?.asObject()?.get("PlayerId")?.asString()
                ?: meFromUsers?.get("saveInfo")?.asObject()?.get("PlayerId")?.asString()

            val meRks = meObj?.get("save")?.asObject()?.get("saveInfo")?.asObject()?.get("summary")?.asObject()?.get("rankingScore")?.asFloat()
                ?: meObj?.get("save")?.asObject()?.get("summary")?.asObject()?.get("rankingScore")?.asFloat()
                ?: meFromUsers?.get("saveInfo")?.asObject()?.get("summary")?.asObject()?.get("rankingScore")?.asFloat()

            val msg = UiText.Res(
                Res.string.tools_api_summary_rank_by_user,
                total?.toString() ?: "—",
                meRank?.toString() ?: "—",
                if (!mePlayerId.isNullOrBlank()) UiText.Res(Res.string.tools_api_summary_part_player, mePlayerId) else UiText.Raw(""),
                if (meRks != null) UiText.Res(Res.string.tools_api_summary_part_rks, formatFourDecimals(meRks)) else UiText.Raw("")
            )
            val rows = buildList {
                if (!mePlayerId.isNullOrBlank()) add(ApiToolRow(UiText.Res(Res.string.tools_api_row_player), UiText.Raw(mePlayerId)))
                if (meRks != null) add(ApiToolRow(UiText.Raw("RKS"), UiText.Raw(formatFourDecimals(meRks))))
                if (meRank != null) add(ApiToolRow(UiText.Res(Res.string.tools_api_row_my_rank), UiText.Raw(meRank.toString())))
                add(ApiToolRow(UiText.Res(Res.string.tools_api_row_total), UiText.Raw(total?.toString() ?: "—")))
            }
            updateTools { it.copy(apiRankByUser = ApiToolResult(message = msg, rows = rows)) }
        }
    }

    fun fetchApiRankByPosition(position: Int) {
        if (position <= 0) {
            updateTools { it.copy(apiRankByPosition = ApiToolResult(message = UiText.Res(Res.string.tools_api_invalid_rank))) }
            return
        }
        val generation = accountAccessGeneration
        viewModelScope.launch {
            updateTools { it.copy(apiRankByPosition = ApiToolResult(isLoading = true)) }
            val result = repository.apiGetRankByPosition(position)
            if (generation != accountAccessGeneration) return@launch
            if (result.isFailure) {
                updateTools {
                    it.copy(
                        apiRankByPosition = ApiToolResult(
                            message = UiText.Res(Res.string.tools_api_query_failed)
                        )
                    )
                }
                return@launch
            }
            val json = result.getOrNull()
            val data = json?.get("data")?.asObject()
            val users = data?.get("users")?.asArray().orEmpty()
            val userObj = users.firstOrNull { it.asObject()?.get("index")?.asInt() == position }?.asObject()
                ?: users.minByOrNull {
                    kotlin.math.abs((it.asObject()?.get("index")?.asInt() ?: Int.MAX_VALUE) - position)
                }?.asObject()
            val rank = userObj?.get("index")?.asInt()
            val playerId = userObj?.get("saveInfo")?.asObject()?.get("PlayerId")?.asString()
                ?: userObj?.get("gameuser")?.asObject()?.get("PlayerId")?.asString()
            val rks = userObj?.get("saveInfo")?.asObject()?.get("summary")?.asObject()?.get("rankingScore")?.asFloat()
                ?: userObj?.get("gameuser")?.asObject()?.get("rankingScore")?.asFloat()
            val exact = rank == position
            val msg = UiText.Res(
                Res.string.tools_api_summary_rank_by_position,
                (rank ?: position).toString(),
                if (!exact && rank != null) UiText.Res(Res.string.tools_api_summary_part_closest, position) else UiText.Raw(""),
                playerId ?: UiText.Res(Res.string.tools_api_unknown_player),
                if (rks != null) UiText.Res(Res.string.tools_api_summary_part_rks, formatFourDecimals(rks)) else UiText.Raw("")
            )
            val rows = buildList {
                add(ApiToolRow(UiText.Res(Res.string.tools_api_row_requested_rank), UiText.Raw(position.toString())))
                add(ApiToolRow(UiText.Res(Res.string.tools_api_row_returned_rank), UiText.Raw(rank?.toString() ?: "—")))
                add(
                    ApiToolRow(
                        UiText.Res(Res.string.tools_api_row_player),
                        playerId?.let(UiText::Raw) ?: UiText.Res(Res.string.tools_api_unknown_player)
                    )
                )
                if (rks != null) add(ApiToolRow(UiText.Raw("RKS"), UiText.Raw(formatFourDecimals(rks))))
                add(
                    ApiToolRow(
                        UiText.Res(Res.string.tools_api_row_match_status),
                        if (exact) {
                            UiText.Res(Res.string.tools_api_match_exact)
                        } else {
                            UiText.Res(Res.string.tools_api_match_closest)
                        }
                    )
                )
            }
            updateTools { it.copy(apiRankByPosition = ApiToolResult(message = msg, rows = rows)) }
        }
    }

    fun fetchApiRksRankForValue(rks: Float) {
        if (rks <= 0f) {
            updateTools { it.copy(apiRksRankResult = ApiToolResult(message = UiText.Res(Res.string.tools_api_invalid_rks))) }
            return
        }
        val generation = accountAccessGeneration
        viewModelScope.launch {
            updateTools { it.copy(apiRksRankResult = ApiToolResult(isLoading = true)) }
            val result = repository.apiGetRksAbove(rks)
            if (generation != accountAccessGeneration) return@launch
            if (result.isFailure) {
                updateTools {
                    it.copy(
                        apiRksRankResult = ApiToolResult(
                            message = UiText.Res(Res.string.tools_api_query_failed)
                        )
                    )
                }
                return@launch
            }
            val dataObj = result.getOrNull()?.get("data")?.asObject()
            val total = dataObj?.get("totNum")?.asInt()
            val rank = dataObj?.get("rksRank")?.asInt()
            updateTools {
                it.copy(
                    apiTotalUsers = total,
                    apiRksRank = rank,
                    apiRksRankResult = ApiToolResult(
                        message = UiText.Res(
                            Res.string.tools_api_summary_rks_rank,
                            formatFourDecimals(rks),
                            rank?.toString() ?: "—",
                            total?.toString() ?: "—"
                        ),
                        rows = listOf(
                            ApiToolRow(UiText.Res(Res.string.tools_api_row_target_rks), UiText.Raw(formatFourDecimals(rks))),
                            ApiToolRow(UiText.Res(Res.string.tools_api_row_users_above), UiText.Raw(rank?.toString() ?: "—")),
                            ApiToolRow(UiText.Res(Res.string.tools_api_row_total), UiText.Raw(total?.toString() ?: "—"))
                        )
                    )
                )
            }
        }
    }

    private fun refreshApiToolData() {
        refreshTagAnalysis(_uiState.value.b30.b30)
        val state = _uiState.value.tools
        if (!state.apiEnabled || !state.useApiData) {
            updateTools {
                it.copy(
                    apiHistorySnapshots = emptyList(),
                    apiRankByUser = ApiToolResult(),
                    apiRankByPosition = ApiToolResult(),
                    apiRksRankResult = ApiToolResult()
                )
            }
            return
        }

        fetchApiHistorySnapshots()
        fetchApiRankByUser()
        if (_uiState.value.b30.displayRks > 0f) {
            fetchApiRksRankForValue(_uiState.value.b30.displayRks)
        }
    }

    private fun fetchApiHistorySnapshots() {
        val state = _uiState.value.tools
        val platform = state.apiPlatform.trim()
        val platformId = state.apiPlatformId.trim()
        val apiUserId = state.apiUserId.trim()
        if (platform.isBlank() || platformId.isBlank() || apiUserId.isBlank()) return

        val generation = accountAccessGeneration
        viewModelScope.launch {
            val result = repository.apiGetSaveHistory(platform, platformId, apiUserId, listOf("rks"))
            if (generation != accountAccessGeneration || result.isFailure) {
                return@launch
            }

            val rksArray = result.getOrNull()?.get("data")?.asObject()?.get("rks")?.asArray().orEmpty()
            val snapshots = rksArray.mapIndexedNotNull { index, item ->
                val obj = item.asObject() ?: return@mapIndexedNotNull null
                val date = obj.get("date")?.asString() ?: return@mapIndexedNotNull null
                val value = obj.get("value")?.asFloat() ?: return@mapIndexedNotNull null
                SyncSnapshot(
                    id = index.toLong() + 1L,
                    timestamp = parseIsoToEpoch(date),
                    rks = value,
                    nickname = _uiState.value.profile.nickname,
                    dataCount = 0,
                    lastSyncedSongId = null,
                    lastSyncedDifficulty = null,
                    lastSyncedScore = null,
                    lastSyncedAccuracy = null
                )
            }.sortedBy { it.timestamp }

            updateTools { it.copy(apiHistorySnapshots = snapshots) }
        }
    }

    // --- JSON parsing helpers ---

    private fun parseIsoToEpoch(iso: String): Long {
        return runCatching {
            Instant.parse(iso).toEpochMilliseconds()
        }.getOrElse { Clock.System.now().toEpochMilliseconds() }
    }

    private fun formatFourDecimals(value: Float): String {
        val scaled = (value * 10_000f).roundToLong()
        val whole = scaled / 10_000L
        val fraction = (scaled % 10_000L).toString().padStart(4, '0')
        return "$whole.$fraction"
    }

    // --- Extension helpers for JSON navigation ---
    private fun JsonObject?.get(key: String): JsonElement? = this?.get(key)
    private fun JsonElement?.asObject(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()
    private fun JsonElement?.asArray(): JsonArray? = runCatching { this?.jsonArray }.getOrNull()
    private fun JsonElement?.asString(): String? = this?.jsonPrimitive?.contentOrNull
    private fun JsonElement?.asInt(): Int? = this?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    private fun JsonElement?.asFloat(): Float? = this?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
    private fun JsonElement?.asBoolean(): Boolean? = this?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    // --- KMP-compatible current time millis ---
    private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

    // --- Application update check ---

    /**
     * Loads the latest published Phigros game update. The last fetched
     * [GameUpdateInfo] is cached as JSON in [SettingsRepository], so the
     * profile page shows the card immediately (even offline) and a
     * successful refresh only rewrites the cache when the content
     * actually changed. Failures keep the cached value and are logged.
     */
    private fun loadGameUpdateInfo() {
        viewModelScope.launch {
            AppLogger.event("data", "game_update_info_load_started")
            val cached = settingsRepository.gameUpdateInfoCache.first()
                ?.let { raw -> runCatching { GameUpdateJson.decodeFromString<GameUpdateInfo>(raw) }.getOrNull() }
            if (cached != null) {
                updateSync { it.copy(gameUpdateInfo = cached) }
            }
            fetchGameUpdateInfoUseCase().fold(
                onSuccess = { info ->
                    if (info != cached) {
                        runCatching {
                            settingsRepository.setGameUpdateInfoCache(GameUpdateJson.encodeToString(info))
                        }.onFailure { e ->
                            AppLogger.event(
                                "data",
                                "game_update_info_cache_write_failed",
                                mapOf("error" to (e.message ?: "unknown")),
                                throwable = e
                            )
                        }
                    }
                    updateSync { it.copy(gameUpdateInfo = info) }
                    AppLogger.event("data", "game_update_info_loaded", mapOf("version" to info.version))
                },
                onFailure = { e ->
                    AppLogger.event(
                        "data",
                        "game_update_info_load_failed",
                        mapOf("error" to (e.message ?: "unknown")),
                        throwable = e
                    )
                }
            )
        }
    }

    fun checkForUpdate(currentVersionName: String) {
        viewModelScope.launch {
            updateSync { it.copy(updateCheckState = UpdateCheckState.Checking) }
            AppLogger.event("update", "check_started")
            val result = checkForUpdateUseCase(
                currentVersionName = currentVersionName,
                includePreRelease = settingsRepository.includePreRelease.first()
            ).toUpdateCheckState()
            updateSync { it.copy(updateCheckState = result) }
            when (result) {
                is UpdateCheckState.Available -> AppLogger.event(
                    "update", "check_update_available", mapOf("version" to result.version)
                )
                is UpdateCheckState.Error -> AppLogger.event(
                    "update", "check_failed", mapOf("error" to result.message)
                )
                UpdateCheckState.NoUpdate -> AppLogger.event("update", "check_no_update")
                UpdateCheckState.Checking, UpdateCheckState.Idle -> Unit
            }
        }
    }

    fun dismissUpdateResult() {
        updateSync { it.copy(updateCheckState = UpdateCheckState.Idle) }
    }

}
