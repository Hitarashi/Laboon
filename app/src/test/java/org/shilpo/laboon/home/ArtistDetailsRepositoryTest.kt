package org.shilpo.laboon.home

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.AuthUser
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.lyricsporn.LyricspornArtist
import org.shilpo.laboon.lyricsporn.LyricspornClient

class ArtistDetailsRepositoryTest {

    @Test
    fun resolveArtistId_withoutSession_returnsNull() = runBlocking {
        val sessionStore = SessionStore(FakeKeyValueStore())
        val cache = ArtistDetailsCache(FakeKeyValueStore())
        val repository = ArtistDetailsRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        assertNull(repository.resolveArtistId("Radiohead"))
    }

    @Test
    fun resolveArtistId_withBlankArtistName_returnsNull() = runBlocking {
        val sessionStore = SessionStore(FakeKeyValueStore()).apply {
            saveSession(
                AuthSession(
                    serverUrl = "https://server.test",
                    token = "token",
                    refreshToken = "refresh",
                    expiresAtUnix = 999999L,
                    user = AuthUser(
                        telegramId = 123L,
                        name = "Test",
                        username = "test",
                        firstName = null,
                        lastName = null,
                    ),
                    lyricspornApiUrl = "https://api.lyricsporn.test",
                ),
            )
        }
        val cache = ArtistDetailsCache(FakeKeyValueStore())
        val repository = ArtistDetailsRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        assertNull(repository.resolveArtistId("   "))
    }

    @Test
    fun getArtist_withoutSession_returnsApiUnavailable() = runBlocking {
        val sessionStore = SessionStore(FakeKeyValueStore())
        val cache = ArtistDetailsCache(FakeKeyValueStore())
        val repository = ArtistDetailsRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        val result = repository.getArtist("657515")
        assertTrue(result is ArtistDetailsResult.Failure)
        assertEquals(
            ArtistDetailsResult.FailureReason.API_UNAVAILABLE,
            (result as ArtistDetailsResult.Failure).reason,
        )
    }

    @Test
    fun getCachedArtist_loadsFromPersistentCache() = runBlocking {
        val store = FakeKeyValueStore()
        val sessionStore = SessionStore(store).apply {
            saveSession(
                AuthSession(
                    serverUrl = "https://server.test",
                    token = "token",
                    refreshToken = "refresh",
                    expiresAtUnix = 999999L,
                    user = AuthUser(
                        telegramId = 123L,
                        name = "Test",
                        username = "test",
                        firstName = null,
                        lastName = null,
                    ),
                    lyricspornApiUrl = "https://api.lyricsporn.test",
                ),
            )
        }
        val cache = ArtistDetailsCache(store)
        val cacheKey = listOf(
            "https://server.test",
            "123",
            "https://api.lyricsporn.test",
            LyricspornClient.currentStorefront(),
            "657515",
        ).joinToString(":")
        val cachedSuccess = ArtistDetailsResult.Success(
            artist = LyricspornArtist(id = "657515", name = "Radiohead"),
            topSongs = emptyList(),
            latestRelease = null,
            albums = emptyList(),
            singles = emptyList(),
            similarArtists = emptyList(),
        )
        cache.save(cacheKey, cachedSuccess)

        val repository = ArtistDetailsRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        val loaded = repository.getCachedArtist("657515")
        assertNotNull(loaded)
        assertEquals("Radiohead", loaded?.artist?.name)

        // getArtist with forceRefresh=false returns cached result without API
        val result = repository.getArtist("657515", forceRefresh = false)
        assertTrue(result is ArtistDetailsResult.Success)
        assertEquals("Radiohead", (result as ArtistDetailsResult.Success).artist.name)
    }

    @Test
    fun resolveArtistId_loadsFromPersistentCache() = runBlocking {
        val store = FakeKeyValueStore()
        val cache = ArtistDetailsCache(store)
        cache.saveResolvedId("radiohead", "657515")

        val sessionStore = SessionStore(store)
        val repository = ArtistDetailsRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        val resolved = repository.resolveArtistId("Radiohead")
        assertEquals("657515", resolved)
    }
}
