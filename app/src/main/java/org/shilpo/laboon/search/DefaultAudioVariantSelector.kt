package org.shilpo.laboon.search

import org.shilpo.laboon.home.TrackFormatVariant
import java.util.Locale

internal object DefaultAudioVariantSelector {
    fun selectDefault(
        variants: List<TrackFormatVariant>,
        dolbySupported: Boolean,
    ): TrackFormatVariant? = variants.asSequence()
        .filterNot { !dolbySupported && it.format.isDolbyFormat() }
        .maxByOrNull { it.format.preference() }

    private fun String.isDolbyFormat(): Boolean = normalizeFormat() in DOLBY_FORMATS

    private fun String.preference(): Int = when (normalizeFormat()) {
        "ec-3", "e-ac-3", "dolby", "dolby-atmos", "atmos" -> 3
        "alac" -> 2
        "aac" -> 1
        else -> 0
    }

    private fun String.normalizeFormat(): String = lowercase(Locale.ROOT)
        .replace('_', '-')
        .replace("ec3", "ec-3")
        .replace("eac3", "e-ac-3")

    private val DOLBY_FORMATS = setOf("ec-3", "e-ac-3", "dolby", "dolby-atmos", "atmos")
}
