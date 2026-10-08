package org.shilpo.laboon

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.LastFmApi

class LastFmApiTest {
    @Test
    fun `track methods use recording ids when available and metadata otherwise`() {
        val withMbid = LastFmApi.similarTracksUrl(
            HomeTrack("id", "Song name", "Artist name", mbid = "recording-id"),
            "key value",
        )
        val withoutMbid = LastFmApi.similarTracksUrl(
            HomeTrack("id", "Song name", "Artist name"),
            "key value",
        )

        assertTrue(withMbid.contains("method=track.getsimilar"))
        assertTrue(withMbid.contains("mbid=recording-id"))
        assertTrue(withMbid.contains("api_key=key+value"))
        assertTrue(withoutMbid.contains("track=Song+name&artist=Artist+name"))
    }

    @Test
    fun `similar track parser keeps score and recording and artist ids distinct`() {
        val candidates = LastFmApi.parseSimilarTracks(
            JSONObject().put(
                "similartracks",
                JSONObject().put(
                    "track",
                    JSONObject()
                        .put("name", "Candidate")
                        .put("mbid", "recording-mbid")
                        .put("match", "0.82")
                        .put(
                            "artist",
                            JSONObject().put("name", "Artist").put("mbid", "artist-mbid")
                        ),
                ),
            ).toString(),
        )

        assertEquals(1, candidates.size)
        assertEquals("recording-mbid", candidates.single().track.mbid)
        assertEquals("artist-mbid", candidates.single().track.artistMbid)
        assertEquals(0.82, candidates.single().similarity!!, 0.0001)
    }

    @Test
    fun `loved tracks parse object and array response forms`() {
        val single =
            """{"lovedtracks":{"track":{"name":"One","artist":{"name":"A"},"mbid":"one"}}}"""
        val many =
            """{"lovedtracks":{"track":[{"name":"One","artist":{"name":"A"},"mbid":"one"},{"name":"Two","artist":"B"}]}}"""

        assertEquals(listOf("one"), LastFmApi.parseLovedTracks(single).map { it.mbid })
        assertEquals(listOf("One", "Two"), LastFmApi.parseLovedTracks(many).map { it.title })
    }
}
