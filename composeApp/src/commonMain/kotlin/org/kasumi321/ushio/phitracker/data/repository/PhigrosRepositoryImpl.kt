package org.kasumi321.ushio.phitracker.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kasumi321.ushio.phitracker.data.api.GitHubReleaseDto
import org.kasumi321.ushio.phitracker.data.api.toDomain
import org.kasumi321.ushio.phitracker.data.api.PhiPluginApi
import org.kasumi321.ushio.phitracker.data.api.TapTapApiClient
import org.kasumi321.ushio.phitracker.data.api.TapTapGameApi
import org.kasumi321.ushio.phitracker.data.database.RecordDao
import org.kasumi321.ushio.phitracker.data.database.AppDatabase
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryDao
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotDao
import org.kasumi321.ushio.phitracker.data.database.SyncWriter
import org.kasumi321.ushio.phitracker.data.database.UserDao
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toSongRecordMap
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toDomain
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toUserProfile
import org.kasumi321.ushio.phitracker.data.parser.SaveParser
import org.kasumi321.ushio.phitracker.data.platform.TokenManager
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey
import org.kasumi321.ushio.phitracker.domain.model.SongApiDetail
import org.kasumi321.ushio.phitracker.domain.model.B30ChartTagBatch
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagSongData
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PhigrosRepositoryImpl(
    private val apiClient: TapTapApiClient,
    private val phiPluginApi: PhiPluginApi,
    private val tapTapGameApi: TapTapGameApi,
    private val httpClient: HttpClient,
    private val saveParser: SaveParser,
    database: AppDatabase,
    private val recordDao: RecordDao,
    private val userDao: UserDao,
    private val syncSnapshotDao: SyncSnapshotDao,
    private val songSyncHistoryDao: SongSyncHistoryDao,
    private val tokenManager: TokenManager,
    private val json: Json,
    private val songDataProvider: SongDataProvider,
) : PhigrosRepository {
    private val syncWriter = SyncWriter(database, recordDao, userDao, syncSnapshotDao, songSyncHistoryDao)
    private val syncSnapshotProjector = SyncSnapshotProjector()
    private val apiDetailMutex = Mutex()
    private val apiDetailCache = mutableMapOf<ApiDetailCacheKey, SongApiDetail>()
    private var apiDetailEpoch = 0L
    private var apiDetailIdentity: Triple<String, String, String>? = null

    override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> = runCatching {
        val userInfo = apiClient.getUserInfo(sessionToken, server)
        UserProfile(
            playerId = userInfo.objectId,
            nickname = userInfo.nickname,
            avatar = "",
            selfIntro = "",
            background = "",
            rks = 0f,
            challengeModeRank = 0,
            gameVersion = 0,
            updatedAt = ""
        )
    }.logFailure("validateToken")

    override suspend fun syncSave(
        sessionToken: String,
        server: Server,
        mode: SyncMode
    ): Result<SyncSaveResult> = runCatching {
        val previousPlayerId = userDao.getUser().first()?.playerId
        val userInfo = apiClient.getUserInfo(sessionToken, server)
        val saveList = apiClient.getGameSaves(sessionToken, server, userInfo.objectId)
        val latestSave = saveList.results.firstOrNull { it.user?.objectId == userInfo.objectId }
            ?: error("没有找到当前用户的存档")
        val summary = saveParser.parseSummary(latestSave.summary)
        val saveData = apiClient.downloadSave(latestSave.gameFile.url)
        val save = saveParser.parseSave(saveData).copy(summary = summary)
        val userProfile = UserProfile(
            playerId = userInfo.objectId,
            nickname = userInfo.nickname,
            avatar = save.user.avatar,
            selfIntro = save.user.selfIntro,
            background = save.user.background,
            rks = summary.rks,
            challengeModeRank = summary.challengeModeRank,
            gameVersion = summary.gameVersion,
            updatedAt = latestSave.updatedAt
        )
        val commitData = syncSnapshotProjector.project(
            save = save,
            profile = userProfile,
            server = server,
            difficulties = songDataProvider.getDifficultyMap(),
            songNames = songDataProvider.getSongNameMap()
        )
        syncWriter.commit(commitData, mode).also {
            if (previousPlayerId != userProfile.playerId) {
                invalidateApiDetailCache()
            }
        }
    }.logFailure("syncSave")

    override fun getCachedSave(): Flow<Save?> = combine(recordDao.getAllRecords(), userDao.getUser()) { records, user ->
        if (records.isEmpty()) return@combine null
        Save(
            gameRecord = records.toSongRecordMap(),
            gameProgress = GameProgress(
                isFirstRun = false,
                legacyChapterFinished = false,
                alreadyShowCollectionTip = false,
                alreadyShowAutoUnlockINTip = false,
                completed = "",
                songUpdateInfo = 0,
                challengeModeRank = user?.challengeModeRank ?: 0,
                money = emptyList(),
                unlockFlagOfSpasmodic = 0,
                unlockFlagOfIgallta = 0,
                unlockFlagOfRrharil = 0,
                flagOfSongRecordKey = 0,
                randomVersionUnlocked = null,
                chapter8UnlockBegin = null,
                chapter8UnlockSecondPhase = null,
                chapter8Passed = null,
                chapter8SongUnlocked = null
            ),
            user = UserSettings(
                showPlayerId = true,
                selfIntro = user?.selfIntro ?: "",
                avatar = user?.avatar ?: "",
                background = user?.background ?: ""
            ),
            summary = null
        )
    }

    override fun getUserProfile(): Flow<UserProfile?> = userDao.getUser().map { it?.toUserProfile() }

    override suspend fun saveSessionToken(token: String, server: Server) {
        invalidateApiDetailCache()
        tokenManager.saveToken(token, server)
    }

    override suspend fun getSessionToken(): Pair<String, Server>? = tokenManager.getToken()

    override suspend fun clearData() {
        invalidateApiDetailCache()
        tokenManager.clearToken()
        recordDao.deleteAll()
        userDao.deleteAll()
    }

    override suspend fun clearTokenSync() {
        invalidateApiDetailCache()
        tokenManager.clearToken()
    }

    override suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int> =
        Difficulty.entries.associateWith { difficulty ->
            recordDao.getClearCountByDifficulty(difficulty.name)
        }

    override suspend fun getTotalFullComboCount(): Int = recordDao.getTotalFcCount()

    override suspend fun getTotalPhiCount(): Int = recordDao.getTotalPhiCount()

    override fun observeSyncSnapshots(): Flow<List<SyncSnapshot>> =
        syncSnapshotDao.getAll().map { snapshots -> snapshots.map { it.toDomain() } }

    override suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot> =
        syncSnapshotDao.getAllOnce().map { it.toDomain() }

    override fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>> =
        songSyncHistoryDao.getBySongId(songId).map { entries -> entries.map { it.toDomain() } }

    override suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry> =
        songSyncHistoryDao.getBySnapshotId(snapshotId).map { it.toDomain() }

    override suspend fun apiTest(): Result<JsonObject> =
        runCatching { phiPluginApi.test() }.logFailure("apiTest")

    override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> =
        runCatching { phiPluginApi.getBindInfo(platform.trim(), platformId.trim()) }.logFailure("apiGetBindInfo")

    override suspend fun getSongApiDetail(key: ApiDetailCacheKey): Result<SongApiDetail> {
        val normalizedKey = key.copy(
            platform = key.platform.trim(),
            platformId = key.platformId.trim(),
            apiUserId = key.apiUserId.trim(),
            songId = key.songId.trim()
        )
        val identity = Triple(normalizedKey.platform, normalizedKey.platformId, normalizedKey.apiUserId)
        val epochAtStart: Long
        apiDetailMutex.withLock {
            if (apiDetailIdentity != null && apiDetailIdentity != identity) {
                advanceApiDetailEpochLocked()
            }
            apiDetailIdentity = identity
            apiDetailCache[normalizedKey]?.let { return Result.success(it) }
            epochAtStart = apiDetailEpoch
        }

        val result = runCatching {
            val difficulty = normalizedKey.difficulty.name
            val rank = phiPluginApi.getRank(
                normalizedKey.platform,
                normalizedKey.platformId,
                normalizedKey.apiUserId,
                normalizedKey.songId,
                difficulty
            )
            val average = phiPluginApi.getAvgAcc(
                normalizedKey.songId,
                difficulty,
                normalizedKey.minRks,
                normalizedKey.maxRks
            )
            val history = phiPluginApi.getScoreHistory(
                normalizedKey.platform,
                normalizedKey.platformId,
                normalizedKey.apiUserId,
                normalizedKey.songId,
                difficulty
            )
            mapSongApiDetail(rank, average, history, normalizedKey)
        }

        result.getOrNull()?.let { detail ->
            apiDetailMutex.withLock {
                if (apiDetailEpoch == epochAtStart && apiDetailIdentity == identity) {
                    apiDetailCache[normalizedKey] = detail
                }
            }
        }
        return result.logFailure("getSongApiDetail")
    }

    override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> =
        runCatching { phiPluginApi.getRksAbove(rks) }.logFailure("apiGetRksAbove")

    override suspend fun apiGetSaveHistory(
        platform: String,
        platformId: String,
        apiUserId: String,
        request: List<String>
    ): Result<JsonObject> =
        runCatching {
            phiPluginApi.getSaveHistory(
                platform.trim(),
                platformId.trim(),
                apiUserId.trim(),
                request.map { it.trim() }
            )
        }.logFailure("apiGetSaveHistory")

    override suspend fun apiGetRankByUser(
        platform: String,
        platformId: String,
        apiUserId: String
    ): Result<JsonObject> =
        runCatching { phiPluginApi.getRankByUser(platform.trim(), platformId.trim(), apiUserId.trim()) }.logFailure("apiGetRankByUser")

    override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> =
        runCatching { phiPluginApi.getRankByPosition(position) }.logFailure("apiGetRankByPosition")

    // ── Chart tags (chartsTag) ──────────────────────────────────────

    override suspend fun getChartTagTree(): Result<List<ChartTagTreeNode>> {
        val now = Clock.System.now()
        chartTagTreeCache?.let { (cachedAt, cached) ->
            if (now - cachedAt < CHART_TAG_TREE_TTL) return Result.success(cached)
        }
        return runCatching {
            val response = phiPluginApi.getChartTagTree().also { it.throwOnApiError() }
            response["data"].asArray()
                ?.mapNotNull { parseTagTreeNode(it) }
                .orEmpty()
                .also { chartTagTreeCache = now to it }
        }.logFailure("getChartTagTree")
    }

    override suspend fun getChartTags(songId: String, difficulty: Difficulty): Result<ChartTagSongData> =
        runCatching {
            val response = phiPluginApi.getChartTags(songId.trim(), difficulty.name).also { it.throwOnApiError() }
            ChartTagSongData(
                songId = songId.trim(),
                difficulty = difficulty,
                tags = parseVoteMap(response["data"]),
                primary = parseVoteMap(response["primary"]),
                secondary = parseVoteMap(response["secondary"]),
                categories = response["tree"].asArray()?.mapNotNull { parseTagTreeNode(it) }.orEmpty()
            )
        }.logFailure("getChartTags")

    override suspend fun getMyChartTagVotes(
        songId: String,
        difficulty: Difficulty,
        platform: String,
        platformId: String,
        apiUserId: String,
        apiToken: String?
    ): Result<Set<String>> = runCatching {
        val response = phiPluginApi.getMyChartTagVotes(
            platform.trim(),
            platformId.trim(),
            apiUserId.trim(),
            apiToken?.trim()?.takeIf { it.isNotEmpty() },
            listOf(songId.trim() to difficulty.name)
        ).also { it.throwOnApiError() }
        response["data"].asArray().orEmpty().flatMap { entry ->
            val obj = entry.asObject() ?: return@flatMap emptyList()
            if (obj["songId"].asString() != songId.trim() || obj["rank"].asString() != difficulty.name) {
                return@flatMap emptyList()
            }
            fun names(key: String) = obj[key].asArray().orEmpty().mapNotNull { it.asString() }
            names("primaryTags") + names("secondaryTags") + names("tags")
        }.toSet()
    }.logFailure("getMyChartTagVotes")

    override suspend fun getB30ChartTags(records: List<BestRecord>): Result<B30ChartTagBatch> = runCatching {
        val requests = records
            .groupBy({ it.songId }, { it.difficulty.name })
            .map { (songId, ranks) -> songId to ranks.distinct() }
        if (requests.isEmpty()) return@runCatching B30ChartTagBatch(emptyMap(), emptyMap())

        val response = phiPluginApi.getChartsTagsBatch(requests).also { it.throwOnApiError() }
        val tags = mutableMapOf<String, Map<Difficulty, Map<String, Int>>>()
        val categories = mutableMapOf<String, Map<Difficulty, List<ChartTagTreeNode>>>()
        response["data"].asObject()?.forEach { (songId, byRank) ->
            tags[songId] = byRank.asObject()?.mapNotNull { (rank, voteMap) ->
                Difficulty.entries.find { it.name == rank }?.let { it to parseVoteMap(voteMap) }
            }?.toMap().orEmpty()
        }
        response["tree"].asObject()?.forEach { (songId, byRank) ->
            categories[songId] = byRank.asObject()?.mapNotNull { (rank, nodeArray) ->
                Difficulty.entries.find { it.name == rank }
                    ?.let { it to nodeArray.asArray().orEmpty().mapNotNull { node -> parseTagTreeNode(node) } }
            }?.toMap().orEmpty()
        }
        B30ChartTagBatch(tags, categories)
    }.logFailure("getB30ChartTags")

    override suspend fun voteChartTags(
        songId: String,
        difficulty: Difficulty,
        primaryTags: List<String>,
        secondaryTags: List<String>,
        platform: String,
        platformId: String,
        apiUserId: String,
        apiToken: String
    ): Result<Unit> {
        val token = apiToken.trim()
        if (token.isEmpty()) return Result.failure(IllegalStateException("缺少 API Token"))
        return runCatching {
            phiPluginApi.setChartsTag(
                platform.trim(),
                platformId.trim(),
                apiUserId.trim(),
                token,
                songId.trim(),
                difficulty.name,
                primaryTags.distinct(),
                secondaryTags.distinct()
            ).also { it.throwOnApiError() }
            chartTagTreeCache = null
        }.logFailure("voteChartTags")
    }

    override suspend fun proposeSongAlias(songId: String, alias: String, note: String?): Result<Unit> =
        runCatching<Unit> {
            val (sessionToken, _) = tokenManager.getToken()
                ?: error("需要先登录 TapTap 账号")
            phiPluginApi.proposeAlias(sessionToken, songId.trim(), alias.trim(), note?.trim())
                .also { it.throwOnApiError() }
        }.logFailure("proposeSongAlias")

    private var chartTagTreeCache: Pair<Instant, List<ChartTagTreeNode>>? = null

    private fun parseTagTreeNode(element: JsonElement?): ChartTagTreeNode? {
        val obj = element.asObject() ?: return null
        return ChartTagTreeNode(
            id = obj["id"].asLong() ?: return null,
            name = obj["name"].asString() ?: return null,
            description = obj["description"].asString(),
            sortOrder = obj["sortOrder"].asInt() ?: 0,
            voteCount = obj["voteCount"].asInt() ?: 0,
            primaryVoteCount = obj["primaryVoteCount"].asInt() ?: 0,
            secondaryVoteCount = obj["secondaryVoteCount"].asInt() ?: 0,
            children = obj["children"].asArray().orEmpty().mapNotNull { parseTagTreeNode(it) }
        )
    }

    private fun parseVoteMap(element: JsonElement?): Map<String, Int> =
        element.asObject()?.mapValues { (_, value) -> value.asInt() ?: 0 }.orEmpty()

    /**
     * phi-plugin-api returns non-2xx responses without a Ktor exception
     * (the client does not enable expectSuccess); errors arrive as
     * { "error": "..." } bodies. Convert them into exceptions here.
     */
    private fun JsonObject.throwOnApiError() {
        val error = this["error"].asString()
        if (error != null) throw IllegalStateException(error)
    }

    private val CHART_TAG_TREE_TTL = 1.hours

    override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<ReleaseInfo> =
        runCatching {
            val response = httpClient.get("https://api.github.com/repos/Kasumi-Ushio/Ushio-Prober-Phigros/releases") {
                headers.append("Accept", "application/vnd.github+json")
            }

            parseGitHubReleaseResponse(
                statusCode = response.status,
                responseText = response.bodyAsText(),
                includePreRelease = includePreRelease,
                json = json,
            ).getOrThrow()
        }.logFailure("fetchLatestRelease")

    override suspend fun fetchGameUpdateInfo(): Result<GameUpdateInfo> = runCatching {
        tapTapGameApi.fetchLatestUpdate()
    }.logFailure("fetchGameUpdateInfo")

    private suspend fun invalidateApiDetailCache() {
        apiDetailMutex.withLock { advanceApiDetailEpochLocked() }
    }

    private fun advanceApiDetailEpochLocked() {
        apiDetailEpoch++
        apiDetailCache.clear()
        apiDetailIdentity = null
    }

    private fun mapSongApiDetail(
        rank: JsonObject,
        average: JsonObject,
        history: JsonObject,
        key: ApiDetailCacheKey
    ): SongApiDetail {
        val rankData = rank["data"].asObject()
        val averageData = average["data"].asObject()
        return SongApiDetail(
            userRank = rankData?.get("userRank").asInt(),
            totalUsers = rankData?.get("totDataNum").asInt(),
            avgAcc = averageData?.get("accAvg").asFloat(),
            avgAccCount = averageData?.get("count").asInt(),
            history = parseSongHistory(history["data"], key.songId, key.difficulty)
        )
    }

    private fun parseSongHistory(
        element: JsonElement?,
        songId: String,
        difficulty: Difficulty
    ): List<SongSyncHistoryEntry> {
        val records = element.asArray()
            ?: element.asObject()?.get(difficulty.name).asArray()
            ?: return emptyList()
        return records.mapNotNull { row ->
            val values = row.asArray()?.takeIf { it.size >= 4 } ?: return@mapNotNull null
            val accuracy = values[0].asFloat() ?: return@mapNotNull null
            val score = values[1].asInt() ?: return@mapNotNull null
            val date = values[2].asString() ?: return@mapNotNull null
            SongSyncHistoryEntry(
                id = 0L,
                snapshotId = 0L,
                songId = songId,
                difficulty = difficulty.name,
                score = score,
                accuracy = accuracy,
                isFullCombo = values[3].asBoolean() ?: false,
                timestamp = runCatching { Instant.parse(date).toEpochMilliseconds() }
                    .getOrElse { Clock.System.now().toEpochMilliseconds() }
            )
        }.sortedByDescending { it.timestamp }
    }

    private fun JsonElement?.asObject(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()
    private fun JsonElement?.asArray(): JsonArray? = runCatching { this?.jsonArray }.getOrNull()
    private fun JsonElement?.asString(): String? = this?.jsonPrimitive?.contentOrNull
    private fun JsonElement?.asInt(): Int? = asString()?.toIntOrNull()
    private fun JsonElement?.asLong(): Long? = asString()?.toLongOrNull()
    private fun JsonElement?.asFloat(): Float? = asString()?.toFloatOrNull()
    private fun JsonElement?.asBoolean(): Boolean? = asString()?.toBooleanStrictOrNull()

    private fun <T> Result<T>.logFailure(action: String): Result<T> = onFailure {
        AppLogger.w("PhigrosRepo", it) { "$action failed" }
    }
}
