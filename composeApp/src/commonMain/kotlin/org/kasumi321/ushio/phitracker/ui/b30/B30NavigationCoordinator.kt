package org.kasumi321.ushio.phitracker.ui.b30

internal interface B30NavigationGateway {
    fun navigateB30()
    fun navigateHomeReplacingLogin()
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

    fun openB30(value: B30ExportPayload) {
        payload = value
        gateway.navigateB30()
    }

    fun toolbarBack() {
        // Do not clear the payload here: the pop transition keeps this
        // destination composed (and on iOS it may even dispose and recreate
        // its composition) until it leaves the composition, and a recomposed
        // screen with a null payload would wrongly trigger payload recovery.
        // The payload is released by destinationDisposed() instead.
        gateway.popCurrent()
    }

    fun destinationDisposed() {
        clearPayload()
    }

    fun homeLogout() {
        clearPayload()
        gateway.navigateLoginReplacingHome()
    }

    fun settingsLogout() {
        clearPayload()
        gateway.navigateLoginReplacingHome()
    }

    fun loginSuccess() {
        clearPayload()
        gateway.navigateHomeReplacingLogin()
    }

    fun skipLoginToHome() {
        // When Home sits below Login (guest opened Login from Profile/Tools)
        // just pop back; otherwise Login is the root and Home must be pushed.
        if (!gateway.popToHome()) {
            gateway.navigateHomeSkippingLogin()
        }
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
    }
}
