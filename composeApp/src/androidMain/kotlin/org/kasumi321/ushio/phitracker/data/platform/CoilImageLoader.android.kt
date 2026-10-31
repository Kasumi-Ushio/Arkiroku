package org.kasumi321.ushio.phitracker.data.platform

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import okio.Path
import org.kasumi321.ushio.phitracker.data.common.resultOf
import okio.Path.Companion.toOkioPath

actual fun coilCacheDirectory(context: PlatformContext): Path = context.cacheDir.resolve("coil_cache").toOkioPath()

actual suspend fun preloadIllustrationThumbnail(url: String, size: Int, allowHardware: Boolean): Result<Unit> = resultOf {
    val context = requireNotNull(AndroidPlatformContext.applicationContext) { "Android context is not initialized" }
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(size)
        .allowHardware(allowHardware)
        .crossfade(200)
        .build()
    val result = SingletonImageLoader.get(context).execute(request)
    require(result is SuccessResult) { "Unable to preload illustration thumbnail" }
}
