package org.shilpo.laboon.ui.screens.player.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.lyrics.LyricsWord

class LyricsModeTest {

    @Test
    fun syncedModeUsesLineTimingWhenAvailable() {
        assertTrue(
            usesSynchronizedLyrics(
                lines = listOf(LyricsLine(text = "First line", startMs = 1_000L)),
                showSyncedLyrics = true,
            ),
        )
    }

    @Test
    fun staticModeDoesNotFollowLineTiming() {
        assertFalse(
            usesSynchronizedLyrics(
                lines = listOf(LyricsLine(text = "First line", startMs = 1_000L)),
                showSyncedLyrics = false,
            ),
        )
    }

    @Test
    fun syncedModeCanUseWordTimingWhenLineTimingIsAbsent() {
        assertTrue(
            usesSynchronizedLyrics(
                lines = listOf(
                    LyricsLine(
                        text = "First line",
                        startMs = 0L,
                        words = listOf(LyricsWord(text = "First", startMs = 1_000L)),
                    ),
                ),
                showSyncedLyrics = true,
            ),
        )
    }
}
