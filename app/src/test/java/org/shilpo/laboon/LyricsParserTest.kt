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

    @Test
    fun `LRC parser identifies standalone singer headers and assigns agents`() {
        val lrc = """
            [00:02.00]Sia:
            [00:04.00]First line by Sia
            [00:08.00]Second line by Sia
            [00:12.00][Sean Paul]
            [00:14.00]First line by Sean Paul
            [00:18.00]Sia/ Sean Paul:
            [00:20.00]Duet line together
            [00:24.00]Both:
            [00:26.00]Both singing line
        """.trimIndent()

        val parsed = LyricsParser.fromText(lrc, durationMs = 35_000L)
        val vocalLines = parsed.lines.filter { !it.isInstrumental }

        // Standalone singer headers should not be emitted
        assertFalse(vocalLines.any { it.text == "Sia:" || it.text == "[Sean Paul]" || it.text == "Sia/ Sean Paul:" || it.text == "Both:" })
        assertEquals(5, vocalLines.size)

        assertEquals("First line by Sia", vocalLines[0].text)
        assertEquals("v1", vocalLines[0].agent)
        assertEquals("Sia", vocalLines[0].singer)

        assertEquals("Second line by Sia", vocalLines[1].text)
        assertEquals("v1", vocalLines[1].agent)
        assertEquals("Sia", vocalLines[1].singer)

        assertEquals("First line by Sean Paul", vocalLines[2].text)
        assertEquals("v2", vocalLines[2].agent)
        assertEquals("Sean Paul", vocalLines[2].singer)

        assertEquals("Duet line together", vocalLines[3].text)
        assertEquals("v3", vocalLines[3].agent)
        assertEquals("Sia/ Sean Paul", vocalLines[3].singer)

        assertEquals("Both singing line", vocalLines[4].text)
        assertEquals("v3", vocalLines[4].agent)
        assertEquals("Both", vocalLines[4].singer)
    }

    @Test
    fun `LRC parser extracts inline singer prefixes and strips them from lyric text`() {
        val lrc = """
            [00:05.00]Sia: Come on come on turn the radio on
            [00:10.00]It's Friday night and it won't be long
            [00:15.00][Sean Paul] Baby girl you know that I love you
            [00:20.00]Both: Dance all night
        """.trimIndent()

        val parsed = LyricsParser.fromText(lrc, durationMs = 30_000L)
        val vocalLines = parsed.lines.filter { !it.isInstrumental }

        assertEquals(4, vocalLines.size)

        assertEquals("Come on come on turn the radio on", vocalLines[0].text)
        assertEquals("v1", vocalLines[0].agent)
        assertEquals("Sia", vocalLines[0].singer)

        assertEquals("It's Friday night and it won't be long", vocalLines[1].text)
        assertEquals("v1", vocalLines[1].agent)
        assertEquals("Sia", vocalLines[1].singer)

        assertEquals("Baby girl you know that I love you", vocalLines[2].text)
        assertEquals("v2", vocalLines[2].agent)
        assertEquals("Sean Paul", vocalLines[2].singer)

        assertEquals("Dance all night", vocalLines[3].text)
        assertEquals("v3", vocalLines[3].agent)
        assertEquals("Both", vocalLines[3].singer)
    }

    @Test
    fun `plain lyrics parser extracts singer headers and prefixes`() {
        val plain = """
            Sia:
            Come on come on turn the radio on
            [Sean Paul]
            Baby girl...
        """.trimIndent()

        val parsed = LyricsParser.fromText(plain, durationMs = 30_000L)
        val lines = parsed.lines
        assertEquals(2, lines.size)
        assertEquals("Come on come on turn the radio on", lines[0].text)
        assertEquals("v1", lines[0].agent)
        assertEquals("Sia", lines[0].singer)
        assertEquals("Baby girl...", lines[1].text)
        assertEquals("v2", lines[1].agent)
        assertEquals("Sean Paul", lines[1].singer)
    }

    @Test
    fun `TTML parser extracts agent metadata and assigns singer names`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head>
                <metadata>
                  <ttm:agent xml:id="v1" type="person"><ttm:name>Sia</ttm:name></ttm:agent>
                  <ttm:agent xml:id="v2" type="person">Sean Paul</ttm:agent>
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="00:02.00" end="00:05.00" ttm:agent="v1">
                    <span begin="00:02.00">Turn </span>
                    <span begin="00:03.00">the </span>
                    <span begin="00:04.00">radio on</span>
                  </p>
                  <p begin="00:06.00" end="00:09.00" agent="v2">
                    <span begin="00:06.00">Baby </span>
                    <span begin="00:07.50">girl</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = LyricsParser.fromText("", durationMs = 20_000L, ttmlRaw = ttml)
        val vocalLines = parsed.lines.filter { !it.isInstrumental }
        assertEquals(2, vocalLines.size)

        assertEquals("v1", vocalLines[0].agent)
        assertEquals("Sia", vocalLines[0].singer)
        assertEquals("Turn the radio on", vocalLines[0].text)

        assertEquals("v2", vocalLines[1].agent)
        assertEquals("Sean Paul", vocalLines[1].singer)
        assertEquals("Baby girl", vocalLines[1].text)
    }

    @Test
    fun `TTML parser preserves syllable and word whitespace correctly`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:01.00" end="00:05.00">
                    <span begin="00:01.00">ling </span>
                    <span begin="00:02.00">fal</span>
                    <span begin="00:03.00">ling </span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = LyricsParser.fromText("", durationMs = 10_000L, ttmlRaw = ttml)
        val vocalLines = parsed.lines.filter { !it.isInstrumental }
        assertEquals(1, vocalLines.size)
        val line = vocalLines[0]
        assertEquals("ling falling", line.text)
        assertEquals(3, line.words.size)
        assertEquals("ling ", line.words[0].text)
        assertEquals("fal", line.words[1].text)
        assertEquals("ling ", line.words[2].text)
    }

    @Test
    fun `TTML parser attaches inline spaces outside spans to previous word`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:01.00" end="00:05.00">
                    <span begin="00:01.00">The</span> <span begin="00:01.50">club</span> <span begin="00:02.00">cra</span><span begin="00:02.30">zy,</span> <span begin="00:02.80">don't</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = LyricsParser.fromText("", durationMs = 10_000L, ttmlRaw = ttml)
        val vocalLines = parsed.lines.filter { !it.isInstrumental }
        assertEquals(1, vocalLines.size)
        val line = vocalLines[0]
        assertEquals("The club crazy, don't", line.text)
        assertEquals(5, line.words.size)
        assertEquals("The ", line.words[0].text)
        assertEquals("club ", line.words[1].text)
        assertEquals("cra", line.words[2].text)
        assertEquals("zy, ", line.words[3].text)
        assertEquals("don't", line.words[4].text)
    }

    @Test
    fun `tagged word-synced lyrics drops singer headers and assigns agents`() {
        val elrc = """
            [00:02.00]<00:02.00>Sia:
            [00:04.00]<00:04.00>Up <00:05.00>with <00:06.00>the <00:07.00>sun
            [00:08.00]<00:08.00>Gotta <00:09.00>get <00:10.00>my <00:11.00>money
            [00:20.00]<00:20.00>Sia/Sean Paul:
            [00:22.00]<00:22.00>Cheap <00:23.00>thrills
        """.trimIndent()

        val parsed = LyricsParser.fromText(elrc, durationMs = 30_000L)
        val vocalLines = parsed.lines.filter { !it.isInstrumental }

        // Singer headers should be dropped
        assertFalse(vocalLines.any { it.text.contains("Sia:") || it.text.contains("Sia/Sean Paul:") })
        assertEquals(3, vocalLines.size)

        assertEquals("Up with the sun", vocalLines[0].text)
        assertEquals("v1", vocalLines[0].agent)
        assertEquals("Sia", vocalLines[0].singer)

        assertEquals("Gotta get my money", vocalLines[1].text)
        assertEquals("v1", vocalLines[1].agent)
        assertEquals("Sia", vocalLines[1].singer)

        assertEquals("Cheap thrills", vocalLines[2].text)
        assertEquals("v3", vocalLines[2].agent)
        assertEquals("Sia/Sean Paul", vocalLines[2].singer)
    }

    @Test
    fun `universal fromText removes singer headers from structured lines and marks multi-singers properly`() {
        val structured = listOf(
            LyricsLine(
                text = "Sia:",
                startMs = 2_000L,
                endMs = 4_000L,
                words = listOf(LyricsWord("Sia:", 2_000L, 4_000L))
            ),
            LyricsLine(
                text = "Hit the dance floor",
                startMs = 4_000L,
                endMs = 8_000L,
                words = listOf(
                    LyricsWord("Hit ", 4_000L, 5_000L),
                    LyricsWord("the ", 5_000L, 6_000L),
                    LyricsWord("dance ", 6_000L, 7_000L),
                    LyricsWord("floor", 7_000L, 8_000L)
                )
            ),
            LyricsLine(
                text = "Sia/Sean Paul:",
                startMs = 20_000L,
                endMs = 22_000L,
                words = listOf(LyricsWord("Sia/Sean Paul:", 20_000L, 22_000L))
            ),
            LyricsLine(
                text = "Baby I don't need dollar bills",
                startMs = 22_000L,
                endMs = 26_000L,
                words = listOf(
                    LyricsWord("Baby ", 22_000L, 23_000L),
                    LyricsWord("I ", 23_000L, 24_000L),
                    LyricsWord("don't ", 24_000L, 25_000L),
                    LyricsWord("need ", 25_000L, 26_000L)
                )
            )
        )

        val parsed = LyricsParser.fromText("", durationMs = 30_000L, structuredLines = structured)
        val vocalLines = parsed.lines.filter { !it.isInstrumental }

        assertEquals(2, vocalLines.size)
        assertFalse(vocalLines.any { it.text == "Sia:" || it.text == "Sia/Sean Paul:" })

        assertEquals("Hit the dance floor", vocalLines[0].text)
        assertEquals("v1", vocalLines[0].agent)
        assertEquals("Sia", vocalLines[0].singer)

        assertEquals("Baby I don't need dollar bills", vocalLines[1].text)
        assertEquals("v3", vocalLines[1].agent)
        assertEquals("Sia/Sean Paul", vocalLines[1].singer)
    }

    @Test
    fun `supports diverse singer header formats including full-width colons, dashes and Chinese keywords`() {
        val lrc = """
            [00:02.00]Sia -
            [00:04.00]Line one by Sia
            [00:08.00]Sean Paul：
            [00:10.00]Line two by Sean Paul
            [00:14.00]David Guetta:
            [00:16.00]Line three by David Guetta
            [00:20.00]合:
            [00:22.00]Chorus line together
        """.trimIndent()

        val parsed = LyricsParser.fromText(lrc, durationMs = 30_000L)
        val vocalLines = parsed.lines.filter { !it.isInstrumental }

        assertEquals(4, vocalLines.size)
        assertFalse(vocalLines.any {
            it.text.contains("Sia -") || it.text.contains("Sean Paul：") || it.text.contains(
                "David Guetta:"
            ) || it.text.contains("合:")
        })

        assertEquals("v1", vocalLines[0].agent)
        assertEquals("Sia", vocalLines[0].singer)

        assertEquals("v2", vocalLines[1].agent)
        assertEquals("Sean Paul", vocalLines[1].singer)

        assertEquals("v4", vocalLines[2].agent)
        assertEquals("David Guetta", vocalLines[2].singer)

        assertEquals("v3", vocalLines[3].agent)
        assertEquals("合", vocalLines[3].singer)
    }

    @Test
    fun `cheap thrills assigns lead singer to v1 and featured singer to v2`() {
        val lrc = """
            [00:01.00]Sean Paul:
            [00:02.00]Up with it girl
            [00:10.00]Sia:
            [00:11.00]Come on come on turn the radio on
            [00:20.00]Sia/Sean Paul:
            [00:21.00]'Til I hit the dance floor
        """.trimIndent()
        val parsed = LyricsParser.fromText(lrc, durationMs = 30_000L, mainArtist = "Sia")
        val vocalLines = parsed.lines.filter { !it.isInstrumental }

        assertEquals(3, vocalLines.size)

        // Sean Paul is the featured singer -> v2
        assertEquals("Up with it girl", vocalLines[0].text)
        assertEquals("v2", vocalLines[0].agent)
        assertEquals("Sean Paul", vocalLines[0].singer)

        // Sia is the lead singer -> v1
        assertEquals("Come on come on turn the radio on", vocalLines[1].text)
        assertEquals("v1", vocalLines[1].agent)
        assertEquals("Sia", vocalLines[1].singer)

        // Duet -> v3
        assertEquals("'Til I hit the dance floor", vocalLines[2].text)
        assertEquals("v3", vocalLines[2].agent)
        assertEquals("Sia/Sean Paul", vocalLines[2].singer)
    }

    @Test
    fun `cheap thrills assigns lead singer to v1 and featured singer to v2 with featured artist string`() {
        val lrc = """
            [00:01.00]Sean Paul:
            [00:02.00]Up with it girl
            [00:10.00]Sia:
            [00:11.00]Come on come on turn the radio on
            [00:20.00]Sia/Sean Paul:
            [00:21.00]'Til I hit the dance floor
        """.trimIndent()
        val parsed =
            LyricsParser.fromText(lrc, durationMs = 30_000L, mainArtist = "Sia feat. Sean Paul")
        val vocalLines = parsed.lines.filter { !it.isInstrumental }

        assertEquals(3, vocalLines.size)
        assertEquals("v2", vocalLines[0].agent)
        assertEquals("Sean Paul", vocalLines[0].singer)
        assertEquals("v1", vocalLines[1].agent)
        assertEquals("Sia", vocalLines[1].singer)
        assertEquals("v3", vocalLines[2].agent)
        assertEquals("Sia/Sean Paul", vocalLines[2].singer)
    }

    @Test
    fun `handles song with credits header, lead singer, counter singer and duet`() {
        val lrc = """
            [00:00.00]Henriques:
            [00:01.00]Sean Paul:
            [00:02.00]Up with it girl
            [00:10.00]Sia:
            [00:11.00]Come on come on turn the radio on
            [00:20.00]Sia & Sean Paul:
            [00:21.00]'Til I hit the dance floor
        """.trimIndent()
        val parsed =
            LyricsParser.fromText(lrc, durationMs = 30_000L, mainArtist = "Sia feat. Sean Paul")
        val vocalLines = parsed.lines.filter { !it.isInstrumental }

        assertEquals(3, vocalLines.size)
        assertEquals("Up with it girl", vocalLines[0].text)
        assertEquals("v2", vocalLines[0].agent)
        assertEquals("Sean Paul", vocalLines[0].singer)

        assertEquals("Come on come on turn the radio on", vocalLines[1].text)
        assertEquals("v1", vocalLines[1].agent)
        assertEquals("Sia", vocalLines[1].singer)

        assertEquals("'Til I hit the dance floor", vocalLines[2].text)
        assertEquals("v3", vocalLines[2].agent)
        assertEquals("Sia & Sean Paul", vocalLines[2].singer)
    }
}
