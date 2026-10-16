package org.kasumi321.ushio.phitracker.data.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * [LogWriter] decorator that enforces the project-wide invariant that
 * everything reaching a sink is redacted: the message is passed through
 * [LogRedactor], and any throwable's stack trace is redacted, appended to
 * the message and forwarded as `null` so the delegate cannot print it raw.
 */
class RedactingLogWriter(
    private val delegate: LogWriter,
) : LogWriter() {

    override fun isLoggable(tag: String, severity: Severity): Boolean =
        delegate.isLoggable(tag, severity)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val redacted = if (throwable == null) {
            LogRedactor.redact(message)
        } else {
            LogRedactor.redact(message) + "\n" + LogRedactor.redact(throwable.stackTraceToString())
        }
        delegate.log(severity, redacted, tag, null)
    }
}
