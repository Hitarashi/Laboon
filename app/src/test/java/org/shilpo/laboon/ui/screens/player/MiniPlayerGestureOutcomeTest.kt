package org.shilpo.laboon.ui.screens.player

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniPlayerGestureOutcomeTest {

    @Test
    fun smallDragBelowBothIntentThresholdsDoesNothing() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = 20f,
            velocityX = 300f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(MiniPlayerGestureOutcome.None, outcome)
    }

    @Test
    fun shortIntentionalSwipeRightPlaysPrevious() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = 64f,
            velocityX = 200f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(MiniPlayerGestureOutcome.Previous, outcome)
    }

    @Test
    fun shortIntentionalSwipeLeftPlaysNext() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = -64f,
            velocityX = -200f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(MiniPlayerGestureOutcome.Next, outcome)
    }

    @Test
    fun fastCompactFlingCrossesVelocityIntentThreshold() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = -28f,
            velocityX = -1_100f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(MiniPlayerGestureOutcome.Next, outcome)
    }

    @Test
    fun longDragKeepsDismissBehavior() {
        val outcomeRight = resolveMiniPlayerGestureOutcome(
            displacementX = 450f,
            velocityX = 0f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr,
        )
        val outcomeLeft = resolveMiniPlayerGestureOutcome(
            displacementX = -450f,
            velocityX = 0f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(MiniPlayerGestureOutcome.DismissRight, outcomeRight)
        assertEquals(MiniPlayerGestureOutcome.DismissLeft, outcomeLeft)
    }

    @Test
    fun cancelledLongDragBelowDismissThresholdDoesNotSkip() {
        val outcome = resolveMiniPlayerGestureOutcome(
            displacementX = 180f,
            velocityX = 0f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(MiniPlayerGestureOutcome.None, outcome)
    }

    @Test
    fun transportSwipeDirectionStaysPhysicalInRtl() {
        val rightSwipe = resolveMiniPlayerGestureOutcome(
            displacementX = 64f,
            velocityX = 200f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Rtl,
        )
        val leftSwipe = resolveMiniPlayerGestureOutcome(
            displacementX = -64f,
            velocityX = -200f,
            screenWidthPx = 1_000f,
            density = 1f,
            layoutDirection = LayoutDirection.Rtl,
        )

        assertEquals(MiniPlayerGestureOutcome.Previous, rightSwipe)
        assertEquals(MiniPlayerGestureOutcome.Next, leftSwipe)
    }

}
