package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.data.platform.getAppMetadata
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog
import org.kasumi321.ushio.phitracker.ui.components.CenteredListItem
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.about_acknowledgments
import phitracker.composeapp.generated.resources.about_acknowledgments_desc
import phitracker.composeapp.generated.resources.about_build_date_label
import phitracker.composeapp.generated.resources.about_build_type_debug
import phitracker.composeapp.generated.resources.about_build_type_label
import phitracker.composeapp.generated.resources.about_build_type_release
import phitracker.composeapp.generated.resources.about_feedback
import phitracker.composeapp.generated.resources.about_feedback_desc
import phitracker.composeapp.generated.resources.about_legal
import phitracker.composeapp.generated.resources.about_legal_desc
import phitracker.composeapp.generated.resources.about_privacy_policy
import phitracker.composeapp.generated.resources.about_privacy_policy_desc
import phitracker.composeapp.generated.resources.about_project_home
import phitracker.composeapp.generated.resources.about_project_home_desc
import phitracker.composeapp.generated.resources.about_tagline
import phitracker.composeapp.generated.resources.about_third_party
import phitracker.composeapp.generated.resources.about_third_party_desc
import phitracker.composeapp.generated.resources.about_title
import phitracker.composeapp.generated.resources.about_version_label
import phitracker.composeapp.generated.resources.action_back
import phitracker.composeapp.generated.resources.common_confirm
import phitracker.composeapp.generated.resources.settings_cancel
import phitracker.composeapp.generated.resources.settings_rerun_onboarding
import phitracker.composeapp.generated.resources.settings_rerun_onboarding_desc
import phitracker.composeapp.generated.resources.settings_rerun_onboarding_dialog_text
import phitracker.composeapp.generated.resources.settings_rerun_onboarding_dialog_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToDisclaimer: () -> Unit,
    onNavigateToAcknowledgments: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onRerunOnboarding: () -> Unit = {}
) {
    val metadata = remember { getAppMetadata() }
    val buildType = if (metadata.buildType == "Debug") {
        stringResource(Res.string.about_build_type_debug)
    } else {
        stringResource(Res.string.about_build_type_release)
    }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var showRerunOnboardingDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Text("Arkiroku", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(Res.string.about_tagline), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(Res.string.about_version_label, metadata.versionName), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(Res.string.about_build_date_label, metadata.buildTime), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.about_build_type_label, buildType), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            CenteredListItem(
                headlineContent = { Text(stringResource(Res.string.about_project_home)) },
                supportingContent = { Text(stringResource(Res.string.about_project_home_desc)) },
                leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/Kasumi-Ushio/Ushio-Prober-Phigros")
                }
            )

            CenteredListItem(
                headlineContent = { Text(stringResource(Res.string.about_feedback)) },
                supportingContent = { Text(stringResource(Res.string.about_feedback_desc)) },
                leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/Kasumi-Ushio/Ushio-Prober-Phigros/issues")
                }
            )

            CenteredListItem(
                headlineContent = { Text(stringResource(Res.string.about_legal)) },
                supportingContent = { Text(stringResource(Res.string.about_legal_desc)) },
                leadingContent = { Icon(Icons.Default.Gavel, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToDisclaimer() }
            )

            CenteredListItem(
                headlineContent = { Text(stringResource(Res.string.about_acknowledgments)) },
                supportingContent = { Text(stringResource(Res.string.about_acknowledgments_desc)) },
                leadingContent = { Icon(Icons.Default.Favorite, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToAcknowledgments() }
            )

            CenteredListItem(
                headlineContent = { Text(stringResource(Res.string.about_privacy_policy)) },
                supportingContent = { Text(stringResource(Res.string.about_privacy_policy_desc)) },
                leadingContent = { Icon(Icons.Default.Shield, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToPrivacyPolicy() }
            )

            CenteredListItem(
                headlineContent = { Text(stringResource(Res.string.about_third_party)) },
                supportingContent = { Text(stringResource(Res.string.about_third_party_desc)) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToLicenses() }
            )

            CenteredListItem(
                headlineContent = { Text(stringResource(Res.string.settings_rerun_onboarding)) },
                supportingContent = { Text(stringResource(Res.string.settings_rerun_onboarding_desc)) },
                leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                modifier = Modifier.clickable { showRerunOnboardingDialog = true }
            )
        }
    }

    if (showRerunOnboardingDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showRerunOnboardingDialog = false },
            title = { Text(stringResource(Res.string.settings_rerun_onboarding_dialog_title)) },
            text = { Text(stringResource(Res.string.settings_rerun_onboarding_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRerunOnboardingDialog = false
                        onRerunOnboarding()
                    }
                ) { Text(stringResource(Res.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRerunOnboardingDialog = false }) {
                    Text(stringResource(Res.string.settings_cancel))
                }
            }
        )
    }
}
