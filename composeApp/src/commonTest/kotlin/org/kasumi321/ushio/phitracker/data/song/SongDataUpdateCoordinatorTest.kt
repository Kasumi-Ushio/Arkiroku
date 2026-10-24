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
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataUpdater
import org.kasumi321.ushio.phitracker.data.platform.NoOpStandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The combined song+collection update flow: one upstream check covers both
 * datasets, one update downloads both, and each dataset keeps its own
 * staging → atomic-commit discipline — a failure on one side must never
 * commit the other.
 */
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

    private fun coordinator(engine: MockEngine): SongDataUpdateCoordinator {
        val songProvider = SongDataProvider(assetReader = fakeReader(), paths = paths)
        val collectibleProvider = CollectibleDataProvider(assetReader = fakeReader(), paths = paths)
        return SongDataUpdateCoordinator(
            songDataUpdater = SongDataUpdater(HttpClient(engine), paths, songProvider),
            songDataProvider = songProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            artworkFileCache = NoOpStandardArtworkCache,
            collectibleDataUpdater = CollectibleDataUpdater(HttpClient(engine), paths, collectibleProvider)
        )
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
}
