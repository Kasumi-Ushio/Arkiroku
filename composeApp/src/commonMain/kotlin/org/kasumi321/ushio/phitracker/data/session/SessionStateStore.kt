package org.kasumi321.ushio.phitracker.data.session

import org.kasumi321.ushio.phitracker.data.platform.LegacySessionMigration
import org.kasumi321.ushio.phitracker.data.platform.LegacySessionMigrationSource
import org.kasumi321.ushio.phitracker.data.platform.SecureKeyValueStorage
import org.kasumi321.ushio.phitracker.data.platform.SessionRecoveryKeys
import org.kasumi321.ushio.phitracker.data.platform.StorageWriteResult

class SessionStateStore(
    private val storage: SecureKeyValueStorage
) {
    private val legacyMigration: LegacySessionMigrationSource? = storage as? LegacySessionMigrationSource

    val pendingLoginOwner: String?
        get() = storage.getString(SessionRecoveryKeys.PENDING_LOGIN_OWNER)?.takeIf(String::isNotBlank)

    val isLogoutPending: Boolean
        get() = storage.getString(SessionRecoveryKeys.LOGOUT_PENDING)?.toBooleanStrictOrNull() == true

    val isLoginRequired: Boolean
        get() = storage.getString(SessionRecoveryKeys.LOGIN_REQUIRED)?.toBooleanStrictOrNull() == true

    val isResetPending: Boolean
        get() = storage.getString(SessionRecoveryKeys.RESET_PENDING)?.toBooleanStrictOrNull() == true

    val hasPendingRecovery: Boolean
        get() = pendingLoginOwner != null || isLogoutPending || isLoginRequired || isResetPending

    /**
     * True while a pre-Keychain recovery intent exists without proof that the
     * durable copy is authoritative. Establishing a session in this state is
     * unsafe: the old intent could later be imported after a login and clear
     * the new account's data. Coordinators must refuse login/resume until it
     * resolves (a later startup) or is explicitly discarded.
     */
    val hasUnresolvedLegacyRecovery: Boolean
        get() = legacyMigration?.legacySessionMigration is LegacySessionMigration.Unresolved

    fun retryUnresolvedLegacyRecovery() {
        if (hasUnresolvedLegacyRecovery) legacyMigration?.retryLegacySessionMigration()
    }

    /**
     * Drops unimported legacy intents once the user explicitly discards the
     * local session. Returns a failure if they could not be removed so callers
     * stay fail-closed instead of unblocking login over a stale intent.
     */
    fun discardUnresolvedLegacyRecovery(): Result<Unit> {
        val migration = legacyMigration ?: return Result.success(Unit)
        if (migration.legacySessionMigration !is LegacySessionMigration.Unresolved) {
            return Result.success(Unit)
        }
        return if (migration.discardLegacySessionIntents()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("legacy session recovery state was not cleared"))
        }
    }

    fun beginLogin(owner: String): Result<Unit> =
        write(SessionRecoveryKeys.PENDING_LOGIN_OWNER, owner) {
            pendingLoginOwner == owner
        }

    fun clearLogin(): Result<Unit> =
        remove(SessionRecoveryKeys.PENDING_LOGIN_OWNER) { pendingLoginOwner == null }

    fun setLogoutPending(pending: Boolean): Result<Unit> = if (pending) {
        write(SessionRecoveryKeys.LOGOUT_PENDING, "true") { isLogoutPending }
    } else {
        remove(SessionRecoveryKeys.LOGOUT_PENDING) { !isLogoutPending }
    }

    fun setLoginRequired(required: Boolean): Result<Unit> = if (required) {
        write(SessionRecoveryKeys.LOGIN_REQUIRED, "true") { isLoginRequired }
    } else {
        remove(SessionRecoveryKeys.LOGIN_REQUIRED) { !isLoginRequired }
    }

    fun setResetPending(pending: Boolean): Result<Unit> = if (pending) {
        write(SessionRecoveryKeys.RESET_PENDING, "true") { isResetPending }
    } else {
        remove(SessionRecoveryKeys.RESET_PENDING) { !isResetPending }
    }

    private fun write(key: String, value: String, verify: () -> Boolean): Result<Unit> {
        val result = storage.putString(key, value)
        return if (result == StorageWriteResult.Persisted && verify()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("session state was not persisted"))
        }
    }

    private fun remove(key: String, verify: () -> Boolean): Result<Unit> {
        val result = storage.remove(key)
        return if (result == StorageWriteResult.Persisted && verify()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("session state was not cleared"))
        }
    }
}
