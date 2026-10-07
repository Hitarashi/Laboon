package org.shilpo.laboon.home

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.AuthUser
import org.shilpo.laboon.auth.SessionStore

class AlbumDetailsRepositoryTest {

    @Test
    fun resolveAlbumIdForTrack_withoutSession_returnsNull() = runBlocking {
        val sessionStore = SessionStore(FakeKeyValueStore())
        val cache = AlbumDetailsCache(FakeKeyValueStore())
        val repository = AlbumDetailsRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        val track = HomeTrack(
            id = "track-1",
            title = "Song Title",
            artist = "Artist Name",
            album = "Album Name",
            providerTrackId = "123456",
        )

        assertNull(repository.resolveAlbumIdForTrack(track))
    }

    @Test
    fun resolveAlbumIdForTrack_withoutLyricspornApiUrl_returnsNull() = runBlocking {
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
                    lyricspornApiUrl = null,
                ),
            )
        }
        val cache = AlbumDetailsCache(FakeKeyValueStore())
        val repository = AlbumDetailsRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        val track = HomeTrack(
            id = "track-1",
            title = "Song Title",
            artist = "Artist Name",
            album = "Album Name",
            providerTrackId = "123456",
        )

        assertNull(repository.resolveAlbumIdForTrack(track))
    }

    @Test
    fun resolveAlbumIdForTrack_withBlankTrackMetadata_returnsNull() = runBlocking {
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
        val cache = AlbumDetailsCache(FakeKeyValueStore())
        val repository = AlbumDetailsRepository(
            sessionStore = sessionStore,
            persistentCache = cache,
        )

        val track = HomeTrack(
            id = "track-empty",
            title = "",
            artist = "",
            album = null,
            providerTrackId = null,
        )

        assertNull(repository.resolveAlbumIdForTrack(track))
    }
}
