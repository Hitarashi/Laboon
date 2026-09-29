package org.shilpo.laboon.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.BackendArtworkResolver
import org.shilpo.laboon.home.HomeTrack
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

interface SearchRepository {
    suspend fun search(query: String): List<HomeTrack>
    suspend fun resolvePlaybackUrl(track: HomeTrack): String?
}

class SearchRepositoryImpl(
    private val sessionStore: SessionStore,
) : SearchRepository {

    override suspend fun search(query: String): List<HomeTrack> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val session = sessionStore.getSession()
        val serverUrl = session?.serverUrl?.trim()?.ifEmpty { null }
        val token = session?.token?.trim()?.ifEmpty { null }
        if (serverUrl == null || token == null) return@withContext emptyList()

        coroutineScope {
            val backendResults = searchBackend(serverUrl, token, trimmed)

            val resolved = backendResults.map { track ->
                async {
                    if (track.artworkUrl.isNullOrBlank()) {
                        val resolvedArt = BackendArtworkResolver.resolveTrackArtwork(
                            serverUrl = serverUrl,
                            token = token,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                        )
                        if (!resolvedArt.isNullOrBlank()) {
                            track.copy(artworkUrl = resolvedArt)
                        } else {
                            track
                        }
                    } else {
                        track
                    }
                }
            }.awaitAll()

            resolved
        }
    }

    override suspend fun resolvePlaybackUrl(track: HomeTrack): String? =
        withContext(Dispatchers.IO) {
            val session = sessionStore.getSession() ?: return@withContext null
            val serverUrl = session.serverUrl.trim().removeSuffix("/")
            val token = session.token.trim()
            if (serverUrl.isEmpty() || token.isEmpty()) return@withContext null

            var backendId = track.backendTrackId
            if (backendId == null || backendId <= 0) {
                val encoded = URLEncoder.encode(
                    "${track.title} ${track.artist}",
                    StandardCharsets.UTF_8.name()
                )
                val searchEndpoint = "$serverUrl/api/v1/search?q=$encoded&limit=5"
                val body = executeGet(
                    searchEndpoint,
                    headers = mapOf(
                        "Authorization" to formatBearerToken(token),
                        "Accept" to "application/json",
                    ),
                )
                if (body != null) {
                    try {
                        val json = JSONObject(body)
                        val cachedArr = json.optJSONArray("cached")
                        if (cachedArr != null && cachedArr.length() > 0) {
                            val first = cachedArr.optJSONObject(0)
                            val id = first?.optInt("id") ?: 0
                            if (id > 0) {
                                backendId = id
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            if (backendId != null && backendId > 0) {
                val playbackEndpoint = "$serverUrl/api/v1/tracks/$backendId/playback"
                val body = executeGet(
                    playbackEndpoint,
                    headers = mapOf(
                        "Authorization" to formatBearerToken(token),
                        "Accept" to "application/json",
                    ),
                )
                if (body != null) {
                    try {
                        val json = JSONObject(body)
                        val streamPath = json.optString("stream_url").trim()
                        if (streamPath.isNotEmpty()) {
                            return@withContext if (streamPath.startsWith("http://") || streamPath.startsWith(
                                    "https://"
                                )
                            ) {
                                streamPath
                            } else {
                                "$serverUrl${if (streamPath.startsWith("/")) "" else "/"}$streamPath"
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            null
        }

    private fun searchBackend(
        serverUrl: String,
        token: String,
        query: String,
    ): List<HomeTrack> {
        val cleanUrl = sanitizeServerUrl(serverUrl)
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val endpoint = "$cleanUrl/api/v1/search?q=$encoded&limit=25"
        val body = executeGet(
            endpoint,
            headers = mapOf(
                "Authorization" to formatBearerToken(token),
                "Accept" to "application/json",
            ),
        ) ?: return emptyList()

        return try {
            val json = JSONObject(body)
            val cachedArr = json.optJSONArray("cached")
            val liveArr = json.optJSONArray("live")
            val canonicalArr = json.optJSONArray("canonical")

            val cachedByTrackId = mutableMapOf<String, Int>()
            val cachedByNormalizedKey = mutableMapOf<String, Int>()
            val cachedArtworkById = mutableMapOf<Int, String>()

            if (cachedArr != null) {
                for (i in 0 until cachedArr.length()) {
                    val cObj = cachedArr.optJSONObject(i) ?: continue
                    val id = cObj.optInt("id")
                    val trackId = cObj.optString("track_id").trim()
                    val title = cObj.optString("title").trim()
                    val artist = cObj.optString("artist").trim()
                    if (id > 0) {
                        if (trackId.isNotEmpty()) {
                            cachedByTrackId[trackId] = id
                        }
                        if (title.isNotEmpty() && artist.isNotEmpty()) {
                            val key = BackendArtworkResolver.normalizedKey(title, artist)
                            cachedByNormalizedKey[key] = id
                        }
                    }
                }
            }

            if (canonicalArr != null) {
                for (i in 0 until canonicalArr.length()) {
                    val canObj = canonicalArr.optJSONObject(i) ?: continue
                    val sources = canObj.optJSONArray("sources") ?: continue
                    var cachedId: Int? = null
                    for (j in 0 until sources.length()) {
                        val sObj = sources.optJSONObject(j) ?: continue
                        val id = sObj.optInt("id")
                        val isCached = sObj.optBoolean("is_cached")
                        if (isCached && id > 0) {
                            cachedId = id
                            break
                        }
                    }

                    val title = canObj.optString("title").trim()
                    val artist = canObj.optString("artist").trim()
                    val artUrl = canObj.optString("artwork_url").trim().ifEmpty { null }

                    if (artUrl != null && title.isNotEmpty() && artist.isNotEmpty()) {
                        BackendArtworkResolver.putCached(
                            BackendArtworkResolver.normalizedKey(title, artist),
                            artUrl,
                        )
                    }

                    if (cachedId != null) {
                        for (j in 0 until sources.length()) {
                            val sObj = sources.optJSONObject(j) ?: continue
                            val tId = sObj.optString("track_id").trim()
                            if (tId.isNotEmpty()) {
                                cachedByTrackId[tId] = cachedId
                            }
                        }
                        if (title.isNotEmpty() && artist.isNotEmpty()) {
                            val key = BackendArtworkResolver.normalizedKey(title, artist)
                            cachedByNormalizedKey[key] = cachedId
                        }
                        if (artUrl != null) {
                            cachedArtworkById[cachedId] = artUrl
                        }
                    }
                }
            }

            val seenKeys = mutableSetOf<String>()
            val orderedResults = mutableListOf<HomeTrack>()

            if (liveArr != null) {
                for (i in 0 until liveArr.length()) {
                    val lObj = liveArr.optJSONObject(i) ?: continue
                    val trackId =
                        lObj.optString("track_id").ifEmpty { lObj.optString("item_id") }.trim()
                    val title = lObj.optString("title").trim()
                    val artist = lObj.optString("artist").trim()
                    val album = lObj.optString("album").trim().ifEmpty { null }
                    val artUrl = lObj.optString("artwork_url").trim().ifEmpty { null }
                    if (title.isEmpty() || artist.isEmpty()) continue

                    val normKey = BackendArtworkResolver.normalizedKey(title, artist)
                    if (artUrl != null) {
                        BackendArtworkResolver.putCached(normKey, artUrl)
                    }

                    val matchedCachedId = cachedByTrackId[trackId] ?: cachedByNormalizedKey[normKey]

                    if (seenKeys.add(normKey)) {
                        orderedResults.add(
                            HomeTrack(
                                id = if (matchedCachedId != null) "peerless_$matchedCachedId" else "peerless_live_$trackId",
                                title = title,
                                artist = artist,
                                album = album,
                                artworkUrl = artUrl
                                    ?: (if (matchedCachedId != null) cachedArtworkById[matchedCachedId] else null),
                                source = "Apple Music",
                                backendTrackId = matchedCachedId,
                                isCached = matchedCachedId != null,
                            )
                        )
                    }
                }
            }

            if (cachedArr != null) {
                for (i in 0 until cachedArr.length()) {
                    val cObj = cachedArr.optJSONObject(i) ?: continue
                    val id = cObj.optInt("id")
                    val title = cObj.optString("title").trim()
                    val artist = cObj.optString("artist").trim()
                    val album = cObj.optString("album").trim().ifEmpty { null }
                    if (id <= 0 || title.isEmpty() || artist.isEmpty()) continue

                    val normKey = BackendArtworkResolver.normalizedKey(title, artist)
                    if (seenKeys.add(normKey)) {
                        val artUrl =
                            cachedArtworkById[id] ?: BackendArtworkResolver.getCached(normKey)
                        orderedResults.add(
                            HomeTrack(
                                id = "peerless_$id",
                                title = title,
                                artist = artist,
                                album = album,
                                artworkUrl = artUrl,
                                source = "Peerless",
                                backendTrackId = id,
                                isCached = true,
                            )
                        )
                    }
                }
            }

            orderedResults
        } catch (_: Exception) {
            emptyList()
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

    private fun executeGet(urlStr: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                for ((key, value) in headers) {
                    setRequestProperty(key, value)
                }
            }
            if (connection.responseCode in 200..299) {
                connection.inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
