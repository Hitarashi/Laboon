package org.shilpo.laboon.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import org.shilpo.laboon.ui.design.LiquidGlassBackdropState
import org.shilpo.laboon.ui.design.liquidGlassBackdropProducer
import org.shilpo.laboon.ui.design.rememberLiquidGlassBackdropState

internal val LocalThemeBackdrop = staticCompositionLocalOf<LiquidGlassBackdropState?> { null }

@Composable
internal fun ThemeBackdropContext(content: @Composable () -> Unit) {
    val state = rememberLiquidGlassBackdropState()
    CompositionLocalProvider(LocalThemeBackdrop provides state, content = content)
}

@Composable
internal fun ThemeBackdropCapture(modifier: Modifier, content: @Composable () -> Unit) {
    val state = LocalThemeBackdrop.current
    val layer = rememberGraphicsLayer()
    Box(
        modifier = if (state != null) modifier.liquidGlassBackdropProducer(
            state,
            layer
        ) else modifier
    ) { content() }
}
