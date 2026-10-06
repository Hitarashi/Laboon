package org.shilpo.laboon.rip

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.search.BatchAvailabilityLookup
import org.shilpo.laboon.search.SearchRepositoryImpl
import org.shilpo.laboon.search.TrackAvailabilityLookup
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where a batch of observed tracks came from. Only used for diagnostics and for scoping
 * [observe] calls; the coordinator treats every source the same way.
 */
enum class AutoRipSource {
    HOME_FEED,
    SEARCH,
    PLAYBACK_QUEUE,
    ALBUM_DETAILS,
}

/**
 * Watches the tracks the app is showing or playing and keeps the server's rip queue fed.
 *
 * Whenever a track shows up in the home feeds, search results/history, the playback queue
 * (whether or not the queue sheet is open) or an open album, its provider track id is checked
 * against [AutoRipCache]. Known-cached ids are skipped. The rest are batched through
 * `POST /api/v1/lookup`; ids the server reports as not cached are handed to
 * [startRip] exactly as the manual download button would. The client never picks a
 * codec/quality: it only sends the provider track id.
 *
 * Design notes:
 * - Only positive ("is cached, with these formats") verdicts are persisted, via [AutoRipCache].
 *   A "not cached" answer is deliberately forgotten so the next scan can notice a finished rip.
 * - A failed lookup is retried once immediately, then dropped until the next scan; it never
 *   turns into a rip.
 * - Scans are coalesced through a debounce window plus a conflated channel so frequent
 *   recompositions cannot flood the backend, and a periodic tick keeps retrying unknown ids
 *   while a session is live.
 */
class AutoRipCoordinator(
    private val sessionStore: SessionStore,
    private val availabilityLookup: TrackAvailabilityLookup =
        SearchRepositoryImpl(sessionStore),
    private val cache: AutoRipCache,
    private val startRip: (HomeTrack) -> Boolean,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val rescanIntervalMs: Long = RESCAN_INTERVAL_MS,
    private val ripSuppressionMs: Long = RIP_SUPPRESSION_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private data class Observed(
        val source: AutoRipSource,
        val tracks: List<HomeTrack>,
    )

    private val observed = LinkedHashMap<AutoRipSource, List<HomeTrack>>()
    private val observedLock = Any()
    private val scanMutex = Mutex()

    /** In-memory suppression so a single un-rippable track cannot re-rip-storm every scan. */
    private val ripRequestedAtMs = LinkedHashMap<String, Long>()

    private val scanRequests = Channel<Unit>(Channel.CONFLATED)
    private var scanLoop: Job? = null

    /** Starts the debounce loop and the periodic rescan. Safe to call repeatedly. */
    fun start() {
        if (scanLoop?.isActive == true) return
        scanLoop = scope.launch {
            launch {
                for (ignored in scanRequests) {
                    delay(debounceMs)
                    runScan()
                }
            }
            launch {
                while (true) {
                    delay(rescanIntervalMs)
                    scanRequests.trySend(Unit)
                }
            }
        }
    }

    /** Stops scanning and forgets what was observed. Rip requests already sent stand. */
    fun stop() {
        scanLoop?.cancel()
        scanLoop = null
        synchronized(observedLock) { observed.clear() }
        synchronized(ripRequestedAtMs) { ripRequestedAtMs.clear() }
        scanRequests.trySend(Unit)
    }

    /**
     * Records the current tracks for [source], replacing whatever was recorded before. An
     * empty list drops that source. Always schedules a scan; the debounce coalesces bursts.
     */
    fun observe(source: AutoRipSource, tracks: List<HomeTrack>) {
        if (!hasSession()) {
            log("observe($source, ${tracks.size}) ignored: no session")
            return
        }
        synchronized(observedLock) {
            if (tracks.isEmpty()) observed.remove(source) else observed[source] = tracks
        }
        log("observe($source, ${tracks.size} tracks)")
        scanRequests.trySend(Unit)
    }

    /**
     * Drops positive verdicts for these ids so the next scan re-checks the server. Used for
     * explicit refresh semantics (album reload) where the local cache may be out of date.
     */
    fun invalidate(providerTrackIds: Collection<String>) {
        if (providerTrackIds.isEmpty()) return
        val ids = providerTrackIds.mapNotNull(AutoRipPlanner::normalizeProviderTrackId)
        if (ids.isEmpty()) return
        cache.invalidate(ids)
        synchronized(ripRequestedAtMs) {
            ids.forEach(ripRequestedAtMs::remove)
        }
        scanRequests.trySend(Unit)
    }

    /** Requests a scan on the next tick without waiting for a state change. */
    fun requestScan() {
        scanRequests.trySend(Unit)
    }

    /**
     * Handles a rip that just completed successfully: drops any stale verdict for the id and
     * looks it up straight away so the freshly ripped formats are cached without waiting for
     * the debounce window or the periodic rescan.
     *
     * Safe to call for an id that is not currently observed; the positive verdict is still
     * recorded so a later scan skips re-looking it up.
     */
    fun onRipCompleted(providerTrackId: String) {
        val id = AutoRipPlanner.normalizeProviderTrackId(providerTrackId) ?: return
        cache.invalidate(listOf(id))
        synchronized(ripRequestedAtMs) { ripRequestedAtMs.remove(id) }

        if (!hasSession()) {
            log("rip completed for $id; lookup deferred (no session)")
            return
        }
        scope.launch { refreshAvailabilityNow(id) }
    }

    /**
     * Looks up a single id outside the debounced scan loop. Any positive verdict is cached so
     * the track is treated as available everywhere without another network call.
     */
    suspend fun refreshAvailabilityNow(providerTrackId: String): BatchAvailabilityLookup {
        val id = AutoRipPlanner.normalizeProviderTrackId(providerTrackId)
            ?: return BatchAvailabilityLookup.EMPTY

        inFlightRefreshes[id]?.let { return it.await() }

        val deferred = CompletableDeferred<BatchAvailabilityLookup>()
        if (inFlightRefreshes.putIfAbsent(id, deferred) != null) {
            return deferred.await()
        }

        return try {
            val lookup = lookupOnce(listOf(id))
            cache.rememberCached(
                lookup.cached.mapValues { (_, availability) -> availability.formats }
            )
            log(
                "availability refresh $id: cached=${lookup.cached.keys} " +
                        "unresolved=${lookup.unresolvedIds.isNotEmpty()}"
            )
            deferred.complete(lookup)
            lookup
        } catch (error: Throwable) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            inFlightRefreshes.remove(id)
        }
    }

    private val inFlightRefreshes =
        ConcurrentHashMap<String, CompletableDeferred<BatchAvailabilityLookup>>()

    private fun hasSession(): Boolean = sessionStore.getSession() != null

    private fun observedTracks(): List<HomeTrack> =
        synchronized(observedLock) { observed.values.flatten() }

    private suspend fun runScan() {
        try {
            if (!hasSession()) {
                log("scan skipped: no session")
                return
            }
            scanMutex.withLock {
                if (!hasSession()) return
                val candidates = AutoRipPlanner.candidates(observedTracks())
                if (candidates.isEmpty()) {
                    log("scan: no candidates")
                    return
                }

                val cacheableIds = candidates.map(AutoRipPlanner.Candidate::providerTrackId)
                val plan = AutoRipPlanner.plan(
                    candidates = candidates,
                    knownCachedIds = cache.knownCachedIds(cacheableIds),
                )
                if (plan.needsLookup.isEmpty()) {
                    log("scan: ${candidates.size} candidate(s) all known cached")
                    return
                }

                val now = nowMs()
                val toLookup = plan.needsLookup.filterNot { isRipRequestSuppressed(it, now) }
                log(
                    "scan: ${candidates.size} candidate(s), " +
                            "${plan.knownCached.size} cached, ${plan.needsLookup.size} to look up, " +
                            "${toLookup.size} after suppression"
                )
                if (toLookup.isEmpty()) return

                for (batch in AutoRipPlanner.batches(toLookup)) {
                    processBatch(batch)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            runCatching { Log.w(TAG, "Auto rip scan failed: ${error.message}") }
        }
    }

    private fun isRipRequestSuppressed(candidate: AutoRipPlanner.Candidate, now: Long): Boolean =
        synchronized(ripRequestedAtMs) {
            val lastRequest = ripRequestedAtMs[candidate.providerTrackId] ?: return false
            val age = now - lastRequest
            if (age < ripSuppressionMs) return true
            ripRequestedAtMs.remove(candidate.providerTrackId)
            false
        }

    private suspend fun processBatch(batch: List<AutoRipPlanner.Candidate>) {
        val requestedIds = batch.map(AutoRipPlanner.Candidate::providerTrackId)

        var decisions = AutoRipPlanner.decide(batch, lookupOnce(requestedIds))
        if (decisions.unknown.isEmpty()) {
            applyDecisions(decisions)
            return
        }


        val retry = AutoRipPlanner.retryOnce(decisions.unknown, attempt = 1)
        if (retry.isNotEmpty()) {
            val retryLookup = lookupOnce(retry.map(AutoRipPlanner.Candidate::providerTrackId))
            decisions = AutoRipPlanner.decide(retry, retryLookup)
        }
        applyDecisions(decisions)
    }

    private suspend fun lookupOnce(ids: List<String>): BatchAvailabilityLookup {
        if (ids.isEmpty()) return BatchAvailabilityLookup.EMPTY
        return runCatching { availabilityLookup.lookupAvailableFormats(ids) }
            .getOrElse { error ->

                runCatching {
                    Log.w(TAG, "lookup failed for ${ids.size} id(s): ${error.message}")
                }
                BatchAvailabilityLookup.unavailable(ids)
            }
    }

    private fun applyDecisions(decisions: AutoRipPlanner.Decisions) {
        cache.rememberCached(decisions.cached)
        if (decisions.rip.isEmpty()) {
            log("batch: cached=${decisions.cached.size} rip=0 unknown=${decisions.unknown.size}")
            return
        }


        val now = nowMs()
        val accepted = mutableListOf<AutoRipPlanner.Candidate>()

        decisions.rip.forEach { candidate ->
            if (startRip(candidate.track)) {
                accepted += candidate
            } else {
                log("startRip declined for ${candidate.providerTrackId}; will retry next scan")
            }
        }
        synchronized(ripRequestedAtMs) {
            accepted.forEach { ripRequestedAtMs[it.providerTrackId] = now }
        }
        log(
            "batch: cached=${decisions.cached.size} rip=${accepted.size}/${decisions.rip.size} " +
                    "unknown=${decisions.unknown.size}"
        )
    }

    private fun log(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private companion object {
        const val TAG = "AutoRip"
        const val DEBOUNCE_MS = 1_500L
        const val RESCAN_INTERVAL_MS = 5 * 60_000L
        const val RIP_SUPPRESSION_MS = 10 * 60_000L
    }
}
