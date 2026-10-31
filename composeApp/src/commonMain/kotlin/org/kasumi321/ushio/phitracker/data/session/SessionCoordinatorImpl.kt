package org.kasumi321.ushio.phitracker.data.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.session.SessionCleanupFailure
import org.kasumi321.ushio.phitracker.domain.session.SessionCleanupRequiredException
import org.kasumi321.ushio.phitracker.domain.session.SessionCoordinator
import org.kasumi321.ushio.phitracker.domain.session.SessionCredentialsPersistenceException
import org.kasumi321.ushio.phitracker.domain.session.SessionEstablished
import org.kasumi321.ushio.phitracker.domain.session.SessionLogoutOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionNotLoggedInException
import org.kasumi321.ushio.phitracker.domain.session.SessionRecoveryUnresolvedException
import org.kasumi321.ushio.phitracker.domain.session.SessionResetOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionResumeOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionTokenRejectedException
import kotlin.coroutines.coroutineContext

class SessionCoordinatorImpl(
    private val repository: PhigrosRepository,
    private val stateStore: SessionStateStore
) : SessionCoordinator {

    private val mutex = Mutex()
    private var activeOperation: Job? = null

    override fun hasPendingRecovery(): Boolean = stateStore.hasPendingRecovery

    private suspend fun <T> runSessionOperation(block: suspend () -> T): T = mutex.withLock {
        val job = coroutineContext.job
        activeOperation = job
        try {
            block()
        } finally {
            if (activeOperation === job) activeOperation = null
        }
    }

    override suspend fun login(sessionToken: String, server: Server): Result<SessionEstablished> =
        runSessionOperation {
            // A legacy recovery intent that could not be imported or proven
            // stale may describe the previous account. Establishing a new
            // session now would let a later startup replay it against the new
            // account, so login is refused until the intent is resolved.
            stateStore.retryUnresolvedLegacyRecovery()
            if (stateStore.hasUnresolvedLegacyRecovery) {
                AppLogger.event("session", "login_blocked_legacy_recovery")
                return@runSessionOperation Result.failure(SessionRecoveryUnresolvedException())
            }
            var targetOwner: String? = null
            var syncCommitted = false
            var established = false
            try {
                if (completePendingReset() || completePendingLogout()) {
                    val failure = if (repository.getCurrentAccountOwner() != null) {
                        SessionCleanupFailure.PreviousAccountData
                    } else {
                        SessionCleanupFailure.PreviousCredentials
                    }
                    return@runSessionOperation Result.failure(SessionCleanupRequiredException(failure))
                }
                recoverPendingLogin()
                val profile = repository.validateToken(sessionToken, server).getOrThrow()
                targetOwner = profile.playerId
                stateStore.beginLogin(profile.playerId).getOrThrow()
                val mode = if (repository.hasLocalRecords(profile.playerId)) SyncMode.Refresh else SyncMode.Bootstrap
                val expectedOwner = repository.getCurrentAccountOwner()
                val syncResult = repository.syncSave(sessionToken, server, mode, expectedOwner).getOrThrow()
                syncCommitted = true
                coroutineContext.ensureActive()
                withContext(NonCancellable) {
                    try {
                        repository.saveSessionToken(sessionToken, server)
                        repository.saveBoundPlayerId(profile.playerId)
                        check(repository.getSessionToken()?.first == sessionToken)
                        check(repository.getBoundPlayerId() == profile.playerId)
                        stateStore.setLoginRequired(false).getOrThrow()
                    } catch (e: SessionCredentialsPersistenceException) {
                        throw e
                    } catch (e: Exception) {
                        throw SessionCredentialsPersistenceException()
                    }
                    try {
                        stateStore.setLogoutPending(false).getOrThrow()
                        stateStore.setResetPending(false).getOrThrow()
                    } catch (e: Exception) {
                        throw SessionCleanupRequiredException(SessionCleanupFailure.LoginFinalization)
                    }
                    established = true
                    stateStore.clearLogin().onFailure {
                        AppLogger.w("Session") { "completed login marker will be reconciled on next resume" }
                    }
                }
                AppLogger.event(
                    "session", "login_succeeded",
                    mapOf("mode" to mode.name, "inherited" to (mode == SyncMode.Refresh).toString())
                )
                Result.success(SessionEstablished(profile = profile, syncResult = syncResult))
            } catch (failure: Throwable) {
                withContext(NonCancellable) {
                    if (!established) {
                        val owner = targetOwner
                        if (owner != null &&
                            repository.getCurrentAccountOwner() == owner &&
                            (syncCommitted || !credentialsMatch(owner))
                        ) {
                            repository.deactivateCurrentAccount()
                        }
                        stateStore.clearLogin()
                    }
                }
                if (failure is SessionTokenRejectedException) invalidateRejectedSession()
                if (failure is CancellationException) throw failure
                AppLogger.event("session", "login_failed", mapOf("error" to (failure.message ?: "unknown")))
                Result.failure(failure)
            }
        }

    override suspend fun resumeSession(): SessionResumeOutcome = runSessionOperation {
        stateStore.retryUnresolvedLegacyRecovery()
        val hadTokenBeforeRecovery = repository.getSessionToken() != null
        if (stateStore.hasUnresolvedLegacyRecovery) {
            // Do not compensate or resume anything: the unimported legacy
            // intent may belong to the previous account and must be resolved
            // on a later startup or discarded by an explicit reset first.
            AppLogger.event("session", "resume_blocked_legacy_recovery")
            return@runSessionOperation SessionResumeOutcome.RequiresLogin(
                hadToken = hadTokenBeforeRecovery,
                localResetPending = stateStore.isResetPending,
                recoveryUnresolved = true
            )
        }
        val recoveryRequiresLogin = runCatching { recoverForResume() }.getOrDefault(true)
        if (recoveryRequiresLogin) {
            return@runSessionOperation SessionResumeOutcome.RequiresLogin(
                hadToken = hadTokenBeforeRecovery,
                credentialCleanupPending = stateStore.isLogoutPending,
                localResetPending = stateStore.isResetPending
            )
        }
        val saved = repository.getSessionToken()
            ?: return@runSessionOperation SessionResumeOutcome.RequiresLogin(hadToken = false)
        val (token, server) = saved
        val pointer = repository.getCurrentAccountOwner()
        val boundPlayerId = repository.getBoundPlayerId()
        val bindingConsistent = boundPlayerId != null && boundPlayerId == pointer
        val hasLocalData = pointer != null && repository.hasLocalRecords(pointer)

        val validate = repository.validateToken(token, server)
        val profile = validate.getOrNull()
        if (profile != null) {
            val mode = if (repository.hasLocalRecords(profile.playerId)) SyncMode.Refresh else SyncMode.Bootstrap
            val sync = repository.syncSave(token, server, mode, pointer)
            if (sync.isSuccess) {
                val bindingPersisted = runCatching {
                    repository.saveBoundPlayerId(profile.playerId)
                    check(repository.getBoundPlayerId() == profile.playerId)
                }.isSuccess
                if (!bindingPersisted) {
                    repository.deactivateCurrentAccount()
                    return@runSessionOperation SessionResumeOutcome.RequiresLogin(hadToken = true)
                }
                AppLogger.event("session", "resume_succeeded", mapOf("source" to "online", "mode" to mode.name))
                return@runSessionOperation SessionResumeOutcome.Resumed(
                    profile = profile,
                    source = SessionResumeOutcome.Resumed.Source.ONLINE,
                    syncResult = sync.getOrThrow()
                )
            }
            if (sync.exceptionOrNull() is SessionTokenRejectedException) {
                return@runSessionOperation rejectedResume()
            }
        } else if (validate.exceptionOrNull() is SessionTokenRejectedException) {
            return@runSessionOperation rejectedResume()
        }

        if (bindingConsistent && hasLocalData) {
            AppLogger.event("session", "resume_succeeded", mapOf("source" to "local"))
            return@runSessionOperation SessionResumeOutcome.Resumed(
                profile = null,
                source = SessionResumeOutcome.Resumed.Source.LOCAL,
                syncResult = null
            )
        }
        AppLogger.event("session", "resume_requires_login")
        SessionResumeOutcome.RequiresLogin(hadToken = true)
    }

    override suspend fun refresh(): Result<SyncSaveResult> = runSessionOperation {
        stateStore.retryUnresolvedLegacyRecovery()
        if (stateStore.hasUnresolvedLegacyRecovery) {
            return@runSessionOperation Result.failure(SessionNotLoggedInException())
        }
        if (stateStore.isResetPending || stateStore.isLogoutPending || stateStore.isLoginRequired) {
            return@runSessionOperation Result.failure(SessionNotLoggedInException())
        }
        recoverPendingLogin()
        val saved = repository.getSessionToken()
            ?: return@runSessionOperation Result.failure(SessionNotLoggedInException())
        val (token, server) = saved
        val owner = repository.getCurrentAccountOwner()
            ?: return@runSessionOperation Result.failure(SessionNotLoggedInException())
        when (val binding = repository.getBoundPlayerId()) {
            null -> {
                val validation = repository.validateToken(token, server)
                val profile = validation.getOrNull()
                if (profile == null) {
                    if (validation.exceptionOrNull() is SessionTokenRejectedException) {
                        invalidateRejectedSession()
                    }
                    return@runSessionOperation Result.failure(
                        validation.exceptionOrNull() ?: SessionNotLoggedInException()
                    )
                }
                if (profile.playerId != owner) {
                    invalidateRejectedSession()
                    return@runSessionOperation Result.failure(SessionNotLoggedInException())
                }
            }
            owner -> Unit
            else -> {
                invalidateRejectedSession()
                return@runSessionOperation Result.failure(SessionNotLoggedInException())
            }
        }
        val result = repository.syncSave(token, server, SyncMode.Refresh, owner)
        if (result.exceptionOrNull() is SessionTokenRejectedException) {
            invalidateRejectedSession()
            return@runSessionOperation result
        }
        if (result.isSuccess && repository.getBoundPlayerId() != owner) {
            val bindingPersisted = runCatching {
                repository.saveBoundPlayerId(owner)
                check(repository.getBoundPlayerId() == owner)
            }.isSuccess
            if (!bindingPersisted) {
                repository.deactivateCurrentAccount()
                return@runSessionOperation Result.failure(SessionCredentialsPersistenceException())
            }
        }
        result
    }

    override suspend fun logout(): Result<SessionLogoutOutcome> {
        activeOperation?.cancelAndJoin()
        return runSessionOperation {
            withContext(NonCancellable) {
                try {
                    // Logout discards the session the legacy intents describe,
                    // so their unresolved copy must go before any new session
                    // can be established. A failed cleanup keeps logout failed.
                    stateStore.discardUnresolvedLegacyRecovery().getOrThrow()
                    stateStore.setLogoutPending(true).getOrThrow()
                    stateStore.setResetPending(false).getOrThrow()
                    repository.clearCurrentAccountRows().getOrThrow()
                } catch (failure: Throwable) {
                    AppLogger.event("session", "logout_failed", mapOf("error" to (failure.message ?: "unknown")))
                    return@withContext Result.failure(failure)
                }

                val credentialsCleared = runCatching { repository.clearTokenSync() }.isSuccess
                val intentCleared = credentialsCleared && stateStore.setLogoutPending(false).isSuccess
                val outcome = if (credentialsCleared && intentCleared) {
                    SessionLogoutOutcome.Completed
                } else {
                    SessionLogoutOutcome.CredentialsCleanupPending
                }
                AppLogger.event("session", "logout_succeeded", mapOf("credentialsCleared" to credentialsCleared.toString()))
                Result.success(outcome)
            }
        }
    }

    override suspend fun resetLocalSession(): Result<Unit> = runSessionOperation {
        withContext(NonCancellable) {
            runCatching {
                // Reset supersedes an older logout without performing the
                // logout's destructive row cleanup. Persist both decisions
                // before hiding the pointer so every interruption is recoverable.
                stateStore.setResetPending(true).getOrThrow()
                stateStore.discardUnresolvedLegacyRecovery().getOrThrow()
                stateStore.setLogoutPending(false).getOrThrow()
                check(!completePendingReset()) { "local session reset is still pending" }
            }
        }
    }

    override suspend fun resetSessionToken(): SessionResetOutcome = runSessionOperation {
        stateStore.retryUnresolvedLegacyRecovery()
        if (stateStore.hasUnresolvedLegacyRecovery) {
            return@runSessionOperation SessionResetOutcome.NotLoggedIn
        }
        if (stateStore.isResetPending || stateStore.isLogoutPending || stateStore.isLoginRequired) {
            return@runSessionOperation SessionResetOutcome.NotLoggedIn
        }
        val saved = repository.getSessionToken()
            ?: return@runSessionOperation SessionResetOutcome.NotLoggedIn
        val (token, server) = saved
        val owner = repository.getCurrentAccountOwner()
            ?: return@runSessionOperation SessionResetOutcome.NotLoggedIn

        val result = repository.resetSessionToken(token, server, owner)
        val newToken = result.getOrNull()
        if (newToken == null) {
            return@runSessionOperation when (result.exceptionOrNull()) {
                is SessionTokenRejectedException -> {
                    invalidateRejectedSession()
                    AppLogger.event("session", "session_token_reset", mapOf("result" to "rejected"))
                    SessionResetOutcome.Rejected
                }
                else -> {
                    AppLogger.event("session", "session_token_reset", mapOf("result" to "unknown"))
                    SessionResetOutcome.OutcomeUnknown
                }
            }
        }

        val persisted = runCatching {
            repository.saveSessionToken(newToken, server)
            check(repository.getSessionToken()?.first == newToken)
        }.isSuccess
        if (!persisted) {
            AppLogger.event("session", "session_token_reset", mapOf("result" to "persist_failed"))
            return@runSessionOperation SessionResetOutcome.OutcomeUnknown
        }
        AppLogger.event("session", "session_token_reset", mapOf("result" to "success"))
        SessionResetOutcome.Success(owner)
    }

    private suspend fun recoverForResume(): Boolean {
        if (stateStore.isResetPending) {
            completePendingReset()
            return true
        }
        if (completePendingLogout()) return true
        if (stateStore.isLoginRequired) {
            repository.deactivateCurrentAccount()
            stateStore.clearLogin()
            return true
        }
        recoverPendingLogin()
        return false
    }

    /** Returns true while reset recovery still prevents a new login. */
    private suspend fun completePendingReset(): Boolean {
        if (!stateStore.isResetPending) return false
        repository.deactivateCurrentAccount()
        stateStore.clearLogin().getOrThrow()
        stateStore.setLoginRequired(true).getOrThrow()
        val credentialsCleared = runCatching {
            try {
                repository.clearTokenSync()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                repository.resetCredentialStorage().getOrThrow()
                AppLogger.event("session", "credential_storage_reset")
            }
        }.also { result ->
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        }.isSuccess
        if (!credentialsCleared) return true
        // Clearing reset first would let a stale logout delete the preserved
        // rows after a process death, so logout is always invalidated first.
        if (!stateStore.setLogoutPending(false).isSuccess) return true
        return !stateStore.setResetPending(false).isSuccess
    }

    private suspend fun completePendingLogout(): Boolean {
        if (!stateStore.isLogoutPending) return false
        val cleared = runCatching {
            repository.clearCurrentAccountRows().getOrThrow()
            repository.clearTokenSync()
        }.isSuccess
        if (!cleared) return true
        return !stateStore.setLogoutPending(false).isSuccess
    }

    private suspend fun rejectedResume(): SessionResumeOutcome.RequiresLogin {
        val invalidated = invalidateRejectedSession()
        AppLogger.event("session", "resume_rejected")
        return SessionResumeOutcome.RequiresLogin(
            hadToken = true,
            credentialCleanupPending = !invalidated
        )
    }

    private suspend fun invalidateRejectedSession(): Boolean = withContext(NonCancellable) {
        val markerPersisted = stateStore.setLoginRequired(true).isSuccess
        val pointerDeactivated = runCatching { repository.deactivateCurrentAccount() }.isSuccess
        val credentialsCleared = runCatching { repository.clearTokenSync() }.isSuccess
        markerPersisted && pointerDeactivated && credentialsCleared
    }

    private suspend fun recoverPendingLogin() {
        val pendingOwner = stateStore.pendingLoginOwner ?: return
        val complete = repository.getCurrentAccountOwner() == pendingOwner &&
            repository.hasLocalRecords(pendingOwner) &&
            credentialsMatch(pendingOwner)
        if (!complete && repository.getCurrentAccountOwner() == pendingOwner) {
            repository.deactivateCurrentAccount()
        }
        stateStore.clearLogin()
    }

    private suspend fun credentialsMatch(owner: String): Boolean =
        repository.getSessionToken() != null && repository.getBoundPlayerId() == owner
}
