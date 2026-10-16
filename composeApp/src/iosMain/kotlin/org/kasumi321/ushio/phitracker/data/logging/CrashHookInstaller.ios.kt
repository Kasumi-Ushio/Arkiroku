package org.kasumi321.ushio.phitracker.data.logging

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.buffer
import kotlin.time.Clock

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual object CrashHookInstaller {

    private var installed = false

    actual fun install(store: LogFileStore) {
        if (installed) return
        installed = true

        setUnhandledExceptionHook { throwable ->
            try {
                writeCrashLog(store, throwable)
            } catch (writeFailure: Exception) {
                println("CrashHookInstaller: failed to write crash log: ${writeFailure.message}")
            }
        }
    }

    private fun writeCrashLog(store: LogFileStore, throwable: Throwable) {
        val path = store.crashLogPath(store.newCrashReportName("kotlin_crash"))
        val fs = FileSystem.SYSTEM
        val parent = path.parent ?: return
        if (!fs.exists(parent)) fs.createDirectories(parent)

        val instant = Clock.System.now()
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val time = "${local.date} ${local.hour.toString().padStart(2, '0')}:" +
                "${local.minute.toString().padStart(2, '0')}:${local.second.toString().padStart(2, '0')}"

        val sb = StringBuilder()
        sb.appendLine("=== Kotlin/Native Unhandled Exception ===")
        sb.appendLine("Time: $time")
        sb.appendLine(LogRedactor.redact("Exception: ${throwable::class.qualifiedName}: ${throwable.message}"))
        sb.appendLine()

        // Bounded structured stack rendering; no full-string materialization.
        val stackFrames = throwable.getStackTrace()
        var totalBytes = 0
        val maxBytes = 64 * 1024
        var lineCount = 0
        for (frame in stackFrames) {
            if (lineCount >= 100) break
            val safeLine = LogRedactor.redact(frame)
            val lineBytes = safeLine.encodeToByteArray().size + 1
            if (totalBytes + lineBytes > maxBytes) break
            sb.appendLine(safeLine)
            totalBytes += lineBytes
            lineCount++
        }

        val sink = fs.sink(path).buffer()
        try {
            sink.writeUtf8(sb.toString())
        } finally {
            sink.close()
        }
        store.pruneCrashReports("kotlin_crash", MAX_CRASH_REPORTS)
    }

    private const val MAX_CRASH_REPORTS = 5
}
