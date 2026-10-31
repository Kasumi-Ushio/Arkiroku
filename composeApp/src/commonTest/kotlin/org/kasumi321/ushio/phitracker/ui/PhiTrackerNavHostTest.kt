package org.kasumi321.ushio.phitracker.ui

import androidx.compose.runtime.saveable.SaverScope
import androidx.lifecycle.SavedStateHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.b30.B30ExportPayload
import org.kasumi321.ushio.phitracker.ui.b30.B30MissingPayloadRecovery
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationCoordinator
import org.kasumi321.ushio.phitracker.ui.navigation.LoginBackStackPolicy
import org.kasumi321.ushio.phitracker.ui.b30.B30NavigationGateway
import org.kasumi321.ushio.phitracker.ui.b30.claimPayload
import org.kasumi321.ushio.phitracker.ui.home.CollectiblesChapterState
import org.kasumi321.ushio.phitracker.ui.home.HomeTab
import org.kasumi321.ushio.phitracker.ui.home.HomeTabState
import org.kasumi321.ushio.phitracker.ui.home.IndexSubTab
import org.kasumi321.ushio.phitracker.ui.home.IndexSubTabState
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings

class PhiTrackerNavHostTest {
    @Test
    fun toolbarBackKeepsPayloadAliveUntilDestinationDispose() {
        val gateway = RecordingGateway("home")
        val coordinator = B30NavigationCoordinator(gateway)
        coordinator.openB30(payload("current"))
        val ownerGeneration = coordinator.payloadGeneration
        gateway.observePayload = { coordinator.payload }

        coordinator.toolbarBack()

        // The payload must survive the pop transition; the destination can be
        // recomposed (or recreated on iOS) before it leaves the composition.
        assertEquals(1, gateway.navigationObservationCount)
        assertNotNull(gateway.payloadObservedDuringNavigation)
        assertNotNull(coordinator.payload)
        assertEquals(listOf("home"), gateway.routes)

        coordinator.destinationDisposed(ownerGeneration)
        assertNull(coordinator.payload)
    }

    @Test
    fun destinationDisposalAfterSystemBackClearsPayload() {
        repeat(2) {
            val gateway = RecordingGateway("home")
            val coordinator = B30NavigationCoordinator(gateway)
            coordinator.openB30(payload("current"))
            val ownerGeneration = coordinator.payloadGeneration

            gateway.systemBack()
            coordinator.destinationDisposed(ownerGeneration)

            assertNull(coordinator.payload)
            assertEquals(listOf("home"), gateway.routes)
        }
    }

    @Test
    fun disposalOfAnExitingEntryKeepsThePayloadOfTheNewerEntry() {
        // User pops the image and reopens it while the exit transition still
        // keeps the old destination composed: the old entry's disposal must
        // not release the payload the new entry already renders.
        val gateway = RecordingGateway("home")
        val coordinator = B30NavigationCoordinator(gateway)
        coordinator.openB30(payload("first"))
        val firstGeneration = coordinator.payloadGeneration
        coordinator.toolbarBack()

        coordinator.openB30(payload("second"))
        val secondGeneration = coordinator.payloadGeneration
        assertTrue(secondGeneration != firstGeneration)

        coordinator.destinationDisposed(firstGeneration)

        assertEquals("second", coordinator.payload?.b30?.single()?.songId)

        coordinator.destinationDisposed(secondGeneration)
        assertNull(coordinator.payload)
    }

    @Test
    fun recreatedExitingEntryKeepsItsSavedGenerationAndCannotClearNewPayload() {
        val gateway = RecordingGateway("home")
        val coordinator = B30NavigationCoordinator(gateway)
        coordinator.openB30(payload("first"))
        val oldHandle = SavedStateHandle()
        val first = coordinator.claimPayload(oldHandle)
        assertEquals("first", first.payload?.b30?.single()?.songId)
        coordinator.toolbarBack()
        coordinator.openB30(payload("second"))

        // iOS dispose/recreate uses the same entry SavedStateHandle, not the
        // latest generation published for the incoming entry.
        val recreatedOld = coordinator.claimPayload(oldHandle)
        assertEquals(first.generation, recreatedOld.generation)
        assertNull(recreatedOld.payload)
        coordinator.destinationDisposed(recreatedOld.generation)
        assertEquals("second", coordinator.payload?.b30?.single()?.songId)

        val incoming = coordinator.claimPayload(SavedStateHandle())
        assertEquals("second", incoming.payload?.b30?.single()?.songId)
        coordinator.destinationDisposed(incoming.generation)
        assertNull(coordinator.payload)
    }

    @Test
    fun disposalAfterSessionLevelClearCannotReleaseALaterPayload() {
        val gateway = RecordingGateway("home")
        val coordinator = B30NavigationCoordinator(gateway)
        coordinator.openB30(payload("previous-session"))
        val staleGeneration = coordinator.payloadGeneration
        coordinator.loginSuccess()

        coordinator.openB30(payload("new-session"))
        val currentPayload = coordinator.payload

        coordinator.destinationDisposed(staleGeneration)

        assertSame(currentPayload, coordinator.payload)
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
    fun guestHomeIsReplacedOnLoginSuccessLeavingExactlyOneHome() {
        // Guest Home opened Login; success must REPLACE that Home entry, not
        // stack a second one on top of the stale guest page.
        val gateway = RecordingGateway("home", "login")
        val coordinator = B30NavigationCoordinator(gateway)

        coordinator.loginSuccess()

        assertEquals(listOf("home"), gateway.routes)
        assertEquals(1, gateway.routes.count { it == "home" }, "exactly one Home entry after login")
    }

    @Test
    fun skipLoginPopsBackToHomeWhenHomeIsBelowLogin() {
        val gateway = RecordingGateway("home", "login")
        val coordinator = B30NavigationCoordinator(gateway)

        coordinator.skipLoginToHome()

        assertEquals(listOf("home"), gateway.routes)
        assertEquals(1, gateway.clearDetailCalls, "skip is a session replacement")
    }

    @Test
    fun skipLoginPushesHomeWhenLoginIsRoot() {
        val gateway = RecordingGateway("login")
        val coordinator = B30NavigationCoordinator(gateway)

        coordinator.skipLoginToHome()

        assertEquals(listOf("home"), gateway.routes)
        assertEquals(1, gateway.clearDetailCalls)
    }

    @Test
    fun sessionResumeKeepsRestoredDetailWithoutSessionReplacement() {
        val gateway = RecordingGateway("login")
        val coordinator = B30NavigationCoordinator(gateway)

        coordinator.resumeSession()

        assertEquals(listOf("home"), gateway.routes)
        assertEquals(0, gateway.clearDetailCalls)
    }

    @Test
    fun sessionResumePopsBackToExistingHomeWithoutReplacingIt() {
        val gateway = RecordingGateway("home", "login")
        val coordinator = B30NavigationCoordinator(gateway)

        coordinator.resumeSession()

        assertEquals(listOf("home"), gateway.routes)
        assertEquals(1, gateway.routes.count { it == "home" }, "the existing Home entry is kept")
        assertEquals(0, gateway.clearDetailCalls)
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
        // The B30 image needs its in-memory payload and falls back to Home.
        // The typed song detail migrates through toDetailRouteRequest with its
        // decoded arguments; its pattern string alone is not navigable.
        assertNull(migratableDetailRoute(null))
        assertNull(migratableDetailRoute(DetailPlaceholderRoute))
        assertNull(migratableDetailRoute(Screen.B30Image.route))
        assertNull(migratableDetailRoute(Screen.Home.route))
        assertNull(migratableDetailRoute("song_detail/{songId}/{difficulty}"))
    }

    @Test
    fun detailPaneStateConsumesPendingRouteOnce() {
        val state = DetailPaneState()
        state.setPendingRoute(DetailRouteRequest.Plain(Screen.Settings.route))

        assertEquals(DetailRouteRequest.Plain(Screen.Settings.route), state.consumePendingRoute())
        assertNull(state.consumePendingRoute())
    }

    @Test
    fun pendingRouteQueuedBeforeGraphReadyIsDeliveredOnce() {
        val state = DetailPaneState()
        val queued = DetailRouteRequest.Plain(Screen.About.route)
        state.setPendingRoute(queued)

        assertEquals(DetailPendingAction.Navigate(queued), state.consumePendingAction())
        assertNull(state.consumePendingRoute())
    }

    @Test
    fun noPendingActionLeavesControllerRestoredStackUntouched() {
        val state = DetailPaneState()

        assertEquals(DetailPendingAction.None, state.consumePendingAction())
    }

    @Test
    fun sessionReplacementRequestsReset() {
        val state = DetailPaneState(DetailRouteRequest.Plain(Screen.Settings.route))
        state.clearForSessionReplacement()

        assertTrue(state.pendingReset)
        assertNull(state.consumePendingRoute())
        assertEquals(DetailPendingAction.Reset, state.consumePendingAction())
        assertTrue(state.pendingReset, "the reset stays pending until the pane applies it")
        state.completePendingReset()
        assertFalse(state.pendingReset)
    }

    @Test
    fun routeQueuedAfterSessionReplacementSupersedesReset() {
        val state = DetailPaneState()
        state.clearForSessionReplacement()
        state.setPendingRoute(DetailRouteRequest.Plain(Screen.About.route))

        assertFalse(state.pendingReset)
        assertEquals(
            DetailPendingAction.Navigate(DetailRouteRequest.Plain(Screen.About.route)),
            state.consumePendingAction()
        )
    }

    @Test
    fun mirroredNullDoesNotCancelPendingReset() {
        val state = DetailPaneState()
        state.clearForSessionReplacement()

        state.setPendingRoute(null)

        assertTrue(state.pendingReset)
    }

    @Test
    fun staleGenerationCannotRequestReset() {
        val state = DetailPaneState()
        val staleGeneration = state.generation
        state.clearForSessionReplacement()
        state.completePendingReset()

        state.requestReset(staleGeneration)
        assertFalse(state.pendingReset)

        state.requestReset()
        assertTrue(state.pendingReset)
    }

    @Test
    fun pendingResetIsDeliveredToGraphReadyEffect() {
        val state = DetailPaneState()
        state.clearForSessionReplacement()

        assertEquals(DetailPendingAction.Reset, state.consumePendingAction())
    }

    @Test
    fun missingB30PayloadInDetailRequestsPaneResetWithoutOuterRecovery() {
        val gateway = RecordingGateway("home", "b30image")
        val coordinator = B30NavigationCoordinator(gateway)
        val state = DetailPaneState()

        recoverMissingB30Payload(B30Host.DetailPane, state, coordinator)

        assertTrue(state.pendingReset)
        assertEquals(listOf("home", "b30image"), gateway.routes)
    }

    @Test
    fun missingB30PayloadOnOuterGraphUsesExistingRecovery() {
        val gateway = RecordingGateway("home", "b30image")
        val coordinator = B30NavigationCoordinator(gateway)
        val state = DetailPaneState()

        recoverMissingB30Payload(B30Host.OuterGraph, state, coordinator)

        assertFalse(state.pendingReset)
        assertEquals(listOf("home"), gateway.routes)
    }

    @Test
    fun detailPaneStateIgnoresNonMigratableDetailRoutes() {
        val state = DetailPaneState()
        state.setPendingRoute(migratableDetailRoute(DetailPlaceholderRoute)?.let(DetailRouteRequest::Plain))

        assertNull(state.consumePendingRoute())
    }

    @Test
    fun detailPaneStateSaverRestoresPendingRouteAcrossRecreation() {
        // A foldable's display switch recreates the Activity; the mirrored
        // detail route must survive so the reopened layout can restore it.
        val state = DetailPaneState()
        state.setPendingRoute(DetailRouteRequest.Plain(Screen.Settings.route))

        val saved = with(DetailPaneState.Saver) {
            with(SaverScope { true }) { save(state) }
        }
        val restored = DetailPaneState.Saver.restore(checkNotNull(saved))

        assertEquals(DetailRouteRequest.Plain(Screen.Settings.route), restored?.consumePendingRoute())
    }

    @Test
    fun detailPaneStateSaverRestoresSongDetailWithArguments() {
        // Song details opened from the B30 or songs tab are the common case on
        // a foldable; their typed arguments must survive the recreation too,
        // including non-ASCII song ids.
        val state = DetailPaneState()
        state.setPendingRoute(DetailRouteRequest.SongDetail("光.姜米條.0", "AT"))

        val saved = with(DetailPaneState.Saver) {
            with(SaverScope { true }) { save(state) }
        }
        val restored = DetailPaneState.Saver.restore(checkNotNull(saved))

        assertEquals(
            DetailRouteRequest.SongDetail("光.姜米條.0", "AT"),
            restored?.consumePendingRoute()
        )
    }

    @Test
    fun detailPaneStateSaverRestoresCollectibleDetailWithArguments() {
        // The item detail carries a non-ASCII zh_cn folder key and must
        // survive a recreation. The chapter level is no longer a route.
        val detailState = DetailPaneState()
        detailState.setPendingRoute(DetailRouteRequest.Collectible("额外章节", "mope1"))
        val detailSaved = with(DetailPaneState.Saver) {
            with(SaverScope { true }) { save(detailState) }
        }
        val detailRestored = DetailPaneState.Saver.restore(checkNotNull(detailSaved))
        assertEquals(
            DetailRouteRequest.Collectible("额外章节", "mope1"),
            detailRestored?.consumePendingRoute()
        )
    }

    @Test
    fun detailPaneStateSaverPersistsResetAcrossRecreation() {
        // A session replacement can land between save and the next composition;
        // the reset flag must survive with no route left to carry it.
        val state = DetailPaneState(DetailRouteRequest.Plain(Screen.Settings.route))
        state.clearForSessionReplacement()

        val saved = with(DetailPaneState.Saver) {
            with(SaverScope { true }) { save(state) }
        }
        val restored = DetailPaneState.Saver.restore(checkNotNull(saved))

        assertTrue(checkNotNull(restored).pendingReset)
        assertNull(restored.consumePendingRoute())
    }

    @Test
    fun detailPaneStateSaverRestoresLegacyRouteOnlyFormat() {
        // Previous releases persisted only the route JSON. An upgrade in the
        // middle of an Activity recreation must still restore that page.
        val legacyRequest: DetailRouteRequest = DetailRouteRequest.Plain(Screen.Settings.route)
        val legacySaved = Json.encodeToString(legacyRequest)

        val restored = DetailPaneState.Saver.restore(legacySaved)

        assertNotNull(restored)
        assertFalse(restored.pendingReset)
        assertEquals(legacyRequest, restored.consumePendingRoute())
    }

    @Test
    fun collectiblesChapterStateSaverRestoresFolderKeyAcrossRecreation() {
        val state = CollectiblesChapterState()
        assertEquals(null, with(CollectiblesChapterState.Saver) {
            with(SaverScope { true }) { save(state) }
        }, "No drill-down means nothing to save")

        state.open("冰封世界")
        val saved = with(CollectiblesChapterState.Saver) {
            with(SaverScope { true }) { save(state) }
        }
        val restored = CollectiblesChapterState.Saver.restore(checkNotNull(saved))
        assertEquals("冰封世界", restored?.folderKey)

        restored?.close()
        assertEquals(null, restored?.folderKey)
    }

    @Test
    fun detailPaneStateSaverSkipsNothingToSave() {
        val saved = with(DetailPaneState.Saver) {
            with(SaverScope { true }) { save(DetailPaneState()) }
        }
        assertNull(saved)
    }

    @Test
    fun saversIgnoreValuesOfTheWrongType() {
        assertNull(DetailPaneState.Saver.restore(123)?.consumePendingRoute())
        assertNull(DetailPaneState.Saver.restore("not-json")?.consumePendingRoute())

        assertNull(CollectiblesChapterState.Saver.restore(123)?.folderKey)
        assertEquals(
            "冰封世界",
            CollectiblesChapterState.Saver.restore("冰封世界")?.folderKey
        )

        assertEquals(HomeTab.Profile, HomeTabState.Saver.restore("not-an-int")?.selected)
        assertEquals(HomeTab.B30, HomeTabState.Saver.restore(1)?.selected)
        assertEquals(HomeTab.Profile, HomeTabState.Saver.restore(99)?.selected)

        assertEquals(IndexSubTab.Songs, IndexSubTabState.Saver.restore("not-an-int")?.selected)
        assertEquals(IndexSubTab.Collectibles, IndexSubTabState.Saver.restore(1)?.selected)
        assertEquals(IndexSubTab.Songs, IndexSubTabState.Saver.restore(99)?.selected)
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
        var clearDetailCalls: Int = 0
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
            // Mirrors the real gateway: the policy decides which entry to pop
            // through; the fake models NavController's popUpTo (search from
            // the top) instead of clearing the whole stack.
            val replacement = LoginBackStackPolicy.homeReplacingLogin(
                backStackRoutes = routes.toList(),
                homeRoute = "home",
                loginRoute = "login"
            )
            popUpTo(replacement.popUpToRoute, replacement.inclusive)
            routes += "home"
        }

        override fun navigateHomeResumingSession() {
            // Mirrors popToHomeOrReplaceLogin: keep an existing Home below
            // Login instead of replacing it.
            navigateHomeSkippingLogin()
        }

        override fun clearDetailForSessionReplacement() {
            clearDetailCalls++
        }

        override fun navigateLoginReplacingHome() {
            observeDuringNavigation()
            popUpTo("home", inclusive = true)
            routes += "login"
        }

        override fun navigateHomeSkippingLogin() {
            val homeIndex = routes.indexOfLast { it == "home" }
            if (homeIndex >= 0) {
                while (routes.lastIndex > homeIndex) routes.removeLast()
            } else {
                popUpTo("login", inclusive = true)
                routes += "home"
            }
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

        /** NavController-style popUpTo: find the topmost match, drop it and everything above. */
        private fun popUpTo(route: String, inclusive: Boolean) {
            val index = routes.indexOfLast { it == route }
            if (index < 0) return
            val keepUpTo = if (inclusive) index else index + 1
            while (routes.lastIndex >= keepUpTo) routes.removeLast()
        }

        private fun observeDuringNavigation() {
            navigationObservationCount += 1
            payloadObservedDuringNavigation = observePayload()
        }
    }
}
