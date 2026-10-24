package org.kasumi321.ushio.phitracker.ui.theme

import kotlin.test.Test
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
}
