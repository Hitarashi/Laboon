package org.shilpo.laboon.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object BackendArtworkResolver {
    private val cache = ConcurrentHashMap<String, String>()

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

    fun executeGet(urlStr: String, token: String? = null): String? {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/json")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", formatBearerToken(token))
                }
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryBackendAsset(
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
        val body = executeGet(endpoint, token) ?: return null
        return try {
            val json = JSONObject(body)
            val url = json.optString("url").trim()
            if (url.isNotEmpty()) url else null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryBackendSearch(
        serverUrl: String,
        token: String,
        query: String,
    ): String? {
        val cleanServerUrl = sanitizeServerUrl(serverUrl)
        val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val endpoint = "$cleanServerUrl/api/v1/search?q=$encodedQuery&limit=1"
        val body = executeGet(endpoint, token) ?: return null
        return try {
            val json = JSONObject(body)
            val liveArr = json.optJSONArray("live")
            val liveUrl = liveArr?.optJSONObject(0)?.optString("artwork_url")?.trim()
            if (!liveUrl.isNullOrEmpty()) return liveUrl

            val canonicalArr = json.optJSONArray("canonical")
            val canonicalUrl = canonicalArr?.optJSONObject(0)?.optString("artwork_url")?.trim()
            if (!canonicalUrl.isNullOrEmpty()) return canonicalUrl

            val cachedArr = json.optJSONArray("cached")
            val cachedUrl = cachedArr?.optJSONObject(0)?.optString("artwork_url")?.trim()
            if (!cachedUrl.isNullOrEmpty()) return cachedUrl

            null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryBackendArtistArtwork(
        serverUrl: String,
        token: String,
        artist: String,
        size: Int = 600,
    ): String? {
        val cleanServerUrl = sanitizeServerUrl(serverUrl)
        val encoded = URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8.name())
        val endpoint = "$cleanServerUrl/api/v1/assets/artists/artwork?name=$encoded&size=$size"
        val body = executeGet(endpoint, token) ?: return null
        return try {
            val json = JSONObject(body)
            val url = json.optString("url").trim()
            if (url.isNotEmpty()) url else null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDeezerArtist(artist: String): String? {
        val encoded = URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8.name())
        val endpoint = "https://api.deezer.com/search/artist?q=$encoded&limit=5"
        val body = executeGet(endpoint) ?: return null
        return try {
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return null
            val target = artist.trim().lowercase()
            val targetNorm = target.replace(Regex("[^a-z0-9]"), "")
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val resName = item.optString("name").trim().lowercase()
                val resNorm = resName.replace(Regex("[^a-z0-9]"), "")
                if (resName == target || (targetNorm.isNotEmpty() && targetNorm == resNorm)) {
                    val xl = item.optString("picture_xl").trim()
                    val big = item.optString("picture_big").trim()
                    val medium = item.optString("picture_medium").trim()
                    val chosen = listOf(
                        xl,
                        big,
                        medium
                    ).firstOrNull { it.isNotEmpty() && !it.contains("/images/artist//") }
                    if (chosen != null) return chosen
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryItunesSearch(
        query: String,
        entity: String,
        expectedTitle: String,
        expectedArtist: String,
    ): String? {
        val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val endpoint = "https://itunes.apple.com/search?term=$encodedQuery&entity=$entity&limit=5"
        val body = executeGet(endpoint) ?: return null
        return try {
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null
            val expArtistNorm = expectedArtist.lowercase().replace(Regex("[^a-z0-9]"), "")
            val expTitleNorm = expectedTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val resArtist =
                    item.optString("artistName").lowercase().replace(Regex("[^a-z0-9]"), "")
                val resTitle =
                    item.optString(if (entity == "album") "collectionName" else "trackName")
                        .lowercase().replace(Regex("[^a-z0-9]"), "")
                val artistMatches =
                    expArtistNorm.isEmpty() || resArtist.contains(expArtistNorm) || expArtistNorm.contains(
                        resArtist
                    )
                val titleMatches =
                    expTitleNorm.isEmpty() || resTitle.contains(expTitleNorm) || expTitleNorm.contains(
                        resTitle
                    )
                if (artistMatches && titleMatches) {
                    val rawUrl = item.optString("artworkUrl100").trim()
                    if (rawUrl.isNotEmpty()) {
                        return rawUrl.replace("100x100bb", "600x600bb")
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
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
}
