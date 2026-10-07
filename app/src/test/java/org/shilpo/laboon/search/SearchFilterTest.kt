package org.shilpo.laboon.search

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore
import org.shilpo.laboon.auth.SessionStore

class SearchFilterTest {

    @Test
    fun `search filter enum contains all 7 categories with correct labels and apiTypes`() {
        assertEquals(7, SearchFilter.ALL.size)
        assertEquals(SearchFilter.TOP_RESULTS, SearchFilter.ALL[0])
        assertEquals("Top Results", SearchFilter.TOP_RESULTS.label)
        assertEquals("top-results", SearchFilter.TOP_RESULTS.apiType)

        assertEquals(SearchFilter.ARTISTS, SearchFilter.ALL[1])
        assertEquals("Artist", SearchFilter.ARTISTS.label)
        assertEquals("artists", SearchFilter.ARTISTS.apiType)

        assertEquals(SearchFilter.ALBUMS, SearchFilter.ALL[2])
        assertEquals("Albums", SearchFilter.ALBUMS.label)
        assertEquals("albums", SearchFilter.ALBUMS.apiType)

        assertEquals(SearchFilter.SONGS, SearchFilter.ALL[3])
        assertEquals("Songs", SearchFilter.SONGS.label)
        assertEquals("songs", SearchFilter.SONGS.apiType)

        assertEquals(SearchFilter.PLAYLISTS, SearchFilter.ALL[4])
        assertEquals("Playlists", SearchFilter.PLAYLISTS.label)
        assertEquals("playlists", SearchFilter.PLAYLISTS.apiType)

        assertEquals(SearchFilter.STATIONS, SearchFilter.ALL[5])
        assertEquals("Stations", SearchFilter.STATIONS.label)
        assertEquals("stations", SearchFilter.STATIONS.apiType)

        assertEquals(SearchFilter.MUSIC_VIDEOS, SearchFilter.ALL[6])
        assertEquals("Music Videos", SearchFilter.MUSIC_VIDEOS.label)
        assertEquals("music-videos", SearchFilter.MUSIC_VIDEOS.apiType)
    }

    @Test
    fun `search with filter returns empty results when no session exists`() = runBlocking {
        val repository = SearchRepositoryImpl(SessionStore(FakeKeyValueStore()))

        for (filter in SearchFilter.ALL) {
            val results = repository.search("Radiohead", filter)
            assertTrue(results.tracks.isEmpty())
            assertTrue(results.albums.isEmpty())
            assertTrue(results.artists.isEmpty())
            assertTrue(results.playlists.isEmpty())
            assertTrue(results.stations.isEmpty())
            assertTrue(results.musicVideos.isEmpty())
            assertTrue(results.topResults.isEmpty())
        }
    }
}
