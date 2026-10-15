package org.kasumi321.ushio.phitracker.ui.utils

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * Single entry point for width-based layout adaptation. All screens must read
 * the window size class through this function instead of calling
 * `currentWindowAdaptiveInfo()` directly, so tests and future API migrations
 * have one place to hook into.
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeClass =
    currentWindowAdaptiveInfoV2().windowSizeClass

/**
 * Bounds (window pixels) of the vertical hinge the device is currently folded
 * around, or null when there is none (flat foldable, non-foldable, iOS). The
 * expanded two-pane layout aligns its split to this hinge and keeps the drag
 * handle clear of it.
 */
@Composable
fun rememberSeparatingVerticalHingeBounds(): Rect? =
    currentWindowAdaptiveInfoV2().windowPosture.separatingVerticalHingeBounds.firstOrNull()

/** Medium width starts at 600dp, Expanded at 840dp, per M3 window size classes. */
fun WindowSizeClass.isAtLeastMedium(): Boolean =
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

fun WindowSizeClass.isExpanded(): Boolean =
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/** Minimum column width for adaptive song/B30 grids on Medium and wider. */
val ADAPTIVE_GRID_MIN_COLUMN_WIDTH: Dp = 340.dp
