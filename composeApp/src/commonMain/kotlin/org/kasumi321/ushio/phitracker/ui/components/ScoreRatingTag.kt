package org.kasumi321.ushio.phitracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

enum class ScoreRating(
    val label: String,
    val color: Color,
    val textColor: Color = Color.White
) {
    False("F", Color(0xFF8E8E93)),
    C("C", Color(0xFF58B4E3)),
    B("B", Color(0xFF2E7D32)),
    A("A", Color(0xFFFF9800)),
    S("S", Color(0xFFFF5DA2)),
    V("V", Color(0xFFD060D8)),
    FullCombo("V", Color(0xFF4FC3F7)),
    Phi("\u03C6", Color(0xFFFFD54F), Color(0xFF5D4037));

    companion object {
        /** Amber gold for the phi rating on light surfaces: the bright yellow is unreadable there. */
        private val PHI_LIGHT_THEME_COLOR = Color(0xF7FFB300)

        fun fromScore(score: Int, isFullCombo: Boolean): ScoreRating {
            return when {
                score >= 1_000_000 -> Phi
                isFullCombo -> FullCombo
                score >= 960_000 -> V
                score >= 920_000 -> S
                score >= 880_000 -> A
                score >= 820_000 -> B
                score >= 700_000 -> C
                else -> False
            }
        }
    }

    /**
     * Theme-aware label color. Only [Phi] switches: its bright yellow has
     * insufficient contrast against light backgrounds, so light themes render
     * it in a darker gold while dark themes keep the original yellow.
     */
    fun displayColor(isDarkTheme: Boolean): Color =
        if (this == Phi && !isDarkTheme) PHI_LIGHT_THEME_COLOR else color
}

@Composable
fun ScoreRatingTag(
    rating: ScoreRating,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp
) {
    ColoredTag(
        text = rating.label,
        backgroundColor = rating.color,
        textColor = rating.textColor,
        fontSize = fontSize,
        modifier = modifier,
        fontFamily = if (rating == ScoreRating.Phi) phiFontFamily else null,
        fontWeight = if (rating == ScoreRating.Phi) FontWeight.ExtraBold else FontWeight.Bold,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding
    )
}
