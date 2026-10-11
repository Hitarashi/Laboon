package org.shilpo.laboon.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlaylistApiTest {

    @Test
    fun `playlist id accepts public YouTube playlist and watch links`() {
        assertEquals(
            "PLabc123_XY",
            YouTubePlaylistApi.playlistIdFromUrl("https://www.youtube.com/playlist?list=PLabc123_XY"),
        )
        assertEquals(
            "PLabc123_XY",
            YouTubePlaylistApi.playlistIdFromUrl("https://music.youtube.com/watch?v=video&list=PLabc123_XY"),
        )
    }

    @Test
    fun `playlist id rejects non YouTube URLs and links without a list`() {
        assertNull(YouTubePlaylistApi.playlistIdFromUrl("https://example.com/playlist?list=PLabc123_XY"))
        assertNull(YouTubePlaylistApi.playlistIdFromUrl("https://youtube.com/watch?v=video"))
    }

    @Test
    fun `playlist response preserves title track metadata and continuation`() {
        val root = JSONObject(
            """
            {
              "header": {
                "musicDetailHeaderRenderer": {
                  "title": {"runs": [{"text": "Night drive"}]}
                }
              },
              "contents": {
                "musicPlaylistShelfRenderer": {
                  "contents": [
                    {
                      "musicResponsiveListItemRenderer": {
                        "flexColumns": [
                          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "First Song", "navigationEndpoint": {"watchEndpoint": {"videoId": "video-1"}}}]}}},
                          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "The Artist"}, {"text": " • "}, {"text": "The Album"}, {"text": " • "}, {"text": "2024"}]}}}
                        ],
                        "fixedColumns": [
                          {"musicResponsiveListItemFixedColumnRenderer": {"text": {"simpleText": "3:42"}}}
                        ]
                      }
                    },
                    {"continuationItemRenderer": {"continuationEndpoint": {"continuationCommand": {"token": "next-page-token"}}}}
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("Night drive", YouTubePlaylistApi.parsePlaylistTitle(root))
        assertEquals("next-page-token", YouTubePlaylistApi.parseContinuationToken(root))
        assertEquals(
            YouTubePlaylistTrack(
                title = "First Song",
                artist = "The Artist",
                album = "The Album",
                durationMs = 222_000,
                videoId = "video-1",
            ),
            YouTubePlaylistApi.parsePlaylistTracks(root).single(),
        )
    }

    @Test
    fun `playlist parser reads artist joins and duration runs from YouTube Music rows`() {
        val root = JSONObject(
            """
            {
              "musicPlaylistShelfRenderer": {
                "contents": [{
                  "musicResponsiveListItemRenderer": {
                    "flexColumns": [
                      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "APT."}]}}},
                      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "ROSÉ"}, {"text": " & "}, {"text": "Bruno Mars"}]}}},
                      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": []}}}
                    ],
                    "fixedColumns": [{"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "2:54"}]}}}]
                  }
                }]
              }
            }
            """.trimIndent(),
        )

        val track = YouTubePlaylistApi.parsePlaylistTracks(root).single()

        assertEquals("APT.", track.title)
        assertEquals("ROSÉ & Bruno Mars", track.artist)
        assertNull(track.album)
        assertEquals(174_000L, track.durationMs)
    }

    @Test
    fun `playlist request identifies the public music playlist`() {
        val request = JSONObject(YouTubePlaylistApi.browseBody("PLabc123_XY"))

        assertEquals("VLPLabc123_XY", request.getString("browseId"))
        assertTrue(request.has("context"))
        assertFalse(request.has("continuation"))
    }

    @Test
    fun `duration matching tolerates rounded YouTube values but rejects different cuts`() {
        assertTrue(YouTubePlaylistApi.durationsAreCompatible(222_000, 221_500))
        assertTrue(YouTubePlaylistApi.durationsAreCompatible(null, 221_500))
        assertFalse(YouTubePlaylistApi.durationsAreCompatible(222_000, 210_000))
    }
}
