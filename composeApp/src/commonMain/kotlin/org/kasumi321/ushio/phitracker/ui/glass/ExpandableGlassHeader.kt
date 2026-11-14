package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled

/**
 * Collapsible section of a glass header. Animates height and alpha with a spring
 * and clips the content to the section bounds while animating, so expanding text
 * never overlaps the list below. With reduced motion enabled it jumps straight
 * to the final state while the blur itself stays in place.
 */
@Composable
fun ExpandableGlassSection(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val sizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val floatSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            expandVertically(animationSpec = sizeSpec) + fadeIn(animationSpec = floatSpec)
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            shrinkVertically(animationSpec = sizeSpec) + fadeOut(animationSpec = floatSpec)
        }
    ) {
        content()
    }
}

/** Rotation progress (0f..180f) for a collapse arrow driven by the same intent. */
@Composable
fun rememberExpansionArrowRotation(expanded: Boolean): State<Float> {
    val reducedMotion = rememberReducedMotionEnabled()
    return animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "expansionArrowRotation"
    )
}
