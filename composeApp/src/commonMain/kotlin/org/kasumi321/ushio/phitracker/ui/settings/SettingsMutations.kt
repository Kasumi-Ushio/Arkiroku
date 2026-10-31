package org.kasumi321.ushio.phitracker.ui.settings

import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.setAppLocale
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import org.kasumi321.ushio.phitracker.ui.utils.resolve
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.settings_api_token_save_failed

fun SettingsViewModel.setThemeMode(value: Int) = launchSetting { settingsRepository.setThemeMode(value) }

fun SettingsViewModel.setAmoledBlack(value: Boolean) = launchSetting {
    AppLogger.event("settings", "amoled_black_changed", mapOf("enabled" to value.toString()))
    settingsRepository.setAmoledBlack(value)
}

fun SettingsViewModel.setThemeColorSource(value: String) = launchSetting {
    AppLogger.event("settings", "theme_color_source_changed", mapOf("source" to value))
    settingsRepository.setThemeColorSource(value)
}

fun SettingsViewModel.setSeedColorArgb(value: Int) = launchSetting {
    AppLogger.event("settings", "theme_seed_color_changed")
    settingsRepository.setSeedColorArgb(value)
}

fun SettingsViewModel.setThemeImageColor(uri: String?, seedColorArgb: Int) = launchSetting {
    AppLogger.event("settings", "theme_image_color_selected", mapOf("uriPresent" to (uri != null).toString()))
    settingsRepository.setThemeImageColor(uri, seedColorArgb)
}

fun SettingsViewModel.clearThemeImageColor() = launchSetting {
    AppLogger.event("settings", "theme_image_color_cleared")
    settingsRepository.clearThemeImageColor()
}

fun SettingsViewModel.setPaletteStyleName(value: String) = launchSetting {
    AppLogger.event("settings", "palette_style_changed", mapOf("style" to value))
    settingsRepository.setPaletteStyleName(value)
}

fun SettingsViewModel.setShowB30Overflow(value: Boolean) = launchSetting {
    AppLogger.event("settings", "b30_overflow_changed", mapOf("enabled" to value.toString()))
    settingsRepository.setShowB30Overflow(value)
}

fun SettingsViewModel.setOverflowCount(value: Int) = launchSetting {
    AppLogger.event("settings", "b30_overflow_count_changed", mapOf("count" to value.toString()))
    settingsRepository.setOverflowCount(value)
}

fun SettingsViewModel.setHazeBlurEnabled(value: Boolean) = launchSetting {
    AppLogger.event("settings", "haze_blur_enabled_changed", mapOf("enabled" to value.toString()))
    settingsRepository.setHazeBlurEnabled(value)
}

fun SettingsViewModel.setHazeBlurStrength(value: Float) = launchSetting {
    AppLogger.event("settings", "haze_blur_strength_changed", mapOf("strength" to value.toString()))
    settingsRepository.setHazeBlurStrength(value)
}

fun SettingsViewModel.setAppLanguage(value: String) = launchSetting {
    AppLogger.event("settings", "app_language_changed", mapOf("language" to value))
    settingsRepository.setAppLanguage(value)
    val restartRequired = setAppLocale(value.takeIf { it != SettingsConstants.LANGUAGE_SYSTEM })
    if (restartRequired) eventChannel.send(SettingsEvent.RestartRequested)
}

fun SettingsViewModel.setApiEnabled(value: Boolean) = launchSetting {
    AppLogger.event("settings", "api_enabled_changed", mapOf("enabled" to value.toString()))
    settingsRepository.setApiEnabled(value)
}

fun SettingsViewModel.setUseApiData(value: Boolean) = launchSetting {
    AppLogger.event("settings", "use_api_data_changed", mapOf("enabled" to value.toString()))
    settingsRepository.setUseApiData(value)
}

fun SettingsViewModel.setApiUserId(value: String) = launchSetting { settingsRepository.setApiId(value) }
fun SettingsViewModel.setApiPlatform(value: String) = launchSetting { settingsRepository.setApiPlatform(value) }
fun SettingsViewModel.setApiPlatformId(value: String) = launchSetting { settingsRepository.setApiPlatformId(value) }
fun SettingsViewModel.setApiToken(value: String) = launchSetting {
    settingsRepository.setApiToken(value).onFailure {
        platformMessage(UiText.Res(Res.string.settings_api_token_save_failed).resolve())
    }
}
fun SettingsViewModel.setIncludePreRelease(value: Boolean) = launchSetting { settingsRepository.setIncludePreRelease(value) }
fun SettingsViewModel.setAutoCheckUpdate(value: Boolean) = launchSetting { settingsRepository.setAutoCheckUpdate(value) }
fun SettingsViewModel.setAutoCheckSongDataUpdate(value: Boolean) = launchSetting { settingsRepository.setAutoCheckSongDataUpdate(value) }
fun SettingsViewModel.setCrashNotificationGuideShown() = launchSetting { settingsRepository.setCrashNotificationGuideShown(true) }

/** Settings entry point for re-running the first-launch wizard on next cold start. */
fun SettingsViewModel.requestOnboardingRerun() = launchSetting {
    AppLogger.event("settings", "onboarding_rerun_requested")
    settingsRepository.setOnboardingCompleted(false)
    settingsRepository.setOnboardingRerunRequested(true)
}
