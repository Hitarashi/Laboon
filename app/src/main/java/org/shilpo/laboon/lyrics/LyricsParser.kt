package org.shilpo.laboon.lyrics

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.max

internal object LyricsParser {
    private val lrcLine = Regex("^\\s*\\[([^]]+)](.*)$")
    private val wordTag = Regex("<([^>]+)>([^<]*)")
    private val singerHeaderRegex =
        Regex("^(?:\\[|\\()?([\\p{L}\\p{N}\\s/&,.'\\-]+?)(?:\\]|\\))?[:：\\-—–]?\\s*$")
    private val artistCreditSeparator =
        Regex("""(?i)\s*,\s*|\s*/\s*|\s*&\s*|\s+feat\.?\s+|\s+ft\.?\s+|\s+with\s+|\s+and\s+|\s*、\s*|\s*;\s*""")
    private val soloAgentId = Regex("(?i)^v[12]$")
    private const val LEAD_AGENT_DOMINANCE_THRESHOLD = 0.60f
    private val inlineBracketPrefixRegex =
        Regex("^\\[([\\p{L}\\p{N}\\s/&,.'\\-]{1,40})\\][:：]?\\s*(.+)$")
    private val inlineColonPrefixRegex =
        Regex("^([\\p{L}\\p{N}\\s/&,.'\\-]{1,40})[:：]\\s*(.+)$")
    private val inlineParenPrefixRegex =
        Regex("^\\(([\\p{L}\\p{N}\\s/&,.'\\-]{1,40})\\)[:：]?\\s*(.+)$")
    private val commonAdlibs = setOf(
        "yeah", "yea", "oh", "ooh", "ah", "aah", "hey", "uh", "woo", "whoa", "baby", "la", "na"
    )

    fun fromText(
        raw: String,
        durationMs: Long,
        ttmlRaw: String? = null,
        structuredLines: List<LyricsLine>? = null,
        mainArtist: String? = null,
    ): ParsedLyrics {
        val cleaned = raw.trim().removePrefix("\uFEFF")
        val lines = structuredLines?.takeIf { it.isNotEmpty() }
            ?: ttmlRaw?.let(::parseTtml).orEmpty().ifEmpty { parseTextLines(cleaned) }
        val resolvedLines = resolveSingersAndAgents(lines, mainArtist)
        val normalized = withLineEnds(resolvedLines, durationMs)
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
        val withRomanization = Romanizer.addMissingRomanization(withBreaks)
        val plainText = withRomanization.asSequence()
            .filterNot { it.isInstrumental }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return ParsedLyrics(format, tier, plainText, withRomanization)
    }

    private fun resolveSingersAndAgents(
        lines: List<LyricsLine>,
        mainArtist: String? = null,
    ): List<LyricsLine> {
        if (lines.isEmpty()) return emptyList()
        val roleResolvedLines = reconcileLeadSpeakerAgents(lines, mainArtist)
        val tracker = SingerTracker()
        for (line in roleResolvedLines) {
            if (!line.singer.isNullOrBlank() && !line.agent.isNullOrBlank()) {
                tracker.registerSinger(line.singer, line.agent)
            }
        }

        val allSingers =
            roleResolvedLines.mapNotNull { it.singer?.trim()?.ifBlank { null } }.distinct()
        val soloSingers = allSingers.filter { !tracker.isDuetOrGroup(it.lowercase()) }
        if (soloSingers.size >= 2) {
            val creditedArtists = splitArtistCredits(mainArtist)
            val mainLower = mainArtist?.lowercase()?.trim()
            val primaryMain = creditedArtists.firstOrNull()?.lowercase()
            val leadSinger = (if (!primaryMain.isNullOrBlank()) {
                soloSingers.firstOrNull {
                    primaryMain == it.lowercase() || primaryMain.contains(it.lowercase()) || it.lowercase()
                        .contains(primaryMain)
                }
            } else null) ?: (if (!mainLower.isNullOrBlank()) {
                soloSingers.firstOrNull {
                    mainLower.contains(it.lowercase()) || it.lowercase().contains(mainLower)
                }
            } else null) ?: run {
                val counts = roleResolvedLines.groupBy { it.singer?.trim()?.lowercase() }
                    .mapValues { it.value.size }
                soloSingers.maxByOrNull { counts[it.lowercase()] ?: 0 } ?: soloSingers.first()
            }
            val remaining = soloSingers.filter { it.lowercase() != leadSinger.lowercase() }
            val secondSinger = remaining.firstOrNull { s ->
                val sLower = s.lowercase()
                creditedArtists.drop(1).any { credit -> singerNamesMatch(s, credit) } ||
                        mainLower?.contains(sLower) == true || sLower.contains("sean") || sLower.contains(
                    "paul"
                )
            } ?: remaining.maxByOrNull { s ->
                roleResolvedLines.count {
                    it.singer?.equals(
                        s,
                        ignoreCase = true
                    ) == true
                }
            }
            ?: remaining.firstOrNull()

            tracker.registerSinger(leadSinger, "v1")
            if (secondSinger != null) {
                tracker.registerSinger(secondSinger, "v2")
                for (other in remaining) {
                    val oLower = other.lowercase()
                    val sLower = secondSinger.lowercase()
                    if (oLower.contains(sLower) || sLower.contains(oLower) ||
                        (sLower.contains("sean") && oLower.contains("henriques")) ||
                        oLower.contains("sean") || oLower.contains("paul") || oLower.contains("henriques")
                    ) {
                        tracker.registerSinger(other, "v2")
                    }
                }
            }
        }

        val result = mutableListOf<LyricsLine>()
        for (line in roleResolvedLines) {
            if (line.isInstrumental) {
                result += line
                continue
            }
            val cleanText = stripTags(line.text).trim()
            val standalone = parseStandaloneSingerHeader(cleanText)
            if (standalone != null) {
                tracker.setSinger(standalone)
                continue
            }

            var text = cleanText
            var words = line.words
            val inlineMatch = matchInlineSingerPrefix(text)
            if (inlineMatch != null) {
                tracker.setSinger(inlineMatch.singer)
                text = inlineMatch.remainingText
                if (words.isNotEmpty()) {
                    val firstWord = words.first()
                    val wordInline = matchInlineSingerPrefix(firstWord.text)
                    if (wordInline != null) {
                        val newFirst = firstWord.copy(text = wordInline.remainingText)
                        words = listOf(newFirst) + words.drop(1)
                    } else {
                        val cleanFirst = stripTags(firstWord.text).trim()
                        if (parseStandaloneSingerHeader(cleanFirst) != null ||
                            cleanFirst.trimEnd(':', '：') == inlineMatch.singer
                        ) {
                            words = words.drop(1)
                        }
                    }
                }
            }

            val assignedSinger = line.singer ?: tracker.currentSinger
            val assignedAgent = assignedSinger?.let { tracker.getAgentForSinger(it) } ?: line.agent
            ?: tracker.currentAgent

            result += line.copy(
                text = text,
                words = words,
                agent = assignedAgent,
                singer = assignedSinger,
            )
        }
        return result
    }

    private fun reconcileLeadSpeakerAgents(
        lines: List<LyricsLine>,
        mainArtist: String?,
    ): List<LyricsLine> {
        val credits = splitArtistCredits(mainArtist)
        val leadArtist = credits.firstOrNull() ?: return lines
        val vocalLines = lines.filter { line ->
            !line.isInstrumental && line.text.isNotBlank() &&
                    !isGroupSinger(line.singer.orEmpty())
        }
        val agentLines = vocalLines.mapNotNull { line ->
            val agent = line.agent?.trim()?.lowercase()
                ?.takeIf { soloAgentId.matches(it) }
                ?: line.singer?.trim()?.lowercase()?.takeIf { soloAgentId.matches(it) }
            agent?.let { line to it }
        }
        val agentCounts = agentLines.groupingBy { it.second }.eachCount()
        if (agentCounts.size < 2) return lines

        val dominantAgent = agentCounts.maxByOrNull { it.value }?.key ?: return lines
        val totalCount = agentCounts.values.sum()
        val dominantShare = agentCounts.getValue(dominantAgent).toFloat() / totalCount
        if (dominantShare < LEAD_AGENT_DOMINANCE_THRESHOLD) return lines

        val namedLeadAgent = agentLines
            .filter { (line, _) -> line.singer?.let { singerNamesMatch(it, leadArtist) } == true }
            .groupingBy { it.second }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val dominantSpeakerIsUnresolved = agentLines.any { (line, agent) ->
            agent == dominantAgent &&
                    (line.singer.isNullOrBlank() || soloAgentId.matches(line.singer.trim()))
        }
        val assignmentsConflict = namedLeadAgent != null && namedLeadAgent != dominantAgent
        val shouldInferLeadAgent = dominantSpeakerIsUnresolved &&
                (assignmentsConflict || namedLeadAgent == null)
        if (!shouldInferLeadAgent) return lines

        val secondaryArtist = credits.getOrNull(1)
        val agentRoles = agentCounts.keys.associateWith { agent ->
            if (agent == dominantAgent) "v1" else "v2"
        }

        return lines.map { line ->
            if (isGroupSinger(line.singer.orEmpty())) return@map line
            val originalAgent = line.agent?.trim()?.lowercase()
                ?.takeIf { it in agentRoles }
                ?: line.singer?.trim()?.lowercase()?.takeIf { it in agentRoles }
                ?: return@map line
            val resolvedAgent = agentRoles.getValue(originalAgent)
            val resolvedSinger = when (resolvedAgent) {
                "v1" -> leadArtist
                "v2" -> secondaryArtist ?: line.singer?.takeUnless { singer ->
                    soloAgentId.matches(singer.trim()) || singerNamesMatch(singer, leadArtist)
                }

                else -> line.singer
            }
            line.copy(agent = resolvedAgent, singer = resolvedSinger)
        }
    }

    private fun splitArtistCredits(artist: String?): List<String> =
        artist.orEmpty()
            .split(artistCreditSeparator)
            .map { it.trim().trim('.', ' ') }
            .filter { it.isNotEmpty() }

    private fun singerNamesMatch(singer: String, artist: String): Boolean {
        val singerKey = singer.trim().lowercase().filter(Char::isLetterOrDigit)
        val artistKey = artist.trim().lowercase().filter(Char::isLetterOrDigit)
        return singerKey.isNotEmpty() && artistKey.isNotEmpty() &&
                (singerKey == artistKey || singerKey.contains(artistKey) || artistKey.contains(
                    singerKey
                ))
    }

    private fun isGroupSinger(singer: String): Boolean {
        val lower = singer.lowercase()
        return lower.contains('/') || lower.contains('&') || lower.contains("both") ||
                lower.contains("all") || lower.contains("chorus") || lower.contains("duet") ||
                lower.contains("ft.") || lower.contains("feat.") || lower.contains('合')
    }

    private data class SingerPrefixMatch(val singer: String, val remainingText: String)

    private fun parseStandaloneSingerHeader(text: String): String? {
        val clean = stripTags(text).trim()
        if (clean.isEmpty() || clean.length > 40) return null
        if (parseLrcTime(clean) != null) return null
        val match = singerHeaderRegex.matchEntire(clean) ?: return null
        val rawCandidate = match.groupValues[1].trim()
        val candidate = rawCandidate.trimEnd(':', '：', '-', '—', '–', ' ').trim()
        if (candidate.isEmpty() || candidate.length > 40) return null
        if (parseLrcTime(candidate) != null) return null

        val hasTrailingSep = clean.endsWith(":") || clean.endsWith("：") ||
                clean.endsWith("-") || clean.endsWith("—") || clean.endsWith("–")
        val trimmedClean = clean.trimEnd(':', '：', '-', '—', '–', ' ').trim()
        val isBracketed = (clean.startsWith("[") && clean.endsWith("]")) ||
                (trimmedClean.startsWith("[") && trimmedClean.endsWith("]"))
        val isParenthesized = (clean.startsWith("(") && clean.endsWith(")")) ||
                (trimmedClean.startsWith("(") && trimmedClean.endsWith(")"))
        val lower = candidate.lowercase()

        val isSingerKeyword =
            lower in setOf("both", "all", "chorus", "duet", "合", "合唱", "男", "女") ||
                    candidate in setOf("合", "合唱", "男", "女") ||
                    lower.matches(Regex("^v[1-9]$")) ||
                    lower.contains('/') || lower.contains('&') ||
                    lower.contains("ft.") || lower.contains("feat.")

        if (hasTrailingSep || isBracketed || isParenthesized || isSingerKeyword) {
            val isAllAdlibs = lower.split(Regex("\\s+")).all { it in commonAdlibs }
            if (isParenthesized && !hasTrailingSep && (lower in commonAdlibs || isAllAdlibs)) {
                return null
            }
            return candidate
        }
        return null
    }

    private fun matchInlineSingerPrefix(text: String): SingerPrefixMatch? {
        val trimmed = stripTags(text).trim()
        val bracketMatch = inlineBracketPrefixRegex.matchEntire(trimmed)
        if (bracketMatch != null) {
            val singer = bracketMatch.groupValues[1].trim()
            val remaining = bracketMatch.groupValues[2].trim()
            if (singer.isNotEmpty() && remaining.isNotEmpty() && parseLrcTime(singer) == null) {
                return SingerPrefixMatch(singer, remaining)
            }
        }
        val colonMatch = inlineColonPrefixRegex.matchEntire(trimmed)
        if (colonMatch != null) {
            val singer = colonMatch.groupValues[1].trim()
            val remaining = colonMatch.groupValues[2].trim()
            val isTime = singer.all { it.isDigit() } && remaining.firstOrNull()?.isDigit() == true
            if (singer.isNotEmpty() && remaining.isNotEmpty() && !isTime && parseLrcTime(singer) == null) {
                return SingerPrefixMatch(singer, remaining)
            }
        }
        val parenMatch = inlineParenPrefixRegex.matchEntire(trimmed)
        if (parenMatch != null) {
            val singer = parenMatch.groupValues[1].trim()
            val remaining = parenMatch.groupValues[2].trim()
            val isAllAdlibs = singer.lowercase().split(Regex("\\s+")).all { it in commonAdlibs }
            if (singer.isNotEmpty() && remaining.isNotEmpty() &&
                singer.lowercase() !in commonAdlibs && !isAllAdlibs && parseLrcTime(singer) == null
            ) {
                return SingerPrefixMatch(singer, remaining)
            }
        }
        return null
    }

    private class SingerTracker {
        private val distinctSingers = mutableListOf<String>()
        private val registeredAgents = mutableMapOf<String, String>()
        var currentSinger: String? = null
            private set
        var currentAgent: String? = null
            private set

        fun registerSinger(name: String, agent: String) {
            val trimmedName = name.trim()
            val trimmedAgent = agent.trim()
            if (trimmedName.isEmpty() || trimmedAgent.isEmpty()) return
            registeredAgents[trimmedName.lowercase()] = trimmedAgent
            val lower = trimmedName.lowercase()
            val isDuet = isDuetOrGroup(lower)
            if (!isDuet && distinctSingers.none { it.equals(trimmedName, ignoreCase = true) }) {
                distinctSingers.add(trimmedName)
            }
        }

        fun setSinger(name: String) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return
            currentSinger = trimmed
            currentAgent = assignAgent(trimmed)
        }

        fun getAgentForSinger(singer: String): String? {
            val lower = singer.trim().lowercase()
            if (lower.isEmpty()) return null
            if (registeredAgents.containsKey(lower)) return registeredAgents[lower]
            if (isDuetOrGroup(lower)) return "v3"
            return null
        }

        fun isDuetOrGroup(lower: String): Boolean =
            lower.contains('/') || lower.contains('&') ||
                    lower.contains("both") || lower.contains("all") ||
                    lower.contains("chorus") || lower.contains("duet") ||
                    lower.contains("ft.") || lower.contains("feat.") ||
                    lower.contains('合')

        private fun assignAgent(name: String): String {
            val lower = name.lowercase()
            if (lower.matches(Regex("^v\\d+$"))) return lower
            if (registeredAgents.containsKey(lower)) {
                return registeredAgents.getValue(lower)
            }
            if (isDuetOrGroup(lower)) {
                return "v3"
            }
            val existingIndex = distinctSingers.indexOfFirst { it.equals(name, ignoreCase = true) }
            val index = if (existingIndex >= 0) {
                existingIndex
            } else {
                distinctSingers.add(name)
                distinctSingers.lastIndex
            }
            return when (index) {
                0 -> "v1"
                1 -> "v2"
                else -> "v${index + 2}"
            }
        }
    }

    fun parseTextLines(raw: String): List<LyricsLine> {
        if (raw.isBlank()) return emptyList()
        val result = mutableListOf<LyricsLine>()
        var hasTimestamp = false
        val tracker = SingerTracker()

        for (rawLine in raw.lineSequence()) {
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty()) continue

            val match = lrcLine.matchEntire(trimmedLine)
            if (match == null) {
                val cleanLine = stripTags(trimmedLine).trim()
                val standalone = parseStandaloneSingerHeader(cleanLine)
                if (standalone != null) {
                    tracker.setSinger(standalone)
                }
                continue
            }

            val startMs = parseLrcTime(match.groupValues[1]) ?: continue
            hasTimestamp = true
            val body = match.groupValues[2].trim()

            val cleanBody = stripTags(body).trim()
            val standalone = parseStandaloneSingerHeader(cleanBody)
            if (standalone != null) {
                tracker.setSinger(standalone)
                continue
            }

            val words = wordTag.findAll(body).mapNotNull { wordMatch ->
                val start = parseLrcTime(wordMatch.groupValues[1]) ?: return@mapNotNull null
                val rawWord = stripTags(wordMatch.groupValues[2])
                val text = if (rawWord.endsWith(" ")) "${rawWord.trim()} " else rawWord.trim()
                text.takeIf { it.isNotEmpty() }?.let { LyricsWord(it, start) }
            }.toList()

            var text = if (words.isNotEmpty()) {
                stripWordTags(body).trim()
            } else {
                stripTags(body).trim()
            }

            val inlineMatch = matchInlineSingerPrefix(text)
            if (inlineMatch != null) {
                tracker.setSinger(inlineMatch.singer)
                text = inlineMatch.remainingText
            }

            var cleanedWords = words
            if (inlineMatch != null && words.isNotEmpty()) {
                val firstWord = words.first()
                val wordInline = matchInlineSingerPrefix(firstWord.text)
                if (wordInline != null) {
                    val newFirst = firstWord.copy(text = wordInline.remainingText)
                    cleanedWords = listOf(newFirst) + words.drop(1)
                } else {
                    val cleanFirst = stripTags(firstWord.text).trim()
                    if (parseStandaloneSingerHeader(cleanFirst) != null ||
                        cleanFirst.trimEnd(':', '：') == inlineMatch.singer
                    ) {
                        cleanedWords = words.drop(1)
                    }
                }
            }

            if (text.isNotEmpty() || cleanedWords.isNotEmpty()) {
                val lineText = text.ifEmpty { cleanedWords.joinToString("") { it.text } }
                result += LyricsLine(
                    text = lineText,
                    startMs = startMs,
                    words = cleanedWords,
                    agent = tracker.currentAgent,
                    singer = tracker.currentSinger,
                )
            }
        }
        if (hasTimestamp && result.isNotEmpty()) return result

        val plainTracker = SingerTracker()
        val plainResult = mutableListOf<LyricsLine>()
        for (line in raw.lineSequence().map(String::trim).filter(String::isNotEmpty)) {
            val standalone = parseStandaloneSingerHeader(line)
            if (standalone != null) {
                plainTracker.setSinger(standalone)
                continue
            }
            var text = line
            val inlineMatch = matchInlineSingerPrefix(text)
            if (inlineMatch != null) {
                plainTracker.setSinger(inlineMatch.singer)
                text = inlineMatch.remainingText
            }
            if (text.isNotEmpty()) {
                plainResult += LyricsLine(
                    text = text,
                    startMs = 0L,
                    agent = plainTracker.currentAgent,
                    singer = plainTracker.currentSinger,
                )
            }
        }
        return plainResult
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
            var agent: String?,
            val role: String?,
            val lang: String?,
            val text: StringBuilder = StringBuilder(),
            val words: MutableList<LyricsWord> = mutableListOf(),
            val backgroundWords: MutableList<LyricsWord> = mutableListOf(),
            val translations: MutableList<LyricsTranslation> = mutableListOf(),
            var romanization: String? = null,
        )

        data class Span(
            val begin: Long?,
            val end: Long?,
            val role: String?,
            val lang: String?,
            val agent: String?,
            val text: StringBuilder = StringBuilder(),
            var hasTimedChild: Boolean = false,
        )

        val output = mutableListOf<LyricsLine>()
        val agentMap = mutableMapOf<String, String>()
        var currentAgentId: String? = null
        val currentAgentText = StringBuilder()
        var inAgentNameTag = false
        var agentNameFromTag: String? = null

        var paragraph: Paragraph? = null
        val spanStack = mutableListOf<Span>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name.substringAfter(':').lowercase()
                    when (tagName) {
                        "agent" -> {
                            currentAgentId = parser.findIdAttribute()
                            currentAgentText.clear()
                            inAgentNameTag = false
                            agentNameFromTag = null
                        }

                        "name" -> {
                            if (currentAgentId != null) {
                                inAgentNameTag = true
                                currentAgentText.clear()
                            }
                        }

                        "p" -> {
                            spanStack.clear()
                            val pAgent = parser.findAgentAttribute()
                            paragraph = Paragraph(
                                begin = parser.attributeValue("begin")?.let(::parseTtmlTime),
                                end = parser.attributeValue("end")?.let(::parseTtmlTime)
                                    ?: parser.attributeValue("dur")?.let(::parseTtmlTime)
                                        ?.let { dur ->
                                            parser.attributeValue("begin")?.let(::parseTtmlTime)
                                                ?.plus(dur)
                                        },
                                agent = pAgent,
                                role = parser.attributeValue("role")?.lowercase(),
                                lang = parser.attributeValue("lang")
                                    ?: parser.attributeValue("xml:lang"),
                            )
                        }

                        "span" -> {
                            val sAgent = parser.findAgentAttribute()
                            val sBegin = parser.attributeValue("begin")?.let(::parseTtmlTime)
                            val sEnd = parser.attributeValue("end")?.let(::parseTtmlTime)
                                ?: parser.attributeValue("dur")?.let(::parseTtmlTime)?.let { dur ->
                                    sBegin?.plus(dur)
                                }
                            spanStack += Span(
                                begin = sBegin,
                                end = sEnd,
                                role = parser.attributeValue("role")?.lowercase(),
                                lang = parser.attributeValue("lang")
                                    ?: parser.attributeValue("xml:lang"),
                                agent = sAgent,
                            )
                            if (paragraph != null && paragraph.agent == null && sAgent != null) {
                                paragraph.agent = sAgent
                            }
                        }
                    }
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text.orEmpty()
                    if (currentAgentId != null) {
                        currentAgentText.append(text)
                    }
                    val activeRole = spanStack.asReversed().firstNotNullOfOrNull { it.role }
                    if (spanStack.isNotEmpty()) {
                        spanStack.forEach { it.text.append(text) }
                        if (activeRole != "x-translation" && activeRole != "x-roman" && activeRole != "x-bg") {
                            paragraph?.text?.append(text)
                        }
                    } else {
                        paragraph?.text?.append(text)
                        if (!text.contains('\n') && text.contains(' ') && paragraph?.words?.isNotEmpty() == true) {
                            val lastIdx = paragraph.words.lastIndex
                            val lastWord = paragraph.words[lastIdx]
                            if (!lastWord.text.endsWith(" ")) {
                                paragraph.words[lastIdx] = lastWord.copy(text = "${lastWord.text} ")
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tagName = parser.name.substringAfter(':').lowercase()
                    when (tagName) {
                        "name" -> {
                            if (inAgentNameTag) {
                                agentNameFromTag = currentAgentText.toString().trim()
                                inAgentNameTag = false
                                currentAgentText.clear()
                            }
                        }

                        "agent" -> {
                            val id = currentAgentId
                            if (id != null) {
                                val name = agentNameFromTag?.takeIf { it.isNotEmpty() }
                                    ?: currentAgentText.toString().trim().takeIf { it.isNotEmpty() }
                                if (name != null) {
                                    agentMap[id] = name
                                }
                            }
                            currentAgentId = null
                            currentAgentText.clear()
                            inAgentNameTag = false
                            agentNameFromTag = null
                        }

                        "span" -> {
                            val completed =
                                if (spanStack.isEmpty()) null else spanStack.removeAt(spanStack.lastIndex)
                            val parent = paragraph
                            if (completed != null && parent != null) {
                                var spanText = completed.text.toString()
                                if (parent.words.isEmpty() && parent.backgroundWords.isEmpty()) {
                                    spanText = spanText.trimStart()
                                }
                                if (parent.agent == null && completed.agent != null) {
                                    parent.agent = completed.agent
                                }
                                val effectiveRole = completed.role
                                    ?: spanStack.asReversed().firstNotNullOfOrNull { it.role }
                                val parentSpan = spanStack.lastOrNull()
                                if (completed.begin != null || completed.hasTimedChild) {
                                    parentSpan?.hasTimedChild = true
                                }
                                when (effectiveRole) {
                                    "x-roman" -> {
                                        if (completed.role == "x-roman") {
                                            val trimmed = spanText.trim()
                                            if (trimmed.isNotEmpty()) {
                                                parent.romanization = trimmed
                                            }
                                        }
                                    }

                                    "x-translation" -> {
                                        if (completed.role == "x-translation") {
                                            val trimmed = spanText.trim()
                                            if (trimmed.isNotEmpty()) {
                                                val lang = completed.lang ?: parent.lang ?: "en"
                                                parent.translations += LyricsTranslation(
                                                    lang,
                                                    trimmed
                                                )
                                            }
                                        }
                                    }

                                    "x-bg" -> {
                                        if (completed.begin != null && !completed.hasTimedChild && spanText.isNotEmpty()) {
                                            parent.backgroundWords += LyricsWord(
                                                spanText,
                                                completed.begin,
                                                completed.end,
                                            )
                                        }
                                    }

                                    else -> {
                                        if (completed.begin != null && !completed.hasTimedChild && spanText.isNotEmpty()) {
                                            parent.words += LyricsWord(
                                                spanText,
                                                completed.begin,
                                                completed.end,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "p" -> {
                            val completed = paragraph
                            if (completed != null) {
                                val pRole = completed.role
                                val pText = completed.text.toString().trim()
                                val start = completed.begin
                                    ?: (completed.words + completed.backgroundWords)
                                        .minOfOrNull { it.startMs }
                                if (pRole == "x-roman") {
                                    val targetIdx =
                                        output.indexOfLast { it.startMs == start }
                                            .takeIf { it >= 0 }
                                            ?: output.lastIndex.takeIf { it >= 0 }
                                    if (targetIdx != null && pText.isNotEmpty()) {
                                        output[targetIdx] =
                                            output[targetIdx].copy(romanization = pText)
                                    }
                                } else if (pRole == "x-translation") {
                                    val targetIdx =
                                        output.indexOfLast { it.startMs == start }
                                            .takeIf { it >= 0 }
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
                                    val backgroundWords =
                                        completed.backgroundWords.sortedBy { it.startMs }
                                    val lineStart = start ?: words.firstOrNull()?.startMs
                                    ?: backgroundWords.firstOrNull()?.startMs
                                    if (lineStart != null && (pText.isNotEmpty() || words.isNotEmpty() || backgroundWords.isNotEmpty())) {
                                        val agentId = completed.agent
                                        val singerName = agentId?.let { id ->
                                            agentMap[id] ?: agentMap.entries.firstOrNull {
                                                it.key.equals(
                                                    id,
                                                    ignoreCase = true
                                                )
                                            }?.value ?: id
                                        }
                                        output += LyricsLine(
                                            text = if (words.isNotEmpty()) words.joinToString("") { it.text }
                                                .trim() else pText,
                                            startMs = lineStart,
                                            endMs = completed.end
                                                ?: (words + backgroundWords).mapNotNull { it.endMs }
                                                    .maxOrNull()
                                                ?: 0L,
                                            words = words,
                                            backgroundWords = backgroundWords,
                                            agent = agentId,
                                            singer = singerName,
                                            translations = completed.translations,
                                            romanization = completed.romanization,
                                        )
                                    }
                                }
                            }
                            paragraph = null
                            spanStack.clear()
                        }
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

        val agentMap = mutableMapOf<String, String>()
        fun collectAgents(node: org.w3c.dom.Node) {
            if (node is org.w3c.dom.Element) {
                val local = node.localName ?: node.tagName.substringAfter(':')
                if (local.equals("agent", ignoreCase = true)) {
                    val id = node.findIdAttribute()
                    if (id != null) {
                        var name: String? = null
                        val childNodes = node.childNodes
                        for (k in 0 until childNodes.length) {
                            val child = childNodes.item(k)
                            if (child is org.w3c.dom.Element) {
                                val childLocal =
                                    child.localName ?: child.tagName.substringAfter(':')
                                if (childLocal.equals("name", ignoreCase = true)) {
                                    val t = child.textContent?.trim()
                                    if (!t.isNullOrEmpty()) {
                                        name = t
                                        break
                                    }
                                }
                            }
                        }
                        if (name.isNullOrBlank()) {
                            name = node.textContent?.trim()
                        }
                        if (!name.isNullOrBlank()) {
                            agentMap[id] = name
                        }
                    }
                }
            }
            val children = node.childNodes
            for (k in 0 until children.length) {
                collectAgents(children.item(k))
            }
        }
        collectAgents(doc)

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
            var agent = p.findAgentAttribute()
            val role = p.attributeValue("role")?.lowercase()
            val lang = p.attributeValue("lang")

            val pText = StringBuilder()
            val words = mutableListOf<LyricsWord>()
            val backgroundWords = mutableListOf<LyricsWord>()
            val translations = mutableListOf<LyricsTranslation>()
            var romanization: String? = null

            fun visit(
                node: org.w3c.dom.Node,
                inheritedRole: String? = null,
                inheritedLang: String? = null
            ): Boolean {
                if (node is org.w3c.dom.Element) {
                    val localName = node.localName ?: node.tagName.substringAfter(':')
                    if (localName.equals("span", ignoreCase = true)) {
                        val spanRole = node.attributeValue("role")?.lowercase()
                        val effectiveRole = spanRole ?: inheritedRole
                        val spanLang = node.attributeValue("lang") ?: inheritedLang
                        val spanAgent = node.findAgentAttribute()
                        if (agent == null && spanAgent != null) agent = spanAgent

                        var hasTimedChild = false
                        val children = node.childNodes
                        for (childIndex in 0 until children.length) {
                            hasTimedChild = visit(
                                children.item(childIndex),
                                effectiveRole,
                                spanLang
                            ) || hasTimedChild
                        }

                        val spanText = node.textContent.orEmpty()
                        val spanBegin = node.attributeValue("begin")?.let(::parseTtmlTime)
                        val spanEnd = node.attributeValue("end")?.let(::parseTtmlTime)
                            ?: node.attributeValue("dur")?.let(::parseTtmlTime)?.let { duration ->
                                spanBegin?.plus(duration)
                            }

                        when (spanRole) {
                            "x-roman" -> spanText.trim().takeIf { it.isNotEmpty() }
                                ?.let { romanization = it }

                            "x-translation" -> spanText.trim().takeIf { it.isNotEmpty() }?.let {
                                translations += LyricsTranslation(spanLang ?: lang ?: "en", it)
                            }

                            else -> {
                                if (spanBegin != null && !hasTimedChild && spanText.isNotEmpty() &&
                                    effectiveRole != "x-roman" && effectiveRole != "x-translation"
                                ) {
                                    val word = LyricsWord(spanText, spanBegin, spanEnd)
                                    if (effectiveRole == "x-bg") backgroundWords += word else words += word
                                    hasTimedChild = true
                                }
                            }
                        }
                        return hasTimedChild
                    }

                    val children = node.childNodes
                    var hasTimedChild = false
                    for (childIndex in 0 until children.length) {
                        hasTimedChild = visit(
                            children.item(childIndex),
                            inheritedRole,
                            inheritedLang
                        ) || hasTimedChild
                    }
                    return hasTimedChild
                }

                if (node.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                    val nodeText = node.textContent.orEmpty()
                    if (inheritedRole != "x-bg" && inheritedRole != "x-translation" && inheritedRole != "x-roman") {
                        pText.append(nodeText)
                    }
                    if (!nodeText.contains('\n') && nodeText.contains(' ')) {
                        val targetWords = if (inheritedRole == "x-bg") backgroundWords else words
                        val lastIdx = targetWords.lastIndex
                        if (lastIdx >= 0 && !targetWords[lastIdx].text.endsWith(" ")) {
                            targetWords[lastIdx] =
                                targetWords[lastIdx].copy(text = "${targetWords[lastIdx].text} ")
                        }
                    }
                }
                return false
            }

            val childNodes = p.childNodes
            for (j in 0 until childNodes.length) visit(childNodes.item(j))

            val text = pText.toString().trim()
            val sortedWords = words.sortedBy { it.startMs }
            val sortedBackgroundWords = backgroundWords.sortedBy { it.startMs }
            val lineStart = begin
                ?: (sortedWords + sortedBackgroundWords).minOfOrNull { it.startMs }

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
                if (lineStart != null && (text.isNotEmpty() || sortedWords.isNotEmpty() || sortedBackgroundWords.isNotEmpty())) {
                    val singer = agent?.let { id ->
                        agentMap[id] ?: agentMap.entries.firstOrNull {
                            it.key.equals(
                                id,
                                ignoreCase = true
                            )
                        }?.value ?: id
                    }
                    output += LyricsLine(
                        text = if (sortedWords.isNotEmpty()) sortedWords.joinToString("") { it.text }
                            .trim() else text,
                        startMs = lineStart,
                        endMs = endMs
                            ?: (sortedWords + sortedBackgroundWords).mapNotNull { it.endMs }
                                .maxOrNull()
                            ?: 0L,
                        words = sortedWords,
                        backgroundWords = sortedBackgroundWords,
                        agent = agent,
                        singer = singer,
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

    private fun org.w3c.dom.Element.attributeValue(name: String): String? =
        (0 until attributes.length).firstNotNullOfOrNull { index ->
            val attribute = attributes.item(index)
            val localName = attribute.localName ?: attribute.nodeName.substringAfter(':')
            if (localName.equals(name, ignoreCase = true)) attribute.nodeValue else null
        }?.takeIf { it.isNotBlank() }

    private fun XmlPullParser.findAgentAttribute(): String? {
        for (i in 0 until attributeCount) {
            val name = getAttributeName(i) ?: ""
            val local = name.substringAfter(':')
            val prefix = getAttributePrefix(i).orEmpty()
            val qName = if (prefix.isNotEmpty() && !name.contains(':')) "$prefix:$name" else name
            if (local.equals("agent", ignoreCase = true) ||
                name.endsWith("agent", ignoreCase = true) ||
                qName.endsWith("agent", ignoreCase = true)
            ) {
                val v = getAttributeValue(i)
                if (!v.isNullOrBlank()) return v.trim()
            }
        }
        return null
    }

    private fun XmlPullParser.findIdAttribute(): String? {
        for (i in 0 until attributeCount) {
            val name = getAttributeName(i) ?: ""
            val local = name.substringAfter(':')
            val prefix = getAttributePrefix(i).orEmpty()
            val qName = if (prefix.isNotEmpty() && !name.contains(':')) "$prefix:$name" else name
            if (local.equals("id", ignoreCase = true) ||
                name.endsWith("id", ignoreCase = true) ||
                qName.endsWith("id", ignoreCase = true)
            ) {
                val v = getAttributeValue(i)
                if (!v.isNullOrBlank()) return v.trim()
            }
        }
        return null
    }

    private fun org.w3c.dom.Element.findAgentAttribute(): String? {
        val direct = getAttribute("agent").takeIf { it.isNotBlank() }
            ?: getAttribute("ttm:agent").takeIf { it.isNotBlank() }
        if (direct != null) return direct.trim()
        val attrs = attributes ?: return null
        for (i in 0 until attrs.length) {
            val item = attrs.item(i) ?: continue
            val name = item.nodeName ?: ""
            val local = item.localName ?: name.substringAfter(':')
            if (local.equals("agent", ignoreCase = true) || name.endsWith(
                    "agent",
                    ignoreCase = true
                )
            ) {
                val v = item.nodeValue
                if (!v.isNullOrBlank()) return v.trim()
            }
        }
        return null
    }

    private fun org.w3c.dom.Element.findIdAttribute(): String? {
        val direct = getAttribute("xml:id").takeIf { it.isNotBlank() }
            ?: getAttribute("id").takeIf { it.isNotBlank() }
            ?: getAttribute("ttm:id").takeIf { it.isNotBlank() }
        if (direct != null) return direct.trim()
        val attrs = attributes ?: return null
        for (i in 0 until attrs.length) {
            val item = attrs.item(i) ?: continue
            val name = item.nodeName ?: ""
            val local = item.localName ?: name.substringAfter(':')
            if (local.equals("id", ignoreCase = true) || name.endsWith("id", ignoreCase = true)) {
                val v = item.nodeValue
                if (!v.isNullOrBlank()) return v.trim()
            }
        }
        return null
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
