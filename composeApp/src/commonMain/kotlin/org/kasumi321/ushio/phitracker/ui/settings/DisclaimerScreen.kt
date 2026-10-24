package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.action_back
import phitracker.composeapp.generated.resources.disclaimer_body_commercial
import phitracker.composeapp.generated.resources.disclaimer_body_illegal
import phitracker.composeapp.generated.resources.disclaimer_body_no_warranty
import phitracker.composeapp.generated.resources.disclaimer_body_unofficial
import phitracker.composeapp.generated.resources.disclaimer_disclaimer_header
import phitracker.composeapp.generated.resources.disclaimer_section_copyright_body
import phitracker.composeapp.generated.resources.disclaimer_section_copyright_title
import phitracker.composeapp.generated.resources.disclaimer_section_license_header
import phitracker.composeapp.generated.resources.disclaimer_section_project_license_body
import phitracker.composeapp.generated.resources.disclaimer_section_project_license_title
import phitracker.composeapp.generated.resources.disclaimer_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclaimerScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.disclaimer_title)) },
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(Res.string.disclaimer_section_license_header),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(stringResource(Res.string.disclaimer_section_copyright_title))
            SectionText(stringResource(Res.string.disclaimer_section_copyright_body))

            SectionTitle(stringResource(Res.string.disclaimer_section_project_license_title))
            SectionText(stringResource(Res.string.disclaimer_section_project_license_body))

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.disclaimer_disclaimer_header),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionText(stringResource(Res.string.disclaimer_body_unofficial))

            SectionText(stringResource(Res.string.disclaimer_body_commercial))

            Text(
                text = stringResource(Res.string.disclaimer_body_illegal),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = stringResource(Res.string.disclaimer_body_no_warranty),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}
