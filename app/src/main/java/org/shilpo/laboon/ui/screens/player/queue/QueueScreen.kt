@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package org.shilpo.laboon.ui.screens.player.queue

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.DiscoveryStatus
import org.shilpo.laboon.playback.QueueEntry
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.ui.design.TrackCodecBadges
import org.shilpo.laboon.ui.design.painterResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen(
    queueState: QueueState,
    onQueueEntryClick: (Long) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onPromoteAutoplay: (Long) -> Unit,
    @Suppress("UNUSED_PARAMETER") onClearUpcoming: () -> Unit,
    onRetryDiscovery: () -> Unit,
    isDiscovering: Boolean,
    discoveryStatus: DiscoveryStatus,
    modifier: Modifier = Modifier,
    queueFractionProvider: () -> Float = { 1f },
    lazyListState: LazyListState = rememberLazyListState(),
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        QueueListContent(
            queueState = queueState,
            lazyListState = lazyListState,
            queueFractionProvider = queueFractionProvider,
            onQueueEntryClick = onQueueEntryClick,
            onRemoveUpNext = onRemoveUpNext,
            onMoveUpNext = onMoveUpNext,
            onRetryDiscovery = onRetryDiscovery,
            isDiscovering = isDiscovering,
            discoveryStatus = discoveryStatus,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun QueueListContent(
    queueState: QueueState,
    lazyListState: LazyListState,
    queueFractionProvider: () -> Float,
    onQueueEntryClick: (Long) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRetryDiscovery: () -> Unit,
    isDiscovering: Boolean,
    discoveryStatus: DiscoveryStatus,
    modifier: Modifier = Modifier,
) {
    val hapticView = LocalView.current
    val upcomingEntries = remember { mutableStateListOf<QueueEntry>() }

    var dragFromIndex by remember { mutableStateOf<Int?>(null) }
    var dragToIndex by remember { mutableStateOf<Int?>(null) }
    var reorderHandleInUse by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            if (dragFromIndex == null) {
                dragFromIndex = from.index
            }
            dragToIndex = to.index
            upcomingEntries.add(to.index, upcomingEntries.removeAt(from.index))
        },
    )

    LaunchedEffect(queueState.playbackUpcomingEntries) {
        if (!reorderableState.isAnyItemDragging) {
            upcomingEntries.clear()
            upcomingEntries.addAll(queueState.playbackUpcomingEntries)
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            val from = dragFromIndex
            val to = dragToIndex
            if (from != null && to != null && from != to) {
                onMoveUpNext(from, to)
            }
            dragFromIndex = null
            dragToIndex = null
        }
    }

    val fadeHeight = 24.dp

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 9.dp, vertical = 2.5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${upcomingEntries.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            if (upcomingEntries.isNotEmpty()) {
                Text(
                    text = "Swipe to remove",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                )
            }
        }

        if (upcomingEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 32.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Queue is empty. Use 'Play Next' or 'Add to Queue' to queue up tracks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                userScrollEnabled = !(reorderableState.isAnyItemDragging || reorderHandleInUse),
                contentPadding = PaddingValues(
                    top = 2.dp,
                    bottom = 44.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier
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
                itemsIndexed(
                    items = upcomingEntries,
                    key = { _, entry -> "queue-entry-${entry.id}" },
                    contentType = { _, _ -> "queue_item" },
                ) { index, entry ->
                    ReorderableItem(
                        state = reorderableState,
                        key = "queue-entry-${entry.id}",
                        modifier = if (reorderableState.isAnyItemDragging) Modifier else Modifier.animateItem(),
                    ) { isDragging ->
                        UpNextTrackRow(
                            track = entry.track,
                            index = index,
                            count = upcomingEntries.size,
                            isDragging = isDragging,
                            isAnyDragging = reorderableState.isAnyItemDragging,
                            onClick = { onQueueEntryClick(entry.id) },
                            onRemove = {
                                upcomingEntries.removeAt(index)
                                onRemoveUpNext(index)
                            },
                            dragHandle = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier
                                        .draggableHandle(
                                            onDragStarted = {
                                                reorderHandleInUse = true
                                                ViewCompat.performHapticFeedback(
                                                    hapticView,
                                                    HapticFeedbackConstantsCompat.GESTURE_START,
                                                )
                                            },
                                            onDragStopped = {
                                                reorderHandleInUse = false
                                                ViewCompat.performHapticFeedback(
                                                    hapticView,
                                                    HapticFeedbackConstantsCompat.GESTURE_END,
                                                )
                                            },
                                        )
                                        .size(40.dp),
                                ) {
                                    ReorderGripAffordance(
                                        tint = if (isDragging) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f)
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (isDiscovering || discoveryStatus != DiscoveryStatus.IDLE || queueState.autoplaySuppressed) {
                    item(key = "autoplay_status") {
                        AutoplayStatus(
                            isDiscovering = isDiscovering,
                            status = discoveryStatus,
                            suppressed = queueState.autoplaySuppressed,
                            onRetry = onRetryDiscovery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun UpNextTrackRow(
    track: HomeTrack,
    index: Int,
    count: Int,
    isDragging: Boolean,
    isAnyDragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val currentOnRemove by rememberUpdatedState(onRemove)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnRemove()
                true
            } else {
                false
            }
        }
    )

    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val itemShape = if (isDragging) RoundedCornerShape(16.dp) else shapes.shape

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "queueItemScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "queueItemElevation",
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                compositingStrategy =
                    if (isDragging) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .shadow(
                elevation = elevation,
                shape = itemShape,
                spotColor = Color.Black.copy(alpha = 0.35f),
                ambientColor = Color.Black.copy(alpha = 0.15f),
            ),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isAnyDragging,
        gesturesEnabled = !isAnyDragging,
        backgroundContent = {
            val isTargeted = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val backgroundColor by animateColorAsState(
                targetValue = if (isTargeted) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                },
                animationSpec = motionScheme.fastEffectsSpec(),
                label = "dismissBgColor",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (isTargeted) 1.2f else 0.9f,
                animationSpec = motionScheme.fastSpatialSpec(),
                label = "dismissIconScale",
            )
            val iconAlpha by animateFloatAsState(
                targetValue = if (isTargeted) 1.0f else 0.70f,
                animationSpec = motionScheme.fastEffectsSpec(),
                label = "dismissIconAlpha",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(itemShape)
                    .background(backgroundColor)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_clear),
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = iconAlpha),
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        },
    ) {
        SegmentedListItem(
            shapes = if (isDragging) shapes.copy(shape = RoundedCornerShape(16.dp)) else shapes,
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = dismissState.currentValue == SwipeToDismissBoxValue.Settled && !isAnyDragging,
                    onClick = onClick,
                ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.app_icon_small),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            },
            content = {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TrackCodecBadges(
                        track = track,
                        height = 9.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            },
            trailingContent = dragHandle,
        )
    }
}

@Composable
private fun AutoplayStatus(
    isDiscovering: Boolean,
    status: DiscoveryStatus,
    suppressed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isDiscovering || status == DiscoveryStatus.LOADING -> {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Finding related songs…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            suppressed -> Text(
                text = "Autoplay is paused because upcoming songs were cleared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            status == DiscoveryStatus.FAILED -> {
                Text(
                    text = "Couldn’t load song suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = onRetry) { Text("Retry") }
            }

            status == DiscoveryStatus.EXHAUSTED -> {
                Text(
                    text = "No more related songs were found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun ReorderGripAffordance(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
) {
    Canvas(modifier = modifier.size(width = 14.dp, height = 20.dp)) {
        val dotRadius = 1.7.dp.toPx()
        val spacingY = 5.5.dp.toPx()
        val spacingX = 5.5.dp.toPx()
        val startX = (size.width - spacingX) / 2f
        val startY = (size.height - (2 * spacingY)) / 2f

        for (row in 0..2) {
            val cy = startY + row * spacingY
            drawCircle(color = tint, radius = dotRadius, center = Offset(startX, cy))
            drawCircle(color = tint, radius = dotRadius, center = Offset(startX + spacingX, cy))
        }
    }
}
