package org.shilpo.laboon.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ListenBrainzPlaylistApiTest {

    @Test
    fun playlistsUrlEncodesUsernameAndUsesPagination() {
        assertEquals(
            "https://api.listenbrainz.org/1/user/a%2Bb/playlists?count=40&offset=20",
            ListenBrainzPlaylistApi.playlistsUrl("a+b", count = 40, offset = 20),
        )
    }

    @Test
    fun parsePlaylistsReadsJspfMetadataAndPlaylistId() {
        val root = JSONObject(
            """
            {
              "playlists": [{
                "playlist": {
                  "identifier": "https://listenbrainz.org/playlist/playlist-123",
                  "title": "Night drive",
                  "creator": "listener",
                  "track": [{}, {}],
                  "extension": {
                    "https://musicbrainz.org/doc/jspf#playlist": {"public": false}
                  }
                }
              }]
            }
            """.trimIndent(),
        )

        val playlist = ListenBrainzPlaylistApi.parsePlaylists(root).single()

        assertEquals("playlist-123", playlist.id)
        assertEquals("Night drive", playlist.title)
        assertEquals("listener", playlist.creator)
        assertFalse(playlist.isPublic == true)
        assertEquals(2, playlist.trackCount)
    }

    @Test
    fun parsePlaylistsDoesNotTreatOmittedRecordingsAsAnEmptyPlaylist() {
        val root = JSONObject(
            """
            {
              "playlists": [{
                "playlist": {
                  "identifier": "https://listenbrainz.org/playlist/playlist-123",
                  "title": "Night drive",
                  "track": []
                }
              }]
            }
            """.trimIndent(),
        )

        assertNull(ListenBrainzPlaylistApi.parsePlaylists(root).single().trackCount)
    }

    @Test
    fun parsePlaylistRecordingMbidsReadsRecordingUrisInOrder() {
        val root = JSONObject(
            """
            {
              "playlist": {
                "track": [
                  {"identifier": ["https://musicbrainz.org/recording/first"]},
                  {"identifier": ["https://musicbrainz.org/recording/second"]},
                  {"identifier": ["https://listenbrainz.org/playlist/not-a-recording"]}
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf("first", "second"),
            ListenBrainzPlaylistApi.parseRecordingMbids(root),
        )
    }

    @Test
    fun createPlaylistBodyCreatesAnEmptyJspfPlaylist() {
        val body = JSONObject(ListenBrainzPlaylistApi.createPlaylistBody("Mix \"one\"", "listener"))
            .getJSONObject("playlist")

        assertEquals("Mix \"one\"", body.getString("title"))
        assertEquals("listener", body.getString("creator"))
        assertEquals(0, body.getJSONArray("track").length())
    }

    @Test
    fun addTracksBodyUsesMusicBrainzRecordingUris() {
        val track = JSONObject(ListenBrainzPlaylistApi.addTracksBody(listOf("mbid-1")))
            .getJSONObject("playlist")
            .getJSONArray("track")
            .getJSONObject(0)

        assertEquals(
            "https://musicbrainz.org/recording/mbid-1",
            track.getJSONArray("identifier").getString(0),
        )
    }

    @Test
    fun parseCreatedPlaylistAcceptsApiPlaylistMbidResponse() {
        val playlist = ListenBrainzPlaylistApi.parseCreatedPlaylist(
            JSONObject(
                """
                {
                  "playlist_mbid": "new-id",
                  "playlist": {"title": "Fresh mix", "creator": "listener"}
                }
                """.trimIndent(),
            ),
        )

        assertNotNull(playlist)
        assertEquals("new-id", playlist?.id)
        assertEquals("Fresh mix", playlist?.title)
        assertNull(ListenBrainzPlaylistApi.parseCreatedPlaylist(JSONObject()))
    }
}
