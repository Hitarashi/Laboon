package org.shilpo.laboon.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R


@Composable
fun LiquidGlassPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdropState: LiquidGlassBackdropState? = null,
    isPlaying: Boolean = false,
    contentDescription: String? = if (isPlaying) "Pause" else "Play",
    size: Dp = 38.dp,
    iconSize: Dp = 19.dp,
) {
    val isDark = isSystemInDarkTheme()
    val motionScheme = MaterialTheme.motionScheme
    val contentColor = MaterialTheme.colorScheme.onSurface

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "liquidGlassPlayButtonScale",
    )

    val pillGradient = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.12f),
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.50f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.75f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f),
            )
        },
    )

    val borderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.10f),
                Color.Transparent,
            )
        },
    )

    val radius = size / 2

    LiquidGlassSurface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        backdropState = backdropState,
        shape = CircleShape,
        cornerRadius = radius,
        topRadius = radius,
        bottomRadius = radius,
        tintColor = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tintAlpha = if (isDark) 0.75f else 0.80f,
        blurRadius = 10.dp,
        refractIndex = 1.5f,
        refractIntensity = 0.40f,
        thicknessDp = 4.5.dp,
        shadowElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = pillGradient,
                    shape = CircleShape,
                )
                .border(
                    width = 0.5.dp,
                    brush = borderBrush,
                    shape = CircleShape,
                )
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = contentColor),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                ),
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier
                    .size(iconSize)
                    .offset(x = if (isPlaying) 0.dp else 1.dp),
            )
        }
    }
}
