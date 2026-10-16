package org.kasumi321.ushio.phitracker.data.logging

import android.os.Handler
import android.os.Looper
import okio.FileSystem
import okio.buffer
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class AnrWatchDog(
    private val store: LogFileStore,
    private val thresholdMs: Long = 4_000L,
    private val debounceMs: Long = 15_000L,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var monitorThread: Thread? = null
    private var lastAnrRecordedAt: Long = 0L

    @Volatile
    private var tickCounter = 0L

    fun start() {
        if (monitorThread != null) return
        monitorThread = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(thresholdMs / 2)
                } catch (_: InterruptedException) {
                    break
                }
                val beforePost = tickCounter
                handler.post { tickCounter++ }
                try {
                    Thread.sleep(thresholdMs / 2)
                } catch (_: InterruptedException) {
                    break
                }
                if (tickCounter == beforePost) {
                    onAnrDetected()
                }
            }
        }, "AnrWatchDog").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        monitorThread?.interrupt()
        monitorThread = null
    }

    private fun onAnrDetected() {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastAnrRecordedAt < debounceMs) return
        lastAnrRecordedAt = now

        try {
            val mainThread = Looper.getMainLooper().thread
            val stackTrace = mainThread.stackTrace

            val path = store.crashLogPath("anr_report.txt")
            val fs = FileSystem.SYSTEM
            val parent = path.parent ?: return
            if (!fs.exists(parent)) fs.createDirectories(parent)

            val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val time = "${local.date} ${local.hour.toString().padStart(2, '0')}:" +
                    "${local.minute.toString().padStart(2, '0')}:${local.second.toString().padStart(2, '0')}"

            val sb = StringBuilder()
            sb.appendLine("=== Suspected ANR (watchdog-based) ===")
            sb.appendLine("Time: $time")
            sb.appendLine("Threshold: ${thresholdMs}ms")
            sb.appendLine("Thread: ${mainThread.name}")
            sb.appendLine()
            sb.appendLine("Main thread stack trace:")
            stackTrace.forEach { element ->
                sb.appendLine("  $element")
            }

            fs.sink(path).buffer().use { it.writeUtf8(LogRedactor.redact(sb.toString())) }
        } catch (e: Exception) {
            AppLogger.w("AnrWatchDog", "Failed to write ANR report: ${e.message}")
        }
    }
}
