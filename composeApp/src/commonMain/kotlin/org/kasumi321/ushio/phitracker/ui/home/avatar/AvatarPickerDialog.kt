package org.kasumi321.ushio.phitracker.ui.home.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.domain.model.GameAvatar
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import org.kasumi321.ushio.phitracker.ui.utils.asString
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.avatar_account_required
import phitracker.composeapp.generated.resources.avatar_catalog_empty
import phitracker.composeapp.generated.resources.avatar_custom_image
import phitracker.composeapp.generated.resources.avatar_picker_title
import phitracker.composeapp.generated.resources.avatar_random
import phitracker.composeapp.generated.resources.avatar_selection_in_progress
import phitracker.composeapp.generated.resources.tools_close

@Composable
fun AvatarPickerDialog(
    avatars: List<GameAvatar>,
    accountAvailable: Boolean,
    selectionInProgress: Boolean,
    selectionError: UiText?,
    onDismiss: () -> Unit,
    onCustomAvatar: () -> Unit,
    onSelect: (GameAvatar) -> Unit,
    previewUri: suspend (GameAvatar) -> String
) {
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
        title = { Text(stringResource(Res.string.avatar_picker_title)) },
        text = {
            // Match the B30 export settings dialog: the content scrolls as a
            // fallback on short screens while the avatar grid scrolls in place.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!accountAvailable) {
                    Text(
                        text = stringResource(Res.string.avatar_account_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectionInProgress) {
                    Text(
                        text = stringResource(Res.string.avatar_selection_in_progress),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                selectionError?.let { error ->
                    Text(
                        text = error.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "action_random_avatar") {
                        AvatarActionGridItem(
                            icon = Icons.Filled.Shuffle,
                            label = stringResource(Res.string.avatar_random),
                            enabled = accountAvailable && !selectionInProgress && avatars.isNotEmpty(),
                            onClick = { avatars.randomOrNull()?.let(onSelect) }
                        )
                    }
                    item(key = "action_custom_avatar") {
                        AvatarActionGridItem(
                            icon = Icons.Filled.PhotoLibrary,
                            label = stringResource(Res.string.avatar_custom_image),
                            enabled = accountAvailable && !selectionInProgress,
                            onClick = onCustomAvatar
                        )
                    }
                    if (avatars.isEmpty()) {
                        item(key = "avatar_catalog_empty", span = { GridItemSpan(3) }) {
                            Text(
                                text = stringResource(Res.string.avatar_catalog_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    }
                    items(
                        items = avatars,
                        key = GameAvatar::id,
                        contentType = { "game_avatar" }
                    ) { avatar ->
                        AvatarGridItem(
                            avatar = avatar,
                            enabled = accountAvailable && !selectionInProgress,
                            onSelect = { onSelect(avatar) },
                            previewUri = previewUri
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.tools_close))
            }
        }
    )
}

@Composable
private fun AvatarActionGridItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.6f)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun AvatarGridItem(
    avatar: GameAvatar,
    enabled: Boolean,
    onSelect: () -> Unit,
    previewUri: suspend (GameAvatar) -> String
) {
    val preview by produceState<AvatarPreview>(
        initialValue = AvatarPreview.Loading,
        key1 = avatar,
        key2 = previewUri
    ) {
        value = try {
            AvatarPreview.Ready(previewUri(avatar))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            AvatarPreview.Failed
        }
    }
    val uri = (preview as? AvatarPreview.Ready)?.uri
    var imageFailed by remember(uri) { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = enabled, onClick = onSelect)
            .alpha(if (enabled) 1f else 0.6f)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            when {
                preview == AvatarPreview.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                uri == null || imageFailed -> Text(
                    text = avatar.id,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
                else -> AsyncImage(
                    model = uri,
                    contentDescription = avatar.id,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onState = { imageState ->
                        imageFailed = imageState is AsyncImagePainter.State.Error
                    }
                )
            }
        }
        Text(
            text = avatar.id,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

private sealed interface AvatarPreview {
    data object Loading : AvatarPreview
    data class Ready(val uri: String) : AvatarPreview
    data object Failed : AvatarPreview
}
