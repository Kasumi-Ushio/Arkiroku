package org.kasumi321.ushio.phitracker.ui.collectible

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.domain.model.CollectibleCategory
import org.kasumi321.ushio.phitracker.domain.model.CollectibleChapter
import org.kasumi321.ushio.phitracker.domain.model.CollectibleCovers
import org.kasumi321.ushio.phitracker.domain.model.CollectibleItem
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog
import org.kasumi321.ushio.phitracker.ui.utils.ADAPTIVE_GRID_MIN_COLUMN_WIDTH
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.collectible_category_bold
import phitracker.composeapp.generated.resources.collectible_category_key
import phitracker.composeapp.generated.resources.collectible_category_main
import phitracker.composeapp.generated.resources.collectible_category_nazo
import phitracker.composeapp.generated.resources.collectible_category_nonsense
import phitracker.composeapp.generated.resources.collectible_category_souvenir
import phitracker.composeapp.generated.resources.collectible_category_unknown
import phitracker.composeapp.generated.resources.collectibles_chapters_selected_count
import phitracker.composeapp.generated.resources.collectibles_data_update_available
import phitracker.composeapp.generated.resources.collectibles_filter_category_label
import phitracker.composeapp.generated.resources.collectibles_filter_chapter_label
import phitracker.composeapp.generated.resources.collectibles_filter_title
import phitracker.composeapp.generated.resources.collectibles_group_item_count
import phitracker.composeapp.generated.resources.collectibles_part_count
import phitracker.composeapp.generated.resources.collectibles_poster_count
import phitracker.composeapp.generated.resources.collectibles_select_chapters_title
import phitracker.composeapp.generated.resources.collectibles_update_now
import phitracker.composeapp.generated.resources.songs_action_done
import phitracker.composeapp.generated.resources.songs_action_reset
import phitracker.composeapp.generated.resources.songs_chapters_select_hint
import phitracker.composeapp.generated.resources.songs_clear_all_count

private data class CollectibleChapterGroup(
    val chapter: CollectibleChapter,
    val items: List<CollectibleItem>
)

/** Narrowest a chapter poster column may shrink to before the grid adds columns. */
private val POSTER_GRID_MIN_COLUMN_WIDTH = 180.dp

/** Poster aspect: the source art is wide (16:9-ish), so crop only a little. */
private val POSTER_ASPECT_RATIO = 16f / 10f

/**
 * Minimum width needed to split the expanded left pane into a poster column
 * and a chapter list. 45% of this is exactly the poster grid's minimum column
 * width, leaving the rest for a readable list; below it the pane falls back to
 * the in-place drill-down.
 */
internal val MinSplitChapterPaneWidth = 400.dp

/** Only the expanded two-pane layout, and only with enough width, splits. */
internal fun useSplitChapterPane(isExpandedPane: Boolean, availableWidth: Dp): Boolean =
    isExpandedPane && availableWidth >= MinSplitChapterPaneWidth

/**
 * Collectibles sub-tab. Two browsing levels share this surface:
 *  - idle (no search query, no active filters): a poster grid with one card
 *    per chapter; tapping a poster opens the chapter's item list in place
 *    when [drilledChapterKey] is provided. In the expanded left pane with
 *    enough width ([splitChapterPane]) the poster grid stays visible and the
 *    drilled list occupies a second column; otherwise the list replaces the
 *    grid. [onChapterClick] only reports the intent; the caller updates the
 *    hoisted drill-down state;
 *  - searching/filtering: the flat chapter-grouped results list, so matches
 *    stay directly visible.
 * Mirrors SongsTab's layout contract (search/filter open from the FAB panel
 * below the floating header) and its update-banner pattern. No Haze here —
 * cards and sheets stay plain per the project glass rules.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CollectiblesTab(
    state: CollectiblesUiState,
    updateAvailable: Boolean,
    onToggleChapter: (String) -> Unit,
    onToggleCategory: (CollectibleCategory) -> Unit,
    onClearChapters: () -> Unit,
    onToggleFilterSheet: (Boolean) -> Unit,
    onResetFilters: () -> Unit,
    onUpdateNow: () -> Unit,
    getCoverUrl: (String) -> String?,
    getPosterUrl: (String) -> String?,
    onChapterClick: (CollectibleChapter) -> Unit,
    onItemClick: (CollectibleItem) -> Unit,
    drilledChapterKey: String? = null,
    splitChapterPane: Boolean = false,
    onBackFromChapter: () -> Unit = {},
    selectedItem: Pair<String, String>? = null,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    useGrid: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState(),
    // One preserved scroll state per drilled chapter. The pager may dispose
    // the offscreen collectibles page, so the caller can back this with a
    // hoisted map; the default keeps the pre-pager per-composition behavior.
    getChapterListState: (String) -> LazyListState = { LazyListState() },
    modifier: Modifier = Modifier
) {
    val groups = remember(state.filteredItems, state.chapters) {
        val byChapter = state.filteredItems.groupBy { it.chapterFolderKey }
        buildList {
            state.chapters.forEach { chapter ->
                byChapter[chapter.folderKey]?.let { add(CollectibleChapterGroup(chapter, it)) }
            }
            // Items whose chapter is missing from the chapter list (not
            // expected from the provider) still render, appended in
            // first-appearance order with a default cover.
            val known = state.chapters.map { it.folderKey }.toSet()
            byChapter.forEach { (folderKey, items) ->
                if (folderKey !in known) {
                    add(
                        CollectibleChapterGroup(
                            CollectibleChapter(
                                folderKey = folderKey,
                                displayName = folderKey,
                                displaySub = "",
                                coverSongId = CollectibleCovers.coverSongId(folderKey)
                            ),
                            items
                        )
                    )
                }
            }
        }
    }
    val chapterItemCounts = remember(state.filteredItems) {
        state.filteredItems.groupingBy { it.chapterFolderKey }.eachCount()
    }
    val browsingActive = state.isBrowsingActive()

    // The header consumed the top inset; pad the list so the first group
    // stays clear of it and the last item clears the floating glass bar.
    val listContentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 8.dp
    )
    when {
        // Expanded left pane: keep the poster wall visible and put the
        // drilled chapter list beside it. The split only exists where a
        // detail pane keeps the reading page off this surface; narrower panes
        // fall back to the in-place drill-down.
        !browsingActive && splitChapterPane && drilledChapterKey != null -> {
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                if (useSplitChapterPane(splitChapterPane, maxWidth)) {
                    val splitPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = listContentPadding.calculateTopPadding(),
                        bottom = listContentPadding.calculateBottomPadding()
                    )
                    Row(modifier = Modifier.fillMaxSize()) {
                        ChapterPosterGrid(
                            state = state,
                            updateAvailable = updateAvailable,
                            onUpdateNow = onUpdateNow,
                            chapterItemCounts = chapterItemCounts,
                            getPosterUrl = getPosterUrl,
                            onChapterClick = onChapterClick,
                            contentPadding = splitPadding,
                            gridState = gridState,
                            selectedChapterKey = drilledChapterKey,
                            modifier = Modifier.weight(0.45f)
                        )
                        VerticalDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(top = listContentPadding.calculateTopPadding()),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        DrilledChapterContent(
                            folderKey = drilledChapterKey,
                            state = state,
                            updateAvailable = false,
                            onUpdateNow = onUpdateNow,
                            getPosterUrl = getPosterUrl,
                            selectedItem = selectedItem,
                            onItemClick = onItemClick,
                            onBackFromChapter = onBackFromChapter,
                            contentPadding = splitPadding,
                            getChapterListState = getChapterListState,
                            modifier = Modifier.weight(0.55f)
                        )
                    }
                } else {
                    DrilledChapterContent(
                        folderKey = drilledChapterKey,
                        state = state,
                        updateAvailable = updateAvailable,
                        onUpdateNow = onUpdateNow,
                        getPosterUrl = getPosterUrl,
                        selectedItem = selectedItem,
                        onItemClick = onItemClick,
                        onBackFromChapter = onBackFromChapter,
                        contentPadding = listContentPadding,
                        getChapterListState = getChapterListState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        // Level 1+2 (two-pane layouts): chapter posters are the idle view;
        // tapping one drills the chapter's item list into this pane in place
        // while the reading page stays in the detail pane. On compact
        // layouts [drilledChapterKey] is always null and posters navigate to
        // the full-screen chapter route instead.
        !browsingActive -> AnimatedContent(
            targetState = drilledChapterKey,
            transitionSpec = {
                // Same motion language as the navigation transitions:
                // drilling down slides forward, back slides in reverse.
                if (targetState != null) {
                    slideInHorizontally { width -> width / 10 } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width / 10 } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width / 10 } + fadeIn() togetherWith
                        slideOutHorizontally { width -> width / 10 } + fadeOut()
                }
            },
            label = "collectiblesChapterDrill"
        ) { chapterKey ->
            if (chapterKey == null) {
                ChapterPosterGrid(
                    state = state,
                    updateAvailable = updateAvailable,
                    onUpdateNow = onUpdateNow,
                    chapterItemCounts = chapterItemCounts,
                    getPosterUrl = getPosterUrl,
                    onChapterClick = onChapterClick,
                    contentPadding = listContentPadding,
                    gridState = gridState,
                    modifier = modifier
                )
            } else {
                DrilledChapterContent(
                    folderKey = chapterKey,
                    state = state,
                    updateAvailable = updateAvailable,
                    onUpdateNow = onUpdateNow,
                    getPosterUrl = getPosterUrl,
                    selectedItem = selectedItem,
                    onItemClick = onItemClick,
                    onBackFromChapter = onBackFromChapter,
                    contentPadding = listContentPadding,
                    getChapterListState = getChapterListState,
                    modifier = modifier
                )
            }
        }
        useGrid -> LazyVerticalGrid(
            columns = GridCells.Adaptive(ADAPTIVE_GRID_MIN_COLUMN_WIDTH),
            state = gridState,
            modifier = modifier.fillMaxSize(),
            contentPadding = listContentPadding,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (updateAvailable) {
                item(
                    key = "collectible_data_update_banner",
                    contentType = "collectible_data_banner",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    CollectibleDataUpdateBanner(onUpdateNow)
                }
            }
            groups.forEach { group ->
                item(
                    key = "chapter_${group.chapter.folderKey}",
                    contentType = "chapter_header",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    CollectibleChapterHeader(
                        group = group,
                        getCoverUrl = getCoverUrl
                    )
                }
                items(
                    group.items,
                    key = { "${it.chapterFolderKey}/${it.key}" },
                    contentType = { "collectible_item" }
                ) { item ->
                    CollectibleItemCard(
                        item = item,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
        else -> LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = listContentPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (updateAvailable) {
                item(key = "collectible_data_update_banner", contentType = "collectible_data_banner") {
                    CollectibleDataUpdateBanner(onUpdateNow)
                }
            }
            groups.forEach { group ->
                item(key = "chapter_${group.chapter.folderKey}", contentType = "chapter_header") {
                    CollectibleChapterHeader(
                        group = group,
                        getCoverUrl = getCoverUrl
                    )
                }
                items(
                    group.items,
                    key = { "${it.chapterFolderKey}/${it.key}" },
                    contentType = { "collectible_item" }
                ) { item ->
                    CollectibleItemCard(
                        item = item,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }

    if (state.showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { onToggleFilterSheet(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            CollectibleFilterSheetContent(
                chapters = state.chapters,
                selectedChapters = state.selectedChapters,
                onToggleChapter = onToggleChapter,
                onClearChapters = onClearChapters,
                selectedCategories = state.selectedCategories,
                onToggleCategory = onToggleCategory,
                onResetFilters = onResetFilters,
                onClose = { onToggleFilterSheet(false) }
            )
        }
    }
}

@Composable
private fun ChapterPosterCard(
    chapter: CollectibleChapter,
    itemCount: Int,
    posterUrl: String?,
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    val imageRequest = remember(platformContext, posterUrl) {
        posterUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(platformContext)
                .data(url)
                .networkCachePolicy(CachePolicy.READ_ONLY)
                .crossfade(200)
                .build()
        }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            // Dark bottom scrim keeps the overlaid text legible on any art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Line 1: the localized chapter ordinal, omitted for chapters
                // without one (e.g. the EX chapter).
                if (chapter.displaySub.isNotBlank()) {
                    Text(
                        text = chapter.displaySub,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                // Line 2: chapter name left, item count right, both in the
                // poster's small/secondary type scale.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chapter.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.collectibles_poster_count, itemCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterPosterGrid(
    state: CollectiblesUiState,
    updateAvailable: Boolean,
    onUpdateNow: () -> Unit,
    chapterItemCounts: Map<String, Int>,
    getPosterUrl: (String) -> String?,
    onChapterClick: (CollectibleChapter) -> Unit,
    contentPadding: PaddingValues,
    gridState: LazyGridState,
    selectedChapterKey: String? = null,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(POSTER_GRID_MIN_COLUMN_WIDTH),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (updateAvailable) {
            item(
                key = "collectible_data_update_banner",
                contentType = "collectible_data_banner",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                CollectibleDataUpdateBanner(onUpdateNow)
            }
        }
        items(
            state.chapters,
            key = { "poster_${it.folderKey}" },
            contentType = { "chapter_poster" }
        ) { chapter ->
            ChapterPosterCard(
                chapter = chapter,
                itemCount = chapterItemCounts[chapter.folderKey] ?: 0,
                posterUrl = getPosterUrl(chapter.coverSongId),
                onClick = { onChapterClick(chapter) },
                selected = chapter.folderKey == selectedChapterKey
            )
        }
    }
}

/**
 * Chapter drill-down (level 2): the chapter's item list with a compact header
 * (thumb + name + ordinal + count + back-to-posters affordance). On the
 * expanded layout it is the second column of the split pane; on compact or
 * narrow panes it replaces the poster grid in place. The back handler
 * closes the drill-down; it is disabled while an item is shown in the detail
 * pane so the pane's own back handling pops that first.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DrilledChapterContent(
    folderKey: String,
    state: CollectiblesUiState,
    updateAvailable: Boolean,
    onUpdateNow: () -> Unit,
    getPosterUrl: (String) -> String?,
    selectedItem: Pair<String, String>?,
    onItemClick: (CollectibleItem) -> Unit,
    onBackFromChapter: () -> Unit,
    contentPadding: PaddingValues,
    getChapterListState: (String) -> LazyListState,
    modifier: Modifier = Modifier
) {
    val chapter = state.chapters.firstOrNull { it.folderKey == folderKey }
    // Unknown chapter (e.g. data changed under a restored state): step back
    // to the poster grid instead of rendering an empty pane. While the first
    // load is still settling, keep the restored drill-down open and wait for
    // the chapter list to arrive (Activity recreation restores folderKey
    // before the data provider has finished loading).
    if (chapter == null) {
        if (!state.isLoading) {
            LaunchedEffect(folderKey) { onBackFromChapter() }
        }
        return
    }
    val items = remember(state.allItems, folderKey) {
        state.allItems.filter { it.chapterFolderKey == folderKey }
    }
    val selectedItemKey = selectedItem?.takeIf { it.first == folderKey }?.second
    val listState = remember(folderKey) { getChapterListState(folderKey) }

    BackHandler(enabled = selectedItem == null, onBack = onBackFromChapter)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        CollectibleChapterContent(
            items = items,
            onItemClick = onItemClick,
            selectedItemKey = selectedItemKey,
            contentPadding = contentPadding,
            listState = listState,
            header = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (updateAvailable) {
                        CollectibleDataUpdateBanner(onUpdateNow)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    CollectibleChapterHeader(
                        chapter = chapter,
                        itemCount = items.size,
                        posterUrl = getPosterUrl(chapter.coverSongId),
                        onBack = onBackFromChapter
                    )
                }
            }
        )
    }
}

@Composable
private fun CollectibleDataUpdateBanner(onUpdateNow: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.collectibles_data_update_available),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onUpdateNow) {
                Text(stringResource(Res.string.collectibles_update_now))
            }
        }
    }
}

@Composable
private fun CollectibleChapterHeader(
    group: CollectibleChapterGroup,
    getCoverUrl: (String) -> String?,
    modifier: Modifier = Modifier
) {
    val imageRequest = rememberLocalCoverRequest(getCoverUrl(group.chapter.coverSongId))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.chapter.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (group.chapter.displaySub.isNotBlank()) {
                Text(
                    text = group.chapter.displaySub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = stringResource(Res.string.collectibles_group_item_count, group.items.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CollectibleItemCard(
    item: CollectibleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    rank: Int? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chapter-list ordinal in the B30 score card's rank style. Flat
            // search results pass no rank, so only chapter lists number items.
            if (rank != null) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (rank >= 100) 13.sp else 16.sp,
                        color = when (rank) {
                            1 -> MaterialTheme.colorScheme.primary
                            in 2..3 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val firstPart = item.parts.firstOrNull()
                    if (firstPart != null) {
                        CategoryChip(firstPart.category)
                        if (firstPart.date.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = firstPart.date,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    if (item.parts.size > 1) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.collectibles_part_count, item.parts.size),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategoryChip(category: CollectibleCategory) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = collectibleCategoryLabel(category),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun collectibleCategoryLabel(category: CollectibleCategory): String = when (category) {
    CollectibleCategory.MAIN -> stringResource(Res.string.collectible_category_main)
    CollectibleCategory.SOUVENIR -> stringResource(Res.string.collectible_category_souvenir)
    CollectibleCategory.BOLD -> stringResource(Res.string.collectible_category_bold)
    CollectibleCategory.KEY -> stringResource(Res.string.collectible_category_key)
    CollectibleCategory.NONSENSE -> stringResource(Res.string.collectible_category_nonsense)
    CollectibleCategory.NAZO -> stringResource(Res.string.collectible_category_nazo)
    CollectibleCategory.UNKNOWN -> stringResource(Res.string.collectible_category_unknown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectibleFilterSheetContent(
    chapters: List<CollectibleChapter>,
    selectedChapters: Set<String>,
    onToggleChapter: (String) -> Unit,
    onClearChapters: () -> Unit,
    selectedCategories: Set<CollectibleCategory>,
    onToggleCategory: (CollectibleCategory) -> Unit,
    onResetFilters: () -> Unit,
    onClose: () -> Unit
) {
    var showChapterDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.collectibles_filter_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onResetFilters) {
                    Text(stringResource(Res.string.songs_action_reset))
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(Res.string.songs_action_done))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            stringResource(Res.string.collectibles_filter_category_label),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CollectibleCategory.entries) { category ->
                FilterChip(
                    selected = category in selectedCategories,
                    onClick = { onToggleCategory(category) },
                    label = { Text(collectibleCategoryLabel(category)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chapter selection lives in its own dialog for the same reason as
        // the songs filter: 15 chapters of chips would fight the sheet's
        // drag-to-dismiss gestures.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showChapterDialog = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.collectibles_filter_chapter_label),
                style = MaterialTheme.typography.titleMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selectedChapters.isEmpty()) {
                        stringResource(Res.string.songs_chapters_select_hint)
                    } else {
                        stringResource(Res.string.collectibles_chapters_selected_count, selectedChapters.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showChapterDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showChapterDialog = false },
            title = { Text(stringResource(Res.string.collectibles_select_chapters_title)) },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    chapters.forEach { chapter ->
                        FilterChip(
                            selected = chapter.folderKey in selectedChapters,
                            onClick = { onToggleChapter(chapter.folderKey) },
                            label = { Text(chapter.displayName) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChapterDialog = false }) {
                    Text(stringResource(Res.string.songs_action_done))
                }
            },
            dismissButton = if (selectedChapters.isNotEmpty()) {
                {
                    TextButton(onClick = onClearChapters) {
                        Text(stringResource(Res.string.songs_clear_all_count, selectedChapters.size))
                    }
                }
            } else null
        )
    }
}
