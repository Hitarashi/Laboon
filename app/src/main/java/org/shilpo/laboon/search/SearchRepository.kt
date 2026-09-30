package org.shilpo.laboon.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.BackendArtworkResolver
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.net.HttpError
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.fold
import org.shilpo.laboon.net.objAtOrNull
import org.shilpo.laboon.net.stringOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PlaybackResolution(
    val streamUrl: String,
    val codec: String? = null,
    val backendTrackId: Int? = null,
)

interface SearchRepository {
    suspend fun search(query: String): List<HomeTrack>
    suspend fun resolvePlaybackUrl(track: HomeTrack): String?
    suspend fun resolvePlayback(track: HomeTrack): PlaybackResolution?


    suspend fun resolvePlaybackBatch(tracks: List<HomeTrack>): List<HomeTrack>
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
        resolvePlayback(track)?.streamUrl

    override suspend fun resolvePlaybackBatch(tracks: List<HomeTrack>): List<HomeTrack> {
        if (tracks.isEmpty()) return emptyList()
        return coroutineScope {
            tracks
                .map { track ->
                    async {

                        if (!track.streamUrl.isNullOrBlank()) {
                            return@async track
                        }
                        val resolution = resolvePlayback(track) ?: return@async null
                        track.copy(
                            streamUrl = resolution.streamUrl,
                            codec = resolution.codec ?: track.codec,
                            backendTrackId = resolution.backendTrackId ?: track.backendTrackId,
                        )
                    }
                }.awaitAll()
                .filterNotNull()
        }
    }

    private fun codecScore(codec: String?): Int {
        val c = codec?.lowercase()?.trim() ?: return 0
        return when {
            c == "ec-3" || c == "ec3" || c == "atmos" || c.contains("dolby") || c == "eac3" -> 3
            c == "lossless" || c == "alac" || c == "flac" -> 2
            c == "aac" || c.contains("mp4a") -> 1
            else -> 0
        }
    }

    override suspend fun resolvePlayback(track: HomeTrack): PlaybackResolution? =
        withContext(Dispatchers.IO) {
            val session = sessionStore.getSession() ?: return@withContext null
            val serverUrl = session.serverUrl.trim().removeSuffix("/")
            val token = session.token.trim()
            if (serverUrl.isEmpty() || token.isEmpty()) return@withContext null

            var backendId = track.backendTrackId
            var foundCodec = track.codec
            if (backendId == null || backendId <= 0) {
                val encoded = URLEncoder.encode(
                    "${track.title} ${track.artist}",
                    StandardCharsets.UTF_8.name()
                )
                val searchEndpoint = "$serverUrl/api/v1/search?q=$encoded&limit=10"
                fetchJson(searchEndpoint, token)?.let { json ->
                    val cachedArr = json.arrOrNull("cached")
                    if (cachedArr != null && cachedArr.length() > 0) {
                        var chosenId = 0
                        var chosenCodec: String? = null
                        var bestMatchScore = -1
                        for (i in 0 until cachedArr.length()) {
                            val item = cachedArr.objAtOrNull(i) ?: continue
                            val id = item.optInt("id")
                            val c = item.stringOrNull("codec")
                            val itemTitle = item.optString("title").trim()
                            val itemArtist = item.optString("artist").trim()
                            if (id > 0) {
                                val score = codecScore(c)
                                val titleMatches =
                                    itemTitle.equals(track.title, ignoreCase = true) ||
                                            track.title.contains(
                                                itemTitle,
                                                ignoreCase = true
                                            ) ||
                                            itemTitle.contains(track.title, ignoreCase = true)
                                val artistMatches =
                                    itemArtist.equals(track.artist, ignoreCase = true) ||
                                            track.artist.contains(
                                                itemArtist,
                                                ignoreCase = true
                                            ) ||
                                            itemArtist.contains(track.artist, ignoreCase = true)
                                val matchBonus =
                                    (if (titleMatches) 10 else 0) + (if (artistMatches) 10 else 0)
                                val totalScore = score + matchBonus
                                if (totalScore > bestMatchScore) {
                                    bestMatchScore = totalScore
                                    chosenId = id
                                    chosenCodec = c
                                }
                            }
                        }
                        if (chosenId > 0 && (backendId == null || backendId <= 0 || codecScore(
                                chosenCodec
                            ) > codecScore(foundCodec))
                        ) {
                            backendId = chosenId
                            if (chosenCodec != null) {
                                foundCodec = chosenCodec
                            }
                        }
                    }
                }
            }

            if (backendId != null && backendId > 0) {
                val playbackEndpoint = "$serverUrl/api/v1/tracks/$backendId/playback"
                fetchJson(playbackEndpoint, token)?.let { json ->
                    val streamPath = json.optString("stream_url").trim()
                    val codec = json.stringOrNull("codec") ?: foundCodec
                    if (streamPath.isNotEmpty()) {
                        val resolvedUrl =
                            if (streamPath.startsWith("http://") || streamPath.startsWith(
                                    "https://"
                                )
                            ) {
                                streamPath
                            } else {
                                "$serverUrl${if (streamPath.startsWith("/")) "" else "/"}$streamPath"
                            }
                        return@withContext PlaybackResolution(
                            streamUrl = resolvedUrl,
                            codec = codec,
                            backendTrackId = backendId.takeIf { it > 0 },
                        )
                    }
                }
            }

            null
        }

    private suspend fun searchBackend(
        serverUrl: String,
        token: String,
        query: String,
    ): List<HomeTrack> {
        val cleanUrl = sanitizeServerUrl(serverUrl)
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val endpoint = "$cleanUrl/api/v1/search?q=$encoded&limit=25"
        val json = fetchJson(endpoint, token) ?: return emptyList()

        return try {
            val cachedArr = json.arrOrNull("cached")
            val liveArr = json.arrOrNull("live")
            val canonicalArr = json.arrOrNull("canonical")

            data class CachedVariant(
                val id: Int,
                val trackId: String?,
                val provider: String?,
                val codec: String?,
                val artworkUrl: String? = null,
                val album: String? = null,
            )

            val cachedVariantsByTrackId = mutableMapOf<String, MutableList<CachedVariant>>()
            val cachedVariantsByNormKey = mutableMapOf<String, MutableList<CachedVariant>>()
            val cachedArtworkById = mutableMapOf<Int, String>()

            fun cleanProvider(provider: String?): String {
                val p = provider?.trim()?.lowercase() ?: ""
                return when {
                    p.contains("apple") || p.contains("itunes") -> "Apple Music"
                    p.contains("qobuz") -> "Qobuz"
                    else -> "Apple Music"
                }
            }

            fun makeVariantKey(normKey: String, provider: String?, codec: String?): String {
                val p = provider?.trim()?.lowercase() ?: ""
                val c = codec?.trim()?.lowercase() ?: ""
                return "${normKey}_${p}_${c}"
            }

            if (cachedArr != null) {
                for (i in 0 until cachedArr.length()) {
                    val cObj = cachedArr.objAtOrNull(i) ?: continue
                    val id = cObj.optInt("id")
                    val trackId = cObj.stringOrNull("track_id")
                    val title = cObj.optString("title").trim()
                    val artist = cObj.optString("artist").trim()
                    val album = cObj.stringOrNull("album")
                    val codec = cObj.stringOrNull("codec")
                    val provider = cleanProvider(cObj.stringOrNull("provider"))
                    val artUrl = cObj.stringOrNull("artwork_url")
                    if (id > 0) {
                        if (artUrl != null) {
                            cachedArtworkById[id] = artUrl
                        }
                        val variant = CachedVariant(id, trackId, provider, codec, artUrl, album)
                        if (trackId != null) {
                            val list = cachedVariantsByTrackId.getOrPut(trackId) { mutableListOf() }
                            if (list.none { it.id == id }) list.add(variant)
                        }
                        if (title.isNotEmpty() && artist.isNotEmpty()) {
                            val key = BackendArtworkResolver.normalizedKey(title, artist)
                            val list = cachedVariantsByNormKey.getOrPut(key) { mutableListOf() }
                            if (list.none { it.id == id }) list.add(variant)
                        }
                    }
                }
            }

            if (canonicalArr != null) {
                for (i in 0 until canonicalArr.length()) {
                    val canObj = canonicalArr.objAtOrNull(i) ?: continue
                    val title = canObj.optString("title").trim()
                    val artist = canObj.optString("artist").trim()
                    val album = canObj.stringOrNull("album")
                    val artUrl = canObj.stringOrNull("artwork_url")
                    val sources = canObj.arrOrNull("sources")

                    if (artUrl != null && title.isNotEmpty() && artist.isNotEmpty()) {
                        BackendArtworkResolver.putCached(
                            BackendArtworkResolver.normalizedKey(title, artist),
                            artUrl,
                        )
                    }

                    if (sources != null && title.isNotEmpty() && artist.isNotEmpty()) {
                        val normKey = BackendArtworkResolver.normalizedKey(title, artist)
                        for (j in 0 until sources.length()) {
                            val sObj = sources.objAtOrNull(j) ?: continue
                            val id = sObj.optInt("id")
                            val isCached = sObj.optBoolean("is_cached")
                            val sTrackId = sObj.stringOrNull("track_id")
                            val sProvider = cleanProvider(sObj.stringOrNull("provider"))
                            val sCodec = sObj.stringOrNull("codec")
                            if (isCached && id > 0) {
                                if (artUrl != null) {
                                    cachedArtworkById[id] = artUrl
                                }
                                val variant =
                                    CachedVariant(id, sTrackId, sProvider, sCodec, artUrl, album)
                                val list =
                                    cachedVariantsByNormKey.getOrPut(normKey) { mutableListOf() }
                                if (list.none { it.id == id }) list.add(variant)
                                if (sTrackId != null) {
                                    val tList =
                                        cachedVariantsByTrackId.getOrPut(sTrackId) { mutableListOf() }
                                    if (tList.none { it.id == id }) tList.add(variant)
                                }
                            }
                        }
                    }
                }
            }

            val seenVariantKeys = mutableSetOf<String>()
            val orderedResults = mutableListOf<HomeTrack>()

            if (liveArr != null) {
                for (i in 0 until liveArr.length()) {
                    val lObj = liveArr.objAtOrNull(i) ?: continue
                    val trackId =
                        lObj.optString("track_id").ifEmpty { lObj.optString("item_id") }.trim()
                    val title = lObj.optString("title").trim()
                    val artist = lObj.optString("artist").trim()
                    val album = lObj.stringOrNull("album")
                    val artUrl = lObj.stringOrNull("artwork_url")
                    if (title.isEmpty() || artist.isEmpty()) continue

                    val normKey = BackendArtworkResolver.normalizedKey(title, artist)
                    if (artUrl != null) {
                        BackendArtworkResolver.putCached(normKey, artUrl)
                    }

                    val variants =
                        (cachedVariantsByTrackId[trackId] ?: cachedVariantsByNormKey[normKey])
                            ?.distinctBy { it.id }
                            ?.sortedByDescending { codecScore(it.codec) }

                    if (!variants.isNullOrEmpty()) {
                        for (v in variants) {
                            val prov = cleanProvider(v.provider)
                            val vKey = makeVariantKey(normKey, prov, v.codec)
                            if (seenVariantKeys.add(vKey)) {
                                orderedResults.add(
                                    HomeTrack(
                                        id = "peerless_${v.id}",
                                        title = title,
                                        artist = artist,
                                        album = album ?: v.album,
                                        artworkUrl = artUrl ?: v.artworkUrl
                                        ?: cachedArtworkById[v.id]
                                        ?: BackendArtworkResolver.getCached(normKey),
                                        source = prov,
                                        backendTrackId = v.id,
                                        isCached = true,
                                        codec = v.codec,
                                    )
                                )
                            }
                        }
                    } else {
                        val vKey = makeVariantKey(normKey, "Apple Music", null)
                        if (seenVariantKeys.add(vKey)) {
                            orderedResults.add(
                                HomeTrack(
                                    id = "peerless_live_$trackId",
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    artworkUrl = artUrl
                                        ?: BackendArtworkResolver.getCached(normKey),
                                    source = "Apple Music",
                                    backendTrackId = null,
                                    isCached = false,
                                    codec = null,
                                )
                            )
                        }
                    }
                }
            }

            if (cachedArr != null) {
                val cachedObjects = mutableListOf<JSONObject>()
                for (i in 0 until cachedArr.length()) {
                    cachedArr.objAtOrNull(i)?.let { cachedObjects.add(it) }
                }
                cachedObjects.sortByDescending { codecScore(it.optString("codec")) }
                for (cObj in cachedObjects) {
                    val id = cObj.optInt("id")
                    val title = cObj.optString("title").trim()
                    val artist = cObj.optString("artist").trim()
                    val album = cObj.stringOrNull("album")
                    val codec = cObj.stringOrNull("codec")
                    val provider = cleanProvider(cObj.stringOrNull("provider"))
                    if (id <= 0 || title.isEmpty() || artist.isEmpty()) continue

                    val normKey = BackendArtworkResolver.normalizedKey(title, artist)
                    val vKey = makeVariantKey(normKey, provider, codec)
                    if (seenVariantKeys.add(vKey)) {
                        val artUrl =
                            cachedArtworkById[id] ?: BackendArtworkResolver.getCached(normKey)
                        orderedResults.add(
                            HomeTrack(
                                id = "peerless_$id",
                                title = title,
                                artist = artist,
                                album = album,
                                artworkUrl = artUrl,
                                source = provider,
                                backendTrackId = id,
                                isCached = true,
                                codec = codec,
                            )
                        )
                    }
                }
            }

            orderedResults
        } catch (e: Exception) {
            logWarning("Search response could not be read from $serverUrl: ${e.message}")
            emptyList()
        }
    }


    private suspend fun fetchJson(url: String, token: String): JSONObject? =
        http.getJson(
            url,
            mapOf(
                "Authorization" to formatBearerToken(token),
                "Accept" to "application/json",
            ),
        ).fold(
            onSuccess = { it },
            onFailure = { error ->
                logWarning("Search request failed (${describe(error)}) for $url: ${error.message}")
                null
            },
        )


    private fun describe(error: HttpError): String = when {
        error.kind == HttpErrorKind.STATUS && error.message.contains(
            "rate limited",
            ignoreCase = true
        ) ->
            "rate limited"

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

    private companion object {
        private val http = HttpJsonClient(
            connectTimeoutMs = REQUEST_TIMEOUT_MS,
            readTimeoutMs = REQUEST_TIMEOUT_MS,
        )
        private const val REQUEST_TIMEOUT_MS = 6_000L
        private const val TAG = "Search"
    }
}
