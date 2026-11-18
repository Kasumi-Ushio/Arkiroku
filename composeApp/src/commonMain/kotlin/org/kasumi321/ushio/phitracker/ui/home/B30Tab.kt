@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.b30.B30TagAnalysisContent
import org.kasumi321.ushio.phitracker.ui.components.AppExpandableSection
import org.kasumi321.ushio.phitracker.ui.components.rememberAppExpansionArrowRotation
import org.kasumi321.ushio.phitracker.ui.utils.ADAPTIVE_GRID_MIN_COLUMN_WIDTH
import org.kasumi321.ushio.phitracker.ui.utils.adaptiveGridColumnCount
import org.kasumi321.ushio.phitracker.ui.utils.asString
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.b30_action_collapse
import phitracker.composeapp.generated.resources.b30_action_expand
import phitracker.composeapp.generated.resources.b30_action_retry
import phitracker.composeapp.generated.resources.b30_empty_message
import phitracker.composeapp.generated.resources.b30_section_best27
import phitracker.composeapp.generated.resources.b30_section_overflow
import phitracker.composeapp.generated.resources.b30_section_phi_best
import phitracker.composeapp.generated.resources.b30_tag_analysis_title

@Composable
fun B30Tab(
    state: B30UiState,
    nickname: String,
    challengeModeRank: Int,
    getIllustrationUrl: (String) -> String?,
    onSongClick: (String, Difficulty?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    useGrid: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState(),
    onRetryTagAnalysis: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val b30 = state.b30
    val showB30Overflow = state.showB30Overflow
    val overflowCount = state.overflowCount
    val phi3 = b30.filter { it.isPhi }
    val b36 = b30.filter { !it.isPhi }
    val b27 = b36.take(27)
    val overflow = if (showB30Overflow) b36.drop(27).take(overflowCount) else emptyList()
    val tagAnalysis = state.tagAnalysis
    val showTagSection = tagAnalysis.isLoading || tagAnalysis.error != null || tagAnalysis.analysis != null

    // The RKS summary lives in the floating B30Header; the list scrolls behind
    // it and the glass bottom bar, padded so the first and last cards stay clear
    if (b30.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.b30_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val listContentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 8.dp
        )
        if (useGrid) {
            val density = LocalDensity.current
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val columnCount = with(density) {
                    adaptiveGridColumnCount(
                        availableWidthPx = (constraints.maxWidth - 32.dp.roundToPx()).coerceAtLeast(0),
                        minColumnWidthPx = ADAPTIVE_GRID_MIN_COLUMN_WIDTH.roundToPx(),
                        spacingPx = ListItemDefaults.SegmentedGap.roundToPx()
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(ADAPTIVE_GRID_MIN_COLUMN_WIDTH),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listContentPadding,
                    horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                if (showTagSection) {
                    item(contentType = "tag_analysis", span = { GridItemSpan(maxLineSpan) }) {
                        CollapsibleTagAnalysis(
                            state = tagAnalysis,
                            onRetry = onRetryTagAnalysis
                        )
                    }
                }

                if (phi3.isNotEmpty()) {
                    item(contentType = "header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(Res.string.b30_section_phi_best),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                        )
                    }
                    itemsIndexed(
                        phi3,
                        key = { _, r -> "phi_${r.songId}_${r.difficulty}" },
                        contentType = { _, _ -> "score_card" }
                    ) { index, record ->
                        ScoreCard(
                            rank = index + 1,
                            record = record,
                            illustrationUrl = getIllustrationUrl(record.songId),
                            onSongClick = onSongClick,
                            shape = ListItemDefaults.segmentedShapes(
                                index = index / columnCount,
                                count = (phi3.size + columnCount - 1) / columnCount
                            ).shape
                        )
                    }
                }

                item(contentType = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(Res.string.b30_section_best27),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
                    )
                }
                itemsIndexed(
                    b27,
                    key = { _, r -> "b27_${r.songId}_${r.difficulty}" },
                    contentType = { _, _ -> "score_card" }
                ) { index, record ->
                    ScoreCard(
                        rank = index + 1,
                        record = record,
                        illustrationUrl = getIllustrationUrl(record.songId),
                        onSongClick = onSongClick,
                        shape = ListItemDefaults.segmentedShapes(
                            index = index / columnCount,
                            count = (b27.size + columnCount - 1) / columnCount
                        ).shape
                    )
                }

                if (overflow.isNotEmpty()) {
                    item(contentType = "header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(Res.string.b30_section_overflow),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
                        )
                    }
                    itemsIndexed(
                        overflow,
                        key = { _, r -> "overflow_${r.songId}_${r.difficulty}" },
                        contentType = { _, _ -> "score_card" }
                    ) { index, record ->
                        ScoreCard(
                            rank = index + 1,
                            record = record,
                            illustrationUrl = getIllustrationUrl(record.songId),
                            onSongClick = onSongClick,
                            shape = ListItemDefaults.segmentedShapes(
                                index = index / columnCount,
                                count = (overflow.size + columnCount - 1) / columnCount
                            ).shape
                        )
                    }
                }
            }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = listContentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
            if (showTagSection) {
                item(contentType = "tag_analysis") {
                    CollapsibleTagAnalysis(
                        state = tagAnalysis,
                        onRetry = onRetryTagAnalysis
                    )
                }
            }

            if (phi3.isNotEmpty()) {
                item(contentType = "header") {
                    Text(
                        text = stringResource(Res.string.b30_section_phi_best),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                }
                itemsIndexed(
                    phi3,
                    key = { _, r -> "phi_${r.songId}_${r.difficulty}" },
                    contentType = { _, _ -> "score_card" }
                ) { index, record ->
                    ScoreCard(
                        rank = index + 1,
                        record = record,
                        illustrationUrl = getIllustrationUrl(record.songId),
                        onSongClick = onSongClick,
                        shape = ListItemDefaults.segmentedShapes(index = index, count = phi3.size).shape
                    )
                }
            }

            item(contentType = "header") {
                Text(
                    text = stringResource(Res.string.b30_section_best27),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
                )
            }
            itemsIndexed(
                b27,
                key = { _, r -> "b27_${r.songId}_${r.difficulty}" },
                contentType = { _, _ -> "score_card" }
            ) { index, record ->
                ScoreCard(
                    rank = index + 1,
                    record = record,
                    illustrationUrl = getIllustrationUrl(record.songId),
                    onSongClick = onSongClick,
                    shape = ListItemDefaults.segmentedShapes(index = index, count = b27.size).shape
                )
            }

            if (overflow.isNotEmpty()) {
                item(contentType = "header") {
                    Text(
                        text = stringResource(Res.string.b30_section_overflow),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
                    )
                }
                itemsIndexed(
                    overflow,
                    key = { _, r -> "overflow_${r.songId}_${r.difficulty}" },
                    contentType = { _, _ -> "score_card" }
                ) { index, record ->
                    ScoreCard(
                        rank = index + 1,
                        record = record,
                        illustrationUrl = getIllustrationUrl(record.songId),
                        onSongClick = onSongClick,
                        shape = ListItemDefaults.segmentedShapes(index = index, count = overflow.size).shape
                    )
                }
            }
            }
        }
    }
}

/**
 * Collapsible B30 tag-analysis block. Its section title shares the list's
 * content start line, while the low-emphasis card contains only the analysis.
 * Loading stays collapsed; the first arrived analysis auto-expands once, after
 * which the manual user toggle wins.
 */
@Composable
private fun CollapsibleTagAnalysis(
    state: B30TagAnalysisState,
    onRetry: () -> Unit
) {
    var userExpanded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val autoExpanded = !state.isLoading && state.analysis != null
    val expanded = userExpanded ?: autoExpanded
    val arrowRotation by rememberAppExpansionArrowRotation(expanded)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { userExpanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.b30_tag_analysis_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) Res.string.b30_action_collapse
                    else Res.string.b30_action_expand
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(arrowRotation)
            )
        }

        AppExpandableSection(expanded = expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.largeIncreased
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    when {
                        state.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.error!!.asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onRetry) { Text(stringResource(Res.string.b30_action_retry)) }
                        }
                        state.analysis != null -> B30TagAnalysisContent(analysis = state.analysis)
                    }
                }
            }
        }
    }
}
