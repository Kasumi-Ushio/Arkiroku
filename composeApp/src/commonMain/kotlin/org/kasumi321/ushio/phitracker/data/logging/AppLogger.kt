package org.kasumi321.ushio.phitracker.data.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.io.RollingFileLogWriter
import co.touchlab.kermit.io.RollingFileLogWriterConfig
import kotlinx.io.files.Path

object AppLogger {

    private var _logger: Logger? = null
    val logger: Logger get() = _logger ?: Logger(StaticConfig())

    var fileWriterActive: Boolean = false
        private set

    /**
     * [platformWriter] is a test seam: host-side unit tests run without
     * Android/iOS logging backends, so they inject a no-op writer.
     */
    fun init(config: LoggerConfig, platformWriter: LogWriter = platformLogWriter()) {
        val writers = mutableListOf<LogWriter>()
        writers += RedactingLogWriter(platformWriter)
        fileWriterActive = false
        if (config.isDebugBuild) {
            writers += RedactingLogWriter(createRuntimeFileWriter(config.logFileStore))
            fileWriterActive = true
        }
        val minSeverity = if (config.isDebugBuild) Severity.Debug else Severity.Warn
        _logger = Logger(StaticConfig(minSeverity = minSeverity, logWriterList = writers))
        i("AppLogger") { "Logging system initialised, fileWriter=$fileWriterActive" }
        event("init", "logging", mapOf("fileWriter" to fileWriterActive.toString()))
    }

    /** Test hook: replace the backing logger directly, bypassing writer assembly. */
    internal fun initForTesting(logger: Logger) {
        _logger = logger
        fileWriterActive = false
    }

    private fun createRuntimeFileWriter(store: LogFileStore): LogWriter =
        RollingFileLogWriter(
            RollingFileLogWriterConfig(
                logFileName = "runtime",
                logFilePath = Path(store.prepareRuntimeLogDir().toString()),
                rollOnSize = RUNTIME_LOG_ROLL_SIZE_BYTES,
                maxLogFiles = RUNTIME_LOG_MAX_FILES,
            )
        )

    fun event(
        category: String,
        name: String,
        attributes: Map<String, String> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        logger.i(throwable = throwable, tag = "Event") {
            val attrStr = if (attributes.isEmpty()) {
                ""
            } else {
                " " + attributes.entries.joinToString(" ") { (key, value) ->
                    "${sanitizeKey(key)}=${sanitizeValue(key, value)}"
                }
            }
            "[event] category=${sanitizeKey(category)} name=${sanitizeKey(name)}$attrStr"
        }
    }

    private fun sanitizeKey(key: String): String = key.filter { it.isLetterOrDigit() || it == '_' || it == '-' }

    private fun sanitizeValue(key: String, value: String): String {
        val lowerKey = key.lowercase()
        if (lowerKey.endsWith("present") && (value == "true" || value == "false")) {
            return value
        }
        if (lowerKey.contains("token") || lowerKey.contains("platformid") || lowerKey.contains("credential")) {
            return "<redacted>"
        }
        return value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(120)
    }

    fun i(tag: String, message: String) = logger.i(message, tag = tag)

    fun i(tag: String, message: () -> String) = logger.i(tag = tag) { message() }

    fun d(tag: String, message: String) = logger.d(message, tag = tag)

    fun d(tag: String, message: () -> String) = logger.d(tag = tag) { message() }

    fun w(tag: String, message: String) = logger.w(message, tag = tag)

    fun w(tag: String, message: () -> String) = logger.w(tag = tag) { message() }

    fun w(tag: String, throwable: Throwable, message: () -> String) =
        logger.w(throwable = throwable, tag = tag) { message() }

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        logger.e(message, throwable, tag = tag)

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) =
        logger.e(throwable = throwable, tag = tag) { message() }
}

data class LoggerConfig(
    val isDebugBuild: Boolean,
    val logFileStore: LogFileStore,
)

private const val RUNTIME_LOG_ROLL_SIZE_BYTES = 2L * 1024L * 1024L
private const val RUNTIME_LOG_MAX_FILES = 6
