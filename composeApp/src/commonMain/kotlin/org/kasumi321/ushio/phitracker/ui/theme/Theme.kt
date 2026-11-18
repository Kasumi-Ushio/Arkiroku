package org.kasumi321.ushio.phitracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.rememberDynamicColorScheme
import org.kasumi321.ushio.phitracker.data.platform.shouldShowThemeColorSourceSetting
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

@Composable
fun PhiTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isAmoled: Boolean = false,
    settings: PhiTrackerThemeSettings = PhiTrackerThemeSettings(),
    expressive: Boolean = true,
    content: @Composable () -> Unit
) {
    val effectiveColorSource = if (shouldShowThemeColorSourceSetting) {
        settings.colorSource
    } else {
        THEME_COLOR_SOURCE_SYSTEM
    }
    val systemSeed = if (effectiveColorSource == THEME_COLOR_SOURCE_SYSTEM) systemSeedColor() else null

    val materialKolorScheme = rememberDynamicColorScheme(
        seedColor = when (effectiveColorSource) {
            THEME_COLOR_SOURCE_IMAGE -> argbToColor(settings.imageSeedColorArgb ?: settings.seedColorArgb)
            THEME_COLOR_SOURCE_SYSTEM -> systemSeed ?: argbToColor(settings.seedColorArgb)
            else -> argbToColor(settings.seedColorArgb)
        },
        isDark = darkTheme,
        isAmoled = isAmoled && darkTheme,
        style = resolvePaletteStyle(settings.paletteStyleName),
        specVersion = resolveColorSpecVersion(settings.colorSpecVersion)
    )

    val baseColorScheme = when (effectiveColorSource) {
        THEME_COLOR_SOURCE_SYSTEM -> materialKolorScheme
        THEME_COLOR_SOURCE_SEED, THEME_COLOR_SOURCE_IMAGE -> materialKolorScheme
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    val colorScheme = if (isAmoled && darkTheme) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF121212)
        )
    } else {
        baseColorScheme
    }

    if (expressive) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = resolveExpressiveMotionScheme(rememberReducedMotionEnabled()),
            shapes = Shapes(),
            typography = Typography(),
            content = content
        )
    } else {
        // Off-screen B30 captures and their onboarding preview intentionally keep
        // the established component metrics instead of inheriting app UI motion.
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
