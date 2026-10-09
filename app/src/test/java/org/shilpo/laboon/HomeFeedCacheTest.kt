package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.home.HomeFeedCache
import org.shilpo.laboon.home.HomeFeedState
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.home.SectionLoadState
import org.shilpo.laboon.home.SectionState

class HomeFeedCacheTest {

    @Test
    fun testEmptyStoreReturnsNull() {
        val store = FakeKeyValueStore()
        val cache = HomeFeedCache(store)
        assertNull(cache.load())
    }

    @Test
    fun testSaveAndLoadPreservesAllSections() {
        val store = FakeKeyValueStore()
        val cache = HomeFeedCache(store)

        val track = HomeTrack(
            id = "t1",
            title = "Title 1",
            artist = "Artist 1",
            album = "Album 1",
            artworkUrl = "https://artwork/1.png",
            playCount = 10L,
            source = "Last.fm",
            streamUrl = "https://stream/1",
            backendTrackId = 42,
            isCached = true,
            codec = "alac",
            mbid = "mbid-1",
        )
        val artist = HomeArtist(
            id = "a1",
            name = "Artist Name",
            playCount = 50L,
            imageUrl = "https://artist/1.png",
        )
        val album = HomeAlbum(
            id = "al1",
            title = "Album Title",
            artist = "Artist Name",
            artworkUrl = "https://album/1.png",
            playCount = 25L,
        )

        val state = HomeFeedState(
            rotation = SectionState(SectionLoadState.LOADED, listOf(track)),
            recommended = SectionState(SectionLoadState.LOADED, listOf(track)),
            topArtists = SectionState(SectionLoadState.LOADED, listOf(artist)),
            topAlbums = SectionState(SectionLoadState.LOADED, listOf(album)),
            topTracks = SectionState(SectionLoadState.LOADED, listOf(track)),
            regionalTrending = SectionState(SectionLoadState.LOADED, listOf(track)),
            globalTrending = SectionState(SectionLoadState.LOADED, listOf(track)),
            weeklyPicks = SectionState(SectionLoadState.LOADED, listOf(track)),
            regionName = "Japan",
        )

        cache.save(state)
        val loaded = cache.load()
        assertNotNull(loaded)

        assertEquals(listOf(track), loaded?.rotation?.items)
        assertEquals(SectionLoadState.LOADED, loaded?.rotation?.status)
        assertEquals(listOf(track), loaded?.recommended?.items)
        assertEquals(listOf(artist), loaded?.topArtists?.items)
        assertEquals(listOf(album), loaded?.topAlbums?.items)
        assertEquals(listOf(track), loaded?.topTracks?.items)
        assertEquals(listOf(track), loaded?.regionalTrending?.items)
        assertEquals(listOf(track), loaded?.globalTrending?.items)
        assertEquals(listOf(track), loaded?.weeklyPicks?.items)
        assertEquals("Japan", loaded?.regionName)
    }

    @Test
    fun librarySongsCacheRoundTripsPlayableTrackMetadata() {
        val cache = HomeFeedCache(FakeKeyValueStore())
        val tracks = listOf(
            HomeTrack(
                id = "song-1",
                title = "Beautiful Mistakes",
                artist = "Maroon 5, Megan Thee Stallion",
                album = "Jordi",
                artworkUrl = "https://artwork/song-1.png",
                playCount = 12,
                source = "Last.fm",
                backendTrackId = 42,
                codec = "alac",
                mbid = "recording-mbid",
                artistMbid = "artist-mbid",
                isrc = "USUM72012345",
                providerTrackId = "apple-track-1",
                availableFormats = listOf("alac"),
                availableVariants = listOf(TrackFormatVariant("alac", 42, 8_192_000L)),
                durationMs = 220_000,
                contentRating = "explicit",
                listenedAtMs = 1_700_000_000_000L,
            ),
        )

        cache.saveLibrarySongs(tracks)

        assertEquals(tracks, cache.loadLibrarySongs())
    }

    @Test
    fun clearRemovesCachedLibrarySongs() {
        val cache = HomeFeedCache(FakeKeyValueStore())
        cache.saveLibrarySongs(listOf(HomeTrack(id = "song-1", title = "Song", artist = "Artist")))

        cache.clear()

        assertNull(cache.loadLibrarySongs())
    }

    @Test
    fun testClearRemovesData() {
        val store = FakeKeyValueStore()
        val cache = HomeFeedCache(store)
        val track = HomeTrack(id = "t1", title = "T", artist = "A")
        val state = HomeFeedState(rotation = SectionState(SectionLoadState.LOADED, listOf(track)))
        cache.save(state)
        assertNotNull(cache.load())

        cache.clear()
        assertNull(cache.load())
    }

    @Test
    fun testMalformedJsonReturnsNullSafely() {
        val store = FakeKeyValueStore()
        store.putString("home_feed_cache_v1", "{invalid json")
        val cache = HomeFeedCache(store)
        assertNull(cache.load())
    }
}
