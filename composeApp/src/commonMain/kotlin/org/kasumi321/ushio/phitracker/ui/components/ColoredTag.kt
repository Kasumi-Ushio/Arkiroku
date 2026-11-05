package org.kasumi321.ushio.phitracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.theme.DifficultyColors

@Composable
internal fun ColoredTag(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight = FontWeight.Bold,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DifficultyTag(
    difficulty: Difficulty,
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    ColoredTag(
        text = text,
        backgroundColor = DifficultyColors.forDifficulty(difficulty),
        textColor = MaterialTheme.colorScheme.surface,
        fontSize = fontSize,
        modifier = modifier
    )
}
