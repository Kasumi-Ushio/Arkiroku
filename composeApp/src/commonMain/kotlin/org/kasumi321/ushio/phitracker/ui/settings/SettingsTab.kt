package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.themeColor
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.data.platform.ThemeImageColorPickResult
import org.kasumi321.ushio.phitracker.data.platform.hasCrashNotificationPermission
import org.kasumi321.ushio.phitracker.data.platform.rememberThemeImageColorPicker
import org.kasumi321.ushio.phitracker.data.platform.requestCrashNotificationPermission
import org.kasumi321.ushio.phitracker.data.platform.shareTextLog
import org.kasumi321.ushio.phitracker.data.platform.shouldShowThemeColorSourceSetting
import org.kasumi321.ushio.phitracker.data.platform.showPlatformAlert
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog
import org.kasumi321.ushio.phitracker.ui.components.AppCircularProgressIndicator
import org.kasumi321.ushio.phitracker.ui.components.AppLinearProgressIndicator
import org.kasumi321.ushio.phitracker.ui.glass.GlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.rememberCollapsingTitleStyle
import org.kasumi321.ushio.phitracker.ui.glass.rememberGlassHazeStyle
import org.kasumi321.ushio.phitracker.ui.theme.THEME_COLOR_SOURCE_IMAGE
import org.kasumi321.ushio.phitracker.ui.theme.THEME_COLOR_SOURCE_SYSTEM
import org.kasumi321.ushio.phitracker.ui.theme.argbToColor
import org.kasumi321.ushio.phitracker.ui.theme.colorToArgb
import org.kasumi321.ushio.phitracker.ui.theme.supportsSpec2025
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.update.UpdateResultDialog
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import org.kasumi321.ushio.phitracker.ui.utils.asString
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.action_back
import phitracker.composeapp.generated.resources.action_settings
import phitracker.composeapp.generated.resources.common_confirm
import phitracker.composeapp.generated.resources.common_unknown_error
import phitracker.composeapp.generated.resources.settings_about_app
import phitracker.composeapp.generated.resources.settings_about_app_desc
import phitracker.composeapp.generated.resources.settings_already_latest
import phitracker.composeapp.generated.resources.settings_amoled_black
import phitracker.composeapp.generated.resources.settings_amoled_black_desc
import phitracker.composeapp.generated.resources.settings_api_credentials
import phitracker.composeapp.generated.resources.settings_api_credentials_configured
import phitracker.composeapp.generated.resources.settings_api_credentials_dialog_title
import phitracker.composeapp.generated.resources.settings_api_credentials_help
import phitracker.composeapp.generated.resources.settings_api_credentials_not_configured
import phitracker.composeapp.generated.resources.settings_api_data_disclaimer
import phitracker.composeapp.generated.resources.settings_api_platform_id
import phitracker.composeapp.generated.resources.settings_api_platform_name
import phitracker.composeapp.generated.resources.settings_api_risk_accept
import phitracker.composeapp.generated.resources.settings_api_risk_dialog_text
import phitracker.composeapp.generated.resources.settings_api_testing
import phitracker.composeapp.generated.resources.settings_api_test_connection
import phitracker.composeapp.generated.resources.settings_api_user_id
import phitracker.composeapp.generated.resources.settings_artwork_preload_done
import phitracker.composeapp.generated.resources.settings_artwork_preload_failed
import phitracker.composeapp.generated.resources.settings_auto_check_song_data_update
import phitracker.composeapp.generated.resources.settings_auto_check_song_data_update_desc
import phitracker.composeapp.generated.resources.settings_auto_check_update
import phitracker.composeapp.generated.resources.settings_auto_check_update_desc
import phitracker.composeapp.generated.resources.settings_blur_strength
import phitracker.composeapp.generated.resources.settings_cache_b30_artwork
import phitracker.composeapp.generated.resources.settings_cache_b30_artwork_desc
import phitracker.composeapp.generated.resources.settings_caching_progress
import phitracker.composeapp.generated.resources.settings_cancel
import phitracker.composeapp.generated.resources.settings_category_about
import phitracker.composeapp.generated.resources.settings_catalog_using_bundled
import phitracker.composeapp.generated.resources.settings_category_b30
import phitracker.composeapp.generated.resources.settings_category_data_cache
import phitracker.composeapp.generated.resources.settings_category_diagnostics
import phitracker.composeapp.generated.resources.settings_category_interface_theme
import phitracker.composeapp.generated.resources.settings_category_score_api
import phitracker.composeapp.generated.resources.settings_category_updates
import phitracker.composeapp.generated.resources.settings_checking_update
import phitracker.composeapp.generated.resources.settings_check_update
import phitracker.composeapp.generated.resources.settings_check_update_desc
import phitracker.composeapp.generated.resources.settings_check_update_failed
import phitracker.composeapp.generated.resources.settings_clear_artwork_cache
import phitracker.composeapp.generated.resources.settings_clear_artwork_cache_desc
import phitracker.composeapp.generated.resources.settings_color_source_default
import phitracker.composeapp.generated.resources.settings_color_source_image
import phitracker.composeapp.generated.resources.settings_clear_cache_dialog_text
import phitracker.composeapp.generated.resources.settings_clear_cache_dialog_title
import phitracker.composeapp.generated.resources.settings_clear_cache_done
import phitracker.composeapp.generated.resources.settings_clear_cache_failed
import phitracker.composeapp.generated.resources.settings_clear_logs
import phitracker.composeapp.generated.resources.settings_clear_logs_desc
import phitracker.composeapp.generated.resources.settings_clear_logs_done_message
import phitracker.composeapp.generated.resources.settings_clear_logs_done_title
import phitracker.composeapp.generated.resources.settings_clear_logs_failed_message
import phitracker.composeapp.generated.resources.settings_clear_logs_failed_title
import phitracker.composeapp.generated.resources.settings_collectible_data_version
import phitracker.composeapp.generated.resources.settings_crash_notification_denied
import phitracker.composeapp.generated.resources.settings_crash_notification_granted
import phitracker.composeapp.generated.resources.settings_crash_notification_guide_text
import phitracker.composeapp.generated.resources.settings_crash_notification_guide_title
import phitracker.composeapp.generated.resources.settings_crash_notification_permission
import phitracker.composeapp.generated.resources.settings_dark_mode
import phitracker.composeapp.generated.resources.settings_done
import phitracker.composeapp.generated.resources.settings_downloading_file
import phitracker.composeapp.generated.resources.settings_enable_now
import phitracker.composeapp.generated.resources.settings_enable_score_api
import phitracker.composeapp.generated.resources.settings_enable_score_api_desc
import phitracker.composeapp.generated.resources.settings_export_crash_failed_message
import phitracker.composeapp.generated.resources.settings_export_crash_log
import phitracker.composeapp.generated.resources.settings_export_crash_log_desc
import phitracker.composeapp.generated.resources.settings_export_crash_log_failed
import phitracker.composeapp.generated.resources.settings_export_runtime_failed_message
import phitracker.composeapp.generated.resources.settings_export_runtime_log
import phitracker.composeapp.generated.resources.settings_export_runtime_log_desc
import phitracker.composeapp.generated.resources.settings_export_runtime_log_failed
import phitracker.composeapp.generated.resources.settings_haze_blur
import phitracker.composeapp.generated.resources.settings_haze_blur_desc
import phitracker.composeapp.generated.resources.settings_hide_api_token
import phitracker.composeapp.generated.resources.settings_include_prerelease
import phitracker.composeapp.generated.resources.settings_include_prerelease_desc
import phitracker.composeapp.generated.resources.settings_language_follow_system
import phitracker.composeapp.generated.resources.settings_language_title
import phitracker.composeapp.generated.resources.settings_later
import phitracker.composeapp.generated.resources.settings_logout
import phitracker.composeapp.generated.resources.settings_logout_confirm
import phitracker.composeapp.generated.resources.settings_logout_dialog_text
import phitracker.composeapp.generated.resources.settings_logout_preserve_data
import phitracker.composeapp.generated.resources.settings_reset_pending_update
import phitracker.composeapp.generated.resources.settings_reset_pending_update_dialog_text
import phitracker.composeapp.generated.resources.settings_catalog_management
import phitracker.composeapp.generated.resources.settings_catalog_management_desc
import phitracker.composeapp.generated.resources.settings_no_crash_log
import phitracker.composeapp.generated.resources.settings_no_crash_log_title
import phitracker.composeapp.generated.resources.settings_no_runtime_log
import phitracker.composeapp.generated.resources.settings_no_runtime_log_title
import phitracker.composeapp.generated.resources.settings_notification_permission_denied_toast
import phitracker.composeapp.generated.resources.settings_overflow_count
import phitracker.composeapp.generated.resources.settings_color_spec
import phitracker.composeapp.generated.resources.settings_color_spec_desc
import phitracker.composeapp.generated.resources.settings_color_spec_fallback
import phitracker.composeapp.generated.resources.settings_palette_style
import phitracker.composeapp.generated.resources.settings_palette_style_desc
import phitracker.composeapp.generated.resources.settings_prerelease_notice
import phitracker.composeapp.generated.resources.settings_redownload_artwork
import phitracker.composeapp.generated.resources.settings_redownload_artwork_desc
import phitracker.composeapp.generated.resources.settings_redownload_dialog_text
import phitracker.composeapp.generated.resources.settings_redownload_dialog_title
import phitracker.composeapp.generated.resources.settings_show_api_token
import phitracker.composeapp.generated.resources.settings_show_overflow
import phitracker.composeapp.generated.resources.settings_show_overflow_desc
import phitracker.composeapp.generated.resources.settings_song_name_separator
import phitracker.composeapp.generated.resources.settings_syncing_avatars
import phitracker.composeapp.generated.resources.settings_syncing_new_artwork
import phitracker.composeapp.generated.resources.settings_theme_amoled
import phitracker.composeapp.generated.resources.settings_theme_color_extracted_subtitle
import phitracker.composeapp.generated.resources.settings_theme_color_extracted_toast
import phitracker.composeapp.generated.resources.settings_theme_color_source
import phitracker.composeapp.generated.resources.settings_theme_color_source_desc
import phitracker.composeapp.generated.resources.settings_theme_dark
import phitracker.composeapp.generated.resources.settings_theme_follow_system
import phitracker.composeapp.generated.resources.settings_theme_light
import phitracker.composeapp.generated.resources.settings_update_complete
import phitracker.composeapp.generated.resources.settings_update_data_dialog_text
import phitracker.composeapp.generated.resources.settings_update_error_message
import phitracker.composeapp.generated.resources.settings_update_failed
import phitracker.composeapp.generated.resources.settings_update_new_songs
import phitracker.composeapp.generated.resources.settings_update_no_new_songs
import phitracker.composeapp.generated.resources.settings_update_partial_avatars
import phitracker.composeapp.generated.resources.settings_update_partial_illustrations
import phitracker.composeapp.generated.resources.settings_update_preparing
import phitracker.composeapp.generated.resources.settings_update_song_data
import phitracker.composeapp.generated.resources.settings_use_api_data
import phitracker.composeapp.generated.resources.settings_use_api_data_desc
import phitracker.composeapp.generated.resources.login_reset_local_session
import phitracker.composeapp.generated.resources.login_reset_local_session_dialog_text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
        themeMode: Int,
        amoledBlack: Boolean = false,
        themeColorSource: String = THEME_COLOR_SOURCE_SYSTEM,
        seedColorArgb: Int = -10011977,
        themeImageSeedColorArgb: Int? = null,
        paletteStyleName: String = PaletteStyle.TonalSpot.name,
        colorSpecVersion: String = "SPEC_2021",
        showB30Overflow: Boolean,
        overflowCount: Int,
        hazeBlurEnabled: Boolean = true,
        hazeBlurStrength: Float = 0.75f,
        appLanguage: String = "system",
        onAppLanguageChange: (String) -> Unit = {},
        onThemeModeChange: (Int) -> Unit,
        onAmoledBlackChange: (Boolean) -> Unit = {},
        onThemeColorSourceChange: (String) -> Unit = {},
        onThemeImageColorSelected: (String?, Int) -> Unit = { _, _ -> },
        onThemeImageColorClear: () -> Unit = {},
        onPaletteStyleNameChange: (String) -> Unit = {},
        onColorSpecVersionChange: (String) -> Unit = {},
        onShowB30OverflowChange: (Boolean) -> Unit,
        onOverflowCountChange: (Int) -> Unit,
        onHazeBlurEnabledChange: (Boolean) -> Unit = {},
        onHazeBlurStrengthChange: (Float) -> Unit = {},
        isCachingB30Artwork: Boolean = false,
        b30ArtworkCacheCompleted: Int = 0,
        b30ArtworkCacheTotal: Int = 0,
        onCacheB30Artwork: ((Result<Unit>) -> Unit) -> Unit = {},
        onClearHighResCache: ((Result<Unit>) -> Unit) -> Unit,
        onRedownloadIllustrations: () -> Unit,
        onNavigateToAbout: () -> Unit,
        onLogout: () -> Unit,
        isResettingLocalSession: Boolean = false,
        localSessionResetMessage: UiText? = null,
        onResetLocalSession: () -> Unit = {},
        onNavigateBack: (() -> Unit)? = null,
        tip: String = "",
        apiEnabled: Boolean = false,
        useApiData: Boolean = false,
        apiUserId: String = "",
        apiPlatform: String = "",
        apiPlatformId: String = "",
        apiToken: String = "",
        isApiTesting: Boolean = false,
        apiTestMessage: UiText? = null,
        onApiEnabledChange: (Boolean) -> Unit = {},
        onUseApiDataChange: (Boolean) -> Unit = {},
        onApiUserIdChange: (String) -> Unit = {},
        onApiPlatformChange: (String) -> Unit = {},
        onApiPlatformIdChange: (String) -> Unit = {},
        onApiTokenChange: (String) -> Unit = {},
        onApiTestConnection: () -> Unit = {},
        isUpdatingData: Boolean = false,
        updateDataProgress: Int = 0,
        updateDataTotal: Int = 0,
        updateDataFileName: String = "",
        updateDataPhase: UpdateDataPhase = UpdateDataPhase.Files,
        updateDataError: UiText? = null,
        updateResultSongNames: List<String>? = null,
        updatePartialIllustrationFailures: Int = 0,
        updatePartialAvatarFailures: Int = 0,
        catalogDegraded: Boolean = false,
        collectibleDataVersion: String = "",
        onUpdateSongData: () -> Unit = {},
        onCancelSongDataUpdate: () -> Unit = {},
        onResetPendingUpdate: () -> Unit = {},
        onDismissUpdateError: () -> Unit = {},
        includePreRelease: Boolean = false,
        autoCheckUpdate: Boolean = true,
        autoCheckSongDataUpdate: Boolean = true,
        updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
        onCheckForUpdate: () -> Unit = {},
        onIncludePreReleaseChange: (Boolean) -> Unit = {},
        onAutoCheckUpdateChange: (Boolean) -> Unit = {},
        onAutoCheckSongDataUpdateChange: (Boolean) -> Unit = {},
        onDismissUpdateResult: () -> Unit = {},
        isDebugBuild: Boolean = false,
        hasRuntimeLogs: Boolean = false,
        hasCrashLogs: Boolean = false,
        onExportRuntimeLog: () -> String = { "" },
        onExportCrashLog: () -> String = { "" },
        onClearAllLogs: () -> Boolean = { false },
        crashNotificationGuideShown: Boolean = false,
        onCrashNotificationGuideShown: () -> Unit = {},
        modifier: Modifier = Modifier
) {
    val noRuntimeLogMessage = stringResource(Res.string.settings_no_runtime_log)
    val noCrashLogMessage = stringResource(Res.string.settings_no_crash_log)
    val themeColorExtractedMessage = stringResource(Res.string.settings_theme_color_extracted_toast)
    val artworkPreloadDoneMessage = stringResource(Res.string.settings_artwork_preload_done)
    val artworkPreloadFailedMessage = stringResource(Res.string.settings_artwork_preload_failed)
    val clearCacheDoneMessage = stringResource(Res.string.settings_clear_cache_done)
    val noRuntimeLogTitle = stringResource(Res.string.settings_no_runtime_log_title)
    val noCrashLogTitle = stringResource(Res.string.settings_no_crash_log_title)
    val exportRuntimeLogFailedTitle = stringResource(Res.string.settings_export_runtime_log_failed)
    val exportCrashLogFailedTitle = stringResource(Res.string.settings_export_crash_log_failed)
    val exportFailedUnknownError = stringResource(Res.string.common_unknown_error)
    val clearLogsDoneTitle = stringResource(Res.string.settings_clear_logs_done_title)
    val clearLogsDoneMessage = stringResource(Res.string.settings_clear_logs_done_message)
    val clearLogsFailedTitle = stringResource(Res.string.settings_clear_logs_failed_title)
    val clearLogsFailedMessage =
            stringResource(Res.string.settings_clear_logs_failed_message, exportFailedUnknownError)
    val notificationPermissionDeniedMessage =
            stringResource(Res.string.settings_notification_permission_denied_toast)

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLocalSessionResetDialog by remember { mutableStateOf(false) }
    var showCatalogManagementDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showRedownloadDialog by remember { mutableStateOf(false) }
    var showApiRiskDialog by remember { mutableStateOf(false) }
    var showApiCredentialDialog by remember { mutableStateOf(false) }
    var apiTokenVisible by remember { mutableStateOf(false) }
    var showUpdateDataDialog by remember { mutableStateOf(false) }
    var showResetPendingDialog by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember {
        mutableStateOf(hasCrashNotificationPermission())
    }
    var showNotificationGuideDialog by remember { mutableStateOf(false) }
    var expandedColorSource by remember { mutableStateOf(false) }
    var expandedPaletteStyle by remember { mutableStateOf(false) }
    var expandedColorSpec by remember { mutableStateOf(false) }
    var pendingThemeImageColor by remember { mutableStateOf<ThemeImageColorPickResult?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val pickThemeImageColor = rememberThemeImageColorPicker { result ->
        pendingThemeImageColor = result
    }

    LaunchedEffect(pendingThemeImageColor) {
        val result = pendingThemeImageColor ?: return@LaunchedEffect
        val fallback = argbToColor(themeImageSeedColorArgb ?: seedColorArgb)
        val themeColor =
                withContext(Dispatchers.Default) { result.image.themeColor(fallback = fallback) }
        onThemeImageColorSelected(result.uri, colorToArgb(themeColor))
        onThemeColorSourceChange(THEME_COLOR_SOURCE_IMAGE)
        pendingThemeImageColor = null
        showPlatformMessage(themeColorExtractedMessage)
    }

    LaunchedEffect(isDebugBuild, notificationPermissionGranted, crashNotificationGuideShown) {
        if (shouldShowCrashNotificationGuide(
                        isDebugBuild,
                        notificationPermissionGranted,
                        crashNotificationGuideShown
                )
        ) {
            showNotificationGuideDialog = true
            onCrashNotificationGuideShown()
        }
    }

    // Page-level HazeState, independent from the home one: the scrolling
    // settings content is the haze source and slides up behind the progressive
    // glass top bar, same pattern as the song detail page
    val settingsHazeState = rememberHazeState()
    val settingsGlassStyle = rememberGlassHazeStyle()
    // Hoisted so the top bar can shrink its title when the content scrolls
    val settingsScrollState = rememberScrollState()
    val settingsAtTop by remember { derivedStateOf { settingsScrollState.value < 48 } }

    Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                GlassTopBar(hazeState = settingsHazeState, style = settingsGlassStyle) {
                    TopAppBar(
                            title = {
                                Column {
                                    Text(
                                            stringResource(Res.string.action_settings),
                                            style = rememberCollapsingTitleStyle(
                                                    compact = !settingsAtTop
                                            )
                                    )
                                    if (tip.isNotBlank()) {
                                        Text(
                                                text = tip,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                modifier =
                                                        Modifier.fillMaxWidth(0.75f)
                                                                .basicMarquee()
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                if (onNavigateBack != null) {
                                    IconButton(onClick = onNavigateBack) {
                                        Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(Res.string.action_back)
                                        )
                                    }
                                }
                            },
                            colors =
                                    TopAppBarDefaults.topAppBarColors(
                                            containerColor = Color.Transparent
                                    )
                    )
                }
            }
    ) { innerPadding ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .hazeSource(state = settingsHazeState)
                                .verticalScroll(settingsScrollState)
                                .padding(
                                        top = innerPadding.calculateTopPadding(),
                                        bottom = innerPadding.calculateBottomPadding()
                                )
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val themeOptions =
                        listOf(
                                stringResource(Res.string.settings_theme_follow_system),
                                stringResource(Res.string.settings_theme_light),
                                stringResource(Res.string.settings_theme_dark),
                                stringResource(Res.string.settings_theme_amoled)
                        )
                val darkModeTitle = stringResource(Res.string.settings_dark_mode)
                val paletteStyleTitle = stringResource(Res.string.settings_palette_style)
                val colorSpecTitle = stringResource(Res.string.settings_color_spec)
                val languageOptions =
                        listOf(
                                SettingsConstants.LANGUAGE_SYSTEM to stringResource(Res.string.settings_language_follow_system),
                                SettingsConstants.LANGUAGE_ENGLISH to "English",
                                SettingsConstants.LANGUAGE_ZH_HANS to "简体中文",
                                SettingsConstants.LANGUAGE_ZH_HANT to "繁體中文"
                        )
                val languageTitle = stringResource(Res.string.settings_language_title)
                val colorSourceOptions =
                        listOf(
                                THEME_COLOR_SOURCE_SYSTEM to
                                        stringResource(Res.string.settings_color_source_default),
                                THEME_COLOR_SOURCE_IMAGE to
                                        stringResource(Res.string.settings_color_source_image)
                        )
                val themeColorSourceTitle = stringResource(Res.string.settings_theme_color_source)
                val defaultColorSourceLabel = stringResource(Res.string.settings_color_source_default)
                val blurStrengthTitle = stringResource(Res.string.settings_blur_strength)
                val overflowCountTitle = stringResource(Res.string.settings_overflow_count)
                var expandedTheme by remember { mutableStateOf(false) }
                var expandedLanguage by remember { mutableStateOf(false) }

                SettingsSection(
                        title = stringResource(Res.string.settings_category_interface_theme)
                ) {
                    valueItem(
                            key = "theme_mode",
                            headline = darkModeTitle,
                            value = themeOptions.getOrElse(themeMode) { themeOptions[0] },
                            onClick = { expandedTheme = true },
                            menuContent = {
                                DropdownMenu(
                                        expanded = expandedTheme,
                                        onDismissRequest = { expandedTheme = false }
                                ) {
                                    themeOptions.forEachIndexed { index, title ->
                                        SettingsMenuItem(
                                                text = title,
                                                selected = themeMode == index,
                                                onClick = {
                                                    onThemeModeChange(index)
                                                    expandedTheme = false
                                                }
                                        )
                                    }
                                }
                            }
                    )

                    valueItem(
                            key = "palette_style",
                            headline = paletteStyleTitle,
                            value = paletteStyleName,
                            onClick = { expandedPaletteStyle = true },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_palette_style_desc))
                            },
                            menuContent = {
                                DropdownMenu(
                                        expanded = expandedPaletteStyle,
                                        onDismissRequest = { expandedPaletteStyle = false }
                                ) {
                                    PaletteStyle.entries.forEach { style ->
                                        SettingsMenuItem(
                                                text = style.name,
                                                selected = paletteStyleName == style.name,
                                                onClick = {
                                                    onPaletteStyleNameChange(style.name)
                                                    expandedPaletteStyle = false
                                                }
                                        )
                                    }
                                }
                            }
                    )

                    valueItem(
                            key = "color_spec",
                            headline = colorSpecTitle,
                            value = if (colorSpecVersion == "SPEC_2025") "2025" else "2021",
                            onClick = { expandedColorSpec = true },
                            supportingContent = {
                                Text(
                                        stringResource(
                                                if (colorSpecVersion == "SPEC_2025" && !supportsSpec2025(paletteStyleName)) {
                                                    Res.string.settings_color_spec_fallback
                                                } else {
                                                    Res.string.settings_color_spec_desc
                                                }
                                        )
                                )
                            },
                            menuContent = {
                                DropdownMenu(
                                        expanded = expandedColorSpec,
                                        onDismissRequest = { expandedColorSpec = false }
                                ) {
                                    listOf("SPEC_2021" to "2021", "SPEC_2025" to "2025").forEach { (version, label) ->
                                        SettingsMenuItem(
                                                text = label,
                                                selected = colorSpecVersion == version,
                                                onClick = {
                                                    onColorSpecVersionChange(version)
                                                    expandedColorSpec = false
                                                }
                                        )
                                    }
                                }
                            }
                    )

                    valueItem(
                            key = "language",
                            headline = languageTitle,
                            value = languageOptions
                                    .firstOrNull { it.first == appLanguage }
                                    ?.second
                                    ?: languageOptions[0].second,
                            onClick = { expandedLanguage = true },
                            menuContent = {
                                DropdownMenu(
                                        expanded = expandedLanguage,
                                        onDismissRequest = { expandedLanguage = false }
                                ) {
                                    languageOptions.forEach { (tag, label) ->
                                        SettingsMenuItem(
                                                text = label,
                                                selected = appLanguage == tag,
                                                onClick = {
                                                    onAppLanguageChange(tag)
                                                    expandedLanguage = false
                                                }
                                        )
                                    }
                                }
                            }
                    )

                    if (shouldShowThemeColorSourceSetting) {
                        valueItem(
                                key = "theme_color_source",
                                headline = themeColorSourceTitle,
                                value = colorSourceOptions
                                        .firstOrNull { it.first == themeColorSource }
                                        ?.second
                                        ?: defaultColorSourceLabel,
                                onClick = { expandedColorSource = true },
                                supportingContent = {
                                    Text(
                                            if (themeColorSource == THEME_COLOR_SOURCE_IMAGE &&
                                                            themeImageSeedColorArgb != null
                                            ) {
                                                stringResource(Res.string.settings_theme_color_extracted_subtitle)
                                            } else {
                                                stringResource(Res.string.settings_theme_color_source_desc)
                                            }
                                    )
                                },
                                menuContent = {
                                    DropdownMenu(
                                            expanded = expandedColorSource,
                                            onDismissRequest = { expandedColorSource = false }
                                    ) {
                                        SettingsMenuItem(
                                                text = stringResource(Res.string.settings_color_source_default),
                                                selected = themeColorSource == THEME_COLOR_SOURCE_SYSTEM,
                                                onClick = {
                                                    onThemeImageColorClear()
                                                    onThemeColorSourceChange(THEME_COLOR_SOURCE_SYSTEM)
                                                    expandedColorSource = false
                                                }
                                        )
                                        SettingsMenuItem(
                                                text = stringResource(Res.string.settings_color_source_image),
                                                selected = themeColorSource == THEME_COLOR_SOURCE_IMAGE,
                                                onClick = {
                                                    expandedColorSource = false
                                                    pickThemeImageColor()
                                                }
                                        )
                                    }
                                }
                        )
                    }

                    // The switch only has an effect in follow-system mode; the
                    // dedicated AMOLED mode (3) always uses pure black.
                    if (themeMode == 0) {
                        switchItem(
                                key = "amoled_black",
                                headlineContent = { Text(stringResource(Res.string.settings_amoled_black)) },
                                checked = amoledBlack,
                                onCheckedChange = onAmoledBlackChange,
                                supportingContent = {
                                    Text(stringResource(Res.string.settings_amoled_black_desc))
                                }
                        )
                    }

                    switchItem(
                            key = "haze_blur",
                            headlineContent = { Text(stringResource(Res.string.settings_haze_blur)) },
                            checked = hazeBlurEnabled,
                            onCheckedChange = onHazeBlurEnabledChange,
                            supportingContent = {
                                Text(stringResource(Res.string.settings_haze_blur_desc))
                            }
                    )

                    if (hazeBlurEnabled) {
                        sliderItem(
                                key = "haze_blur_strength",
                                headline = blurStrengthTitle,
                                valueText = "${(hazeBlurStrength * 100).roundToInt()}%",
                                value = hazeBlurStrength,
                                onValueChange = onHazeBlurStrengthChange,
                                valueRange = SettingsConstants.HAZE_STRENGTH_MIN..SettingsConstants.HAZE_STRENGTH_MAX,
                                steps = SettingsConstants.HAZE_SLIDER_STEPS
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsSection(title = stringResource(Res.string.settings_category_b30)) {
                    switchItem(
                            key = "show_b30_overflow",
                            headlineContent = { Text(stringResource(Res.string.settings_show_overflow)) },
                            checked = showB30Overflow,
                            onCheckedChange = onShowB30OverflowChange,
                            supportingContent = {
                                Text(stringResource(Res.string.settings_show_overflow_desc))
                            }
                    )

                    if (showB30Overflow) {
                        sliderItem(
                                key = "b30_overflow_count",
                                headline = overflowCountTitle,
                                valueText = overflowCount.toString(),
                                value = overflowCount.toFloat(),
                                onValueChange = { onOverflowCountChange(it.roundToInt()) },
                                valueRange = SettingsConstants.OVERFLOW_COUNT_MIN.toFloat()..
                                        SettingsConstants.OVERFLOW_COUNT_MAX.toFloat(),
                                steps = SettingsConstants.OVERFLOW_SLIDER_STEPS
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsSection(title = stringResource(Res.string.settings_category_score_api)) {
                    switchItem(
                            key = "score_api_enabled",
                            headlineContent = { Text(stringResource(Res.string.settings_enable_score_api)) },
                            checked = apiEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showApiRiskDialog = true
                                } else {
                                    onApiEnabledChange(false)
                                }
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_enable_score_api_desc))
                            }
                    )

                    if (apiEnabled) {
                        val credentialsConfigured = apiPlatform.isNotBlank() &&
                                apiPlatformId.isNotBlank() && apiUserId.isNotBlank() && apiToken.isNotBlank()

                        actionItem(
                                key = "score_api_credentials",
                                onClick = { showApiCredentialDialog = true },
                                headlineContent = {
                                    Text(stringResource(Res.string.settings_api_credentials))
                                },
                                supportingContent = {
                                    Text(
                                            if (credentialsConfigured) {
                                                stringResource(Res.string.settings_api_credentials_configured)
                                            } else {
                                                stringResource(Res.string.settings_api_credentials_not_configured)
                                            }
                                    )
                                }
                        )

                        switchItem(
                                key = "score_api_use_data",
                                headlineContent = { Text(stringResource(Res.string.settings_use_api_data)) },
                                checked = useApiData,
                                onCheckedChange = onUseApiDataChange,
                                supportingContent = {
                                    Column {
                                        Text(stringResource(Res.string.settings_use_api_data_desc))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(stringResource(Res.string.settings_api_data_disclaimer))
                                    }
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsSection(title = stringResource(Res.string.settings_category_updates)) {
                    switchItem(
                            key = "auto_check_update",
                            headlineContent = { Text(stringResource(Res.string.settings_auto_check_update)) },
                            checked = autoCheckUpdate,
                            onCheckedChange = onAutoCheckUpdateChange,
                            supportingContent = {
                                Text(stringResource(Res.string.settings_auto_check_update_desc))
                            }
                    )
                    switchItem(
                            key = "auto_check_song_data_update",
                            headlineContent = {
                                Text(stringResource(Res.string.settings_auto_check_song_data_update))
                            },
                            checked = autoCheckSongDataUpdate,
                            onCheckedChange = onAutoCheckSongDataUpdateChange,
                            supportingContent = {
                                Text(stringResource(Res.string.settings_auto_check_song_data_update_desc))
                            }
                    )
                    switchItem(
                            key = "include_prerelease",
                            headlineContent = { Text(stringResource(Res.string.settings_include_prerelease)) },
                            checked = includePreRelease,
                            onCheckedChange = onIncludePreReleaseChange,
                            supportingContent = {
                                Column {
                                    Text(stringResource(Res.string.settings_include_prerelease_desc))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(stringResource(Res.string.settings_prerelease_notice))
                                }
                            }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsSection(title = stringResource(Res.string.settings_category_data_cache)) {
                    actionItem(
                            key = "cache_b30_artwork",
                            onClick = {
                                onCacheB30Artwork { result ->
                                    if (result.isSuccess) {
                                        showPlatformMessage(artworkPreloadDoneMessage)
                                    } else {
                                        showPlatformMessage(artworkPreloadFailedMessage)
                                    }
                                }
                            },
                            headlineContent = {
                                Text(stringResource(Res.string.settings_cache_b30_artwork))
                            },
                            supportingContent = {
                                Text(
                                        if (isCachingB30Artwork) {
                                            stringResource(
                                                    Res.string.settings_caching_progress,
                                                    b30ArtworkCacheCompleted,
                                                    b30ArtworkCacheTotal
                                            )
                                        } else {
                                            stringResource(Res.string.settings_cache_b30_artwork_desc)
                                        }
                                )
                            },
                            leadingContent = {
                                if (isCachingB30Artwork) {
                                    AppCircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                                }
                            },
                            enabled = !isCachingB30Artwork
                    )
                    actionItem(
                            key = "clear_artwork_cache",
                            onClick = { showClearCacheDialog = true },
                            headlineContent = {
                                Text(stringResource(Res.string.settings_clear_artwork_cache))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_clear_artwork_cache_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            }
                    )
                    actionItem(
                            key = "redownload_artwork",
                            onClick = { showRedownloadDialog = true },
                            headlineContent = {
                                Text(stringResource(Res.string.settings_redownload_artwork))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_redownload_artwork_desc))
                            },
                            leadingContent = {
                                Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                )
                            }
                    )
                    actionItem(
                            key = "catalog_management",
                            onClick = { showCatalogManagementDialog = true },
                            headlineContent = {
                                Text(stringResource(Res.string.settings_catalog_management))
                            },
                            supportingContent = {
                                Column {
                                    Text(stringResource(Res.string.settings_catalog_management_desc))
                                    if (catalogDegraded) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                                text = stringResource(Res.string.settings_catalog_using_bundled),
                                                color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                            stringResource(
                                                    Res.string.settings_collectible_data_version,
                                                    collectibleDataVersion.ifBlank { "—" }
                                            )
                                    )
                                }
                            },
                            leadingContent = {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                            }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsSection(title = stringResource(Res.string.settings_category_diagnostics)) {
                    actionItem(
                            key = "reset_local_session",
                            onClick = { showLocalSessionResetDialog = true },
                            headlineContent = {
                                Text(stringResource(Res.string.login_reset_local_session))
                            },
                            supportingContent = {
                                Column {
                                    Text(stringResource(Res.string.login_reset_local_session_dialog_text))
                                    localSessionResetMessage?.let { message ->
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                                text = message.asString(),
                                                color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            },
                            trailingContent = {
                                if (isResettingLocalSession) {
                                    AppCircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                    )
                                }
                            },
                            enabled = !isResettingLocalSession
                    )
                    actionItem(
                            key = "export_crash_log",
                            onClick = exportCrashLog@ {
                                if (!hasCrashLogs) {
                                    showPlatformAlert(noCrashLogTitle, noCrashLogMessage)
                                    return@exportCrashLog
                                }
                                val text = onExportCrashLog()
                                if (text.isBlank()) {
                                    showPlatformAlert(noCrashLogTitle, noCrashLogMessage)
                                    return@exportCrashLog
                                }
                                coroutineScope.launch {
                                    val result = shareTextLog(text, "phitracker_crash_reports.txt")
                                    if (result.isFailure) {
                                        showPlatformAlert(
                                                exportCrashLogFailedTitle,
                                                getString(
                                                        Res.string.settings_export_crash_failed_message,
                                                        result.exceptionOrNull()?.message
                                                                ?: exportFailedUnknownError
                                                )
                                        )
                                    }
                                }
                            },
                            headlineContent = {
                                Text(stringResource(Res.string.settings_export_crash_log))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_export_crash_log_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                            }
                    )
                    actionItem(
                            key = "clear_logs",
                            onClick = {
                                val ok = onClearAllLogs()
                                if (ok) {
                                    showPlatformAlert(clearLogsDoneTitle, clearLogsDoneMessage)
                                } else {
                                    showPlatformAlert(clearLogsFailedTitle, clearLogsFailedMessage)
                                }
                            },
                            headlineContent = {
                                Text(stringResource(Res.string.settings_clear_logs))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_clear_logs_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            }
                    )

                    if (isDebugBuild) {
                        actionItem(
                                key = "crash_notification_permission",
                                onClick = {
                                    requestCrashNotificationPermission { granted ->
                                        notificationPermissionGranted = granted
                                        if (!granted) {
                                            showPlatformMessage(notificationPermissionDeniedMessage)
                                        }
                                    }
                                },
                                headlineContent = {
                                    Text(stringResource(Res.string.settings_crash_notification_permission))
                                },
                                supportingContent = {
                                    Text(
                                            if (notificationPermissionGranted) {
                                                stringResource(Res.string.settings_crash_notification_granted)
                                            } else {
                                                stringResource(Res.string.settings_crash_notification_denied)
                                            }
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                }
                        )
                        actionItem(
                                key = "export_runtime_log",
                                onClick = exportRuntimeLog@ {
                                    if (!hasRuntimeLogs) {
                                        showPlatformAlert(noRuntimeLogTitle, noRuntimeLogMessage)
                                        return@exportRuntimeLog
                                    }
                                    val text = onExportRuntimeLog()
                                    if (text.isBlank()) {
                                        showPlatformAlert(noRuntimeLogTitle, noRuntimeLogMessage)
                                        return@exportRuntimeLog
                                    }
                                    coroutineScope.launch {
                                        val result = shareTextLog(text, "phitracker_runtime_logs.txt")
                                        if (result.isFailure) {
                                            showPlatformAlert(
                                                    exportRuntimeLogFailedTitle,
                                                    getString(
                                                            Res.string.settings_export_runtime_failed_message,
                                                            result.exceptionOrNull()?.message
                                                                    ?: exportFailedUnknownError
                                                    )
                                            )
                                        }
                                    }
                                },
                                headlineContent = {
                                    Text(stringResource(Res.string.settings_export_runtime_log))
                                },
                                supportingContent = {
                                    Text(stringResource(Res.string.settings_export_runtime_log_desc))
                                },
                                leadingContent = {
                                    Icon(Icons.Default.BugReport, contentDescription = null)
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsSection(title = stringResource(Res.string.settings_category_about)) {
                    actionItem(
                            key = "check_app_update",
                            onClick = onCheckForUpdate,
                            headlineContent = {
                                Text(stringResource(Res.string.settings_check_update))
                            },
                            supportingContent = {
                                when (val state = updateCheckState) {
                                    is UpdateCheckState.Checking ->
                                        Text(stringResource(Res.string.settings_checking_update))
                                    is UpdateCheckState.NoUpdate ->
                                        Text(stringResource(Res.string.settings_already_latest))
                                    is UpdateCheckState.Error ->
                                        Text(
                                                stringResource(
                                                        Res.string.settings_check_update_failed,
                                                        state.message
                                                )
                                        )
                                    else ->
                                        Text(stringResource(Res.string.settings_check_update_desc))
                                }
                            },
                            leadingContent = {
                                if (updateCheckState is UpdateCheckState.Checking) {
                                    AppCircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                                }
                            },
                            enabled = updateCheckState !is UpdateCheckState.Checking
                    )
                    actionItem(
                            key = "about_app",
                            onClick = onNavigateToAbout,
                            headlineContent = {
                                Text(stringResource(Res.string.settings_about_app))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_about_app_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Default.Info, contentDescription = null)
                            },
                            trailingContent = {
                                Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null
                                )
                            }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                )
                ) { Text(stringResource(Res.string.settings_logout)) }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showLocalSessionResetDialog) {
        AnimatedAlertDialog(
                onDismissRequest = { showLocalSessionResetDialog = false },
                title = { Text(stringResource(Res.string.login_reset_local_session)) },
                text = { Text(stringResource(Res.string.login_reset_local_session_dialog_text)) },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showLocalSessionResetDialog = false
                                onResetLocalSession()
                            }
                    ) { Text(stringResource(Res.string.login_reset_local_session)) }
                },
                dismissButton = {
                    TextButton(onClick = { showLocalSessionResetDialog = false }) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
        )
    }

    if (showLogoutDialog) {
        AnimatedAlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text(stringResource(Res.string.settings_logout)) },
                text = {
                    Text(stringResource(Res.string.settings_logout_dialog_text))
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showLogoutDialog = false
                                onLogout()
                            },
                            colors =
                                    ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                    )
                    ) { Text(stringResource(Res.string.settings_logout_confirm)) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showLogoutDialog = false }) {
                            Text(stringResource(Res.string.settings_cancel))
                        }
                        TextButton(
                                onClick = {
                                    showLogoutDialog = false
                                    onResetLocalSession()
                                },
                                enabled = !isResettingLocalSession
                        ) { Text(stringResource(Res.string.settings_logout_preserve_data)) }
                    }
                }
        )
    }

    if (showApiRiskDialog) {
        AnimatedAlertDialog(
                onDismissRequest = { showApiRiskDialog = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text(stringResource(Res.string.settings_enable_score_api)) },
                text = {
                    Text(stringResource(Res.string.settings_api_risk_dialog_text))
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showApiRiskDialog = false
                                onApiEnabledChange(true)
                            }
                    ) { Text(stringResource(Res.string.settings_api_risk_accept)) }
                },
                dismissButton = {
                    TextButton(onClick = { showApiRiskDialog = false }) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
        )
    }

    if (showApiCredentialDialog) {
        AnimatedAlertDialog(
                onDismissRequest = { showApiCredentialDialog = false },
                icon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                title = {
                    Text(stringResource(Res.string.settings_api_credentials_dialog_title))
                },
                text = {
                    Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                                text = stringResource(Res.string.settings_api_credentials_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                                value = apiPlatform,
                                onValueChange = onApiPlatformChange,
                                label = { Text(stringResource(Res.string.settings_api_platform_name)) },
                                placeholder = { Text("platform") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                                value = apiPlatformId,
                                onValueChange = onApiPlatformIdChange,
                                label = { Text(stringResource(Res.string.settings_api_platform_id)) },
                                placeholder = { Text("platform_id") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                                value = apiUserId,
                                onValueChange = onApiUserIdChange,
                                label = { Text(stringResource(Res.string.settings_api_user_id)) },
                                placeholder = { Text("api_user_id") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                                value = apiToken,
                                onValueChange = onApiTokenChange,
                                label = { Text("API Token") },
                                placeholder = { Text("api_token") },
                                singleLine = true,
                                visualTransformation =
                                        if (apiTokenVisible) VisualTransformation.None
                                        else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { apiTokenVisible = !apiTokenVisible }) {
                                        Icon(
                                                imageVector =
                                                        if (apiTokenVisible) Icons.Default.VisibilityOff
                                                        else Icons.Default.Visibility,
                                                contentDescription =
                                                        if (apiTokenVisible)
                                                                stringResource(
                                                                        Res.string.settings_hide_api_token
                                                                )
                                                        else
                                                                stringResource(
                                                                        Res.string.settings_show_api_token
                                                                )
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                                onClick = onApiTestConnection,
                                enabled = !isApiTesting,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isApiTesting) {
                                AppCircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                    if (isApiTesting)
                                            stringResource(Res.string.settings_api_testing)
                                    else stringResource(Res.string.settings_api_test_connection)
                            )
                        }

                        if (apiTestMessage != null) {
                            Text(
                                    text = apiTestMessage.asString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showApiCredentialDialog = false }) {
                        Text(stringResource(Res.string.settings_done))
                    }
                }
        )
    }

    if (showClearCacheDialog) {
        AnimatedAlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text(stringResource(Res.string.settings_clear_cache_dialog_title)) },
                text = {
                    Text(stringResource(Res.string.settings_clear_cache_dialog_text))
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showClearCacheDialog = false
                                onClearHighResCache { result ->
                                    coroutineScope.launch {
                                        if (result.isSuccess) {
                                            showPlatformMessage(clearCacheDoneMessage)
                                        } else {
                                            showPlatformMessage(
                                                    getString(
                                                            Res.string.settings_clear_cache_failed,
                                                            result.exceptionOrNull()?.message
                                                                    ?: exportFailedUnknownError
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                    ) { Text(stringResource(Res.string.common_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
        )
    }

    if (showRedownloadDialog) {
        AnimatedAlertDialog(
                onDismissRequest = { showRedownloadDialog = false },
                title = { Text(stringResource(Res.string.settings_redownload_dialog_title)) },
                text = {
                    Text(stringResource(Res.string.settings_redownload_dialog_text))
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showRedownloadDialog = false
                                onRedownloadIllustrations()
                            },
                            colors =
                                    ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                    )
                    ) { Text(stringResource(Res.string.common_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRedownloadDialog = false }) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
        )
    }

    if (showCatalogManagementDialog) {
        AnimatedAlertDialog(
                onDismissRequest = { showCatalogManagementDialog = false },
                title = { Text(stringResource(Res.string.settings_catalog_management)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(Res.string.settings_catalog_management_desc))
                        Text(
                                stringResource(
                                        Res.string.settings_collectible_data_version,
                                        collectibleDataVersion.ifBlank { "—" }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (catalogDegraded) {
                            Text(
                                    text = stringResource(Res.string.settings_catalog_using_bundled),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                            )
                        }
                        OutlinedButton(
                                onClick = {
                                    showCatalogManagementDialog = false
                                    showUpdateDataDialog = true
                                },
                                enabled = !isUpdatingData,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.settings_update_song_data))
                        }
                        OutlinedButton(
                                onClick = {
                                    showCatalogManagementDialog = false
                                    showResetPendingDialog = true
                                },
                                enabled = !isUpdatingData,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.settings_reset_pending_update))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCatalogManagementDialog = false }) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
        )
    }

    if (showUpdateDataDialog) {
        AnimatedAlertDialog(
                onDismissRequest = { showUpdateDataDialog = false },
                title = { Text(stringResource(Res.string.settings_update_song_data)) },
                text = {
                    Text(stringResource(Res.string.settings_update_data_dialog_text))
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showUpdateDataDialog = false
                                onUpdateSongData()
                            }
                    ) { Text(stringResource(Res.string.common_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDataDialog = false }) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
        )
    }

    if (showResetPendingDialog) {
        AnimatedAlertDialog(
                onDismissRequest = { showResetPendingDialog = false },
                title = { Text(stringResource(Res.string.settings_reset_pending_update)) },
                text = { Text(stringResource(Res.string.settings_reset_pending_update_dialog_text)) },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showResetPendingDialog = false
                                onResetPendingUpdate()
                            }
                    ) { Text(stringResource(Res.string.common_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetPendingDialog = false }) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
        )
    }

    if (isUpdatingData) {
        AnimatedAlertDialog(
                onDismissRequest = {},
                properties =
                        DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                title = { Text(stringResource(Res.string.settings_update_song_data)) },
                text = {
                    Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                                text =
                                        when {
                                            updateDataPhase == UpdateDataPhase.Avatars ->
                                                    stringResource(
                                                            Res.string.settings_syncing_avatars,
                                                            updateDataFileName,
                                                            updateDataProgress,
                                                            updateDataTotal
                                                    )
                                            updateDataPhase == UpdateDataPhase.Illustrations ->
                                                    stringResource(
                                                            Res.string.settings_syncing_new_artwork,
                                                            updateDataFileName,
                                                            updateDataProgress,
                                                            updateDataTotal
                                                    )
                                            updateDataTotal > 0 ->
                                                    stringResource(
                                                            Res.string.settings_downloading_file,
                                                            updateDataFileName,
                                                            updateDataProgress,
                                                            updateDataTotal
                                                    )
                                            else -> stringResource(Res.string.settings_update_preparing)
                                        },
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        val progress =
                                if (updateDataTotal > 0)
                                        updateDataProgress.toFloat() / updateDataTotal
                                else 0f
                        AppLinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onCancelSongDataUpdate) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
        )
    }

    if (updateDataError != null) {
        AnimatedAlertDialog(
                onDismissRequest = onDismissUpdateError,
                title = { Text(stringResource(Res.string.settings_update_failed)) },
                text = {
                    Text(
                            stringResource(
                                    Res.string.settings_update_error_message,
                                    updateDataError.asString()
                            )
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismissUpdateError) {
                        Text(stringResource(Res.string.common_confirm))
                    }
                }
        )
    }

    if (updateResultSongNames != null) {
        AnimatedAlertDialog(
                onDismissRequest = onDismissUpdateResult,
                title = { Text(stringResource(Res.string.settings_update_complete)) },
                text = {
                    Column {
                        if (updateResultSongNames.isEmpty()) {
                            Text(stringResource(Res.string.settings_update_no_new_songs))
                        } else {
                            Text(
                                    stringResource(
                                            Res.string.settings_update_new_songs,
                                            updateResultSongNames.size
                                    ) +
                                            updateResultSongNames.joinToString(
                                                    stringResource(
                                                            Res.string.settings_song_name_separator
                                                    )
                                            )
                            )
                        }
                        if (updatePartialIllustrationFailures > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                    text = stringResource(
                                            Res.string.settings_update_partial_illustrations,
                                            updatePartialIllustrationFailures
                                    ),
                                    color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (updatePartialAvatarFailures > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                    text = stringResource(
                                            Res.string.settings_update_partial_avatars,
                                            updatePartialAvatarFailures
                                    ),
                                    color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismissUpdateResult) {
                        Text(stringResource(Res.string.common_confirm))
                    }
                }
        )
    }

    if (showNotificationGuideDialog) {
        AnimatedAlertDialog(
                onDismissRequest = {
                    showNotificationGuideDialog = false
                    onCrashNotificationGuideShown()
                },
                title = {
                    Text(stringResource(Res.string.settings_crash_notification_guide_title))
                },
                text = {
                    Text(stringResource(Res.string.settings_crash_notification_guide_text))
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showNotificationGuideDialog = false
                                onCrashNotificationGuideShown()
                                requestCrashNotificationPermission { granted ->
                                    notificationPermissionGranted = granted
                                    if (!granted) {
                                        showPlatformMessage(
                                                notificationPermissionDeniedMessage
                                        )
                                    }
                                }
                            }
                    ) { Text(stringResource(Res.string.settings_enable_now)) }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                showNotificationGuideDialog = false
                                onCrashNotificationGuideShown()
                            }
                    ) { Text(stringResource(Res.string.settings_later)) }
                }
        )
    }

    val updateState = updateCheckState
    if (updateState is UpdateCheckState.Available) {
        UpdateResultDialog(
                version = updateState.version,
                body = updateState.body,
                htmlUrl = updateState.htmlUrl,
                onDismiss = onDismissUpdateResult,
                onDownload = { uriHandler ->
                    onDismissUpdateResult()
                    uriHandler.openUri(updateState.htmlUrl)
                }
        )
    }
}

internal fun shouldShowCrashNotificationGuide(
        isDebugBuild: Boolean,
        permissionGranted: Boolean,
        alreadyShown: Boolean
): Boolean = isDebugBuild && !permissionGranted && !alreadyShown
