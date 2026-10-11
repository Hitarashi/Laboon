package org.shilpo.laboon.scrobble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.net.HttpOutcome

class ScrobbleCoordinatorTest {

    private class FakeLastFmScrobbleClient : LastFmScrobbleClient() {
        val nowPlayingCalls = mutableListOf<NowPlayingCall>()
        val scrobbleCalls = mutableListOf<List<QueuedScrobble>>()

        data class NowPlayingCall(
            val artist: String,
            val track: String,
            val album: String?,
            val durationSeconds: Long?,
            val credentials: LastFmCredentials,
        )

        override suspend fun updateNowPlaying(
            artist: String,
            track: String,
            album: String?,
            durationSeconds: Long?,
            credentials: LastFmCredentials,
        ): HttpOutcome<Boolean> {
            nowPlayingCalls.add(NowPlayingCall(artist, track, album, durationSeconds, credentials))
            return HttpOutcome.Success(true)
        }

        override suspend fun scrobble(
            items: List<QueuedScrobble>,
            credentials: LastFmCredentials,
        ): HttpOutcome<List<ScrobbleResult>> {
            scrobbleCalls.add(items)
            return HttpOutcome.Success(items.map { ScrobbleResult(accepted = true) })
        }
    }

    private fun testTrack(id: String = "track-1", durationMs: Long = 180_000L) = HomeTrack(
        id = id,
        title = "Track Title",
        artist = "Artist Name",
        album = "Album Name",
        durationMs = durationMs,
    )

    private val connectedCredentials = LastFmCredentials(
        connected = true,
        username = "tester",
        sessionKey = "session_key",
        apiKey = "api_key",
        apiSecret = "api_secret",
    )

    @Test
    fun shortTrack_under30Seconds_doesNotScrobble() {
        val store = FakeKeyValueStore()
        val sessionStore = SessionStore(store).apply { saveLastFmCredentials(connectedCredentials) }
        val queue = ScrobbleQueue(store)
        val client = FakeLastFmScrobbleClient()
        var fakeTime = 1000000L

        val coordinator = ScrobbleCoordinator(
            sessionStore = sessionStore,
            queue = queue,
            client = client,
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            timeProvider = { fakeTime },
        )

        val track = testTrack(durationMs = 25_000L) // 25s (< 30s)

        // Initial tick
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 0L, durationMs = 25_000L)

        // Play for 25 seconds (25 steps of 1000ms)
        for (i in 1..25) {
            fakeTime += 1000L
            coordinator.onPlaybackTick(track, isPlaying = true, positionMs = i * 1000L, durationMs = 25_000L)
        }

        assertFalse(coordinator.isScrobbled())
        assertEquals(0, client.scrobbleCalls.size)
        assertEquals(0, queue.size())
    }

    @Test
    fun trackScrobblesWhen50PercentPlayed() {
        val store = FakeKeyValueStore()
        val sessionStore = SessionStore(store).apply { saveLastFmCredentials(connectedCredentials) }
        val queue = ScrobbleQueue(store)
        val client = FakeLastFmScrobbleClient()
        var fakeTime = 1000000L

        val coordinator = ScrobbleCoordinator(
            sessionStore = sessionStore,
            queue = queue,
            client = client,
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            timeProvider = { fakeTime },
        )

        val track = testTrack(durationMs = 60_000L) // 60s track, 50% = 30s

        // Initial tick
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 0L, durationMs = 60_000L)

        // Play 29 seconds (under 50%)
        for (i in 1..29) {
            fakeTime += 1000L
            coordinator.onPlaybackTick(track, isPlaying = true, positionMs = i * 1000L, durationMs = 60_000L)
        }
        assertFalse(coordinator.isScrobbled())
        assertEquals(0, client.scrobbleCalls.size)

        // Advance 1 more second to reach 30s (50%)
        fakeTime += 1000L
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 30_000L, durationMs = 60_000L)

        assertTrue(coordinator.isScrobbled())
        assertEquals(1, client.scrobbleCalls.size)
        val scrobbledItem = client.scrobbleCalls[0][0]
        assertEquals(track.title, scrobbledItem.track)
        assertEquals(track.artist, scrobbledItem.artist)
        assertEquals(track.album, scrobbledItem.album)
    }

    @Test
    fun longTrack_scrobblesAt240Seconds_notNeeding50Percent() {
        val store = FakeKeyValueStore()
        val sessionStore = SessionStore(store).apply { saveLastFmCredentials(connectedCredentials) }
        val queue = ScrobbleQueue(store)
        val client = FakeLastFmScrobbleClient()
        var fakeTime = 1000000L

        val coordinator = ScrobbleCoordinator(
            sessionStore = sessionStore,
            queue = queue,
            client = client,
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            timeProvider = { fakeTime },
        )

        val track = testTrack(durationMs = 600_000L) // 10 minutes (600s), 50% = 300s, max threshold = 240s

        // Initial tick
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 0L, durationMs = 600_000L)

        // Play for 239 seconds
        for (i in 1..239) {
            fakeTime += 1000L
            coordinator.onPlaybackTick(track, isPlaying = true, positionMs = i * 1000L, durationMs = 600_000L)
        }
        assertFalse(coordinator.isScrobbled())
        assertEquals(0, client.scrobbleCalls.size)

        // Advance to 240 seconds
        fakeTime += 1000L
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 240_000L, durationMs = 600_000L)

        assertTrue(coordinator.isScrobbled())
        assertEquals(1, client.scrobbleCalls.size)
    }

    @Test
    fun trackOnlyScrobblesOnce() {
        val store = FakeKeyValueStore()
        val sessionStore = SessionStore(store).apply { saveLastFmCredentials(connectedCredentials) }
        val queue = ScrobbleQueue(store)
        val client = FakeLastFmScrobbleClient()
        var fakeTime = 1000000L

        val coordinator = ScrobbleCoordinator(
            sessionStore = sessionStore,
            queue = queue,
            client = client,
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            timeProvider = { fakeTime },
        )

        val track = testTrack(durationMs = 60_000L)
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 0L, durationMs = 60_000L)

        // Play for 40 seconds (crosses 30s threshold at tick 30)
        for (i in 1..40) {
            fakeTime += 1000L
            coordinator.onPlaybackTick(track, isPlaying = true, positionMs = i * 1000L, durationMs = 60_000L)
        }

        assertTrue(coordinator.isScrobbled())
        assertEquals(1, client.scrobbleCalls.size)
    }

    @Test
    fun nowPlaying_isSentAfter5SecondsOfActivePlayback() {
        val store = FakeKeyValueStore()
        val sessionStore = SessionStore(store).apply { saveLastFmCredentials(connectedCredentials) }
        val queue = ScrobbleQueue(store)
        val client = FakeLastFmScrobbleClient()
        var fakeTime = 1000000L

        val coordinator = ScrobbleCoordinator(
            sessionStore = sessionStore,
            queue = queue,
            client = client,
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            timeProvider = { fakeTime },
        )

        val track = testTrack(durationMs = 120_000L)
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 0L, durationMs = 120_000L)

        // 4 seconds played
        for (i in 1..4) {
            fakeTime += 1000L
            coordinator.onPlaybackTick(track, isPlaying = true, positionMs = i * 1000L, durationMs = 120_000L)
        }
        assertFalse(coordinator.isNowPlayingSent())
        assertEquals(0, client.nowPlayingCalls.size)

        // 5th second played
        fakeTime += 1000L
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 5_000L, durationMs = 120_000L)

        assertTrue(coordinator.isNowPlayingSent())
        assertEquals(1, client.nowPlayingCalls.size)
        assertEquals("Track Title", client.nowPlayingCalls[0].track)
        assertEquals("Artist Name", client.nowPlayingCalls[0].artist)

        // Additional ticks do not resend Now Playing for same track
        for (i in 6..10) {
            fakeTime += 1000L
            coordinator.onPlaybackTick(track, isPlaying = true, positionMs = i * 1000L, durationMs = 120_000L)
        }
        assertEquals(1, client.nowPlayingCalls.size)
    }

    @Test
    fun pausedTime_doesNotCountTowardThreshold() {
        val store = FakeKeyValueStore()
        val sessionStore = SessionStore(store).apply { saveLastFmCredentials(connectedCredentials) }
        val queue = ScrobbleQueue(store)
        val client = FakeLastFmScrobbleClient()
        var fakeTime = 1000000L

        val coordinator = ScrobbleCoordinator(
            sessionStore = sessionStore,
            queue = queue,
            client = client,
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            timeProvider = { fakeTime },
        )

        val track = testTrack(durationMs = 60_000L)
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 0L, durationMs = 60_000L)

        // Play for 3 seconds
        for (i in 1..3) {
            fakeTime += 1000L
            coordinator.onPlaybackTick(track, isPlaying = true, positionMs = i * 1000L, durationMs = 60_000L)
        }
        assertEquals(3000L, coordinator.getAccumulatedPlayTimeMs())
        assertFalse(coordinator.isNowPlayingSent())

        // Pause for 60 seconds (with ticks while paused)
        for (i in 1..60) {
            fakeTime += 1000L
            coordinator.onPlaybackTick(track, isPlaying = false, positionMs = 3000L, durationMs = 60_000L)
        }
        assertEquals(3000L, coordinator.getAccumulatedPlayTimeMs())
        assertFalse(coordinator.isNowPlayingSent())
        assertFalse(coordinator.isScrobbled())

        // Resume playing for 1 second (total active = 4s)
        fakeTime += 1000L
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 4000L, durationMs = 60_000L)
        assertEquals(4000L, coordinator.getAccumulatedPlayTimeMs())
        assertFalse(coordinator.isNowPlayingSent())

        // Play 1 more second (total active = 5s) -> Now Playing triggers
        fakeTime += 1000L
        coordinator.onPlaybackTick(track, isPlaying = true, positionMs = 5000L, durationMs = 60_000L)
        assertEquals(5000L, coordinator.getAccumulatedPlayTimeMs())
        assertTrue(coordinator.isNowPlayingSent())
        assertFalse(coordinator.isScrobbled())
    }
}
