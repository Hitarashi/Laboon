package org.shilpo.laboon.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchRepositoryImpl

interface PlaybackManager {
    val state: StateFlow<PlaybackState>
    fun play(track: HomeTrack)
    fun pause()
    fun resume()
    fun togglePlayPause()
    fun seekTo(progress: Float)
    fun stop()
    fun release()
}

class PlaybackManagerImpl(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val searchRepository: SearchRepository = SearchRepositoryImpl(sessionStore),
) : PlaybackManager {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var progressJob: Job? = null
    private var resolveJob: Job? = null
    private var fadeJob: Job? = null
    private var pendingFadeIn: Boolean = false

    init {
        initPlayer()
    }

    private fun initPlayer() {
        if (player != null) return
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(true)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .build()
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setAudioOffloadPreferences(
                        TrackSelectionParameters.AudioOffloadPreferences.DEFAULT
                    )
            )
        }

        val exo = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _state.value = _state.value.copy(isBuffering = true)
                    }

                    Player.STATE_READY -> {
                        val dur = exo.duration.coerceAtLeast(0L)
                        _state.value = _state.value.copy(
                            isBuffering = false,
                            durationMs = dur,
                        )
                    }

                    Player.STATE_ENDED -> {
                        fadeJob?.cancel()
                        pendingFadeIn = false
                        exo.volume = 1f
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            progress = 1.0f,
                        )
                        stopProgressTracker()
                    }

                    Player.STATE_IDLE -> {
                        _state.value = _state.value.copy(isBuffering = false)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                    if (pendingFadeIn) {
                        pendingFadeIn = false
                        fadeIn(350L)
                    }
                } else {
                    stopProgressTracker()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val format = exo.audioFormat
                val mimeType = format?.sampleMimeType
                if (mimeType != null) {
                    val resolvedCodec = when {
                        mimeType == MimeTypes.AUDIO_E_AC3_JOC ||
                                mimeType == MimeTypes.AUDIO_E_AC3 ||
                                mimeType.contains("dolby") ||
                                mimeType.contains("eac3") ||
                                (format.channelCount > 2) -> "ec-3"

                        mimeType.contains("flac") -> "flac"
                        mimeType.contains("alac") -> "alac"
                        else -> null
                    }
                    if (resolvedCodec != null && _state.value.currentTrack?.codec != resolvedCodec) {
                        _state.value = _state.value.copy(
                            currentTrack = _state.value.currentTrack?.copy(codec = resolvedCodec)
                        )
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                fadeJob?.cancel()
                pendingFadeIn = false
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    error = error.message,
                )
                stopProgressTracker()
            }
        })

        try {
            val openAppIntent = Intent(context, org.shilpo.laboon.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val forwardingPlayer = object : ForwardingPlayer(exo) {
                override fun play() {
                    resume()
                }

                override fun pause() {
                    this@PlaybackManagerImpl.pause()
                }

                override fun setPlayWhenReady(playWhenReady: Boolean) {
                    if (playWhenReady) {
                        resume()
                    } else {
                        this@PlaybackManagerImpl.pause()
                    }
                }
            }
            val session = MediaSession.Builder(context, forwardingPlayer)
                .setSessionActivity(pendingIntent)
                .build()
            mediaSession = session
            PlaybackServiceHolder.mediaSession = session
            PlaybackServiceHolder.service?.let { s ->
                runCatching { s.addSession(session) }
            }
        } catch (_: Exception) {
        }

        player = exo
    }

    override fun play(track: HomeTrack) {
        resolveJob?.cancel()
        fadeJob?.cancel()
        pendingFadeIn = false
        player?.let { exo ->
            if (exo.isPlaying) {
                exo.volume = 0f
                exo.pause()
            }
        }
        _state.value = _state.value.copy(
            currentTrack = track,
            isBuffering = true,
            isPlaying = false,
            currentPositionMs = 0L,
            progress = 0f,
            error = null,
        )

        val knownStream = track.streamUrl
        if (!knownStream.isNullOrBlank()) {
            val trackWithCodec = if (track.codec == null) track.copy(codec = "alac") else track
            _state.value = _state.value.copy(currentTrack = trackWithCodec)
            startPlayback(trackWithCodec, knownStream)
        } else {
            resolveJob = scope.launch {
                val resolution = searchRepository.resolvePlayback(track)
                if (resolution != null) {
                    val updatedTrack = track.copy(
                        streamUrl = resolution.streamUrl,
                        codec = resolution.codec ?: track.codec ?: "alac",
                    )
                    _state.value = _state.value.copy(currentTrack = updatedTrack)
                    startPlayback(updatedTrack, resolution.streamUrl)
                } else {
                    _state.value = _state.value.copy(
                        isBuffering = false,
                        error = "Unable to resolve stream",
                    )
                }
            }
        }
    }

    private fun startPlayback(track: HomeTrack, streamUrl: String) {
        val exo = player ?: return
        fadeJob?.cancel()
        pendingFadeIn = true
        exo.volume = 0f
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)

        track.album?.let { metadataBuilder.setAlbumTitle(it) }
        track.artworkUrl?.let { metadataBuilder.setArtworkUri(Uri.parse(it)) }

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.play()

        val serviceIntent = Intent(context, PlaybackService::class.java)
        try {
            context.startService(serviceIntent)
        } catch (_: Exception) {
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (_: Exception) {
            }
        }
    }

    private fun fadeIn(durationMs: Long = 300L) {
        val exo = player ?: return
        fadeJob?.cancel()
        val startVol = exo.volume.coerceIn(0f, 1f)
        val targetVol = 1.0f
        if (startVol >= targetVol) {
            exo.volume = targetVol
            return
        }
        val remainingRatio = (targetVol - startVol) / targetVol
        val effectiveDuration = (durationMs * remainingRatio).toLong().coerceAtLeast(50L)
        fadeJob = scope.launch(Dispatchers.Main) {
            val stepInterval = 16L
            val totalSteps = (effectiveDuration / stepInterval).coerceAtLeast(1)
            val volStep = (targetVol - startVol) / totalSteps
            var currentVol = startVol
            for (step in 1..totalSteps) {
                delay(stepInterval)
                if (!isActive) break
                currentVol = (currentVol + volStep).coerceIn(0f, 1f)
                exo.volume = currentVol
            }
            exo.volume = targetVol
        }
    }

    private fun fadeOut(durationMs: Long = 250L, onComplete: (() -> Unit)? = null) {
        val exo = player ?: return
        fadeJob?.cancel()
        val startVol = exo.volume.coerceIn(0f, 1f)
        if (startVol <= 0.01f || !exo.isPlaying) {
            exo.pause()
            exo.volume = 0f
            onComplete?.invoke()
            return
        }
        val remainingRatio = startVol / 1.0f
        val effectiveDuration = (durationMs * remainingRatio).toLong().coerceAtLeast(50L)
        fadeJob = scope.launch(Dispatchers.Main) {
            val stepInterval = 16L
            val totalSteps = (effectiveDuration / stepInterval).coerceAtLeast(1)
            val volStep = startVol / totalSteps
            var currentVol = startVol
            for (step in 1..totalSteps) {
                delay(stepInterval)
                if (!isActive) break
                currentVol = (currentVol - volStep).coerceIn(0f, 1f)
                exo.volume = currentVol
            }
            exo.pause()
            exo.volume = 0f
            onComplete?.invoke()
        }
    }

    override fun pause() {
        val exo = player ?: return
        pendingFadeIn = false
        _state.value = _state.value.copy(isPlaying = false)
        fadeOut(250L)
    }

    override fun resume() {
        val exo = player ?: return
        pendingFadeIn = false
        fadeJob?.cancel()
        _state.value = _state.value.copy(isPlaying = true)
        if (exo.volume <= 0.02f) {
            exo.volume = 0f
        }
        exo.play()
        fadeIn(300L)
    }

    override fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying || _state.value.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    override fun seekTo(progress: Float) {
        val exo = player ?: return
        val dur = exo.duration
        if (dur > 0) {
            val targetMs = (dur * progress.coerceIn(0f, 1f)).toLong()
            exo.seekTo(targetMs)
            _state.value = _state.value.copy(
                currentPositionMs = targetMs,
                progress = progress,
            )
        }
    }

    override fun stop() {
        resolveJob?.cancel()
        fadeJob?.cancel()
        pendingFadeIn = false
        stopProgressTracker()
        val exo = player
        if (exo != null) {
            exo.volume = 0f
            exo.stop()
            exo.volume = 1f
        }
        _state.value = PlaybackState()
    }

    override fun release() {
        resolveJob?.cancel()
        fadeJob?.cancel()
        pendingFadeIn = false
        stopProgressTracker()
        val session = mediaSession
        if (session != null) {
            PlaybackServiceHolder.service?.let { s ->
                runCatching { s.removeSession(session) }
            }
            session.release()
        }
        mediaSession = null
        PlaybackServiceHolder.mediaSession = null
        player?.release()
        player = null
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val exo = player
                if (exo != null && exo.isPlaying) {
                    val pos = exo.currentPosition.coerceAtLeast(0L)
                    val dur = exo.duration.coerceAtLeast(0L)
                    val prog = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                    _state.value = _state.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        progress = prog,
                    )
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}
