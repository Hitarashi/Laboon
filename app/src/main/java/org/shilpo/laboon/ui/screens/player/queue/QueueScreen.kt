package org.shilpo.laboon.ui.screens.player.queue

import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.ui.design.CodecIcon
import kotlin.math.roundToInt

@Composable
fun QueueScreen(
    queueState: QueueState,
    onTrackClick: (HomeTrack) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    queueFractionProvider: () -> Float = { 1f },
    lazyListState: LazyListState = rememberLazyListState(),
) {
    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        QueueListContent(
            queueState = queueState,
            lazyListState = lazyListState,
            queueFractionProvider = queueFractionProvider,
            onTrackClick = onTrackClick,
            onRemoveUpNext = onRemoveUpNext,
            onMoveUpNext = onMoveUpNext,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun QueueListContent(
    queueState: QueueState,
    lazyListState: LazyListState,
    queueFractionProvider: () -> Float,
    onTrackClick: (HomeTrack) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticView = LocalView.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val itemWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { (configuration.screenWidthDp.dp - 40.dp).toPx() }
    }

    val upcomingTracks = remember { mutableStateListOf<HomeTrack>() }
    LaunchedEffect(queueState.upcoming) {
        upcomingTracks.clear()
        upcomingTracks.addAll(queueState.upcoming)
    }

    val fadeHeight = 24.dp
    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = 12.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = if (queueFractionProvider() >= 0.99f) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
            }
            .drawWithContent {
                drawContent()
                if (queueFractionProvider() <= 0f) return@drawWithContent
                val fadeHeightPx = fadeHeight.toPx()
                if (size.height > 0f && fadeHeightPx > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startY = 0f,
                            endY = fadeHeightPx,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = size.height - fadeHeightPx,
                            endY = size.height,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        queueState.currentTrack?.let { current ->
            item(key = "now_playing_header") {
                Text(
                    text = "NOW PLAYING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }

            item(key = "current_track_${current.id}") {
                QueueItemRow(
                    track = current,
                    isActive = true,
                    onClick = { onTrackClick(current) },
                    onRemove = null,
                    reorderGestureArea = null,
                    itemWidthPx = itemWidthPx,
                    hapticView = hapticView,
                )
            }
        }

        if (queueState.upcoming.isNotEmpty()) {
            item(key = "up_next_header") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "UP NEXT (${queueState.upNextCount})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }

            itemsIndexed(
                items = upcomingTracks,
                key = { _, track -> "upcoming_${track.id}" },
            ) { index, track ->
                var dragOffsetY by remember { mutableFloatStateOf(0f) }
                var isReordering by remember { mutableStateOf(false) }

                val itemHeightPx = with(density) { 68.dp.toPx() }

                QueueItemRow(
                    track = track,
                    isActive = false,
                    onClick = { onTrackClick(track) },
                    onRemove = { onRemoveUpNext(index) },
                    reorderGestureArea = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .pointerInput(index) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            isReordering = true
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            val shiftCount =
                                                (dragOffsetY / itemHeightPx).roundToInt()
                                            val targetIndex = (index + shiftCount).coerceIn(
                                                0,
                                                upcomingTracks.lastIndex
                                            )
                                            if (targetIndex != index) {
                                                onMoveUpNext(index, targetIndex)
                                            }
                                            dragOffsetY = 0f
                                            isReordering = false
                                        },
                                        onDragCancel = {
                                            dragOffsetY = 0f
                                            isReordering = false
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount
                                        },
                                    )
                                },
                        )
                    },
                    itemWidthPx = itemWidthPx,
                    hapticView = hapticView,
                    modifier = Modifier
                        .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                        .zIndex(if (isReordering) 1f else 0f),
                )
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    track: HomeTrack,
    isActive: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    reorderGestureArea: (@Composable () -> Unit)?,
    itemWidthPx: Float,
    hapticView: View,
    modifier: Modifier = Modifier,
) {
    val dismissScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissOffsetAnimatable = remember(track.id) { Animatable(0f) }
    val currentOnRemove by rememberUpdatedState(onRemove)

    val dismissEnabled = onRemove != null
    val dismissHandler = remember(track.id, dismissEnabled, itemWidthPx) {
        if (dismissEnabled && itemWidthPx > 0f) {
            QueueItemDismissGestureHandler(
                scope = dismissScope,
                density = density,
                hapticView = hapticView,
                hapticFeedbackEnabled = true,
                offsetAnimatable = dismissOffsetAnimatable,
                itemWidthPx = itemWidthPx,
                onDismiss = { currentOnRemove?.invoke() },
            )
        } else {
            null
        }
    }

    val isSwipeTargeted = dismissHandler?.isInDismissZone == true
    val currentOffsetPx = dismissOffsetAnimatable.value
    val revealWidthPx = (-currentOffsetPx).coerceAtLeast(0f)
    val dismissBackgroundColor by animateColorAsState(
        targetValue = if (isSwipeTargeted) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "dismissBackgroundColor",
    )
    val dismissGestureModifier = if (dismissEnabled && dismissHandler != null) {
        Modifier.pointerInput(track.id, dismissHandler) {
            detectHorizontalDragGestures(
                onDragStart = { dismissHandler.onDragStart() },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dismissHandler.onHorizontalDrag(dragAmount)
                },
                onDragEnd = { dismissHandler.onDragEnd() },
                onDragCancel = { dismissHandler.onDragCancel() },
            )
        }
    } else {
        Modifier
    }

    val rowShape = RoundedCornerShape(16.dp)
    val rowBackground = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        Color.White.copy(alpha = 0.04f)
    }
    val rowBorderColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.weight(1f),
        ) {
            if (revealWidthPx > 0f) {
                val revealWidthDp = with(density) { revealWidthPx.toDp() }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .height(60.dp)
                        .width(revealWidthDp)
                        .clip(rowShape)
                        .background(dismissBackgroundColor),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = currentOffsetPx }
                    .then(dismissGestureModifier),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(rowBackground)
                        .border(1.dp, rowBorderColor, rowShape)
                        .clickable(enabled = currentOffsetPx == 0f, onClick = onClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!track.artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(track.artworkUrl)
                                    .crossfade(true)
                                    .build(),
                                placeholder = painterResource(R.drawable.app_icon_small),
                                error = painterResource(R.drawable.app_icon_small),
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(46.dp),
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.app_icon_small),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = track.title,
                            fontSize = 14.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = track.artist,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.70f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            CodecIcon(
                                codec = track.codec,
                                height = 9.dp,
                                tint = Color.White.copy(alpha = 0.60f),
                            )
                        }
                    }
                }
            }
        }

        if (reorderGestureArea != null) {
            reorderGestureArea()
        }
    }
}
