package org.kasumi321.ushio.phitracker.ui.settings

import kotlinx.coroutines.flow.update
import org.kasumi321.ushio.phitracker.domain.session.SessionLogoutOutcome
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.login_error_reset_failed

fun SettingsViewModel.dismissUpdateResult() = mutableUiState.update { it.copy(updateCheckState = UpdateCheckState.Idle) }
fun SettingsViewModel.dismissUpdateDataError() {
    acknowledgeCatalogRepairResult()
    mutableUiState.update { it.copy(updateDataError = null, updateDataBusyMessage = null) }
}

/**
 * Logout routes through SessionCoordinator: it cancels any in-flight refresh,
 * then clears the current account's data transactionally. The LoggedOut event
 * (which navigates to Login) is emitted only on success — a failed clear keeps
 * the user on Settings with the data intact instead of a half-logged-out state.
 */
fun SettingsViewModel.logout() {
    launchSetting {
        sessionCoordinator.logout().fold(
            onSuccess = { outcome ->
                eventChannel.send(
                    when (outcome) {
                        SessionLogoutOutcome.Completed -> SettingsEvent.LoggedOut
                        SessionLogoutOutcome.CredentialsCleanupPending ->
                            SettingsEvent.LoggedOutCredentialsPending
                    }
                )
            },
            onFailure = { eventChannel.send(SettingsEvent.LogoutFailed) }
        )
    }
}

fun SettingsViewModel.resetLocalSession() {
    launchSetting {
        mutableUiState.update {
            it.copy(isResettingLocalSession = true, localSessionResetMessage = null)
        }
        val result = sessionCoordinator.resetLocalSession()
        mutableUiState.update {
            it.copy(
                isResettingLocalSession = false,
                localSessionResetMessage = if (result.isFailure) {
                    UiText.Res(Res.string.login_error_reset_failed)
                } else {
                    null
                }
            )
        }
        if (result.isSuccess) eventChannel.send(SettingsEvent.LocalSessionReset)
    }
}
