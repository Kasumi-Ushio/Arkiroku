package org.kasumi321.ushio.phitracker.data.platform

import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.Server

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
    private val storage: SecureKeyValueStorage
) {
    fun saveToken(token: String, server: Server) {
        storage.putString(KEY_SESSION_TOKEN, token.trim())
        storage.putString(KEY_SERVER, server.name)
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

    fun savePlayerBinding(playerId: String) {
        storage.putString(KEY_SESSION_PLAYER_ID, playerId)
    }

    fun getPlayerBinding(): String? = storage.getString(KEY_SESSION_PLAYER_ID)

    fun clearToken() {
        storage.remove(KEY_SESSION_TOKEN)
        storage.remove(KEY_SERVER)
        storage.remove(KEY_SESSION_PLAYER_ID)
    }

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SERVER = "server"
        private const val KEY_SESSION_PLAYER_ID = "session_player_id"
    }
}
