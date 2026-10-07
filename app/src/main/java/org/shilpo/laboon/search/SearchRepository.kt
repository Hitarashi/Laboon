package org.shilpo.laboon.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.home.HomePlaylist
import org.shilpo.laboon.home.HomeStation
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.objAtOrNull
import org.shilpo.laboon.net.stringOrNull
import java.util.Locale

data class PlaybackResolution(
    val streamUrl: String,
    val codec: String? = null,
    val backendTrackId: Int? = null,
)

/**
 * Cached-server-side state for one provider track id, as reported by `POST /api/v1/lookup`.
 * Only produced for tracks that actually have at least one usable format id.
 */
data class CachedTrackAvailability(
    val variants: List<TrackFormatVariant>,
    val preferredCodec: String?,
    val playbackTrackId: Int?,
) {
    val formats: List<String> get() = variants.map(TrackFormatVariant::format)
}

/**
 * Result of one batch availability lookup.
 *
 * Semantics, which must stay aligned with [SearchRepositoryImpl.enrichAvailability]:
 * a successful response is a complete answer. The server only reports tracks it has
 * formats for, so **absence from [cached] means "not ripped"** — exactly the condition
 * that makes the UI show the download icon. [unresolvedIds] is the *only* signal that we
 * failed to get an answer; those ids must be retried rather than ripped.
 */
data class BatchAvailabilityLookup(
    val cached: Map<String, CachedTrackAvailability> = emptyMap(),
    val unresolvedIds: Set<String> = emptySet(),
) {
    /** Ids we have no verdict for because the request failed. */
    fun unresolvedIn(requested: Iterable<String>): List<String> =
        requested.filter { it in unresolvedIds }

    companion object {
        /** A successful lookup that found nothing cached: every id needs a rip. */
        val EMPTY = BatchAvailabilityLookup()

        /** A lookup we could not perform: every requested id is unknown. */
        fun unavailable(requestedIds: Iterable<String>): BatchAvailabilityLookup =
            BatchAvailabilityLookup(unresolvedIds = requestedIds.toSet())
    }
}

fun interface TrackAvailabilityLookup {
    suspend fun lookupAvailableFormats(providerTrackIds: List<String>): BatchAvailabilityLookup
}

data class BatchAlbumAvailabilityLookup(
    val cachedAlbumIds: Set<String> = emptySet(),
    val uncachedAlbumIds: Set<String> = emptySet(),
    val unresolvedAlbumIds: Set<String> = emptySet(),
) {
    companion object {
        val EMPTY = BatchAlbumAvailabilityLookup()

        fun unavailable(requestedIds: Iterable<String>): BatchAlbumAvailabilityLookup =
            BatchAlbumAvailabilityLookup(unresolvedAlbumIds = requestedIds.toSet())
    }
}

fun interface AlbumAvailabilityLookup {
    suspend fun lookupAvailableAlbums(providerAlbumIds: List<String>): BatchAlbumAvailabilityLookup
}

enum class SearchFilter(val label: String, val apiType: String) {
    TOP_RESULTS("Top Results", "top-results"),
    ARTISTS("Artist", "artists"),
    ALBUMS("Albums", "albums"),
    SONGS("Songs", "songs"),
    PLAYLISTS("Playlists", "playlists"),
    STATIONS("Stations", "stations"),
    MUSIC_VIDEOS("Music Videos", "music-videos");

    companion object {
        val ALL = entries.toList()
    }
}

data class SearchResults(
    val tracks: List<HomeTrack> = emptyList(),
    val albums: List<HomeAlbum> = emptyList(),
    val artists: List<HomeArtist> = emptyList(),
    val playlists: List<HomePlaylist> = emptyList(),
    val stations: List<HomeStation> = emptyList(),
    val musicVideos: List<HomeTrack> = emptyList(),
    val topResults: List<LyricspornCatalogItem> = emptyList(),
)

data class SearchSuggestions(
    val tracks: List<HomeTrack> = emptyList(),
    val albums: List<HomeAlbum> = emptyList(),
    val artists: List<HomeArtist> = emptyList(),
)

interface SearchRepository {
    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.TOP_RESULTS
    ): SearchResults

    suspend fun enrichAvailability(tracks: List<HomeTrack>): List<HomeTrack>
    suspend fun searchHints(query: String): List<String> = emptyList()
    suspend fun searchSuggestions(query: String): SearchSuggestions = SearchSuggestions()
    suspend fun resolvePlaybackUrl(track: HomeTrack): String?
    suspend fun resolvePlayback(track: HomeTrack): PlaybackResolution?
    suspend fun resolvePlaybackBatch(tracks: List<HomeTrack>): List<HomeTrack>
}

class SearchRepositoryImpl(
    private val sessionStore: SessionStore,
    private val http: HttpJsonClient = HttpJsonClient(),
) : SearchRepository, TrackAvailabilityLookup, AlbumAvailabilityLookup {

    override suspend fun search(
        query: String,
        filter: SearchFilter,
    ): SearchResults = withContext(Dispatchers.IO) {
        val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
        val types = if (filter == SearchFilter.TOP_RESULTS) {
            "top-results,songs,albums,artists,playlists,stations,music-videos"
        } else {
            filter.apiType
        }
        var catalogResults = LyricspornClient.searchCatalog(
            apiBaseUrl = apiBaseUrl,
            term = query,
            types = types,
            limit = MAX_SEARCH_RESULTS,
        )
        if (catalogResults.songs.isEmpty() && catalogResults.albums.isEmpty() && catalogResults.artists.isEmpty() && types.contains(
                "top-results"
            )
        ) {
            val safeTypes = types.split(',').filter { it != "top-results" && it != "topResults" }
                .joinToString(",")
            if (safeTypes.isNotBlank()) {
                val fallback = LyricspornClient.searchCatalog(
                    apiBaseUrl = apiBaseUrl,
                    term = query,
                    types = safeTypes,
                    limit = MAX_SEARCH_RESULTS,
                )
                if (fallback.songs.isNotEmpty() || fallback.albums.isNotEmpty() || fallback.artists.isNotEmpty()) {
                    catalogResults = fallback
                }
            }
        }
        val tracks = withAvailability(catalogResults.songs)
        val albums = catalogResults.albums.map { it.toHomeAlbum() }
        val artists = catalogResults.artists.map { it.toHomeArtist() }
        val playlists = catalogResults.playlists.map { it.toHomePlaylist() }
        val stations = catalogResults.stations.map { it.toHomeStation() }
        val musicVideos = withAvailability(catalogResults.musicVideos)
        SearchResults(
            tracks = tracks,
            albums = albums,
            artists = artists,
            playlists = playlists,
            stations = stations,
            musicVideos = musicVideos,
            topResults = catalogResults.topResults,
        )
    }

    override suspend fun searchHints(query: String): List<String> =
        LyricspornClient.searchHints(
            apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl,
            term = query,
            limit = MAX_HINTS,
        )

    override suspend fun searchSuggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
            val suggestions = LyricspornClient.searchTopSuggestions(
                apiBaseUrl,
                query,
                limit = MAX_TOP_RESULTS,
            )
            val tracks = withAvailability(suggestions.songs)
            val albums = suggestions.albums.map { it.toHomeAlbum() }
            val artists = suggestions.artists.map { it.toHomeArtist() }
            SearchSuggestions(tracks = tracks, albums = albums, artists = artists)
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

    private fun LyricspornCatalogItem.toHomePlaylist(): HomePlaylist =
        HomePlaylist(
            id = "apple_$id",
            title = name,
            curator = artistName,
            artworkUrl = artworkUrl,
            appleCatalogId = id,
        )

    private fun LyricspornCatalogItem.toHomeStation(): HomeStation =
        HomeStation(
            id = "apple_$id",
            title = name,
            artworkUrl = artworkUrl,
            appleCatalogId = id,
        )

    private suspend fun withAvailability(items: List<LyricspornCatalogItem>): List<HomeTrack> {
        if (items.isEmpty()) return emptyList()
        return enrichAvailability(items.map { item ->
            HomeTrack(
                id = "apple_${item.id}",
                title = item.name,
                artist = item.artistName.orEmpty(),
                album = item.albumName,
                artworkUrl = item.artworkUrl,
                source = null,
                isrc = item.isrc,
                providerTrackId = item.id,
                durationMs = item.durationMs,
            )
        })
    }

    override suspend fun enrichAvailability(tracks: List<HomeTrack>): List<HomeTrack> {
        if (tracks.isEmpty()) return emptyList()
        val availabilityByAppleId =
            lookupAvailableFormats(tracks.mapNotNull(HomeTrack::providerTrackId)).cached
        return tracks.map { track ->
            val availability = track.providerTrackId?.let(availabilityByAppleId::get)
            track.copy(
                backendTrackId = availability?.playbackTrackId,
                isCached = availability?.variants?.isNotEmpty() == true,
                codec = availability?.preferredCodec,
                availableFormats = availability?.variants.orEmpty()
                    .map(TrackFormatVariant::format),
                availableVariants = availability?.variants.orEmpty(),
            )
        }
    }

    override suspend fun lookupAvailableFormats(
        providerTrackIds: List<String>,
    ): BatchAvailabilityLookup =
        withContext(Dispatchers.IO) {

            val session = sessionStore.getSession()
                ?: return@withContext BatchAvailabilityLookup.unavailable(providerTrackIds)
            val serverUrl = sanitizeServerUrl(session.serverUrl)
            val token = session.token.trim()
            if (serverUrl.isEmpty() || token.isEmpty()) {
                return@withContext BatchAvailabilityLookup.unavailable(providerTrackIds)
            }

            val validIds = providerTrackIds
                .mapNotNull(::normalizeProviderTrackId)
                .distinct()
            if (validIds.isEmpty()) return@withContext BatchAvailabilityLookup.EMPTY

            val availabilityById = linkedMapOf<String, CachedTrackAvailability>()
            val unresolved = linkedSetOf<String>()
            for (batch in validIds.chunked(LOOKUP_BATCH_SIZE)) {
                val body = JSONObject().put("track_ids", JSONArray(batch)).toString()
                val response = when (
                    val outcome = http.postJson(
                        "$serverUrl/api/v1/lookup",
                        body,
                        mapOf("Authorization" to "Bearer $token"),
                    )
                ) {
                    is HttpOutcome.Success -> runCatching { JSONObject(outcome.value) }.getOrNull()
                    is HttpOutcome.Failure -> null
                }

                if (response == null) {
                    unresolved += batch
                    continue
                }

                val tracks = response.arrOrNull("tracks")
                if (tracks == null) {
                    unresolved += batch
                    continue
                }
                for (index in 0 until tracks.length()) {
                    val track = tracks.objAtOrNull(index) ?: continue
                    val appleId = track.stringOrNull("apple_track_id")
                        ?.let(::normalizeProviderTrackId)
                        ?: continue
                    val cachedFormats = track.arrOrNull("formats")?.let { values ->
                        buildList<TrackFormatVariant> {
                            for (formatIndex in 0 until values.length()) {
                                val value = values.objAtOrNull(formatIndex) ?: continue
                                val format =
                                    value.stringOrNull("format")?.normalizeFormat() ?: continue
                                if (format !in SUPPORTED_FORMATS || any { it.format == format }) continue
                                val playbackTrackId = value.optInt("id")
                                    .takeIf { value.has("id") && !value.isNull("id") && it > 0 }
                                    ?: continue
                                val fileSizeBytes = value.optLong("file_size_bytes")
                                    .takeIf {
                                        value.has("file_size_bytes") &&
                                                !value.isNull("file_size_bytes") && it > 0L
                                    }
                                add(
                                    TrackFormatVariant(
                                        format = format,
                                        backendTrackId = playbackTrackId,
                                        fileSizeBytes = fileSizeBytes,
                                    )
                                )
                            }
                        }
                    }.orEmpty()
                    if (cachedFormats.isNotEmpty()) {
                        val preferred = cachedFormats.maxByOrNull { it.format.preference() }
                        availabilityById[appleId] = CachedTrackAvailability(
                            variants = cachedFormats,
                            preferredCodec = preferred?.format,
                            playbackTrackId = preferred?.backendTrackId,
                        )
                    }
                }
            }
            BatchAvailabilityLookup(
                cached = availabilityById,
                unresolvedIds = unresolved,
            )
        }

    override suspend fun lookupAvailableAlbums(
        providerAlbumIds: List<String>,
    ): BatchAlbumAvailabilityLookup = withContext(Dispatchers.IO) {
        val session = sessionStore.getSession()
            ?: return@withContext BatchAlbumAvailabilityLookup.unavailable(providerAlbumIds)
        val serverUrl = sanitizeServerUrl(session.serverUrl)
        val token = session.token.trim()
        if (serverUrl.isEmpty() || token.isEmpty()) {
            return@withContext BatchAlbumAvailabilityLookup.unavailable(providerAlbumIds)
        }

        val validIds = providerAlbumIds
            .mapNotNull(::normalizeProviderTrackId)
            .distinct()
        if (validIds.isEmpty()) return@withContext BatchAlbumAvailabilityLookup.EMPTY

        val cached = linkedSetOf<String>()
        val uncached = linkedSetOf<String>()
        val unresolved = linkedSetOf<String>()

        for (batch in validIds.chunked(LOOKUP_BATCH_SIZE)) {
            val body = JSONObject().put("album_ids", JSONArray(batch)).toString()
            val response = when (
                val outcome = http.postJson(
                    "$serverUrl/api/v1/lookup",
                    body,
                    mapOf("Authorization" to "Bearer $token"),
                )
            ) {
                is HttpOutcome.Success -> runCatching { JSONObject(outcome.value) }.getOrNull()
                is HttpOutcome.Failure -> null
            }

            if (response == null) {
                unresolved += batch
                continue
            }

            val albums = response.arrOrNull("albums")
            if (albums == null) {
                unresolved += batch
                continue
            }

            val foundIds = mutableSetOf<String>()
            for (index in 0 until albums.length()) {
                val albumObj = albums.objAtOrNull(index) ?: continue
                val appleId = albumObj.stringOrNull("apple_album_id")
                    ?.let(::normalizeProviderTrackId)
                    ?: continue
                foundIds += appleId
                val zipAvailable = albumObj.optBoolean("zip_available", false)
                if (zipAvailable) {
                    cached += appleId
                } else {
                    uncached += appleId
                }
            }
            for (id in batch) {
                if (id !in foundIds) {
                    uncached += id
                }
            }
        }
        BatchAlbumAvailabilityLookup(
            cachedAlbumIds = cached,
            uncachedAlbumIds = uncached,
            unresolvedAlbumIds = unresolved,
        )
    }

    private fun normalizeProviderTrackId(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    override suspend fun resolvePlaybackUrl(track: HomeTrack): String? =
        resolvePlayback(track)?.streamUrl

    override suspend fun resolvePlaybackBatch(tracks: List<HomeTrack>): List<HomeTrack> {
        if (tracks.isEmpty()) return emptyList()
        return coroutineScope {
            tracks.map { track ->
                async {
                    if (!track.streamUrl.isNullOrBlank()) return@async track
                    if (!track.isCached) return@async null
                    val resolution = resolvePlayback(track) ?: return@async null
                    track.copy(
                        streamUrl = resolution.streamUrl,
                        codec = resolution.codec ?: track.codec,
                        backendTrackId = resolution.backendTrackId ?: track.backendTrackId,
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }

    override suspend fun resolvePlayback(track: HomeTrack): PlaybackResolution? =
        withContext(Dispatchers.IO) {
            val session = sessionStore.getSession() ?: return@withContext null
            val serverUrl = sanitizeServerUrl(session.serverUrl)
            val token = session.token.trim()
            val backendId = track.backendTrackId?.takeIf { it > 0 } ?: return@withContext null
            if (serverUrl.isEmpty() || token.isEmpty()) return@withContext null

            val response = when (
                val outcome = http.getJson(
                    "$serverUrl/api/v1/tracks/$backendId/playback",
                    mapOf("Authorization" to "Bearer $token"),
                )
            ) {
                is HttpOutcome.Success -> outcome.value
                is HttpOutcome.Failure -> null
            } ?: return@withContext null

            val streamPath = response.optString("stream_url").trim()
            if (streamPath.isEmpty()) return@withContext null
            val resolvedUrl =
                if (streamPath.startsWith("http://") || streamPath.startsWith("https://")) {
                    streamPath
                } else {
                    "$serverUrl${if (streamPath.startsWith("/")) "" else "/"}$streamPath"
                }
            PlaybackResolution(
                streamUrl = resolvedUrl,
                codec = response.stringOrNull("codec") ?: track.codec,
                backendTrackId = backendId,
            )
        }

    private fun String.preference(): Int = when (this) {
        "ec-3" -> 3
        "alac" -> 2
        "aac" -> 1
        else -> 0
    }

    private fun String.normalizeFormat(): String = lowercase(Locale.ROOT)
        .replace("ec3", "ec-3")
        .replace("dolby_atmos", "ec-3")

    private fun sanitizeServerUrl(value: String): String {
        val trimmed = value.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private companion object {
        const val MAX_SEARCH_RESULTS = 25
        const val MAX_HINTS = 5
        const val MAX_TOP_RESULTS = 5
        const val LOOKUP_BATCH_SIZE = 50
        val SUPPORTED_FORMATS = setOf("alac", "ec-3", "aac")
    }
}
