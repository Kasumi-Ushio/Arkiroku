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
import phitracker.composeapp.generated.resources.privacy_heading
import phitracker.composeapp.generated.resources.privacy_intro_1
import phitracker.composeapp.generated.resources.privacy_intro_2
import phitracker.composeapp.generated.resources.privacy_intro_3
import phitracker.composeapp.generated.resources.privacy_section_1_intro
import phitracker.composeapp.generated.resources.privacy_section_1_item_1
import phitracker.composeapp.generated.resources.privacy_section_1_item_2
import phitracker.composeapp.generated.resources.privacy_section_1_item_3
import phitracker.composeapp.generated.resources.privacy_section_1_item_4
import phitracker.composeapp.generated.resources.privacy_section_1_item_5
import phitracker.composeapp.generated.resources.privacy_section_1_title
import phitracker.composeapp.generated.resources.privacy_section_2_intro
import phitracker.composeapp.generated.resources.privacy_section_2_item_1
import phitracker.composeapp.generated.resources.privacy_section_2_item_2
import phitracker.composeapp.generated.resources.privacy_section_2_item_3
import phitracker.composeapp.generated.resources.privacy_section_2_item_4
import phitracker.composeapp.generated.resources.privacy_section_2_title
import phitracker.composeapp.generated.resources.privacy_section_3_detail
import phitracker.composeapp.generated.resources.privacy_section_3_intro
import phitracker.composeapp.generated.resources.privacy_section_3_item_1
import phitracker.composeapp.generated.resources.privacy_section_3_item_2
import phitracker.composeapp.generated.resources.privacy_section_3_item_3
import phitracker.composeapp.generated.resources.privacy_section_3_item_4
import phitracker.composeapp.generated.resources.privacy_section_3_note
import phitracker.composeapp.generated.resources.privacy_section_3_title
import phitracker.composeapp.generated.resources.privacy_section_4_intro
import phitracker.composeapp.generated.resources.privacy_section_4_item_1
import phitracker.composeapp.generated.resources.privacy_section_4_item_2
import phitracker.composeapp.generated.resources.privacy_section_4_item_3
import phitracker.composeapp.generated.resources.privacy_section_4_item_4
import phitracker.composeapp.generated.resources.privacy_section_4_item_5
import phitracker.composeapp.generated.resources.privacy_section_4_note
import phitracker.composeapp.generated.resources.privacy_section_4_title
import phitracker.composeapp.generated.resources.privacy_section_5_intro
import phitracker.composeapp.generated.resources.privacy_section_5_item_1
import phitracker.composeapp.generated.resources.privacy_section_5_item_2
import phitracker.composeapp.generated.resources.privacy_section_5_item_3
import phitracker.composeapp.generated.resources.privacy_section_5_item_4
import phitracker.composeapp.generated.resources.privacy_section_5_title
import phitracker.composeapp.generated.resources.privacy_section_6_text_1
import phitracker.composeapp.generated.resources.privacy_section_6_text_2
import phitracker.composeapp.generated.resources.privacy_section_6_title
import phitracker.composeapp.generated.resources.privacy_section_7_text
import phitracker.composeapp.generated.resources.privacy_section_7_title
import phitracker.composeapp.generated.resources.privacy_section_8_intro
import phitracker.composeapp.generated.resources.privacy_section_8_item_1
import phitracker.composeapp.generated.resources.privacy_section_8_item_2
import phitracker.composeapp.generated.resources.privacy_section_8_item_3
import phitracker.composeapp.generated.resources.privacy_section_8_item_4
import phitracker.composeapp.generated.resources.privacy_section_8_item_5
import phitracker.composeapp.generated.resources.privacy_section_8_title
import phitracker.composeapp.generated.resources.privacy_section_9_intro
import phitracker.composeapp.generated.resources.privacy_section_9_item_1
import phitracker.composeapp.generated.resources.privacy_section_9_item_2
import phitracker.composeapp.generated.resources.privacy_section_9_title
import phitracker.composeapp.generated.resources.privacy_title
import phitracker.composeapp.generated.resources.privacy_updated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.privacy_title)) },
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
                text = stringResource(Res.string.privacy_heading),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            PolicyText(stringResource(Res.string.privacy_updated))

            Spacer(modifier = Modifier.height(16.dp))
            PolicyText(stringResource(Res.string.privacy_intro_1))
            PolicyText(stringResource(Res.string.privacy_intro_2))
            PolicyText(stringResource(Res.string.privacy_intro_3))

            PolicyTitle(stringResource(Res.string.privacy_section_1_title))
            PolicyText(stringResource(Res.string.privacy_section_1_intro))
            PolicyText(stringResource(Res.string.privacy_section_1_item_1))
            PolicyText(stringResource(Res.string.privacy_section_1_item_2))
            PolicyText(stringResource(Res.string.privacy_section_1_item_3))
            PolicyText(stringResource(Res.string.privacy_section_1_item_4))
            PolicyText(stringResource(Res.string.privacy_section_1_item_5))

            PolicyTitle(stringResource(Res.string.privacy_section_2_title))
            PolicyText(stringResource(Res.string.privacy_section_2_intro))
            PolicyText(stringResource(Res.string.privacy_section_2_item_1))
            PolicyText(stringResource(Res.string.privacy_section_2_item_2))
            PolicyText(stringResource(Res.string.privacy_section_2_item_3))
            PolicyText(stringResource(Res.string.privacy_section_2_item_4))

            PolicyTitle(stringResource(Res.string.privacy_section_3_title))
            PolicyText(stringResource(Res.string.privacy_section_3_intro))
            PolicyText(stringResource(Res.string.privacy_section_3_detail))
            PolicyText(stringResource(Res.string.privacy_section_3_item_1))
            PolicyText(stringResource(Res.string.privacy_section_3_item_2))
            PolicyText(stringResource(Res.string.privacy_section_3_item_3))
            PolicyText(stringResource(Res.string.privacy_section_3_item_4))
            PolicyText(stringResource(Res.string.privacy_section_3_note))

            PolicyTitle(stringResource(Res.string.privacy_section_4_title))
            PolicyText(stringResource(Res.string.privacy_section_4_intro))
            PolicyText(stringResource(Res.string.privacy_section_4_item_1))
            PolicyText(stringResource(Res.string.privacy_section_4_item_2))
            PolicyText(stringResource(Res.string.privacy_section_4_item_3))
            PolicyText(stringResource(Res.string.privacy_section_4_item_4))
            PolicyText(stringResource(Res.string.privacy_section_4_item_5))
            PolicyText(stringResource(Res.string.privacy_section_4_note))

            PolicyTitle(stringResource(Res.string.privacy_section_5_title))
            PolicyText(stringResource(Res.string.privacy_section_5_intro))
            PolicyText(stringResource(Res.string.privacy_section_5_item_1))
            PolicyText(stringResource(Res.string.privacy_section_5_item_2))
            PolicyText(stringResource(Res.string.privacy_section_5_item_3))
            PolicyText(stringResource(Res.string.privacy_section_5_item_4))

            PolicyTitle(stringResource(Res.string.privacy_section_6_title))
            PolicyText(stringResource(Res.string.privacy_section_6_text_1))
            PolicyText(stringResource(Res.string.privacy_section_6_text_2))

            PolicyTitle(stringResource(Res.string.privacy_section_7_title))
            PolicyText(stringResource(Res.string.privacy_section_7_text))

            PolicyTitle(stringResource(Res.string.privacy_section_8_title))
            PolicyText(stringResource(Res.string.privacy_section_8_intro))
            PolicyText(stringResource(Res.string.privacy_section_8_item_1))
            PolicyText(stringResource(Res.string.privacy_section_8_item_2))
            PolicyText(stringResource(Res.string.privacy_section_8_item_3))
            PolicyText(stringResource(Res.string.privacy_section_8_item_4))
            PolicyText(stringResource(Res.string.privacy_section_8_item_5))

            PolicyTitle(stringResource(Res.string.privacy_section_9_title))
            PolicyText(stringResource(Res.string.privacy_section_9_intro))
            PolicyText(stringResource(Res.string.privacy_section_9_item_1))
            PolicyText(stringResource(Res.string.privacy_section_9_item_2))

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PolicyTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = androidx.compose.ui.Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun PolicyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = androidx.compose.ui.Modifier.padding(bottom = 6.dp)
    )
}
