package org.shilpo.laboon.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.lyricsporn.LyricspornArtist
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpOutcome
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchRepositoryImpl
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

sealed interface ArtistDetailsResult {
    data class Success(
        val artist: LyricspornArtist,
        val topSongs: List<HomeTrack>,
        val latestRelease: HomeAlbum?,
        val albums: List<HomeAlbum>,
        val singles: List<HomeAlbum>,
        val similarArtists: List<HomeArtist>,
    ) : ArtistDetailsResult

    data object NotFound : ArtistDetailsResult

    data class Failure(val reason: FailureReason) : ArtistDetailsResult

    enum class FailureReason {
        API_UNAVAILABLE,
        NETWORK,
        SERVER,
        INVALID_RESPONSE,
    }
}

class ArtistDetailsRepository(
    private val sessionStore: SessionStore,
    private val persistentCache: ArtistDetailsCache,
    private val availabilityResolver: SearchRepository = SearchRepositoryImpl(sessionStore),
    private val artistClient: LyricspornClient = LyricspornClient,
) {
    private val artistCache = ConcurrentHashMap<String, ArtistDetailsResult.Success>()
    private val nameToIdCache = ConcurrentHashMap<String, String>()

    suspend fun resolveArtistId(artistName: String): String? = withContext(Dispatchers.IO) {
        val normalized = artistName.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return@withContext null
        nameToIdCache[normalized]?.let { return@withContext it }
        persistentCache.loadResolvedId(normalized)?.let {
            nameToIdCache[normalized] = it
            return@withContext it
        }

        val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl ?: return@withContext null
        val resolvedId = artistClient.resolveArtistId(apiBaseUrl, artistName)
        if (resolvedId != null) {
            nameToIdCache[normalized] = resolvedId
            persistentCache.saveResolvedId(normalized, resolvedId)
        }
        resolvedId
    }

    fun getCachedArtist(appleArtistId: String): ArtistDetailsResult.Success? {
        val cacheKey = artistCacheKey(appleArtistId)
        return artistCache[cacheKey] ?: persistentCache.load(cacheKey)?.also {
            artistCache[cacheKey] = it
        }
    }

    suspend fun getArtist(
        appleArtistId: String,
        forceRefresh: Boolean = false
    ): ArtistDetailsResult = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            getCachedArtist(appleArtistId)?.let { return@withContext it }
        }
        val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
            ?: return@withContext ArtistDetailsResult.Failure(ArtistDetailsResult.FailureReason.API_UNAVAILABLE)

        when (val outcome = artistClient.getArtistDetails(apiBaseUrl, appleArtistId)) {
            is HttpOutcome.Failure -> {
                val reason = when {
                    outcome.error.statusCode == 404 -> return@withContext ArtistDetailsResult.NotFound
                    outcome.error.kind == HttpErrorKind.NETWORK ||
                            outcome.error.kind == HttpErrorKind.TIMEOUT -> ArtistDetailsResult.FailureReason.NETWORK

                    outcome.error.kind == HttpErrorKind.STATUS -> ArtistDetailsResult.FailureReason.SERVER
                    outcome.error.kind == HttpErrorKind.MALFORMED -> ArtistDetailsResult.FailureReason.INVALID_RESPONSE
                    else -> ArtistDetailsResult.FailureReason.SERVER
                }
                ArtistDetailsResult.Failure(reason)
            }

            is HttpOutcome.Success -> {
                val artist = outcome.value
                val rawTracks = artist.topSongs.map { item ->
                    HomeTrack(
                        id = "apple_${item.id}",
                        title = item.name,
                        artist = item.artistName ?: artist.name,
                        album = item.albumName,
                        artworkUrl = item.artworkUrl,
                        source = null,
                        isrc = item.isrc,
                        providerTrackId = item.id,
                        durationMs = item.durationMs,
                    )
                }
                val enrichedTracks = availabilityResolver.enrichAvailability(rawTracks)

                val latestRelease = artist.latestRelease?.toHomeAlbum()
                val albums = artist.fullAlbums.map { it.toHomeAlbum() }
                val singles = artist.singles.map { it.toHomeAlbum() }
                val similar = artist.similarArtists.map { it.toHomeArtist() }

                val success = ArtistDetailsResult.Success(
                    artist = artist,
                    topSongs = enrichedTracks,
                    latestRelease = latestRelease,
                    albums = albums,
                    singles = singles,
                    similarArtists = similar,
                )
                val cacheKey = artistCacheKey(appleArtistId)
                artistCache[cacheKey] = success
                persistentCache.save(cacheKey, success)
                success
            }
        }
    }

    private fun artistCacheKey(appleArtistId: String): String {
        val session = sessionStore.getSession()
        return listOf(
            session?.serverUrl.orEmpty(),
            session?.user?.telegramId?.toString().orEmpty(),
            session?.lyricspornApiUrl.orEmpty(),
            LyricspornClient.currentStorefront(),
            appleArtistId,
        ).joinToString(":")
    }

    private fun LyricspornCatalogItem.toHomeAlbum(): HomeAlbum =
        HomeAlbum(
            id = "apple_$id",
            title = name,
            artist = artistName.orEmpty(),
            artworkUrl = artworkUrl,
            appleCatalogId = id,
        )

    private fun LyricspornCatalogItem.toHomeArtist(): HomeArtist =
        HomeArtist(
            id = "apple_$id",
            name = name,
            imageUrl = artworkUrl,
            appleCatalogId = id,
        )
}
