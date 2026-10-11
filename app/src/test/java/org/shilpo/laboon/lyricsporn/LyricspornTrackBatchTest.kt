package org.shilpo.laboon.lyricsporn

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricspornTrackBatchTest {

    @Test
    fun `batch request asks for canonical track identifiers`() {
        val body = JSONObject(
            LyricspornClient.trackBatchRequestBody(listOf("1559885421", "9876543210")),
        )
        val items = body.getJSONArray("items")

        assertEquals(2, items.length())
        assertEquals("1559885421", items.getJSONObject(0).getString("appleId"))
        assertTrue(items.getJSONObject(0).getJSONArray("include").toString().contains("identifiers"))
    }

    @Test
    fun `batch response keeps Apple title artist album duration and ISRC`() {
        val response = JSONObject().put(
            "items",
            JSONArray().put(
                JSONObject()
                    .put("index", 0)
                    .put("status", "matched")
                    .put(
                        "track",
                        JSONObject()
                            .put("id", "1559885421")
                            .put("title", "Canonical Title")
                            .put("artist", "Canonical Artist")
                            .put("album", "Canonical Album")
                            .put("durationMs", 214000)
                            .put("isrc", "USABC2100001"),
                    ),
            ).put(
                JSONObject()
                    .put("index", 1)
                    .put("status", "not_found")
                    .put("appleId", "9876543210"),
            ),
        )

        val parsed = LyricspornClient.parseTrackBatchResponse(response)

        assertEquals(
            LyricspornCatalogItem(
                id = "1559885421",
                type = "song",
                name = "Canonical Title",
                artistName = "Canonical Artist",
                albumName = "Canonical Album",
                durationMs = 214000,
                isrc = "USABC2100001",
            ),
            parsed["1559885421"],
        )
        assertEquals(1, parsed.size)
    }
}
