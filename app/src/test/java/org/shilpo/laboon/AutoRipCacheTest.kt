package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.rip.AutoRipCache

class AutoRipCacheTest {

    private val store = FakeKeyValueStore()

    @Test
    fun `an unseen id is not cached`() {
        assertFalse(AutoRipCache(store).isCached("111"))
    }

    @Test
    fun `a positive verdict survives a new cache instance`() {
        AutoRipCache(store).rememberCached("111", listOf("alac", "aac"))

        val reloaded = AutoRipCache(store)
        assertTrue(reloaded.isCached("111"))
        assertEquals(listOf("alac", "aac"), reloaded.cachedFormats("111"))
    }

    @Test
    fun `empty formats are never persisted as a positive verdict`() {
        val cache = AutoRipCache(store)

        cache.rememberCached("111", emptyList())

        assertFalse(cache.isCached("111"))
        assertFalse(AutoRipCache(store).isCached("111"))
    }

    @Test
    fun `non digit ids are rejected`() {
        val cache = AutoRipCache(store)

        cache.rememberCached("abc", listOf("alac"))

        assertFalse(cache.isCached("abc"))
        assertTrue(cache.knownCachedIds(listOf("abc")).isEmpty())
    }

    @Test
    fun `only previously cached ids are reported as known`() {
        val cache = AutoRipCache(store)
        cache.rememberCached(mapOf("1" to listOf("alac"), "2" to listOf("aac")))

        val known = cache.knownCachedIds(listOf("1", "2", "3"))

        assertEquals(setOf("1", "2"), known)
    }

    @Test
    fun `a later verdict replaces the earlier formats`() {
        val cache = AutoRipCache(store)
        cache.rememberCached("111", listOf("aac"))

        cache.rememberCached("111", listOf("alac", "aac"))

        assertEquals(listOf("alac", "aac"), AutoRipCache(store).cachedFormats("111"))
    }

    @Test
    fun `invalidate drops the positive verdict so the next scan re-checks`() {
        val cache = AutoRipCache(store)
        cache.rememberCached(mapOf("1" to listOf("alac"), "2" to listOf("alac")))

        cache.invalidate(listOf("1"))

        assertFalse(cache.isCached("1"))
        assertTrue(cache.isCached("2"))
    }

    @Test
    fun `invalidate of an unknown id is a no-op`() {
        val cache = AutoRipCache(store)
        cache.rememberCached("111", listOf("alac"))

        cache.invalidate(listOf("999"))

        assertTrue(cache.isCached("111"))
    }

    @Test
    fun `clear forgets everything`() {
        val cache = AutoRipCache(store)
        cache.rememberCached("111", listOf("alac"))

        cache.clear()

        assertFalse(cache.isCached("111"))
        assertTrue(store.stored.isEmpty())
    }

    @Test
    fun `corrupt persisted json is treated as an empty cache`() {
        store.putString("auto_rip_cache_v1", "not json at all")

        assertFalse(AutoRipCache(store).isCached("111"))
    }

    @Test
    fun `a wrong cache version is ignored`() {
        store.putString(
            "auto_rip_cache_v1",
            """{"version":0,"entries":[{"id":"111","formats":["alac"]}]}""",
        )

        assertFalse(AutoRipCache(store).isCached("111"))
    }

    @Test
    fun `an unseen album id is not cached`() {
        assertFalse(AutoRipCache(store).isAlbumCached("111"))
    }

    @Test
    fun `a positive album verdict survives a new cache instance`() {
        AutoRipCache(store).rememberAlbumCached("111")

        val reloaded = AutoRipCache(store)
        assertTrue(reloaded.isAlbumCached("111"))
    }

    @Test
    fun `non digit album ids are rejected`() {
        val cache = AutoRipCache(store)
        cache.rememberAlbumCached("abc")

        assertFalse(cache.isAlbumCached("abc"))
        assertTrue(cache.knownCachedAlbumIds(listOf("abc")).isEmpty())
    }

    @Test
    fun `only previously cached album ids are reported as known`() {
        val cache = AutoRipCache(store)
        cache.rememberAlbumsCached(listOf("1", "2"))

        val known = cache.knownCachedAlbumIds(listOf("1", "2", "3"))
        assertEquals(setOf("1", "2"), known)
    }

    @Test
    fun `invalidateAlbums drops the positive verdict so the next scan re-checks`() {
        val cache = AutoRipCache(store)
        cache.rememberAlbumsCached(listOf("1", "2"))

        cache.invalidateAlbums(listOf("1"))

        assertFalse(cache.isAlbumCached("1"))
        assertTrue(cache.isAlbumCached("2"))
    }

    @Test
    fun `clear forgets both tracks and albums`() {
        val cache = AutoRipCache(store)
        cache.rememberCached("111", listOf("alac"))
        cache.rememberAlbumCached("222")

        cache.clear()

        assertFalse(cache.isCached("111"))
        assertFalse(cache.isAlbumCached("222"))
        assertTrue(store.stored.isEmpty())
    }

    @Test
    fun `corrupt persisted album json is treated as an empty cache`() {
        store.putString("auto_rip_album_cache_v1", "not json at all")

        assertFalse(AutoRipCache(store).isAlbumCached("111"))
    }

    @Test
    fun `a wrong album cache version is ignored`() {
        store.putString(
            "auto_rip_album_cache_v1",
            """{"version":0,"entries":[{"id":"111"}]}""",
        )

        assertFalse(AutoRipCache(store).isAlbumCached("111"))
    }
}
