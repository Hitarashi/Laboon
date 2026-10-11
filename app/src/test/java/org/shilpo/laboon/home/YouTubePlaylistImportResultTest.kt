package org.shilpo.laboon.home

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubePlaylistImportResultTest {

    @Test
    fun `unmatched tracks retain source order and report Apple catalog mismatch`() {
        val tracks = listOf(
            YouTubePlaylistTrack("Imported", "Artist"),
            YouTubePlaylistTrack("No Apple match", "Artist"),
        )

        val skipped = skippedYouTubePlaylistTracks(
            sourceTracks = tracks,
            resolvedSourceIndices = setOf(0),
        )

        assertEquals(
            listOf(YouTubePlaylistSkippedTrack(
                tracks[1],
                YouTubePlaylistSkippedTrack.Reason.NO_APPLE_MATCH,
            )),
            skipped,
        )
    }
}
