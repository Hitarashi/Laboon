package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.rip.AutoRipPlanner
import org.shilpo.laboon.search.BatchAvailabilityLookup
import org.shilpo.laboon.search.CachedTrackAvailability

/**
 * Regression tests for the reported failure: tracks showing a cloud_download icon (i.e. the UI
 * considered them NOT cached) were never sent to startRip by the auto-rip coordinator.
 */
class AutoRipReproTest {

    private fun candidate(id: String) = AutoRipPlanner.Candidate(
        id,
        HomeTrack(id = "apple_$id", title = "Song $id", artist = "Artist", providerTrackId = id),
    )

    private fun cachedAvailability(vararg formats: String) = CachedTrackAvailability(
        variants = formats.mapIndexed { index, f ->
            TrackFormatVariant(format = f, backendTrackId = index + 1)
        },
        preferredCodec = formats.firstOrNull(),
        playbackTrackId = formats.size.takeIf { it > 0 }?.let { 1 },
    )

    /**
     * The UI's cloud_download icon is driven by `!track.isCached`, and enrichAvailability treats
     * an id the server did not mention as not cached. The coordinator must agree with that, or
     * the exact tracks the user is invited to download are the ones never auto-ripped.
     */
    @Test
    fun `an id the server omits from a successful response is not cached, so it rips`() {
        val lookup = BatchAvailabilityLookup(cached = mapOf("2" to cachedAvailability("alac")))

        val decisions = AutoRipPlanner.decide(
            listOf(candidate("1"), candidate("2")),
            lookup,
        )

        assertEquals(
            "omitted id must be queued for rip",
            listOf("1"),
            decisions.rip.map { it.providerTrackId },
        )
    }

    @Test
    fun `an empty successful response means every requested id needs a rip`() {
        val decisions = AutoRipPlanner.decide(
            listOf(candidate("1"), candidate("2"), candidate("3")),
            BatchAvailabilityLookup.EMPTY,
        )

        assertEquals(listOf("1", "2", "3"), decisions.rip.map { it.providerTrackId })
        assertTrue(decisions.unknown.isEmpty())
    }

    @Test
    fun `only an explicitly reported batch failure leaves ids unknown`() {
        val decisions = AutoRipPlanner.decide(
            listOf(candidate("1"), candidate("2")),
            BatchAvailabilityLookup(unresolvedIds = setOf("1", "2")),
        )

        assertTrue(decisions.rip.isEmpty())
        assertEquals(listOf("1", "2"), decisions.unknown.map { it.providerTrackId })
    }
}
