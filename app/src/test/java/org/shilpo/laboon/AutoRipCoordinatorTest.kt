package org.shilpo.laboon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.AuthUser
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.rip.AutoRipCache
import org.shilpo.laboon.rip.AutoRipCoordinator
import org.shilpo.laboon.rip.AutoRipSource
import org.shilpo.laboon.search.BatchAvailabilityLookup
import org.shilpo.laboon.search.CachedTrackAvailability
import org.shilpo.laboon.search.TrackAvailabilityLookup

class AutoRipCoordinatorTest {

    private val store = FakeKeyValueStore()
    private val cache = AutoRipCache(store)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ripped = mutableListOf<String>()
    private val lookedUpBatches = mutableListOf<List<String>>()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun sessionStore(withSession: Boolean = true): SessionStore {
        val sessionStore = SessionStore(store)
        if (withSession) {
            sessionStore.saveSession(
                AuthSession(
                    serverUrl = "https://example.com",
                    token = "token",
                    refreshToken = "refresh",
                    expiresAtUnix = Long.MAX_VALUE,
                    user = AuthUser(
                        telegramId = 1L,
                        name = "Tester",
                        username = null,
                        firstName = null,
                        lastName = null,
                    ),
                )
            )
        }
        return sessionStore
    }

    private fun track(id: String?) = HomeTrack(
        id = "apple_${id.orEmpty()}",
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

    private fun coordinator(
        lookup: TrackAvailabilityLookup,
        withSession: Boolean = true,
        ripSuppressionMs: Long = 0L,
    ) = AutoRipCoordinator(
        sessionStore = sessionStore(withSession),
        availabilityLookup = lookup,
        cache = cache,
        startRip = { track ->
            track.providerTrackId?.let(ripped::add) != null
            true
        },
        scope = scope,
        debounceMs = 1L,
        ripSuppressionMs = ripSuppressionMs,
        rescanIntervalMs = Long.MAX_VALUE,
    ).also { it.start() }

    private fun recordingLookup(
        result: (ids: List<String>) -> BatchAvailabilityLookup,
    ): TrackAvailabilityLookup = TrackAvailabilityLookup { ids ->
        synchronized(lookedUpBatches) { lookedUpBatches += ids }
        result(ids)
    }

    /** The scan runs on the coordinator's own scope; wait for it to settle. */
    private fun settle() = runBlocking { delay(SCAN_SETTLE_MS) }

    @Test
    fun `no session means nothing is observed or ripped`() {
        val autoRip = coordinator(
            lookup = recordingLookup { BatchAvailabilityLookup.EMPTY },
            withSession = false,
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()

        assertTrue(lookedUpBatches.isEmpty())
        assertTrue(ripped.isEmpty())
    }

    @Test
    fun `a track the server has not cached is sent to startRip`() {
        val autoRip = coordinator(
            lookup = recordingLookup { BatchAvailabilityLookup.EMPTY }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()

        assertEquals(listOf("111"), ripped)
    }

    @Test
    fun `a track the server has cached is never ripped`() {
        val autoRip = coordinator(
            lookup = recordingLookup {
                BatchAvailabilityLookup(cached = mapOf("111" to availability("alac")))
            }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()

        assertTrue(ripped.isEmpty())
        assertTrue(cache.isCached("111"))
    }

    @Test
    fun `a cached verdict is not looked up again on a later scan`() {
        val autoRip = coordinator(
            lookup = recordingLookup {
                BatchAvailabilityLookup(cached = mapOf("111" to availability("alac")))
            }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()
        val lookupsAfterFirstScan = synchronized(lookedUpBatches) { lookedUpBatches.size }

        autoRip.requestScan()
        settle()

        assertEquals(lookupsAfterFirstScan, synchronized(lookedUpBatches) { lookedUpBatches.size })
        assertTrue(ripped.isEmpty())
    }

    @Test
    fun `a cached verdict persists across a coordinator restart`() {
        val first = coordinator(
            lookup = recordingLookup {
                BatchAvailabilityLookup(cached = mapOf("111" to availability("alac")))
            }
        )
        first.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()
        first.stop()

        val second = coordinator(
            lookup = recordingLookup { BatchAvailabilityLookup.EMPTY }
        )
        second.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()

        assertTrue(ripped.isEmpty())
    }

    @Test
    fun `a failing lookup is retried once and never rips`() {
        var attempts = 0
        val autoRip = coordinator(
            lookup = recordingLookup { ids ->
                attempts++
                BatchAvailabilityLookup.unavailable(ids)
            }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()

        assertEquals(2, attempts)
        assertTrue(ripped.isEmpty())
    }

    @Test
    fun `a failing lookup that recovers on retry still rips`() {
        var attempts = 0
        val autoRip = coordinator(
            lookup = recordingLookup { ids ->
                attempts++
                if (attempts == 1) {
                    BatchAvailabilityLookup.unavailable(ids)
                } else {
                    BatchAvailabilityLookup.EMPTY
                }
            }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()

        assertEquals(2, attempts)
        assertEquals(listOf("111"), ripped)
    }

    @Test
    fun `tracks without a valid provider id are ignored entirely`() {
        val autoRip = coordinator(
            lookup = recordingLookup { BatchAvailabilityLookup.EMPTY }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("abc"), track(null)))
        settle()

        assertTrue(lookedUpBatches.isEmpty())
        assertTrue(ripped.isEmpty())
    }

    /**
     * Within one scan the same id observed on several sources must be looked up and ripped
     * exactly once. Each recorded lookup batch must therefore be duplicate-free; how many
     * scans run is a debounce concern covered by the suppression test.
     */
    @Test
    fun `the same track seen on several sources is only requested once per scan`() {
        val autoRip = coordinator(
            lookup = recordingLookup { BatchAvailabilityLookup.EMPTY }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        autoRip.observe(AutoRipSource.SEARCH, listOf(track("111")))
        autoRip.observe(AutoRipSource.PLAYBACK_QUEUE, listOf(track("111")))
        autoRip.observe(AutoRipSource.ALBUM_DETAILS, listOf(track("111")))
        settle()

        val batches = synchronized(lookedUpBatches) { lookedUpBatches.toList() }
        assertTrue(batches.isNotEmpty())
        batches.forEach { batch ->
            assertEquals("duplicate ids in one lookup batch: $batch", batch.distinct(), batch)
            assertEquals(listOf("111"), batch)
        }
        assertTrue(ripped.isNotEmpty())
        assertEquals(List(ripped.size) { "111" }, ripped)
    }

    @Test
    fun `a later rip request is suppressed to avoid re-rip storms`() {
        val autoRip = coordinator(
            lookup = recordingLookup { BatchAvailabilityLookup.EMPTY },
            ripSuppressionMs = Long.MAX_VALUE,
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()
        autoRip.requestScan()
        settle()

        assertEquals(listOf("111"), ripped)
    }

    /**
     * End-to-end shape of the reported bug: search returns many tracks the UI marks
     * `isCached = false` (so they all show the cloud_download icon), the server omits every
     * one of them from the lookup response, and the user expects rips to be created without
     * tapping download. A successful-but-empty answer must rip them all.
     */
    @Test
    fun `tracks omitted from a successful response are auto ripped without user action`() {
        val autoRip = coordinator(lookup = recordingLookup { BatchAvailabilityLookup.EMPTY })

        autoRip.observe(
            AutoRipSource.SEARCH,
            listOf(track("111"), track("222"), track("333")),
        )
        settle()

        assertEquals(listOf("111", "222", "333"), ripped)
    }

    /** A partial server answer still rips the missing ones and keeps the cached one. */
    @Test
    fun `a partial response rips only the omitted ids`() {
        val autoRip = coordinator(
            lookup = recordingLookup {
                BatchAvailabilityLookup(cached = mapOf("222" to availability("alac")))
            }
        )

        autoRip.observe(
            AutoRipSource.SEARCH,
            listOf(track("111"), track("222"), track("333")),
        )
        settle()

        assertEquals(listOf("111", "333"), ripped)
        assertTrue(cache.isCached("222"))
    }

    /** The socket not being up must not permanently suppress a track. */
    @Test
    fun `a declined startRip stays retryable on the next scan`() {
        var acceptRip = false
        val autoRip = AutoRipCoordinator(
            sessionStore = sessionStore(),
            availabilityLookup = recordingLookup { BatchAvailabilityLookup.EMPTY },
            cache = cache,
            startRip = { track ->
                acceptRip && track.providerTrackId?.let(ripped::add) != null
            },
            scope = scope,
            debounceMs = 1L,
            ripSuppressionMs = Long.MAX_VALUE,
            rescanIntervalMs = Long.MAX_VALUE,
        ).also { it.start() }

        autoRip.observe(AutoRipSource.SEARCH, listOf(track("111")))
        settle()
        assertTrue(ripped.isEmpty())

        acceptRip = true
        autoRip.requestScan()
        settle()

        assertEquals(listOf("111"), ripped)
    }

    @Test
    fun `refreshAvailabilityNow records the positive verdict without a scan`() = runBlocking {
        val autoRip = coordinator(
            lookup = recordingLookup { ids ->
                BatchAvailabilityLookup(cached = ids.associateWith { availability("alac") })
            }
        )

        val lookup = autoRip.refreshAvailabilityNow("111")

        assertEquals(setOf("111"), lookup.cached.keys)
        assertTrue(cache.isCached("111"))
        assertTrue(ripped.isEmpty())
    }

    @Test
    fun `refreshAvailabilityNow ignores ids that are still not cached`() = runBlocking {
        val autoRip = coordinator(lookup = recordingLookup { BatchAvailabilityLookup.EMPTY })

        val lookup = autoRip.refreshAvailabilityNow("111")

        assertTrue(lookup.cached.isEmpty())
        assertFalse(cache.isCached("111"))
        assertTrue(ripped.isEmpty())
    }

    @Test
    fun `refreshAvailabilityNow rejects an unusable provider id`() = runBlocking {
        val autoRip = coordinator(lookup = recordingLookup { BatchAvailabilityLookup.EMPTY })

        assertTrue(autoRip.refreshAvailabilityNow("not-an-id").cached.isEmpty())
        assertTrue(synchronized(lookedUpBatches) { lookedUpBatches.isEmpty() })
    }

    @Test
    fun `a rip completion invalidates the stale verdict and relooks the id up`() {
        var lookupCalls = 0
        var cachedOnServer = false
        val autoRip = coordinator(
            lookup = recordingLookup { ids ->
                lookupCalls++
                BatchAvailabilityLookup(
                    cached = if (cachedOnServer) {
                        ids.associateWith { availability("alac") }
                    } else {
                        emptyMap()
                    },
                )
            },
            ripSuppressionMs = Long.MAX_VALUE,
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()
        assertEquals(listOf("111"), ripped)
        assertFalse(cache.isCached("111"))

        cachedOnServer = true
        autoRip.onRipCompleted("111")
        settle()

        assertEquals(2, lookupCalls)
        assertTrue(cache.isCached("111"))
    }

    @Test
    fun `invalidate forces a re-check on the next scan`() {
        val autoRip = coordinator(
            lookup = recordingLookup {
                BatchAvailabilityLookup(cached = mapOf("111" to availability("alac")))
            },
            ripSuppressionMs = Long.MAX_VALUE,
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()
        val lookupsBeforeInvalidate = synchronized(lookedUpBatches) { lookedUpBatches.size }
        assertTrue(ripped.isEmpty())

        autoRip.invalidate(listOf("111"))
        autoRip.requestScan()
        settle()

        assertTrue(synchronized(lookedUpBatches) { lookedUpBatches.size } > lookupsBeforeInvalidate)
        assertTrue(ripped.isEmpty())
        assertTrue(cache.isCached("111"))
    }

    @Test
    fun `a lookup that throws is treated as unknown rather than a rip`() {
        val autoRip = coordinator(
            lookup = TrackAvailabilityLookup { throw IllegalStateException("boom") }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        settle()

        assertTrue(ripped.isEmpty())
    }

    @Test
    fun `many candidates are looked up in batched chunks`() {
        val autoRip = coordinator(
            lookup = recordingLookup { BatchAvailabilityLookup.EMPTY }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, (1..120).map { track(it.toString()) })
        settle()

        assertEquals(
            listOf(50, 50, 20),
            synchronized(lookedUpBatches) { lookedUpBatches.map { it.size } },
        )
        assertEquals(120, ripped.size)
        assertEquals(120, ripped.distinct().size)
    }

    @Test
    fun `an emptied source no longer contributes candidates`() {
        val autoRip = coordinator(
            lookup = recordingLookup { BatchAvailabilityLookup.EMPTY }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        autoRip.observe(AutoRipSource.HOME_FEED, emptyList())
        settle()

        assertTrue(ripped.isEmpty())
    }

    @Test
    fun `stop clears observed candidates so later scans do nothing`() {
        val autoRip = coordinator(
            lookup = recordingLookup { BatchAvailabilityLookup.EMPTY }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("111")))
        autoRip.stop()
        autoRip.requestScan()
        settle()

        assertTrue(ripped.isEmpty())
    }

    @Test
    fun `candidates keep the order they were first observed in`() {
        val autoRip = coordinator(
            lookup = recordingLookup { BatchAvailabilityLookup.EMPTY }
        )

        autoRip.observe(AutoRipSource.HOME_FEED, listOf(track("2"), track("1")))
        settle()

        assertEquals(listOf("2", "1"), ripped)
        assertFalse(cache.isCached("2"))
    }

    private companion object {
        const val SCAN_SETTLE_MS = 300L
    }
}
