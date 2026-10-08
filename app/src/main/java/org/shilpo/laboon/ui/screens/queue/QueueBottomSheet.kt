@file:OptIn(ExperimentalMaterial3Api::class)

package org.shilpo.laboon.ui.screens.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.DiscoveryStatus
import org.shilpo.laboon.playback.QueueOrigin
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.TrackCodecBadges
import org.shilpo.laboon.ui.design.painterResource

@Composable
fun QueueBottomSheet(
    queueState: QueueState,
    onDismiss: () -> Unit,
    onTrackClick: (HomeTrack) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onClearUpNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onQueueEntryClick: (Long) -> Unit = { entryId ->
        queueState.playbackUpcomingEntries.firstOrNull { it.id == entryId }
            ?.track?.let(onTrackClick)
    },
    onPromoteAutoplay: (Long) -> Unit = {},
    onRetryDiscovery: () -> Unit = {},
    isDiscovering: Boolean = false,
    discoveryStatus: DiscoveryStatus = DiscoveryStatus.IDLE,
    modifier: Modifier = Modifier,
) {
    val theme = org.shilpo.laboon.theme.LocalVisualTheme.current
    if (theme?.definition?.screens?.containsKey("queue") == true) {
        val actions = setOf(
            org.shilpo.laboon.theme.contract.VisualThemeAction.BACK,
            org.shilpo.laboon.theme.contract.VisualThemeAction.PLAY_QUEUE_ENTRY,
            org.shilpo.laboon.theme.contract.VisualThemeAction.REMOVE_QUEUE_ENTRY,
            org.shilpo.laboon.theme.contract.VisualThemeAction.MOVE_QUEUE_ENTRY,
            org.shilpo.laboon.theme.contract.VisualThemeAction.CLEAR_QUEUE,
            org.shilpo.laboon.theme.contract.VisualThemeAction.TOGGLE_SHUFFLE,
            org.shilpo.laboon.theme.contract.VisualThemeAction.CYCLE_REPEAT,
            org.shilpo.laboon.theme.contract.VisualThemeAction.PROMOTE_AUTOPLAY,
            org.shilpo.laboon.theme.contract.VisualThemeAction.RETRY_DISCOVERY
        )
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            org.shilpo.laboon.theme.ThemeRouteContent(
                theme = theme, screenName = "queue", modifier = modifier.fillMaxSize(),
                presentation = org.shilpo.laboon.theme.renderer.VisualThemePresentation(
                    values = mapOf(
                        "queue.count" to queueState.upNextCount.toString(),
                        "queue.shuffle" to queueState.isShuffle.toString(),
                        "queue.repeatMode" to queueState.repeatMode.name.lowercase(),
                        "playback.isDiscovering" to isDiscovering.toString()
                    ),
                    collections = mapOf("queue" to queueState.playbackUpcomingEntries.mapIndexed { index, entry ->
                        mapOf(
                            "queue.entryId" to entry.id.toString(),
                            "queue.index" to index.toString(),
                            "queue.origin" to entry.origin.name.lowercase(),
                            "track.id" to entry.track.id,
                            "track.title" to entry.track.title,
                            "track.artist" to entry.track.artist,
                            "track.artworkUrl" to entry.track.artworkUrl.orEmpty(),
                        )
                    }),
                ),
                availableActions = actions,
                onAction = { action, parameters ->
                    val index = parameters["queue.index"]?.toIntOrNull()
                        ?.takeIf { it in queueState.playbackUpcomingEntries.indices }
                    val entryId = parameters["queue.entryId"]?.toLongOrNull()
                        ?.takeIf { id -> queueState.playbackUpcomingEntries.any { it.id == id } }
                    when (action) {
                        org.shilpo.laboon.theme.contract.VisualThemeAction.BACK -> onDismiss()
                        org.shilpo.laboon.theme.contract.VisualThemeAction.PLAY_QUEUE_ENTRY -> entryId?.let(
                            onQueueEntryClick
                        )

                        org.shilpo.laboon.theme.contract.VisualThemeAction.REMOVE_QUEUE_ENTRY -> index?.let(
                            onRemoveUpNext
                        )

                        org.shilpo.laboon.theme.contract.VisualThemeAction.MOVE_QUEUE_ENTRY -> {
                            val to = when (parameters["queue.direction"]) {
                                "up" -> index?.minus(1); "down" -> index?.plus(1); else -> null
                            }
                            if (index != null && to != null && to in queueState.playbackUpcomingEntries.indices) onMoveUpNext(
                                index,
                                to
                            )
                        }

                        org.shilpo.laboon.theme.contract.VisualThemeAction.CLEAR_QUEUE -> onClearUpNext()
                        org.shilpo.laboon.theme.contract.VisualThemeAction.TOGGLE_SHUFFLE -> onToggleShuffle()
                        org.shilpo.laboon.theme.contract.VisualThemeAction.CYCLE_REPEAT -> onCycleRepeatMode()
                        org.shilpo.laboon.theme.contract.VisualThemeAction.PROMOTE_AUTOPLAY -> entryId?.let(
                            onPromoteAutoplay
                        )

                        org.shilpo.laboon.theme.contract.VisualThemeAction.RETRY_DISCOVERY -> onRetryDiscovery()
                        else -> Unit
                    }
                },
                fallback = {},
            )
        }
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val upcomingEntries = queueState.playbackUpcomingEntries
    val forwardHistoryCount = queueState.forwardHistory.size
    val queueGroups = buildList {
        if (forwardHistoryCount > 0) {
            add(
                "Previously played" to upcomingEntries.take(forwardHistoryCount)
                    .mapIndexed { index, entry -> index to entry })
        }
        val remaining = upcomingEntries.drop(forwardHistoryCount).mapIndexed { index, entry ->
            (index + forwardHistoryCount) to entry
        }
        listOf(
            QueueOrigin.MANUAL to "Your queue",
            QueueOrigin.CONTEXT to "Album/playlist",
            QueueOrigin.AUTOPLAY to "Autoplay",
        ).forEach { (origin, title) ->
            val entries = remaining.filter { it.second.origin == origin }
            if (entries.isNotEmpty()) add(title to entries)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Playing Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    "Autoplay on", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                QueueToggleIcon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = if (queueState.isShuffle) "Shuffle on" else "Shuffle off",
                    active = queueState.isShuffle,
                    onClick = onToggleShuffle,
                )
                QueueToggleIcon(
                    painter = if (queueState.repeatMode == RepeatMode.ONE) {
                        painterResource(R.drawable.ic_repeat_one)
                    } else {
                        painterResource(R.drawable.ic_repeat)
                    },
                    contentDescription = "Repeat ${queueState.repeatMode.name.lowercase()}",
                    active = queueState.repeatMode != RepeatMode.OFF,
                    onClick = onCycleRepeatMode,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Repeat: ${queueState.repeatMode.label()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                queueState.currentTrack?.let { current ->
                    item(key = "now_playing_section") {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        NowPlayingQueueCard(track = current)
                    }
                }

                item(key = "up_next_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Your queue (${queueState.upNextCount})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )

                        if (upcomingEntries.isNotEmpty()) {
                            TextButton(onClick = onClearUpNext) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                if (upcomingEntries.isEmpty()) {
                    item(key = "up_next_empty") {
                        if (isDiscovering) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .semantics {
                                            contentDescription = "Finding playable tracks"
                                        },
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Finding playable tracks...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                text = "No songs queued. Use 'Play Next' or 'Add to Queue' on any track.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                } else {
                    queueGroups.forEach { (title, entries) ->
                        item(key = "queue_group_$title") {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        itemsIndexed(
                            items = entries,
                            key = { _, row -> "upnext_${row.second.id}" },
                        ) { groupIndex, (index, entry) ->
                            val isPlayedHistory = title == "Previously played"
                            QueueTrackRow(
                                track = entry.track,
                                onClick = { onQueueEntryClick(entry.id) },
                                onMoveUp = if (!isPlayedHistory && groupIndex > 0) {
                                    { onMoveUpNext(index, index - 1) }
                                } else null,
                                onMoveDown = if (!isPlayedHistory && groupIndex < entries.lastIndex) {
                                    { onMoveUpNext(index, index + 1) }
                                } else null,
                                onRemove = { onRemoveUpNext(index) },
                                onPromote = if (entry.origin == QueueOrigin.AUTOPLAY) {
                                    { onPromoteAutoplay(entry.id) }
                                } else null,
                            )
                        }
                    }
                }
                if (isDiscovering || discoveryStatus == DiscoveryStatus.FAILED ||
                    discoveryStatus == DiscoveryStatus.EXHAUSTED
                ) {
                    item(key = "discovery_state") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDiscovering) CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = when {
                                    isDiscovering -> "Finding related songs…"
                                    discoveryStatus == DiscoveryStatus.FAILED -> "Song discovery failed"
                                    else -> "No related songs found"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (!isDiscovering) TextButton(onClick = onRetryDiscovery) { Text("Retry") }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun RepeatMode.label(): String = when (this) {
    RepeatMode.OFF -> "Off"
    RepeatMode.ALL -> "All"
    RepeatMode.ONE -> "Track"
}

@Composable
private fun QueueToggleIcon(
    painter: Painter,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NowPlayingQueueCard(track: HomeTrack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                    modifier = Modifier.size(52.dp),
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TrackCodecBadges(
                    track = track,
                    height = 9.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: HomeTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onPromote: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                    modifier = Modifier.size(42.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.app_icon_small),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TrackCodecBadges(
                    track = track,
                    height = 8.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }

        if (onMoveUp != null || onMoveDown != null || onRemove != null || onPromote != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painter = materialSymbolPainterResource(
                                name = "keyboard_arrow_up",
                                slot = "queue.moveEarlier",
                            ),
                            contentDescription = "Move earlier",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painter = materialSymbolPainterResource(
                                name = "keyboard_arrow_down",
                                slot = "queue.moveLater",
                            ),
                            contentDescription = "Move later",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clear),
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onPromote != null) {
                    TextButton(onClick = onPromote) { Text("Keep") }
                }
            }
        }
    }
}
