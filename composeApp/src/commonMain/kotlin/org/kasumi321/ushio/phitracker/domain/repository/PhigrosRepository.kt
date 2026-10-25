package org.kasumi321.ushio.phitracker.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey
import org.kasumi321.ushio.phitracker.domain.model.SongApiDetail
import org.kasumi321.ushio.phitracker.domain.model.B30ChartTagBatch
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagSongData
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo

interface PhigrosRepository {
    suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile>

    suspend fun syncSave(sessionToken: String, server: Server, mode: SyncMode): Result<SyncSaveResult>

    /**
     * Owner-aware sync used by SessionCoordinator: the commit verifies the
     * current-account pointer still equals [expectedCurrentOwner] before
     * writing, so a cancelled operation's late network result cannot write
     * back over a newer session state. The default ignores the guard so
     * lightweight test fakes can keep implementing the 3-arg form only.
     */
    suspend fun syncSave(
        sessionToken: String,
        server: Server,
        mode: SyncMode,
        expectedCurrentOwner: String?
    ): Result<SyncSaveResult> = syncSave(sessionToken, server, mode)

    fun getCachedSave(): Flow<Save?>
    fun getUserProfile(): Flow<UserProfile?>
    suspend fun saveSessionToken(token: String, server: Server)
    suspend fun getSessionToken(): Pair<String, Server>?
    suspend fun clearData()

    /**
     * Logout-clear: removes the CURRENT account's records, user, snapshots,
     * per-song history and pointer in one transaction, then clears the stored
     * token. Fails (without touching the token) when the database clear fails.
     */
    suspend fun clearCurrentAccountData(): Result<Unit> = runCatching { clearData() }

    suspend fun clearTokenSync()

    /** The account (TapTap objectId) whose data is currently displayed, if any. */
    suspend fun getCurrentAccountOwner(): String? = null

    /** Whether [ownerPlayerId] already has local score rows (inheritance check). */
    suspend fun hasLocalRecords(ownerPlayerId: String): Boolean = getCachedSave().first() != null

    /** The objectId the stored session token was issued for, if recorded. */
    suspend fun getBoundPlayerId(): String? = null

    suspend fun saveBoundPlayerId(playerId: String) {}

    suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int>
    suspend fun getTotalFullComboCount(): Int
    suspend fun getTotalPhiCount(): Int
    fun observeSyncSnapshots(): Flow<List<SyncSnapshot>>
    suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot>
    fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>>
    suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry>

    suspend fun apiTest(): Result<JsonObject>
    suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject>
    suspend fun getSongApiDetail(key: ApiDetailCacheKey): Result<SongApiDetail>
    suspend fun apiGetRksAbove(rks: Float): Result<JsonObject>
    suspend fun apiGetSaveHistory(
        platform: String,
        platformId: String,
        apiUserId: String,
        request: List<String> = emptyList()
    ): Result<JsonObject>
    suspend fun apiGetRankByUser(platform: String, platformId: String, apiUserId: String): Result<JsonObject>
    suspend fun apiGetRankByPosition(position: Int): Result<JsonObject>

    // ── Chart tags (chartsTag) ──────────────────────────────────────
    // Read endpoints are public; getMyChartTagVotes needs the platform
    // identity triplet; voteChartTags always requires an api_token.

    suspend fun getChartTagTree(): Result<List<ChartTagTreeNode>>
    suspend fun getChartTags(songId: String, difficulty: Difficulty): Result<ChartTagSongData>
    suspend fun getMyChartTagVotes(
        songId: String,
        difficulty: Difficulty,
        platform: String,
        platformId: String,
        apiUserId: String,
        apiToken: String?
    ): Result<Set<String>>
    suspend fun getB30ChartTags(records: List<BestRecord>): Result<B30ChartTagBatch>
    suspend fun voteChartTags(
        songId: String,
        difficulty: Difficulty,
        primaryTags: List<String>,
        secondaryTags: List<String>,
        platform: String,
        platformId: String,
        apiUserId: String,
        apiToken: String
    ): Result<Unit>

    // ── Alias proposals (alias-proposals) ───────────────────────────
    // Submission authenticates with the login sessionToken (the `token`
    // field); unlike chart-tag voting no api_token is involved.

    suspend fun proposeSongAlias(songId: String, alias: String, note: String?): Result<Unit>

    suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<ReleaseInfo>
    suspend fun fetchGameUpdateInfo(): Result<GameUpdateInfo>
}
