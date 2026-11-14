@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class SettingsListEntry(
    val key: String,
    val content: @Composable (ListItemShapes) -> Unit
)

/** Collects visible rows before layout so conditional settings keep correct segmented shapes. */
internal class SettingsSectionScope internal constructor() {
    private val mutableEntries = mutableListOf<SettingsListEntry>()
    internal val entries: List<SettingsListEntry> get() = mutableEntries

    fun actionItem(
        key: String,
        onClick: () -> Unit,
        headlineContent: @Composable () -> Unit,
        supportingContent: @Composable (() -> Unit)? = null,
        leadingContent: @Composable (() -> Unit)? = null,
        trailingContent: @Composable (() -> Unit)? = null,
        enabled: Boolean = true
    ) {
        add(key) { shapes ->
            SegmentedListItem(
                onClick = onClick,
                shapes = shapes,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                supportingContent = supportingContent,
                verticalAlignment = Alignment.CenterVertically,
                colors = settingsSegmentedColors(),
                content = headlineContent
            )
        }
    }

    fun valueItem(
        key: String,
        headline: String,
        value: String,
        onClick: () -> Unit,
        menuContent: @Composable () -> Unit,
        supportingContent: @Composable (() -> Unit)? = null,
        enabled: Boolean = true
    ) {
        add(key) { shapes ->
            SettingsValueItem(
                headline = headline,
                value = value,
                onClick = onClick,
                shapes = shapes,
                menuContent = menuContent,
                supportingContent = supportingContent,
                enabled = enabled
            )
        }
    }

    fun switchItem(
        key: String,
        headlineContent: @Composable () -> Unit,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        supportingContent: @Composable (() -> Unit)? = null,
        leadingContent: @Composable (() -> Unit)? = null,
        enabled: Boolean = true
    ) {
        add(key) { shapes ->
            SettingsSwitchItem(
                headlineContent = headlineContent,
                checked = checked,
                onCheckedChange = onCheckedChange,
                shapes = shapes,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                enabled = enabled
            )
        }
    }

    fun sliderItem(
        key: String,
        headline: String,
        valueText: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        enabled: Boolean = true,
        onValueChangeFinished: (() -> Unit)? = null
    ) {
        add(key) { shapes ->
            SettingsSliderItem(
                headline = headline,
                valueText = valueText,
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                shapes = shapes,
                enabled = enabled,
                onValueChangeFinished = onValueChangeFinished
            )
        }
    }

    private fun add(key: String, content: @Composable (ListItemShapes) -> Unit) {
        require(key.isNotBlank()) { "Settings item key must not be blank" }
        require(mutableEntries.none { it.key == key }) { "Duplicate settings item key: $key" }
        mutableEntries += SettingsListEntry(key = key, content = content)
    }
}

/**
 * A titled group of segmented settings rows. Registration is intentionally not
 * composable; resolve direct resource values in the caller and keep composable
 * work in row slots so derived collections stay safe across local recomposition.
 */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: SettingsSectionScope.() -> Unit
) {
    // Mirror Material ButtonGroup: derive a fresh item collection from the latest
    // content instead of appending again when only the content lambda recomposes.
    val latestContent = rememberUpdatedState(content)
    val scope = remember {
        derivedStateOf {
            SettingsSectionScope().apply(latestContent.value)
        }
    }.value
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            scope.entries.forEachIndexed { index, entry ->
                key(entry.key) {
                    entry.content(
                        ListItemDefaults.segmentedShapes(
                            index = index,
                            count = scope.entries.size
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsValueItem(
    headline: String,
    value: String,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    menuContent: @Composable () -> Unit,
    supportingContent: @Composable (() -> Unit)?,
    enabled: Boolean
) {
    // Keep the current value readable instead of squeezing the headline at large font scales.
    val valueBelow = LocalDensity.current.fontScale > 1.3f
    val effectiveSupportingContent: @Composable (() -> Unit)? =
        if (valueBelow || supportingContent != null) {
            {
                Column {
                    if (valueBelow) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    supportingContent?.invoke()
                }
            }
        } else {
            null
        }

    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        supportingContent = effectiveSupportingContent,
        verticalAlignment = Alignment.CenterVertically,
        trailingContent = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!valueBelow) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                menuContent()
            }
        },
        colors = settingsSegmentedColors()
    ) {
        Text(headline)
    }
}

@Composable
private fun SettingsSwitchItem(
    headlineContent: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    supportingContent: @Composable (() -> Unit)?,
    leadingContent: @Composable (() -> Unit)?,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                Box(modifier = Modifier.padding(end = 16.dp)) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        content = leadingContent
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                    headlineContent()
                }
                supportingContent?.let {
                    ProvideTextStyle(
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        it()
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun SettingsSliderItem(
    headline: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    shapes: ListItemShapes,
    enabled: Boolean,
    onValueChangeFinished: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = headline, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun SettingsMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = if (selected) {
            {
                Icon(Icons.Filled.Check, contentDescription = null)
            }
        } else {
            null
        },
        onClick = onClick
    )
}

@Composable
private fun settingsSegmentedColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
)
