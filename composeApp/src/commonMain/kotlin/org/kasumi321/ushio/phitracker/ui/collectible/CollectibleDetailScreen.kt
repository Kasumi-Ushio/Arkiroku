package org.kasumi321.ushio.phitracker.ui.collectible

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.domain.model.CollectibleCategory
import org.kasumi321.ushio.phitracker.domain.model.CollectiblePart
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.action_back
import phitracker.composeapp.generated.resources.collectible_detail_category_label
import phitracker.composeapp.generated.resources.collectible_detail_chapter_format
import phitracker.composeapp.generated.resources.collectible_detail_content_title
import phitracker.composeapp.generated.resources.collectible_detail_date_label
import phitracker.composeapp.generated.resources.collectible_detail_info_title
import phitracker.composeapp.generated.resources.collectible_detail_part_header
import phitracker.composeapp.generated.resources.collectible_detail_supervisor_label

/**
 * Collectible detail page (level 3): edge-to-edge poster-style cover art that
 * draws under the status bar. A theme-surface scrim is pinned to the status-bar
 * region instead of scrolling with the cover, and fades out once the cover has
 * fully passed the status-bar bottom edge; it keeps the system icons readable
 * while any of the cover remains behind them. The cover keeps only the chapter
 * line and the item name over its bottom scrim; category, date and supervisor
 * metadata moves into a body card, and each part's body text gets its own card
 * (same card idiom as the song detail page). The CSV content keeps literal
 * two-character `\n` sequences; they become real line breaks here. No Haze —
 * the page is plain surface, glass stays on the home navigation.
 */
@Composable
fun CollectibleDetailScreen(
    state: CollectibleDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = state.item ?: return
    val chapterLabel = state.chapter?.let { chapter ->
        if (chapter.displaySub.isNotBlank()) {
            stringResource(
                Res.string.collectible_detail_chapter_format,
                chapter.displaySub,
                chapter.displayName
            )
        } else {
            chapter.displayName
        }
    } ?: item.chapterFolderKey
    val sortedParts = item.parts.sortedBy { it.subIndex }
    val categories = sortedParts.map { it.category }.distinct()
    val dates = sortedParts.map { it.date }.filter { it.isNotBlank() }.distinct()
    val supervisors = sortedParts.map { it.supervisor }.filter { it.isNotBlank() }.distinct()
    val hasMetadata = categories.isNotEmpty() || dates.isNotEmpty() || supervisors.isNotEmpty()

    val scrollState = rememberScrollState()
    var coverHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val statusBarHeightPx = with(density) { statusBarHeight.roundToPx() }
    // The scrim belongs to the status-bar region: keep it while the cover's
    // bottom edge is still below the status-bar bottom edge (or before the
    // header has been measured), fade it out once the cover has fully passed.
    val scrimVisible by remember(statusBarHeightPx) {
        derivedStateOf {
            coverHeightPx == 0 || scrollState.value < coverHeightPx - statusBarHeightPx
        }
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (scrimVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "collectibleCoverScrimAlpha"
    )

    // Surface (not a bare Box) so LocalContentColor is onSurface — texts that
    // don't set an explicit color stay readable in dark mode.
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Edge-to-edge: the cover draws under the status bar; only
                    // the floating back button reserves the inset.
                    .verticalScroll(scrollState)
            ) {
                DetailArtHeader(
                    chapterLabel = chapterLabel,
                    title = item.displayName,
                    artUrl = state.coverUrl,
                    modifier = Modifier.onSizeChanged { coverHeightPx = it.height }
                )
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(16.dp))

                    if (hasMetadata) {
                        MetadataCard(
                            categories = categories,
                            dates = dates,
                            supervisors = supervisors
                        )
                    }

                    sortedParts.forEachIndexed { index, part ->
                        if (index > 0 || hasMetadata) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        PartCard(
                            part = part,
                            showHeader = sortedParts.size > 1,
                            ordinal = index + 1,
                            itemDisplayName = item.displayName
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Status-bar scrim: pinned to the top of the screen, not part of
            // the scrolling cover. A theme-surface fade keeps the globally
            // themed status icons readable in both modes without touching the
            // system appearance, and it fades away with the cover.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeight + 24.dp)
                    .alpha(scrimAlpha)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                0.55f to MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                1f to Color.Transparent
                            )
                        )
                    )
            )

            // Floating back button over the art. The cover now extends under
            // the status bar, so only this control reserves the inset to stay
            // clickable and clear of the status icons.
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Cover with only the chapter line and item name over the bottom scrim; the
 * remaining metadata lives in [MetadataCard] below. The status-bar scrim is
 * owned by the screen so it can stay pinned while this header scrolls.
 */
@Composable
private fun DetailArtHeader(
    chapterLabel: String,
    title: String,
    artUrl: String?,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    val imageRequest = remember(platformContext, artUrl) {
        artUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(platformContext)
                .data(url)
                .crossfade(200)
                .build()
        }
    }

    // The poster keeps at least a 16:10 image area.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minCoverHeight = maxWidth * 10f / 16f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minCoverHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
            // Bottom scrim: darkens the poster so the overlaid chapter/name
            // stay legible on bright covers in both themes.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.42f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.86f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Line 1: chapter info ("第一章 · 冰封世界"), omitted only when
                // the item has no chapter data at all.
                if (chapterLabel.isNotBlank()) {
                    Text(
                        text = chapterLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                // Line 2: the collectible name.
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Item metadata as a body card: primary-tinted section title followed by
 * label: value rows (same idiom as the song detail header rows). For
 * multi-part items the values are de-duplicated in part order so differing
 * dates or supervisors from each part stay visible.
 */
@Composable
private fun MetadataCard(
    categories: List<CollectibleCategory>,
    dates: List<String>,
    supervisors: List<String>
) {
    val categoryLabels = categories.map { collectibleCategoryLabel(it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(Res.string.collectible_detail_info_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (categoryLabels.isNotEmpty()) {
                InfoRow(
                    label = stringResource(Res.string.collectible_detail_category_label),
                    value = categoryLabels.joinToString(" / ")
                )
            }
            if (dates.isNotEmpty()) {
                InfoRow(
                    label = stringResource(Res.string.collectible_detail_date_label),
                    value = dates.joinToString(" / ")
                )
            }
            if (supervisors.isNotEmpty()) {
                InfoRow(
                    label = stringResource(Res.string.collectible_detail_supervisor_label),
                    value = supervisors.joinToString(" / ")
                )
            }
        }
    }
}

@Composable
private fun PartCard(
    part: CollectiblePart,
    showHeader: Boolean,
    ordinal: Int,
    itemDisplayName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Every card gets a primary-tinted title, matching the song detail
            // page's section cards. Multi-part: "Part N" plus the part name as
            // subtitle; single-part: the part name, or a generic "Content"
            // title when it is absent or just repeats the item name.
            val partNameDistinct = part.name.isNotBlank() && part.name != itemDisplayName
            Text(
                text = if (showHeader) {
                    stringResource(Res.string.collectible_detail_part_header, ordinal)
                } else if (partNameDistinct) {
                    part.name
                } else {
                    stringResource(Res.string.collectible_detail_content_title)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (showHeader && partNameDistinct) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = part.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (part.content.isNotBlank()) {
                SelectionContainer {
                    Text(
                        // The CSV carries literal \n two-character sequences;
                        // restore real line breaks for display.
                        text = part.content.replace("\\n", "\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (part.properties.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = part.properties,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Same idiom as the song detail header rows: the label stays unselectable so
 * copying never picks it up, the value is selectable.
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
