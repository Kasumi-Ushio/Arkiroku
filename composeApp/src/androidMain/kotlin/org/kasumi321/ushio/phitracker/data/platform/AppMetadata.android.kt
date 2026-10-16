package org.kasumi321.ushio.phitracker.data.platform

import android.content.pm.ApplicationInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun getAppMetadata(): AppMetadata {
    val context = AndroidPlatformContext.applicationContext
    if (context == null) {
        appMetadataFallbackUsed = true
        return AppMetadata(
            versionName = "0.1.0",
            buildTime = formatBuildTime(),
            buildType = "Release"
        )
    }
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        AppMetadata(
            versionName = packageInfo.versionName ?: "0.1.0",
            buildTime = formatBuildTime(),
            buildType = if (isDebuggable) "Debug" else "Release"
        )
    } catch (e: Exception) {
        appMetadataFallbackUsed = true
        AppMetadata(
            versionName = "0.1.0",
            buildTime = formatBuildTime(),
            buildType = "Release"
        )
    }
}

/**
 * Set when [getAppMetadata] fell back to defaults (which also forces
 * `buildType = "Release"` and thus disables the file log writer). Logging is
 * not yet initialised when the fallback happens, so [MainActivity] consumes
 * this flag right after `createLoggingState` and reports it as an event.
 */
var appMetadataFallbackUsed: Boolean = false
    private set

private fun formatBuildTime(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ANDROID_BUILD_TIME_MILLIS))
