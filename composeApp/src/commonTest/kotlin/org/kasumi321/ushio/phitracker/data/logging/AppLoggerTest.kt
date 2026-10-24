package org.kasumi321.ushio.phitracker.data.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class AppLoggerTest {

    private val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "app_logger_test_${kotlin.random.Random.nextInt(10000, 99999)}"

    @AfterTest
    fun tearDown() {
        try {
            if (FileSystem.SYSTEM.exists(tempDir)) {
                FileSystem.SYSTEM.deleteRecursively(tempDir)
            }
        } catch (e: Exception) {
            println("AppLoggerTest cleanup failed: ${e.message}")
        }
    }

    private fun createStore(name: String): LogFileStore = LogFileStore(
        fileSystem = FileSystem.SYSTEM,
        runtimeLogDir = tempDir / "runtime_$name",
        crashLogDir = tempDir / "crash_$name"
    )

    /**
     * The rolling file writer flushes on a background coroutine, so export
     * assertions must wait for the marker to actually land on disk.
     */
    private fun awaitRuntimeExport(store: LogFileStore, marker: String): String {
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        var export = store.buildRuntimeExport()
        while (!export.contains(marker)) {
            check(deadline.hasNotPassedNow()) { "Timed out waiting for marker [$marker] in runtime export" }
            export = store.buildRuntimeExport()
        }
        return export
    }

    @Test
    fun `event method outputs readable text format with attributes`() {
        val store = createStore("format")

        AppLogger.init(LoggerConfig(isDebugBuild = true, logFileStore = store), NoOpLogWriter())
        assertTrue(AppLogger.fileWriterActive)

        AppLogger.event("init", "logging", mapOf("fileWriter" to "true"))
        AppLogger.event("navigation", "entered_home")

        // The rolling writer flushes asynchronously; wait for the marker
        // before asserting that the runtime file exists (the old immediate
        // check raced the writer's first flush).
        val export = awaitRuntimeExport(store, "[event] category=navigation name=entered_home")
        assertTrue(store.listRuntimeFiles().isNotEmpty())
        assertContains(export, "[event] category=init name=logging fileWriter=true")
    }

    @Test
    fun `event method works without file writer`() {
        val store = createStore("nofile")

        AppLogger.init(LoggerConfig(isDebugBuild = false, logFileStore = store), NoOpLogWriter())
        assertFalse(AppLogger.fileWriterActive)

        AppLogger.event("init", "koin", mapOf("status" to "success"))
        AppLogger.event("api", "test_started")
    }

    @Test
    fun `redacts sessionToken and preserves tokenPresent in event export`() {
        val store = createStore("redact")
        AppLogger.init(LoggerConfig(isDebugBuild = true, logFileStore = store), NoOpLogWriter())
        assertTrue(AppLogger.fileWriterActive)

        AppLogger.event("auth", "login", mapOf(
            "sessionToken" to "r_abc123secret",
            "tokenPresent" to "true",
            "platformId" to "android-test",
        ))

        val export = awaitRuntimeExport(store, "sessionToken=<redacted>")
        assertContains(export, "tokenPresent=true")
        assertContains(export, "platformId=<redacted>")
        assertFalse(export.contains("r_abc123secret"))
        assertFalse(export.contains("android-test"))
    }

    @Test
    fun `redacts sensitive data in direct log messages through file writer`() {
        val store = createStore("direct")
        AppLogger.init(LoggerConfig(isDebugBuild = true, logFileStore = store), NoOpLogWriter())

        AppLogger.i("TestTag", "Auth failed: sessionToken=secret123 X-LC-Session: abc456")
        AppLogger.i("TestTag", "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload")

        // The awaited marker must come from the last logged line: the writer
        // coroutine delivers entries in order, so earlier markers can appear
        // before later lines have landed.
        val export = awaitRuntimeExport(store, "Authorization: Bearer <redacted>")
        assertContains(export, "sessionToken=<redacted>")
        assertContains(export, "Authorization: Bearer <redacted>")
        assertFalse(export.contains("secret123"))
        assertFalse(export.contains("abc456"))
        assertFalse(export.contains("eyJhbGci"))
    }

    @Test
    fun `redacts access_token and mac_key in events`() {
        val store = createStore("tokens")
        AppLogger.init(LoggerConfig(isDebugBuild = true, logFileStore = store), NoOpLogWriter())

        AppLogger.event("oauth", "token_refresh", mapOf(
            "access_token" to "ya29.secret123",
            "mac_key" to "deadbeef-cafe-9999",
        ))

        val export = awaitRuntimeExport(store, "mac_key=<redacted>")
        assertContains(export, "access_token=<redacted>")
        assertFalse(export.contains("ya29.secret123"))
        assertFalse(export.contains("deadbeef-cafe-9999"))
    }

    @Test
    fun `retains non-sensitive attributes unchanged in export`() {
        val store = createStore("safe")
        AppLogger.init(LoggerConfig(isDebugBuild = true, logFileStore = store), NoOpLogWriter())

        AppLogger.event("ui", "button_click", mapOf(
            "button" to "export_logs",
            "screen" to "settings",
        ))

        val export = awaitRuntimeExport(store, "screen=settings")
        assertContains(export, "button=export_logs")
    }

    @Test
    fun `lazy messages are not evaluated below min severity`() {
        val capturing = CapturingLogWriter()
        AppLogger.initForTesting(
            Logger(StaticConfig(minSeverity = Severity.Warn, logWriterList = listOf(capturing)))
        )

        var evaluated = false
        AppLogger.d("Test") { evaluated = true; "debug message" }
        AppLogger.i("Test") { evaluated = true; "info message" }
        assertFalse(evaluated)
        assertTrue(capturing.entries.isEmpty())

        AppLogger.w("Test") { evaluated = true; "warn message" }
        assertTrue(evaluated)
        assertEquals(1, capturing.entries.size)
        assertEquals("warn message", capturing.entries.single().message)
    }

    @Test
    fun `event passes throwable through to writers`() {
        val capturing = CapturingLogWriter()
        AppLogger.initForTesting(
            Logger(StaticConfig(minSeverity = Severity.Verbose, logWriterList = listOf(capturing)))
        )

        val failure = IllegalStateException("sync boom")
        AppLogger.event("sync", "sync_failed", mapOf("reason" to "io"), throwable = failure)

        val entry = capturing.entries.single { it.tag == "Event" }
        assertEquals(failure, entry.throwable)
        assertContains(entry.message, "name=sync_failed")
        assertContains(entry.message, "reason=io")
    }

    @Test
    fun `RedactingLogWriter redacts message and flattens throwable`() {
        val capturing = CapturingLogWriter()
        val writer = RedactingLogWriter(capturing)

        val failure = IllegalStateException("Authorization: Bearer topsecretvalue123")
        writer.log(Severity.Error, "failed with sessionToken=abc123secret", "Test", failure)

        val entry = capturing.entries.single()
        assertNull(entry.throwable)
        assertContains(entry.message, "sessionToken=<redacted>")
        assertFalse(entry.message.contains("abc123secret"))
        assertFalse(entry.message.contains("topsecretvalue123"))
    }

    @Test
    fun `buildRuntimeExport redacts raw pre-existing runtime file`() {
        val runtimeDir = tempDir / "runtime_raw"
        val crashDir = tempDir / "crash_raw"
        val fs = FileSystem.SYSTEM
        if (!fs.exists(runtimeDir)) fs.createDirectories(runtimeDir)
        if (!fs.exists(crashDir)) fs.createDirectories(crashDir)

        val rawContent = """
            2026-05-16 12:00:00.000 I/OldLogger: Auth failed: sessionToken=leakedSecret999
            2026-05-16 12:00:01.000 I/OldLogger: X-LC-Session: leaked-session-hdr
            2026-05-16 12:00:02.000 I/OldLogger: platformId=old-android-test
            2026-05-16 12:00:03.000 I/OldLogger: access_token=ya29.leaked_oauth
        """.trimIndent()
        val rawPath = runtimeDir / "runtime_old_raw.txt"
        fs.write(rawPath) { writeUtf8(rawContent) }

        val store = LogFileStore(fs, runtimeDir, crashDir)
        assertTrue(store.hasRuntimeLogs())

        val export = store.buildRuntimeExport()
        assertContains(export, "sessionToken=<redacted>")
        assertContains(export, "X-LC-Session: <redacted>")
        assertContains(export, "platformId=<redacted>")
        assertContains(export, "access_token=<redacted>")
        assertFalse(export.contains("leakedSecret999"))
        assertFalse(export.contains("leaked-session-hdr"))
        assertFalse(export.contains("old-android-test"))
        assertFalse(export.contains("ya29.leaked_oauth"))
    }

    @Test
    fun `buildCrashExport redacts raw pre-existing crash file`() {
        val runtimeDir = tempDir / "runtime_crash_raw"
        val crashDir = tempDir / "crash_crash_raw"
        val fs = FileSystem.SYSTEM
        if (!fs.exists(runtimeDir)) fs.createDirectories(runtimeDir)
        if (!fs.exists(crashDir)) fs.createDirectories(crashDir)

        val rawCrash = """
            === Kotlin Uncaught Exception ===
            Time: 2026-05-16 12:05:00
            Thread: main
            Exception: java.lang.RuntimeException: API call failed with sessionToken=crashLeak123
            Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.crash_token_value
            mac_key=dead-beef-crash-key
            at com.example.Api.call(Api.kt:100)
            Caused by (1): java.io.IOException: Bearer leakedBearerInCause
        """.trimIndent()
        val crashPath = crashDir / "crash_old_raw.txt"
        fs.write(crashPath) { writeUtf8(rawCrash) }

        val store = LogFileStore(fs, runtimeDir, crashDir)
        assertTrue(store.hasCrashLogs())

        val export = store.buildCrashExport()
        assertContains(export, "sessionToken=<redacted>")
        assertContains(export, "Authorization: Bearer <redacted>")
        assertContains(export, "mac_key=<redacted>")
        assertContains(export, "Bearer <redacted>")
        assertFalse(export.contains("crashLeak123"))
        assertFalse(export.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(export.contains("dead-beef-crash-key"))
        assertFalse(export.contains("leakedBearerInCause"))
    }

    private class NoOpLogWriter : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) = Unit
    }

    private class CapturingLogWriter : LogWriter() {
        data class Entry(val severity: Severity, val message: String, val tag: String, val throwable: Throwable?)

        val entries = mutableListOf<Entry>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            entries += Entry(severity, message, tag, throwable)
        }
    }
}
