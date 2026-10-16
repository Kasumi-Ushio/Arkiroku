package org.kasumi321.ushio.phitracker.data.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.LogcatWriter

actual fun platformLogWriter(): LogWriter = LogcatWriter()
