package org.kasumi321.ushio.phitracker.data.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Transaction-state coverage of the shared staged-commit engine: rollback
 * completeness at every commit position, the strict self-describing prestate
 * manifest (empty/truncated/unknown/extra fields fail closed), the
 * COMMITTING/COMMITTED boundaries, atomic (never delete-first) restore,
 * committed data never being rolled back, blocked scenes + reset, cache
 * invalidation after every rewrite, and cancellation semantics. This engine
 * backs both SongDataUpdater and CollectibleDataUpdater.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StagedFileSetUpdateTest {

    private val fs = FileSystem.SYSTEM
    private lateinit var base: Path
    private val fileNames = listOf("a.csv", "b.csv", "c.json")

    private val persistentDir get() = base / "data"
    private val cacheDir get() = base / "cache"
    private val stagingDir get() = cacheDir / "staging"
    private val backupDir get() = cacheDir / "backup"
    private val legacyBackupDir get() = base / "legacy-backup"
    private val manifestFile get() = backupDir / "prestate.txt"
    private val stateFile get() = backupDir / "transaction.state"
    private val repairMarker get() = persistentDir / "repair-required.marker"

    private val restoreSuffix = ".restore.tmp"

    @BeforeTest
    fun setUp() {
        base = "/tmp/staged-commit-test-${hashCode()}-${Random.nextLong().toString(16)}".toPath()
        fs.createDirectories(cacheDir)
    }

    @AfterTest
    fun tearDown() {
        if (::base.isInitialized) fs.deleteRecursively(base)
    }

    private fun engine(
        failOnCommitIndex: Int? = null,
        invalidateCalls: MutableList<Int>? = null,
        legacyBackupDir: Path? = null
    ): StagedFileSetUpdate {
        val client = HttpClient(
            MockEngine { request ->
                val fileName = request.url.encodedPath.substringAfterLast('/')
                respond(
                    content = ByteReadChannel("new-$fileName"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }
        )
        var commitIndex = 0
        return StagedFileSetUpdate(
            fs = fs,
            httpClient = client,
            baseUrl = "https://example.test/files/",
            persistentDir = persistentDir,
            stagingDir = stagingDir,
            backupDir = backupDir,
            legacyBackupDir = legacyBackupDir,
            fileNames = fileNames,
            invalidateCache = { invalidateCalls?.add(1) ?: Unit },
            commitAttempt = { _, move ->
                val index = commitIndex++
                if (failOnCommitIndex != null && index == failOnCommitIndex) {
                    throw RuntimeException("injected commit failure #$index")
                }
                move()
            }
        )
    }

    private fun downloadClient(): HttpClient = HttpClient(
        MockEngine { request ->
            val fileName = request.url.encodedPath.substringAfterLast('/')
            respond(
                content = ByteReadChannel("new-$fileName"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
    )

    private fun failingClient(): HttpClient = HttpClient(
        MockEngine { respond("upstream unavailable", HttpStatusCode.InternalServerError) }
    )

    private fun stagedUpdate(
        fileSystem: FileSystem = fs,
        client: HttpClient = downloadClient(),
        invalidateCalls: MutableList<Int>? = null,
        commitAttempt: (fileName: String, move: () -> Unit) -> Unit = { _, move -> move() },
        legacyBackupDir: Path? = null
    ): StagedFileSetUpdate = StagedFileSetUpdate(
        fs = fileSystem,
        httpClient = client,
        baseUrl = "https://example.test/files/",
        persistentDir = persistentDir,
        stagingDir = stagingDir,
        backupDir = backupDir,
        legacyBackupDir = legacyBackupDir,
        fileNames = fileNames,
        invalidateCache = { invalidateCalls?.add(1) ?: Unit },
        commitAttempt = commitAttempt
    )

    private fun manifestOf(backups: Map<String, String>): String = buildString {
        append("PHI-PRESTATE-V1\n")
        append(backups.size).append('\n')
        for ((name, content) in backups) {
            append(content.encodeToByteArray().size).append('\t').append(name).append('\n')
        }
    }

    private fun writePersistent(contents: Map<String, String>) {
        fs.createDirectories(persistentDir)
        for ((name, content) in contents) fs.write(persistentDir / name) { writeUtf8(content) }
    }

    /**
     * Writes a retained scene. [rawManifest]/[rawState] bypass the well-formed
     * writers for corruption tests.
     */
    private fun writeScene(
        persistent: Map<String, String> = emptyMap(),
        backups: Map<String, String> = emptyMap(),
        state: String? = null,
        rawState: String? = null,
        rawManifest: String? = null,
        omitManifest: Boolean = false
    ) {
        writePersistent(persistent)
        fs.createDirectories(backupDir)
        for ((name, content) in backups) fs.write(backupDir / name) { writeUtf8(content) }
        if (!omitManifest) {
            val manifest = rawManifest ?: manifestOf(backups)
            fs.write(manifestFile) { writeUtf8(manifest) }
        }
        if (rawState != null) {
            fs.write(stateFile) { writeUtf8(rawState) }
        } else if (state != null) {
            fs.write(stateFile) { writeUtf8("$state\n") }
        }
    }

    private fun writeLegacyScene(
        manifest: String?,
        backups: Map<String, String>,
        state: String? = null
    ) {
        fs.createDirectories(legacyBackupDir)
        for ((name, content) in backups) fs.write(legacyBackupDir / name) { writeUtf8(content) }
        if (manifest != null) fs.write(legacyBackupDir / "prestate.txt") { writeUtf8(manifest) }
        if (state != null) fs.write(legacyBackupDir / "transaction.state") { writeUtf8(state) }
    }

    private fun contentOf(name: String): String? =
        if (fs.exists(persistentDir / name)) fs.read(persistentDir / name) { readUtf8() } else null

    private fun assertPersistent(contents: Map<String, String>) {
        for ((name, content) in contents) {
            assertEquals(content, contentOf(name), "$name content")
        }
    }

    // ---------------------------------------------------------------------
    // Runtime rollback matrix
    // ---------------------------------------------------------------------

    @Test
    fun commitFailureAtEveryPositionRestoresAllPreExistingFiles() = runTest {
        for (failIndex in fileNames.indices) {
            setUp(); try {
                writePersistent(fileNames.associateWith { "old-$it" })
                val invalidations = mutableListOf<Int>()

                val result = engine(failIndex, invalidations).updateAll()

                assertTrue(result.isFailure, "commit at $failIndex must fail")
                for (name in fileNames) {
                    assertEquals("old-$name", contentOf(name), "$name must keep old content (fail at $failIndex)")
                }
                assertFalse(fs.exists(stagingDir), "staging cleaned (fail at $failIndex)")
                assertFalse(fs.exists(backupDir), "backup cleaned (fail at $failIndex)")
                assertTrue(invalidations.isEmpty(), "a fully rolled back commit leaves disk unchanged (fail at $failIndex)")
            } finally {
                tearDown()
            }
        }
    }

    @Test
    fun commitFailureAtEveryPositionRemovesNewlyCreatedFiles() = runTest {
        for (failIndex in fileNames.indices) {
            setUp(); try {
                val invalidations = mutableListOf<Int>()

                val result = engine(failIndex, invalidations).updateAll()

                assertTrue(result.isFailure, "commit at $failIndex must fail")
                for (name in fileNames) {
                    assertFalse(
                        fs.exists(persistentDir / name),
                        "$name must not survive a failed FIRST commit (fail at $failIndex)"
                    )
                }
                assertFalse(fs.exists(stagingDir), "staging cleaned (fail at $failIndex)")
                assertFalse(fs.exists(backupDir), "backup cleaned (fail at $failIndex)")
                assertTrue(invalidations.isEmpty(), "a fully rolled back commit must not invalidate caches")
            } finally {
                tearDown()
            }
        }
    }

    @Test
    fun successfulCommitInvalidatesCacheExactlyOnce() = runTest {
        writePersistent(fileNames.associateWith { "old-$it" })
        val invalidations = mutableListOf<Int>()

        val result = engine(invalidateCalls = invalidations).updateAll()

        assertTrue(result.isSuccess)
        assertEquals(1, invalidations.size)
        for (name in fileNames) {
            assertEquals("new-$name", contentOf(name))
        }
        assertFalse(fs.exists(backupDir), "a fully committed backup is consumed")
        assertFalse(fs.exists(stagingDir))
    }

    @Test
    fun cancellationDuringDownloadPropagatesAndLeavesNoResidue() {
        val dispatcher = StandardTestDispatcher()
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val client = HttpClient(
                MockEngine(
                    MockEngineConfig().apply {
                        this.dispatcher = dispatcher
                        addHandler {
                            gate.await()
                            respond(ByteReadChannel("never-reached"))
                        }
                    }
                )
            )
            val staged = StagedFileSetUpdate(
                fs = fs,
                httpClient = client,
                baseUrl = "https://example.test/files/",
                persistentDir = persistentDir,
                stagingDir = stagingDir,
                backupDir = backupDir,
                fileNames = fileNames,
                invalidateCache = { error("must not run on a cancelled update") }
            )
            var returned: Result<Unit>? = null

            val job = launch { returned = staged.updateAll() }
            runCurrent()
            assertTrue(fs.exists(stagingDir), "staging exists while downloading")

            job.cancelAndJoin()

            assertTrue(job.isCancelled, "cancellation must propagate instead of becoming a failure Result")
            assertNull(returned, "a cancelled update must not produce a Result")
            assertFalse(fs.exists(stagingDir), "cancelled update cleans staging")
            assertFalse(fs.exists(backupDir), "cancelled update cleans backup")
            assertEquals(
                emptyList<String>(),
                if (fs.exists(persistentDir)) fs.list(persistentDir) else emptyList(),
                "no persistent file written"
            )
        }
    }

    // ---------------------------------------------------------------------
    // Preparation failures
    // ---------------------------------------------------------------------

    @Test
    fun failureCreatingPersistentDirectoryIsReturnedAsFailure() = runTest {
        val failure = IllegalStateException("injected persistent directory failure")
        val invalidations = mutableListOf<Int>()
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun createDirectory(dir: Path, mustCreate: Boolean) {
                if (dir == persistentDir) throw failure
                super.createDirectory(dir, mustCreate)
            }
        }

        val result = stagedUpdate(fileSystem = faultyFs, invalidateCalls = invalidations).updateAll()

        assertTrue(result.isFailure, "preparation errors must not escape as raw throws")
        assertSame(failure, result.exceptionOrNull())
        assertFalse(fs.exists(persistentDir))
        assertFalse(fs.exists(stagingDir))
        assertFalse(fs.exists(backupDir))
        assertTrue(invalidations.isEmpty())
    }

    @Test
    fun failurePreparingStagingDirectoryIsReturnedAsFailure() = runTest {
        writePersistent(fileNames.associateWith { "old-$it" })
        val failure = IllegalStateException("injected staging directory failure")
        val invalidations = mutableListOf<Int>()
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun createDirectory(dir: Path, mustCreate: Boolean) {
                if (dir == stagingDir) throw failure
                super.createDirectory(dir, mustCreate)
            }
        }

        val result = stagedUpdate(fileSystem = faultyFs, invalidateCalls = invalidations).updateAll()

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertPersistent(fileNames.associateWith { "old-$it" })
        assertFalse(fs.exists(stagingDir))
        assertFalse(fs.exists(backupDir))
        assertTrue(invalidations.isEmpty())
    }

    @Test
    fun commitFailureWithMixedPrestateRestoresOnlyFilesThatExisted() = runTest {
        writePersistent(mapOf("a.csv" to "old-a.csv", "c.json" to "old-c.json"))
        val invalidations = mutableListOf<Int>()

        val result = engine(failOnCommitIndex = 2, invalidateCalls = invalidations).updateAll()

        assertTrue(result.isFailure)
        assertEquals("old-a.csv", contentOf("a.csv"))
        assertFalse(fs.exists(persistentDir / "b.csv"), "a file absent before must not survive rollback")
        assertEquals("old-c.json", contentOf("c.json"))
        assertFalse(fs.exists(stagingDir))
        assertFalse(fs.exists(backupDir))
        assertTrue(invalidations.isEmpty())
    }

    @Test
    fun backupCopyFailureLeavesPersistentFilesUntouched() = runTest {
        writePersistent(fileNames.associateWith { "old-$it" })
        val failure = IllegalStateException("injected backup copy failure")
        val invalidations = mutableListOf<Int>()
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun copy(source: Path, target: Path) {
                if (target.parent == backupDir) throw failure
                delegate.copy(source, target)
            }
        }

        val result = stagedUpdate(fileSystem = faultyFs, invalidateCalls = invalidations).updateAll()

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertPersistent(fileNames.associateWith { "old-$it" })
        assertFalse(fs.exists(stagingDir))
        assertFalse(fs.exists(backupDir), "a backup this run created but never committed is discarded")
        assertTrue(invalidations.isEmpty())
    }

    @Test
    fun preparationFailureBeforeRecoveryKeepsEntryBackup() = runTest {
        fs.createDirectories(backupDir)
        fs.write(backupDir / "a.csv") { writeUtf8("old-a.csv") }
        fs.write(manifestFile) { writeUtf8(manifestOf(mapOf("a.csv" to "old-a.csv"))) }
        fs.write(stateFile) { writeUtf8("COMMITTED\n") }
        val failure = IllegalStateException("injected persistent directory failure")
        val invalidations = mutableListOf<Int>()
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun createDirectory(dir: Path, mustCreate: Boolean) {
                if (dir == persistentDir) throw failure
                super.createDirectory(dir, mustCreate)
            }
        }

        val result = stagedUpdate(
            fileSystem = faultyFs,
            client = failingClient(),
            invalidateCalls = invalidations
        ).updateAll()

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertTrue(fs.exists(backupDir), "a preparation failure must not delete the entry scene")
        assertTrue(fs.exists(manifestFile))
        assertTrue(fs.exists(stateFile))
        assertFalse(fs.exists(stagingDir))
        assertTrue(invalidations.isEmpty())
    }

    // ---------------------------------------------------------------------
    // Incomplete runtime rollback
    // ---------------------------------------------------------------------

    @Test
    fun rollbackRestoreFailureReturnsStagedRollbackExceptionAndRetainsBackup() = runTest {
        writePersistent(fileNames.associateWith { "old-$it" })
        val commitFailure = IllegalStateException("injected commit failure")
        val rollbackFailure = IllegalStateException("injected rollback restore failure")
        val invalidations = mutableListOf<Int>()
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun atomicMove(source: Path, target: Path) {
                if (target == persistentDir / "a.csv" && source.name == "a.csv$restoreSuffix") {
                    throw rollbackFailure
                }
                delegate.atomicMove(source, target)
            }
        }
        var commits = 0

        val result = stagedUpdate(
            fileSystem = faultyFs,
            invalidateCalls = invalidations,
            commitAttempt = { _, move ->
                if (commits++ == 1) throw commitFailure
                move()
            }
        ).updateAll()

        val exception = result.exceptionOrNull()
        assertIs<StagedRollbackException>(exception)
        assertSame(commitFailure, exception.primary)
        assertSame(commitFailure, exception.cause)
        assertEquals(listOf("a.csv"), exception.failures.map { it.fileName })
        assertSame(rollbackFailure, exception.failures.single().cause)
        assertTrue(exception.message?.contains("backup retained") == true)

        assertEquals("new-a.csv", contentOf("a.csv"), "failed restore keeps the committed file (no delete-first)")
        assertEquals("old-b.csv", contentOf("b.csv"))
        assertEquals("old-c.json", contentOf("c.json"))
        assertEquals(
            manifestOf(fileNames.associateWith { "old-$it" }),
            fs.read(manifestFile) { readUtf8() }
        )
        for (name in fileNames) {
            assertEquals("old-$name", fs.read(backupDir / name) { readUtf8() })
        }
        assertFalse(fs.exists(stagingDir))
        assertFalse(fs.exists(backupDir / "a.csv$restoreSuffix"), "restore temp is cleaned up")
        assertEquals(1, invalidations.size, "an incomplete rollback leaves mixed data and must invalidate")
    }

    @Test
    fun rollbackRestoreCopyFailureKeepsCurrentFileAndRetainsBackup() = runTest {
        writePersistent(fileNames.associateWith { "old-$it" })
        val commitFailure = IllegalStateException("injected commit failure")
        val rollbackFailure = IllegalStateException("injected rollback copy failure")
        val invalidations = mutableListOf<Int>()
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun copy(source: Path, target: Path) {
                if (source.parent == backupDir && target == persistentDir / "a.csv$restoreSuffix") {
                    throw rollbackFailure
                }
                delegate.copy(source, target)
            }
        }
        var commits = 0

        val result = stagedUpdate(
            fileSystem = faultyFs,
            invalidateCalls = invalidations,
            commitAttempt = { _, move ->
                if (commits++ == 1) throw commitFailure
                move()
            }
        ).updateAll()

        val exception = result.exceptionOrNull()
        assertIs<StagedRollbackException>(exception, "actual=$exception")
        assertSame(commitFailure, exception.primary)
        assertEquals(listOf("a.csv"), exception.failures.map { it.fileName })
        assertSame(rollbackFailure, exception.failures.single().cause)

        assertEquals("new-a.csv", contentOf("a.csv"), "the failed restore must not delete the current file")
        assertEquals("old-b.csv", contentOf("b.csv"))
        assertEquals("old-c.json", contentOf("c.json"))
        assertTrue(fs.exists(backupDir / "prestate.txt"))
        assertEquals("old-a.csv", fs.read(backupDir / "a.csv") { readUtf8() }, "backup copy must be retained")
        assertFalse(fs.exists(stagingDir))
        assertEquals(1, invalidations.size)
    }

    @Test
    fun missingBackupForPreexistingFileIsCountedAsRollbackFailure() = runTest {
        writePersistent(fileNames.associateWith { "old-$it" })
        val commitFailure = IllegalStateException("injected commit failure after dropping backup")
        val invalidations = mutableListOf<Int>()

        val result = stagedUpdate(
            invalidateCalls = invalidations,
            commitAttempt = { fileName, move ->
                move()
                if (fileName == "a.csv") {
                    fs.delete(backupDir / fileName)
                    throw commitFailure
                }
            }
        ).updateAll()

        val exception = result.exceptionOrNull()
        assertIs<StagedRollbackException>(exception)
        assertSame(commitFailure, exception.primary)
        assertEquals(listOf("a.csv"), exception.failures.map { it.fileName })
        assertTrue(exception.failures.single().cause.message?.contains("backup missing") == true)

        assertEquals("new-a.csv", contentOf("a.csv"))
        assertEquals("old-b.csv", contentOf("b.csv"))
        assertEquals("old-c.json", contentOf("c.json"))
        assertEquals(
            manifestOf(fileNames.associateWith { "old-$it" }),
            fs.read(manifestFile) { readUtf8() }
        )
        assertFalse(fs.exists(backupDir / "a.csv"))
        assertTrue(fs.exists(backupDir / "b.csv"))
        assertTrue(fs.exists(backupDir / "c.json"))
        assertFalse(fs.exists(stagingDir))
        assertEquals(1, invalidations.size)
    }

    @Test
    fun nextUpdateRestoresRetainedBackupBeforeCommittingAgain() = runTest {
        writePersistent(mapOf("a.csv" to "old-a.csv", "b.csv" to "old-b.csv"))
        val commitFailure = IllegalStateException("injected commit failure")
        val rollbackFailure = IllegalStateException("injected rollback restore failure")
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun atomicMove(source: Path, target: Path) {
                if (target == persistentDir / "a.csv" && source.name == "a.csv$restoreSuffix") {
                    throw rollbackFailure
                }
                delegate.atomicMove(source, target)
            }
        }
        var commits = 0
        val first = stagedUpdate(
            fileSystem = faultyFs,
            commitAttempt = { _, move ->
                if (commits++ == 1) throw commitFailure
                move()
            }
        ).updateAll()

        assertIs<StagedRollbackException>(first.exceptionOrNull())
        assertEquals("new-a.csv", contentOf("a.csv"))
        assertEquals("old-b.csv", contentOf("b.csv"))
        assertEquals("COMMITTING", fs.read(stateFile) { readUtf8() }.trim())

        val recoveryInvalidations = mutableListOf<Int>()
        val recovered = stagedUpdate(
            client = failingClient(),
            invalidateCalls = recoveryInvalidations
        ).updateAll()

        assertTrue(recovered.isFailure)
        assertEquals("old-a.csv", contentOf("a.csv"), "backup restores earlier content")
        assertEquals("old-b.csv", contentOf("b.csv"))
        assertFalse(fs.exists(persistentDir / "c.json"))
        assertFalse(fs.exists(backupDir), "recovered backup is consumed")
        assertFalse(fs.exists(stagingDir))
        assertEquals(
            1,
            recoveryInvalidations.size,
            "recovery rewrote a.csv, so the provider cache must be invalidated even though the download failed"
        )

        val invalidations = mutableListOf<Int>()
        val second = stagedUpdate(invalidateCalls = invalidations).updateAll()

        assertTrue(second.isSuccess, "a recovered engine must not stay locked")
        for (name in fileNames) {
            assertEquals("new-$name", contentOf(name))
        }
        assertEquals(1, invalidations.size)
        assertFalse(fs.exists(backupDir))
        assertFalse(fs.exists(stagingDir))
    }

    @Test
    fun cancellationDuringCommitPropagatesAndSkipsCacheInvalidation() = runTest {
        writePersistent(fileNames.associateWith { "old-$it" })
        val invalidations = mutableListOf<Int>()
        var cancellation: CancellationException? = null
        var returned: Result<Unit>? = null
        var commits = 0

        val job = launch {
            returned = stagedUpdate(
                invalidateCalls = invalidations,
                commitAttempt = { _, move ->
                    if (commits++ == 1) {
                        val failure = CancellationException("injected commit cancellation")
                        cancellation = failure
                        throw failure
                    }
                    move()
                }
            ).updateAll()
        }
        job.join()

        assertTrue(job.isCancelled, "cancellation must propagate instead of becoming a failure Result")
        assertNull(returned, "a cancelled commit must not produce a Result")
        assertEquals("injected commit cancellation", cancellation?.message)
        assertTrue(invalidations.isEmpty(), "a fully rolled back cancelled commit leaves disk unchanged")
        assertPersistent(fileNames.associateWith { "old-$it" })
        assertFalse(fs.exists(stagingDir))
        assertFalse(fs.exists(backupDir))
    }

    @Test
    fun cancellationDuringIncompleteRollbackStillPropagatesAndRetainsBackup() = runTest {
        writePersistent(fileNames.associateWith { "old-$it" })
        val rollbackFailure = IllegalStateException("injected rollback restore failure")
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun atomicMove(source: Path, target: Path) {
                if (target == persistentDir / "a.csv" && source.name == "a.csv$restoreSuffix") {
                    throw rollbackFailure
                }
                delegate.atomicMove(source, target)
            }
        }
        val invalidations = mutableListOf<Int>()
        var returned: Result<Unit>? = null
        var commits = 0

        val job = launch {
            returned = stagedUpdate(
                fileSystem = faultyFs,
                invalidateCalls = invalidations,
                commitAttempt = { _, move ->
                    if (commits++ == 1) {
                        throw CancellationException("injected commit cancellation")
                    }
                    move()
                }
            ).updateAll()
        }
        job.join()

        assertTrue(job.isCancelled)
        assertNull(returned)
        assertEquals(1, invalidations.size, "an incomplete rollback must invalidate even while cancelling")
        assertEquals("new-a.csv", contentOf("a.csv"))
        assertEquals("old-b.csv", contentOf("b.csv"))
        assertEquals("old-c.json", contentOf("c.json"))
        assertTrue(fs.exists(manifestFile))
        assertEquals("COMMITTING", fs.read(stateFile) { readUtf8() }.trim())
        assertFalse(fs.exists(stagingDir))
    }

    // ---------------------------------------------------------------------
    // Strict manifest / state handling (fail closed)
    // ---------------------------------------------------------------------

    @Test
    fun emptyTruncatedUnknownOrExtraManifestFieldsAreBlockedAndKeepData() = runTest {
        val invalidManifests = listOf(
            "" to "empty file",
            "PHI-PRESTATE-V1\n" to "truncated after magic",
            "PHI-PRESTATE-V1\n2\n" to "count without entries",
            "PHI-PRESTATE-V1\n2\n9\ta.csv" to "fewer entries than count",
            "PHI-PRESTATE-V1\n1\n9\ta.csv\n9\tb.csv" to "extra entries beyond count",
            "PHI-PRESTATE-V1\n0\n9\ta.csv" to "entry without count",
            "PHI-PRESTATE-V1\n1\n9\ta.csv\textra" to "extra field",
            "PHI-PRESTATE-V1\n1\n9\tunknown.csv" to "unknown file name",
            "PHI-PRESTATE-V1\n1\nnot-a-size\ta.csv" to "unknown size field",
            "PHI-PRESTATE-WRONG\n0\n" to "unknown magic"
        )
        for ((manifest, label) in invalidManifests) {
            setUp(); try {
                writeScene(
                    persistent = mapOf("a.csv" to "current-a.csv", "b.csv" to "current-b.csv"),
                    backups = mapOf("a.csv" to "old-a.csv"),
                    state = "COMMITTING",
                    rawManifest = manifest
                )

                val result = stagedUpdate(client = failingClient()).recoverPendingUpdate()

                val error = result.exceptionOrNull()
                assertIs<StagedUpdateBlockedException>(error, "manifest case must fail closed: $label")
                assertEquals("current-a.csv", contentOf("a.csv"), "no data removed: $label")
                assertEquals("current-b.csv", contentOf("b.csv"), "no data removed: $label")
                assertTrue(fs.exists(manifestFile), "blocked scene is retained: $label")
                assertTrue(fs.exists(stateFile), "blocked scene is retained: $label")

                val update = stagedUpdate(client = failingClient()).updateAll()
                assertIs<StagedUpdateBlockedException>(update.exceptionOrNull(), "update stays blocked: $label")
                assertEquals("current-a.csv", contentOf("a.csv"), "preparation never runs: $label")
                assertTrue(fs.exists(backupDir), "blocked scene survives updateAll: $label")
            } finally {
                tearDown()
            }
        }
    }

    @Test
    fun committingSceneWithMissingOrTruncatedBackupIsBlocked() = runTest {
        writeScene(
            persistent = mapOf("a.csv" to "current-a.csv", "b.csv" to "current-b.csv"),
            backups = mapOf("a.csv" to "old-a.csv"),
            state = "COMMITTING",
            rawManifest = manifestOf(mapOf("a.csv" to "old-a.csv", "b.csv" to "old-b.csv"))
        )

        val result = stagedUpdate(client = failingClient()).recoverPendingUpdate()

        val error = result.exceptionOrNull()
        assertIs<StagedUpdateBlockedException>(error)
        assertTrue(error.reason.contains("b.csv"))
        assertEquals("current-a.csv", contentOf("a.csv"))
        assertEquals("current-b.csv", contentOf("b.csv"))
        assertTrue(fs.exists(backupDir))
    }

    @Test
    fun committingSceneWithTruncatedBackupIsBlocked() = runTest {
        val fullBackup = "old-b.csv"
        writeScene(
            persistent = mapOf("a.csv" to "current-a.csv", "b.csv" to "current-b.csv"),
            backups = mapOf("a.csv" to "old-a.csv", "b.csv" to fullBackup.dropLast(1)),
            state = "COMMITTING",
            rawManifest = manifestOf(mapOf("a.csv" to "old-a.csv", "b.csv" to fullBackup))
        )

        val result = stagedUpdate(client = failingClient()).recoverPendingUpdate()

        val error = result.exceptionOrNull()
        assertIs<StagedUpdateBlockedException>(error)
        assertEquals("current-a.csv", contentOf("a.csv"))
        assertEquals("current-b.csv", contentOf("b.csv"))
        assertTrue(fs.exists(backupDir / "b.csv"), "the corrupt scene is kept for inspection/reset")
        assertFalse(fs.exists(persistentDir / "b.csv$restoreSuffix"))
    }

    @Test
    fun preflightFailureKeepsRetainedBackupAndCurrentData() = runTest {
        writeScene(
            persistent = emptyMap(),
            backups = mapOf("a.csv" to "old-a.csv"),
            state = "COMMITTING"
        )
        // A non-regular target fails the preflight before any restore runs.
        fs.createDirectories(persistentDir / "a.csv")
        val invalidations = mutableListOf<Int>()

        val result = stagedUpdate(invalidateCalls = invalidations).recoverPendingUpdate()

        val error = result.exceptionOrNull()
        assertIs<StagedRollbackException>(error)
        assertEquals(listOf("a.csv"), error.failures.map { it.fileName })
        assertTrue(fs.metadata(persistentDir / "a.csv").isDirectory, "the current target is untouched")
        assertTrue(fs.exists(backupDir / "a.csv"), "the retained backup survives a preparation failure")
        assertTrue(fs.exists(manifestFile))
        assertTrue(invalidations.isEmpty(), "nothing was rewritten")

        val update = stagedUpdate(client = failingClient()).updateAll()

        assertTrue(update.isFailure)
        assertTrue(fs.exists(backupDir), "a failed update must not delete the entry scene")
    }

    @Test
    fun unknownTransactionStateIsBlockedUntilReset() = runTest {
        writeScene(
            persistent = mapOf("a.csv" to "current-a.csv"),
            backups = mapOf("a.csv" to "old-a.csv"),
            rawState = "HALF-WRITTEN\n"
        )

        val result = stagedUpdate(client = failingClient()).recoverPendingUpdate()

        assertIs<StagedUpdateBlockedException>(result.exceptionOrNull())
        assertEquals("current-a.csv", contentOf("a.csv"))

        val reset = stagedUpdate().resetPendingUpdate()
        assertTrue(reset.isSuccess, "reset must clear an unrecoverable scene: $reset")
        assertFalse(fs.exists(backupDir))
        assertEquals("current-a.csv", contentOf("a.csv"), "reset never rescues or deletes persistent data")

        val invalidations = mutableListOf<Int>()
        val update = stagedUpdate(invalidateCalls = invalidations).updateAll()

        assertTrue(update.isSuccess, "after reset a full download heals the dataset: $update")
        for (name in fileNames) assertEquals("new-$name", contentOf(name))
        assertEquals(1, invalidations.size)
    }

    @Test
    fun noStatePartialBackupIsDiscardedWithoutRestoreAndDoesNotLockUpdates() = runTest {
        writePersistent(mapOf("a.csv" to "current-a.csv"))
        fs.createDirectories(backupDir)
        fs.write(backupDir / "b.csv") { writeUtf8("old-b.csv") }

        val invalidations = mutableListOf<Int>()
        val failed = stagedUpdate(client = failingClient(), invalidateCalls = invalidations).updateAll()

        assertTrue(failed.isFailure, "the download still fails")
        assertEquals("current-a.csv", contentOf("a.csv"), "an uncommitted backup never rewrites data")
        assertFalse(fs.exists(backupDir), "the inert partial backup is discarded")
        assertTrue(invalidations.isEmpty())

        val second = stagedUpdate(invalidateCalls = invalidations).updateAll()

        assertTrue(second.isSuccess, "a partial backup from a crash must not lock future updates: $second")
        for (name in fileNames) assertEquals("new-$name", contentOf(name))
        assertEquals(1, invalidations.size)

        val third = stagedUpdate(invalidateCalls = invalidations).updateAll()
        assertTrue(third.isSuccess, "repeated updates keep working")
        assertEquals(2, invalidations.size)
    }

    // ---------------------------------------------------------------------
    // COMMITTING / COMMITTED boundaries
    // ---------------------------------------------------------------------

    @Test
    fun committedSceneNeverRollsBackNewData() = runTest {
        writePersistent(fileNames.associateWith { "new-$it" })
        writeScene(
            backups = fileNames.associateWith { "old-$it" },
            state = "COMMITTED"
        )
        val invalidations = mutableListOf<Int>()

        val result = stagedUpdate(client = failingClient(), invalidateCalls = invalidations).updateAll()

        assertTrue(result.isFailure, "the following download still fails")
        for (name in fileNames) {
            assertEquals("new-$name", contentOf(name), "COMMITTED data is authoritative: $name")
        }
        assertFalse(fs.exists(backupDir), "the stale committed backup is discarded")
        assertEquals(1, invalidations.size, "COMMITTED recovery revalidates the provider cache")
    }

    @Test
    fun committingSceneRewritesThenInvalidatesCacheEvenWhenDownloadFails() = runTest {
        writePersistent(fileNames.associateWith { "new-$it" })
        writeScene(
            backups = fileNames.associateWith { "old-$it" },
            state = "COMMITTING"
        )
        val invalidations = mutableListOf<Int>()

        val result = stagedUpdate(client = failingClient(), invalidateCalls = invalidations).updateAll()

        assertTrue(result.isFailure)
        for (name in fileNames) {
            assertEquals("old-$name", contentOf(name), "recovery restores the previous set: $name")
        }
        assertFalse(fs.exists(backupDir))
        assertEquals(1, invalidations.size, "a rewritten dataset must invalidate the cache before any download")
    }

    @Test
    fun restoreIsAtomicSoAFailedRestoreKeepsTheCurrentFile() = runTest {
        writePersistent(mapOf("a.csv" to "new-a.csv", "b.csv" to "new-b.csv", "c.json" to "new-c.json"))
        writeScene(
            backups = mapOf("a.csv" to "old-a.csv", "b.csv" to "old-b.csv", "c.json" to "old-c.json"),
            state = "COMMITTING"
        )
        val rollbackFailure = IllegalStateException("injected restore move failure")
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun atomicMove(source: Path, target: Path) {
                if (target == persistentDir / "b.csv" && source.name == "b.csv$restoreSuffix") {
                    throw rollbackFailure
                }
                delegate.atomicMove(source, target)
            }
        }
        val invalidations = mutableListOf<Int>()

        val result = stagedUpdate(fileSystem = faultyFs, invalidateCalls = invalidations).recoverPendingUpdate()

        val error = result.exceptionOrNull()
        assertIs<StagedRollbackException>(error)
        assertEquals(listOf("b.csv"), error.failures.map { it.fileName })
        assertSame(rollbackFailure, error.failures.single().cause)
        assertEquals("old-a.csv", contentOf("a.csv"), "already restored files stay restored")
        assertEquals("new-b.csv", contentOf("b.csv"), "a failed restore must not delete the target first")
        assertEquals("old-c.json", contentOf("c.json"), "remaining entries are still restored after one failure")
        assertTrue(fs.exists(backupDir), "the scene is retained for another attempt")
        assertFalse(fs.exists(persistentDir / "b.csv$restoreSuffix"))
        assertEquals(1, invalidations.size, "a rewritten dataset invalidates exactly once per recovery attempt")

        val retryInvalidations = mutableListOf<Int>()
        val retry = stagedUpdate(invalidateCalls = retryInvalidations).recoverPendingUpdate()

        assertTrue(retry.isSuccess, "the retained scene can be recovered on retry: $retry")
        for (name in fileNames) assertEquals("old-$name", contentOf(name))
        assertFalse(fs.exists(backupDir))
        assertEquals(1, retryInvalidations.size, "the retry only rewrites b.csv")
    }

    @Test
    fun filesOutsideTheManifestAreRemovedOnlyAfterAllRestoresSucceed() = runTest {
        writePersistent(mapOf("a.csv" to "new-a.csv", "b.csv" to "new-b.csv"))
        writeScene(
            backups = mapOf("a.csv" to "old-a.csv"),
            state = "COMMITTING",
            rawManifest = manifestOf(mapOf("a.csv" to "old-a.csv"))
        )
        val rollbackFailure = IllegalStateException("injected restore move failure")
        val faultyFs = object : ForwardingFileSystem(fs) {
            override fun atomicMove(source: Path, target: Path) {
                if (target == persistentDir / "a.csv" && source.name == "a.csv$restoreSuffix") {
                    throw rollbackFailure
                }
                delegate.atomicMove(source, target)
            }
        }

        val failed = stagedUpdate(fileSystem = faultyFs).recoverPendingUpdate()

        assertTrue(failed.isFailure)
        assertEquals("new-a.csv", contentOf("a.csv"))
        assertEquals("new-b.csv", contentOf("b.csv"), "newly committed files survive an incomplete restore")

        val recovered = stagedUpdate().recoverPendingUpdate()

        assertTrue(recovered.isSuccess)
        assertEquals("old-a.csv", contentOf("a.csv"))
        assertFalse(fs.exists(persistentDir / "b.csv"), "unlisted files are removed after a full restore")
        assertFalse(fs.exists(persistentDir / "c.json"), "unlisted files are removed after a full restore")
        assertFalse(fs.exists(backupDir))
    }

    @Test
    fun emptyPrestateManifestRestoresAFreshInstallByDeletingCommittedFiles() = runTest {
        writePersistent(fileNames.associateWith { "new-$it" })
        writeScene(
            backups = emptyMap(),
            state = "COMMITTING",
            rawManifest = manifestOf(emptyMap())
        )
        val invalidations = mutableListOf<Int>()

        val result = stagedUpdate(invalidateCalls = invalidations).recoverPendingUpdate()

        assertTrue(result.isSuccess, "a zero-entry manifest is valid and means 'no previous files': $result")
        for (name in fileNames) assertFalse(fs.exists(persistentDir / name), "$name is newly created and removed")
        assertEquals(1, invalidations.size)
        assertFalse(fs.exists(backupDir))
    }

    @Test
    fun hasPendingRecoveryTracksTheRetainedScene() = runTest {
        val staged = stagedUpdate()
        assertFalse(staged.hasPendingRecovery())

        fs.createDirectories(backupDir)
        assertTrue(staged.hasPendingRecovery())

        assertTrue(staged.resetPendingUpdate().isSuccess)
        assertFalse(staged.hasPendingRecovery())
    }

    @Test
    fun recoveryWithoutASceneIsANoOp() = runTest {
        val invalidations = mutableListOf<Int>()

        val result = stagedUpdate(invalidateCalls = invalidations).recoverPendingUpdate()

        assertTrue(result.isSuccess)
        assertTrue(invalidations.isEmpty())
        assertFalse(fs.exists(backupDir))
    }

    // ---------------------------------------------------------------------
    // Legacy cache scenes and the repair marker
    // ---------------------------------------------------------------------

    @Test
    fun legacyNoStateSceneIsRestoredWithCommittingSemantics() = runTest {
        writePersistent(mapOf("a.csv" to "new-a.csv", "b.csv" to "new-b.csv"))
        writeLegacyScene(manifest = "a.csv", backups = mapOf("a.csv" to "old-a.csv"))
        val invalidations = mutableListOf<Int>()
        val staged = stagedUpdate(
            client = failingClient(),
            invalidateCalls = invalidations,
            legacyBackupDir = legacyBackupDir
        )
        assertTrue(staged.hasPendingRecovery(), "a legacy scene counts as pending recovery")

        val result = staged.recoverPendingUpdate()

        assertTrue(result.isSuccess, "a valid non-empty legacy scene restores: $result")
        assertEquals("old-a.csv", contentOf("a.csv"))
        assertFalse(fs.exists(persistentDir / "b.csv"), "COMMITTING semantics remove files the old prestate lacked")
        assertFalse(fs.exists(legacyBackupDir), "the consumed legacy scene is deleted")
        assertEquals(1, invalidations.size, "the rewrite invalidates the provider cache")
        assertFalse(staged.hasPendingRecovery())
    }

    @Test
    fun updateRecoversAValidLegacySceneBeforeItsDownloadFails() = runTest {
        writePersistent(mapOf("a.csv" to "new-a.csv", "b.csv" to "new-b.csv"))
        writeLegacyScene(manifest = "a.csv", backups = mapOf("a.csv" to "old-a.csv"))
        val invalidations = mutableListOf<Int>()

        val result = stagedUpdate(
            client = failingClient(),
            invalidateCalls = invalidations,
            legacyBackupDir = legacyBackupDir
        ).updateAll()

        assertTrue(result.isFailure, "the download still fails")
        assertEquals("old-a.csv", contentOf("a.csv"), "the legacy scene is restored before downloading")
        assertFalse(fs.exists(persistentDir / "b.csv"))
        assertFalse(fs.exists(legacyBackupDir), "the legacy scene is consumed even when the download fails")
        assertEquals(1, invalidations.size)
    }

    @Test
    fun emptyOrInvalidLegacyScenesFailClosedUntilReset() = runTest {
        val cases: List<Triple<String?, Map<String, String>, String?>> = listOf(
            Triple(null, mapOf("a.csv" to "old-a.csv"), null),
            Triple("", mapOf("a.csv" to "old-a.csv"), null),
            Triple("a.csv\n", mapOf("a.csv" to "old-a.csv"), null),
            Triple("a.csv\na.csv", mapOf("a.csv" to "old-a.csv"), null),
            Triple("unknown.csv", mapOf("unknown.csv" to "old"), null),
            Triple("a.csv", emptyMap(), null),
            Triple("a.csv", mapOf("a.csv" to "old-a.csv"), "COMMITTING\n")
        )
        for ((manifest, backups, state) in cases) {
            setUp(); try {
                writePersistent(mapOf("a.csv" to "current-a.csv"))
                writeLegacyScene(manifest = manifest, backups = backups, state = state)
                val staged = stagedUpdate(client = failingClient(), legacyBackupDir = legacyBackupDir)

                val result = staged.recoverPendingUpdate()

                assertIs<StagedUpdateBlockedException>(
                    result.exceptionOrNull(),
                    "legacy scene must fail closed: manifest=$manifest state=$state"
                )
                assertEquals("current-a.csv", contentOf("a.csv"))
                assertTrue(fs.exists(legacyBackupDir), "the ambiguous scene is retained")
                assertTrue(staged.hasPendingRecovery())

                val update = stagedUpdate(client = failingClient(), legacyBackupDir = legacyBackupDir).updateAll()
                assertIs<StagedUpdateBlockedException>(update.exceptionOrNull())
                assertTrue(fs.exists(legacyBackupDir), "a blocked legacy scene survives updateAll")

                val reset = staged.resetPendingUpdate()
                assertTrue(reset.isSuccess, "reset clears the unrecoverable legacy scene: $reset")
                assertFalse(fs.exists(legacyBackupDir))
                assertTrue(staged.hasPendingRepair(), "reset records the outstanding full update")
            } finally {
                tearDown()
            }
        }
    }

    @Test
    fun repairMarkerForcesCheckAndOnlyASuccessfulUpdateClearsIt() = runTest {
        writePersistent(mapOf("a.csv" to "current-a.csv"))
        val staged = stagedUpdate(client = failingClient())

        val reset = staged.resetPendingUpdate()

        assertTrue(reset.isSuccess)
        assertTrue(staged.hasPendingRepair(), "reset persists the repair obligation")
        assertTrue(fs.exists(repairMarker), "the marker is stored with the persistent dataset")
        val check = staged.checkUpstreamChanged { "current-a.csv" }
        assertTrue(check.isSuccess)
        assertEquals(true, check.getOrNull(), "the marker forces an update without probing the network")

        val failed = staged.updateAll()
        assertTrue(failed.isFailure)
        assertTrue(staged.hasPendingRepair(), "a failed full update must not clear the marker")

        val working = stagedUpdate()
        val healed = working.updateAll()

        assertTrue(healed.isSuccess, "a successful full update clears the marker: $healed")
        assertFalse(working.hasPendingRepair())
        assertFalse(fs.exists(repairMarker))
        assertEquals(false, working.checkUpstreamChanged { "new-a.csv" }.getOrNull())
    }

    @Test
    fun bundledFallbackQuarantinesDownloadedFilesAndKeepsBothMarkers() = runTest {
        writePersistent(mapOf("a.csv" to "downloaded-a", "b.csv" to "downloaded-b"))
        val staged = stagedUpdate()
        val fallbackMarker = persistentDir / "bundled-fallback.marker"
        val quarantine = base / "data_quarantine"

        val result = staged.useBundledFallback()

        assertTrue(result.isSuccess, "fallback must not require the network: $result")
        assertTrue(staged.hasPendingRepair(), "the repair obligation survives the degradation")
        assertTrue(staged.isUsingBundledFallback())
        assertTrue(fs.exists(repairMarker))
        assertTrue(fs.exists(fallbackMarker))
        assertFalse(fs.exists(persistentDir / "a.csv"), "the provider can no longer read downloaded files")
        assertFalse(fs.exists(persistentDir / "b.csv"))
        assertEquals("downloaded-a", fs.read(quarantine / "a.csv") { readUtf8() })
        assertEquals("downloaded-b", fs.read(quarantine / "b.csv") { readUtf8() })

        // Idempotent: a crash-interrupted fallback can be completed at startup.
        assertTrue(staged.useBundledFallback().isSuccess)
        assertTrue(staged.isUsingBundledFallback())
        assertFalse(fs.exists(persistentDir / "a.csv"))
    }

    @Test
    fun bundledFallbackDropsABlockedLegacySceneInsteadOfReplayingIt() = runTest {
        val legacy = base / "legacy-backup"
        fs.createDirectories(legacy)
        fs.write(legacy / "prestate.txt") { writeUtf8("") }
        val staged = stagedUpdate(legacyBackupDir = legacy)
        assertTrue(staged.hasPendingRecovery())

        val result = staged.useBundledFallback()

        assertTrue(result.isSuccess, "the explicit fallback is a valid escape from a blocked scene: $result")
        assertFalse(staged.hasPendingRecovery(), "the legacy scene is discarded, not restored")
        assertFalse(fs.exists(legacy))
        assertTrue(staged.hasPendingRepair())
        assertTrue(staged.isUsingBundledFallback())
    }

    @Test
    fun successfulUpdateClearsFallbackAndDeletesTheQuarantine() = runTest {
        writePersistent(mapOf("a.csv" to "downloaded-a"))
        val staged = stagedUpdate()
        val quarantine = base / "data_quarantine"
        assertTrue(staged.useBundledFallback().isSuccess)
        assertTrue(fs.exists(quarantine))

        val healed = staged.updateAll()

        assertTrue(healed.isSuccess, "a full download restores the downloaded catalog: $healed")
        assertFalse(staged.isUsingBundledFallback())
        assertFalse(staged.hasPendingRepair())
        assertFalse(fs.exists(persistentDir / "bundled-fallback.marker"))
        assertFalse(fs.exists(repairMarker))
        assertFalse(fs.exists(quarantine), "the set-aside copy is no longer needed")
        assertEquals("new-a.csv", fs.read(persistentDir / "a.csv") { readUtf8() })
    }

    // ---------------------------------------------------------------------
    // resultOf semantics
    // ---------------------------------------------------------------------

    @Test
    fun resultOfReturnsSuccessForNormalCompletion() {
        val result = resultOf { 42 }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun resultOfWrapsOrdinaryFailureInFailureResult() {
        val failure = IllegalStateException("ordinary failure")

        val result = resultOf<Int> { throw failure }

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun resultOfRethrowsCancellationInsteadOfWrappingIt() {
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> {
            resultOf<Int> { throw cancellation }
        }

        assertSame(cancellation, thrown)
    }
}
