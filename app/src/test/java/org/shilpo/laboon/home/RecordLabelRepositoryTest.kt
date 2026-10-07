package org.shilpo.laboon.home

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.AuthUser
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.lyricsporn.LyricspornRecordLabel
import org.shilpo.laboon.ui.screens.label.RecordLabelUiState
import org.shilpo.laboon.ui.screens.label.toUiState

class RecordLabelRepositoryTest {

    private fun createSessionStore(): SessionStore =
        SessionStore(FakeKeyValueStore()).apply {
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

    @Test
    fun resolveRecordLabelId_withoutSession_returnsNull() = runBlocking {
        val sessionStore = SessionStore(FakeKeyValueStore())
        val cache = RecordLabelCache(FakeKeyValueStore())
        val repository = RecordLabelRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        assertNull(repository.resolveRecordLabelId("Sub Pop"))
    }

    @Test
    fun resolveRecordLabelId_withBlankLabelName_returnsNull() = runBlocking {
        val sessionStore = createSessionStore()
        val cache = RecordLabelCache(FakeKeyValueStore())
        val repository = RecordLabelRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        assertNull(repository.resolveRecordLabelId("   "))
    }

    @Test
    fun resolveRecordLabelId_loadsFromPersistentCache() = runBlocking {
        val store = FakeKeyValueStore()
        val cache = RecordLabelCache(store)
        cache.saveResolvedId("sub pop", "1544322965")

        val sessionStore = SessionStore(store)
        val repository = RecordLabelRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        val resolved = repository.resolveRecordLabelId("Sub Pop")
        assertEquals("1544322965", resolved)
    }

    @Test
    fun getRecordLabel_withoutSession_returnsNull() = runBlocking {
        val sessionStore = SessionStore(FakeKeyValueStore())
        val cache = RecordLabelCache(FakeKeyValueStore())
        val repository = RecordLabelRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        assertNull(repository.getRecordLabel("1544322965"))
    }

    @Test
    fun getRecordLabel_withBlankInput_returnsNull() = runBlocking {
        val sessionStore = createSessionStore()
        val cache = RecordLabelCache(FakeKeyValueStore())
        val repository = RecordLabelRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        assertNull(repository.getRecordLabel("  "))
    }

    @Test
    fun getCachedRecordLabel_loadsFromPersistentCache() = runBlocking {
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
        val cache = RecordLabelCache(store)
        val cacheKey = listOf(
            "https://server.test",
            "123",
            "https://api.lyricsporn.test",
            LyricspornClient.currentStorefront(),
            "1544322965",
        ).joinToString(":")

        val cachedLabel = LyricspornRecordLabel(
            id = "1544322965",
            name = "Sub Pop",
            url = "https://music.apple.com/us/record-label/sub-pop/1544322965",
            description = "Iconic Seattle indie label.",
            artworkUrl = "https://example.com/artwork.jpg",
            editorialArtworkUrl = "https://example.com/editorial.jpg",
            latestReleases = listOf(
                HomeAlbum(
                    id = "apple_1",
                    title = "Bleach",
                    artist = "Nirvana",
                    artworkUrl = "https://example.com/bleach.jpg",
                    appleCatalogId = "1",
                ),
            ),
            topReleases = listOf(
                HomeAlbum(
                    id = "apple_2",
                    title = "Give Up",
                    artist = "The Postal Service",
                    artworkUrl = "https://example.com/giveup.jpg",
                    appleCatalogId = "2",
                ),
            ),
        )
        cache.save(cacheKey, cachedLabel)

        val repository = RecordLabelRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        val loaded = repository.getCachedRecordLabel("1544322965")
        assertNotNull(loaded)
        assertEquals("Sub Pop", loaded?.name)
        assertEquals("Iconic Seattle indie label.", loaded?.description)
        assertEquals(1, loaded?.latestReleases?.size)
        assertEquals("Bleach", loaded?.latestReleases?.first()?.title)
        assertEquals(1, loaded?.topReleases?.size)
        assertEquals("Give Up", loaded?.topReleases?.first()?.title)

        // getRecordLabel with forceRefresh=false returns cached result without API
        val result = repository.getRecordLabel("1544322965", forceRefresh = false)
        assertNotNull(result)
        assertEquals("Sub Pop", result?.name)
    }

    @Test
    fun getCachedRecordLabel_withApplePrefix_resolvesCachedUnprefixedId() = runBlocking {
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
        val cache = RecordLabelCache(store)
        val cacheKey = listOf(
            "https://server.test",
            "123",
            "https://api.lyricsporn.test",
            LyricspornClient.currentStorefront(),
            "1544322965",
        ).joinToString(":")

        val cachedLabel = LyricspornRecordLabel(
            id = "1544322965",
            name = "Sub Pop",
        )
        cache.save(cacheKey, cachedLabel)

        val repository = RecordLabelRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        val loaded = repository.getCachedRecordLabel("apple_1544322965")
        assertNotNull(loaded)
        assertEquals("Sub Pop", loaded?.name)
    }

    @Test
    fun recordLabelCache_handlesMemoryOnlyWhenStoreIsNull() {
        val cache = RecordLabelCache(store = null)
        val label = LyricspornRecordLabel(
            id = "test_id",
            name = "Test Label",
        )
        cache.save("test_key", label)
        val loaded = cache.load("test_key")
        assertNotNull(loaded)
        assertEquals("Test Label", loaded?.name)

        cache.saveResolvedId("test label", "test_id")
        assertEquals("test_id", cache.loadResolvedId("test label"))
    }

    @Test
    fun recordLabelCache_persistsAndRestoresArtistsWithArtwork() {
        val store = FakeKeyValueStore()
        val cache = RecordLabelCache(store)
        val artists = listOf(
            HomeArtist(
                id = "apple_12345",
                name = "Arijit Singh",
                imageUrl = "https://example.com/arijit.jpg",
                appleCatalogId = "12345",
            ),
            HomeArtist(
                id = "apple_67890",
                name = "Shreya Ghoshal",
                imageUrl = "https://example.com/shreya.jpg",
                appleCatalogId = "67890",
            ),
        )
        val label = LyricspornRecordLabel(
            id = "label_test",
            name = "Test Label",
            artists = artists,
        )
        cache.save("cache_key", label)

        val restoredCache = RecordLabelCache(store)
        val loaded = restoredCache.load("cache_key")
        assertNotNull(loaded)
        assertEquals(2, loaded?.artists?.size)
        assertEquals("Arijit Singh", loaded?.artists?.first()?.name)
        assertEquals("https://example.com/arijit.jpg", loaded?.artists?.first()?.imageUrl)
        assertEquals("12345", loaded?.artists?.first()?.appleCatalogId)
    }

    @Test
    fun toUiState_prefersEnrichedArtistsOverAlbumSplit() {
        val enriched = listOf(
            HomeArtist(
                id = "apple_999",
                name = "Stebin Ben",
                imageUrl = "https://example.com/stebin.jpg",
                appleCatalogId = "999",
            ),
        )
        val label = LyricspornRecordLabel(
            id = "test",
            name = "Label",
            latestReleases = listOf(
                HomeAlbum(
                    id = "album_1",
                    title = "Song",
                    artist = "Stebin Ben, Javed-Mohsin",
                ),
            ),
            artists = enriched,
        )
        val state = label.toUiState()
        org.junit.Assert.assertTrue(state is RecordLabelUiState.Loaded)
        val loaded = state as RecordLabelUiState.Loaded
        assertEquals(1, loaded.artists.size)
        assertEquals("Stebin Ben", loaded.artists.first().name)
        assertEquals("https://example.com/stebin.jpg", loaded.artists.first().imageUrl)
    }

    @Test
    fun toUiState_splitsCompoundArtistsWhenEnrichedArtistsEmpty() {
        val label = LyricspornRecordLabel(
            id = "test",
            name = "Label",
            latestReleases = listOf(
                HomeAlbum(
                    id = "album_1",
                    title = "Song",
                    artist = "Stebin Ben, Javed-Mohsin",
                ),
            ),
            artists = emptyList(),
        )
        val state = label.toUiState()
        org.junit.Assert.assertTrue(state is RecordLabelUiState.Loaded)
        val loaded = state as RecordLabelUiState.Loaded
        assertEquals(2, loaded.artists.size)
        assertEquals("Stebin Ben", loaded.artists[0].name)
        assertEquals("Javed-Mohsin", loaded.artists[1].name)
    }
}
