package org.kasumi321.ushio.phitracker.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Elastic tab indicators, modeled after MDC-Android's
 * `ElasticTabIndicatorInterpolator`: the leading edge moves first while the
 * trailing edge follows on a slower curve, so the bar stretches toward the new
 * tab and then contracts back to its target width.
 *
 * Two drivers share the same edge logic:
 *  - [ElasticPagerPrimaryIndicator] / [ElasticPagerSecondaryIndicator] follow a
 *    [PagerState] live, so a drag stretches the bar with the finger and a
 *    programmatic pager animation drives it frame by frame.
 *  - [ElasticClickPrimaryIndicator] is used by tab rows without a pager; its
 *    two edges are animated independently with different springs.
 *
 * Indicator height, color and shape come from the Material 3 defaults
 * (`TabRowDefaults.PrimaryIndicator` / `SecondaryIndicator`); only the motion
 * is custom. Reduced motion collapses the elastic easing to a synchronous
 * (non-stretching) animation.
 */

internal data class ElasticIndicatorBounds(val left: Dp, val right: Dp)

/**
 * Pager-driven primary indicator (content-sized, rounded top corners).
 * Typical use: `PrimaryTabRow(indicator = { ElasticPagerPrimaryIndicator(state) })`.
 */
@Composable
fun TabIndicatorScope.ElasticPagerPrimaryIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()
    TabRowDefaults.PrimaryIndicator(
        modifier = modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
            layoutElasticIndicator(
                measurable = measurable,
                constraints = constraints,
                bounds = elasticPagerBounds(
                    pagerState = pagerState,
                    tabPositions = tabPositions,
                    matchContentSize = true,
                    reducedMotion = reducedMotion
                )
            )
        },
        width = Dp.Unspecified
    )
}

/**
 * Pager-driven secondary indicator (full tab width, square bar). Typical use:
 * `SecondaryTabRow(indicator = { ElasticPagerSecondaryIndicator(state) })`.
 */
@Composable
fun TabIndicatorScope.ElasticPagerSecondaryIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()
    TabRowDefaults.SecondaryIndicator(
        modifier = modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
            layoutElasticIndicator(
                measurable = measurable,
                constraints = constraints,
                bounds = elasticPagerBounds(
                    pagerState = pagerState,
                    tabPositions = tabPositions,
                    matchContentSize = false,
                    reducedMotion = reducedMotion
                )
            )
        }
    )
}

/**
 * Click-driven primary indicator for tab rows without a pager (login /
 * onboarding login step). The leading edge uses a stiffer spring and the
 * trailing edge a softer one; the width difference during the animation is the
 * elastic stretch.
 *
 * The target edges depend on [TabPosition]s, which Material 3 only exposes
 * inside [TabIndicatorScope.tabIndicatorLayout]. A zero-size probe therefore
 * captures the positions on the first layout pass; the animated indicator is
 * composed afterwards, so its `animateDpAsState` values initialize at the
 * selected tab instead of animating in from zero.
 */
@Composable
fun TabIndicatorScope.ElasticClickPrimaryIndicator(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier
) {
    var tabPositions by remember { mutableStateOf<List<TabPosition>>(emptyList()) }

    if (tabPositions.isEmpty()) {
        Box(
            modifier = Modifier.tabIndicatorLayout { _, _, positions ->
                if (positions != tabPositions) tabPositions = positions
                layout(0, 0) {}
            }
        )
    } else {
        val positions = tabPositions
        val targetBounds = positions
            .getOrNull(selectedTabIndex.coerceIn(positions.indices))
            ?.indicatorBounds(matchContentSize = true)
            ?: ElasticIndicatorBounds(0.dp, 0.dp)

        // Freeze the direction for one selection change; otherwise a
        // recomposition during the animation would swap the two specs.
        val lastSelected = remember { intArrayOf(selectedTabIndex) }
        val movingRight = remember(selectedTabIndex) {
            val moving = selectedTabIndex >= lastSelected[0]
            lastSelected[0] = selectedTabIndex
            moving
        }
        val reducedMotion = rememberReducedMotionEnabled()

        val leadingSpec = remember(reducedMotion) {
            if (reducedMotion) {
                snap<Dp>()
            } else {
                spring<Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            }
        }
        val trailingSpec = remember(reducedMotion) {
            if (reducedMotion) {
                snap<Dp>()
            } else {
                spring<Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
            }
        }
        val left by animateDpAsState(
            targetValue = targetBounds.left,
            animationSpec = if (movingRight) trailingSpec else leadingSpec,
            label = "elasticTabIndicatorLeft"
        )
        val right by animateDpAsState(
            targetValue = targetBounds.right,
            animationSpec = if (movingRight) leadingSpec else trailingSpec,
            label = "elasticTabIndicatorRight"
        )

        TabRowDefaults.PrimaryIndicator(
            modifier = modifier.tabIndicatorLayout { measurable, constraints, measuredPositions ->
                if (measuredPositions != tabPositions) tabPositions = measuredPositions
                layoutElasticIndicator(
                    measurable = measurable,
                    constraints = constraints,
                    bounds = ElasticIndicatorBounds(left, right)
                )
            },
            width = Dp.Unspecified
        )
    }
}

/**
 * Interpolates the visible indicator edges for the current pager position.
 *
 * `currentPage + currentPageOffsetFraction` is a continuous, monotonic page
 * coordinate across the 0.5 snap flip. Between the two neighbouring tabs:
 *  - the edge in the direction of travel (the right edge when moving toward a
 *    higher tab) uses the fast-start/slow-end curve,
 *  - the opposite edge uses the slow-start/fast-end curve.
 * Applying the pair to the lower-to-upper tab bounds makes the same formula
 * work for forward and backward drags, matching MDC's two easing functions.
 */
private fun elasticPagerBounds(
    pagerState: PagerState,
    tabPositions: List<TabPosition>,
    matchContentSize: Boolean,
    reducedMotion: Boolean
): ElasticIndicatorBounds {
    if (tabPositions.isEmpty()) return ElasticIndicatorBounds(0.dp, 0.dp)

    val lastIndex = tabPositions.lastIndex
    val rawPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, lastIndex.toFloat())
    val startIndex = rawPosition.toInt().coerceIn(0, lastIndex)
    val endIndex = (startIndex + 1).coerceAtMost(lastIndex)
    val startBounds = tabPositions[startIndex].indicatorBounds(matchContentSize)
    if (startIndex == endIndex) return startBounds

    val endBounds = tabPositions[endIndex].indicatorBounds(matchContentSize)
    val progress = (rawPosition - startIndex).coerceIn(0f, 1f)
    val leading = if (reducedMotion) progress else easeOutSine(progress)
    val trailing = if (reducedMotion) progress else easeInSine(progress)
    return elasticEdgeBounds(
        start = startBounds,
        end = endBounds,
        leadingFraction = leading,
        trailingFraction = trailing
    )
}

/**
 * Shared edge model: the leading edge and the trailing edge advance from the
 * start to the end bounds at different fractions.
 */
private fun elasticEdgeBounds(
    start: ElasticIndicatorBounds,
    end: ElasticIndicatorBounds,
    leadingFraction: Float,
    trailingFraction: Float
): ElasticIndicatorBounds = ElasticIndicatorBounds(
    left = lerp(start.left, end.left, trailingFraction),
    right = lerp(start.right, end.right, leadingFraction)
)

/** MDC's accelerating curve (ease-in sine): slow start, fast finish. */
private fun easeInSine(fraction: Float): Float =
    (1.0 - cos(fraction * PI / 2.0)).toFloat()

/** MDC's decelerating curve (ease-out sine): fast start, slow finish. */
private fun easeOutSine(fraction: Float): Float =
    sin(fraction * PI / 2.0).toFloat()

private fun TabPosition.indicatorBounds(matchContentSize: Boolean): ElasticIndicatorBounds {
    val inset = if (matchContentSize) {
        ((width - contentWidth) * 0.5f).coerceAtLeast(0.dp)
    } else {
        0.dp
    }
    return ElasticIndicatorBounds(
        left = left + inset,
        right = left + width - inset
    )
}

private fun MeasureScope.layoutElasticIndicator(
    measurable: Measurable,
    constraints: Constraints,
    bounds: ElasticIndicatorBounds
): MeasureResult {
    val leftPx = bounds.left.roundToPx()
    val widthPx = (bounds.right - bounds.left).roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(
        Constraints(
            minWidth = widthPx,
            maxWidth = widthPx,
            minHeight = 0,
            maxHeight = constraints.maxHeight
        )
    )
    return layout(constraints.maxWidth, placeable.height) {
        placeable.place(leftPx, 0)
    }
}
