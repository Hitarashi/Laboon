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
