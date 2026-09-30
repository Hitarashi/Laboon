package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.PlaybackPersistence

class PlaybackPersistenceTest {

    @Test
    fun testDefaultDismissedStateIsTrueWhenClean() {
        val store = FakeKeyValueStore()
        val persistence = PlaybackPersistence(store)
        assertTrue(persistence.isPlayerDismissed())
        assertNull(persistence.getLastTrack())
        assertEquals(0L, persistence.getLastPosition())
        assertEquals(0L, persistence.getLastDuration())
    }

    @Test
    fun testSaveAndLoadLastTrack() {
        val store = FakeKeyValueStore()
        val persistence = PlaybackPersistence(store)
        val track = HomeTrack(
            id = "track_42",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            artworkUrl = "https://images.example/m83.jpg",
            playCount = 100L,
            source = "Last.fm",
            streamUrl = "https://stream.example/42",
            backendTrackId = 101,
            isCached = true,
            codec = "alac",
            mbid = "mbid-42",
        )

        persistence.saveLastTrack(track)
        persistence.setPlayerDismissed(false)
        persistence.saveLastPosition(45000L)
        persistence.saveLastDuration(240000L)

        assertFalse(persistence.isPlayerDismissed())
        val loaded = persistence.getLastTrack()
        assertNotNull(loaded)
        assertEquals(track, loaded)
        assertEquals(45000L, persistence.getLastPosition())
        assertEquals(240000L, persistence.getLastDuration())
    }

    @Test
    fun testDismissalSurvivesAcrossReads() {
        val store = FakeKeyValueStore()
        val persistence = PlaybackPersistence(store)

        persistence.setPlayerDismissed(true)
        assertTrue(persistence.isPlayerDismissed())

        persistence.setPlayerDismissed(false)
        assertFalse(persistence.isPlayerDismissed())

        persistence.setPlayerDismissed(true)
        assertTrue(persistence.isPlayerDismissed())
    }

    @Test
    fun testClearLastTrack() {
        val store = FakeKeyValueStore()
        val persistence = PlaybackPersistence(store)
        val track = HomeTrack(id = "1", title = "T", artist = "A")

        persistence.saveLastTrack(track)
        assertNotNull(persistence.getLastTrack())

        persistence.clearLastTrack()
        assertNull(persistence.getLastTrack())
    }
}
