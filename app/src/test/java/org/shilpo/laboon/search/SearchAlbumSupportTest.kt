package org.shilpo.laboon.search

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem
import org.shilpo.laboon.lyricsporn.LyricspornClient

class SearchAlbumSupportTest {

    @Test
    fun `default SearchRepository searchSuggestions returns empty SearchSuggestions`() =
        runBlocking {
            val stub = object : SearchRepository {
                override suspend fun search(query: String, filter: SearchFilter) = SearchResults()
                override suspend fun enrichAvailability(tracks: List<HomeTrack>) = tracks
                override suspend fun resolvePlaybackUrl(track: HomeTrack) = null
                override suspend fun resolvePlayback(track: HomeTrack) = null
                override suspend fun resolvePlaybackBatch(tracks: List<HomeTrack>) = tracks
            }

            val result = stub.searchSuggestions("query")
            assertEquals(emptyList<HomeTrack>(), result.tracks)
            assertEquals(emptyList<HomeAlbum>(), result.albums)
        }

    @Test
    fun `search with no session returns empty results`() = runBlocking {
        val repository = SearchRepositoryImpl(SessionStore(FakeKeyValueStore()))

        val result = repository.search("OK Computer")
        assertEquals(emptyList<HomeTrack>(), result.tracks)
        assertEquals(emptyList<HomeAlbum>(), result.albums)
    }

    @Test
    fun `searchSuggestions with no session returns empty suggestions`() = runBlocking {
        val repository = SearchRepositoryImpl(SessionStore(FakeKeyValueStore()))

        val result = repository.searchSuggestions("Radiohead")
        assertEquals(emptyList<HomeTrack>(), result.tracks)
        assertEquals(emptyList<HomeAlbum>(), result.albums)
    }

    @Test
    fun `LyricspornClient searchCatalog with blank term or null url returns empty`() = runBlocking {
        assertEquals(
            emptyList<LyricspornCatalogItem>(),
            LyricspornClient.searchCatalog(null, "term").songs
        )
        assertEquals(
            emptyList<LyricspornCatalogItem>(),
            LyricspornClient.searchCatalog(null, "term").albums
        )
        assertEquals(
            emptyList<LyricspornCatalogItem>(),
            LyricspornClient.searchCatalog("https://api.example.com", " ").songs
        )
        assertEquals(
            emptyList<LyricspornCatalogItem>(),
            LyricspornClient.searchCatalog("https://api.example.com", " ").albums
        )
    }

    @Test
    fun `LyricspornClient searchTopSuggestions with blank term or null url returns empty`() =
        runBlocking {
            assertEquals(
                emptyList<LyricspornCatalogItem>(),
                LyricspornClient.searchTopSuggestions(null, "term").songs
            )
            assertEquals(
                emptyList<LyricspornCatalogItem>(),
                LyricspornClient.searchTopSuggestions(null, "term").albums
            )
            assertEquals(
                emptyList<LyricspornCatalogItem>(),
                LyricspornClient.searchTopSuggestions("https://api.example.com", "").songs
            )
            assertEquals(
                emptyList<LyricspornCatalogItem>(),
                LyricspornClient.searchTopSuggestions("https://api.example.com", "").albums
            )
        }
}
