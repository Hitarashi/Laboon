package org.shilpo.laboon.ui.screens.album

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumRelatedDestinationTest {

    @Test
    fun artistDestination_trimsNameAndIdentifiesArtist() {
        val destination = albumArtistDestination("  Björk  ")

        assertEquals(AlbumRelatedDestination.Artist("Björk"), destination)
        assertEquals("Artist", destination?.title)
        assertEquals("ARTIST", destination?.identity)
    }

    @Test
    fun recordLabelDestination_trimsNameAndIdentifiesRecordLabel() {
        val destination = albumRecordLabelDestination("  T-Series  ")

        assertEquals(AlbumRelatedDestination.RecordLabel("T-Series"), destination)
        assertEquals("Record label", destination?.title)
        assertEquals("RECORD LABEL", destination?.identity)
    }

    @Test
    fun destinations_withBlankNamesAreNotOpened() {
        assertNull(albumArtistDestination(null))
        assertNull(albumArtistDestination("  "))
        assertNull(albumRecordLabelDestination(null))
        assertNull(albumRecordLabelDestination("\t"))
    }
}
