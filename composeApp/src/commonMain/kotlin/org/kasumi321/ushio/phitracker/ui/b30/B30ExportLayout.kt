package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.ui.home.ProfileHeaderCard
import org.kasumi321.ushio.phitracker.ui.home.ScoreCardContent
import org.kasumi321.ushio.phitracker.ui.home.StatsTableCard
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.b30ex_data_analysis
import phitracker.composeapp.generated.resources.b30ex_tag_stats_unavailable

/**
 * Thumbnail down-scale applied to every export score card. Shared with the B30
 * preloader so it can warm the exact Coil request key the cards consume.
 */
internal const val B30_EXPORT_CARD_THUMBNAIL_SCALE = 0.9f

/**
 * Slot id under which the export profile avatar is tracked by
 * [B30ExportImageLoadTracker].
 */
internal const val B30_EXPORT_AVATAR_SLOT = "avatar"

@Composable
internal fun B30ExportLayout(
    data: B30ExportData,
    allowHardwareImages: Boolean = true,
    imageLoadTracker: B30ExportImageLoadTracker? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        // Pre-blurred bitmap supplied by the platform renderer.
        // Android provides a StackBlur bitmap (radius from
        // B30ExportData.backgroundBlurRadius) via B30ImageGenerator.android;
        // iOS also preloads and supplies a blurred bitmap before capturing this
        // shared layout. Production code MUST supply backgroundBitmap — when absent, no image
        // background is rendered (only the white overlay below contributes).
        if (data.backgroundBitmap != null) {
            Image(
                bitmap = data.backgroundBitmap,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.2f
                        scaleY = 1.2f
                    },
                contentScale = ContentScale.Crop,
                alpha = 1f
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background((if (data.darkTheme) Color.Black else Color.White).copy(alpha = 0.65f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val headerHeight = B30ExportSpec.profileCardHeightDp.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProfileHeaderCard(
                    nickname = data.nickname,
                    displayRks = data.rks,
                    challengeModeRank = data.challengeLevel,
                    moneyString = data.moneyString,
                    avatarUri = data.avatarUri,
                    onAvatarClick = null,
                    contentHorizontalPadding = 9.dp,
                    contentVerticalPadding = 5.dp,
                    textVerticalSpacing = 2.dp,
                    avatarSize = B30ExportSpec.AVATAR_SIZE_DP.dp,
                    avatarTextSpacing = 18.dp,
                    centerContent = true,
                    modifier = Modifier
                        .width(B30ExportSpec.profileCardWidthDp.dp)
                        .height(headerHeight),
                    allowHardwareImages = allowHardwareImages,
                    avatarRequestSizePx = B30ExportSpec.avatarSizePx,
                    avatarCrossfade = false,
                    onAvatarSettled = imageLoadTracker?.let { tracker ->
                        { error ->
                            // A broken avatar must not fail the whole export;
                            // log it and let the capture proceed (placeholder).
                            if (error != null) {
                                AppLogger.w(
                                    "B30ExportLayout",
                                    "avatar load failed: ${error.message ?: error::class.simpleName}"
                                )
                            }
                            tracker.onIllustrationSettled(B30_EXPORT_AVATAR_SLOT, null)
                        }
                    }
                )
                StatsTableCard(
                    clearCounts = data.statsTable.clearCounts,
                    fcCount = data.statsTable.fcCount,
                    phiCount = data.statsTable.phiCount,
                    contentHorizontalPadding = 9.dp,
                    contentVerticalPadding = 5.dp,
                    rowSpacing = 7.dp,
                    modifier = Modifier
                        .width(B30ExportSpec.statsCardWidthDp.dp)
                        .height(headerHeight)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionTitle("Phi")
            Spacer(modifier = Modifier.height(6.dp))
            ExportCardGrid(
                cards = data.phiRecords,
                rankLabelProvider = { index -> "P${index + 1}" },
                cardWidth = B30ExportSpec.cardWidthDp.dp,
                cardHeight = B30ExportSpec.cardHeightDp.dp,
                horizontalGap = B30ExportSpec.cardHorizontalGapDp.dp,
                verticalGap = B30ExportSpec.cardVerticalGapDp.dp,
                allowHardwareImages = allowHardwareImages,
                sectionId = "phi",
                cardStyle = data.cardStyle,
                imageLoadTracker = imageLoadTracker
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Best 27")
            Spacer(modifier = Modifier.height(6.dp))
            ExportCardGrid(
                cards = data.bestRecords,
                rankLabelProvider = { index -> "#${index + 1}" },
                cardWidth = B30ExportSpec.cardWidthDp.dp,
                cardHeight = B30ExportSpec.cardHeightDp.dp,
                horizontalGap = B30ExportSpec.cardHorizontalGapDp.dp,
                verticalGap = B30ExportSpec.cardVerticalGapDp.dp,
                allowHardwareImages = allowHardwareImages,
                sectionId = "best",
                cardStyle = data.cardStyle,
                imageLoadTracker = imageLoadTracker
            )

            if (data.overflowRecords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle("OVERFLOW")
                Spacer(modifier = Modifier.height(6.dp))
                ExportCardGrid(
                    cards = data.overflowRecords,
                    rankLabelProvider = { index -> "#${index + 1}" },
                    cardWidth = B30ExportSpec.cardWidthDp.dp,
                    cardHeight = B30ExportSpec.cardHeightDp.dp,
                    horizontalGap = B30ExportSpec.cardHorizontalGapDp.dp,
                    verticalGap = B30ExportSpec.cardVerticalGapDp.dp,
                    allowHardwareImages = allowHardwareImages,
                    sectionId = "overflow",
                    cardStyle = data.cardStyle,
                    imageLoadTracker = imageLoadTracker
                )
            }

            // phi-plugin's b19 analysis row: one fused tag-analysis panel
            // (radar and the strong/weak rankings under a single "谱面标签
            // 能力" header) beside the RKS histogram panel.
            if (data.tagAnalysis != null || data.histogram != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle(stringResource(Res.string.b30ex_data_analysis))
                Spacer(modifier = Modifier.height(B30ExportSpec.tagSectionGapDp.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(B30ExportSpec.cardHorizontalGapDp.dp)
                ) {
                    // The tag analysis spans the width the two former tag
                    // cards occupied, fused into a single panel.
                    TagSectionCard(
                        modifier = Modifier
                            .width(
                                (B30ExportSpec.cardWidthDp * 2 + B30ExportSpec.cardHorizontalGapDp).dp
                            )
                            .height(B30ExportSpec.tagSectionHeightDp.dp)
                    ) {
                        val analysis = data.tagAnalysis
                        if (analysis != null) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                B30TagAnalysisPanelHeader(totalVotes = analysis.totalVotes)
                                Spacer(modifier = Modifier.height(4.dp))
                                ChartTagInsufficientScrim(
                                    insufficient = analysis.insufficient,
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TagRadarChart(
                                            categories = analysis.categories,
                                            averageRks = analysis.averageRks,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                        B30TagStrongWeakColumns(
                                            analysis = analysis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        } else {
                            // The tag analysis was unavailable when the image
                            // was generated (fetch failed or timed out): keep
                            // the panel with an explicit notice.
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.b30ex_tag_stats_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    data.histogram?.let { histogram ->
                        TagSectionCard(
                            modifier = Modifier
                                .width(B30ExportSpec.cardWidthDp.dp)
                                .height(B30ExportSpec.tagSectionHeightDp.dp)
                        ) {
                            B30RksHistogramChart(
                                histogram = histogram,
                                // 132 - 20: the legend row takes ~20dp, so the
                                // chart shrinks to keep the card height fixed.
                                chartHeight = 112.dp,
                                modifier = Modifier.fillMaxSize().padding(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Generated by Arkiroku",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = data.dateText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExportCardGrid(
    cards: List<ExportCardData>,
    rankLabelProvider: (Int) -> String,
    cardWidth: Dp,
    cardHeight: Dp,
    horizontalGap: Dp,
    verticalGap: Dp,
    allowHardwareImages: Boolean = true,
    sectionId: String,
    cardStyle: B30ExportCardStyle,
    imageLoadTracker: B30ExportImageLoadTracker?
) {
    val rows = cards.chunked(3)
    rows.forEachIndexed { rowIndex, row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(horizontalGap)
        ) {
            row.forEachIndexed { colIndex, card ->
                val cardModifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                val settledCallback = imageLoadTracker?.let { tracker ->
                    { slotId: String, error: Throwable? -> tracker.onIllustrationSettled(slotId, error) }
                }
                val slotId = "$sectionId:${rowIndex * 3 + colIndex}"
                when (cardStyle) {
                    B30ExportCardStyle.Classic -> ScoreCardContent(
                        record = card.record,
                        rank = rowIndex * 3 + colIndex + 1,
                        rankLabel = rankLabelProvider(rowIndex * 3 + colIndex),
                        illustrationUri = card.illustrationUri,
                        contentHorizontalPadding = 9.dp,
                        contentVerticalPadding = 5.dp,
                        compactText = true,
                        thumbnailScale = B30_EXPORT_CARD_THUMBNAIL_SCALE,
                        onClick = null,
                        modifier = cardModifier,
                        allowHardwareImages = allowHardwareImages,
                        imageSlotId = slotId,
                        onIllustrationSettled = settledCallback
                    )
                    B30ExportCardStyle.Poster -> ExportPosterCard(
                        record = card.record,
                        rank = rowIndex * 3 + colIndex + 1,
                        rankLabel = rankLabelProvider(rowIndex * 3 + colIndex),
                        illustrationUri = card.illustrationUri,
                        modifier = cardModifier,
                        allowHardwareImages = allowHardwareImages,
                        imageSlotId = slotId,
                        onIllustrationSettled = settledCallback
                    )
                }
            }
        }
        if (rowIndex < rows.lastIndex) {
            Spacer(modifier = Modifier.height(verticalGap))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Card shell for the chart-tag section, matching the header card styling. */
@Composable
private fun TagSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        content()
    }
}
