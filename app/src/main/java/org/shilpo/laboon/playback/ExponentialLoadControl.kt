package org.shilpo.laboon.playback

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

class ExponentialLoadControl(
    private val delegate: DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            30_000,
            300_000,
            500,
            1_500,
        )
        .setBackBuffer(30_000, true)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build(),
    private val initialBufferTargetUs: Long = 15_000_000L,
    private val maxBufferTargetUs: Long = 300_000_000L,
) : LoadControl {

    override fun onPrepared(playerId: PlayerId) {
        delegate.onPrepared(playerId)
    }

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) {
        delegate.onTracksSelected(parameters, trackGroups, trackSelections)
    }

    override fun onStopped(playerId: PlayerId) {
        delegate.onStopped(playerId)
    }

    override fun onReleased(playerId: PlayerId) {
        delegate.onReleased(playerId)
    }

    override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = 30_000_000L

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = true

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val positionSeconds = (parameters.playbackPositionUs / 1_000_000L).coerceAtLeast(0L)
        val step = (positionSeconds / 10L).coerceAtMost(5L)
        val dynamicTargetUs =
            (initialBufferTargetUs * (1L shl step.toInt())).coerceAtMost(maxBufferTargetUs)

        if (parameters.bufferedDurationUs < dynamicTargetUs) {
            return true
        }
        return delegate.shouldContinueLoading(parameters)
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        if (parameters.bufferedDurationUs >= 500_000L) {
            return true
        }
        return delegate.shouldStartPlayback(parameters)
    }
}
