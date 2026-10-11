package org.shilpo.laboon

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.LastFmApi

class LastFmApiTest {
    @Test
    fun `similar tracks always use title and artist`() {
        val url = LastFmApi.similarTracksUrl(
            HomeTrack("id", "Song name", "Artist name"),
            "key value",
        )

        assertTrue(url.contains("method=track.getsimilar"))
        assertTrue(url.contains("api_key=key+value"))
        assertTrue(url.contains("track=Song+name&artist=Artist+name"))
    }

    @Test
    fun `similar track parser keeps score and metadata`() {
        val candidates = LastFmApi.parseSimilarTracks(
            JSONObject().put(
                "similartracks",
                JSONObject().put(
                    "track",
                    JSONObject()
                        .put("name", "Candidate")
                        .put("match", "0.82")
                        .put("artist", JSONObject().put("name", "Artist")),
                ),
            ).toString(),
        )

        assertEquals(1, candidates.size)
        assertEquals("Candidate", candidates.single().track.title)
        assertEquals("Artist", candidates.single().track.artist)
        assertEquals(0.82, candidates.single().similarity!!, 0.0001)
    }

    @Test
    fun `loved tracks parse object and array response forms`() {
        val single =
            """{"lovedtracks":{"track":{"name":"One","artist":{"name":"A"}}}}"""
        val many =
            """{"lovedtracks":{"track":[{"name":"One","artist":{"name":"A"}},{"name":"Two","artist":"B"}]}}"""

        assertEquals(listOf("One"), LastFmApi.parseLovedTracks(single).map { it.title })
        assertEquals(listOf("One", "Two"), LastFmApi.parseLovedTracks(many).map { it.title })
    }
}
