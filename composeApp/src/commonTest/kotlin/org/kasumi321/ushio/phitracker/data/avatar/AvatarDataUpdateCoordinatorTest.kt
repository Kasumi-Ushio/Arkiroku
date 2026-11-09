package org.kasumi321.ushio.phitracker.data.avatar

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AvatarDataUpdateCoordinatorTest {
    private val fs = FileSystem.SYSTEM
    private lateinit var base: Path
    private lateinit var paths: PlatformPaths
    private var bundledCatalog = "bundled\tbundled\n"

    @BeforeTest
    fun setUp() {
        base = "/tmp/avatar-coordinator-${hashCode()}-${Random.nextLong().toString(16)}".toPath()
        fs.createDirectories(base)
        fs.createDirectories(base / "cache")
        paths = PlatformPaths(base.toString(), (base / "cache").toString())
    }

    @AfterTest
    fun tearDown() {
        if (::base.isInitialized) fs.deleteRecursively(base)
    }

    private data class Harness(
        val coordinator: AvatarDataUpdateCoordinator,
        val provider: AvatarCatalogProvider
    )

    private fun harness(
        engine: MockEngine,
        bundledImages: Map<String, ByteArray> = emptyMap(),
        updaterFactory: ((HttpClient, AvatarCatalogProvider) -> AvatarDataUpdater)? = null
    ): Harness {
        val client = HttpClient(engine)
        val provider = AvatarCatalogProvider(
            assetReader = object : TextAssetReader {
                override fun readText(name: String): String = bundledCatalog
            },
            paths = paths
        )
        val updater = updaterFactory?.invoke(client, provider)
            ?: AvatarDataUpdater(client, paths, provider, fs)
        val imageStore = AvatarImageStore(
            httpClient = client,
            paths = paths,
            assetReader = object : BinaryAssetReader {
                override fun readBytes(name: String): ByteArray =
                    bundledImages[name] ?: error("Asset not found: $name")
            },
            fs = fs
        )
        return Harness(AvatarDataUpdateCoordinator(updater, provider, imageStore), provider)
    }

    private fun committedCatalogPath() =
        base / AvatarCatalogProvider.DATA_DIRECTORY / AvatarCatalogProvider.CATALOG_FILE

    @Test
    fun catalogCommitsBeforeImagesAndPartialImageFailureDoesNotRollItBack() = runTest {
        val freshCatalog = "good\tgood\nbad\tbad\n"
        val harness = harness(MockEngine { request ->
            when (val name = request.url.encodedPath.substringAfterLast('/')) {
                "tmp.tsv" -> respond(freshCatalog)
                "bad.png" -> respondError(HttpStatusCode.BadGateway)
                "good.png" -> respond(AvatarTestFixtures.pngOne)
                else -> error("Unexpected request: $name")
            }
        })

        val outcome = harness.coordinator.update().getOrThrow()

        assertEquals(setOf("good"), outcome.downloadedKeys)
        assertEquals(setOf("bad"), outcome.failedKeys)
        assertTrue(outcome.isPartial)
        assertEquals(freshCatalog, fs.read(committedCatalogPath()) { readUtf8() })
        assertEquals(setOf("good", "bad"), harness.provider.getAvatars().map { it.id }.toSet())
        assertTrue(fs.exists(base / "avatar_pool" / "avatar.good.png"))

        val retryRequests = mutableListOf<String>()
        val recreated = harness(MockEngine { request ->
            when (val name = request.url.encodedPath.substringAfterLast('/')) {
                "bad.png" -> {
                    retryRequests += name
                    respond(AvatarTestFixtures.pngTwo)
                }
                else -> error("Unexpected request: $name")
            }
        })
        val healed = recreated.coordinator.reconcileImages().getOrThrow()

        assertEquals(setOf("bad"), healed.downloadedKeys)
        assertTrue(healed.failedKeys.isEmpty())
        assertEquals(listOf("bad.png"), retryRequests)
        assertEquals(freshCatalog, fs.read(committedCatalogPath()) { readUtf8() })
    }

    @Test
    fun secondUpdateFailsFastWithBusyWhileFirstOwnsMutex() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val harness = harness(
            engine = MockEngine { error("network must not be used") },
            bundledImages = mapOf("avatar.bundled.png" to AvatarTestFixtures.pngOne),
            updaterFactory = { client, provider ->
                object : AvatarDataUpdater(client, paths, provider, fs) {
                    override suspend fun updateAll(
                        onProgress: (Int, Int, String) -> Unit
                    ): Result<Unit> {
                        entered.complete(Unit)
                        release.await()
                        return Result.success(Unit)
                    }
                }
            }
        )
        var firstResult: Result<AvatarDataUpdateCoordinator.UpdateOutcome>? = null
        val first = launch { firstResult = harness.coordinator.update() }
        entered.await()

        val second = harness.coordinator.update()

        assertIs<AvatarUpdateAlreadyInProgressException>(second.exceptionOrNull())
        release.complete(Unit)
        first.join()
        assertTrue(firstResult?.isSuccess == true)
    }

    @Test
    fun cancellationDuringImageDownloadPropagatesAndCommittedCatalogCanHealLater() = runTest {
        val imageStarted = CompletableDeferred<Unit>()
        val releaseImage = CompletableDeferred<Unit>()
        val freshCatalog = "cancelled\tcancelled\n"
        val harness = harness(MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "tmp.tsv" -> respond(freshCatalog)
                "cancelled.png" -> {
                    imageStarted.complete(Unit)
                    releaseImage.await()
                    respond(AvatarTestFixtures.pngOne)
                }
                else -> error("Unexpected request")
            }
        })

        val running = launch { harness.coordinator.update() }
        imageStarted.await()
        running.cancelAndJoin()

        assertTrue(running.isCancelled)
        assertEquals(freshCatalog, fs.read(committedCatalogPath()) { readUtf8() })
        assertTrue(harness.provider.getAvatars().single().resourceKey == "cancelled")

        releaseImage.complete(Unit)
        val retry = harness.coordinator.reconcileImages().getOrThrow()
        assertEquals(setOf("cancelled"), retry.downloadedKeys)
        assertTrue(retry.failedKeys.isEmpty())
    }
}
