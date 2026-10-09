@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package org.shilpo.laboon.ui.screens.player.queue

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.shilpo.laboon.playback.SleepTimerManagerHolder
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.DiscoveryStatus
import org.shilpo.laboon.playback.PlaybackManagerHolder
import org.shilpo.laboon.playback.QueueEntry
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.screens.home.HomeDailyMixSongOptionsSheet
import org.shilpo.laboon.ui.design.TrackCodecBadges
import androidx.compose.ui.res.painterResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen(
    queueState: QueueState,
    onQueueEntryClick: (Long) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onPromoteAutoplay: (Long) -> Unit,
    onClearUpcoming: () -> Unit,
    onRetryDiscovery: () -> Unit,
    isDiscovering: Boolean,
    discoveryStatus: DiscoveryStatus,
    modifier: Modifier = Modifier,
    queueFractionProvider: () -> Float = { 1f },
    lazyListState: LazyListState = rememberLazyListState(),
    showHeader: Boolean = true,
    currentTrack: HomeTrack? = queueState.currentTrack,
    isShuffle: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    isPlaying: Boolean = true,
    showFloatingControls: Boolean = true,
    onMoreOptions: (() -> Unit)? = null,
    onTrackMoreOptions: ((HomeTrack) -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val sleepTimerManager = remember(context) { SleepTimerManagerHolder.getInstance(context) }
    val sleepTimerState by sleepTimerManager.state.collectAsState()
    var showTimerOptionsSheet by remember { mutableStateOf(false) }
    var internalSelectedTrackForOptions by remember { mutableStateOf<HomeTrack?>(null) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var showClearQueueDialog by remember { mutableStateOf(false) }

    val upcomingEntries = remember { mutableStateListOf<QueueEntry>() }

    var dragFromIndex by remember { mutableStateOf<Int?>(null) }
    var dragToIndex by remember { mutableStateOf<Int?>(null) }
    var reorderHandleInUse by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            val staticItemCount = if (currentTrack == null) 0 else 1
            val fromIndex = from.index - staticItemCount
            val toIndex = to.index - staticItemCount
            if (fromIndex in upcomingEntries.indices && toIndex in upcomingEntries.indices) {
                if (dragFromIndex == null) {
                    dragFromIndex = fromIndex
                }
                dragToIndex = toIndex
                upcomingEntries.add(toIndex, upcomingEntries.removeAt(fromIndex))
            }
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

    BackHandler(enabled = isFabExpanded) {
        isFabExpanded = false
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showHeader) {
                QueueTopHeader(
                    count = upcomingEntries.size,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (upcomingEntries.isEmpty() && currentTrack == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
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
                    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val bottomPadding = if (showFloatingControls) 110.dp + bottomInset else 16.dp + bottomInset

                    LazyColumn(
                        state = lazyListState,
                        userScrollEnabled = !(reorderableState.isAnyItemDragging || reorderHandleInUse),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = bottomPadding,
                            start = 12.dp,
                            end = if (lazyListState.canScrollForward || lazyListState.canScrollBackward) 26.dp else 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        currentTrack?.let { track ->
                            item(key = "current-queue-track", contentType = "current_track") {
                                CurrentQueueTrackRow(
                                    track = track,
                                    isPlaying = isPlaying,
                                    onMoreOptions = {
                                        if (onTrackMoreOptions != null) {
                                            onTrackMoreOptions(track)
                                        } else if (onMoreOptions != null) {
                                            onMoreOptions()
                                        } else {
                                            internalSelectedTrackForOptions = track
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        if (upcomingEntries.isEmpty() && currentTrack != null) {
                            item(key = "empty_queue_info") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                        .padding(vertical = 32.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "No upcoming tracks lined up.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = upcomingEntries,
                                key = { _, entry -> "queue-entry-${entry.id}" },
                                contentType = { _, _ -> "queue_item" },
                            ) { index, entry ->
                                val hapticView = LocalView.current
                                ReorderableItem(
                                    state = reorderableState,
                                    key = "queue-entry-${entry.id}",
                                    modifier = if (reorderableState.isAnyItemDragging) Modifier else Modifier.animateItem(),
                                ) { isDragging ->
                                    UpNextTrackRow(
                                        track = entry.track,
                                        index = index,
                                        isDragging = isDragging,
                                        isAnyDragging = reorderableState.isAnyItemDragging,
                                        onClick = { onQueueEntryClick(entry.id) },
                                        onRemove = {
                                            upcomingEntries.removeAt(index)
                                            onRemoveUpNext(index)
                                        },
                                        onMoreOptions = {
                                            if (onTrackMoreOptions != null) {
                                                onTrackMoreOptions(entry.track)
                                            } else if (onMoreOptions != null) {
                                                onMoreOptions()
                                            } else {
                                                internalSelectedTrackForOptions = entry.track
                                            }
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

                    ExpressiveScrollBar(
                        listState = lazyListState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(
                                top = 16.dp,
                                end = 6.dp,
                                bottom = if (showFloatingControls) 100.dp + bottomInset else 16.dp + bottomInset,
                            ),
                    )
                }
            }
        }

        if (showFloatingControls) {
            val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp + bottomNavPadding)
                    .height(70.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.fillMaxHeight(),
                    shape = RoundedCornerShape(
                        topStart = 50.dp,
                        bottomStart = 50.dp,
                        topEnd = 8.dp,
                        bottomEnd = 8.dp,
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        FilledTonalIconButton(
                            onClick = onShuffle,
                            colors = if (isShuffle) {
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                painter = materialSymbolPainterResource("shuffle", slot = "playback.shuffle"),
                                contentDescription = "Toggle shuffle",
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        val isRepeatActive = repeatMode != RepeatMode.OFF
                        FilledTonalIconButton(
                            onClick = onRepeat,
                            colors = if (isRepeatActive) {
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            val repeatIcon = if (repeatMode == RepeatMode.ONE) {
                                materialSymbolPainterResource("repeat_one", slot = "playback.repeatOne", filled = true)
                            } else {
                                materialSymbolPainterResource("repeat", slot = "playback.repeat")
                            }
                            Icon(
                                painter = repeatIcon,
                                contentDescription = "Toggle repeat",
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        val isTimerActive = sleepTimerState.isTimerActive
                        FilledTonalIconButton(
                            onClick = { showTimerOptionsSheet = true },
                            enabled = true,
                            colors = if (isTimerActive) {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                painter = materialSymbolPainterResource("timer", filled = true),
                                contentDescription = stringResource(R.string.sleep_timer_ui_title),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clickable(
                            onClick = { isFabExpanded = !isFabExpanded },
                        ),
                    shape = RoundedCornerShape(
                        topStart = 8.dp,
                        bottomStart = 8.dp,
                        topEnd = 50.dp,
                        bottomEnd = 50.dp,
                    ),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = materialSymbolPainterResource("more_horiz"),
                            contentDescription = "Queue actions",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isFabExpanded,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(20f)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            isFabExpanded = false
                        },
                )
            }

            AnimatedVisibility(
                visible = isFabExpanded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(30f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .padding(bottom = 100.dp + bottomNavPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QueueToolbarMenuButton(
                            text = "Locate current song",
                            iconPainter = materialSymbolPainterResource("my_location"),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            enabled = true,
                            onClick = {
                                isFabExpanded = false
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(0)
                                }
                            },
                        )

                        QueueToolbarMenuButton(
                            text = "Clear queue",
                            iconPainter = materialSymbolPainterResource("clear_all"),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            enabled = upcomingEntries.isNotEmpty(),
                            onClick = {
                                isFabExpanded = false
                                showClearQueueDialog = true
                            },
                        )

                        QueueToolbarMenuButton(
                            text = "Save as playlist",
                            iconPainter = materialSymbolPainterResource("library_add"),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            enabled = false,
                            onClick = {},
                        )
                    }
                }
            }
        }

        if (showClearQueueDialog) {
            AlertDialog(
                onDismissRequest = { showClearQueueDialog = false },
                title = { Text("Clear queue?") },
                text = { Text("Are you sure you want to remove all upcoming tracks from the queue?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearUpcoming()
                            showClearQueueDialog = false
                        },
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearQueueDialog = false },
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showTimerOptionsSheet) {
            TimerOptionsBottomSheet(
                onPlayCounter = { count ->
                    sleepTimerManager.setPlayCounter(count)
                },
                activeTimerValueDisplay = sleepTimerState.activeTimerValueDisplay,
                activeTimerDurationMinutes = sleepTimerState.activeTimerDurationMinutes,
                playCount = sleepTimerState.playCount,
                isEndOfTrackTimerActive = sleepTimerState.isEndOfTrackTimerActive,
                onDismiss = { showTimerOptionsSheet = false },
                onCancelCountedPlay = {
                    sleepTimerManager.cancelCountedPlay()
                },
                onSetPredefinedTimer = { minutes ->
                    sleepTimerManager.setPredefinedTimer(minutes)
                },
                onSetEndOfTrackTimer = { enable ->
                    sleepTimerManager.setEndOfTrackTimer(enable)
                },
                onCancelTimer = {
                    sleepTimerManager.cancelTimer()
                },
            )
        }

        internalSelectedTrackForOptions?.let { optTrack ->
            HomeDailyMixSongOptionsSheet(
                track = optTrack,
                onDismiss = { internalSelectedTrackForOptions = null },
                onPlay = {
                    PlaybackManagerHolder.getInstanceOrNull()?.play(optTrack)
                    internalSelectedTrackForOptions = null
                },
                onPlayNext = {
                    PlaybackManagerHolder.getInstanceOrNull()?.playNext(optTrack)
                    internalSelectedTrackForOptions = null
                },
                onAddToQueue = {
                    PlaybackManagerHolder.getInstanceOrNull()?.addToQueue(optTrack)
                    internalSelectedTrackForOptions = null
                },
                onLoadTrackGenres = { emptyList() },
                onDownload = {
                    internalSelectedTrackForOptions = null
                },
            )
        }
    }
}

@Composable
private fun QueueTopHeader(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Next up",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    count <= 0 -> "No tracks lined up"
                    count == 1 -> "1 track lined up."
                    else -> "$count tracks lined up."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CurrentQueueTrackRow(
    track: HomeTrack,
    isPlaying: Boolean,
    onMoreOptions: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(60.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        color = colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHighest),
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
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.primary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            PlayingEqIcon(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(width = 18.dp, height = 16.dp),
                color = colorScheme.secondary,
                isPlaying = isPlaying,
            )

            Spacer(Modifier.width(4.dp))

            FilledIconButton(
                onClick = { onMoreOptions?.invoke() },
                enabled = onMoreOptions != null,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colorScheme.tertiaryContainer,
                    contentColor = colorScheme.onTertiaryContainer,
                ),
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = materialSymbolPainterResource("more_vert"),
                    contentDescription = "More options for ${track.title}",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun UpNextTrackRow(
    track: HomeTrack,
    index: Int,
    isDragging: Boolean,
    isAnyDragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoreOptions: (() -> Unit)?,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentOnRemove by rememberUpdatedState(onRemove)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnRemove()
                true
            } else {
                false
            }
        },
    )

    val itemShape = RoundedCornerShape(22.dp)
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "queueItemScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 4.dp else 1.dp,
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
            },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isAnyDragging,
        gesturesEnabled = !isAnyDragging,
        backgroundContent = {
            val isTargeted = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val backgroundColor by animateColorAsState(
                targetValue = if (isTargeted) {
                    colorScheme.errorContainer
                } else {
                    colorScheme.errorContainer.copy(alpha = 0.85f)
                },
                animationSpec = tween(durationMillis = 150),
                label = "dismissBgColor",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (isTargeted) 1.15f else 0.95f,
                animationSpec = tween(durationMillis = 120),
                label = "dismissIconScale",
            )
            val iconAlpha by animateFloatAsState(
                targetValue = if (isTargeted) 1.0f else 0.75f,
                animationSpec = tween(durationMillis = 120),
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
                    painter = materialSymbolPainterResource("close", slot = "action.close"),
                    contentDescription = "Remove from queue",
                    tint = colorScheme.onErrorContainer.copy(alpha = iconAlpha),
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
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(itemShape)
                .clickable(
                    enabled = dismissState.currentValue == SwipeToDismissBoxValue.Settled && !isAnyDragging,
                    onClick = onClick,
                ),
            shape = itemShape,
            color = colorScheme.surfaceContainerLowest,
            tonalElevation = elevation,
            shadowElevation = elevation,
        ) {
            Row(
                modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dragHandle()

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceContainerHighest),
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
                            tint = colorScheme.primary.copy(alpha = 0.75f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        TrackCodecBadges(
                            track = track,
                            height = 9.dp,
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                FilledIconButton(
                    onClick = { onMoreOptions?.invoke() },
                    enabled = onMoreOptions != null,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colorScheme.surfaceContainerHigh,
                        contentColor = colorScheme.onSurface,
                    ),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = materialSymbolPainterResource("more_vert"),
                        contentDescription = "More options for ${track.title}",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueToolbarMenuButton(
    text: String,
    iconPainter: Painter,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalView.current
    val actualContainerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.45f)
    val actualContentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)

    Surface(
        modifier = modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .heightIn(min = 48.dp)
            .wrapContentWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                ViewCompat.performHapticFeedback(
                    haptic,
                    HapticFeedbackConstantsCompat.GESTURE_START,
                )
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        color = actualContainerColor,
        tonalElevation = if (enabled) 8.dp else 2.dp,
        shadowElevation = if (enabled) 8.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = text,
                tint = actualContentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = actualContentColor,
            )
        }
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
