package org.kasumi321.ushio.phitracker.data.platform

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import okio.Path
import org.kasumi321.ushio.phitracker.data.common.resultOf
import okio.Path.Companion.toPath

actual fun coilCacheDirectory(context: PlatformContext): Path = "${createPlatformPaths().cacheDir}/coil_cache".toPath()

actual suspend fun preloadIllustrationThumbnail(url: String, size: Int, allowHardware: Boolean): Result<Unit> = resultOf {
    // allowHardware has no iOS analogue (Skia software bitmaps); size still governs
    // the Coil memory-cache key, so it must match the on-screen request.
    val context = PlatformContext.INSTANCE
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(size)
        .crossfade(200)
        .build()
    val result = SingletonImageLoader.get(context).execute(request)
    require(result is SuccessResult) { "Unable to preload illustration thumbnail" }
}
