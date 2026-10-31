package org.kasumi321.ushio.phitracker.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.session.SessionTokenRejectedException

class TapTapApiClient(
    private val httpClient: HttpClient
) {
    private fun buildHeaders(sessionToken: String, server: Server): Map<String, String> = mapOf(
        "X-LC-Id" to TapTapConstants.lcClientId(server),
        "X-LC-Key" to TapTapConstants.lcAppKey(server),
        "X-LC-Session" to sessionToken
    )

    /**
     * TapTap's built-in account service answers an invalid sessionToken
     * (reset or revoked server-side) with 401/403. Surface that as a distinct
     * domain exception instead of a body-decode failure, so callers can route
     * the user to re-login while keeping the account's local data intact.
     */
    private fun HttpResponse.rejectIfSessionTokenInvalid(): HttpResponse {
        when (status.value) {
            401, 403 -> throw SessionTokenRejectedException("TapTap rejected the session token (${status.value})")
        }
        return this
    }

    suspend fun getUserInfo(sessionToken: String, server: Server): UserInfoResponse {
        val baseUrl = TapTapConstants.baseUrl(server)
        return httpClient.get(baseUrl + TapTapConstants.Endpoints.USERS_ME) {
            buildHeaders(sessionToken, server).forEach { (key, value) -> header(key, value) }
        }.rejectIfSessionTokenInvalid().body()
    }

    suspend fun getGameSaves(sessionToken: String, server: Server, userObjectId: String): GameSaveListResponse {
        val baseUrl = TapTapConstants.baseUrl(server)
        return httpClient.get(baseUrl + TapTapConstants.Endpoints.GAME_SAVE) {
            buildHeaders(sessionToken, server).forEach { (key, value) -> header(key, value) }
            parameter("where", userPointerWhere(userObjectId))
            parameter("order", "-updatedAt")
            parameter("limit", 1)
        }.rejectIfSessionTokenInvalid().body()
    }

    suspend fun downloadSave(url: String): ByteArray = httpClient.get(url).body()

    /**
     * Rotates the account's sessionToken (TapTap built-in account REST API:
     * PUT /1.1/users/<objectId>/refreshSessionToken, authenticated with the
     * CURRENT sessionToken). The response carries the new token; the old one
     * stops working immediately. The objectId never changes, so the caller can
     * re-link the new credential to the existing local account data.
     */
    suspend fun resetSessionToken(
        sessionToken: String,
        server: Server,
        userObjectId: String
    ): SessionTokenResetResponse {
        val url = "${TapTapConstants.baseUrl(server)}${TapTapConstants.Endpoints.refreshSessionToken(userObjectId)}"
        return httpClient.put(url) {
            buildHeaders(sessionToken, server).forEach { (key, value) -> header(key, value) }
        }.rejectIfSessionTokenInvalid().body()
    }

    private fun userPointerWhere(userObjectId: String): String =
        """{"user":{"__type":"Pointer","className":"_User","objectId":"$userObjectId"}}"""
}

@Serializable
data class UserInfoResponse(
    val objectId: String,
    val nickname: String
)

@Serializable
data class SessionTokenResetResponse(
    val sessionToken: String,
    val objectId: String = "",
    val updatedAt: String? = null
)

@Serializable
data class GameSaveListResponse(
    val results: List<GameSaveItem>
)

@Serializable
data class GameSaveItem(
    val objectId: String,
    val summary: String,
    val updatedAt: String,
    val gameFile: GameFileRef,
    val user: LeanCloudPointer? = null
)

@Serializable
data class GameFileRef(
    @SerialName("objectId") val id: String,
    val url: String
)

@Serializable
data class LeanCloudPointer(
    @SerialName("__type") val type: String,
    val className: String,
    val objectId: String
)
