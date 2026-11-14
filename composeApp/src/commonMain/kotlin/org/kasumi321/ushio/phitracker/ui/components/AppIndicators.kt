@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.kasumi321.ushio.phitracker.ui.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled

/** Expressive loading for short, indeterminate waits with room for a morphing shape. */
@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier) {
    if (rememberReducedMotionEnabled()) {
        CircularProgressIndicator(modifier = modifier)
    } else {
        LoadingIndicator(modifier = modifier)
    }
}

/** Determinate long-running progress; indeterminate work keeps the standard indicator instead. */
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
