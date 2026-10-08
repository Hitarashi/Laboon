package org.shilpo.laboon.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.home.TrackFormatVariant

class DefaultAudioVariantSelectorTest {
    private val variants = listOf(
        TrackFormatVariant(format = "ec-3", backendTrackId = 1),
        TrackFormatVariant(format = "alac", backendTrackId = 2),
        TrackFormatVariant(format = "aac", backendTrackId = 3),
    )

    @Test
    fun `Dolby is preferred when device supports E-AC-3`() {
        assertEquals(
            variants[0],
            DefaultAudioVariantSelector.selectDefault(variants, dolbySupported = true),
        )
    }

    @Test
    fun `lossless is preferred over Dolby when device lacks E-AC-3`() {
        assertEquals(
            variants[1],
            DefaultAudioVariantSelector.selectDefault(variants, dolbySupported = false),
        )
    }

    @Test
    fun `AAC is selected when lossless and Dolby are unavailable to the device`() {
        assertEquals(
            variants[2],
            DefaultAudioVariantSelector.selectDefault(
                variants.filter { it.format != "alac" },
                dolbySupported = false,
            ),
        )
    }

    @Test
    fun `unsupported Dolby is not selected when it is the only variant`() {
        assertNull(
            DefaultAudioVariantSelector.selectDefault(
                listOf(variants[0]),
                dolbySupported = false,
            ),
        )
    }
}
