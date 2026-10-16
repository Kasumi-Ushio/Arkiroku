package org.kasumi321.ushio.phitracker.data.platform

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

private const val TAG = "B30Background"

/**
 * Attempts to open an InputStream via [ContentResolver.openInputStream].
 * Catches all exceptions so a throw (e.g. WayDroid NPE from DocumentsUI)
 * does not prevent the caller from trying the fallback strategy.
 */
private fun tryOpenInputStream(resolver: android.content.ContentResolver, uri: Uri): InputStream? {
    return try {
        resolver.openInputStream(uri)
    } catch (e: Exception) {
        AppLogger.w(TAG, "openInputStream threw for $uri: ${e.javaClass.simpleName}: ${e.message}")
        null
    }
}

/**
 * Attempts to open an InputStream via [ContentResolver.openFileDescriptor].
 * Catches all exceptions and null returns so the caller can differentiate
 * between "provider refused" and "provider crashed".
 */
private fun tryOpenFileDescriptor(resolver: android.content.ContentResolver, uri: Uri): InputStream? {
    return try {
        val pfd = resolver.openFileDescriptor(uri, "r")
        if (pfd != null) {
            ParcelFileDescriptor.AutoCloseInputStream(pfd)
        } else {
            AppLogger.w(TAG, "openFileDescriptor returned null for $uri")
            null
        }
    } catch (e: Exception) {
        AppLogger.w(TAG, "openFileDescriptor threw for $uri: ${e.javaClass.simpleName}: ${e.message}")
        null
    }
}

private fun copyBackgroundToPrivateStorage(context: Context, sourceUri: Uri): String? {
    return runCatching {
        val bgDir = File(context.filesDir, "b30-backgrounds").apply {
            if (!exists()) {
                check(mkdirs()) { "Failed to create b30-backgrounds directory" }
            }
        }
        val targetFile = File(bgDir, "bg_${System.currentTimeMillis()}.jpg")

        // Strategy 1: openInputStream (most providers)
        var input: InputStream? = tryOpenInputStream(context.contentResolver, sourceUri)

        // Strategy 2: openFileDescriptor (DocumentsProvider, MediaDocuments, etc.)
        if (input == null) {
            AppLogger.d(TAG, "openInputStream unavailable, trying openFileDescriptor for $sourceUri")
            input = tryOpenFileDescriptor(context.contentResolver, sourceUri)
        }

        requireNotNull(input) {
            "All provider-open strategies failed for URI: $sourceUri"
        }

        input.use { stream ->
            targetFile.outputStream().use { output ->
                stream.copyTo(output)
            }
        }

        AppLogger.d(
            TAG,
            "Copied background to ${targetFile.absolutePath} (${targetFile.length()} bytes)"
        )

        // Retain the latest 3 files, delete older ones.
        bgDir.listFiles()
            ?.sortedByDescending { file -> file.lastModified() }
            ?.drop(3)
            ?.forEach { file ->
                file.delete()
                AppLogger.d(TAG, "Cleaned up old background: ${file.name}")
            }

        Uri.fromFile(targetFile).toString()
    }.onFailure { e ->
        AppLogger.e(TAG, "Failed to copy background from $sourceUri to app-private storage", e)
    }.getOrNull()
}

/**
 * Returns a lambda that launches the platform image picker. When an image
 * is selected it is copied into app-private [filesDir]/b30-backgrounds/
 * and the resulting file:// URI is delivered via [onResult].
 */
@Composable
actual fun rememberB30BackgroundPicker(onResult: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            AppLogger.d(TAG, "Picker returned null URI (cancelled or failed)")
            onResult(null)
            return@rememberLauncherForActivityResult
        }

        // Capture the best available Context on the main thread before
        // dispatching the heavy I/O work to a background dispatcher.
        val context = AndroidPlatformContext.currentActivity
            ?: AndroidPlatformContext.applicationContext
        if (context == null) {
            AppLogger.e(TAG, "No Context available -- background cannot be copied")
            onResult(null)
            return@rememberLauncherForActivityResult
        }

        scope.launch(Dispatchers.IO) {
            val result = copyBackgroundToPrivateStorage(context, uri)
            AppLogger.d(TAG, "copyBackgroundToPrivateStorage: $result")
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
