package org.shilpo.laboon.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.objAtOrNull
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import java.util.Locale
import kotlin.math.roundToLong

internal class LyricsRepositoryImpl(
    private val lyricspornApiUrlProvider: () -> String?,
    private val client: HttpJsonClient = HttpJsonClient(),
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val diskCache: LyricsDiskCache? = null,
) : LyricsRepository {

    private data class Cached(val value: LyricsResult, val expiresAt: Long)

    private val cache = LinkedHashMap<String, Cached>(16, 0.75f, true)

    override suspend fun lookup(track: LyricsLookup): LyricsResult = withContext(Dispatchers.IO) {
        val durationMs = track.durationSeconds?.coerceAtLeast(0L)?.times(1_000L) ?: 0L
        if (track.title.isBlank() || track.artistString.isBlank()) {
            return@withContext fallbackLyrics(track, durationMs)
        }
        val apiBaseUrl = LyricspornClient.normalizeApiBaseUrl(lyricspornApiUrlProvider())
            ?: return@withContext fallbackLyrics(track, durationMs)

        val key = listOf(
            apiBaseUrl,
            track.appleTrackId.orEmpty(),
            track.title.trim().lowercase(Locale.ROOT),
            track.artistString.trim().lowercase(Locale.ROOT),
            track.album.orEmpty().trim().lowercase(Locale.ROOT),
        ).joinToString("\u0000")
        val cached = synchronized(cache) {
            cache[key]?.also { if (it.expiresAt <= nowMs()) cache.remove(key) }
        }
        cached?.takeIf { it.expiresAt > nowMs() }?.let { return@withContext it.value }

        diskCache?.get(key)?.takeIf { it.expiresAt > nowMs() }?.let { persisted ->
            remember(key, persisted.value, persisted.expiresAt)
            return@withContext persisted.value
        }

        val appleId = track.appleTrackId?.takeIf { it.isNumericId() }
            ?: LyricspornClient.searchSongs(
                apiBaseUrl = apiBaseUrl,
                term = listOf(track.title.trim(), track.artistString.trim()).joinToString(" "),
                limit = 1,
            ).firstOrNull()?.id
        if (appleId.isNullOrBlank()) return@withContext fallbackLyrics(track, durationMs)

        val url = "$apiBaseUrl/tracks/$appleId?include=lyrics&formats=json"
        val response = when (val outcome = client.getJson(url)) {
            is HttpOutcome.Success -> outcome.value
            is HttpOutcome.Failure -> null
        }
        val result = response?.objOrNull("lyrics")
            ?.objOrNull("formats")
            ?.objOrNull("json")
            ?.takeIf { it.optString("status") == "available" }
            ?.objOrNull("content")
            ?.toLyricsResult()
            ?: fallbackLyrics(track, durationMs)

        if (result.provider != null) {
            val expiresAt = nowMs() + CACHE_TTL_MS
            remember(key, result, expiresAt)
            diskCache?.put(key, result, expiresAt)
        }
        result
    }

    private fun remember(key: String, value: LyricsResult, expiresAt: Long) {
        synchronized(cache) {
            cache[key] = Cached(value, expiresAt)
            while (cache.size > MAX_CACHE_ENTRIES) cache.remove(cache.entries.first().key)
        }
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

    private fun JSONObject.toLyricsResult(): LyricsResult? {
        val lines = arrOrNull("lines")?.toLyricsLines().orEmpty()
        val plainText = stringOrNull("plainText")
            ?: lines.joinToString("\n") { it.text }
        if (plainText.isBlank() && lines.isEmpty()) return null

        return LyricsResult(
            provider = stringOrNull("provider"),
            attribution = stringOrNull("attribution"),
            format = when (stringOrNull("format")?.lowercase(Locale.ROOT)) {
                "elrc" -> LyricsFormat.Elrc
                "lrc" -> LyricsFormat.Lrc
                else -> LyricsFormat.Plain
            },
            syncLevel = when (stringOrNull("syncLevel")?.lowercase(Locale.ROOT)) {
                "word" -> LyricsSyncLevel.Word
                "line" -> LyricsSyncLevel.Line
                else -> LyricsSyncLevel.Plain
            },
            plainText = plainText,
            lines = lines,
        )
    }

    private fun JSONArray.toLyricsLines(): List<LyricsLine> = buildList {
        for (index in 0 until length()) {
            val line = objAtOrNull(index) ?: continue
            add(
                LyricsLine(
                    text = line.stringOrNull("text").orEmpty(),
                    startMs = line.millis("startMs"),
                    endMs = line.millis("endMs"),
                    words = line.objArray("words") { word ->
                        LyricsWord(
                            text = word.stringOrNull("text").orEmpty(),
                            startMs = word.millis("startMs"),
                            endMs = word.millisOrNull("endMs"),
                        )
                    },
                    backgroundWords = line.objArray("backgroundWords") { word ->
                        LyricsWord(
                            text = word.stringOrNull("text").orEmpty(),
                            startMs = word.millis("startMs"),
                            endMs = word.millisOrNull("endMs"),
                        )
                    },
                    alignment = line.stringOrNull("alignment"),
                    agent = line.stringOrNull("agent"),
                    singer = line.stringOrNull("singer"),
                    translations = line.objArray("translations") { translation ->
                        LyricsTranslation(
                            language = translation.stringOrNull("language").orEmpty(),
                            text = translation.stringOrNull("text").orEmpty(),
                        )
                    },
                    romanization = line.stringOrNull("romanization"),
                    isInstrumental = line.optBoolean("isInstrumental", false),
                ),
            )
        }
    }

    private inline fun <T> JSONObject.objArray(key: String, map: (JSONObject) -> T): List<T> =
        arrOrNull(key)?.let { values ->
            buildList {
                for (index in 0 until values.length()) {
                    values.objAtOrNull(index)?.let { add(map(it)) }
                }
            }
        }.orEmpty()

    private fun JSONObject.millis(key: String): Long = millisOrNull(key) ?: 0L

    private fun JSONObject.millisOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf(Double::isFinite)
            ?.roundToLong()

    private fun String.isNumericId(): Boolean = isNotBlank() && all(Char::isDigit)

    private companion object {
        const val CACHE_TTL_MS = 12 * 60 * 60 * 1_000L
        const val MAX_CACHE_ENTRIES = 256
    }
}
