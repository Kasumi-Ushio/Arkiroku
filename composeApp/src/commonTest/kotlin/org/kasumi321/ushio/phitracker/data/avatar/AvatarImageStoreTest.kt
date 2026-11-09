package org.kasumi321.ushio.phitracker.data.avatar

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.platform.BinaryAssetReader
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.domain.model.GameAvatar
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarImageStoreTest {
    private val fs = FileSystem.SYSTEM
    private lateinit var base: Path
    private lateinit var paths: PlatformPaths
    private var bundledImages = mapOf<String, ByteArray>()

    @BeforeTest
    fun setUp() {
        base = "/tmp/avatar-image-store-${hashCode()}-${Random.nextLong().toString(16)}".toPath()
        fs.createDirectories(base)
        fs.createDirectories(base / "cache")
        paths = PlatformPaths(base.toString(), (base / "cache").toString())
    }

    @AfterTest
    fun tearDown() {
        if (::base.isInitialized) fs.deleteRecursively(base)
    }

    private fun store(engine: MockEngine) = AvatarImageStore(
        httpClient = HttpClient(engine),
        paths = paths,
        assetReader = object : BinaryAssetReader {
            override fun readBytes(name: String): ByteArray =
                bundledImages[name] ?: error("Asset not found: $name")
        },
        fs = fs
    )

    private fun avatar(key: String) = GameAvatar(id = "id-$key", resourceKey = key)

    private fun poolPath(key: String) = base / "avatar_pool" / "avatar.$key.png"

    private fun writePool(key: String, bytes: ByteArray = AvatarTestFixtures.pngOne) {
        val path = poolPath(key)
        fs.createDirectories(path.parent!!)
        fs.write(path) { write(bytes) }
    }

    @Test
    fun previewPrefersPoolWithoutReadingBundleOrNetwork() = runTest {
        writePool("selected", AvatarTestFixtures.pngTwo)
        bundledImages = mapOf("avatar.selected.png" to AvatarTestFixtures.pngOne)
        var requests = 0
        val imageStore = store(MockEngine { requests++; error("network must not be used") })

        val uri = imageStore.previewUri(avatar("selected"))

        assertEquals(poolPath("selected").toString(), uri)
        assertContentEquals(AvatarTestFixtures.pngTwo, fs.read(poolPath("selected")) { readByteArray() })
        assertEquals(0, requests)
    }

    @Test
    fun previewMaterializesBundleBeforeRemoteFallback() = runTest {
        bundledImages = mapOf("avatar.bundled.png" to AvatarTestFixtures.pngOne)
        var requests = 0
        val imageStore = store(MockEngine { requests++; error("network must not be used") })

        val uri = imageStore.previewUri(avatar("bundled"))
        val local = uri.toPath()

        assertTrue(local.toString().contains("avatar_bundled"))
        assertContentEquals(AvatarTestFixtures.pngOne, fs.read(local) { readByteArray() })
        assertEquals(0, requests)
    }

    @Test
    fun previewMayBeRemoteButEnsureLocalDownloadsAndReturnsPoolFile() = runTest {
        var requests = 0
        val imageStore = store(MockEngine { request ->
            requests++
            assertTrue(request.url.encodedPath.endsWith("/remote.png"))
            respond(AvatarTestFixtures.pngOne)
        })

        val preview = imageStore.previewUri(avatar("remote"))
        assertTrue(preview.startsWith("https://"))
        assertFalse(fs.exists(poolPath("remote")))

        val local = imageStore.ensureLocal(avatar("remote"))
        assertEquals(poolPath("remote").toString(), local)
        assertContentEquals(AvatarTestFixtures.pngOne, fs.read(poolPath("remote")) { readByteArray() })
        assertEquals(1, requests)
    }

    @Test
    fun reconcileDownloadsOnlyNewKeysAndDeletesObsoleteOnes() = runTest {
        writePool("existing")
        writePool("obsolete")
        bundledImages = mapOf("avatar.bundled.png" to AvatarTestFixtures.pngTwo)
        val requested = mutableListOf<String>()
        val imageStore = store(MockEngine { request ->
            val key = request.url.encodedPath.substringAfterLast('/').removeSuffix(".png")
            requested += key
            respond(AvatarTestFixtures.pngOne)
        })
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = imageStore.reconcile(setOf("existing", "bundled", "new")) { completed, total, _ ->
            progress += completed to total
        }

        assertEquals(setOf("new"), result.downloadedKeys)
        assertEquals(setOf("obsolete"), result.deletedKeys)
        assertTrue(result.failedKeys.isEmpty())
        assertEquals(listOf("new"), requested)
        assertTrue(fs.exists(poolPath("existing")))
        assertTrue(fs.exists(poolPath("new")))
        assertFalse(fs.exists(poolPath("obsolete")))
        assertFalse(fs.exists(poolPath("bundled")))
        assertEquals(2 to 2, progress.last())
    }

    @Test
    fun partialFailureStillDeletesObsoleteAndNewInstanceRetriesOnlyMissing() = runTest {
        writePool("obsolete")
        val firstRequests = mutableListOf<String>()
        val first = store(MockEngine { request ->
            val key = request.url.encodedPath.substringAfterLast('/').removeSuffix(".png")
            firstRequests += key
            if (key == "bad") respondError(HttpStatusCode.ServiceUnavailable)
            else respond(AvatarTestFixtures.pngOne)
        })

        val partial = first.reconcile(setOf("good", "bad"))

        assertEquals(setOf("good"), partial.downloadedKeys)
        assertEquals(setOf("obsolete"), partial.deletedKeys)
        assertEquals(setOf("bad"), partial.failedKeys)
        assertEquals(setOf("good", "bad"), firstRequests.toSet())
        assertFalse(fs.exists(poolPath("obsolete")))

        val retryRequests = mutableListOf<String>()
        val recreated = store(MockEngine { request ->
            retryRequests += request.url.encodedPath.substringAfterLast('/').removeSuffix(".png")
            respond(AvatarTestFixtures.pngTwo)
        })
        val healed = recreated.reconcile(setOf("good", "bad"))

        assertEquals(setOf("bad"), healed.downloadedKeys)
        assertTrue(healed.failedKeys.isEmpty())
        assertEquals(listOf("bad"), retryRequests)
        assertContentEquals(AvatarTestFixtures.pngTwo, fs.read(poolPath("bad")) { readByteArray() })
    }

    @Test
    fun accountCopySurvivesPoolCleanupAndOnlyManagedFileCanBeDeleted() = runTest {
        writePool("chosen")
        val imageStore = store(MockEngine { error("network must not be used") })

        val accountUri = imageStore.createAccountAvatar(avatar("chosen"), "player/甲")
        val accountPath = accountUri.toPath()
        val unrelated = base / "avatar_pool" / "unrelated.png"
        fs.write(unrelated) { write(AvatarTestFixtures.pngTwo) }
        val nested = base / "avatar_builtin" / "nested" / "not-managed.png"
        fs.createDirectories(nested.parent!!)
        fs.write(nested) { write(AvatarTestFixtures.pngTwo) }

        imageStore.reconcile(emptySet())
        assertFalse(fs.exists(poolPath("chosen")))
        assertTrue(fs.exists(accountPath))

        imageStore.deleteManagedAccountAvatar(unrelated.toString())
        imageStore.deleteManagedAccountAvatar(nested.toString())
        imageStore.deleteManagedAccountAvatar(null)
        assertTrue(fs.exists(unrelated))
        assertTrue(fs.exists(nested))

        imageStore.deleteManagedAccountAvatar(accountUri)
        assertFalse(fs.exists(accountPath))
    }

    @Test
    fun invalidRemotePngIsNotPersistedAsLocal() = runTest {
        val imageStore = store(MockEngine { respond("not a png".encodeToByteArray()) })

        assertFailsWith<IllegalArgumentException> {
            imageStore.ensureLocal(avatar("broken"))
        }
        assertFalse(fs.exists(poolPath("broken")))
    }

    @Test
    fun publicOperationsRejectTraversalKeys() = runTest {
        val imageStore = store(MockEngine { error("network must not be used") })

        assertFailsWith<IllegalArgumentException> { imageStore.previewUri(avatar("../escape")) }
        assertFailsWith<IllegalArgumentException> { imageStore.ensureLocal(avatar("dir\\escape")) }
        assertFailsWith<IllegalArgumentException> { imageStore.reconcile(setOf("..")) }
    }
}
