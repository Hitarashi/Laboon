package org.shilpo.laboon.lyricsporn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricspornEditorialNotesTest {

    @Test
    fun preferredAlbumEditorialNotes_prefersStandardCopy() {
        assertEquals(
            "Full editorial copy",
            preferredAlbumEditorialNotes(
                standard = " Full editorial copy ",
                short = "Short copy",
            ),
        )
    }

    @Test
    fun preferredAlbumEditorialNotes_fallsBackToShortWhenStandardIsBlank() {
        assertEquals(
            "Short copy",
            preferredAlbumEditorialNotes(standard = " ", short = " Short copy "),
        )
    }

    @Test
    fun preferredAlbumEditorialNotes_returnsNullWhenBothAreMissing() {
        assertNull(preferredAlbumEditorialNotes(standard = null, short = null))
    }
}
