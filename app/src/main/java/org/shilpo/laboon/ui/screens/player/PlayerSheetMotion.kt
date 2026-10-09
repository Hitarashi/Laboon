package org.shilpo.laboon.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class PlayerSheetDragFrame(
    val translationY: Float,
    val expansionFraction: Float,
)

internal enum class PlayerSheetTargetState {
    COLLAPSED,
    EXPANDED,
}

/** The sheet follows the finger; only the collapsed edge allows 20% mini-height overscroll. */
internal fun computePlayerSheetDragFrame(
    currentTranslationY: Float,
    dragAmount: Float,
    expandedY: Float,
    collapsedY: Float,
    miniHeightPx: Float,
    initialFractionOnDragStart: Float,
    initialYOnDragStart: Float,
): PlayerSheetDragFrame {
    val newY = (currentTranslationY + dragAmount).coerceIn(
        expandedY,
        collapsedY + miniHeightPx * 0.2f,
    )
    val denominator = (collapsedY - expandedY).coerceAtLeast(1f)
    val dragRatio = (initialYOnDragStart - newY) / denominator
    val newFraction = (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
    return PlayerSheetDragFrame(
        translationY = newY,
        expansionFraction = newFraction,
    )
}

internal fun isExpandedPlayerQueueSwipeEligible(
    currentState: PlayerSheetTargetState,
    expansionFraction: Float,
    touchY: Float,
    hostHeightPx: Float,
    bottomGestureExclusionPx: Float,
): Boolean {
    val bottomGestureBoundaryY = (hostHeightPx - bottomGestureExclusionPx).coerceAtLeast(0f)
    return currentState == PlayerSheetTargetState.EXPANDED &&
        expansionFraction >= 0.99f &&
        touchY < bottomGestureBoundaryY
}

internal fun shouldOpenQueueSheetFromUpwardDrag(
    gestureEligibleAtStart: Boolean,
    queueSwipeAlreadyConsumed: Boolean,
    accumulatedDragY: Float,
    activationThresholdPx: Float,
): Boolean = gestureEligibleAtStart &&
    !queueSwipeAlreadyConsumed &&
    accumulatedDragY < -activationThresholdPx

internal fun playerPanelProgressForDrag(
    panelOpenAtStart: Boolean,
    accumulatedDragY: Float,
    panelHeightPx: Float,
): Float {
    val dragProgress = accumulatedDragY / panelHeightPx.coerceAtLeast(1f)
    return if (panelOpenAtStart) {
        (1f - dragProgress.coerceAtLeast(0f)).coerceIn(0f, 1f)
    } else {
        (-dragProgress).coerceAtLeast(0f).coerceIn(0f, 1f)
    }
}

internal fun shouldSettlePlayerPanelOpen(
    panelProgress: Float,
    verticalVelocity: Float,
    progressThreshold: Float,
    velocityThreshold: Float,
): Boolean = when {
    verticalVelocity > velocityThreshold -> false
    verticalVelocity < -velocityThreshold -> true
    else -> panelProgress >= progressThreshold
}

/** Resolves release exactly like Pixel Player: direction, then velocity, then the halfway point. */
internal fun resolvePlayerSheetTargetState(
    currentState: PlayerSheetTargetState,
    accumulatedDragY: Float,
    minDragThresholdPx: Float,
    verticalVelocity: Float,
    velocityThreshold: Float,
    currentFraction: Float,
): PlayerSheetTargetState = when {
    currentState == PlayerSheetTargetState.EXPANDED && accumulatedDragY <= 0f ->
        PlayerSheetTargetState.EXPANDED

    abs(accumulatedDragY) > minDragThresholdPx ->
        if (accumulatedDragY < 0f) PlayerSheetTargetState.EXPANDED else PlayerSheetTargetState.COLLAPSED

    abs(verticalVelocity) > velocityThreshold ->
        if (verticalVelocity < 0f) PlayerSheetTargetState.EXPANDED else PlayerSheetTargetState.COLLAPSED

    currentFraction > 0.5f -> PlayerSheetTargetState.EXPANDED
    else -> PlayerSheetTargetState.COLLAPSED
}

internal fun playerSheetCollapseDamping(currentFraction: Float): Float = lerp(
    start = Spring.DampingRatioNoBouncy,
    stop = Spring.DampingRatioLowBouncy,
    fraction = currentFraction,
)

internal fun playerSheetCollapseSquash(currentFraction: Float): Float = lerp(
    start = 1f,
    stop = 0.97f,
    fraction = currentFraction,
)

/** Keeps translation and expansion in one mutation, so a new drag interrupts the same motion. */
internal class PlayerSheetMotionController(
    private val translationY: Animatable<Float, AnimationVector1D>,
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val collapsedY: Float,
    private val expandedY: Float = 0f,
) {
    private val mutex = MutatorMutex()

    suspend fun animateTo(
        targetExpanded: Boolean,
        animationSpec: AnimationSpec<Float>,
        initialVelocity: Float = 0f,
    ) {
        val targetFraction = if (targetExpanded) 1f else 0f
        val targetY = if (targetExpanded) expandedY else collapsedY
        val velocityScale = (collapsedY - expandedY).coerceAtLeast(1f)
        if (
            translationY.value == targetY &&
            expansionFraction.value == targetFraction &&
            !translationY.isRunning &&
            !expansionFraction.isRunning
        ) return

        mutex.mutate {
            coroutineScope {
                launch {
                    translationY.animateTo(
                        targetValue = targetY,
                        initialVelocity = initialVelocity,
                        animationSpec = animationSpec,
                    )
                }
                launch {
                    expansionFraction.animateTo(
                        targetValue = targetFraction,
                        initialVelocity = initialVelocity / velocityScale,
                        animationSpec = animationSpec,
                    )
                }
            }
        }
    }

    suspend fun stop() {
        translationY.stop()
        expansionFraction.stop()
    }

    suspend fun snapTo(frame: PlayerSheetDragFrame) {
        mutex.mutate {
            translationY.snapTo(frame.translationY)
            expansionFraction.snapTo(frame.expansionFraction)
        }
    }
}

/** Intercepts panel swipes before they can collapse the expanded player sheet. */
internal class PlayerSheetVerticalDragGestureHandler(
    private val scope: CoroutineScope,
    private val densityProvider: () -> Density,
    private val motionController: PlayerSheetMotionController,
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val translationY: Animatable<Float, AnimationVector1D>,
    private val expandedYProvider: () -> Float,
    private val collapsedYProvider: () -> Float,
    private val miniHeightPxProvider: () -> Float,
    private val queueGestureBottomExclusionPxProvider: () -> Float,
    private val currentStateProvider: () -> PlayerSheetTargetState,
    private val isQueueOrLyricsPanelOpenProvider: () -> Boolean,
    private val onPanelDragStart: () -> Unit,
    private val onBeginQueuePanelDrag: () -> Unit,
    private val onPanelDragProgress: (Float) -> Unit,
    private val onPanelDragSettle: (Boolean) -> Unit,
    private val onAnimateSheet: suspend (Boolean, AnimationSpec<Float>?, Float) -> Unit,
    private val onExpandSheetState: () -> Unit,
    private val onCollapseSheetState: () -> Unit,
    private val onCollapseSquash: suspend (Float) -> Unit,
) {
    private var initialFractionOnDragStart = 0f
    private var initialYOnDragStart = 0f
    private var accumulatedDragY = 0f
    private var panelHeightPx = 1f
    private var panelOpenAtDragStart = false
    private var queueGestureEligibleAtStart = false
    private var panelDragActive = false
    private var dragSnapJob: Job? = null

    fun onDragStart(startPosition: Offset, hostHeightPx: Float) {
        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            motionController.stop()
        }
        initialFractionOnDragStart = expansionFraction.value
        initialYOnDragStart = translationY.value
        accumulatedDragY = 0f
        panelHeightPx = hostHeightPx.coerceAtLeast(1f)
        panelOpenAtDragStart = isQueueOrLyricsPanelOpenProvider()
        panelDragActive = panelOpenAtDragStart &&
            currentStateProvider() == PlayerSheetTargetState.EXPANDED
        if (panelDragActive) onPanelDragStart()
        queueGestureEligibleAtStart = isExpandedPlayerQueueSwipeEligible(
            currentState = currentStateProvider(),
            expansionFraction = expansionFraction.value,
            touchY = startPosition.y,
            hostHeightPx = hostHeightPx,
            bottomGestureExclusionPx = queueGestureBottomExclusionPxProvider(),
        ) && !panelOpenAtDragStart
    }

    fun onVerticalDrag(uptimeMillis: Long, position: Offset, dragAmount: Float, velocityTracker: VelocityTracker) {
        accumulatedDragY += dragAmount
        velocityTracker.addPosition(uptimeMillis, position)
        val queueDragThresholdPx = with(densityProvider()) { 4.dp.toPx() }
        if (!panelDragActive && shouldOpenQueueSheetFromUpwardDrag(
                gestureEligibleAtStart = queueGestureEligibleAtStart,
                queueSwipeAlreadyConsumed = false,
                accumulatedDragY = accumulatedDragY,
                activationThresholdPx = queueDragThresholdPx,
            )
        ) {
            panelOpenAtDragStart = false
            panelDragActive = true
            onPanelDragStart()
            onBeginQueuePanelDrag()
        }
        if (panelDragActive) {
            onPanelDragProgress(
                playerPanelProgressForDrag(
                    panelOpenAtStart = panelOpenAtDragStart,
                    accumulatedDragY = accumulatedDragY,
                    panelHeightPx = panelHeightPx,
                )
            )
            return
        }

        val frame = computePlayerSheetDragFrame(
            currentTranslationY = translationY.value,
            dragAmount = dragAmount,
            expandedY = expandedYProvider(),
            collapsedY = collapsedYProvider(),
            miniHeightPx = miniHeightPxProvider(),
            initialFractionOnDragStart = initialFractionOnDragStart,
            initialYOnDragStart = initialYOnDragStart,
        )
        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            motionController.snapTo(frame)
        }
    }

    fun onDragEnd(velocityTracker: VelocityTracker) {
        dragSnapJob?.cancel()
        dragSnapJob = null
        if (panelDragActive) {
            val velocityY = velocityTracker.calculateVelocity().y
            val panelProgress = playerPanelProgressForDrag(
                panelOpenAtStart = panelOpenAtDragStart,
                accumulatedDragY = accumulatedDragY,
                panelHeightPx = panelHeightPx,
            )
            onPanelDragSettle(
                shouldSettlePlayerPanelOpen(
                    panelProgress = panelProgress,
                    verticalVelocity = velocityY,
                    progressThreshold = if (panelOpenAtDragStart) 0.72f else 0.35f,
                    velocityThreshold = 150f,
                )
            )
            resetGestureState()
            return
        }
        val expandedPlayerDraggedUpward =
            currentStateProvider() == PlayerSheetTargetState.EXPANDED &&
                expansionFraction.value >= 0.99f &&
                accumulatedDragY < 0f
        if (expandedPlayerDraggedUpward) {
            resetGestureState()
            return
        }
        val velocityY = velocityTracker.calculateVelocity().y
        val currentFraction = expansionFraction.value
        val target = resolvePlayerSheetTargetState(
            currentState = currentStateProvider(),
            accumulatedDragY = accumulatedDragY,
            minDragThresholdPx = with(densityProvider()) { 5.dp.toPx() },
            verticalVelocity = velocityY,
            velocityThreshold = 150f,
            currentFraction = currentFraction,
        )

        scope.launch {
            if (target == PlayerSheetTargetState.EXPANDED) {
                launch { onAnimateSheet(true, null, 0f) }
                onExpandSheetState()
            } else {
                val dynamicDamping = playerSheetCollapseDamping(currentFraction)
                launch { onCollapseSquash(playerSheetCollapseSquash(currentFraction)) }
                launch {
                    onAnimateSheet(
                        false,
                        spring(dampingRatio = dynamicDamping, stiffness = Spring.StiffnessLow),
                        velocityY,
                    )
                }
                onCollapseSheetState()
            }
        }
        resetGestureState()
    }

    fun onDragCancel(velocityTracker: VelocityTracker) = onDragEnd(velocityTracker)

    private fun resetGestureState() {
        accumulatedDragY = 0f
        panelHeightPx = 1f
        panelOpenAtDragStart = false
        queueGestureEligibleAtStart = false
        panelDragActive = false
    }
}

internal fun Modifier.playerSheetVerticalDragGesture(
    enabled: Boolean,
    handler: PlayerSheetVerticalDragGestureHandler,
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled, handler) {
        val velocityTracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { startPosition ->
                velocityTracker.resetTracking()
                handler.onDragStart(startPosition, size.height.toFloat())
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                handler.onVerticalDrag(
                    uptimeMillis = change.uptimeMillis,
                    position = change.position,
                    dragAmount = dragAmount,
                    velocityTracker = velocityTracker,
                )
            },
            onDragEnd = { handler.onDragEnd(velocityTracker) },
            onDragCancel = { handler.onDragCancel(velocityTracker) },
        )
    }
}
