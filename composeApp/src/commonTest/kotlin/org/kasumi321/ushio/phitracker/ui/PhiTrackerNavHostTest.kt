package org.kasumi321.ushio.phitracker.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.b30.B30ExportPayload
import org.kasumi321.ushio.phitracker.ui.b30.B30MissingPayloadRecovery
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationCoordinator
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationGateway
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings

class PhiTrackerNavHostTest {
    @Test
    fun toolbarBackKeepsPayloadAliveUntilDestinationDispose() {
        val gateway = RecordingGateway("home")
        val coordinator = B30NavigationCoordinator(gateway)
        coordinator.openB30(payload("current"))
        gateway.observePayload = { coordinator.payload }

        coordinator.toolbarBack()

        // The payload must survive the pop transition; the destination can be
        // recomposed (or recreated on iOS) before it leaves the composition.
        assertEquals(1, gateway.navigationObservationCount)
        assertNotNull(gateway.payloadObservedDuringNavigation)
        assertNotNull(coordinator.payload)
        assertEquals(listOf("home"), gateway.routes)

        coordinator.destinationDisposed()
        assertNull(coordinator.payload)
    }

    @Test
    fun destinationDisposalAfterSystemBackClearsPayload() {
        repeat(2) {
            val gateway = RecordingGateway("home")
            val coordinator = B30NavigationCoordinator(gateway)
            coordinator.openB30(payload("current"))

            gateway.systemBack()
            coordinator.destinationDisposed()

            assertNull(coordinator.payload)
            assertEquals(listOf("home"), gateway.routes)
        }
    }

    @Test
    fun homeLogoutClearsPayloadBeforeReplacingGraphWithLogin() {
        val gateway = RecordingGateway("home")
        val coordinator = B30NavigationCoordinator(gateway)
        coordinator.openB30(payload("previous-session"))
        gateway.systemBack()
        gateway.observePayload = { coordinator.payload }

        coordinator.homeLogout()

        assertEquals(1, gateway.navigationObservationCount)
        assertNull(gateway.payloadObservedDuringNavigation)
        assertNull(coordinator.payload)
        assertEquals(listOf("login"), gateway.routes)
    }

    @Test
    fun settingsLogoutClearsPayloadBeforeReplacingGraphWithLogin() {
        val gateway = RecordingGateway("home")
        val coordinator = B30NavigationCoordinator(gateway)
        coordinator.openB30(payload("previous-session"))
        gateway.systemBack()
        gateway.routes += "settings"
        gateway.observePayload = { coordinator.payload }

        coordinator.settingsLogout()

        assertEquals(1, gateway.navigationObservationCount)
        assertNull(gateway.payloadObservedDuringNavigation)
        assertNull(coordinator.payload)
        assertEquals(listOf("login"), gateway.routes)
    }

    @Test
    fun loginSuccessClearsPreviousSessionPayloadBeforeOpeningHome() {
        val gateway = RecordingGateway("login")
        val coordinator = B30NavigationCoordinator(gateway)
        coordinator.openB30(payload("previous-session"))
        gateway.systemBack()
        gateway.observePayload = { coordinator.payload }

        coordinator.loginSuccess()

        assertEquals(1, gateway.navigationObservationCount)
        assertNull(gateway.payloadObservedDuringNavigation)
        assertNull(coordinator.payload)
        assertEquals(listOf("home"), gateway.routes)
    }

    @Test
    fun missingPayloadPopsToExistingHomeWithoutRenderData() {
        val gateway = RecordingGateway("home", "b30image")
        val coordinator = B30NavigationCoordinator(gateway)

        val recovery = coordinator.recoverMissingPayload()

        assertEquals(B30MissingPayloadRecovery.HomePopped, recovery)
        assertNull(coordinator.payload)
        assertEquals(listOf("home"), gateway.routes)
    }

    @Test
    fun skipLoginPopsBackToHomeWhenHomeIsBelowLogin() {
        val gateway = RecordingGateway("home", "login")
        val coordinator = B30NavigationCoordinator(gateway)

        coordinator.skipLoginToHome()

        assertEquals(listOf("home"), gateway.routes)
    }

    @Test
    fun skipLoginPushesHomeWhenLoginIsRoot() {
        val gateway = RecordingGateway("login")
        val coordinator = B30NavigationCoordinator(gateway)

        coordinator.skipLoginToHome()

        assertEquals(listOf("home"), gateway.routes)
    }

    @Test
    fun missingPayloadClearsGraphToLoginWhenHomeIsAbsent() {
        val gateway = RecordingGateway("b30image")
        val coordinator = B30NavigationCoordinator(gateway)

        val recovery = coordinator.recoverMissingPayload()

        assertEquals(B30MissingPayloadRecovery.NavigateLogin, recovery)
        assertNull(coordinator.payload)
        assertEquals(listOf("login"), gateway.routes)
    }

    @Test
    fun missingPayloadRecoversToDetailPlaceholderWhenDetailPaneActive() {
        val gateway = RecordingGateway("detail_placeholder", "b30image")
        gateway.hasDetailPane = true
        val coordinator = B30NavigationCoordinator(gateway)

        val recovery = coordinator.recoverMissingPayload()

        assertEquals(B30MissingPayloadRecovery.HomePopped, recovery)
        assertNull(coordinator.payload)
        assertEquals(listOf("detail_placeholder"), gateway.routes)
    }

    @Test
    fun migratableDetailRouteKeepsPlainSecondaryRoutes() {
        val migratable = listOf(
            Screen.About,
            Screen.Disclaimer,
            Screen.Acknowledgments,
            Screen.Licenses,
            Screen.PrivacyPolicy,
            Screen.Settings,
            Screen.Suggest
        )
        migratable.forEach { screen ->
            assertEquals(screen.route, migratableDetailRoute(screen.route))
        }
    }

    @Test
    fun migratableDetailRouteDropsRoutesThatCannotMigrate() {
        // The B30 image needs its in-memory payload; typed routes cannot be
        // re-navigated from their pattern string; both fall back to Home.
        assertNull(migratableDetailRoute(null))
        assertNull(migratableDetailRoute(DetailPlaceholderRoute))
        assertNull(migratableDetailRoute(Screen.B30Image.route))
        assertNull(migratableDetailRoute(Screen.Home.route))
        assertNull(migratableDetailRoute("song_detail/{songId}/{difficulty}"))
    }

    @Test
    fun detailPaneStateConsumesPendingRouteOnce() {
        val state = DetailPaneState()
        state.onDetailRouteChanged(Screen.Settings.route)

        assertEquals(Screen.Settings.route, state.consumePendingRoute())
        assertNull(state.consumePendingRoute())
    }

    @Test
    fun detailPaneStateIgnoresNonMigratableDetailRoutes() {
        val state = DetailPaneState()
        state.onDetailRouteChanged(DetailPlaceholderRoute)

        assertNull(state.consumePendingRoute())
    }

    private fun payload(songId: String) = B30ExportPayload(
        b30 = listOf(
            BestRecord(
                songId = songId,
                songName = "Song $songId",
                difficulty = Difficulty.IN,
                score = 987_654,
                accuracy = 98.76f,
                isFullCombo = true,
                chartConstant = 15.2f,
                rks = 14.4f
            )
        ),
        displayRks = 15.4f,
        nickname = "Player",
        challengeModeRank = 48,
        moneyString = "1 2 3 4 5",
        clearCounts = mapOf("IN" to 4),
        fcCount = 7,
        phiCount = 2,
        avatarUri = "avatar://player",
        showB30Overflow = true,
        overflowCount = 9,
        themeSettings = PhiTrackerThemeSettings()
    )

    private class RecordingGateway(vararg initialRoutes: String) : B30NavigationGateway {
        val routes = initialRoutes.toMutableList()
        var hasDetailPane: Boolean = false
        var observePayload: () -> B30ExportPayload? = { null }
        var payloadObservedDuringNavigation: B30ExportPayload? = null
            private set
        var navigationObservationCount: Int = 0
            private set

        override fun navigateB30() {
            routes += "b30image"
        }

        override fun navigateHomeReplacingLogin() {
            observeDuringNavigation()
            routes.clear()
            routes += "home"
        }

        override fun navigateLoginReplacingHome() {
            observeDuringNavigation()
            routes.clear()
            routes += "login"
        }

        override fun navigateHomeSkippingLogin() {
            routes.removeAll { it == "login" }
            routes += "home"
        }

        override fun popCurrent() {
            observeDuringNavigation()
            routes.removeLast()
        }

        override fun popToHome(): Boolean {
            val homeIndex = routes.indexOfLast { it == "home" }
            if (homeIndex < 0) return false
            while (routes.lastIndex > homeIndex) routes.removeLast()
            return true
        }

        override fun popDetailToPlaceholder(): Boolean {
            if (!hasDetailPane) return false
            routes.clear()
            routes += "detail_placeholder"
            return true
        }

        override fun navigateLoginClearingGraph() {
            routes.clear()
            routes += "login"
        }

        fun systemBack() {
            routes.removeLast()
        }

        private fun observeDuringNavigation() {
            navigationObservationCount += 1
            payloadObservedDuringNavigation = observePayload()
        }
    }
}
