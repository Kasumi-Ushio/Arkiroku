package org.kasumi321.ushio.phitracker

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.ConfigureCoilImageLoader
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.ui.DataRecoveryGate
import org.kasumi321.ushio.phitracker.ui.PhiTrackerNavHost
import org.kasumi321.ushio.phitracker.ui.components.PlatformAlertHost
import org.kasumi321.ushio.phitracker.ui.glass.GlassSettings
import org.kasumi321.ushio.phitracker.ui.glass.LocalGlassSettings
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerTheme
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings
import org.kasumi321.ushio.phitracker.ui.theme.SystemBarAppearanceEffect
import org.kasumi321.ushio.phitracker.ui.theme.resolveAmoled
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    ConfigureCoilImageLoader()

    AppLogger.event("startup", "App.beforeSettingsInject")
    val settingsRepository: SettingsRepository = koinInject()
    AppLogger.event("startup", "App.afterSettingsInject")
    val themeMode by settingsRepository.themeMode.collectAsState(initial = 0)
    val amoledBlack by settingsRepository.amoledBlack.collectAsState(initial = false)
    val themeColorSource by settingsRepository.themeColorSource.collectAsState(initial = "system")
    val seedColorArgb by settingsRepository.seedColorArgb.collectAsState(initial = -10011977)
    val imageSeedColorArgb by settingsRepository.themeImageSeedColorArgb.collectAsState(initial = null)
    val paletteStyleName by settingsRepository.paletteStyleName.collectAsState(initial = "TonalSpot")
    val hazeBlurEnabled by settingsRepository.hazeBlurEnabled.collectAsState(initial = true)
    val hazeBlurStrength by settingsRepository.hazeBlurStrength.collectAsState(initial = 0.75f)

    val darkTheme = when (themeMode) {
        1 -> false
        2, 3 -> true
        else -> isSystemInDarkTheme()
    }
    val isAmoled = resolveAmoled(themeMode, amoledBlack)
    val themeSettings = PhiTrackerThemeSettings(
        themeMode = themeMode,
        colorSource = themeColorSource,
        seedColorArgb = seedColorArgb,
        imageSeedColorArgb = imageSeedColorArgb,
        paletteStyleName = paletteStyleName
    )

    PhiTrackerTheme(darkTheme = darkTheme, isAmoled = isAmoled, settings = themeSettings) {
        // Status bar / navigation bar icon contrast follows the in-app theme
        // mode; the one-time enableEdgeToEdge in MainActivity only reads the
        // system night mode and never updates on runtime theme changes.
        SystemBarAppearanceEffect(darkTheme)
        CompositionLocalProvider(
            LocalGlassSettings provides GlassSettings(
                blurEnabled = hazeBlurEnabled,
                blurStrength = hazeBlurStrength
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                DataRecoveryGate {
                    PhiTrackerNavHost()
                }
                PlatformAlertHost()
            }
        }
    }
}
