package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.ui.DetailPaneState
import org.kasumi321.ushio.phitracker.ui.DetailPlaceholderRoute
import org.kasumi321.ushio.phitracker.ui.DetailRouteRequest
import org.kasumi321.ushio.phitracker.ui.Screen
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationCoordinator
import org.kasumi321.ushio.phitracker.ui.navigateDetailRequest
import org.kasumi321.ushio.phitracker.ui.navigation.CollectibleDetailRoute
import org.kasumi321.ushio.phitracker.ui.navigation.SongDetailRoute
import org.kasumi321.ushio.phitracker.ui.secondaryDestinations
import org.kasumi321.ushio.phitracker.ui.toDetailRouteRequest
import org.kasumi321.ushio.phitracker.ui.utils.rememberSeparatingVerticalHingeBounds
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.detail_pane_placeholder

/** Narrowest the list pane may be dragged to. */
private val MinListPaneWidth = 320.dp

/** Width the detail pane always keeps, capping how wide the list pane grows. */
private val MinDetailPaneWidth = 400.dp

/** Touch target width of the draggable divider between the two panes. */
private val DividerHandleWidth = 16.dp

/** Default list pane width: a phone-like 9:16 (width:height) portrait pane. */
internal fun defaultListPaneWidth(height: Dp): Dp = height * 9 / 16

/**
 * Settled list pane width before clamping: a user's drag wins, then the
 * hinge's left edge on a book-mode foldable, then the 9:16 default.
 */
internal fun preferredListPaneWidth(userWidthDp: Float, hingeLeft: Dp?, height: Dp): Dp = when {
    !userWidthDp.isNaN() -> userWidthDp.dp
    hingeLeft != null -> hingeLeft
    else -> defaultListPaneWidth(height)
}

/** Keeps the list pane inside [MinListPaneWidth, totalWidth - [MinDetailPaneWidth]]. */
internal fun clampListPaneWidth(width: Dp, totalWidth: Dp): Dp =
    width.coerceIn(MinListPaneWidth, (totalWidth - MinDetailPaneWidth).coerceAtLeast(MinListPaneWidth))

/**
 * Animated list pane width: full incoming width at progress 0 (detail pane
 * hidden), [listPaneWidthPx] at progress 1. The child is measured at whichever
 * settled width the animation is currently closer to, so no reflow happens
 * while the pane is at rest: the single mid-flight remeasure (e.g. the grid
 * column change) is masked by the motion, and plain animation frames only move
 * the reported width (the caller clips) without re-measuring the tab content.
 */
private fun Modifier.listPaneWidth(progress: State<Float>, listPaneWidthPx: Int) = layout { measurable, constraints ->
    val fullWidth = constraints.maxWidth
    val p = progress.value.coerceIn(0f, 1f)
    val w = fullWidth + ((listPaneWidthPx - fullWidth) * p).roundToInt()
    val measureWidth = if (p < 0.5f) fullWidth else listPaneWidthPx
    val placeable = measurable.measure(constraints.copy(minWidth = measureWidth, maxWidth = measureWidth))
    layout(w, placeable.height) { placeable.place(0, 0) }
}

/**
 * Animated detail pane width: 0 when hidden, [detailWidthPx] when open. The
 * destination is always measured at the settled [detailWidthPx] so it never
 * re-lays out during the animation.
 */
private fun Modifier.detailPaneWidth(progress: State<Float>, detailWidthPx: Int) = layout { measurable, constraints ->
    val p = progress.value.coerceIn(0f, 1f)
    val w = (detailWidthPx * p).roundToInt()
    val placeable = measurable.measure(constraints.copy(minWidth = detailWidthPx, maxWidth = detailWidthPx))
    layout(w, placeable.height) { placeable.place(0, 0) }
}

/** Animated width for the drag handle and the hinge gap: 0 when the detail pane is hidden. */
private fun Modifier.animatedWidth(progress: State<Float>, widthPx: Int) = layout { measurable, constraints ->
    val w = (widthPx * progress.value.coerceIn(0f, 1f)).roundToInt()
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(w, placeable.height) { placeable.place(0, 0) }
}

/**
 * Expanded-width home layout: the rail and the four home tabs stay pinned in
 * a list pane on the left, while secondary destinations (settings, song
 * details, B30 image, …) open in the detail pane on the right. The detail
 * pane only takes space while a real destination is on it — at the
 * placeholder route the list pane expands to the full window width. The list
 * pane defaults to a 9:16 (width:height) portrait width and can be resized by
 * dragging the divider between the panes (double-tap the divider to restore
 * the default). The detail pane is a nested NavHost whose controller is
 * published through [detailPaneState] so the shared navigation gateways can
 * route to it.
 *
 * Full-screen destinations (onboarding, login, illustration preview) never
 * enter this layout; they stay on the outer graph above Home.
 *
 * Foldables: on a book-mode device the split aligns to the vertical hinge and
 * the hinge's physical strip is left empty. A fold/unfold display switch
 * recreates the Activity, so the detail route persists through
 * [detailPaneState]'s saver and is restored into the fresh pane (or reopened
 * full-screen when the new posture is below Expanded).
 */
@Composable
internal fun ExpandedHomeScreen(
    homeViewModel: HomeViewModel,
    detailPaneState: DetailPaneState,
    b30Navigation: B30NavigationCoordinator,
    settingsRepository: SettingsRepository,
    reducedMotionEnabled: Boolean,
    outerNavController: NavHostController,
    tabState: HomeTabState,
    indexSubTabState: IndexSubTabState,
    collectiblesChapterState: CollectiblesChapterState,
    onLogout: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val detailNavController = rememberNavController()

    DisposableEffect(detailNavController) {
        detailPaneState.controller = detailNavController
        onDispose { detailPaneState.controller = null }
    }

    // Top-level detail destinations replace each other above the placeholder,
    // keeping the back stack shallow; nested flows (e.g. About -> Licenses)
    // navigate plainly inside the detail graph and stack normally.
    fun navigateDetail(request: DetailRouteRequest) {
        detailNavController.navigateDetailRequest(request) {
            popUpTo(DetailPlaceholderRoute) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun navigateSongDetail(songId: String, difficulty: org.kasumi321.ushio.phitracker.domain.model.Difficulty?) {
        detailNavController.navigate(SongDetailRoute.from(songId = songId, difficulty = difficulty)) {
            popUpTo(DetailPlaceholderRoute) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun navigateCollectibleDetail(folderKey: String, key: String) {
        detailNavController.navigate(CollectibleDetailRoute(folderKey = folderKey, key = key)) {
            popUpTo(DetailPlaceholderRoute) { inclusive = false }
            launchSingleTop = true
        }
    }

    // Restore the detail destination that survived an Activity recreation (a
    // foldable's display switch recreates the Activity). This must run before
    // the route mirror below, whose first placeholder report would otherwise
    // erase the restored route.
    LaunchedEffect(detailNavController) {
        detailPaneState.consumePendingRoute()?.let { navigateDetail(it) }
    }

    // Mirror the detail pane's current destination so the compact layout can
    // reopen it full-screen when the window shrinks below Expanded.
    val detailEntry by detailNavController.currentBackStackEntryAsState()
    LaunchedEffect(detailEntry) {
        detailPaneState.setPendingRoute(detailEntry?.toDetailRouteRequest())
    }

    // The collectible item currently shown in the detail pane (if any), used
    // to highlight its card in the left-pane chapter list.
    val selectedCollectibleItem = remember(detailEntry) {
        val entry = detailEntry ?: return@remember null
        if (!entry.destination.hasRoute<CollectibleDetailRoute>()) return@remember null
        entry.toRoute<CollectibleDetailRoute>().let { it.folderKey to it.key }
    }

    val density = LocalDensity.current

    // NaN = the user has not dragged the divider; the pane then follows the
    // 9:16 default derived from the window height. A dragged value survives
    // configuration changes (rememberSaveable) and is clamped on every layout.
    var userWidthDp by rememberSaveable { mutableFloatStateOf(Float.NaN) }

    // The detail pane only takes space while a real secondary destination is
    // on it; at the placeholder route the list pane expands to the full window
    // width instead of showing an empty pane. The animation is clip-driven:
    // the detail pane always measures at its settled width, and the list pane
    // reflows once mid-flight (see listPaneWidth/detailPaneWidth), so rapid
    // open/close toggles never storm the pane contents with per-frame
    // re-measure. The no-bouncy spring keeps velocity continuous when the
    // animation is retargeted mid-flight.
    val detailRoute = detailEntry?.destination?.route
    val detailVisible = detailRoute != null && detailRoute != DetailPlaceholderRoute
    val paneProgress = animateFloatAsState(
        targetValue = if (detailVisible) 1f else 0f,
        animationSpec = if (reducedMotionEnabled) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "detailPaneProgress"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val totalWidth = maxWidth
        // Book-mode foldable: split along the hinge and keep the drag handle
        // clear of it. A user's drag still wins over the hinge-aligned default.
        val hingeBounds = rememberSeparatingVerticalHingeBounds()
        val hingeLeft = hingeBounds?.let { with(density) { it.left.toDp() } }
        val hingeGapPx = hingeBounds?.let { (it.right - it.left).roundToInt() } ?: 0
        val listPaneWidth = clampListPaneWidth(
            width = preferredListPaneWidth(userWidthDp, hingeLeft, maxHeight),
            totalWidth = totalWidth
        )
        val fullWidthPx = constraints.maxWidth
        val listPaneWidthPx = with(density) { listPaneWidth.roundToPx() }
        val handleWidthPx = with(density) { DividerHandleWidth.roundToPx() }
        val detailWidthPx = (fullWidthPx - listPaneWidthPx - handleWidthPx - hingeGapPx).coerceAtLeast(0)
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .listPaneWidth(paneProgress, listPaneWidthPx)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                MainScreen(
                    onLogout = onLogout,
                    onNavigateToB30Image = b30Navigation::openB30,
                    onNavigateToSongDetail = { songId -> navigateSongDetail(songId, null) },
                    onNavigateToSongDetailWithDifficulty = { songId, difficulty ->
                        navigateSongDetail(songId, difficulty)
                    },
                    onNavigateToCollectibleDetail = { folderKey, key ->
                        navigateCollectibleDetail(folderKey, key)
                    },
                    onNavigateToAbout = { navigateDetail(DetailRouteRequest.Plain(Screen.About.route)) },
                    onNavigateToSettings = { navigateDetail(DetailRouteRequest.Plain(Screen.Settings.route)) },
                    onNavigateToSuggest = { navigateDetail(DetailRouteRequest.Plain(Screen.Suggest.route)) },
                    onNavigateToLogin = onNavigateToLogin,
                    tabState = tabState,
                    indexSubTabState = indexSubTabState,
                    isInExpandedPane = true,
                    selectedCollectibleItem = selectedCollectibleItem,
                    collectiblesChapterState = collectiblesChapterState,
                    viewModel = homeViewModel
                )
            }
            // The strip the physical hinge occupies stays empty so neither the
            // drag handle nor any pane content sits under it.
            if (hingeGapPx > 0) {
                Spacer(
                    modifier = Modifier
                        .animatedWidth(paneProgress, hingeGapPx)
                        .fillMaxHeight()
                )
            }
            // Drag handle: horizontal drag resizes the list pane, double-tap
            // restores the default (hinge-aligned on a book-mode foldable, 9:16
            // elsewhere). The centered pill is the visual affordance that the
            // divider can be dragged.
            Box(
                modifier = Modifier
                    .animatedWidth(paneProgress, handleWidthPx)
                    .fillMaxHeight()
                    .clipToBounds()
                    .draggable(
                        state = rememberDraggableState { delta ->
                            val base = if (userWidthDp.isNaN()) listPaneWidth else userWidthDp.dp
                            userWidthDp = clampListPaneWidth(
                                width = base + with(density) { delta.toDp() },
                                totalWidth = totalWidth
                            ).value
                        },
                        orientation = Orientation.Horizontal
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { userWidthDp = Float.NaN })
                    },
                contentAlignment = Alignment.Center
            ) {
                VerticalDivider(modifier = Modifier.statusBarsPadding())
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            NavHost(
                navController = detailNavController,
                startDestination = DetailPlaceholderRoute,
                modifier = Modifier
                    .detailPaneWidth(paneProgress, detailWidthPx)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                composable(DetailPlaceholderRoute) {
                    DetailPanePlaceholder()
                }
                secondaryDestinations(
                    host = detailNavController,
                    outer = outerNavController,
                    b30Navigation = b30Navigation,
                    reducedMotionEnabled = reducedMotionEnabled,
                    settingsRepository = settingsRepository,
                    detailPaneState = detailPaneState,
                    migrateOnExpanded = false
                )
            }
        }
    }
}

@Composable
private fun DetailPanePlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(96.dp)
        )
        Text(
            text = stringResource(Res.string.detail_pane_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
