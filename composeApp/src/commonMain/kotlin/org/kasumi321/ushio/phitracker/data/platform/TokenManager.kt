package org.kasumi321.ushio.phitracker.data.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.session.SessionCredentialsPersistenceException

data class StoredSessionCredentials(
    val token: Pair<String, Server>?,
    val boundPlayerId: String?
)

/**
 * SessionToken secure storage manager.
 * - Keys: "session_token", "server", "session_player_id"
 * - save trims token whitespace
 * - get returns null if no session_token stored
 * - server defaults to CN if missing or invalid
 * - session_player_id records the TapTap objectId the token was issued for;
 *   it lets offline startup recovery prove the cached data belongs to this
 *   token's account (objectIds survive sessionToken resets, tokens do not)
 * - clear removes all three keys
 */
class TokenManager(
    private val storage: SecureKeyValueStorage,
    private val recoverLegacyApiToken: () -> String? = { null }
) {
    private val _credentials = MutableStateFlow(readCredentials())
    val credentials: StateFlow<StoredSessionCredentials> = _credentials.asStateFlow()

    fun saveToken(token: String, server: Server): Result<Unit> {
        val normalized = token.trim()
        val previousToken = storage.getString(KEY_SESSION_TOKEN)
        val previousServer = storage.getString(KEY_SERVER)
        val tokenResult = storage.putString(KEY_SESSION_TOKEN, normalized)
        val serverResult = storage.putString(KEY_SERVER, server.name)
        val verified = tokenResult == StorageWriteResult.Persisted &&
            serverResult == StorageWriteResult.Persisted &&
            getToken() == normalized to server
        if (verified) {
            refreshCredentials()
            return Result.success(Unit)
        }
        restore(KEY_SESSION_TOKEN, previousToken)
        restore(KEY_SERVER, previousServer)
        refreshCredentials()
        return Result.failure(
            SessionCredentialsPersistenceException(
                storageResetRequired = tokenResult == StorageWriteResult.Ephemeral ||
                    serverResult == StorageWriteResult.Ephemeral
            )
        )
    }

    fun getToken(): Pair<String, Server>? {
        val token = storage.getString(KEY_SESSION_TOKEN) ?: return null
        val serverName = storage.getString(KEY_SERVER) ?: Server.CN.name
        val server = try {
            Server.valueOf(serverName)
        } catch (_: Exception) {
            AppLogger.w("TokenManager") { "Unknown stored server name, falling back to CN" }
            Server.CN
        }
        return token to server
    }

    fun savePlayerBinding(playerId: String): Result<Unit> {
        val previous = getPlayerBinding()
        val result = storage.putString(KEY_SESSION_PLAYER_ID, playerId)
        if (result == StorageWriteResult.Persisted && getPlayerBinding() == playerId) {
            refreshCredentials()
            return Result.success(Unit)
        }
        restore(KEY_SESSION_PLAYER_ID, previous)
        refreshCredentials()
        return Result.failure(
            SessionCredentialsPersistenceException(
                storageResetRequired = result == StorageWriteResult.Ephemeral
            )
        )
    }

    fun getPlayerBinding(): String? = storage.getString(KEY_SESSION_PLAYER_ID)

    fun clearToken(): Result<Unit> {
        val results = listOf(
            storage.remove(KEY_SESSION_TOKEN),
            storage.remove(KEY_SERVER),
            storage.remove(KEY_SESSION_PLAYER_ID)
        )
        val verified = results.all { it == StorageWriteResult.Persisted } &&
            storage.getString(KEY_SESSION_TOKEN) == null &&
            storage.getString(KEY_SERVER) == null &&
            storage.getString(KEY_SESSION_PLAYER_ID) == null
        refreshCredentials()
        return if (verified) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("session credentials were not fully removed"))
        }
    }

    fun resetStorage(): Result<Unit> {
        // Read every still-accessible source before replacing the master key.
        // A restored device may no longer be able to decrypt the primary copy.
        val apiToken = runCatching { storage.getString(KEY_API_TOKEN) }.getOrNull()
            ?: runCatching { recoverLegacyApiToken() }.getOrNull()
        val result = storage.resetStorage()
        val restored = result.isSuccess && (
            apiToken == null || (
                storage.putString(KEY_API_TOKEN, apiToken) == StorageWriteResult.Persisted &&
                    storage.getString(KEY_API_TOKEN) == apiToken
                )
            )
        refreshCredentials()
        return if (restored) Result.success(Unit) else Result.failure(IllegalStateException("secure storage was not rebuilt"))
    }

    private fun restore(key: String, value: String?) {
        if (value == null) storage.remove(key) else storage.putString(key, value)
    }

    private fun readCredentials() = StoredSessionCredentials(
        token = getToken(),
        boundPlayerId = getPlayerBinding()
    )

    private fun refreshCredentials() {
        _credentials.value = readCredentials()
    }

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SERVER = "server"
        private const val KEY_SESSION_PLAYER_ID = "session_player_id"
        private const val KEY_API_TOKEN = "api_token"
    }
}
