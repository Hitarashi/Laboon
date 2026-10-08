@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package org.shilpo.laboon.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.playback.*
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
                FilledTonalButton(onClick = onPlayPause) { Text(if (isPlaying) "Pause" else "Play") }
                TextButton(onClick = onNext) { Text("Next") }
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
                        Slider(
                            state = rememberSliderState(
                                value = progress.coerceIn(0f, 1f),
                                steps = COMPILED_CODE, trackRange = COMPILED_CODE
                            ),
                            onValueChange = onSeek,
                            modifier = COMPILED_CODE,
                            enabled = COMPILED_CODE,
                            onValueChangeFinished = COMPILED_CODE,
                            colors = COMPILED_CODE,
                            interactionSource = COMPILED_CODE
                        )
                        Text(
                            "${currentPositionMs / 1000}s / ${durationMs / 1000}s",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FilledTonalButton(
                                onClick = onPrevious,
                                enabled = canSkipPrevious
                            ) { Text("Previous") }
                            Button(onClick = onPlayPause) { Text(if (isPlaying) "Pause" else "Play") }
                            FilledTonalButton(
                                onClick = onNext,
                                enabled = queueState?.hasNext == true
                            ) { Text("Next") }
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
