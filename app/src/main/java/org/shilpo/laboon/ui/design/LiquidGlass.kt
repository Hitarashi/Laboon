package org.shilpo.laboon.ui.design

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.theme.LocalVisualTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min

@Stable
class LiquidGlassBackdropState {
    var layer: GraphicsLayer? by mutableStateOf(null)
    var rootOffset: Offset by mutableStateOf(Offset.Zero)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    var invalidationToken by mutableLongStateOf(0L)
        private set

    fun invalidate() {
        invalidationToken++
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyInvalidated() {
        for (listener in listeners) {
            listener()
        }
    }
}

@Composable
fun rememberLiquidGlassBackdropState(): LiquidGlassBackdropState {
    return remember { LiquidGlassBackdropState() }
}

@Composable
fun Modifier.liquidGlassBackdropProducer(
    state: LiquidGlassBackdropState,
    layer: GraphicsLayer,
    backgroundColor: Color = Color.Unspecified,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        LocalVisualTheme.current?.definition?.effects?.containsKey("glassSurface") != true
    ) return this
    return this
        .onGloballyPositioned { coordinates ->
            state.rootOffset = coordinates.positionInRoot()
            state.layer = layer
        }
        .drawWithContent {
            val token = state.invalidationToken
            layer.record {
                if (backgroundColor != Color.Unspecified) {
                    drawRect(color = backgroundColor)
                }
                this@drawWithContent.drawContent()
            }
            drawLayer(layer)
            state.notifyInvalidated()
        }
}

@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun LiquidGlassRuntimeSurface(
    shaderSource: String,
    shaderUniforms: Map<String, Float>,
    modifier: Modifier = Modifier,
    backdropState: LiquidGlassBackdropState? = null,
    shape: Shape = RoundedCornerShape(32.dp),
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
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val glassLayer = rememberGraphicsLayer()
    val shader = remember(shaderSource) { runCatching { RuntimeShader(shaderSource) }.getOrNull() }

    var myOffset by remember { mutableStateOf(Offset.Zero) }
    var invalidationTick by remember { mutableLongStateOf(0L) }

    DisposableEffect(backdropState) {
        if (backdropState == null) return@DisposableEffect onDispose {}
        val listener: () -> Unit = {
            invalidationTick++
        }
        backdropState.addListener(listener)
        onDispose {
            backdropState.removeListener(listener)
        }
    }

    val topRadiusPx = with(density) { topRadius.toPx() }
    val bottomRadiusPx = with(density) { bottomRadius.toPx() }
    val thicknessPx = with(density) { thicknessDp.toPx() }
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    val strokeWidthPx = with(density) { 0.8.dp.toPx() }

    var cachedWidth by remember { mutableStateOf(-1f) }
    var cachedHeight by remember { mutableStateOf(-1f) }
    var cachedTopRadius by remember { mutableStateOf(-1f) }
    var cachedBottomRadius by remember { mutableStateOf(-1f) }
    var cachedThickness by remember { mutableStateOf(-1f) }
    var cachedTint by remember { mutableStateOf(Color.Unspecified) }
    var cachedAlpha by remember { mutableStateOf(-1f) }
    var cachedRefractIntensity by remember { mutableStateOf(-1f) }
    var cachedUniforms by remember(shaderUniforms) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var cachedComposeEffect by remember(shader) {
        mutableStateOf<androidx.compose.ui.graphics.RenderEffect?>(
            null
        )
    }

    if (shader == null) {
        Box(modifier = modifier.clip(shape)) { content() }
        return
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                spotColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.18f),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.20f else 0.10f),
            )
            .clip(shape)
            .onGloballyPositioned { coordinates ->
                myOffset = coordinates.positionInRoot()
            }
            .drawWithContent {
                val bLayer = backdropState?.layer
                val w = size.width
                val h = size.height

                if (w > 0f && h > 0f) {
                    val clampedTop = min(topRadiusPx, h / 2f)
                    val clampedBottom = min(bottomRadiusPx, h / 2f)
                    val effectiveRefract =
                        if (clampedTop <= 0.5f && clampedBottom <= 0.5f) 0f else refractIntensity
                    val actualThickness = max(min(thicknessPx, min(w, h) / 5f), 1f)

                    if (cachedComposeEffect == null ||
                        cachedWidth != w || cachedHeight != h ||
                        cachedTopRadius != clampedTop || cachedBottomRadius != clampedBottom ||
                        cachedThickness != actualThickness || cachedTint != tintColor || cachedAlpha != tintAlpha ||
                        cachedRefractIntensity != effectiveRefract
                        || cachedUniforms != shaderUniforms
                    ) {
                        cachedWidth = w
                        cachedHeight = h
                        cachedTopRadius = clampedTop
                        cachedBottomRadius = clampedBottom
                        cachedThickness = actualThickness
                        cachedTint = tintColor
                        cachedAlpha = tintAlpha
                        cachedRefractIntensity = effectiveRefract
                        cachedUniforms = shaderUniforms

                        shader.setFloatUniform("resolution", w, h)
                        shader.setFloatUniform("center", w / 2f, h / 2f)
                        shader.setFloatUniform("size", w / 2f, h / 2f)
                        shader.setFloatUniform(
                            "radius",
                            clampedBottom,
                            clampedTop,
                            clampedBottom,
                            clampedTop
                        )
                        shader.setFloatUniform("thickness", actualThickness)
                        shader.setFloatUniform("refract_index", refractIndex)
                        shader.setFloatUniform("refract_intensity", effectiveRefract)
                        shaderUniforms.forEach { (name, value) ->
                            runCatching { shader.setFloatUniform(name, value) }
                        }

                        val a = tintAlpha
                        shader.setFloatUniform(
                            "foreground_color_premultiplied",
                            tintColor.red * a,
                            tintColor.green * a,
                            tintColor.blue * a,
                            a,
                        )

                        val blurEffect = RenderEffect.createBlurEffect(
                            blurRadiusPx,
                            blurRadiusPx,
                            Shader.TileMode.CLAMP
                        )
                        val satMatrix = ColorMatrix().apply { setSaturation(3.0f) }
                        val satEffect =
                            RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(satMatrix))
                        val frostedBlur = RenderEffect.createChainEffect(satEffect, blurEffect)
                        val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "img")
                        val chain = RenderEffect.createChainEffect(shaderEffect, frostedBlur)
                        cachedComposeEffect = chain.asComposeRenderEffect()
                    }

                    glassLayer.renderEffect = cachedComposeEffect

                    val bOffset = backdropState?.rootOffset ?: Offset.Zero
                    val dx = bOffset.x - myOffset.x
                    val dy = bOffset.y - myOffset.y

                    val currentTick = invalidationTick
                    if (bLayer != null && currentTick >= 0L) {
                        glassLayer.record {
                            translate(dx, dy) {
                                drawLayer(bLayer)
                            }
                        }
                        drawLayer(glassLayer)
                    } else {
                        drawRect(color = tintColor.copy(alpha = tintAlpha))
                    }

                    val strokePath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(0f, 0f, w, h),
                                topLeft = CornerRadius(clampedTop, clampedTop),
                                topRight = CornerRadius(clampedTop, clampedTop),
                                bottomRight = CornerRadius(clampedBottom, clampedBottom),
                                bottomLeft = CornerRadius(clampedBottom, clampedBottom),
                            )
                        )
                    }

                    if (clampedTop > 0.5f) {
                        val topStrokeColor =
                            if (isDark) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.65f)
                        val strokeTopBrush = Brush.verticalGradient(
                            colors = listOf(topStrokeColor, Color.Transparent),
                            startY = 0f,
                            endY = clampedTop * 1.5f,
                        )
                        drawPath(
                            path = strokePath,
                            brush = strokeTopBrush,
                            style = Stroke(width = strokeWidthPx),
                        )
                    }

                    if (showBottomRefractionEdge && clampedBottom > 0.5f) {
                        val bottomStrokeColor =
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
                        val strokeBottomBrush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, bottomStrokeColor),
                            startY = h - clampedBottom * 1.5f,
                            endY = h,
                        )
                        drawPath(
                            path = strokePath,
                            brush = strokeBottomBrush,
                            style = Stroke(width = strokeWidthPx),
                        )
                    }

                    if (clampedTop > 0.5f || clampedBottom > 0.5f) {
                        val sheenBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.05f else 0.12f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = h * 0.45f,
                        )
                        drawRect(brush = sheenBrush)
                    }
                }

                drawContent()
            },
    ) {
        content()
    }
}
