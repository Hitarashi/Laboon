package org.shilpo.laboon.playback

import org.shilpo.laboon.home.HomeTrack

data class PlaybackState(
    val currentTrack: HomeTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0f,
    val error: String? = null,
)
