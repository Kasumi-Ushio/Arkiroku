package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.data.platform.getAppMetadata
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.data.platform.triggerAppRestart
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.settings_logout_credential_pending
import phitracker.composeapp.generated.resources.settings_logout_failed
import phitracker.composeapp.generated.resources.settings_restart_required_message
import phitracker.composeapp.generated.resources.settings_restart_required_title

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val metadata = getAppMetadata()
    val isDebugBuild = metadata.buildType == "Debug"
    val restartRequiredTitle = stringResource(Res.string.settings_restart_required_title)
    val restartRequiredMessage = stringResource(Res.string.settings_restart_required_message)
    val logoutFailedMessage = stringResource(Res.string.settings_logout_failed)
    val logoutCredentialPendingMessage = stringResource(Res.string.settings_logout_credential_pending)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.LoggedOut -> onLogout()
                SettingsEvent.LoggedOutCredentialsPending -> {
                    showPlatformMessage(logoutCredentialPendingMessage)
                    onLogout()
                }
                SettingsEvent.LogoutFailed -> showPlatformMessage(logoutFailedMessage)
                SettingsEvent.LocalSessionReset -> onLogout()
                SettingsEvent.RestartRequested ->
                    triggerAppRestart(restartRequiredTitle, restartRequiredMessage)
            }
        }
    }

    SettingsTab(
        themeMode = state.themeMode,
        amoledBlack = state.amoledBlack,
        themeColorSource = state.themeColorSource,
        seedColorArgb = state.seedColorArgb,
        themeImageSeedColorArgb = state.themeImageSeedColorArgb,
        paletteStyleName = state.paletteStyleName,
        showB30Overflow = state.showB30Overflow,
        overflowCount = state.overflowCount,
        hazeBlurEnabled = state.hazeBlurEnabled,
        hazeBlurStrength = state.hazeBlurStrength,
        appLanguage = state.appLanguage,
        onAppLanguageChange = { viewModel.setAppLanguage(it) },
        onThemeModeChange = { viewModel.setThemeMode(it) },
        onAmoledBlackChange = { viewModel.setAmoledBlack(it) },
        onThemeColorSourceChange = { viewModel.setThemeColorSource(it) },
        onThemeImageColorSelected = { uri, seedArgb -> viewModel.setThemeImageColor(uri, seedArgb) },
        onThemeImageColorClear = { viewModel.clearThemeImageColor() },
        onPaletteStyleNameChange = { viewModel.setPaletteStyleName(it) },
        onShowB30OverflowChange = { viewModel.setShowB30Overflow(it) },
        onOverflowCountChange = { viewModel.setOverflowCount(it) },
        onHazeBlurEnabledChange = { viewModel.setHazeBlurEnabled(it) },
        onHazeBlurStrengthChange = { viewModel.setHazeBlurStrength(it) },
        isCachingB30Artwork = state.isCachingB30Artwork,
        b30ArtworkCacheCompleted = state.b30ArtworkCacheCompleted,
        b30ArtworkCacheTotal = state.b30ArtworkCacheTotal,
        onCacheB30Artwork = { callback -> viewModel.cacheB30StandardArtwork(onComplete = callback) },
        onClearHighResCache = { callback -> viewModel.clearHighResCache(onComplete = callback) },
        onRedownloadIllustrations = { viewModel.resetIllustrationDownload() },
        onNavigateToAbout = onNavigateToAbout,
        onLogout = { viewModel.logout() },
        isResettingSessionToken = state.isResettingSessionToken,
        sessionTokenResetMessage = state.sessionTokenResetMessage,
        onResetSessionToken = { viewModel.resetSessionToken() },
        onDismissSessionTokenResetMessage = { viewModel.dismissSessionTokenResetMessage() },
        isResettingLocalSession = state.isResettingLocalSession,
        localSessionResetMessage = state.localSessionResetMessage,
        onResetLocalSession = { viewModel.resetLocalSession() },
        onNavigateBack = onNavigateBack,
        tip = state.tip,
        apiEnabled = state.apiEnabled,
        useApiData = state.useApiData,
        apiUserId = state.apiUserId,
        apiPlatform = state.apiPlatform,
        apiPlatformId = state.apiPlatformId,
        apiToken = state.apiToken,
        isApiTesting = state.isApiTesting,
        apiTestMessage = state.apiTestMessage,
        onApiEnabledChange = { viewModel.setApiEnabled(it) },
        onUseApiDataChange = { viewModel.setUseApiData(it) },
        onApiUserIdChange = { viewModel.setApiUserId(it) },
        onApiPlatformChange = { viewModel.setApiPlatform(it) },
        onApiPlatformIdChange = { viewModel.setApiPlatformId(it) },
        onApiTokenChange = { viewModel.setApiToken(it) },
        onApiTestConnection = { viewModel.testApiConnection() },
        isUpdatingData = state.isUpdatingData,
        updateDataProgress = state.updateDataProgress,
        updateDataTotal = state.updateDataTotal,
        updateDataFileName = state.updateDataFileName,
        updateDataPhase = state.updateDataPhase,
        updateDataError = state.updateDataBusyMessage ?: state.updateDataError,
        updateResultSongNames = state.updateResultSongNames,
        updatePartialIllustrationFailures = state.updatePartialIllustrationFailures,
        catalogDegraded = state.catalogDegraded,
        collectibleDataVersion = state.collectibleDataVersion,
        onUpdateSongData = { viewModel.updateSongData() },
        onCancelSongDataUpdate = { viewModel.cancelSongDataUpdate() },
        onResetPendingUpdate = { viewModel.resetPendingUpdate() },
        onDismissUpdateError = { viewModel.dismissUpdateDataError() },
        includePreRelease = state.includePreRelease,
        autoCheckUpdate = state.autoCheckUpdate,
        autoCheckSongDataUpdate = state.autoCheckSongDataUpdate,
        updateCheckState = state.updateCheckState,
        onCheckForUpdate = { viewModel.checkForUpdate(metadata.versionName) },
        onIncludePreReleaseChange = { viewModel.setIncludePreRelease(it) },
        onAutoCheckUpdateChange = { viewModel.setAutoCheckUpdate(it) },
        onAutoCheckSongDataUpdateChange = { viewModel.setAutoCheckSongDataUpdate(it) },
        onDismissUpdateResult = { viewModel.dismissUpdateDataResult() },
        isDebugBuild = isDebugBuild,
        hasRuntimeLogs = state.hasRuntimeLogs,
        hasCrashLogs = state.hasCrashLogs,
        onExportRuntimeLog = { viewModel.exportRuntimeLogText() },
        onExportCrashLog = { viewModel.exportCrashLogText() },
        onClearAllLogs = { viewModel.clearAllLogs() },
        crashNotificationGuideShown = state.crashNotificationGuideShown,
        onCrashNotificationGuideShown = { viewModel.setCrashNotificationGuideShown() },
        modifier = androidx.compose.ui.Modifier
    )
}
