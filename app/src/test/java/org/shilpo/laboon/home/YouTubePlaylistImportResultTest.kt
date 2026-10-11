package org.shilpo.laboon.home

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubePlaylistImportResultTest {

    @Test
    fun `skipped tracks retain source order and distinguish each matching failure`() {
        val tracks = listOf(
            YouTubePlaylistTrack("Imported", "Artist"),
            YouTubePlaylistTrack("No Apple match", "Artist"),
            YouTubePlaylistTrack("Lookup failed", "Artist"),
            YouTubePlaylistTrack("No recording", "Artist"),
        )

        val skipped = skippedYouTubePlaylistTracks(
            sourceTracks = tracks,
            appleMatchedSourceIndices = setOf(0, 2, 3),
            resolvedSourceIndices = setOf(0),
            lookupFailedSourceIndices = setOf(2),
        )

        assertEquals(
            listOf(
                YouTubePlaylistSkippedTrack(
                    tracks[1],
                    YouTubePlaylistSkippedTrack.Reason.NO_APPLE_MATCH,
                ),
                YouTubePlaylistSkippedTrack(
                    tracks[2],
                    YouTubePlaylistSkippedTrack.Reason.LISTENBRAINZ_LOOKUP_FAILED,
                ),
                YouTubePlaylistSkippedTrack(
                    tracks[3],
                    YouTubePlaylistSkippedTrack.Reason.NO_LISTENBRAINZ_MATCH,
                ),
            ),
            skipped,
        )
    }
}
