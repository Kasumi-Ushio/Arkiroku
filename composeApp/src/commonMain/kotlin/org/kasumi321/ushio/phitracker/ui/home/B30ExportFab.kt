package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.b30_header_generate_image

@Composable
internal fun B30ExportFab(
    expanded: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExtendedFloatingActionButton(
        text = { Text(stringResource(Res.string.b30_header_generate_image)) },
        icon = {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = stringResource(Res.string.b30_header_generate_image)
                )
            }
        },
        onClick = onClick,
        expanded = expanded,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
}
