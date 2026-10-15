package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.action_settings
import phitracker.composeapp.generated.resources.action_sync
import phitracker.composeapp.generated.resources.home_tab_profile
import phitracker.composeapp.generated.resources.home_tab_songs
import phitracker.composeapp.generated.resources.home_tab_tools
import phitracker.composeapp.generated.resources.preload_dialog_downloading
import phitracker.composeapp.generated.resources.preload_dialog_message
import phitracker.composeapp.generated.resources.preload_dialog_skip
import phitracker.composeapp.generated.resources.preload_dialog_start
import phitracker.composeapp.generated.resources.preload_dialog_title
import phitracker.composeapp.generated.resources.profile_avatar
import phitracker.composeapp.generated.resources.profile_set_avatar
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.rememberAvatarPicker
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.b30.B30ExportPayload
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog
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
    Songs,
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

/** Circular avatar pinned to the top of the rail; taps open the avatar picker. */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToB30Image: (B30ExportPayload) -> Unit,
    onNavigateToSongDetail: (String) -> Unit,
    onNavigateToSongDetailWithDifficulty: (String, org.kasumi321.ushio.phitracker.domain.model.Difficulty?) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSuggest: () -> Unit,
    onNavigateToLogin: () -> Unit,
    tabState: HomeTabState,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedTab = tabState.selected
    val tip = remember(selectedTab) { viewModel.getRandomTip() }
    val reducedMotionEnabled = rememberReducedMotionEnabled()
    val windowSizeClass = rememberWindowSizeClass()
    val navMode = if (windowSizeClass.isAtLeastMedium()) HomeNavMode.Rail else HomeNavMode.BottomBar

    val navItems = listOf(
        BottomNavItem(HomeTab.Profile, stringResource(Res.string.home_tab_profile), Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(HomeTab.B30, "B30", Icons.Filled.Star, Icons.Outlined.StarBorder),
        BottomNavItem(HomeTab.Songs, stringResource(Res.string.home_tab_songs), Icons.Filled.MusicNote, Icons.Outlined.MusicNote),
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
            HomeTab.Songs -> "songs"
            HomeTab.Tools -> "tools"
        }
        AppLogger.event("navigation", "tab_switched", mapOf("tab" to tabName))
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
            onStartDownload = { viewModel.startPreloadIllustrations() },
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

    // Songs header compacts on scroll into a title row with a search icon on
    // the top right; tapping the icon reopens the full search field until the
    // list returns to the top
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
    var songsSearchOpen by remember { mutableStateOf(false) }
    // Any scroll while the reopened search field is visible collapses it again
    val songsScrollInProgress =
        if (songsUseGrid) songsGridState.isScrollInProgress else songsListState.isScrollInProgress
    LaunchedEffect(songsScrollInProgress) {
        if (songsScrollInProgress) songsSearchOpen = false
    }
    // Armed only by the search icon: an icon-triggered reopening focuses the
    // field (raising the IME), while automatic re-expansion when the list
    // returns to the top must not pop the keyboard
    var songsFocusOnExpand by remember { mutableStateOf(false) }

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

    HomeGlassScaffold(
        snackbarHostState = snackbarHostState,
        navMode = navMode,
        topBar = { hazeState, glassStyle ->
            // Keep the bottom edge blurred while the songs search field is
            // expanded, so the field never sits on the faded-out gradient tail
            val songsSearchExpanded = selectedTab == HomeTab.Songs &&
                (songsAtTop || songsSearchOpen)
            GlassTopBar(
                hazeState = hazeState,
                style = glassStyle,
                progressiveEndIntensity = if (songsSearchExpanded) 0.5f else 0f
            ) {
                when (selectedTab) {
                    HomeTab.B30 -> B30Header(
                        state = state.b30,
                        tip = tip,
                        expanded = b30HeaderExpanded,
                        onToggle = toggleB30Header,
                        onGenerateImage = generateB30Image
                    )
                    HomeTab.Songs -> SongsHeader(
                        songCount = state.songs.filteredSongs.size,
                        tip = tip,
                        searchQuery = state.songs.searchQuery,
                        activeFilterCount = songsActiveFilterCount,
                        compact = !songsAtTop && !songsSearchOpen,
                        focusOnExpand = songsFocusOnExpand,
                        onExpandFocusHandled = { songsFocusOnExpand = false },
                        onSearchChange = { viewModel.searchSongs(it) },
                        onSearchExpandRequest = {
                            songsFocusOnExpand = true
                            songsSearchOpen = true
                        },
                        onOpenFilter = { viewModel.toggleFilterSheet(true) }
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
                    onAvatarClick = { launchAvatarPicker() },
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
            HomeTab.Songs -> {
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
                                .padding(top = contentPadding.calculateTopPadding())
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
                        contentPadding = contentPadding,
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
                                    top = contentPadding.calculateTopPadding() + 8.dp
                                )
                        )
                    }
                }
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
    onStartDownload: () -> Unit,
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
            if (!isPreloading) {
                TextButton(onClick = onStartDownload) {
                    Text(stringResource(Res.string.preload_dialog_start))
                }
            }
        },
        dismissButton = {
            if (!isPreloading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.preload_dialog_skip))
                }
            }
        }
    )
}
