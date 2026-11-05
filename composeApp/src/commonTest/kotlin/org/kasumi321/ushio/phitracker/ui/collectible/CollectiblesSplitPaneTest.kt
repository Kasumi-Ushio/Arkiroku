package org.kasumi321.ushio.phitracker.ui.collectible

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectiblesSplitPaneTest {
    @Test
    fun expandedPaneSplitsOnlyWhenBothColumnsFit() {
        assertTrue(useSplitChapterPane(isExpandedPane = true, availableWidth = 450.dp))
        assertTrue(useSplitChapterPane(isExpandedPane = true, availableWidth = MinSplitChapterPaneWidth))
        assertFalse(useSplitChapterPane(isExpandedPane = true, availableWidth = 399.dp))
        assertFalse(useSplitChapterPane(isExpandedPane = true, availableWidth = 320.dp))
    }

    @Test
    fun compactAndRailPanesNeverSplit() {
        assertFalse(useSplitChapterPane(isExpandedPane = false, availableWidth = 900.dp))
    }

    @Test
    fun stackedWidthClampsUserFractionToThirds() {
        assertEquals(1f / 3f, stackedListWidthFraction(1f, 0.1f), 0.0001f)
        assertEquals(2f / 3f, stackedListWidthFraction(1f, 0.9f), 0.0001f)
    }

    @Test
    fun stackedWidthMovesBetweenClosedHalfWindowAndFullWidth() {
        assertEquals(0f, stackedListWidthFraction(0f, 0.55f))
        assertEquals(0.55f, stackedListWidthFraction(1f, 0.55f))
        assertEquals(0.775f, stackedListWidthFraction(1.5f, 0.55f), 0.0001f)
        assertEquals(1f, stackedListWidthFraction(2f, 0.55f))
    }

    @Test
    fun stackedWidthReturnsToCustomFractionAfterDetail() {
        assertEquals(1f, stackedListWidthFraction(2f, 0.4f))
        assertEquals(0.4f, stackedListWidthFraction(1f, 0.4f), 0.0001f)
    }
}
