package org.shilpo.laboon.ui.screens.player

import org.junit.Assert.assertNull
import org.junit.Test

class PlayerCarouselIndexTest {
    @Test
    fun stalePagerIndexAfterQueueShrinksReturnsNoItem() {
        val shortenedQueue = List(29) { "track-$it" }

        assertNull(playerCarouselItemAtOrNull(shortenedQueue, index = 29))
    }
}
