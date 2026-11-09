package org.kasumi321.ushio.phitracker.data.avatar

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.platform.BinaryAssetReader
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AvatarCatalogSessionTest {
    private val fs = FileSystem.SYSTEM
    private val dispatcher = StandardTestDispatcher()
    private lateinit var base: Path
    private lateinit var paths: PlatformPaths
    private lateinit var sessionScope: CoroutineScope

    @BeforeTest
    fun setUp() {
        base = "/tmp/avatar-session-${hashCode()}-${Random.nextLong().toString(16)}".toPath()
        fs.createDirectories(base)
        fs.createDirectories(base / "cache")
        paths = PlatformPaths(base.toString(), (base / "cache").toString())
        sessionScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(dispatcher.scheduler))
    }

    @AfterTest
    fun tearDown() {
        sessionScope.cancel()
        if (::base.isInitialized) fs.deleteRecursively(base)
    }

    private fun session(
        engine: MockEngine,
        bundledCatalog: String = "bundled\tbundled\n",
        bundledImages: Map<String, ByteArray> = mapOf("avatar.bundled.png" to AvatarTestFixtures.pngOne)
    ): AvatarCatalogSession {
        val client = HttpClient(engine)
        val provider = AvatarCatalogProvider(
            assetReader = object : TextAssetReader {
                override fun readText(name: String): String = bundledCatalog
            },
            paths = paths
        )
        val imageStore = AvatarImageStore(
            httpClient = client,
            paths = paths,
            assetReader = object : BinaryAssetReader {
                override fun readBytes(name: String): ByteArray =
                    bundledImages[name] ?: error("Asset not found: $name")
            },
            fs = fs
        )
        val coordinator = AvatarDataUpdateCoordinator(
            updater = AvatarDataUpdater(client, paths, provider, fs),
            catalogProvider = provider,
            imageStore = imageStore
        )
        return AvatarCatalogSession(coordinator, sessionScope)
    }

    private suspend fun AvatarCatalogSession.awaitTerminal(): AvatarCatalogState = state.first {
        it.status == AvatarCatalogRunStatus.Ready ||
            it.status == AvatarCatalogRunStatus.PartiallySucceeded ||
            it.status == AvatarCatalogRunStatus.Failed
    }

    @Test
    fun updatePublishesCatalogThenImageProgressAndReady() = runTest(dispatcher) {
        val catalogStarted = CompletableDeferred<Unit>()
        val releaseCatalog = CompletableDeferred<Unit>()
        val imageStarted = CompletableDeferred<Unit>()
        val releaseImage = CompletableDeferred<Unit>()
        val session = session(MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "tmp.tsv" -> {
                    catalogStarted.complete(Unit)
                    releaseCatalog.await()
                    respond("fresh\tfresh\n")
                }
                "fresh.png" -> {
                    imageStarted.complete(Unit)
                    releaseImage.await()
                    respond(AvatarTestFixtures.pngOne)
                }
                else -> error("Unexpected request")
            }
        }, bundledImages = emptyMap())

        session.update()
        assertEquals(AvatarCatalogRunStatus.UpdatingCatalog, session.state.value.status)
        session.update()
        assertEquals(AvatarCatalogRunStatus.UpdatingCatalog, session.state.value.status)
        catalogStarted.await()
        releaseCatalog.complete(Unit)

        imageStarted.await()
        assertEquals(AvatarCatalogRunStatus.ReconcilingImages, session.state.value.status)
        assertEquals("fresh", session.state.value.currentAvatar)
        assertEquals(1, session.state.value.total)

        releaseImage.complete(Unit)
        val terminal = session.awaitTerminal()
        assertEquals(AvatarCatalogRunStatus.Ready, terminal.status)
        assertEquals(0, terminal.failedDownloads)
        assertFalse(terminal.updateAvailable)
        assertTrue(fs.exists(base / "avatar_pool" / "avatar.fresh.png"))
    }

    @Test
    fun ensureReadyReconcilesAnAlreadyCommittedCatalog() = runTest(dispatcher) {
        val dataDir = base / AvatarCatalogProvider.DATA_DIRECTORY
        fs.createDirectories(dataDir)
        fs.write(dataDir / AvatarCatalogProvider.CATALOG_FILE) {
            writeUtf8("committed\tcommitted\n")
        }
        val session = session(MockEngine { request ->
            assertEquals("committed.png", request.url.encodedPath.substringAfterLast('/'))
            respond(AvatarTestFixtures.pngOne)
        }, bundledImages = emptyMap())

        session.ensureReady()
        val terminal = session.awaitTerminal()

        assertEquals(AvatarCatalogRunStatus.Ready, terminal.status)
        assertTrue(fs.exists(base / "avatar_pool" / "avatar.committed.png"))
        assertFalse(session.state.value.usingBundledFallback)
    }

    @Test
    fun partialImageFailureKeepsCatalogAndDismissReturnsToReady() = runTest(dispatcher) {
        val freshCatalog = "good\tgood\nbad\tbad\n"
        val session = session(MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "tmp.tsv" -> respond(freshCatalog)
                "good.png" -> respond(AvatarTestFixtures.pngOne)
                "bad.png" -> respondError(HttpStatusCode.InternalServerError)
                else -> error("Unexpected request")
            }
        }, bundledImages = emptyMap())

        session.update()
        val partial = session.awaitTerminal()

        assertEquals(AvatarCatalogRunStatus.PartiallySucceeded, partial.status)
        assertEquals(1, partial.failedDownloads)
        assertEquals(null, partial.failure)
        assertEquals(
            freshCatalog,
            fs.read(base / AvatarCatalogProvider.DATA_DIRECTORY / AvatarCatalogProvider.CATALOG_FILE) {
                readUtf8()
            }
        )

        session.dismissResult()
        assertEquals(AvatarCatalogRunStatus.Ready, session.state.value.status)
        assertEquals(0, session.state.value.failedDownloads)
    }

    @Test
    fun bundledFallbackCancelsRunningUpdateAndLateResultCannotOverwriteIt() = runTest(dispatcher) {
        val updateStarted = CompletableDeferred<Unit>()
        val releaseCancelledRequest = CompletableDeferred<Unit>()
        val session = session(MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "tmp.tsv" -> {
                    updateStarted.complete(Unit)
                    releaseCancelledRequest.await()
                    respond("late\tlate\n")
                }
                else -> error("Unexpected request")
            }
        })

        session.update()
        updateStarted.await()
        session.useBundledFallback()
        val terminal = session.awaitTerminal()

        assertEquals(AvatarCatalogRunStatus.Ready, terminal.status)
        assertTrue(terminal.usingBundledFallback)
        assertTrue(terminal.updateAvailable)

        releaseCancelledRequest.complete(Unit)
        advanceUntilIdle()
        assertEquals(AvatarCatalogRunStatus.Ready, session.state.value.status)
        assertTrue(session.state.value.usingBundledFallback)
        assertFalse(
            fs.exists(base / AvatarCatalogProvider.DATA_DIRECTORY / AvatarCatalogProvider.CATALOG_FILE)
        )
    }
}
