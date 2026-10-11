package org.shilpo.laboon.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBackNavigationTest {
    @Test
    fun pickerBackIsLeftForThePickerToHandle() {
        assertFalse(
            shouldHandleHomeBack(
                canGoBackWithinHome = true,
                overlayStackIsEmpty = true,
                showRipVisualizer = false,
                playlistPickerOpen = true,
            ),
        )
    }

    @Test
    fun tabBackRemainsEnabledWhenNoOverlayOrPickerIsOpen() {
        assertTrue(
            shouldHandleHomeBack(
                canGoBackWithinHome = true,
                overlayStackIsEmpty = true,
                showRipVisualizer = false,
                playlistPickerOpen = false,
            ),
        )
    }
}
