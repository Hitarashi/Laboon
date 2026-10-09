package org.shilpo.laboon.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpOutcome
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.objAtOrNull
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import java.util.Locale
import kotlin.math.roundToLong

internal class LyricsRepositoryImpl(
    private val lyricspornApiUrlProvider: () -> String?,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val diskCache: LyricsDiskCache? = null,
    private val searchSongsForLyrics: suspend (String?, String) -> HttpOutcome<List<LyricspornCatalogItem>> =
        { apiBaseUrl, term -> LyricspornClient.searchSongsForLyrics(apiBaseUrl, term) },
    private val getTrackLyrics: suspend (String?, String) -> HttpOutcome<JSONObject> =
        { apiBaseUrl, appleTrackId -> LyricspornClient.getTrackLyricsOutcome(apiBaseUrl, appleTrackId) },
) : LyricsRepository {

    private data class Cached(val value: LyricsResult, val expiresAt: Long)

    private val cache = LinkedHashMap<String, Cached>(16, 0.75f, true)

    override suspend fun lookup(track: LyricsLookup): LyricsLookupResult = withContext(Dispatchers.IO) {
        val apiBaseUrl = LyricspornClient.normalizeApiBaseUrl(lyricspornApiUrlProvider())
            ?: return@withContext LyricsLookupResult.Failed

        val directAppleId = track.appleTrackId?.takeIf { it.isNumericId() }
        if (directAppleId == null && (track.title.isBlank() || track.artistString.isBlank())) {
            return@withContext LyricsLookupResult.NotFound
        }

        val key = listOf(
            apiBaseUrl,
            LyricspornClient.currentStorefront(),
            directAppleId.orEmpty(),
            track.title.trim().lowercase(Locale.ROOT),
            track.artistString.trim().lowercase(Locale.ROOT),
            track.album.orEmpty().trim().lowercase(Locale.ROOT),
        ).joinToString("\u0000")
        val cached = synchronized(cache) {
            cache[key]?.also { if (it.expiresAt <= nowMs()) cache.remove(key) }
        }
        cached?.takeIf { it.expiresAt > nowMs() }?.let { cachedValue ->
            val value = cachedValue.value.withGeneratedRomanization()
            if (value != cachedValue.value) remember(key, value, cachedValue.expiresAt)
            return@withContext LyricsLookupResult.Found(value)
        }

        diskCache?.get(key)?.takeIf { it.expiresAt > nowMs() }?.let { persisted ->
            val value = persisted.value.withGeneratedRomanization()
            remember(key, value, persisted.expiresAt)
            if (value != persisted.value) diskCache.put(key, value, persisted.expiresAt)
            return@withContext LyricsLookupResult.Found(value)
        }

        val appleId = directAppleId ?: when (
            val search = searchSongsForLyrics(
                apiBaseUrl,
                listOf(track.title.trim(), track.artistString.trim()).joinToString(" "),
            )
        ) {
            is HttpOutcome.Failure -> return@withContext LyricsLookupResult.Failed
            is HttpOutcome.Success -> search.value.firstOrNull()?.id
                ?: return@withContext LyricsLookupResult.NotFound
        }
        if (!appleId.isNumericId()) return@withContext LyricsLookupResult.Failed

        val result = when (val response = getTrackLyrics(apiBaseUrl, appleId)) {
            is HttpOutcome.Failure -> {
                if (response.error.kind == HttpErrorKind.STATUS && response.error.statusCode == 404) {
                    LyricsLookupResult.NotFound
                } else {
                    LyricsLookupResult.Failed
                }
            }
            is HttpOutcome.Success -> response.value.toLyricsLookupResult()
        }
        val found = result as? LyricsLookupResult.Found ?: return@withContext result
        val resultWithRomanization = found.lyrics.withGeneratedRomanization()

        if (resultWithRomanization.provider != null) {
            val expiresAt = nowMs() + CACHE_TTL_MS
            remember(key, resultWithRomanization, expiresAt)
            diskCache?.put(key, resultWithRomanization, expiresAt)
        }
        LyricsLookupResult.Found(resultWithRomanization)
    }

    private fun LyricsResult.withGeneratedRomanization(): LyricsResult {
        val romanizedLines = Romanizer.addMissingRomanization(lines)
        return if (romanizedLines == lines) this else copy(lines = romanizedLines)
    }

    private fun remember(key: String, value: LyricsResult, expiresAt: Long) {
        synchronized(cache) {
            cache[key] = Cached(value, expiresAt)
            while (cache.size > MAX_CACHE_ENTRIES) cache.remove(cache.entries.first().key)
        }
    }

    private fun JSONObject.toLyricsLookupResult(): LyricsLookupResult {
        val lyrics = objOrNull("lyrics") ?: return LyricsLookupResult.Failed
        val lyricsStatus = lyrics.stringOrNull("status")?.lowercase(Locale.ROOT)
        if (lyricsStatus in NOT_FOUND_STATUSES) return LyricsLookupResult.NotFound

        val jsonFormat = lyrics.objOrNull("formats")?.objOrNull("json")
            ?: return LyricsLookupResult.Failed
        val formatStatus = jsonFormat.stringOrNull("status")?.lowercase(Locale.ROOT)
        if (formatStatus in NOT_FOUND_STATUSES) return LyricsLookupResult.NotFound
        if (formatStatus != "available") {
            return if (jsonFormat.stringOrNull("reason").orEmpty().indicatesNoLyrics()) {
                LyricsLookupResult.NotFound
            } else {
                LyricsLookupResult.Failed
            }
        }

        val content = jsonFormat.objOrNull("content") ?: return LyricsLookupResult.Failed
        val result = content.toLyricsResult() ?: return LyricsLookupResult.Failed
        return LyricsLookupResult.Found(result)
    }

    private fun String.indicatesNoLyrics(): Boolean {
        val normalized = lowercase(Locale.ROOT)
        return normalized.contains("no lyrics") ||
                normalized.contains("lyrics not found") ||
                normalized.contains("lyrics unavailable") ||
                normalized.contains("lyrics are unavailable")
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
        val NOT_FOUND_STATUSES = setOf("not_found", "notfound", "missing")
        const val CACHE_TTL_MS = 12 * 60 * 60 * 1_000L
        const val MAX_CACHE_ENTRIES = 256
    }
}
