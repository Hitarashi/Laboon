package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MusicBrainzIsrcLookupTest {

    @Test
    fun `search batches valid ISRCs with OR terms`() {
        val url = MusicBrainzIsrcLookup.searchUrl(
            listOf("USABC2100001", "bad value", "GBABC2100002", "USABC2100001"),
        )!!
        val query = URLDecoder.decode(
            url.substringAfter("query=").substringBefore('&'),
            StandardCharsets.UTF_8.name(),
        )

        assertEquals("(isrc:USABC2100001 OR isrc:GBABC2100002)", query)
        assertEquals("json", url.substringAfter("fmt=").substringBefore('&'))
    }

    @Test
    fun `ISRC matching prefers the canonical title and artist among recordings`() {
        val requestedIsrc = "USABC2100001"
        val exactMbid = "12345678-1234-1234-1234-123456789012"
        val results = JSONObject().put(
            "recordings",
            JSONArray()
                .put(recording("87654321-1234-1234-1234-123456789012", "Other Title", "Other Artist", requestedIsrc))
                .put(recording(exactMbid, "Canonical Title", "Canonical Artist", requestedIsrc)),
        )
        val byIsrc = MusicBrainzIsrcLookup.parseSearchResponse(results)
        val requested = listOf(
            HomeTrack(
                id = "apple-1",
                title = "Canonical Title",
                artist = "Canonical Artist",
                isrc = requestedIsrc,
            ),
        )

        assertEquals(mapOf(0 to exactMbid), MusicBrainzIsrcLookup.matchTracks(requested, byIsrc))
    }

    @Test
    fun `tracks without a catalog ISRC are left for metadata fallback`() {
        val requested = listOf(HomeTrack(id = "youtube-1", title = "A Song", artist = "An Artist"))

        assertNull(requested.single().isrc)
        assertEquals(emptyMap<Int, String>(), MusicBrainzIsrcLookup.matchTracks(requested, emptyMap()))
    }

    private fun recording(id: String, title: String, artist: String, isrc: String) =
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put(
                "artist-credit",
                JSONArray().put(
                    JSONObject().put("name", artist).put("joinphrase", ""),
                ),
            )
            .put("isrcs", JSONArray().put(isrc))
}
