@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.kasumi321.ushio.phitracker.data.platform

import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

/**
 * iOS persistent data lives under Documents, which iCloud/iTunes backups and
 * device-to-device migration copy by default. Downloaded catalog data, scenes,
 * markers and quarantine directories are excluded so a partial commit cannot be
 * replayed on another device. The app re-applies the flag on startup, and any
 * scene that still arrives is validated fail-closed by `StagedFileSetUpdate`.
 */
actual fun markExcludedFromPlatformBackup(path: String) {
    runCatching {
        NSURL.fileURLWithPath(path).setResourceValue(
            value = true,
            forKey = NSURLIsExcludedFromBackupKey,
            error = null
        )
    }
}
