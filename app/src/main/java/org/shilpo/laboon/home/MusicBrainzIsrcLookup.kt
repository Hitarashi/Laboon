package org.shilpo.laboon.home

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.BuildConfig
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.objAtOrNull
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class MusicBrainzRecording(
    val mbid: String,
    val title: String,
    val artist: String,
)

internal object MusicBrainzIsrcLookup {
    private const val BASE_URL = "https://musicbrainz.org/ws/2/recording/"
    private const val MAX_ISRCS_PER_QUERY = 25
    private const val MIN_REQUEST_INTERVAL_MS = 1_000L
    private val isrcPattern = Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")
    private val mbidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val http = HttpJsonClient(readTimeoutMs = 20_000)
    private val requestMutex = Mutex()
    private var lastRequestAtMs = 0L

    fun searchUrl(isrcs: List<String>): String? {
        val terms = isrcs.mapNotNull(::normalizeIsrc).distinct()
        if (terms.isEmpty()) return null
        val query = terms.joinToString(" OR ") { "isrc:$it" }.let { "($it)" }
        return "$BASE_URL?query=${query.urlEncoded()}&fmt=json&limit=100&inc=isrcs+artist-credits"
    }

    fun parseSearchResponse(response: JSONObject): Map<String, List<MusicBrainzRecording>> = buildMap {
        val recordings = response.arrOrNull("recordings") ?: return@buildMap
        for (index in 0 until recordings.length()) {
            val recording = recordings.objAtOrNull(index) ?: continue
            val mbid = recording.stringOrNull("id")?.takeIf(mbidPattern::matches) ?: continue
            val title = recording.stringOrNull("title")?.takeIf(String::isNotBlank) ?: continue
            val artist = recording.artistCreditName().takeIf(String::isNotBlank) ?: continue
            val isrcs = recording.arrOrNull("isrcs") ?: continue
            val match = MusicBrainzRecording(mbid = mbid, title = title, artist = artist)
            for (isrcIndex in 0 until isrcs.length()) {
                val isrc = normalizeIsrc(isrcs.optString(isrcIndex)) ?: continue
                val existing = get(isrc).orEmpty()
                put(isrc, existing + match)
            }
        }
    }

    fun matchTracks(
        tracks: List<HomeTrack>,
        recordingsByIsrc: Map<String, List<MusicBrainzRecording>>,
    ): Map<Int, String> = buildMap {
        tracks.forEachIndexed { index, track ->
            val isrc = track.isrc?.let(::normalizeIsrc) ?: return@forEachIndexed
            val recordings = recordingsByIsrc[isrc].orEmpty()
            if (recordings.isEmpty()) return@forEachIndexed
            val exact = recordings.firstOrNull { recording ->
                normalizeMetadata(recording.title) == normalizeMetadata(track.title) &&
                    normalizeMetadata(recording.artist) == normalizeMetadata(track.artist)
            }
            put(index, (exact ?: recordings.first()).mbid)
        }
    }

    suspend fun findRecordings(isrcs: List<String>): Map<String, List<MusicBrainzRecording>> {
        val validIsrcs = isrcs.mapNotNull(::normalizeIsrc).distinct()
        if (validIsrcs.isEmpty()) return emptyMap()
        val recordingsByIsrc = mutableMapOf<String, List<MusicBrainzRecording>>()
        for (batch in validIsrcs.chunked(MAX_ISRCS_PER_QUERY)) {
            val url = searchUrl(batch) ?: continue
            val response = requestMutex.withLock {
                val now = System.currentTimeMillis()
                val remaining = MIN_REQUEST_INTERVAL_MS - (now - lastRequestAtMs)
                if (remaining > 0L) delay(remaining)
                lastRequestAtMs = System.currentTimeMillis()
                http.getJson(
                    url,
                    mapOf(
                        "Accept" to "application/json",
                        "User-Agent" to "Laboon/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
                    ),
                )
            }
            if (response is HttpOutcome.Success) {
                parseSearchResponse(response.value).forEach { (isrc, matches) ->
                    recordingsByIsrc[isrc] = recordingsByIsrc[isrc].orEmpty() + matches
                }
            }
        }
        return recordingsByIsrc
    }

    private fun JSONObject.artistCreditName(): String = buildString {
        val artistCredit = arrOrNull("artist-credit") ?: return@buildString
        for (index in 0 until artistCredit.length()) {
            when (val part = artistCredit.opt(index)) {
                is JSONObject -> {
                    append(
                        part.stringOrNull("name")
                            ?: part.objOrNull("artist")?.stringOrNull("name").orEmpty(),
                    )
                    append(part.stringOrNull("joinphrase").orEmpty())
                }

                is String -> append(part)
            }
        }
    }

    private fun normalizeIsrc(value: String): String? = value
        .uppercase(Locale.ROOT)
        .replace("-", "")
        .takeIf(isrcPattern::matches)

    private fun normalizeMetadata(value: String): String = value
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

    private fun String.urlEncoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}
