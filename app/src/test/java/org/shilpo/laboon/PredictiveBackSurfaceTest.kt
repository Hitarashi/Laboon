package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.ui.design.PredictiveBackGeometry
import org.shilpo.laboon.ui.design.PredictiveBackSpec
import org.shilpo.laboon.ui.design.predictiveBackGeometry

class PredictiveBackSurfaceTest {

    private val maxShiftPx = 132f

    private fun geometryAt(
        spec: PredictiveBackSpec,
        progress: Float,
        swipeFromRight: Boolean = false,
    ): PredictiveBackGeometry = predictiveBackGeometry(
        spec = spec,
        progress = progress,
        swipeFromRight = swipeFromRight,
        maxShiftPx = maxShiftPx,
    )

    @Test
    fun specIsConstructibleWithDefaults() {
        val spec = PredictiveBackSpec()
        assertEquals(PredictiveBackSpec.DEFAULT_SCALE_DEPTH, spec.scaleDepth, 0f)
        assertEquals(PredictiveBackSpec.DEFAULT_CORNER_DP, spec.cornerDp, 0f)
        assertEquals(PredictiveBackSpec.DEFAULT_SHIFT_DP, spec.shiftDp, 0f)
        assertEquals(PredictiveBackSpec(), PredictiveBackSpec.MainScreen)
    }

    @Test
    fun specIsConstructibleWithExplicitValues() {
        val spec = PredictiveBackSpec(scaleDepth = 0.5f, cornerDp = 12f, shiftDp = 90f)
        assertEquals(0.5f, spec.scaleDepth, 0f)
        assertEquals(12f, spec.cornerDp, 0f)
        assertEquals(90f, spec.shiftDp, 0f)
        assertEquals(
            PredictiveBackSpec(0.5f, 12f, 90f).copy(cornerDp = 13f),
            spec.copy(cornerDp = 13f),
        )
    }

    @Test
    fun eachCallSiteKeepsTheNumbersItUsedBeforeTheSpecsWereExtracted() {
        assertEquals(PredictiveBackSpec(0.08f, 28f, 44f), PredictiveBackSpec.MainScreen)
        assertEquals(PredictiveBackSpec(0.08f, 24f, 40f), PredictiveBackSpec.HomeTab)
        assertEquals(PredictiveBackSpec(0.10f, 32f, 56f), PredictiveBackSpec.HomeSettings)
    }

    @Test
    fun theTableHoldsExactlyTheThreeCallSites() {
        assertEquals(3, PredictiveBackSpec.Table.size)
        assertEquals(
            listOf(
                PredictiveBackSpec.MainScreen,
                PredictiveBackSpec.HomeTab,
                PredictiveBackSpec.HomeSettings,
            ),
            PredictiveBackSpec.Table,
        )
    }

    @Test
    fun theThreeCallSitesAreDistinctSpecs() {
        val table = PredictiveBackSpec.Table
        assertEquals("every pair of call sites must differ", table.size, table.distinct().size)

        val corners = table.map { it.cornerDp }
        val shifts = table.map { it.shiftDp }
        assertEquals(corners.size, corners.distinct().size)
        assertEquals(shifts.size, shifts.distinct().size)

        assertEquals(2, table.map { it.scaleDepth }.distinct().size)
        assertNotEquals(PredictiveBackSpec.HomeTab, PredictiveBackSpec.MainScreen)
        assertNotEquals(PredictiveBackSpec.HomeSettings, PredictiveBackSpec.HomeTab)
        assertNotEquals(PredictiveBackSpec.HomeSettings, PredictiveBackSpec.MainScreen)
    }

    @Test
    fun atZeroProgressEverySurfaceIsAtRest() {
        PredictiveBackSpec.Table.forEach { spec ->
            val rest = geometryAt(spec, progress = 0f)
            assertEquals("rest scale for $spec", 1f, rest.scale, 0f)
            assertEquals("rest translation for $spec", 0f, rest.translationX, 0f)
            assertEquals("rest corner for $spec", 0f, rest.cornerDp, 0f)
        }
    }

    @Test
    fun atFullProgressEverySurfaceIsAtItsFullDepth() {
        PredictiveBackSpec.Table.forEach { spec ->
            val full = geometryAt(spec, progress = 1f)
            assertEquals("full scale for $spec", 1f - spec.scaleDepth, full.scale, 1e-6f)
            assertEquals("full translation for $spec", maxShiftPx, full.translationX, 1e-4f)
            assertEquals("full corner for $spec", spec.cornerDp, full.cornerDp, 1e-6f)
        }
    }

    @Test
    fun aRightEdgeSwipeTranslatesTheSurfaceTheOtherWay() {
        PredictiveBackSpec.Table.forEach { spec ->
            val fromLeft = geometryAt(spec, progress = 0.5f, swipeFromRight = false)
            val fromRight = geometryAt(spec, progress = 0.5f, swipeFromRight = true)
            assertEquals(-fromLeft.translationX, fromRight.translationX, 1e-4f)
            assertEquals(fromLeft.translationX, maxShiftPx * 0.5f, 1e-4f)
        }
    }

    @Test
    fun progressIsMonotonicAndStaysInsideTheSpecRange() {
        PredictiveBackSpec.Table.forEach { spec ->
            var previousScale = Float.MAX_VALUE
            var previousCorner = -Float.MAX_VALUE
            var previousTranslation = -Float.MAX_VALUE

            for (step in 0..100) {
                val depth = step / 100f
                val at = geometryAt(spec, progress = depth)

                assertTrue("scale must be positive at $depth for $spec", at.scale > 0f)
                assertTrue("scale must not grow with progress for $spec", at.scale <= previousScale)
                assertTrue(
                    "corner must not grow backwards for $spec",
                    at.cornerDp >= previousCorner
                )
                assertTrue(
                    "translation must not grow backwards for $spec",
                    at.translationX >= previousTranslation,
                )
                assertTrue(
                    "corner must stay within the spec for $spec",
                    at.cornerDp <= spec.cornerDp
                )

                previousScale = at.scale
                previousCorner = at.cornerDp
                previousTranslation = at.translationX
            }
        }
    }

    @Test
    fun outOfRangeProgressIsClampedToTheSurfaceDepth() {
        PredictiveBackSpec.Table.forEach { spec ->
            val atRest = geometryAt(spec, progress = 0f)
            val atFullDepth = geometryAt(spec, progress = 1f)

            listOf(-0.5f, -1f, -1000f).forEach { beyondRest ->
                assertEquals(
                    "negative progress $beyondRest for $spec",
                    atRest,
                    geometryAt(spec, beyondRest)
                )
            }
            listOf(1.5f, 2f, 1000f).forEach { beyondFull ->
                assertEquals(
                    "overshoot $beyondFull for $spec",
                    atFullDepth,
                    geometryAt(spec, beyondFull),
                )
            }
        }
    }

    @Test
    fun aSpecWithNoShiftBudgetStaysHorizontallyStill() {
        val still = predictiveBackGeometry(
            spec = PredictiveBackSpec.HomeSettings,
            progress = 1f,
            swipeFromRight = false,
            maxShiftPx = 0f,
        )
        assertEquals(0f, still.translationX, 0f)
        assertEquals(PredictiveBackSpec.HomeSettings.cornerDp, still.cornerDp, 1e-6f)
    }

    @Test
    fun theClipThresholdSitsBelowEveryFullDepthCorner() {
        val threshold = PredictiveBackSpec.MIN_CLIP_CORNER_DP
        PredictiveBackSpec.Table.forEach { spec ->
            assertTrue(
                "corner $spec must rise past the clip threshold at full depth",
                spec.cornerDp > threshold,
            )
        }
    }
}
