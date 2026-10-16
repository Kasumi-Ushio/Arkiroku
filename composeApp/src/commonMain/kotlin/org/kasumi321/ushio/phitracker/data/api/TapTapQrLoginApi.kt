package org.kasumi321.ushio.phitracker.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.ApiCrypto
import org.kasumi321.ushio.phitracker.data.platform.createApiCrypto
import org.kasumi321.ushio.phitracker.domain.model.Server
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TapTapQrLoginApi(
    private val httpClient: HttpClient,
    private val apiCrypto: ApiCrypto = createApiCrypto(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    suspend fun requestDeviceCode(server: Server): DeviceCodeResponse {
        val deviceId = generateDeviceId()
        val response = httpClient.submitForm(
            url = TapTapConstants.oauthCodeUrl(server),
            formParameters = Parameters.build {
                append("client_id", TapTapConstants.lcClientId(server))
                append("response_type", "device_code")
                append("scope", "public_profile")
                append("version", "2.1")
                append("platform", "unity")
                append("info", """{"device_id":"$deviceId"}""")
            }
        )
        return DeviceCodeResponse(
            data = json.decodeFromString<DeviceCodeApiResponse>(response.bodyAsText()).data,
            deviceId = deviceId
        )
    }

    suspend fun checkQrCodeResult(deviceCode: String, deviceId: String, server: Server): QrCheckResult {
        return try {
            val response = httpClient.submitForm(
                url = TapTapConstants.oauthTokenUrl(server),
                formParameters = Parameters.build {
                    append("grant_type", "device_token")
                    append("client_id", TapTapConstants.lcClientId(server))
                    append("secret_type", "hmac-sha-1")
                    append("code", deviceCode)
                    append("version", "1.0")
                    append("platform", "unity")
                    append("info", """{"device_id":"$deviceId"}""")
                }
            )
            val jsonObj = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val dataObj = jsonObj["data"]
            val success = jsonObj["success"]?.jsonPrimitive?.booleanOrNull == true
            when {
                success && dataObj != null -> QrCheckResult(success = true, data = json.decodeFromJsonElement(dataObj))
                dataObj != null -> QrCheckResult(
                    success = false,
                    error = dataObj.jsonObject["error"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                )
                else -> {
                    val tokenData = json.decodeFromJsonElement<TapTapTokenData>(jsonObj)
                    if (tokenData.kid != null && tokenData.accessToken != null) {
                        QrCheckResult(success = true, data = tokenData)
                    } else {
                        QrCheckResult(success = false, error = json.decodeFromJsonElement<QrErrorData>(jsonObj).error ?: "unknown")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("TapTapQrLoginApi", e) { "QR polling request failed, returning network_error" }
            QrCheckResult(success = false, error = "network_error")
        }
    }

    suspend fun getProfile(tokenData: TapTapTokenData, server: Server): TapTapProfile {
        val url = TapTapConstants.profileUrl(server)
        val authorization = buildMacAuthorization(
            requestUrl = url,
            method = "GET",
            keyId = tokenData.kid!!,
            macKey = tokenData.macKey!!
        )
        return httpClient.get(url) { header("Authorization", authorization) }.body()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun exchangeForSessionToken(profile: TapTapProfile, tokenData: TapTapTokenData, server: Server): String {
        val timestamp = Clock.System.now().epochSeconds
        val hash = apiCrypto.md5Hex("$timestamp${TapTapConstants.lcAppKey(server)}")
        val body = buildJsonObject {
            putJsonObject("authData") {
                putJsonObject("taptap") {
                    put("openid", profile.openid)
                    put("name", profile.name)
                    put("avatar", profile.avatar)
                    put("kid", tokenData.kid)
                    put("access_token", tokenData.accessToken)
                    put("token_type", tokenData.tokenType)
                    put("mac_key", tokenData.macKey)
                    put("mac_algorithm", tokenData.macAlgorithm)
                    put("scope", tokenData.scope)
                }
            }
        }
        val response = httpClient.post(TapTapConstants.lcUsersUrl(server)) {
            header("X-LC-Id", TapTapConstants.lcClientId(server))
            header("X-LC-Sign", "$hash,$timestamp")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return response.body<LcLoginResponse>().sessionToken
    }

    @OptIn(ExperimentalTime::class, ExperimentalEncodingApi::class)
    private fun buildMacAuthorization(requestUrl: String, method: String, keyId: String, macKey: String): String {
        val parts = UrlParts.parse(requestUrl)
        val time = Clock.System.now().epochSeconds.toString().padStart(10, '0')
        val nonce = generateRandomString(16)
        val signatureBase = "$time\n$nonce\n$method\n${parts.uri}\n${parts.host}\n${parts.port}\n\n"
        val signature = Base64.Default.encode(apiCrypto.hmacSha1(signatureBase, macKey))
        return "MAC id=\"$keyId\", ts=\"$time\", nonce=\"$nonce\", mac=\"$signature\""
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun generateRandomString(length: Int): String {
        val bytes = ByteArray(length) { Random.nextInt(0, 256).toByte() }
        return Base64.Default.encode(bytes)
    }

    private fun generateDeviceId(): String = buildString(32) {
        repeat(32) { append("0123456789abcdef"[Random.nextInt(16)]) }
    }

    private data class UrlParts(val host: String, val uri: String, val port: String) {
        companion object {
            fun parse(url: String): UrlParts {
                val schemeEnd = url.indexOf("://")
                val scheme = url.substring(0, schemeEnd)
                val rest = url.substring(schemeEnd + 3)
                val slashIndex = rest.indexOf('/')
                val authority = if (slashIndex >= 0) rest.substring(0, slashIndex) else rest
                val uri = if (slashIndex >= 0) rest.substring(slashIndex) else "/"
                val host = authority.substringBefore(':')
                val port = authority.substringAfter(':', if (scheme == "https") "443" else "80")
                return UrlParts(host = host, uri = uri, port = port)
            }
        }
    }
}

@Serializable
data class DeviceCodeApiResponse(val data: DeviceCodeData)

@Serializable
data class DeviceCodeData(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String = "",
    @SerialName("qrcode_url") val qrcodeUrl: String,
    @SerialName("verification_url") val verificationUrl: String = "",
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5
)

data class DeviceCodeResponse(val data: DeviceCodeData, val deviceId: String)

@Serializable
data class QrErrorData(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class TapTapTokenData(
    val kid: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("mac_key") val macKey: String? = null,
    @SerialName("mac_algorithm") val macAlgorithm: String? = null,
    val scope: String? = null
)

data class QrCheckResult(val success: Boolean, val data: TapTapTokenData? = null, val error: String? = null)

@Serializable
data class TapTapProfile(val data: TapTapProfileData) {
    val openid: String get() = data.openid
    val name: String get() = data.name
    val avatar: String get() = data.avatar
}

@Serializable
data class TapTapProfileData(val openid: String, val name: String, val avatar: String)

@Serializable
data class LcLoginResponse(val sessionToken: String, val objectId: String = "")
