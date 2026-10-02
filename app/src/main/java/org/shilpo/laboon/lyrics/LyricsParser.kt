package org.shilpo.laboon.lyrics

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.max

internal object LyricsParser {
    private val lrcLine = Regex("^\\s*\\[([^]]+)](.*)$")
    private val wordTag = Regex("<([^>]+)>([^<]*)")

    fun fromText(
        raw: String,
        durationMs: Long,
        ttmlRaw: String? = null,
        structuredLines: List<LyricsLine>? = null,
    ): ParsedLyrics {
        val cleaned = raw.trim().removePrefix("\uFEFF")
        val lines = structuredLines?.takeIf { it.isNotEmpty() }
            ?: ttmlRaw?.let(::parseTtml).orEmpty().ifEmpty { parseTextLines(cleaned) }
        val normalized = withLineEnds(lines, durationMs)
        val hasWords = normalized.any { it.words.isNotEmpty() || it.backgroundWords.isNotEmpty() }
        val hasLineTimes = normalized.any { it.startMs > 0L }
        val format = when {
            hasWords -> LyricsFormat.Elrc
            hasLineTimes -> LyricsFormat.Lrc
            else -> LyricsFormat.Plain
        }
        val tier = when (format) {
            LyricsFormat.Elrc -> LyricsSyncLevel.Word
            LyricsFormat.Lrc -> LyricsSyncLevel.Line
            LyricsFormat.Plain -> LyricsSyncLevel.Plain
        }
        val withBreaks = if (tier != LyricsSyncLevel.Plain) {
            insertInstrumentalBreaks(normalized, durationMs)
        } else normalized
        val withRomanization = withBreaks.map { line ->
            if (!line.isInstrumental && line.romanization == null && line.text.isNotBlank() && Romanizer.needsRomanization(
                    line.text
                )
            ) {
                val roman = Romanizer.romanize(line.text)
                if (roman != null) line.copy(romanization = roman) else line
            } else {
                line
            }
        }
        val plainText = withRomanization.asSequence()
            .filterNot { it.isInstrumental }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return ParsedLyrics(format, tier, plainText, withRomanization)
    }

    fun parseTextLines(raw: String): List<LyricsLine> {
        if (raw.isBlank()) return emptyList()
        val result = mutableListOf<LyricsLine>()
        var hasTimestamp = false
        for (rawLine in raw.lineSequence()) {
            val match = lrcLine.matchEntire(rawLine) ?: continue
            val startMs = parseLrcTime(match.groupValues[1]) ?: continue
            hasTimestamp = true
            val body = match.groupValues[2]
            val words = wordTag.findAll(body).mapNotNull { wordMatch ->
                val start = parseLrcTime(wordMatch.groupValues[1]) ?: return@mapNotNull null
                val text = stripTags(wordMatch.groupValues[2]).trim()
                text.takeIf { it.isNotEmpty() }?.let { LyricsWord(it, start) }
            }.toList()
            val text = if (words.isNotEmpty()) {
                stripWordTags(body).trim()
            } else {
                stripTags(body).trim()
            }
            if (text.isNotEmpty() || words.isNotEmpty()) {
                result += LyricsLine(
                    text = text.ifEmpty { words.joinToString("") { it.text } },
                    startMs = startMs,
                    words = words,
                )
            }
        }
        if (hasTimestamp && result.isNotEmpty()) return result
        return raw.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { line -> LyricsLine(line, 0L) }
            .toList()
    }

    fun parseTtml(raw: String): List<LyricsLine> = runCatching {
        val parser = try {
            Xml.newPullParser()
        } catch (_: Throwable) {
            return@runCatching parseTtmlDom(raw)
        }
        parser.apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(raw.trim().removePrefix("\uFEFF")))
        }

        data class Paragraph(
            val begin: Long?,
            val end: Long?,
            val agent: String?,
            val role: String?,
            val lang: String?,
            val text: StringBuilder = StringBuilder(),
            val words: MutableList<LyricsWord> = mutableListOf(),
            val translations: MutableList<LyricsTranslation> = mutableListOf(),
            var romanization: String? = null,
        )

        data class Span(
            val begin: Long?,
            val role: String?,
            val lang: String?,
            val text: StringBuilder = StringBuilder(),
        )

        val output = mutableListOf<LyricsLine>()
        var paragraph: Paragraph? = null
        var span: Span? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "p" -> paragraph = Paragraph(
                        begin = parser.attributeValue("begin")?.let(::parseTtmlTime),
                        end = parser.attributeValue("end")?.let(::parseTtmlTime)
                            ?: parser.attributeValue("dur")?.let(::parseTtmlTime)?.let { dur ->
                                parser.attributeValue("begin")?.let(::parseTtmlTime)?.plus(dur)
                            },
                        agent = parser.attributeValue("agent"),
                        role = parser.attributeValue("role")?.lowercase(),
                        lang = parser.attributeValue("lang") ?: parser.attributeValue("xml:lang"),
                    )

                    "span" -> span = Span(
                        begin = parser.attributeValue("begin")?.let(::parseTtmlTime),
                        role = parser.attributeValue("role")?.lowercase(),
                        lang = parser.attributeValue("lang") ?: parser.attributeValue("xml:lang"),
                    )
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text.orEmpty()
                    val currSpan = span
                    if (currSpan != null) {
                        currSpan.text.append(text)
                        if (currSpan.role != "x-translation" && currSpan.role != "x-roman") {
                            paragraph?.text?.append(text)
                        }
                    } else {
                        paragraph?.text?.append(text)
                    }
                }

                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "span" -> {
                        val completed = span
                        val parent = paragraph
                        if (completed != null && parent != null) {
                            val spanText = completed.text.toString().trim()
                            when (completed.role) {
                                "x-roman" -> {
                                    if (spanText.isNotEmpty()) {
                                        parent.romanization = spanText
                                    }
                                }

                                "x-translation" -> {
                                    if (spanText.isNotEmpty()) {
                                        val lang = completed.lang ?: parent.lang ?: "en"
                                        parent.translations += LyricsTranslation(lang, spanText)
                                    }
                                }

                                else -> {
                                    if (completed.begin != null && spanText.isNotEmpty()) {
                                        parent.words += LyricsWord(spanText, completed.begin)
                                    }
                                }
                            }
                        }
                        span = null
                    }

                    "p" -> {
                        val completed = paragraph
                        if (completed != null) {
                            val pRole = completed.role
                            val pText = completed.text.toString().trim()
                            val start = completed.begin ?: completed.words.firstOrNull()?.startMs
                            if (pRole == "x-roman") {
                                val targetIdx =
                                    output.indexOfLast { it.startMs == start }.takeIf { it >= 0 }
                                        ?: output.lastIndex.takeIf { it >= 0 }
                                if (targetIdx != null && pText.isNotEmpty()) {
                                    output[targetIdx] = output[targetIdx].copy(romanization = pText)
                                }
                            } else if (pRole == "x-translation") {
                                val targetIdx =
                                    output.indexOfLast { it.startMs == start }.takeIf { it >= 0 }
                                        ?: output.lastIndex.takeIf { it >= 0 }
                                if (targetIdx != null && pText.isNotEmpty()) {
                                    val lang = completed.lang ?: "en"
                                    val filtered =
                                        output[targetIdx].translations.filter { it.language != lang }
                                    output[targetIdx] = output[targetIdx].copy(
                                        translations = filtered + LyricsTranslation(lang, pText)
                                    )
                                }
                            } else {
                                val words = completed.words.sortedBy { it.startMs }
                                val lineStart = start ?: words.firstOrNull()?.startMs
                                if (lineStart != null && (pText.isNotEmpty() || words.isNotEmpty())) {
                                    output += LyricsLine(
                                        text = if (words.isNotEmpty()) words.joinToString("") { it.text } else pText,
                                        startMs = lineStart,
                                        endMs = completed.end ?: words.lastOrNull()?.endMs ?: 0L,
                                        words = words,
                                        agent = completed.agent,
                                        translations = completed.translations,
                                        romanization = completed.romanization,
                                    )
                                }
                            }
                        }
                        paragraph = null
                        span = null
                    }
                }
            }
            event = parser.next()
        }
        output
    }.getOrDefault(emptyList())

    private fun parseTtmlDom(raw: String): List<LyricsLine> = runCatching {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        val doc =
            builder.parse(org.xml.sax.InputSource(StringReader(raw.trim().removePrefix("\uFEFF"))))
        val pList = doc.getElementsByTagNameNS("*", "p").let { list ->
            if (list.length > 0) list else doc.getElementsByTagName("p")
        }
        val output = mutableListOf<LyricsLine>()
        for (i in 0 until pList.length) {
            val p = pList.item(i) as? org.w3c.dom.Element ?: continue
            val begin = p.getAttribute("begin").takeIf { it.isNotBlank() }?.let(::parseTtmlTime)
            val end = p.getAttribute("end").takeIf { it.isNotBlank() }?.let(::parseTtmlTime)
            val dur = p.getAttribute("dur").takeIf { it.isNotBlank() }?.let(::parseTtmlTime)
            val endMs = end ?: (if (begin != null && dur != null) begin + dur else null)
            val agent = p.getAttribute("agent").takeIf { it.isNotBlank() }
            val role =
                (p.getAttribute("ttm:role").takeIf { it.isNotBlank() } ?: p.getAttribute("role")
                    .takeIf { it.isNotBlank() })?.lowercase()
            val lang =
                p.getAttribute("xml:lang").takeIf { it.isNotBlank() } ?: p.getAttribute("lang")
                    .takeIf { it.isNotBlank() }

            val pText = StringBuilder()
            val words = mutableListOf<LyricsWord>()
            val translations = mutableListOf<LyricsTranslation>()
            var romanization: String? = null

            val childNodes = p.childNodes
            for (j in 0 until childNodes.length) {
                val child = childNodes.item(j)
                if (child is org.w3c.dom.Element && (child.localName == "span" || child.tagName.endsWith(
                        "span"
                    ))
                ) {
                    val sBegin =
                        child.getAttribute("begin").takeIf { it.isNotBlank() }?.let(::parseTtmlTime)
                    val sRole = (child.getAttribute("ttm:role").takeIf { it.isNotBlank() }
                        ?: child.getAttribute("role").takeIf { it.isNotBlank() })?.lowercase()
                    val sLang = child.getAttribute("xml:lang").takeIf { it.isNotBlank() }
                        ?: child.getAttribute("lang").takeIf { it.isNotBlank() }
                    val sText = child.textContent.orEmpty().trim()

                    when (sRole) {
                        "x-roman" -> if (sText.isNotEmpty()) romanization = sText
                        "x-translation" -> if (sText.isNotEmpty()) translations += LyricsTranslation(
                            sLang ?: lang ?: "en",
                            sText
                        )

                        else -> {
                            pText.append(child.textContent.orEmpty())
                            if (sBegin != null && sText.isNotEmpty()) {
                                words += LyricsWord(sText, sBegin)
                            }
                        }
                    }
                } else if (child.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                    pText.append(child.textContent.orEmpty())
                }
            }

            val text = pText.toString().trim()
            val lineStart = begin ?: words.firstOrNull()?.startMs

            if (role == "x-roman") {
                val targetIdx = output.indexOfLast { it.startMs == lineStart }.takeIf { it >= 0 }
                    ?: output.lastIndex.takeIf { it >= 0 }
                if (targetIdx != null && text.isNotEmpty()) {
                    output[targetIdx] = output[targetIdx].copy(romanization = text)
                }
            } else if (role == "x-translation") {
                val targetIdx = output.indexOfLast { it.startMs == lineStart }.takeIf { it >= 0 }
                    ?: output.lastIndex.takeIf { it >= 0 }
                if (targetIdx != null && text.isNotEmpty()) {
                    val language = lang ?: "en"
                    val filtered = output[targetIdx].translations.filter { it.language != language }
                    output[targetIdx] = output[targetIdx].copy(
                        translations = filtered + LyricsTranslation(
                            language,
                            text
                        )
                    )
                }
            } else {
                if (lineStart != null && (text.isNotEmpty() || words.isNotEmpty())) {
                    output += LyricsLine(
                        text = if (words.isNotEmpty()) words.joinToString("") { it.text } else text,
                        startMs = lineStart,
                        endMs = endMs ?: words.lastOrNull()?.endMs ?: 0L,
                        words = words.sortedBy { it.startMs },
                        agent = agent,
                        translations = translations,
                        romanization = romanization,
                    )
                }
            }
        }
        output
    }.getOrDefault(emptyList())

    fun convertTtml(raw: String, wordSyncedOnly: Boolean = false): String? {
        val lines = parseTtml(raw)
        if (lines.isEmpty() || (wordSyncedOnly && lines.none { it.words.isNotEmpty() })) return null
        return lines.joinToString("\n") { line ->
            val body = if (line.words.isNotEmpty()) {
                line.words.joinToString("") { "<${formatTime(it.startMs)}>${it.text}" }
            } else line.text
            "[${formatTime(line.startMs)}]$body"
        }
    }

    fun metadataScore(
        lookup: LyricsLookup,
        title: String,
        artist: String,
        album: String? = null,
        duration: Long? = null,
    ): Int? {
        if (!titleMatches(lookup.title, title) || !artistsMatch(
                lookup.artistString,
                artist
            )
        ) return null
        val expectedDuration = lookup.durationSeconds
        if (expectedDuration != null && expectedDuration > 0 && duration != null &&
            kotlin.math.abs(expectedDuration - duration) > 12L
        ) return null
        var score = 160
        if (titleKey(lookup.title) == titleKey(title)) score += 30
        if (!lookup.album.isNullOrBlank() && !album.isNullOrBlank() && titleMatches(
                lookup.album,
                album
            )
        ) score += 25
        if (expectedDuration != null && duration != null) {
            score += when (kotlin.math.abs(expectedDuration - duration)) {
                in 0..2 -> 30
                in 3..5 -> 20
                else -> 10
            }
        }
        return score
    }

    fun lrcTimestamp(value: String): Long? = parseLrcTime(value)

    private fun withLineEnds(lines: List<LyricsLine>, durationMs: Long): List<LyricsLine> {
        val sorted = lines.sortedBy { it.startMs }
        return sorted.mapIndexed { index, line ->
            val nextStart = sorted.getOrNull(index + 1)?.startMs?.takeIf { it > line.startMs }
            val wordEnd = (line.words + line.backgroundWords).mapNotNull { it.endMs }.maxOrNull()
            val defaultLineDuration = 3_500L
            val gapToNext = nextStart?.let { it - line.startMs } ?: 0L
            val end = line.endMs.takeIf { it > line.startMs }
                ?: wordEnd?.takeIf { it > line.startMs }
                ?: (if (nextStart != null && gapToNext >= defaultLineDuration + 5_000L) line.startMs + defaultLineDuration else nextStart)
                ?: (if (durationMs > line.startMs) durationMs else line.startMs + 3_000L)
            val words = inferWordEnds(line.words, end)
            val background = inferWordEnds(line.backgroundWords, end)
            line.copy(endMs = end, words = words, backgroundWords = background)
        }
    }

    private fun inferWordEnds(words: List<LyricsWord>, lineEnd: Long): List<LyricsWord> =
        words.mapIndexed { index, word ->
            val inferred = words.getOrNull(index + 1)?.startMs ?: lineEnd
            word.copy(endMs = word.endMs?.takeIf { it > word.startMs } ?: max(
                inferred,
                word.startMs + 1L
            ))
        }

    private fun insertInstrumentalBreaks(
        lines: List<LyricsLine>,
        durationMs: Long
    ): List<LyricsLine> {
        if (lines.isEmpty()) return lines
        val result = mutableListOf<LyricsLine>()
        val first = lines.first()
        if (!first.isInstrumental && first.startMs >= 5_000L) result += LyricsLine(
            "",
            0L,
            first.startMs,
            isInstrumental = true
        )
        lines.forEach { line ->
            val previous = result.lastOrNull()?.takeUnless { it.isInstrumental }
            if (previous != null && line.startMs - previous.endMs >= 5_000L) {
                result += LyricsLine("", previous.endMs, line.startMs, isInstrumental = true)
            }
            result += line
        }
        val last = result.lastOrNull()?.takeUnless { it.isInstrumental }
        if (last != null && durationMs - last.endMs >= 5_000L) {
            result += LyricsLine("", last.endMs, durationMs, isInstrumental = true)
        }
        return result
    }

    private fun parseLrcTime(raw: String): Long? {
        val parts = raw.trim().split(':')
        if (parts.size != 2) return null
        val minutes = parts[0].toLongOrNull() ?: return null
        val seconds = parts[1].toDoubleOrNull() ?: return null
        if (minutes < 0 || seconds < 0.0) return null
        return minutes * 60_000L + (seconds * 1000.0).toLong()
    }

    private fun parseTtmlTime(raw: String): Long? {
        val value = raw.trim()
        listOf("ms" to 1.0, "h" to 3_600_000.0, "m" to 60_000.0, "s" to 1_000.0)
            .forEach { (suffix, scale) ->
                if (value.endsWith(suffix)) return value.removeSuffix(suffix).trim()
                    .toDoubleOrNull()
                    ?.takeIf { it >= 0 }?.let { (it * scale).toLong() }
            }
        val parts = value.split(':')
        val milliseconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()?.times(1000.0)
            2 -> {
                val m = parts[0].toLongOrNull() ?: return null
                val s = parts[1].toDoubleOrNull() ?: return null
                (m * 60_000.0) + s * 1000.0
            }

            3 -> {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parts[2].toDoubleOrNull() ?: return null
                (h * 3_600_000.0) + (m * 60_000.0) + s * 1000.0
            }

            else -> null
        }
        return milliseconds?.takeIf { it >= 0 }?.toLong()
    }

    private fun formatTime(ms: Long): String =
        "%02d:%02d.%03d".format(ms / 60_000L, (ms / 1_000L) % 60L, ms % 1_000L)

    private fun stripWordTags(text: String): String = wordTag.replace(text) { it.groupValues[2] }
    private fun stripTags(text: String): String = Regex("(?s)<[^>]+>").replace(text, "")

    private fun XmlPullParser.attributeValue(name: String): String? =
        (0 until attributeCount).firstNotNullOfOrNull { index ->
            if (getAttributeName(index).substringAfter(':') == name) getAttributeValue(index) else null
        }

    private fun titleMatches(expected: String, actual: String): Boolean {
        val wanted = titleKey(expected)
        val found = titleKey(actual)
        return wanted.isNotEmpty() && found.isNotEmpty() &&
                (wanted == found || wanted.contains(found) || found.contains(wanted))
    }

    private fun artistsMatch(expected: String, actual: String): Boolean {
        val wantedArtists = artistParts(expected).map(::artistKey).filter(String::isNotEmpty)
        val foundArtists = artistParts(actual).map(::artistKey).filter(String::isNotEmpty)
        return wantedArtists.any { it in foundArtists }
    }

    private fun artistParts(value: String): List<String> =
        value.split(',', '/', '&', '、', ';').map(String::trim).filter(String::isNotEmpty)

    private fun artistKey(value: String): String {
        val tokens = value.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotEmpty)
        if (tokens.size <= 1) return tokens.firstOrNull().orEmpty()
        return tokens.dropLast(1).mapNotNull { it.firstOrNull() }.joinToString("") + tokens.last()
    }

    private fun titleKey(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }
}

internal data class ParsedLyrics(
    val format: LyricsFormat,
    val syncLevel: LyricsSyncLevel,
    val plainText: String,
    val lines: List<LyricsLine>,
)
