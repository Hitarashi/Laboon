package org.shilpo.laboon.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import java.util.Locale

val SoftTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.55f),
    blurRadius = 8f,
)

val PlayerCapsuleShape = RoundedCornerShape(24.dp)
val PlayerSheetCornerRadius = 32.dp
val PlayerSheetShape =
    RoundedCornerShape(topStart = PlayerSheetCornerRadius, topEnd = PlayerSheetCornerRadius)

fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
fun Modifier.playerSheetBackground(
    baseColor: Color = Color(0xFF141416),
    isBlurEnabled: Boolean = false,
): Modifier {
    val animatedBaseColor by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "PlayerSheetBgAnimation",
    )

    val cardBackgroundBrush = remember(animatedBaseColor) {
        val midTone = lerp(animatedBaseColor, Color(0xFF101010), 0.35f)
        val deepTone = lerp(animatedBaseColor, Color(0xFF0A0A0A), 0.60f)

        Brush.verticalGradient(
            0.0f to animatedBaseColor,
            0.50f to midTone,
            1.0f to deepTone,
        )
    }

    val glassBorderBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = 0.20f),
            1.0f to Color.Black.copy(alpha = 0.04f),
        )
    }

    return this
        .border(width = 1.dp, brush = glassBorderBrush, shape = PlayerSheetShape)
        .clip(PlayerSheetShape)
        .background(
            if (isBlurEnabled) {
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.45f),
                        Color.Black.copy(alpha = 0.75f),
                    )
                )
            } else {
                cardBackgroundBrush
            }
        )
}
