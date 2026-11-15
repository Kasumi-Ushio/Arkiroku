package org.kasumi321.ushio.phitracker.ui.utils

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MotionScheme

/**
 * Short directional transition for content that belongs to the same page.
 * Spatial, effect and size motion all follow the active scheme; reduced motion
 * therefore leaves no size-only animation behind when enter/exit are disabled.
 */
fun <T> AnimatedContentTransitionScope<T>.horizontalContentTransition(
    reducedMotion: Boolean,
    motionScheme: MotionScheme,
    forward: Boolean
): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        slideInHorizontally(
            animationSpec = if (reducedMotion) snap() else motionScheme.defaultSpatialSpec(),
            initialOffsetX = { fullWidth -> direction * fullWidth / 10 }
        ) + fadeIn(
            animationSpec = if (reducedMotion) snap() else motionScheme.defaultEffectsSpec()
        ) togetherWith slideOutHorizontally(
            animationSpec = if (reducedMotion) snap() else motionScheme.defaultSpatialSpec(),
            targetOffsetX = { fullWidth -> -direction * fullWidth / 10 }
        ) + fadeOut(
            animationSpec = if (reducedMotion) snap() else motionScheme.defaultEffectsSpec()
        )
    ).using(
        SizeTransform(clip = true) { _, _ ->
            if (reducedMotion) snap() else motionScheme.defaultSpatialSpec()
        }
    )
}

/**
 * Content transition for expand/collapse sections whose expanded content is a
 * superset of the collapsed one (changelog text, alias chips). Expansion swaps
 * instantly and lets the growing clipped bounds reveal the extra content
 * progressively; collapse crossfades the full content into the truncated copy
 * so the content outside the collapsed bounds disappears gradually instead of
 * vanishing at once.
 */
fun AnimatedContentTransitionScope<Boolean>.expandCollapseTransition(
    reducedMotion: Boolean,
    motionScheme: MotionScheme
) = (
    if (targetState) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        fadeIn(
            animationSpec = if (reducedMotion) snap() else motionScheme.defaultEffectsSpec()
        ) togetherWith fadeOut(
            animationSpec = if (reducedMotion) snap() else motionScheme.defaultEffectsSpec()
        )
    }
    ).using(
        SizeTransform(clip = true) { _, _ ->
            if (reducedMotion) snap() else motionScheme.defaultSpatialSpec()
        }
    )
