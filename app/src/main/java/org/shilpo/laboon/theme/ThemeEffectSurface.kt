package org.shilpo.laboon.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.shilpo.laboon.theme.contract.ThemeOptionType
import org.shilpo.laboon.theme.renderer.VisualShaderEffect
import org.shilpo.laboon.ui.design.LiquidGlassSurface

@Composable
internal fun ThemeEffectSurface(
    effectId: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val theme = LocalVisualTheme.current
    val controller = LocalVisualThemeController.current
    LocalVisualThemeRevision.current
    val effect = theme?.definition?.effects?.get(effectId)
    if (theme == null || effect == null || controller == null) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = content,
        )
        return
    }

    if (effectId == GLASS_EFFECT_ID) {
        LiquidGlassSurface(
            modifier = modifier,
            backdropState = LocalThemeBackdrop.current,
            content = content
        )
        return
    }

    val source = remember(
        theme.packageName,
        theme.packageRevision,
        theme.manifest.version,
        effect.shaderAsset,
        controller
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { controller.readAsset(theme, effect.shaderAsset).toString(Charsets.UTF_8) }
                .getOrNull()
        } else {
            null
        }
    }
    if (source == null) {
        content()
        return
    }

    val optionUniforms = theme.definition.options.mapNotNull { option ->
        if (option.type != ThemeOptionType.SLIDER) return@mapNotNull null
        if (option.id !in effect.uniforms) return@mapNotNull null
        controller.optionValue(theme, option).toFloatOrNull()
            ?.takeIf(Float::isFinite)
            ?.let { option.id to it }
    }.toMap()
    VisualShaderEffect(
        shaderSource = source,
        uniforms = effect.uniforms + optionUniforms,
        modifier = modifier,
        content = content,
    )
}

private const val GLASS_EFFECT_ID = "glassSurface"
