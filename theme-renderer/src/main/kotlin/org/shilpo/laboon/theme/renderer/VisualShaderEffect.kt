package org.shilpo.laboon.theme.renderer

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Applies a packaged AGSL shader to the rendered child content on Android 13 and later.
 * The shader receives the child as a `content` input and may declare a `resolution` float2.
 * Unsupported devices and shader failures keep the child visible without the effect.
 */
@Composable
fun VisualShaderEffect(
    shaderSource: String,
    uniforms: Map<String, Float> = emptyMap(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        content()
        return
    }
    Api33VisualShaderEffect(shaderSource, uniforms, modifier, content)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Api33VisualShaderEffect(
    shaderSource: String,
    uniforms: Map<String, Float>,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shader = remember(shaderSource) {
        runCatching { android.graphics.RuntimeShader(shaderSource) }.getOrNull()
    }
    val effect = remember(shader) {
        shader?.let {
            runCatching {
                android.graphics.RenderEffect.createRuntimeShaderEffect(it, "content")
                    .asComposeRenderEffect()
            }.getOrNull()
        }
    }
    SideEffect {
        shader?.let { runtimeShader ->
            uniforms.forEach { (name, value) ->
                runCatching { runtimeShader.setFloatUniform(name, value) }
            }
        }
    }
    val effectModifier = if (effect != null) {
        modifier
            .onSizeChanged { size: IntSize ->
                runCatching {
                    shader?.setFloatUniform(
                        "resolution",
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
            }
            .graphicsLayer { renderEffect = effect }
    } else modifier

    Box(modifier = effectModifier) { content() }
}
