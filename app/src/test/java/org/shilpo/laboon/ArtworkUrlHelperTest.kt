package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.playback.ArtworkUrlHelper

class ArtworkUrlHelperTest {

    @Test
    fun testNullAndBlank() {
        assertNull(ArtworkUrlHelper.toLowQuality(null))
        assertNull(ArtworkUrlHelper.toHighQuality(null))
        assertEquals("", ArtworkUrlHelper.toLowQuality(""))
        assertEquals("", ArtworkUrlHelper.toHighQuality(""))
    }

    @Test
    fun testAppleMusicStandardUrl() {
        val original =
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/99/9b/abc/xyz/100x100bb.jpg"
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/99/9b/abc/xyz/160x160bb.jpg",
            ArtworkUrlHelper.toLowQuality(original),
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/99/9b/abc/xyz/1200x1200bb.jpg",
            ArtworkUrlHelper.toHighQuality(original),
        )
    }

    @Test
    fun testAppleMusicTemplateUrl() {
        val original =
            "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/4a/5b/6c/mzi.y/{w}x{h}bb.{f}"
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/4a/5b/6c/mzi.y/160x160bb.jpg",
            ArtworkUrlHelper.toLowQuality(original),
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/4a/5b/6c/mzi.y/1200x1200bb.jpg",
            ArtworkUrlHelper.toHighQuality(original),
        )
    }

    @Test
    fun testCoverArtArchive() {
        val original = "https://coverartarchive.org/release/12345/front-500.jpg"
        assertEquals(
            "https://coverartarchive.org/release/12345/front-250.jpg",
            ArtworkUrlHelper.toLowQuality(original),
        )
        assertEquals(
            "https://coverartarchive.org/release/12345/front-1200.jpg",
            ArtworkUrlHelper.toHighQuality(original),
        )
    }

    @Test
    fun testDeezer() {
        val original = "https://e-cdns-images.dzcdn.net/images/cover/abc/250x250-000000-80-0-0.jpg"
        assertEquals(
            "https://e-cdns-images.dzcdn.net/images/cover/abc/250x250-000000-80-0-0.jpg",
            ArtworkUrlHelper.toLowQuality(original),
        )
        assertEquals(
            "https://e-cdns-images.dzcdn.net/images/cover/abc/1000x1000-000000-80-0-0.jpg",
            ArtworkUrlHelper.toHighQuality(original),
        )
    }

    @Test
    fun testQobuz() {
        val original = "https://static.qobuz.com/images/covers/123_600.jpg"
        assertEquals(
            "https://static.qobuz.com/images/covers/123_230.jpg",
            ArtworkUrlHelper.toLowQuality(original),
        )
        assertEquals(
            "https://static.qobuz.com/images/covers/123_org.jpg",
            ArtworkUrlHelper.toHighQuality(original),
        )
    }

    @Test
    fun testPeerlessServerAsset() {
        val original = "https://stream.server.org/api/v1/assets/tracks/42/artwork?size=600"
        assertEquals(
            "https://stream.server.org/api/v1/assets/tracks/42/artwork?size=160",
            ArtworkUrlHelper.toLowQuality(original),
        )
        assertEquals(
            "https://stream.server.org/api/v1/assets/tracks/42/artwork?size=1200",
            ArtworkUrlHelper.toHighQuality(original),
        )
    }
}
