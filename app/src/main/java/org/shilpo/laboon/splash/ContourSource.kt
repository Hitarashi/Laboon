package org.shilpo.laboon.splash

import androidx.core.graphics.PathParser
import kotlin.math.max

interface ContourSource {
    fun slots(
        width: Float,
        height: Float,
        density: Float,
        totalSlots: Int,
        logoTargetSizeDp: Float
    ): Slots.ShapeSlots?
}

object Contour {
    val polygon: ContourSource = PolygonContour

    fun svg(pathData: String, customVectorPath: android.graphics.Path?): ContourSource =
        FallbackContour(SvgContour(pathData, customVectorPath), PolygonContour)
}

private object PolygonContour : ContourSource {
    override fun slots(
        width: Float,
        height: Float,
        density: Float,
        totalSlots: Int,
        logoTargetSizeDp: Float
    ): Slots.ShapeSlots {
        if (width <= 0f || height <= 0f) return Slots.empty(totalSlots)
        val (c, size) = Slots.boxFrame(width, height, density, logoTargetSizeDp)
        val raw = Slots.contour(c.x, c.y, size)
        return Slots.ShapeSlots(
            Slots.ndResample(raw, totalSlots),
            listOf(0 until totalSlots),
            Slots.tips(width, height, density, logoTargetSizeDp),
            null
        )
    }
}

private class SvgContour(
    private val pathData: String,
    private val customVectorPath: android.graphics.Path?
) : ContourSource {
    override fun slots(
        width: Float,
        height: Float,
        density: Float,
        totalSlots: Int,
        logoTargetSizeDp: Float
    ): Slots.ShapeSlots? {
        if (width <= 0f || height <= 0f) return Slots.empty(totalSlots)
        val (c, size) = Slots.boxFrame(width, height, density, logoTargetSizeDp)
        val sourcePath = customVectorPath ?: try {
            PathParser.createPathFromPathData(pathData)
        } catch (_: Exception) {
            null
        } ?: return null

        val p = android.graphics.Path(sourcePath)
        val bounds = android.graphics.RectF()
        p.computeBounds(bounds, true)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return null
        val scale = size / max(bounds.width(), bounds.height())
        val matrix = android.graphics.Matrix().apply {
            postTranslate(-bounds.centerX(), -bounds.centerY())
            postScale(scale, scale)
            postTranslate(c.x, c.y)
        }
        p.transform(matrix)
        return Slots.parseContourPath(p, totalSlots)
    }
}

private class FallbackContour(
    private val primary: ContourSource,
    private val fallback: ContourSource
) : ContourSource {
    override fun slots(
        width: Float,
        height: Float,
        density: Float,
        totalSlots: Int,
        logoTargetSizeDp: Float
    ): Slots.ShapeSlots? =
        primary.slots(width, height, density, totalSlots, logoTargetSizeDp)
            ?: fallback.slots(width, height, density, totalSlots, logoTargetSizeDp)
}
