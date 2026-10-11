package org.shilpo.laboon.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongLinkScraperTest {

    @Test
    fun `scrapes Apple Music song id from exact YouTube page data`() {
        val parsed = SongLinkPageParser.parse(
            page(
                videoId = VIDEO_ID,
                appleMusicUrl = "https://geo.music.apple.com/in/album/_/1559885420?i=1559885421&mt=1",
                appleUniqueId = "itunes|song|1559885421",
            ),
            expectedVideoId = VIDEO_ID,
        )

        assertEquals(
            SongLinkPageResult.AppleMatch(
                appleTrackId = "1559885421",
                appleUrl = "https://geo.music.apple.com/in/album/_/1559885420?i=1559885421&mt=1",
            ),
            parsed,
        )
    }

    @Test
    fun `reports a valid source page without an Apple Music mapping`() {
        val parsed = SongLinkPageParser.parse(
            page(videoId = VIDEO_ID, appleMusicUrl = null, appleUniqueId = null),
            expectedVideoId = VIDEO_ID,
        )

        assertEquals(SongLinkPageResult.NoAppleMatch, parsed)
    }

    @Test
    fun `uses the Apple song identity when its page url points at the album`() {
        val parsed = SongLinkPageParser.parse(
            page(
                videoId = VIDEO_ID,
                appleMusicUrl = "https://music.apple.com/in/album/release/1559885420",
                appleUniqueId = "itunes|song|1559885421",
            ),
            expectedVideoId = VIDEO_ID,
        )

        assertEquals(
            SongLinkPageResult.AppleMatch(
                appleTrackId = "1559885421",
                appleUrl = "https://music.apple.com/in/album/release/1559885420",
            ),
            parsed,
        )
    }

    @Test
    fun `does not accept an Apple mapping from a different source video`() {
        val parsed = SongLinkPageParser.parse(
            page(
                videoId = "oHg5SJYRHA0",
                appleMusicUrl = "https://music.apple.com/us/song/example/1234567890",
                appleUniqueId = "itunes|song|1234567890",
            ),
            expectedVideoId = VIDEO_ID,
        )

        assertEquals(SongLinkPageResult.InvalidPage, parsed)
    }

    @Test
    fun `rejects non Apple links and conflicting catalog ids`() {
        val nonApple = SongLinkPageParser.parse(
            page(
                videoId = VIDEO_ID,
                appleMusicUrl = "https://example.com/song/1559885421",
                appleUniqueId = "itunes|song|1559885421",
            ),
            expectedVideoId = VIDEO_ID,
        )
        val conflictingId = SongLinkPageParser.parse(
            page(
                videoId = VIDEO_ID,
                appleMusicUrl = "https://music.apple.com/us/song/example/1559885421",
                appleUniqueId = "itunes|song|1559885422",
            ),
            expectedVideoId = VIDEO_ID,
        )

        assertEquals(SongLinkPageResult.NoAppleMatch, nonApple)
        assertEquals(SongLinkPageResult.NoAppleMatch, conflictingId)
    }

    @Test
    fun `builds a song link URL only for a YouTube video id`() {
        assertEquals("https://song.link/y/$VIDEO_ID", SongLinkScraper.pageUrl(VIDEO_ID))
        assertNull(SongLinkScraper.pageUrl("not a video id"))
    }

    @Test
    fun `resolves Songlink first and does not call Lyricsporn after a direct match`() = kotlinx.coroutines.runBlocking {
        var fallbackCalls = 0
        val resolver = YouTubePlaylistTrackResolver(
            songLinkLookup = { SongLinkScrapedTrack("1559885421", "https://music.apple.com/us/song/song/1559885421") },
            lyricspornLookup = { _, _ -> fallbackCalls++; null },
        )

        val resolved = resolver.resolve(apiBaseUrl = "https://lyrics.example", track = sourceTrack())

        assertEquals("1559885421", resolved?.appleTrackId)
        assertEquals(YouTubePlaylistMatchSource.SONGLINK, resolved?.source)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `falls back to Lyricsporn when Songlink has no Apple mapping`() = kotlinx.coroutines.runBlocking {
        var fallbackCalls = 0
        val lyricspornItem = org.shilpo.laboon.lyricsporn.LyricspornCatalogItem(
            id = "9876543210",
            type = "song",
            name = "A Song",
            artistName = "An Artist",
            durationMs = 180_000,
        )
        val resolver = YouTubePlaylistTrackResolver(
            songLinkLookup = { null },
            lyricspornLookup = { _, _ ->
                fallbackCalls++
                YouTubePlaylistCandidateMatch(lyricspornItem, confidence = 1.0)
            },
        )

        val resolved = resolver.resolve(apiBaseUrl = "https://lyrics.example", track = sourceTrack())

        assertEquals("9876543210", resolved?.appleTrackId)
        assertEquals(YouTubePlaylistMatchSource.LYRICS_PORN, resolved?.source)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun `rejects a Songlink hit with incompatible duration and tries Lyricsporn`() = kotlinx.coroutines.runBlocking {
        var fallbackCalls = 0
        val item = org.shilpo.laboon.lyricsporn.LyricspornCatalogItem(
            id = "9876543210",
            type = "song",
            name = "A Song",
            artistName = "An Artist",
            durationMs = 240_000,
        )
        val resolver = YouTubePlaylistTrackResolver(
            songLinkLookup = {
                SongLinkScrapedTrack(
                    "1234567890",
                    "https://music.apple.com/us/song/song/1234567890",
                    sourceDurationMs = 200_000,
                )
            },
            lyricspornLookup = { _, _ ->
                fallbackCalls++
                YouTubePlaylistCandidateMatch(item, confidence = 1.0)
            },
        )

        val resolved = resolver.resolve(
            apiBaseUrl = "https://lyrics.example",
            track = sourceTrack().copy(durationMs = 240_000),
        )

        assertEquals("9876543210", resolved?.appleTrackId)
        assertEquals(YouTubePlaylistMatchSource.LYRICS_PORN, resolved?.source)
        assertTrue(fallbackCalls > 0)
    }

    @Test
    fun `rejects non exact Lyricsporn fallback`() = kotlinx.coroutines.runBlocking {
        val item = org.shilpo.laboon.lyricsporn.LyricspornCatalogItem(
            id = "9876543210",
            type = "song",
            name = "A Song (Live)",
            artistName = "An Artist",
            durationMs = 180_000,
        )
        val resolver = YouTubePlaylistTrackResolver(
            songLinkLookup = { null },
            lyricspornLookup = { _, _ ->
                YouTubePlaylistCandidateMatch(item, confidence = 0.5)
            },
        )

        assertNull(resolver.resolve("https://lyrics.example", sourceTrack()))
    }

    @Test
    fun acceptsExactYouTubeLinkWhenAnotherProviderIsPrimary() {
        val parsed = SongLinkPageParser.parse(
            page(
                videoId = VIDEO_ID,
                appleMusicUrl = "https://music.apple.com/us/song/example/1234567890",
                appleUniqueId = "itunes|song|1234567890",
                entityVideoId = "oHg5SJYRHA0",
            ),
            expectedVideoId = VIDEO_ID,
        )

        assertEquals(
            SongLinkPageResult.AppleMatch(
                appleTrackId = "1234567890",
                appleUrl = "https://music.apple.com/us/song/example/1234567890",
            ),
            parsed,
        )
    }

    private fun page(
        videoId: String,
        appleMusicUrl: String?,
        appleUniqueId: String?,
        entityVideoId: String = videoId,
    ): String {
        val links = JSONObject().apply {
            put("platform", "youtubeMusic")
            put("url", "https://music.youtube.com/watch?v=$videoId")
        }
        val appleLink = appleMusicUrl?.let {
            JSONObject().apply {
                put("platform", "appleMusic")
                put("url", it)
                appleUniqueId?.let { id -> put("uniqueId", id) }
            }
        }
        val pageData = JSONObject()
            .put("entityUniqueId", "youtube|song|$videoId")
            .put(
                "entityData",
                JSONObject()
                    .put("provider", "youtube")
                    .put("type", "song")
                    .put("id", entityVideoId),
            )
            .put(
                "sections",
                org.json.JSONArray().put(
                    JSONObject()
                        .put(
                            "links",
                            org.json.JSONArray().apply {
                                put(links)
                                appleLink?.let(::put)
                            },
                        ),
                ),
            )
        val root = JSONObject().put(
            "props",
            JSONObject().put(
                "pageProps",
                JSONObject().put("pageData", pageData),
            ),
        )
        return "<script id=\"__NEXT_DATA__\" type=\"application/json\">$root</script>"
    }

    private fun sourceTrack() = YouTubePlaylistTrack(
        title = "A Song",
        artist = "An Artist",
        durationMs = 180_000,
        videoId = VIDEO_ID,
    )

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
    }
}
