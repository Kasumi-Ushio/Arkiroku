package org.kasumi321.ushio.phitracker.ui.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.pager.PagerState
import kotlin.math.abs

/**
 * Animate to [targetPage] without animateScrollToPage's long-distance pre-jump.
 * [position] is shared across requests so a new target continues from the
 * current value and velocity. The pager must not use page spacing.
 */
internal suspend fun PagerState.animateContinuouslyToPage(
    targetPage: Int,
    animationSpec: FiniteAnimationSpec<Float>,
    position: Animatable<Float, AnimationVector1D>
) {
    if (pageCount == 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)

    scroll {
        updateTargetPage(target)
        val actualPosition = currentPage + currentPageOffsetFraction
        if (abs(position.value - actualPosition) > 0.001f) {
            position.snapTo(actualPosition)
        }

        var previousPosition = position.value
        position.animateTo(target.toFloat(), animationSpec) {
            val deltaPages = value - previousPosition
            val pageSize = layoutInfo.pageSize
            if (pageSize > 0 && deltaPages != 0f) {
                val consumedPages = scrollBy(deltaPages * pageSize) / pageSize
                previousPosition += consumedPages
            }
        }
    }

    // Remove any floating-point residue left by pixel-based consumption.
    if (currentPage != target || currentPageOffsetFraction != 0f) {
        scrollToPage(target)
    }
}
