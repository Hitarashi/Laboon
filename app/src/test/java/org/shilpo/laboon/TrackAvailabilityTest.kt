package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackAvailability
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.search.BatchAvailabilityLookup
import org.shilpo.laboon.search.CachedTrackAvailability

class TrackAvailabilityTest {

    private fun track(id: String) = HomeTrack(
        id = "apple_$id",
        title = "Song $id",
        artist = "Artist",
        providerTrackId = id,
    )

    private fun availability(vararg formats: String) = CachedTrackAvailability(
        variants = formats.mapIndexed { index, format ->
            TrackFormatVariant(format = format, backendTrackId = index + 1)
        },
        preferredCodec = formats.firstOrNull(),
        playbackTrackId = formats.size.takeIf { it > 0 }?.let { 1 },
    )

    @Test
    fun `a ripped track becomes cached and playable`() {
        val updated = TrackAvailability.apply(
            listOf(track("111")),
            mapOf("111" to availability("alac", "aac")),
        ).single()

        assertTrue(updated.isCached)
        assertTrue(updated.isPlayable)
        assertEquals(listOf("alac", "aac"), updated.availableFormats)
        assertEquals("alac", updated.codec)
    }

    @Test
    fun `tracks without a matching verdict are untouched`() {
        val original = track("111")

        val updated = TrackAvailability.apply(
            listOf(original),
            mapOf("222" to availability("alac")),
        )

        assertSame(original, updated.single())
        assertFalse(updated.single().isCached)
    }

    @Test
    fun `only the ripped track in a mixed list changes`() {
        val result = TrackAvailability.apply(
            listOf(track("111"), track("222"), track("333")),
            mapOf("222" to availability("alac")),
        )

        assertEquals(listOf(false, true, false), result.map { it.isCached })
    }

    @Test
    fun `an empty availability map returns the same list instance`() {
        val tracks = listOf(track("111"))

        assertSame(tracks, TrackAvailability.apply(tracks, emptyMap()))
    }

    @Test
    fun `an unchanged track list is returned by identity so callers can skip work`() {
        val tracks = listOf(track("111"))

        assertSame(
            tracks,
            TrackAvailability.apply(tracks, mapOf("999" to availability("alac"))),
        )
    }

    @Test
    fun `the batch lookup overload applies its cached verdicts`() {
        val updated = TrackAvailability.apply(
            listOf(track("111")),
            BatchAvailabilityLookup(cached = mapOf("111" to availability("alac"))),
        ).single()

        assertTrue(updated.isCached)
    }

    @Test
    fun `a failed batch changes nothing`() {
        val tracks = listOf(track("111"))

        assertSame(
            tracks,
            TrackAvailability.apply(tracks, BatchAvailabilityLookup.unavailable(listOf("111"))),
        )
    }

    @Test
    fun `re-applying the same verdict is idempotent`() {
        val once =
            TrackAvailability.apply(listOf(track("111")), mapOf("111" to availability("alac")))
        val twice = TrackAvailability.apply(once, mapOf("111" to availability("alac")))

        assertEquals(once, twice)
    }
}
