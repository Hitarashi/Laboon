package org.shilpo.laboon.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.AudioQualityInfo
import org.shilpo.laboon.ui.design.AudioQualityBadge

@Composable
internal fun PlayerSeekBar(
    track: HomeTrack,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    audioQuality: AudioQualityInfo?,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onAudioQualityClick: () -> Unit = {},
    onAudioQualityPositioned: ((Rect) -> Unit)? = null,
    audioBadgeAlpha: Float = 1f,
    isDark: Boolean,
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    val safeDuration = durationMs.coerceAtLeast(0L)

    val (smoothProgressFraction, displayedPosition) = rememberSmoothProgress(
        isPlayingProvider = { isPlaying },
        currentPositionProvider = {
            if (isSeeking) (seekPosition * safeDuration).toLong() else currentPositionMs
        },
        totalDuration = safeDuration,
        isVisible = true,
    )

    Column(modifier = modifier) {
        WavySliderExpressive(
            value = { if (isSeeking) seekPosition else smoothProgressFraction.value },
            onValueChange = { fraction ->
                isSeeking = true
                seekPosition = fraction
            },
            onValueCommit = { fraction ->
                isSeeking = false
                onSeek(fraction)
            },
            enabled = safeDuration > 0L,
            activeTrackColor = if (isDark) Color.White else Color(0xFF191C1E),
            inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.24f) else Color.Black.copy(
                alpha = 0.16f
            ),
            thumbColor = if (isDark) Color.White else Color(0xFF191C1E),
            isPlaying = isPlaying,
            isVisible = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val position = if (isSeeking) {
                (seekPosition * safeDuration).toLong()
            } else {
                displayedPosition.value
            }
            Text(
                text = formatDurationMs(position),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
            )

            AudioQualityBadge(
                quality = audioQuality,
                fallbackCodec = track.codec,
                track = track,
                isDark = isDark,
                modifier = Modifier
                    .graphicsLayer { alpha = audioBadgeAlpha.coerceIn(0f, 1f) }
                    .then(
                        if (onAudioQualityPositioned != null) {
                            Modifier.onGloballyPositioned { coords ->
                                onAudioQualityPositioned(coords.boundsInRoot())
                            }
                        } else Modifier
                    ),
                onClick = onAudioQualityClick,
            )

            Text(
                text = formatDurationMs(safeDuration),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
            )
        }
    }
}
