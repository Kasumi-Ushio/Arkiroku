package org.kasumi321.ushio.phitracker.data.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.OSLogWriter

actual fun platformLogWriter(): LogWriter = OSLogWriter(
    subsystem = "org.kasumi321.ushio.phitracker",
    category = "App",
)
