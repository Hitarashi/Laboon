package org.shilpo.laboon.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerArtistParsingTest {

    @Test
    fun testSingleArtist() {
        val artists = "Daft Punk".splitArtistsByDelimiters()
        assertEquals(listOf("Daft Punk"), artists)
    }

    @Test
    fun testSlashDelimiter() {
        val artists = "Artist1 / Artist2".splitArtistsByDelimiters()
        assertEquals(listOf("Artist1", "Artist2"), artists)
    }

    @Test
    fun testWordDelimiters() {
        val artists = "Drake feat. Rihanna".splitArtistsByDelimiters()
        assertEquals(listOf("Drake", "Rihanna"), artists)
    }

    @Test
    fun testMultipleDelimitersCombined() {
        val artists = "Artist A ft. Artist B, Artist C & Artist D".splitArtistsByDelimiters()
        assertEquals(listOf("Artist A", "Artist B", "Artist C", "Artist D"), artists)
    }

    @Test
    fun testEscapedDelimiter() {
        val artists = "AC\\\\/DC".splitArtistsByDelimiters()
        assertEquals(listOf("AC/DC"), artists)
    }

    @Test
    fun testEmptyOrBlankString() {
        assertEquals(emptyList<String>(), "".splitArtistsByDelimiters())
        assertEquals(emptyList<String>(), "   ".splitArtistsByDelimiters())
    }

    @Test
    fun testArtistShortcutShapeCornerSizes() {
        val singleShape = artistShortcutShape(0, 1)
        assertEquals(26f, singleShape.topStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)))

        val topShape = artistShortcutShape(0, 3)
        assertEquals(26f, topShape.topStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)))
        assertEquals(10f, topShape.bottomStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)))

        val middleShape = artistShortcutShape(1, 3)
        assertEquals(10f, middleShape.topStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)))
        assertEquals(10f, middleShape.bottomStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)))

        val bottomShape = artistShortcutShape(2, 3)
        assertEquals(10f, bottomShape.topStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)))
        assertEquals(26f, bottomShape.bottomStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)))
    }
}
