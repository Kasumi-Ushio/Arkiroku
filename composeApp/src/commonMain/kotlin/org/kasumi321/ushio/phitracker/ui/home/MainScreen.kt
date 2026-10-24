package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.action_settings
import phitracker.composeapp.generated.resources.action_sync
import phitracker.composeapp.generated.resources.collectibles_all_items_count
import phitracker.composeapp.generated.resources.collectibles_search_placeholder
import phitracker.composeapp.generated.resources.collectibles_subtab_collectibles
import phitracker.composeapp.generated.resources.collectibles_subtab_songs
import phitracker.composeapp.generated.resources.home_tab_index
import phitracker.composeapp.generated.resources.home_tab_profile
import phitracker.composeapp.generated.resources.home_tab_tools
import phitracker.composeapp.generated.resources.preload_current_song
import phitracker.composeapp.generated.resources.preload_dialog_cancel
import phitracker.composeapp.generated.resources.preload_dialog_downloading
import phitracker.composeapp.generated.resources.preload_dialog_message
import phitracker.composeapp.generated.resources.preload_dialog_minimize
import phitracker.composeapp.generated.resources.preload_dialog_skip
import phitracker.composeapp.generated.resources.preload_dialog_start
import phitracker.composeapp.generated.resources.preload_dialog_title
import phitracker.composeapp.generated.resources.profile_avatar
import phitracker.composeapp.generated.resources.profile_set_avatar
import phitracker.composeapp.generated.resources.songs_all_songs_count
import phitracker.composeapp.generated.resources.songs_search_placeholder
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.rememberAvatarPicker
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.b30.B30ExportPayload
import org.kasumi321.ushio.phitracker.ui.collectible.CollectiblesTab
import org.kasumi321.ushio.phitracker.ui.collectible.CollectiblesViewModel
import org.kasumi321.ushio.phitracker.ui.collectible.isBrowsingActive
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog
import org.kasumi321.ushio.phitracker.ui.components.ElasticPagerPrimaryIndicator
import org.kasumi321.ushio.phitracker.ui.glass.GlassBottomBar
import org.kasumi321.ushio.phitracker.ui.glass.GlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.HomeGlassScaffold
import org.kasumi321.ushio.phitracker.ui.glass.HomeGlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.HomeHeaderSpec
import org.kasumi321.ushio.phitracker.ui.glass.HomeNavMode
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.update.UpdateResultDialog
import org.kasumi321.ushio.phitracker.ui.utils.asString
import org.kasumi321.ushio.phitracker.ui.utils.isAtLeastMedium
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled
import org.kasumi321.ushio.phitracker.ui.utils.rememberWindowSizeClass
import org.kasumi321.ushio.phitracker.ui.utils.resolve
import org.koin.compose.viewmodel.koinViewModel

private const val B30_TAG_ANALYSIS_SETTLE_TIMEOUT_MS = 10_000L

enum class HomeTab {
    Profile,
    B30,
    Index,
    Tools
}

class HomeTabState(initial: HomeTab = HomeTab.Profile) {
    var selected by mutableStateOf(initial)
        private set

    fun select(tab: HomeTab) {
        selected = tab
    }

    companion object {
        val Saver = Saver<HomeTabState, Int>(
            save = { it.selected.ordinal },
            restore = { HomeTabState(HomeTab.entries[it]) }
        )
    }
}

/** Sub-tabs of the Index page: the original song list and the new collectible index. */
enum class IndexSubTab {
    Songs,
    Collectibles
}

/**
 * Sub-tab selection of the Index page, hoisted next to [HomeTabState] so the
 * selection survives the compact/expanded layout swap (a foldable's
 * fold/unfold disposes and rebuilds the home composition).
 */
class IndexSubTabState(initial: IndexSubTab = IndexSubTab.Songs) {
    var selected by mutableStateOf(initial)
        private set

    fun select(subTab: IndexSubTab) {
        selected = subTab
    }

    companion object {
        val Saver = Saver<IndexSubTabState, Int>(
            save = { it.selected.ordinal },
            restore = { IndexSubTabState(IndexSubTab.entries[it]) }
        )
    }
}

/**
 * In-place chapter drill-down state of the collectibles browser: the chapter
 * list replaces the poster grid inside the Index content area on both compact
 * and expanded layouts (on expanded it is the left pane while the reading page
 * stays in the detail pane). Hoisted at the home destination level (next to
 * [HomeTabState]) so it survives the compact/expanded layout swap,
 * configuration changes and process death.
 */
class CollectiblesChapterState(initialFolderKey: String? = null) {
    var folderKey by mutableStateOf(initialFolderKey)
        private set

    val isDrilledDown: Boolean get() = folderKey != null

    fun open(folderKey: String) {
        this.folderKey = folderKey
    }

    fun close() {
        folderKey = null
    }

    companion object {
        val Saver = Saver<CollectiblesChapterState, String>(
            save = { it.folderKey },
            restore = { saved -> CollectiblesChapterState(saved) }
        )
    }
}

data class BottomNavItem(
    val tab: HomeTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun MainBottomBar(
    navItems: List<BottomNavItem>,
    selectedTab: HomeTab,
    reducedMotionEnabled: Boolean,
    onTabSelected: (HomeTab) -> Unit
) {
    // The surrounding GlassBottomBar supplies the blurred background; both
    // branches stay transparent so the same glass container shows through.
    if (!reducedMotionEnabled) {
        NavigationBar(containerColor = Color.Transparent) {
            navItems.forEach { item ->
                val selected = selectedTab == item.tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(item.tab) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
        return
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            navItems.forEach { item ->
                val selected = selectedTab == item.tab
                TextButton(onClick = { onTabSelected(item.tab) }) {
                    Text(
                        text = item.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/**
 * Circular avatar pinned to the top of the rail. With no avatar set, taps open
 * the avatar picker; once set, taps switch to the Profile tab instead.
 */
@Composable
private fun RailAvatar(
    avatarUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUri != null) {
            val imageRequest = remember(platformContext, avatarUri) {
                ImageRequest.Builder(platformContext)
                    .data(avatarUri)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = stringResource(Res.string.profile_avatar),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.profile_set_avatar),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MainNavigationRail(
    navItems: List<BottomNavItem>,
    selectedTab: HomeTab,
    avatarUri: String?,
    onAvatarClick: () -> Unit,
    reducedMotionEnabled: Boolean,
    onTabSelected: (HomeTab) -> Unit
) {
    // Opaque official-styled rail: tab content is physically shifted right of
    // the rail (see HomeGlassScaffold), so nothing ever scrolls beneath it and
    // a glass background would have nothing to blur. The trailing divider
    // separates the rail from the content; it is part of this slot so the
    // scaffold's measured rail width includes it.
    Row {
        if (!reducedMotionEnabled) {
            NavigationRail(
                header = { RailAvatar(avatarUri = avatarUri, onClick = onAvatarClick) }
            ) {
                Spacer(modifier = Modifier.weight(1f))
                navItems.forEach { item ->
                    val selected = selectedTab == item.tab
                    NavigationRailItem(
                        selected = selected,
                        onClick = { onTabSelected(item.tab) },
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
                // Lift the item group one tab height off the bottom edge.
                Spacer(modifier = Modifier.height(64.dp))
            }
            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.statusBarsPadding()
            )
            return@Row
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RailAvatar(avatarUri = avatarUri, onClick = onAvatarClick)
                Spacer(modifier = Modifier.weight(1f))
                navItems.forEach { item ->
                    val selected = selectedTab == item.tab
                    TextButton(onClick = { onTabSelected(item.tab) }) {
                        Text(
                            text = item.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
        VerticalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.statusBarsPadding()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToB30Image: (B30ExportPayload) -> Unit,
    onNavigateToSongDetail: (String) -> Unit,
    onNavigateToSongDetailWithDifficulty: (String, org.kasumi321.ushio.phitracker.domain.model.Difficulty?) -> Unit,
    onNavigateToCollectibleDetail: (String, String) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSuggest: () -> Unit,
    onNavigateToLogin: () -> Unit,
    tabState: HomeTabState,
    indexSubTabState: IndexSubTabState,
    isInExpandedPane: Boolean = false,
    selectedCollectibleItem: Pair<String, String>? = null,
    collectiblesChapterState: CollectiblesChapterState =
        rememberSaveable(saver = CollectiblesChapterState.Saver) { CollectiblesChapterState() },
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val collectiblesViewModel: CollectiblesViewModel = koinViewModel()
    val collectiblesState by collectiblesViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedTab = tabState.selected
    val tip = remember(selectedTab) { viewModel.getRandomTip() }
    val reducedMotionEnabled = rememberReducedMotionEnabled()
    val windowSizeClass = rememberWindowSizeClass()
    val navMode = if (windowSizeClass.isAtLeastMedium()) HomeNavMode.Rail else HomeNavMode.BottomBar

    val navItems = listOf(
        BottomNavItem(HomeTab.Profile, stringResource(Res.string.home_tab_profile), Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(HomeTab.B30, "B30", Icons.Filled.Star, Icons.Outlined.StarBorder),
        BottomNavItem(HomeTab.Index, stringResource(Res.string.home_tab_index), Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
        BottomNavItem(HomeTab.Tools, stringResource(Res.string.home_tab_tools), Icons.Filled.Build, Icons.Outlined.Build)
    )

    val launchAvatarPicker = rememberAvatarPicker { uri -> uri?.let(viewModel::setAvatarUri) }

    LaunchedEffect(state.sync.isLoggedOut) {
        if (state.sync.isLoggedOut) onLogout()
    }

    LaunchedEffect(selectedTab) {
        val tabName = when (selectedTab) {
            HomeTab.Profile -> "profile"
            HomeTab.B30 -> "b30"
            HomeTab.Index -> "index"
            HomeTab.Tools -> "tools"
        }
        AppLogger.event("navigation", "tab_switched", mapOf("tab" to tabName))
    }

    LaunchedEffect(indexSubTabState.selected) {
        val subTabName = when (indexSubTabState.selected) {
            IndexSubTab.Songs -> "songs"
            IndexSubTab.Collectibles -> "collectibles"
        }
        AppLogger.event("navigation", "index_subtab_switched", mapOf("subtab" to subTabName))
    }

    LaunchedEffect(state.sync.error) {
        state.sync.error?.let {
            snackbarHostState.showSnackbar(it.resolve())
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.songs.songDataMessage) {
        state.songs.songDataMessage?.let {
            snackbarHostState.showSnackbar(it.resolve())
            viewModel.clearSongDataMessage()
        }
    }

    // Home-visible update dialog (Blocker 1 fix)
    // Composed BEFORE early returns so Available state is visible even during preload/loading
    val updateState = state.sync.updateCheckState
    if (updateState is UpdateCheckState.Available) {
        UpdateResultDialog(
            version = updateState.version,
            body = updateState.body,
            htmlUrl = updateState.htmlUrl,
            onDismiss = { viewModel.dismissUpdateResult() },
            onDownload = { uriHandler ->
                viewModel.dismissUpdateResult()
                uriHandler.openUri(updateState.htmlUrl)
            }
        )
    }

    if (state.songs.showPreloadDialog) {
        IllustrationPreloadDialog(
            isPreloading = state.songs.isPreloading,
            progress = state.songs.preloadProgress,
            completed = state.songs.preloadCompleted,
            total = state.songs.preloadTotal,
            currentSong = state.songs.preloadCurrentSong,
            onStartDownload = { viewModel.startPreloadIllustrations() },
            onMinimize = { viewModel.minimizePreload() },
            onCancelDownload = { viewModel.cancelPreload() },
            onDismiss = { viewModel.dismissPreload() }
        )
    }

    // B30 image export snapshots the chart-tag analysis from the current
    // state, so a transient fetch failure (the error persists until a retry
    // succeeds) would silently drop the tag cards from the image. Gate the
    // export on the analysis being settled: retry once when a previous
    // attempt failed, wait for an in-flight attempt, and export anyway on
    // timeout so the button never dead-locks.
    var awaitTagAnalysisForExport by remember { mutableStateOf(false) }

    fun buildExportPayload(current: HomeUiState) = B30ExportPayload(
        b30 = current.b30.b30,
        displayRks = current.b30.displayRks,
        nickname = current.profile.nickname,
        challengeModeRank = current.profile.challengeModeRank,
        moneyString = current.profile.moneyString,
        clearCounts = current.profile.clearCounts,
        fcCount = current.profile.fcCount,
        phiCount = current.profile.phiCount,
        avatarUri = current.profile.avatarUri,
        showB30Overflow = current.b30.showB30Overflow,
        overflowCount = current.b30.overflowCount,
        themeSettings = current.b30.themeSettings,
        tagAnalysis = current.b30.tagAnalysis.analysis
    )

    LaunchedEffect(awaitTagAnalysisForExport) {
        if (!awaitTagAnalysisForExport) return@LaunchedEffect
        // Read the ViewModel flow directly; the delegated `state` above is
        // stale inside the effect body.
        withTimeoutOrNull(B30_TAG_ANALYSIS_SETTLE_TIMEOUT_MS) {
            viewModel.uiState.first { !it.b30.tagAnalysis.isLoading }
        }
        awaitTagAnalysisForExport = false
        onNavigateToB30Image(buildExportPayload(viewModel.uiState.value))
    }

    val generateB30Image = {
        val tagState = state.b30.tagAnalysis
        when {
            tagState.analysis != null || state.b30.b30.isEmpty() ->
                onNavigateToB30Image(buildExportPayload(state))
            // A fetch is already running: wait for it instead of duplicating.
            tagState.isLoading -> awaitTagAnalysisForExport = true
            // A previous attempt failed: retry once and export once the
            // retry settles (success or failure).
            else -> {
                viewModel.retryTagAnalysis()
                awaitTagAnalysisForExport = true
            }
        }
    }

    // B30 header collapse state: user intent is saveable UI state (never the
    // ViewModel); scrolling away from the top auto-collapses, returning to the
    // top re-expands only when the user did not explicitly collapse
    val b30ListState = rememberLazyListState()
    val b30GridState = rememberLazyGridState()
    val b30UseGrid = windowSizeClass.isAtLeastMedium()
    var b30UserCollapsed by rememberSaveable { mutableStateOf(false) }
    var b30UserExpanded by rememberSaveable { mutableStateOf(false) }
    val b30AtTop by remember(b30UseGrid) {
        derivedStateOf {
            if (b30UseGrid) {
                b30GridState.firstVisibleItemIndex == 0 &&
                    b30GridState.firstVisibleItemScrollOffset < 48
            } else {
                b30ListState.firstVisibleItemIndex == 0 &&
                    b30ListState.firstVisibleItemScrollOffset < 48
            }
        }
    }
    LaunchedEffect(b30AtTop) {
        if (!b30AtTop) b30UserExpanded = false
    }
    val b30HeaderExpanded = !b30UserCollapsed && (b30AtTop || b30UserExpanded)
    val toggleB30Header = {
        if (b30HeaderExpanded) {
            b30UserCollapsed = true
            b30UserExpanded = false
        } else {
            b30UserCollapsed = false
            b30UserExpanded = true
        }
    }

    // The two Index sub-tabs keep independent list/grid scroll state so
    // switching between them restores each position. The header shrinks while
    // the active list is scrolled away from the top; search itself lives in
    // the FAB-opened panel below.
    val songsListState = rememberLazyListState()
    val songsGridState = rememberLazyGridState()
    val songsUseGrid = windowSizeClass.isAtLeastMedium()
    val songsAtTop by remember(songsUseGrid) {
        derivedStateOf {
            if (songsUseGrid) {
                songsGridState.firstVisibleItemIndex == 0 &&
                    songsGridState.firstVisibleItemScrollOffset < 48
            } else {
                songsListState.firstVisibleItemIndex == 0 &&
                    songsListState.firstVisibleItemScrollOffset < 48
            }
        }
    }

    // Collectibles idle mode renders the poster grid (always the grid state);
    // its flat results list follows the songs sub-tab's list/grid split, so
    // the at-top probe must read whichever state is actually driving content.
    val collectiblesBrowsingActive = collectiblesState.isBrowsingActive()
    val collectiblesListState = rememberLazyListState()
    val collectiblesGridState = rememberLazyGridState()
    // The drilled chapter list lives inside the pager page and would lose its
    // scroll state when the offscreen page is disposed. Keep one state per
    // chapter so re-entering the same chapter restores its position while a
    // different chapter still starts at the top.
    val collectiblesChapterListStates = remember { mutableMapOf<String, LazyListState>() }
    val collectiblesAtTop by remember(songsUseGrid, collectiblesBrowsingActive) {
        derivedStateOf {
            if (!collectiblesBrowsingActive || songsUseGrid) {
                collectiblesGridState.firstVisibleItemIndex == 0 &&
                    collectiblesGridState.firstVisibleItemScrollOffset < 48
            } else {
                collectiblesListState.firstVisibleItemIndex == 0 &&
                    collectiblesListState.firstVisibleItemScrollOffset < 48
            }
        }
    }
    val indexShowsCollectibles = indexSubTabState.selected == IndexSubTab.Collectibles

    // The two Index sub-tabs live in a pager. IndexSubTabState stays the
    // single selection source: a tab click updates it and the effect below
    // animates the pager; a drag syncs back only once it settles, so passing
    // through the midpoint reports one switch instead of a stream of them.
    val indexPagerState = rememberPagerState(initialPage = indexSubTabState.selected.ordinal) {
        IndexSubTab.entries.size
    }
    LaunchedEffect(indexPagerState, indexSubTabState) {
        snapshotFlow { indexPagerState.settledPage }.collect { page ->
            val target = IndexSubTab.entries.getOrNull(page) ?: return@collect
            if (indexSubTabState.selected != target) {
                indexSubTabState.select(target)
            }
        }
    }
    LaunchedEffect(indexSubTabState.selected, indexPagerState) {
        val target = indexSubTabState.selected.ordinal
        // targetPage covers both a click on a distant tab and a quick
        // re-click that must cancel an in-flight scroll back to this page.
        if (indexPagerState.targetPage != target) {
            indexPagerState.animateScrollToPage(target)
        }
    }

    // Profile and tools only shrink their top bar titles when scrolled away
    // from the top, no further header changes
    val profileScrollState = rememberScrollState()
    val toolsScrollState = rememberScrollState()
    val profileAtTop by remember { derivedStateOf { profileScrollState.value < 48 } }
    val toolsAtTop by remember { derivedStateOf { toolsScrollState.value < 48 } }
    val songsActiveFilterCount = remember(
        state.songs.selectedChapters,
        state.songs.selectedDifficulty,
        state.songs.minLevel,
        state.songs.maxLevel,
        state.songs.levelMinBound,
        state.songs.levelMaxBound
    ) {
        var count = 0
        if (state.songs.selectedChapters.isNotEmpty()) count += state.songs.selectedChapters.size
        if (state.songs.selectedDifficulty != null) count++
        if (state.songs.minLevel > state.songs.levelMinBound || state.songs.maxLevel < state.songs.levelMaxBound) count++
        count
    }
    val collectiblesActiveFilterCount = remember(
        collectiblesState.selectedChapters,
        collectiblesState.selectedCategories
    ) {
        var count = 0
        if (collectiblesState.selectedChapters.isNotEmpty()) count += collectiblesState.selectedChapters.size
        if (collectiblesState.selectedCategories.isNotEmpty()) count++
        count
    }

    HomeGlassScaffold(
        snackbarHostState = snackbarHostState,
        navMode = navMode,
        topBar = { hazeState, glassStyle ->
            val indexAtTop = if (indexShowsCollectibles) collectiblesAtTop else songsAtTop
            GlassTopBar(hazeState = hazeState, style = glassStyle) {
                when (selectedTab) {
                    HomeTab.B30 -> B30Header(
                        state = state.b30,
                        tip = tip,
                        expanded = b30HeaderExpanded,
                        onToggle = toggleB30Header,
                        onGenerateImage = generateB30Image
                    )
                    HomeTab.Index -> IndexHeader(
                        countText = if (indexShowsCollectibles) {
                            stringResource(Res.string.collectibles_all_items_count, collectiblesState.filteredItems.size)
                        } else {
                            stringResource(Res.string.songs_all_songs_count, state.songs.filteredSongs.size)
                        },
                        tip = tip,
                        compact = !indexAtTop,
                        tabRow = {
                            // Transparent container: the row sits on the glass
                            // header, so the blurred content shows through. No
                            // divider either — a hard bottom line would cut the
                            // glass header off from the content below; the
                            // indicator floats directly on the glass.
                            PrimaryTabRow(
                                selectedTabIndex = indexSubTabState.selected.ordinal,
                                containerColor = Color.Transparent,
                                modifier = Modifier.fillMaxWidth(),
                                indicator = {
                                    ElasticPagerPrimaryIndicator(pagerState = indexPagerState)
                                },
                                divider = {}
                            ) {
                                Tab(
                                    selected = !indexShowsCollectibles,
                                    onClick = { indexSubTabState.select(IndexSubTab.Songs) },
                                    text = { Text(stringResource(Res.string.collectibles_subtab_songs)) },
                                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = null) }
                                )
                                Tab(
                                    selected = indexShowsCollectibles,
                                    onClick = { indexSubTabState.select(IndexSubTab.Collectibles) },
                                    text = { Text(stringResource(Res.string.collectibles_subtab_collectibles)) },
                                    icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) }
                                )
                            }
                        }
                    )
                    HomeTab.Profile -> HomeGlassTopBar(
                        spec = HomeHeaderSpec(
                            title = stringResource(Res.string.home_tab_profile),
                            tip = tip,
                            compact = !profileAtTop,
                            actions = {
                                if (state.sync.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(onClick = { viewModel.refresh() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.action_sync))
                                    }
                                }
                                IconButton(onClick = onNavigateToSettings) {
                                    Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.action_settings))
                                }
                            }
                        )
                    )
                    HomeTab.Tools -> HomeGlassTopBar(
                        spec = HomeHeaderSpec(title = stringResource(Res.string.home_tab_tools), tip = tip, compact = !toolsAtTop)
                    )
                }
            }
        },
        navigationBar = { hazeState, glassStyle ->
            when (navMode) {
                HomeNavMode.Rail -> MainNavigationRail(
                    navItems = navItems,
                    selectedTab = selectedTab,
                    avatarUri = state.profile.avatarUri,
                    onAvatarClick = {
                        if (state.profile.avatarUri == null) {
                            launchAvatarPicker()
                        } else {
                            tabState.select(HomeTab.Profile)
                        }
                    },
                    reducedMotionEnabled = reducedMotionEnabled,
                    onTabSelected = tabState::select
                )
                else -> GlassBottomBar(hazeState = hazeState, style = glassStyle) {
                    MainBottomBar(
                        navItems = navItems,
                        selectedTab = selectedTab,
                        reducedMotionEnabled = reducedMotionEnabled,
                        onTabSelected = tabState::select
                    )
                }
            }
        }
    ) { contentPadding ->
        when (selectedTab) {
            HomeTab.Profile -> {
                val pullRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.sync.isSyncing,
                    onRefresh = { viewModel.refresh() },
                    state = pullRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        // The tab content draws full-bleed behind the glass top
                        // bar, so push the spinner below the bar's bottom edge
                        // to keep it out of the blurred area
                        PullToRefreshDefaults.Indicator(
                            state = pullRefreshState,
                            isRefreshing = state.sync.isSyncing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = contentPadding.calculateTopPadding())
                        )
                    }
                ) {
                    ProfileTab(
                        state = state.profile,
                        displayRks = state.b30.displayRks,
                        isLoggedIn = state.tools.sessionToken != null,
                        onNavigateToLogin = onNavigateToLogin,
                        onAvatarSelected = { viewModel.setAvatarUri(it) },
                        onSongClick = { songId, difficulty ->
                            if (difficulty != null) {
                                onNavigateToSongDetailWithDifficulty(songId, difficulty)
                            } else {
                                onNavigateToSongDetail(songId)
                            }
                        },
                        getIllustrationUrl = { viewModel.getLowIllustrationUrl(it) },
                        histogram = state.b30.histogram,
                        gameUpdateInfo = state.sync.gameUpdateInfo,
                        showPreloadCard = state.songs.shouldShowPreloadCard(),
                        preloadCompleted = state.songs.preloadCompleted,
                        preloadTotal = state.songs.preloadTotal,
                        preloadProgress = state.songs.preloadProgress,
                        preloadCurrentSong = state.songs.preloadCurrentSong,
                        onPreloadCardClick = { viewModel.reopenPreloadDialog() },
                        contentPadding = contentPadding,
                        scrollState = profileScrollState
                    )
                }
            }
            HomeTab.B30 -> B30Tab(
                state = state.b30,
                nickname = state.profile.nickname,
                challengeModeRank = state.profile.challengeModeRank,
                getIllustrationUrl = { viewModel.getLowIllustrationUrl(it) },
                onSongClick = { songId, difficulty ->
                    if (difficulty != null) {
                        onNavigateToSongDetailWithDifficulty(songId, difficulty)
                    } else {
                        onNavigateToSongDetail(songId)
                    }
                },
                contentPadding = contentPadding,
                listState = b30ListState,
                useGrid = b30UseGrid,
                gridState = b30GridState,
                onRetryTagAnalysis = { viewModel.retryTagAnalysis() }
            )
            HomeTab.Index -> {
                // The sub-tabs live in the header's slot; the lists consume
                // the scaffold padding exactly like the other tabs. Search is
                // a FAB-opened layer inside this content area (the left pane
                // on the two-pane layout), so it never covers the detail pane.
                var indexSearchExpanded by rememberSaveable { mutableStateOf(false) }
                // The panel samples a tab-local source placed on the Index
                // pager, not the home scaffold's shared HazeState: that source
                // is an ancestor of this panel, and Haze forbids an effect from
                // drawing an ancestor source. The pager is a sibling of the
                // panel, so the panel can sample it safely.
                val searchHazeState = rememberHazeState()
                val focusManager = LocalFocusManager.current
                val indexSubTabName = if (indexShowsCollectibles) "collectibles" else "songs"
                val indexSearchQuery = if (indexShowsCollectibles) {
                    collectiblesState.searchQuery
                } else {
                    state.songs.searchQuery
                }
                // The panel floats above the list rather than occupying layout
                // space: opening it must not shift the list content, and the
                // top items being covered while it is open is intentional.
                val indexContentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    // Keep the last item clear of the FAB (it is hidden while
                    // the panel is open; the extra room is harmless then).
                    bottom = contentPadding.calculateBottomPadding() + 72.dp
                )
                val closeIndexSearch = {
                    // Drop focus before removing the field so the IME hides.
                    focusManager.clearFocus()
                    indexSearchExpanded = false
                    AppLogger.event(
                        "navigation",
                        "index_search_closed",
                        mapOf("subtab" to indexSubTabName)
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = indexPagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(state = searchHazeState)
                    ) { page ->
                        when (IndexSubTab.entries[page]) {
                            IndexSubTab.Songs -> {
                                val pullRefreshState = rememberPullToRefreshState()
                                PullToRefreshBox(
                                    isRefreshing = state.songs.isSongDataRefreshing,
                                    onRefresh = { viewModel.refreshSongData() },
                                    state = pullRefreshState,
                                    modifier = Modifier.fillMaxSize(),
                                    indicator = {
                                        PullToRefreshDefaults.Indicator(
                                            state = pullRefreshState,
                                            isRefreshing = state.songs.isSongDataRefreshing,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = indexContentPadding.calculateTopPadding())
                                        )
                                    }
                                ) {
                                    SongsTab(
                                        state = state.songs,
                                        onSearchChange = { viewModel.searchSongs(it) },
                                        onToggleChapter = { viewModel.toggleChapter(it) },
                                        onClearChapters = { viewModel.resetFilters() },
                                        onDifficultySelect = { viewModel.filterByDifficulty(it) },
                                        onLevelRangeSelect = { min, max -> viewModel.filterByLevelRange(min, max) },
                                        onToggleFilterSheet = { viewModel.toggleFilterSheet(it) },
                                        onResetFilters = { viewModel.resetFilters() },
                                        onRefreshSongData = { viewModel.refreshSongData() },
                                        getIllustrationUrl = { viewModel.getLowIllustrationUrl(it) },
                                        onSongClick = { songId, _ -> onNavigateToSongDetail(songId) },
                                        contentPadding = indexContentPadding,
                                        listState = songsListState,
                                        useGrid = songsUseGrid,
                                        gridState = songsGridState
                                    )
                                    // The progress card floats below the glass top bar instead of
                                    // scrolling with the list, so the pull gesture collapsing the
                                    // header can never slide it under the blurred area.
                                    val songDataStatusText = state.songs.songDataStatusText
                                    if (state.songs.isSongDataRefreshing && songDataStatusText != null) {
                                        SongDataRefreshProgressCard(
                                            statusText = songDataStatusText.asString(),
                                            progressFraction = state.songs.songDataProgressFraction,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(
                                                    start = 16.dp,
                                                    end = 16.dp,
                                                    top = indexContentPadding.calculateTopPadding() + 8.dp
                                                )
                                        )
                                    }
                                }
                            }
                            IndexSubTab.Collectibles -> {
                                val pullRefreshState = rememberPullToRefreshState()
                                PullToRefreshBox(
                                    isRefreshing = state.songs.isSongDataRefreshing,
                                    onRefresh = { viewModel.refreshSongData() },
                                    state = pullRefreshState,
                                    modifier = Modifier.fillMaxSize(),
                                    indicator = {
                                        PullToRefreshDefaults.Indicator(
                                            state = pullRefreshState,
                                            isRefreshing = state.songs.isSongDataRefreshing,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = indexContentPadding.calculateTopPadding())
                                        )
                                    }
                                ) {
                                    CollectiblesTab(
                                        state = collectiblesState,
                                        updateAvailable = state.songs.songDataUpdateAvailable,
                                        onToggleChapter = { collectiblesViewModel.toggleChapter(it) },
                                        onToggleCategory = { collectiblesViewModel.toggleCategory(it) },
                                        onClearChapters = { collectiblesViewModel.resetFilters() },
                                        onToggleFilterSheet = { collectiblesViewModel.toggleFilterSheet(it) },
                                        onResetFilters = { collectiblesViewModel.resetFilters() },
                                        onUpdateNow = { viewModel.refreshSongData() },
                                        getCoverUrl = collectiblesViewModel::getCoverUrl,
                                        getPosterUrl = collectiblesViewModel::getPosterCoverUrl,
                                        drilledChapterKey = collectiblesChapterState.folderKey,
                                        splitChapterPane = isInExpandedPane,
                                        onBackFromChapter = {
                                            AppLogger.event(
                                                "navigation",
                                                "collectible_chapter_closed",
                                                mapOf("folder" to (collectiblesChapterState.folderKey ?: ""))
                                            )
                                            collectiblesChapterState.close()
                                        },
                                        selectedItem = if (isInExpandedPane) selectedCollectibleItem else null,
                                        onChapterClick = { chapter ->
                                            // Level 2 drills down in place on compact; on
                                            // expanded it splits the left pane. Tapping the
                                            // already-open poster collapses the split.
                                            val alreadyOpen = collectiblesChapterState.folderKey == chapter.folderKey
                                            if (alreadyOpen) {
                                                AppLogger.event(
                                                    "navigation",
                                                    "collectible_chapter_closed",
                                                    mapOf("folder" to chapter.folderKey)
                                                )
                                                collectiblesChapterState.close()
                                            } else {
                                                AppLogger.event(
                                                    "navigation",
                                                    "collectible_chapter_opened",
                                                    mapOf("folder" to chapter.folderKey)
                                                )
                                                collectiblesChapterState.open(chapter.folderKey)
                                            }
                                        },
                                        onItemClick = { item ->
                                            // Result-item taps also record the chapter,
                                            // so leaving search lands on its inline
                                            // chapter list on both layouts.
                                            collectiblesChapterState.open(item.chapterFolderKey)
                                            onNavigateToCollectibleDetail(item.chapterFolderKey, item.key)
                                        },
                                        contentPadding = indexContentPadding,
                                        listState = collectiblesListState,
                                        useGrid = songsUseGrid,
                                        gridState = collectiblesGridState,
                                        getChapterListState = { chapterKey ->
                                            collectiblesChapterListStates.getOrPut(chapterKey) {
                                                LazyListState()
                                            }
                                        }
                                    )
                                    // The combined index (songs + collectibles)
                                    // refresh reports through the songs state on
                                    // both sub-tabs.
                                    val songDataStatusText = state.songs.songDataStatusText
                                    if (state.songs.isSongDataRefreshing && songDataStatusText != null) {
                                        SongDataRefreshProgressCard(
                                            statusText = songDataStatusText.asString(),
                                            progressFraction = state.songs.songDataProgressFraction,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(
                                                    start = 16.dp,
                                                    end = 16.dp,
                                                    top = indexContentPadding.calculateTopPadding() + 8.dp
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = indexSearchExpanded,
                        enter = if (reducedMotionEnabled) {
                            EnterTransition.None
                        } else {
                            fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleIn(
                                    initialScale = 0.85f,
                                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                        },
                        exit = if (reducedMotionEnabled) {
                            ExitTransition.None
                        } else {
                            fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleOut(
                                    targetScale = 0.85f,
                                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = contentPadding.calculateTopPadding() + 8.dp
                            )
                    ) {
                        IndexSearchPanel(
                            hazeState = searchHazeState,
                            query = indexSearchQuery,
                            placeholder = if (indexShowsCollectibles) {
                                stringResource(Res.string.collectibles_search_placeholder)
                            } else {
                                stringResource(Res.string.songs_search_placeholder)
                            },
                            activeFilterCount = if (indexShowsCollectibles) {
                                collectiblesActiveFilterCount
                            } else {
                                songsActiveFilterCount
                            },
                            onQueryChange = {
                                if (indexShowsCollectibles) {
                                    collectiblesViewModel.search(it)
                                } else {
                                    viewModel.searchSongs(it)
                                }
                            },
                            onOpenFilter = {
                                if (indexShowsCollectibles) {
                                    collectiblesViewModel.toggleFilterSheet(true)
                                } else {
                                    viewModel.toggleFilterSheet(true)
                                }
                            },
                            onClose = closeIndexSearch
                        )
                    }
                    if (!indexSearchExpanded) {
                        IndexSearchFab(
                            active = indexSearchQuery.isNotEmpty(),
                            onClick = {
                                indexSearchExpanded = true
                                AppLogger.event(
                                    "navigation",
                                    "index_search_opened",
                                    mapOf("subtab" to indexSubTabName)
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = 16.dp,
                                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                                )
                        )
                    }
                }
                // Composed after the pager/chapter BackHandler so an open
                // search panel consumes back first.
                BackHandler(enabled = indexSearchExpanded) { closeIndexSearch() }
            }
            HomeTab.Tools -> ToolsTab(
                state = state.tools,
                defaultRks = state.b30.displayRks,
                onNavigateToSuggest = onNavigateToSuggest,
                onNavigateToLogin = onNavigateToLogin,
                onFetchRankByUser = { viewModel.fetchApiRankByUser() },
                onFetchRankByPosition = { viewModel.fetchApiRankByPosition(it) },
                onFetchRksRank = { viewModel.fetchApiRksRankForValue(it) },
                contentPadding = contentPadding,
                scrollState = toolsScrollState
            )
        }
    }
}

@Composable
private fun IllustrationPreloadDialog(
    isPreloading: Boolean,
    progress: Float,
    completed: Int,
    total: Int,
    currentSong: String?,
    onStartDownload: () -> Unit,
    onMinimize: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedAlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(Res.string.preload_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isPreloading) {
                    Text(
                        text = stringResource(Res.string.preload_dialog_downloading, completed, total),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    currentSong?.let { song ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.preload_current_song, song),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.preload_dialog_message),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start
                    )
                }
            }
        },
        confirmButton = {
            if (isPreloading) {
                TextButton(onClick = onMinimize) {
                    Text(stringResource(Res.string.preload_dialog_minimize))
                }
            } else {
                TextButton(onClick = onStartDownload) {
                    Text(stringResource(Res.string.preload_dialog_start))
                }
            }
        },
        dismissButton = {
            if (isPreloading) {
                TextButton(onClick = onCancelDownload) {
                    Text(stringResource(Res.string.preload_dialog_cancel))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.preload_dialog_skip))
                }
            }
        }
    )
}
