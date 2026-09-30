package org.shilpo.laboon.home

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.shilpo.laboon.net.HttpError
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.fold
import org.shilpo.laboon.net.objAtOrNull
import org.shilpo.laboon.net.stringOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object BackendArtworkResolver {
    private val cache = ConcurrentHashMap<String, String>()

    private val http = HttpJsonClient(
        connectTimeoutMs = LOOKUP_TIMEOUT_MS,
        readTimeoutMs = LOOKUP_TIMEOUT_MS,
    )

    fun normalizedKey(title: String, artist: String): String =
        "${title.lowercase().trim()}:::${artist.lowercase().trim()}"

    fun normalizedArtistKey(artist: String): String =
        "artist:::${artist.lowercase().trim()}"

    fun getCached(key: String): String? = cache[key]

    fun putCached(key: String, url: String) {
        cache[key] = url
    }

    fun clearCache() {
        cache.clear()
    }

    suspend fun resolveTrackArtwork(
        serverUrl: String? = null,
        token: String? = null,
        title: String,
        artist: String,
        album: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (title.isBlank() && artist.isBlank()) return@withContext null
        val cacheKey = normalizedKey(title, artist)
        cache[cacheKey]?.let { return@withContext it }

        if (!serverUrl.isNullOrBlank() && !token.isNullOrBlank()) {
            val assetUrl = queryBackendAsset(serverUrl, token, title, artist)
            if (!assetUrl.isNullOrBlank()) {
                cache[cacheKey] = assetUrl
                return@withContext assetUrl
            }

            val searchQuery = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
            val searchUrl = queryBackendSearch(serverUrl, token, searchQuery)
            if (!searchUrl.isNullOrBlank()) {
                cache[cacheKey] = searchUrl
                return@withContext searchUrl
            }
        }

        val itunesQuery = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val fallbackUrl = queryItunesSearch(itunesQuery, "song", title, artist)
        if (!fallbackUrl.isNullOrBlank()) {
            cache[cacheKey] = fallbackUrl
            return@withContext fallbackUrl
        }

        null
    }

    suspend fun resolveAlbumArtwork(
        serverUrl: String? = null,
        token: String? = null,
        title: String,
        artist: String,
    ): String? = withContext(Dispatchers.IO) {
        if (title.isBlank() && artist.isBlank()) return@withContext null
        val cacheKey = normalizedKey(title, artist)
        cache[cacheKey]?.let { return@withContext it }

        if (!serverUrl.isNullOrBlank() && !token.isNullOrBlank()) {
            val assetUrl = queryBackendAsset(serverUrl, token, title, artist)
            if (!assetUrl.isNullOrBlank()) {
                cache[cacheKey] = assetUrl
                return@withContext assetUrl
            }

            val searchQuery = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
            val searchUrl = queryBackendSearch(serverUrl, token, searchQuery)
            if (!searchUrl.isNullOrBlank()) {
                cache[cacheKey] = searchUrl
                return@withContext searchUrl
            }
        }

        val itunesQuery = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val fallbackUrl = queryItunesSearch(itunesQuery, "album", title, artist)
        if (!fallbackUrl.isNullOrBlank()) {
            cache[cacheKey] = fallbackUrl
            return@withContext fallbackUrl
        }

        null
    }

    suspend fun resolveArtistArtwork(
        serverUrl: String? = null,
        token: String? = null,
        artist: String,
    ): String? = withContext(Dispatchers.IO) {
        if (artist.isBlank()) return@withContext null
        val cacheKey = normalizedArtistKey(artist)
        val legacyKey = ":::${artist.lowercase().trim()}"
        cache[cacheKey]?.let { return@withContext it }
        cache[legacyKey]?.let { return@withContext it }

        if (!serverUrl.isNullOrBlank() && !token.isNullOrBlank()) {
            val backendUrl = queryBackendArtistArtwork(serverUrl, token, artist.trim(), 600)
            if (!backendUrl.isNullOrBlank()) {
                cache[cacheKey] = backendUrl
                cache[legacyKey] = backendUrl
                return@withContext backendUrl
            }
        }

        val deezerUrl = queryDeezerArtist(artist.trim())
        if (!deezerUrl.isNullOrBlank()) {
            cache[cacheKey] = deezerUrl
            cache[legacyKey] = deezerUrl
            return@withContext deezerUrl
        }

        null
    }


    private suspend fun fetchJson(url: String, token: String?): JSONObject? {
        val headers = buildMap {
            put("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                put("Authorization", formatBearerToken(token))
            }
        }
        return http.getJson(url, headers).fold(
            onSuccess = { it },
            onFailure = { error ->
                logWarning("Artwork lookup failed (${describe(error)}) for $url: ${error.message}")
                null
            },
        )
    }

    private suspend fun queryBackendAsset(
        serverUrl: String,
        token: String,
        title: String,
        artist: String,
    ): String? {
        val cleanServerUrl = sanitizeServerUrl(serverUrl)
        val slug = generateSlug(title, artist)
        val encodedTitle = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8.name())
        val encodedArtist = URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8.name())
        val endpoint =
            "$cleanServerUrl/api/v1/assets/providers/apple/tracks/$slug/artwork?title=$encodedTitle&artist=$encodedArtist&size=600"
        val json = fetchJson(endpoint, token) ?: return null
        return json.stringOrNull("url")
    }

    private suspend fun queryBackendSearch(
        serverUrl: String,
        token: String,
        query: String,
    ): String? {
        val cleanServerUrl = sanitizeServerUrl(serverUrl)
        val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val endpoint = "$cleanServerUrl/api/v1/search?q=$encodedQuery&limit=1"
        val json = fetchJson(endpoint, token) ?: return null

        val liveUrl = json.arrOrNull("live")?.objAtOrNull(0)?.stringOrNull("artwork_url")
        if (liveUrl != null) return liveUrl

        val canonicalUrl = json.arrOrNull("canonical")?.objAtOrNull(0)?.stringOrNull("artwork_url")
        if (canonicalUrl != null) return canonicalUrl

        return json.arrOrNull("cached")?.objAtOrNull(0)?.stringOrNull("artwork_url")
    }

    private suspend fun queryBackendArtistArtwork(
        serverUrl: String,
        token: String,
        artist: String,
        size: Int = 600,
    ): String? {
        val cleanServerUrl = sanitizeServerUrl(serverUrl)
        val encoded = URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8.name())
        val endpoint = "$cleanServerUrl/api/v1/assets/artists/artwork?name=$encoded&size=$size"
        val json = fetchJson(endpoint, token) ?: return null
        return json.stringOrNull("url")
    }

    private suspend fun queryDeezerArtist(artist: String): String? {
        val encoded = URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8.name())
        val endpoint = "https://api.deezer.com/search/artist?q=$encoded&limit=5"
        val json = fetchJson(endpoint, null) ?: return null
        val data = json.arrOrNull("data") ?: return null
        val target = artist.trim().lowercase()
        val targetNorm = target.replace(NON_ALNUM, "")
        for (i in 0 until data.length()) {
            val item = data.objAtOrNull(i) ?: continue
            val resName = item.optString("name").trim().lowercase()
            val resNorm = resName.replace(NON_ALNUM, "")
            if (resName == target || (targetNorm.isNotEmpty() && targetNorm == resNorm)) {
                val chosen = listOf(
                    item.optString("picture_xl").trim(),
                    item.optString("picture_big").trim(),
                    item.optString("picture_medium").trim(),
                ).firstOrNull { it.isNotEmpty() && !it.contains("/images/artist//") }
                if (chosen != null) return chosen
            }
        }
        return null
    }

    private suspend fun queryItunesSearch(
        query: String,
        entity: String,
        expectedTitle: String,
        expectedArtist: String,
    ): String? {
        val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val endpoint = "https://itunes.apple.com/search?term=$encodedQuery&entity=$entity&limit=5"
        val json = fetchJson(endpoint, null) ?: return null
        val results = json.arrOrNull("results") ?: return null
        val expArtistNorm = expectedArtist.lowercase().replace(NON_ALNUM, "")
        val expTitleNorm = expectedTitle.lowercase().replace(NON_ALNUM, "")
        for (i in 0 until results.length()) {
            val item = results.objAtOrNull(i) ?: continue
            val resArtist = item.optString("artistName").lowercase().replace(NON_ALNUM, "")
            val resTitle = item.optString(if (entity == "album") "collectionName" else "trackName")
                .lowercase().replace(NON_ALNUM, "")
            val artistMatches =
                expArtistNorm.isEmpty() || resArtist.contains(expArtistNorm) || expArtistNorm.contains(
                    resArtist
                )
            val titleMatches =
                expTitleNorm.isEmpty() || resTitle.contains(expTitleNorm) || expTitleNorm.contains(
                    resTitle
                )
            if (artistMatches && titleMatches) {
                val rawUrl = item.stringOrNull("artworkUrl100")
                if (rawUrl != null) {
                    return rawUrl.replace("100x100bb", "600x600bb")
                }
            }
        }
        return null
    }


    private fun describe(error: HttpError): String = when {
        error.kind == HttpErrorKind.STATUS &&
                error.message.contains("rate limited", ignoreCase = true) -> "rate limited"

        error.statusCode == 401 -> "unauthorized"
        error.statusCode == 403 -> "forbidden"
        error.kind == HttpErrorKind.TIMEOUT -> "timed out"
        error.kind == HttpErrorKind.NETWORK -> "network unavailable"
        error.kind == HttpErrorKind.MALFORMED -> "malformed body"
        else -> "unexpected error"
    }


    private fun logWarning(message: String) {
        // android.util.Log is a throwing stub under JVM unit tests, so a log line must never be the thing that fails a request.
        runCatching { Log.w(TAG, message) }
    }

    private fun generateSlug(title: String, artist: String): String {
        val raw = "${title.lowercase().trim()}:::${artist.lowercase().trim()}"
        return try {
            val digest =
                MessageDigest.getInstance("MD5").digest(raw.toByteArray(StandardCharsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            raw.hashCode().toUInt().toString(16)
        }
    }

    private fun sanitizeServerUrl(serverUrl: String): String {
        val trimmed = serverUrl.trim().removeSuffix("/")
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    private fun formatBearerToken(token: String): String {
        val trimmed = token.trim()
        return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            trimmed
        } else {
            "Bearer $trimmed"
        }
    }

    private val NON_ALNUM = Regex("[^a-z0-9]")

    private const val TAG = "Artwork"
    private const val LOOKUP_TIMEOUT_MS = 6_000L
}
