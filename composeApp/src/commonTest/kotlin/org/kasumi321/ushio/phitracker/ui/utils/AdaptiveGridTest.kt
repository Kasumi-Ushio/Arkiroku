package org.kasumi321.ushio.phitracker.ui.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveGridTest {
    @Test
    fun adaptiveColumnCountMatchesGridCellsFormula() {
        assertEquals(1, adaptiveGridColumnCount(availableWidthPx = 300, minColumnWidthPx = 340, spacingPx = 2))
        assertEquals(1, adaptiveGridColumnCount(availableWidthPx = 340, minColumnWidthPx = 340, spacingPx = 2))
        assertEquals(2, adaptiveGridColumnCount(availableWidthPx = 682, minColumnWidthPx = 340, spacingPx = 2))
        assertEquals(3, adaptiveGridColumnCount(availableWidthPx = 1024, minColumnWidthPx = 340, spacingPx = 2))
    }

    @Test
    fun adaptiveColumnCountAlwaysKeepsOneColumn() {
        assertEquals(1, adaptiveGridColumnCount(availableWidthPx = 0, minColumnWidthPx = 340, spacingPx = 2))
        assertEquals(1, adaptiveGridColumnCount(availableWidthPx = -10, minColumnWidthPx = 340, spacingPx = 2))
    }
}
