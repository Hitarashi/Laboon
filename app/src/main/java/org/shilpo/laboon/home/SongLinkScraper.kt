package org.shilpo.laboon.home

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

internal data class SongLinkScrapedTrack(
    val appleTrackId: String,
    val appleUrl: String,
    val sourceDurationMs: Long? = null,
)

internal sealed interface SongLinkPageResult {
    data class AppleMatch(
        val appleTrackId: String,
        val appleUrl: String,
        val sourceDurationMs: Long? = null,
    ) : SongLinkPageResult

    data object NoAppleMatch : SongLinkPageResult
    data object InvalidPage : SongLinkPageResult
}

internal object SongLinkPageParser {
    private val nextDataScript = Regex(
        "<script[^>]*\\bid=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val appleTrackIdPattern = Regex("\\d{1,20}")
    private val supportedYouTubeProviders = setOf("youtube", "youtubeMusic")
    private val appleHosts = setOf("music.apple.com", "geo.music.apple.com", "itunes.apple.com")

    fun parse(html: String, expectedVideoId: String): SongLinkPageResult {
        val script = nextDataScript.find(html)?.groupValues?.getOrNull(1)
            ?: return SongLinkPageResult.InvalidPage
        val root = runCatching { JSONObject(script) }.getOrNull()
            ?: return SongLinkPageResult.InvalidPage
        val pageData = root.objOrNull("props")
            ?.objOrNull("pageProps")
            ?.objOrNull("pageData")
            ?: return SongLinkPageResult.InvalidPage
        val source = pageData.objOrNull("entityData") ?: return SongLinkPageResult.InvalidPage
        val sections = pageData.optJSONArray("sections") ?: return SongLinkPageResult.NoAppleMatch
        val exactPrimarySource = source.stringOrNull("provider") in supportedYouTubeProviders &&
                source.stringOrNull("type") == "song" &&
                source.stringOrNull("id") == expectedVideoId
        if (!exactPrimarySource && !hasExactYouTubeLink(sections, expectedVideoId)) {
            return SongLinkPageResult.InvalidPage
        }
        val durationMs = source.optLong("duration", -1L).takeIf { exactPrimarySource && it > 0L }

        for (sectionIndex in 0 until sections.length()) {
            val links = sections.optJSONObject(sectionIndex)?.optJSONArray("links") ?: continue
            for (linkIndex in 0 until links.length()) {
                val link = links.optJSONObject(linkIndex) ?: continue
                if (link.stringOrNull("platform") != "appleMusic") continue
                val url = link.stringOrNull("url") ?: continue
                if (!isAppleUrl(url)) continue
                val urlTrackId = appleTrackIdFromUrl(url)
                val uniqueIdTrack = appleTrackIdFromUniqueId(link.stringOrNull("uniqueId"))
                if (uniqueIdTrack != null && urlTrackId != null && uniqueIdTrack != urlTrackId) continue
                val appleTrackId = uniqueIdTrack ?: urlTrackId ?: continue
                return SongLinkPageResult.AppleMatch(
                    appleTrackId = appleTrackId,
                    appleUrl = url,
                    sourceDurationMs = durationMs,
                )
            }
        }
        return SongLinkPageResult.NoAppleMatch
    }

    private fun hasExactYouTubeLink(sections: JSONArray, expectedVideoId: String): Boolean {
        for (sectionIndex in 0 until sections.length()) {
            val links = sections.optJSONObject(sectionIndex)?.optJSONArray("links") ?: continue
            for (linkIndex in 0 until links.length()) {
                val link = links.optJSONObject(linkIndex) ?: continue
                if (link.stringOrNull("platform") !in supportedYouTubeProviders) continue
                val uniqueId = link.stringOrNull("uniqueId")?.split('|')
                if (uniqueId?.size == 3 &&
                    uniqueId[0] in supportedYouTubeProviders &&
                    uniqueId[1] == "song" &&
                    uniqueId[2] == expectedVideoId
                ) {
                    return true
                }
                if (youtubeVideoIdFromUrl(link.stringOrNull("url")) == expectedVideoId) return true
            }
        }
        return false
    }

    private fun appleTrackIdFromUniqueId(uniqueId: String?): String? {
        val parts = uniqueId?.split('|') ?: return null
        if (parts.size != 3 || parts[0] !in setOf("itunes", "appleMusic") || parts[1] != "song") {
            return null
        }
        return parts[2].takeIf(appleTrackIdPattern::matches)
    }

    private fun appleTrackIdFromUrl(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val queryTrackId = uri.rawQuery.orEmpty()
            .split('&')
            .firstNotNullOfOrNull { parameter ->
                val name = parameter.substringBefore('=').urlDecoded()
                if (name != "i") return@firstNotNullOfOrNull null
                parameter.substringAfter('=', "").urlDecoded().takeIf(appleTrackIdPattern::matches)
            }
        if (queryTrackId != null) return queryTrackId

        val path = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        if (path.any { it.equals("song", ignoreCase = true) }) {
            return path.lastOrNull()?.removePrefix("id")?.takeIf(appleTrackIdPattern::matches)
        }
        return null
    }

    private fun youtubeVideoIdFromUrl(value: String?): String? {
        val uri = value?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        if (uri.scheme !in setOf("https", "http") || uri.host?.lowercase() !in youtubeHosts) return null
        if (uri.host?.lowercase() == "youtu.be" || uri.host?.lowercase() == "www.youtu.be") {
            return uri.path.orEmpty().trim('/').takeIf(YOUTUBE_VIDEO_ID_PATTERN::matches)
        }
        return uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull { parameter ->
            val name = parameter.substringBefore('=').urlDecoded()
            if (name != "v") return@firstNotNullOfOrNull null
            parameter.substringAfter('=', "").urlDecoded().takeIf(YOUTUBE_VIDEO_ID_PATTERN::matches)
        }
    }

    private fun isAppleUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme in setOf("https", "http") && uri.host?.lowercase() in appleHosts
    }

    private fun String.urlDecoded(): String =
        runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }.getOrDefault(this)

    private val youtubeHosts = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "www.youtu.be",
    )
    private val YOUTUBE_VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")
}

internal class SongLinkScraper(
    private val http: HttpJsonClient = HttpJsonClient(readTimeoutMs = 20_000),
) {
    private data class CachedResult(val track: SongLinkScrapedTrack?)

    private val cache = ConcurrentHashMap<String, CachedResult>()
    private val keyLocks = ConcurrentHashMap<String, Mutex>()
    private val requestPermits = Semaphore(MAX_CONCURRENT_REQUESTS)

    suspend fun findAppleTrack(videoId: String): SongLinkScrapedTrack? {
        val url = pageUrl(videoId) ?: return null
        cache[videoId]?.let { return it.track }
        val lock = keyLocks.computeIfAbsent(videoId) { Mutex() }
        return lock.withLock {
            cache[videoId]?.let { return@withLock it.track }
            requestPermits.withPermit {
                when (val response = http.get(url, HTML_HEADERS)) {
                    is HttpOutcome.Failure -> null
                    is HttpOutcome.Success -> when (
                        val result = SongLinkPageParser.parse(response.value, videoId)
                    ) {
                        is SongLinkPageResult.AppleMatch -> {
                            val track = SongLinkScrapedTrack(
                                appleTrackId = result.appleTrackId,
                                appleUrl = result.appleUrl,
                                sourceDurationMs = result.sourceDurationMs,
                            )
                            cache[videoId] = CachedResult(track)
                            track
                        }

                        SongLinkPageResult.NoAppleMatch -> {
                            cache[videoId] = CachedResult(null)
                            null
                        }

                        SongLinkPageResult.InvalidPage -> null
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_CONCURRENT_REQUESTS = 4
        private val VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")
        private val HTML_HEADERS = mapOf(
            "Accept" to "text/html,application/xhtml+xml",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
        )

        fun pageUrl(videoId: String): String? =
            videoId.takeIf(VIDEO_ID_PATTERN::matches)?.let { "https://song.link/y/$it" }
    }
}

internal enum class YouTubePlaylistMatchSource {
    SONGLINK,
    LYRICS_PORN,
}

internal data class YouTubePlaylistAppleTrack(
    val appleTrackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val isrc: String?,
    val source: YouTubePlaylistMatchSource,
)

internal class YouTubePlaylistTrackResolver(
    private val songLinkLookup: suspend (String) -> SongLinkScrapedTrack? =
        SongLinkScraper()::findAppleTrack,
    private val lyricspornLookup: suspend (String?, YouTubePlaylistTrack) ->
        YouTubePlaylistCandidateMatch? = { apiBaseUrl, track ->
            val query = listOf(track.title, track.artist)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .joinToString(" ")
            val candidates = LyricspornClient.searchCatalog(
                apiBaseUrl = apiBaseUrl,
                term = query,
                types = "songs",
                limit = MAX_LYRICS_PORN_CANDIDATES,
            ).songs
            YouTubePlaylistTrackMatcher.bestCandidate(track, candidates)
        },
) {
    private val lyricspornPermits = Semaphore(MAX_CONCURRENT_LYRICS_PORN_REQUESTS)

    suspend fun resolve(
        apiBaseUrl: String?,
        track: YouTubePlaylistTrack,
    ): YouTubePlaylistAppleTrack? {
        val songLinkMatch = track.videoId?.let { videoId -> songLinkLookup(videoId) }
        if (songLinkMatch != null && YouTubePlaylistApi.durationsAreCompatible(
                track.durationMs,
                songLinkMatch.sourceDurationMs,
            )
        ) {
            return YouTubePlaylistAppleTrack(
                appleTrackId = songLinkMatch.appleTrackId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = null,
                durationMs = track.durationMs ?: songLinkMatch.sourceDurationMs,
                isrc = null,
                source = YouTubePlaylistMatchSource.SONGLINK,
            )
        }

        val lyricspornMatch = lyricspornPermits.withPermit {
            lyricspornLookup(apiBaseUrl, track)
        }?.takeIf { it.confidence >= YouTubePlaylistTrackMatcher.MIN_CONFIDENCE } ?: return null
        val item = lyricspornMatch.item
        if (!YouTubePlaylistApi.durationsAreCompatible(
                track.durationMs,
                item.durationMs,
            )
        ) {
            return null
        }
        return YouTubePlaylistAppleTrack(
            appleTrackId = item.id,
            title = item.name,
            artist = item.artistName?.trim()?.takeIf(String::isNotEmpty) ?: track.artist,
            album = item.albumName ?: track.album,
            artworkUrl = item.artworkUrl,
            durationMs = item.durationMs ?: track.durationMs,
            isrc = item.isrc,
            source = YouTubePlaylistMatchSource.LYRICS_PORN,
        )
    }

    private companion object {
        const val MAX_CONCURRENT_LYRICS_PORN_REQUESTS = 4
        const val MAX_LYRICS_PORN_CANDIDATES = 25
    }
}
