package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

data class YouTubePlaylistTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val videoId: String? = null,
)

data class PublicYouTubePlaylist(
    val title: String,
    val tracks: List<YouTubePlaylistTrack>,
    val isTruncated: Boolean,
)

object YouTubePlaylistApi {
    private const val CLIENT_VERSION = "1.20240923.01.00"
    const val MAX_TRACKS = 200
    private const val MAX_PAGES = 20
    private val playlistIdPattern = Regex("[A-Za-z0-9_-]{8,}")

    fun playlistIdFromUrl(value: String): String? {
        val raw = value.trim().takeIf(String::isNotEmpty) ?: return null
        val withScheme = if (raw.contains("://")) raw else "https://$raw"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in SUPPORTED_HOSTS) return null
        val query = uri.rawQuery ?: return null
        val playlistId = query.split('&').firstNotNullOfOrNull { pair ->
            val name = pair.substringBefore('=').urlDecoded()
            if (name != "list") return@firstNotNullOfOrNull null
            pair.substringAfter('=', "").urlDecoded().takeIf(String::isNotBlank)
        } ?: return null
        return playlistId.takeIf(playlistIdPattern::matches)
    }

    fun browseBody(playlistId: String): String = JSONObject()
        .put("context", clientContext())
        .put("browseId", if (playlistId.startsWith("VL")) playlistId else "VL$playlistId")
        .toString()

    fun continuationBody(token: String): String = JSONObject()
        .put("context", clientContext())
        .put("continuation", token)
        .toString()

    fun durationsAreCompatible(sourceDurationMs: Long?, catalogDurationMs: Long?): Boolean =
        sourceDurationMs == null || catalogDurationMs == null ||
                abs(sourceDurationMs - catalogDurationMs) <= MAX_DURATION_DIFFERENCE_MS

    fun parsePlaylistTitle(root: JSONObject): String? {
        val headerKeys = listOf(
            "musicDetailHeaderRenderer",
            "musicEditablePlaylistDetailHeaderRenderer",
            "musicResponsiveHeaderRenderer",
        )
        return headerKeys.firstNotNullOfOrNull { key ->
            findObjects(root, key).firstNotNullOfOrNull { header ->
                readText(header.optJSONObject("title"))?.takeIf(String::isNotBlank)
            }
        }
    }

    fun parsePlaylistTracks(root: JSONObject): List<YouTubePlaylistTrack> =
        findObjects(root, "musicResponsiveListItemRenderer").mapNotNull(::parseTrack)

    fun parseContinuationToken(root: JSONObject): String? =
        findObjects(root, "continuationCommand")
            .firstNotNullOfOrNull { it.optString("token").takeIf(String::isNotBlank) }

    private fun parseTrack(renderer: JSONObject): YouTubePlaylistTrack? {
        val columns = renderer.optJSONArray("flexColumns") ?: return null
        val title = columnText(columns, 0)?.takeIf(String::isNotBlank) ?: return null
        val metadata = columnText(columns, 1).orEmpty()
        val metadataParts = metadata.split('•').map(String::trim).filter(String::isNotEmpty)
        val artist = metadataParts.firstOrNull()?.takeIf(String::isNotBlank) ?: return null
        val album = metadataParts.getOrNull(1)
            ?.takeUnless { it.matches(YEAR_PATTERN) || it.equals("single", ignoreCase = true) || it.equals("ep", ignoreCase = true) }
        return YouTubePlaylistTrack(
            title = title,
            artist = artist,
            album = album,
            durationMs = findDurationMs(renderer),
            videoId = findString(renderer, "videoId"),
        )
    }

    private fun columnText(columns: JSONArray, index: Int): String? =
        columns.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.let(::readText)

    private fun readText(text: JSONObject?): String? {
        val value = text ?: return null
        value.optString("simpleText").trim().takeIf(String::isNotEmpty)?.let { return it }
        val runs = value.optJSONArray("runs") ?: return null
        return buildString {
            for (index in 0 until runs.length()) {
                val run = runs.optJSONObject(index) ?: continue
                append(run.optString("text"))
            }
        }.trim().takeIf(String::isNotEmpty)
    }

    private fun findDurationMs(root: JSONObject): Long? {
        val textValues = mutableListOf<String>()
        collectTextValues(root, textValues)
        return textValues.firstNotNullOfOrNull(::parseDurationMs)
    }

    private fun parseDurationMs(value: String): Long? {
        if (!DURATION_PATTERN.matches(value)) return null
        val parts = value.split(':').map(String::toLongOrNull)
        if (parts.any { it == null }) return null
        val seconds = when (parts.size) {
            2 -> parts[0]!! * 60 + parts[1]!!
            3 -> parts[0]!! * 3_600 + parts[1]!! * 60 + parts[2]!!
            else -> return null
        }
        return seconds.takeIf { it > 0 }?.times(1_000)
    }

    private fun collectTextValues(value: Any?, destination: MutableList<String>) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    if ((key == "simpleText" || key == "text") && child is String) {
                        destination += child.trim()
                    }
                    collectTextValues(child, destination)
                }
            }

            is JSONArray -> for (index in 0 until value.length()) {
                collectTextValues(value.opt(index), destination)
            }
        }
    }

    private fun findString(root: JSONObject, key: String): String? {
        val keys = root.keys()
        while (keys.hasNext()) {
            val childKey = keys.next()
            val child = root.opt(childKey)
            if (childKey == key && child is String && child.isNotBlank()) return child
            when (child) {
                is JSONObject -> findString(child, key)?.let { return it }
                is JSONArray -> for (index in 0 until child.length()) {
                    val item = child.optJSONObject(index) ?: continue
                    findString(item, key)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findObjects(root: JSONObject, key: String): List<JSONObject> = buildList {
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val childKey = keys.next()
                        val child = value.opt(childKey)
                        if (childKey == key && child is JSONObject) add(child)
                        visit(child)
                    }
                }

                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index))
            }
        }
        visit(root)
    }

    private fun clientContext(): JSONObject = JSONObject().put(
        "client",
        JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", "en")
            .put("gl", "US"),
    )

    private fun String.urlDecoded(): String =
        runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }.getOrDefault(this)

    private val SUPPORTED_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "www.youtu.be",
    )
    private val YEAR_PATTERN = Regex("\\d{4}")
    private val DURATION_PATTERN = Regex("\\d{1,2}:\\d{2}(?::\\d{2})?")
    private const val MAX_DURATION_DIFFERENCE_MS = 5_000L
}

class YouTubePlaylistClient(
    private val http: HttpJsonClient = HttpJsonClient(readTimeoutMs = 20_000),
) {
    suspend fun fetchPublicPlaylist(url: String): PublicYouTubePlaylist? {
        val playlistId = YouTubePlaylistApi.playlistIdFromUrl(url) ?: return null
        var response = request(YouTubePlaylistApi.browseBody(playlistId)) ?: return null
        val title = YouTubePlaylistApi.parsePlaylistTitle(response) ?: "YouTube playlist"
        val tracks = mutableListOf<YouTubePlaylistTrack>()
        tracks += YouTubePlaylistApi.parsePlaylistTracks(response)
        val seenTokens = mutableSetOf<String>()
        var continuation = YouTubePlaylistApi.parseContinuationToken(response)
        var pageCount = 1

        while (continuation != null && tracks.size < YouTubePlaylistApi.MAX_TRACKS && pageCount < MAX_PAGES) {
            if (!seenTokens.add(continuation)) break
            response = request(YouTubePlaylistApi.continuationBody(continuation)) ?: break
            tracks += YouTubePlaylistApi.parsePlaylistTracks(response)
            continuation = YouTubePlaylistApi.parseContinuationToken(response)
            pageCount++
        }
        val truncated = continuation != null
        return PublicYouTubePlaylist(
            title = title,
            tracks = tracks.take(YouTubePlaylistApi.MAX_TRACKS),
            isTruncated = truncated,
        )
    }

    private suspend fun request(body: String): JSONObject? = when (
        val result = http.postJson(
            YOUTUBE_BROWSE_URL,
            body,
            mapOf("Accept" to "application/json"),
        )
    ) {
        is HttpOutcome.Failure -> null
        is HttpOutcome.Success -> runCatching { JSONObject(result.value) }.getOrNull()
    }

    private companion object {
        const val YOUTUBE_BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
        const val MAX_PAGES = 20
    }
}
