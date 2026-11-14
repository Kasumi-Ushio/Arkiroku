package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.profile_data_label
import phitracker.composeapp.generated.resources.profile_not_logged_in

/** App-only profile hero. The B30 exporter deliberately keeps its baseline header. */
@Composable
internal fun ProfileHeroCard(
    nickname: String,
    displayRks: Float,
    challengeModeRank: Int,
    moneyString: String,
    avatarUri: String?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < 480.dp
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            if (compact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    HeroIdentity(
                        nickname = nickname,
                        moneyString = moneyString,
                        avatarUri = avatarUri,
                        onAvatarClick = onAvatarClick
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HeroRks(displayRks = displayRks, challengeModeRank = challengeModeRank)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeroIdentity(
                        nickname = nickname,
                        moneyString = moneyString,
                        avatarUri = avatarUri,
                        onAvatarClick = onAvatarClick,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    HeroRks(displayRks = displayRks, challengeModeRank = challengeModeRank)
                }
            }
        }
    }
}

@Composable
private fun HeroIdentity(
    nickname: String,
    moneyString: String,
    avatarUri: String?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(
            avatarUri = avatarUri,
            avatarSize = 88.dp,
            onAvatarClick = onAvatarClick
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = nickname.ifBlank { stringResource(Res.string.profile_not_logged_in) },
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (moneyString.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.profile_data_label, moneyString),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HeroRks(displayRks: Float, challengeModeRank: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = displayRks.formatFour(),
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (challengeModeRank > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            ChallengeBadge(challengeModeRank)
        }
    }
}
