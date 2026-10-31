package org.kasumi321.ushio.phitracker.data.platform

enum class StorageWriteResult {
    Persisted,
    Ephemeral,
    Failed
}

interface SecureKeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String): StorageWriteResult
    fun remove(key: String): StorageWriteResult

    /** Rebuilds this store after an explicit user-confirmed recovery action. */
    fun resetStorage(): Result<Unit> = Result.failure(UnsupportedOperationException("secure storage reset is not supported"))
}

/**
 * Device-protected key-value storage for credentials (session token and
 * binding, Phi-Plugin api_token): iOS Keychain, Android
 * EncryptedSharedPreferences. Callers handling credentials must require
 * [StorageWriteResult.Persisted]; a readable in-memory fallback is not durable.
 */
expect fun createSecureKeyValueStorage(name: String): SecureKeyValueStorage

/**
 * Plain key-value storage for non-secret settings: NSUserDefaults on iOS,
 * SharedPreferences on Android. Restore-safe (no crypto keys involved).
 */
expect fun createPlainKeyValueStorage(name: String): SecureKeyValueStorage

/**
 * Durable storage for session recovery intents (pending login owner, logout
 * intent, login_required) that must survive process death to be compensated
 * on the next start.
 *
 * Android keeps the plain SharedPreferences file used by earlier builds. iOS
 * uses the `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` Keychain and
 * migrates values previously written to NSUserDefaults for the same [name],
 * removing the plaintext copies once the Keychain value is durable. Callers
 * handling these intents must require [StorageWriteResult.Persisted].
 *
 * A platform that imports intents from a legacy location may also expose
 * [LegacySessionMigrationSource] on the returned storage. Callers must then
 * fail closed while [LegacySessionMigration.Unresolved]: the legacy intent may
 * still be live, and replaying it after a new login would erase the new
 * account's data.
 */
expect fun createSessionStateStorage(name: String): SecureKeyValueStorage

/**
 * Outcome of importing session recovery intents from a legacy plaintext
 * store. The import can only be trusted when every legacy intent was either
 * copied to the durable store or proven superseded by a durable value.
 */
sealed interface LegacySessionMigration {
    /** No legacy recovery intents were found. */
    data object NotRequired : LegacySessionMigration

    /** Every legacy recovery intent is durably imported or proven stale. */
    data object Completed : LegacySessionMigration

    /**
     * At least one legacy recovery intent still exists without evidence that
     * the durable copy is authoritative — the durable store could not be
     * queried, a write failed, or the migration marker could not be persisted.
     * Login and resume must stay blocked until a later probe resolves it or an
     * explicit session reset discards the intents.
     */
    data class Unresolved(val keys: List<String>) : LegacySessionMigration
}

/**
 * Implemented by the storage returned from [createSessionStateStorage] when
 * the platform still keeps session recovery intents in a legacy location.
 */
interface LegacySessionMigrationSource {
    val legacySessionMigration: LegacySessionMigration

    /** Re-probes the legacy and durable stores without recreating the process. */
    fun retryLegacySessionMigration()

    /**
     * Drops legacy intents that were never imported because the user
     * explicitly discarded the local session (reset/logout); the old intents
     * describe a session that no longer matters. Returns true only when no
     * legacy recovery intent remains, so callers keep failing closed when the
     * cleanup itself could not be persisted.
     */
    fun discardLegacySessionIntents(): Boolean
}

/**
 * Keys that carry session recovery semantics. Only these are imported from a
 * legacy store; anything else written under the same prefix is left alone.
 * The session keys must match [org.kasumi321.ushio.phitracker.data.session.SessionStateStore].
 */
internal object SessionRecoveryKeys {
    const val PENDING_LOGIN_OWNER = "pending_login_owner"
    const val LOGOUT_PENDING = "logout_pending"
    const val LOGIN_REQUIRED = "login_required"
    const val RESET_PENDING = "reset_pending"

    val ALL = listOf(PENDING_LOGIN_OWNER, LOGOUT_PENDING, LOGIN_REQUIRED, RESET_PENDING)

    /**
     * A legacy value is worth migrating only when it asks for recovery work.
     * A cleared flag ("false") or a blank owner carries no intent and can be
     * dropped without consulting the durable store.
     */
    fun carriesRecoveryIntent(key: String, value: String): Boolean = when (key) {
        PENDING_LOGIN_OWNER -> value.isNotBlank()
        LOGOUT_PENDING, LOGIN_REQUIRED, RESET_PENDING -> value.toBooleanStrictOrNull() == true
        else -> false
    }
}

/** Non-destructive read source for credentials stored by pre-secure-store builds. */
expect fun createLegacySettingsStorage(name: String): SecureKeyValueStorage?
