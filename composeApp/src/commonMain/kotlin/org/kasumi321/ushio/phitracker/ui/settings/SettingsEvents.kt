package org.kasumi321.ushio.phitracker.ui.settings

import kotlinx.coroutines.flow.update
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState

fun SettingsViewModel.dismissUpdateResult() = mutableUiState.update { it.copy(updateCheckState = UpdateCheckState.Idle) }
fun SettingsViewModel.dismissUpdateDataError() = mutableUiState.update { it.copy(updateDataError = null) }

/**
 * Logout routes through SessionCoordinator: it cancels any in-flight refresh,
 * then clears the current account's data transactionally. The LoggedOut event
 * (which navigates to Login) is emitted only on success — a failed clear keeps
 * the user on Settings with the data intact instead of a half-logged-out state.
 */
fun SettingsViewModel.logout() {
    launchSetting {
        if (sessionCoordinator.logout().isSuccess) {
            eventChannel.send(SettingsEvent.LoggedOut)
        } else {
            eventChannel.send(SettingsEvent.LogoutFailed)
        }
    }
}
