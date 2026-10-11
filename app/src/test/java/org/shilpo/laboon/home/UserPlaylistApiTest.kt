package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPlaylistApiTest {

    @Test
    fun parsesFavoritesAndCustomPlaylistsFromServer() {
        val playlists = UserPlaylistApi.parsePlaylists(
            JSONArray(
                """
                [
                  {"id":"favorites-42","name":"Favorites","is_favorites":true,"track_count":3,"preview_track_ids":["1559885421","1440643709"]},
                  {"id":"pl_custom","name":"Road Trip","is_favorites":false,"source_url":null,"track_count":8,"preview_track_ids":[]}
                ]
                """.trimIndent(),
            ),
        )

        assertEquals(2, playlists.size)
        assertTrue(playlists[0].isFavorites)
        assertEquals(3, playlists[0].trackCount)
        assertEquals(listOf("1559885421", "1440643709"), playlists[0].previewTrackIds)
        assertFalse(playlists[1].isFavorites)
        assertEquals("Road Trip", playlists[1].name)
    }

    @Test
    fun importBodyContainsOnlyAppleTrackIdsAndSourceUrl() {
        val body = UserPlaylistApi.importBody(
            name = "Mix",
            sourceUrl = "https://music.youtube.com/playlist?list=PL123",
            trackIds = listOf("1559885421", "1440643709"),
        )

        assertEquals("PL123", JSONObject(body).getString("source_url").substringAfter("list="))
        assertEquals(
            listOf("1559885421", "1440643709"),
            JSONObject(body).getJSONArray("track_ids").let { array ->
                (0 until array.length()).map(array::getString)
            },
        )
        assertEquals(3, JSONObject(body).length())
        assertFalse(JSONObject(body).has("tracks"))
    }

    @Test
    fun parsesOrderedTrackIdsWithoutMetadataSnapshots() {
        val detail = UserPlaylistApi.parseDetail(
            JSONObject(
                """
                {"playlist":{"id":"pl_1","name":"Mix","is_favorites":false,"track_count":2},"track_ids":["1559885421","1440643709"]}
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("1559885421", "1440643709"), detail?.trackIds)
    }
}
