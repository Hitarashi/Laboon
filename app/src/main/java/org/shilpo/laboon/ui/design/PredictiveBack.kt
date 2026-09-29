package org.shilpo.laboon.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

data class PredictiveBackSpec(
    val scaleDepth: Float = DEFAULT_SCALE_DEPTH,
    val cornerDp: Float = DEFAULT_CORNER_DP,
    val shiftDp: Float = DEFAULT_SHIFT_DP,
) {
    companion object {
        const val DEFAULT_SCALE_DEPTH: Float = 0.08f
        const val DEFAULT_CORNER_DP: Float = 28f
        const val DEFAULT_SHIFT_DP: Float = 44f
        const val MIN_CLIP_CORNER_DP: Float = 0.5f

        val MainScreen: PredictiveBackSpec = PredictiveBackSpec()
        val HomeTab: PredictiveBackSpec = PredictiveBackSpec(
            scaleDepth = 0.08f,
            cornerDp = 24f,
            shiftDp = 40f,
        )
        val HomeSettings: PredictiveBackSpec = PredictiveBackSpec(
            scaleDepth = 0.10f,
            cornerDp = 32f,
            shiftDp = 56f,
        )

        val Table: List<PredictiveBackSpec> = listOf(MainScreen, HomeTab, HomeSettings)
    }
}

data class PredictiveBackGeometry(
    val scale: Float,
    val translationX: Float,
    val cornerDp: Float,
)

fun predictiveBackGeometry(
    spec: PredictiveBackSpec,
    progress: Float,
    swipeFromRight: Boolean,
    maxShiftPx: Float,
): PredictiveBackGeometry {
    val depth = progress.coerceIn(0f, 1f)
    return PredictiveBackGeometry(
        scale = 1f - depth * spec.scaleDepth,
        translationX = (if (swipeFromRight) -depth else depth) * maxShiftPx,
        cornerDp = depth * spec.cornerDp,
    )
}

class PredictiveBackState(
    val progress: Float,
    val swipeFromRight: Boolean,
) {
    fun progressFor(active: Boolean): Float = if (active) progress else 0f
}

@Composable
fun rememberPredictiveBackState(enabled: Boolean, onBack: () -> Unit): PredictiveBackState {
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )

    val transition = backState.transitionState
    val inProgress = transition is NavigationEventTransitionState.InProgress
    val event = (transition as? NavigationEventTransitionState.InProgress)?.latestEvent

    return PredictiveBackState(
        progress = if (enabled && inProgress) event?.progress ?: 0f else 0f,
        swipeFromRight = event?.swipeEdge == NavigationEvent.EDGE_RIGHT,
    )
}

@Composable
fun PredictiveBackSurface(
    state: PredictiveBackState,
    spec: PredictiveBackSpec,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    val maxShiftPx = with(LocalDensity.current) { spec.shiftDp.dp.toPx() }
    val target = predictiveBackGeometry(
        spec = spec,
        progress = state.progressFor(active),
        swipeFromRight = state.swipeFromRight,
        maxShiftPx = maxShiftPx,
    )

    val motionScheme = MaterialTheme.motionScheme

    val animatedScale by animateFloatAsState(
        targetValue = target.scale,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "backScale",
    )
    val animatedCornerRaw by animateFloatAsState(
        targetValue = target.cornerDp,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "backCorner",
    )
    val animatedShift by animateFloatAsState(
        targetValue = target.translationX,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "backShift",
    )

    val animatedCorner = animatedCornerRaw.coerceAtLeast(0f)

    content(
        modifier.graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
            translationX = animatedShift
            shape = RoundedCornerShape(animatedCorner.dp)
            clip = animatedCorner > PredictiveBackSpec.MIN_CLIP_CORNER_DP
        }
    )
}
