package org.kasumi321.ushio.phitracker.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.circle
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.ui.components.ElasticClickPrimaryIndicator
import org.kasumi321.ushio.phitracker.ui.utils.asString
import org.kasumi321.ushio.phitracker.ui.utils.resolve
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.login_app_credit
import phitracker.composeapp.generated.resources.login_cancel
import phitracker.composeapp.generated.resources.login_failed_fallback
import phitracker.composeapp.generated.resources.login_generate_qr
import phitracker.composeapp.generated.resources.login_generating_qr
import phitracker.composeapp.generated.resources.login_login_and_sync
import phitracker.composeapp.generated.resources.login_qr_code_cd
import phitracker.composeapp.generated.resources.login_qr_expired
import phitracker.composeapp.generated.resources.login_qr_hint_idle
import phitracker.composeapp.generated.resources.login_qr_remaining_time
import phitracker.composeapp.generated.resources.login_qr_scanned
import phitracker.composeapp.generated.resources.login_qr_tap_hint
import phitracker.composeapp.generated.resources.login_qr_waiting_scan
import phitracker.composeapp.generated.resources.login_regenerate_qr
import phitracker.composeapp.generated.resources.login_reset_local_session
import phitracker.composeapp.generated.resources.login_reset_local_session_dialog_text
import phitracker.composeapp.generated.resources.login_retry
import phitracker.composeapp.generated.resources.login_security_tip
import phitracker.composeapp.generated.resources.login_select_server
import phitracker.composeapp.generated.resources.login_skip_to_home
import phitracker.composeapp.generated.resources.login_success
import phitracker.composeapp.generated.resources.login_syncing_game_data
import phitracker.composeapp.generated.resources.login_tab_qr
import phitracker.composeapp.generated.resources.login_tab_token
import phitracker.composeapp.generated.resources.login_token_hint
import phitracker.composeapp.generated.resources.login_token_hint_question
import phitracker.composeapp.generated.resources.login_token_tutorial_link

private val QrCodeMaxSize = 280.dp

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onSessionResumed: () -> Unit,
    onSkip: () -> Unit,
    onResetLocalSession: () -> Unit,
    viewModel: LoginViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            if (state.sessionRestored) onSessionResumed() else onLoginSuccess()
        }
    }

    LaunchedEffect(state.error, state.qrError) {
        val msg = state.error ?: state.qrError
        msg?.let {
            snackbarHostState.showSnackbar(it.resolve())
            viewModel.clearError()
        }
    }

    if (state.isCheckingToken) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = "Arkiroku",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.login_app_credit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                LoginMethodSection(
                    state = state,
                    onUpdateServer = viewModel::updateServer,
                    onStartQrLogin = viewModel::startQrLogin,
                    onCancelQrLogin = viewModel::cancelQrLogin,
                    onTokenChange = viewModel::updateToken,
                    onLogin = viewModel::login
                )

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(onClick = onSkip, enabled = !state.isLoading && !state.isCheckingToken) {
                    Text(
                        text = stringResource(Res.string.login_skip_to_home),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { showResetDialog = true },
                    enabled = !state.isLoading && !state.isCheckingToken
                ) {
                    Text(
                        text = stringResource(Res.string.login_reset_local_session),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(Res.string.login_reset_local_session)) },
            text = { Text(stringResource(Res.string.login_reset_local_session_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetLocalSession()
                    }
                ) { Text(stringResource(Res.string.login_reset_local_session)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(Res.string.login_cancel))
                }
            }
        )
    }
}

/**
 * Server picker plus the QR / sessionToken login tabs. Shared by [LoginScreen]
 * and the onboarding login step, where the whole flow is embedded inline; the
 * host owns the [LoginViewModel] and the snackbar / navigation reactions.
 */
@Composable
internal fun LoginMethodSection(
    state: LoginUiState,
    onUpdateServer: (Server) -> Unit,
    onStartQrLogin: () -> Unit,
    onCancelQrLogin: () -> Unit,
    onTokenChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(Res.string.login_select_server),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Server.entries.forEach { server ->
                FilterChip(
                    selected = state.server == server,
                    onClick = { onUpdateServer(server) },
                    label = { Text(server.displayName) },
                    enabled = !state.isLoading && state.qrStatus == QrStatus.Idle
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LoginTabRow(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "login_tab"
        ) { tab ->
            when (tab) {
                0 -> QrLoginContent(
                    state = state,
                    onStartQrLogin = onStartQrLogin,
                    onCancel = onCancelQrLogin
                )
                1 -> TokenLoginContent(
                    state = state,
                    onTokenChange = onTokenChange,
                    onLogin = onLogin
                )
            }
        }
    }
}

@Composable
internal fun QrLoginContent(
    state: LoginUiState,
    onStartQrLogin: () -> Unit,
    onCancel: () -> Unit,
    onOpenQrUrl: ((String) -> Unit)? = null
) {
    DisposableEffect(Unit) {
        onDispose { onCancel() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state.qrStatus) {
            QrStatus.Idle -> {
                Text(
                    text = stringResource(Res.string.login_qr_hint_idle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onStartQrLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.login_generate_qr))
                }
            }

            QrStatus.Loading -> {
                Spacer(modifier = Modifier.height(48.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.login_generating_qr),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            QrStatus.WaitingScan, QrStatus.Scanned -> {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                state.qrCodeUrl?.let { url ->
                    QrCodeImage(
                        url = url,
                        onClick = { onOpenQrUrl?.invoke(url) ?: uriHandler.openUri(url) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.login_qr_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                val statusText = if (state.qrStatus == QrStatus.Scanned) {
                    stringResource(Res.string.login_qr_scanned)
                } else {
                    stringResource(Res.string.login_qr_waiting_scan)
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.qrStatus == QrStatus.Scanned)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.login_qr_remaining_time, state.qrRemainingSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.qrRemainingSeconds <= 30)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(Res.string.login_cancel))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.login_security_tip),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            QrStatus.Exchanging -> {
                Spacer(modifier = Modifier.height(48.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.login_syncing_game_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            QrStatus.Success -> {
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.login_success),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            QrStatus.Error -> {
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.qrError?.asString() ?: stringResource(Res.string.login_failed_fallback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartQrLogin) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.login_retry))
                }
            }

            QrStatus.Expired -> {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(Res.string.login_qr_expired),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartQrLogin) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.login_regenerate_qr))
                }
            }
        }
    }
}

@Composable
internal fun TokenLoginContent(
    state: LoginUiState,
    onTokenChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = state.token,
            onValueChange = onTokenChange,
            label = { Text("sessionToken") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isLoading,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = state.token.isNotBlank() && !state.isLoading && !state.isLoggedIn
        ) {
            AnimatedVisibility(visible = state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            AnimatedVisibility(visible = !state.isLoading) {
                // After a successful login the button turns into the success
                // signal itself and locks, instead of leaving a live "log in"
                // button behind (the QR tab shows the same text via its own
                // Success state).
                Text(
                    text = if (state.isLoggedIn) {
                        stringResource(Res.string.login_success)
                    } else {
                        stringResource(Res.string.login_login_and_sync)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val tokenHint = stringResource(Res.string.login_token_hint)
        val tokenHintQuestion = stringResource(Res.string.login_token_hint_question)
        val tutorialLinkText = stringResource(Res.string.login_token_tutorial_link)
        val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
            append(tokenHint)
            append(tokenHintQuestion)
            val link = androidx.compose.ui.text.LinkAnnotation.Url(
                url = "https://www.kdocs.cn/l/cvMDjWPTNaz4",
                styles = androidx.compose.ui.text.TextLinkStyles(
                    style = androidx.compose.ui.text.SpanStyle(
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            )
            pushLink(link)
            append(tutorialLinkText)
            pop()
        }

        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QrCodeImage(url: String, onClick: (() -> Unit)? = null) {
    ElevatedCard(
        modifier = Modifier
            .widthIn(max = QrCodeMaxSize)
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = rememberQrCodePainter(
                    data = url,
                    darkBrush = QrBrush.solid(Color.Black),
                    lightBrush = QrBrush.solid(Color.White),
                    ballBrush = QrBrush.solid(Color.Black),
                    frameBrush = QrBrush.solid(Color.Black),
                    ballShape = QrBallShape.circle(),
                    darkPixelShape = QrPixelShape.roundCorners(.25f),
                    frameShape = QrFrameShape.roundCorners(.25f),
                ),
                contentDescription = stringResource(Res.string.login_qr_code_cd),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun LoginTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTab,
        modifier = Modifier.fillMaxWidth(),
        indicator = { ElasticClickPrimaryIndicator(selectedTabIndex = selectedTab) }
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            text = { Text(stringResource(Res.string.login_tab_qr)) },
            icon = { Icon(Icons.Default.QrCode2, contentDescription = null) }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            text = { Text(stringResource(Res.string.login_tab_token)) },
            icon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
        )
    }
}
