package org.shilpo.laboon.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSheetMotionTest {

    @Test
    fun dragTracksFingerAndMapsCollapsedRangeToExpansionFraction() {
        val frame = computePlayerSheetDragFrame(
            currentTranslationY = 600f,
            dragAmount = -300f,
            expandedY = 0f,
            collapsedY = 600f,
            miniHeightPx = 100f,
            initialFractionOnDragStart = 0f,
            initialYOnDragStart = 600f,
        )

        assertEquals(300f, frame.translationY, 0.001f)
        assertEquals(0.5f, frame.expansionFraction, 0.001f)
    }

    @Test
    fun expandedPlayerCannotOverscrollAboveItsTopEdge() {
        val expandedFrame = computePlayerSheetDragFrame(
            currentTranslationY = 0f,
            dragAmount = -50f,
            expandedY = 0f,
            collapsedY = 600f,
            miniHeightPx = 100f,
            initialFractionOnDragStart = 1f,
            initialYOnDragStart = 0f,
        )
        val collapsedFrame = computePlayerSheetDragFrame(
            currentTranslationY = 600f,
            dragAmount = 50f,
            expandedY = 0f,
            collapsedY = 600f,
            miniHeightPx = 100f,
            initialFractionOnDragStart = 0f,
            initialYOnDragStart = 600f,
        )

        assertEquals(0f, expandedFrame.translationY, 0.001f)
        assertEquals(1f, expandedFrame.expansionFraction, 0.001f)
        assertEquals(620f, collapsedFrame.translationY, 0.001f)
        assertEquals(0f, collapsedFrame.expansionFraction, 0.001f)
    }

    @Test
    fun queueSwipeStartsOnlyFromExpandedPlayerOutsideBottomGestureExclusion() {
        assertTrue(
            isExpandedPlayerQueueSwipeEligible(
                currentState = PlayerSheetTargetState.EXPANDED,
                expansionFraction = 1f,
                touchY = 400f,
                hostHeightPx = 900f,
                bottomGestureExclusionPx = 32f,
            )
        )
        assertFalse(
            isExpandedPlayerQueueSwipeEligible(
                currentState = PlayerSheetTargetState.COLLAPSED,
                expansionFraction = 1f,
                touchY = 400f,
                hostHeightPx = 900f,
                bottomGestureExclusionPx = 32f,
            )
        )
        assertFalse(
            isExpandedPlayerQueueSwipeEligible(
                currentState = PlayerSheetTargetState.EXPANDED,
                expansionFraction = 0.98f,
                touchY = 400f,
                hostHeightPx = 900f,
                bottomGestureExclusionPx = 32f,
            )
        )
        assertFalse(
            isExpandedPlayerQueueSwipeEligible(
                currentState = PlayerSheetTargetState.EXPANDED,
                expansionFraction = 1f,
                touchY = 868f,
                hostHeightPx = 900f,
                bottomGestureExclusionPx = 32f,
            )
        )
    }

    @Test
    fun queueSwipeOpensAfterUpwardDragThresholdOnlyOnce() {
        assertTrue(
            shouldOpenQueueSheetFromUpwardDrag(
                gestureEligibleAtStart = true,
                queueSwipeAlreadyConsumed = false,
                accumulatedDragY = -5f,
                activationThresholdPx = 4f,
            )
        )
        assertFalse(
            shouldOpenQueueSheetFromUpwardDrag(
                gestureEligibleAtStart = true,
                queueSwipeAlreadyConsumed = false,
                accumulatedDragY = -4f,
                activationThresholdPx = 4f,
            )
        )
        assertFalse(
            shouldOpenQueueSheetFromUpwardDrag(
                gestureEligibleAtStart = true,
                queueSwipeAlreadyConsumed = true,
                accumulatedDragY = -20f,
                activationThresholdPx = 4f,
            )
        )
    }

    @Test
    fun panelProgressTracksTheFingerInBothDirections() {
        assertEquals(
            0.25f,
            playerPanelProgressForDrag(
                panelOpenAtStart = false,
                accumulatedDragY = -250f,
                panelHeightPx = 1_000f,
            ),
            0.001f,
        )
        assertEquals(
            0.75f,
            playerPanelProgressForDrag(
                panelOpenAtStart = true,
                accumulatedDragY = 250f,
                panelHeightPx = 1_000f,
            ),
            0.001f,
        )
    }

    @Test
    fun panelSettleUsesSwipeVelocityBeforeProgress() {
        assertTrue(
            shouldSettlePlayerPanelOpen(
                panelProgress = 0.2f,
                verticalVelocity = -151f,
                progressThreshold = 0.35f,
                velocityThreshold = 150f,
            )
        )
        assertFalse(
            shouldSettlePlayerPanelOpen(
                panelProgress = 0.8f,
                verticalVelocity = 151f,
                progressThreshold = 0.35f,
                velocityThreshold = 150f,
            )
        )
        assertFalse(
            shouldSettlePlayerPanelOpen(
                panelProgress = 0.71f,
                verticalVelocity = 0f,
                progressThreshold = 0.72f,
                velocityThreshold = 150f,
            )
        )
        assertTrue(
            shouldSettlePlayerPanelOpen(
                panelProgress = 0.4f,
                verticalVelocity = 0f,
                progressThreshold = 0.35f,
                velocityThreshold = 150f,
            )
        )
    }

    @Test
    fun downwardDragFromExpandedCollapsesWithoutDismissingPlayer() {
        val target = resolvePlayerSheetTargetState(
            currentState = PlayerSheetTargetState.EXPANDED,
            accumulatedDragY = 80f,
            minDragThresholdPx = 5f,
            verticalVelocity = 0f,
            velocityThreshold = 150f,
            currentFraction = 0.9f,
        )

        assertEquals(PlayerSheetTargetState.COLLAPSED, target)
    }

    @Test
    fun smallDragUsesReleaseVelocityBeforeHalfwayFraction() {
        val target = resolvePlayerSheetTargetState(
            currentState = PlayerSheetTargetState.COLLAPSED,
            accumulatedDragY = -2f,
            minDragThresholdPx = 5f,
            verticalVelocity = -151f,
            velocityThreshold = 150f,
            currentFraction = 0.2f,
        )

        assertEquals(PlayerSheetTargetState.EXPANDED, target)
    }

    @Test
    fun belowThresholdReleaseSettlesToNearestEndpoint() {
        val target = resolvePlayerSheetTargetState(
            currentState = PlayerSheetTargetState.COLLAPSED,
            accumulatedDragY = -4f,
            minDragThresholdPx = 5f,
            verticalVelocity = -100f,
            velocityThreshold = 150f,
            currentFraction = 0.51f,
        )

        assertEquals(PlayerSheetTargetState.EXPANDED, target)
    }
}
