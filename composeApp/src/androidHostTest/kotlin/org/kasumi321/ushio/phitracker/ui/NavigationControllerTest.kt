package org.kasumi321.ushio.phitracker.ui

import android.content.Context
import androidx.compose.runtime.saveable.SaverScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationCoordinator
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationGateway
import org.kasumi321.ushio.phitracker.ui.navigation.CollectibleDetailRoute
import org.kasumi321.ushio.phitracker.ui.navigation.SongDetailRoute

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NavigationControllerTest {
    @Test
    fun restoredNestedDetailResetsToPlaceholderWithProductionAction() {
        val original = detailController()
        original.navigate(Screen.Settings.route)
        original.navigate(Screen.About.route)
        assertEquals(Screen.About.route, original.currentDestination?.route)

        // Mirrors the nested rememberNavController save/restore in Home's
        // saveable state.
        val saved = checkNotNull(original.saveState())
        val restored = newController()
        restored.restoreState(saved)
        restored.setDetailGraph()
        assertEquals(Screen.About.route, restored.currentDestination?.route)

        val state = DetailPaneState()
        state.attachController(restored)
        state.clearForSessionReplacement()
        val generation = state.generation
        // A reset wins even over a route captured at composition time.
        val action = state.consumePendingAction()
        assertEquals(DetailPendingAction.Reset, action)

        restored.applyDetailPendingAction(action, state, generation)

        assertEquals(DetailPlaceholderRoute, restored.currentDestination?.route)
        assertEquals(listOf(DetailPlaceholderRoute), restored.routes())
        assertFalse(state.pendingReset)
    }

    @Test
    fun restoredSettingsAboutFlowIsKeptWhenTheMirroredRouteMatchesTop() {
        val original = detailController()
        original.navigate(Screen.Settings.route)
        original.navigate(Screen.About.route)
        val originalEntries = original.entries()

        // Activity recreation saves both the nested controller's stack and
        // DetailPaneState (which carries the mirrored top route).
        val saved = checkNotNull(original.saveState())
        val paneState = DetailPaneState()
        paneState.setPendingRoute(DetailRouteRequest.Plain(Screen.About.route))
        val savedPane = with(DetailPaneState.Saver) {
            with(SaverScope { true }) { save(paneState) }
        }
        val restored = newController()
        restored.restoreState(saved)
        restored.setDetailGraph()
        val restoredPane = checkNotNull(DetailPaneState.Saver.restore(checkNotNull(savedPane)))
        assertEquals(
            listOf(DetailPlaceholderRoute, Screen.Settings.route, Screen.About.route),
            restored.routes()
        )

        val action = restoredPane.consumePendingAction()
        restored.applyDetailPendingAction(action, restoredPane, restoredPane.generation)

        assertEquals(
            listOf(DetailPlaceholderRoute, Screen.Settings.route, Screen.About.route),
            restored.routes(),
            "the restored nested flow must not be popped and rebuilt"
        )
        assertEquals(originalEntries, restored.entries(), "the same entries (and ViewModels) are kept")
    }

    @Test
    fun restoredAboutLicensesFlowKeepsAboutBelowLicenses() {
        val original = detailController()
        original.navigate(Screen.Settings.route)
        original.navigate(Screen.About.route)
        original.navigate(Screen.Licenses.route)
        val originalEntries = original.entries()

        val saved = checkNotNull(original.saveState())
        val restored = newController()
        restored.restoreState(saved)
        restored.setDetailGraph()

        val state = DetailPaneState()
        state.setPendingRoute(DetailRouteRequest.Plain(Screen.Licenses.route))
        val action = state.consumePendingAction()
        restored.applyDetailPendingAction(action, state, state.generation)

        assertEquals(
            listOf(
                DetailPlaceholderRoute,
                Screen.Settings.route,
                Screen.About.route,
                Screen.Licenses.route
            ),
            restored.routes()
        )
        assertEquals(originalEntries, restored.entries())

        // The nested flow still backs out to About instead of the placeholder.
        assertTrue(restored.popBackStack())
        assertEquals(Screen.About.route, restored.currentDestination?.route)
    }

    @Test
    fun restoredSongDetailWithSameArgumentsIsNotRenavigated() {
        val route = SongDetailRoute(songId = "光.姜米條.0", difficultyName = "AT")
        val original = detailController()
        original.navigate(route)
        val originalEntries = original.entries()

        val saved = checkNotNull(original.saveState())
        val restored = newController()
        restored.restoreState(saved)
        restored.setDetailGraph()

        val state = DetailPaneState()
        state.setPendingRoute(DetailRouteRequest.SongDetail(route.songId, route.difficultyName))
        val action = state.consumePendingAction()
        restored.applyDetailPendingAction(action, state, state.generation)

        assertEquals(2, restored.routes().size)
        assertEquals(originalEntries, restored.entries())
        assertEquals(route, restored.currentEntry().toRoute<SongDetailRoute>())
    }

    @Test
    fun restoredSongDetailWithDifferentArgumentsReplacesTheStack() {
        val original = detailController()
        original.navigate(SongDetailRoute(songId = "song.0", difficultyName = "IN"))
        val originalEntryId = original.currentEntry().id

        val saved = checkNotNull(original.saveState())
        val restored = newController()
        restored.restoreState(saved)
        restored.setDetailGraph()

        val state = DetailPaneState()
        val request = DetailRouteRequest.SongDetail("song.0", "AT")
        state.setPendingRoute(request)
        restored.applyDetailPendingAction(
            state.consumePendingAction(),
            state,
            state.generation
        )

        assertEquals(2, restored.routes().size, "the previous page is replaced, not stacked")
        assertEquals("AT", restored.currentEntry().toRoute<SongDetailRoute>().difficultyName)
        assertNotEquals(originalEntryId, restored.currentEntry().id)
    }

    @Test
    fun foldMigrationToADifferentRestoredRouteReplacesThePaneStack() {
        // The pane still restores the page from the last expanded session while
        // the compact layout hands over another full-screen page.
        val original = detailController()
        original.navigate(Screen.About.route)

        val saved = checkNotNull(original.saveState())
        val restored = newController()
        restored.restoreState(saved)
        restored.setDetailGraph()

        val state = DetailPaneState()
        val request = DetailRouteRequest.Plain(Screen.Settings.route)
        state.setPendingRoute(request)
        restored.applyDetailPendingAction(
            state.consumePendingAction(),
            state,
            state.generation
        )

        assertEquals(
            listOf(DetailPlaceholderRoute, Screen.Settings.route),
            restored.routes()
        )
    }

    @Test
    fun foldMigrationToTheSameRestoredPageKeepsItsEntry() {
        val original = detailController()
        original.navigate(Screen.Settings.route)
        val originalEntryId = original.currentEntry().id

        val saved = checkNotNull(original.saveState())
        val restored = newController()
        restored.restoreState(saved)
        restored.setDetailGraph()

        val state = DetailPaneState()
        val request = DetailRouteRequest.Plain(Screen.Settings.route)
        state.setPendingRoute(request)
        restored.applyDetailPendingAction(
            state.consumePendingAction(),
            state,
            state.generation
        )

        assertEquals(
            listOf(DetailPlaceholderRoute, Screen.Settings.route),
            restored.routes()
        )
        assertEquals(originalEntryId, restored.currentEntry().id)
    }

    @Test
    fun hostMatchesOnlyThePageWithTheSameRouteAndArguments() {
        val outer = outerController()
        outer.navigate(Screen.Settings.route)

        assertTrue(outer.isShowingDetailRequest(DetailRouteRequest.Plain(Screen.Settings.route)))
        assertFalse(outer.isShowingDetailRequest(DetailRouteRequest.Plain(Screen.About.route)))
        assertFalse(outer.isShowingDetailRequest(DetailRouteRequest.SongDetail("song.0", null)))
    }

    @Test
    fun typedDetailMatchingComparesDecodedArguments() {
        val controller = detailController()
        controller.navigate(CollectibleDetailRoute(folderKey = "额外章节", key = "mope1"))

        assertTrue(
            controller.isShowingDetailRequest(
                DetailRouteRequest.Collectible(folderKey = "额外章节", key = "mope1")
            )
        )
        assertFalse(
            controller.isShowingDetailRequest(
                DetailRouteRequest.Collectible(folderKey = "额外章节", key = "mope2")
            )
        )
        assertTrue(controller.currentEntry().destination.hasRoute<CollectibleDetailRoute>())
    }

    @Test
    fun skipWithHomeBelowLoginPopsBackWithoutReplacingHome() {
        val controller = outerController()
        val homeEntryId = controller.currentEntry().id
        controller.navigate(Screen.Login.route)

        controller.popToHomeOrReplaceLogin()

        assertEquals(Screen.Home.route, controller.currentDestination?.route)
        assertEquals(listOf(Screen.Home.route), controller.routes())
        assertEquals(homeEntryId, controller.currentEntry().id)
    }

    @Test
    fun resumeWithHomeBelowLoginKeepsTheExistingHomeEntry() {
        val controller = outerController()
        val homeEntryId = controller.currentEntry().id
        controller.navigate(Screen.Login.route)

        controller.popToHomeOrReplaceLogin()

        assertEquals(Screen.Home.route, controller.currentDestination?.route)
        assertEquals(homeEntryId, controller.currentEntry().id)
    }

    @Test
    fun skipFromLoginRootPushesHomeAndDropsLogin() {
        val controller = navController(startDestination = Screen.Login.route, Screen.Home.route)

        controller.popToHomeOrReplaceLogin()

        assertEquals(Screen.Home.route, controller.currentDestination?.route)
        assertEquals(listOf(Screen.Home.route), controller.routes())
    }

    @Test
    fun missingB30PayloadInDetailResetsPaneWithoutTouchingOuterStack() {
        val outer = outerController()
        val outerHomeId = outer.currentEntry().id
        val detail = detailController()
        detail.navigate(Screen.About.route)
        val state = DetailPaneState()
        state.attachController(detail)
        val coordinator = B30NavigationCoordinator(unexpectedGateway())

        recoverMissingB30Payload(B30Host.DetailPane, state, coordinator)
        assertTrue(state.pendingReset)

        val action = state.consumePendingAction()
        assertEquals(DetailPendingAction.Reset, action)
        detail.applyDetailPendingAction(action, state, state.generation)

        assertEquals(DetailPlaceholderRoute, detail.currentDestination?.route)
        assertEquals(Screen.Home.route, outer.currentDestination?.route)
        assertEquals(outerHomeId, outer.currentEntry().id)
    }

    @Test
    fun missingB30PayloadOnOuterGraphStillPopsHome() {
        val outer = navController(
            startDestination = Screen.Home.route,
            Screen.B30Image.route,
            Screen.Login.route
        )
        outer.navigate(Screen.B30Image.route)
        val coordinator = B30NavigationCoordinator(
            object : B30NavigationGateway {
                override fun navigateB30() = unexpected()
                override fun navigateHomeReplacingLogin() = unexpected()
                override fun navigateHomeResumingSession() = unexpected()
                override fun clearDetailForSessionReplacement() = unexpected()
                override fun navigateLoginReplacingHome() = unexpected()
                override fun navigateHomeSkippingLogin() = unexpected()
                override fun popCurrent() = unexpected()
                override fun popToHome(): Boolean =
                    outer.popBackStack(Screen.Home.route, inclusive = false)
                override fun popDetailToPlaceholder(): Boolean = false
                override fun navigateLoginClearingGraph() = unexpected()
            }
        )

        recoverMissingB30Payload(B30Host.OuterGraph, DetailPaneState(), coordinator)

        assertEquals(Screen.Home.route, outer.currentDestination?.route)
    }

    private fun outerController(): NavHostController = navController(
        startDestination = Screen.Home.route,
        Screen.Login.route,
        Screen.B30Image.route,
        Screen.Settings.route
    )

    private fun detailController(): NavHostController {
        val controller = newController()
        controller.setDetailGraph()
        return controller
    }

    private fun newController(): NavHostController = NavHostController(context()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        setViewModelStore(ViewModelStore())
        setLifecycleOwner(TestLifecycleOwner())
    }

    private fun navController(startDestination: String, vararg routes: String): NavHostController =
        newController().apply {
            graph = createGraph(startDestination = startDestination) {
                composable(startDestination) {}
                routes.forEach { route -> composable(route) {} }
            }
        }

    private fun NavHostController.setDetailGraph() {
        graph = createGraph(startDestination = DetailPlaceholderRoute) {
            composable(DetailPlaceholderRoute) {}
            composable(Screen.Settings.route) {}
            composable(Screen.About.route) {}
            composable(Screen.Licenses.route) {}
            composable(Screen.B30Image.route) {}
            composable<SongDetailRoute> {}
            composable<CollectibleDetailRoute> {}
        }
    }

    // The root NavGraph is itself an entry in currentBackStack; it has no route.
    private fun NavHostController.routes(): List<String> =
        currentBackStack.value.mapNotNull { it.destination.route }

    // Route + entry id, so a test can prove the restored entries were kept
    // instead of popped and rebuilt with fresh ViewModel stores.
    private fun NavHostController.entries(): List<Pair<String, String>> =
        currentBackStack.value.mapNotNull { entry ->
            entry.destination.route?.let { it to entry.id }
        }

    private fun NavHostController.currentEntry(): NavBackStackEntry =
        currentBackStack.value.last()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun unexpectedGateway(): B30NavigationGateway = object : B30NavigationGateway {
        override fun navigateB30() = unexpected()
        override fun navigateHomeReplacingLogin() = unexpected()
        override fun navigateHomeResumingSession() = unexpected()
        override fun clearDetailForSessionReplacement() = unexpected()
        override fun navigateLoginReplacingHome() = unexpected()
        override fun navigateHomeSkippingLogin() = unexpected()
        override fun popCurrent() = unexpected()
        override fun popToHome(): Boolean = unexpected()
        override fun popDetailToPlaceholder(): Boolean = unexpected()
        override fun navigateLoginClearingGraph() = unexpected()
    }

    private fun unexpected(): Nothing = error("the outer gateway must not be consulted for a detail-hosted B30 recovery")

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
    }
}
