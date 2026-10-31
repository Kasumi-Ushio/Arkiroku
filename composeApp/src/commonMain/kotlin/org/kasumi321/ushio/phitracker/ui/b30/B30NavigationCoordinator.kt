package org.kasumi321.ushio.phitracker.ui.b30

import androidx.lifecycle.SavedStateHandle

internal interface B30NavigationGateway {
    fun navigateB30()
    fun navigateHomeReplacingLogin()
    fun navigateHomeResumingSession()
    fun clearDetailForSessionReplacement()
    fun navigateLoginReplacingHome()
    fun navigateHomeSkippingLogin()
    fun popCurrent()
    fun popToHome(): Boolean

    /** Pops a detail-pane-hosted B30 image back to the pane placeholder.
     * Returns false when no detail pane is active (compact layout). */
    fun popDetailToPlaceholder(): Boolean
    fun navigateLoginClearingGraph()
}

internal class B30NavigationCoordinator(
    private val gateway: B30NavigationGateway
) {
    var payload: B30ExportPayload? = null
        private set

    /**
     * Identity of the current payload delivery, bumped whenever [payload] is
     * published or released. Each B30 backStackEntry stores its first owner in
     * SavedStateHandle and hands that generation back when it disposes, so an
     * exit transition cannot release a newer entry's payload.
     *
     * The payload itself is memory-only: an Activity recreation or process
     * death cannot restore it, and the destination falls back to the existing
     * missing-payload recovery. This generation only orders in-process
     * disposals.
     */
    var payloadGeneration: Long = 0L
        private set

    fun openB30(value: B30ExportPayload) {
        payload = value
        payloadGeneration++
        gateway.navigateB30()
    }

    fun toolbarBack() {
        // Do not clear the payload here: the pop transition keeps this
        // destination composed (and on iOS it may even dispose and recreate
        // its composition) until it leaves the composition, and a recomposed
        // screen with a null payload would wrongly trigger payload recovery.
        // The payload is released by destinationDisposed(ownerGeneration)
        // instead.
        gateway.popCurrent()
    }

    /**
     * Releases the payload, but only for the entry that actually owns it: an
     * entry that is disposed after a newer openB30 (exit animation still
     * running, or the same entry recomposed for a new payload) must not clear
     * what the newer entry is showing.
     */
    fun destinationDisposed(ownerGeneration: Long) {
        if (ownerGeneration != payloadGeneration) return
        clearPayload()
    }

    fun settingsLogout() {
        clearPayload()
        gateway.navigateLoginReplacingHome()
    }

    fun loginSuccess() {
        clearPayload()
        gateway.navigateHomeReplacingLogin()
    }

    fun resumeSession() {
        gateway.navigateHomeResumingSession()
    }

    fun skipLoginToHome() {
        clearPayload()
        gateway.clearDetailForSessionReplacement()
        // The gateway is stack-aware: an existing Home below Login is popped
        // back to (the reset requested just above makes its restored nested
        // graph return to the placeholder), while a Login-rooted stack pushes
        // Home and drops Login.
        gateway.navigateHomeSkippingLogin()
    }

    fun recoverMissingPayload(): B30MissingPayloadRecovery {
        clearPayload()
        // In the expanded two-pane layout the B30 image lives in the detail
        // pane; recovering means returning that pane to its placeholder.
        if (gateway.popDetailToPlaceholder()) {
            return B30MissingPayloadRecovery.HomePopped
        }
        return if (gateway.popToHome()) {
            B30MissingPayloadRecovery.HomePopped
        } else {
            gateway.navigateLoginClearingGraph()
            B30MissingPayloadRecovery.NavigateLogin
        }
    }

    private fun clearPayload() {
        payload = null
        // Invalidate every previously captured generation: an old entry that
        // disposes after a session-level clear must not be mistaken for the
        // owner of a payload opened later.
        payloadGeneration++
    }
}

internal data class B30OwnedPayload(
    val payload: B30ExportPayload?,
    val generation: Long
)

internal fun B30NavigationCoordinator.claimPayload(
    savedStateHandle: SavedStateHandle
): B30OwnedPayload {
    val ownerGeneration = savedStateHandle.get<Long>(B30_PAYLOAD_GENERATION_KEY)
        ?: payloadGeneration.also { savedStateHandle[B30_PAYLOAD_GENERATION_KEY] = it }
    return B30OwnedPayload(
        payload = payload.takeIf { ownerGeneration == payloadGeneration },
        generation = ownerGeneration
    )
}

private const val B30_PAYLOAD_GENERATION_KEY = "b30.payload.generation"
