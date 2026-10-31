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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.common.StagedUpdateBlockedException
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SongDataUpdaterTest {

    private val fs = FileSystem.SYSTEM
    private lateinit var testBaseDir: okio.Path
    private lateinit var paths: PlatformPaths

    @BeforeTest
    fun setUp() {
        testBaseDir = "/tmp/song-updater-test-${hashCode()}-${Random.nextLong().toString(16)}".toPath()
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

    private fun fakeProvider(): SongDataProvider {
        val reader = object : TextAssetReader {
            override fun readText(name: String): String = when (name) {
                "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
                "infolist.json" -> "{}"
                "notesInfo.json" -> "{}"
                else -> error("Unexpected: $name")
            }
        }
        return SongDataProvider(assetReader = reader, paths = paths)
    }

    private fun songDataDir() = testBaseDir / "song_data"

    private fun songBackupDir() = testBaseDir / "song_data_backup"

    private fun legacySongBackupDir() = paths.cacheDir.toPath() / "song_data_backup"

    private fun manifestOf(backups: Map<String, String>): String = buildString {
        append("PHI-PRESTATE-V1\n")
        append(backups.size).append('\n')
        for ((name, content) in backups) {
            append(content.encodeToByteArray().size).append('\t').append(name).append('\n')
        }
    }

    private fun writeRetainedScene(
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

    private fun writeLegacyScene(
        manifest: String?,
        backups: Map<String, String>
    ) {
        val backup = legacySongBackupDir()
        fs.createDirectories(backup)
        for ((name, content) in backups) fs.write(backup / name) { writeUtf8(content) }
        if (manifest != null) fs.write(backup / "prestate.txt") { writeUtf8(manifest) }
    }

    private fun assertFileWritten(fileName: String) {
        val file = songDataDir() / fileName
        assertTrue(fs.exists(file), "Expected file $fileName to exist in song_data")
        val content = fs.read(file) { readUtf8() }
        assertTrue(content.isNotEmpty(), "Expected $fileName to have content")
    }

    private fun assertNoFileWritten(fileName: String) {
        val file = songDataDir() / fileName
        if (!fs.exists(file)) return
        val size = fs.metadata(file).size ?: 0L
        assertFalse(size > 0L, "Expected $fileName to NOT exist in song_data")
    }

    @Test
    fun updateAllDownloadsAllFilesAndInvalidatesCache() = runTest {
        val mockEngine = MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            when (fileName) {
                in SongDataUpdater.FILE_NAMES -> respond(
                    content = ByteReadChannel("mock-content-$fileName"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mockEngine)
        val provider = fakeProvider()

        val updater = SongDataUpdater(client, paths, provider)
        val result = updater.updateAll()

        assertTrue(result.isSuccess, "Expected updateAll to succeed")
        for (fileName in SongDataUpdater.FILE_NAMES) {
            assertFileWritten(fileName)
        }

        val fileContent = fs.read(songDataDir() / "info.csv") { readUtf8() }
        assertEquals("mock-content-info.csv", fileContent)
    }

    @Test
    fun updateAllReturnsFailureOnHttpError() = runTest {
        val mockEngine = MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            if (fileName == "info.csv") {
                respondError(HttpStatusCode.NotFound)
            } else {
                respond(
                    content = ByteReadChannel("ok"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }
        }
        val client = HttpClient(mockEngine)
        val provider = fakeProvider()

        val updater = SongDataUpdater(client, paths, provider)
        val result = updater.updateAll()

        assertTrue(result.isFailure, "Expected updateAll to fail on 404")
    }

    @Test
    fun updateAllReturnsFailureOnNetworkError() = runTest {
        val mockEngine = MockEngine {
            throw RuntimeException("Network error")
        }
        val client = HttpClient(mockEngine)
        val provider = fakeProvider()

        val updater = SongDataUpdater(client, paths, provider)
        val result = updater.updateAll()

        assertTrue(result.isFailure, "Expected updateAll to fail on network error")
    }

    @Test
    fun afterUpdateProviderReadsUpdatedFiles() = runTest {
        val mockEngine = MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            respond(
                content = ByteReadChannel(
                    when (fileName) {
                        "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\ntest\tUpdated\tComp\tIll\t\t\t\t\t9.0\t9.5\t10.0\t\n"
                        else -> "{}"
                    }
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val client = HttpClient(mockEngine)
        val provider = fakeProvider()

        val updater = SongDataUpdater(client, paths, provider)
        updater.updateAll()

        val songs = provider.getSongs()
        assertEquals("Updated", songs["test.0"]?.name)
    }

    @Test
    fun partialDownloadFailureLeavesExistingPersistentFilesUntouched() = runTest {
        val existingContent = "existing-content-before-update"
        val songDataDir = songDataDir()
        fs.createDirectories(songDataDir)
        for (fileName in SongDataUpdater.FILE_NAMES) {
            fs.write(songDataDir / fileName) { writeUtf8(existingContent) }
        }

        // Mock: first two downloads succeed, third throws
        var downloadCount = 0
        val mockEngine = MockEngine { request ->
            downloadCount++
            if (downloadCount >= 3) {
                throw RuntimeException("Simulated network error on download $downloadCount")
            }
            val fileName = request.url.encodedPath.substringAfterLast('/')
            respond(
                content = ByteReadChannel("new-content-$fileName"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val client = HttpClient(mockEngine)
        val provider = fakeProvider()

        val updater = SongDataUpdater(client, paths, provider)
        val result = updater.updateAll()

        assertTrue(result.isFailure, "Expected updateAll to fail on partial download error")

        // All persistent files must remain unchanged with original content
        for (fileName in SongDataUpdater.FILE_NAMES) {
            val file = songDataDir / fileName
            assertTrue(fs.exists(file), "Expected $fileName to still exist")
            val content = fs.read(file) { readUtf8() }
            assertEquals(existingContent, content, "$fileName should have original content")
        }

        // No stale staging or backup residue should remain
        val stagingDir = paths.cacheDir.toPath() / "song_data_staging"
        assertFalse(fs.exists(stagingDir), "Staging dir should be cleaned up after failure")
        val backupDir = songBackupDir()
        assertFalse(fs.exists(backupDir), "Backup dir should be cleaned up after failure")
    }

    @Test
    fun checkUpstreamChangedReturnsFalseWhenInfoCsvMatches() = runTest {
        val provider = fakeProvider()
        val localInfoCsv = provider.currentInfoCsv()
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(localInfoCsv),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val updater = SongDataUpdater(HttpClient(mockEngine), paths, provider)

        val result = updater.checkUpstreamChanged()

        assertTrue(result.isSuccess, "Expected checkUpstreamChanged to succeed")
        assertEquals(false, result.getOrNull(), "Identical info.csv should report no upstream change")
    }

    @Test
    fun checkUpstreamChangedReturnsTrueWhenInfoCsvDiffers() = runTest {
        val provider = fakeProvider()
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("id\tsong\tcomposer\nnew-song\tNew Song\tSomeone\n"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val updater = SongDataUpdater(HttpClient(mockEngine), paths, provider)

        val result = updater.checkUpstreamChanged()

        assertTrue(result.isSuccess, "Expected checkUpstreamChanged to succeed")
        assertEquals(true, result.getOrNull(), "Differing info.csv should report an upstream change")
    }

    @Test
    fun checkUpstreamChangedReturnsFailureOnNetworkError() = runTest {
        val mockEngine = MockEngine {
            throw RuntimeException("Network error")
        }
        val updater = SongDataUpdater(HttpClient(mockEngine), paths, fakeProvider())

        val result = updater.checkUpstreamChanged()

        assertTrue(result.isFailure, "Expected checkUpstreamChanged to fail on network error")
    }

    @Test
    fun cancellationDuringDownloadPropagatesAsCancellationAndCleansStaging() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val client = HttpClient(
                MockEngine(
                    io.ktor.client.engine.mock.MockEngineConfig().apply {
                        dispatcher = testDispatcher
                        addHandler {
                            gate.await()
                            respond(ByteReadChannel("never-reached"))
                        }
                    }
                )
            )
            val provider = fakeProvider()
            val updater = SongDataUpdater(client, paths, provider)
            var returned: Result<Unit>? = null

            val job = launch { returned = updater.updateAll() }
            runCurrent()
            assertTrue(fs.exists(paths.cacheDir.toPath() / "song_data_staging"), "staging exists while downloading")

            job.cancelAndJoin()

            assertTrue(job.isCancelled, "cancellation must propagate, not convert into a failure Result")
            assertEquals(null, returned, "a cancelled update must not produce a Result")
            assertFalse(fs.exists(paths.cacheDir.toPath() / "song_data_staging"), "cancelled update cleans staging")
            assertFalse(fs.exists(songBackupDir()), "cancelled update cleans the persistent backup")
            assertEquals(emptyList<String>(), songDataDir().let { if (fs.exists(it)) fs.list(it) else emptyList() }, "no persistent file written")
        }
    }

    @Test
    fun checkUpstreamChangedReturnsTrueWhenPendingRecoveryExists() = runTest {
        fs.createDirectories(songBackupDir())
        val mockEngine = MockEngine {
            throw RuntimeException("network must not be probed while a recovery scene is pending")
        }
        val updater = SongDataUpdater(HttpClient(mockEngine), paths, fakeProvider())

        val result = updater.checkUpstreamChanged()

        assertTrue(result.isSuccess, "A pending scene must report a change without a network round trip")
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun updateAllRecoversPendingSceneAndInvalidatesProviderCacheEvenWhenDownloadFails() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val oldInfo = header + "old\tOld Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val mixedInfo = header + "mixed\tMixed Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val provider = fakeProvider()
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(mixedInfo) }
        assertEquals("Mixed Song", provider.getSongs()["mixed.0"]?.name, "the provider caches the mixed on-disk state")
        writeRetainedScene(
            state = "COMMITTING",
            manifest = manifestOf(mapOf("info.csv" to oldInfo)),
            backups = mapOf("info.csv" to oldInfo)
        )

        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val result = SongDataUpdater(HttpClient(engine), paths, provider).updateAll()

        assertTrue(result.isFailure, "the download after recovery still fails")
        assertEquals(oldInfo, fs.read(songDataDir() / "info.csv") { readUtf8() })
        assertEquals(
            "Old Song",
            provider.getSongs()["old.0"]?.name,
            "recovery rewrote info.csv; the provider must not keep serving the mixed cache"
        )
        assertNull(provider.getSongs()["mixed.0"])
        assertFalse(fs.exists(songBackupDir()), "a recovered scene is consumed")
    }

    @Test
    fun twoUpdatesInARowDoNotLockAfterPartialBackupResidue() = runTest {
        fs.createDirectories(songBackupDir())
        fs.write(songBackupDir() / "infolist.json") { writeUtf8("partial-copy-from-a-crashed-run") }
        val mockEngine = MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            respond(
                content = ByteReadChannel("mock-content-$fileName"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val provider = fakeProvider()
        val updater = SongDataUpdater(HttpClient(mockEngine), paths, provider)

        val first = updater.updateAll()
        val second = updater.updateAll()

        assertTrue(first.isSuccess, "an uncommitted partial backup must not block the next update: $first")
        assertTrue(second.isSuccess, "repeated updates must keep working: $second")
        assertFalse(fs.exists(songBackupDir()))
    }

    @Test
    fun resetPendingUpdateClearsBlockedSceneWithoutTouchingPersistentData() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val currentInfo = header + "current\tCurrent\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(currentInfo) }
        writeRetainedScene(
            state = "COMMITTING",
            manifest = "",
            backups = mapOf("info.csv" to "old")
        )
        val updater = SongDataUpdater(HttpClient(MockEngine { error("unused") }), paths, fakeProvider())

        val blocked = updater.recoverPendingUpdate()

        assertIs<StagedUpdateBlockedException>(blocked.exceptionOrNull())
        assertTrue(updater.hasPendingRecovery())
        assertEquals(currentInfo, fs.read(songDataDir() / "info.csv") { readUtf8() })

        assertTrue(updater.resetPendingUpdate().isSuccess)
        assertFalse(updater.hasPendingRecovery())
        assertEquals(currentInfo, fs.read(songDataDir() / "info.csv") { readUtf8() }, "reset never restores or deletes data")
    }

    @Test
    fun committingSceneNeedsExplicitRecoveryBeforeTheProviderSeesRestoredData() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val oldInfo = header + "old\tOld Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val mixedInfo = header + "mixed\tMixed Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val provider = fakeProvider()
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(mixedInfo) }
        assertEquals("Mixed Song", provider.getSongs()["mixed.0"]?.name)
        writeRetainedScene(
            state = "COMMITTING",
            manifest = manifestOf(mapOf("info.csv" to oldInfo)),
            backups = mapOf("info.csv" to oldInfo)
        )
        assertTrue(fs.exists(songBackupDir()), "the new scene lives under filesDir")
        assertFalse(fs.exists(legacySongBackupDir()), "a new-format scene is not written to cacheDir")
        val updater = SongDataUpdater(HttpClient(MockEngine { error("unused") }), paths, provider)

        assertEquals(
            "Mixed Song",
            provider.getSongs()["mixed.0"]?.name,
            "nothing may be read as recovered before the explicit recovery ran"
        )

        val recovered = updater.recoverPendingUpdate()

        assertTrue(recovered.isSuccess, "explicit recovery restores the COMMITTING scene: $recovered")
        assertEquals(oldInfo, fs.read(songDataDir() / "info.csv") { readUtf8() })
        assertEquals("Old Song", provider.getSongs()["old.0"]?.name, "the provider cache is invalidated by recovery")
        assertNull(provider.getSongs()["mixed.0"])
        assertFalse(fs.exists(songBackupDir()))
    }

    @Test
    fun legacyNoStateCacheSceneIsRestoredWithCommittingSemantics() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val oldInfo = header + "old\tOld Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val mixedInfo = header + "mixed\tMixed Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val provider = fakeProvider()
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(mixedInfo) }
        fs.write(songDataDir() / "notesInfo.json") { writeUtf8("newly-committed") }
        assertEquals("Mixed Song", provider.getSongs()["mixed.0"]?.name)
        // Old versions wrote one filename per line and no transaction state.
        writeLegacyScene(manifest = "info.csv", backups = mapOf("info.csv" to oldInfo))

        val updater = SongDataUpdater(HttpClient(MockEngine { error("unused") }), paths, provider)
        val recovered = updater.recoverPendingUpdate()

        assertTrue(recovered.isSuccess, "a valid non-empty legacy scene is recoverable: $recovered")
        assertEquals(oldInfo, fs.read(songDataDir() / "info.csv") { readUtf8() })
        assertNull(provider.getSongs()["mixed.0"])
        assertEquals("Old Song", provider.getSongs()["old.0"]?.name)
        assertFalse(fs.exists(songDataDir() / "notesInfo.json"), "COMMITTING semantics remove files the prestate lacked")
        assertFalse(fs.exists(legacySongBackupDir()), "the migrated legacy scene is consumed")
        assertFalse(fs.exists(songBackupDir()), "recovery never leaves a new-format scene behind")
    }

    @Test
    fun emptyOrInvalidLegacyCacheScenesFailClosedUntilReset() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val currentInfo = header + "current\tCurrent\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val oldInfo = header + "old\tOld Song\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        val invalidScenes: List<Pair<String?, Map<String, String>>> = listOf(
            null to mapOf("info.csv" to oldInfo),
            "" to mapOf("info.csv" to oldInfo),
            "info.csv\n" to mapOf("info.csv" to oldInfo),
            "info.csv\ninfo.csv" to mapOf("info.csv" to oldInfo),
            "unknown.csv" to mapOf("unknown.csv" to "old"),
            "info.csv" to emptyMap()
        )
        for ((manifest, backups) in invalidScenes) {
            setUp(); try {
                fs.createDirectories(songDataDir())
                fs.write(songDataDir() / "info.csv") { writeUtf8(currentInfo) }
                writeLegacyScene(manifest = manifest, backups = backups)
                val updater = SongDataUpdater(HttpClient(MockEngine { error("unused") }), paths, fakeProvider())

                val recovered = updater.recoverPendingUpdate()

                assertIs<StagedUpdateBlockedException>(
                    recovered.exceptionOrNull(),
                    "legacy scene must fail closed: manifest=$manifest backups=${backups.keys}"
                )
                assertEquals(currentInfo, fs.read(songDataDir() / "info.csv") { readUtf8() })
                assertTrue(updater.hasPendingRecovery(), "the ambiguous legacy scene is retained")
                assertTrue(fs.exists(legacySongBackupDir()))

                val reset = updater.resetPendingUpdate()

                assertTrue(reset.isSuccess, "reset clears an unrecoverable legacy scene: $reset")
                assertFalse(fs.exists(legacySongBackupDir()))
                assertTrue(updater.hasPendingRepair(), "reset records the outstanding full download")
            } finally {
                tearDown()
            }
        }
    }

    @Test
    fun resetRequiresAFullUpdateAndFailedUpdateKeepsTheRepairMarker() = runTest {
        val header = "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n"
        val currentInfo = header + "current\tCurrent\tComp\tIll\t\t\t\t\t1.0\t2.0\t3.0\t"
        fs.createDirectories(songDataDir())
        fs.write(songDataDir() / "info.csv") { writeUtf8(currentInfo) }
        writeRetainedScene(
            state = "COMMITTING",
            manifest = "",
            backups = mapOf("info.csv" to "garbage")
        )
        val updater = SongDataUpdater(
            HttpClient(MockEngine { error("network must not be probed while the marker is pending") }),
            paths,
            fakeProvider()
        )
        assertIs<StagedUpdateBlockedException>(updater.recoverPendingUpdate().exceptionOrNull())

        val reset = updater.resetPendingUpdate()

        assertTrue(reset.isSuccess)
        assertTrue(updater.hasPendingRepair(), "reset must leave a persistent repair obligation")
        val check = updater.checkUpstreamChanged()
        assertTrue(check.isSuccess)
        assertEquals(true, check.getOrNull(), "the marker forces an update without a network probe")

        val failed = updater.updateAll()
        assertTrue(failed.isFailure)
        assertTrue(updater.hasPendingRepair(), "a failed full update must not clear the marker")

        val working = SongDataUpdater(
            HttpClient(MockEngine { request ->
                val fileName = request.url.encodedPath.substringAfterLast('/')
                respond(
                    content = ByteReadChannel("mock-content-$fileName"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }),
            paths,
            fakeProvider()
        )
        val healed = working.updateAll()

        assertTrue(healed.isSuccess, "a successful full update clears the repair marker: $healed")
        assertFalse(working.hasPendingRepair())
        assertEquals("mock-content-info.csv", fs.read(songDataDir() / "info.csv") { readUtf8() })
    }

    @Test
    fun resetFailsClosedWhenTheRepairMarkerCannotBePersisted() = runTest {
        fs.createDirectories(songDataDir())
        writeRetainedScene(
            state = "COMMITTING",
            manifest = "",
            backups = mapOf("info.csv" to "garbage")
        )
        val failure = IllegalStateException("injected marker move failure")
        val marker = songDataDir() / "repair-required.marker"
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun atomicMove(source: Path, target: Path) {
                if (target == marker) throw failure
                delegate.atomicMove(source, target)
            }
        }
        val updater = SongDataUpdater(
            HttpClient(MockEngine { error("unused") }),
            paths,
            fakeProvider(),
            faultyFs
        )

        val reset = updater.resetPendingUpdate()

        assertTrue(reset.isFailure, "an unpersisted marker must fail the reset")
        assertEquals(failure, reset.exceptionOrNull())
        assertTrue(fs.exists(songBackupDir()), "the scene is retained when the marker cannot be written")
    }
}
