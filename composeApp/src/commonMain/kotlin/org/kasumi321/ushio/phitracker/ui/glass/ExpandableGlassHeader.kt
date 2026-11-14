package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import org.kasumi321.ushio.phitracker.ui.components.AppExpandableSection
import org.kasumi321.ushio.phitracker.ui.components.rememberAppExpansionArrowRotation

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
    AppExpandableSection(expanded = expanded, modifier = modifier, content = content)
}

/** Rotation progress (0f..180f) for a collapse arrow driven by the same intent. */
@Composable
fun rememberExpansionArrowRotation(expanded: Boolean): State<Float> =
    rememberAppExpansionArrowRotation(expanded)
