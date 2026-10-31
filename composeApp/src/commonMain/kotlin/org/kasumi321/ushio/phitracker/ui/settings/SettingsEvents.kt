package org.kasumi321.ushio.phitracker.ui.settings

import kotlinx.coroutines.flow.update
import org.kasumi321.ushio.phitracker.domain.session.SessionLogoutOutcome
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.settings_reset_token_result_rejected
import phitracker.composeapp.generated.resources.settings_reset_token_result_success
import phitracker.composeapp.generated.resources.settings_reset_token_result_unknown
import phitracker.composeapp.generated.resources.login_error_reset_failed
import phitracker.composeapp.generated.resources.sync_login_required

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

/**
 * Rotates the current account's sessionToken via SessionCoordinator. The
 * result distinguishes a confirmed rotation (data inherited unchanged), an
 * uncertain network outcome (the old credential may already be dead) and a
 * rejection (a fresh login is required) — each maps to its own message.
 */
fun SettingsViewModel.resetSessionToken() {
    launchSetting {
        mutableUiState.update { it.copy(isResettingSessionToken = true, sessionTokenResetMessage = null) }
        val message = when (val outcome = sessionCoordinator.resetSessionToken()) {
            is org.kasumi321.ushio.phitracker.domain.session.SessionResetOutcome.Success ->
                UiText.Res(Res.string.settings_reset_token_result_success)

            org.kasumi321.ushio.phitracker.domain.session.SessionResetOutcome.OutcomeUnknown ->
                UiText.Res(Res.string.settings_reset_token_result_unknown)

            org.kasumi321.ushio.phitracker.domain.session.SessionResetOutcome.Rejected ->
                UiText.Res(Res.string.settings_reset_token_result_rejected)

            org.kasumi321.ushio.phitracker.domain.session.SessionResetOutcome.NotLoggedIn ->
                UiText.Res(Res.string.sync_login_required)
        }
        mutableUiState.update {
            it.copy(isResettingSessionToken = false, sessionTokenResetMessage = message)
        }
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

fun SettingsViewModel.dismissSessionTokenResetMessage() =
    mutableUiState.update { it.copy(sessionTokenResetMessage = null) }
