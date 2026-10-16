package org.kasumi321.ushio.phitracker.data.logging

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import org.kasumi321.ushio.phitracker.data.platform.AndroidPlatformContext

/**
 * Complements [AnrWatchDog] with system-confirmed ANRs: Android 11+ records
 * every process exit, so on cold start we can report exits the watchdog
 * cannot observe (e.g. background kills). Already-reported exits are skipped
 * via a persisted watermark.
 */
object SystemAnrExitReporter {

    fun reportRecentAnrs() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val context = AndroidPlatformContext.applicationContext ?: return
        runCatching {
            val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastReported = prefs.getLong(KEY_LAST_EXIT_TIMESTAMP, 0L)
            val exits = activityManager.getHistoricalProcessExitReasons(null, 0, 10).orEmpty()
            val newestTimestamp = exits.maxOfOrNull { it.timestamp } ?: lastReported
            val newAnrCount = exits.count {
                it.reason == ApplicationExitInfo.REASON_ANR && it.timestamp > lastReported
            }
            if (newAnrCount > 0) {
                AppLogger.event("anr", "system_confirmed", mapOf("count" to newAnrCount.toString()))
            }
            if (newestTimestamp > lastReported) {
                prefs.edit().putLong(KEY_LAST_EXIT_TIMESTAMP, newestTimestamp).apply()
            }
        }.onFailure {
            AppLogger.w("SystemAnrExitReporter") { "Failed to read historical exit reasons" }
        }
    }

    private const val PREFS_NAME = "phi_tracker_exit_reports"
    private const val KEY_LAST_EXIT_TIMESTAMP = "last_exit_timestamp"
}
