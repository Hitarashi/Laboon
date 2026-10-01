package org.shilpo.laboon

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class LoadControlTest {

    @Test
    fun testDefaultLoadControlBackBufferDoesNotThrow() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(60_000, 300_000, 2_000, 5_000)
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        assertEquals(30_000_000L, loadControl.getBackBufferDurationUs(PlayerId.UNSET))
        assertTrue(loadControl.retainBackBufferFromKeyframe(PlayerId.UNSET))
    }
}
