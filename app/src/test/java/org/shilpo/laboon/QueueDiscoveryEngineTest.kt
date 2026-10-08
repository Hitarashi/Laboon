package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.home.UserTasteProfile
import org.shilpo.laboon.playback.DiscoveryCandidate
import org.shilpo.laboon.playback.DiscoveryTier
import org.shilpo.laboon.playback.QueueDiscoveryEngine
import org.shilpo.laboon.playback.QueueDiscoveryResult
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

    @Test
    fun rankingCombinesProvidersBoostsLovedTracksAndExcludesDislikes() {
        val engine = QueueDiscoveryEngine(SessionStore(FakeKeyValueStore()))
        val sharedTrack = track("shared", "Shared pick")
        val lovedTrack = track("loved", "Loved pick")
        val dislikedTrack = track("hate", "Disliked pick", "Hater")
            .copy(mbid = "disliked-mbid")

        fun candidate(
            track: HomeTrack,
            source: String,
            provider: String,
            rank: Int,
        ) = DiscoveryCandidate(
            track = track,
            source = source,
            tier = DiscoveryTier.RELATED_RECORDING,
            rank = rank,
            reason = "related",
            supportingSources = setOf(provider),
        )

        val ranked = engine.rankCandidates(
            candidates = listOf(
                candidate(sharedTrack, "Last.fm similar", "Last.fm", 6),
                candidate(
                    sharedTrack.copy(id = "shared-lb"),
                    "ListenBrainz similar",
                    "ListenBrainz",
                    5
                ),
                candidate(lovedTrack, "Last.fm similar", "Last.fm", 12),
                candidate(dislikedTrack, "ListenBrainz similar", "ListenBrainz", 0),
            ),
            taste = UserTasteProfile(lovedTracks = listOf(lovedTrack)),
            excludedKeys = emptySet(),
            dislikedMbids = setOf("DISLIKED-MBID"),
            limit = 6,
        )

        assertEquals("Loved pick", ranked.first().track.title)
        assertEquals(1, ranked.count { it.track.title == "Shared pick" })
        assertEquals(
            setOf("Last.fm", "ListenBrainz"),
            ranked.single { it.track.title == "Shared pick" }.supportingSources
        )
        assertFalse(ranked.any { it.recordingMbid == "disliked-mbid" })
    }

    @Test
    fun partialProviderFailureKeepsPlayableResultsAndOffersRetryState() {
        val partialTrack = track("partial", "Playable partial result")
        val result = QueueDiscoveryResult(
            tracks = listOf(partialTrack),
            successfulResponses = 1,
            failedResponses = 1,
        )

        assertTrue(result.failed)
        assertEquals(listOf(partialTrack), result.tracks)
        assertFalse(result.exhausted)
    }

    @Test
    fun emptySuccessfulDiscoveryIsExhaustedRatherThanFailed() {
        val result = QueueDiscoveryResult(
            tracks = emptyList(),
            successfulResponses = 2,
            failedResponses = 0,
        )

        assertFalse(result.failed)
        assertTrue(result.exhausted)
    }

    private fun track(id: String, title: String, artist: String = "Artist") =
        HomeTrack(id = id, title = title, artist = artist)
}
