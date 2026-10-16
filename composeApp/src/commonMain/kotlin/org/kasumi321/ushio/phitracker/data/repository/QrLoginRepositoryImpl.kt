package org.kasumi321.ushio.phitracker.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import org.kasumi321.ushio.phitracker.data.api.TapTapQrLoginApi
import org.kasumi321.ushio.phitracker.data.api.TapTapTokenData
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.QrAuthorizationId
import org.kasumi321.ushio.phitracker.domain.model.QrChallengeId
import org.kasumi321.ushio.phitracker.domain.model.QrLoginChallenge
import org.kasumi321.ushio.phitracker.domain.model.QrLoginPollResult
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.repository.QrLoginRepository
import kotlin.random.Random
import kotlin.time.Clock

class QrLoginRepositoryImpl(
    private val api: TapTapQrLoginApi,
    private val clockMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val opaqueId: () -> String = ::newOpaqueId
) : QrLoginRepository {
    private val mutex = Mutex()
    private val challenges = mutableMapOf<QrChallengeId, ChallengeContext>()
    private val authorizations = mutableMapOf<QrAuthorizationId, AuthorizationContext>()

    override suspend fun requestChallenge(server: Server): QrLoginChallenge {
        pruneExpired()
        val response = safely("QR challenge request failed") {
            api.requestDeviceCode(server)
        }
        require(response.data.deviceCode.isNotBlank() && response.data.qrcodeUrl.isNotBlank()) {
            "Malformed QR challenge response"
        }
        val id = QrChallengeId(opaqueId())
        val expiresAt = clockMillis() + response.data.expiresIn.coerceAtLeast(0) * 1_000L
        mutex.withLock {
            challenges[id] = ChallengeContext(
                deviceCode = response.data.deviceCode,
                deviceId = response.deviceId,
                server = server,
                expiresAt = expiresAt
            )
        }
        return QrLoginChallenge(id = id, qrUrl = response.data.qrcodeUrl, expiresAt = expiresAt)
    }

    override suspend fun poll(challengeId: QrChallengeId): QrLoginPollResult {
        pruneExpired()
        val context = mutex.withLock { challenges[challengeId] }
            ?: throw IllegalArgumentException("Unknown or expired QR challenge")
        val result = safely("QR polling failed") {
            api.checkQrCodeResult(context.deviceCode, context.deviceId, context.server)
        }
        if (result.success) {
            val tokenData = result.data?.takeIf(::isComplete)
                ?: throw IllegalStateException("Malformed QR authorization response")
            val authorizationId = QrAuthorizationId(opaqueId())
            mutex.withLock {
                challenges.remove(challengeId)
                authorizations[authorizationId] = AuthorizationContext(
                    tokenData = tokenData,
                    server = context.server,
                    expiresAt = context.expiresAt
                )
            }
            return QrLoginPollResult.Authorized(authorizationId)
        }
        return when (result.error) {
            "authorization_waiting" -> QrLoginPollResult.AuthorizationWaiting
            "network_error" -> throw IllegalStateException("QR polling failed")
            "unknown" -> throw IllegalStateException("Malformed QR polling response")
            else -> QrLoginPollResult.Pending
        }
    }

    override suspend fun exchangeForSessionToken(authorizationId: QrAuthorizationId): String {
        pruneExpired()
        val context = mutex.withLock { authorizations[authorizationId] }
            ?: throw IllegalArgumentException("Unknown or expired QR authorization")
        return try {
            safely("QR authorization exchange failed") {
                val profile = api.getProfile(context.tokenData, context.server)
                api.exchangeForSessionToken(profile, context.tokenData, context.server)
            }
        } finally {
            mutex.withLock { authorizations.remove(authorizationId) }
        }
    }

    private suspend fun <T> safely(message: String, operation: suspend () -> T): T = try {
        operation()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        AppLogger.w("QrLoginRepository", error) { message }
        throw IllegalStateException(message, error)
    }

    private suspend fun pruneExpired() {
        val now = clockMillis()
        mutex.withLock {
            challenges.entries.removeAll { it.value.expiresAt <= now }
            authorizations.entries.removeAll { it.value.expiresAt <= now }
        }
    }

    private fun isComplete(token: TapTapTokenData): Boolean =
        !token.kid.isNullOrBlank() &&
            !token.accessToken.isNullOrBlank() &&
            !token.macKey.isNullOrBlank()

    private data class ChallengeContext(
        val deviceCode: String,
        val deviceId: String,
        val server: Server,
        val expiresAt: Long
    )

    private data class AuthorizationContext(
        val tokenData: TapTapTokenData,
        val server: Server,
        val expiresAt: Long
    )

    private companion object {
        fun newOpaqueId(): String = Random.nextBytes(16).joinToString(separator = "") {
            it.toUByte().toString(16).padStart(2, '0')
        }
    }
}
