package org.kasumi321.ushio.phitracker.data.logging

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Manages the runtime and crash log files with rotation.
 *
 * Supported operations:
 * - Read back / export all runtime logs
 * - Read back / export all crash logs
 * - Clear runtime logs
 * - Clear crash logs
 */
class LogFileStore(
    val fileSystem: FileSystem,
    runtimeLogDir: Path,
    crashLogDir: Path,
) {
    private val runtimeDir = runtimeLogDir
    private val crashDir = crashLogDir

    // ── Runtime log ──────────────────────────────────────────────

    /** Ensure the runtime log directory exists and return it (for the rolling file writer). */
    fun prepareRuntimeLogDir(): Path {
        ensureDir(runtimeDir)
        return runtimeDir
    }

    /** List all runtime log files, newest first. `.log` is the rolling writer's
     *  extension; `.txt` covers files written by app versions before the switch. */
    fun listRuntimeFiles(): List<Path> {
        val dir = runtimeDir
        if (!fileSystem.exists(dir)) return emptyList()
        return fileSystem.list(dir)
            .filter { it.name.startsWith("runtime") && (it.name.endsWith(".log") || it.name.endsWith(".txt")) }
            .sortedByDescending { fileSystem.metadataOrNull(it)?.lastModifiedAtMillis ?: 0L }
    }

    /** Read and concatenate all runtime log files into a single export string. */
    fun buildRuntimeExport(): String {
        val files = listRuntimeFiles()
        if (files.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("Arkiroku Runtime Logs")
        sb.appendLine("Files: ${files.size}")
        sb.appendLine()
        files.forEachIndexed { index, path ->
            sb.appendLine("===== Log ${index + 1} / ${files.size} =====")
            sb.appendLine("File: ${path.name}")
            sb.appendLine(LogRedactor.redact(readFileSafe(path)))
            sb.appendLine()
        }
        return sb.toString()
    }

    /** Delete all runtime log files. */
    fun clearRuntimeLogs() {
        listRuntimeFiles().forEach { fileSystem.delete(it, mustExist = false) }
    }

    // ── Crash log ────────────────────────────────────────────────

    private fun crashFile(name: String = "crash_report.txt") = crashDir / name

    /** Return a crash file path for writing. */
    fun crashLogPath(name: String = "crash_report.txt"): Path {
        ensureDir(crashDir)
        return crashFile(name)
    }

    /** Timestamped crash report file name, e.g. `kotlin_crash_20261016_153000.txt`. */
    fun newCrashReportName(prefix: String): String {
        val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val stamp = local.date.toString().replace("-", "") +
                "_${local.hour.toString().padStart(2, '0')}" +
                local.minute.toString().padStart(2, '0') +
                local.second.toString().padStart(2, '0')
        return "${prefix}_$stamp.txt"
    }

    /** Delete all but the newest [keep] crash files whose name starts with [prefix]. */
    fun pruneCrashReports(prefix: String, keep: Int) {
        listCrashFiles()
            .filter { it.name.startsWith(prefix) }
            .drop(keep)
            .forEach { fileSystem.delete(it, mustExist = false) }
    }

    /** List crash log files, newest first. */
    fun listCrashFiles(): List<Path> {
        val dir = crashDir
        if (!fileSystem.exists(dir)) return emptyList()
        return fileSystem.list(dir)
            .filter { it.name.endsWith(".txt") }
            .sortedByDescending { fileSystem.metadataOrNull(it)?.lastModifiedAtMillis ?: 0L }
    }

    fun buildCrashExport(): String {
        val files = listCrashFiles()
        if (files.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("Arkiroku Crash / ANR Reports")
        sb.appendLine("Files: ${files.size}")
        sb.appendLine()
        files.forEachIndexed { index, path ->
            sb.appendLine("===== Report ${index + 1} / ${files.size} =====")
            sb.appendLine("File: ${path.name}")
            sb.appendLine(LogRedactor.redact(readFileSafe(path)))
            sb.appendLine()
        }
        return sb.toString()
    }

    fun clearCrashLogs() {
        listCrashFiles().forEach { fileSystem.delete(it, mustExist = false) }
    }

    /** True if any runtime logs exist. */
    fun hasRuntimeLogs(): Boolean = listRuntimeFiles().isNotEmpty()

    /** True if any crash logs exist. */
    fun hasCrashLogs(): Boolean = listCrashFiles().isNotEmpty()

    // ── Internal ─────────────────────────────────────────────────

    private fun ensureDir(dir: Path) {
        if (!fileSystem.exists(dir)) fileSystem.createDirectories(dir)
    }

    private fun readFileSafe(path: Path): String {
        return try {
            fileSystem.read(path) { readUtf8() }
        } catch (e: Exception) {
            "Failed to read: ${e.message}"
        }
    }
}
