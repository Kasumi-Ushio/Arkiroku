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
        assertEquals(455.dp, clampListPaneWidth(200.dp, totalWidth = 1365.dp))
    }

    @Test
    fun clampListPaneWidthKeepsDetailPaneMinimum() {
        assertEquals(910.dp, clampListPaneWidth(2000.dp, totalWidth = 1365.dp))
    }

    @Test
    fun clampListPaneWidthCollapsesToUpperBoundWhenWindowTooNarrow() {
        assertEquals(400.dp, clampListPaneWidth(500.dp, totalWidth = 600.dp))
    }

    @Test
    fun clampListPaneWidthAccountsForReservedSpace() {
        assertEquals(894.dp, clampListPaneWidth(2000.dp, totalWidth = 1365.dp, reservedWidth = 16.dp))
    }

    @Test
    fun clampListPaneWidthAddsLeadingContentToMinimum() {
        assertEquals(535.dp, clampListPaneWidth(100.dp, totalWidth = 1365.dp, leadingContentWidth = 80.dp))
    }

    @Test
    fun preferredListPaneWidthPrefersUserDrag() {
        assertEquals(500.dp, preferredListPaneWidth(500f, hingeLeft = 700.dp, height = 818.dp))
    }

    @Test
    fun preferredListPaneWidthAlignsToHingeBeforeNineToSixteen() {
        assertEquals(700.dp, preferredListPaneWidth(Float.NaN, hingeLeft = 700.dp, height = 818.dp))
    }

    @Test
    fun preferredListPaneWidthFallsBackToNineToSixteen() {
        assertEquals(460.125.dp, preferredListPaneWidth(Float.NaN, hingeLeft = null, height = 818.dp))
    }
}
