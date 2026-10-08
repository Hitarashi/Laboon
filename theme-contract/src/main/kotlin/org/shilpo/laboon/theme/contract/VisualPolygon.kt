package org.shilpo.laboon.theme.contract

data class VisualPoint(val x: Float, val y: Float)

/** Closed polygons use normalized coordinates and matching vertex counts for compatible morphs. */
object VisualPolygon {
    fun parse(value: String): List<VisualPoint>? {
        if (value.length > 1_024) return null
        val pairs = value.split(';')
        if (pairs.size !in 3..64) return null
        return pairs.map { pair ->
            val coordinates = pair.split(',')
            if (coordinates.size != 2) return null
            val x = coordinates[0].trim().toFloatOrNull() ?: return null
            val y = coordinates[1].trim().toFloatOrNull() ?: return null
            if (x !in 0f..1f || y !in 0f..1f) return null
            VisualPoint(x, y)
        }
    }
}
