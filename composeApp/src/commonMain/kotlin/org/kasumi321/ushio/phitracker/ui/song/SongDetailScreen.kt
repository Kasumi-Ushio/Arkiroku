package org.kasumi321.ushio.phitracker.ui.song

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagCategoryDisplay
import org.kasumi321.ushio.phitracker.domain.model.ChartTagVoteCount
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.ui.components.ElasticPagerPrimaryIndicator
import org.kasumi321.ushio.phitracker.ui.components.ScoreRating
import org.kasumi321.ushio.phitracker.ui.components.ScoreRatingTag
import org.kasumi321.ushio.phitracker.ui.glass.GlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.rememberGlassHazeStyle
import org.kasumi321.ushio.phitracker.ui.utils.asString
import org.kasumi321.ushio.phitracker.ui.utils.chapterDisplayNameLocalized
import org.kasumi321.ushio.phitracker.ui.utils.expandCollapseTransition
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.song_detail_aliases_label
import phitracker.composeapp.generated.resources.song_detail_alias_contribute
import phitracker.composeapp.generated.resources.song_detail_alias_propose_alias_label
import phitracker.composeapp.generated.resources.song_detail_alias_propose_cancel
import phitracker.composeapp.generated.resources.song_detail_alias_propose_doc_button
import phitracker.composeapp.generated.resources.song_detail_alias_propose_doc_hint
import phitracker.composeapp.generated.resources.song_detail_alias_propose_note_label
import phitracker.composeapp.generated.resources.song_detail_alias_propose_review_notice
import phitracker.composeapp.generated.resources.song_detail_alias_propose_submit
import phitracker.composeapp.generated.resources.song_detail_alias_propose_success
import phitracker.composeapp.generated.resources.song_detail_alias_propose_title
import phitracker.composeapp.generated.resources.song_detail_avg_acc
import phitracker.composeapp.generated.resources.song_detail_cd_back
import phitracker.composeapp.generated.resources.song_detail_chapter
import phitracker.composeapp.generated.resources.song_detail_charter
import phitracker.composeapp.generated.resources.song_detail_chart_info
import phitracker.composeapp.generated.resources.song_detail_chart_ranking
import phitracker.composeapp.generated.resources.song_detail_chart_tags
import phitracker.composeapp.generated.resources.song_detail_collapse
import phitracker.composeapp.generated.resources.song_detail_community_stats
import phitracker.composeapp.generated.resources.song_detail_composer
import phitracker.composeapp.generated.resources.song_detail_duration
import phitracker.composeapp.generated.resources.song_detail_expand_all
import phitracker.composeapp.generated.resources.song_detail_illustrator
import phitracker.composeapp.generated.resources.song_detail_no_notes_data
import phitracker.composeapp.generated.resources.song_detail_no_sync_history
import phitracker.composeapp.generated.resources.song_detail_no_tags_yet
import phitracker.composeapp.generated.resources.song_detail_notes_distribution
import phitracker.composeapp.generated.resources.song_detail_record_title
import phitracker.composeapp.generated.resources.song_detail_rks_value
import phitracker.composeapp.generated.resources.song_detail_sync_history
import phitracker.composeapp.generated.resources.song_detail_title
import phitracker.composeapp.generated.resources.song_detail_unknown
import phitracker.composeapp.generated.resources.song_detail_vote_button
import phitracker.composeapp.generated.resources.song_detail_vote_instructions
import phitracker.composeapp.generated.resources.song_detail_vote_primary
import phitracker.composeapp.generated.resources.song_detail_vote_secondary
import phitracker.composeapp.generated.resources.song_detail_vote_submit
import phitracker.composeapp.generated.resources.song_detail_vote_submitting
import phitracker.composeapp.generated.resources.song_detail_vote_success
import phitracker.composeapp.generated.resources.song_detail_voted_button
import kotlin.math.roundToInt
import kotlin.time.Instant

private fun Float.formatFourDecimals(): String {
    val v = (this * 10000).roundToInt()
    return "${v / 10000}.${(kotlin.math.abs(v) % 10000).toString().padStart(4, '0')}"
}

private fun Int.formatScore(): String {
    return this.toString().reversed().chunked(3).joinToString(",").reversed()
}

private fun Long.formatSyncTime(): String {
    val dateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    val month = dateTime.month.ordinal + 1
    return "${month.toString().padStart(2, '0')}-${dateTime.day.toString().padStart(2, '0')} ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    songInfo: SongInfo,
    userRecords: List<BestRecord> = emptyList(),
    syncHistory: List<SongSyncHistoryEntry> = emptyList(),
    apiEnabled: Boolean = false,
    useApiData: Boolean = false,
    apiRequestKey: String = "",
    apiDetails: Map<Difficulty, SongApiDetailState> = emptyMap(),
    onLoadSongApiDetail: (Difficulty) -> Unit = {},
    chartTags: Map<Difficulty, ChartTagUiState> = emptyMap(),
    onLoadChartTags: (Difficulty) -> Unit = {},
    canVote: Boolean = false,
    onSubmitChartTagVote: (Difficulty, List<String>, List<String>) -> Unit = { _, _, _ -> },
    aliasProposal: AliasProposalUiState = AliasProposalUiState(),
    onSubmitAliasProposal: (String, String) -> Unit = { _, _ -> },
    onAliasProposalDialogOpen: () -> Unit = {},
    lowIllustrationUrls: Map<Difficulty, String?>,
    onIllustrationClick: (Difficulty) -> Unit,
    initialDifficulty: Difficulty? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableDifficulties = Difficulty.entries.filter { songInfo.difficulties.containsKey(it) }
    val defaultTabIndex = availableDifficulties.indexOfFirst { it == Difficulty.IN }.takeIf { it >= 0 } ?: 0
    val initialTabIndex = initialDifficulty?.let { availableDifficulties.indexOf(it) }?.takeIf { it >= 0 }
    val pagerState = rememberPagerState(initialPage = initialTabIndex ?: defaultTabIndex) { availableDifficulties.size }
    val selectedDifficulty = availableDifficulties.getOrNull(pagerState.currentPage) ?: Difficulty.IN
    val songApiDetail = apiDetails[selectedDifficulty] ?: SongApiDetailState()

    LaunchedEffect(apiEnabled, useApiData, apiRequestKey, selectedDifficulty) {
        if (apiEnabled && useApiData) {
            onLoadSongApiDetail(selectedDifficulty)
        }
    }

    LaunchedEffect(apiRequestKey, selectedDifficulty) {
        onLoadChartTags(selectedDifficulty)
    }

    // Page-level HazeState, independent from the home one. The detail content is
    // the haze source; the info header slides up behind the progressive glass
    // top bar as the difficulty page scrolls, so the blur always has real
    // moving content to sample instead of being a plain color swap.
    val detailHazeState = rememberHazeState()
    val detailGlassStyle = rememberGlassHazeStyle()
    var infoHeaderHeightPx by remember { mutableIntStateOf(0) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    val reducedMotion = rememberReducedMotionEnabled()

    // Collapsing header over nested scroll: scrolling forward collapses the
    // header before the page content moves (pre-scroll), scrolling back expands
    // it once the content reaches the top (post-scroll), and a topward fling
    // springs the header back open with its leftover velocity. Because the
    // header consumes deltas through the connection instead of resizing the
    // scroll range mid-fling, a single fling carries through both phases.
    val headerCollapseConnection = remember(reducedMotion) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || infoHeaderHeightPx <= 0) return Offset.Zero
                val newOffset = (headerOffsetPx + available.y)
                    .coerceIn(-infoHeaderHeightPx.toFloat(), 0f)
                val consumed = newOffset - headerOffsetPx
                headerOffsetPx = newOffset
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y <= 0f || infoHeaderHeightPx <= 0) return Offset.Zero
                val newOffset = (headerOffsetPx + available.y)
                    .coerceIn(-infoHeaderHeightPx.toFloat(), 0f)
                val used = newOffset - headerOffsetPx
                headerOffsetPx = newOffset
                return Offset(0f, used)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f && headerOffsetPx < 0f) {
                    if (reducedMotion) {
                        headerOffsetPx = 0f
                    } else {
                        animate(
                            initialValue = headerOffsetPx,
                            targetValue = 0f,
                            initialVelocity = available.y,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) { value, _ -> headerOffsetPx = value }
                    }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Scaffold(
        topBar = {
            GlassTopBar(hazeState = detailHazeState, style = detailGlassStyle) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.song_detail_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.song_detail_cd_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .hazeSource(state = detailHazeState)
                .nestedScroll(headerCollapseConnection)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            val contentBottomPadding = innerPadding.calculateBottomPadding()

            // Collapsing header: the layout shrinks while the content translates
            // up behind the glass bar; no clip so the sliding header stays
            // visible under the bar until fully collapsed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Non-consuming scrollable: nested scroll deltas only
                    // ever originate from scrollable children, so without
                    // this a header that fills the screen (e.g. fully
                    // expanded aliases) would swallow every drag. Deltas
                    // flow through headerCollapseConnection exactly like
                    // content scrolls, fling inertia included
                    .scrollable(
                        orientation = Orientation.Vertical,
                        state = rememberScrollableState { 0f }
                    )
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val collapsePx = (-headerOffsetPx)
                            .coerceIn(0f, infoHeaderHeightPx.toFloat())
                            .roundToInt()
                        layout(placeable.width, (placeable.height - collapsePx).coerceAtLeast(0)) {
                            placeable.placeRelative(0, -collapsePx)
                        }
                    }
            ) {
                SongInfoHeader(
                    songInfo = songInfo,
                    difficulty = selectedDifficulty,
                    thumbnailUrl = lowIllustrationUrls[selectedDifficulty],
                    onIllustrationClick = onIllustrationClick,
                    aliasProposal = aliasProposal,
                    onSubmitAliasProposal = onSubmitAliasProposal,
                    onAliasProposalDialogOpen = onAliasProposalDialogOpen,
                    modifier = Modifier.onSizeChanged { infoHeaderHeightPx = it.height }
                )
            }

            if (availableDifficulties.isNotEmpty()) {
                DifficultyTabRow(
                    pagerState = pagerState,
                    availableDifficulties = availableDifficulties,
                    difficulties = songInfo.difficulties
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = true,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { page ->
                    val pageDifficulty = availableDifficulties[page]
                    DifficultyContent(
                        songInfo = songInfo,
                        difficulty = pageDifficulty,
                        userRecords = userRecords,
                        apiEnabled = apiEnabled,
                        useApiData = useApiData,
                        songApiDetail = apiDetails[pageDifficulty] ?: SongApiDetailState(),
                        chartTagState = chartTags[pageDifficulty] ?: ChartTagUiState(),
                        canVote = canVote,
                        onSubmitChartTagVote = onSubmitChartTagVote,
                        syncHistory = syncHistory,
                        contentBottomPadding = contentBottomPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongInfoHeader(
    songInfo: SongInfo,
    difficulty: Difficulty,
    thumbnailUrl: String?,
    onIllustrationClick: (Difficulty) -> Unit,
    aliasProposal: AliasProposalUiState,
    onSubmitAliasProposal: (String, String) -> Unit,
    onAliasProposalDialogOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        val platformContext = LocalPlatformContext.current
        val thumbnailRequest = remember(platformContext, thumbnailUrl) {
            thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
                ImageRequest.Builder(platformContext)
                    .data(url)
                    .size(168)
                    .networkCachePolicy(CachePolicy.READ_ONLY)
                    .crossfade(200)
                    .build()
            }
        }
        AsyncImage(
            model = thumbnailRequest,
            contentDescription = "Illustration",
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onIllustrationClick(difficulty) },
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            SelectionContainer {
                BasicText(
                    text = songInfo.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        // Fixed titleLarge line height would let an auto-shrunk
                        // title drift off-center; Unspecified follows the font.
                        lineHeight = TextUnit.Unspecified
                    ),
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 12.sp,
                        maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
                        stepSize = 1.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(
                    text = stringResource(Res.string.song_detail_composer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Only the value is selectable, so copying never picks up the label
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = songInfo.composer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                Text(
                    text = stringResource(Res.string.song_detail_illustrator),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = songInfo.illustrator,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AliasChips(
                nicknames = songInfo.nicknames,
                proposalState = aliasProposal,
                onSubmitProposal = onSubmitAliasProposal,
                onProposalDialogOpen = onAliasProposalDialogOpen
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                InfoChip(label = "BPM", value = songInfo.bpm)
                InfoChip(label = stringResource(Res.string.song_detail_duration), value = songInfo.length)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.song_detail_chapter, chapterDisplayNameLocalized(songInfo.chapter)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Song aliases rendered as chips in the same visual language as chart tags.
 * Collapsed to two rows by default with an expand control on the title row;
 * expanding reveals the full set with a trailing collapse chip, and tapping
 * again collapses. Expansion reveals the chips progressively as the bounds
 * grow; collapse crossfades the full set into the truncated rows so the
 * hidden chips disappear gradually instead of vanishing at once (see
 * expandCollapseTransition).
 *
 * The whole FlowLayout overflow API (ContextualFlowRow and FlowRow's overflow
 * parameter) is deprecated and unmaintained, so the collapse is a plain
 * two-line clip driven by maxLines and the expand control lives beside the
 * section title. Because the control is no longer overflow-aware on its own,
 * an invisible unclipped copy of the chips is measured alongside the visible
 * row: the expand control only appears when the full set is taller than the
 * two collapsed rows, i.e. when there is actually more to reveal.
 *
 * The title row also carries the alias contribution entry, which stays
 * visible even for songs without any alias yet. Both title-row controls use
 * the same toggle-chip style as the collapse chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AliasChips(
    nicknames: List<String>,
    proposalState: AliasProposalUiState,
    onSubmitProposal: (String, String) -> Unit,
    onProposalDialogOpen: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showProposalDialog by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotionEnabled()
    var fullHeightPx by remember(nicknames) { mutableStateOf<Int?>(null) }
    var collapsedHeightPx by remember(nicknames) { mutableStateOf<Int?>(null) }
    val canExpand = fullHeightPx?.let { full ->
        collapsedHeightPx?.let { collapsed -> full > collapsed }
    } == true
    val chipContent: @Composable (Int) -> Unit = { index ->
        if (index < nicknames.size) {
            AliasChip(nicknames[index])
        } else {
            AliasToggleChip(text = stringResource(Res.string.song_detail_collapse), onClick = { expanded = false })
        }
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.song_detail_aliases_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AliasToggleChip(
                    text = stringResource(Res.string.song_detail_alias_contribute),
                    onClick = {
                        onProposalDialogOpen()
                        showProposalDialog = true
                    }
                )
                if (!expanded && canExpand) {
                    AliasToggleChip(
                        text = stringResource(Res.string.song_detail_expand_all, nicknames.size),
                        onClick = { expanded = true }
                    )
                }
            }
        }
        if (nicknames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Box {
                // Measurement copy: laid out with unlimited lines but reporting
                // zero size, so it never affects layout or pixels. The height
                // is captured inside the layout block because any observation
                // modifier placed around it would only see the reported (0, 0).
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0f)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            if (fullHeightPx != placeable.height) {
                                fullHeightPx = placeable.height
                            }
                            layout(0, 0) { placeable.place(0, 0) }
                        }
                ) {
                    nicknames.forEach { AliasChip(it) }
                }
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = { expandCollapseTransition(reducedMotion) },
                    label = "aliasChips",
                    modifier = Modifier.fillMaxWidth()
                ) { targetExpanded ->
                    // The collapse toggle rides as a real last item when expanded
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxLines = if (targetExpanded) Int.MAX_VALUE else 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { if (!targetExpanded) collapsedHeightPx = it.height }
                    ) {
                        repeat(nicknames.size + if (targetExpanded) 1 else 0) { index ->
                            chipContent(index)
                        }
                    }
                }
            }
        }
        if (proposalState.succeeded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.song_detail_alias_propose_success),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    if (showProposalDialog) {
        AliasProposalDialog(
            state = proposalState,
            onDismiss = { showProposalDialog = false },
            onSubmit = onSubmitProposal
        )
    }
}

@Composable
private fun AliasProposalDialog(
    state: AliasProposalUiState,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var alias by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.succeeded) {
        if (state.succeeded) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!state.submitting) onDismiss() },
        title = { Text(stringResource(Res.string.song_detail_alias_propose_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text(stringResource(Res.string.song_detail_alias_propose_alias_label)) },
                        singleLine = true,
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(Res.string.song_detail_alias_propose_note_label)) },
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let { error ->
                        Text(
                            text = error.asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = stringResource(Res.string.song_detail_alias_propose_review_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Text(
                        text = stringResource(Res.string.song_detail_alias_propose_doc_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(alias, note) },
                enabled = !state.submitting && alias.isNotBlank()
            ) {
                Text(
                    stringResource(
                        if (state.submitting) Res.string.song_detail_vote_submitting
                        else Res.string.song_detail_alias_propose_submit
                    )
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { uriHandler.openUri(ALIAS_SHARED_DOC_URL) }) {
                    Text(stringResource(Res.string.song_detail_alias_propose_doc_button))
                }
                TextButton(onClick = onDismiss, enabled = !state.submitting) {
                    Text(stringResource(Res.string.song_detail_alias_propose_cancel))
                }
            }
        }
    )
}

// Community-maintained shared spreadsheet for alias submissions, recommended
// by the phi-plugin upstream while their proposal review queue is backlogged.
private const val ALIAS_SHARED_DOC_URL = "https://kdocs.cn/l/catqcMM9UR5Y"

@Composable
private fun AliasChip(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp)
    ) {
        SelectionContainer {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AliasToggleChip(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DifficultyContent(
    songInfo: SongInfo,
    difficulty: Difficulty,
    userRecords: List<BestRecord>,
    apiEnabled: Boolean,
    useApiData: Boolean,
    songApiDetail: SongApiDetailState,
    chartTagState: ChartTagUiState,
    canVote: Boolean,
    onSubmitChartTagVote: (Difficulty, List<String>, List<String>) -> Unit,
    syncHistory: List<SongSyncHistoryEntry>,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState()
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            val charter = songInfo.charters[difficulty] ?: stringResource(Res.string.song_detail_unknown)
            val notes = songInfo.noteCounts[difficulty]
            val record = userRecords.find { it.difficulty == difficulty }

            if (record != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(Res.string.song_detail_record_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = record.score.formatScore(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                ScoreRatingTag(
                                    rating = ScoreRating.fromScore(record.score, record.isFullCombo),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${record.accuracy.formatFourDecimals()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(Res.string.song_detail_rks_value, record.rks.formatFourDecimals()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (apiEnabled && useApiData) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.song_detail_community_stats),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (songApiDetail.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (songApiDetail.error != null) {
                            Text(
                                text = songApiDetail.error!!.asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                stringResource(
                                    Res.string.song_detail_chart_ranking,
                                    songApiDetail.userRank ?: "—",
                                    songApiDetail.totalUsers ?: "—"
                                )
                            )
                            Text(
                                text = stringResource(
                                    Res.string.song_detail_avg_acc,
                                    songApiDetail.avgAcc?.let { "${it.formatFourDecimals()}%" } ?: "—",
                                    songApiDetail.avgAccCount ?: 0
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.song_detail_chart_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row {
                        Text(
                            text = stringResource(Res.string.song_detail_charter),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = charter,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (notes != null && notes.total > 0) {
                        Text(
                            text = stringResource(Res.string.song_detail_notes_distribution, notes.total),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            NoteStatItem("Tap", notes.tap)
                            NoteStatItem("Drag", notes.drag)
                            NoteStatItem("Hold", notes.hold)
                            NoteStatItem("Flick", notes.flick)
                        }
                    } else {
                        Text(
                            text = stringResource(Res.string.song_detail_no_notes_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart-tag reads are public, so the section shows regardless of the
            // score-API switch; only voting requires the api_token.
            ChartTagSection(
                state = chartTagState,
                difficulty = difficulty,
                canVote = canVote,
                onSubmitVote = onSubmitChartTagVote
            )
            Spacer(modifier = Modifier.height(16.dp))

            val currentHistory = if (apiEnabled && useApiData) songApiDetail.history else syncHistory
            val filteredHistory = currentHistory
                .filter { it.difficulty == difficulty.name }
                .take(3)

            if (filteredHistory.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.song_detail_sync_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                filteredHistory.forEach { entry ->
                    SyncHistoryCard(entry)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        text = stringResource(Res.string.song_detail_no_sync_history),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Keep the last card clear of the gesture navigation bar and the
            // screen's rounded bottom corners
            Spacer(modifier = Modifier.height(contentBottomPadding + 16.dp))
            }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.takeIf { it.isNotBlank() } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun NoteStatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SyncHistoryCard(entry: SongSyncHistoryEntry) {
    val formattedTime = remember(entry.timestamp) {
        entry.timestamp.formatSyncTime()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.score.formatScore(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    ScoreRatingTag(
                        rating = ScoreRating.fromScore(entry.score, entry.isFullCombo),
                        fontSize = 10.sp
                    )
                }
            }
            Text(
                text = "${entry.accuracy.formatFourDecimals()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChartTagSection(
    state: ChartTagUiState,
    difficulty: Difficulty,
    canVote: Boolean,
    onSubmitVote: (Difficulty, List<String>, List<String>) -> Unit
) {
    var showVoteSheet by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.song_detail_chart_tags),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            when {
                state.error != null -> Text(
                    text = state.error!!.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                state.isLoading -> Unit
                state.categories.all { it.tags.isEmpty() } -> Text(
                    text = stringResource(Res.string.song_detail_no_tags_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> state.categories.filter { it.tags.isNotEmpty() }.forEach { category ->
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        category.tags.forEach { tag -> ChartTagChip(tag) }
                    }
                }
            }

            if (state.voteSucceeded) {
                Text(
                    text = stringResource(Res.string.song_detail_vote_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Voting is only possible with an api_token; without one the
            // button stays hidden instead of failing at submit time. Once the
            // chart has a vote on record (local or server-side) the button
            // stays visible but greyed out so re-voting is clearly a no-op.
            if (canVote) {
                OutlinedButton(
                    onClick = { showVoteSheet = true },
                    enabled = !state.isLoading && state.error == null && !state.hasVoted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (state.hasVoted) Res.string.song_detail_voted_button
                            else Res.string.song_detail_vote_button
                        )
                    )
                }
            }
        }
    }

    if (showVoteSheet) {
        ChartTagVoteSheet(
            state = state,
            onDismiss = { showVoteSheet = false },
            onSubmit = { primary, secondary ->
                onSubmitVote(difficulty, primary, secondary)
            }
        )
    }
}

@Composable
private fun ChartTagChip(tag: ChartTagVoteCount) {
    Surface(
        color = if (tag.isMine) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "${if (tag.isMine) "✓ " else ""}${tag.name} ${tag.votes}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChartTagVoteSheet(
    state: ChartTagUiState,
    onDismiss: () -> Unit,
    onSubmit: (List<String>, List<String>) -> Unit
) {
    var primaryMode by remember { mutableStateOf(true) }
    var primarySelection by remember {
        mutableStateOf(
            state.allCategories.flatMap { category -> category.tags.filter { it.isMine }.map { it.name } }.toSet()
        )
    }
    var secondarySelection by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(state.voteSucceeded) {
        if (state.voteSucceeded) onDismiss()
    }

    val tagScrollState = rememberScrollState()
    // Two-stage downward drag, gated at gesture start: a drag that begins
    // while the tag list can still scroll up only pulls the list back to the
    // top (sheet gestures stay off for the whole gesture); only a drag that
    // begins with the list already at the top may drag the sheet to dismiss.
    var sheetGesturesEnabled by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Gestures also stay off until the entry animation settles on Expanded:
    // a swipe that lands while the sheet is still rising must not be able to
    // fling it back off screen.
    val sheetSettled by remember { derivedStateOf { sheetState.currentValue == SheetValue.Expanded } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled && sheetSettled
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        sheetGesturesEnabled = !tagScrollState.canScrollBackward
                    }
                }
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.song_detail_vote_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = primaryMode,
                    onClick = { primaryMode = true },
                    label = { Text(stringResource(Res.string.song_detail_vote_primary)) }
                )
                FilterChip(
                    selected = !primaryMode,
                    onClick = { primaryMode = false },
                    label = { Text(stringResource(Res.string.song_detail_vote_secondary)) }
                )
            }
            Text(
                text = stringResource(Res.string.song_detail_vote_instructions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Constrain the scroll area to the remaining sheet height so the
            // submit button below stays visible even with many tag categories.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(tagScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.allCategories.forEach { category ->
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        category.tags.forEach { tag ->
                            val inPrimary = tag.name in primarySelection
                            val inSecondary = tag.name in secondarySelection
                            FilterChip(
                                selected = inPrimary || inSecondary,
                                onClick = {
                                    if (primaryMode) {
                                        primarySelection = if (inPrimary) {
                                            primarySelection - tag.name
                                        } else {
                                            primarySelection + tag.name
                                        }
                                        secondarySelection = secondarySelection - tag.name
                                    } else {
                                        secondarySelection = if (inSecondary) {
                                            secondarySelection - tag.name
                                        } else {
                                            secondarySelection + tag.name
                                        }
                                        primarySelection = primarySelection - tag.name
                                    }
                                },
                                label = { Text(tag.name) },
                                // Primary and secondary picks keep separate
                                // highlights so the two groups stay readable
                                // at a glance before they are submitted apart.
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when {
                                        inPrimary -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.tertiaryContainer
                                    },
                                    selectedLabelColor = when {
                                        inPrimary -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            )
                        }
                    }
                }
            }

            state.voteError?.let { error ->
                Text(
                    text = error.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = { onSubmit(primarySelection.toList(), secondarySelection.toList()) },
                enabled = !state.voteSubmitting && (primarySelection.isNotEmpty() || secondarySelection.isNotEmpty()),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.voteSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (state.voteSubmitting) {
                        stringResource(Res.string.song_detail_vote_submitting)
                    } else {
                        stringResource(Res.string.song_detail_vote_submit)
                    }
                )
            }
        }
    }
}

@Composable
private fun DifficultyTabRow(
    pagerState: androidx.compose.foundation.pager.PagerState,
    availableDifficulties: List<Difficulty>,
    difficulties: Map<Difficulty, Float>
) {
    PrimaryTabRow(
        selectedTabIndex = pagerState.currentPage,
        modifier = Modifier.fillMaxWidth(),
        indicator = { ElasticPagerPrimaryIndicator(pagerState = pagerState) }
    ) {
        val scope = rememberCoroutineScope()
        availableDifficulties.forEachIndexed { index, diff ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                text = {
                    Text(text = "${diff.name} ${difficulties[diff] ?: ""}")
                }
            )
        }
    }
}
