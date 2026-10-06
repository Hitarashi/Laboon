package org.shilpo.laboon.ui.screens.album

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.lyricsporn.LyricspornAlbum
import org.shilpo.laboon.lyricsporn.LyricspornAlbumTrack

class AlbumMetadataFooterTest {

    @Test
    fun albumFooterMetadata_formatsAlbumDetailsForVisibleFooter() {
        val album = LyricspornAlbum(
            id = "album-1",
            name = "Album",
            releaseDate = "2011-09-30",
            trackCount = 14,
            copyright = "℗ 2011 Example Records",
            recordLabel = "Example Records",
            tracks = listOf(
                track("track-1", 30 * 60_000L),
                track("track-2", 36 * 60_000L),
            ),
        )

        val metadata = albumFooterMetadata(album)

        assertEquals("30 September 2011", metadata.releaseDate)
        assertEquals("14 songs, 1 hour 6 minutes", metadata.trackSummary)
        assertEquals("℗ 2011 Example Records", metadata.copyright)
        assertEquals("Example Records", metadata.recordLabel)
        assertTrue(metadata.hasContent)
    }

    @Test
    fun albumFooterMetadata_usesTrackListCountAndOmitsUnknownDuration() {
        val album = LyricspornAlbum(
            id = "album-1",
            name = "Album",
            tracks = listOf(track("track-1", null)),
        )

        val metadata = albumFooterMetadata(album)

        assertEquals("1 song", metadata.trackSummary)
        assertNull(metadata.releaseDate)
        assertTrue(metadata.hasContent)
    }

    @Test
    fun albumFooterMetadata_withNoAvailableDetailsHasNoFooterContent() {
        val metadata = albumFooterMetadata(
            LyricspornAlbum(id = "album-1", name = "Album"),
        )

        assertNull(metadata.trackSummary)
        assertFalse(metadata.hasContent)
    }

    @Test
    fun albumFooterMetadata_editorialNotesOnlyDoNotCreateFooterContent() {
        val metadata = albumFooterMetadata(
            LyricspornAlbum(
                id = "album-1",
                name = "Album",
                editorialNotes = "Description belongs above the tracks.",
            ),
        )

        assertFalse(metadata.hasContent)
    }

    private fun track(id: String, durationMs: Long?) = LyricspornAlbumTrack(
        id = id,
        name = id,
        durationMs = durationMs,
    )
}
