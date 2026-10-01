package org.shilpo.laboon.lyrics

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray

internal object LrclibLyricsSource : LyricsSource {
    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> =
        coroutineScope {
            val exact = async { exact(http, input) }
            val search = async { search(http, input) }
            exact.await() + search.await()
        }

    private suspend fun exact(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val query = buildList {
            add("artist_name=${formEncode(input.artistString)}")
            add("track_name=${formEncode(input.title)}")
            input.album?.takeIf(String::isNotBlank)?.let { add("album_name=${formEncode(it)}") }
            input.durationSeconds?.takeIf { it > 0L }?.let { add("duration=$it") }
        }.joinToString("&")
        val url = "https://lrclib.net/api/get?$query"
        val data = jsonObject(http.get(url)) ?: return emptyList()
        return listOfNotNull(
            LyricsCandidate(data.text("syncedLyrics"), "LRCLIB Exact (LRC)", "lrclib", url, 15),
            LyricsCandidate(data.text("plainLyrics"), "LRCLIB Exact (Plain)", "lrclib", url, 10),
        )
    }

    private suspend fun search(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val url =
            "https://lrclib.net/api/search?q=${formEncode("${input.title} ${input.artistString}")}"
        val results =
            runCatching { JSONArray(http.get(url).orEmpty()) }.getOrNull() ?: return emptyList()
        val matches = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            .filter { item ->
                val title = item.text("trackName") ?: return@filter false
                val artist = item.text("artistName") ?: return@filter false
                val duration = item.optLong("duration", -1L).takeIf { it > 0 }
                lrclibMetadataMatches(input, title, artist, duration)
            }
            .sortedBy { item ->
                val actual = item.optLong("duration", 0L)
                kotlin.math.abs((input.durationSeconds ?: actual) - actual)
            }
        return matches.flatMap { item ->
            listOfNotNull(
                LyricsCandidate(item.text("syncedLyrics"), "LRCLIB Search (LRC)", "lrclib", url, 5),
                LyricsCandidate(
                    item.text("plainLyrics"),
                    "LRCLIB Search (Plain)",
                    "lrclib",
                    url,
                    0
                ),
            )
        }
    }

    private fun lrclibMetadataMatches(
        input: LyricsLookup,
        title: String,
        artist: String,
        duration: Long?
    ): Boolean {
        val wantedTitle = simpleKey(input.title)
        val foundTitle = simpleKey(title)
        val wantedArtist = simpleKey(input.artistString)
        val foundArtist = simpleKey(artist)
        return !(wantedTitle.isEmpty() || foundTitle.isEmpty() ||
                !(foundTitle == wantedTitle || foundTitle.contains(wantedTitle) || wantedTitle.contains(
                    foundTitle
                )) ||
                wantedArtist.isEmpty() || foundArtist.isEmpty() ||
                !(foundArtist == wantedArtist || foundArtist.contains(wantedArtist) || wantedArtist.contains(
                    foundArtist
                ))) && (input.durationSeconds == null || duration == null || kotlin.math.abs(input.durationSeconds - duration) <= 12L)
    }

    private fun simpleKey(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
}

internal object PaxsenixLyricsSource : LyricsSource {
    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val appleId = input.appleTrackId?.takeIf(String::isNotBlank) ?: return emptyList()
        val url = "https://lyrics.paxsenix.org/apple-music/lyrics?id=${uriComponent(appleId)}"
        val data = jsonObject(http.get(url)) ?: return emptyList()
        val ttml = data.text("ttmlContent")
        val converted = ttml?.let { LyricsParser.convertTtml(it, wordSyncedOnly = true) }
        return listOfNotNull(
            LyricsCandidate(
                data.text("elrc"),
                "Paxsenix Apple Music (ELRC)",
                "paxsenix",
                url,
                30,
                ttmlRaw = ttml
            ),
            LyricsCandidate(
                converted,
                "Paxsenix Apple Music (TTML-ELRC)",
                "paxsenix",
                url,
                30,
                ttmlRaw = ttml
            ),
            LyricsCandidate(
                data.text("lrc"),
                "Paxsenix Apple Music (LRC)",
                "paxsenix",
                url,
                25,
                ttmlRaw = ttml
            ),
            LyricsCandidate(
                data.text("plain"),
                "Paxsenix Apple Music (Plain)",
                "paxsenix",
                url,
                20
            ),
        )
    }
}

internal object BetterLyricsSource : LyricsSource {
    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val primary = endpoint(http, input, "getLyrics")
        return primary.ifEmpty { endpoint(http, input, "kugou/getLyrics") }
    }

    private suspend fun endpoint(
        http: LyricsHttp,
        input: LyricsLookup,
        path: String
    ): List<LyricsCandidate> {
        val url = buildString {
            append("https://lyrics-api.boidu.dev/$path?s=${uriComponent(input.title)}")
            append("&a=${uriComponent(input.artistString)}")
            input.album?.takeIf(String::isNotBlank)?.let { append("&al=${uriComponent(it)}") }
            input.durationSeconds?.takeIf { it > 0L }?.let { append("&d=$it") }
        }
        val data = jsonObject(http.get(url)) ?: return emptyList()
        val ttml = data.text("ttml")
        return listOfNotNull(
            LyricsCandidate(
                ttml?.let { LyricsParser.convertTtml(it, wordSyncedOnly = true) },
                "BetterLyrics (Word Synced)", "betterlyrics", url, 25, ttmlRaw = ttml,
            ),
            LyricsCandidate(
                data.text("lrc"),
                "BetterLyrics (LRC)",
                "betterlyrics",
                url,
                20,
                ttmlRaw = ttml
            ),
        )
    }
}

internal object UnisonLyricsSource : LyricsSource {
    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        if (input.title.isBlank() || input.artistString.isBlank()) return emptyList()
        val url = buildString {
            append("https://unison.boidu.dev/lyrics?song=${formEncode(input.title.trim())}")
            append("&artist=${formEncode(input.artistString.trim())}")
            input.album?.takeIf(String::isNotBlank)
                ?.let { append("&album=${formEncode(it.trim())}") }
            input.durationSeconds?.takeIf { it > 0L }?.let { append("&duration=$it") }
        }
        val found = candidate(http, input, url)
        if (found.isNotEmpty()) return found
        val videoId = input.youtubeVideoId?.takeIf(String::isNotBlank) ?: return emptyList()
        return candidate(http, input, "https://unison.boidu.dev/lyrics?v=${formEncode(videoId)}")
    }

    private suspend fun candidate(
        http: LyricsHttp,
        input: LyricsLookup,
        url: String
    ): List<LyricsCandidate> {
        val response = jsonObject(http.get(url)) ?: return emptyList()
        if (response.optBoolean("success", true).not()) return emptyList()
        val data = response.optJSONObject("data") ?: response
        val raw = data.text("lyrics") ?: return emptyList()
        val isTtml = data.text("format").equals("ttml", true) || raw.trimStart()
            .startsWith("<tt") || raw.trimStart().startsWith("<?xml")
        val id = data.optString("id", "unison")
        val attribution = "Lyrics from Unison (https://unison.boidu.dev)"
        val ttml = raw.takeIf { isTtml }
        val text = if (isTtml) LyricsParser.convertTtml(raw, wordSyncedOnly = true)
            ?: LyricsParser.convertTtml(raw) else raw
        return listOfNotNull(
            LyricsCandidate(
                text,
                if (isTtml) "Unison (TTML)" else "Unison",
                id,
                url,
                22,
                ttmlRaw = ttml,
                attribution = attribution
            )
        )
    }
}

internal object BinimumLyricsSource : LyricsSource {
    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val url = buildString {
            append("https://lyrics-api.binimum.org/?track=${uriComponent(input.title)}")
            append("&artist=${uriComponent(input.artistString)}")
            input.album?.takeIf(String::isNotBlank)?.let { append("&album=${uriComponent(it)}") }
            input.durationSeconds?.takeIf { it > 0L }?.let { append("&duration=$it") }
        }
        val results = jsonObject(http.get(url))?.optJSONArray("results") ?: return emptyList()
        val items = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            .mapNotNull { item ->
                val title = item.text("track_name") ?: return@mapNotNull null
                val artist = item.text("artist_name") ?: return@mapNotNull null
                val score =
                    LyricsParser.metadataScore(input, title, artist, null, item.number("duration"))
                        ?: return@mapNotNull null
                Triple(item, score, item.text("timing_type").orEmpty())
            }
            .sortedWith(compareByDescending<Triple<org.json.JSONObject, Int, String>> {
                it.third.equals("word", true) || it.third.equals("syllable", true)
            }.thenByDescending { it.second })

        for ((item, _, _) in items) {
            val sheetUrl = item.text("lyricsUrl") ?: continue
            if (!sheetUrl.startsWith("https://")) continue
            val ttml = http.get(sheetUrl) ?: continue
            val title = item.text("track_name").orEmpty()
            val artist = item.text("artist_name").orEmpty()
            val album = item.text("album_name")
            val duration = item.number("duration")
            val score =
                LyricsParser.metadataScore(input, title, artist, album, duration) ?: continue
            val text = LyricsParser.convertTtml(ttml, wordSyncedOnly = true)
                ?: LyricsParser.convertTtml(ttml)
                ?: continue
            val candidate = LyricsCandidate(
                text = text,
                provider = "Apple Music (Binimum TTML)",
                sourceId = "binimum",
                sourceUrl = sheetUrl,
                weight = 10,
                metadataScore = score,
                ttmlRaw = ttml,
            )
            return listOf(candidate)
        }
        return emptyList()
    }
}

internal object AmllTtmlDbSource : LyricsSource {
    @Volatile
    private var cachedIndex: List<AmllEntry>? = null

    private data class AmllEntry(
        val title: String,
        val artists: List<String>,
        val album: String?,
        val ncmId: String?,
        val file: String,
    )

    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val indexUrl =
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/metadata/raw-lyrics-index.jsonl"
        val entries = cachedIndex ?: run {
            val loaded = http.get(indexUrl)?.lineSequence()?.mapNotNull(::parseEntry)?.toList()
                ?: return emptyList()
            synchronized(this) {
                if (cachedIndex == null) cachedIndex = loaded
                cachedIndex ?: loaded
            }
        }
        val entry = entries.mapNotNull { item ->
            val score = LyricsParser.metadataScore(
                input,
                item.title,
                item.artists.joinToString(", "),
                item.album
            )
            score?.let { item to it }
        }.maxByOrNull { it.second }?.first ?: return emptyList()
        val url =
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/raw-lyrics/${
                uriComponent(entry.file)
            }"
        val ttml = http.get(url) ?: return emptyList()
        val text = LyricsParser.convertTtml(ttml, wordSyncedOnly = true)
            ?: LyricsParser.convertTtml(ttml)
            ?: return emptyList()
        return listOfNotNull(
            candidateForRecording(
                text, "AMLL TTML Database", entry.ncmId ?: "amll-ttml-db", url, 10,
                input, entry.title, entry.artists.joinToString(", "), entry.album, null,
                ttml = ttml,
            )
        )
    }

    private fun parseEntry(raw: String): AmllEntry? = runCatching {
        val item = org.json.JSONObject(raw)
        val metadata = item.optJSONArray("metadata") ?: return null
        val values = mutableMapOf<String, List<String>>()
        for (i in 0 until metadata.length()) {
            val pair = metadata.optJSONArray(i) ?: continue
            val key = pair.optString(0)
            val array = pair.optJSONArray(1) ?: continue
            values[key] = (0 until array.length()).mapNotNull {
                array.optString(it).takeIf(String::isNotBlank)
            }
        }
        AmllEntry(
            title = values["musicName"]?.firstOrNull() ?: return null,
            artists = values["artists"].orEmpty(),
            album = values["album"]?.firstOrNull(),
            ncmId = values["ncmMusicId"]?.firstOrNull(),
            file = item.text("rawLyricFile") ?: return null,
        )
    }.getOrNull()
}
