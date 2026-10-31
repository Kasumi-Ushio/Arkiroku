package org.kasumi321.ushio.phitracker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.QrLoginPollResult
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.repository.QrLoginRepository
import org.kasumi321.ushio.phitracker.domain.session.SessionCoordinator
import org.kasumi321.ushio.phitracker.domain.session.SessionCleanupFailure
import org.kasumi321.ushio.phitracker.domain.session.SessionCleanupRequiredException
import org.kasumi321.ushio.phitracker.domain.session.SessionCredentialsPersistenceException
import org.kasumi321.ushio.phitracker.domain.session.SessionRecoveryUnresolvedException
import org.kasumi321.ushio.phitracker.domain.session.SessionResumeOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionTokenRejectedException
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.login_error_credential_expired
import phitracker.composeapp.generated.resources.login_error_empty_token
import phitracker.composeapp.generated.resources.login_error_expired_no_save
import phitracker.composeapp.generated.resources.login_error_login_finalization
import phitracker.composeapp.generated.resources.login_error_persist_failed
import phitracker.composeapp.generated.resources.login_error_previous_credential_cleanup
import phitracker.composeapp.generated.resources.login_error_previous_data_cleanup
import phitracker.composeapp.generated.resources.login_error_recovery_unresolved
import phitracker.composeapp.generated.resources.login_error_reset_failed
import phitracker.composeapp.generated.resources.login_error_reset_pending
import phitracker.composeapp.generated.resources.login_error_storage_rebuild_required
import phitracker.composeapp.generated.resources.login_error_sync_failed
import phitracker.composeapp.generated.resources.login_qr_auth_failed
import phitracker.composeapp.generated.resources.login_qr_fetch_failed
import phitracker.composeapp.generated.resources.login_qr_status_failed
import phitracker.composeapp.generated.resources.login_qr_sync_failed
import phitracker.composeapp.generated.resources.settings_logout_credential_pending

/** QR 扫码状态 */
enum class QrStatus {
    Idle,
    Loading,
    WaitingScan,
    Scanned,
    Exchanging,
    Success,
    Error,
    Expired
}

data class LoginUiState(
    val token: String = "",
    val server: Server = Server.CN,
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val isLoggedIn: Boolean = false,
    val sessionRestored: Boolean = false,
    val isCheckingToken: Boolean = true,
    val qrCodeUrl: String? = null,
    val qrStatus: QrStatus = QrStatus.Idle,
    val qrError: UiText? = null,
    val qrRemainingSeconds: Int = 0
)

class LoginViewModel(
    private val sessionCoordinator: SessionCoordinator,
    private val qrLoginRepository: QrLoginRepository,
    private val clockMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var qrPollingJob: Job? = null

    init {
        checkExistingToken()
    }

    private fun checkExistingToken() {
        viewModelScope.launch {
            when (val outcome = sessionCoordinator.resumeSession()) {
                is SessionResumeOutcome.Resumed -> {
                    _uiState.update {
                        it.copy(
                            isCheckingToken = false,
                            isLoggedIn = true,
                            sessionRestored = true,
                            server = _uiState.value.server
                        )
                    }
                    AppLogger.event(
                        "login",
                        "state_checked",
                        mapOf("tokenPresent" to "true", "loggedIn" to "true", "source" to outcome.source.name.lowercase())
                    )
                }

                is SessionResumeOutcome.RequiresLogin -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isCheckingToken = false,
                            isLoggedIn = false,
                            sessionRestored = false,
                            error = when {
                                outcome.recoveryUnresolved -> UiText.Res(Res.string.login_error_recovery_unresolved)
                                outcome.localResetPending -> UiText.Res(Res.string.login_error_reset_pending)
                                outcome.credentialCleanupPending ->
                                    UiText.Res(Res.string.settings_logout_credential_pending)
                                outcome.hadToken -> UiText.Res(Res.string.login_error_expired_no_save)
                                else -> null
                            }
                        )
                    }
                    AppLogger.event(
                        "login",
                        "state_checked",
                        mapOf("tokenPresent" to outcome.hadToken.toString(), "loggedIn" to "false")
                    )
                }
            }
        }
    }

    fun updateToken(token: String) {
        _uiState.update { it.copy(token = token.trim(), error = null) }
    }

    fun updateServer(server: Server) {
        _uiState.update { it.copy(server = server, error = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.token.isBlank()) {
            _uiState.update { it.copy(error = UiText.Res(Res.string.login_error_empty_token)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, sessionRestored = false, error = null) }

            val result = sessionCoordinator.login(state.token, state.server)
            result.fold(
                onSuccess = {
                    _uiState.update { state0 ->
                        state0.copy(isLoading = false, isLoggedIn = true, sessionRestored = false)
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, error = mapLoginError(throwable))
                    }
                }
            )
        }
    }

    private fun mapLoginError(throwable: Throwable): UiText = when (throwable) {
        is SessionTokenRejectedException -> UiText.Res(Res.string.login_error_credential_expired)
        is SessionRecoveryUnresolvedException -> UiText.Res(Res.string.login_error_recovery_unresolved)
        is SessionCredentialsPersistenceException -> UiText.Res(
            if (throwable.storageResetRequired) {
                Res.string.login_error_storage_rebuild_required
            } else {
                Res.string.login_error_persist_failed
            }
        )
        is SessionCleanupRequiredException -> UiText.Res(
            when (throwable.failure) {
                SessionCleanupFailure.PreviousAccountData -> Res.string.login_error_previous_data_cleanup
                SessionCleanupFailure.PreviousCredentials -> Res.string.login_error_previous_credential_cleanup
                SessionCleanupFailure.LoginFinalization -> Res.string.login_error_login_finalization
            }
        )
        else -> UiText.Res(Res.string.login_error_sync_failed)
    }

    fun resetLocalSession() {
        cancelQrLogin()
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, isCheckingToken = true, sessionRestored = false, error = null)
            }
            val result = try {
                sessionCoordinator.resetLocalSession()
            } catch (failure: Throwable) {
                currentCoroutineContext().ensureActive()
                Result.failure(failure)
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isCheckingToken = false,
                    isLoggedIn = false,
                    sessionRestored = false,
                    error = if (result.isSuccess) {
                        null
                    } else {
                        UiText.Res(Res.string.login_error_reset_failed)
                    }
                )
            }
        }
    }

    fun startQrLogin() {
        cancelQrLogin()

        val server = _uiState.value.server
        _uiState.update {
            it.copy(qrStatus = QrStatus.Loading, qrError = null, qrCodeUrl = null)
        }

        qrPollingJob = viewModelScope.launch {
            var failureMessage = UiText.Res(Res.string.login_qr_fetch_failed)
            try {
                val challenge = qrLoginRepository.requestChallenge(server)
                val expiresIn = remainingSeconds(challenge.expiresAt)

                _uiState.update {
                    it.copy(
                        qrCodeUrl = challenge.qrUrl,
                        qrStatus = QrStatus.WaitingScan,
                        qrRemainingSeconds = expiresIn
                    )
                }

                while (clockMillis() < challenge.expiresAt) {
                    _uiState.update { it.copy(qrRemainingSeconds = remainingSeconds(challenge.expiresAt)) }

                    failureMessage = UiText.Res(Res.string.login_qr_status_failed)
                    when (val result = qrLoginRepository.poll(challenge.id)) {
                        is QrLoginPollResult.Authorized -> {
                            _uiState.update { it.copy(qrStatus = QrStatus.Exchanging) }
                            failureMessage = UiText.Res(Res.string.login_qr_auth_failed)
                            val sessionToken = qrLoginRepository.exchangeForSessionToken(result.authorizationId)

                            val loginResult = sessionCoordinator.login(sessionToken, server)
                            if (loginResult.isFailure) {
                                _uiState.update {
                                    it.copy(
                                        qrStatus = QrStatus.Error,
                                        qrError = loginResult.exceptionOrNull()?.let(::mapLoginError)
                                            ?: UiText.Res(Res.string.login_qr_sync_failed)
                                    )
                                }
                                return@launch
                            }

                            _uiState.update {
                                it.copy(
                                    qrStatus = QrStatus.Success,
                                    isLoggedIn = true,
                                    sessionRestored = false,
                                    token = sessionToken
                                )
                            }
                            return@launch
                        }
                        QrLoginPollResult.AuthorizationWaiting -> {
                            _uiState.update { it.copy(qrStatus = QrStatus.Scanned) }
                        }
                        QrLoginPollResult.Pending -> Unit
                    }

                    delay(2000)
                }

                _uiState.update { it.copy(qrStatus = QrStatus.Expired, qrRemainingSeconds = 0) }

            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update {
                    it.copy(
                        qrStatus = QrStatus.Error,
                        qrError = failureMessage
                    )
                }
            }
        }
    }

    fun cancelQrLogin() {
        qrPollingJob?.cancel()
        qrPollingJob = null
        _uiState.update {
            it.copy(qrStatus = QrStatus.Idle, qrCodeUrl = null, qrError = null)
        }
    }

    private fun remainingSeconds(expiresAt: Long): Int =
        (((expiresAt - clockMillis()).coerceAtLeast(0) + 999L) / 1_000L).toInt()

    fun clearError() {
        _uiState.update { it.copy(error = null, qrError = null) }
    }
}
