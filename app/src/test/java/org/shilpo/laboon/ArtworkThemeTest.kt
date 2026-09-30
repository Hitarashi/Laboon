package org.shilpo.laboon

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.ui.design.theme.ArtworkColorExtractor
import org.shilpo.laboon.ui.design.theme.ArtworkColorSchemeGenerator

class ArtworkThemeTest {
    @Test
    fun testRgbToHsl() {
        val redHsl = ArtworkColorExtractor.rgbToHsl(255, 0, 0)
        assertEquals(0f, redHsl[0], 0.01f)
        assertEquals(1f, redHsl[1], 0.01f)
        assertEquals(0.5f, redHsl[2], 0.01f)

        val greenHsl = ArtworkColorExtractor.rgbToHsl(0, 255, 0)
        assertEquals(120f, greenHsl[0], 0.01f)
        assertEquals(1f, greenHsl[1], 0.01f)
        assertEquals(0.5f, greenHsl[2], 0.01f)

        val blueHsl = ArtworkColorExtractor.rgbToHsl(0, 0, 255)
        assertEquals(240f, blueHsl[0], 0.01f)
        assertEquals(1f, blueHsl[1], 0.01f)
        assertEquals(0.5f, blueHsl[2], 0.01f)
    }

    @Test
    fun testExtraction() {
        val redPixels = IntArray(100) { (0xFF shl 24) or (0xFF shl 16) }
        val redSeed = ArtworkColorExtractor.extractSeedFromPixels(redPixels)
        assertNotNull(redSeed)

        val grayPixels = IntArray(100) { (0xFF shl 24) or (0x80 shl 16) or (0x80 shl 8) or 0x80 }
        val graySeed = ArtworkColorExtractor.extractSeedFromPixels(grayPixels)
        assertNull(graySeed)
    }

    @Test
    fun testColorSchemeGeneration() {
        val seed = Color.hsl(210f, 0.8f, 0.5f)
        val darkScheme = ArtworkColorSchemeGenerator.generateColorScheme(seed, true)
        assertNotNull(darkScheme)
        val lightScheme = ArtworkColorSchemeGenerator.generateColorScheme(seed, false)
        assertNotNull(lightScheme)
    }
}
