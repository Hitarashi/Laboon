package org.shilpo.laboon.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.auth.SharedPreferencesKeyValueStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.lyrics.LyricsDiskCache
import org.shilpo.laboon.lyrics.LyricsLookup
import org.shilpo.laboon.lyrics.LyricsLookupResult
import org.shilpo.laboon.lyrics.LyricsRepository
import org.shilpo.laboon.lyrics.LyricsRepositoryImpl
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.rip.AutoRipCoordinator
import org.shilpo.laboon.rip.AutoRipSource
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchRepositoryImpl
import java.nio.ByteBuffer
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private fun resourceContentLength(responseHeaders: Map<String, List<String>>): Long? {
    val contentRange = responseHeaders.entries
        .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    if (contentRange != null) {
        return contentRange.substringAfterLast('/', "")
            .toLongOrNull()
            ?.takeIf { it > 0L }
    }

    return responseHeaders.entries
        .firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
        ?.value
        ?.firstOrNull()
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
}

interface PlaybackManager {
    val state: StateFlow<PlaybackState>
    val spectrumState: StateFlow<SpectrumFrame>
    val queueManager: QueueManager
    fun play(
        track: HomeTrack,
        contextTracks: List<HomeTrack>? = null,
        startPositionMs: Long? = null
    )

    fun playNext(track: HomeTrack)
    fun addToQueue(track: HomeTrack)
    fun playQueueEntry(entryId: Long)
    fun retryDiscovery()
    fun retryLyrics()
    fun skipToNext()
    fun skipToPrevious()
    fun pause()
    fun resume()
    fun togglePlayPause()
    fun seekTo(progress: Float)
    fun switchQualityVariant(track: HomeTrack, variant: TrackFormatVariant)
    fun requestMotionArtwork(track: HomeTrack)
    fun syncWithCurrentPlayer()
    fun stop()
    fun dismiss()
    fun release()
}

@OptIn(UnstableApi::class)
class PlaybackManagerImpl(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val searchRepository: SearchRepository = SearchRepositoryImpl(sessionStore),
    override val queueManager: QueueManager = QueueManagerImpl(
        persistence = QueuePersistence(SharedPreferencesKeyValueStore(context)),
    ),
    private val discoveryEngine: QueueDiscoveryEngine = QueueDiscoveryEngine(sessionStore),
    private val playbackPersistence: PlaybackPersistence = PlaybackPersistence(
        SharedPreferencesKeyValueStore(context),
    ),
    private val lyricsRepository: LyricsRepository = LyricsRepositoryImpl(
        lyricspornApiUrlProvider = { sessionStore.getSession()?.lyricspornApiUrl },
        diskCache = LyricsDiskCache(context),
    ),
    private val autoRipCoordinator: AutoRipCoordinator? = null,
    private val completedRipTrackIds: SharedFlow<String>? = null,
) : PlaybackManager {

    private data class PlayerAudioSinkState(
        @Volatile var inputFormat: Format? = null,
        @Volatile var audioTrackConfig: AudioSink.AudioTrackConfig? = null,
        @Volatile var inputIsPcm: Boolean = false,
    )

    private val spectrumVisualizer = SpectrumVisualizer()
    override val spectrumState: StateFlow<SpectrumFrame> = spectrumVisualizer.state

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(
        run {
            val lastTrack = playbackPersistence.getLastTrack()
            val lastPos = playbackPersistence.getLastPosition()
            val lastDur = playbackPersistence.getLastDuration()
            val isDismissed = playbackPersistence.isPlayerDismissed()
            if (!isDismissed && lastTrack != null) {
                val prog = if (lastDur > 0L) (lastPos.toFloat() / lastDur.toFloat()).coerceIn(
                    0f,
                    1f
                ) else 0f
                PlaybackState(
                    currentTrack = lastTrack,
                    isPlaying = false,
                    isBuffering = false,
                    currentPositionMs = lastPos,
                    durationMs = lastDur,
                    progress = prog,
                )
            } else {
                PlaybackState()
            }
        }
    )
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var sessionForwardingPlayer: Player? = null
    private var sessionCommandListeners = IdentityHashMap<Player.Listener, Player.Listener>()
    private var sessionCommandUpdateJob: Job? = null
    private val playerAudioSinkStates = ConcurrentHashMap<ExoPlayer, PlayerAudioSinkState>()
    private var progressJob: Job? = null
    private var resolveJob: Job? = null
    private var qualitySwitchJob: Job? = null
    private var qualitySwitchGeneration: Long = 0L
    private var qualitySwitchCandidate: ExoPlayer? = null
    private var qualityHandoffFadeJob: Job? = null
    private var qualityHandoffPreviousPlayer: ExoPlayer? = null
    private var pendingPlaybackMediaId: String? = null
    private var preloadJob: Job? = null
    private var precacheJob: Job? = null
    private var currentCacheWriter: CacheWriter? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null
    private var discoveryJob: Job? = null
    private var discoveryGeneration: Long = 0L
    private var waitingForAutoRipTrackId: String? = null
    private var currentArtworkLookupKey: String? = null
    private var fadeJob: Job? = null
    private var pendingFadeIn: Boolean = false
    private var retryJob: Job? = null
    private var retryCount: Int = 0
    private var cacheBypassKey: String? = null
    private var lyricsJob: Job? = null
    private var motionArtworkJob: Job? = null
    private var motionArtworkRequestedTrackId: String? = null
    private var lyricsGeneration = 0L
    private var lyricsRequestedGeneration = -1L
    private var metadataJob: Job? = null

    @Volatile
    private var currentDecoderName: String? = null

    @Volatile
    private var currentAudioFormat: Format? = null

    @Volatile
    private var sinkInputFormat: Format? = null

    @Volatile
    private var sinkAudioTrackConfig: AudioSink.AudioTrackConfig? = null

    @Volatile
    private var currentResourceLengthBytes: Long = 0L
    private val songCache by lazy { SongCache.getInstance(context) }

    init {
        initPlayer()
        _state.value.currentTrack?.let { track ->
            _state.value = _state.value.copy(lyricsLoading = true)
            requestLyrics(track, _state.value.durationMs)
            requestTrackArtwork(track)
            requestTrackMetadata(track)
        }
        scope.launch {
            queueManager.state.collect { qState ->
                syncPlayerModes(qState)
                if (qState.autoplaySuppressed && _state.value.discoveryStatus == DiscoveryStatus.LOADING) {
                    discoveryJob?.cancel()
                    discoveryGeneration += 1L
                    _state.value = _state.value.copy(
                        isDiscovering = false,
                        discoveryStatus = DiscoveryStatus.IDLE,
                    )
                }
                val autoRipTracks = buildList {
                    qState.currentTrack?.let(::add)
                    qState.upcomingEntries.forEach { add(it.track) }
                }
                autoRipCoordinator?.observe(AutoRipSource.PLAYBACK_QUEUE, autoRipTracks)
                qState.currentTrack?.let(::requestTrackArtwork)
                val queuedCurrent = qState.currentTrack
                if (queuedCurrent != null && _state.value.currentTrack?.id == queuedCurrent.id) {
                    if (_state.value.currentTrack != queuedCurrent) {
                        _state.value = _state.value.copy(currentTrack = queuedCurrent)
                    }
                    if (
                        waitingForAutoRipTrackId == queuedCurrent.id && queuedCurrent.isCached &&
                        queuedCurrent.backendTrackId != null
                    ) {
                        waitingForAutoRipTrackId = null
                        executePlayTrack(queuedCurrent)
                    }
                }
                _state.value = _state.value.copy(
                    canSkipNext = qState.hasNext,
                    canSkipPrevious = qState.hasPrevious,
                )
            }
        }
        if (autoRipCoordinator != null && completedRipTrackIds != null) {
            scope.launch {
                completedRipTrackIds.collect { providerTrackId ->
                    val availability =
                        autoRipCoordinator.refreshAvailabilityNow(providerTrackId).cached
                    if (availability.isNotEmpty()) queueManager.applyAvailability(availability)
                }
            }
        }
    }

    private fun syncPlayerModes(qState: QueueState) {
        val exo = player ?: return
        // QueueManager owns context repeats; Media3 only repeats the current item for Repeat One.
        val targetRepeatMode = if (qState.repeatMode == RepeatMode.ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        if (exo.repeatMode != targetRepeatMode) {
            exo.repeatMode = targetRepeatMode
        }
        if (exo.shuffleModeEnabled) {
            exo.shuffleModeEnabled = false
        }
    }

    private fun requestTrackArtwork(track: HomeTrack) {
        if (!track.artworkUrl.isNullOrBlank()) {
            currentArtworkLookupKey = null
            return
        }
        val lyricspornApiUrl = sessionStore.getSession()?.lyricspornApiUrl
        if (lyricspornApiUrl.isNullOrBlank()) return
        val lookupKey = LyricspornClient.normalizedArtworkKey(
            track.title,
            track.artist,
            lyricspornApiUrl,
        )
        if (currentArtworkLookupKey == lookupKey) return
        currentArtworkLookupKey = lookupKey

        scope.launch {
            val artworkUrl = LyricspornClient.resolveTrackArtwork(
                apiBaseUrl = lyricspornApiUrl,
                title = track.title,
                artist = track.artist,
                album = track.album,
            ) ?: return@launch
            if (currentArtworkLookupKey != lookupKey) return@launch

            _state.update { current ->
                val currentTrack = current.currentTrack ?: return@update current
                if (LyricspornClient.normalizedArtworkKey(
                        currentTrack.title,
                        currentTrack.artist,
                        sessionStore.getSession()?.lyricspornApiUrl,
                    ) != lookupKey
                ) {
                    return@update current
                }
                current.copy(currentTrack = currentTrack.copy(artworkUrl = artworkUrl))
            }
            _state.value.currentTrack
                ?.takeIf {
                    LyricspornClient.normalizedArtworkKey(
                        it.title,
                        it.artist,
                        sessionStore.getSession()?.lyricspornApiUrl,
                    ) == lookupKey &&
                            it.artworkUrl == artworkUrl
                }
                ?.let(playbackPersistence::saveLastTrack)
        }
    }

    private fun requestTrackMetadata(track: HomeTrack) {
        if (!track.album.isNullOrBlank() &&
            !track.providerTrackId.isNullOrBlank() &&
            !track.artworkUrl.isNullOrBlank()
        ) {
            return
        }
        val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
            ?.takeIf(String::isNotBlank) ?: return

        metadataJob?.cancel()
        metadataJob = scope.launch {
            val appleTrackId =
                track.providerTrackId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            var resolvedAlbum = track.album?.takeIf(String::isNotBlank)
            var resolvedProviderTrackId = appleTrackId
            var resolvedArtworkUrl = track.artworkUrl?.takeIf(String::isNotBlank)

            if (resolvedAlbum == null && appleTrackId != null) {
                resolvedAlbum = LyricspornClient.getTrackAlbumName(apiBaseUrl, appleTrackId)
            }

            if (resolvedProviderTrackId == null || resolvedArtworkUrl == null || resolvedAlbum == null) {
                val match = LyricspornClient.resolveTrackCatalogItem(
                    apiBaseUrl = apiBaseUrl,
                    title = track.title,
                    artist = track.artist,
                    album = resolvedAlbum,
                    durationMs = track.durationMs,
                )?.item
                if (match != null) {
                    if (resolvedProviderTrackId == null && match.id.all(Char::isDigit)) {
                        resolvedProviderTrackId = match.id
                    }
                    if (resolvedAlbum == null) {
                        resolvedAlbum = match.albumName
                    }
                    if (resolvedArtworkUrl == null) {
                        resolvedArtworkUrl = match.artworkUrl
                    }
                }
            }

            if (resolvedAlbum == null && resolvedProviderTrackId != null) {
                resolvedAlbum =
                    LyricspornClient.getTrackAlbumName(apiBaseUrl, resolvedProviderTrackId)
            }

            if (!isActive) return@launch

            val isUpdated = resolvedAlbum != track.album ||
                    resolvedProviderTrackId != track.providerTrackId ||
                    resolvedArtworkUrl != track.artworkUrl

            if (isUpdated) {
                val currentTrack = _state.value.currentTrack
                if (currentTrack?.id == track.id) {
                    val updatedTrack = currentTrack.copy(
                        album = resolvedAlbum ?: currentTrack.album,
                        providerTrackId = resolvedProviderTrackId ?: currentTrack.providerTrackId,
                        artworkUrl = resolvedArtworkUrl ?: currentTrack.artworkUrl,
                    )
                    _state.update { current ->
                        if (current.currentTrack?.id == track.id) {
                            current.copy(currentTrack = updatedTrack)
                        } else {
                            current
                        }
                    }
                    queueManager.updateCurrentTrack(updatedTrack)
                    playbackPersistence.saveLastTrack(updatedTrack)
                }
            }
        }
    }

    override fun requestMotionArtwork(track: HomeTrack) {
        if (track.providerTrackId.isNullOrBlank() &&
            (track.title.isBlank() || track.artist.isBlank())
        ) {
            return
        }
        if (motionArtworkRequestedTrackId == track.id) return
        val currentTrack = _state.value.currentTrack
        if (currentTrack != null && currentTrack.id != track.id) return
        val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
            ?.takeIf(String::isNotBlank)
            ?: return

        motionArtworkJob?.cancel()
        motionArtworkRequestedTrackId = track.id
        _state.update { current ->
            if (current.currentTrack != null && current.currentTrack.id != track.id) {
                current
            } else {
                current.copy(motionArtwork = null, motionArtworkTrackId = track.id)
            }
        }
        motionArtworkJob = scope.launch {
            val artwork = LyricspornClient.resolveTrackMotionArtwork(
                apiBaseUrl = apiBaseUrl,
                appleTrackId = track.providerTrackId,
                title = track.title,
                artist = track.artist,
                album = track.album,
            )
            if (!isActive || motionArtworkRequestedTrackId != track.id) return@launch
            _state.update { current ->
                if (current.motionArtworkTrackId != track.id ||
                    (current.currentTrack != null && current.currentTrack.id != track.id)
                ) {
                    current
                } else {
                    current.copy(motionArtwork = artwork)
                }
            }
        }
    }

    private fun buildPlayer(handleAudioFocus: Boolean = true): ExoPlayer {
        var owner: ExoPlayer? = null
        val audioSinkState = PlayerAudioSinkState()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                val defaultSink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .build()

                return object : ForwardingAudioSink(defaultSink) {
                    private var visualizedBuffer: ByteBuffer? = null
                    private var inputIsPcm = false

                    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
                        super.configure(audioSinkConfig)
                        inputIsPcm = audioSinkConfig.format.sampleMimeType == MimeTypes.AUDIO_RAW
                        audioSinkState.inputFormat = audioSinkConfig.format
                        audioSinkState.inputIsPcm = inputIsPcm
                        visualizedBuffer = null
                        if (player === owner) {
                            sinkInputFormat = audioSinkConfig.format
                            spectrumVisualizer.sink.flush(
                                audioSinkConfig.format.sampleRate,
                                audioSinkConfig.format.channelCount,
                                audioSinkConfig.format.pcmEncoding,
                            )
                            owner?.let { active -> scope.launch { updateAudioQuality(active) } }
                        }
                    }

                    override fun handleBuffer(
                        buffer: ByteBuffer,
                        presentationTimeUs: Long,
                        encodedAccessUnitCount: Int,
                    ): Boolean {

                        if (player === owner && inputIsPcm && visualizedBuffer !== buffer) {
                            spectrumVisualizer.sink.handleBuffer(buffer)
                            visualizedBuffer = buffer
                        }
                        val consumed =
                            super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
                        if (consumed) visualizedBuffer = null
                        return consumed
                    }

                    override fun flush() {
                        super.flush()
                        visualizedBuffer = null
                        if (player === owner) spectrumVisualizer.reset()
                    }

                    override fun reset() {
                        super.reset()
                        visualizedBuffer = null
                        if (player === owner) spectrumVisualizer.reset()
                    }

                    override fun setListener(listener: AudioSink.Listener) {
                        super.setListener(object : AudioSink.Listener by listener {
                            override fun onAudioTrackInitialized(audioTrackConfig: AudioSink.AudioTrackConfig) {
                                listener.onAudioTrackInitialized(audioTrackConfig)
                                audioSinkState.audioTrackConfig = audioTrackConfig
                                if (player === owner) {
                                    sinkAudioTrackConfig = audioTrackConfig
                                    owner?.let { active -> scope.launch { updateAudioQuality(active) } }
                                }
                            }
                        })
                    }
                }
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setAudioOffloadPreferences(
                        TrackSelectionParameters.AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                            .build()
                    )
            )
        }

        val upstreamHttpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

        val songCache = this.songCache
        val factory = CacheDataSource.Factory()
            .setCache(songCache)
            .setUpstreamDataSourceFactory(upstreamHttpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        cacheDataSourceFactory = factory

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(factory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                60_000,
                300_000,
                2_000,
                5_000,
            )
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exo = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(playbackAudioAttributes(), handleAudioFocus)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
        owner = exo
        playerAudioSinkStates[exo] = audioSinkState
        return exo
    }

    private fun playbackAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
        .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
        .build()

    private fun attachPlayerListeners(exo: ExoPlayer) {
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                if (player === exo && exo.currentMediaItem?.mediaId == currentQueueMediaId()) {
                    currentDecoderName = decoderName
                    updateAudioQuality(exo)
                }
            }

            override fun onLoadCompleted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
                if (player === exo && exo.currentMediaItem?.mediaId == currentQueueMediaId()) {
                    val resourceLength = resourceContentLength(loadEventInfo.responseHeaders)
                    if (resourceLength != null && resourceLength > currentResourceLengthBytes) {
                        currentResourceLengthBytes = resourceLength
                        updateAudioQuality(exo)
                    }
                }
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                if (player === exo && exo.currentMediaItem?.mediaId == currentQueueMediaId()) {
                    currentAudioFormat = format
                    updateAudioQuality(exo)
                }
            }
        })

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player !== exo) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _state.value = _state.value.copy(isBuffering = true)
                    }

                    Player.STATE_READY -> {
                        val pendingMediaId = pendingPlaybackMediaId
                        if ((pendingMediaId != null && exo.currentMediaItem?.mediaId != pendingMediaId) ||
                            isQualitySwitchPreparing(exo)
                        ) {
                            return
                        }
                        syncTrackStateWithPlayer(exo)
                        retryCount = 0
                        val dur = exo.duration.coerceAtLeast(0L)
                        val pos = exo.currentPosition.coerceAtLeast(0L)
                        val prog =
                            if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                        _state.value = _state.value.copy(
                            isBuffering = false,
                            durationMs = dur,
                            currentPositionMs = pos,
                            progress = prog,
                            switchingQualityFormat = null,
                        )
                        _state.value.currentTrack?.let { track -> requestLyrics(track, dur) }
                        updateAudioQuality(exo)
                    }

                    Player.STATE_ENDED -> {
                        fadeJob?.cancel()
                        pendingFadeIn = false
                        exo.volume = 1f
                        if (_state.value.switchingQualityFormat != null ||
                            pendingPlaybackMediaId != null
                        ) {
                            _state.value = _state.value.copy(
                                isPlaying = false,
                                isBuffering = true,
                                currentPositionMs = exo.duration.coerceAtLeast(0L),
                                progress = 1f,
                            )
                            stopProgressTracker()
                        } else if (queueManager.state.value.hasNext) {
                            skipToNext()
                        } else if (_state.value.discoveryStatus == DiscoveryStatus.LOADING) {
                            _state.value = _state.value.copy(
                                isPlaying = false,
                                isBuffering = true,
                                currentPositionMs = exo.duration.coerceAtLeast(0L),
                                progress = 1f,
                            )
                            stopProgressTracker()
                        } else {
                            _state.value = _state.value.copy(
                                isPlaying = false,
                                isBuffering = false,
                                progress = 1.0f,
                            )
                            stopProgressTracker()
                        }
                    }

                    Player.STATE_IDLE -> {
                        _state.value = _state.value.copy(isBuffering = false)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player !== exo) return
                when (reason) {

                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                        -> if (mediaItem != null) {
                        followPlayerTransition(exo, mediaItem.mediaId)
                    }

                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> {
                        if (mediaItem != null && mediaItem.mediaId != currentQueueMediaId()) {
                            followPlayerTransition(exo, mediaItem.mediaId)
                        } else {
                            _state.value = _state.value.copy(
                                currentPositionMs = 0L,
                                progress = 0f,
                            )
                        }
                    }

                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player !== exo) return
                syncTrackStateWithPlayer(exo)
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
                if (player !== exo) return
                if (exo.currentMediaItem?.mediaId == currentQueueMediaId()) {
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (i in 0 until group.length) {
                                if (group.isTrackSelected(i)) {
                                    currentAudioFormat = group.getTrackFormat(i)
                                    break
                                }
                            }
                        }
                    }
                    updateAudioQuality(exo)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (player !== exo) return
                fadeJob?.cancel()
                pendingFadeIn = false
                val failedTrack = _state.value.currentTrack
                val failedTrackId = failedTrack?.id
                val failedCacheKey = failedTrack?.let(::cacheKey)
                val cacheRecoveryAvailable =
                    isCacheIntegrityError(error) && failedCacheKey != null &&
                            cacheBypassKey != failedCacheKey
                val isTransient = shouldRetryPlaybackError(error) || cacheRecoveryAvailable
                if (isTransient && retryCount < MAX_RETRIES) {
                    retryCount++
                    val backoffMs = 1000L * retryCount
                    if (cacheRecoveryAvailable) {
                        cacheBypassKey = failedCacheKey
                    }
                    _state.value = _state.value.copy(
                        isBuffering = true,
                        isPlaying = false,
                        error = null,
                    )
                    retryJob?.cancel()
                    retryJob = scope.launch {
                        delay(backoffMs)
                        val track = _state.value.currentTrack ?: return@launch
                        if (track.id != failedTrackId || cacheKey(track) != failedCacheKey) return@launch
                        val resolution = searchRepository.resolvePlayback(track)
                        if (resolution != null) {
                            val updatedTrack = track.copy(
                                streamUrl = resolution.streamUrl,
                                codec = resolution.codec ?: track.codec,
                            )
                            if (cacheRecoveryAvailable) {
                                withContext(Dispatchers.IO) {
                                    runCatching { songCache.removeResource(cacheKey(track)) }
                                }
                            }
                            _state.value = _state.value.copy(currentTrack = updatedTrack)
                            val currentPos = player?.currentPosition
                                ?.takeIf { it > 0L }
                                ?: _state.value.currentPositionMs
                            startPlayback(
                                updatedTrack,
                                resolution.streamUrl,
                                currentPos,
                                bypassCache = cacheBypassKey == cacheKey(track),
                            )
                        } else {
                            _state.value = _state.value.copy(
                                isPlaying = false,
                                isBuffering = false,
                                error = "Unable to resolve stream after retry",
                                switchingQualityFormat = null,
                            )
                            stopProgressTracker()
                        }
                    }
                } else {
                    retryCount = 0
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = error.message,
                        switchingQualityFormat = null,
                    )
                    stopProgressTracker()
                }
            }
        })
    }

    private fun createSessionForwardingPlayer(exo: ExoPlayer): Player {
        val commandListeners = IdentityHashMap<Player.Listener, Player.Listener>()
        val forwardingPlayer = object : ForwardingPlayer(exo) {
            override fun addListener(listener: Player.Listener) {
                commandListeners[listener] = listener
                super.addListener(listener)
            }

            override fun removeListener(listener: Player.Listener) {
                if (commandListeners.remove(listener) != null) {
                    super.removeListener(listener)
                }
            }

            override fun getAvailableCommands(): Player.Commands {
                val queueState = queueManager.state.value
                val commands = super.getAvailableCommands().buildUpon()
                if (queueState.hasPrevious) {
                    commands.add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    commands.add(COMMAND_SEEK_TO_PREVIOUS)
                } else {
                    commands.remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    commands.remove(COMMAND_SEEK_TO_PREVIOUS)
                }
                if (queueState.hasNext) {
                    commands.add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    commands.add(COMMAND_SEEK_TO_NEXT)
                } else {
                    commands.remove(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    commands.remove(COMMAND_SEEK_TO_NEXT)
                }
                return commands.build()
            }

            override fun isCommandAvailable(command: Int): Boolean = when (command) {
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS -> queueManager.state.value.hasPrevious

                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_NEXT -> queueManager.state.value.hasNext

                else -> super.isCommandAvailable(command)
            }

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

            override fun seekToNext() {
                skipToNext()
            }

            override fun seekToPrevious() {
                skipToPrevious()
            }

            override fun seekToPreviousMediaItem() {
                skipToPrevious()
            }

            override fun seekToNextMediaItem() {
                skipToNext()
            }
        }
        sessionCommandListeners = commandListeners
        sessionForwardingPlayer = forwardingPlayer
        return forwardingPlayer
    }

    private fun startSessionCommandUpdates() {
        if (sessionCommandUpdateJob?.isActive == true) return
        sessionCommandUpdateJob = scope.launch {
            var previousCommands = sessionForwardingPlayer?.availableCommands
            queueManager.state.collect {
                val forwardingPlayer = sessionForwardingPlayer ?: return@collect
                val commands = forwardingPlayer.availableCommands
                if (commands == previousCommands) return@collect
                previousCommands = commands
                val events = Player.Events(
                    FlagSet.Builder().add(Player.EVENT_AVAILABLE_COMMANDS_CHANGED).build(),
                )
                sessionCommandListeners.values.toList().forEach { listener ->
                    listener.onAvailableCommandsChanged(commands)
                    listener.onEvents(forwardingPlayer, events)
                }
            }
        }
    }

    private fun initPlayer() {
        if (player != null) return
        val exo = buildPlayer()
        player = exo
        attachPlayerListeners(exo)

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
            val forwardingPlayer = createSessionForwardingPlayer(exo)
            val session = MediaSession.Builder(context, forwardingPlayer)
                .setSessionActivity(pendingIntent)
                .build()
            mediaSession = session
            PlaybackServiceHolder.mediaSession = session
            PlaybackServiceHolder.service?.let { s ->
                runCatching { s.addSession(session) }
            }
            startSessionCommandUpdates()
        } catch (_: Exception) {
        }

        player = exo
    }

    override fun play(track: HomeTrack, contextTracks: List<HomeTrack>?, startPositionMs: Long?) {
        queueManager.play(track, contextTracks)
        executePlayTrack(track, startPositionMs ?: 0L)
        _state.value = _state.value.copy(discoveryStatus = DiscoveryStatus.IDLE)
        scheduleDiscoveryRefillIfNeeded()
    }

    override fun playNext(track: HomeTrack) {
        queueManager.playNext(track)
        schedulePreloadNext()
    }

    override fun addToQueue(track: HomeTrack) {
        queueManager.addToQueue(track)
        schedulePreloadNext()
    }

    override fun retryDiscovery() {
        queueManager.retryAutoplay()
        _state.value = _state.value.copy(error = null, discoveryStatus = DiscoveryStatus.IDLE)
        scheduleDiscoveryRefillIfNeeded(force = true)
    }

    override fun retryLyrics() {
        val state = _state.value
        val track = state.currentTrack ?: return
        if (state.lyricsLoading) return
        invalidateLyricsRequest()
        _state.value = state.copy(
            lyricsLines = emptyList(),
            lyricsProvider = null,
            lyricsLoading = true,
            lyricsFailed = false,
        )
        requestLyrics(track, state.durationMs)
    }

    override fun playQueueEntry(entryId: Long) {
        val track = queueManager.playEntry(entryId) ?: return
        executePlayTrack(track)
        scheduleDiscoveryRefillIfNeeded()
    }

    override fun skipToNext() {
        val exo = player ?: return
        val queueState = queueManager.state.value
        if (queueState.repeatMode != RepeatMode.ONE &&
            exo.mediaItemCount > 1 && exo.currentMediaItemIndex < exo.mediaItemCount - 1
        ) {
            val expectedMediaId = queueManager.peekNextEntry()?.let(::mediaIdForEntry)
            val playerNextMediaId = exo.getMediaItemAt(exo.currentMediaItemIndex + 1).mediaId
            if (expectedMediaId == playerNextMediaId) {
                exo.seekToNextMediaItem()
            } else {
                playQueuedNextTrack()
            }
        } else {
            // Explicit Next advances even when Repeat One is active.
            playQueuedNextTrack()
        }
    }

    private fun resetPipelineForTrack() {
        currentAudioFormat = null
        sinkInputFormat = null
        sinkAudioTrackConfig = null
        currentDecoderName = null
        currentResourceLengthBytes = 0L
    }

    private fun syncTrackStateWithPlayer(exo: ExoPlayer) {
        val mediaId = exo.currentMediaItem?.mediaId ?: return
        val pendingMediaId = pendingPlaybackMediaId
        if (pendingMediaId != null) {
            if (mediaId != pendingMediaId) return
            pendingPlaybackMediaId = null
        }
        if (mediaId == currentQueueMediaId()) return
        followPlayerTransition(exo, mediaId)
    }

    private fun isQualitySwitchPreparing(exo: ExoPlayer): Boolean {
        val state = _state.value
        val format = state.switchingQualityFormat ?: return false
        val track = state.currentTrack ?: return false
        val targetTrackId = track.availableVariants.firstOrNull {
            it.format.equals(format, ignoreCase = true)
        }?.backendTrackId ?: return true
        if (track.backendTrackId != targetTrackId) return true
        val streamUrl = track.streamUrl ?: return false
        val loadedStreamUrl = exo.currentMediaItem?.localConfiguration?.uri?.toString()
        return loadedStreamUrl != streamUrl
    }

    override fun syncWithCurrentPlayer() {
        player?.let(::syncTrackStateWithPlayer)
    }

    private fun followPlayerTransition(exo: ExoPlayer, transitionedMediaId: String) {
        if (exo.currentMediaItem?.mediaId != transitionedMediaId) return
        val pendingMediaId = pendingPlaybackMediaId
        if (pendingMediaId != null && transitionedMediaId != pendingMediaId) return
        if (pendingMediaId == transitionedMediaId) pendingPlaybackMediaId = null
        if (transitionedMediaId == currentQueueMediaId()) return

        val nextTrack = queueManager.selectCurrentById(transitionedMediaId)
        if (nextTrack == null) return
        val trackChanged = nextTrack.id != _state.value.currentTrack?.id
        if (trackChanged) {
            invalidateLyricsRequest()
            qualitySwitchJob?.cancel()
            qualitySwitchJob = null
            qualitySwitchGeneration += 1L
            releaseQualitySwitchCandidate()
            cancelQualityHandoff()
        }
        resetPipelineForTrack()
        val initialQuality = resolveQualityFromMetadata(nextTrack)
        _state.value = _state.value.copy(
            currentTrack = nextTrack,
            audioQuality = initialQuality,
            currentPositionMs = 0L,
            progress = 0f,
            lyricsLines = if (trackChanged) emptyList() else _state.value.lyricsLines,
            lyricsProvider = if (trackChanged) null else _state.value.lyricsProvider,
            lyricsLoading = trackChanged || _state.value.lyricsLoading,
            lyricsFailed = if (trackChanged) false else _state.value.lyricsFailed,
            switchingQualityFormat = null,
        )
        playbackPersistence.saveLastTrack(nextTrack)
        if (trackChanged) {
            requestLyrics(nextTrack, 0L)
            requestTrackMetadata(nextTrack)
        }
        if (exo.mediaItemCount > 1 && exo.currentMediaItemIndex > 0) {
            exo.removeMediaItem(0)
        }
        schedulePreloadNext()
        scheduleDiscoveryRefillIfNeeded()
    }

    private fun playQueuedNextTrack() {
        val nextTrack = queueManager.advanceToNext()
        if (nextTrack != null) {
            executePlayTrack(nextTrack)
            scheduleDiscoveryRefillIfNeeded()
        } else {
            _state.value = _state.value.copy(isPlaying = false, progress = 1.0f)
        }
    }

    override fun skipToPrevious() {
        if (player == null) return
        val previousEntryId = queueManager.state.value.currentEntry?.id
        val previousTrack = queueManager.advanceToPrevious() ?: return
        val nextEntryId = queueManager.state.value.currentEntry?.id
        if (nextEntryId != null && nextEntryId != previousEntryId) {
            executePlayTrack(previousTrack)
        }
    }

    private fun executePlayTrack(track: HomeTrack, startPositionMs: Long = 0L) {
        waitingForAutoRipTrackId = null
        pendingPlaybackMediaId = currentQueueMediaId(track)
        invalidateLyricsRequest()
        resolveJob?.cancel()
        qualitySwitchJob?.cancel()
        qualitySwitchJob = null
        qualitySwitchGeneration += 1L
        releaseQualitySwitchCandidate()
        cancelQualityHandoff()
        preloadJob?.cancel()
        cancelPrecache()
        fadeJob?.cancel()
        retryJob?.cancel()
        retryCount = 0
        cacheBypassKey = null
        pendingFadeIn = false
        val retainedMotionArtwork = _state.value.motionArtwork
            .takeIf { _state.value.motionArtworkTrackId == track.id }
        val keepMotionArtworkRequest = motionArtworkRequestedTrackId == track.id
        if (!keepMotionArtworkRequest) {
            motionArtworkJob?.cancel()
            motionArtworkJob = null
            motionArtworkRequestedTrackId = null
        }
        player?.let { exo ->
            if (exo.isPlaying) {
                exo.volume = 0f
                exo.pause()
            }
        }
        resetPipelineForTrack()
        val initialQuality = resolveQualityFromMetadata(track)
        val dur = _state.value.durationMs.takeIf { it > 0L }
            ?: playbackPersistence.getLastDuration().coerceAtLeast(0L)
        val initialProg = if (startPositionMs > 0 && dur > 0) {
            (startPositionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        _state.value = _state.value.copy(
            currentTrack = track,
            audioQuality = initialQuality,
            isBuffering = true,
            isPlaying = false,
            currentPositionMs = startPositionMs,
            durationMs = dur,
            progress = initialProg,
            error = null,
            switchingQualityFormat = null,
            lyricsLines = emptyList(),
            lyricsProvider = null,
            lyricsLoading = true,
            lyricsFailed = false,
            motionArtwork = retainedMotionArtwork,
            motionArtworkTrackId = track.id.takeIf {
                retainedMotionArtwork != null || keepMotionArtworkRequest
            },
        )
        requestLyrics(track, 0L)
        requestTrackMetadata(track)
        playbackPersistence.saveLastTrack(track)
        playbackPersistence.setPlayerDismissed(false)
        if (startPositionMs > 0L) {
            playbackPersistence.saveLastPosition(startPositionMs)
        }
        if (dur > 0L) {
            playbackPersistence.saveLastDuration(dur)
        }

        val knownStream = track.streamUrl
        if (!knownStream.isNullOrBlank()) {
            _state.value = _state.value.copy(currentTrack = track)
            startPlayback(track, knownStream, startPositionMs)
            schedulePreloadNext()
        } else {
            resolveJob = scope.launch {
                val resolution = searchRepository.resolvePlayback(track)
                if (resolution != null) {
                    val updatedTrack = track.copy(
                        streamUrl = resolution.streamUrl,
                        codec = resolution.codec ?: track.codec,
                    )
                    _state.value = _state.value.copy(currentTrack = updatedTrack)
                    startPlayback(updatedTrack, resolution.streamUrl, startPositionMs)
                    schedulePreloadNext()
                } else {
                    if (!track.providerTrackId.isNullOrBlank() && !track.isCached) {
                        val ripCompletedTrack = queueManager.state.value.currentTrack
                            ?.takeIf { it.id == track.id && it.isCached && it.backendTrackId != null }
                        if (ripCompletedTrack != null) {
                            scope.launch { executePlayTrack(ripCompletedTrack) }
                        } else {
                            waitingForAutoRipTrackId = track.id
                            _state.value = _state.value.copy(isBuffering = true, error = null)
                        }
                    } else {
                        _state.value = _state.value.copy(
                            isBuffering = false,
                            error = "Unable to resolve stream",
                        )
                    }
                }
            }
        }
    }

    private fun invalidateLyricsRequest() {
        lyricsJob?.cancel()
        lyricsGeneration += 1L
        lyricsRequestedGeneration = -1L
    }

    private fun requestLyrics(track: HomeTrack, durationMs: Long) {
        val generation = lyricsGeneration
        if (lyricsRequestedGeneration == generation) return
        lyricsRequestedGeneration = generation
        lyricsJob = scope.launch {
            val result = try {
                lyricsRepository.lookup(
                    LyricsLookup(
                        title = track.title,
                        artists = listOf(track.artist),
                        album = track.album,
                        durationSeconds = durationMs.takeIf { it > 0L }?.div(1_000L),
                        appleTrackId = track.providerTrackId,
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                LyricsLookupResult.Failed
            }
            if (generation != lyricsGeneration || _state.value.currentTrack?.id != track.id) return@launch
            val foundLyrics = (result as? LyricsLookupResult.Found)?.lyrics
            _state.value = _state.value.copy(
                lyricsLines = foundLyrics?.lines.orEmpty(),
                lyricsProvider = foundLyrics?.provider,
                lyricsLoading = false,
                lyricsFailed = result is LyricsLookupResult.Failed,
            )
        }
    }

    private fun schedulePreloadNext() {
        if (queueManager.state.value.isShuffle ||
            queueManager.state.value.repeatMode == RepeatMode.ONE
        ) {
            preloadJob?.cancel()
            cancelPrecache()
            return
        }
        preloadJob?.cancel()
        preloadJob = scope.launch {
            delay(400)
            if (player == null) return@launch
            val nextEntry = queueManager.peekNextEntry() ?: run {
                cancelPrecache()
                return@launch
            }
            val nextTrack = nextEntry.track

            val streamUrl = if (!nextTrack.streamUrl.isNullOrBlank()) {
                nextTrack.streamUrl
            } else {
                searchRepository.resolvePlayback(nextTrack)?.streamUrl
            } ?: run {
                cancelPrecache()
                return@launch
            }

            if (!isActive) return@launch
            ArtworkUrlHelper.preload(context, nextTrack.artworkUrl)
            startPrecacheNext(nextEntry, streamUrl)
        }
    }

    private fun cacheKey(track: HomeTrack): String =
        track.backendTrackId?.let { "${track.id}:$it" } ?: track.id

    private fun isTrackFullyCached(track: HomeTrack): Boolean {
        return try {
            val key = cacheKey(track)
            val currentTrackLength = currentResourceLengthBytes
                .takeIf { _state.value.currentTrack?.let(::cacheKey) == key && it > 0L }
            val cachedLength =
                ContentMetadata.getContentLength(songCache.getContentMetadata(key))
            val length = currentTrackLength ?: cachedLength
            length > 0L && songCache.isCached(key, 0L, length)
        } catch (_: Exception) {
            false
        }
    }

    private fun shouldRetryPlaybackError(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        var hasSocketFailure = false
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                val statusCode = cause.responseCode
                return statusCode == 401 || statusCode == 408 || statusCode == 429 ||
                        statusCode in 500..599
            }
            if (cause is java.net.SocketTimeoutException || cause is java.net.SocketException) {
                hasSocketFailure = true
            }
            cause = cause.cause
        }

        return hasSocketFailure ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    }

    private fun isCacheIntegrityError(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is DataSourceException &&
                cause.reason == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
            ) {
                return true
            }
            if (cause is IllegalStateException && cause.stackTrace.any {
                    it.className == "androidx.media3.datasource.cache.SimpleCache" &&
                            it.methodName == "commitFile"
                }
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun startPrecacheNext(nextEntry: QueueEntry, streamUrl: String) {
        cancelPrecache()
        val track = nextEntry.track
        val factory = cacheDataSourceFactory ?: return
        val currentTrack = _state.value.currentTrack ?: return
        val currentTrackCacheKey = cacheKey(currentTrack)
        precacheJob = scope.launch(Dispatchers.IO) {
            while (isActive && _state.value.currentTrack?.let(::cacheKey) == currentTrackCacheKey) {
                if (isTrackFullyCached(currentTrack)) {
                    break
                }
                delay(1000)
            }
            if (!isActive || _state.value.currentTrack?.let(::cacheKey) != currentTrackCacheKey) return@launch
            try {
                val cacheDataSource = factory.createDataSource()
                val dataSpec = DataSpec.Builder()
                    .setUri(Uri.parse(streamUrl))
                    .setKey(cacheKey(track))
                    .build()
                val writer = CacheWriter(cacheDataSource, dataSpec, null, null)
                currentCacheWriter = writer
                writer.cache()
                withContext(Dispatchers.Main.immediate) {
                    if (!isActive || _state.value.currentTrack?.let(::cacheKey) != currentTrackCacheKey) {
                        return@withContext
                    }
                    val exo = player ?: return@withContext
                    if (queueManager.peekNextEntry()?.id == nextEntry.id && exo.mediaItemCount <= 1) {
                        exo.addMediaItem(
                            buildMediaItem(track, streamUrl, mediaIdForEntry(nextEntry)),
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                currentCacheWriter = null
            }
        }
    }

    private fun cancelPrecache() {
        precacheJob?.cancel()
        precacheJob = null
        val writer = currentCacheWriter
        currentCacheWriter = null
        if (writer != null) {
            runCatching { writer.cancel() }
        }
    }

    private fun scheduleDiscoveryRefillIfNeeded(force: Boolean = false) {
        discoveryJob?.cancel()
        val generation = ++discoveryGeneration
        val qState = queueManager.state.value
        if (qState.autoplaySuppressed || (!force && qState.upNextCount > DISCOVERY_REFILL_THRESHOLD)) {
            _state.value = _state.value.copy(
                isDiscovering = false,
                discoveryStatus = DiscoveryStatus.IDLE,
            )
            return
        }
        val seeds = queueManager.discoverySeedTracks()
        if (seeds.isEmpty()) {
            _state.value = _state.value.copy(
                isDiscovering = false,
                discoveryStatus = DiscoveryStatus.IDLE,
            )
            return
        }

        _state.value = _state.value.copy(
            isDiscovering = true,
            discoveryStatus = DiscoveryStatus.LOADING,
        )
        discoveryJob = scope.launch {
            var result: QueueDiscoveryResult? = null
            try {
                val stateAtStart = queueManager.state.value
                val queuedKeys = buildSet {
                    stateAtStart.currentTrack?.let { track ->
                        add(TrackIdentity.keyOf(track))
                        add(TrackIdentity.keyOf(null, track.title, track.artist))
                    }
                    stateAtStart.playbackUpcomingEntries.forEach { entry ->
                        add(TrackIdentity.keyOf(entry.track))
                        add(TrackIdentity.keyOf(null, entry.track.title, entry.track.artist))
                    }
                    addAll(stateAtStart.dismissedAutoplayKeys)
                }
                val exclude = queueManager.getRecentHistoryKeys() + queuedKeys
                result =
                    discoveryEngine.discoverNextTracks(seeds, exclude, limit = 6) { discovered ->
                        withContext(Dispatchers.Main.immediate) {
                            if (isActive && generation == discoveryGeneration) {
                                queueManager.appendDiscovery(discovered)
                                val exo = player
                                if (
                                    exo?.playbackState == Player.STATE_ENDED && exo.playWhenReady &&
                                    queueManager.peekNextEntry() != null
                                ) {
                                    playQueuedNextTrack()
                                } else {
                                    schedulePreloadNext()
                                }
                            }
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                result =
                    QueueDiscoveryResult(emptyList(), successfulResponses = 0, failedResponses = 1)
            } finally {
                if (generation == discoveryGeneration) {
                    val discoveryStatus = when {
                        result?.failed == true -> DiscoveryStatus.FAILED
                        result?.exhausted == true -> DiscoveryStatus.EXHAUSTED
                        else -> DiscoveryStatus.IDLE
                    }
                    _state.value = _state.value.copy(
                        isDiscovering = false,
                        discoveryStatus = discoveryStatus,
                    )
                    val exo = player
                    if (
                        discoveryStatus != DiscoveryStatus.IDLE &&
                        queueManager.state.value.upNextCount == 0 &&
                        exo?.playbackState == Player.STATE_ENDED
                    ) {
                        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                        stopProgressTracker()
                    }
                }
            }
        }
    }

    private companion object {

        const val DISCOVERY_REFILL_THRESHOLD = 4
        const val MAX_RETRIES = 3
        const val QUALITY_SWITCH_READY_TIMEOUT_MS = 45_000L
        const val QUALITY_SWITCH_PLAY_TIMEOUT_MS = 3_000L
        const val QUALITY_SWITCH_FADE_STEP_MS = 18L
    }

    private fun buildMediaItem(
        track: HomeTrack,
        streamUrl: String,
        mediaId: String = currentQueueMediaId(track) ?: track.id,
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
        track.album?.let { metadataBuilder.setAlbumTitle(it) }
        track.artworkUrl?.let { metadataBuilder.setArtworkUri(Uri.parse(it)) }

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setCustomCacheKey(cacheKey(track))
            .setUri(streamUrl)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun mediaIdForEntry(entry: QueueEntry): String =
        QueueManagerImpl.QUEUE_MEDIA_ID_PREFIX + entry.id

    private fun currentQueueMediaId(track: HomeTrack? = _state.value.currentTrack): String? {
        val currentEntry = queueManager.state.value.currentEntry ?: return track?.id
        return if (track == null || currentEntry.track.id == track.id) {
            mediaIdForEntry(currentEntry)
        } else {
            track.id
        }
    }

    private fun resolveQualityFromMetadata(track: HomeTrack?): AudioQualityInfo? {
        val codec = track?.codec?.trim()?.lowercase() ?: return null
        val isLossless = codec.contains("hires") || codec == "hi-res" || codec.contains("24-") ||
                codec == "lossless" || codec == "alac" || codec == "flac"
        val isDolby =
            codec.contains("dolby") || codec.contains("atmos") || codec == "ec-3" || codec == "ec3"
        val resolved = when {
            isLossless -> if (codec.contains("alac")) "alac" else "flac"
            isDolby -> "ec-3"
            codec.contains("aac") || codec.contains("mp4a") -> "aac"
            codec.contains("opus") -> "opus"
            codec.contains("mp3") || codec.contains("mpeg") -> "mp3"
            else -> codec
        }
        val (deviceName, deviceType, deviceProtocol) = resolveOutputDevice(context)
        val initialPipeline = AudioPipelineDetails(
            trackId = track.id,
            isLocked = false,
            trackCodec = resolved.uppercase(),
            container = resolved.uppercase(),
            bitDepth = null,
            sampleRateHz = null,
            bitrateKbps = null,
            channelCount = 2,
            decoderName = null,
            decodedFormat = null,
            inputSampleRateHz = null,
            outputSampleRateHz = 48000,
            isResampled = false,
            processingMode = "DefaultAudioSink",
            outputEngine = "AudioTrack (Android AudioFlinger)",
            bufferSizeFrames = 3072,
            latencyMs = 64,
            deviceName = deviceName,
            deviceType = deviceType,
            deviceProtocol = deviceProtocol,
        )
        return when {
            isLossless -> AudioQualityInfo(
                trackId = track.id,
                isLocked = false,
                codec = "LOSSLESS",
                isLossless = true,
                isHiRes = false,
                sampleRate = null,
                pipelineDetails = initialPipeline,
            )

            isDolby -> AudioQualityInfo(
                trackId = track.id,
                isLocked = false,
                codec = "DOLBY ATMOS",
                isLossless = false,
                isDolby = true,
                sampleRate = null,
                pipelineDetails = initialPipeline,
            )

            else -> AudioQualityInfo(
                trackId = track.id,
                isLocked = false,
                codec = resolved.uppercase(),
                sampleRate = null,
                pipelineDetails = initialPipeline,
            )
        }
    }

    private fun updateAudioQuality(exo: ExoPlayer) {
        val currentTrack = _state.value.currentTrack ?: return
        val currentMediaItem = exo.currentMediaItem
        if (currentMediaItem != null && currentMediaItem.mediaId != currentQueueMediaId()) {
            return
        }

        val format = currentAudioFormat
        val trackCodecLower = currentTrack.codec?.lowercase()
        val mimeType = format?.sampleMimeType?.lowercase()
        val codecs = format?.codecs?.lowercase()
        val effectiveFormat = sinkInputFormat ?: format
        val sampleRate = sinkInputFormat?.sampleRate?.takeIf { it > 0 }
            ?: effectiveFormat?.sampleRate?.takeIf { it > 0 }
        val pcmEncoding = sinkInputFormat?.pcmEncoding ?: effectiveFormat?.pcmEncoding
        val isSampleRateHiRes = sampleRate != null && sampleRate > 48000
        val isBitDepthHiRes = pcmEncoding == C.ENCODING_PCM_24BIT ||
                pcmEncoding == C.ENCODING_PCM_32BIT ||
                pcmEncoding == C.ENCODING_PCM_FLOAT

        val isExplicitDolbyMime = mimeType == MimeTypes.AUDIO_E_AC3_JOC ||
                mimeType == MimeTypes.AUDIO_E_AC3 ||
                mimeType == MimeTypes.AUDIO_AC3 ||
                mimeType?.contains("dolby") == true ||
                mimeType?.contains("eac3") == true ||
                codecs?.contains("ec-3") == true ||
                codecs?.contains("ec3") == true

        val isExplicitLosslessMime = mimeType == MimeTypes.AUDIO_FLAC ||
                mimeType == MimeTypes.AUDIO_ALAC ||
                mimeType == MimeTypes.AUDIO_WAV ||
                codecs?.contains("flac") == true ||
                codecs?.contains("alac") == true

        val isDolby = when {
            isExplicitLosslessMime -> false
            isExplicitDolbyMime -> true
            else -> trackCodecLower?.let { it.contains("dolby") || it.contains("atmos") || it == "ec-3" || it == "ec3" } == true
        }

        val isLossless = !isDolby && (
                isExplicitLosslessMime ||
                        mimeType == MimeTypes.AUDIO_RAW ||
                        trackCodecLower?.let {
                            it == "alac" || it == "flac" || it.contains("lossless") ||
                                    it.contains("hires") || it == "hi-res" || it.contains("24-")
                        } == true
                )

        val isHiRes = isLossless && (isSampleRateHiRes || isBitDepthHiRes)

        val resolvedCodec: String = when {
            isDolby -> "ec-3"
            isLossless -> when {
                mimeType?.contains("alac") == true || codecs?.contains("alac") == true || trackCodecLower?.contains(
                    "alac"
                ) == true -> "alac"

                mimeType?.contains("wav") == true -> "wav"
                else -> "flac"
            }

            else -> when {
                mimeType == MimeTypes.AUDIO_AAC || mimeType?.contains("mp4a") == true || codecs?.contains(
                    "mp4a"
                ) == true || trackCodecLower?.contains("aac") == true -> "aac"

                mimeType == MimeTypes.AUDIO_OPUS || mimeType?.contains("opus") == true || codecs?.contains(
                    "opus"
                ) == true || trackCodecLower?.contains("opus") == true -> "opus"

                mimeType == MimeTypes.AUDIO_MPEG || mimeType?.contains("mpeg") == true || mimeType?.contains(
                    "mp3"
                ) == true || trackCodecLower?.contains("mp3") == true -> "mp3"

                else -> "mp3"
            }
        }

        val isLocked = sinkInputFormat != null || format != null

        val pipeline = resolvePipelineDetails(
            exo = exo,
            format = format,
            currentTrack = currentTrack,
            resolvedCodec = resolvedCodec,
            isLossless = isLossless,
            isHiRes = isHiRes,
            isDolby = isDolby,
            sampleRate = sampleRate,
            isLocked = isLocked,
        )

        val qualityInfo: AudioQualityInfo = when {
            isHiRes -> {
                AudioQualityInfo(
                    trackId = currentTrack.id,
                    isLocked = isLocked,
                    codec = "HI-RES",
                    isLossless = true,
                    isHiRes = true,
                    isDolby = false,
                    sampleRate = sampleRate,
                    bitDepth = if (pipeline.bitDepth?.contains("24") == true) 24 else if (pipeline.bitDepth?.contains(
                            "32"
                        ) == true
                    ) 32 else 16,
                    pipelineDetails = pipeline,
                )
            }

            isLossless -> {
                AudioQualityInfo(
                    trackId = currentTrack.id,
                    isLocked = isLocked,
                    codec = "LOSSLESS",
                    isLossless = true,
                    isHiRes = false,
                    isDolby = false,
                    sampleRate = sampleRate,
                    bitDepth = if (pipeline.bitDepth?.contains("24") == true) 24 else 16,
                    pipelineDetails = pipeline,
                )
            }

            isDolby -> {
                AudioQualityInfo(
                    trackId = currentTrack.id,
                    isLocked = isLocked,
                    codec = "DOLBY ATMOS",
                    isLossless = false,
                    isHiRes = false,
                    isDolby = true,
                    sampleRate = sampleRate,
                    bitDepth = 24,
                    pipelineDetails = pipeline,
                )
            }

            else -> {
                AudioQualityInfo(
                    trackId = currentTrack.id,
                    isLocked = isLocked,
                    codec = resolvedCodec.uppercase(),
                    isLossless = false,
                    isHiRes = false,
                    isDolby = false,
                    sampleRate = sampleRate,
                    bitDepth = null,
                    pipelineDetails = pipeline,
                )
            }
        }

        if (_state.value.audioQuality != qualityInfo) {
            _state.value = _state.value.copy(
                audioQuality = qualityInfo,
            )
        }
    }

    private fun resolveOutputDevice(context: Context): Triple<String, OutputDeviceType, String> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return Triple(
                "Built-in Speaker",
                OutputDeviceType.PHONE_SPEAKER,
                "Phone Loudspeaker"
            )

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (d in devices) {
            val name = d.productName?.toString().orEmpty()
            val lower = name.lowercase()
            when (d.type) {
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    val devType =
                        if (lower.contains("dac") || lower.contains("amp") || lower.contains("fiio") || lower.contains(
                                "dongle"
                            ) || lower.contains("audio") || lower.contains("quest")
                        ) {
                            OutputDeviceType.USB_DAC
                        } else {
                            OutputDeviceType.EARBUDS
                        }
                    val label =
                        name.ifBlank { if (devType == OutputDeviceType.USB_DAC) "USB Hi-Res DAC" else "USB Audio Device" }
                    return Triple(label, devType, "USB Audio")
                }

                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                    val devType = when {
                        lower.contains("car") || lower.contains("sync") || lower.contains("uconnect") || lower.contains(
                            "bt_car"
                        ) -> OutputDeviceType.CAR_AUDIO

                        d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER || lower.contains("speaker") || lower.contains(
                            "soundlink"
                        ) || lower.contains("flip") || lower.contains("charge") || lower.contains("boom") || lower.contains(
                            "echo"
                        ) -> OutputDeviceType.BLUETOOTH_SPEAKER

                        lower.contains("buds") || lower.contains("airpods") || lower.contains("earbuds") || lower.contains(
                            "wf-"
                        ) || lower.contains("tws") || lower.contains("freebuds") -> OutputDeviceType.EARBUDS

                        else -> OutputDeviceType.OVER_EAR
                    }
                    val label = name.ifBlank { "Bluetooth Audio" }
                    val protocol =
                        if (d.type == AudioDeviceInfo.TYPE_BLE_HEADSET || d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) "BLE Audio" else "Bluetooth A2DP"
                    return Triple(label, devType, protocol)
                }

                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                    val label = name.ifBlank { "Wired Headphones" }
                    return Triple(label, OutputDeviceType.OVER_EAR, "3.5mm Headphone Jack")
                }

                AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                    val label = name.ifBlank { "Wired Earphones" }
                    return Triple(label, OutputDeviceType.EARBUDS, "3.5mm Headset Jack")
                }

                AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> {
                    val label = name.ifBlank { "HDMI Display" }
                    return Triple(label, OutputDeviceType.MONITOR_HDMI, "HDMI / eARC")
                }
            }
        }
        return Triple("Built-in Speaker", OutputDeviceType.PHONE_SPEAKER, "Phone Loudspeaker")
    }

    private fun resolvePipelineDetails(
        exo: ExoPlayer,
        format: Format?,
        currentTrack: HomeTrack?,
        resolvedCodec: String?,
        isLossless: Boolean,
        isHiRes: Boolean,
        isDolby: Boolean,
        sampleRate: Int?,
        isLocked: Boolean = true,
    ): AudioPipelineDetails {
        val (deviceName, deviceType, deviceProtocol) = resolveOutputDevice(context)

        val effectiveEncoding = sinkInputFormat?.pcmEncoding ?: format?.pcmEncoding
        val bitDepthStr = when (effectiveEncoding) {
            C.ENCODING_PCM_24BIT -> "24-bit"
            C.ENCODING_PCM_32BIT -> "32-bit"
            C.ENCODING_PCM_FLOAT -> if (isDolby || isHiRes) "24-bit" else "24-bit"
            C.ENCODING_PCM_16BIT -> "16-bit"
            else -> if (isHiRes || isDolby) "24-bit" else if (isLossless) "16-bit" else null
        }

        val containerStr = format?.containerMimeType?.substringAfter('/')?.uppercase()
            ?: when {
                resolvedCodec == "alac" || format?.sampleMimeType?.contains("mp4") == true -> "M4A / MP4"
                resolvedCodec == "flac" || format?.sampleMimeType?.contains("flac") == true -> "FLAC"
                resolvedCodec == "opus" || format?.sampleMimeType?.contains("opus") == true -> "OGG / OPUS"
                resolvedCodec == "aac" -> "AAC"
                resolvedCodec == "mp3" -> "MP3"
                else -> null
            }

        val contentLength = try {
            val trackKey = currentTrack?.let(::cacheKey)
            var len = -1L
            if (!trackKey.isNullOrBlank()) {
                val meta = songCache.getContentMetadata(trackKey)
                len = ContentMetadata.getContentLength(meta)
            }
            if (len <= 0L) {
                val streamUrl = currentTrack?.streamUrl
                if (!streamUrl.isNullOrBlank()) {
                    val meta = songCache.getContentMetadata(streamUrl)
                    len = ContentMetadata.getContentLength(meta)
                }
            }
            if (len > 0L) len else currentResourceLengthBytes
        } catch (_: Exception) {
            currentResourceLengthBytes
        }

        val durMs =
            exo.duration.takeIf { it > 0L } ?: _state.value.durationMs.takeIf { it > 0L } ?: 0L
        val durSec = durMs / 1000.0

        val channels = sinkInputFormat?.channelCount ?: format?.channelCount ?: 2
        val inputSr = sinkInputFormat?.sampleRate?.takeIf { it > 0 } ?: sampleRate ?: 44100
        val outputSr = sinkAudioTrackConfig?.sampleRate ?: 48000
        val isResampled = inputSr != outputSr

        val bitrateKbps = when {
            format != null && format.bitrate > 0 -> format.bitrate / 1000
            contentLength > 0L && durSec > 0.0 -> {
                ((contentLength * 8.0) / durSec / 1000.0).roundToInt()
            }

            isLossless -> {
                val bd = if (bitDepthStr?.contains("24") == true) 24 else 16
                ((inputSr * bd * channels * 0.62) / 1000).toInt()
            }

            else -> 320
        }

        val decodedFmt = when {
            effectiveEncoding == C.ENCODING_PCM_FLOAT -> "Float32 PCM"
            bitDepthStr != null -> "PCM $bitDepthStr Signed LE"
            isLossless -> "PCM 16-bit Signed LE"
            else -> "PCM 16-bit"
        }

        val outputModeStr = when (sinkAudioTrackConfig?.encoding) {
            C.ENCODING_PCM_FLOAT -> "Float32 Output"
            C.ENCODING_PCM_16BIT -> "16-bit PCM Output"
            C.ENCODING_PCM_24BIT -> "24-bit PCM Output"
            else -> "Float32 Output"
        }

        val bytesPerSample = when (sinkAudioTrackConfig?.encoding) {
            C.ENCODING_PCM_FLOAT -> 4
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            else -> 4
        }
        val frameBytes = bytesPerSample * channels
        val bufferBytes = sinkAudioTrackConfig?.bufferSize ?: (3072 * 4)
        val bufferFrames = if (frameBytes > 0) bufferBytes / frameBytes else 3072
        val latencyMs = (bufferFrames.toDouble() / outputSr * 1000.0).roundToInt()

        return AudioPipelineDetails(
            trackId = currentTrack?.id,
            isLocked = isLocked,
            trackCodec = resolvedCodec?.uppercase() ?: if (isLossless) "FLAC" else "AUDIO",
            container = containerStr ?: if (isLossless) "FLAC" else "AUDIO",
            bitDepth = bitDepthStr ?: if (isLossless) "16-bit" else null,
            sampleRateHz = inputSr,
            bitrateKbps = bitrateKbps,
            channelCount = channels,
            decoderName = currentDecoderName
                ?: if (isLossless) "MediaCodec FLAC Decoder" else "Android MediaCodec",
            decodedFormat = decodedFmt,
            inputSampleRateHz = inputSr,
            outputSampleRateHz = outputSr,
            isResampled = isResampled,
            processingMode = "DefaultAudioSink • $outputModeStr",
            outputEngine = "AudioTrack (Android AudioFlinger)",
            bufferSizeFrames = bufferFrames,
            latencyMs = latencyMs,
            deviceName = deviceName,
            deviceType = deviceType,
            deviceProtocol = deviceProtocol,
        )
    }

    private fun startPlayback(
        track: HomeTrack,
        streamUrl: String,
        startPositionMs: Long = 0L,
        bypassCache: Boolean = false,
        playWhenReady: Boolean = true,
    ) {
        val exo = player ?: return
        fadeJob?.cancel()
        pendingFadeIn = playWhenReady
        exo.volume = if (playWhenReady) 0f else 1f

        resetPipelineForTrack()
        val initialQuality = resolveQualityFromMetadata(track)
        _state.value = _state.value.copy(currentTrack = track, audioQuality = initialQuality)

        val mediaItem = buildMediaItem(track, streamUrl)
        exo.clearMediaItems()
        if (bypassCache) {
            val directFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setAllowCrossProtocolRedirects(true)
            val source = ProgressiveMediaSource.Factory(directFactory)
                .createMediaSource(mediaItem)
            exo.setMediaSource(source, startPositionMs)
        } else if (startPositionMs > 0L) {
            exo.setMediaItem(mediaItem, startPositionMs)
        } else {
            exo.setMediaItem(mediaItem)
        }
        exo.prepare()
        if (playWhenReady) exo.play() else exo.pause()
        ArtworkUrlHelper.preload(context, track.artworkUrl)

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
        cancelQualityHandoff()
        val exo = player ?: return
        pendingFadeIn = false
        val pos = exo.currentPosition.coerceAtLeast(0L)
        val dur = exo.duration.coerceAtLeast(0L)
        val prog =
            if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else _state.value.progress
        _state.value = _state.value.copy(
            isPlaying = false,
            currentPositionMs = if (pos > 0) pos else _state.value.currentPositionMs,
            progress = prog,
        )
        if (pos > 0L) {
            playbackPersistence.saveLastPosition(pos)
        }
        if (dur > 0L) {
            playbackPersistence.saveLastDuration(dur)
        }
        spectrumVisualizer.reset()
        fadeOut(250L)
    }

    override fun resume() {
        val exo = player ?: return
        val currentMedia = exo.currentMediaItem
        if (currentMedia == null) {
            val track = _state.value.currentTrack
            if (track != null) {
                executePlayTrack(track, _state.value.currentPositionMs)
                scheduleDiscoveryRefillIfNeeded()
                return
            }
        }
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
        cancelQualityHandoff()
        val exo = player ?: return
        val dur = if (exo.duration > 0) exo.duration else _state.value.durationMs
        if (dur > 0) {
            val targetMs = (dur * progress.coerceIn(0f, 1f)).toLong()
            if (exo.currentMediaItem != null) {
                exo.seekTo(targetMs)
            }
            _state.value = _state.value.copy(
                currentPositionMs = targetMs,
                progress = progress.coerceIn(0f, 1f),
            )
            playbackPersistence.saveLastPosition(targetMs)
        }
    }

    private suspend fun awaitPlayerReady(exo: ExoPlayer): Boolean =
        withTimeoutOrNull(QUALITY_SWITCH_READY_TIMEOUT_MS) {
            if (exo.playbackState == Player.STATE_READY) return@withTimeoutOrNull true
            suspendCancellableCoroutine { continuation ->
                val listener = object : Player.Listener {
                    private fun finish(ready: Boolean) {
                        exo.removeListener(this)
                        if (continuation.isActive) continuation.resume(ready)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> finish(true)
                            Player.STATE_ENDED -> finish(false)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        finish(false)
                    }
                }
                exo.addListener(listener)
                continuation.invokeOnCancellation { exo.removeListener(listener) }
                if (exo.playbackState == Player.STATE_READY) {
                    exo.removeListener(listener)
                    if (continuation.isActive) continuation.resume(true)
                } else if (exo.playbackState == Player.STATE_ENDED) {
                    exo.removeListener(listener)
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        } == true

    private suspend fun awaitPlayerPlaying(exo: ExoPlayer): Boolean =
        withTimeoutOrNull(QUALITY_SWITCH_PLAY_TIMEOUT_MS) {
            if (exo.isPlaying) return@withTimeoutOrNull true
            suspendCancellableCoroutine { continuation ->
                val listener = object : Player.Listener {
                    private fun finish(playing: Boolean) {
                        exo.removeListener(this)
                        if (continuation.isActive) continuation.resume(playing)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) finish(true)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        finish(false)
                    }
                }
                exo.addListener(listener)
                continuation.invokeOnCancellation { exo.removeListener(listener) }
                if (exo.isPlaying) {
                    exo.removeListener(listener)
                    if (continuation.isActive) continuation.resume(true)
                }
            }
        } == true

    private fun releaseQualitySwitchCandidate(candidate: ExoPlayer? = null) {
        val pending = qualitySwitchCandidate ?: return
        if (candidate != null && pending !== candidate) return
        qualitySwitchCandidate = null
        if (pending !== player) {
            pending.pause()
            pending.release()
            playerAudioSinkStates.remove(pending)
        }
    }

    private fun cancelQualityHandoff() {
        val hadQualityHandoff = qualityHandoffPreviousPlayer != null
        qualityHandoffFadeJob?.cancel()
        qualityHandoffFadeJob = null
        qualityHandoffPreviousPlayer?.let { previous ->
            if (previous !== player) {
                previous.pause()
                previous.release()
                playerAudioSinkStates.remove(previous)
            }
        }
        qualityHandoffPreviousPlayer = null
        player?.let { active -> if (active.isPlaying) active.volume = 1f }
        if (hadQualityHandoff) {
            player?.setAudioAttributes(playbackAudioAttributes(), true)
        }
    }

    private fun fadeBetweenQualityPlayers(previous: ExoPlayer, replacement: ExoPlayer) {
        val previousVolume = previous.volume.coerceIn(0f, 1f)
        qualityHandoffPreviousPlayer = previous
        qualityHandoffFadeJob = scope.launch {
            val steps = 8
            for (step in 1..steps) {
                delay(QUALITY_SWITCH_FADE_STEP_MS)
                if (player !== replacement) break
                val fraction = step.toFloat() / steps
                replacement.volume = fraction
                previous.volume = previousVolume * (1f - fraction)
            }
            if (player === replacement) replacement.volume = 1f
            previous.volume = 0f
            previous.pause()
            previous.release()
            replacement.setAudioAttributes(playbackAudioAttributes(), true)
            playerAudioSinkStates.remove(previous)
            if (qualityHandoffPreviousPlayer === previous) {
                qualityHandoffPreviousPlayer = null
                qualityHandoffFadeJob = null
            }
        }
    }

    private fun promoteQualitySwitchPlayer(
        previous: ExoPlayer,
        replacement: ExoPlayer,
        track: HomeTrack,
        positionMs: Long,
        shouldResume: Boolean,
    ): Boolean {
        val previousForwardingPlayer = sessionForwardingPlayer
        val previousCommandListeners = sessionCommandListeners
        val forwardingPlayer = createSessionForwardingPlayer(replacement)
        try {
            mediaSession?.setPlayer(forwardingPlayer)
        } catch (_: Exception) {
            sessionForwardingPlayer = previousForwardingPlayer
            sessionCommandListeners = previousCommandListeners
            return false
        }

        fadeJob?.cancel()
        fadeJob = null
        cancelQualityHandoff()
        player = replacement
        attachPlayerListeners(replacement)
        qualitySwitchCandidate = null
        resetPipelineForTrack()
        playerAudioSinkStates[replacement]?.let { audioState ->
            sinkInputFormat = audioState.inputFormat
            sinkAudioTrackConfig = audioState.audioTrackConfig
            if (audioState.inputIsPcm) {
                audioState.inputFormat?.let { format ->
                    spectrumVisualizer.sink.flush(
                        format.sampleRate,
                        format.channelCount,
                        format.pcmEncoding,
                    )
                }
            }
        }
        replacement.currentTracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (index in 0 until group.length) {
                    if (group.isTrackSelected(index)) {
                        currentAudioFormat = group.getTrackFormat(index)
                        break
                    }
                }
            }
        }

        queueManager.updateCurrentTrack(track)
        playbackPersistence.saveLastTrack(track)
        playbackPersistence.saveLastPosition(positionMs)
        val durationMs = replacement.duration.takeIf { it > 0L }
            ?: _state.value.durationMs
        if (durationMs > 0L) playbackPersistence.saveLastDuration(durationMs)
        val currentPositionMs = replacement.currentPosition.coerceAtLeast(0L)
        val progress = if (durationMs > 0L) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            _state.value.progress
        }
        _state.value = _state.value.copy(
            currentTrack = track,
            audioQuality = resolveQualityFromMetadata(track),
            isPlaying = shouldResume && replacement.isPlaying,
            isBuffering = false,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            progress = progress,
            switchingQualityFormat = null,
            error = null,
        )
        retryCount = 0
        pendingFadeIn = false
        updateAudioQuality(replacement)
        if (shouldResume) {
            startProgressTracker()
            fadeBetweenQualityPlayers(previous, replacement)
        } else {
            replacement.volume = 1f
            previous.pause()
            previous.release()
            replacement.setAudioAttributes(playbackAudioAttributes(), true)
            playerAudioSinkStates.remove(previous)
            stopProgressTracker()
        }
        return true
    }

    override fun switchQualityVariant(track: HomeTrack, variant: TrackFormatVariant) {
        val currentTrack = _state.value.currentTrack ?: return
        if (currentTrack.id != track.id) return
        val availableVariant = currentTrack.availableVariants.firstOrNull {
            it.backendTrackId == variant.backendTrackId && it.format.equals(variant.format, true)
        } ?: return

        if (currentTrack.backendTrackId == availableVariant.backendTrackId) {
            if (_state.value.switchingQualityFormat != null) {
                qualitySwitchJob?.cancel()
                qualitySwitchJob = null
                qualitySwitchGeneration += 1L
                releaseQualitySwitchCandidate()
                _state.value = _state.value.copy(
                    switchingQualityFormat = null,
                    isBuffering = player?.playbackState == Player.STATE_BUFFERING,
                    isPlaying = player?.isPlaying == true,
                )
            }
            return
        }

        qualitySwitchJob?.cancel()
        releaseQualitySwitchCandidate()
        cancelQualityHandoff()
        val generation = ++qualitySwitchGeneration
        val selectedTrack = currentTrack.copy(
            streamUrl = null,
            backendTrackId = availableVariant.backendTrackId,
            codec = availableVariant.format,
        )
        val exo = player

        if (exo == null || exo.currentMediaItem?.mediaId != currentQueueMediaId()) {
            qualitySwitchJob = null
            queueManager.updateCurrentTrack(selectedTrack)
            playbackPersistence.saveLastTrack(selectedTrack)
            _state.value = _state.value.copy(
                currentTrack = selectedTrack,
                audioQuality = resolveQualityFromMetadata(selectedTrack),
                switchingQualityFormat = null,
                error = null,
            )
            return
        }

        _state.value = _state.value.copy(
            switchingQualityFormat = availableVariant.format,
            error = null,
        )
        val activePlayer = exo
        qualitySwitchJob = scope.launch {
            var replacement: ExoPlayer? = null
            try {
                val resolution = searchRepository.resolvePlayback(selectedTrack)
                    ?: return@launch
                if (!isActive || generation != qualitySwitchGeneration || player !== activePlayer) {
                    return@launch
                }
                if (_state.value.currentTrack?.id != currentTrack.id) return@launch

                val currentMediaItem = activePlayer.currentMediaItem ?: return@launch
                val currentIndex = activePlayer.currentMediaItemIndex
                    .takeIf { it in 0 until activePlayer.mediaItemCount }
                    ?: return@launch
                val resolvedTrack = selectedTrack.copy(
                    streamUrl = resolution.streamUrl,
                    codec = resolution.codec ?: selectedTrack.codec,
                )
                val initialPositionMs = if (activePlayer.playbackState == Player.STATE_ENDED) {
                    0L
                } else {
                    activePlayer.currentPosition.coerceAtLeast(0L)
                }
                val replacementPlaylist = List(activePlayer.mediaItemCount) { index ->
                    if (index == currentIndex) {
                        buildMediaItem(resolvedTrack, resolution.streamUrl, currentMediaItem.mediaId)
                    } else {
                        activePlayer.getMediaItemAt(index)
                    }
                }
                val candidate = buildPlayer(handleAudioFocus = false).also {
                    replacement = it
                    qualitySwitchCandidate = it
                    it.volume = 0f
                    it.setMediaItems(replacementPlaylist, currentIndex, initialPositionMs)
                    it.prepare()
                }

                if (!awaitPlayerReady(candidate)) return@launch
                if (!isActive || generation != qualitySwitchGeneration || player !== activePlayer ||
                    _state.value.currentTrack?.id != currentTrack.id
                ) {
                    return@launch
                }

                // The old stream kept playing while the new one buffered. Re-align the staged
                // player at the live playhead before it is allowed to take over.
                val handoffPositionMs = if (activePlayer.playbackState == Player.STATE_ENDED) {
                    0L
                } else {
                    activePlayer.currentPosition.coerceAtLeast(0L)
                }
                candidate.seekTo(currentIndex, handoffPositionMs)
                if (!awaitPlayerReady(candidate)) return@launch
                if (!isActive || generation != qualitySwitchGeneration || player !== activePlayer ||
                    _state.value.currentTrack?.id != currentTrack.id
                ) {
                    return@launch
                }

                val shouldResumePlayback = activePlayer.playWhenReady &&
                    (_state.value.isPlaying ||
                        activePlayer.playbackState == Player.STATE_BUFFERING ||
                        activePlayer.playbackState == Player.STATE_ENDED)
                if (shouldResumePlayback) {
                    candidate.play()
                    if (!awaitPlayerPlaying(candidate)) return@launch
                }
                if (!isActive || generation != qualitySwitchGeneration || player !== activePlayer ||
                    _state.value.currentTrack?.id != currentTrack.id
                ) {
                    return@launch
                }

                if (!promoteQualitySwitchPlayer(
                        previous = activePlayer,
                        replacement = candidate,
                        track = resolvedTrack,
                        positionMs = handoffPositionMs,
                        shouldResume = shouldResumePlayback,
                    )
                ) {
                    return@launch
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the active stream untouched if the replacement cannot be prepared.
            } finally {
                replacement?.let(::releaseQualitySwitchCandidate)
                if (generation == qualitySwitchGeneration) {
                    if (player === activePlayer && _state.value.switchingQualityFormat != null) {
                        _state.value = _state.value.copy(
                            switchingQualityFormat = null,
                            isBuffering = activePlayer.playbackState == Player.STATE_BUFFERING,
                            isPlaying = activePlayer.isPlaying,
                        )
                    }
                    qualitySwitchJob = null
                }
            }
        }
    }

    override fun stop() {
        lyricsJob?.cancel()
        motionArtworkJob?.cancel()
        motionArtworkJob = null
        motionArtworkRequestedTrackId = null
        lyricsGeneration += 1L
        lyricsRequestedGeneration = -1L
        resolveJob?.cancel()
        qualitySwitchJob?.cancel()
        qualitySwitchJob = null
        qualitySwitchGeneration += 1L
        releaseQualitySwitchCandidate()
        cancelQualityHandoff()
        pendingPlaybackMediaId = null
        preloadJob?.cancel()
        cancelPrecache()
        discoveryJob?.cancel()
        fadeJob?.cancel()
        retryJob?.cancel()
        retryCount = 0
        pendingFadeIn = false
        stopProgressTracker()
        spectrumVisualizer.reset()
        val exo = player
        if (exo != null) {
            exo.volume = 0f
            exo.stop()
            exo.clearMediaItems()
            exo.volume = 1f
        }
        PlaybackServiceHolder.service?.let { s ->
            runCatching {
                s.stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
                s.stopSelf()
            }
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.cancelAll()
        _state.value = PlaybackState()
    }

    override fun dismiss() {
        playbackPersistence.setPlayerDismissed(true)
        stop()
    }

    override fun release() {
        metadataJob?.cancel()
        metadataJob = null
        lyricsJob?.cancel()
        motionArtworkJob?.cancel()
        motionArtworkJob = null
        motionArtworkRequestedTrackId = null
        lyricsGeneration += 1L
        resolveJob?.cancel()
        qualitySwitchJob?.cancel()
        qualitySwitchJob = null
        qualitySwitchGeneration += 1L
        releaseQualitySwitchCandidate()
        cancelQualityHandoff()
        pendingPlaybackMediaId = null
        preloadJob?.cancel()
        cancelPrecache()
        discoveryJob?.cancel()
        fadeJob?.cancel()
        retryJob?.cancel()
        retryCount = 0
        pendingFadeIn = false
        stopProgressTracker()
        spectrumVisualizer.reset()
        autoRipCoordinator?.observe(AutoRipSource.PLAYBACK_QUEUE, emptyList())
        queueManager.release()
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
        player?.let(playerAudioSinkStates::remove)
        player = null
        sessionForwardingPlayer = null
        sessionCommandListeners.clear()
        sessionCommandUpdateJob?.cancel()
        sessionCommandUpdateJob = null
        scope.cancel()
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val exo = player
                if (exo != null) syncTrackStateWithPlayer(exo)
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

object PlaybackManagerHolder {
    private var instance: PlaybackManager? = null
    private val lock = Any()

    fun getInstance(
        context: Context,
        sessionStore: SessionStore,
        autoRipCoordinator: AutoRipCoordinator? = null,
        completedRipTrackIds: SharedFlow<String>? = null,
    ): PlaybackManager {
        return synchronized(lock) {
            instance ?: PlaybackManagerImpl(
                context = context.applicationContext,
                sessionStore = sessionStore,
                autoRipCoordinator = autoRipCoordinator,
                completedRipTrackIds = completedRipTrackIds,
            ).also {
                instance = it
            }
        }
    }

    fun getInstanceOrNull(): PlaybackManager? {
        return synchronized(lock) { instance }
    }

    fun release() {
        synchronized(lock) {
            instance?.release()
            instance = null
        }
    }
}
