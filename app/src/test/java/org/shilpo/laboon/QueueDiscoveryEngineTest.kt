package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.playback.prioritizeDiscoveryTracks

class QueueDiscoveryEngineTest {

    @Test
    fun prioritizesTrackMatchesBeforePersonalFallback() {
        val direct = listOf(
            track("direct-1", "Seed match one"),
            track("direct-2", "Seed match two"),
        )
        val personal = listOf(
            track("personal-1", "Personal one"),
            track("personal-2", "Personal two"),
            track("personal-3", "Personal three"),
        )

        val result = prioritizeDiscoveryTracks(
            trackSpecific = direct,
            artistSpecific = emptyList(),
            personalized = personal,
            limit = 4,
        )

        assertEquals(direct + personal.take(2), result)
    }

    @Test
    fun doesNotUsePersonalFallbackWhenTrackMatchesFillQueue() {
        val direct = (1..6).map { track("direct-$it", "Seed match $it") }
        val personal = listOf(track("personal-1", "Personal one"))

        val result = prioritizeDiscoveryTracks(
            trackSpecific = direct,
            artistSpecific = emptyList(),
            personalized = personal,
            limit = 6,
        )

        assertEquals(direct, result)
    }

    @Test
    fun excludesHistoryAndDeduplicatesAcrossRecommendationSources() {
        val direct = listOf(track("direct-1", "Direct"))
        val artistSpecific = listOf(track("artist-1", "Artist pick"))
        val duplicate = track("duplicate", "Same song", "Same artist")
        val personalized = listOf(
            duplicate,
            duplicate.copy(id = "duplicate-copy"),
            track("history-copy", "Already heard", "History Artist"),
        )
        val historyKey = TrackIdentity.keyOf(track("history", "Already heard", "History Artist"))

        val result = prioritizeDiscoveryTracks(
            trackSpecific = direct + duplicate,
            artistSpecific = artistSpecific,
            personalized = personalized,
            excludedKeys = setOf(historyKey),
            limit = 6,
        )

        assertEquals(listOf(direct.single(), duplicate, artistSpecific.single()), result)
    }

    private fun track(id: String, title: String, artist: String = "Artist") =
        HomeTrack(id = id, title = title, artist = artist)
}
