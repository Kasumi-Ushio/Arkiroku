package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Where the home screen places its primary navigation, by window width. */
enum class HomeNavMode {
    /** Bottom navigation bar (Compact width). */
    BottomBar,

    /** Opaque rail at the start edge (Medium width and the Expanded list pane). */
    Rail
}

/**
 * Shared glass frame for the home screen. Owns the single HazeState used by the
 * whole home page: tab content draws full-bleed as the haze source, the top bar
 * and the navigation component float above it with glass effects. The reported
 * [PaddingValues] describe the bar/rail extents; tab content must use them as
 * scroll padding so the first and last items are never covered.
 *
 * In [HomeNavMode.Rail] the [navigationBar] slot is placed as an opaque
 * official-styled rail at the start edge instead of the bottom; the content
 * is physically shifted right of the measured rail width (tab contents only
 * consume the top/bottom of the reported [PaddingValues], so the rail width
 * cannot travel through that contract), and the top bar is shifted right of
 * the rail so the two never overlap. The rail itself is not a glass layer:
 * nothing scrolls beneath it, so blur would have nothing to sample. The
 * full-bleed haze source is kept for the glass top bar.
 */
@Composable
fun HomeGlassScaffold(
    snackbarHostState: SnackbarHostState,
    navMode: HomeNavMode = HomeNavMode.BottomBar,
    topBar: @Composable (HazeState, HazeBlurStyle) -> Unit,
    navigationBar: @Composable (HazeState, HazeBlurStyle) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val hazeState = rememberHazeState()
    val glassStyle = rememberGlassHazeStyle()

    if (navMode == HomeNavMode.BottomBar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = { topBar(hazeState, glassStyle) },
            bottomBar = { navigationBar(hazeState, glassStyle) }
        ) { innerPadding ->
            // Scaffold lays the bars out as overlays; content stays full-bleed so it
            // can scroll behind them, and uses innerPadding only as scroll padding.
            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
                content(innerPadding)
            }
        }
        return
    }

    // Rail: the rail sits at the start edge. The haze source stays
    // full-bleed so the top bar has something to blur, while the content
    // itself is physically shifted right of the rail: the tab contents only
    // ever consume the top/bottom of the reported PaddingValues (their
    // horizontal padding is hardcoded), so the rail width cannot travel
    // through the PaddingValues contract and must be applied as layout
    // padding here. With no bottom bar in this mode the scaffold's
    // contentWindowInsets already contributes the system navigation bar
    // inset to innerPadding.
    var railWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val railWidth = with(density) { railWidthPx.toDp() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(modifier = Modifier.padding(start = railWidth)) {
                topBar(hazeState, glassStyle)
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = railWidth)
            ) {
                content(
                    PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding()
                    )
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .onSizeChanged { railWidthPx = it.width }
            ) {
                navigationBar(hazeState, glassStyle)
            }
        }
    }
}
