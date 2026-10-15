package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
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
import org.kasumi321.ushio.phitracker.ui.components.CenteredListItem
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog
import org.kasumi321.ushio.phitracker.ui.glass.GlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.rememberCollapsingTitleStyle
import org.kasumi321.ushio.phitracker.ui.glass.rememberGlassHazeStyle
import org.kasumi321.ushio.phitracker.ui.theme.THEME_COLOR_SOURCE_IMAGE
import org.kasumi321.ushio.phitracker.ui.theme.THEME_COLOR_SOURCE_SYSTEM
import org.kasumi321.ushio.phitracker.ui.theme.argbToColor
import org.kasumi321.ushio.phitracker.ui.theme.colorToArgb
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
import phitracker.composeapp.generated.resources.settings_category_b30
import phitracker.composeapp.generated.resources.settings_category_data_cache
import phitracker.composeapp.generated.resources.settings_category_debug
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
import phitracker.composeapp.generated.resources.settings_no_crash_log
import phitracker.composeapp.generated.resources.settings_no_crash_log_title
import phitracker.composeapp.generated.resources.settings_no_runtime_log
import phitracker.composeapp.generated.resources.settings_no_runtime_log_title
import phitracker.composeapp.generated.resources.settings_notification_permission_denied_toast
import phitracker.composeapp.generated.resources.settings_overflow_count
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
import phitracker.composeapp.generated.resources.settings_update_song_data
import phitracker.composeapp.generated.resources.settings_update_song_data_desc
import phitracker.composeapp.generated.resources.settings_use_api_data
import phitracker.composeapp.generated.resources.settings_use_api_data_desc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
        themeMode: Int,
        themeColorSource: String = THEME_COLOR_SOURCE_SYSTEM,
        seedColorArgb: Int = -10011977,
        themeImageSeedColorArgb: Int? = null,
        themeImageUri: String? = null,
        paletteStyleName: String = PaletteStyle.TonalSpot.name,
        showB30Overflow: Boolean,
        overflowCount: Int,
        hazeBlurEnabled: Boolean = true,
        hazeBlurStrength: Float = 0.75f,
        appLanguage: String = "system",
        onAppLanguageChange: (String) -> Unit = {},
        onThemeModeChange: (Int) -> Unit,
        onThemeColorSourceChange: (String) -> Unit = {},
        onSeedColorArgbChange: (Int) -> Unit = {},
        onThemeImageColorSelected: (String?, Int) -> Unit = { _, _ -> },
        onThemeImageColorClear: () -> Unit = {},
        onPaletteStyleNameChange: (String) -> Unit = {},
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
        onUpdateSongData: () -> Unit = {},
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
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showRedownloadDialog by remember { mutableStateOf(false) }
    var showApiRiskDialog by remember { mutableStateOf(false) }
    var showApiCredentialDialog by remember { mutableStateOf(false) }
    var apiTokenVisible by remember { mutableStateOf(false) }
    var showUpdateDataDialog by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember {
        mutableStateOf(hasCrashNotificationPermission())
    }
    var showNotificationGuideDialog by remember { mutableStateOf(false) }
    var expandedColorSource by remember { mutableStateOf(false) }
    var expandedPaletteStyle by remember { mutableStateOf(false) }
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
                CategoryTitle(stringResource(Res.string.settings_category_interface_theme))

                val themeOptions =
                        listOf(
                                stringResource(Res.string.settings_theme_follow_system),
                                stringResource(Res.string.settings_theme_light),
                                stringResource(Res.string.settings_theme_dark),
                                stringResource(Res.string.settings_theme_amoled)
                        )
                var expandedTheme by remember { mutableStateOf(false) }

                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            stringResource(Res.string.settings_dark_mode),
                            style = MaterialTheme.typography.bodyLarge
                    )
                    Box {
                        TextButton(onClick = { expandedTheme = true }) {
                            Text(themeOptions.getOrElse(themeMode) { themeOptions[0] })
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                                expanded = expandedTheme,
                                onDismissRequest = { expandedTheme = false }
                        ) {
                            themeOptions.forEachIndexed { index, title ->
                                DropdownMenuItem(
                                        text = { Text(title) },
                                        onClick = {
                                            onThemeModeChange(index)
                                            expandedTheme = false
                                        }
                                )
                            }
                        }
                    }
                }

                val languageOptions =
                        listOf(
                                SettingsConstants.LANGUAGE_SYSTEM to stringResource(Res.string.settings_language_follow_system),
                                SettingsConstants.LANGUAGE_ENGLISH to "English",
                                SettingsConstants.LANGUAGE_ZH_HANS to "简体中文",
                                SettingsConstants.LANGUAGE_ZH_HANT to "繁體中文"
                        )
                var expandedLanguage by remember { mutableStateOf(false) }

                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            stringResource(Res.string.settings_language_title),
                            style = MaterialTheme.typography.bodyLarge
                    )
                    Box {
                        TextButton(onClick = { expandedLanguage = true }) {
                            Text(
                                    languageOptions
                                            .firstOrNull { it.first == appLanguage }
                                            ?.second
                                            ?: languageOptions[0].second
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                                expanded = expandedLanguage,
                                onDismissRequest = { expandedLanguage = false }
                        ) {
                            languageOptions.forEach { (tag, label) ->
                                DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onAppLanguageChange(tag)
                                            expandedLanguage = false
                                        }
                                )
                            }
                        }
                    }
                }

                if (shouldShowThemeColorSourceSetting) {
                    val colorSourceOptions =
                            listOf(
                                    THEME_COLOR_SOURCE_SYSTEM to
                                            stringResource(Res.string.settings_color_source_default),
                                    THEME_COLOR_SOURCE_IMAGE to
                                            stringResource(Res.string.settings_color_source_image)
                            )

                    Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    stringResource(Res.string.settings_theme_color_source),
                                    style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                    text =
                                            if (themeColorSource == THEME_COLOR_SOURCE_IMAGE &&
                                                            themeImageSeedColorArgb != null
                                            ) {
                                                stringResource(
                                                        Res.string.settings_theme_color_extracted_subtitle
                                                )
                                            } else {
                                                stringResource(
                                                        Res.string.settings_theme_color_source_desc
                                                )
                                            },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            TextButton(onClick = { expandedColorSource = true }) {
                                Text(
                                        colorSourceOptions
                                                .firstOrNull { it.first == themeColorSource }
                                                ?.second
                                                ?: stringResource(
                                                        Res.string.settings_color_source_default
                                                )
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                    expanded = expandedColorSource,
                                    onDismissRequest = { expandedColorSource = false }
                            ) {
                                DropdownMenuItem(
                                        text = {
                                            Text(stringResource(Res.string.settings_color_source_default))
                                        },
                                        onClick = {
                                            onThemeImageColorClear()
                                            onThemeColorSourceChange(THEME_COLOR_SOURCE_SYSTEM)
                                            expandedColorSource = false
                                        }
                                )
                                DropdownMenuItem(
                                        text = {
                                            Text(stringResource(Res.string.settings_color_source_image))
                                        },
                                        onClick = {
                                            expandedColorSource = false
                                            pickThemeImageColor()
                                        }
                                )
                            }
                        }
                    }
                }

                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                stringResource(Res.string.settings_palette_style),
                                style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                                text = stringResource(Res.string.settings_palette_style_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        TextButton(onClick = { expandedPaletteStyle = true }) {
                            Text(paletteStyleName)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                                expanded = expandedPaletteStyle,
                                onDismissRequest = { expandedPaletteStyle = false }
                        ) {
                            PaletteStyle.entries.forEach { style ->
                                DropdownMenuItem(
                                        text = { Text(style.name) },
                                        onClick = {
                                            onPaletteStyleNameChange(style.name)
                                            expandedPaletteStyle = false
                                        }
                                )
                            }
                        }
                    }
                }

                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                stringResource(Res.string.settings_haze_blur),
                                style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                                text = stringResource(Res.string.settings_haze_blur_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = hazeBlurEnabled, onCheckedChange = { onHazeBlurEnabledChange(it) })
                }

                if (hazeBlurEnabled) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(Res.string.settings_blur_strength))
                            Text(
                                    text = "${(hazeBlurStrength * 100).roundToInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                                value = hazeBlurStrength,
                                onValueChange = { onHazeBlurStrengthChange(it) },
                                valueRange =
                                        SettingsConstants.HAZE_STRENGTH_MIN..SettingsConstants
                                                .HAZE_STRENGTH_MAX,
                                steps = SettingsConstants.HAZE_SLIDER_STEPS,
                                modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                CategoryTitle(stringResource(Res.string.settings_category_b30))
                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                stringResource(Res.string.settings_show_overflow),
                                style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                                text = stringResource(Res.string.settings_show_overflow_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = showB30Overflow, onCheckedChange = { onShowB30OverflowChange(it) })
                }

                if (showB30Overflow) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(Res.string.settings_overflow_count))
                            Text(
                                    text = overflowCount.toString(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                                value = overflowCount.toFloat(),
                                onValueChange = { onOverflowCountChange(it.roundToInt()) },
                                valueRange =
                                        SettingsConstants.OVERFLOW_COUNT_MIN
                                                .toFloat()..SettingsConstants.OVERFLOW_COUNT_MAX
                                                .toFloat(),
                                steps = SettingsConstants.OVERFLOW_SLIDER_STEPS,
                                modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                CategoryTitle(stringResource(Res.string.settings_category_score_api))

                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                stringResource(Res.string.settings_enable_score_api),
                                style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                                text = stringResource(Res.string.settings_enable_score_api_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = apiEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showApiRiskDialog = true
                                } else {
                                    onApiEnabledChange(false)
                                }
                            }
                    )
                }

                if (apiEnabled) {
                    val credentialsConfigured = apiPlatform.isNotBlank() &&
                            apiPlatformId.isNotBlank() && apiUserId.isNotBlank() && apiToken.isNotBlank()

                    CenteredListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.settings_api_credentials))
                            },
                            supportingContent = {
                                Text(
                                        text =
                                                if (credentialsConfigured)
                                                        stringResource(
                                                                Res.string.settings_api_credentials_configured
                                                        )
                                                else
                                                        stringResource(
                                                                Res.string.settings_api_credentials_not_configured
                                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                )
                            },
                            modifier = Modifier.clickable { showApiCredentialDialog = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    stringResource(Res.string.settings_use_api_data),
                                    style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                    text = stringResource(Res.string.settings_use_api_data_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = useApiData, onCheckedChange = onUseApiDataChange)
                    }

                    Text(
                            text = stringResource(Res.string.settings_api_data_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                CategoryTitle(stringResource(Res.string.settings_category_updates))

                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                stringResource(Res.string.settings_auto_check_update),
                                style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                                text = stringResource(Res.string.settings_auto_check_update_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoCheckUpdate, onCheckedChange = { onAutoCheckUpdateChange(it) })
                }

                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                stringResource(Res.string.settings_auto_check_song_data_update),
                                style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                                text = stringResource(Res.string.settings_auto_check_song_data_update_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = autoCheckSongDataUpdate,
                            onCheckedChange = { onAutoCheckSongDataUpdateChange(it) }
                    )
                }

                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                stringResource(Res.string.settings_include_prerelease),
                                style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                                text = stringResource(Res.string.settings_include_prerelease_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = includePreRelease,
                            onCheckedChange = { onIncludePreReleaseChange(it) }
                    )
                }

                Text(
                        text = stringResource(Res.string.settings_prerelease_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                CategoryTitle(stringResource(Res.string.settings_category_data_cache))

                CenteredListItem(
                        headlineContent = {
                            Text(stringResource(Res.string.settings_cache_b30_artwork))
                        },
                        supportingContent = {
                            if (isCachingB30Artwork) {
                                Text(
                                        stringResource(
                                                Res.string.settings_caching_progress,
                                                b30ArtworkCacheCompleted,
                                                b30ArtworkCacheTotal
                                        )
                                )
                            } else {
                                Text(
                                        stringResource(Res.string.settings_cache_b30_artwork_desc)
                                )
                            }
                        },
                        leadingContent = {
                            if (isCachingB30Artwork) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                            }
                        },
                        modifier =
                                Modifier.clickable(enabled = !isCachingB30Artwork) {
                                    onCacheB30Artwork { result ->
                                        if (result.isSuccess) {
                                            showPlatformMessage(artworkPreloadDoneMessage)
                                        } else {
                                            showPlatformMessage(artworkPreloadFailedMessage)
                                        }
                                    }
                                }
                )

                CenteredListItem(
                        headlineContent = {
                            Text(stringResource(Res.string.settings_clear_artwork_cache))
                        },
                        supportingContent = {
                            Text(stringResource(Res.string.settings_clear_artwork_cache_desc))
                        },
                        leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                        modifier = Modifier.clickable { showClearCacheDialog = true }
                )

                CenteredListItem(
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
                        },
                        modifier = Modifier.clickable { showRedownloadDialog = true }
                )

                CenteredListItem(
                        headlineContent = { Text(stringResource(Res.string.settings_update_song_data)) },
                        supportingContent = {
                            Text(stringResource(Res.string.settings_update_song_data_desc))
                        },
                        leadingContent = {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                        },
                        modifier = Modifier.clickable { showUpdateDataDialog = true }
                )

                if (isDebugBuild) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    CategoryTitle(stringResource(Res.string.settings_category_debug))

                    CenteredListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.settings_crash_notification_permission))
                            },
                            supportingContent = {
                                if (notificationPermissionGranted) {
                                    Text(
                                            stringResource(
                                                    Res.string.settings_crash_notification_granted
                                            )
                                    )
                                } else {
                                    Text(
                                            stringResource(
                                                    Res.string.settings_crash_notification_denied
                                            )
                                    )
                                }
                            },
                            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                            modifier =
                                    Modifier.clickable {
                                        requestCrashNotificationPermission { granted ->
                                            notificationPermissionGranted = granted
                                            if (!granted) {
                                                showPlatformMessage(
                                                        notificationPermissionDeniedMessage
                                                )
                                            }
                                        }
                                    }
                    )

                    CenteredListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.settings_export_runtime_log))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_export_runtime_log_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                            },
                            modifier =
                                    Modifier.clickable {
                                        if (!hasRuntimeLogs) {
                                            showPlatformAlert(noRuntimeLogTitle, noRuntimeLogMessage)
                                            return@clickable
                                        }
                                        val text = onExportRuntimeLog()
                                        if (text.isBlank()) {
                                            showPlatformAlert(noRuntimeLogTitle, noRuntimeLogMessage)
                                            return@clickable
                                        }
                                        coroutineScope.launch {
                                            val result =
                                                    shareTextLog(text, "phitracker_runtime_logs.txt")
                                            if (result.isFailure) {
                                                showPlatformAlert(
                                                        exportRuntimeLogFailedTitle,
                                                        getString(
                                                                Res.string
                                                                        .settings_export_runtime_failed_message,
                                                                result.exceptionOrNull()?.message
                                                                        ?: exportFailedUnknownError
                                                        )
                                                )
                                            }
                                        }
                                    }
                    )

                    CenteredListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.settings_export_crash_log))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_export_crash_log_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                            },
                            modifier =
                                    Modifier.clickable {
                                        if (!hasCrashLogs) {
                                            showPlatformAlert(noCrashLogTitle, noCrashLogMessage)
                                            return@clickable
                                        }
                                        val text = onExportCrashLog()
                                        if (text.isBlank()) {
                                            showPlatformAlert(noCrashLogTitle, noCrashLogMessage)
                                            return@clickable
                                        }
                                        coroutineScope.launch {
                                            val result =
                                                    shareTextLog(text, "phitracker_crash_reports.txt")
                                            if (result.isFailure) {
                                                showPlatformAlert(
                                                        exportCrashLogFailedTitle,
                                                        getString(
                                                                Res.string
                                                                        .settings_export_crash_failed_message,
                                                                result.exceptionOrNull()?.message
                                                                        ?: exportFailedUnknownError
                                                        )
                                                )
                                            }
                                        }
                                    }
                    )

                    CenteredListItem(
                            headlineContent = { Text(stringResource(Res.string.settings_clear_logs)) },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_clear_logs_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            },
                            modifier =
                                    Modifier.clickable {
                                        val ok = onClearAllLogs()
                                        if (ok) {
                                            showPlatformAlert(
                                                    clearLogsDoneTitle,
                                                    clearLogsDoneMessage
                                            )
                                        } else {
                                            showPlatformAlert(
                                                    clearLogsFailedTitle,
                                                    clearLogsFailedMessage
                                            )
                                        }
                                    }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                CategoryTitle(stringResource(Res.string.settings_category_about))

                CenteredListItem(
                        headlineContent = { Text(stringResource(Res.string.settings_check_update)) },
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
                                CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                            }
                        },
                        modifier =
                                Modifier.clickable(
                                        enabled = updateCheckState !is UpdateCheckState.Checking
                                ) { onCheckForUpdate() }
                )

                Spacer(modifier = Modifier.height(4.dp))

                CenteredListItem(
                        headlineContent = { Text(stringResource(Res.string.settings_about_app)) },
                        supportingContent = {
                            Text(stringResource(Res.string.settings_about_app_desc))
                        },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onNavigateToAbout() }
                )

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
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text(stringResource(Res.string.settings_cancel))
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
                                CircularProgressIndicator(
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
                                        if (updateDataPhase == UpdateDataPhase.Illustrations)
                                                stringResource(
                                                        Res.string.settings_syncing_new_artwork,
                                                        updateDataFileName,
                                                        updateDataProgress,
                                                        updateDataTotal
                                                )
                                        else
                                                stringResource(
                                                        Res.string.settings_downloading_file,
                                                        updateDataFileName,
                                                        updateDataProgress,
                                                        updateDataTotal
                                                ),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        val progress =
                                if (updateDataTotal > 0)
                                        updateDataProgress.toFloat() / updateDataTotal
                                else 0f
                        LinearProgressIndicator(
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
                confirmButton = {}
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

@Composable
private fun CategoryTitle(title: String) {
    Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
    )
}

internal fun shouldShowCrashNotificationGuide(
        isDebugBuild: Boolean,
        permissionGranted: Boolean,
        alreadyShown: Boolean
): Boolean = isDebugBuild && !permissionGranted && !alreadyShown
