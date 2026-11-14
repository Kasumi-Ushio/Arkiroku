package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.lerp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled

/**
 * Glass container for top bars and headers. Draws a progressive blur (full near
 * the status bar, easing towards [progressiveEndIntensity] at the bottom edge)
 * behind whatever header content is placed inside. The content itself must use
 * transparent backgrounds. Raise [progressiveEndIntensity] above 0f when tall
 * header content (like an expanded search field) should stay fully covered by
 * the blur instead of sitting on the faded-out edge.
 */
@Composable
fun GlassTopBar(
    hazeState: HazeState,
    style: HazeBlurStyle,
    modifier: Modifier = Modifier,
    progressiveEndIntensity: Float = 0f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = style.withTopBarProgressive(progressiveEndIntensity),
                performanceMode = HazePerformanceMode.Performance
            )
    ) {
        content()
    }
}

/**
 * Immutable header configuration a tab hands to the home scaffold. Tabs supply
 * title, tip and action callbacks only; they never receive a ViewModel. Set
 * [compact] when the tab content is scrolled away from the top so the title
 * shrinks, matching the collapsible tab headers.
 */
data class HomeHeaderSpec(
    val title: String,
    val tip: String = "",
    val compact: Boolean = false,
    val actions: @Composable RowScope.() -> Unit = {}
)

/**
 * Animated title style for collapsible headers: lerps font and line height
 * between titleLarge and titleMedium as [compact] flips, so the title visibly
 * shrinks instead of popping. Snaps to the final style with reduced motion.
 */
@Composable
fun rememberCollapsingTitleStyle(compact: Boolean): TextStyle {
    val expandedStyle = MaterialTheme.typography.titleLarge
    val compactStyle = MaterialTheme.typography.titleMedium
    val reducedMotion = rememberReducedMotionEnabled()
    val fraction by animateFloatAsState(
        targetValue = if (compact) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "collapsingTitleStyle"
    )
    return remember(expandedStyle, compactStyle, fraction) {
        expandedStyle.copy(
            fontSize = lerp(expandedStyle.fontSize, compactStyle.fontSize, fraction),
            lineHeight = lerp(expandedStyle.lineHeight, compactStyle.lineHeight, fraction)
        )
    }
}

/** Default home top bar: title plus scrolling tip line and optional actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeGlassTopBar(spec: HomeHeaderSpec) {
    TopAppBar(
        title = {
            Column {
                Text(spec.title, style = rememberCollapsingTitleStyle(spec.compact))
                if (spec.tip.isNotBlank()) {
                    Text(
                        text = spec.tip,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .basicMarquee()
                    )
                }
            }
        },
        actions = spec.actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
