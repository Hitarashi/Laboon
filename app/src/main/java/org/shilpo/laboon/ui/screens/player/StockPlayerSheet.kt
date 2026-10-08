@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package org.shilpo.laboon.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
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
import org.shilpo.laboon.theme.LocalVisualTheme
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.painterResource
import org.shilpo.laboon.ui.design.MiniPlayerHeight
import org.shilpo.laboon.ui.design.PixelPlayerFacingCornerRadius
import org.shilpo.laboon.ui.design.PixelMiniPlayerHeight
import org.shilpo.laboon.ui.design.PixelPlayerOuterCornerRadius
import org.shilpo.laboon.ui.design.NavigationBarMaxWidth
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
    lyrics: List<LyricsLine>, lyricsLoading: Boolean,
    collapsedBottomChromeClearance: Dp,
    navigationBarHiddenProgress: Float,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf("player") }
    var queueVisible by rememberSaveable { mutableStateOf(false) }
    var audioVisible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val usesPixelPlayerChrome = LocalVisualTheme.current == null
    val miniPlayerHeight = if (usesPixelPlayerChrome) PixelMiniPlayerHeight else MiniPlayerHeight
    val expansionProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 255, easing = FastOutSlowInEasing),
        label = "playerSheetExpansion",
    )
    SideEffect { onExpansionChange?.invoke(expansionProgress) }
    BackHandler(enabled = expanded) {
        expanded = false
        panel = "player"
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val screenWidthPx = remember(configuration, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val offsetAnimatableX = remember { Animatable(0f) }
    val offsetAnimatableY = remember { Animatable(0f) }

    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = scope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = offsetAnimatableX,
        screenWidthPx = screenWidthPx,
        onDismissPlaylistAndShowUndo = onDismiss,
        onDismissStarted = {},
        onPrevious = onPrevious,
        onNext = onNext,
    )

    val miniVerticalGestureHandler = rememberMiniPlayerVerticalDragGestureHandler(
        scope = scope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = offsetAnimatableY,
        onExpand = { expanded = true },
        onDismiss = onDismiss,
    )

    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val collapsedBottomInset = navigationBarInset + collapsedBottomChromeClearance
        val sheetBottomInset = lerp(collapsedBottomInset, 0.dp, expansionProgress)
        val sheetHeight = lerp(miniPlayerHeight, maxHeight, expansionProgress)
        val horizontalGutter = lerp(if (usesPixelPlayerChrome) 22.dp else 16.dp, 0.dp, expansionProgress)
        val maxSheetWidth = lerp(NavigationBarMaxWidth, maxWidth, expansionProgress)
        val collapsedBottomRadius = if (usesPixelPlayerChrome) {
            lerp(
                PixelPlayerFacingCornerRadius,
                PixelPlayerOuterCornerRadius,
                navigationBarHiddenProgress.coerceIn(0f, 1f),
            )
        } else {
            28.dp
        }
        val topCornerRadius = lerp(PixelPlayerOuterCornerRadius, 0.dp, expansionProgress)
        val bottomCornerRadius = lerp(collapsedBottomRadius, 0.dp, expansionProgress)

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = sheetBottomInset)
                .padding(horizontal = horizontalGutter)
                .widthIn(max = maxSheetWidth)
                .fillMaxWidth()
                .height(sheetHeight)
                .graphicsLayer {
                    translationX = offsetAnimatableX.value
                    translationY = offsetAnimatableY.value * (1f - expansionProgress)
                }
                .miniPlayerDismissHorizontalGesture(
                    enabled = expansionProgress <= 0.01f,
                    handler = miniDismissGestureHandler,
                )
                .miniPlayerVerticalDragGesture(
                    enabled = expansionProgress <= 0.01f,
                    handler = miniVerticalGestureHandler,
                )
                .clickable(
                    enabled = expansionProgress <= 0.01f,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.player_open),
                    onClick = { expanded = true },
                ),
            shape = if (usesPixelPlayerChrome) {
                RoundedCornerShape(
                    topStart = topCornerRadius,
                    topEnd = topCornerRadius,
                    bottomStart = bottomCornerRadius,
                    bottomEnd = bottomCornerRadius,
                )
            } else {
                RoundedCornerShape(
                    topStart = topCornerRadius,
                    topEnd = topCornerRadius,
                    bottomStart = bottomCornerRadius,
                    bottomEnd = bottomCornerRadius,
                )
            },
            color = if (usesPixelPlayerChrome) {
                androidx.compose.ui.graphics.lerp(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    expansionProgress,
                )
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (expansionProgress > 0f) {
                    ExpandedPlayerContent(
                        track = track,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        progress = progress,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        audioQuality = audioQuality,
                        switchingQualityFormat = switchingQualityFormat,
                        onQualityVariantSelected = onQualityVariantSelected,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        canSkipPrevious = canSkipPrevious,
                        onPlayPause = onPlayPause,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onSeek = onSeek,
                        onShuffle = onShuffle,
                        onRepeat = onRepeat,
                        onCollapse = { expanded = false; panel = "player" },
                        onDismiss = { expanded = false; onDismiss() },
                        panel = panel,
                        onPanelChange = { panel = it },
                        queueVisible = { queueVisible = true },
                        audioVisible = { audioVisible = true },
                        onAlbum = onAlbum,
                        onArtist = onArtist,
                        lyrics = lyrics,
                        lyricsLoading = lyricsLoading,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = ((expansionProgress - 0.08f) / 0.42f).coerceIn(0f, 1f)
                            },
                    )
                }
                if (expansionProgress < 0.5f) {
                    Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(miniPlayerHeight)
                    .padding(
                        start = if (usesPixelPlayerChrome) 10.dp else 12.dp,
                        end = if (usesPixelPlayerChrome) 8.dp else 12.dp,
                    )
                    .graphicsLayer {
                        alpha = (1f - expansionProgress * 2f).coerceIn(0f, 1f)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (usesPixelPlayerChrome) 44.dp else 48.dp)
                        .clip(if (usesPixelPlayerChrome) CircleShape else MaterialTheme.shapes.medium),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = 12.dp,
                            end = if (usesPixelPlayerChrome) 0.dp else 12.dp,
                        ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val titleStyle = if (usesPixelPlayerChrome) {
                        MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                        )
                    } else {
                        MaterialTheme.typography.titleSmall
                    }
                    val artistStyle = if (usesPixelPlayerChrome) {
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            letterSpacing = 0.sp,
                        )
                    } else {
                        MaterialTheme.typography.bodySmall
                    }
                    Text(
                        text = track.title,
                        style = titleStyle,
                        color = if (usesPixelPlayerChrome) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
                    )
                    Text(
                        text = track.artist,
                        style = artistStyle,
                        color = if (usesPixelPlayerChrome) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
                    )
                }
                if (usesPixelPlayerChrome) {
                    val playButtonCorner by animateDpAsState(
                        targetValue = if (isPlaying) 10.dp else 18.dp,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "miniPlayerPlayButtonCorner",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(R.string.player_previous),
                                    enabled = canSkipPrevious,
                                    onClick = onPrevious,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSkipPrevious) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = materialSymbolPainterResource(
                                        name = "skip_previous",
                                        slot = "playback.previous",
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (canSkipPrevious) 1f else 0.38f,
                                    ),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(
                                        if (isPlaying) R.string.player_pause else R.string.player_play,
                                    ),
                                    enabled = durationMs > 0L,
                                    onClick = onPlayPause,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(playButtonCorner))
                                    .background(
                                        if (durationMs > 0L) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                            if (isBuffering) {
                                LoadingIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(
                                    painter = materialSymbolPainterResource(
                                    name = if (isPlaying) "pause" else "play_arrow",
                                    slot = if (isPlaying) "playback.pause" else "playback.play",
                                ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        }
                        val canSkipNext = queueState?.hasNext == true
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(R.string.player_next),
                                    enabled = canSkipNext,
                                    onClick = onNext,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSkipNext) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = materialSymbolPainterResource(
                                        name = "skip_next",
                                        slot = "playback.skip",
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (canSkipNext) 1f else 0.38f,
                                    ),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                } else {
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

@Composable
private fun ExpandedPlayerContent(
    track: HomeTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Float,
    currentPositionMs: Long,
    durationMs: Long,
    audioQuality: AudioQualityInfo?,
    switchingQualityFormat: String?,
    onQualityVariantSelected: ((TrackFormatVariant) -> Unit)?,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    canSkipPrevious: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
    panel: String,
    onPanelChange: (String) -> Unit,
    queueVisible: () -> Unit,
    audioVisible: () -> Unit,
    onAlbum: (suspend (HomeTrack) -> Boolean)?,
    onArtist: ((String) -> Unit)?,
    lyrics: List<LyricsLine>,
    lyricsLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val seekSliderState = rememberSliderState(
        value = progress.coerceIn(0f, 1f),
        trackRange = 0f..1f,
    )
    LaunchedEffect(progress) {
        seekSliderState.value = progress.coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    painter = materialSymbolPainterResource(
                        name = "keyboard_arrow_down",
                        slot = "player.collapse",
                    ),
                    contentDescription = "Collapse player",
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = queueVisible) {
                    Icon(
                        painter = materialSymbolPainterResource(
                            name = "queue_music",
                            slot = "player.queue",
                        ),
                        contentDescription = "Queue",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = audioVisible) {
                    Icon(
                        painter = materialSymbolPainterResource(
                            name = "graphic_eq",
                            slot = "player.audio_info",
                        ),
                        contentDescription = "Audio information",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
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
                    .height(520.dp),
            )
            TextButton(onClick = { onPanelChange("player") }) { Text("Back to player") }
        } else {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.extraLarge),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
            )
            TextButton(onClick = { onArtist?.invoke(track.artist) }) {
                Text(track.artist)
            }
            track.album?.let { album ->
                TextButton(onClick = {
                    scope.launch { onAlbum?.invoke(track) }
                }) { Text(album) }
            }
            if (isBuffering) LoadingIndicator()
            Slider(
                state = seekSliderState,
                onValueChange = { value ->
                    seekSliderState.value = value
                    onSeek(value)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = durationMs > 0L,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${currentPositionMs / 1000}s",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "${durationMs / 1000}s",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = canSkipPrevious) {
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
                    modifier = Modifier.size(64.dp),
                    enabled = durationMs > 0L,
                ) {
                    Icon(
                        painter = materialSymbolPainterResource(
                            name = if (isPlaying) "pause" else "play_arrow",
                            slot = if (isPlaying) "playback.pause" else "playback.play",
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        painter = materialSymbolPainterResource(
                            name = "skip_next",
                            slot = "playback.skip",
                        ),
                        contentDescription = "Next",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = isShuffle,
                    onClick = onShuffle,
                    label = { Text("Shuffle") },
                )
                FilterChip(
                    selected = repeatMode != RepeatMode.OFF,
                    onClick = onRepeat,
                    label = { Text("Repeat: ${repeatMode.name.lowercase()}") },
                )
                FilterChip(
                    selected = false,
                    onClick = { onPanelChange("lyrics") },
                    label = { Text("Lyrics") },
                )
                FilterChip(
                    selected = false,
                    onClick = queueVisible,
                    label = { Text("Queue") },
                )
                FilterChip(
                    selected = false,
                    onClick = audioVisible,
                    label = { Text("Audio") },
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(track.availableVariants, key = { it.format }) { variant ->
                    FilterChip(
                        selected = audioQuality?.codec.equals(variant.format, ignoreCase = true),
                        onClick = { onQualityVariantSelected?.invoke(variant) },
                        label = { Text(variant.format) },
                    )
                }
            }
            switchingQualityFormat?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDismiss) { Text("Dismiss player") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
