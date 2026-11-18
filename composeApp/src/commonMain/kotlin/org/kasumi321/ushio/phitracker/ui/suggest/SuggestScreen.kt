package org.kasumi321.ushio.phitracker.ui.suggest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import org.kasumi321.ushio.phitracker.ui.components.AppLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestTargetMode
import org.kasumi321.ushio.phitracker.ui.components.AppExpandableSection
import org.kasumi321.ushio.phitracker.ui.components.rememberAppExpansionArrowRotation
import org.kasumi321.ushio.phitracker.ui.glass.GlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.rememberGlassHazeStyle
import org.kasumi321.ushio.phitracker.ui.home.ScoreCardContent
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.kasumi321.ushio.phitracker.ui.home.formatFour
import org.kasumi321.ushio.phitracker.ui.home.formatTwo
import org.kasumi321.ushio.phitracker.ui.utils.asString
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.action_back
import phitracker.composeapp.generated.resources.suggest_chart_rks_label
import phitracker.composeapp.generated.resources.suggest_collapse
import phitracker.composeapp.generated.resources.suggest_empty
import phitracker.composeapp.generated.resources.suggest_expand
import phitracker.composeapp.generated.resources.suggest_hint_cards
import phitracker.composeapp.generated.resources.suggest_hint_input
import phitracker.composeapp.generated.resources.suggest_hint_mode
import phitracker.composeapp.generated.resources.suggest_mode_player_rks
import phitracker.composeapp.generated.resources.suggest_mode_single_chart_rks
import phitracker.composeapp.generated.resources.suggest_need_save_sync
import phitracker.composeapp.generated.resources.suggest_no_b30_impact
import phitracker.composeapp.generated.resources.suggest_no_data
import phitracker.composeapp.generated.resources.suggest_rks_delta_format
import phitracker.composeapp.generated.resources.suggest_target_accuracy
import phitracker.composeapp.generated.resources.suggest_target_label
import phitracker.composeapp.generated.resources.suggest_target_placeholder
import phitracker.composeapp.generated.resources.suggest_target_range_hint
import phitracker.composeapp.generated.resources.suggest_title
import phitracker.composeapp.generated.resources.suggest_total_rks_change
import phitracker.composeapp.generated.resources.suggest_view_chart_detail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestScreen(
        viewModel: SuggestViewModel,
        getIllustrationUrl: (String) -> String?,
        onNavigateToSongDetail: (String, Difficulty?) -> Unit,
        onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    // The description block collapses once the list leaves the top and only
    // re-expands when the list is scrolled all the way back up.
    val descriptionVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    // Page-level glass header (own HazeState, like the song detail page): the
    // suggestion list draws full-bleed as the haze source and scrolls under the
    // floating top bar; blur on/off and strength follow the settings entries
    // through LocalGlassSettings.
    val hazeState = rememberHazeState()
    val glassStyle = rememberGlassHazeStyle()

    Scaffold(
            topBar = {
                // Uniform full-strength blur: this bar carries functional
                // controls (chips, input), and a progressive gradient reads
                // as uneven frosting when list cards sit under its bottom edge.
                GlassTopBar(hazeState = hazeState, style = glassStyle, progressiveEndIntensity = 1f) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TopAppBar(
                                title = { Text(stringResource(Res.string.suggest_title)) },
                                navigationIcon = {
                                    IconButton(onClick = onNavigateBack) {
                                        Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(Res.string.action_back)
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            AppExpandableSection(expanded = descriptionVisible) {
                                Column {
                                    Text(
                                            text = stringResource(Res.string.suggest_hint_input),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                            text = stringResource(Res.string.suggest_hint_mode),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                            text = stringResource(Res.string.suggest_hint_cards),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                        selected = state.targetMode == SuggestTargetMode.PlayerDisplayRks,
                                        onClick = { viewModel.setTargetMode(SuggestTargetMode.PlayerDisplayRks) },
                                        label = { Text(stringResource(Res.string.suggest_mode_player_rks)) }
                                )
                                FilterChip(
                                        selected = state.targetMode == SuggestTargetMode.SingleChartRks,
                                        onClick = { viewModel.setTargetMode(SuggestTargetMode.SingleChartRks) },
                                        label = { Text(stringResource(Res.string.suggest_mode_single_chart_rks)) }
                                )
                            }

                            OutlinedTextField(
                                    value = state.targetInput,
                                    onValueChange = { viewModel.setTargetInput(it) },
                                    label = { Text(stringResource(Res.string.suggest_target_label)) },
                                    placeholder = { Text(stringResource(Res.string.suggest_target_placeholder)) },
                                    supportingText = { Text(state.targetError?.asString() ?: stringResource(Res.string.suggest_target_range_hint, state.maxTargetRks.formatTwo())) },
                                    isError = state.targetError != null,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                ) {
                    AppLoadingIndicator()
                }
            }
            !state.hasSaveData -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    SuggestEmptyHint(stringResource(Res.string.suggest_need_save_sync))
                }
            }
            state.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    SuggestEmptyHint(state.targetError?.asString() ?: stringResource(Res.string.suggest_empty))
                }
            }
            else -> {
                LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                        contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = innerPadding.calculateTopPadding() + 12.dp,
                                bottom = innerPadding.calculateBottomPadding() + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                            state.items,
                            key = { _, item -> "${item.songId}:${item.difficulty.name}" }
                    ) { index, item ->
                        SuggestScoreCard(
                                rank = index + 1,
                                item = item,
                                illustrationUrl = getIllustrationUrl(item.songId),
                                onNavigateToSongDetail = onNavigateToSongDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestEmptyHint(message: String) {
    Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
    ) {
        Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuggestScoreCard(
        rank: Int,
        item: SuggestItem,
        illustrationUrl: String?,
        onNavigateToSongDetail: (String, Difficulty?) -> Unit
) {
    // Expanded by default: the suggestion details (target ACC, RKS delta) are
    // the primary information on this page, so they should be visible at a
    // glance; the divider arrow still allows collapsing individual cards.
    var expanded by rememberSaveable { mutableStateOf(true) }

    val record =
            remember(item) {
                BestRecord(
                        songId = item.songId,
                        songName = item.songName,
                        difficulty = item.difficulty,
                        score = item.currentScore ?: 0,
                        accuracy = item.currentAcc ?: 0f,
                        isFullCombo = item.isFullCombo,
                        chartConstant = item.chartConstant,
                        rks = item.currentRks
                )
            }
    val noDataText = stringResource(Res.string.suggest_no_data)
    val currentAccText = remember(item.currentAcc, noDataText) { item.currentAcc?.let { "${it.formatTwo()}%" } ?: noDataText }
    val targetAccText = remember(item.targetAcc) { "${item.targetAcc.formatTwo()}%" }
    val currentRksText = remember(item.currentRks) { item.currentRks.formatFour() }
    val potentialRksText = remember(item.potentialRks) { item.potentialRks.formatFour() }
    val hasImpact = item.deltaRks > 0.00005f

    ScoreCardContent(
            record = record,
            rank = rank,
            rankLabel = "#$rank",
            illustrationUri = illustrationUrl,
            contentHorizontalPadding = 12.dp,
            contentVerticalPadding = 12.dp,
            compactText = false,
            thumbnailScale = 1f,
            marqueeSongTitle = true,
            onClick = { _, _ -> expanded = !expanded },
            footer = {
                val arrowRotation by rememberAppExpansionArrowRotation(expanded)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) stringResource(Res.string.suggest_collapse) else stringResource(Res.string.suggest_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp).rotate(arrowRotation)
                    )
                }
                AppExpandableSection(expanded = expanded) {
                    Column(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    ) {
                        SuggestDetailRow(label = stringResource(Res.string.suggest_target_accuracy), value = "$currentAccText → $targetAccText")
                        SuggestDetailRow(label = stringResource(Res.string.suggest_chart_rks_label), value = "$currentRksText → $potentialRksText")

                        Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text = stringResource(Res.string.suggest_total_rks_change),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasImpact) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier =
                                                Modifier.clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                            text =
                                                    stringResource(
                                                            Res.string.suggest_rks_delta_format,
                                                            item.newDisplayRks.formatFour(),
                                                            item.deltaRks.formatFour()
                                                    ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                        text = stringResource(Res.string.suggest_no_b30_impact),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                    onClick = {
                                        onNavigateToSongDetail(item.songId, item.difficulty)
                                    }
                            ) { Text(stringResource(Res.string.suggest_view_chart_detail)) }
                        }
                    }
                }
            }
    )
}

@Composable
private fun SuggestDetailRow(label: String, value: String) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
        )
    }
}
