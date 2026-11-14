package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.ui.b30.setImageRequestAllowHardware
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.profile_avatar
import phitracker.composeapp.generated.resources.profile_set_avatar

/** Shared avatar rendering for the app hero and the fixed-size B30 export header. */
@Composable
internal fun ProfileAvatar(
    avatarUri: String?,
    avatarSize: Dp,
    onAvatarClick: (() -> Unit)?,
    allowHardwareImages: Boolean = true,
    avatarRequestSizePx: Int? = null,
    avatarCrossfade: Boolean = true,
    onAvatarSettled: ((Throwable?) -> Unit)? = null
) {
    val platformContext = LocalPlatformContext.current
    Box(
        modifier = Modifier
            .size(avatarSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (onAvatarClick != null) Modifier.clickable { onAvatarClick() }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUri != null) {
            val imageRequest = remember(platformContext, avatarUri, avatarRequestSizePx, avatarCrossfade) {
                ImageRequest.Builder(platformContext).apply {
                    data(avatarUri)
                    avatarRequestSizePx?.let { size(it) }
                    crossfade(avatarCrossfade)
                    setImageRequestAllowHardware(allowHardwareImages)
                }.build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = stringResource(Res.string.profile_avatar),
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                onState = onAvatarSettled?.let { settled ->
                    { state ->
                        when (state) {
                            is AsyncImagePainter.State.Success -> settled(null)
                            is AsyncImagePainter.State.Error -> settled(state.result.throwable)
                            else -> Unit
                        }
                    }
                }
            )
        } else {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.profile_set_avatar),
                modifier = Modifier.size(avatarSize * 0.4f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
