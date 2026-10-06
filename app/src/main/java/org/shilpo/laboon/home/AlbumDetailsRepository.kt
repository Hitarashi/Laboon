package org.shilpo.laboon.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.lyricsporn.LyricspornAlbum
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpOutcome
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchRepositoryImpl
import java.util.concurrent.ConcurrentHashMap

sealed interface AlbumDetailsResult {
    data class Success(
        val album: LyricspornAlbum,
        val tracks: List<HomeTrack>,
    ) : AlbumDetailsResult

    data object NotFound : AlbumDetailsResult

    data class Failure(val reason: FailureReason) : AlbumDetailsResult

    enum class FailureReason {
        API_UNAVAILABLE,
        NETWORK,
        SERVER,
        INVALID_RESPONSE,
    }
}

class AlbumDetailsRepository(
    private val sessionStore: SessionStore,
    private val persistentCache: AlbumDetailsCache,
    private val availabilityResolver: SearchRepository = SearchRepositoryImpl(sessionStore),
    private val albumClient: LyricspornClient = LyricspornClient,
) {
    private val albumCache = ConcurrentHashMap<String, AlbumDetailsResult.Success>()

    suspend fun getAlbumIdForTrack(appleTrackId: String?): String? =
        withContext(Dispatchers.IO) {
            val trackId = appleTrackId?.takeIf(String::isNotBlank)
                ?: return@withContext null

            val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
                ?: return@withContext null

            albumClient.getTrackAlbumId(apiBaseUrl, trackId)
        }

    fun getCachedAlbum(appleAlbumId: String): AlbumDetailsResult.Success? {
        val cacheKey = albumCacheKey(appleAlbumId)
        return albumCache[cacheKey] ?: persistentCache.load(cacheKey)?.also {
            albumCache[cacheKey] = it
        }
    }

    suspend fun getAlbum(appleAlbumId: String): AlbumDetailsResult {
        getCachedAlbum(appleAlbumId)?.let { return it }
        return fetchAlbum(appleAlbumId)
    }

    suspend fun refreshAlbum(appleAlbumId: String): AlbumDetailsResult = fetchAlbum(appleAlbumId)

    private suspend fun fetchAlbum(appleAlbumId: String): AlbumDetailsResult =
        withContext(Dispatchers.IO) {
            val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
                ?: return@withContext AlbumDetailsResult.Failure(
                    AlbumDetailsResult.FailureReason.API_UNAVAILABLE,
                )

            val result =
                when (val response = albumClient.getAlbumDetails(apiBaseUrl, appleAlbumId)) {
                    is HttpOutcome.Success -> {
                        val album = response.value
                        val tracks = availabilityResolver.enrichAvailability(
                            album.tracks.map { catalogTrack ->
                                HomeTrack(
                                    id = "apple_${catalogTrack.id}",
                                    title = catalogTrack.name,
                                    artist = catalogTrack.artistName ?: album.artistName.orEmpty(),
                                    album = catalogTrack.albumName ?: album.name,
                                    artworkUrl = catalogTrack.artworkUrl ?: album.artworkUrl,
                                    isrc = catalogTrack.isrc,
                                    providerTrackId = catalogTrack.id,
                                    durationMs = catalogTrack.durationMs,
                                    contentRating = catalogTrack.contentRating,
                                )
                            },
                        )
                        AlbumDetailsResult.Success(album, tracks)
                    }

                    is HttpOutcome.Failure -> when {
                        response.error.statusCode == 404 -> AlbumDetailsResult.NotFound
                        response.error.kind == HttpErrorKind.NETWORK ||
                                response.error.kind == HttpErrorKind.TIMEOUT ->
                            AlbumDetailsResult.Failure(AlbumDetailsResult.FailureReason.NETWORK)

                        response.error.kind == HttpErrorKind.MALFORMED ->
                            AlbumDetailsResult.Failure(AlbumDetailsResult.FailureReason.INVALID_RESPONSE)

                        else -> AlbumDetailsResult.Failure(AlbumDetailsResult.FailureReason.SERVER)
                    }
                }
            if (result is AlbumDetailsResult.Success) {
                val cacheKey = albumCacheKey(appleAlbumId)
                albumCache[cacheKey] = result
                persistentCache.save(cacheKey, result)
            }
            result
        }

    private fun albumCacheKey(appleAlbumId: String): String {
        val session = sessionStore.getSession()
        return listOf(
            session?.serverUrl.orEmpty(),
            session?.user?.telegramId?.toString().orEmpty(),
            session?.lyricspornApiUrl.orEmpty(),
            LyricspornClient.currentStorefront(),
            appleAlbumId,
        ).joinToString(":")
    }
}
