package org.kasumi321.ushio.phitracker.data.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val THEME_PICKER_TAG = "ThemeImagePicker"

actual val shouldShowThemeColorSourceSetting: Boolean = false

@Composable
actual fun rememberThemeImageColorPicker(onResult: (ThemeImageColorPickResult?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }

        val context = AndroidPlatformContext.currentActivity
            ?: AndroidPlatformContext.applicationContext
        if (context == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }

        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read selected image")
                val image = decodeImageBitmap(bytes)
                    ?: error("Unable to decode selected image")
                ThemeImageColorPickResult(uri = uri.toString(), image = image)
            }.onFailure {
                AppLogger.e(THEME_PICKER_TAG, "Failed to decode theme image", it)
            }.getOrNull()
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

private fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    val maxSize = 256
    val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
    val bitmap = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    ) ?: return null

    val largestSide = maxOf(bitmap.width, bitmap.height)
    val sampled = if (largestSide > maxSize) {
        val scale = maxSize.toFloat() / largestSide
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        bitmap.recycle()
        scaled
    } else {
        bitmap
    }
    return sampled.asImageBitmap()
}

private fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sampleSize = 1
    while ((width / sampleSize) > maxSize * 2 || (height / sampleSize) > maxSize * 2) {
        sampleSize *= 2
    }
    return sampleSize
}
