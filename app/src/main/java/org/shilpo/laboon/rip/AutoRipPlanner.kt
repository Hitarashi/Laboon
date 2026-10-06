package org.shilpo.laboon.rip

import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.search.BatchAvailabilityLookup

/**
 * Pure decision layer for the auto-rip flow. No I/O, no coroutines, no Android: given the
 * observed candidates and what a lookup returned, decide which ids to look up, which are
 * known cached, which need a rip, and which are still unknown.
 */
object AutoRipPlanner {

    /** A candidate the app can act on: a valid digit-only provider id plus its track. */
    data class Candidate(
        val providerTrackId: String,
        val track: HomeTrack,
    )

    data class Plan(
        val candidates: List<Candidate>,
        val knownCached: List<Candidate>,
        val needsLookup: List<Candidate>,
    )

    /** Verdict for one requested id after a successful (parseable) lookup. */
    sealed interface Verdict {
        /** Server has it ripped; formats are worth caching locally. */
        data class Cached(val formats: List<String>) : Verdict

        /** Server answered and has no cached formats: enqueue for rip. */
        data object NeedsRip : Verdict

        /** Batch failed or the server never mentioned this id: retry, do not rip. */
        data object Unknown : Verdict
    }

    data class Decisions(
        val cached: Map<String, List<String>>,
        val rip: List<Candidate>,
        val unknown: List<Candidate>,
    ) {
        val hasWork: Boolean get() = cached.isNotEmpty() || rip.isNotEmpty()
    }

    fun normalizeProviderTrackId(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    /**
     * Turns observed tracks into deduplicated candidates, dropping anything without a valid
     * digit-only provider track id (nothing downstream can rip those).
     */
    fun candidates(tracks: Iterable<HomeTrack>): List<Candidate> {
        val byId = LinkedHashMap<String, HomeTrack>()
        for (track in tracks) {
            val id = normalizeProviderTrackId(track.providerTrackId) ?: continue
            byId.putIfAbsent(id, track)
        }
        return byId.map { (id, track) -> Candidate(id, track) }
    }

    /**
     * Splits candidates into already-cached (per the positive-only local cache) and the rest,
     * which need a server lookup. Known-cached ids are never re-looked-up.
     */
    fun plan(
        candidates: List<Candidate>,
        knownCachedIds: Set<String>,
    ): Plan {
        val known = candidates.filter { it.providerTrackId in knownCachedIds }
        val lookup = candidates.filterNot { it.providerTrackId in knownCachedIds }
        return Plan(candidates = candidates, knownCached = known, needsLookup = lookup)
    }

    /**
     * Applies a lookup result.
     *
     * A successful lookup is a complete answer: the server only returns entries for tracks it
     * holds formats for, so anything requested but absent from the cached map is *not cached*
     * and must be queued for a rip. That is the same rule the UI uses to decide whether to
     * show the download icon, so the two can never disagree.
     *
     * Only ids the lookup explicitly reported as unresolved (the request failed) stay
     * [Verdict.Unknown], so a flaky batch is retried instead of spamming rips.
     */
    fun decide(
        requested: List<Candidate>,
        lookup: BatchAvailabilityLookup,
    ): Decisions {
        val cached = LinkedHashMap<String, List<String>>()
        val rip = mutableListOf<Candidate>()
        val unknown = mutableListOf<Candidate>()

        for (candidate in requested) {
            val id = candidate.providerTrackId
            val availability = lookup.cached[id]
            when {
                availability != null -> cached[id] = availability.formats
                id in lookup.unresolvedIds -> unknown += candidate
                else -> rip += candidate
            }
        }
        return Decisions(cached = cached, rip = rip, unknown = unknown)
    }

    /**
     * Splits [Decisions.unknown] into the ids to retry immediately (one retry per batch)
     * and the rest, which wait for the next scan or state change rather than being retried
     * in a tight loop.
     */
    fun retryOnce(unknown: List<Candidate>, attempt: Int): List<Candidate> =
        if (attempt < MAX_LOOKUP_ATTEMPTS) unknown else emptyList()

    /** Chunks lookup requests so one payload never grows without bound. */
    fun batches(
        candidates: List<Candidate>,
        batchSize: Int = LOOKUP_BATCH_SIZE
    ): List<List<Candidate>> {
        if (batchSize <= 0) return listOf(candidates)
        return candidates.chunked(batchSize)
    }

    const val MAX_LOOKUP_ATTEMPTS = 2
    const val LOOKUP_BATCH_SIZE = 50
}
