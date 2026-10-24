package org.kasumi321.ushio.phitracker.ui.collectible

import androidx.compose.ui.unit.dp
import kotlin.test.Test
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
}
