@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.kasumi321.ushio.phitracker.ui.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled

/** Expressive loading for short, indeterminate waits with room for a morphing shape. */
@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LoadingIndicatorDefaults.indicatorColor
) {
    if (rememberReducedMotionEnabled()) {
        CircularProgressIndicator(modifier = modifier, color = color)
    } else {
        LoadingIndicator(modifier = modifier, color = color)
    }
}

/** Compact indeterminate progress for buttons, inline statuses and image placeholders. */
@Composable
fun AppCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth
) {
    if (rememberReducedMotionEnabled()) {
        CircularProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeWidth = strokeWidth
        )
    } else {
        val stroke = with(LocalDensity.current) {
            Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        }
        CircularWavyProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            stroke = stroke,
            trackStroke = stroke
        )
    }
}

/** Determinate long-running progress. */
@Composable
fun AppLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    if (rememberReducedMotionEnabled()) {
        LinearProgressIndicator(progress = progress, modifier = modifier)
    } else {
        LinearWavyProgressIndicator(progress = progress, modifier = modifier)
    }
}

/** Indeterminate long-running progress with the same expressive wave language. */
@Composable
fun AppLinearProgressIndicator(modifier: Modifier = Modifier) {
    if (rememberReducedMotionEnabled()) {
        LinearProgressIndicator(modifier = modifier)
    } else {
        LinearWavyProgressIndicator(modifier = modifier)
    }
}

@Composable
fun AppPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    if (rememberReducedMotionEnabled()) {
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = modifier
        )
    } else {
        PullToRefreshDefaults.LoadingIndicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = modifier
        )
    }
}
