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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.ack_final_message
import phitracker.composeapp.generated.resources.ack_final_title
import phitracker.composeapp.generated.resources.ack_phigroslibrary_auth_link
import phitracker.composeapp.generated.resources.ack_phigroslibrary_auth_prefix
import phitracker.composeapp.generated.resources.ack_phigroslibrary_auth_suffix
import phitracker.composeapp.generated.resources.ack_phigroslibrary_copyright
import phitracker.composeapp.generated.resources.ack_phigroslibrary_description
import phitracker.composeapp.generated.resources.ack_phiplugin_copyright
import phitracker.composeapp.generated.resources.ack_phiplugin_description
import phitracker.composeapp.generated.resources.ack_respect_statement
import phitracker.composeapp.generated.resources.ack_section_referenced_copyright
import phitracker.composeapp.generated.resources.ack_section_special_thanks
import phitracker.composeapp.generated.resources.ack_thanks_pigeon_games
import phitracker.composeapp.generated.resources.ack_thanks_taptap
import phitracker.composeapp.generated.resources.ack_title
import phitracker.composeapp.generated.resources.action_back

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcknowledgmentsScreen(
    onNavigateBack: () -> Unit
) {
    val authPrefix = stringResource(Res.string.ack_phigroslibrary_auth_prefix)
    val authLink = stringResource(Res.string.ack_phigroslibrary_auth_link)
    val authSuffix = stringResource(Res.string.ack_phigroslibrary_auth_suffix)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.ack_title)) },
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
                text = stringResource(Res.string.ack_section_special_thanks),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionText(stringResource(Res.string.ack_thanks_pigeon_games))
            SectionText(stringResource(Res.string.ack_thanks_taptap))


            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.ack_section_referenced_copyright),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle("Catrong/phi-plugin")
            SectionText(stringResource(Res.string.ack_phiplugin_description))
            SectionText(stringResource(Res.string.ack_phiplugin_copyright))

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle("7aGiven/PhigrosLibrary")
            SectionText(stringResource(Res.string.ack_phigroslibrary_description))
            SectionText(stringResource(Res.string.ack_phigroslibrary_copyright))
            SectionText(
                buildAnnotatedString {
                    append(authPrefix)
                    withLink(LinkAnnotation.Url("https://github.com/7aGiven/PhigrosLibrary/issues/11")) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                            append(authLink)
                        }
                    }
                    append(authSuffix)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.ack_respect_statement),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.ack_final_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.ack_final_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
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
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SectionText(text: AnnotatedString) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
