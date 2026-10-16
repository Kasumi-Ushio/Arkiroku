package org.kasumi321.ushio.phitracker.data.repository

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.kasumi321.ushio.phitracker.data.api.GitHubReleaseDto
import org.kasumi321.ushio.phitracker.data.api.toDomain
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo

internal fun parseGitHubReleaseResponse(
    statusCode: HttpStatusCode,
    responseText: String,
    includePreRelease: Boolean,
    json: Json,
): Result<ReleaseInfo> = runCatching {
    if (statusCode == HttpStatusCode.Forbidden && responseText.contains("\"API rate limit\"")) {
        error("GitHub API 请求频率超限，请稍后再试")
    }
    if (!statusCode.value.toString().startsWith("2")) {
        error("GitHub 服务异常（${statusCode.value}），请稍后再试")
    }

    val releases = try {
        json.decodeFromString<List<GitHubReleaseDto>>(responseText)
    } catch (e: SerializationException) {
        AppLogger.e("GitHubReleaseParser", e) { "Failed to parse GitHub releases JSON" }
        throw IllegalStateException("GitHub 返回数据格式异常，请稍后再试", e)
    }

    val candidates = if (includePreRelease) releases else releases.filter { !it.prerelease }
    candidates.firstOrNull()?.toDomain() ?: error("未找到任何发布版本")
}
