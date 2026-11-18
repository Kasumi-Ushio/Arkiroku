package org.kasumi321.ushio.phitracker.ui.song

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import org.kasumi321.ushio.phitracker.ui.components.AppCircularProgressIndicator
import org.kasumi321.ushio.phitracker.ui.components.AppLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.data.platform.saveArtworkToPictures
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.ill_preview_cd_rotate
import phitracker.composeapp.generated.resources.ill_preview_save_failed
import phitracker.composeapp.generated.resources.ill_preview_save_failed_with_reason
import phitracker.composeapp.generated.resources.ill_preview_saved_to_album
import phitracker.composeapp.generated.resources.ill_preview_unknown_error
import kotlin.math.roundToInt

/**
 * Fullscreen illustration preview as its own navigation destination (see
 * [IllustrationPreviewRoute][org.kasumi321.ushio.phitracker.ui.navigation.IllustrationPreviewRoute]).
 * A dedicated page replaces the old in-composition overlay, so entering and
 * leaving the preview rides the shared navigation transitions instead of
 * popping in abruptly, and the system back gesture simply pops the back stack.
 *
 * The action buttons sit on a translucent dark scrim rather than a glass
 * capsule: the capsule samples the artwork through Haze, which washes the
 * white icons out on light artwork regardless of the app theme. Gestures
 * mirror the B30 image page: pinch zoom, drag pan and double-tap zoom.
 * Rotation only advances in 90° steps via the corner button — free two-finger
 * rotation was removed because the pan clamp below is only exact for quarter
 * turns, and arbitrary angles mis-clamped the pan (stuck drags, unreachable
 * edges).
 */
@Composable
fun IllustrationPreviewScreen(
    illustrationUrl: String?,
    songId: String,
    difficulty: Difficulty? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        var scale by rememberSaveable { mutableFloatStateOf(1f) }
        var rotation by rememberSaveable { mutableFloatStateOf(0f) }
        var panOffset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        val coroutineScope = rememberCoroutineScope()
        var isDownloading by remember { mutableStateOf(false) }

        val saveFailedMessage = stringResource(Res.string.ill_preview_save_failed)
        val savedToAlbumMessage = stringResource(Res.string.ill_preview_saved_to_album)
        val unknownErrorMessage = stringResource(Res.string.ill_preview_unknown_error)

        val platformContext = LocalPlatformContext.current
        val previewRequest = remember(platformContext, illustrationUrl) {
            illustrationUrl?.takeIf { it.isNotBlank() }?.let { url ->
                ImageRequest.Builder(platformContext)
                    .data(url)
                    .diskCacheKey(url)
                    .crossfade(200)
                    .build()
            }
        }
        val painter = rememberAsyncImagePainter(model = previewRequest)
        val painterState by painter.state.collectAsState()

        // Clamps the pan so the fitted image edge never travels inside the
        // viewport. Fit applies before rotation, so scale the raw dimensions
        // first, then swap the drawn extents when a quarter turn is active.
        fun clampPan(scaleValue: Float, rotationValue: Float, pan: Offset): Offset {
            val imageSize = painter.intrinsicSize
            val containerW = containerSize.width.toFloat()
            val containerH = containerSize.height.toFloat()
            if (imageSize.width <= 0f || imageSize.height <= 0f || containerW <= 0f || containerH <= 0f) {
                return pan
            }
            val fitScale = minOf(containerW / imageSize.width, containerH / imageSize.height)
            val drawnW = imageSize.width * fitScale
            val drawnH = imageSize.height * fitScale
            val quarterTurns = ((rotationValue.roundToInt() % 360) + 360) % 360 / 90
            val extentW = if (quarterTurns % 2 == 1) drawnH else drawnW
            val extentH = if (quarterTurns % 2 == 1) drawnW else drawnH
            val maxX = (extentW * scaleValue - containerW).coerceAtLeast(0f) / 2f
            val maxY = (extentH * scaleValue - containerH).coerceAtLeast(0f) / 2f
            return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
        }

        Image(
            painter = painter,
            contentDescription = "Full Illustration",
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    // Double tap toggles between fit and 2x, matching the B30
                    // image page.
                    detectTapGestures(
                        onDoubleTap = {
                            val newScale = if (scale > 1.5f) 1f else 2f
                            scale = newScale
                            panOffset = if (newScale == 1f) Offset.Zero else clampPan(newScale, rotation, panOffset)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        panOffset = clampPan(scale, rotation, panOffset + pan)
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    rotationZ = rotation,
                    translationX = panOffset.x,
                    translationY = panOffset.y
                ),
            contentScale = ContentScale.Fit
        )

        // High-res artwork can take a moment on first load; surface the wait
        // instead of staring at a black screen.
        if (painterState is AsyncImagePainter.State.Loading) {
            AppLoadingIndicator(color = Color.White)
        }

        PreviewButtonCapsule(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            IconButton(
                onClick = {
                    val standardArtworkUrl = illustrationUrl.orEmpty()
                    if (standardArtworkUrl.isBlank()) {
                        showPlatformMessage(saveFailedMessage)
                        return@IconButton
                    }
                    isDownloading = true
                    coroutineScope.launch {
                        val difficultySuffix = difficulty?.let { "_${it.name}" } ?: ""
                        val fileName = "${songId.replace(".", "_")}${difficultySuffix}_hq.png"
                        val result = saveArtworkToPictures(standardArtworkUrl, fileName)
                        showPlatformMessage(
                            if (result.isSuccess) {
                                savedToAlbumMessage
                            } else {
                                getString(
                                    Res.string.ill_preview_save_failed_with_reason,
                                    result.exceptionOrNull()?.message ?: unknownErrorMessage
                                )
                            }
                        )
                        isDownloading = false
                    }
                },
                enabled = !isDownloading
            ) {
                if (isDownloading) {
                    AppCircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        trackColor = Color.Transparent
                    )
                } else {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = "Save",
                        tint = Color.White
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        PreviewButtonCapsule(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            IconButton(
                onClick = {
                    rotation = (rotation / 90f).roundToInt() * 90f + 90f
                    // A quarter turn swaps the drawn extents, so the current
                    // pan may fall outside the new bounds.
                    panOffset = clampPan(scale, rotation, panOffset)
                }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = stringResource(Res.string.ill_preview_cd_rotate),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun PreviewButtonCapsule(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
