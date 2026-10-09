package org.shilpo.laboon.ui.screens.player.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import kotlin.math.abs

private data class ScrollMetrics(
    val progress: Float,
    val totalItemsCount: Int,
    val maxScrollIndex: Int,
    val scrollableHeight: Float,
)

@Composable
fun ExpressiveScrollBar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    thickness: Dp = 6.dp,
    indicatorExpandedWidth: Dp = 22.dp,
    paddingEnd: Dp = 4.dp,
) {
    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    var pendingScrollIndex by remember { mutableIntStateOf(-1) }
    val displayedProgress = remember { Animatable(0f) }
    var hasSyncedDisplayedProgress by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val isInteracting = isPressed || isDragging

    val animatedWidth by animateDpAsState(
        targetValue = if (isInteracting) indicatorExpandedWidth else thickness,
        animationSpec = tween(durationMillis = 200),
        label = "WidthAnimation",
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "IconAlpha",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(indicatorExpandedWidth + paddingEnd),
    ) {
        val density = LocalDensity.current
        val constraintsMaxHeight = maxHeight
        val coarseJumpThresholdPx = with(density) { 16.dp.toPx() }
        val smoothJumpMinDistancePx = with(density) { 10.dp.toPx() }

        val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
        val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }

        if (!canScrollForward && !canScrollBackward) return@BoxWithConstraints

        fun getScrollStats(): ScrollMetrics {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val totalItemsCount = layoutInfo.totalItemsCount

            if (visibleItems.isEmpty() || totalItemsCount == 0) {
                return ScrollMetrics(
                    progress = 0f,
                    totalItemsCount = totalItemsCount,
                    maxScrollIndex = 1,
                    scrollableHeight = 1f,
                )
            }

            val viewportHeightPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
                .toFloat().coerceAtLeast(1f)
            val firstVisible = visibleItems.first()
            val lastVisible = visibleItems.last()
            val visibleCount = visibleItems.size
            val estimatedItemHeightPx = (lastVisible.offset + lastVisible.size - firstVisible.offset)
                .toFloat() / visibleCount.coerceAtLeast(1)

            val totalContentHeightPx = (totalItemsCount * estimatedItemHeightPx).coerceAtLeast(1f)
            val currentScrollPx = (firstVisible.index * estimatedItemHeightPx + listState.firstVisibleItemScrollOffset)
                .coerceAtLeast(0f)
            val totalScrollablePx = (totalContentHeightPx - viewportHeightPx).coerceAtLeast(1f)

            val realProgress = if (!canScrollForward && totalItemsCount > 0) {
                1f
            } else if (!canScrollBackward) {
                0f
            } else {
                (currentScrollPx / totalScrollablePx).coerceIn(0f, 0.999f)
            }

            val availableHeight = with(density) { constraintsMaxHeight.toPx() }
            val handleHeightPx = with(density) { minHeight.toPx() }
            val scrollableHeight = (availableHeight - handleHeightPx).coerceAtLeast(1f)

            return ScrollMetrics(
                progress = realProgress,
                totalItemsCount = totalItemsCount,
                maxScrollIndex = (totalItemsCount - visibleCount).coerceAtLeast(1),
                scrollableHeight = scrollableHeight,
            )
        }

        fun updateProgressFromTouch(touchY: Float, grabOffset: Float) {
            val stats = getScrollStats()
            val targetHandleTop = touchY - grabOffset
            val newProgress = (targetHandleTop / stats.scrollableHeight).coerceIn(0f, 1f)
            dragProgress = newProgress
            val targetIndex = (newProgress * (stats.totalItemsCount - 1))
                .toInt().coerceIn(0, (stats.totalItemsCount - 1).coerceAtLeast(0))
            pendingScrollIndex = targetIndex
        }

        LaunchedEffect(Unit) {
            snapshotFlow { pendingScrollIndex }
                .distinctUntilChanged()
                .collectLatest { index ->
                    if (index >= 0) {
                        listState.scrollToItem(index)
                    }
                }
        }

        LaunchedEffect(listState, constraintsMaxHeight, minHeight, isDragging) {
            if (isDragging) return@LaunchedEffect
            snapshotFlow { getScrollStats() }
                .distinctUntilChanged()
                .collectLatest { stats ->
                    val targetProgress = stats.progress
                    if (!hasSyncedDisplayedProgress) {
                        displayedProgress.snapTo(targetProgress)
                        hasSyncedDisplayedProgress = true
                    } else {
                        val sourceIsScrolling = listState.isScrollInProgress
                        val handleDeltaPx = abs(targetProgress - displayedProgress.value) * stats.scrollableHeight
                        val estimatedStepPx = stats.scrollableHeight / stats.maxScrollIndex.coerceAtLeast(1).toFloat()
                        val shouldSmoothJump = !sourceIsScrolling &&
                            estimatedStepPx >= coarseJumpThresholdPx &&
                            handleDeltaPx >= smoothJumpMinDistancePx

                        if (shouldSmoothJump) {
                            displayedProgress.animateTo(
                                targetValue = targetProgress,
                                animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing),
                            )
                        } else {
                            displayedProgress.snapTo(targetProgress)
                        }
                    }
                }
        }

        LaunchedEffect(isDragging, dragProgress) {
            if (isDragging && dragProgress >= 0f) {
                displayedProgress.snapTo(dragProgress)
                hasSyncedDisplayedProgress = true
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    var grabOffset = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val stats = getScrollStats()
                            val handleTop = displayedProgress.value * stats.scrollableHeight
                            val handleHeight = minHeight.toPx()
                            grabOffset = if (offset.y in handleTop..(handleTop + handleHeight)) {
                                offset.y - handleTop
                            } else {
                                handleHeight / 2f
                            }
                            updateProgressFromTouch(offset.y, grabOffset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateProgressFromTouch(change.position.y, grabOffset)
                        },
                        onDragEnd = {
                            isDragging = false
                            dragProgress = -1f
                            pendingScrollIndex = -1
                        },
                        onDragCancel = {
                            isDragging = false
                            dragProgress = -1f
                            pendingScrollIndex = -1
                        },
                    )
                },
        ) {
            val stats = getScrollStats()
            val availableHeightPx = with(density) { constraintsMaxHeight.toPx() }
            val handleHeightPx = with(density) { minHeight.toPx() }
            val currentProgress = displayedProgress.value.coerceIn(0f, 1f)
            val currentHandleTopPx = currentProgress * (availableHeightPx - handleHeightPx).coerceAtLeast(0f)
            val currentHandleWidthPx = with(density) { animatedWidth.toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val left = size.width - currentHandleWidthPx
                val cornerRadius = CornerRadius(currentHandleWidthPx / 2f, currentHandleWidthPx / 2f)
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(left, currentHandleTopPx),
                    size = Size(currentHandleWidthPx, handleHeightPx),
                    cornerRadius = cornerRadius,
                )
            }

            if (iconAlpha > 0f) {
                val handleTopDp = with(density) { currentHandleTopPx.toDp() }
                Icon(
                    painter = materialSymbolPainterResource("unfold_more"),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = currentHandleTopPx + (handleHeightPx - 20.dp.toPx()) / 2f
                            translationX = (size.width - currentHandleWidthPx) + (currentHandleWidthPx - 20.dp.toPx()) / 2f
                            alpha = iconAlpha
                        },
                )
            }
        }
    }
}
