package org.kasumi321.ushio.phitracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairEntryPoint
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairFailure
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairSession
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairStage
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairState
import org.kasumi321.ushio.phitracker.data.song.CatalogRepairStatus
import org.koin.compose.koinInject
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.common_cancel
import phitracker.composeapp.generated.resources.data_recovery_checking_message
import phitracker.composeapp.generated.resources.data_recovery_checking_title
import phitracker.composeapp.generated.resources.data_recovery_error_busy_message
import phitracker.composeapp.generated.resources.data_recovery_error_busy_title
import phitracker.composeapp.generated.resources.data_recovery_error_network_message
import phitracker.composeapp.generated.resources.data_recovery_error_network_title
import phitracker.composeapp.generated.resources.data_recovery_error_scene_message
import phitracker.composeapp.generated.resources.data_recovery_error_scene_title
import phitracker.composeapp.generated.resources.data_recovery_error_storage_message
import phitracker.composeapp.generated.resources.data_recovery_error_storage_title
import phitracker.composeapp.generated.resources.data_recovery_error_unknown_message
import phitracker.composeapp.generated.resources.data_recovery_error_unknown_title
import phitracker.composeapp.generated.resources.data_recovery_progress
import phitracker.composeapp.generated.resources.data_recovery_reset
import phitracker.composeapp.generated.resources.data_recovery_resetting_message
import phitracker.composeapp.generated.resources.data_recovery_resetting_title
import phitracker.composeapp.generated.resources.data_recovery_retry
import phitracker.composeapp.generated.resources.data_recovery_use_bundled
import phitracker.composeapp.generated.resources.data_recovery_use_bundled_confirm
import phitracker.composeapp.generated.resources.data_recovery_use_bundled_dialog_text
import phitracker.composeapp.generated.resources.data_recovery_use_bundled_dialog_title

/**
 * Blocks the navigation graph while the catalog update engine reports a
 * blocking repair (a retained scene, a reset marker without a backup, or a
 * download that still owes data). The gate renders only the app-scoped
 * [CatalogRepairSession] state, so an Activity/ViewModel teardown never cancels
 * the download; leaving and returning restores the live progress.
 *
 * A marker without a backup is NOT treated as ready: the session must complete
 * a full download or the user must explicitly choose the bundled read-only
 * fallback before navigation is rendered.
 */
@Composable
fun DataRecoveryGate(content: @Composable () -> Unit) {
    val session: CatalogRepairSession = koinInject()
    LaunchedEffect(session) { session.ensureStartupRepair() }
    val state by session.state.collectAsState()
    val contentState = rememberSaveableStateHolder()

    if (!state.blocking) {
        // The graph must leave composition while files are untrusted, but its
        // saveable navigation/page state survives a failed reset and restoration.
        contentState.SaveableStateProvider("catalog-navigation") { content() }
        return
    }

    var showFallbackDialog by remember { mutableStateOf(false) }
    DataRecoveryScreen(
        state = state,
        onRetry = { session.retry() },
        onReset = { session.startResetAndRedownload(CatalogRepairEntryPoint.Recovery) },
        onUseBundled = { showFallbackDialog = true }
    )
    if (showFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showFallbackDialog = false },
            title = { Text(stringResource(Res.string.data_recovery_use_bundled_dialog_title)) },
            text = { Text(stringResource(Res.string.data_recovery_use_bundled_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFallbackDialog = false
                        session.useBundledFallback()
                    }
                ) { Text(stringResource(Res.string.data_recovery_use_bundled_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFallbackDialog = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun DataRecoveryScreen(
    state: CatalogRepairState,
    onRetry: () -> Unit,
    onReset: () -> Unit,
    onUseBundled: () -> Unit
) {
    val failed = state.status == CatalogRepairStatus.Failed
    val failure = state.failure
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!failed) {
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(
                    when {
                        failed -> failure.failureTitle()
                        state.stage == CatalogRepairStage.Checking -> Res.string.data_recovery_checking_title
                        else -> Res.string.data_recovery_resetting_title
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    when {
                        failed -> failure.failureMessage()
                        state.stage == CatalogRepairStage.Checking -> Res.string.data_recovery_checking_message
                        else -> Res.string.data_recovery_resetting_message
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!failed && state.total > 0) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { state.completed.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        Res.string.data_recovery_progress,
                        state.completed,
                        state.total
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (failed || state.status == CatalogRepairStatus.Running) {
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (failed) {
                        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.data_recovery_retry))
                        }
                        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.data_recovery_reset))
                        }
                    }
                    TextButton(onClick = onUseBundled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.data_recovery_use_bundled))
                    }
                }
            }
        }
    }
}

private fun CatalogRepairFailure?.failureTitle() = when (this) {
    CatalogRepairFailure.Network -> Res.string.data_recovery_error_network_title
    CatalogRepairFailure.Storage -> Res.string.data_recovery_error_storage_title
    CatalogRepairFailure.Scene -> Res.string.data_recovery_error_scene_title
    CatalogRepairFailure.Busy -> Res.string.data_recovery_error_busy_title
    CatalogRepairFailure.Unknown, null -> Res.string.data_recovery_error_unknown_title
}

private fun CatalogRepairFailure?.failureMessage() = when (this) {
    CatalogRepairFailure.Network -> Res.string.data_recovery_error_network_message
    CatalogRepairFailure.Storage -> Res.string.data_recovery_error_storage_message
    CatalogRepairFailure.Scene -> Res.string.data_recovery_error_scene_message
    CatalogRepairFailure.Busy -> Res.string.data_recovery_error_busy_message
    CatalogRepairFailure.Unknown, null -> Res.string.data_recovery_error_unknown_message
}
