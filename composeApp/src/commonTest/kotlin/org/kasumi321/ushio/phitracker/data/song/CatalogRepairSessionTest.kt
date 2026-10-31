package org.kasumi321.ushio.phitracker.data.song

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataUpdater
import org.kasumi321.ushio.phitracker.data.common.CatalogDownloadException
import org.kasumi321.ushio.phitracker.data.common.CatalogStorageException
import org.kasumi321.ushio.phitracker.data.common.StagedRollbackException
import org.kasumi321.ushio.phitracker.data.common.StagedUpdateBlockedException
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The app-scoped repair session is the single owner of startup repair,
 * reset+redownload and the bundled read-only fallback. These tests pin the
 * properties the gate relies on: a repair marker without a backup is never
 * treated as ready, runs are de-duplicated and survive caller/process death,
 * both datasets are attempted, reset reconciles illustrations by default, and
 * failure categories never expose transport detail.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogRepairSessionTest {

    private val fs = FileSystem.SYSTEM
    private val dispatcher = StandardTestDispatcher()
    private lateinit var base: Path
    private lateinit var paths: PlatformPaths
    private lateinit var sessionScope: CoroutineScope

    private val songHeader =
        "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
    private val bundledInfo = songHeader + "test\tBundled Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
    private val freshInfo = songHeader + "new\tNew Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
    private val collectionHeader =
        "folder,folder_sub,cover,key,sub_index,name,date,supervisor,category,content,properties"
    private val bundledCsv = collectionHeader + "\n" +
        "chapter,,cover.jpg,key1,1,Item,2024/01/01,Lead,nonsense,content,"

    private val bundledAssets: Map<String, String>
        get() = mapOf(
            "info.csv" to bundledInfo,
            "infolist.json" to "{}",
            "notesInfo.json" to "{}",
            "nicklist.yaml" to "{}",
            "collection_zh_cn.csv" to bundledCsv,
            "collection_version.txt" to "1.0.0"
        )

    @BeforeTest
    fun setUp() {
        base = "/tmp/catalog-repair-session-${hashCode()}-${Random.nextLong().toString(16)}".toPath()
        fs.createDirectories(base)
        fs.createDirectories(base / "cache")
        paths = PlatformPaths(filesDir = base.toString(), cacheDir = (base / "cache").toString())
        // The real session runs on its own dispatcher; downloads complete on
        // Ktor's engine threads, so tests await the terminal state instead of
        // relying on virtual-time advancement for that part.
        sessionScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(dispatcher.scheduler))
    }

    @AfterTest
    fun tearDown() {
        sessionScope.cancel()
        if (::base.isInitialized) fs.deleteRecursively(base)
    }

    private fun songDataDir() = base / "song_data"
    private fun repairMarker() = songDataDir() / "repair-required.marker"
    private fun fallbackMarker() = songDataDir() / "bundled-fallback.marker"
    private fun collectibleRepairMarker() = base / "collectible_data" / "repair-required.marker"
    private fun collectibleFallbackMarker() = base / "collectible_data" / "bundled-fallback.marker"
    private fun collectibleDataDir() = base / "collectible_data"
    private fun collectibleBackupDir() = base / "collectible_data_backup"
    private fun songBackupDir() = base / "song_data_backup"
    private fun quarantineDir() = base / "song_data_quarantine"

    private fun writeSongBaseline(info: String = freshInfo) {
        fs.createDirectories(songDataDir())
        SongDataUpdater.FILE_NAMES.forEach { name ->
            fs.write(songDataDir() / name) { writeUtf8(if (name == "info.csv") info else "{}") }
        }
    }

    private fun writeCollectibleBaseline() {
        fs.createDirectories(collectibleDataDir())
        CollectibleDataUpdater.FILE_NAMES.forEach { name ->
            fs.write(collectibleDataDir() / name) {
                writeUtf8(if (name == "collection_version.txt") "1.0.0" else bundledCsv)
            }
        }
    }

    private fun assetReader(): TextAssetReader = object : TextAssetReader {
        override fun readText(name: String): String =
            bundledAssets[name] ?: error("Asset not found: $name")
    }

    private class RecordingCache : StandardArtworkCache {
        val thumbnails = mutableListOf<String>()
        val clearedThumbnails = mutableListOf<String>()
        var failThumbnails = false

        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
            thumbnails += songId
            if (failThumbnails) error("injected illustration failure")
            return "file://thumb/$songId"
        }

        override fun getThumbnailIfPresent(songId: String): String? = null
        override fun hasAllThumbnails(songIds: Iterable<String>) = false
        override fun clearThumbnails(songIds: Iterable<String>) {
            clearedThumbnails += songIds
        }

        override fun clearAllThumbnails() = Unit
        override suspend fun getOrDownloadStandard(songId: String, url: String) = "file://std/$songId"
        override fun getStandardIfPresent(songId: String): String? = null
        override fun clearStandard(songIds: Iterable<String>) = Unit
        override fun clearAllStandard() = Unit
    }

    private class Harness(
        val session: CatalogRepairSession,
        val provider: SongDataProvider,
        val collectibleProvider: CollectibleDataProvider,
        val cache: RecordingCache,
        val requested: MutableList<String>,
        val clearedUrls: MutableList<List<String>>
    )

    private fun createHarness(
        failCollectible: () -> Boolean = { false },
        gate: CompletableDeferred<Unit>? = null,
        scope: CoroutineScope = sessionScope,
        extraFreshInfo: String = "",
        songUpdaterFactory: (HttpClient, SongDataProvider) -> SongDataUpdater = { client, provider ->
            SongDataUpdater(client, paths, provider)
        },
        collectibleUpdaterFactory: (HttpClient, CollectibleDataProvider) -> CollectibleDataUpdater =
            { client, provider -> CollectibleDataUpdater(client, paths, provider) }
    ): Harness {
        val requested = mutableListOf<String>()
        val clearedUrls = mutableListOf<List<String>>()
        val engine = MockEngine { request ->
            val name = request.url.encodedPath.substringAfterLast('/')
            requested += name
            gate?.await()
            if (failCollectible() && (name.startsWith("collection_") || name == "version.txt")) {
                return@MockEngine respondError(HttpStatusCode.InternalServerError)
            }
            val body = when (name) {
                "info.csv" -> freshInfo + extraFreshInfo
                "version.txt" -> "9.9.9"
                "collection_zh_cn.csv" -> bundledCsv
                else -> "{}"
            }
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val client = HttpClient(engine)
        val provider = SongDataProvider(assetReader = assetReader(), paths = paths)
        val collectibleProvider = CollectibleDataProvider(assetReader = assetReader(), paths = paths)
        val cache = RecordingCache()
        val coordinator = SongDataUpdateCoordinator(
            songDataUpdater = songUpdaterFactory(client, provider),
            songDataProvider = provider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            artworkFileCache = cache,
            collectibleDataUpdater = collectibleUpdaterFactory(client, collectibleProvider),
            thumbnailPreloader = object : IllustrationThumbnailPreloader {
                override suspend fun preload(url: String): Result<Unit> = Result.success(Unit)
            },
            clearCacheUrls = { clearedUrls += it }
        )
        return Harness(
            session = CatalogRepairSession(coordinator, scope),
            provider = provider,
            collectibleProvider = collectibleProvider,
            cache = cache,
            requested = requested,
            clearedUrls = clearedUrls
        )
    }

    private fun writeBlockedSongScene() {
        fs.createDirectories(songBackupDir())
        fs.write(songBackupDir() / "info.csv") { writeUtf8("garbage") }
        fs.write(songBackupDir() / "transaction.state") { writeUtf8("COMMITTING\n") }
        fs.write(songBackupDir() / "prestate.txt") { writeUtf8("") }
    }

    private fun writeMarker(path: Path = repairMarker()) {
        fs.createDirectories(path.parent!!)
        fs.write(path) { writeUtf8("repair-required\n") }
    }

    private suspend fun Harness.awaitTerminal(): CatalogRepairState = session.state.first {
        it.status != CatalogRepairStatus.Running &&
            it.status != CatalogRepairStatus.Cancelling &&
            it.status != CatalogRepairStatus.Idle
    }

    @Test
    fun markerWithoutBackupIsRepairedBeforeTheAppBecomesReady() = runTest(dispatcher) {
        writeMarker()
        val harness = createHarness()

        harness.session.ensureStartupRepair()
        runCurrent()
        assertTrue(harness.session.state.value.blocking, "the gate stays blocked while repair runs")

        harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Succeeded, harness.session.state.value.status)
        assertFalse(harness.session.state.value.blocking, "only a completed repair unblocks the app")
        assertFalse(harness.session.state.value.degraded)
        assertFalse(fs.exists(repairMarker()), "a full download discharges the marker")
        assertEquals("New Song", harness.provider.getSongs()["new.0"]?.name)
        assertTrue(harness.requested.contains("info.csv"))
    }

    @Test
    fun startupWithoutPendingWorkBecomesReadyWithoutNetwork() = runTest(dispatcher) {
        val harness = createHarness()

        harness.session.ensureStartupRepair()
        harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Succeeded, harness.session.state.value.status)
        assertFalse(harness.session.state.value.blocking)
        assertTrue(harness.requested.isEmpty(), "no pending work must not probe the network")
    }

    @Test
    fun runningRepairExposesProgressAndIgnoresDuplicateStarts() = runTest(dispatcher) {
        writeMarker()
        val gate = CompletableDeferred<Unit>()
        val harness = createHarness(gate = gate)

        harness.session.ensureStartupRepair()
        runCurrent()

        assertEquals(CatalogRepairStatus.Running, harness.session.state.value.status)
        assertEquals(CatalogRepairStage.Files, harness.session.state.value.stage)
        assertEquals(4, harness.session.state.value.total, "file progress is published before the first download")

        // A second entry point must not start a parallel download.
        harness.session.startUpdate()
        harness.session.startResetAndRedownload()
        runCurrent()

        gate.complete(Unit)
        harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Succeeded, harness.session.state.value.status)
        assertEquals(
            1,
            harness.requested.count { it == "info.csv" },
            "duplicate entry points must not download the same dataset twice"
        )
    }

    @Test
    fun cancelledProcessLeavesMarkerAndANewSessionFinishesTheRepair() = runTest(dispatcher) {
        writeMarker()
        val gate = CompletableDeferred<Unit>()
        val abandonedScope = CoroutineScope(SupervisorJob() + dispatcher)
        val first = createHarness(gate = gate, scope = abandonedScope)
        first.session.ensureStartupRepair()
        runCurrent()
        assertEquals(CatalogRepairStatus.Running, first.session.state.value.status)

        // Simulate process death: the owning scope disappears mid-download.
        abandonedScope.cancel()
        advanceUntilIdle()
        assertTrue(fs.exists(repairMarker()), "the obligation survives the cancelled run")

        val second = createHarness()
        second.session.ensureStartupRepair()
        second.awaitTerminal()

        assertEquals(CatalogRepairStatus.Succeeded, second.session.state.value.status)
        assertFalse(fs.exists(repairMarker()))
        assertEquals("New Song", second.provider.getSongs()["new.0"]?.name)
    }

    @Test
    fun oneDatasetFailureKeepsTheMarkerAndRetryCompletesBoth() = runTest(dispatcher) {
        writeMarker()
        var failCollectible = true
        val harness = createHarness(failCollectible = { failCollectible })

        harness.session.ensureStartupRepair()
        harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Failed, harness.session.state.value.status)
        assertEquals(CatalogRepairFailure.Network, harness.session.state.value.failure)
        assertTrue(harness.session.state.value.blocking, "a failed repair keeps the gate blocked")
        assertTrue(fs.exists(repairMarker()), "the other dataset still owes its download")
        assertTrue(fs.exists(songDataDir() / "info.csv"), "the committed song dataset is not rolled back")

        failCollectible = false
        harness.session.retry()
        harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Succeeded, harness.session.state.value.status)
        assertFalse(fs.exists(repairMarker()))
        assertEquals(
            "9.9.9",
            fs.read(base / "collectible_data" / "collection_version.txt") { readUtf8() }
        )
    }

    @Test
    fun resetRedownloadsAndReconcilesIllustrationsByDefault() = runTest(dispatcher) {
        writeBlockedSongScene()
        val harness = createHarness()

        harness.session.startResetAndRedownload()
        harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Succeeded, harness.session.state.value.status)
        assertEquals("New Song", harness.provider.getSongs()["new.0"]?.name)
        assertTrue(
            harness.cache.thumbnails.any { it.startsWith("new") },
            "newly added songs are reconciled with illustration downloads"
        )
        assertTrue(harness.clearedUrls.isNotEmpty(), "removed songs release their cached URLs")
    }

    @Test
    fun blockedSceneNeverBecomesReadyAndBundledFallbackKeepsTheAppUsable() = runTest(dispatcher) {
        writeBlockedSongScene()
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(freshInfo) }
        val harness = createHarness()

        harness.session.ensureStartupRepair()
        harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Failed, harness.session.state.value.status)
        assertEquals(CatalogRepairFailure.Scene, harness.session.state.value.failure)
        assertTrue(harness.session.state.value.blocking, "a blocked scene keeps the gate blocked")

        harness.session.useBundledFallback()
        harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Degraded, harness.session.state.value.status)
        assertFalse(harness.session.state.value.blocking)
        assertTrue(harness.session.state.value.degraded)
        assertEquals(
            "Bundled Song",
            harness.provider.getSongs()["test.0"]?.name,
            "the provider reads bundled assets only"
        )
        assertFalse(fs.exists(songDataDir() / "info.csv"), "downloaded data is set aside")
        assertTrue(fs.exists(quarantineDir() / "info.csv"))
        assertTrue(fs.exists(repairMarker()))
        assertTrue(fs.exists(fallbackMarker()))
        assertFalse(fs.exists(songBackupDir()), "the unrecoverable scene is dropped, never replayed")

        harness.session.startUpdate()
        harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Succeeded, harness.session.state.value.status)
        assertFalse(harness.session.state.value.degraded)
        assertEquals("New Song", harness.provider.getSongs()["new.0"]?.name)
        assertFalse(fs.exists(repairMarker()))
        assertFalse(fs.exists(fallbackMarker()))
        assertFalse(fs.exists(quarantineDir()), "a successful update discards the quarantined copy")
    }

    @Test
    fun manualUpdateThatFindsABlockedSceneReBlocksTheGate() = runTest(dispatcher) {
        writeBlockedSongScene()
        val harness = createHarness()

        harness.session.startUpdate()
        harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Failed, harness.session.state.value.status)
        assertEquals(CatalogRepairFailure.Scene, harness.session.state.value.failure)
        assertTrue(
            harness.session.state.value.blocking,
            "mixed files found by a manual update must block the provider again"
        )
        assertTrue(fs.exists(repairMarker()))
    }

    @Test
    fun runningRepairCanBeCancelledIntoTrustedBundledFallback() = runTest(dispatcher) {
        writeMarker()
        val gate = CompletableDeferred<Unit>()
        val harness = createHarness(gate = gate)
        harness.session.ensureStartupRepair()
        runCurrent()
        assertEquals(CatalogRepairStatus.Running, harness.session.state.value.status)

        harness.session.useBundledFallback()
        harness.awaitTerminal()

        val state = harness.session.state.value
        assertEquals(CatalogRepairStatus.Degraded, state.status)
        assertFalse(state.blocking)
        assertTrue(state.degraded)
        assertTrue(fs.exists(repairMarker()), "cancellation keeps the repair obligation")
        assertTrue(fs.exists(fallbackMarker()))
        assertEquals("Bundled Song", harness.provider.getSongs()["test.0"]?.name)
    }

    @Test
    fun resetNetworkFailureReblocksEvenAfterSuccessfulNonBlockingUpdate() = runTest(dispatcher) {
        var failCollectible = false
        val harness = createHarness(failCollectible = { failCollectible })
        harness.session.startUpdate()
        harness.awaitTerminal()
        assertFalse(harness.session.state.value.blocking)
        writeBlockedSongScene()
        failCollectible = true

        harness.session.startResetAndRedownload()
        harness.awaitTerminal()

        val state = harness.session.state.value
        assertEquals(CatalogRepairStatus.Failed, state.status)
        assertEquals(CatalogRepairFailure.Network, state.failure)
        assertTrue(state.blocking, "reset discarded the scene, so network failure cannot fall through")
        assertTrue(fs.exists(repairMarker()))
    }

    @Test
    fun dismissingResultDoesNotDismissTheNextRun() = runTest(dispatcher) {
        val harness = createHarness()
        harness.session.startUpdate()
        harness.awaitTerminal()
        val firstGeneration = harness.session.state.value.resultGeneration

        harness.session.dismissResult()
        assertTrue(harness.session.state.value.resultDismissed)
        harness.session.startUpdate()
        harness.awaitTerminal()

        assertTrue(harness.session.state.value.resultGeneration > firstGeneration)
        assertFalse(harness.session.state.value.resultDismissed)
    }

    @Test
    fun degradedUpdateFailureKeepsFilesystemAndIndicatorConsistent() = runTest(dispatcher) {
        writeBlockedSongScene()
        var failCollectible = false
        val harness = createHarness(failCollectible = { failCollectible })
        harness.session.ensureStartupRepair()
        harness.awaitTerminal()
        harness.session.useBundledFallback()
        harness.awaitTerminal()
        assertTrue(harness.session.state.value.degraded)

        failCollectible = true
        harness.session.startUpdate()
        val failed = harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Failed, failed.status)
        assertTrue(failed.degraded, "a failed online retry must not erase the actual fallback state")
        assertFalse(fs.exists(fallbackMarker()), "the song dataset committed and left fallback")
        assertTrue(fs.exists(collectibleFallbackMarker()), "the collectible dataset remains degraded")
        assertEquals("New Song", harness.provider.getSongs()["new.0"]?.name)
        assertTrue(fs.exists(songDataDir() / "info.csv"), "the committed dataset is not rolled back")

        failCollectible = false
        harness.session.startUpdate()
        val recovered = harness.awaitTerminal()
        assertEquals(CatalogRepairStatus.Succeeded, recovered.status)
        assertFalse(recovered.degraded)
        assertFalse(fs.exists(fallbackMarker()))
    }

    @Test
    fun partialIllustrationSuccessRetainsAddedSongNames() = runTest(dispatcher) {
        val harness = createHarness()
        harness.cache.failThumbnails = true

        harness.session.startUpdate()
        val result = harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Succeeded, result.status)
        assertEquals(listOf("New Song"), result.addedSongNames)
        assertEquals(1, result.partialIllustrationFailures)
    }

    @Test
    fun noAddedSongsHasAnEmptyResultWithoutPartialFailures() = runTest(dispatcher) {
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(freshInfo) }
        val harness = createHarness()

        harness.session.startUpdate()
        val result = harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Succeeded, result.status)
        assertEquals(emptyList(), result.addedSongNames)
        assertEquals(0, result.partialIllustrationFailures)
    }

    @Test
    fun partialIllustrationSuccessRetainsEveryAddedSongName() = runTest(dispatcher) {
        val secondSong = "\nsecond\tSecond Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val harness = createHarness(extraFreshInfo = secondSong)
        harness.cache.failThumbnails = true

        harness.session.startUpdate()
        val result = harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Succeeded, result.status)
        assertEquals(listOf("New Song", "Second Song"), result.addedSongNames)
        assertEquals(2, result.partialIllustrationFailures)
    }

    @Test
    fun nonCooperativeFailureCannotOverwriteFallbackOrFollowingReset() = runTest(dispatcher) {
        val markStarted = CompletableDeferred<Unit>()
        val releaseMark = CompletableDeferred<Unit>()
        var failCollectible = true
        val harness = createHarness(
            failCollectible = { failCollectible },
            songUpdaterFactory = { client, provider ->
                object : SongDataUpdater(client, paths, provider) {
                    override suspend fun markRepairRequired(): Result<Unit> {
                        markStarted.complete(Unit)
                        withContext(NonCancellable) { releaseMark.await() }
                        return super.markRepairRequired()
                    }
                }
            }
        )
        harness.session.startResetAndRedownload()
        markStarted.await()

        harness.session.useBundledFallback()
        assertEquals(CatalogRepairStatus.Running, harness.session.state.value.status)
        assertEquals(CatalogRepairEntryPoint.Recovery, harness.session.state.value.resultOwner)
        releaseMark.complete(Unit)
        val degraded = harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Degraded, degraded.status)
        assertTrue(degraded.degraded)
        assertTrue(fs.exists(fallbackMarker()))
        assertEquals("Bundled Song", harness.provider.getSongs()["test.0"]?.name)

        failCollectible = false
        harness.session.startResetAndRedownload()
        val reset = harness.awaitTerminal()
        assertEquals(CatalogRepairStatus.Succeeded, reset.status)
        assertFalse(reset.degraded)
        assertFalse(fs.exists(fallbackMarker()))
    }

    @Test
    fun lateProgressFromReplacedRunCannotChangeNewRunState() = runTest(dispatcher) {
        val updateStarted = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        val harness = createHarness(
            songUpdaterFactory = { client, provider ->
                object : SongDataUpdater(client, paths, provider) {
                    override suspend fun updateAll(
                        onProgress: (Int, Int, String) -> Unit
                    ): Result<Unit> {
                        onProgress(1, 4, "old")
                        updateStarted.complete(Unit)
                        withContext(NonCancellable) { releaseUpdate.await() }
                        onProgress(3, 4, "late")
                        return Result.failure(CatalogDownloadException())
                    }
                }
            }
        )
        harness.session.startUpdate(CatalogRepairEntryPoint.Home)
        updateStarted.await()

        harness.session.useBundledFallback()
        releaseUpdate.complete(Unit)
        val result = harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Degraded, result.status)
        assertEquals(CatalogRepairEntryPoint.Recovery, result.resultOwner)
        assertEquals("", result.currentItem)
        assertEquals(0, result.completed)
        assertTrue(fs.exists(fallbackMarker()))
    }

    @Test
    fun blockingCancellationFinishesOwnedMarkerWriteBeforeAnyNewRun() = runTest(dispatcher) {
        val updateStarted = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        val markStarted = CompletableDeferred<Unit>()
        val releaseMark = CompletableDeferred<Unit>()
        var updateCalls = 0
        val harness = createHarness(
            songUpdaterFactory = { client, provider ->
                object : SongDataUpdater(client, paths, provider) {
                    override suspend fun updateAll(
                        onProgress: (Int, Int, String) -> Unit
                    ): Result<Unit> {
                        if (updateCalls++ > 0) return super.updateAll(onProgress)
                        updateStarted.complete(Unit)
                        withContext(NonCancellable) { releaseUpdate.await() }
                        return Result.failure(CatalogDownloadException())
                    }

                    override suspend fun markRepairRequired(): Result<Unit> {
                        markStarted.complete(Unit)
                        withContext(NonCancellable) { releaseMark.await() }
                        return super.markRepairRequired()
                    }
                }
            }
        )

        assertEquals(
            CatalogRepairStartResult.Started,
            harness.session.startResetAndRedownload()
        )
        updateStarted.await()
        harness.session.cancelRunning()

        assertEquals(CatalogRepairStatus.Cancelling, harness.session.state.value.status)
        assertTrue(harness.session.state.value.blocking)
        val cancellingGeneration = harness.session.state.value.resultGeneration
        assertEquals(CatalogRepairStartResult.AlreadyRunning, harness.session.retry())
        assertEquals(CatalogRepairStartResult.AlreadyRunning, harness.session.startUpdate())
        assertEquals(
            CatalogRepairStartResult.AlreadyRunning,
            harness.session.startResetAndRedownload()
        )
        assertEquals(CatalogRepairStartResult.AlreadyRunning, harness.session.useBundledFallback())
        assertEquals(cancellingGeneration, harness.session.state.value.resultGeneration)

        releaseUpdate.complete(Unit)
        markStarted.await()
        assertEquals(CatalogRepairStatus.Cancelling, harness.session.state.value.status)
        assertEquals(CatalogRepairStartResult.AlreadyRunning, harness.session.useBundledFallback())
        releaseMark.complete(Unit)
        val failed = harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Failed, failed.status)
        assertTrue(failed.blocking)
        assertTrue(fs.exists(repairMarker()))
        assertTrue(fs.exists(collectibleRepairMarker()))
        assertFalse(fs.exists(fallbackMarker()))
        assertFalse(fs.exists(collectibleFallbackMarker()))

        assertEquals(CatalogRepairStartResult.Started, harness.session.useBundledFallback())
        val degraded = harness.awaitTerminal()
        assertEquals(CatalogRepairStatus.Degraded, degraded.status)
        assertTrue(degraded.degraded)
        assertTrue(fs.exists(repairMarker()))
        assertTrue(fs.exists(collectibleRepairMarker()))
        assertTrue(fs.exists(fallbackMarker()))
        assertTrue(fs.exists(collectibleFallbackMarker()))
        assertEquals("Bundled Song", harness.provider.getSongs()["test.0"]?.name)

        assertEquals(
            CatalogRepairStartResult.Started,
            harness.session.startResetAndRedownload()
        )
        val succeeded = harness.awaitTerminal()
        assertEquals(CatalogRepairStatus.Succeeded, succeeded.status)
        assertFalse(succeeded.degraded)
        assertFalse(fs.exists(repairMarker()))
        assertFalse(fs.exists(collectibleRepairMarker()))
        assertFalse(fs.exists(fallbackMarker()))
        assertFalse(fs.exists(collectibleFallbackMarker()))
        assertEquals("New Song", harness.provider.getSongs()["new.0"]?.name)
    }

    @Test
    fun cancellationMarkerFailureStaysBlockingAndNextStartupRecovers() = runTest(dispatcher) {
        val updateStarted = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        val harness = createHarness(
            songUpdaterFactory = { client, provider ->
                object : SongDataUpdater(client, paths, provider) {
                    override suspend fun updateAll(
                        onProgress: (Int, Int, String) -> Unit
                    ): Result<Unit> {
                        updateStarted.complete(Unit)
                        withContext(NonCancellable) { releaseUpdate.await() }
                        return Result.failure(CatalogDownloadException())
                    }

                    override suspend fun markRepairRequired(): Result<Unit> =
                        Result.failure(CatalogStorageException())
                }
            }
        )

        harness.session.startResetAndRedownload()
        updateStarted.await()
        harness.session.cancelRunning()
        releaseUpdate.complete(Unit)
        val failed = harness.awaitTerminal()

        assertEquals(CatalogRepairStatus.Failed, failed.status)
        assertEquals(CatalogRepairFailure.Storage, failed.failure)
        assertTrue(failed.blocking)
        assertTrue(fs.exists(repairMarker()))
        assertTrue(fs.exists(collectibleRepairMarker()))

        val recovered = createHarness()
        recovered.session.ensureStartupRepair()
        val ready = recovered.awaitTerminal()
        assertEquals(CatalogRepairStatus.Succeeded, ready.status)
        assertFalse(ready.blocking)
        assertFalse(fs.exists(repairMarker()))
        assertFalse(fs.exists(collectibleRepairMarker()))
        assertEquals("New Song", recovered.provider.getSongs()["new.0"]?.name)
    }

    @Test
    fun ordinaryCancellationRechecksSongSceneAfterIncompleteRollback() = runTest(dispatcher) {
        writeSongBaseline(info = bundledInfo)
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        var concealRecovery = false
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun metadataOrNull(path: Path) =
                if (concealRecovery && path == songBackupDir()) null else delegate.metadataOrNull(path)

            override fun atomicMove(source: Path, target: Path) {
                if (target == songDataDir() / "infolist.json" && source.name == "infolist.json") {
                    commitStarted.complete(Unit)
                    runBlocking { releaseCommit.await() }
                    throw CancellationException("cancelled song commit")
                }
                if (target == songDataDir() / "info.csv" && source.name == "info.csv.restore.tmp") {
                    throw IllegalStateException("injected song rollback restore failure")
                }
                delegate.atomicMove(source, target)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val harness = createHarness(
                scope = scope,
                songUpdaterFactory = { client, provider ->
                    SongDataUpdater(client, paths, provider, faultyFs)
                }
            )
            harness.session.startUpdate()
            commitStarted.await()
            concealRecovery = true
            harness.session.cancelRunning()
            assertEquals(CatalogRepairStatus.Cancelling, harness.session.state.value.status)
            assertFalse(harness.session.state.value.blocking)

            concealRecovery = false
            releaseCommit.complete(Unit)
            val failed = harness.awaitTerminal()
            assertEquals(CatalogRepairStatus.Failed, failed.status)
            assertTrue(failed.blocking)
            assertTrue(fs.exists(songBackupDir() / "transaction.state"))
            assertTrue(fs.exists(repairMarker()))
            assertTrue(fs.exists(collectibleRepairMarker()))

            val recovered = createHarness()
            recovered.session.ensureStartupRepair()
            val ready = recovered.awaitTerminal()
            assertEquals(CatalogRepairStatus.Succeeded, ready.status)
            assertFalse(fs.exists(songBackupDir()))
            assertFalse(fs.exists(repairMarker()))
            assertFalse(fs.exists(collectibleRepairMarker()))
            assertEquals("New Song", recovered.provider.getSongs()["new.0"]?.name)

            recovered.session.useBundledFallback()
            val degraded = recovered.awaitTerminal()
            assertEquals(CatalogRepairStatus.Degraded, degraded.status)
            assertEquals("Bundled Song", recovered.provider.getSongs()["test.0"]?.name)
            assertTrue(fs.exists(repairMarker()))
            assertTrue(fs.exists(collectibleRepairMarker()))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun ordinaryCancellationRechecksCollectibleSceneAfterIncompleteRollback() = runTest(dispatcher) {
        writeSongBaseline()
        writeCollectibleBaseline()
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        var concealRecovery = false
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun metadataOrNull(path: Path) =
                if (concealRecovery && path == collectibleBackupDir()) null else delegate.metadataOrNull(path)

            override fun atomicMove(source: Path, target: Path) {
                if (target == collectibleDataDir() / "collection_zh_cn.csv" &&
                    source.name == "collection_zh_cn.csv"
                ) {
                    commitStarted.complete(Unit)
                    runBlocking { releaseCommit.await() }
                    throw CancellationException("cancelled collectible commit")
                }
                if (target == collectibleDataDir() / "collection_version.txt" &&
                    source.name == "collection_version.txt.restore.tmp"
                ) {
                    throw IllegalStateException("injected collectible rollback restore failure")
                }
                delegate.atomicMove(source, target)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val harness = createHarness(
                scope = scope,
                collectibleUpdaterFactory = { client, provider ->
                    CollectibleDataUpdater(client, paths, provider, faultyFs)
                }
            )
            harness.session.startUpdate()
            commitStarted.await()
            concealRecovery = true
            harness.session.cancelRunning()
            assertEquals(CatalogRepairStatus.Cancelling, harness.session.state.value.status)
            assertFalse(harness.session.state.value.blocking)

            concealRecovery = false
            releaseCommit.complete(Unit)
            val failed = harness.awaitTerminal()
            assertEquals(CatalogRepairStatus.Failed, failed.status)
            assertTrue(failed.blocking)
            assertTrue(fs.exists(collectibleBackupDir() / "transaction.state"))
            assertTrue(fs.exists(repairMarker()))
            assertTrue(fs.exists(collectibleRepairMarker()))

            val recovered = createHarness()
            recovered.session.ensureStartupRepair()
            val ready = recovered.awaitTerminal()
            assertEquals(CatalogRepairStatus.Succeeded, ready.status)
            assertFalse(fs.exists(collectibleBackupDir()))
            assertFalse(fs.exists(repairMarker()))
            assertFalse(fs.exists(collectibleRepairMarker()))
            assertEquals("New Song", recovered.provider.getSongs()["new.0"]?.name)
            assertEquals("9.9.9", recovered.collectibleProvider.currentVersionText())

            recovered.session.useBundledFallback()
            val degraded = recovered.awaitTerminal()
            assertEquals(CatalogRepairStatus.Degraded, degraded.status)
            assertEquals("Bundled Song", recovered.provider.getSongs()["test.0"]?.name)
            assertEquals("1.0.0", recovered.collectibleProvider.currentVersionText())
            assertTrue(fs.exists(repairMarker()))
            assertTrue(fs.exists(collectibleRepairMarker()))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingManualRunFinalizesIdleAndIgnoresLateFailure() = runTest(dispatcher) {
        val updateStarted = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        val harness = createHarness(
            songUpdaterFactory = { client, provider ->
                object : SongDataUpdater(client, paths, provider) {
                    override suspend fun updateAll(
                        onProgress: (Int, Int, String) -> Unit
                    ): Result<Unit> {
                        updateStarted.complete(Unit)
                        withContext(NonCancellable) { releaseUpdate.await() }
                        return Result.failure(CatalogDownloadException())
                    }
                }
            }
        )
        harness.session.startUpdate()
        updateStarted.await()

        harness.session.cancelRunning()
        assertEquals(CatalogRepairStatus.Cancelling, harness.session.state.value.status)
        assertFalse(harness.session.state.value.blocking)
        assertEquals(CatalogRepairStartResult.AlreadyRunning, harness.session.startUpdate())
        releaseUpdate.complete(Unit)
        advanceUntilIdle()

        assertEquals(CatalogRepairStatus.Idle, harness.session.state.value.status)
        assertFalse(harness.session.state.value.blocking)
    }

    @Test
    fun failureCategoriesNeverCarryTransportDetail() {
        assertEquals(CatalogRepairFailure.Network, CatalogDownloadException().toCatalogRepairFailure())
        assertEquals(
            CatalogRepairFailure.Scene,
            StagedUpdateBlockedException("invalid", base / "x").toCatalogRepairFailure()
        )
        assertEquals(
            CatalogRepairFailure.Storage,
            StagedRollbackException(IllegalStateException("io"), emptyList(), base / "x").toCatalogRepairFailure()
        )
        assertEquals(CatalogRepairFailure.Storage, CatalogStorageException().toCatalogRepairFailure())
        assertEquals(
            CatalogRepairFailure.Busy,
            UpdateAlreadyInProgressException().toCatalogRepairFailure()
        )
        assertEquals(
            CatalogRepairFailure.Unknown,
            IllegalStateException("raw transport detail").toCatalogRepairFailure()
        )
        assertEquals(CatalogRepairFailure.Unknown, (null as Throwable?).toCatalogRepairFailure())
    }
}
