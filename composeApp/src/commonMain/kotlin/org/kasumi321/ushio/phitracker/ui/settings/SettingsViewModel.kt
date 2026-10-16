package org.kasumi321.ushio.phitracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.logging.CrashReportExporter
import org.kasumi321.ushio.phitracker.data.logging.RuntimeLogExporter
import org.kasumi321.ushio.phitracker.data.platform.CoilIllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.clearAllImageCache
import org.kasumi321.ushio.phitracker.data.platform.clearImageCacheUrls
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdateCoordinator
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.CheckForUpdateUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.update.toUpdateCheckState
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import org.kasumi321.ushio.phitracker.ui.utils.resolve
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.api_error_missing_fields
import phitracker.composeapp.generated.resources.common_unknown_error
import phitracker.composeapp.generated.resources.settings_api_test_failed
import phitracker.composeapp.generated.resources.settings_api_test_ok
import phitracker.composeapp.generated.resources.settings_api_test_partial
import phitracker.composeapp.generated.resources.settings_b30_cache_no_songs
import phitracker.composeapp.generated.resources.settings_b30_cache_partial_failed
import phitracker.composeapp.generated.resources.settings_redownload_failed

internal class NoB30SongsToCacheException : IllegalStateException("no B30 songs to cache")
internal class B30ArtworkCachePartialFailure(val failures: Int) :
    IllegalStateException("$failures B30 artwork cache failures")

class SettingsViewModel(
    internal val repository: PhigrosRepository,
    internal val settingsRepository: SettingsRepository,
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    getB30UseCase: GetB30UseCase,
    private val songDataProvider: SongDataProvider,
    private val songDataUpdateCoordinator: SongDataUpdateCoordinator,
    private val illustrationProvider: IllustrationProvider,
    private val artworkFileCache: StandardArtworkCache,
    internal val runtimeLogExporter: RuntimeLogExporter,
    internal val crashReportExporter: CrashReportExporter,
    tipsProvider: TipsProvider,
    private val thumbnailPreloader: IllustrationThumbnailPreloader = CoilIllustrationThumbnailPreloader,
    private val clearCacheUrls: suspend (List<String>) -> Unit = ::clearImageCacheUrls,
    private val clearAllCache: suspend () -> Unit = ::clearAllImageCache,
    private val platformMessage: (String) -> Unit = ::showPlatformMessage
) : ViewModel() {
    internal val mutableUiState = MutableStateFlow(
        SettingsUiState(
            hasRuntimeLogs = runtimeLogExporter.hasLogs(),
            hasCrashLogs = crashReportExporter.hasReports(),
            tip = tipsProvider.getRandomTip()
        )
    )
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    internal val eventChannel = Channel<SettingsEvent>(capacity = Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var b30: List<BestRecord> = emptyList()

    init {
        observeSettings()
        viewModelScope.launch {
            getB30UseCase(
                songDataProvider.getDifficultyMap(),
                songDataProvider.getSongNameMap()
            ).collect { (records, _) -> b30 = records }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch { settingsRepository.themeMode.collect { value -> mutableUiState.update { it.copy(themeMode = value) } } }
        viewModelScope.launch { settingsRepository.themeColorSource.collect { value -> mutableUiState.update { it.copy(themeColorSource = value) } } }
        viewModelScope.launch { settingsRepository.seedColorArgb.collect { value -> mutableUiState.update { it.copy(seedColorArgb = value) } } }
        viewModelScope.launch { settingsRepository.themeImageSeedColorArgb.collect { value -> mutableUiState.update { it.copy(themeImageSeedColorArgb = value) } } }
        viewModelScope.launch { settingsRepository.themeImageUri.collect { value -> mutableUiState.update { it.copy(themeImageUri = value) } } }
        viewModelScope.launch { settingsRepository.paletteStyleName.collect { value -> mutableUiState.update { it.copy(paletteStyleName = value) } } }
        viewModelScope.launch { settingsRepository.showB30Overflow.collect { value -> mutableUiState.update { it.copy(showB30Overflow = value) } } }
        viewModelScope.launch { settingsRepository.overflowCount.collect { value -> mutableUiState.update { it.copy(overflowCount = value) } } }
        viewModelScope.launch { settingsRepository.hazeBlurEnabled.collect { value -> mutableUiState.update { it.copy(hazeBlurEnabled = value) } } }
        viewModelScope.launch { settingsRepository.hazeBlurStrength.collect { value -> mutableUiState.update { it.copy(hazeBlurStrength = value) } } }
        viewModelScope.launch { settingsRepository.appLanguage.collect { value -> mutableUiState.update { it.copy(appLanguage = value) } } }
        viewModelScope.launch { settingsRepository.apiEnabled.collect { value -> mutableUiState.update { it.copy(apiEnabled = value) } } }
        viewModelScope.launch { settingsRepository.useApiData.collect { value -> mutableUiState.update { it.copy(useApiData = value) } } }
        viewModelScope.launch { settingsRepository.apiId.collect { value -> mutableUiState.update { it.copy(apiUserId = value) } } }
        viewModelScope.launch { settingsRepository.apiPlatform.collect { value -> mutableUiState.update { it.copy(apiPlatform = value) } } }
        viewModelScope.launch { settingsRepository.apiPlatformId.collect { value -> mutableUiState.update { it.copy(apiPlatformId = value) } } }
        viewModelScope.launch { settingsRepository.apiToken.collect { value -> mutableUiState.update { it.copy(apiToken = value) } } }
        viewModelScope.launch { settingsRepository.includePreRelease.collect { value -> mutableUiState.update { it.copy(includePreRelease = value) } } }
        viewModelScope.launch { settingsRepository.autoCheckUpdate.collect { value -> mutableUiState.update { it.copy(autoCheckUpdate = value) } } }
        viewModelScope.launch { settingsRepository.autoCheckSongDataUpdate.collect { value -> mutableUiState.update { it.copy(autoCheckSongDataUpdate = value) } } }
        viewModelScope.launch { settingsRepository.crashNotificationGuideShown.collect { value -> mutableUiState.update { it.copy(crashNotificationGuideShown = value) } } }
    }

    internal fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun testApiConnection() {
        viewModelScope.launch {
            val platform = mutableUiState.value.apiPlatform.trim()
            val platformId = mutableUiState.value.apiPlatformId.trim()
            val apiUserId = mutableUiState.value.apiUserId.trim()
            if (platform.isBlank() || platformId.isBlank() || apiUserId.isBlank()) {
                mutableUiState.update { it.copy(apiTestMessage = UiText.Res(Res.string.api_error_missing_fields)) }
                return@launch
            }
            mutableUiState.update { it.copy(isApiTesting = true, apiTestMessage = null) }
            AppLogger.event("api", "test_started")
            val status = repository.apiTest()
            if (status.isFailure) {
                val message = status.exceptionOrNull()?.message ?: "unknown"
                mutableUiState.update {
                    it.copy(
                        isApiTesting = false,
                        apiTestMessage = UiText.Res(Res.string.settings_api_test_failed, message)
                    )
                }
                AppLogger.event("api", "test_failed", mapOf("error" to message), throwable = status.exceptionOrNull())
                return@launch
            }
            val accountLookup = repository.apiGetRankByUser(platform, platformId, apiUserId)
            mutableUiState.update {
                it.copy(
                    isApiTesting = false,
                    apiTestMessage = if (accountLookup.isSuccess) {
                        UiText.Res(Res.string.settings_api_test_ok)
                    } else {
                        UiText.Res(
                            Res.string.settings_api_test_partial,
                            accountLookup.exceptionOrNull()?.message
                                ?: UiText.Res(Res.string.common_unknown_error)
                        )
                    }
                )
            }
            AppLogger.event(
                "api",
                if (accountLookup.isSuccess) "test_success" else "test_partial",
                mapOf("accountLookupSuccess" to accountLookup.isSuccess.toString())
            )
        }
    }

    fun checkForUpdate(currentVersionName: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(updateCheckState = UpdateCheckState.Checking) }
            AppLogger.event("update", "check_started")
            val result = checkForUpdateUseCase(
                currentVersionName,
                settingsRepository.includePreRelease.first()
            ).toUpdateCheckState()
            mutableUiState.update { it.copy(updateCheckState = result) }
            when (result) {
                is UpdateCheckState.Available -> AppLogger.event("update", "check_update_available", mapOf("version" to result.version))
                is UpdateCheckState.Error -> AppLogger.event("update", "check_failed", mapOf("error" to result.message))
                UpdateCheckState.NoUpdate -> AppLogger.event("update", "check_no_update")
                UpdateCheckState.Checking, UpdateCheckState.Idle -> Unit
            }
        }
    }

    fun cacheB30StandardArtwork(onComplete: (Result<Unit>) -> Unit = {}) {
        if (mutableUiState.value.isCachingB30Artwork) return
        viewModelScope.launch {
            val songIds = b30.map { it.songId }.distinct()
            if (songIds.isEmpty()) {
                onComplete(Result.failure(NoB30SongsToCacheException()))
                return@launch
            }
            mutableUiState.update { it.copy(isCachingB30Artwork = true, b30ArtworkCacheTotal = songIds.size, b30ArtworkCacheCompleted = 0, b30ArtworkCacheError = null) }
            AppLogger.event("cache", "b30_standard_artwork_started", mapOf("count" to songIds.size.toString()))
            val semaphore = Semaphore(4)
            val mutex = Mutex()
            var completed = 0
            var failures = 0
            songIds.map { songId ->
                launch {
                    semaphore.withPermit {
                        val result = runCatching {
                            // The null difficulty lands per-difficulty songs
                            // on their top variant slot, matching what the
                            // B30 export resolves for display.
                            val cacheKey = illustrationProvider.cacheKey(songId, null)
                            val url = illustrationProvider.getStandardUrl(songId)
                            artworkFileCache.getOrDownloadStandard(cacheKey, url)
                            thumbnailPreloader.preload(url).getOrThrow()
                        }
                        mutex.withLock {
                            if (result.isFailure) failures++
                            mutableUiState.update { it.copy(b30ArtworkCacheCompleted = ++completed) }
                        }
                    }
                }
            }.forEach { it.join() }
            val result = if (failures == 0) Result.success(Unit) else Result.failure(B30ArtworkCachePartialFailure(failures))
            mutableUiState.update {
                it.copy(
                    isCachingB30Artwork = false,
                    b30ArtworkCacheError = result.exceptionOrNull()?.let(::b30ArtworkCacheErrorText)
                )
            }
            AppLogger.event("cache", "b30_standard_artwork_finished", mapOf("count" to songIds.size.toString(), "failures" to failures.toString()))
            onComplete(result)
        }
    }

    fun clearHighResCache(onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                artworkFileCache.clearAllStandard()
                clearCacheUrls(songDataProvider.getSongs().keys.map(illustrationProvider::getStandardUrl))
            }
            AppLogger.event("cache", if (result.isSuccess) "high_res_clear_success" else "high_res_clear_failed", mapOf("error" to (result.exceptionOrNull()?.message ?: "")), throwable = result.exceptionOrNull())
            onComplete(result)
        }
    }

    fun resetIllustrationDownload() {
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.setPreloadDone(false)
                settingsRepository.setIllustrationPreloadDeclined(false)
                clearAllCache()
                artworkFileCache.clearAllThumbnails()
                artworkFileCache.clearAllStandard()
            }
            if (result.isSuccess) {
                AppLogger.event("cache", "redownload_reset_success")
                eventChannel.send(SettingsEvent.RestartRequested)
            } else {
                val message = result.exceptionOrNull()?.message ?: "unknown"
                AppLogger.event("cache", "redownload_reset_failed", mapOf("error" to message), throwable = result.exceptionOrNull())
                val errorText = UiText.Res(Res.string.settings_redownload_failed, message)
                platformMessage(errorText.resolve())
                mutableUiState.update { it.copy(updateDataError = errorText) }
            }
        }
    }

    fun updateSongData() {
        if (mutableUiState.value.isUpdatingData) return
        viewModelScope.launch {
            val total = SongDataUpdater.FILE_NAMES.size
            mutableUiState.update { it.copy(isUpdatingData = true, updateDataPhase = UpdateDataPhase.Files, updateDataProgress = 0, updateDataTotal = total, updateDataFileName = "", updateDataError = null, updateResultSongNames = null) }
            AppLogger.event("data", "update_song_data_started", mapOf("totalFiles" to total.toString()))
            val result = songDataUpdateCoordinator.update(
                onFileProgress = { current, count, fileName ->
                    mutableUiState.update { it.copy(updateDataPhase = UpdateDataPhase.Files, updateDataProgress = current, updateDataTotal = count, updateDataFileName = fileName) }
                },
                onIllustrationProgress = { progress ->
                    mutableUiState.update {
                        it.copy(
                            updateDataPhase = UpdateDataPhase.Illustrations,
                            updateDataProgress = progress.completed,
                            updateDataTotal = progress.total,
                            updateDataFileName = progress.currentSongName
                        )
                    }
                }
            )
            result.fold(
                onSuccess = { outcome ->
                    mutableUiState.update {
                        it.copy(
                            isUpdatingData = false,
                            updateDataProgress = total,
                            updateDataFileName = "",
                            updateDataError = null,
                            updateResultSongNames = outcome.addedSongNames
                        )
                    }
                    AppLogger.event("data", "update_song_data_success", mapOf("addedSongs" to outcome.addedSongNames.size.toString()))
                },
                onFailure = { error ->
                    val message = error.message
                    mutableUiState.update { it.copy(isUpdatingData = false, updateDataError = message?.let(UiText::Raw)) }
                    AppLogger.event("data", "update_song_data_failed", mapOf("error" to (message ?: "unknown")), throwable = error)
                }
            )
        }
    }

    private fun b30ArtworkCacheErrorText(error: Throwable): UiText = when (error) {
        is NoB30SongsToCacheException -> UiText.Res(Res.string.settings_b30_cache_no_songs)
        is B30ArtworkCachePartialFailure -> UiText.Res(Res.string.settings_b30_cache_partial_failed, error.failures)
        else -> error.message?.let(UiText::Raw) ?: UiText.Res(Res.string.common_unknown_error)
    }

    fun dismissUpdateDataResult() {
        mutableUiState.update { it.copy(updateResultSongNames = null) }
    }

}
