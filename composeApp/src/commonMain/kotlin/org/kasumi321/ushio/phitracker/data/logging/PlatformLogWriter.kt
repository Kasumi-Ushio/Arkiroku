package org.kasumi321.ushio.phitracker.data.logging

import co.touchlab.kermit.LogWriter

/**
 * Platform console sink (Android Logcat / iOS OSLog). Must not require any
 * platform context so it can be constructed during [createLoggingState],
 * before Koin or platform paths exist. [AppLogger] wraps it in a
 * [RedactingLogWriter] before use.
 */
expect fun platformLogWriter(): LogWriter
