package org.shilpo.laboon.scrobble

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.net.HttpOutcome

class ScrobbleCoordinator(
    private val sessionStore: SessionStore,
    private val queue: ScrobbleQueue,
    private val client: LastFmScrobbleClient = LastFmScrobbleClient(),
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {

    private var activeTrackId: String? = null
    private var startedAtEpochSeconds: Long = 0L
    private var accumulatedPlayTimeMs: Long = 0L
    private var lastTickTimestampMs: Long = 0L
    private var nowPlayingSent: Boolean = false
    private var scrobbled: Boolean = false

    private val flushMutex = Mutex()

    @Synchronized
    fun onPlaybackTick(
        track: HomeTrack?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (track == null) {
            resetActiveTrackState()
            return
        }

        val now = timeProvider()

        if (track.id != activeTrackId) {
            activeTrackId = track.id
            startedAtEpochSeconds = now / 1000L
            lastTickTimestampMs = now
            accumulatedPlayTimeMs = 0L
            nowPlayingSent = false
            scrobbled = false
        }

        if (isPlaying) {
            val delta = now - lastTickTimestampMs
            if (delta in 0..1500L) {
                accumulatedPlayTimeMs += delta
            }
            lastTickTimestampMs = now

            // Now Playing check
            if (!nowPlayingSent && accumulatedPlayTimeMs >= NOW_PLAYING_THRESHOLD_MS) {
                nowPlayingSent = true
                val artist = track.artist
                val title = track.title
                val album = track.album
                val durationSec = if (durationMs > 0) durationMs / 1000L else null

                scope.launch(ioDispatcher) {
                    val creds = sessionStore.getLastFmCredentials()
                    if (creds != null && creds.canScrobble()) {
                        client.updateNowPlaying(
                            artist = artist,
                            track = title,
                            album = album,
                            durationSeconds = durationSec,
                            credentials = creds,
                        )
                    }
                }
            }

            // Scrobble check
            val threshold = minOf(durationMs / 2, SCROBBLE_MAX_THRESHOLD_MS)
            if (!scrobbled && durationMs >= MIN_TRACK_DURATION_MS && accumulatedPlayTimeMs >= threshold) {
                scrobbled = true
                val queued = QueuedScrobble(
                    artist = track.artist,
                    track = track.title,
                    album = track.album,
                    timestampEpochSeconds = startedAtEpochSeconds,
                    durationSeconds = if (durationMs > 0) durationMs / 1000L else null,
                )
                queue.enqueue(queued)
                scope.launch(ioDispatcher) {
                    flushQueue()
                }
            }
        } else {
            lastTickTimestampMs = now
        }
    }

    suspend fun flushQueue() {
        flushMutex.withLock {
            val creds = sessionStore.getLastFmCredentials()
            if (creds == null || !creds.canScrobble()) return

            while (true) {
                val items = queue.peek(LastFmScrobbleClient.MAX_BATCH_SIZE)
                if (items.isEmpty()) break

                val outcome = client.scrobble(items, creds)
                if (outcome is HttpOutcome.Success) {
                    queue.remove(items.map { it.id }.toSet())
                    if (items.size < LastFmScrobbleClient.MAX_BATCH_SIZE) break
                } else {
                    break
                }
            }
        }
    }

    @Synchronized
    private fun resetActiveTrackState() {
        activeTrackId = null
        startedAtEpochSeconds = 0L
        accumulatedPlayTimeMs = 0L
        lastTickTimestampMs = 0L
        nowPlayingSent = false
        scrobbled = false
    }

    @Synchronized
    fun getActiveTrackId(): String? = activeTrackId

    @Synchronized
    fun getAccumulatedPlayTimeMs(): Long = accumulatedPlayTimeMs

    @Synchronized
    fun isNowPlayingSent(): Boolean = nowPlayingSent

    @Synchronized
    fun isScrobbled(): Boolean = scrobbled

    companion object {
        const val NOW_PLAYING_THRESHOLD_MS = 5_000L
        const val MIN_TRACK_DURATION_MS = 30_000L
        const val SCROBBLE_MAX_THRESHOLD_MS = 240_000L

        fun LastFmCredentials.canScrobble(): Boolean =
            connected &&
                !sessionKey.isNullOrBlank() &&
                !apiKey.isNullOrBlank() &&
                !apiSecret.isNullOrBlank()
    }
}
