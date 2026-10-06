package org.shilpo.laboon.home

object TrackIdentity {

    private const val MBID_PREFIX = "mbid:"
    private const val META_PREFIX = "meta:"
    private const val META_SEPARATOR = ":::"

    private val BRACKET_QUALIFIER_WORDS = listOf(
        "remaster",
        "remastered",
        "explicit",
        "deluxe",
        "bonus",
        "version",
        "anniversary",
        "live",
        "remix",
        "mono",
        "stereo",
        "edit",
        "radio",
        "single",
        "album",
        "ep",
        "demo",
        "take",
        "expanded",
        "reissue",
        "clean",
    )

    private val EDGE_NOISE_CHARS = charArrayOf('-', '_', '.', ',', '!', '?')

    fun keyOf(track: HomeTrack): String = keyOf(track.mbid, track.title, track.artist)

    fun keyOf(mbid: String?, title: String, artist: String): String {
        val trimmedMbid = mbid?.trim().orEmpty()
        if (trimmedMbid.isNotEmpty()) {
            return MBID_PREFIX + trimmedMbid.lowercase()
        }
        return META_PREFIX + normalizedTitle(title) + META_SEPARATOR + normalizedArtist(artist)
    }

    fun isSameTrack(a: HomeTrack, b: HomeTrack): Boolean = keyOf(a) == keyOf(b)

    fun normalizedTitle(raw: String): String = normalize(raw, stripQualifiers = true)

    fun normalizedArtist(raw: String): String = normalize(raw, stripQualifiers = false)

    private fun normalize(raw: String, stripQualifiers: Boolean): String {
        var value = raw.trim().lowercase().replace(WHITESPACE_RUN, " ")

        if (stripQualifiers) {
            value = removeTrailingDashQualifier(value)
            value = removeEnclosedQualifierGroups(value)
            value = removeTrailingDashQualifier(value)
        }

        value = removeTrailingFeaturedClause(value)
        value = value.trim().replace(TRAILING_NOISE, "")
        return value.trim().trim(*EDGE_NOISE_CHARS).trim(*TRAILING_NOISE_CHARS)
    }

    private val WHITESPACE_RUN = Regex("\\s+")

    private val TRAILING_NOISE = Regex("[\\s&+,]+$")
    private val TRAILING_NOISE_CHARS = charArrayOf('&', '+', ',')

    private fun removeTrailingDashQualifier(value: String): String {
        var result = value
        var separator = result.lastIndexOf(" - ")
        while (separator >= 0) {
            if (isQualifierPhrase(result.substring(separator + 3))) {
                return result.substring(0, separator).trim()
            }
            separator = result.lastIndexOf(" - ", separator - 1)
        }
        return result
    }

    private fun removeEnclosedQualifierGroups(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '(' && char != '[') {
                out.append(char)
                index++
                continue
            }
            val closeChar = if (char == '(') ')' else ']'
            val close = value.indexOf(closeChar, index + 1)
            if (close < 0) {
                out.append(value.substring(index))
                break
            }
            val inner = value.substring(index + 1, close)
            if (isQualifierPhrase(inner)) {
                val head = out.toString().trim()
                val tail = value.substring(close + 1).trimStart()
                out.setLength(0)
                when {
                    head.isEmpty() -> Unit
                    tail.isEmpty() -> out.append(head)
                    else -> out.append(head).append(' ')
                }
                index = close + 1
                continue
            }
            out.append(value, index, close + 1)
            index = close + 1
        }
        return out.toString().replace(WHITESPACE_RUN, " ").trim()
    }

    private fun isQualifierPhrase(phrase: String): Boolean {
        if (phrase.isBlank()) return false
        if (YEAR_TOKEN.containsMatchIn(phrase)) return true
        return phrase.split(' ', '\t').any { token ->
            val word = token.trim(*EDGE_NOISE_CHARS, '(', ')', '[', ']')
            BRACKET_QUALIFIER_WORDS.any { qualifier -> word.startsWith(qualifier) }
        }
    }

    private val YEAR_TOKEN = Regex("(^|\\s)(19\\d\\d|20\\d\\d)($|\\s|[)\\]])")
    private val FEATURED_CLAUSE =
        Regex("(?<=[\\s(\\[])\\s*(feat|ft|featuring)\\.?\\s*[)\\]]?.*$", RegexOption.IGNORE_CASE)

    private val DANGLING_GROUP_OPENERS = charArrayOf('(', '[')

    private fun removeTrailingFeaturedClause(value: String): String {
        val match = FEATURED_CLAUSE.find(value) ?: return value
        return value.substring(0, match.range.first).trim().trimEnd(*DANGLING_GROUP_OPENERS).trim()
    }
}
