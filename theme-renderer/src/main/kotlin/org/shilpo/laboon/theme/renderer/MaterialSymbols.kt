package org.shilpo.laboon.theme.renderer

import android.graphics.Paint
import android.text.TextPaint
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import org.shilpo.laboon.theme.contract.MaterialSymbolCatalog

/** Active theme's semantic Laboon icon replacements. The host still owns every action. */
val LocalThemeIconOverrides = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** Resolves a bundled Material Symbol into a tintable painter. Unknown names fall back safely. */
@Composable
fun materialSymbolPainterResource(
    name: String,
    slot: String? = null,
    filled: Boolean = false,
): Painter {
    val overrides = LocalThemeIconOverrides.current
    val resolvedName = (slot?.let(overrides::get) ?: name)
        .takeIf { it in MaterialSymbolCatalog.supported }
        ?: "info"
    val context = LocalContext.current
    val defaultColor = LocalContentColor.current
    val typeface = remember(context, filled) {
        context.resources.getFont(
            if (filled) R.font.material_symbols_rounded_filled
            else R.font.material_symbols_rounded_outlined
        )
    }
    return remember(resolvedName, typeface, defaultColor) {
        MaterialSymbolPainter(resolvedName, typeface, defaultColor.toArgb())
    }
}

private class MaterialSymbolPainter(
    private val symbolName: String,
    private val typeface: android.graphics.Typeface,
    defaultColor: Int,
) : Painter() {
    override val intrinsicSize: Size = Size(24f, 24f)

    private var painterAlpha: Float = 1f
    private var painterColorFilter: ColorFilter? = null
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = this@MaterialSymbolPainter.typeface
        textAlign = Paint.Align.CENTER
        color = defaultColor
        setFontFeatureSettings("'rlig' 1, 'liga' 1")
    }

    override fun applyAlpha(alpha: Float): Boolean {
        painterAlpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        painterColorFilter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        paint.textSize = size.minDimension
        paint.alpha = (painterAlpha * 255f).toInt().coerceIn(0, 255)
        paint.colorFilter = painterColorFilter?.asAndroidColorFilter()
        val metrics = paint.fontMetrics
        val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                symbolName,
                size.width / 2f,
                baseline,
                paint,
            )
        }
    }
}
