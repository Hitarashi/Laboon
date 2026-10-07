package org.shilpo.laboon.lyricsporn

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.net.HttpError
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.objAtOrNull
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class LyricspornCatalogItem(
    val id: String,
    val type: String,
    val name: String,
    val artistName: String? = null,
    val albumName: String? = null,
    val artworkUrl: String? = null,
    val motionArtwork: LyricspornMotionArtwork? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
)

data class LyricspornSearchResults(
    val songs: List<LyricspornCatalogItem> = emptyList(),
    val albums: List<LyricspornCatalogItem> = emptyList(),
    val artists: List<LyricspornCatalogItem> = emptyList(),
    val playlists: List<LyricspornCatalogItem> = emptyList(),
    val stations: List<LyricspornCatalogItem> = emptyList(),
    val musicVideos: List<LyricspornCatalogItem> = emptyList(),
    val topResults: List<LyricspornCatalogItem> = emptyList(),
)

data class LyricspornTopSuggestions(
    val songs: List<LyricspornCatalogItem> = emptyList(),
    val albums: List<LyricspornCatalogItem> = emptyList(),
    val artists: List<LyricspornCatalogItem> = emptyList(),
)

data class LyricspornCatalogMatch(
    val item: LyricspornCatalogItem,
    val isExactIdentity: Boolean,
)

data class LyricspornMotionArtwork(
    val url: String,
    val format: String? = null,
)

internal fun preferredAlbumEditorialNotes(standard: String?, short: String?): String? =
    standard?.trim()?.takeIf(String::isNotEmpty)
        ?: short?.trim()?.takeIf(String::isNotEmpty)

private data class LyricspornTrackDetails(
    val albumId: String?,
    val motionArtwork: LyricspornMotionArtwork?,
    val albumName: String? = null,
)

object LyricspornClient {
    private const val MAX_ARTWORK_LOOKUPS = 3
    private const val DEFAULT_STOREFRONT = "us"

    private val http = HttpJsonClient()
    private val catalogItemCache = ConcurrentHashMap<String, LyricspornCatalogMatch>()
    private val motionArtworkCache = ConcurrentHashMap<String, LyricspornMotionArtwork>()
    private val trackDetailsCache = ConcurrentHashMap<String, LyricspornTrackDetails>()
    private val trackDetailsLocks = ConcurrentHashMap<String, Mutex>()
    private val artworkPermits = Semaphore(MAX_ARTWORK_LOOKUPS)

    fun normalizeApiBaseUrl(value: String?): String? {
        val baseUrl = value?.trim()?.trimEnd('/')?.takeIf(String::isNotEmpty) ?: return null
        return baseUrl.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("http://", ignoreCase = true)
        }
    }

    fun normalizedArtworkKey(title: String, artist: String, apiBaseUrl: String?): String =
        "${normalizeApiBaseUrl(apiBaseUrl).orEmpty()}:${currentStorefront()}:${title.normalized()}:${artist.normalized()}"

    suspend fun searchCatalog(
        apiBaseUrl: String?,
        term: String,
        types: String = "top-results,songs,albums,artists,playlists,stations,music-videos",
        limit: Int = 25,
    ): LyricspornSearchResults {
        if (term.isBlank()) return LyricspornSearchResults()
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return LyricspornSearchResults()
        val query = encode(term.trim())
        val encodedTypes = encode(types.trim())
        val json = getJson(
            "$baseUrl/catalog/search?term=$query&types=$encodedTypes&limit=${
                limit.coerceIn(
                    1,
                    25
                )
            }&artworkSize=300",
        ) ?: return LyricspornSearchResults()
        val resultsObj = json.objOrNull("results") ?: return LyricspornSearchResults()
        val topResults = resultsObj.objOrNull("topResults")?.arrOrNull("items")
            ?.toCatalogItems(300)
            .orEmpty()
            .filter { it.id.matches(APPLE_CATALOG_ID_PATTERN) }
        val songs = resultsObj.objOrNull("songs")?.arrOrNull("items")
            ?.toCatalogItems(300)
            .orEmpty()
            .filter { it.type == "song" && it.id.isNumericAppleId() }
        val albums = resultsObj.objOrNull("albums")?.arrOrNull("items")
            ?.toCatalogItems(300)
            .orEmpty()
            .filter { it.type == "album" && it.id.matches(APPLE_CATALOG_ID_PATTERN) }
        val artists = resultsObj.objOrNull("artists")?.arrOrNull("items")
            ?.toCatalogItems(300)
            .orEmpty()
            .filter { it.type == "artist" && it.id.matches(APPLE_CATALOG_ID_PATTERN) }
        val playlists = resultsObj.objOrNull("playlists")?.arrOrNull("items")
            ?.toCatalogItems(300)
            .orEmpty()
            .filter { it.type == "playlist" && it.id.matches(APPLE_CATALOG_ID_PATTERN) }
        val stations = resultsObj.objOrNull("stations")?.arrOrNull("items")
            ?.toCatalogItems(300)
            .orEmpty()
            .filter { it.type == "station" && it.id.matches(APPLE_CATALOG_ID_PATTERN) }
        val musicVideos = resultsObj.objOrNull("musicVideos")?.arrOrNull("items")
            ?.toCatalogItems(300)
            .orEmpty()
            .filter { it.type == "musicVideo" && it.id.matches(APPLE_CATALOG_ID_PATTERN) }
        return LyricspornSearchResults(
            songs = songs,
            albums = albums,
            artists = artists,
            playlists = playlists,
            stations = stations,
            musicVideos = musicVideos,
            topResults = topResults,
        )
    }

    suspend fun getTrackLyrics(apiBaseUrl: String?, appleTrackId: String): JSONObject? {
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return null
        val trackId = appleTrackId.takeIf { it.isNumericAppleId() } ?: return null
        return getJson("$baseUrl/tracks/$trackId?include=lyrics&formats=json")
    }

    suspend fun getTrackAlbumId(apiBaseUrl: String?, appleTrackId: String): String? {
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return null
        val trackId = appleTrackId.takeIf { it.isNumericAppleId() } ?: return null
        return getTrackDetails(baseUrl, trackId)?.albumId
    }

    suspend fun getTrackAlbumName(apiBaseUrl: String?, appleTrackId: String): String? {
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return null
        val trackId = appleTrackId.takeIf { it.isNumericAppleId() } ?: return null
        return getTrackDetails(baseUrl, trackId)?.albumName
    }

    suspend fun getAlbumDetails(
        apiBaseUrl: String?,
        appleAlbumId: String,
    ): HttpOutcome<LyricspornAlbum> {
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl)
            ?: return HttpOutcome.Failure(
                HttpError(HttpErrorKind.NETWORK, message = "Lyricsporn API is unavailable"),
            )
        val albumId = appleAlbumId.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
            ?: return HttpOutcome.Failure(
                HttpError(HttpErrorKind.MALFORMED, message = "Invalid Apple Music album ID"),
            )
        val url =
            "$baseUrl/albums/$albumId?include=artwork,tracks,artists,genres,recordLabels,otherVersions,editorialNotes" +
                    "&limit=100&artworkSize=1200"
        return when (val response = http.getJson(url.withCurrentStorefront())) {
            is HttpOutcome.Failure -> response
            is HttpOutcome.Success -> {
                val album = response.value.objOrNull("data")?.toLyricspornAlbum()
                if (album != null) {
                    HttpOutcome.Success(album)
                } else {
                    HttpOutcome.Failure(
                        HttpError(
                            HttpErrorKind.MALFORMED,
                            message = "Lyricsporn returned an invalid album response",
                        ),
                    )
                }
            }
        }
    }

    suspend fun searchHints(apiBaseUrl: String?, term: String, limit: Int = 5): List<String> {
        if (term.isBlank()) return emptyList()
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return emptyList()
        val query = encode(term.trim())
        val json = getJson(
            "$baseUrl/catalog/search/hints?term=$query&limit=${limit.coerceIn(1, 25)}",
        ) ?: return emptyList()
        return json.arrOrNull("terms")?.let { items ->
            buildList {
                for (index in 0 until items.length()) {
                    items.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
                }
            }
        }.orEmpty()
    }

    suspend fun searchTopSuggestions(
        apiBaseUrl: String?,
        term: String,
        limit: Int = 5,
    ): LyricspornTopSuggestions {
        if (term.isBlank()) return LyricspornTopSuggestions()
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return LyricspornTopSuggestions()
        val query = encode(term.trim())
        val json = getJson(
            "$baseUrl/catalog/search/suggestions?term=$query&kinds=topResults&types=songs,albums,artists" +
                    "&limit=${limit.coerceIn(1, 10)}&artworkSize=300",
        ) ?: return LyricspornTopSuggestions()
        val suggestions = json.arrOrNull("suggestions") ?: return LyricspornTopSuggestions()
        val songs = mutableListOf<LyricspornCatalogItem>()
        val albums = mutableListOf<LyricspornCatalogItem>()
        val artists = mutableListOf<LyricspornCatalogItem>()
        for (index in 0 until suggestions.length()) {
            val suggestion = suggestions.objAtOrNull(index) ?: continue
            if (suggestion.optString("kind") != "topResults") continue
            val item = suggestion.objOrNull("content")?.toCatalogItem(300) ?: continue
            if (item.type == "song" && item.id.isNumericAppleId()) {
                songs.add(item)
            } else if (item.type == "album" && item.id.matches(APPLE_CATALOG_ID_PATTERN)) {
                albums.add(item)
            } else if (item.type == "artist" && item.id.matches(APPLE_CATALOG_ID_PATTERN)) {
                artists.add(item)
            }
        }
        return LyricspornTopSuggestions(
            songs = songs.distinctBy(LyricspornCatalogItem::id),
            albums = albums.distinctBy(LyricspornCatalogItem::id),
            artists = artists.distinctBy(LyricspornCatalogItem::id),
        )
    }

    suspend fun getArtistDetails(
        apiBaseUrl: String?,
        appleArtistId: String,
        artworkSize: Int = 600,
    ): HttpOutcome<LyricspornArtist> {
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl)
            ?: return HttpOutcome.Failure(
                HttpError(HttpErrorKind.NETWORK, message = "Lyricsporn API is unavailable"),
            )
        val artistId = appleArtistId.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
            ?: return HttpOutcome.Failure(
                HttpError(HttpErrorKind.MALFORMED, message = "Invalid Apple Music artist ID"),
            )
        val url =
            "$baseUrl/artists/$artistId?include=artwork,editorialNotes,topSongs,latestRelease,fullAlbums,singles,similarArtists" +
                    "&limit=20&artworkSize=$artworkSize"
        return when (val response = http.getJson(url.withCurrentStorefront())) {
            is HttpOutcome.Failure -> response
            is HttpOutcome.Success -> {
                val artist = response.value.objOrNull("data")?.toLyricspornArtist(artworkSize)
                if (artist != null) {
                    HttpOutcome.Success(artist)
                } else {
                    HttpOutcome.Failure(
                        HttpError(
                            HttpErrorKind.MALFORMED,
                            message = "Lyricsporn returned an invalid artist response",
                        ),
                    )
                }
            }
        }
    }

    suspend fun resolveArtistId(apiBaseUrl: String?, artistName: String): String? {
        val trimmed = artistName.trim()
        if (trimmed.isEmpty()) return null
        val results = searchCatalog(apiBaseUrl, trimmed, limit = 5)
        val normalized = trimmed.lowercase(Locale.ROOT)
        return results.artists.firstOrNull {
            it.name.trim().lowercase(Locale.ROOT) == normalized
        }?.id
            ?: results.artists.firstOrNull()?.id
    }

    suspend fun resolveArtistCatalogItem(
        apiBaseUrl: String?,
        artistName: String,
        artworkSize: Int = 300,
    ): LyricspornCatalogItem? {
        val trimmed = artistName.trim()
        if (trimmed.isEmpty()) return null
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return null
        val items = searchCatalogItems(
            apiBaseUrl = baseUrl,
            term = trimmed,
            type = "artists",
            limit = 5,
            artworkSize = artworkSize,
        )
        if (items.isEmpty()) return null
        fun normalizeKey(s: String): String =
            s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "")

        val normalizedQuery = normalizeKey(trimmed)
        val exact = items.firstOrNull { item ->
            normalizeKey(item.name) == normalizedQuery
        }
        return exact ?: items.firstOrNull()
    }

    suspend fun getRecordLabel(
        apiBaseUrl: String?,
        appleLabelId: String,
        artworkSize: Int = 600,
    ): HttpOutcome<LyricspornRecordLabel> {
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl)
            ?: return HttpOutcome.Failure(
                HttpError(HttpErrorKind.NETWORK, message = "Lyricsporn API is unavailable"),
            )
        val labelId = appleLabelId.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
            ?: return HttpOutcome.Failure(
                HttpError(HttpErrorKind.MALFORMED, message = "Invalid Apple Music record label ID"),
            )
        val url =
            "$baseUrl/record-labels/$labelId?include=artwork,editorialArtwork,description,latestReleases,topReleases" +
                    "&limit=25&artworkSize=$artworkSize"
        return when (val response = http.getJson(url.withCurrentStorefront())) {
            is HttpOutcome.Failure -> response
            is HttpOutcome.Success -> {
                val label = response.value.objOrNull("data")?.toLyricspornRecordLabel(artworkSize)
                if (label != null) {
                    HttpOutcome.Success(label)
                } else {
                    HttpOutcome.Failure(
                        HttpError(
                            HttpErrorKind.MALFORMED,
                            message = "Lyricsporn returned an invalid record label response",
                        ),
                    )
                }
            }
        }
    }

    suspend fun resolveRecordLabelId(apiBaseUrl: String?, labelName: String): String? {
        val trimmed = labelName.trim()
        if (trimmed.isEmpty()) return null
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return null
        val url = "$baseUrl/catalog/search?term=${encode(trimmed)}&types=record-labels&limit=5"
        val json = getJson(url) ?: return null
        val resultsObj = json.objOrNull("results") ?: return null
        val recordLabels = resultsObj.objOrNull("recordLabels")
            ?: resultsObj.objOrNull("record-labels")
            ?: return null
        val itemsArr = recordLabels.arrOrNull("items")
            ?: recordLabels.arrOrNull("data")
            ?: return null

        fun normalizeKey(s: String): String =
            s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "")

        val normalizedQuery = normalizeKey(trimmed)
        if (normalizedQuery.isEmpty()) return null

        var bestPartialMatchId: String? = null

        for (index in 0 until itemsArr.length()) {
            val itemObj = itemsArr.objAtOrNull(index) ?: continue
            val id = itemObj.stringOrNull("id")?.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
                ?: continue
            val name = itemObj.stringOrNull("name")
                ?: itemObj.objOrNull("attributes")?.stringOrNull("name")
                ?: continue
            val normalizedName = normalizeKey(name)

            if (normalizedName == normalizedQuery) {
                return id
            }
            if (bestPartialMatchId == null && (normalizedName.contains(normalizedQuery) || normalizedQuery.contains(
                    normalizedName
                ))
            ) {
                bestPartialMatchId = id
            }
        }
        return bestPartialMatchId
    }

    suspend fun resolveTrackArtwork(
        apiBaseUrl: String?,
        title: String,
        artist: String,
        album: String? = null,
    ): String? =
        resolveArtwork(
            apiBaseUrl = apiBaseUrl,
            type = "songs",
            title = title,
            artist = artist,
            album = album,
        )

    suspend fun resolveTrackCatalogItem(
        apiBaseUrl: String?,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null,
    ): LyricspornCatalogMatch? =
        resolveCatalogItem(
            apiBaseUrl = apiBaseUrl,
            type = "songs",
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
        )

    suspend fun resolveTrackMotionArtwork(
        apiBaseUrl: String?,
        appleTrackId: String?,
        title: String,
        artist: String,
        album: String? = null,
    ): LyricspornMotionArtwork? {
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return null
        val trackId = appleTrackId?.takeIf { id -> id.isNotBlank() && id.all(Char::isDigit) }
        val normalizedTitle = title.normalized()
        val normalizedArtist = artist.normalized()
        if (trackId == null && (normalizedTitle.isBlank() || normalizedArtist.isBlank())) {
            return null
        }
        val cacheKey = if (trackId != null) {
            "$baseUrl:${currentStorefront()}:track-motion:$trackId"
        } else {
            "$baseUrl:${currentStorefront()}:track-motion:$normalizedTitle:$normalizedArtist:${
                album.orEmpty().normalized()
            }"
        }
        motionArtworkCache[cacheKey]?.let { return it }

        return artworkPermits.withPermit {
            motionArtworkCache[cacheKey]?.let { return@withPermit it }
            val artwork = if (trackId != null) {
                getTrackDetails(baseUrl, trackId)?.motionArtwork
            } else {
                val query = listOf(title.trim(), artist.trim(), album.orEmpty().trim())
                    .filter(String::isNotEmpty)
                    .joinToString(" ")
                val items = searchCatalogItems(
                    apiBaseUrl = baseUrl,
                    term = query,
                    type = "songs",
                    limit = 5,
                    artworkSize = 50,
                    includeMotionArtwork = true,
                )
                val exactMatch = items.firstOrNull { item ->
                    item.name.normalized() == normalizedTitle &&
                            item.artistName.orEmpty().normalized() == normalizedArtist &&
                            (album.isNullOrBlank() ||
                                    item.albumName.orEmpty().normalized() == album.normalized())
                } ?: items.firstOrNull { item ->
                    item.name.normalized() == normalizedTitle &&
                            item.artistName.orEmpty().normalized() == normalizedArtist
                }
                exactMatch?.motionArtwork
            }
            artwork?.also { motionArtworkCache[cacheKey] = it }
        }
    }

    private suspend fun getTrackDetails(
        apiBaseUrl: String,
        appleTrackId: String,
    ): LyricspornTrackDetails? {
        val key = "$apiBaseUrl:${currentStorefront()}:track-details:$appleTrackId"
        trackDetailsCache[key]?.let { return it }
        val lock = trackDetailsLocks[key] ?: synchronized(trackDetailsLocks) {
            trackDetailsLocks[key] ?: Mutex().also { trackDetailsLocks[key] = it }
        }
        return lock.withLock {
            trackDetailsCache[key]?.let { return@withLock it }
            val track = getJson(
                "$apiBaseUrl/tracks/$appleTrackId?include=motionArtwork,album",
            )?.objOrNull("track") ?: return@withLock null
            val albumObj = track.objOrNull("albumResource")
            val albumId =
                albumObj?.stringOrNull("id")?.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
                    ?: track.stringOrNull("albumId")
                        ?.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
            val albumName = track.stringOrNull("album")
                ?: track.stringOrNull("albumName")
                ?: albumObj?.stringOrNull("name")
            LyricspornTrackDetails(
                albumId = albumId,
                motionArtwork = track.objOrNull("motionArtwork")?.toMotionArtwork(),
                albumName = albumName,
            ).also { trackDetailsCache[key] = it }
        }
    }

    suspend fun resolveAlbumArtwork(apiBaseUrl: String?, title: String, artist: String): String? =
        resolveArtwork(apiBaseUrl = apiBaseUrl, type = "albums", title = title, artist = artist)

    suspend fun resolveAlbumCatalogItem(
        apiBaseUrl: String?,
        title: String,
        artist: String,
    ): LyricspornCatalogMatch? = resolveCatalogItem(
        apiBaseUrl = apiBaseUrl,
        type = "albums",
        title = title,
        artist = artist,
    )?.takeIf { it.item.type == "album" }

    suspend fun resolveArtistArtwork(apiBaseUrl: String?, artist: String): String? =
        resolveArtwork(apiBaseUrl = apiBaseUrl, type = "artists", title = artist, artist = null)

    suspend fun resolveArtistCatalogItem(
        apiBaseUrl: String?,
        artist: String
    ): LyricspornCatalogItem? =
        resolveCatalogItem(apiBaseUrl = apiBaseUrl, type = "artists", title = artist, artist = null)
            ?.item

    private suspend fun resolveArtwork(
        apiBaseUrl: String?,
        type: String,
        title: String,
        artist: String?,
        album: String? = null,
    ): String? = resolveCatalogItem(apiBaseUrl, type, title, artist, album)
        ?.takeIf { type != "songs" || it.isExactIdentity }
        ?.item
        ?.artworkUrl

    private suspend fun resolveCatalogItem(
        apiBaseUrl: String?,
        type: String,
        title: String,
        artist: String?,
        album: String? = null,
        durationMs: Long? = null,
    ): LyricspornCatalogMatch? {
        if (title.isBlank()) return null
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return null
        val normalizedTitle = title.normalized()
        val normalizedArtist = artist.orEmpty().normalized()
        val normalizedAlbum = album.orEmpty().normalized()
        if (normalizedTitle.isBlank()) return null
        val key =
            "$baseUrl:${currentStorefront()}:$type:$normalizedTitle:$normalizedArtist:$normalizedAlbum:$durationMs"
        catalogItemCache[key]?.let { return it }

        return artworkPermits.withPermit {
            catalogItemCache[key]?.let { return@withPermit it }
            val query = listOfNotNull(
                title.trim(),
                artist?.trim()?.takeIf(String::isNotEmpty),
                album?.trim()?.takeIf(String::isNotEmpty),
            )
                .joinToString(" ")
            val items = searchCatalogItems(baseUrl, query, type, limit = 5, artworkSize = 300)
                .let { results ->
                    if (type == "songs") {
                        results.filter { it.type == "song" && it.id.isNumericAppleId() }
                    } else {
                        results
                    }
                }
            val exactIdentity = items.firstOrNull { item ->
                item.name.normalized() == normalizedTitle &&
                        !normalizedArtist.isBlank() &&
                        item.artistName.orEmpty().normalized() == normalizedArtist &&
                        (normalizedAlbum.isBlank() || item.albumName.orEmpty()
                            .normalized().let { itemAlbum ->
                                itemAlbum.isBlank() || itemAlbum == normalizedAlbum
                            }) &&
                        (durationMs == null || item.durationMs == null ||
                                item.durationMs == durationMs)
            }
            val exact = items.firstOrNull { item ->
                item.name.normalized() == normalizedTitle &&
                        (artist == null || item.artistName.orEmpty()
                            .normalized() == normalizedArtist) &&
                        (normalizedAlbum.isBlank() || item.albumName.orEmpty()
                            .normalized().let { itemAlbum ->
                                itemAlbum.isBlank() || itemAlbum == normalizedAlbum
                            }) &&
                        (durationMs == null || item.durationMs == null ||
                                item.durationMs == durationMs)
            }
            val selected = exactIdentity ?: exact ?: items.firstOrNull { item ->
                val itemTitle = item.name.normalized()
                val titleMatches =
                    itemTitle.contains(normalizedTitle) || normalizedTitle.contains(itemTitle)
                val artistMatches = artist == null ||
                        item.artistName.orEmpty().normalized().let { itemArtist ->
                            normalizedArtist.isNotEmpty() && itemArtist.isNotEmpty() &&
                                    (itemArtist == normalizedArtist ||
                                            itemArtist.contains(normalizedArtist) || normalizedArtist.contains(
                                        itemArtist
                                    ))
                        }
                titleMatches && artistMatches && itemTitle.isNotEmpty()
            }
            selected?.let { item ->
                LyricspornCatalogMatch(
                    item = item,
                    isExactIdentity = exactIdentity?.id == item.id,
                ).also {
                    catalogItemCache[key] = it
                }
            }
        }
    }

    private suspend fun searchCatalogItems(
        apiBaseUrl: String,
        term: String,
        type: String,
        limit: Int,
        artworkSize: Int,
        includeMotionArtwork: Boolean = false,
    ): List<LyricspornCatalogItem> {
        val json = getJson(
            "$apiBaseUrl/catalog/search?term=${encode(term)}&types=$type" +
                    "&limit=${limit.coerceIn(1, 25)}" +
                    "&artworkSize=$artworkSize" +
                    if (includeMotionArtwork) "&include=motionArtwork" else "",
        ) ?: return emptyList()
        return json.objOrNull("results")?.objOrNull(type)?.arrOrNull("items")
            ?.toCatalogItems(artworkSize)
            .orEmpty()
    }

    private suspend fun getJson(url: String): JSONObject? =
        when (val response = http.getJson(url.withCurrentStorefront())) {
            is HttpOutcome.Success -> response.value
            is HttpOutcome.Failure -> null
        }

    private fun String.withCurrentStorefront(): String {
        val queryStart = indexOf('?')
        val path = if (queryStart < 0) this else substring(0, queryStart)
        val query = if (queryStart < 0) emptyList() else substring(queryStart + 1).split('&')
        val otherParameters = query.filterNot { it.substringBefore('=') == "storefront" }
        return buildString {
            append(path)
            append('?')
            if (otherParameters.isNotEmpty()) {
                append(otherParameters.joinToString("&"))
                append('&')
            }
            append("storefront=")
            append(currentStorefront())
        }
    }

    private fun org.json.JSONArray.toCatalogItems(artworkSize: Int): List<LyricspornCatalogItem> =
        buildList {
            for (index in 0 until length()) {
                objAtOrNull(index)?.toCatalogItem(artworkSize)?.let(::add)
            }
        }

    private fun JSONObject.toCatalogItem(artworkSize: Int): LyricspornCatalogItem? {
        val id = stringOrNull("id") ?: return null
        val name = stringOrNull("name") ?: return null
        val artworkTemplate = objOrNull("artwork")?.stringOrNull("url")
        val artworkUrl = artworkTemplate?.let {
            it.replace("{w}", artworkSize.toString()).replace("{h}", artworkSize.toString())
        }
        return LyricspornCatalogItem(
            id = id,
            type = stringOrNull("type").orEmpty(),
            name = name,
            artistName = stringOrNull("artistName"),
            albumName = stringOrNull("albumName")
                ?: stringOrNull("album")
                ?: objOrNull("albumResource")?.stringOrNull("name"),
            artworkUrl = artworkUrl,
            motionArtwork = objOrNull("motionArtwork")?.toMotionArtwork(),
            durationMs = optLong("durationMs").takeIf { has("durationMs") && !isNull("durationMs") },
            isrc = stringOrNull("isrc"),
        )
    }

    private fun JSONObject.toMotionArtwork(): LyricspornMotionArtwork? {
        val variants = objOrNull("variants") ?: return null
        val variant = variants.objOrNull("default")
            ?: variants.objOrNull("square")
            ?: variants.objOrNull("portrait")
            ?: return null
        val url = variant.stringOrNull("url")?.takeIf(String::isNotBlank) ?: return null
        return LyricspornMotionArtwork(
            url = url,
            format = variant.stringOrNull("format")?.lowercase(Locale.ROOT),
        )
    }

    private fun JSONObject.toLyricspornAlbum(): LyricspornAlbum? {
        val id = stringOrNull("id")?.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
            ?: return null
        val name = stringOrNull("name") ?: return null
        if (stringOrNull("type") != "album") return null

        val collections = objOrNull("collections")
        val tracks = collections?.objOrNull("tracks")?.arrOrNull("items")
            ?.toAlbumTracks()
            .orEmpty()
        val otherVersions = collections?.objOrNull("otherVersions")?.arrOrNull("items")
            ?.toAlbumVersions()
            .orEmpty()
        val notes = objOrNull("editorialNotes")
        val recordLabelsColl = collections?.objOrNull("recordLabels")
            ?: collections?.objOrNull("record-labels")
            ?: objOrNull("relationships")?.objOrNull("recordLabels")
            ?: objOrNull("relationships")?.objOrNull("record-labels")
        val firstLabelItem = recordLabelsColl?.arrOrNull("items")?.objAtOrNull(0)
            ?: recordLabelsColl?.arrOrNull("data")?.objAtOrNull(0)
        val recordLabelId = firstLabelItem?.stringOrNull("id")
            ?: firstLabelItem?.objOrNull("attributes")?.stringOrNull("id")
        val recordLabelName = stringOrNull("recordLabel")
            ?: firstLabelItem?.stringOrNull("name")
            ?: firstLabelItem?.objOrNull("attributes")?.stringOrNull("name")

        return LyricspornAlbum(
            id = id,
            name = name,
            artistName = stringOrNull("artistName"),
            artistUrl = stringOrNull("artistUrl"),
            url = stringOrNull("url"),
            artworkUrl = objOrNull("artwork")?.stringOrNull("url")?.albumArtworkUrl(1200),
            genres = arrOrNull("genres")?.stringValues().orEmpty(),
            releaseDate = stringOrNull("releaseDate"),
            trackCount = intOrNull("trackCount"),
            contentRating = stringOrNull("contentRating"),
            copyright = stringOrNull("copyright"),
            recordLabel = recordLabelName,
            recordLabelId = recordLabelId,
            editorialNotes = preferredAlbumEditorialNotes(
                standard = notes?.stringOrNull("standard"),
                short = notes?.stringOrNull("short"),
            ),
            tracks = tracks,
            otherVersions = otherVersions,
        )
    }

    private fun JSONObject.toLyricspornArtist(artworkSize: Int): LyricspornArtist? {
        val id = stringOrNull("id")?.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
            ?: return null
        val name = stringOrNull("name") ?: return null
        if (stringOrNull("type") != "artist") return null

        val collections = objOrNull("collections")
        val topSongs = collections?.objOrNull("topSongs")?.arrOrNull("items")
            ?.toCatalogItems(artworkSize)
            .orEmpty()
            .filter { it.type == "song" && it.id.isNumericAppleId() }

        val latestRelease = collections?.objOrNull("latestRelease")?.arrOrNull("items")
            ?.toCatalogItems(artworkSize)
            ?.firstOrNull { it.type == "album" && it.id.matches(APPLE_CATALOG_ID_PATTERN) }

        val fullAlbums = collections?.objOrNull("fullAlbums")?.arrOrNull("items")
            ?.toCatalogItems(artworkSize)
            .orEmpty()
            .filter { it.type == "album" && it.id.matches(APPLE_CATALOG_ID_PATTERN) }

        val singles = collections?.objOrNull("singles")?.arrOrNull("items")
            ?.toCatalogItems(artworkSize)
            .orEmpty()
            .filter { it.type == "album" && it.id.matches(APPLE_CATALOG_ID_PATTERN) }

        val similarArtists = collections?.objOrNull("similarArtists")?.arrOrNull("items")
            ?.toCatalogItems(artworkSize)
            .orEmpty()
            .filter { it.type == "artist" && it.id.matches(APPLE_CATALOG_ID_PATTERN) }

        val notes = objOrNull("editorialNotes")

        return LyricspornArtist(
            id = id,
            name = name,
            url = stringOrNull("url"),
            artworkUrl = objOrNull("artwork")?.stringOrNull("url")?.albumArtworkUrl(artworkSize),
            genres = arrOrNull("genres")?.stringValues().orEmpty(),
            editorialNotes = preferredAlbumEditorialNotes(
                standard = notes?.stringOrNull("standard"),
                short = notes?.stringOrNull("short"),
            ),
            topSongs = topSongs,
            latestRelease = latestRelease,
            fullAlbums = fullAlbums,
            singles = singles,
            similarArtists = similarArtists,
        )
    }

    private fun JSONObject.toLyricspornRecordLabel(artworkSize: Int): LyricspornRecordLabel? {
        val id = stringOrNull("id")?.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
            ?: return null
        val name = stringOrNull("name")
            ?: objOrNull("attributes")?.stringOrNull("name")
            ?: return null

        val url = stringOrNull("url")
            ?: objOrNull("attributes")?.stringOrNull("url")

        val description = stringOrNull("description")
            ?: objOrNull("description")?.stringOrNull("standard")
            ?: objOrNull("description")?.stringOrNull("short")
            ?: objOrNull("editorialNotes")?.stringOrNull("standard")
            ?: objOrNull("editorialNotes")?.stringOrNull("short")
            ?: objOrNull("attributes")?.stringOrNull("description")
            ?: objOrNull("attributes")?.objOrNull("description")?.stringOrNull("standard")
            ?: objOrNull("attributes")?.objOrNull("description")?.stringOrNull("short")
            ?: objOrNull("attributes")?.objOrNull("editorialNotes")?.stringOrNull("standard")
            ?: objOrNull("attributes")?.objOrNull("editorialNotes")?.stringOrNull("short")

        val artworkObj = objOrNull("artwork") ?: objOrNull("attributes")?.objOrNull("artwork")
        val artworkUrl = artworkObj?.stringOrNull("url")?.albumArtworkUrl(artworkSize)
        val editorialArtworkUrl = toEditorialArtworkUrl(artworkSize)

        val collections = objOrNull("collections")
            ?: objOrNull("relationships")
            ?: objOrNull("attributes")?.objOrNull("collections")

        fun parseAlbumCollection(key: String): List<HomeAlbum> {
            val collectionObj = collections?.objOrNull(key) ?: return emptyList()
            val itemsArr = collectionObj.arrOrNull("items")
                ?: collectionObj.arrOrNull("data")
                ?: return emptyList()
            return itemsArr.toCatalogItems(artworkSize).map { item ->
                HomeAlbum(
                    id = "apple_${item.id}",
                    title = item.name,
                    artist = item.artistName ?: "",
                    artworkUrl = item.artworkUrl,
                    appleCatalogId = item.id,
                )
            }
        }

        val latestReleases = parseAlbumCollection("latestReleases")
        val topReleases = parseAlbumCollection("topReleases")

        return LyricspornRecordLabel(
            id = id,
            name = name,
            url = url,
            description = description,
            artworkUrl = artworkUrl,
            editorialArtworkUrl = editorialArtworkUrl,
            latestReleases = latestReleases,
            topReleases = topReleases,
        )
    }

    private fun JSONObject.toEditorialArtworkUrl(artworkSize: Int): String? {
        val editorial = objOrNull("editorialArtwork")
            ?: objOrNull("attributes")?.objOrNull("editorialArtwork")
            ?: return null
        editorial.stringOrNull("url")?.albumArtworkUrl(artworkSize)?.let { return it }
        val preferredKeys =
            listOf("banner", "storeFlowcase", "header", "static", "default", "brandLogo")
        for (key in preferredKeys) {
            editorial.objOrNull(key)?.stringOrNull("url")?.albumArtworkUrl(artworkSize)
                ?.let { return it }
        }
        val keys = editorial.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            editorial.objOrNull(key)?.stringOrNull("url")?.albumArtworkUrl(artworkSize)
                ?.let { return it }
        }
        return null
    }

    private fun org.json.JSONArray.toAlbumTracks(): List<LyricspornAlbumTrack> = buildList {
        for (index in 0 until length()) {
            val track = objAtOrNull(index) ?: continue
            val id = track.stringOrNull("id")
                ?.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
                ?: continue
            val name = track.stringOrNull("name") ?: continue
            add(
                LyricspornAlbumTrack(
                    id = id,
                    name = name,
                    artistName = track.stringOrNull("artistName"),
                    albumName = track.stringOrNull("albumName"),
                    artworkUrl = track.objOrNull("artwork")?.stringOrNull("url")
                        ?.albumArtworkUrl(300),
                    durationMs = track.longOrNull("durationMs"),
                    isrc = track.stringOrNull("isrc"),
                    contentRating = track.stringOrNull("contentRating"),
                    url = track.stringOrNull("url"),
                ),
            )
        }
    }

    private fun org.json.JSONArray.toAlbumVersions(): List<LyricspornAlbumVersion> = buildList {
        for (index in 0 until length()) {
            val version = objAtOrNull(index) ?: continue
            val id = version.stringOrNull("id")
                ?.takeIf { it.matches(APPLE_CATALOG_ID_PATTERN) }
                ?: continue
            val name = version.stringOrNull("name") ?: continue
            add(
                LyricspornAlbumVersion(
                    id = id,
                    name = name,
                    artistName = version.stringOrNull("artistName"),
                    artworkUrl = version.objOrNull("artwork")?.stringOrNull("url")
                        ?.albumArtworkUrl(300),
                    releaseDate = version.stringOrNull("releaseDate"),
                    trackCount = version.intOrNull("trackCount"),
                    contentRating = version.stringOrNull("contentRating"),
                ),
            )
        }
    }

    private fun JSONObject.intOrNull(key: String): Int? =
        optInt(key).takeIf { has(key) && !isNull(key) }

    private fun JSONObject.longOrNull(key: String): Long? =
        optLong(key).takeIf { has(key) && !isNull(key) }

    private fun org.json.JSONArray.stringValues(): List<String> = buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
        }
    }

    private fun String.albumArtworkUrl(size: Int): String =
        replace("{w}", size.toString()).replace("{h}", size.toString())

    private fun String.normalized(): String = lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun String.isNumericAppleId(): Boolean = isNotBlank() && all(Char::isDigit)

    private val APPLE_CATALOG_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")

    internal fun currentStorefront(): String = Locale.getDefault().country
        .takeIf { country ->
            country.length == 2 && country.all { character ->
                character in 'A'..'Z' || character in 'a'..'z'
            }
        }
        ?.lowercase(Locale.ROOT)
        ?: DEFAULT_STOREFRONT

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
