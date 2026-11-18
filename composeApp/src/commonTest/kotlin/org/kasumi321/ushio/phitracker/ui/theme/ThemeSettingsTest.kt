package org.kasumi321.ushio.phitracker.ui.theme

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeSettingsTest {
    @Test
    fun followSystemModeUsesTheAmoledSwitch() {
        assertTrue(resolveAmoled(themeMode = 0, amoledBlack = true))
        assertFalse(resolveAmoled(themeMode = 0, amoledBlack = false))
    }

    @Test
    fun dedicatedAmoledModeAlwaysUsesAmoled() {
        assertTrue(resolveAmoled(themeMode = 3, amoledBlack = false))
        assertTrue(resolveAmoled(themeMode = 3, amoledBlack = true))
    }

    @Test
    fun lightAndDarkModesIgnoreTheAmoledSwitch() {
        assertFalse(resolveAmoled(themeMode = 1, amoledBlack = true))
        assertFalse(resolveAmoled(themeMode = 2, amoledBlack = true))
    }

    @Test
    fun colorSpecParsingFallsBackTo2021() {
        assertEquals(ColorSpec.SpecVersion.SPEC_2021, resolveColorSpecVersion("SPEC_2021"))
        assertEquals(ColorSpec.SpecVersion.SPEC_2025, resolveColorSpecVersion("SPEC_2025"))
        assertEquals(ColorSpec.SpecVersion.SPEC_2021, resolveColorSpecVersion("unknown"))
    }

    @Test
    fun spec2025SupportMatchesMaterialKolorVariants() {
        listOf(
            PaletteStyle.TonalSpot,
            PaletteStyle.Neutral,
            PaletteStyle.Vibrant,
            PaletteStyle.Expressive
        ).forEach { style -> assertTrue(supportsSpec2025(style.name)) }

        listOf(
            PaletteStyle.Monochrome,
            PaletteStyle.Fidelity,
            PaletteStyle.Content,
            PaletteStyle.Rainbow,
            PaletteStyle.FruitSalad
        ).forEach { style -> assertFalse(supportsSpec2025(style.name)) }
    }
}
