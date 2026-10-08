package org.shilpo.laboon.ui.screens.home

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Rounded star shape used by PixelPlayer's Daily Mix artwork stack. */
internal class RoundedStarShape(
    private val sides: Int,
    private val curve: Double = 0.09,
    private val rotation: Float = 0f,
    iterations: Int = 360,
) : Shape {
    private companion object {
        const val TWO_PI = 2 * PI
    }

    private val steps = TWO_PI / min(iterations, 360)
    private val rotationRadians = (PI / 180) * rotation

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply {
            val radius = min(size.height, size.width) * 0.4 * mapRange(1.0, 0.0, 0.5, 1.0, curve)
            val centerX = size.width * 0.5f
            val centerY = size.height * 0.5f

            fun pointAt(angle: Double): Pair<Float, Float> {
                val x = radius * (cos(angle - rotationRadians) * (1 + curve * cos(sides * angle)))
                val y = radius * (sin(angle - rotationRadians) * (1 + curve * cos(sides * angle)))
                return (x + centerX).toFloat() to (y + centerY).toFloat()
            }

            val (startX, startY) = pointAt(0.0)
            moveTo(startX, startY)
            var angle = steps
            while (angle < TWO_PI) {
                val (x, y) = pointAt(angle)
                lineTo(x, y)
                angle += steps
            }
            close()
        })

    private fun mapRange(a: Double, b: Double, c: Double, d: Double, value: Double): Double =
        (value - a) / (b - a) * (d - c) + c
}
