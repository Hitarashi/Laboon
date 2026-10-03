package org.shilpo.laboon.ui.screens.player.lyrics

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.playback.SpectrumFrame

private const val BAR_COUNT = 30
private val BAR_WIDTH = 3.dp
private val BAR_SPACING = 3.dp
private val WAVEFORM_WIDTH = (BAR_WIDTH + BAR_SPACING) * BAR_COUNT
private val MIN_BAR_HEIGHT = 3.dp
private val MAX_BAR_HEIGHT = 22.4.dp
private val WAVEFORM_BAR_EASING = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction)
}

@Composable
fun InstrumentalWaveformView(
    startTime: Long,
    endTime: Long,
    currentProgressMs: Long,
    primaryColor: Color,
    spectrum: SpectrumFrame = SpectrumFrame(),
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val isActive = currentProgressMs in startTime..endTime
    val isVisualizerActive = isActive && isPlaying
    val gapProgress = if (endTime > startTime) {
        ((currentProgressMs - startTime).toFloat() / (endTime - startTime)).coerceIn(0f, 1f)
    } else {
        if (currentProgressMs >= endTime) 1f else 0f
    }
    val fillProgress by animateFloatAsState(
        targetValue = gapProgress,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "instrumental waveform progress",
    )
    val visualizerAlpha by animateFloatAsState(
        targetValue = if (isVisualizerActive) 0.85f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "instrumental waveform opacity",
    )
    Box(
        modifier = modifier
            .width(WAVEFORM_WIDTH)
            .height(36.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .width(WAVEFORM_WIDTH)
                .height(32.dp)
                .graphicsLayer { alpha = visualizerAlpha },
            horizontalArrangement = Arrangement.spacedBy(BAR_SPACING, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(BAR_COUNT) { index ->
                val bandIndex = index * spectrum.bands.size / BAR_COUNT
                val bandLevel = spectrum.bands.getOrNull(bandIndex)?.coerceIn(0f, 1f) ?: 0f
                val targetHeight = if (isVisualizerActive) {
                    maxOf(MIN_BAR_HEIGHT, MAX_BAR_HEIGHT * bandLevel)
                } else {
                    MIN_BAR_HEIGHT
                }
                val barHeight by animateDpAsState(
                    targetValue = targetHeight,
                    animationSpec = tween(durationMillis = 80, easing = WAVEFORM_BAR_EASING),
                    label = "instrumental waveform bar",
                )

                Box(
                    modifier = Modifier
                        .width(BAR_WIDTH)
                        .height(barHeight)
                        .graphicsLayer {
                            val barProgress = (fillProgress * BAR_COUNT - index).coerceIn(0f, 1f)
                            alpha = 0.28f + 0.72f * barProgress
                        }
                        .background(primaryColor, CircleShape),
                )
            }
        }
    }
}
