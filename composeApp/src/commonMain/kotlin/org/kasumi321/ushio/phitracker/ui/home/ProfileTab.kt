package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.data.platform.rememberAvatarPicker
import org.kasumi321.ushio.phitracker.domain.model.B30RksHistogram
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo
import org.kasumi321.ushio.phitracker.ui.b30.B30RksHistogramChart
import org.kasumi321.ushio.phitracker.ui.b30.setImageRequestAllowHardware
import org.kasumi321.ushio.phitracker.ui.components.phiFontFamily
import org.kasumi321.ushio.phitracker.ui.glass.rememberExpansionArrowRotation
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors
import org.kasumi321.ushio.phitracker.ui.utils.expandCollapseTransition
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.action_go_login
import phitracker.composeapp.generated.resources.profile_avatar
import phitracker.composeapp.generated.resources.profile_collapse
import phitracker.composeapp.generated.resources.profile_data_label
import phitracker.composeapp.generated.resources.profile_expand
import phitracker.composeapp.generated.resources.profile_game_update_label
import phitracker.composeapp.generated.resources.profile_game_update_version
import phitracker.composeapp.generated.resources.profile_guest_message
import phitracker.composeapp.generated.resources.profile_guest_title
import phitracker.composeapp.generated.resources.profile_never_synced
import phitracker.composeapp.generated.resources.profile_no_score_changes
import phitracker.composeapp.generated.resources.profile_not_logged_in
import phitracker.composeapp.generated.resources.profile_recent_sync
import phitracker.composeapp.generated.resources.profile_set_avatar
import phitracker.composeapp.generated.resources.profile_sync_time

private val ChallengeTierColors = listOf(
    Color(0xFFCCCCCC),
    Color(0xFF4CAF50),
    Color(0xFF2196F3),
    Color(0xFFF44336),
    Color(0xFFFFD700),
    Color.Unspecified
)

private val RainbowBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFF0000),
        Color(0xFFFF7F00),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF0000FF),
        Color(0xFF4B0082),
        Color(0xFF9400D3)
    )
)

private val FcColor = Color(0xFF4FC3F7)
private val PhiColor = Color(0xFFFFD54F)
private val PhiTextColor = Color(0xFF5D4037)

@Composable
fun ProfileTab(
    state: ProfileUiState,
    displayRks: Float,
    isLoggedIn: Boolean = true,
    onNavigateToLogin: () -> Unit = {},
    onAvatarSelected: (String) -> Unit,
    onSongClick: (String, Difficulty?) -> Unit,
    getIllustrationUrl: (String) -> String?,
    histogram: B30RksHistogram? = null,
    gameUpdateInfo: GameUpdateInfo? = null,
    contentPadding: PaddingValues = PaddingValues(),
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier
) {
    val nickname = state.nickname
    val challengeModeRank = state.challengeModeRank
    val moneyString = state.moneyString
    val clearCounts = state.clearCounts
    val fcCount = state.fcCount
    val phiCount = state.phiCount
    val avatarUri = state.avatarUri
    val lastSyncTime = state.lastSyncTime
    val recentSyncedRecords = state.recentSyncedRecords
    val launchPicker = rememberAvatarPicker { uri ->
        uri?.let { onAvatarSelected(it) }
    }

    // Full-bleed scroll: content scrolls behind the floating glass bars, with
    // spacers keeping the first and last items clear of them
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))

            if (!isLoggedIn) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.profile_guest_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.profile_guest_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onNavigateToLogin, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(Res.string.action_go_login))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            ProfileHeaderCard(
                nickname = nickname,
                displayRks = displayRks,
                challengeModeRank = challengeModeRank,
                moneyString = moneyString,
                avatarUri = avatarUri,
                onAvatarClick = { launchPicker() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            StatsTableCard(
                clearCounts = clearCounts,
                fcCount = fcCount,
                phiCount = phiCount,
                modifier = Modifier.fillMaxWidth()
            )

            if (histogram != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        B30RksHistogramChart(
                            histogram = histogram,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (gameUpdateInfo != null) {
                Spacer(modifier = Modifier.height(16.dp))
                GameUpdateInfoCard(
                    info = gameUpdateInfo,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.profile_recent_sync),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (lastSyncTime != null) {
                val formattedTime = epochMillisToDateTimeString(lastSyncTime)
                Text(
                    text = stringResource(Res.string.profile_sync_time, formattedTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (recentSyncedRecords.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSyncedRecords.forEachIndexed { index, record ->
                            ScoreCard(
                                rank = index + 1,
                                record = record,
                                illustrationUrl = getIllustrationUrl(record.songId),
                                onSongClick = onSongClick
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.profile_no_score_changes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        text = stringResource(Res.string.profile_never_synced),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
        }
    }
}

@Composable
fun ProfileHeaderCard(
    nickname: String,
    displayRks: Float,
    challengeModeRank: Int,
    moneyString: String,
    avatarUri: String?,
    onAvatarClick: (() -> Unit)? = null,
    contentHorizontalPadding: Dp = 20.dp,
    contentVerticalPadding: Dp = 20.dp,
    textVerticalSpacing: Dp = 3.dp,
    avatarSize: Dp = 72.dp,
    avatarTextSpacing: Dp = 16.dp,
    centerContent: Boolean = false,
    modifier: Modifier = Modifier,
    allowHardwareImages: Boolean = true,
    // Export tuning: the off-screen B30 capture pins the avatar request size so
    // its Coil memory-cache key matches the export preloader, disables
    // crossfade (a mid-transition frame would capture semi-transparent), and
    // reports painter completion so the capture can wait for the avatar.
    avatarRequestSizePx: Int? = null,
    avatarCrossfade: Boolean = true,
    onAvatarSettled: ((Throwable?) -> Unit)? = null
) {
    val platformContext = LocalPlatformContext.current

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentHorizontalPadding, vertical = contentVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centerContent) Arrangement.Center else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (onAvatarClick != null) Modifier.clickable { onAvatarClick() }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    val imageRequest = remember(platformContext, avatarUri, avatarRequestSizePx, avatarCrossfade) {
                        ImageRequest.Builder(platformContext).apply {
                            data(avatarUri)
                            avatarRequestSizePx?.let { size(it) }
                            crossfade(avatarCrossfade)
                            setImageRequestAllowHardware(allowHardwareImages)
                        }.build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = stringResource(Res.string.profile_avatar),
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        onState = onAvatarSettled?.let { settled ->
                            { state ->
                                when (state) {
                                    is AsyncImagePainter.State.Success -> settled(null)
                                    is AsyncImagePainter.State.Error -> settled(state.result.throwable)
                                    else -> Unit
                                }
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.profile_set_avatar),
                        modifier = Modifier.size(avatarSize * 0.4f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(avatarTextSpacing))

            Column(
                verticalArrangement = Arrangement.spacedBy(textVerticalSpacing, Alignment.CenterVertically),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = nickname.ifBlank { stringResource(Res.string.profile_not_logged_in) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (moneyString.isNotBlank()) {
                    Text(
                        text = stringResource(Res.string.profile_data_label, moneyString),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displayRks.formatFour(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (challengeModeRank > 0) {
                        ChallengeBadge(challengeModeRank)
                    }
                }
            }
        }
    }
}

@Composable
fun StatsTableCard(
    clearCounts: Map<String, Int>,
    fcCount: Int,
    phiCount: Int,
    contentHorizontalPadding: Dp = 16.dp,
    contentVerticalPadding: Dp = 16.dp,
    rowSpacing: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentHorizontalPadding, vertical = contentVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(rowSpacing, Alignment.CenterVertically)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DifficultyStatItem("EZ", clearCounts["EZ"] ?: 0, DifficultyColors.EZ)
                DifficultyStatItem("HD", clearCounts["HD"] ?: 0, DifficultyColors.HD)
                DifficultyStatItem("IN", clearCounts["IN"] ?: 0, DifficultyColors.IN)
                DifficultyStatItem("AT", clearCounts["AT"] ?: 0, DifficultyColors.AT)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BadgeStatItem("FC", fcCount, FcColor, Color.White)
                BadgeStatItem("\u03C6", phiCount, PhiColor, PhiTextColor, fontFamily = phiFontFamily)
            }
        }
    }
}

@Composable
private fun DifficultyStatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BadgeStatItem(
    label: String,
    count: Int,
    bgColor: Color,
    textColor: Color,
    fontFamily: FontFamily? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(bgColor)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                color = textColor,
                fontSize = 13.sp
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChallengeBadge(challengeModeRank: Int) {
    val tier = challengeModeRank / 100
    val level = challengeModeRank % 100

    val isRainbow = tier == 5
    val bgColor = if (!isRainbow && tier in ChallengeTierColors.indices) {
        ChallengeTierColors[tier]
    } else {
        Color.Transparent
    }
    val textColor = when (tier) {
        0 -> Color(0xFF333333)
        4 -> Color(0xFF5D4037)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (isRainbow) {
                    Modifier.background(RainbowBrush)
                } else {
                    Modifier.background(bgColor)
                }
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$level",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isRainbow) Color.White else textColor,
            fontSize = 13.sp
        )
    }
}

/**
 * Latest Phigros game update from TapTap. The changelog can be long, so it
 * renders collapsed to a few lines by default; tapping the title row toggles
 * the full text (same interaction as the B30 tab's CollapsibleTagAnalysis).
 * Expansion reveals the text progressively as the bounds grow; collapse
 * crossfades the full text into the truncated copy so the hidden lines
 * disappear gradually instead of vanishing at once (see
 * expandCollapseTransition). The arrow rotates with the state.
 */
@Composable
private fun GameUpdateInfoCard(
    info: GameUpdateInfo,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotionEnabled()
    val arrowRotation by rememberExpansionArrowRotation(expanded)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.profile_game_update_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.profile_game_update_version, info.version),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = info.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        stringResource(Res.string.profile_collapse)
                    } else {
                        stringResource(Res.string.profile_expand)
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrowRotation)
                )
            }
            AnimatedContent(
                targetState = expanded,
                transitionSpec = { expandCollapseTransition(reducedMotion) },
                label = "gameUpdateChangelog",
                modifier = Modifier.padding(top = 8.dp)
            ) { targetExpanded ->
                Text(
                    text = info.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (targetExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
