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
import org.shilpo.laboon.home.SavedPlaylist
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
    fun libraryPageCachesRoundTripAllCategoryData() {
        val cache = HomeFeedCache(FakeKeyValueStore())
        val recentTrack = HomeTrack(
            id = "recent-1",
            title = "Recent",
            artist = "Artist",
            listenedAtMs = 1234L,
        )
        val lovedTrack = HomeTrack(id = "loved-1", title = "Loved", artist = "Artist")
        val album = HomeAlbum(id = "album-1", title = "Album", artist = "Artist")
        val artist = HomeArtist(id = "artist-1", name = "Artist", playCount = 9L)
        val playlist = SavedPlaylist(
            id = "playlist-1",
            name = "Playlist",
            isFavorites = false,
            sourceUrl = "https://music.youtube.com/playlist?list=public-id",
            trackCount = 3,
            previewTrackIds = listOf("1559885421", "1440643709"),
            previewArtworkByTrackId = mapOf(
                "1559885421" to "https://example.test/first.jpg",
                "1440643709" to "https://example.test/second.jpg",
            ),
        )
        val playlistTracks = listOf(
            HomeTrack(id = "playlist-song-1", title = "Playlist song", artist = "Artist"),
        )

        cache.saveLibraryRecentTracks(listOf(recentTrack))
        cache.saveLibraryLovedTracks(listOf(lovedTrack))
        cache.saveLibraryAlbums(listOf(album))
        cache.saveLibraryArtists(listOf(artist))
        cache.saveLibraryPlaylists(listOf(playlist))
        cache.saveLibraryPlaylistTracks(playlist.id, playlistTracks)

        assertEquals(listOf(recentTrack), cache.loadLibraryRecentTracks())
        assertEquals(listOf(lovedTrack), cache.loadLibraryLovedTracks())
        assertEquals(listOf(album), cache.loadLibraryAlbums())
        assertEquals(listOf(artist), cache.loadLibraryArtists())
        assertEquals(listOf(playlist), cache.loadLibraryPlaylists())
        assertEquals(playlistTracks, cache.loadLibraryPlaylistTracks(playlist.id))

        cache.removeLibraryPlaylistTracks(playlist.id)

        assertNull(cache.loadLibraryPlaylistTracks(playlist.id))
    }

    @Test
    fun clearRemovesAllLibraryPageCaches() {
        val cache = HomeFeedCache(FakeKeyValueStore())
        cache.saveLibraryRecentTracks(listOf(HomeTrack(id = "recent", title = "R", artist = "A")))
        cache.saveLibraryLovedTracks(listOf(HomeTrack(id = "loved", title = "L", artist = "A")))
        cache.saveLibraryAlbums(listOf(HomeAlbum(id = "album", title = "Album", artist = "A")))
        cache.saveLibraryArtists(listOf(HomeArtist(id = "artist", name = "A")))
        cache.saveLibraryPlaylists(listOf(SavedPlaylist(id = "playlist", name = "P", isFavorites = false)))
        cache.saveLibraryPlaylistTracks(
            "playlist",
            listOf(HomeTrack(id = "song", title = "Song", artist = "A")),
        )

        cache.clear()

        assertNull(cache.loadLibraryRecentTracks())
        assertNull(cache.loadLibraryLovedTracks())
        assertNull(cache.loadLibraryAlbums())
        assertNull(cache.loadLibraryArtists())
        assertNull(cache.loadLibraryPlaylists())
        assertNull(cache.loadLibraryPlaylistTracks("playlist"))
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
