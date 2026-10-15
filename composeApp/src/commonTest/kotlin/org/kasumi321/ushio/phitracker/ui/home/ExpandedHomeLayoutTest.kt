package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpandedHomeLayoutTest {

    @Test
    fun defaultListPaneWidthFollowsNineToSixteenOfHeight() {
        assertEquals(460.125.dp, defaultListPaneWidth(818.dp))
        assertEquals(360.dp, defaultListPaneWidth(640.dp))
    }

    @Test
    fun clampListPaneWidthEnforcesLowerBound() {
        assertEquals(320.dp, clampListPaneWidth(200.dp, totalWidth = 1365.dp))
    }

    @Test
    fun clampListPaneWidthKeepsDetailPaneMinimum() {
        assertEquals(965.dp, clampListPaneWidth(2000.dp, totalWidth = 1365.dp))
    }

    @Test
    fun clampListPaneWidthCollapsesToLowerBoundWhenWindowTooNarrow() {
        // totalWidth below 720dp cannot honor both bounds; the list pane
        // minimum wins so the rail stays usable.
        assertEquals(320.dp, clampListPaneWidth(500.dp, totalWidth = 600.dp))
    }
}
