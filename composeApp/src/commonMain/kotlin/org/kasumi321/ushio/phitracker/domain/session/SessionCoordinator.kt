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
class SessionCredentialsPersistenceException(
    val storageResetRequired: Boolean = false
) : Exception("failed to persist session credentials")

enum class SessionCleanupFailure {
    PreviousAccountData,
    PreviousCredentials,
    LoginFinalization
}

class SessionCleanupRequiredException(
    val failure: SessionCleanupFailure = SessionCleanupFailure.PreviousCredentials
) : Exception("previous session cleanup is incomplete")

/**
 * A pre-Keychain session recovery intent could not be imported or proven
 * stale, so no session may be established: replaying the old intent after a
 * login would erase the new account's data. The block clears on a later
 * startup once the secure store answers, or when the user explicitly resets
 * the local session.
 */
class SessionRecoveryUnresolvedException : Exception("legacy session recovery state is unresolved")

/**
 * Result of a sessionToken reset request. The rotation is a revocation first:
 * once TapTap accepts the request the OLD credential is dead even if we never
 * learn the new one, so a network failure mid-flight leaves an uncertain state
 * that must be reported honestly instead of masked as success or failure.
 */
sealed interface SessionResetOutcome {
    /** Rotation confirmed and the new token is stored; local data untouched (objectId never rotates). */
    data class Success(val playerId: String) : SessionResetOutcome

    /** The request failed before a definitive answer: the server may or may not have rotated the token. */
    data object OutcomeUnknown : SessionResetOutcome

    /** TapTap rejected the current token (already reset/revoked elsewhere): a fresh login is needed. */
    data object Rejected : SessionResetOutcome

    /** No stored session: nothing to rotate. */
    data object NotLoggedIn : SessionResetOutcome
}

data class SessionEstablished(
    val profile: UserProfile,
    val syncResult: SyncSaveResult
)

sealed interface SessionLogoutOutcome {
    data object Completed : SessionLogoutOutcome
    data object CredentialsCleanupPending : SessionLogoutOutcome
}

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
    data class RequiresLogin(
        val hadToken: Boolean,
        val credentialCleanupPending: Boolean = false,
        val localResetPending: Boolean = false,
        val recoveryUnresolved: Boolean = false
    ) : SessionResumeOutcome
}

/**
 * Single orchestration boundary for every session mutation — token login,
 * QR login (post exchange), startup resume, save refresh and logout — so the
 * persistence orderings they rely on cannot interleave:
 *
 * - login records a durable pending owner before sync; credentials are
 *   persisted in a short non-cancellable section and incomplete activation is
 *   compensated on cancellation or the next resume;
 * - the sync mode is owner-scoped: an account with local records resumes with
 *   Refresh (score history continues across sessionToken resets), a new
 *   account bootstraps;
 * - logout records a durable intent before clearing rows and verifies token,
 *   server and binding removal; a residual intent blocks automatic resume;
 * - offline resume requires a non-null binding matching current_account;
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

    suspend fun logout(): Result<SessionLogoutOutcome>

    fun hasPendingRecovery(): Boolean = false

    suspend fun resetLocalSession(): Result<Unit> =
        Result.failure(UnsupportedOperationException("local session reset is not supported"))

    /**
     * Rotates the sessionToken of the CURRENT account. On success the new
     * credential replaces the old one in secure storage while the account
     * binding and every local row stay — objectIds survive token resets, so
     * the inherited data simply continues under the new credential.
     */
    suspend fun resetSessionToken(): SessionResetOutcome
}
