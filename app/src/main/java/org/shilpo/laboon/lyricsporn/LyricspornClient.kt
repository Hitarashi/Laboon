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
    val durationMs: Long? = null,
    val isrc: String? = null,
)

object LyricspornClient {
    private const val MAX_ARTWORK_LOOKUPS = 3

    private val http = HttpJsonClient()
    private val artworkCache = ConcurrentHashMap<String, String>()
    private val artworkPermits = Semaphore(MAX_ARTWORK_LOOKUPS)

    fun normalizeApiBaseUrl(value: String?): String? {
        val baseUrl = value?.trim()?.trimEnd('/')?.takeIf(String::isNotEmpty) ?: return null
        return baseUrl.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("http://", ignoreCase = true)
        }
    }

    fun normalizedArtworkKey(title: String, artist: String, apiBaseUrl: String?): String =
        "${normalizeApiBaseUrl(apiBaseUrl).orEmpty()}:${title.normalized()}:${artist.normalized()}"

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
    ): String? {
        if (title.isBlank()) return null
        val baseUrl = normalizeApiBaseUrl(apiBaseUrl) ?: return null
        val normalizedTitle = title.normalized()
        val normalizedArtist = artist.orEmpty().normalized()
        val normalizedAlbum = album.orEmpty().normalized()
        if (normalizedTitle.isBlank()) return null
        val key = "$baseUrl:$type:$normalizedTitle:$normalizedArtist:$normalizedAlbum"
        artworkCache[key]?.let { return it }

        return artworkPermits.withPermit {
            artworkCache[key]?.let { return@withPermit it }
            val query = listOfNotNull(
                title.trim(),
                artist?.trim()?.takeIf(String::isNotEmpty),
                album?.trim()?.takeIf(String::isNotEmpty),
            )
                .joinToString(" ")
            val items = searchCatalogItems(baseUrl, query, type, limit = 5, artworkSize = 300)
            val exact = items.firstOrNull { item ->
                item.name.normalized() == normalizedTitle &&
                        (artist == null || item.artistName.orEmpty()
                            .normalized() == normalizedArtist)
            }
            val selected = exact ?: items.firstOrNull { item ->
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
            selected?.artworkUrl?.also { artworkCache[key] = it }
        }
    }

    private suspend fun searchCatalogItems(
        apiBaseUrl: String,
        term: String,
        type: String,
        limit: Int,
        artworkSize: Int,
    ): List<LyricspornCatalogItem> {
        val json = getJson(
            "$apiBaseUrl/catalog/search?term=${encode(term)}&types=$type" +
                    "&limit=${limit.coerceIn(1, 25)}&artworkSize=$artworkSize",
        ) ?: return emptyList()
        return json.objOrNull("results")?.objOrNull(type)?.arrOrNull("items")
            ?.toCatalogItems(artworkSize)
            .orEmpty()
    }

    private suspend fun getJson(url: String): JSONObject? =
        when (val response = http.getJson(url)) {
            is HttpOutcome.Success -> response.value
            is HttpOutcome.Failure -> null
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
            durationMs = optLong("durationMs").takeIf { has("durationMs") && !isNull("durationMs") },
            isrc = stringOrNull("isrc"),
        )
    }

    private fun String.normalized(): String = lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun String.isNumericAppleId(): Boolean = isNotBlank() && all(Char::isDigit)

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
