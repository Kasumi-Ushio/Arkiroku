package org.kasumi321.ushio.phitracker.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.ui.b30.B30ExportCardStyle
import org.kasumi321.ushio.phitracker.ui.b30.B30ExportSpec
import org.kasumi321.ushio.phitracker.ui.b30.B30_EXPORT_CARD_THUMBNAIL_SCALE
import org.kasumi321.ushio.phitracker.ui.b30.ExportPosterCard
import org.kasumi321.ushio.phitracker.ui.glass.rememberGlassHazeStyle
import org.kasumi321.ushio.phitracker.ui.home.ScoreCardContent
import org.kasumi321.ushio.phitracker.ui.login.LoginMethodSection
import org.kasumi321.ushio.phitracker.ui.login.LoginViewModel
import org.kasumi321.ushio.phitracker.ui.settings.SettingsConstants
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerTheme
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings
import org.kasumi321.ushio.phitracker.ui.utils.asString
import org.koin.compose.viewmodel.koinViewModel
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.b30ex_card_style
import phitracker.composeapp.generated.resources.b30ex_card_style_classic
import phitracker.composeapp.generated.resources.b30ex_card_style_poster
import phitracker.composeapp.generated.resources.b30ex_theme_dark
import phitracker.composeapp.generated.resources.b30ex_theme_follow_global
import phitracker.composeapp.generated.resources.b30ex_theme_light
import phitracker.composeapp.generated.resources.login_success
import phitracker.composeapp.generated.resources.onboarding_appearance_haze
import phitracker.composeapp.generated.resources.onboarding_appearance_preview
import phitracker.composeapp.generated.resources.onboarding_appearance_theme
import phitracker.composeapp.generated.resources.onboarding_api_section_desc
import phitracker.composeapp.generated.resources.onboarding_api_section_title
import phitracker.composeapp.generated.resources.onboarding_back
import phitracker.composeapp.generated.resources.onboarding_b30_preview_unavailable
import phitracker.composeapp.generated.resources.onboarding_done_greeting
import phitracker.composeapp.generated.resources.onboarding_done_start
import phitracker.composeapp.generated.resources.onboarding_login_guest
import phitracker.composeapp.generated.resources.onboarding_next
import phitracker.composeapp.generated.resources.onboarding_preload_cancel
import phitracker.composeapp.generated.resources.onboarding_preload_current_song
import phitracker.composeapp.generated.resources.onboarding_preload_disclosure
import phitracker.composeapp.generated.resources.onboarding_preload_done
import phitracker.composeapp.generated.resources.onboarding_preload_download
import phitracker.composeapp.generated.resources.onboarding_preload_failed
import phitracker.composeapp.generated.resources.onboarding_preload_progress
import phitracker.composeapp.generated.resources.onboarding_preload_retry
import phitracker.composeapp.generated.resources.onboarding_preload_skip
import phitracker.composeapp.generated.resources.onboarding_song_data_failed
import phitracker.composeapp.generated.resources.onboarding_song_data_update_now
import phitracker.composeapp.generated.resources.onboarding_step_b30_message
import phitracker.composeapp.generated.resources.onboarding_step_b30_title
import phitracker.composeapp.generated.resources.onboarding_step_counter
import phitracker.composeapp.generated.resources.onboarding_step_done_title
import phitracker.composeapp.generated.resources.onboarding_step_language_message
import phitracker.composeapp.generated.resources.onboarding_step_language_title
import phitracker.composeapp.generated.resources.onboarding_step_login_message
import phitracker.composeapp.generated.resources.onboarding_step_login_title
import phitracker.composeapp.generated.resources.onboarding_step_preload_message
import phitracker.composeapp.generated.resources.onboarding_step_preload_title
import phitracker.composeapp.generated.resources.onboarding_step_song_data_message
import phitracker.composeapp.generated.resources.onboarding_step_song_data_title
import phitracker.composeapp.generated.resources.onboarding_step_update_message
import phitracker.composeapp.generated.resources.onboarding_step_update_title
import phitracker.composeapp.generated.resources.onboarding_title
import phitracker.composeapp.generated.resources.onboarding_welcome_message
import phitracker.composeapp.generated.resources.onboarding_welcome_start
import phitracker.composeapp.generated.resources.settings_api_platform_id
import phitracker.composeapp.generated.resources.settings_api_platform_name
import phitracker.composeapp.generated.resources.settings_api_user_id
import phitracker.composeapp.generated.resources.settings_auto_check_song_data_update
import phitracker.composeapp.generated.resources.settings_auto_check_song_data_update_desc
import phitracker.composeapp.generated.resources.settings_auto_check_update
import phitracker.composeapp.generated.resources.settings_auto_check_update_desc
import phitracker.composeapp.generated.resources.settings_blur_strength
import phitracker.composeapp.generated.resources.settings_enable_score_api
import phitracker.composeapp.generated.resources.settings_enable_score_api_desc
import phitracker.composeapp.generated.resources.settings_include_prerelease
import phitracker.composeapp.generated.resources.settings_include_prerelease_desc
import phitracker.composeapp.generated.resources.settings_language_follow_system
import phitracker.composeapp.generated.resources.settings_language_title
import phitracker.composeapp.generated.resources.settings_blur_strength
import phitracker.composeapp.generated.resources.settings_enable_score_api
import phitracker.composeapp.generated.resources.settings_enable_score_api_desc
import phitracker.composeapp.generated.resources.settings_haze_blur_desc
import phitracker.composeapp.generated.resources.settings_palette_style
import phitracker.composeapp.generated.resources.settings_theme_amoled
import phitracker.composeapp.generated.resources.settings_theme_dark
import phitracker.composeapp.generated.resources.settings_theme_follow_system
import phitracker.composeapp.generated.resources.settings_theme_light
import phitracker.composeapp.generated.resources.settings_update_partial_illustrations
import phitracker.composeapp.generated.resources.songs_songdata_checking
import phitracker.composeapp.generated.resources.songs_songdata_downloading
import phitracker.composeapp.generated.resources.songs_songdata_updated
import phitracker.composeapp.generated.resources.songs_songdata_up_to_date

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: (restartRequired: Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val systemDark = isSystemInDarkTheme()
    // themeMode 0 (follow system) is not a valid manual export choice, so the
    // global mode is resolved to a concrete value before use as the manual
    // default — same rule as the B30 export page.
    val resolvedGlobalThemeMode = when (state.themeMode) {
        1 -> 1
        2, 3 -> state.themeMode
        else -> if (systemDark) 2 else 1
    }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished(state.restartRequiredOnFinish)
    }

    // imePadding lifts the whole scaffold (step area AND the bottom bar) above
    // the IME, so the API text fields stay visible while typing; the step
    // Column scrolls and brings the focused field into view. It stacks with
    // the bottom bar's navigationBarsPadding() without consuming its insets.
    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            OnboardingBottomBar(
                stepIndex = state.stepIndex,
                stepCount = state.steps.size,
                canAdvance = state.canAdvance && !state.isFinishing,
                onBack = viewModel::previousStep,
                onNext = viewModel::nextStep,
                onFinish = viewModel::finish
            )
        }
    ) { innerPadding ->
        // Horizontal 16 matches the settings page outer inset; the rows
        // below mirror SettingsTab's native row idioms one-to-one.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            // "Step X of N" beats the dot row for orientation (UX guidance for
            // task wizards); N shifts with the conditional B30 step. The
            // finale hides it — the single start button closes the wizard
            // there and a counter would just be noise.
            if (state.step != OnboardingStep.Completion) {
                Text(
                    text = stringResource(
                        Res.string.onboarding_step_counter,
                        state.stepIndex + 1,
                        state.steps.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            AnimatedContent(targetState = state.step, label = "onboardingStep") { step ->
                when (step) {
                    OnboardingStep.Welcome -> WelcomeStep()
                    OnboardingStep.Language -> LanguageStep(
                        appLanguage = state.appLanguage,
                        themeMode = state.themeMode,
                        paletteStyleName = state.paletteStyleName,
                        hazeBlurEnabled = state.hazeBlurEnabled,
                        hazeBlurStrength = state.hazeBlurStrength,
                        onSelectLanguage = viewModel::selectLanguage,
                        onThemeModeChange = viewModel::selectThemeMode,
                        onPaletteStyleChange = viewModel::selectPaletteStyle,
                        onHazeBlurEnabledChange = viewModel::setHazeBlurEnabled,
                        onHazeBlurStrengthChange = viewModel::setHazeBlurStrength
                    )
                    OnboardingStep.LoginChoice -> LoginChoiceStep(
                        loggedIn = state.loggedIn,
                        apiEnabled = state.apiEnabled,
                        apiUserId = state.apiUserId,
                        apiPlatform = state.apiPlatform,
                        apiPlatformId = state.apiPlatformId,
                        apiToken = state.apiToken,
                        onSkipLogin = { viewModel.selectLoginChoice(OnboardingLoginChoice.Guest) },
                        onLoggedIn = viewModel::onLoggedIn,
                        onApiEnabledChange = viewModel::setApiEnabled,
                        onApiUserIdChange = viewModel::setApiUserId,
                        onApiPlatformChange = viewModel::setApiPlatform,
                        onApiPlatformIdChange = viewModel::setApiPlatformId,
                        onApiTokenChange = viewModel::setApiToken
                    )
                    OnboardingStep.SongDataUpdate -> SongDataUpdateStep(
                        status = state.songDataStatus,
                        completed = state.songDataCompleted,
                        total = state.songDataTotal,
                        currentFile = state.songDataCurrentFile,
                        partialIllustrationFailures = state.songDataPartialIllustrationFailures,
                        onCheck = viewModel::checkSongDataUpdate,
                        onUpdate = viewModel::startSongDataUpdate,
                        onCancel = viewModel::cancelSongDataUpdate
                    )
                    OnboardingStep.IllustrationPreload -> PreloadChoiceStep(
                        choice = state.preloadChoice,
                        status = state.preloadStatus,
                        completed = state.preloadCompleted,
                        total = state.preloadTotal,
                        currentSong = state.preloadCurrentSong,
                        totalCount = state.illustrationTotalCount,
                        onStartDownload = viewModel::startIllustrationPreload,
                        onCancelDownload = viewModel::cancelIllustrationPreload,
                        onSkip = viewModel::skipIllustrationPreload
                    )
                    OnboardingStep.B30Style -> B30StyleStep(
                        b30Records = state.b30Records,
                        cardStyle = state.b30CardStyle,
                        themeFollowGlobal = state.b30ThemeFollowGlobal,
                        exportThemeMode = state.b30ExportThemeMode,
                        globalThemeMode = state.themeMode,
                        paletteStyleName = state.paletteStyleName,
                        systemDark = systemDark,
                        getLowIllustrationUrl = viewModel::getLowIllustrationUrl,
                        onCardStyleChange = viewModel::selectB30CardStyle,
                        onThemeFollowGlobalChange = { follow ->
                            // Mirror the export page: prefill the manual mode
                            // with the current global theme so turning the
                            // switch off never flips the preview appearance.
                            if (!follow && state.b30ExportThemeMode == null) {
                                viewModel.setB30ExportThemeMode(resolvedGlobalThemeMode)
                            }
                            viewModel.setB30ThemeFollowGlobal(follow)
                        },
                        onExportThemeModeChange = viewModel::setB30ExportThemeMode
                    )
                    OnboardingStep.AutoUpdate -> AutoUpdateStep(
                        autoCheckUpdate = state.autoCheckUpdate,
                        autoCheckSongDataUpdate = state.autoCheckSongDataUpdate,
                        includePreRelease = state.includePreRelease,
                        onAutoCheckUpdateChange = viewModel::setAutoCheckUpdate,
                        onAutoCheckSongDataUpdateChange = viewModel::setAutoCheckSongDataUpdate,
                        onIncludePreReleaseChange = viewModel::setIncludePreRelease
                    )
                    OnboardingStep.Completion -> CompletionStep()
                }
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, message: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun LanguageStep(
    appLanguage: String,
    themeMode: Int,
    paletteStyleName: String,
    hazeBlurEnabled: Boolean,
    hazeBlurStrength: Float,
    onSelectLanguage: (String) -> Unit,
    onThemeModeChange: (Int) -> Unit,
    onPaletteStyleChange: (String) -> Unit,
    onHazeBlurEnabledChange: (Boolean) -> Unit,
    onHazeBlurStrengthChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            StepHeader(
                title = stringResource(Res.string.onboarding_step_language_title),
                message = stringResource(Res.string.onboarding_step_language_message)
            )

            // Settings-like rows: label on the left, dropdown picker on the right,
            // whole row tappable — same idiom as the Settings page.
            val languageOptions = listOf(
                SettingsConstants.LANGUAGE_SYSTEM to stringResource(Res.string.settings_language_follow_system),
                SettingsConstants.LANGUAGE_ENGLISH to "English",
                SettingsConstants.LANGUAGE_ZH_HANS to "简体中文",
                SettingsConstants.LANGUAGE_ZH_HANT to "繁體中文"
            )
            val selectedLanguageIndex = languageOptions.indexOfFirst { it.first == appLanguage }.coerceAtLeast(0)
            SettingsDropdownRow(
                title = stringResource(Res.string.settings_language_title),
                options = languageOptions.map { it.second },
                selectedIndex = selectedLanguageIndex,
                onSelect = { index -> onSelectLanguage(languageOptions[index].first) }
            )

            val themeOptions = listOf(
                stringResource(Res.string.settings_theme_follow_system),
                stringResource(Res.string.settings_theme_light),
                stringResource(Res.string.settings_theme_dark),
                stringResource(Res.string.settings_theme_amoled)
            )
            SettingsDropdownRow(
                title = stringResource(Res.string.onboarding_appearance_theme),
                options = themeOptions,
                selectedIndex = themeMode.coerceIn(0, themeOptions.lastIndex),
                onSelect = onThemeModeChange
            )
            SettingsDropdownRow(
                title = stringResource(Res.string.settings_palette_style),
                options = PaletteStyle.entries.map { it.name },
                selectedIndex = PaletteStyle.entries.indexOfFirst { it.name == paletteStyleName }.coerceAtLeast(0),
                onSelect = { index -> onPaletteStyleChange(PaletteStyle.entries[index].name) }
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.onboarding_appearance_haze),
                description = stringResource(Res.string.settings_haze_blur_desc),
                checked = hazeBlurEnabled,
                onCheckedChange = onHazeBlurEnabledChange
            )

            if (hazeBlurEnabled) {
                // Mirror of the settings tab's blur-strength block: label row
                // with a bold primary percentage, slider underneath.
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
                        onValueChange = onHazeBlurStrengthChange,
                        valueRange = SettingsConstants.HAZE_STRENGTH_MIN..SettingsConstants.HAZE_STRENGTH_MAX,
                        steps = SettingsConstants.HAZE_SLIDER_STEPS,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(Res.string.onboarding_appearance_preview),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    HazeBlurPreview()
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        }
    }

/**
 * Mirror of the settings tab's dropdown row (SettingsTab: theme/language
 * rows): plain bodyLarge title, trailing TextButton with the current value
 * and a dropdown caret (TextButton content color = primary, ripple included),
 * menu anchored in a Box. Keep visually identical to the settings tab — it
 * is the source of truth for this row style.
 */
@Composable
private fun SettingsDropdownRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(options.getOrElse(selectedIndex) { options.first() })
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, label ->
                    DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSelect(index)
                                expanded = false
                            }
                    )
                }
            }
        }
    }
}

/**
 * Mirror of the settings tab's switch row with a subtitle (SettingsTab: haze
 * row): title bodyLarge + subtitle bodySmall/onSurfaceVariant in a weighted
 * Column, trailing Switch.
 */
@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
            )
            Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AppearanceSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * Live frosted-glass demo. The vivid layer underneath is a local haze source
 * and the overlaying plate blurs it through [rememberGlassHazeStyle], which
 * reads the app-root [LocalGlassSettings]; the repository setters apply
 * immediately, so dragging the strength slider updates the preview in place.
 */
@Composable
private fun HazeBlurPreview() {
    val hazeState = rememberHazeState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFEF5350),
                            Color(0xFFAB47BC),
                            Color(0xFF5C6BC0),
                            Color(0xFF29B6F6),
                            Color(0xFF9CCC65)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Color(0xFFFFEE58),
                    Color(0xFFFF7043),
                    Color(0xFF26A69A),
                    Color(0xFFEC407A)
                ).forEach { circle ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(circle)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = rememberGlassHazeStyle()
                )
        )
    }
}

@Composable
private fun LoginChoiceStep(
    loggedIn: Boolean,
    apiEnabled: Boolean,
    apiUserId: String,
    apiPlatform: String,
    apiPlatformId: String,
    apiToken: String,
    onSkipLogin: () -> Unit,
    onLoggedIn: () -> Unit,
    onApiEnabledChange: (Boolean) -> Unit,
    onApiUserIdChange: (String) -> Unit,
    onApiPlatformChange: (String) -> Unit,
    onApiPlatformIdChange: (String) -> Unit,
    onApiTokenChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepHeader(
                title = stringResource(Res.string.onboarding_step_login_title),
                message = stringResource(Res.string.onboarding_step_login_message)
            )
            InlineLoginSection(onLoggedIn = onLoggedIn, onSkip = onSkipLogin)
            if (loggedIn) {
                ApiSetupSection(
                    apiEnabled = apiEnabled,
                    apiUserId = apiUserId,
                    apiPlatform = apiPlatform,
                    apiPlatformId = apiPlatformId,
                    apiToken = apiToken,
                    onApiEnabledChange = onApiEnabledChange,
                    onApiUserIdChange = onApiUserIdChange,
                    onApiPlatformChange = onApiPlatformChange,
                    onApiPlatformIdChange = onApiPlatformIdChange,
                    onApiTokenChange = onApiTokenChange
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        }
    }

/**
 * Full login flow embedded in the wizard: a wizard-scoped [LoginViewModel]
 * (independent from the Login page instance) drives the shared
 * [LoginMethodSection] directly — the step IS the login form, no extra
 * "log in now" gate button. QR polling is cancelled by [QrLoginContent] itself
 * when the step leaves composition. Once isLoggedIn flips, [onLoggedIn]
 * reports back so the wizard can offer the score API setup in place, and the
 * "continue without login" escape hatch below disappears.
 */
@Composable
private fun InlineLoginSection(onLoggedIn: () -> Unit, onSkip: () -> Unit) {
    val loginViewModel: LoginViewModel = koinViewModel()
    val loginState by loginViewModel.uiState.collectAsState()

    LaunchedEffect(loginState.isLoggedIn) {
        if (loginState.isLoggedIn) onLoggedIn()
    }

    if (loginState.isCheckingToken) {
        CircularProgressIndicator()
        return
    }

    if (loginState.isLoggedIn) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.login_success),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        return
    }

    LoginMethodSection(
        state = loginState,
        onUpdateServer = loginViewModel::updateServer,
        onStartQrLogin = loginViewModel::startQrLogin,
        onCancelQrLogin = loginViewModel::cancelQrLogin,
        onTokenChange = loginViewModel::updateToken,
        onLogin = loginViewModel::login
    )

    // Token-login failures surface as plain state.error (the Login page shows
    // them in a snackbar); inline there is no scaffold, so show them as text.
    loginState.error?.let { error ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error.asString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Escape hatch under the login form, mirroring the standalone Login
    // page's skip entry: continue as a guest, recording that choice.
    Spacer(modifier = Modifier.height(16.dp))
    TextButton(onClick = onSkip) {
        Text(
            text = stringResource(Res.string.onboarding_login_guest),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Optional score lookup API credentials, offered in place after a successful
 * inline login. Strings mirror the Settings page; unlike Settings there is no
 * risk dialog or connection test here — everything can be adjusted later.
 */
@Composable
private fun ApiSetupSection(
    apiEnabled: Boolean,
    apiUserId: String,
    apiPlatform: String,
    apiPlatformId: String,
    apiToken: String,
    onApiEnabledChange: (Boolean) -> Unit,
    onApiUserIdChange: (String) -> Unit,
    onApiPlatformChange: (String) -> Unit,
    onApiPlatformIdChange: (String) -> Unit,
    onApiTokenChange: (String) -> Unit
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

    AppearanceSectionTitle(stringResource(Res.string.onboarding_api_section_title))

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settings_enable_score_api),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(Res.string.settings_enable_score_api_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = apiEnabled, onCheckedChange = onApiEnabledChange)
    }

    if (apiEnabled) {
        OutlinedTextField(
            value = apiPlatform,
            onValueChange = onApiPlatformChange,
            label = { Text(stringResource(Res.string.settings_api_platform_name)) },
            placeholder = { Text("platform") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiPlatformId,
            onValueChange = onApiPlatformIdChange,
            label = { Text(stringResource(Res.string.settings_api_platform_id)) },
            placeholder = { Text("platform_id") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiUserId,
            onValueChange = onApiUserIdChange,
            label = { Text(stringResource(Res.string.settings_api_user_id)) },
            placeholder = { Text("api_user_id") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiToken,
            onValueChange = onApiTokenChange,
            label = { Text("API Token") },
            placeholder = { Text("api_token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        // The how-to-get-these hints belong next to the fields they describe,
        // and only matter once the section is actually open.
        Text(
            text = stringResource(Res.string.onboarding_api_section_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SongDataUpdateStep(
    status: OnboardingSongDataStatus,
    completed: Int,
    total: Int,
    currentFile: String?,
    partialIllustrationFailures: Int,
    onCheck: () -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit
) {
    // Entering the step is the explicit request for the upstream probe,
    // regardless of the auto-check toggle.
    LaunchedEffect(Unit) { onCheck() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepHeader(
                title = stringResource(Res.string.onboarding_step_song_data_title),
                message = stringResource(Res.string.onboarding_step_song_data_message)
            )
            when (status) {
                OnboardingSongDataStatus.Idle,
                OnboardingSongDataStatus.Checking -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.songs_songdata_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                OnboardingSongDataStatus.UpToDate -> {
                    Text(
                        text = stringResource(Res.string.songs_songdata_up_to_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                OnboardingSongDataStatus.UpdateAvailable -> {
                    ChoiceButton(
                        text = stringResource(Res.string.onboarding_song_data_update_now),
                        selected = true,
                        onClick = onUpdate
                    )
                }

                OnboardingSongDataStatus.Updating -> {
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else completed.toFloat() / total },
                        modifier = Modifier.fillMaxWidth()
                    )
                    currentFile?.let { file ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(Res.string.songs_songdata_downloading, file, completed, total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(Res.string.onboarding_preload_cancel))
                    }
                }

                OnboardingSongDataStatus.Updated -> {
                    // Same idiom as the preload step: the full bar stays visible so
                    // the completion reads as the end state of the run above it.
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (partialIllustrationFailures > 0) {
                            stringResource(
                                Res.string.settings_update_partial_illustrations,
                                partialIllustrationFailures
                            )
                        } else {
                            stringResource(Res.string.songs_songdata_updated)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (partialIllustrationFailures > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        textAlign = TextAlign.Center
                    )
                }

                OnboardingSongDataStatus.Failed -> {
                    Text(
                        text = stringResource(Res.string.onboarding_song_data_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onCheck) {
                        Text(stringResource(Res.string.onboarding_preload_retry))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        }
    }

@Composable
private fun PreloadChoiceStep(
    choice: OnboardingPreloadChoice,
    status: OnboardingPreloadStatus,
    completed: Int,
    total: Int,
    currentSong: String?,
    totalCount: Int,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepHeader(
                title = stringResource(Res.string.onboarding_step_preload_title),
                message = stringResource(Res.string.onboarding_step_preload_message)
            )
            when (status) {
                OnboardingPreloadStatus.Idle -> {
                    ChoiceButton(
                        text = stringResource(Res.string.onboarding_preload_download),
                        selected = choice == OnboardingPreloadChoice.Download,
                        onClick = onStartDownload
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ChoiceButton(
                        text = stringResource(Res.string.onboarding_preload_skip),
                        selected = choice == OnboardingPreloadChoice.Skip,
                        onClick = onSkip
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.onboarding_preload_disclosure, totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                OnboardingPreloadStatus.Running -> {
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else completed.toFloat() / total },
                        modifier = Modifier.fillMaxWidth()
                    )
                    currentSong?.let { song ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(Res.string.onboarding_preload_current_song, song),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.onboarding_preload_progress, completed, total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onCancelDownload) {
                        Text(stringResource(Res.string.onboarding_preload_cancel))
                    }
                }

                OnboardingPreloadStatus.Succeeded -> {
                    // Keep the full progress bar and counter visible so the
                    // completion reads as the end state of the download above,
                    // not as a disconnected message.
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.onboarding_preload_progress, completed, total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.onboarding_preload_done),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OnboardingPreloadStatus.Failed -> {
                    Text(
                        text = stringResource(Res.string.onboarding_preload_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        OutlinedButton(onClick = onStartDownload) {
                            Text(stringResource(Res.string.onboarding_preload_retry))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(onClick = onSkip) {
                            Text(stringResource(Res.string.onboarding_preload_skip))
                        }
                    }
                }
            }
        }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun B30StyleStep(
    b30Records: List<BestRecord>,
    cardStyle: B30ExportCardStyle,
    themeFollowGlobal: Boolean,
    exportThemeMode: Int?,
    globalThemeMode: Int,
    paletteStyleName: String,
    systemDark: Boolean,
    getLowIllustrationUrl: (String) -> String?,
    onCardStyleChange: (B30ExportCardStyle) -> Unit,
    onThemeFollowGlobalChange: (Boolean) -> Unit,
    onExportThemeModeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepHeader(
                title = stringResource(Res.string.onboarding_step_b30_title),
                message = stringResource(Res.string.onboarding_step_b30_message)
            )

            AppearanceSectionTitle(stringResource(Res.string.b30ex_card_style))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = cardStyle == B30ExportCardStyle.Classic,
                    onClick = { onCardStyleChange(B30ExportCardStyle.Classic) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(Res.string.b30ex_card_style_classic))
                }
                SegmentedButton(
                    selected = cardStyle == B30ExportCardStyle.Poster,
                    onClick = { onCardStyleChange(B30ExportCardStyle.Poster) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(Res.string.b30ex_card_style_poster))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.b30ex_theme_follow_global),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(checked = themeFollowGlobal, onCheckedChange = onThemeFollowGlobalChange)
            }
            AnimatedVisibility(visible = !themeFollowGlobal) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val themeLabels = listOf(
                        stringResource(Res.string.b30ex_theme_light),
                        stringResource(Res.string.b30ex_theme_dark),
                        "AMOLED"
                    )
                    themeLabels.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = exportThemeMode == index + 1,
                            onClick = { onExportThemeModeChange(index + 1) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeLabels.size)
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            AppearanceSectionTitle(stringResource(Res.string.onboarding_appearance_preview))
            Spacer(modifier = Modifier.height(4.dp))
            B30CardPreview(
                b30Records = b30Records,
                cardStyle = cardStyle,
                themeFollowGlobal = themeFollowGlobal,
                exportThemeMode = exportThemeMode,
                globalThemeMode = globalThemeMode,
                paletteStyleName = paletteStyleName,
                systemDark = systemDark,
                getLowIllustrationUrl = getLowIllustrationUrl
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        }
    }

/**
 * Live single-card preview of the B30 style step: renders the player's top
 * record with the currently selected card style and theme, so switching
 * either re-renders the exact card the export image would use. The card keeps
 * the export grid's proportions (4:1) and the Classic card's compact export
 * paddings, mirroring [B30ExportLayout].
 */
@Composable
private fun B30CardPreview(
    b30Records: List<BestRecord>,
    cardStyle: B30ExportCardStyle,
    themeFollowGlobal: Boolean,
    exportThemeMode: Int?,
    globalThemeMode: Int,
    paletteStyleName: String,
    systemDark: Boolean,
    getLowIllustrationUrl: (String) -> String?
) {
    if (b30Records.isEmpty()) {
        Text(
            text = stringResource(Res.string.onboarding_b30_preview_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
        )
        return
    }

    val resolvedGlobalThemeMode = when (globalThemeMode) {
        1 -> 1
        2, 3 -> globalThemeMode
        else -> if (systemDark) 2 else 1
    }
    val effectiveThemeMode =
        if (themeFollowGlobal) resolvedGlobalThemeMode else exportThemeMode ?: resolvedGlobalThemeMode

    // The B30 list arrives sorted by rks; maxByOrNull keeps this correct even
    // for a hand-fed list. The rank label matches the export chain (phi
    // records label as "Pn", the rest as "#n").
    val topRecord = b30Records.maxByOrNull { it.rks } ?: b30Records.first()
    val rankLabel = if (topRecord.isPhi) "P1" else "#1"
    // Render at the export's natural card width (~283dp), centered, instead of
    // stretching the card to the screen width — the layout is designed for the
    // export grid's three-column cell.
    val cardModifier = Modifier
        .widthIn(max = B30ExportSpec.cardWidthDp.dp)
        .fillMaxWidth()
        .aspectRatio(B30ExportSpec.CARD_ASPECT)

    PhiTrackerTheme(
        darkTheme = effectiveThemeMode != 1,
        isAmoled = effectiveThemeMode == 3,
        settings = PhiTrackerThemeSettings(
            themeMode = effectiveThemeMode,
            paletteStyleName = paletteStyleName
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (cardStyle) {
                B30ExportCardStyle.Classic -> ScoreCardContent(
                    record = topRecord,
                    rank = 1,
                    rankLabel = rankLabel,
                    illustrationUri = getLowIllustrationUrl(topRecord.songId),
                    contentHorizontalPadding = 9.dp,
                    contentVerticalPadding = 5.dp,
                    compactText = true,
                    thumbnailScale = B30_EXPORT_CARD_THUMBNAIL_SCALE,
                    onClick = null,
                    modifier = cardModifier
                )

                B30ExportCardStyle.Poster -> ExportPosterCard(
                    record = topRecord,
                    rank = 1,
                    rankLabel = rankLabel,
                    illustrationUri = getLowIllustrationUrl(topRecord.songId),
                    modifier = cardModifier
                )
            }
        }
    }
}

/**
 * Opening screen: the app name and a one-line invitation, centered in the
 * step area. Kept in the same centered idiom as the finale; the bottom bar
 * supplies the only CTA ("Get started"), so there is nothing to scroll.
 */
@Composable
private fun WelcomeStep() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.onboarding_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.onboarding_welcome_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Final confirmation, centered like the welcome screen: one unambiguous
 * finish line and a light greeting — the configuration details already did
 * their job on the previous steps and would only clutter the send-off. The
 * bottom bar reduces to one primary "start using" button on this step.
 */
@Composable
private fun CompletionStep() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.onboarding_step_done_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_done_greeting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text)
        }
    }
}

@Composable
private fun AutoUpdateStep(
    autoCheckUpdate: Boolean,
    autoCheckSongDataUpdate: Boolean,
    includePreRelease: Boolean,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
    onAutoCheckSongDataUpdateChange: (Boolean) -> Unit,
    onIncludePreReleaseChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            StepHeader(
                title = stringResource(Res.string.onboarding_step_update_title),
                message = stringResource(Res.string.onboarding_step_update_message)
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_auto_check_update),
                description = stringResource(Res.string.settings_auto_check_update_desc),
                checked = autoCheckUpdate,
                onCheckedChange = onAutoCheckUpdateChange
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_auto_check_song_data_update),
                description = stringResource(Res.string.settings_auto_check_song_data_update_desc),
                checked = autoCheckSongDataUpdate,
                onCheckedChange = onAutoCheckSongDataUpdateChange
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_include_prerelease),
                description = stringResource(Res.string.settings_include_prerelease_desc),
                checked = includePreRelease,
                onCheckedChange = onIncludePreReleaseChange
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        }
    }

@Composable
private fun OnboardingBottomBar(
    stepIndex: Int,
    stepCount: Int,
    canAdvance: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    // Finale: a single full-width primary button — no back, no dots, one CTA.
    if (stepIndex == stepCount - 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = onFinish,
                enabled = canAdvance,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.onboarding_done_start))
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.CenterStart) {
            if (stepIndex > 0) {
                TextButton(onClick = onBack) {
                    Text(stringResource(Res.string.onboarding_back))
                }
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(stepCount) { index ->
                val selected = index == stepIndex
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        Box(modifier = Modifier.widthIn(min = 72.dp), contentAlignment = Alignment.CenterEnd) {
            // The primary action is a filled button: it is the one CTA that
            // advances the wizard, so it must outrank the plain back button.
            // The opening step says "Get started" instead of a bare "Next".
            Button(onClick = onNext, enabled = canAdvance) {
                Text(
                    text = stringResource(
                        if (stepIndex == 0) Res.string.onboarding_welcome_start
                        else Res.string.onboarding_next
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
