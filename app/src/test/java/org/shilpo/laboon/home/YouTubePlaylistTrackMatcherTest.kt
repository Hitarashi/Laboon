package org.shilpo.laboon.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem

class YouTubePlaylistTrackMatcherTest {

    @Test
    fun `chooses the duration compatible candidate instead of the first search result`() {
        val source = sourceTrack()
        val wrongDuration = candidate(
            id = "111",
            name = "A Song",
            artist = "An Artist",
            album = "Source Album",
            durationMs = 205_000,
        )
        val rightSongOtherRelease = candidate(
            id = "222",
            name = "A Song",
            artist = "An Artist",
            album = "Compilation",
            durationMs = 181_000,
        )

        val match = YouTubePlaylistTrackMatcher.bestCandidate(
            source,
            listOf(wrongDuration, rightSongOtherRelease),
        )

        assertEquals("222", match?.item?.id)
    }

    @Test
    fun `accepts feature credit formatting differences when the primary artist matches`() {
        val source = sourceTrack().copy(
            title = "A Song (feat. Guest Artist)",
            artist = "Main Artist",
            album = null,
        )
        val candidate = candidate(
            id = "333",
            name = "A Song",
            artist = "Main Artist & Guest Artist",
            album = "Another Album",
            durationMs = source.durationMs,
        )

        val match = YouTubePlaylistTrackMatcher.bestCandidate(source, listOf(candidate))

        assertEquals("333", match?.item?.id)
    }

    @Test
    fun `rejects a different explicit version`() {
        val source = sourceTrack().copy(album = null)
        val liveVersion = candidate(
            id = "444",
            name = "A Song (Live)",
            artist = "An Artist",
            album = null,
            durationMs = source.durationMs,
        )

        assertNull(YouTubePlaylistTrackMatcher.bestCandidate(source, listOf(liveVersion)))
    }

    @Test
    fun `allows the same recording from a different album`() {
        val source = sourceTrack()
        val first = candidate("555", "A Song", "An Artist", "Album One", source.durationMs)
        val second = candidate("666", "A Song", "An Artist", "Album Two", source.durationMs)

        assertEquals("555", YouTubePlaylistTrackMatcher.bestCandidate(source, listOf(first, second))?.item?.id)
    }

    @Test
    fun `rejects unrelated catalog results`() {
        val unrelated = candidate(
            id = "777",
            name = "Something Else",
            artist = "Someone Else",
            album = null,
            durationMs = 180_000,
        )

        assertNull(YouTubePlaylistTrackMatcher.bestCandidate(sourceTrack(), listOf(unrelated)))
    }

    private fun sourceTrack() = YouTubePlaylistTrack(
        title = "A Song",
        artist = "An Artist",
        album = "Source Album",
        durationMs = 180_000,
        videoId = "abcdefghijk",
    )

    private fun candidate(
        id: String,
        name: String,
        artist: String,
        album: String?,
        durationMs: Long?,
    ) = LyricspornCatalogItem(
        id = id,
        type = "song",
        name = name,
        artistName = artist,
        albumName = album,
        durationMs = durationMs,
    )
}
