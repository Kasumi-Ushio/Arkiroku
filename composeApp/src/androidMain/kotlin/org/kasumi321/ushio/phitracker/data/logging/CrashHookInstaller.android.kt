package org.kasumi321.ushio.phitracker.data.logging

import okio.FileSystem
import okio.buffer
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

actual object CrashHookInstaller {

    private var installed = false

    actual fun install(store: LogFileStore) {
        if (installed) return
        installed = true

        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(store, thread, throwable)
            } catch (writeFailure: Exception) {
                writeFailure.printStackTrace()
            }
            originalHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(store: LogFileStore, thread: Thread, throwable: Throwable) {
        val path = store.crashLogPath(store.newCrashReportName("kotlin_crash"))
        val fs = FileSystem.SYSTEM
        val parent = path.parent ?: return
        if (!fs.exists(parent)) fs.createDirectories(parent)

        val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val time = "${local.date} ${local.hour.toString().padStart(2, '0')}:" +
                "${local.minute.toString().padStart(2, '0')}:${local.second.toString().padStart(2, '0')}"

        val sb = StringBuilder()
        sb.appendLine("=== Kotlin Uncaught Exception ===")
        sb.appendLine("Time: $time")
        sb.appendLine("Thread: ${thread.name}")
        sb.appendLine("Exception: ${throwable::class.qualifiedName}: ${throwable.message}")
        sb.appendLine()
        sb.appendLine(throwable.stackTraceToString())

        val causes = generateSequence(throwable.cause) { it.cause }
        causes.forEachIndexed { i, cause ->
            sb.appendLine()
            sb.appendLine("Caused by (${i + 1}): ${cause::class.qualifiedName}: ${cause.message}")
            sb.appendLine(cause.stackTraceToString())
        }

        fs.sink(path).buffer().use { it.writeUtf8(LogRedactor.redact(sb.toString())) }
        store.pruneCrashReports("kotlin_crash", MAX_CRASH_REPORTS)
    }

    private const val MAX_CRASH_REPORTS = 5
}
