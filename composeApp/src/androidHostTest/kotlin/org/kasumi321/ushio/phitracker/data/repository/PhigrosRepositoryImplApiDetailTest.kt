package org.kasumi321.ushio.phitracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.kasumi321.ushio.phitracker.data.api.PhiPluginApi
import org.kasumi321.ushio.phitracker.data.api.TapTapApiClient
import org.kasumi321.ushio.phitracker.data.api.TapTapGameApi
import org.kasumi321.ushio.phitracker.data.database.AppDatabase
import org.kasumi321.ushio.phitracker.data.parser.AesDecryptor
import org.kasumi321.ushio.phitracker.data.parser.SaveParser
import org.kasumi321.ushio.phitracker.data.platform.SecureKeyValueStorage
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.TokenManager
import org.kasumi321.ushio.phitracker.domain.usecase.GetChartTagsUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.ProposeSongAliasUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.VoteChartTagsUseCase
import org.kasumi321.ushio.phitracker.data.platform.NoOpStandardArtworkCache
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.ui.settings.FakeSettingsRepository
import org.kasumi321.ushio.phitracker.ui.settings.TestAssets
import org.kasumi321.ushio.phitracker.ui.song.SongDetailViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class PhigrosRepositoryImplApiDetailTest {
    private lateinit var database: AppDatabase
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).setQueryCoroutineContext(Dispatchers.Default).build()
    }

    @AfterTest
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun sameIdentityReusesSuccessAcrossCallersAndMapsCompleteState() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        val repository = repository(requests)
        val key = key()

        // When
        val first = repository.getSongApiDetail(key).getOrThrow()
        val second = repository.getSongApiDetail(key).getOrThrow()

        // Then
        assertEquals(first, second)
        assertEquals(3, requests.size)
        assertEquals(7, first.userRank)
        assertEquals(12, first.totalUsers)
        assertEquals(98.5f, first.avgAcc)
        assertEquals(6, first.avgAccCount)
        assertEquals(1, first.history.size)
    }

    @Test
    fun sameIdentityReusesDataAcrossDistinctRouteViewModelInstances() = runTest(dispatcher) {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        val repository = repository(requests)
        val settings = FakeSettingsRepository().apply {
            setApiEnabled(true)
            setUseApiData(true)
            setApiId("api-user-a")
            setApiPlatform("taptap")
            setApiPlatformId("account-a")
        }
        val first = viewModel(repository, settings)
        advanceUntilIdle()
        first.loadSongApiDetail(Difficulty.IN)
        first.uiState.first { it.apiDetails[Difficulty.IN]?.isLoading == false }

        // When
        val second = viewModel(repository, settings)
        advanceUntilIdle()
        second.loadSongApiDetail(Difficulty.IN)
        second.uiState.first { it.apiDetails[Difficulty.IN]?.isLoading == false }

        // Then
        assertEquals(7, first.getSongApiDetail(Difficulty.IN).userRank)
        assertEquals(first.getSongApiDetail(Difficulty.IN), second.getSongApiDetail(Difficulty.IN))
        assertEquals(3, requests.size)
    }

    @Test
    fun platformIdChangeRefetchesAllThreeForAccountB() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        val repository = repository(requests)

        // When
        repository.getSongApiDetail(key()).getOrThrow()
        repository.getSongApiDetail(key(platformId = "account-b")).getOrThrow()

        // Then
        assertEquals(6, requests.size)
        assertTrue(requests.any { it.body.contains("\"platform_id\":\"account-b\"") })
    }

    @Test
    fun platformChangeRefetchesAllThree() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        val repository = repository(requests)

        // When
        repository.getSongApiDetail(key()).getOrThrow()
        repository.getSongApiDetail(key(platform = "discord")).getOrThrow()

        // Then
        assertEquals(6, requests.size)
        assertTrue(requests.any { it.body.contains("\"platform\":\"discord\"") })
    }

    @Test
    fun rksRangeChangeRefetchesAllThreeAsOneCompositeMiss() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        val repository = repository(requests)

        // When
        repository.getSongApiDetail(key()).getOrThrow()
        repository.getSongApiDetail(key(minRks = 15f, maxRks = 15.03f)).getOrThrow()

        // Then
        assertEquals(6, requests.size)
        assertTrue(requests.any { it.body.contains("\"minRks\":15.0") && it.body.contains("\"maxRks\":15.03") })
    }

    @Test
    fun accountAToBTokenReplacementCannotReuseAccountACache() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        val repository = repository(requests)
        repository.saveSessionToken("account-a-token", Server.CN)
        repository.getSongApiDetail(key()).getOrThrow()

        // When
        repository.saveSessionToken("account-b-token", Server.CN)
        repository.getSongApiDetail(key()).getOrThrow()

        // Then
        assertEquals(6, requests.size)
    }

    @Test
    fun failedRequestIsNotCachedAndRetryReissuesAllNeededWork() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        var failRank = true
        val repository = repository(requests) { request ->
            if (request.path.endsWith("/get/scoreList/user") && failRank) {
                failRank = false
                throw IOException("offline")
            }
            responseFor(request.path)
        }

        // When
        val first = repository.getSongApiDetail(key())
        val second = repository.getSongApiDetail(key())

        // Then
        assertTrue(first.isFailure)
        assertTrue(second.isSuccess)
        assertEquals(4, requests.size)
    }

    @Test
    fun averageFailureIsNotCachedAndRetryReissuesCompleteDetailRequest() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        var failAverage = true
        val repository = repository(requests) { request ->
            if (request.path.endsWith("/get/scoreList/songAccAvg") && failAverage) {
                failAverage = false
                throw IOException("average unavailable")
            }
            responseFor(request.path)
        }

        // When
        val first = repository.getSongApiDetail(key())
        val second = repository.getSongApiDetail(key())

        // Then
        assertTrue(first.isFailure)
        assertTrue(second.isSuccess)
        assertEquals(5, requests.size)
        assertEquals(2, requests.count { it.path.endsWith("/get/scoreList/user") })
        assertEquals(2, requests.count { it.path.endsWith("/get/scoreList/songAccAvg") })
        assertEquals(1, requests.count { it.path.endsWith("/get/history/record") })
    }

    @Test
    fun historyFailureIsNotCachedAndRetryReissuesCompleteDetailRequest() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        var failHistory = true
        val repository = repository(requests) { request ->
            if (request.path.endsWith("/get/history/record") && failHistory) {
                failHistory = false
                throw IOException("history unavailable")
            }
            responseFor(request.path)
        }

        // When
        val first = repository.getSongApiDetail(key())
        val second = repository.getSongApiDetail(key())

        // Then
        assertTrue(first.isFailure)
        assertTrue(second.isSuccess)
        assertEquals(6, requests.size)
        assertEquals(2, requests.count { it.path.endsWith("/get/scoreList/user") })
        assertEquals(2, requests.count { it.path.endsWith("/get/scoreList/songAccAvg") })
        assertEquals(2, requests.count { it.path.endsWith("/get/history/record") })
    }

    @Test
    fun concurrentDifferentIdentitiesRejectStaleAccountACacheWriteBack() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        val accountARankStarted = CompletableDeferred<Unit>()
        val accountBRankStarted = CompletableDeferred<Unit>()
        val releaseAccountARank = CompletableDeferred<Unit>()
        val releaseAccountBRank = CompletableDeferred<Unit>()
        val repository = repository(requests) { request ->
            val isAccountA = request.body.contains("\"platform_id\":\"account-a\"")
            when {
                request.path.endsWith("/get/scoreList/user") && isAccountA -> {
                    accountARankStarted.complete(Unit)
                    releaseAccountARank.await()
                    "{\"data\":{\"userRank\":7,\"totDataNum\":12}}"
                }
                request.path.endsWith("/get/scoreList/user") -> {
                    accountBRankStarted.complete(Unit)
                    releaseAccountBRank.await()
                    "{\"data\":{\"userRank\":22,\"totDataNum\":33}}"
                }
                else -> responseFor(request.path)
            }
        }
        val accountAKey = key(platformId = " account-a ")
        val accountBKey = key(platformId = " account-b ")

        // When
        val accountAInFlight = async { repository.getSongApiDetail(accountAKey) }
        accountARankStarted.await()
        val accountBInFlight = async { repository.getSongApiDetail(accountBKey) }
        accountBRankStarted.await()
        releaseAccountBRank.complete(Unit)
        val accountB = accountBInFlight.await().getOrThrow()
        releaseAccountARank.complete(Unit)
        val accountA = accountAInFlight.await().getOrThrow()
        val cachedAccountB = repository.getSongApiDetail(accountBKey).getOrThrow()
        val refetchedAccountA = repository.getSongApiDetail(accountAKey).getOrThrow()

        // Then
        assertEquals(22, accountB.userRank)
        assertEquals(7, accountA.userRank)
        assertEquals(22, cachedAccountB.userRank)
        assertEquals(7, refetchedAccountA.userRank)
        assertEquals(9, requests.size)
        assertEquals(3, requests.count { it.path.endsWith("/get/scoreList/user") })
        assertEquals(3, requests.count { it.path.endsWith("/get/scoreList/songAccAvg") })
        assertEquals(3, requests.count { it.path.endsWith("/get/history/record") })
        assertEquals(
            2,
            requests.count {
                it.path.endsWith("/get/scoreList/user") && it.body.contains("\"platform_id\":\"account-a\"")
            }
        )
        assertEquals(
            1,
            requests.count {
                it.path.endsWith("/get/scoreList/user") && it.body.contains("\"platform_id\":\"account-b\"")
            }
        )
    }

    @Test
    fun invalidationDuringInFlightPreventsStaleWriteBack() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        val rankStarted = CompletableDeferred<Unit>()
        val releaseRank = CompletableDeferred<Unit>()
        var blockFirstRank = true
        val repository = repository(requests) { request ->
            if (request.path.endsWith("/get/scoreList/user") && blockFirstRank) {
                blockFirstRank = false
                rankStarted.complete(Unit)
                releaseRank.await()
            }
            responseFor(request.path)
        }

        // When
        val stale = async { repository.getSongApiDetail(key()) }
        rankStarted.await()
        repository.saveSessionToken("replacement", Server.CN)
        releaseRank.complete(Unit)
        assertTrue(stale.await().isSuccess)
        repository.getSongApiDetail(key()).getOrThrow()

        // Then
        assertEquals(6, requests.size)
    }

    @Test
    fun tokenClearLogoutAndLocalClearEachInvalidateSuccessfulCache() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        val repository = repository(requests)

        // When / Then
        repository.getSongApiDetail(key()).getOrThrow()
        repository.clearTokenSync()
        repository.getSongApiDetail(key()).getOrThrow()
        assertEquals(6, requests.size)

        repository.clearData()
        repository.getSongApiDetail(key()).getOrThrow()
        assertEquals(9, requests.size)

        repository.saveSessionToken("new-token", Server.CN)
        repository.getSongApiDetail(key()).getOrThrow()
        assertEquals(12, requests.size)
    }

    @Test
    fun malformedSuccessfulPayloadIsSafeAndDoesNotInventValues() = runTest {
        // Given
        val requests = mutableListOf<CapturedRequest>()
        val repository = repository(requests) { "{\"data\":{\"unexpected\":true}}" }

        // When
        val result = repository.getSongApiDetail(key()).getOrThrow()

        // Then
        assertNull(result.userRank)
        assertNull(result.avgAcc)
        assertTrue(result.history.isEmpty())
        assertEquals(3, requests.size)
    }

    private fun repository(
        requests: MutableList<CapturedRequest>,
        response: suspend (CapturedRequest) -> String = { request -> responseFor(request.path) }
    ): PhigrosRepositoryImpl {
        val json = Json { ignoreUnknownKeys = true }
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(json) }
            engine {
                addHandler { request ->
                    val path = request.url.encodedPath
                    val capturedRequest = CapturedRequest(path, request.body.toByteArray().decodeToString())
                    requests += capturedRequest
                    jsonResponse(response(capturedRequest))
                }
            }
        }
        return PhigrosRepositoryImpl(
            apiClient = TapTapApiClient(client),
            phiPluginApi = PhiPluginApi(client),
            tapTapGameApi = TapTapGameApi(client),
            httpClient = client,
            saveParser = SaveParser(AesDecryptor()),
            database = database,
            recordDao = database.recordDao(),
            userDao = database.userDao(),
            syncSnapshotDao = database.syncSnapshotDao(),
            songSyncHistoryDao = database.songSyncHistoryDao(),
            currentAccountDao = database.currentAccountDao(),
            tokenManager = TokenManager(FakeStorage()),
            json = json,
            songDataProvider = SongDataProvider(assetReader = EmptySongAssets)
        )
    }

    private fun viewModel(
        repository: PhigrosRepositoryImpl,
        settings: FakeSettingsRepository
    ) = SongDetailViewModel(
        songId = "song-a.0",
        initialDifficulty = Difficulty.IN,
        repository = repository,
        settingsRepository = settings,
        songDataProvider = SongDataProvider(assetReader = TestAssets),
        illustrationUriResolver = IllustrationUriResolver(NoOpStandardArtworkCache, IllustrationProvider()),
        getChartTagsUseCase = GetChartTagsUseCase(repository),
        voteChartTagsUseCase = VoteChartTagsUseCase(repository),
        proposeSongAliasUseCase = ProposeSongAliasUseCase(repository)
    )

    private fun MockRequestHandleScope.jsonResponse(body: String) = respond(
        content = body,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )

    private fun key(
        platform: String = " taptap ",
        platformId: String = " account-a ",
        apiUserId: String = " api-user-a ",
        minRks: Float = 14.985f,
        maxRks: Float = 15.015f
    ) = ApiDetailCacheKey(platform, platformId, apiUserId, " song-a.0 ", Difficulty.IN, minRks, maxRks)

    private data class CapturedRequest(val path: String, val body: String)

    private class FakeStorage : SecureKeyValueStorage {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private object EmptySongAssets : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT"
            "infolist.json", "notesInfo.json" -> "{}"
            else -> error("Unexpected asset $name")
        }
    }

    private companion object {
        fun responseFor(path: String): String = when {
            path.endsWith("/get/scoreList/user") -> "{\"data\":{\"userRank\":7,\"totDataNum\":12}}"
            path.endsWith("/get/scoreList/songAccAvg") -> "{\"data\":{\"accAvg\":98.5,\"count\":6}}"
            path.endsWith("/get/history/record") -> "{\"data\":[[99.0,990000,\"2026-01-01T00:00:00Z\",true]]}"
            else -> error("Unexpected path $path")
        }
    }
}
