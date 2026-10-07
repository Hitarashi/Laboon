package org.shilpo.laboon.ui.screens.home

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeFeedState
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.SectionLoadState
import org.shilpo.laboon.home.SectionState
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.search.PlaybackResolution
import org.shilpo.laboon.search.SearchFilter
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchResults

class FeedAvailabilityRefreshTest {

    private fun track(id: String, isCached: Boolean = false) = HomeTrack(
        id = "apple_$id",
        title = "Song $id",
        artist = "Artist",
        providerTrackId = id,
        isCached = isCached,
    )

    private fun enriched(track: HomeTrack, cached: Boolean) = track.copy(
        isCached = cached,
        codec = if (cached) "alac" else null,
        backendTrackId = if (cached) 42 else null,
        availableFormats = if (cached) listOf("alac") else emptyList(),
        availableVariants = if (cached) {
            listOf(TrackFormatVariant(format = "alac", backendTrackId = 42))
        } else {
            emptyList()
        },
    )

    private fun repository(cachedIds: Set<String>) = object : SearchRepository {
        override suspend fun search(query: String, filter: SearchFilter): SearchResults =
            SearchResults()

        override suspend fun enrichAvailability(tracks: List<HomeTrack>): List<HomeTrack> =
            tracks.map { enriched(it, it.providerTrackId in cachedIds) }

        override suspend fun resolvePlaybackUrl(track: HomeTrack): String? = null
        override suspend fun resolvePlayback(track: HomeTrack): PlaybackResolution? = null
        override suspend fun resolvePlaybackBatch(tracks: List<HomeTrack>): List<HomeTrack> =
            emptyList()
    }

    private fun state(vararg topTracks: HomeTrack) = HomeFeedState(
        topTracks = SectionState(SectionLoadState.LOADED, topTracks.toList()),
    )

    @Test
    fun `a stale cached track is resolved as playable on load`() = runBlocking {
        val availability = state(track("111", isCached = false))
            .resolveAvailability(repository(setOf("111")))

        val updated = state(track("111", isCached = false)).withAvailability(availability)

        assertTrue(updated.topTracks.items.single().isCached)
        assertTrue(updated.topTracks.items.single().isPlayable)
    }

    @Test
    fun `a track that is genuinely not cached produces no verdict`() = runBlocking {
        val availability = state(track("111"))
            .resolveAvailability(repository(emptySet()))

        assertTrue(availability.isEmpty())
    }

    @Test
    fun `only cached tracks from a mixed section are reported`() = runBlocking {
        val availability = state(track("111"), track("222"), track("333"))
            .resolveAvailability(repository(setOf("111", "333")))

        assertEquals(setOf("111", "333"), availability.keys)
    }

    @Test
    fun `an empty feed resolves to no verdicts`() = runBlocking {
        val availability = HomeFeedState().resolveAvailability(repository(setOf("111")))

        assertTrue(availability.isEmpty())
    }

    @Test
    fun `sections that are not loaded are left untouched`() = runBlocking {
        val availability = state(track("111"))
            .copy(topTracks = SectionState(SectionLoadState.IDLE, listOf(track("222"))))
            .resolveAvailability(repository(setOf("111", "222")))

        val updated = state(track("111"))
            .copy(topTracks = SectionState(SectionLoadState.IDLE, listOf(track("222"))))
            .withAvailability(availability)

        assertEquals(SectionLoadState.IDLE, updated.topTracks.status)
        assertEquals(false, updated.topTracks.items.single().isCached)
    }

    @Test
    fun `applying verdicts updates every loaded track section`() = runBlocking {
        val availability = HomeFeedState(
            topTracks = SectionState(SectionLoadState.LOADED, listOf(track("111"))),
            weeklyPicks = SectionState(SectionLoadState.LOADED, listOf(track("222"))),
        ).resolveAvailability(repository(setOf("111", "222")))

        val updated = HomeFeedState(
            topTracks = SectionState(SectionLoadState.LOADED, listOf(track("111"))),
            weeklyPicks = SectionState(SectionLoadState.LOADED, listOf(track("222"))),
        ).withAvailability(availability)

        assertTrue(updated.topTracks.items.single().isCached)
        assertTrue(updated.weeklyPicks.items.single().isCached)
    }

    @Test
    fun `applying empty verdicts is a no-op`() {
        val original = state(track("111", isCached = true))

        assertEquals(original, original.withAvailability(emptyMap()))
    }
}
