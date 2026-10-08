package org.shilpo.laboon.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun validateAgslShader(
    source: String,
    uniforms: Map<String, Float>,
    backdrop: Boolean
): Boolean = runCatching {
    val shader = RuntimeShader(source)
    android.graphics.RenderEffect.createRuntimeShaderEffect(
        shader,
        if (backdrop) "img" else "content"
    )
    uniforms.forEach { (name, value) -> shader.setFloatUniform(name, value) }
    if (backdrop) {
        shader.setFloatUniform("resolution", 1f, 1f)
        shader.setFloatUniform("center", 0.5f, 0.5f)
        shader.setFloatUniform("size", 0.5f, 0.5f)
        shader.setFloatUniform("radius", 0f, 0f, 0f, 0f)
        shader.setFloatUniform("thickness", 1f)
        shader.setFloatUniform("refract_index", 1.5f)
        shader.setFloatUniform("refract_intensity", 0f)
        shader.setFloatUniform("foreground_color_premultiplied", 0f, 0f, 0f, 0f)
    }
}.isSuccess
