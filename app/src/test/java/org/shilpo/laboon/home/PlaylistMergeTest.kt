package org.shilpo.laboon.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistMergeTest {
    @Test
    fun keepsDestinationTracksFirstAndDeduplicatesInSourceOrder() {
        assertEquals(
            listOf("104", "102", "101", "103"),
            combinePlaylistTrackIds(
                destinationTrackIds = listOf("104", "102"),
                sourceTrackIds = listOf(listOf("101", "102"), listOf("103", "101")),
            ),
        )
    }
}
