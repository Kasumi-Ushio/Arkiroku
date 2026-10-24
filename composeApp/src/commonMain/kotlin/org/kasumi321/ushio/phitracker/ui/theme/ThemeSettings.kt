package org.kasumi321.ushio.phitracker.ui.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle

const val THEME_COLOR_SOURCE_SYSTEM = "system"
const val THEME_COLOR_SOURCE_SEED = "seed"
const val THEME_COLOR_SOURCE_IMAGE = "image"

const val DEFAULT_THEME_SEED_ARGB: Int = -10011977 // 0xFF6750A4

data class PhiTrackerThemeSettings(
    val themeMode: Int = 0,
    val colorSource: String = THEME_COLOR_SOURCE_SYSTEM,
    val seedColorArgb: Int = DEFAULT_THEME_SEED_ARGB,
    val imageSeedColorArgb: Int? = null,
    val imageUri: String? = null,
    val paletteStyleName: String = PaletteStyle.TonalSpot.name
)

fun resolvePaletteStyle(name: String): PaletteStyle =
    PaletteStyle.entries.firstOrNull { it.name == name } ?: PaletteStyle.TonalSpot

/**
 * Whether the dark scheme should use the AMOLED variant. Theme mode 3 is the
 * dedicated AMOLED mode and always wins; the separate switch only applies to
 * follow-system mode (0). [PhiTrackerTheme] still requires the effective
 * theme to be dark, so a light system keeps the light scheme.
 */
fun resolveAmoled(themeMode: Int, amoledBlack: Boolean): Boolean =
    themeMode == 3 || (themeMode == 0 && amoledBlack)

fun argbToColor(argb: Int): Color {
    val alpha = (argb ushr 24 and 0xFF) / 255f
    val red = (argb ushr 16 and 0xFF) / 255f
    val green = (argb ushr 8 and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    return Color(red = red, green = green, blue = blue, alpha = alpha)
}

fun colorToArgb(color: Color): Int {
    fun Float.channel(): Int = (coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (color.alpha.channel() shl 24) or
        (color.red.channel() shl 16) or
        (color.green.channel() shl 8) or
        color.blue.channel()
}
