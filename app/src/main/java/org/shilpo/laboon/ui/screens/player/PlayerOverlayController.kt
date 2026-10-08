package org.shilpo.laboon.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerOverlayController(
    state: PlayerOverlayState,
    actions: PlayerOverlayActions,
    onAudioQualityPositioned: (Rect) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    lyricsTools: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (lyricsTools != null) {
            lyricsTools()
            Spacer(modifier = Modifier.height(12.dp))
        }

        PlayerSeekBar(
            track = state.track,
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            currentPositionMs = state.currentPositionMs,
            durationMs = state.durationMs,
            audioQuality = state.audioQuality,
            switchingQualityFormat = state.switchingQualityFormat,
            onQualityVariantSelected = actions.onQualityVariantSelected,
            onSeek = actions.onSeek,
            onAudioQualityClick = actions.onAudioQualityClick,
            onAudioQualityPositioned = onAudioQualityPositioned,
            audioBadgeAlpha = 1f,
            isDark = isDark,
            showAudioQuality = false,
        )

        Spacer(modifier = Modifier.height(14.dp))

        FullPlayerTransportControls(
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            canSkipPrevious = state.canSkipPrevious,
            canSkipNext = state.queueState?.hasNext == true,
            onPlayPause = actions.onPlayPause,
            onPrevious = actions.onPrevious,
            onNext = actions.onNext,
        )

        Spacer(modifier = Modifier.height(14.dp))

        FullPlayerToggleRow(
            isShuffle = state.isShuffle,
            repeatMode = state.repeatMode,
            onShuffle = actions.onToggleShuffle,
            onRepeat = actions.onCycleRepeatMode,
        )
    }
}
