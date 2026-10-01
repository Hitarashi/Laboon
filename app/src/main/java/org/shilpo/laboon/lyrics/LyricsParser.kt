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
        val withBreaks = if (tier == LyricsSyncLevel.Word) {
            insertInstrumentalBreaks(normalized, durationMs)
        } else normalized
        val plainText = withBreaks.asSequence()
            .filterNot { it.isInstrumental }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return ParsedLyrics(format, tier, plainText, withBreaks)
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
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(raw.trim().removePrefix("\uFEFF")))
        }

        data class Paragraph(
            val begin: Long?,
            val end: Long?,
            val agent: String?,
            val text: StringBuilder = StringBuilder(),
            val words: MutableList<LyricsWord> = mutableListOf(),
        )

        data class Span(val begin: Long?, val text: StringBuilder = StringBuilder())

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
                    )

                    "span" -> span = Span(parser.attributeValue("begin")?.let(::parseTtmlTime))
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text.orEmpty()
                    paragraph?.text?.append(text)
                    span?.text?.append(text)
                }

                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "span" -> {
                        val completed = span
                        val parent = paragraph
                        if (completed != null && parent != null && completed.begin != null) {
                            val wordText = completed.text.toString().trim()
                            if (wordText.isNotEmpty()) parent.words += LyricsWord(
                                wordText,
                                completed.begin
                            )
                        }
                        span = null
                    }

                    "p" -> {
                        val completed = paragraph
                        if (completed != null) {
                            val words = completed.words.sortedBy { it.startMs }
                            val start = completed.begin ?: words.firstOrNull()?.startMs
                            val text = completed.text.toString().trim()
                            if (start != null && (text.isNotEmpty() || words.isNotEmpty())) {
                                output += LyricsLine(
                                    text = text.ifEmpty { words.joinToString("") { it.text } },
                                    startMs = start,
                                    endMs = completed.end ?: words.lastOrNull()?.endMs ?: 0L,
                                    words = words,
                                    agent = completed.agent,
                                )
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
            val end = line.endMs.takeIf { it > line.startMs }
                ?: wordEnd?.takeIf { it > line.startMs }
                ?: nextStart
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
        if (first.startMs >= 5_000L) result += LyricsLine(
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
