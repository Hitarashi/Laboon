package org.shilpo.laboon.ui.screens.home

internal fun shouldHandleHomeBack(
    canGoBackWithinHome: Boolean,
    overlayStackIsEmpty: Boolean,
    showRipVisualizer: Boolean,
    playlistPickerOpen: Boolean,
): Boolean =
    canGoBackWithinHome &&
        overlayStackIsEmpty &&
        !showRipVisualizer &&
        !playlistPickerOpen
