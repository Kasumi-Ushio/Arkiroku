package org.kasumi321.ushio.phitracker.ui.home.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.data.avatar.AvatarCatalogFailure
import org.kasumi321.ushio.phitracker.data.avatar.AvatarCatalogRunStatus
import org.kasumi321.ushio.phitracker.data.avatar.AvatarCatalogState
import org.kasumi321.ushio.phitracker.domain.model.GameAvatar
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import org.kasumi321.ushio.phitracker.ui.utils.asString
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.avatar_account_required
import phitracker.composeapp.generated.resources.avatar_catalog_checking
import phitracker.composeapp.generated.resources.avatar_catalog_current
import phitracker.composeapp.generated.resources.avatar_catalog_empty
import phitracker.composeapp.generated.resources.avatar_catalog_failed_downloads
import phitracker.composeapp.generated.resources.avatar_catalog_partial_success
import phitracker.composeapp.generated.resources.avatar_catalog_progress
import phitracker.composeapp.generated.resources.avatar_catalog_reconciling_images
import phitracker.composeapp.generated.resources.avatar_catalog_updating
import phitracker.composeapp.generated.resources.avatar_check_updates
import phitracker.composeapp.generated.resources.avatar_custom_image
import phitracker.composeapp.generated.resources.avatar_dismiss_result
import phitracker.composeapp.generated.resources.avatar_failure_busy
import phitracker.composeapp.generated.resources.avatar_failure_network
import phitracker.composeapp.generated.resources.avatar_failure_scene
import phitracker.composeapp.generated.resources.avatar_failure_storage
import phitracker.composeapp.generated.resources.avatar_failure_unknown
import phitracker.composeapp.generated.resources.avatar_picker_title
import phitracker.composeapp.generated.resources.avatar_selection_in_progress
import phitracker.composeapp.generated.resources.avatar_update_catalog
import phitracker.composeapp.generated.resources.avatar_use_bundled_fallback
import phitracker.composeapp.generated.resources.avatar_using_bundled_fallback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    avatars: List<GameAvatar>,
    catalogState: AvatarCatalogState,
    accountAvailable: Boolean,
    selectionInProgress: Boolean,
    selectionError: UiText?,
    onDismiss: () -> Unit,
    onCustomAvatar: () -> Unit,
    onSelect: (GameAvatar) -> Unit,
    onCheckForUpdates: () -> Unit,
    onUpdate: () -> Unit,
    onUseBundledFallback: () -> Unit,
    onDismissResult: () -> Unit,
    previewUri: suspend (GameAvatar) -> String
) {
    val catalogBusy = catalogState.status == AvatarCatalogRunStatus.Checking ||
        catalogState.status == AvatarCatalogRunStatus.UpdatingCatalog ||
        catalogState.status == AvatarCatalogRunStatus.ReconcilingImages

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.avatar_picker_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            CatalogStatus(
                state = catalogState,
                onUseBundledFallback = onUseBundledFallback,
                onDismissResult = onDismissResult,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            if (!accountAvailable) {
                Text(
                    text = stringResource(Res.string.avatar_account_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            if (selectionInProgress) {
                Text(
                    text = stringResource(Res.string.avatar_selection_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            selectionError?.let { error ->
                Text(
                    text = error.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCustomAvatar,
                    enabled = accountAvailable && !selectionInProgress,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(Res.string.avatar_custom_image),
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
                OutlinedButton(
                    onClick = onCheckForUpdates,
                    enabled = !catalogBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(Res.string.avatar_check_updates),
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (catalogState.updateAvailable) {
                OutlinedButton(
                    onClick = onUpdate,
                    enabled = !catalogBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(stringResource(Res.string.avatar_update_catalog))
                }
            }

            if (avatars.isEmpty()) {
                Text(
                    text = stringResource(Res.string.avatar_catalog_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = 16.dp,
                        bottom = 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
        }
    }
}

@Composable
private fun CatalogStatus(
    state: AvatarCatalogState,
    onUseBundledFallback: () -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when (state.status) {
        AvatarCatalogRunStatus.Checking -> stringResource(Res.string.avatar_catalog_checking)
        AvatarCatalogRunStatus.UpdatingCatalog -> stringResource(Res.string.avatar_catalog_updating)
        AvatarCatalogRunStatus.ReconcilingImages ->
            stringResource(Res.string.avatar_catalog_reconciling_images)
        AvatarCatalogRunStatus.PartiallySucceeded ->
            stringResource(Res.string.avatar_catalog_partial_success)
        AvatarCatalogRunStatus.Idle,
        AvatarCatalogRunStatus.Ready,
        AvatarCatalogRunStatus.Failed -> null
    }
    val hasContent = statusText != null || state.failedDownloads > 0 ||
        state.failure != null || state.usingBundledFallback ||
        state.status == AvatarCatalogRunStatus.PartiallySucceeded ||
        state.status == AvatarCatalogRunStatus.Failed
    if (!hasContent) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (state.usingBundledFallback) {
            StatusText(stringResource(Res.string.avatar_using_bundled_fallback))
        }
        statusText?.let {
            StatusText(it)
            if (state.status == AvatarCatalogRunStatus.Checking ||
                state.status == AvatarCatalogRunStatus.UpdatingCatalog ||
                state.status == AvatarCatalogRunStatus.ReconcilingImages
            ) {
                if (state.total > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (state.completed.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    StatusText(
                        stringResource(
                            Res.string.avatar_catalog_progress,
                            state.completed,
                            state.total
                        )
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.currentAvatar.isNotBlank()) {
                    StatusText(stringResource(Res.string.avatar_catalog_current, state.currentAvatar))
                }
            }
        }
        if (state.failedDownloads > 0) {
            StatusText(
                stringResource(
                    Res.string.avatar_catalog_failed_downloads,
                    state.failedDownloads
                )
            )
        }
        state.failure?.let { failure ->
            val failureResource = when (failure) {
                AvatarCatalogFailure.Network -> Res.string.avatar_failure_network
                AvatarCatalogFailure.Storage -> Res.string.avatar_failure_storage
                AvatarCatalogFailure.Scene -> Res.string.avatar_failure_scene
                AvatarCatalogFailure.Busy -> Res.string.avatar_failure_busy
                AvatarCatalogFailure.Unknown -> Res.string.avatar_failure_unknown
            }
            Text(
                text = stringResource(failureResource),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (state.status == AvatarCatalogRunStatus.Failed &&
            state.failure != AvatarCatalogFailure.Busy &&
            !state.usingBundledFallback
        ) {
            TextButton(onClick = onUseBundledFallback) {
                Text(stringResource(Res.string.avatar_use_bundled_fallback))
            }
        }
        if (state.status == AvatarCatalogRunStatus.PartiallySucceeded ||
            state.status == AvatarCatalogRunStatus.Failed
        ) {
            TextButton(onClick = onDismissResult) {
                Text(stringResource(Res.string.avatar_dismiss_result))
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
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
