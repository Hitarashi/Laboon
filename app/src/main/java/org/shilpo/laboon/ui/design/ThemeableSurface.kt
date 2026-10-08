package org.shilpo.laboon.ui.design

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.theme.LocalVisualTheme
import org.shilpo.laboon.theme.LocalVisualThemeController
import androidx.compose.material3.Surface as MaterialSurface

@Composable
internal fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    backdropState: LiquidGlassBackdropState? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    cornerRadius: Dp = 32.dp,
    topRadius: Dp = cornerRadius,
    bottomRadius: Dp = cornerRadius,
    tintColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    tintAlpha: Float = 0.85f,
    blurRadius: Dp = 26.dp,
    refractIndex: Float = 1.5f,
    refractIntensity: Float = 0.75f,
    thicknessDp: Dp = 11.dp,
    shadowElevation: Dp = 8.dp,
    showBottomRefractionEdge: Boolean = true,
    content: @Composable () -> Unit,
) {
    val theme = LocalVisualTheme.current
    val controller = LocalVisualThemeController.current
    val effect = theme?.definition?.effects?.get("glassSurface")
    org.shilpo.laboon.theme.LocalVisualThemeRevision.current
    val optionUniforms = if (theme != null && controller != null) {
        theme.definition.options.mapNotNull { option ->
            val value = controller.optionValue(theme, option).toFloatOrNull()
            value?.takeIf(Float::isFinite)?.let { option.id to it }
        }.toMap()
    } else emptyMap()
    val glassOption = theme?.definition?.options?.firstOrNull { it.id == "ambient_glass" }
    val glassEnabled = theme == null || glassOption == null || controller?.optionValue(
        theme,
        glassOption
    ) != "false"
    val uniforms = effect?.uniforms.orEmpty() + optionUniforms
    val shaderSource = remember(
        theme?.packageName,
        theme?.packageRevision,
        theme?.manifest?.version,
        effect?.shaderAsset,
        controller
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && theme != null &&
            controller != null && effect != null
        ) {
            runCatching { controller.readAsset(theme, effect.shaderAsset).toString(Charsets.UTF_8) }
                .getOrNull()
        } else {
            null
        }
    }

    if (glassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shaderSource != null) {
        LiquidGlassRuntimeSurface(
            shaderSource = shaderSource,
            shaderUniforms = uniforms,
            modifier = modifier,
            backdropState = backdropState,
            shape = shape,
            cornerRadius = cornerRadius,
            topRadius = topRadius,
            bottomRadius = bottomRadius,
            tintColor = tintColor,
            tintAlpha = tintAlpha,
            blurRadius = blurRadius,
            refractIndex = refractIndex,
            refractIntensity = refractIntensity,
            thicknessDp = thicknessDp,
            shadowElevation = shadowElevation,
            showBottomRefractionEdge = showBottomRefractionEdge,
            content = content,
        )
    } else {
        MaterialSurface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = content,
        )
    }
}
