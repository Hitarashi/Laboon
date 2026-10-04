package org.shilpo.laboon.lyricsporn

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
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

data class LyricspornCatalogMatch(
    val item: LyricspornCatalogItem,
    val isExactIdentity: Boolean,
)

data class LyricspornMotionArtwork(
    val url: String,
    val format: String? = null,
)

object LyricspornClient {
    private const val MAX_ARTWORK_LOOKUPS = 3
    private const val DEFAULT_STOREFRONT = "us"

    private val http = HttpJsonClient()
    private val catalogItemCache = ConcurrentHashMap<String, LyricspornCatalogMatch>()
    private val motionArtworkCache = ConcurrentHashMap<String, LyricspornMotionArtwork>()
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

    suspend fun searchSongs(
        apiBaseUrl: String?,
        term: String,
        limit: Int = 25,
    ): List<LyricspornCatalogItem> {
        if (term.isBlank()) return emptyList()
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return emptyList()
        val query = encode(term.trim())
        val json = getJson(
            "$baseUrl/catalog/search?term=$query&types=songs&limit=${limit.coerceIn(1, 25)}" +
                    "&artworkSize=300",
        ) ?: return emptyList()
        return json.objOrNull("results")?.objOrNull("songs")?.arrOrNull("items")
            ?.toCatalogItems(300)
            .orEmpty()
            .filter { it.type == "song" && it.id.isNumericAppleId() }
    }

    suspend fun getTrackLyrics(apiBaseUrl: String?, appleTrackId: String): JSONObject? {
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return null
        val trackId = appleTrackId.takeIf { it.isNumericAppleId() } ?: return null
        return getJson("$baseUrl/tracks/$trackId?include=lyrics&formats=json")
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

    suspend fun searchTopSongSuggestions(
        apiBaseUrl: String?,
        term: String,
        limit: Int = 5,
    ): List<LyricspornCatalogItem> {
        if (term.isBlank()) return emptyList()
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return emptyList()
        val query = encode(term.trim())
        val json = getJson(
            "$baseUrl/catalog/search/suggestions?term=$query&kinds=topResults&types=songs" +
                    "&limit=${limit.coerceIn(1, 10)}&artworkSize=300",
        ) ?: return emptyList()
        val suggestions = json.arrOrNull("suggestions") ?: return emptyList()
        return buildList {
            for (index in 0 until suggestions.length()) {
                val suggestion = suggestions.objAtOrNull(index) ?: continue
                if (suggestion.optString("kind") != "topResults") continue
                val item = suggestion.objOrNull("content")?.toCatalogItem(300) ?: continue
                if (item.type == "song" && item.id.isNumericAppleId()) add(item)
            }
        }.distinctBy(LyricspornCatalogItem::id)
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
                getJson(
                    "$baseUrl/tracks/$trackId?include=motionArtwork"
                )
                    ?.objOrNull("track")
                    ?.objOrNull("motionArtwork")
                    ?.toMotionArtwork()
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

    suspend fun resolveAlbumArtwork(apiBaseUrl: String?, title: String, artist: String): String? =
        resolveArtwork(apiBaseUrl = apiBaseUrl, type = "albums", title = title, artist = artist)

    suspend fun resolveArtistArtwork(apiBaseUrl: String?, artist: String): String? =
        resolveArtwork(apiBaseUrl = apiBaseUrl, type = "artists", title = artist, artist = null)

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
            albumName = stringOrNull("albumName"),
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

    private fun String.normalized(): String = lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun String.isNumericAppleId(): Boolean = isNotBlank() && all(Char::isDigit)

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
