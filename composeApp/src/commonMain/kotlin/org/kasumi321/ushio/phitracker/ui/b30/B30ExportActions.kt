@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupMenuState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import org.jetbrains.compose.resources.stringResource
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.b30ex_action_save
import phitracker.composeapp.generated.resources.b30ex_action_share

@Composable
internal fun B30ExportActions(
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val stacked = maxWidth < 420.dp || LocalDensity.current.fontScale > 1.3f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SaveActionButton(onClick = onSave, modifier = Modifier.fillMaxWidth())
                ShareActionButton(onClick = onShare, modifier = Modifier.fillMaxWidth())
            }
        } else {
            ButtonGroup(
                modifier = Modifier.fillMaxWidth(),
                overflowIndicator = { state -> ButtonGroupDefaults.OverflowIndicator(state) }
            ) {
                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        SaveActionButton(
                            onClick = onSave,
                            modifier = Modifier
                                .weight(1f)
                                .animateWidth(interactionSource),
                            shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                            interactionSource = interactionSource
                        )
                    },
                    menuContent = { state -> ActionMenuItem(SaveAction.Save, state, onSave) }
                )
                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        ShareActionButton(
                            onClick = onShare,
                            modifier = Modifier
                                .weight(1f)
                                .animateWidth(interactionSource),
                            shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                            interactionSource = interactionSource
                        )
                    },
                    menuContent = { state -> ActionMenuItem(SaveAction.Share, state, onShare) }
                )
            }
        }
    }
}

private enum class SaveAction { Save, Share }

@Composable
private fun ActionMenuItem(
    action: SaveAction,
    state: ButtonGroupMenuState,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                stringResource(
                    if (action == SaveAction.Save) Res.string.b30ex_action_save
                    else Res.string.b30ex_action_share
                )
            )
        },
        leadingIcon = {
            Icon(
                if (action == SaveAction.Save) Icons.Filled.Save else Icons.Filled.Share,
                contentDescription = null
            )
        },
        onClick = {
            onClick()
            state.dismiss()
        }
    )
}

@Composable
private fun SaveActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    shape: Shape = ButtonDefaults.shape,
    interactionSource: MutableInteractionSource? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(ButtonDefaults.MediumContainerHeight),
        shape = shape,
        interactionSource = interactionSource
    ) {
        Icon(Icons.Filled.Save, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(Res.string.b30ex_action_save), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ShareActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    shape: Shape = ButtonDefaults.shape,
    interactionSource: MutableInteractionSource? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(ButtonDefaults.MediumContainerHeight),
        shape = shape,
        interactionSource = interactionSource
    ) {
        Icon(Icons.Filled.Share, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(Res.string.b30ex_action_share), style = MaterialTheme.typography.labelLarge)
    }
}
