package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackIdentity

class TrackIdentityTest {

    private val titleSamples = listOf(
        "Song",
        "Song  Title",
        "song title",
        "Song (Remastered 2011)",
        "Song - Remastered 2011",
        "Song (feat. Other)",
        "Song feat. Other",
        "1989",
        "24K Magic",
        "Tyler, The Creator",
        "Earth, Wind & Fire",
        "Song One",
        "Song Two",
    )

    private val artistSamples = listOf(
        "Artist",
        "A",
        "A feat. B",
        "A & B",
        "Tyler, The Creator",
        "Earth, Wind & Fire",
    )

    private fun track(title: String, artist: String = "Artist", mbid: String? = null) =
        HomeTrack(id = "id-$title", title = title, artist = artist, mbid = mbid)

    @Test
    fun testIdenticalTitleAndArtistShareKey() {
        assertEquals(
            TrackIdentity.keyOf(track("Song", "Artist")),
            TrackIdentity.keyOf(track("Song", "Artist")),
        )
    }

    @Test
    fun testKeyIgnoresCaseAndWhitespace() {
        assertEquals("song title", TrackIdentity.normalizedTitle("Song  Title"))
        assertEquals(
            TrackIdentity.keyOf(track("Song", "Artist")),
            TrackIdentity.keyOf(track("song", "artist")),
        )
    }

    @Test
    fun testEnclosedRemasterQualifierIsStripped() {
        assertEquals("song", TrackIdentity.normalizedTitle("Song (Remastered 2011)"))
    }

    @Test
    fun testDashedRemasterQualifierIsStripped() {
        assertEquals("song", TrackIdentity.normalizedTitle("Song - Remastered 2011"))
    }

    @Test
    fun testEnclosedFeaturedClauseIsStripped() {
        assertEquals("song", TrackIdentity.normalizedTitle("Song (feat. Other)"))
    }

    @Test
    fun testTrailingFeaturedClauseIsStripped() {
        assertEquals("song", TrackIdentity.normalizedTitle("Song feat. Other"))
    }

    @Test
    fun testDifferentTitlesStayDifferent() {
        assertNotEquals(
            TrackIdentity.keyOf(track("Song One")),
            TrackIdentity.keyOf(track("Song Two")),
        )
    }

    @Test
    fun liveRemixAndVersionTitlesRemainSeparateRecordings() {
        val studio = track("Song")
        val live = track("Song (Live)")
        val remix = track("Song (Remix)")
        val version = track("Song (Version)")

        assertNotEquals(TrackIdentity.keyOf(studio), TrackIdentity.keyOf(live))
        assertNotEquals(TrackIdentity.keyOf(studio), TrackIdentity.keyOf(remix))
        assertNotEquals(TrackIdentity.keyOf(studio), TrackIdentity.keyOf(version))
    }

    @Test
    fun testArtistFeaturedClauseCollapses() {
        assertEquals(
            TrackIdentity.keyOf(track("Song", "A")),
            TrackIdentity.keyOf(track("Song", "A feat. B")),
        )
    }

    @Test
    fun testArtistJoinsAreNotStripped() {
        assertEquals("a & b", TrackIdentity.normalizedArtist("A & B"))
        assertNotEquals(
            TrackIdentity.keyOf(track("Song", "A")),
            TrackIdentity.keyOf(track("Song", "A & B")),
        )
        assertEquals("tyler, the creator", TrackIdentity.normalizedArtist("Tyler, The Creator"))
        assertEquals("earth, wind & fire", TrackIdentity.normalizedArtist("Earth, Wind & Fire"))
    }

    @Test
    fun testSameMbidYieldsSameKeyAcrossSpellings() {
        assertEquals(
            TrackIdentity.keyOf(track("Song (Remastered 2011)", mbid = "abc-123")),
            TrackIdentity.keyOf(track("Song", mbid = "ABC-123")),
        )
    }

    @Test
    fun testDifferentMbidsYieldDifferentKeys() {
        assertNotEquals(
            TrackIdentity.keyOf(track("Song", mbid = "abc-123")),
            TrackIdentity.keyOf(track("Song", mbid = "def-456")),
        )
    }

    @Test
    fun testBlankMbidFallsThroughToMetadataKey() {
        val metadataKey = TrackIdentity.keyOf(track("Song", "Artist"))
        assertEquals(metadataKey, TrackIdentity.keyOf(track("Song", "Artist", mbid = "   ")))
        assertEquals(metadataKey, TrackIdentity.keyOf(track("Song", "Artist", mbid = "")))
    }

    @Test
    fun testYearLikeTitleIsPreserved() {
        assertEquals("1989", TrackIdentity.normalizedTitle("1989"))
    }

    @Test
    fun testNumberInsideTitleIsPreserved() {
        assertEquals("24k magic", TrackIdentity.normalizedTitle("24K Magic"))
    }

    @Test
    fun testNormalizationIsIdempotent() {
        for (sample in titleSamples) {
            val once = TrackIdentity.normalizedTitle(sample)
            assertEquals("title: $sample", once, TrackIdentity.normalizedTitle(once))
        }
        for (sample in artistSamples) {
            val once = TrackIdentity.normalizedArtist(sample)
            assertEquals("artist: $sample", once, TrackIdentity.normalizedArtist(once))
        }
    }

    @Test
    fun testIsSameTrackMatchesKeyEquality() {
        val a = track("Song (feat. Other)", "A feat. B")
        val b = track("Song", "A")
        assertTrue(TrackIdentity.isSameTrack(a, b))
        assertTrue(TrackIdentity.isSameTrack(b, b))
        assertTrue(!TrackIdentity.isSameTrack(b, track("Song", "B")))
    }
}
