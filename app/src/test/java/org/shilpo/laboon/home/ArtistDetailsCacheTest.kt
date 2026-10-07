package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore
import org.shilpo.laboon.lyricsporn.LyricspornArtist
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem

class ArtistDetailsCacheTest {

    @Test
    fun load_ignoresEntriesFromAnOlderVersion() {
        val store = FakeKeyValueStore().apply {
            putString(
                "artist_details_cache_v1",
                JSONObject()
                    .put("version", 999)
                    .put(
                        "entries",
                        JSONArray().put(
                            JSONObject().put("key", "artist-1"),
                        ),
                    )
                    .toString(),
            )
        }

        assertNull(ArtistDetailsCache(store).load("artist-1"))
    }

    @Test
    fun saveAndLoad_persistsAllArtistDetails() {
        val store = FakeKeyValueStore()
        val cache = ArtistDetailsCache(store)

        val success = ArtistDetailsResult.Success(
            artist = LyricspornArtist(
                id = "123",
                name = "Arijit Singh",
                url = "https://music.apple.com/artist/123",
                artworkUrl = "https://artwork.test/123.jpg",
                genres = listOf("Bollywood", "Pop"),
                editorialNotes = "Prolific playback singer.",
                topSongs = listOf(
                    LyricspornCatalogItem(
                        id = "song-1",
                        type = "song",
                        name = "Tum Hi Ho",
                        artistName = "Arijit Singh",
                        albumName = "Aashiqui 2",
                        artworkUrl = "https://artwork.test/song1.jpg",
                        durationMs = 262000L,
                        isrc = "INUM71300001",
                    ),
                ),
                latestRelease = LyricspornCatalogItem(
                    id = "alb-latest",
                    type = "album",
                    name = "Latest Single",
                    artistName = "Arijit Singh",
                ),
                fullAlbums = listOf(
                    LyricspornCatalogItem(
                        id = "alb-1",
                        type = "album",
                        name = "Aashiqui 2",
                        artistName = "Arijit Singh",
                    ),
                ),
                singles = emptyList(),
                similarArtists = listOf(
                    LyricspornCatalogItem(
                        id = "art-2",
                        type = "artist",
                        name = "Atif Aslam",
                        artworkUrl = "https://artwork.test/atif.jpg",
                    ),
                ),
            ),
            topSongs = listOf(
                HomeTrack(
                    id = "apple_song-1",
                    title = "Tum Hi Ho",
                    artist = "Arijit Singh",
                    album = "Aashiqui 2",
                    durationMs = 262000L,
                ),
            ),
            latestRelease = HomeAlbum(
                id = "apple_alb-latest",
                title = "Latest Single",
                artist = "Arijit Singh",
            ),
            albums = listOf(
                HomeAlbum(
                    id = "apple_alb-1",
                    title = "Aashiqui 2",
                    artist = "Arijit Singh",
                ),
            ),
            singles = emptyList(),
            similarArtists = listOf(
                HomeArtist(
                    id = "apple_art-2",
                    name = "Atif Aslam",
                    imageUrl = "https://artwork.test/atif.jpg",
                ),
            ),
        )

        val cacheKey = "test_cache_key_arijit"
        cache.save(cacheKey, success)

        val loaded = cache.load(cacheKey)
        assertNotNull(loaded)
        assertEquals("Arijit Singh", loaded?.artist?.name)
        assertEquals("Tum Hi Ho", loaded?.topSongs?.firstOrNull()?.title)
        assertEquals("Aashiqui 2", loaded?.albums?.firstOrNull()?.title)
        assertEquals("Atif Aslam", loaded?.similarArtists?.firstOrNull()?.name)
    }

    @Test
    fun saveAndLoadResolvedId_persistsNormalizedNameMapping() {
        val store = FakeKeyValueStore()
        val cache = ArtistDetailsCache(store)

        cache.saveResolvedId("arijit singh", "123456")
        assertEquals("123456", cache.loadResolvedId("arijit singh"))
        assertNull(cache.loadResolvedId("unknown artist"))
    }
}
