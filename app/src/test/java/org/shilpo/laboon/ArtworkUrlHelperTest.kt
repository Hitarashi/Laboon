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
}
