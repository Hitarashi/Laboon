package org.shilpo.laboon.home

import org.junit.Assert.assertEquals
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore
import org.shilpo.laboon.lyricsporn.LyricspornAlbum
import org.shilpo.laboon.lyricsporn.LyricspornAlbumTrack

class AlbumTrackUrlCacheTest {

    @Test
    fun saveAndLoad_preservesCanonicalAlbumTrackUrl() {
        val track = LyricspornAlbumTrack(
            id = "200",
            name = "Song",
            url = "https://music.apple.com/in/album/example/100?i=200",
        )
        val cache = AlbumDetailsCache(FakeKeyValueStore())

        cache.save(
            cacheKey = "album-100",
            result = AlbumDetailsResult.Success(
                album = LyricspornAlbum(id = "100", name = "Album", tracks = listOf(track)),
                tracks = emptyList(),
            ),
        )

        assertEquals(track.url, cache.load("album-100")?.album?.tracks?.single()?.url)
    }
}
