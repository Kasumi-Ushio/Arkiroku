package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.ui.glass.ExpandableGlassSection
import org.kasumi321.ushio.phitracker.ui.glass.rememberGlassHazeStyle
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.index_search_close
import phitracker.composeapp.generated.resources.songs_cd_clear_search
import phitracker.composeapp.generated.resources.songs_cd_expand_search
import phitracker.composeapp.generated.resources.songs_cd_filter

/**
 * Header of the Index tab: title (result count), marquee tip and the optional
 * sub-tab row. Search and filter moved out of the header into the FAB-opened
 * [IndexSearchPanel], so the header only keeps the collapsing title animation
 * — the title shrinks while the active list is scrolled away from the top.
 *
 * [tabRow] stays composed in both header states, keeping the sub-tabs
 * reachable while the list is scrolled.
 */
@Composable
fun IndexHeader(
    countText: String,
    tip: String,
    compact: Boolean,
    tabRow: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val expansion by animateFloatAsState(
        targetValue = if (compact) 0f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "indexHeaderExpansion"
    )
    val titleStyle = MaterialTheme.typography.titleLarge
    val titleFontSize = lerp(16f, 22f, expansion)
    val titleLineHeight = lerp(24f, 28f, expansion)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title slot mirrors the other tabs' headers: the tip hugs the
            // title instead of sitting below the action row
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = countText,
                    style = titleStyle,
                    fontSize = titleFontSize.sp,
                    lineHeight = titleLineHeight.sp
                )
                ExpandableGlassSection(expanded = tip.isNotBlank()) {
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )
                }
            }
        }

        if (tabRow != null) {
            Spacer(modifier = Modifier.height(8.dp))
            tabRow()
        }

        Spacer(modifier = Modifier.height(lerp(4f, 12f, expansion).dp))
    }
}

/**
 * Floating search entry for the Index tab. The FAB keeps the Index content
 * area clean (no permanent search field) and opens [IndexSearchPanel] on tap;
 * once a query is active it switches to the primary color so the filtered
 * state stays visible after the panel closes.
 */
@Composable
internal fun IndexSearchFab(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = stringResource(Res.string.songs_cd_expand_search)
        )
    }
}

/**
 * Expanded search layer of the Index tab: a rounded glass panel floating below
 * the glass header with the live search field, clear action and the filter
 * entry. It is composed inside the tab content area (the left pane on the
 * two-pane layout), above the list, which keeps filtering live underneath.
 *
 * The panel samples a tab-local [HazeState] whose source is the active list
 * (the home scaffold's shared state cannot be used: it wraps this panel, and
 * Haze forbids an effect from drawing an ancestor source). The list behind the
 * panel is blurred, then a translucent surface color is laid over the blur.
 * The overlay follows the active theme and keeps the field readable over any
 * artwork while still letting the content show through.
 *
 * The caller owns visibility and dismisses it from the back gesture; closing
 * here mirrors the same callback.
 */
@Composable
internal fun IndexSearchPanel(
    hazeState: HazeState,
    query: String,
    placeholder: String,
    activeFilterCount: Int,
    onQueryChange: (String) -> Unit,
    onOpenFilter: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glassStyle = rememberGlassHazeStyle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = glassStyle
            )
            // Extra surface layer on top of the blur (the shared glass style
            // already supplies the main surface-derived tint): it raises text
            // contrast on busy content in both themes without making the panel
            // fully opaque.
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.index_search_close)
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(Res.string.songs_cd_clear_search)
                            )
                        }
                    }
                },
                singleLine = true
            )
            IndexFilterEntry(
                activeFilterCount = activeFilterCount,
                onOpenFilter = onOpenFilter
            )
        }
    }
}

/**
 * Badged filter icon used by the expanded search layer.
 */
@Composable
private fun IndexFilterEntry(
    activeFilterCount: Int,
    onOpenFilter: () -> Unit
) {
    IconButton(onClick = onOpenFilter) {
        BadgedBox(
            badge = {
                if (activeFilterCount > 0) {
                    Badge { Text(activeFilterCount.toString()) }
                }
            }
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = stringResource(Res.string.songs_cd_filter),
                tint = if (activeFilterCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
