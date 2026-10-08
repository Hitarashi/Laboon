package org.shilpo.laboon.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualThemeCrashRecoveryTest {
    @Test
    fun detectsMeasureFailuresWrappedByTheUiThread() {
        val cause = IllegalStateException("Unbounded scrolling layout").apply {
            stackTrace = arrayOf(
                StackTraceElement(
                    "androidx.compose.foundation.lazy.LazyLayout",
                    "measure",
                    "LazyLayout.kt",
                    1
                )
            )
        }
        assertTrue(hasVisualThemeFailure(RuntimeException("Wrapped UI failure", cause)))
    }

    @Test
    fun doesNotClassifyPlaybackFailuresAsRendererFailures() {
        val failure = IllegalStateException("Playback error").apply {
            stackTrace = arrayOf(
                StackTraceElement(
                    "org.shilpo.laboon.playback.PlaybackManagerImpl",
                    "play",
                    "PlaybackManager.kt",
                    1
                )
            )
        }
        assertFalse(hasVisualThemeFailure(failure))
    }
}
