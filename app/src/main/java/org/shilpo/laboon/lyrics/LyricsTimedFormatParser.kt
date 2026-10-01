package org.shilpo.laboon.lyrics

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.Base64
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal object LyricsTimedFormatParser {
    private val krcKey = byteArrayOf(
        0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69,
    )
    private val qrcKey = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)
    private val krcWord = Regex("<(\\d+),(\\d+),[^>]*>([^<]*)")
    private val qrcWord = Regex("\\((\\d+),(\\d+)\\)")
    private val yrcWord = Regex("\\((\\d+),(\\d+)(?:,\\d+)?\\)([^()]*)")

    fun decryptKrc(encoded: String): String? = runCatching {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size > 4)
        val body = bytes.copyOfRange(4, bytes.size)
        body.indices.forEach {
            body[it] = (body[it].toInt() xor krcKey[it % krcKey.size].toInt()).toByte()
        }
        inflate(body)
    }.getOrNull()

    fun decryptQrc(hex: String): String? = runCatching {
        val compact = hex.filterNot(Char::isWhitespace)
        require(compact.length >= 16 && compact.length % 2 == 0)
        val encrypted = ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(qrcKey, "DESede"))
        val size = ((encrypted.size + 7) / 8) * 8
        val padded = encrypted.copyOf(size)
        val plain = cipher.doFinal(padded).copyOf(encrypted.size)
        inflate(plain)
    }.getOrNull()

    fun extractQrcXmlContent(raw: String): String? = runCatching {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(raw))
        }
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                for (index in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(index) == "LyricContent") return parser.getAttributeValue(
                        index
                    )
                }
            }
            event = parser.next()
        }
        null
    }.getOrNull()

    fun parseKrc(raw: String): List<LyricsLine> = raw.lineSequence().mapNotNull { line ->
        val match =
            Regex("^\\[(\\d+),(\\d+)](.*)$").matchEntire(line.trim()) ?: return@mapNotNull null
        val start = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val duration = match.groupValues[2].toLongOrNull() ?: 0L
        val words = krcWord.findAll(match.groupValues[3]).mapNotNull { word ->
            val offset = word.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val wordDuration = word.groupValues[2].toLongOrNull() ?: 0L
            val text = word.groupValues[3]
            text.takeIf(String::isNotEmpty)
                ?.let { LyricsWord(it, start + offset, start + offset + wordDuration) }
        }.toList()
        if (words.isEmpty()) null else LyricsLine(
            words.joinToString("") { it.text },
            start,
            start + duration,
            words
        )
    }.toList()

    fun parseQrc(raw: String): List<LyricsLine> = raw.lineSequence().mapNotNull { rawLine ->
        val line = rawLine.trim()
        val header = Regex("^\\[(\\d+),(\\d+)](.*)$").matchEntire(line) ?: return@mapNotNull null
        val start = header.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val duration = header.groupValues[2].toLongOrNull() ?: 0L
        val body = header.groupValues[3]
        val words = mutableListOf<LyricsWord>()
        var cursor = 0
        var pending = StringBuilder()
        for (match in qrcWord.findAll(body)) {
            pending.append(body.substring(cursor, match.range.first))
            val wordStart = match.groupValues[1].toLongOrNull() ?: continue
            val wordDuration = match.groupValues[2].toLongOrNull() ?: 0L
            val text = pending.toString()
            if (text.isNotEmpty()) words += LyricsWord(text, wordStart, wordStart + wordDuration)
            pending = StringBuilder()
            cursor = match.range.last + 1
        }
        pending.append(body.substring(cursor))
        if (pending.isNotEmpty() && words.isNotEmpty()) {
            val last = words.removeAt(words.lastIndex)
            words += last.copy(text = last.text + pending.toString())
        }
        if (words.isEmpty()) null else LyricsLine(
            words.joinToString("") { it.text },
            start,
            start + duration,
            words
        )
    }.toList()

    fun parseYrc(raw: String): List<LyricsLine> = raw.lineSequence().mapNotNull { rawLine ->
        val line = rawLine.trim()
        val header = Regex("^\\[(\\d+),(\\d+)](.*)$").matchEntire(line) ?: return@mapNotNull null
        val start = header.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val duration = header.groupValues[2].toLongOrNull() ?: 0L
        val words = yrcWord.findAll(header.groupValues[3]).mapNotNull { match ->
            val offset = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val wordDuration = match.groupValues[2].toLongOrNull() ?: 0L
            val text = match.groupValues[3]
            text.takeIf(String::isNotEmpty)
                ?.let { LyricsWord(it, start + offset, start + offset + wordDuration) }
        }.toList()
        if (words.isEmpty()) null else LyricsLine(
            words.joinToString("") { it.text },
            start,
            start + duration,
            words
        )
    }.toList()

    fun asEnhancedLrc(lines: List<LyricsLine>): String = lines.joinToString("\n") { line ->
        val body = if (line.words.isEmpty()) line.text else line.words.joinToString("") { word ->
            "<${formatTime(word.startMs)}>${word.text}"
        }
        "[${formatTime(line.startMs)}]$body"
    }

    fun decodeBase64OrRaw(raw: String): String? = runCatching {
        String(Base64.getDecoder().decode(raw), Charsets.UTF_8)
    }.getOrNull() ?: raw.takeIf(String::isNotBlank)

    private fun inflate(bytes: ByteArray): String {
        val decompressed = InflaterInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        val withoutBom = if (decompressed.size >= 3 &&
            decompressed[0] == 0xef.toByte() && decompressed[1] == 0xbb.toByte() && decompressed[2] == 0xbf.toByte()
        ) decompressed.copyOfRange(3, decompressed.size) else decompressed
        return String(withoutBom, Charsets.UTF_8)
    }

    private fun formatTime(ms: Long): String =
        "%02d:%02d.%03d".format(ms / 60_000L, (ms / 1_000L) % 60L, ms % 1_000L)
}
