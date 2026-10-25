package org.kasumi321.ushio.phitracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.triggerAppRestart
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.ui.b30.B30ImageScreen
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationCoordinator
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationGateway
import org.kasumi321.ushio.phitracker.ui.collectible.CollectibleDetailScreen
import org.kasumi321.ushio.phitracker.ui.collectible.CollectibleDetailViewModel
import org.kasumi321.ushio.phitracker.ui.home.ExpandedHomeScreen
import org.kasumi321.ushio.phitracker.ui.home.CollectiblesChapterState
import org.kasumi321.ushio.phitracker.ui.home.HomeTabState
import org.kasumi321.ushio.phitracker.ui.home.HomeViewModel
import org.kasumi321.ushio.phitracker.ui.home.IndexSubTabState
import org.kasumi321.ushio.phitracker.ui.home.MainScreen
import org.kasumi321.ushio.phitracker.ui.login.LoginScreen
import org.kasumi321.ushio.phitracker.ui.login.LoginViewModel
import org.kasumi321.ushio.phitracker.ui.navigation.CollectibleDetailRoute
import org.kasumi321.ushio.phitracker.ui.navigation.IllustrationPreviewRoute
import org.kasumi321.ushio.phitracker.ui.navigation.SongDetailRoute
import org.kasumi321.ushio.phitracker.ui.onboarding.OnboardingScreen
import org.kasumi321.ushio.phitracker.ui.onboarding.OnboardingViewModel
import org.kasumi321.ushio.phitracker.ui.settings.AboutScreen
import org.kasumi321.ushio.phitracker.ui.settings.AcknowledgmentsScreen
import org.kasumi321.ushio.phitracker.ui.settings.DisclaimerScreen
import org.kasumi321.ushio.phitracker.ui.settings.LicensesScreen
import org.kasumi321.ushio.phitracker.ui.settings.PrivacyPolicyScreen
import org.kasumi321.ushio.phitracker.ui.settings.SettingsScreen
import org.kasumi321.ushio.phitracker.ui.settings.SettingsViewModel
import org.kasumi321.ushio.phitracker.ui.settings.requestOnboardingRerun
import org.kasumi321.ushio.phitracker.ui.song.IllustrationPreviewScreen
import org.kasumi321.ushio.phitracker.ui.song.SongDetailScreen
import org.kasumi321.ushio.phitracker.ui.song.SongDetailViewModel
import org.kasumi321.ushio.phitracker.ui.suggest.SuggestScreen
import org.kasumi321.ushio.phitracker.ui.suggest.SuggestViewModel
import org.kasumi321.ushio.phitracker.ui.utils.isExpanded
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled
import org.kasumi321.ushio.phitracker.ui.utils.rememberWindowSizeClass
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.jetbrains.compose.resources.stringResource
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.action_back
import phitracker.composeapp.generated.resources.collectible_detail_key_label
import phitracker.composeapp.generated.resources.collectible_detail_title
import phitracker.composeapp.generated.resources.collectible_not_found
import phitracker.composeapp.generated.resources.nav_song_detail_title
import phitracker.composeapp.generated.resources.nav_song_id
import phitracker.composeapp.generated.resources.nav_song_not_found
import phitracker.composeapp.generated.resources.settings_restart_required_message
import phitracker.composeapp.generated.resources.settings_restart_required_title

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object B30Image : Screen("b30image")
    data object About : Screen("about")
    data object Disclaimer : Screen("disclaimer")
    data object Acknowledgments : Screen("acknowledgments")
    data object Licenses : Screen("licenses")
    data object PrivacyPolicy : Screen("privacy_policy")
    data object Settings : Screen("settings")
    data object Suggest : Screen("suggest")
}

/** Start destination of the detail-pane NavHost in the expanded two-pane layout. */
internal const val DetailPlaceholderRoute = "detail_placeholder"

/**
 * A secondary destination captured for a hand-off between the compact and
 * expanded layouts, or for restoration after an Activity recreation. Typed
 * routes (song detail) keep their decoded arguments; destinations carrying
 * in-memory payloads (the B30 image) cannot be captured and fall back to
 * Home instead.
 */
@Serializable
internal sealed interface DetailRouteRequest {
    @Serializable
    data class Plain(val route: String) : DetailRouteRequest

    @Serializable
    data class SongDetail(val songId: String, val difficultyName: String?) : DetailRouteRequest

    @Serializable
    data class Collectible(val folderKey: String, val key: String) : DetailRouteRequest
}

/**
 * Bridge between the outer NavHost and the detail-pane NavHost of the expanded
 * two-pane layout. [controller] is set while the detail pane is composed and
 * null in the compact single-pane layout, so navigation gateways can route to
 * whichever host currently owns secondary destinations. [setPendingRoute]
 * continuously mirrors the detail pane's current destination; when the window
 * shrinks below Expanded the compact layout consumes it to reopen the same
 * page full-screen, and when it grows back to Expanded the full-screen page
 * hands itself back through the same slot so the two-pane layout can reopen
 * it in the detail pane.
 *
 * The pending route also survives Activity recreation (a foldable's display
 * switch recreates the Activity even with the full `configChanges` set): the
 * [Saver] persists it as JSON, and whichever layout mounts first restores it.
 */
internal class DetailPaneState(restoredRoute: DetailRouteRequest? = null) {
    var controller: NavHostController? = null
    private var pendingRoute: DetailRouteRequest? = restoredRoute

    fun setPendingRoute(request: DetailRouteRequest?) {
        pendingRoute = request
    }

    fun consumePendingRoute(): DetailRouteRequest? {
        val route = pendingRoute
        pendingRoute = null
        return route
    }

    /**
     * Delivery used when the detail graph becomes usable: a route queued while
     * the graph was not ready is newer than [restored] (the route captured at
     * composition time), so it wins; otherwise the restored route is used.
     */
    fun consumePendingRouteOr(restored: DetailRouteRequest?): DetailRouteRequest? =
        consumePendingRoute() ?: restored

    companion object {
        private val saverJson = Json { ignoreUnknownKeys = true }

        val Saver: Saver<DetailPaneState, Any> = Saver(
            save = { state ->
                state.pendingRoute?.let { saverJson.encodeToString(it) }
            },
            restore = { saved ->
                DetailPaneState(
                    (saved as? String)?.let {
                        runCatching { saverJson.decodeFromString<DetailRouteRequest>(it) }.getOrNull()
                    }
                )
            }
        )
    }
}

/**
 * Capture [entry]'s destination for a cross-layout hand-off, or null when the
 * destination cannot migrate. Plain string routes migrate as-is; the typed
 * song detail keeps its decoded arguments so it can be re-navigated; the B30
 * image needs its in-memory payload and falls back to Home.
 */
internal fun NavBackStackEntry.toDetailRouteRequest(): DetailRouteRequest? = when {
    destination.hasRoute<SongDetailRoute>() -> {
        val route = toRoute<SongDetailRoute>()
        DetailRouteRequest.SongDetail(route.songId, route.difficultyName)
    }

    destination.hasRoute<CollectibleDetailRoute>() -> {
        val route = toRoute<CollectibleDetailRoute>()
        DetailRouteRequest.Collectible(route.folderKey, route.key)
    }

    else -> migratableDetailRoute(destination.route)?.let(DetailRouteRequest::Plain)
}

/**
 * Only plain string routes migrate by route string. The typed song detail is
 * captured through [toDetailRouteRequest] instead; the B30 image needs its
 * in-memory payload and falls back to Home.
 */
internal fun migratableDetailRoute(route: String?): String? = when (route) {
    Screen.About.route,
    Screen.Disclaimer.route,
    Screen.Acknowledgments.route,
    Screen.Licenses.route,
    Screen.PrivacyPolicy.route,
    Screen.Settings.route,
    Screen.Suggest.route -> route

    else -> null
}

/** Navigate to a captured secondary destination, restoring typed arguments. */
internal fun NavHostController.navigateDetailRequest(
    request: DetailRouteRequest,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    when (request) {
        is DetailRouteRequest.Plain -> navigate(request.route, builder)
        is DetailRouteRequest.SongDetail -> navigate(
            SongDetailRoute(songId = request.songId, difficultyName = request.difficultyName),
            builder
        )
        is DetailRouteRequest.Collectible -> navigate(
            CollectibleDetailRoute(folderKey = request.folderKey, key = request.key),
            builder
        )
    }
}

/** The route the cold start must open, plus the one-shot flag side effects. */
internal data class StartGateDecision(
    val route: String,
    val consumeRerunRequest: Boolean,
    val markOnboardingCompleted: Boolean
)

/**
 * Pure start-gate decision, kept out of the composable so the precedence is
 * unit-testable: an explicit rerun beats everything (token holders included);
 * otherwise only brand-new installs (no token, no preload record, wizard
 * never completed) see the wizard, and upgrading users are silently marked
 * onboarded exactly once.
 */
internal fun decideStartGate(
    onboardingCompleted: Boolean,
    rerunRequested: Boolean,
    preloadDone: Boolean,
    hasToken: Boolean
): StartGateDecision = when {
    rerunRequested || (!onboardingCompleted && !hasToken && !preloadDone) ->
        StartGateDecision(
            route = Screen.Onboarding.route,
            consumeRerunRequest = rerunRequested,
            markOnboardingCompleted = false
        )

    else ->
        StartGateDecision(
            route = if (hasToken) Screen.Login.route else Screen.Home.route,
            consumeRerunRequest = false,
            markOnboardingCompleted = !onboardingCompleted
        )
}

private const val NavTransitionDurationMillis = 250

private fun forwardEnterTransition(reducedMotionEnabled: Boolean): EnterTransition =
    if (reducedMotionEnabled) {
        EnterTransition.None
    } else {
        slideInHorizontally(
            animationSpec = tween(durationMillis = NavTransitionDurationMillis),
            initialOffsetX = { fullWidth -> fullWidth / 10 }
        ) + fadeIn(animationSpec = tween(durationMillis = NavTransitionDurationMillis))
    }

private fun forwardExitTransition(reducedMotionEnabled: Boolean): ExitTransition =
    if (reducedMotionEnabled) {
        ExitTransition.None
    } else {
        slideOutHorizontally(
            animationSpec = tween(durationMillis = NavTransitionDurationMillis),
            targetOffsetX = { fullWidth -> -fullWidth / 10 }
        ) + fadeOut(animationSpec = tween(durationMillis = NavTransitionDurationMillis))
    }

private fun popEnterTransition(reducedMotionEnabled: Boolean): EnterTransition =
    if (reducedMotionEnabled) {
        EnterTransition.None
    } else {
        slideInHorizontally(
            animationSpec = tween(durationMillis = NavTransitionDurationMillis),
            initialOffsetX = { fullWidth -> -fullWidth / 10 }
        ) + fadeIn(animationSpec = tween(durationMillis = NavTransitionDurationMillis))
    }

private fun popExitTransition(reducedMotionEnabled: Boolean): ExitTransition =
    if (reducedMotionEnabled) {
        ExitTransition.None
    } else {
        slideOutHorizontally(
            animationSpec = tween(durationMillis = NavTransitionDurationMillis),
            targetOffsetX = { fullWidth -> fullWidth / 10 }
        ) + fadeOut(animationSpec = tween(durationMillis = NavTransitionDurationMillis))
    }

@Composable
fun PhiTrackerNavHost() {
    AppLogger.event("startup", "NavHost.enter")
    val navController = rememberNavController()
    val settingsRepository: SettingsRepository = koinInject()
    val repository: PhigrosRepository = koinInject()

    // Decide the start destination once. A rerun requested from Settings wins
    // outright — token holders included — so the wizard can guide them again
    // (its inline login restores the session from the cached token, and the
    // wizard always hands off to Home afterwards). Fresh installs see the
    // wizard; upgrading users who hold a token or preload record but never
    // ran it are silently marked onboarded, as before.
    val startRoute by produceState<String?>(initialValue = null) {
        val decision = decideStartGate(
                onboardingCompleted = settingsRepository.onboardingCompleted.first(),
                rerunRequested = settingsRepository.onboardingRerunRequested.first(),
                preloadDone = settingsRepository.getPreloadDone(),
                hasToken = repository.getSessionToken() != null
        )
        if (decision.consumeRerunRequest) settingsRepository.setOnboardingRerunRequested(false)
        if (decision.markOnboardingCompleted) settingsRepository.setOnboardingCompleted(true)
        value = decision.route
    }
    val currentStartRoute = startRoute
    if (currentStartRoute == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    // Explicit dotted keys: the default key is the position hash in base 36,
    // which can collide with runtime-saveable slots after rapid Activity
    // recreation (density changes) and composition-structure swaps. Dotted
    // custom keys cannot collide with a base-36 position hash.
    @Suppress("DEPRECATION")
    val detailPaneState = rememberSaveable(
        key = "phitracker.detail_pane_state",
        saver = DetailPaneState.Saver
    ) { DetailPaneState() }
    // Collectibles chapter drill-down state: used inline by the compact Index
    // content and the expanded home's left pane. Hoisted at the NavHost level
    // so both layouts share one instance; it survives configuration changes
    // and process death via the Saver.
    @Suppress("DEPRECATION")
    val collectiblesChapterState = rememberSaveable(
        key = "phitracker.collectibles_chapter_state",
        saver = CollectiblesChapterState.Saver
    ) { CollectiblesChapterState() }
    val b30Navigation = remember(navController, detailPaneState) {
        B30NavigationCoordinator(
            object : B30NavigationGateway {
                override fun navigateB30() {
                    val detail = detailPaneState.controller
                    if (detail != null) {
                        detail.navigate(Screen.B30Image.route) {
                            popUpTo(DetailPlaceholderRoute) { inclusive = false }
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(Screen.B30Image.route)
                    }
                }

                override fun navigateHomeReplacingLogin() {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }

                override fun navigateLoginReplacingHome() {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }

                override fun navigateHomeSkippingLogin() {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }

                override fun popCurrent() {
                    // toolbarBack from the B30 image: it lives in the detail
                    // pane when one is active, otherwise on the outer stack.
                    detailPaneState.controller?.popBackStack() ?: navController.popBackStack()
                }

                override fun popToHome(): Boolean =
                    navController.popBackStack(Screen.Home.route, inclusive = false)

                override fun popDetailToPlaceholder(): Boolean =
                    detailPaneState.controller
                        ?.popBackStack(DetailPlaceholderRoute, inclusive = false)
                        ?: false

                override fun navigateLoginClearingGraph() {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        )
    }
    val reducedMotionEnabled = rememberReducedMotionEnabled()

    NavHost(
        navController = navController,
        startDestination = currentStartRoute,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        composable(
            route = Screen.Onboarding.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_onboarding") }
            val onboardingViewModel: OnboardingViewModel = koinViewModel()
            val restartRequiredTitle = stringResource(Res.string.settings_restart_required_title)
            val restartRequiredMessage = stringResource(Res.string.settings_restart_required_message)
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onFinished = { restartRequired ->
                    // The wizard always hands off to Home: logging in now
                    // happens inline in the login step, so there is no
                    // separate Login destination anymore.
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                    // iOS applies the picked language only after a cold start;
                    // Android already applied it immediately.
                    if (restartRequired) {
                        triggerAppRestart(restartRequiredTitle, restartRequiredMessage)
                    }
                }
            )
        }
        composable(
            route = Screen.Login.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_login") }
            AppLogger.event("startup", "Login.beforeViewModel")
            val loginViewModel: LoginViewModel = koinViewModel()
            AppLogger.event("startup", "Login.afterViewModel")
            LoginScreen(
                onLoginSuccess = b30Navigation::loginSuccess,
                onSkip = b30Navigation::skipLoginToHome,
                viewModel = loginViewModel
            )
        }
        composable(
            route = Screen.Home.route,
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) {
            LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_home") }
            val homeViewModel: HomeViewModel = koinViewModel()
            val windowSizeClass = rememberWindowSizeClass()
            // Hoisted above the layout split: the tab selection must survive
            // swapping between the compact and expanded home layouts (a
            // foldable's fold/unfold swaps them without recreating this
            // destination).
            val tabState = rememberSaveable(saver = HomeTabState.Saver) { HomeTabState() }
            // Same hoisting for the Index page's sub-tab selection.
            val indexSubTabState = rememberSaveable(saver = IndexSubTabState.Saver) { IndexSubTabState() }
            if (windowSizeClass.isExpanded()) {
                // Expanded: rail + home tabs stay pinned on the left while
                // secondary pages open in the detail pane on the right.
                ExpandedHomeScreen(
                    homeViewModel = homeViewModel,
                    detailPaneState = detailPaneState,
                    b30Navigation = b30Navigation,
                    settingsRepository = settingsRepository,
                    reducedMotionEnabled = reducedMotionEnabled,
                    outerNavController = navController,
                    tabState = tabState,
                    indexSubTabState = indexSubTabState,
                    collectiblesChapterState = collectiblesChapterState,
                    onNavigateToLogin = { navController.navigate(Screen.Login.route) }
                )
            } else {
                // Shrunk below Expanded (e.g. foldable closed): reopen the page
                // the detail pane was showing, now full-screen. The collectible
                // chapter is already held by CollectiblesChapterState, so only
                // the detail destination needs restoring.
                LaunchedEffect(Unit) {
                    detailPaneState.consumePendingRoute()?.let {
                        navController.navigateDetailRequest(it)
                    }
                }
                MainScreen(
                    onNavigateToB30Image = b30Navigation::openB30,
                    onNavigateToSongDetail = { songId ->
                        navController.navigate(SongDetailRoute.from(songId = songId, difficulty = null))
                    },
                    onNavigateToSongDetailWithDifficulty = { songId, difficulty ->
                        navController.navigate(SongDetailRoute.from(songId = songId, difficulty = difficulty))
                    },
                    onNavigateToCollectibleDetail = { folderKey, key ->
                        navController.navigate(CollectibleDetailRoute(folderKey = folderKey, key = key))
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToSuggest = {
                        navController.navigate(Screen.Suggest.route)
                    },
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route)
                    },
                    tabState = tabState,
                    indexSubTabState = indexSubTabState,
                    collectiblesChapterState = collectiblesChapterState,
                    viewModel = homeViewModel
                )
            }
        }
        secondaryDestinations(
            host = navController,
            outer = navController,
            b30Navigation = b30Navigation,
            reducedMotionEnabled = reducedMotionEnabled,
            settingsRepository = settingsRepository,
            detailPaneState = detailPaneState,
            migrateOnExpanded = true
        )
        composable<IllustrationPreviewRoute>(
            enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
            exitTransition = { forwardExitTransition(reducedMotionEnabled) },
            popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
            popExitTransition = { popExitTransition(reducedMotionEnabled) }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<IllustrationPreviewRoute>()
            LaunchedEffect(route.songId) {
                AppLogger.event(
                    "navigation",
                    "entered_illustrationpreview",
                    mapOf("songId" to route.songId)
                )
            }
            val illustrationResolver: IllustrationUriResolver = koinInject()
            IllustrationPreviewScreen(
                illustrationUrl = illustrationResolver.standardUri(route.songId, route.difficulty()),
                songId = route.songId,
                difficulty = route.difficulty(),
                onClose = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Compact→expanded hand-off: when the window grows back to Expanded while a
 * secondary page sits full-screen on the outer stack (unfolding a foldable,
 * or widening a window), hand the page back to the detail pane and pop the
 * outer entry so Home resurfaces as the two-pane layout. Wired only on the
 * outer graph ([enabled]); the detail pane's own copies of these destinations
 * must not migrate.
 */
@Composable
private fun MigrateSecondaryToDetailOnExpanded(
    request: DetailRouteRequest,
    enabled: Boolean,
    detailPaneState: DetailPaneState,
    host: NavHostController
) {
    if (!enabled) return
    val expanded = rememberWindowSizeClass().isExpanded()
    LaunchedEffect(expanded) {
        if (expanded) {
            detailPaneState.setPendingRoute(request)
            host.popBackStack()
        }
    }
}

/**
 * Secondary destinations shared by the outer NavHost (compact layout) and the
 * detail-pane NavHost (expanded two-pane layout). [host] is the controller of
 * the graph being built — all secondary-to-secondary navigation and back pops
 * go through it; [outer] is always the root controller, used only to reach
 * destinations that stay full-screen in both layouts (the illustration
 * preview). Registering the same destinations on both graphs keeps the
 * compact flow unchanged while letting the detail pane host them when
 * expanded.
 *
 * [migrateOnExpanded] must be true only on the outer graph: when the window
 * grows back to Expanded while one of these pages sits on it full-screen, the
 * page hands itself back to the detail pane via [detailPaneState] and pops,
 * so Home resurfaces as the two-pane layout.
 */
internal fun NavGraphBuilder.secondaryDestinations(
    host: NavHostController,
    outer: NavHostController,
    b30Navigation: B30NavigationCoordinator,
    reducedMotionEnabled: Boolean,
    settingsRepository: SettingsRepository,
    detailPaneState: DetailPaneState,
    migrateOnExpanded: Boolean
) {
    composable(
        route = Screen.B30Image.route,
        enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
        exitTransition = { forwardExitTransition(reducedMotionEnabled) },
        popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
        popExitTransition = { popExitTransition(reducedMotionEnabled) }
    ) { backStackEntry ->
        LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_b30image") }
        DisposableEffect(backStackEntry) {
            onDispose {
                b30Navigation.destinationDisposed()
            }
        }
        val payload = b30Navigation.payload
        if (payload == null) {
            // Only recover when this entry is actually on top of the back
            // stack: during a pop transition the outgoing destination can
            // be recomposed, or disposed and recreated on iOS, after its
            // payload was released. Recovering in that state would clear
            // the whole navigation graph back to the login screen.
            val currentEntry by host.currentBackStackEntryAsState()
            if (currentEntry?.id == backStackEntry.id) {
                LaunchedEffect(backStackEntry) {
                    b30Navigation.recoverMissingPayload()
                }
            }
            return@composable
        }
        val illustrationResolver: IllustrationUriResolver = koinInject()
        val illustrationPreloadDeclined by settingsRepository.illustrationPreloadDeclined.collectAsState(
            initial = false
        )
        B30ImageScreen(
            b30 = payload.b30,
            displayRks = payload.displayRks,
            nickname = payload.nickname,
            challengeModeRank = payload.challengeModeRank,
            moneyString = payload.moneyString,
            clearCounts = payload.clearCounts,
            fcCount = payload.fcCount,
            phiCount = payload.phiCount,
            avatarUri = payload.avatarUri,
            showB30Overflow = payload.showB30Overflow,
            overflowCount = payload.overflowCount,
            themeSettings = payload.themeSettings,
            tagAnalysis = payload.tagAnalysis,
            getLowIllustrationUrl = { songId ->
                if (illustrationPreloadDeclined) illustrationResolver.lowLocalUri(songId)
                else illustrationResolver.lowUri(songId)
            },
            getStandardIllustrationUrl = illustrationResolver::standardUri,
            onBack = b30Navigation::toolbarBack
        )
    }
    composable(
        route = Screen.About.route,
        enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
        exitTransition = { forwardExitTransition(reducedMotionEnabled) },
        popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
        popExitTransition = { popExitTransition(reducedMotionEnabled) }
    ) {
        MigrateSecondaryToDetailOnExpanded(
            request = DetailRouteRequest.Plain(Screen.About.route),
            enabled = migrateOnExpanded,
            detailPaneState = detailPaneState,
            host = host
        )
        LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_about") }
        val aboutSettingsViewModel: SettingsViewModel = koinViewModel()
        AboutScreen(
            onNavigateBack = { host.popBackStack() },
            onNavigateToLicenses = { host.navigate(Screen.Licenses.route) },
            onNavigateToDisclaimer = { host.navigate(Screen.Disclaimer.route) },
            onNavigateToAcknowledgments = { host.navigate(Screen.Acknowledgments.route) },
            onNavigateToPrivacyPolicy = { host.navigate(Screen.PrivacyPolicy.route) },
            onRerunOnboarding = { aboutSettingsViewModel.requestOnboardingRerun() }
        )
    }
    composable(
        route = Screen.Disclaimer.route,
        enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
        exitTransition = { forwardExitTransition(reducedMotionEnabled) },
        popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
        popExitTransition = { popExitTransition(reducedMotionEnabled) }
    ) {
        MigrateSecondaryToDetailOnExpanded(
            request = DetailRouteRequest.Plain(Screen.Disclaimer.route),
            enabled = migrateOnExpanded,
            detailPaneState = detailPaneState,
            host = host
        )
        DisclaimerScreen(
            onNavigateBack = { host.popBackStack() }
        )
    }
    composable(
        route = Screen.Acknowledgments.route,
        enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
        exitTransition = { forwardExitTransition(reducedMotionEnabled) },
        popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
        popExitTransition = { popExitTransition(reducedMotionEnabled) }
    ) {
        MigrateSecondaryToDetailOnExpanded(
            request = DetailRouteRequest.Plain(Screen.Acknowledgments.route),
            enabled = migrateOnExpanded,
            detailPaneState = detailPaneState,
            host = host
        )
        AcknowledgmentsScreen(
            onNavigateBack = { host.popBackStack() }
        )
    }
    composable(
        route = Screen.Licenses.route,
        enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
        exitTransition = { forwardExitTransition(reducedMotionEnabled) },
        popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
        popExitTransition = { popExitTransition(reducedMotionEnabled) }
    ) {
        MigrateSecondaryToDetailOnExpanded(
            request = DetailRouteRequest.Plain(Screen.Licenses.route),
            enabled = migrateOnExpanded,
            detailPaneState = detailPaneState,
            host = host
        )
        LicensesScreen(
            onNavigateBack = { host.popBackStack() }
        )
    }
    composable(
        route = Screen.Settings.route,
        enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
        exitTransition = { forwardExitTransition(reducedMotionEnabled) },
        popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
        popExitTransition = { popExitTransition(reducedMotionEnabled) }
    ) {
        MigrateSecondaryToDetailOnExpanded(
            request = DetailRouteRequest.Plain(Screen.Settings.route),
            enabled = migrateOnExpanded,
            detailPaneState = detailPaneState,
            host = host
        )
        LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_settings") }
        val viewModel: SettingsViewModel = koinViewModel()
        SettingsScreen(
            viewModel = viewModel,
            onNavigateBack = { host.popBackStack() },
            onNavigateToAbout = { host.navigate(Screen.About.route) },
            onLogout = b30Navigation::settingsLogout
        )
    }
    composable(
        route = Screen.Suggest.route,
        enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
        exitTransition = { forwardExitTransition(reducedMotionEnabled) },
        popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
        popExitTransition = { popExitTransition(reducedMotionEnabled) }
    ) {
        MigrateSecondaryToDetailOnExpanded(
            request = DetailRouteRequest.Plain(Screen.Suggest.route),
            enabled = migrateOnExpanded,
            detailPaneState = detailPaneState,
            host = host
        )
        LaunchedEffect(Unit) { AppLogger.event("navigation", "entered_suggest") }
        val suggestViewModel: SuggestViewModel = koinViewModel()
        val illustrationResolver: IllustrationUriResolver = koinInject()
        val illustrationPreloadDeclined by settingsRepository.illustrationPreloadDeclined.collectAsState(
            initial = false
        )
        SuggestScreen(
            viewModel = suggestViewModel,
            getIllustrationUrl = { songId ->
                if (illustrationPreloadDeclined) illustrationResolver.lowLocalUri(songId)
                else illustrationResolver.lowUri(songId)
            },
            onNavigateToSongDetail = { songId, difficulty ->
                host.navigate(SongDetailRoute.from(songId = songId, difficulty = difficulty))
            },
            onNavigateBack = { host.popBackStack() }
        )
    }
    composable(
        route = Screen.PrivacyPolicy.route,
        enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
        exitTransition = { forwardExitTransition(reducedMotionEnabled) },
        popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
        popExitTransition = { popExitTransition(reducedMotionEnabled) }
    ) {
        MigrateSecondaryToDetailOnExpanded(
            request = DetailRouteRequest.Plain(Screen.PrivacyPolicy.route),
            enabled = migrateOnExpanded,
            detailPaneState = detailPaneState,
            host = host
        )
        PrivacyPolicyScreen(
            onNavigateBack = { host.popBackStack() }
        )
    }
    composable<SongDetailRoute>(
        enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
        exitTransition = { forwardExitTransition(reducedMotionEnabled) },
        popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
        popExitTransition = { popExitTransition(reducedMotionEnabled) }
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<SongDetailRoute>()
        MigrateSecondaryToDetailOnExpanded(
            request = DetailRouteRequest.SongDetail(route.songId, route.difficultyName),
            enabled = migrateOnExpanded,
            detailPaneState = detailPaneState,
            host = host
        )
        val songId = route.songId
        val difficulty = route.difficulty()
        val viewModel: SongDetailViewModel = koinViewModel(
            viewModelStoreOwner = backStackEntry,
            parameters = { parametersOf(songId, difficulty ?: org.kasumi321.ushio.phitracker.domain.model.Difficulty.IN) }
        )
        val state by viewModel.uiState.collectAsState()
        LaunchedEffect(songId, difficulty) {
            AppLogger.event(
                "navigation",
                "entered_songdetail",
                mapOf("songId" to songId, "difficulty" to (difficulty?.name ?: "default"))
            )
        }
        val songInfo = state.songInfo
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            songInfo != null -> SongDetailScreen(
                songInfo = songInfo,
                userRecords = state.userRecords,
                syncHistory = state.syncHistory,
                apiEnabled = state.apiEnabled,
                useApiData = state.useApiData,
                apiRequestKey = "${state.apiPlatform.trim()}\u0000${state.apiPlatformId.trim()}\u0000${state.apiUserId.trim()}\u0000${state.displayRks}",
                apiDetails = state.apiDetails,
                onLoadSongApiDetail = viewModel::loadSongApiDetail,
                chartTags = state.chartTags,
                onLoadChartTags = viewModel::loadChartTags,
                canVote = state.apiToken.isNotBlank(),
                onSubmitChartTagVote = viewModel::submitChartTagVote,
                aliasProposal = state.aliasProposal,
                onSubmitAliasProposal = viewModel::submitAliasProposal,
                onAliasProposalDialogOpen = viewModel::resetAliasProposalState,
                lowIllustrationUrls = state.lowIllustrationUrls,
                onIllustrationClick = { difficulty ->
                    // The preview stays a full-screen viewer on the outer graph
                    // in both layouts.
                    outer.navigate(IllustrationPreviewRoute.from(songInfo.id, difficulty))
                },
                initialDifficulty = state.initialDifficulty,
                onBack = { host.popBackStack() }
            )
            else -> SongDetailNotFound(songId = songId, onBack = { host.popBackStack() })
        }
    }
    composable<CollectibleDetailRoute>(
        enterTransition = { forwardEnterTransition(reducedMotionEnabled) },
        exitTransition = { forwardExitTransition(reducedMotionEnabled) },
        popEnterTransition = { popEnterTransition(reducedMotionEnabled) },
        popExitTransition = { popExitTransition(reducedMotionEnabled) }
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<CollectibleDetailRoute>()
        MigrateSecondaryToDetailOnExpanded(
            request = DetailRouteRequest.Collectible(route.folderKey, route.key),
            enabled = migrateOnExpanded,
            detailPaneState = detailPaneState,
            host = host
        )
        val viewModel: CollectibleDetailViewModel = koinViewModel(
            viewModelStoreOwner = backStackEntry,
            parameters = { parametersOf(route.folderKey, route.key) }
        )
        val state by viewModel.uiState.collectAsState()
        LaunchedEffect(route.folderKey, route.key) {
            AppLogger.event(
                "navigation",
                "entered_collectibledetail",
                mapOf("folder" to route.folderKey, "key" to route.key)
            )
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.item != null -> CollectibleDetailScreen(
                state = state,
                onBack = { host.popBackStack() }
            )
            else -> CollectibleDetailNotFound(
                folderKey = route.folderKey,
                key = route.key,
                onBack = { host.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectibleDetailNotFound(folderKey: String, key: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.collectible_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(Res.string.collectible_not_found),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.collectible_detail_key_label, "$folderKey · $key"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                onClick = onBack,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text(stringResource(Res.string.action_back))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongDetailNotFound(songId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.nav_song_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(Res.string.nav_song_not_found),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.nav_song_id, songId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                onClick = onBack,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text(stringResource(Res.string.action_back))
            }
        }
    }
}
