package org.kasumi321.ushio.phitracker.domain.session

import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.UserProfile

/**
 * TapTap rejected the stored sessionToken (HTTP 401/403): it was reset or
 * revoked server-side, and the user must log in again. Local account data is
 * unaffected — a fresh login with the same TapTap account resolves to the same
 * objectId and inherits it.
 */
class SessionTokenRejectedException(message: String = "session token rejected") : Exception(message)

/** No stored session token / active account; the caller must route to login. */
class SessionNotLoggedInException : Exception("no active session")

/** The secure store dropped the credential write; the login attempt failed. */
class SessionCredentialsPersistenceException : Exception("failed to persist session credentials")

data class SessionEstablished(
    val profile: UserProfile,
    val syncResult: SyncSaveResult
)

sealed interface SessionResumeOutcome {
    /**
     * The session is usable. [profile] is present whenever it could be read
     * (online paths); LOCAL means offline recovery backed by the account's own
     * cached data.
     */
    data class Resumed(
        val profile: UserProfile?,
        val source: Source,
        val syncResult: SyncSaveResult?
    ) : SessionResumeOutcome {
        enum class Source { ONLINE, LOCAL }
    }

    /** [hadToken] tells the login page whether to explain the fallback. */
    data class RequiresLogin(val hadToken: Boolean) : SessionResumeOutcome
}

/**
 * Single orchestration boundary for every session mutation — token login,
 * QR login (post exchange), startup resume, save refresh and logout — so the
 * persistence orderings they rely on cannot interleave:
 *
 * - login validates the token and syncs BEFORE anything is persisted; on
 *   failure nothing is written (no "new token + old cache" state);
 * - the sync mode is owner-scoped: an account with local records resumes with
 *   Refresh (score history continues across sessionToken resets), a new
 *   account bootstraps;
 * - logout cancels any in-flight operation first, then clears the current
 *   account's data transactionally;
 * - commits verify the current-account pointer is still what the operation
 *   started with, so a cancelled operation's late network result never
 *   writes back over a newer session state.
 *
 * ViewModels send intents here; they must not order persistence themselves.
 */
interface SessionCoordinator {
    suspend fun login(sessionToken: String, server: Server): Result<SessionEstablished>

    suspend fun resumeSession(): SessionResumeOutcome

    suspend fun refresh(): Result<SyncSaveResult>

    suspend fun logout(): Result<Unit>
}
