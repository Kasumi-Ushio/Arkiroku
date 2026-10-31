package org.kasumi321.ushio.phitracker.ui.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.B30ChartTagBatch
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagSongData
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository

internal object TestAssets : TextAssetReader {
    override fun readText(name: String): String = when (name) {
        "tips.txt" -> "Tip: settings"
        "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\nsong-a\tSong A\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0\nsong-b\tSong B\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0"
        "infolist.json", "notesInfo.json" -> "{}"
        else -> error("Unexpected asset: $name")
    }
}

internal object EmptyArtworkCache : StandardArtworkCache {
    override suspend fun getOrDownloadThumbnail(songId: String, url: String) = url
    override fun getThumbnailIfPresent(songId: String): String? = null
    override fun hasAllThumbnails(songIds: Iterable<String>) = false
    override fun clearThumbnails(songIds: Iterable<String>) = Unit
    override fun clearAllThumbnails() = Unit
    override suspend fun getOrDownloadStandard(songId: String, url: String) = url
    override fun getStandardIfPresent(songId: String): String? = null
    override fun clearStandard(songIds: Iterable<String>) = Unit
    override fun clearAllStandard() = Unit
}

internal class RecordingArtworkCache : StandardArtworkCache {
    val standardDownloads = mutableListOf<Pair<String, String>>()
    var clearedAllStandard = false
        private set

    override suspend fun getOrDownloadThumbnail(songId: String, url: String) = url
    override fun getThumbnailIfPresent(songId: String): String? = null
    override fun hasAllThumbnails(songIds: Iterable<String>) = false
    override fun clearThumbnails(songIds: Iterable<String>) = Unit
    override fun clearAllThumbnails() = Unit
    override suspend fun getOrDownloadStandard(songId: String, url: String): String {
        standardDownloads += songId to url
        return "/cache/$songId.png"
    }
    override fun getStandardIfPresent(songId: String): String? = null
    override fun clearStandard(songIds: Iterable<String>) = Unit
    override fun clearAllStandard() { clearedAllStandard = true }
}

internal fun b30FixtureSave(): Save = Save(
    gameRecord = mapOf(
        "song-a.0" to SongRecord("song-a.0", mapOf(Difficulty.IN to LevelRecord(990_000, 99f, false))),
        "song-b.0" to SongRecord("song-b.0", mapOf(Difficulty.IN to LevelRecord(980_000, 98f, false)))
    ),
    gameProgress = GameProgress(
        isFirstRun = false,
        legacyChapterFinished = false,
        alreadyShowCollectionTip = false,
        alreadyShowAutoUnlockINTip = false,
        completed = "",
        songUpdateInfo = 0,
        challengeModeRank = 0,
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
    user = UserSettings(false, "", "", ""),
    summary = null
)

internal class FakeSettingsRepository : SettingsRepository {
    private val themeModeState = MutableStateFlow(0)
    override val themeMode: Flow<Int> = themeModeState
    private val amoledBlackState = MutableStateFlow(false)
    override val amoledBlack: Flow<Boolean> = amoledBlackState
    private val colorSourceState = MutableStateFlow("system")
    override val themeColorSource: Flow<String> = colorSourceState
    private val seedState = MutableStateFlow(-10011977)
    override val seedColorArgb: Flow<Int> = seedState
    private val imageSeedState = MutableStateFlow<Int?>(null)
    override val themeImageSeedColorArgb: Flow<Int?> = imageSeedState
    private val imageUriState = MutableStateFlow<String?>(null)
    override val themeImageUri: Flow<String?> = imageUriState
    private val paletteState = MutableStateFlow("TonalSpot")
    override val paletteStyleName: Flow<String> = paletteState
    private val overflowState = MutableStateFlow(false)
    override val showB30Overflow: Flow<Boolean> = overflowState
    private val countState = MutableStateFlow(9)
    override val overflowCount: Flow<Int> = countState
    private val appLanguageState = MutableStateFlow("system")
    override val appLanguage: Flow<String> = appLanguageState
    private val b30CardStyleState = MutableStateFlow("classic")
    override val b30CardStyle: Flow<String> = b30CardStyleState
    private val b30ThemeFollowGlobalState = MutableStateFlow(true)
    override val b30ThemeFollowGlobal: Flow<Boolean> = b30ThemeFollowGlobalState
    private val b30ExportThemeModeState = MutableStateFlow<Int?>(null)
    override val b30ExportThemeMode: Flow<Int?> = b30ExportThemeModeState
    private val hazeEnabledState = MutableStateFlow(true)
    override val hazeBlurEnabled: Flow<Boolean> = hazeEnabledState
    private val hazeStrengthState = MutableStateFlow(0.75f)
    override val hazeBlurStrength: Flow<Float> = hazeStrengthState
    private val includeState = MutableStateFlow(false)
    override val includePreRelease: Flow<Boolean> = includeState
    private val autoState = MutableStateFlow(true)
    override val autoCheckUpdate: Flow<Boolean> = autoState
    private val autoSongDataState = MutableStateFlow(true)
    override val autoCheckSongDataUpdate: Flow<Boolean> = autoSongDataState
    private val preloadDoneState = MutableStateFlow(true)
    private val apiEnabledState = MutableStateFlow(false)
    override val apiEnabled: Flow<Boolean> = apiEnabledState
    private val useApiState = MutableStateFlow(false)
    override val useApiData: Flow<Boolean> = useApiState
    private val apiIdState = MutableStateFlow("")
    override val apiId: Flow<String> = apiIdState
    private val platformState = MutableStateFlow("")
    override val apiPlatform: Flow<String> = platformState
    private val platformIdState = MutableStateFlow("")
    override val apiPlatformId: Flow<String> = platformIdState
    private val apiTokenState = MutableStateFlow("")
    override val apiToken: Flow<String> = apiTokenState
    private val guideState = MutableStateFlow(false)
    override val crashNotificationGuideShown: Flow<Boolean> = guideState
    private val onboardingCompletedState = MutableStateFlow(false)
    override val onboardingCompleted: Flow<Boolean> = onboardingCompletedState
    private val onboardingRerunRequestedState = MutableStateFlow(false)
    override val onboardingRerunRequested: Flow<Boolean> = onboardingRerunRequestedState
    private val illustrationPreloadRequestedState = MutableStateFlow(false)
    override val illustrationPreloadRequested: Flow<Boolean> = illustrationPreloadRequestedState
    private val illustrationPreloadDeclinedState = MutableStateFlow(false)
    override val illustrationPreloadDeclined: Flow<Boolean> = illustrationPreloadDeclinedState
    private val gameUpdateCacheState = MutableStateFlow<String?>(null)
    override val gameUpdateInfoCache: Flow<String?> = gameUpdateCacheState
    private val chartVoteKeysState = MutableStateFlow<Set<String>>(emptySet())
    override val chartVoteKeys: Flow<Set<String>> = chartVoteKeysState
    val chartVoteKeyWrites = mutableListOf<String>()
    override suspend fun setThemeMode(mode: Int) { themeModeState.value = mode }
    override suspend fun setAmoledBlack(enabled: Boolean) { amoledBlackState.value = enabled }
    override suspend fun setThemeColorSource(source: String) { colorSourceState.value = source }
    override suspend fun setSeedColorArgb(argb: Int) { seedState.value = argb }
    override suspend fun setThemeImageColor(uri: String?, seedColorArgb: Int) { imageUriState.value = uri; imageSeedState.value = seedColorArgb }
    override suspend fun clearThemeImageColor() { imageUriState.value = null; imageSeedState.value = null }
    override suspend fun setPaletteStyleName(name: String) { paletteState.value = name }
    override suspend fun setShowB30Overflow(show: Boolean) { overflowState.value = show }
    override suspend fun setOverflowCount(count: Int) { countState.value = count.coerceIn(1, 30) }
    override suspend fun setAppLanguage(language: String) { appLanguageState.value = language }
    override suspend fun setB30CardStyle(style: String) { b30CardStyleState.value = style }
    override suspend fun setB30ThemeFollowGlobal(follow: Boolean) { b30ThemeFollowGlobalState.value = follow }
    override suspend fun setB30ExportThemeMode(mode: Int) { b30ExportThemeModeState.value = mode.coerceIn(1, 3) }
    override suspend fun setHazeBlurEnabled(enabled: Boolean) { hazeEnabledState.value = enabled }
    override suspend fun setHazeBlurStrength(strength: Float) { hazeStrengthState.value = strength.coerceIn(0.5f, 1.5f) }
    override suspend fun getPreloadDone() = preloadDoneState.value
    val preloadDoneWrites = mutableListOf<Boolean>()
    override suspend fun setPreloadDone(done: Boolean) {
        preloadDoneWrites += done
        preloadDoneState.value = done
    }
    override suspend fun setIncludePreRelease(enabled: Boolean) { includeState.value = enabled }
    override suspend fun setAutoCheckUpdate(enabled: Boolean) { autoState.value = enabled }
    override suspend fun setAutoCheckSongDataUpdate(enabled: Boolean) { autoSongDataState.value = enabled }
    override suspend fun setApiEnabled(enabled: Boolean) { apiEnabledState.value = enabled }
    override suspend fun setUseApiData(useApiData: Boolean) { useApiState.value = useApiData }
    override suspend fun setApiId(apiId: String) { apiIdState.value = apiId.trim() }
    override suspend fun setApiPlatform(platform: String) { platformState.value = platform.trim() }
    override suspend fun setApiPlatformId(platformId: String) { platformIdState.value = platformId.trim() }
    override suspend fun setApiToken(apiToken: String): Result<Unit> {
        apiTokenState.value = apiToken.trim()
        return Result.success(Unit)
    }
    override suspend fun setCrashNotificationGuideShown(shown: Boolean) { guideState.value = shown }
    override suspend fun setOnboardingCompleted(completed: Boolean) { onboardingCompletedState.value = completed }
    override suspend fun setOnboardingRerunRequested(requested: Boolean) { onboardingRerunRequestedState.value = requested }
    override suspend fun setIllustrationPreloadRequested(requested: Boolean) { illustrationPreloadRequestedState.value = requested }
    override suspend fun setIllustrationPreloadDeclined(declined: Boolean) { illustrationPreloadDeclinedState.value = declined }
    override suspend fun setGameUpdateInfoCache(cache: String?) { gameUpdateCacheState.value = cache }
    override suspend fun recordChartVote(chartVoteKey: String) {
        chartVoteKeyWrites += chartVoteKey
        chartVoteKeysState.value = chartVoteKeysState.value + chartVoteKey
    }
}

internal class FakePhigrosRepository : PhigrosRepository {
    var cachedSave: Save? = null
        set(value) {
            field = value
            cachedSaveFlow.value = value
        }
    private val cachedSaveFlow = MutableStateFlow<Save?>(null)
    var profile: UserProfile? = null
    var songHistory: List<SongSyncHistoryEntry> = emptyList()
    var songApiDetail: Result<org.kasumi321.ushio.phitracker.domain.model.SongApiDetail> = Result.failure(IllegalStateException("not configured"))
    var songApiDetailRequests = mutableListOf<org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey>()
    var release: Result<ReleaseInfo> = Result.failure(IllegalStateException("not configured"))
    var apiStatus: Result<JsonObject> = Result.failure(IllegalStateException("not configured"))
    var bind: Result<JsonObject> = Result.failure(IllegalStateException("not configured"))
    var chartTagTree: Result<List<ChartTagTreeNode>> = Result.failure(IllegalStateException("not configured"))
    var chartTagData: Result<ChartTagSongData> = Result.failure(IllegalStateException("not configured"))
    var chartTagDataRequests = mutableListOf<Pair<String, Difficulty>>()
    var chartTagsGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var myChartTagVotes: Result<Set<String>> = Result.success(emptySet())
    val myChartTagVoteRequests = mutableListOf<List<String>>()
    var voteChartTagsResult: Result<Unit> = Result.success(Unit)
    val voteChartTagRequests = mutableListOf<ChartTagVoteRequest>()
    override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> = Result.failure(IllegalStateException())
    override suspend fun syncSave(sessionToken: String, server: Server, mode: SyncMode): Result<SyncSaveResult> = Result.failure(IllegalStateException())
    override fun getCachedSave(): Flow<Save?> = cachedSaveFlow
    override fun getUserProfile(): Flow<UserProfile?> = flowOf(profile)
    override suspend fun saveSessionToken(token: String, server: Server) = Unit
    override suspend fun getSessionToken(): Pair<String, Server>? = null
    override suspend fun clearData() = Unit
    override suspend fun clearTokenSync() = Unit
    override suspend fun resetCredentialStorage(): Result<Unit> =
        Result.failure(UnsupportedOperationException("credential reset is not used by this test"))
    override suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int> = emptyMap()
    override suspend fun getTotalFullComboCount() = 0
    override suspend fun getTotalPhiCount() = 0
    override fun observeSyncSnapshots(): Flow<List<SyncSnapshot>> = flowOf(emptyList())
    override suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot> = emptyList()
    override fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>> = flowOf(songHistory)
    override suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry> = emptyList()
    override suspend fun apiTest() = apiStatus
    override suspend fun apiGetBindInfo(platform: String, platformId: String) = bind
    override suspend fun getSongApiDetail(key: org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey): Result<org.kasumi321.ushio.phitracker.domain.model.SongApiDetail> {
        songApiDetailRequests += key
        return songApiDetail
    }
    override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> = Result.failure(IllegalStateException())
    override suspend fun apiGetSaveHistory(platform: String, platformId: String, apiUserId: String, request: List<String>): Result<JsonObject> = Result.failure(IllegalStateException())
    override suspend fun apiGetRankByUser(platform: String, platformId: String, apiUserId: String): Result<JsonObject> = bind
    override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> = Result.failure(IllegalStateException())
    override suspend fun getChartTagTree(): Result<List<ChartTagTreeNode>> = chartTagTree

    override suspend fun getChartTags(songId: String, difficulty: Difficulty): Result<ChartTagSongData> {
        chartTagDataRequests += songId to difficulty
        chartTagsGate?.await()
        return chartTagData
    }

    override suspend fun getMyChartTagVotes(
        songId: String,
        difficulty: Difficulty,
        platform: String,
        platformId: String,
        apiUserId: String,
        apiToken: String?
    ): Result<Set<String>> {
        myChartTagVoteRequests += listOf(songId, difficulty.name, platform, platformId, apiUserId, apiToken.orEmpty())
        return myChartTagVotes
    }

    override suspend fun getB30ChartTags(records: List<BestRecord>): Result<B30ChartTagBatch> =
        Result.failure(UnsupportedOperationException())

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
        voteChartTagRequests += ChartTagVoteRequest(
            songId, difficulty, primaryTags, secondaryTags, platform, platformId, apiUserId, apiToken
        )
        return voteChartTagsResult
    }

    var proposeSongAliasResult: Result<Unit> = Result.success(Unit)
    val proposedAliases = mutableListOf<Triple<String, String, String?>>()
    override suspend fun proposeSongAlias(songId: String, alias: String, note: String?): Result<Unit> {
        proposedAliases += Triple(songId, alias, note)
        return proposeSongAliasResult
    }

    override suspend fun fetchLatestRelease(includePreRelease: Boolean) = release

    var gameUpdateInfo: Result<GameUpdateInfo> =
        Result.failure(UnsupportedOperationException())
    override suspend fun fetchGameUpdateInfo(): Result<GameUpdateInfo> = gameUpdateInfo
}

internal data class ChartTagVoteRequest(
    val songId: String,
    val difficulty: Difficulty,
    val primaryTags: List<String>,
    val secondaryTags: List<String>,
    val platform: String,
    val platformId: String,
    val apiUserId: String,
    val apiToken: String
)
