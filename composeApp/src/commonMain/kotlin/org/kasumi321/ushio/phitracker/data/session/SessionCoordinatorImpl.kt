package org.kasumi321.ushio.phitracker.data.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.session.SessionCoordinator
import org.kasumi321.ushio.phitracker.domain.session.SessionCredentialsPersistenceException
import org.kasumi321.ushio.phitracker.domain.session.SessionEstablished
import org.kasumi321.ushio.phitracker.domain.session.SessionNotLoggedInException
import org.kasumi321.ushio.phitracker.domain.session.SessionResumeOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionTokenRejectedException
import kotlin.coroutines.coroutineContext

class SessionCoordinatorImpl(
    private val repository: PhigrosRepository
) : SessionCoordinator {

    private val mutex = Mutex()

    /**
     * The coroutine of the session operation currently holding [mutex].
     * Logout cancels (and joins) it before clearing data, so an in-flight
     * refresh can never commit over a finished logout.
     */
    private var activeOperation: Job? = null

    private suspend fun <T> runSessionOperation(block: suspend () -> T): T = mutex.withLock {
        val job = coroutineContext.job
        activeOperation = job
        try {
            block()
        } finally {
            if (activeOperation === job) activeOperation = null
        }
    }

    /**
     * Login validates first and syncs BEFORE anything is persisted, so a
     * failed login leaves the previous session state untouched — a "new token
     * + old cache" hybrid can never be written.
     *
     * Inheritance: the sync mode follows the validated account's own local
     * footprint. An account that already has score rows on this device
     * resumes with Refresh (score history continues across sessionToken
     * resets, since objectIds never rotate); a first-time account bootstraps.
     */
    override suspend fun login(sessionToken: String, server: Server): Result<SessionEstablished> =
        runSessionOperation {
            val validate = repository.validateToken(sessionToken, server)
            val profile = validate.getOrNull()
                ?: return@runSessionOperation Result.failure(
                    validate.exceptionOrNull() ?: IllegalStateException("token validation failed")
                )

            val mode = if (repository.hasLocalRecords(profile.playerId)) SyncMode.Refresh else SyncMode.Bootstrap
            val expectedOwner = repository.getCurrentAccountOwner()
            val syncResult = repository.syncSave(sessionToken, server, mode, expectedOwner)
                .getOrElse { failure ->
                    if (failure !is CancellationException) {
                        AppLogger.event("session", "login_failed", mapOf("error" to (failure.message ?: "unknown")))
                    }
                    return@runSessionOperation Result.failure(failure)
                }
            val persistFailure = persistCredentials(sessionToken, server, profile.playerId)
            if (persistFailure != null) {
                return@runSessionOperation Result.failure(persistFailure)
            }
            AppLogger.event(
                "session", "login_succeeded",
                mapOf("mode" to mode.name, "inherited" to (mode == SyncMode.Refresh).toString())
            )
            Result.success(SessionEstablished(profile = profile, syncResult = syncResult))
        }

    override suspend fun resumeSession(): SessionResumeOutcome = runSessionOperation {
        val saved = repository.getSessionToken()
            ?: return@runSessionOperation SessionResumeOutcome.RequiresLogin(hadToken = false)
        val (token, server) = saved
        val pointer = repository.getCurrentAccountOwner()
        val boundPlayerId = repository.getBoundPlayerId()
        // Offline trust requires proof the cached data belongs to THIS token's
        // account: the stored binding (written after the sync that produced the
        // cache) must match the pointer. Installs upgraded from pre-binding
        // versions have no binding yet and keep the historical trust-once
        // behavior; the first successful online sync writes the binding.
        val bindingConsistent = boundPlayerId == null || boundPlayerId == pointer
        val hasLocalData = pointer != null && repository.hasLocalRecords(pointer)

        val validate = repository.validateToken(token, server)
        val profile = validate.getOrNull()
        if (profile != null) {
            val mode = if (repository.hasLocalRecords(profile.playerId)) SyncMode.Refresh else SyncMode.Bootstrap
            val sync = repository.syncSave(token, server, mode, pointer)
            if (sync.isSuccess) {
                runCatching { repository.saveBoundPlayerId(profile.playerId) }
                AppLogger.event(
                    "session", "resume_succeeded",
                    mapOf("source" to "online", "mode" to mode.name)
                )
                return@runSessionOperation SessionResumeOutcome.Resumed(
                    profile = profile,
                    source = SessionResumeOutcome.Resumed.Source.ONLINE,
                    syncResult = sync.getOrThrow()
                )
            }
            // Validation passed but the save sync failed (e.g. flaky network):
            // fall through and keep the local session when it is trustworthy.
        } else if (validate.exceptionOrNull() is SessionTokenRejectedException) {
            // Reset or revoked server-side: local data stays for the inherited
            // re-login, but this token must not keep the session alive.
            AppLogger.event("session", "resume_rejected")
            return@runSessionOperation SessionResumeOutcome.RequiresLogin(hadToken = true)
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
        val saved = repository.getSessionToken()
            ?: return@runSessionOperation Result.failure(SessionNotLoggedInException())
        val (token, server) = saved
        val owner = repository.getCurrentAccountOwner()
            ?: return@runSessionOperation Result.failure(SessionNotLoggedInException())
        repository.syncSave(token, server, SyncMode.Refresh, owner)
    }

    override suspend fun logout(): Result<Unit> {
        activeOperation?.cancelAndJoin()
        return runSessionOperation { repository.clearCurrentAccountData() }
            .onSuccess { AppLogger.event("session", "logout_succeeded") }
            .onFailure { AppLogger.event("session", "logout_failed", mapOf("error" to (it.message ?: "unknown"))) }
    }

    /**
     * Persists token + owner binding after a successful commit and verifies
     * the write landed (a degraded secure store may silently drop it). On
     * failure the just-committed account rows are removed again — otherwise a
     * pointer would name an account whose token nobody holds.
     */
    private suspend fun persistCredentials(token: String, server: Server, playerId: String): Exception? {
        return try {
            repository.saveSessionToken(token, server)
            repository.saveBoundPlayerId(playerId)
            val stored = repository.getSessionToken()
            if (stored?.first != token) {
                runCatching { repository.clearCurrentAccountData() }
                SessionCredentialsPersistenceException()
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { repository.clearCurrentAccountData() }
            e
        }
    }
}
