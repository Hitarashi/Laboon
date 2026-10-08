package org.shilpo.laboon.ui.screens.player

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import org.shilpo.laboon.lyricsporn.LyricspornMotionArtwork
import java.util.Locale

@Composable
@OptIn(markerClass = [UnstableApi::class])
internal fun MotionArtworkVideo(
    artwork: LyricspornMotionArtwork,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentIsPlaying = rememberUpdatedState(isPlaying)
    var hasRenderedFrame by remember(artwork.url) { mutableStateOf(false) }
    val player = remember(artwork.url, artwork.format) {
        ExoPlayer.Builder(context).build().apply {
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(artwork.toMediaItem())
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasRenderedFrame = true
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hasRenderedFrame = false
            }
        }

        fun syncPlayback() {
            if (
                currentIsPlaying.value &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            } else {
                player.pause()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> syncPlayback()
                Lifecycle.Event.ON_STOP -> {
                    hasRenderedFrame = false
                    player.pause()
                    player.stop()
                }

                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        syncPlayback()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, lifecycleOwner, isPlaying) {
        if (
            isPlaying &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        } else {
            player.pause()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (hasRenderedFrame) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "motionArtworkAlpha",
    )
    ContentFrame(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = ContentScale.Crop,
        keepContentOnReset = false,
        shutter = {},
        modifier = modifier.alpha(alpha),
    )
}

private fun LyricspornMotionArtwork.toMediaItem(): MediaItem {
    val normalizedUrl = url.lowercase(Locale.ROOT)
    val mimeType = when {
        format == "hls" || normalizedUrl.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
        format == "mp4" || normalizedUrl.contains(".mp4") -> MimeTypes.VIDEO_MP4
        else -> null
    }
    return MediaItem.Builder()
        .setUri(url)
        .apply { mimeType?.let(::setMimeType) }
        .build()
}
