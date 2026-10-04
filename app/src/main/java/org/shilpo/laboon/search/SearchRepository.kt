package org.shilpo.laboon.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.SessionStore
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

interface SearchRepository {
    suspend fun search(query: String): List<HomeTrack>
    suspend fun enrichAvailability(tracks: List<HomeTrack>): List<HomeTrack>
    suspend fun searchHints(query: String): List<String> = emptyList()
    suspend fun searchSuggestions(query: String): List<HomeTrack> = emptyList()
    suspend fun resolvePlaybackUrl(track: HomeTrack): String?
    suspend fun resolvePlayback(track: HomeTrack): PlaybackResolution?
    suspend fun resolvePlaybackBatch(tracks: List<HomeTrack>): List<HomeTrack>
}

class SearchRepositoryImpl(
    private val sessionStore: SessionStore,
    private val http: HttpJsonClient = HttpJsonClient(),
) : SearchRepository {

    private data class CachedTrackAvailability(
        val variants: List<TrackFormatVariant>,
        val preferredCodec: String?,
        val playbackTrackId: Int?,
    )

    override suspend fun search(query: String): List<HomeTrack> = withContext(Dispatchers.IO) {
        val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
        val items = LyricspornClient.searchSongs(apiBaseUrl, query, limit = MAX_SEARCH_RESULTS)
        withAvailability(items)
    }

    override suspend fun searchHints(query: String): List<String> =
        LyricspornClient.searchHints(
            apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl,
            term = query,
            limit = MAX_HINTS,
        )

    override suspend fun searchSuggestions(query: String): List<HomeTrack> =
        withContext(Dispatchers.IO) {
            val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
            val items = LyricspornClient.searchTopSongSuggestions(
                apiBaseUrl,
                query,
                limit = MAX_TOP_RESULTS,
            )
            withAvailability(items)
        }

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
            lookupAvailableFormats(tracks.mapNotNull(HomeTrack::providerTrackId))
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

    private suspend fun lookupAvailableFormats(
        appleIds: List<String>,
    ): Map<String, CachedTrackAvailability> =
        withContext(Dispatchers.IO) {
            val session = sessionStore.getSession() ?: return@withContext emptyMap()
            val serverUrl = sanitizeServerUrl(session.serverUrl)
            val token = session.token.trim()
            if (serverUrl.isEmpty() || token.isEmpty()) return@withContext emptyMap()

            val validIds = appleIds
                .distinct()
                .filter { id -> id.isNotBlank() && id.all(Char::isDigit) }
            if (validIds.isEmpty()) return@withContext emptyMap()

            val availabilityById = linkedMapOf<String, CachedTrackAvailability>()
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
                } ?: continue

                val tracks = response.arrOrNull("tracks") ?: continue
                for (index in 0 until tracks.length()) {
                    val track = tracks.objAtOrNull(index) ?: continue
                    val appleId = track.stringOrNull("apple_track_id") ?: continue
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
            availabilityById
        }

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
