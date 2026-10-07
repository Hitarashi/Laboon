package org.shilpo.laboon.search

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem
import org.shilpo.laboon.lyricsporn.LyricspornClient

class SearchArtistSupportTest {

    @Test
    fun `search with no session returns empty artists`() = runBlocking {
        val repository = SearchRepositoryImpl(SessionStore(FakeKeyValueStore()))

        val result = repository.search("Radiohead")
        assertEquals(emptyList<HomeArtist>(), result.artists)
    }

    @Test
    fun `searchSuggestions with no session returns empty artists`() = runBlocking {
        val repository = SearchRepositoryImpl(SessionStore(FakeKeyValueStore()))

        val result = repository.searchSuggestions("Radiohead")
        assertEquals(emptyList<HomeArtist>(), result.artists)
    }

    @Test
    fun `LyricspornClient searchCatalog with blank term returns empty artists`() = runBlocking {
        assertEquals(
            emptyList<LyricspornCatalogItem>(),
            LyricspornClient.searchCatalog(null, "term").artists
        )
        assertEquals(
            emptyList<LyricspornCatalogItem>(),
            LyricspornClient.searchCatalog("https://api.example.com", " ").artists
        )
    }

    @Test
    fun `LyricspornClient searchTopSuggestions with blank term returns empty artists`() =
        runBlocking {
            assertEquals(
                emptyList<LyricspornCatalogItem>(),
                LyricspornClient.searchTopSuggestions(null, "term").artists
            )
            assertEquals(
                emptyList<LyricspornCatalogItem>(),
                LyricspornClient.searchTopSuggestions("https://api.example.com", "").artists
            )
        }
}
