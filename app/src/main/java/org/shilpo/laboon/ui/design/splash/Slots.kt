package org.shilpo.laboon.ui.design.splash

import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.PathParser
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

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

object Slots {
    const val RD = 0.5483f
    const val SHAPE_LOGO = "logo"

    private val pos = FloatArray(2)
    private var segLengths = FloatArray(32)

    const val LOGO_PATH =
        "M 64.3 62.8 c -3.1 -1.6 -5.2 -1.9 -11.3 -1.5 c -8.3 0.4 -12.2 2 -17.3 7.1 c -4.9 4.8 -7 9.9 -6.9 17 c 0 10.4 4.9 18.8 13.5 23.1 c 10.2 5.1 23.4 2.4 30.8 -6.4 c 5.4 -6.2 5.9 -9.3 5.9 -33.7 v -21.9 l 5.8 -0.1 c 7 -0.1 12.9 -1.9 14.3 -4.5 c 1.7 -3.3 -0.9 -6.9 -6.5 -9 s -9.8 -5.7 -13.3 -11.7 c -2 -3.4 -3 -4.2 -5.3 -4.2 c -1.8 0 -3.5 0.8 -4.4 2.2 c -1.3 1.9 -1.6 5.9 -1.6 23.9 v 21.7 z"

    data class ShapeSlots(
        val slots: List<Offset>,
        val loops: List<IntRange>,
        val tips: List<Offset>,
        val outlinePath: android.graphics.Path?
    )

    fun empty(totalSlots: Int): ShapeSlots =
        ShapeSlots(emptyList(), listOf(0 until totalSlots), emptyList(), null)

    fun center(width: Float, height: Float): Offset =
        Offset(width / 2f, height / 2f)

    fun boxSize(
        width: Float,
        height: Float,
        density: Float,
        logoTargetSizeDp: Float
    ): Float = minOf(
        logoTargetSizeDp * density,
        minOf(width, height) * 0.42f
    )

    fun boxFrame(
        width: Float,
        height: Float,
        density: Float,
        logoTargetSizeDp: Float
    ): Pair<Offset, Float> =
        Pair(center(width, height), boxSize(width, height, density, logoTargetSizeDp))

    fun pbBolt(cx: Float, cy: Float, size: Float): List<Offset> {
        val w = size * RD
        val left = cx - w / 2f
        val top = cy - size / 2f

        val raw = listOf(
            Offset(0.72f, 0.00f),
            Offset(0.24f, 0.52f),
            Offset(0.54f, 0.52f),
            Offset(0.28f, 1.00f),
            Offset(0.76f, 0.48f),
            Offset(0.46f, 0.48f)
        )
        return raw.map { Offset(left + it.x * w, top + it.y * size) }
    }

    fun tips(
        width: Float,
        height: Float,
        density: Float,
        logoTargetSizeDp: Float
    ): List<Offset> {
        val (c, size) = boxFrame(width, height, density, logoTargetSizeDp)
        val w = size * RD
        return listOf(
            Offset(c.x + w * 0.22f, c.y - size * 0.5f),
            Offset(c.x - w * 0.22f, c.y + size * 0.5f)
        )
    }

    fun contour(cx: Float, cy: Float, size: Float): List<Offset> =
        pbBolt(cx, cy, size)

    fun ndResample(pts: List<Offset>, count: Int): List<Offset> = synchronized(this) {
        if (pts.isEmpty() || count <= 0) return@synchronized emptyList()
        val n = pts.size
        var lengths = segLengths
        if (lengths.size < n) {
            lengths = FloatArray(n)
            segLengths = lengths
        }
        var perimeter = 0f
        for (i in 0 until n) {
            val p1 = pts[i]
            val p2 = pts[(i + 1) % n]
            val len = hypot(p2.x - p1.x, p2.y - p1.y)
            lengths[i] = len
            perimeter += len
        }
        if (perimeter <= 0f) return@synchronized List(count) { pts.first() }

        val step = perimeter / count
        val res = ArrayList<Offset>(count)
        var segIdx = 0
        var segStartDist = 0f

        for (i in 0 until count) {
            val targetDist = i * step
            while (segIdx < n - 1 && targetDist >= segStartDist + lengths[segIdx]) {
                segStartDist += lengths[segIdx]
                segIdx++
            }
            val segLen = lengths[segIdx]
            val t = if (segLen > 0f) ((targetDist - segStartDist) / segLen).coerceIn(0f, 1f) else 0f
            val p1 = pts[segIdx]
            val p2 = pts[(segIdx + 1) % n]
            res.add(
                Offset(
                    x = p1.x + (p2.x - p1.x) * t,
                    y = p1.y + (p2.y - p1.y) * t
                )
            )
        }
        res
    }

    fun parseContourPath(path: android.graphics.Path, totalSlots: Int): ShapeSlots =
        synchronized(this) {
            val rawLengths = ArrayList<Float>()
            val measure = android.graphics.PathMeasure(path, false)
            do {
                val len = measure.length
                if (len > 0f) {
                    rawLengths.add(len)
                }
            } while (measure.nextContour())

            if (rawLengths.isEmpty() || totalSlots <= 0) {
                return@synchronized ShapeSlots(
                    emptyList(),
                    listOf(0 until totalSlots),
                    emptyList(),
                    path
                )
            }

            val maxLen = rawLengths.maxOrNull() ?: 0f
            val minThreshold = (maxLen * 0.25f).coerceAtLeast(15f)
            val hasSignificant = rawLengths.any { it >= minThreshold }

            val contourLengths = rawLengths.map { len ->
                if (!hasSignificant || len >= minThreshold) len else 0f
            }

            val totalLength = contourLengths.sum()
            if (totalLength <= 0f) {
                return@synchronized ShapeSlots(
                    emptyList(),
                    listOf(0 until totalSlots),
                    emptyList(),
                    path
                )
            }

            val counts = contourLengths.map { len ->
                if (len > 0f) {
                    ((len / totalLength * totalSlots).toInt()).coerceAtLeast(3)
                } else {
                    0
                }
            }.toMutableList()

            val longestIndex = contourLengths.indices.maxByOrNull { contourLengths[it] } ?: 0
            val diff = totalSlots - counts.sum()
            counts[longestIndex] += diff
            if (counts[longestIndex] < 3) {
                counts[longestIndex] = 3
            }

            val samplingMeasure = android.graphics.PathMeasure(path, false)
            val slots = ArrayList<Offset>(totalSlots)
            val loops = ArrayList<IntRange>()
            var contourIdx = 0

            do {
                val len = samplingMeasure.length
                if (len <= 0f) continue
                if (contourIdx >= counts.size) break
                val count = counts[contourIdx]
                val startIndex = slots.size
                if (count > 0) {
                    val step = len / count
                    for (i in 0 until count) {
                        samplingMeasure.getPosTan(i * step, pos, null)
                        slots.add(Offset(pos[0], pos[1]))
                    }
                    loops.add(startIndex until slots.size)
                }
                contourIdx++
            } while (samplingMeasure.nextContour())

            val centroid = if (slots.isNotEmpty()) {
                var sumX = 0f
                var sumY = 0f
                for (s in slots) {
                    sumX += s.x
                    sumY += s.y
                }
                Offset(sumX / slots.size, sumY / slots.size)
            } else {
                Offset.Zero
            }

            val tips = slots.sortedByDescending {
                (it.x - centroid.x) * (it.x - centroid.x) + (it.y - centroid.y) * (it.y - centroid.y)
            }.take(3)

            ShapeSlots(
                slots = slots,
                loops = if (loops.isEmpty()) listOf(0 until totalSlots) else loops,
                tips = tips,
                outlinePath = path
            )
        }

    private data class PairDist(val member: Int, val slot: Int, val d: Float)

    fun zdGreedyCompile(members: List<Offset>, slots: List<Offset>): List<Int> {
        if (members.isEmpty() || slots.isEmpty()) return List(members.size) { -1 }

        val pairs = ArrayList<PairDist>(members.size * slots.size)
        for (i in members.indices) {
            val m = members[i]
            for (j in slots.indices) {
                val s = slots[j]
                val d = hypot(m.x - s.x, m.y - s.y)
                pairs.add(PairDist(i, j, d))
            }
        }
        pairs.sortBy { it.d }

        val usedSlots = BooleanArray(slots.size)
        val assignedMembers = BooleanArray(members.size)
        val assign = IntArray(members.size) { -1 }
        var assignedCount = 0
        val targetCount = min(members.size, slots.size)

        for (p in pairs) {
            if (!assignedMembers[p.member] && !usedSlots[p.slot]) {
                assignedMembers[p.member] = true
                usedSlots[p.slot] = true
                assign[p.member] = p.slot
                assignedCount++
                if (assignedCount == targetCount) break
            }
        }
        return assign.toList()
    }
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
