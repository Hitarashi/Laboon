package org.shilpo.laboon.lyrics

import org.json.JSONArray
import org.json.JSONObject

internal object KugouLyricsSource : LyricsSource {
    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val searchUrl =
            "https://mobiles.kugou.com/api/v3/search/song?format=json&keyword=${uriComponent("${input.title} ${input.artistString}")}&page=1&pagesize=20&showtype=1"
        val songs = jsonObject(http.get(searchUrl))?.optJSONObject("data")?.optJSONArray("info")
            ?: return emptyList()
        val matches = (0 until songs.length()).mapNotNull { songs.optJSONObject(it) }
            .mapNotNull { song ->
                val title = song.text("songname") ?: return@mapNotNull null
                val artist = song.text("singername") ?: return@mapNotNull null
                val duration = song.number("duration")
                val score = LyricsParser.metadataScore(input, title, artist, null, duration)
                    ?: return@mapNotNull null
                Triple(song, score, duration ?: 0L)
            }
            .sortedByDescending { it.second }
            .take(4)

        for ((song, _, duration) in matches) {
            val hash = song.text("hash") ?: continue
            val indexUrl =
                "https://lyrics.kugou.com/search?ver=1&man=yes&client=mobi&hash=${formEncode(hash)}&duration=${duration * 1000L}"
            val lyricCandidates =
                jsonObject(http.get(indexUrl))?.optJSONArray("candidates") ?: continue
            val ordered =
                (0 until lyricCandidates.length()).mapNotNull { lyricCandidates.optJSONObject(it) }
                    .sortedByDescending { it.optLong("krctype", 0L) == 2L }
                    .take(3)
            for (lyricCandidate in ordered) {
                val id = lyricCandidate.optString("id").takeIf(String::isNotBlank) ?: continue
                val accessKey = lyricCandidate.text("accesskey") ?: continue
                val sheetUrl =
                    "https://lyrics.kugou.com/download?ver=1&client=pc&id=${formEncode(id)}&accesskey=${
                        formEncode(accessKey)
                    }&fmt=krc&charset=utf8"
                val encoded = jsonObject(http.get(sheetUrl))?.text("content") ?: continue
                val raw = LyricsTimedFormatParser.decryptKrc(encoded) ?: continue
                val lines = LyricsTimedFormatParser.parseKrc(raw)
                if (lines.isEmpty()) continue
                val title = song.text("songname").orEmpty()
                val artist = song.text("singername").orEmpty()
                val album = song.text("album_name")
                val matchScore =
                    LyricsParser.metadataScore(input, title, artist, album, duration) ?: continue
                return listOf(
                    LyricsCandidate(
                        text = LyricsTimedFormatParser.asEnhancedLrc(lines),
                        provider = "Kugou KRC",
                        sourceId = "kugou:$id",
                        sourceUrl = sheetUrl,
                        weight = 10,
                        metadataScore = matchScore,
                        structuredLines = lines,
                    )
                )
            }
        }
        return emptyList()
    }
}

internal object NetEaseLyricsSource : LyricsSource {
    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val headers = mapOf("Referer" to "https://music.163.com")
        val searchUrl =
            "https://music.163.com/api/search/get?s=${formEncode("${input.title} ${input.artistString}")}&type=1&limit=8"
        val songs =
            jsonObject(http.get(searchUrl, headers))?.optJSONObject("result")?.optJSONArray("songs")
                ?: return emptyList()
        val matches = (0 until songs.length()).mapNotNull { songs.optJSONObject(it) }
            .mapNotNull { song ->
                val title = song.text("name") ?: return@mapNotNull null
                val artists =
                    song.optJSONArray("artists")?.joinObjects { it.text("name") }.orEmpty()
                val duration = song.number("duration")?.div(1000L)
                val score = LyricsParser.metadataScore(input, title, artists, null, duration)
                    ?: return@mapNotNull null
                Triple(song, score, artists)
            }
            .sortedByDescending { it.second }
            .take(3)

        for ((song, _, artists) in matches) {
            val id = song.optString("id").takeIf(String::isNotBlank) ?: continue
            val lyricUrl =
                "https://music.163.com/api/song/lyric/v1?id=$id&cp=false&lv=0&tv=0&rv=0&kv=0&yv=0&ytv=0&yrv=0"
            val sheet = jsonObject(http.get(lyricUrl, headers)) ?: continue
            val yrc = sheet.optJSONObject("yrc")?.text("lyric")
            val lrc = sheet.optJSONObject("lrc")?.text("lyric")
            val structured = yrc?.let(LyricsTimedFormatParser::parseYrc)?.takeIf { it.isNotEmpty() }
                ?: lrc?.let(LyricsParser::parseTextLines)
                ?: continue
            val title = song.text("name").orEmpty()
            val album = song.optJSONObject("album")?.text("name")
            val duration = song.number("duration")?.div(1000L)
            val matchScore =
                LyricsParser.metadataScore(input, title, artists, album, duration) ?: continue
            return listOf(
                LyricsCandidate(
                    text = LyricsTimedFormatParser.asEnhancedLrc(structured),
                    provider = "NetEase (YRC/LRC)",
                    sourceId = "netease:$id",
                    sourceUrl = lyricUrl,
                    weight = 10,
                    metadataScore = matchScore,
                    structuredLines = structured,
                )
            )
        }
        return emptyList()
    }
}

internal object QqMusicLyricsSource : LyricsSource {
    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val headers = mapOf("Referer" to "https://y.qq.com/")
        val searchUrl =
            "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=1&n=10&w=${formEncode("${input.title} ${input.artistString}")}"
        val songs =
            jsonObject(http.get(searchUrl, headers))?.optJSONObject("data")?.optJSONObject("song")
                ?.optJSONArray("list")
                ?: return emptyList()
        val matches = (0 until songs.length()).mapNotNull { songs.optJSONObject(it) }
            .mapNotNull { song ->
                val title = song.text("songname") ?: return@mapNotNull null
                val artists = song.optJSONArray("singer")?.joinObjects { it.text("name") }.orEmpty()
                val duration = song.number("interval")
                val score = LyricsParser.metadataScore(input, title, artists, null, duration)
                    ?: return@mapNotNull null
                Triple(song, score, artists)
            }
            .sortedByDescending { it.second }
            .take(3)

        for ((song, _, artists) in matches) {
            val mid = song.text("songmid") ?: continue
            val lyricUrl =
                "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=${formEncode(mid)}&format=json&nobase64=0"
            val sheet = jsonObject(http.get(lyricUrl, headers)) ?: continue
            val encoded = sheet.text("qrc") ?: sheet.text("lyric") ?: continue
            val decoded = LyricsTimedFormatParser.decodeBase64OrRaw(encoded) ?: continue
            val raw = LyricsTimedFormatParser.extractQrcXmlContent(decoded) ?: decoded
            val decrypted = LyricsTimedFormatParser.decryptQrc(raw)
            val structured =
                decrypted?.let(LyricsTimedFormatParser::parseQrc)?.takeIf { it.isNotEmpty() }
                    ?: LyricsTimedFormatParser.parseQrc(raw).takeIf { it.isNotEmpty() }
                    ?: LyricsParser.parseTextLines(raw)
                        .takeIf { parsed -> parsed.any { it.startMs > 0L } }
                    ?: continue
            val title = song.text("songname").orEmpty()
            val album = song.text("albumname")
            val duration = song.number("interval")
            val matchScore =
                LyricsParser.metadataScore(input, title, artists, album, duration) ?: continue
            return listOf(
                LyricsCandidate(
                    text = LyricsTimedFormatParser.asEnhancedLrc(structured),
                    provider = "QQ Music (QRC)",
                    sourceId = "qq:$mid",
                    sourceUrl = lyricUrl,
                    weight = 10,
                    metadataScore = matchScore,
                    structuredLines = structured,
                )
            )
        }
        return emptyList()
    }
}

internal object MusixmatchLyricsSource : LyricsSource {
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36",
        "Cookie" to "x-mxm-token-guid=",
    )

    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val tokenUrl =
            "https://apic-desktop.musixmatch.com/ws/1.1/token.get?format=json&app_id=web-desktop-app-v1.0"
        val token = jsonObject(http.get(tokenUrl, headers))
            ?.optJSONObject("message")?.optJSONObject("body")?.text("user_token")
            ?.takeIf { it.isNotEmpty() && !it.contains("UpgradeOnly") } ?: return emptyList()
        val params = buildList {
            add("format=json")
            add("namespace=lyrics_richsynched")
            add("subtitle_format=mxm")
            add("optional_calls=track.richsync")
            add("app_id=web-desktop-app-v1.0")
            add("usertoken=${formEncode(token)}")
            add("q_track=${formEncode(input.title)}")
            add("q_artist=${formEncode(input.artistString)}")
            input.album?.takeIf(String::isNotBlank)?.let { add("q_album=${formEncode(it)}") }
            input.durationSeconds?.takeIf { it > 0 }?.let { add("q_duration=$it") }
        }.joinToString("&")
        val url = "https://apic-desktop.musixmatch.com/ws/1.1/macro.subtitles.get?$params"
        val response = jsonObject(http.get(url, headers)) ?: return emptyList()
        val body = response.optJSONObject("message")?.optJSONObject("body") ?: return emptyList()
        val calls = body.optJSONObject("macro_calls") ?: return emptyList()
        val track = calls.optJSONObject("matcher.track.get")?.optJSONObject("message")
            ?.optJSONObject("body")?.optJSONObject("track") ?: return emptyList()
        val title = track.text("track_name").orEmpty()
        val artist = track.text("artist_name").orEmpty()
        val album = track.text("album_name")
        val duration = track.number("track_length")
        val metadata =
            LyricsParser.metadataScore(input, title, artist, album, duration) ?: return emptyList()
        val richsync = calls.optJSONObject("track.richsync.get")?.optJSONObject("message")
            ?.optJSONObject("body")?.optJSONObject("richsync")?.text("richsync_body")
        val richLines = richsync?.let(::parseRichsync)
        val subtitle = calls.optJSONObject("track.subtitles.get")?.optJSONObject("message")
            ?.optJSONObject("body")?.optJSONArray("subtitle_list")?.optJSONObject(0)
            ?.optJSONObject("subtitle")?.text("subtitle_body")
        val structured = richLines?.takeIf { it.isNotEmpty() }
            ?: subtitle?.let(::parseSubtitles)
            ?: return emptyList()
        return listOf(
            LyricsCandidate(
                text = LyricsTimedFormatParser.asEnhancedLrc(structured),
                provider = "Musixmatch",
                sourceId = "musixmatch",
                sourceUrl = url,
                weight = 20,
                metadataScore = metadata,
                structuredLines = structured,
            )
        )
    }

    private fun parseRichsync(raw: String): List<LyricsLine>? = runCatching {
        val verses = JSONArray(raw)
        (0 until verses.length()).mapNotNull { index ->
            val verse = verses.optJSONObject(index) ?: return@mapNotNull null
            val start = (verse.opt("ts") as? String)?.toDoubleOrNull()?.times(1_000.0)?.toLong()
                ?: (verse.opt("ts") as? Number)?.toDouble()?.times(1_000.0)?.toLong()
                ?: return@mapNotNull null
            val end = (verse.opt("te") as? String)?.toDoubleOrNull()?.times(1_000.0)?.toLong()
                ?: (verse.opt("te") as? Number)?.toDouble()?.times(1_000.0)?.toLong()
            val tokens = verse.optJSONArray("l") ?: JSONArray()
            val words = (0 until tokens.length()).mapNotNull { wordIndex ->
                val token = tokens.optJSONObject(wordIndex) ?: return@mapNotNull null
                val text = token.text("c") ?: return@mapNotNull null
                val offset = (token.opt("o") as? String)?.toDoubleOrNull()?.times(1_000.0)?.toLong()
                    ?: (token.opt("o") as? Number)?.toDouble()?.times(1_000.0)?.toLong()
                    ?: return@mapNotNull null
                val nextOffset = tokens.optJSONObject(wordIndex + 1)?.let { next ->
                    (next.opt("o") as? String)?.toDoubleOrNull()?.times(1_000.0)?.toLong()
                        ?: (next.opt("o") as? Number)?.toDouble()?.times(1_000.0)?.toLong()
                }
                LyricsWord(
                    text,
                    start + offset,
                    start + (nextOffset ?: end?.minus(start) ?: offset + 1L)
                )
            }
            if (words.isEmpty()) null else LyricsLine(
                words.joinToString("") { it.text },
                start,
                end ?: 0L,
                words
            )
        }
    }.getOrNull()

    private fun parseSubtitles(raw: String): List<LyricsLine>? {
        val lrc = LyricsParser.parseTextLines(raw)
        if (lrc.any { it.startMs > 0L }) return lrc
        return runCatching {
            val cues = JSONArray(raw)
            (0 until cues.length()).mapNotNull { index ->
                val cue = cues.optJSONObject(index) ?: return@mapNotNull null
                val time = cue.optJSONObject("time") ?: return@mapNotNull null
                val start =
                    (time.opt("total") as? String)?.toDoubleOrNull()?.times(1_000.0)?.toLong()
                        ?: (time.opt("total") as? Number)?.toDouble()?.times(1_000.0)?.toLong()
                        ?: return@mapNotNull null
                val text = cue.text("text") ?: return@mapNotNull null
                LyricsLine(text, start)
            }
        }.getOrNull()
    }
}

internal object YouTubeMusicLyricsSource : LyricsSource {
    private const val API = "https://music.youtube.com/youtubei/v1"
    private const val CLIENT_VERSION = "1.20240923.01.00"

    override suspend fun lookup(http: LyricsHttp, input: LyricsLookup): List<LyricsCandidate> {
        val query = "${input.title} ${input.artistString}"
        val searchBody = JSONObject()
            .put("context", context())
            .put("query", query)
            .put("params", "EgWKAQIIAWoKEAkQAxAEEAoQBQ%3D%3D")
        val search = post(http, "search", searchBody) ?: return emptyList()
        val tracks = findObjects(search, "musicResponsiveListItemRenderer")
            .mapNotNull { renderer -> parseTrack(renderer) }
            .filter { track ->
                LyricsParser.metadataScore(
                    input,
                    track.title,
                    track.artist,
                    track.album,
                    track.duration
                ) != null ||
                        track.videoId == input.youtubeVideoId
            }
            .sortedByDescending { track ->
                LyricsParser.metadataScore(
                    input,
                    track.title,
                    track.artist,
                    track.album,
                    track.duration
                ) ?: 0
            }
            .take(3)

        for (track in tracks) {
            val id = track.videoId ?: continue
            val next = post(
                http,
                "next",
                JSONObject()
                    .put("context", context())
                    .put("videoId", id)
                    .put("playlistId", "RDAMVM$id")
                    .put("enablePersistentPlaylistPanel", true),
            ) ?: continue
            val browseId = findObjects(next, "browseEndpoint").firstNotNullOfOrNull { endpoint ->
                val browse = endpoint.optJSONObject("browseEndpoint") ?: endpoint
                val idValue = browse.optString("browseId").takeIf(String::isNotBlank)
                    ?: return@firstNotNullOfOrNull null
                val pageType = browse.optJSONObject("browseEndpointContextSupportedConfigs")
                    ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")
                idValue.takeIf { pageType == "MUSIC_PAGE_TYPE_TRACK_LYRICS" || it.startsWith("MPLYt") }
            } ?: continue
            val response = post(
                http,
                "browse",
                JSONObject().put("context", context()).put("browseId", browseId)
            ) ?: continue
            val description = findObjects(response, "musicDescriptionShelfRenderer")
                .firstNotNullOfOrNull { shelf ->
                    textAt(
                        shelf,
                        listOf("description")
                    )?.takeIf(String::isNotBlank)
                }
                ?: continue
            val score = LyricsParser.metadataScore(
                input,
                track.title,
                track.artist,
                track.album,
                track.duration
            ) ?: continue
            val candidate = LyricsCandidate(
                text = description,
                provider = "YouTube Music Description",
                sourceId = "youtube:$id",
                sourceUrl = "https://music.youtube.com/watch?v=$id",
                weight = 15,
                metadataScore = score + 5,
            )
            return listOf(candidate)
        }
        return emptyList()
    }

    private data class Track(
        val title: String,
        val artist: String,
        val album: String?,
        val duration: Long?,
        val videoId: String?
    )

    private fun parseTrack(renderer: JSONObject): Track? {
        val columns = renderer.optJSONArray("flexColumns") ?: return null
        val titleColumn =
            columns.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
        val title = textAt(titleColumn, listOf("text"))?.takeIf(String::isNotBlank) ?: return null
        val runs = titleColumn?.optJSONObject("text")?.optJSONArray("runs")
        val videoId = runs?.let { values ->
            (0 until values.length()).firstNotNullOfOrNull { index ->
                values.optJSONObject(index)?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")?.optString("videoId")
                    ?.takeIf(String::isNotBlank)
            }
        } ?: findStrings(renderer, "videoId").firstOrNull()
        val metadata =
            columns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.let { textAt(it, listOf("text")) }.orEmpty()
        val parts = metadata.split(" • ").map(String::trim)
        val artist = parts.firstOrNull().orEmpty()
        val album = parts.getOrNull(1)
        val durationText = findText(renderer, Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?$"))
        val duration = durationText?.split(':')?.mapNotNull(String::toLongOrNull)?.let { values ->
            when (values.size) {
                2 -> values[0] * 60 + values[1]
                3 -> values[0] * 3_600 + values[1] * 60 + values[2]
                else -> null
            }
        }
        return Track(title, artist, album, duration, videoId)
    }

    private suspend fun post(http: LyricsHttp, endpoint: String, body: JSONObject): JSONObject? =
        jsonObject(
            http.postJson(
                "$API/$endpoint?prettyPrint=false",
                body.toString(),
                mapOf("Content-Type" to "application/json")
            )
        )

    private fun context() = JSONObject().put(
        "client",
        JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", CLIENT_VERSION)
            .put("hl", "en").put("gl", "US"),
    )

    private fun findObjects(root: Any?, key: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val item = value.opt(key)
                    if (item is JSONObject) result += item
                    val keys = value.keys()
                    while (keys.hasNext()) visit(value.opt(keys.next()))
                }

                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index))
            }
        }
        visit(root)
        return result
    }

    private fun findStrings(root: Any?, key: String): List<String> {
        val result = mutableListOf<String>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    value.optString(key).takeIf(String::isNotBlank)?.let(result::add)
                    val keys = value.keys()
                    while (keys.hasNext()) visit(value.opt(keys.next()))
                }

                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index))
            }
        }
        visit(root)
        return result
    }

    private fun textAt(root: JSONObject?, path: List<String>): String? {
        var value: JSONObject? = root
        path.forEach { key -> value = value?.optJSONObject(key) }
        val text = value?.optString("simpleText")?.takeIf(String::isNotBlank)
        if (text != null) return text
        val runs = value?.optJSONArray("runs") ?: return null
        return (0 until runs.length()).mapNotNull {
            (runs.optJSONObject(it)?.optString("text"))?.takeIf(String::isNotBlank)
        }.joinToString("")
            .takeIf(String::isNotBlank)
    }

    private fun findText(root: Any?, pattern: Regex): String? {
        fun visit(value: Any?): String? = when (value) {
            is JSONObject -> {
                val simple = value.optString("simpleText").takeIf { pattern.matches(it) }
                simple ?: value.optJSONArray("runs")?.let { runs ->
                    (0 until runs.length()).joinToString("") {
                        runs.optJSONObject(it)?.optString("text").orEmpty()
                    }
                        .takeIf { pattern.matches(it) }
                } ?: run {
                    val keys = value.keys()
                    var found: String? = null
                    while (keys.hasNext() && found == null) found = visit(value.opt(keys.next()))
                    found
                }
            }

            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { visit(value.opt(it)) }
            else -> null
        }
        return visit(root)
    }
}

private inline fun JSONArray.joinObjects(transform: (JSONObject) -> String?): String =
    (0 until length()).mapNotNull { optJSONObject(it)?.let(transform) }.joinToString(", ")
