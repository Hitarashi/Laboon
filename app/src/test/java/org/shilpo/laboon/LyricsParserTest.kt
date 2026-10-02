package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.lyrics.LyricsParser
import org.shilpo.laboon.lyrics.LyricsSyncLevel
import org.shilpo.laboon.lyrics.LyricsWord

class LyricsParserTest {

    @Test
    fun `inserts instrumental break for intro greater than or equal to 5 seconds on line synced lyrics`() {
        val lrc = """
            [00:08.00]First vocal line
            [00:12.00]Second vocal line
        """.trimIndent()

        val parsed = LyricsParser.fromText(lrc, durationMs = 30_000L)
        assertEquals(LyricsSyncLevel.Line, parsed.syncLevel)

        val firstLine = parsed.lines.first()
        assertTrue("Expected first line to be instrumental intro", firstLine.isInstrumental)
        assertEquals(0L, firstLine.startMs)
        assertEquals(8_000L, firstLine.endMs)

        val secondLine = parsed.lines[1]
        assertFalse(secondLine.isInstrumental)
        assertEquals("First vocal line", secondLine.text)
        assertEquals(8_000L, secondLine.startMs)
    }

    @Test
    fun `inserts instrumental break for gap greater than or equal to 5 seconds between lines`() {
        val lrc = """
            [00:02.00]Line 1
            [00:15.00]Line 2
        """.trimIndent()

        val parsed = LyricsParser.fromText(lrc, durationMs = 30_000L)
        val instrumental = parsed.lines.firstOrNull { it.isInstrumental }
        assertTrue("Expected instrumental break between lines", instrumental != null)
        assertEquals(true, instrumental?.isInstrumental)
        assertTrue(parsed.lines.any { it.text == "Line 1" })
        assertTrue(parsed.lines.any { it.text == "Line 2" })
    }

    @Test
    fun `word synced lyrics retain agent tags`() {
        val lines = listOf(
            LyricsLine(
                text = "Singer One",
                startMs = 6_000L,
                endMs = 10_000L,
                words = listOf(
                    LyricsWord("Singer", 6_000L, 7_500L),
                    LyricsWord("One", 7_500L, 10_000L)
                ),
                agent = "v1",
            ),
            LyricsLine(
                text = "Singer Two",
                startMs = 12_000L,
                endMs = 16_000L,
                words = listOf(
                    LyricsWord("Singer", 12_000L, 13_500L),
                    LyricsWord("Two", 13_500L, 16_000L)
                ),
                agent = "v2",
            ),
        )

        val parsed = LyricsParser.fromText("", durationMs = 20_000L, structuredLines = lines)
        assertEquals(LyricsSyncLevel.Word, parsed.syncLevel)

        val vocalLines = parsed.lines.filter { !it.isInstrumental }
        assertEquals(2, vocalLines.size)
        assertEquals("v1", vocalLines[0].agent)
        assertEquals("v2", vocalLines[1].agent)

        // Also verify intro instrumental break is inserted (since first vocal line starts at 6s >= 5s)
        val firstLine = parsed.lines.first()
        assertTrue("Intro instrumental break should be inserted", firstLine.isInstrumental)
        assertEquals(0L, firstLine.startMs)
        assertEquals(6_000L, firstLine.endMs)
    }

    @Test
    fun `Romanizer detects non-Latin scripts correctly`() {
        assertTrue(org.shilpo.laboon.lyrics.Romanizer.needsRomanization("আমি বাংলায় গান গাই")) // Bengali
        assertTrue(org.shilpo.laboon.lyrics.Romanizer.needsRomanization("नमस्ते दुनिया")) // Devanagari
        assertTrue(org.shilpo.laboon.lyrics.Romanizer.needsRomanization("こんにちは")) // Japanese Hiragana
        assertTrue(org.shilpo.laboon.lyrics.Romanizer.needsRomanization("東京")) // Kanji
        assertTrue(org.shilpo.laboon.lyrics.Romanizer.needsRomanization("안녕하세요")) // Korean Hangul
        assertTrue(org.shilpo.laboon.lyrics.Romanizer.needsRomanization("Привет мир")) // Cyrillic
        assertTrue(org.shilpo.laboon.lyrics.Romanizer.needsRomanization("مرحبا بالعالم")) // Arabic

        assertFalse(org.shilpo.laboon.lyrics.Romanizer.needsRomanization("Hello World"))
        assertFalse(org.shilpo.laboon.lyrics.Romanizer.needsRomanization("La vie en rose 123!"))
        assertFalse(org.shilpo.laboon.lyrics.Romanizer.needsRomanization(""))
    }

    @Test
    fun `TTML parser extracts x-translation and x-roman roles from spans`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:04.00" end="00:08.00">
                    <span begin="00:04.00">আমার</span>
                    <span begin="00:05.50">সোনার</span>
                    <span begin="00:07.00">বাংলা</span>
                    <span ttm:role="x-roman">Amar shonar bangla</span>
                    <span ttm:role="x-translation" xml:lang="en">My golden Bengal</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = LyricsParser.fromText("", durationMs = 15_000L, ttmlRaw = ttml)
        val vocalLines = parsed.lines.filter { !it.isInstrumental }
        assertEquals(1, vocalLines.size)
        val line = vocalLines[0]
        assertEquals("আমারসোনারবাংলা", line.text)
        assertEquals("Amar shonar bangla", line.romanization)
        assertEquals(1, line.translations.size)
        assertEquals("en", line.translations[0].language)
        assertEquals("My golden Bengal", line.translations[0].text)
    }

    @Test
    fun `TTML parser extracts x-translation and x-roman roles from paragraphs`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:02.00" end="00:06.00">Bonjour le monde</p>
                  <p begin="00:02.00" end="00:06.00" ttm:role="x-translation" xml:lang="en">Hello world</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = LyricsParser.fromText("", durationMs = 10_000L, ttmlRaw = ttml)
        val vocalLines = parsed.lines.filter { !it.isInstrumental }
        assertEquals(1, vocalLines.size)
        val line = vocalLines[0]
        assertEquals("Bonjour le monde", line.text)
        assertEquals(1, line.translations.size)
        assertEquals("en", line.translations[0].language)
        assertEquals("Hello world", line.translations[0].text)
    }
}
