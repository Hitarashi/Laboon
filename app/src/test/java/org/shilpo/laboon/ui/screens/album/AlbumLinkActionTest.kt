package org.shilpo.laboon.ui.screens.album

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumLinkActionTest {

    @Test
    fun canonicalAppleMusicAlbumUrl_keepsTheRealTrimmedAppleLink() {
        val url = "  https://music.apple.com/us/album/example/123  "

        assertEquals(
            "https://music.apple.com/us/album/example/123",
            canonicalAppleMusicAlbumUrl(url),
        )
    }

    @Test
    fun canonicalAppleMusicAlbumUrl_omitsMissingOrNonAppleLinks() {
        assertNull(canonicalAppleMusicAlbumUrl(null))
        assertNull(canonicalAppleMusicAlbumUrl("  "))
        assertNull(canonicalAppleMusicAlbumUrl("https://example.com/album/123"))
        assertNull(canonicalAppleMusicAlbumUrl("http://music.apple.com/us/album/123"))
        assertNull(canonicalAppleMusicAlbumUrl("https://music.apple.com.example.com/album/123"))
    }

    @Test
    fun appleMusicTrackShareUrl_prefersCanonicalTrackUrlAndNormalizesTrackId() {
        assertEquals(
            "https://music.apple.com/in/album/example/100?ls=1&i=200",
            appleMusicTrackShareUrl(
                trackUrl = "https://music.apple.com/in/album/example/100?i=200&ls=1&i=200",
                albumUrl = "https://music.apple.com/in/album/example/100",
                trackCatalogId = "200",
            ),
        )
    }

    @Test
    fun appleMusicTrackShareUrl_acceptsCanonicalSongPathWithMatchingCatalogId() {
        assertEquals(
            "https://music.apple.com/in/song/example/200",
            appleMusicTrackShareUrl(
                trackUrl = "https://music.apple.com/in/song/example/200",
                albumUrl = null,
                trackCatalogId = "200",
            ),
        )
    }

    @Test
    fun appleMusicTrackShareUrl_derivesLinkAndReplacesExistingTrackIds() {
        assertEquals(
            "https://music.apple.com/in/album/example/100?ls=1&i=200",
            appleMusicTrackShareUrl(
                trackUrl = null,
                albumUrl = "https://music.apple.com/in/album/example/100?i=old&i=duplicate&ls=1",
                trackCatalogId = "200",
            ),
        )
    }

    @Test
    fun appleMusicTrackShareUrl_fallsBackFromMismatchedTrackUrlToAlbumUrl() {
        assertEquals(
            "https://music.apple.com/in/album/example/100?i=200",
            appleMusicTrackShareUrl(
                trackUrl = "https://music.apple.com/in/album/example/100?i=old",
                albumUrl = "https://music.apple.com/in/album/example/100",
                trackCatalogId = "200",
            ),
        )
    }

    @Test
    fun appleMusicTrackShareUrl_rejectsMissingOrInvalidTrackIdentityAndUrl() {
        assertNull(appleMusicTrackShareUrl(null, null, null))
        assertNull(
            appleMusicTrackShareUrl(
                null,
                "https://music.apple.com/in/album/example/100",
                ""
            )
        )
        assertNull(
            appleMusicTrackShareUrl(
                null,
                "https://music.apple.com/in/album/example/100",
                "abc"
            )
        )
        assertNull(appleMusicTrackShareUrl(null, null, "200"))
        assertNull(appleMusicTrackShareUrl(null, "https://example.com/album/100", "200"))
        assertNull(
            appleMusicTrackShareUrl(
                null,
                "https://music.apple.com/in/artist/example/100",
                "200"
            )
        )
        assertNull(
            appleMusicTrackShareUrl(
                "https://music.apple.com/in/artist/example/100?i=200",
                null,
                "200"
            )
        )
    }
}
