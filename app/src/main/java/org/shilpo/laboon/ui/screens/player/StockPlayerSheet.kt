@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package org.shilpo.laboon.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.playback.AudioQualityInfo
import org.shilpo.laboon.playback.DiscoveryStatus
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.painterResource
import org.shilpo.laboon.ui.screens.player.lyrics.LyricsScreen
import org.shilpo.laboon.ui.screens.queue.QueueBottomSheet

/** Built-in presentation uses Material components and their expressive motion scheme. */
@Composable
internal fun StockPlayerSheet(
    track: HomeTrack, isPlaying: Boolean, isBuffering: Boolean, progress: Float,
    currentPositionMs: Long, durationMs: Long, audioQuality: AudioQualityInfo?,
    switchingQualityFormat: String?, onQualityVariantSelected: ((TrackFormatVariant) -> Unit)?,
    isShuffle: Boolean, repeatMode: RepeatMode, canSkipPrevious: Boolean,
    onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Float) -> Unit,
    onShuffle: () -> Unit, onRepeat: () -> Unit, onDismiss: () -> Unit,
    onExpansionChange: ((Float) -> Unit)?, queueState: QueueState?,
    onRemove: ((Int) -> Unit)?, onMove: ((Int, Int) -> Unit)?, onTrack: ((HomeTrack) -> Unit)?,
    onQueueEntry: ((Long) -> Unit)?, onPromote: ((Long) -> Unit)?, onClear: (() -> Unit)?,
    onRetry: (() -> Unit)?, isDiscovering: Boolean, discoveryStatus: DiscoveryStatus,
    onAlbum: (suspend (HomeTrack) -> Boolean)?, onArtist: ((String) -> Unit)?,
    lyrics: List<LyricsLine>, lyricsLoading: Boolean, modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf("player") }
    var queueVisible by rememberSaveable { mutableStateOf(false) }
    var audioVisible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(expanded) { onExpansionChange?.invoke(if (expanded) 1f else 0f) }
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 84.dp)
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp),
                    enabled = durationMs > 0L,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp),
                    enabled = queueState?.hasNext == true,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip),
                        contentDescription = "Next",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false; panel = "player" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 760.dp)
                    .padding(horizontal = 20.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { expanded = false; panel = "player" }) { Text("Close") }
                    TextButton(onClick = {
                        panel = if (panel == "lyrics") "player" else "lyrics"
                    }) { Text("Lyrics") }
                    TextButton(onClick = { queueVisible = true }) { Text("Queue") }
                    TextButton(onClick = { audioVisible = true }) { Text("Audio") }
                }
                if (panel == "lyrics") {
                    LyricsScreen(
                        track = track,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        lyricsLines = lyrics,
                        lyricsLoading = lyricsLoading,
                        onSeek = onSeek,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                        )
                        Text(track.title, style = MaterialTheme.typography.headlineSmall)
                        TextButton(onClick = { onArtist?.invoke(track.artist) }) { Text(track.artist) }
                        track.album?.let { album ->
                            TextButton(onClick = {
                                scope.launch {
                                    onAlbum?.invoke(
                                        track
                                    )
                                }
                            }) { Text(album) }
                        }
                        if (isBuffering) LoadingIndicator()
                        val seekSliderState = rememberSliderState(
                            value = progress.coerceIn(0f, 1f),
                            trackRange = 0f..1f,
                        )
                        LaunchedEffect(progress) {
                            seekSliderState.value = progress.coerceIn(0f, 1f)
                        }
                        Slider(
                            state = seekSliderState,
                            onValueChange = { value ->
                                seekSliderState.value = value
                                onSeek(value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = durationMs > 0L,
                        )
                        Text(
                            "${currentPositionMs / 1000}s / ${durationMs / 1000}s",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = onPrevious,
                                enabled = canSkipPrevious,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painter = materialSymbolPainterResource(
                                        name = "skip_previous",
                                        slot = "playback.previous",
                                    ),
                                    contentDescription = "Previous",
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            FilledIconButton(
                                onClick = onPlayPause,
                                modifier = Modifier.size(56.dp),
                                enabled = durationMs > 0L,
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                                    ),
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            IconButton(
                                onClick = onNext,
                                enabled = queueState?.hasNext == true,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_skip),
                                    contentDescription = "Next",
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = isShuffle,
                                onClick = onShuffle,
                                label = { Text("Shuffle") })
                            FilterChip(
                                selected = repeatMode != RepeatMode.OFF,
                                onClick = onRepeat,
                                label = { Text("Repeat: ${repeatMode.name.lowercase()}") })
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(track.availableVariants, key = { it.format }) { variant ->
                                FilterChip(
                                    selected = audioQuality?.codec.equals(
                                        variant.format,
                                        ignoreCase = true
                                    ),
                                    onClick = { onQualityVariantSelected?.invoke(variant) },
                                    label = { Text(variant.format) })
                            }
                        }
                        switchingQualityFormat?.let {
                            Text(
                                "Switching to $it",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = {
                            expanded = false; onDismiss()
                        }) { Text("Dismiss player") }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
    if (queueVisible && queueState != null) QueueBottomSheet(
        queueState = queueState,
        onDismiss = { queueVisible = false },
        onTrackClick = { onTrack?.invoke(it) },
        onRemoveUpNext = { onRemove?.invoke(it) },
        onMoveUpNext = { from, to -> onMove?.invoke(from, to) },
        onClearUpNext = { onClear?.invoke() },
        onToggleShuffle = onShuffle,
        onCycleRepeatMode = onRepeat,
        onQueueEntryClick = { onQueueEntry?.invoke(it) },
        onPromoteAutoplay = { onPromote?.invoke(it) },
        onRetryDiscovery = { onRetry?.invoke() },
        isDiscovering = isDiscovering,
        discoveryStatus = discoveryStatus,
    )
    if (audioVisible) AudioInfoDialog(
        isOpen = true, onDismiss = { audioVisible = false },
        pipeline = audioQuality?.pipelineDetails, track = track, durationMs = durationMs
    )
}
