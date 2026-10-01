package org.shilpo.laboon.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class LyricsCandidate(
    val text: String?,
    val provider: String,
    val sourceId: String,
    val sourceUrl: String? = null,
    val weight: Int,
    val metadataScore: Int = 0,
    val ttmlRaw: String? = null,
    val structuredLines: List<LyricsLine>? = null,
    val attribution: String? = null,
) {
    fun score(): Int {
        val parsed = LyricsParser.fromText(text.orEmpty(), 0L, ttmlRaw, structuredLines)
        val tierScore = when (parsed.syncLevel) {
            LyricsSyncLevel.Word -> 1_000
            LyricsSyncLevel.Line -> 500
            LyricsSyncLevel.Plain -> 100
        }
        return if (parsed.plainText.trim().length < 10 || parsed.lines.size < 2) 0
        else tierScore + weight + metadataScore
    }

    fun toResult(durationMs: Long): LyricsResult {
        val parsed = LyricsParser.fromText(text.orEmpty(), durationMs, ttmlRaw, structuredLines)
        return LyricsResult(
            provider = provider,
            attribution = attribution,
            format = parsed.format,
            syncLevel = parsed.syncLevel,
            plainText = parsed.plainText,
            lines = parsed.lines,
        )
    }
}

internal interface LyricsSource {
    suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate>
}

internal class LyricsHttp(private val client: HttpJsonClient = HttpJsonClient()) {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String? =
        when (val response = client.get(url, DEFAULT_HEADERS + headers)) {
            is HttpOutcome.Success -> response.value
            is HttpOutcome.Failure -> null
        }

    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap()
    ): String? =
        when (val response = client.postJson(url, body, DEFAULT_HEADERS + headers)) {
            is HttpOutcome.Success -> response.value
            is HttpOutcome.Failure -> null
        }

    private companion object {
        val DEFAULT_HEADERS = mapOf("User-Agent" to "AlacBot/1.0", "Accept" to "*/*")
    }
}

internal class LyricsRepositoryImpl(
    private val http: LyricsHttp = LyricsHttp(),
    private val sources: List<LyricsSource> = defaultLyricsSources(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) : LyricsRepository {

    private data class Cached(val value: LyricsResult, val expiresAt: Long)

    private val cache = LinkedHashMap<String, Cached>(16, 0.75f, true)

    override suspend fun lookup(track: LyricsLookup): LyricsResult {
        val durationMs = track.durationSeconds?.coerceAtLeast(0L)?.times(1_000L) ?: 0L
        if (track.title.isBlank() || track.artistString.isBlank()) {
            return fallbackLyrics(track, durationMs)
        }
        val key = listOf(
            track.title.trim().lowercase(),
            track.artistString.trim().lowercase(),
            track.album.orEmpty().trim().lowercase(),
            track.durationSeconds?.toString().orEmpty(),
            track.appleTrackId.orEmpty(),
            track.youtubeVideoId.orEmpty(),
        ).joinToString("\u0000")
        val cached = synchronized(cache) {
            cache[key]?.also { if (it.expiresAt <= nowMs()) cache.remove(key) }
        }
        cached?.takeIf { it.expiresAt > nowMs() }?.let { return it.value }

        val candidates = coroutineScope {
            sources.map { source ->
                async {
                    try {
                        withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                            source.lookup(
                                http,
                                track
                            )
                        }.orEmpty()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        val best = candidates
            .mapIndexed { index, candidate -> index to candidate }
            .filter { it.second.score() > 0 }
            .maxWithOrNull(compareBy<Pair<Int, LyricsCandidate>> { it.second.score() }.thenBy { -it.first })
            ?.second
            ?.toResult(durationMs)
            ?: fallbackLyrics(track, durationMs)
        if (best.provider != null) synchronized(cache) {
            cache[key] = Cached(best, nowMs() + CACHE_TTL_MS)
            while (cache.size > MAX_CACHE_ENTRIES) cache.remove(cache.entries.first().key)
        }
        return best
    }

    private fun fallbackLyrics(track: LyricsLookup, durationMs: Long): LyricsResult {
        val text = "${track.title.trim()} - ${track.artistString.trim()}"
        return LyricsResult(
            provider = null,
            format = LyricsFormat.Plain,
            syncLevel = LyricsSyncLevel.Plain,
            plainText = text,
            lines = listOf(LyricsLine(text, 0L, durationMs)),
        )
    }

    private companion object {
        const val PROVIDER_TIMEOUT_MS = 12_000L
        const val CACHE_TTL_MS = 12 * 60 * 60 * 1_000L
        const val MAX_CACHE_ENTRIES = 256
    }
}

internal fun formEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

internal fun uriComponent(value: String): String = formEncode(value)
    .replace("+", "%20")
    .replace("%21", "!")
    .replace("%27", "'")
    .replace("%28", "(")
    .replace("%29", ")")
    .replace("%7E", "~")

internal fun jsonObject(body: String?): org.json.JSONObject? =
    body?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }

internal fun org.json.JSONObject.text(key: String): String? =
    optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

internal fun org.json.JSONObject.number(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0L }

internal fun candidateForRecording(
    text: String?,
    provider: String,
    sourceId: String,
    url: String,
    weight: Int,
    input: LyricsLookup? = null,
    recordingTitle: String? = null,
    recordingArtist: String? = null,
    recordingAlbum: String? = null,
    recordingDuration: Long? = null,
    ttml: String? = null,
    attribution: String? = null,
): LyricsCandidate? {
    val lyrics = text?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val matchScore = if (input != null && recordingTitle != null && recordingArtist != null) {
        LyricsParser.metadataScore(
            input,
            recordingTitle,
            recordingArtist,
            recordingAlbum,
            recordingDuration
        )
            ?: return null
    } else 0
    return LyricsCandidate(
        text = lyrics,
        provider = provider,
        sourceId = sourceId,
        sourceUrl = url,
        weight = weight,
        metadataScore = matchScore,
        ttmlRaw = ttml,
        attribution = attribution,
    )
}

private fun defaultLyricsSources(): List<LyricsSource> = listOf(
    PaxsenixLyricsSource,
    BetterLyricsSource,
    UnisonLyricsSource,
    BinimumLyricsSource,
    AmllTtmlDbSource,
    KugouLyricsSource,
    NetEaseLyricsSource,
    MusixmatchLyricsSource,
    QqMusicLyricsSource,
    YouTubeMusicLyricsSource,
    LrclibLyricsSource,
)
