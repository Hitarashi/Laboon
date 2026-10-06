package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.rip.AutoRipPlanner
import org.shilpo.laboon.rip.AutoRipPlanner.Decisions
import org.shilpo.laboon.rip.AutoRipPlanner.Verdict
import org.shilpo.laboon.search.BatchAvailabilityLookup
import org.shilpo.laboon.search.CachedTrackAvailability

class AutoRipPlannerTest {

    private fun track(id: String?, title: String = "Song") = HomeTrack(
        id = id ?: "apple_$title",
        title = title,
        artist = "Artist",
        providerTrackId = id,
    )

    private fun candidate(id: String) =
        AutoRipPlanner.Candidate(id, track(id))

    private fun availability(vararg formats: String) = CachedTrackAvailability(
        variants = formats.mapIndexed { index, format ->
            TrackFormatVariant(format = format, backendTrackId = index + 1)
        },
        preferredCodec = formats.firstOrNull(),
        playbackTrackId = formats.size.takeIf { it > 0 }?.let { 1 },
    )

    @Test
    fun `only digit-only provider ids are accepted`() {
        assertEquals("123", AutoRipPlanner.normalizeProviderTrackId(" 123 "))
        assertEquals(null, AutoRipPlanner.normalizeProviderTrackId("12a3"))
        assertEquals(null, AutoRipPlanner.normalizeProviderTrackId(""))
        assertEquals(null, AutoRipPlanner.normalizeProviderTrackId("  "))
        assertEquals(null, AutoRipPlanner.normalizeProviderTrackId(null))
    }

    @Test
    fun `tracks without a valid provider id never become candidates`() {
        val candidates = AutoRipPlanner.candidates(
            listOf(
                track("111"),
                track("abc"),
                track(null),
                track("2 2"),
            )
        )

        assertEquals(listOf("111"), candidates.map { it.providerTrackId })
    }

    @Test
    fun `duplicate provider ids collapse to the first observed track`() {
        val candidates = AutoRipPlanner.candidates(
            listOf(
                track("111", title = "First"),
                track("111", title = "Second"),
            )
        )

        assertEquals(1, candidates.size)
        assertEquals("First", candidates.single().track.title)
    }

    @Test
    fun `known cached candidates are never looked up again`() {
        val cached = candidate("111")
        val fresh = candidate("222")

        val plan = AutoRipPlanner.plan(
            candidates = listOf(cached, fresh),
            knownCachedIds = setOf("111"),
        )

        assertEquals(listOf(cached), plan.knownCached)
        assertEquals(listOf(fresh), plan.needsLookup)
    }

    @Test
    fun `an id absent from a successful response is queued for rip`() {
        val requested = listOf(candidate("111"))

        val decisions = AutoRipPlanner.decide(requested, BatchAvailabilityLookup.EMPTY)

        assertEquals(emptyMap<String, List<String>>(), decisions.cached)
        assertEquals(listOf("111"), decisions.rip.map { it.providerTrackId })
        assertTrue(decisions.unknown.isEmpty())
    }

    @Test
    fun `a resolved id with cached formats is cached locally, never ripped`() {
        val decisions = AutoRipPlanner.decide(
            listOf(candidate("111")),
            BatchAvailabilityLookup(cached = mapOf("111" to availability("alac", "aac"))),
        )

        assertEquals(mapOf("111" to listOf("alac", "aac")), decisions.cached)
        assertTrue(decisions.rip.isEmpty())
    }

    @Test
    fun `an unresolved id is unknown rather than ripped`() {
        val decisions = AutoRipPlanner.decide(
            listOf(candidate("111")),
            BatchAvailabilityLookup(unresolvedIds = setOf("111")),
        )

        assertTrue(decisions.rip.isEmpty())
        assertEquals(listOf("111"), decisions.unknown.map { it.providerTrackId })
        assertFalse(decisions.hasWork)
    }

    @Test
    fun `a batch mixes cached, rip and unknown verdicts`() {
        val decisions = AutoRipPlanner.decide(
            listOf(candidate("1"), candidate("2"), candidate("3")),
            BatchAvailabilityLookup(
                cached = mapOf("1" to availability("alac")),
                unresolvedIds = setOf("3"),
            ),
        )

        assertEquals(mapOf("1" to listOf("alac")), decisions.cached)
        assertEquals(listOf("2"), decisions.rip.map { it.providerTrackId })
        assertEquals(listOf("3"), decisions.unknown.map { it.providerTrackId })
    }

    @Test
    fun `unknown ids are retried exactly once and then dropped`() {
        val unknown = listOf(candidate("111"))

        assertEquals(unknown, AutoRipPlanner.retryOnce(unknown, attempt = 1))
        assertTrue(AutoRipPlanner.retryOnce(unknown, attempt = 2).isEmpty())
    }

    @Test
    fun `a successful retry clears the unknown verdict`() {
        val requested = listOf(candidate("111"))

        val retry = AutoRipPlanner.retryOnce(
            AutoRipPlanner.decide(
                requested,
                BatchAvailabilityLookup.unavailable(listOf("111"))
            ).unknown,
            attempt = 1,
        )
        val decisions = AutoRipPlanner.decide(retry, BatchAvailabilityLookup.EMPTY)

        assertEquals(listOf("111"), decisions.rip.map { it.providerTrackId })
        assertTrue(decisions.unknown.isEmpty())
    }

    @Test
    fun `verdict classification stays stable across repeats`() {
        val requested = listOf(candidate("111"))
        val lookup = BatchAvailabilityLookup(cached = mapOf("111" to availability("alac")))

        val first: Decisions = AutoRipPlanner.decide(requested, lookup)
        val second: Decisions = AutoRipPlanner.decide(requested, lookup)

        assertEquals(first, second)
        assertTrue(first.rip.isEmpty())
    }

    @Test
    fun `lookup requests are chunked to the batch size`() {
        val candidates = (1..120).map { candidate(it.toString()) }

        val batches = AutoRipPlanner.batches(candidates)

        assertEquals(listOf(50, 50, 20), batches.map { it.size })
        assertEquals(120, batches.sumOf { it.size })
    }

    @Test
    fun `an empty candidate list produces no batches`() {
        assertTrue(AutoRipPlanner.batches(emptyList()).isEmpty())
    }

    @Test
    fun `only ids from a failed batch are reported unresolved`() {
        val lookup = BatchAvailabilityLookup(unresolvedIds = setOf("2"))

        assertEquals(listOf("2"), lookup.unresolvedIn(listOf("1", "2", "3")))
    }

    @Test
    fun `a successful lookup reports nothing unresolved`() {
        assertTrue(BatchAvailabilityLookup.EMPTY.unresolvedIn(listOf("1", "2")).isEmpty())
    }

    @Test
    fun `verdict is a closed set of cached, needs-rip and unknown`() {
        val cached: Verdict = Verdict.Cached(listOf("alac"))
        val needsRip: Verdict = Verdict.NeedsRip
        val unknown: Verdict = Verdict.Unknown

        assertEquals(listOf("alac"), (cached as Verdict.Cached).formats)
        assertEquals(Verdict.NeedsRip, needsRip)
        assertEquals(Verdict.Unknown, unknown)
    }
}
