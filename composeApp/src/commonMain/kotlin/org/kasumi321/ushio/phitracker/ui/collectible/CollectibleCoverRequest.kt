package org.kasumi321.ushio.phitracker.ui.collectible

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Shared request builder for the small chapter-cover thumbnails shown in the
 * grouped list header and the drilled chapter header. Local files stay
 * read-only (never trigger a network fetch) and decode at 96px.
 */
@Composable
internal fun rememberLocalCoverRequest(url: String?): ImageRequest? {
    val platformContext = LocalPlatformContext.current
    return remember(platformContext, url) {
        url?.takeIf { it.isNotBlank() }?.let {
            ImageRequest.Builder(platformContext)
                .data(it)
                .size(96)
                .networkCachePolicy(CachePolicy.READ_ONLY)
                .crossfade(200)
                .build()
        }
    }
}
