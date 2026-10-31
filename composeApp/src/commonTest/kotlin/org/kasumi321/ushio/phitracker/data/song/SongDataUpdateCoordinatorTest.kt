package org.kasumi321.ushio.phitracker.data.song

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataUpdater
import org.kasumi321.ushio.phitracker.data.common.StagedUpdateBlockedException
import org.kasumi321.ushio.phitracker.data.platform.CoilIllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.NoOpStandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The combined song+collection update flow: one upstream check covers both
 * datasets, one update downloads both, and each dataset keeps its own
 * staging → atomic-commit discipline — a failure on one side must never
 * commit the other.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SongDataUpdateCoordinatorTest {

    private val fs = FileSystem.SYSTEM
    private lateinit var testBaseDir: okio.Path
    private lateinit var paths: PlatformPaths

    private val bundledInfoCsv =
        "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n" +
            "test\tSong\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
    private val bundledVersion = "4.0.1.2"
    private val collectionHeader =
        "folder,folder_sub,cover,key,sub_index,name,date,supervisor,category,content,properties"
    private val bundledCollectionCsv = collectionHeader + "\n" +
        "额外章节,,cover.jpg,mope1,1,收容报告,767/06/01,主管,nonsense,内容,"

    private val bundledAssets: Map<String, String>
        get() = mapOf(
            "info.csv" to bundledInfoCsv,
            "infolist.json" to "{}",
            "notesInfo.json" to "{}",
            "nicklist.yaml" to "test:\n  - nick\n",
            "collection_zh_cn.csv" to bundledCollectionCsv,
            "collection_version.txt" to bundledVersion
        )

    @BeforeTest
    fun setUp() {
        testBaseDir = "/tmp/song-update-coordinator-test-${hashCode()}-${Random.nextLong().toString(16)}".toPath()
        fs.createDirectories(testBaseDir)
        fs.createDirectories(testBaseDir / "cache")
        paths = PlatformPaths(
            filesDir = testBaseDir.toString(),
            cacheDir = (testBaseDir / "cache").toString()
        )
    }

    @AfterTest
    fun tearDown() {
        if (::testBaseDir.isInitialized) {
            fs.deleteRecursively(testBaseDir)
        }
    }

    private fun fakeReader(): TextAssetReader = object : TextAssetReader {
        override fun readText(name: String): String =
            bundledAssets[name] ?: error("Asset not found: $name")
    }

    private fun coordinator(
        engine: MockEngine,
        thumbnailPreloader: IllustrationThumbnailPreloader = CoilIllustrationThumbnailPreloader
    ): SongDataUpdateCoordinator = coordinatorWithProvider(engine, thumbnailPreloader).first

    private fun coordinatorWithProvider(
        engine: MockEngine,
        thumbnailPreloader: IllustrationThumbnailPreloader = CoilIllustrationThumbnailPreloader
    ): Pair<SongDataUpdateCoordinator, SongDataProvider> {
        val songProvider = SongDataProvider(assetReader = fakeReader(), paths = paths)
        val collectibleProvider = CollectibleDataProvider(assetReader = fakeReader(), paths = paths)
        val coordinator = SongDataUpdateCoordinator(
            songDataUpdater = SongDataUpdater(HttpClient(engine), paths, songProvider),
            songDataProvider = songProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            artworkFileCache = NoOpStandardArtworkCache,
            collectibleDataUpdater = CollectibleDataUpdater(HttpClient(engine), paths, collectibleProvider),
            thumbnailPreloader = thumbnailPreloader
        )
        return coordinator to songProvider
    }

    private fun songDataDir() = testBaseDir / "song_data"

    private fun songBackupDir() = testBaseDir / "song_data_backup"

    private fun collectibleBackupDir() = testBaseDir / "collectible_data_backup"

    private fun legacySongBackupDir() = testBaseDir / "cache" / "song_data_backup"

    private fun manifestOf(backups: Map<String, String>): String = buildString {
        append("PHI-PRESTATE-V1\n")
        append(backups.size).append('\n')
        for ((name, content) in backups) {
            append(content.encodeToByteArray().size).append('\t').append(name).append('\n')
        }
    }

    private fun writeSongScene(
        state: String?,
        manifest: String,
        backups: Map<String, String>
    ) {
        val backup = songBackupDir()
        fs.createDirectories(backup)
        for ((name, content) in backups) fs.write(backup / name) { writeUtf8(content) }
        fs.write(backup / "prestate.txt") { writeUtf8(manifest) }
        if (state != null) fs.write(backup / "transaction.state") { writeUtf8("$state\n") }
    }

    private fun writeLegacySongScene(manifest: String?, backups: Map<String, String>) {
        val backup = legacySongBackupDir()
        fs.createDirectories(backup)
        for ((name, content) in backups) fs.write(backup / name) { writeUtf8(content) }
        if (manifest != null) fs.write(backup / "prestate.txt") { writeUtf8(manifest) }
    }

    private fun writeCollectibleScene(
        state: String,
        manifest: String,
        backups: Map<String, String>
    ) {
        val backup = collectibleBackupDir()
        fs.createDirectories(backup)
        for ((name, content) in backups) fs.write(backup / name) { writeUtf8(content) }
        fs.write(backup / "prestate.txt") { writeUtf8(manifest) }
        fs.write(backup / "transaction.state") { writeUtf8("$state\n") }
    }

    private fun MockRequestHandleScope.textResponse(body: String) = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "text/plain")
    )

    @Test
    fun checkReportsChangeWhenOnlyCollectibleDataIsStale() = runTest {
        // Song info.csv matches the bundled copy; only the collection
        // version.txt differs — the combined check must still report stale.
        val engine = MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "info.csv" -> textResponse(bundledInfoCsv)
                "version.txt" -> textResponse("9.9.9\n")
                else -> textResponse("x")
            }
        }

        val result = coordinator(engine).checkUpstreamChanged()

        assertTrue(result.isSuccess, "Combined check should succeed")
        assertEquals(true, result.getOrNull(), "Stale collectible data alone must report an update")
    }

    @Test
    fun checkReportsNoChangeWhenBothDatasetsMatch() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "info.csv" -> textResponse(bundledInfoCsv)
                "version.txt" -> textResponse(bundledVersion)
                else -> textResponse("x")
            }
        }

        val result = coordinator(engine).checkUpstreamChanged()

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull())
    }

    @Test
    fun checkFailsWhenCollectibleProbeFails() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "version.txt" -> respondError(HttpStatusCode.InternalServerError)
                else -> textResponse(bundledInfoCsv)
            }
        }

        val result = coordinator(engine).checkUpstreamChanged()

        assertTrue(result.isFailure, "A failing collectible probe must fail the combined check")
    }

    @Test
    fun updateCommitsSongDataEvenWhenCollectibleDownloadFails() = runTest {
        // Song files download fine; the collection CSVs 500 out. The song
        // dataset keeps its own commit discipline, the collectible dataset
        // must be left untouched (staging cleaned, nothing committed).
        val engine = MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            when {
                fileName.startsWith("collection_") && fileName.endsWith(".csv") ->
                    respondError(HttpStatusCode.InternalServerError)
                else -> textResponse("new-content-$fileName")
            }
        }

        val result = coordinator(engine).update(syncIllustrations = false)

        assertTrue(result.isFailure, "Collectible download failure must fail the combined update")

        val songDataDir = testBaseDir / "song_data"
        assertTrue(fs.exists(songDataDir / "info.csv"), "Song data must be committed")
        assertEquals(
            "new-content-info.csv",
            fs.read(songDataDir / "info.csv") { readUtf8() }
        )

        val collectibleDir = testBaseDir / "collectible_data"
        assertFalse(
            fs.exists(collectibleDir / "collection_version.txt"),
            "Collectible files must NOT be committed when its download fails"
        )
    }

    @Test
    fun updateCommitsBothDatasetsOnSuccessWithCombinedProgress() = runTest {
        val engine = MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            textResponse("new-content-$fileName")
        }
        val progressEvents = mutableListOf<Triple<Int, Int, String>>()

        val result = coordinator(engine).update(
            syncIllustrations = false,
            onFileProgress = { done, total, file -> progressEvents.add(Triple(done, total, file)) }
        )

        assertTrue(result.isSuccess, "Combined update should succeed: $result")
        for (fileName in SongDataUpdater.FILE_NAMES) {
            assertTrue(fs.exists(testBaseDir / "song_data" / fileName), "Missing song file $fileName")
        }
        for (fileName in CollectibleDataUpdater.FILE_NAMES) {
            assertTrue(fs.exists(testBaseDir / "collectible_data" / fileName), "Missing collectible file $fileName")
        }

        // The collectible phase continues the index/count across both
        // datasets: 4 song files + 6 collection files = one 10-file bar, and
        // the first collection event starts where the song phase ended.
        val songFileCount = SongDataUpdater.FILE_NAMES.size
        val collectibleFileCount = CollectibleDataUpdater.FILE_NAMES.size
        val firstCollectibleEvent = progressEvents.first { it.third == CollectibleDataUpdater.FILE_NAMES.first() }
        assertEquals(songFileCount, firstCollectibleEvent.first)
        assertEquals(songFileCount + collectibleFileCount, firstCollectibleEvent.second)
        val lastEvent = progressEvents.last()
        assertEquals(songFileCount + collectibleFileCount, lastEvent.first)
        assertEquals(songFileCount + collectibleFileCount, lastEvent.second)
    }

    @Test
    fun illustrationPartialFailureKeepsCommittedSongDataAndReportsFailure() = runTest {
        val preloader = object : IllustrationThumbnailPreloader {
            override suspend fun preload(url: String): Result<Unit> =
                Result.failure(IllegalStateException("image unavailable"))
        }

        val result = coordinator(engineWithAddedSong(), preloader).update()

        assertTrue(result.isFailure)
        assertTrue(fs.exists(testBaseDir / "song_data" / "info.csv"))
        assertTrue(fs.read(testBaseDir / "song_data" / "info.csv") { readUtf8() }.contains("added"))
    }

    @Test
    fun cancellationDuringIllustrationReconcilePropagatesAndKeepsCommittedSongData() = runTest {
        val reconcileStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val preloader = object : IllustrationThumbnailPreloader {
            override suspend fun preload(url: String): Result<Unit> {
                reconcileStarted.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val session = coordinator(engineWithAddedSong(), preloader)

        val job = launch { session.update() }
        reconcileStarted.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(fs.exists(testBaseDir / "song_data" / "info.csv"))
        assertTrue(fs.read(testBaseDir / "song_data" / "info.csv") { readUtf8() }.contains("added"))
    }

    private fun engineWithAddedSong(): MockEngine = MockEngine { request ->
        when (val fileName = request.url.encodedPath.substringAfterLast('/')) {
            "info.csv" -> textResponse(
                bundledInfoCsv + "\nadded\tAdded Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
            )
            "infolist.json", "notesInfo.json" -> textResponse("{}")
            "nicklist.yaml" -> textResponse("")
            "collection_version.txt" -> textResponse("9.9.9")
            else -> {
                if (fileName.startsWith("collection_") && fileName.endsWith(".csv")) {
                    textResponse(collectionHeader + "\n")
                } else {
                    textResponse("x")
                }
            }
        }
    }

    @Test
    fun secondEntryPointFailsFastWhileAnUpdateIsRunning() = runTest {
        // Home pull-to-refresh and Settings/Onboarding each used to run their
        // own in-flight flag; the coordinator's mutex is now the boundary —
        // a second entry must fail fast instead of racing the staging dirs.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val songProvider = SongDataProvider(assetReader = fakeReader(), paths = paths)
        val collectibleProvider = CollectibleDataProvider(assetReader = fakeReader(), paths = paths)
        val slowSongUpdater = object : SongDataUpdater(HttpClient(MockEngine { error("unused") }), paths, songProvider) {
            override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> {
                gate.await()
                return Result.success(Unit)
            }
        }
        val instantCollectibleUpdater = object : CollectibleDataUpdater(HttpClient(MockEngine { error("unused") }), paths, collectibleProvider) {
            override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> =
                Result.success(Unit)
        }
        val session = SongDataUpdateCoordinator(
            songDataUpdater = slowSongUpdater,
            songDataProvider = songProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            artworkFileCache = NoOpStandardArtworkCache,
            collectibleDataUpdater = instantCollectibleUpdater
        )

        val first = launch { firstResult = session.update(syncIllustrations = false) }
        runCurrent()
        val second = session.update(syncIllustrations = false)

        assertTrue(second.isFailure, "the second entry must fail fast while the first runs")
        assertTrue(second.exceptionOrNull() is UpdateAlreadyInProgressException)

        gate.complete(Unit)
        first.join()
        assertTrue(firstResult?.isSuccess == true, "the first (owning) update still completes normally")
    }

    @Test
    fun checkReportsChangeWhenASongRecoverySceneIsPending() = runTest {
        fs.createDirectories(songBackupDir())
        val engine = MockEngine { error("network must not be probed while recovery is pending") }

        val result = coordinator(engine).checkUpstreamChanged()

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun checkReportsChangeWhenACollectibleRecoverySceneIsPending() = runTest {
        fs.createDirectories(collectibleBackupDir())
        val engine = MockEngine { error("network must not be probed while recovery is pending") }

        val result = coordinator(engine).checkUpstreamChanged()

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun updateRecoversPendingSceneBeforeReadingSongsEvenWhenDownloadFails() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val oldInfo = header + "old\tOld Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val mixedInfo = header + "mixed\tMixed Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(mixedInfo) }
        val (session, provider) = coordinatorWithProvider(
            MockEngine { respondError(HttpStatusCode.InternalServerError) }
        )
        assertEquals("Mixed Song", provider.getSongs()["mixed.0"]?.name, "the provider caches the mixed snapshot")
        writeSongScene(
            state = "COMMITTING",
            manifest = manifestOf(mapOf("info.csv" to oldInfo)),
            backups = mapOf("info.csv" to oldInfo)
        )

        val result = session.update(syncIllustrations = false)

        assertTrue(result.isFailure, "the download after recovery still fails")
        assertEquals(oldInfo, fs.read(songDataDir() / "info.csv") { readUtf8() })
        assertEquals(
            "Old Song",
            provider.getSongs()["old.0"]?.name,
            "the coordinator recovers before getSongs and the rewritten dataset invalidates the cache"
        )
        assertFalse(fs.exists(songBackupDir()), "the recovered scene is consumed")
    }

    @Test
    fun getSongsFailureIsReturnedAsResultInsteadOfThrowing() = runTest {
        val failure = IllegalStateException("broken song index")
        val brokenProvider = SongDataProvider(
            assetReader = object : TextAssetReader {
                override fun readText(name: String): String = throw failure
            },
            paths = null
        )
        val session = SongDataUpdateCoordinator(
            songDataUpdater = SongDataUpdater(HttpClient(MockEngine { error("unused") }), paths, brokenProvider),
            songDataProvider = brokenProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            artworkFileCache = NoOpStandardArtworkCache,
            collectibleDataUpdater = CollectibleDataUpdater(
                HttpClient(MockEngine { error("unused") }),
                paths,
                CollectibleDataProvider(assetReader = fakeReader(), paths = paths)
            )
        )

        val result = session.update(syncIllustrations = false)

        assertTrue(result.isFailure, "getSongs failures must stay inside the Result boundary")
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun twoUpdatesInARowDoNotLockAfterPartialBackupResidue() = runTest {
        fs.createDirectories(songBackupDir())
        fs.write(songBackupDir() / "infolist.json") { writeUtf8("partial-copy-from-a-crashed-run") }
        val engine = MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            textResponse("content-$fileName")
        }
        val session = coordinator(engine)

        val first = session.update(syncIllustrations = false)
        val second = session.update(syncIllustrations = false)

        assertTrue(first.isSuccess, "an uncommitted partial backup must not block the next update: $first")
        assertTrue(second.isSuccess, "repeated updates must keep working: $second")
        assertFalse(fs.exists(songBackupDir()))
    }

    @Test
    fun clearedCacheLeavesPersistentAuthoritativeAndFullDownloadHeals() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val mixedInfo = header + "mixed\tMixed Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val freshInfo = header + "fresh\tFresh Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(mixedInfo) }
        val engine = MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "info.csv" -> textResponse(freshInfo)
                "version.txt" -> textResponse("9.9.9")
                else -> textResponse("{}")
            }
        }
        val (session, provider) = coordinatorWithProvider(engine)
        assertEquals("Mixed Song", provider.getSongs()["mixed.0"]?.name)
        assertFalse(fs.exists(songBackupDir()), "the cache (and with it the scene) was cleared")

        val result = session.update(syncIllustrations = false)

        assertTrue(result.isSuccess, "a full download heals persistent files without a recovery scene: $result")
        assertEquals("Fresh Song", provider.getSongs()["fresh.0"]?.name)
        assertEquals(freshInfo, fs.read(songDataDir() / "info.csv") { readUtf8() })
    }

    @Test
    fun blockedRecoveryFailsUpdateUntilResetThenFullDownloadHeals() = runTest {
        writeSongScene(
            state = "COMMITTING",
            manifest = "",
            backups = mapOf("info.csv" to "garbage")
        )
        val engine = MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            textResponse("content-$fileName")
        }
        val session = coordinator(engine)

        val blocked = session.update(syncIllustrations = false)

        assertIs<StagedUpdateBlockedException>(blocked.exceptionOrNull())
        assertTrue(session.hasPendingRecovery(), "a blocked scene is retained")
        assertTrue(fs.exists(songBackupDir()))

        val reset = session.resetPendingUpdate()

        assertTrue(reset.isSuccess, "reset is the explicit escape hatch: $reset")
        assertFalse(session.hasPendingRecovery())

        val healed = session.update(syncIllustrations = false)

        assertTrue(healed.isSuccess, "after reset the full download commits over the current files: $healed")
        assertFalse(fs.exists(songBackupDir()))
    }

    @Test
    fun committingSceneIsRecoveredByExplicitRecoveryBeforeTheProviderReads() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val oldInfo = header + "old\tOld Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val mixedInfo = header + "mixed\tMixed Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(mixedInfo) }
        val (session, provider) = coordinatorWithProvider(
            MockEngine { error("network must not be probed during recovery") }
        )
        assertEquals("Mixed Song", provider.getSongs()["mixed.0"]?.name)
        writeSongScene(
            state = "COMMITTING",
            manifest = manifestOf(mapOf("info.csv" to oldInfo)),
            backups = mapOf("info.csv" to oldInfo)
        )
        assertTrue(fs.exists(songBackupDir()), "the retained scene lives under filesDir")
        assertFalse(fs.exists(legacySongBackupDir()), "new scenes never use cacheDir")

        val recovered = session.recoverPendingUpdate()

        assertTrue(recovered.isSuccess, "explicit recovery restores the scene: $recovered")
        assertEquals(oldInfo, fs.read(songDataDir() / "info.csv") { readUtf8() })
        assertEquals("Old Song", provider.getSongs()["old.0"]?.name)
        assertFalse(fs.exists(songBackupDir()))
    }

    @Test
    fun recoveryAttemptsBothDatasetsAndReportsTheFirstFailure() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val currentInfo = header + "current\tCurrent\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val oldVersion = "1.0.0"
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(currentInfo) }
        // Song scene is unrecoverable...
        writeSongScene(
            state = "COMMITTING",
            manifest = "",
            backups = mapOf("info.csv" to "garbage")
        )
        // ...while the collectible scene is valid.
        val collectibleDir = testBaseDir / "collectible_data"
        fs.createDirectories(collectibleDir)
        fs.write(collectibleDir / "collection_version.txt") { writeUtf8("9.9.9") }
        writeCollectibleScene(
            state = "COMMITTING",
            manifest = manifestOf(mapOf("collection_version.txt" to oldVersion)),
            backups = mapOf("collection_version.txt" to oldVersion)
        )
        val session = coordinator(MockEngine { error("unused") })

        val recovered = session.recoverPendingUpdate()

        assertIs<StagedUpdateBlockedException>(recovered.exceptionOrNull(), "the first (song) failure is reported")
        assertTrue(fs.exists(songBackupDir()), "the failed song scene is retained")
        assertEquals(oldVersion, fs.read(collectibleDir / "collection_version.txt") { readUtf8() })
        assertFalse(fs.exists(collectibleBackupDir()), "the healthy dataset is still recovered")
    }

    @Test
    fun resetClearsTheSceneThenRunsAFullUpdate() = runTest {
        writeSongScene(
            state = "COMMITTING",
            manifest = "",
            backups = mapOf("info.csv" to "garbage")
        )
        val engine = MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            textResponse("content-$fileName")
        }
        val session = coordinator(engine)

        val reset = session.resetPendingUpdate(syncIllustrations = false)

        assertTrue(reset.isSuccess, "reset performs the full update itself: $reset")
        assertFalse(session.hasPendingRecovery())
        assertFalse(session.hasPendingRepair(), "a successful full update clears the marker")
        assertEquals("content-info.csv", fs.read(songDataDir() / "info.csv") { readUtf8() })
        assertEquals(
            "content-version.txt",
            fs.read(testBaseDir / "collectible_data" / "collection_version.txt") { readUtf8() }
        )
        val check = session.checkUpstreamChanged()
        assertTrue(check.isSuccess)
        assertEquals(false, check.getOrNull(), "the refreshed data is not reported stale")
    }

    @Test
    fun failedResetUpdateKeepsTheRepairMarkerAndCheckReportsChange() = runTest {
        writeSongScene(
            state = "COMMITTING",
            manifest = "",
            backups = mapOf("info.csv" to "garbage")
        )
        val session = coordinator(MockEngine { respondError(HttpStatusCode.InternalServerError) })

        val reset = session.resetPendingUpdate(syncIllustrations = false)

        assertTrue(reset.isFailure, "reset must not claim success when the full download fails")
        assertFalse(session.hasPendingRecovery(), "the reset removed the original scene")
        assertTrue(session.hasPendingRepair(), "the repair obligation survives a failed update")
        assertFalse(fs.exists(songBackupDir()))
        val check = session.checkUpstreamChanged()
        assertTrue(check.isSuccess)
        assertEquals(true, check.getOrNull(), "checkUpstreamChanged forces an update while the marker exists")
    }

    @Test
    fun recoverAndResetWaitForARunningUpdate() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val songProvider = SongDataProvider(assetReader = fakeReader(), paths = paths)
        val collectibleProvider = CollectibleDataProvider(assetReader = fakeReader(), paths = paths)
        val slowSongUpdater = object : SongDataUpdater(HttpClient(MockEngine { error("unused") }), paths, songProvider) {
            override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> {
                gate.await()
                return Result.success(Unit)
            }
        }
        val instantCollectibleUpdater = object : CollectibleDataUpdater(HttpClient(MockEngine { error("unused") }), paths, collectibleProvider) {
            override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> =
                Result.success(Unit)
        }
        val session = SongDataUpdateCoordinator(
            songDataUpdater = slowSongUpdater,
            songDataProvider = songProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            artworkFileCache = NoOpStandardArtworkCache,
            collectibleDataUpdater = instantCollectibleUpdater
        )
        var updateResult: Result<SongDataUpdateCoordinator.UpdateOutcome>? = null
        var recoverResult: Result<Unit>? = null
        var resetResult: Result<SongDataUpdateCoordinator.UpdateOutcome>? = null
        val recoverFinished = kotlinx.coroutines.CompletableDeferred<Unit>()
        val resetFinished = kotlinx.coroutines.CompletableDeferred<Unit>()

        val updateJob = launch { updateResult = session.update(syncIllustrations = false) }
        runCurrent()
        val recoverJob = launch {
            recoverResult = session.recoverPendingUpdate()
            recoverFinished.complete(Unit)
        }
        val resetJob = launch {
            resetResult = session.resetPendingUpdate(syncIllustrations = false)
            resetFinished.complete(Unit)
        }
        runCurrent()

        assertFalse(recoverFinished.isCompleted, "recovery must not race a running update")
        assertFalse(resetFinished.isCompleted, "reset must not race a running update")

        gate.complete(Unit)
        updateJob.join()
        recoverJob.join()
        resetJob.join()

        assertTrue(updateResult?.isSuccess == true, "the owning update completes: $updateResult")
        assertTrue(recoverResult?.isSuccess == true, "recovery runs after the update releases the mutex: $recoverResult")
        assertTrue(resetResult?.isSuccess == true, "reset runs after the update releases the mutex: $resetResult")
    }

    @Test
    fun validLegacyCacheSceneIsRecoveredAndConsumed() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val oldInfo = header + "old\tOld Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val mixedInfo = header + "mixed\tMixed Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(mixedInfo) }
        writeLegacySongScene(manifest = "info.csv", backups = mapOf("info.csv" to oldInfo))
        val session = coordinator(MockEngine { error("network must not be probed during recovery") })

        val recovered = session.recoverPendingUpdate()

        assertTrue(recovered.isSuccess, "a valid no-state legacy scene is restored: $recovered")
        assertEquals(oldInfo, fs.read(songDataDir() / "info.csv") { readUtf8() })
        assertFalse(fs.exists(legacySongBackupDir()), "the legacy scene is consumed")
    }

    @Test
    fun validLegacyCollectibleCacheSceneIsRecoveredAndConsumed() = runTest {
        val collectibleDir = testBaseDir / "collectible_data"
        fs.createDirectories(collectibleDir)
        fs.write(collectibleDir / "collection_version.txt") { writeUtf8("9.9.9") }
        val legacy = testBaseDir / "cache" / "collectible_data_backup"
        fs.createDirectories(legacy)
        fs.write(legacy / "collection_version.txt") { writeUtf8("1.0.0") }
        fs.write(legacy / "prestate.txt") { writeUtf8("collection_version.txt") }
        val session = coordinator(MockEngine { error("network must not be probed during recovery") })

        val recovered = session.recoverPendingUpdate()

        assertTrue(recovered.isSuccess, "the collectible legacy scene is migrated: $recovered")
        assertEquals("1.0.0", fs.read(collectibleDir / "collection_version.txt") { readUtf8() })
        assertFalse(fs.exists(legacy), "the migrated legacy scene is consumed")
        assertFalse(fs.exists(collectibleBackupDir()), "no new-format scene is left behind")
    }

    @Test
    fun invalidLegacyCacheSceneBlocksUntilResetDownloadsAgain() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val currentInfo = header + "current\tCurrent\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(currentInfo) }
        writeLegacySongScene(manifest = "", backups = mapOf("info.csv" to "old"))
        val engine = MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            textResponse("content-$fileName")
        }
        val session = coordinator(engine)

        val blocked = session.update(syncIllustrations = false)

        assertIs<StagedUpdateBlockedException>(blocked.exceptionOrNull())
        assertTrue(session.hasPendingRecovery())
        assertTrue(fs.exists(legacySongBackupDir()), "the ambiguous scene is retained")
        assertEquals(currentInfo, fs.read(songDataDir() / "info.csv") { readUtf8() })

        val reset = session.resetPendingUpdate(syncIllustrations = false)

        assertTrue(reset.isSuccess, "reset clears the legacy scene and downloads again: $reset")
        assertFalse(session.hasPendingRecovery())
        assertFalse(session.hasPendingRepair())
        assertFalse(fs.exists(legacySongBackupDir()))
        assertEquals("content-info.csv", fs.read(songDataDir() / "info.csv") { readUtf8() })
    }

    private var firstResult: Result<SongDataUpdateCoordinator.UpdateOutcome>? = null
}
