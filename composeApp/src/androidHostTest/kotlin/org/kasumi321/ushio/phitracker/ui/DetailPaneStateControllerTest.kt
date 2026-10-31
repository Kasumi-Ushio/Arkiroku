package org.kasumi321.ushio.phitracker.ui

import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DetailPaneStateControllerTest {
    @Test
    fun staleOwnerCannotReleaseTheNewController() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val oldController = NavHostController(context)
        val newController = NavHostController(context)
        val state = DetailPaneState()

        state.attachController(oldController)
        state.attachController(newController)
        state.releaseController(oldController)
        assertSame(newController, state.controller)

        state.releaseController(newController)
        assertNull(state.controller)
    }

    @Test
    fun sessionReplacementDropsControllerAndPendingRoute() {
        val state = DetailPaneState(DetailRouteRequest.Plain(Screen.Settings.route))
        state.attachController(NavHostController(ApplicationProvider.getApplicationContext()))

        state.clearForSessionReplacement()

        assertNull(state.controller)
        assertNull(state.consumePendingRoute())
    }

    @Test
    fun staleGenerationCannotAttachOrWriteRoutesAfterSessionReplacement() {
        val state = DetailPaneState(DetailRouteRequest.Plain(Screen.Settings.route))
        val staleGeneration = state.generation
        val staleController = NavHostController(ApplicationProvider.getApplicationContext())
        state.clearForSessionReplacement()

        state.attachController(staleController, staleGeneration)
        state.setPendingRoute(DetailRouteRequest.Plain(Screen.About.route), staleGeneration)

        assertNull(state.controller)
        assertNull(state.consumePendingRoute())
        kotlin.test.assertEquals(staleGeneration + 1, state.generation)

        state.attachController(staleController)
        assertSame(staleController, state.controller)
    }
}
