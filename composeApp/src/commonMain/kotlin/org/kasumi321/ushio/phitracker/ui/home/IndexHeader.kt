package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.ui.glass.ExpandableGlassSection
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.songs_cd_clear_search
import phitracker.composeapp.generated.resources.songs_cd_filter

/**
 * Header of the Index tab: title (result count), marquee tip and the optional
 * sub-tab row. Search and filter live in the FAB-opened bottom sheet, so the
 * header only keeps the collapsing title animation
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

/** Opens the combined search and filter sheet for the active Index sub-tab. */
@Composable
internal fun IndexSearchFab(
    activeFilterCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = activeFilterCount > 0
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
        BadgedBox(
            badge = {
                if (active) Badge { Text(activeFilterCount.toString()) }
            }
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = stringResource(Res.string.songs_cd_filter)
            )
        }
    }
}

@Composable
internal fun IndexSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
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
}
