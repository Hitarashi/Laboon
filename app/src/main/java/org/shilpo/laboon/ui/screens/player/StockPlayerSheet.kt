@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.lyricsporn.LyricspornMotionArtwork
import org.shilpo.laboon.playback.AudioQualityInfo
import org.shilpo.laboon.playback.DiscoveryStatus
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.theme.LocalVisualTheme
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.theme.RoundedSans
import org.shilpo.laboon.ui.design.MiniPlayerHeight
import org.shilpo.laboon.ui.design.NavigationBarMaxWidth
import org.shilpo.laboon.ui.design.PixelMiniPlayerHeight
import org.shilpo.laboon.ui.design.PixelPlayerFacingCornerRadius
import org.shilpo.laboon.ui.design.PixelPlayerOuterCornerRadius
import org.shilpo.laboon.ui.screens.player.lyrics.LyricsScreen
import org.shilpo.laboon.ui.screens.queue.QueueBottomSheet

private const val MotionArtworkRequestProgress = 0.97f
private const val MotionArtworkDisplayProgress = 0.96f

/** One sheet owns both player states, so its content, bounds, and drag stay in sync. */
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
    motionArtwork: LyricspornMotionArtwork?, onRequestMotionArtwork: (() -> Unit)?,
    collapsedBottomChromeClearance: Dp,
    navigationBarHiddenProgress: Float,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf("player") }
    var queueVisible by rememberSaveable { mutableStateOf(false) }
    var audioVisible by rememberSaveable { mutableStateOf(false) }
    var moreOptionsVisible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val usesPixelPlayerChrome = LocalVisualTheme.current == null
    val miniPlayerHeight = if (usesPixelPlayerChrome) PixelMiniPlayerHeight else MiniPlayerHeight
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = remember(configuration, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val horizontalOffset = remember { Animatable(0f) }
    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = scope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = horizontalOffset,
        screenWidthPx = screenWidthPx,
        onDismissPlaylistAndShowUndo = onDismiss,
        onDismissStarted = {},
        onPrevious = onPrevious,
        onNext = onNext,
    )
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val defaultSheetAnimation = remember {
        tween<Float>(durationMillis = 255, easing = FastOutSlowInEasing)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val collapsedBottomInset = navigationBarInset + collapsedBottomChromeClearance
        val collapsedHorizontalGutter = if (usesPixelPlayerChrome) 22.dp else 16.dp
        val collapsedY = with(density) {
            (maxHeight - miniPlayerHeight - collapsedBottomInset).coerceAtLeast(0.dp).toPx()
        }
        val expansionFraction = remember(collapsedY) {
            Animatable(if (expanded) 1f else 0f)
        }
        val currentOnRequestMotionArtwork = rememberUpdatedState(onRequestMotionArtwork)
        LaunchedEffect(track.id, expansionFraction) {
            snapshotFlow { expansionFraction.value }
                .first { it >= MotionArtworkRequestProgress }
            currentOnRequestMotionArtwork.value?.invoke()
        }
        val sheetTranslationY = remember(collapsedY) {
            Animatable(if (expanded) 0f else collapsedY)
        }
        val motionController = remember(collapsedY, expansionFraction, sheetTranslationY) {
            PlayerSheetMotionController(
                translationY = sheetTranslationY,
                expansionFraction = expansionFraction,
                collapsedY = collapsedY,
            )
        }
        val overshootScaleY = remember { Animatable(1f) }
        val verticalDragHandler = remember(
            density,
            motionController,
            expansionFraction,
            sheetTranslationY,
            collapsedY,
            queueState != null,
        ) {
            PlayerSheetVerticalDragGestureHandler(
                scope = scope,
                densityProvider = { density },
                motionController = motionController,
                expansionFraction = expansionFraction,
                translationY = sheetTranslationY,
                expandedYProvider = { 0f },
                collapsedYProvider = { collapsedY },
                miniHeightPxProvider = { with(density) { miniPlayerHeight.toPx() } },
                queueGestureBottomExclusionPxProvider = {
                    with(density) { maxOf(20.dp, navigationBarInset + 8.dp).toPx() }
                },
                currentStateProvider = {
                    if (expanded) PlayerSheetTargetState.EXPANDED else PlayerSheetTargetState.COLLAPSED
                },
                onOpenQueueSheet = { if (queueState != null) queueVisible = true },
                onAnimateSheet = { targetExpanded, animationSpec, initialVelocity ->
                    motionController.animateTo(
                        targetExpanded = targetExpanded,
                        animationSpec = animationSpec ?: defaultSheetAnimation,
                        initialVelocity = initialVelocity,
                    )
                },
                onExpandSheetState = {
                    expanded = true
                    scope.launch { animateExpansionOvershoot(overshootScaleY) }
                },
                onCollapseSheetState = {
                    expanded = false
                    panel = "player"
                },
                onCollapseSquash = { startingScale ->
                    overshootScaleY.snapTo(startingScale)
                    overshootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessVeryLow,
                        ),
                    )
                },
            )
        }

        fun animatePlayerSheet(targetExpanded: Boolean) {
            expanded = targetExpanded
            if (!targetExpanded) panel = "player"
            scope.launch {
                coroutineScope {
                    launch {
                        motionController.animateTo(
                            targetExpanded = targetExpanded,
                            animationSpec = defaultSheetAnimation,
                        )
                    }
                    launch {
                        if (targetExpanded) {
                            animateExpansionOvershoot(overshootScaleY)
                        } else {
                            overshootScaleY.snapTo(0.96f)
                            overshootScaleY.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                                ),
                            )
                        }
                    }
                }
            }
        }

        val expansionProgress = expansionFraction.value
        SideEffect { onExpansionChange?.invoke(expansionProgress) }
        BackHandler(
            enabled = !moreOptionsVisible &&
                (expanded || expansionProgress > 0.01f || panel != "player"),
        ) {
            if (panel != "player") {
                panel = "player"
            } else {
                animatePlayerSheet(false)
            }
        }

        val sheetHeight = lerp(miniPlayerHeight, maxHeight, expansionProgress)
        val gestureHostHeight = maxHeight
        val horizontalGutter = lerp(collapsedHorizontalGutter, 0.dp, expansionProgress)
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
        // Pixel Player keeps a full-height input layer offset to the sheet's current top edge.
        // That leaves the Home feed above a collapsed mini-player outside the gesture hit region.
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, sheetTranslationY.value.roundToInt()) }
                    .fillMaxWidth()
                    .height(gestureHostHeight)
                    .miniPlayerDismissHorizontalGesture(
                        enabled = !expanded,
                        handler = miniDismissGestureHandler,
                    )
                    .playerSheetVerticalDragGesture(
                        enabled = true,
                        handler = verticalDragHandler,
                    ),
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = horizontalGutter)
                        .widthIn(max = maxSheetWidth)
                        .fillMaxWidth()
                        .height(sheetHeight)
                        .graphicsLayer {
                            translationX = horizontalOffset.value
                            scaleY = overshootScaleY.value
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .clickable(
                            enabled = !expanded,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.player_open),
                            onClick = { animatePlayerSheet(true) },
                        ),
                    shape = RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius,
                    ),
                    color = if (usesPixelPlayerChrome) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ExpandedPlayerContent(
                            track = track,
                            useRoundedTypography = usesPixelPlayerChrome,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
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
                            onCollapse = { animatePlayerSheet(false) },
                            onOpenMoreOptions = { moreOptionsVisible = true },
                            panel = panel,
                            onPanelChange = { panel = it },
                            queueVisible = { queueVisible = true },
                            audioVisible = { audioVisible = true },
                            onAlbum = onAlbum,
                            onArtist = onArtist,
                            lyrics = lyrics,
                            lyricsLoading = lyricsLoading,
                            motionArtwork = motionArtwork,
                            queueState = queueState,
                            onTrack = onTrack,
                            expansionProgress = expansionProgress,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val contentAlpha = ((expansionProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                    alpha = contentAlpha
                                    translationY = with(density) {
                                        lerp(24.dp, 0.dp, contentAlpha).toPx()
                                    }
                                }
                                .zIndex(if (expansionProgress >= 0.5f) 1f else 0f),
                        )

                        MiniPlayerContent(
                            track = track,
                            isPlaying = isPlaying,
                            durationMs = durationMs,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = queueState?.hasNext == true,
                            isInteractive = expansionProgress <= 0.01f,
                            isPixelChrome = usesPixelPlayerChrome,
                            onPlayPause = onPlayPause,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onOpen = { animatePlayerSheet(true) },
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
                                }
                                .zIndex(if (expansionProgress < 0.5f) 1f else 0f),
                        )
                }
            }
        }
    }
    }

    if (queueVisible && queueState != null) {
        QueueBottomSheet(
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
    }
    if (audioVisible) {
        AudioInfoDialog(
            isOpen = true,
            onDismiss = { audioVisible = false },
            pipeline = audioQuality?.pipelineDetails,
            track = track,
            durationMs = durationMs,
        )
    }
    if (moreOptionsVisible) {
        ModalBottomSheet(
            onDismissRequest = { moreOptionsVisible = false },
        ) {
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

private suspend fun animateExpansionOvershoot(scale: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>) {
    scale.snapTo(1f)
    scale.animateTo(
        targetValue = 1f,
        animationSpec = keyframes {
            durationMillis = 250
            1f at 0
            1.05f at 125
            1f at 250
        },
    )
}

@Composable
private fun MiniPlayerContent(
    track: HomeTrack,
    isPlaying: Boolean,
    durationMs: Long,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    isInteractive: Boolean,
    isPixelChrome: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(
            enabled = isInteractive,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClickLabel = stringResource(R.string.player_open),
            onClick = onOpen,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(if (isPixelChrome) 44.dp else 48.dp)
                .clip(if (isPixelChrome) CircleShape else MaterialTheme.shapes.medium),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = if (isPixelChrome) 0.dp else 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = if (isPixelChrome) {
                    MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                    )
                } else MaterialTheme.typography.titleSmall,
                color = if (isPixelChrome) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
            )
            Text(
                text = track.artist,
                style = if (isPixelChrome) {
                    MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, letterSpacing = 0.sp)
                } else MaterialTheme.typography.bodySmall,
                color = if (isPixelChrome) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
            )
        }
        if (isPixelChrome) {
            MiniPlayerSymbolButton(
                icon = "skip_previous",
                slot = "playback.previous",
                label = stringResource(R.string.player_previous),
                enabled = isInteractive && canSkipPrevious,
                isPrimary = false,
                onClick = onPrevious,
            )
            MiniPlayerSymbolButton(
                icon = if (isPlaying) "pause" else "play_arrow",
                slot = if (isPlaying) "playback.pause" else "playback.play",
                label = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                enabled = isInteractive && durationMs > 0L,
                isPrimary = true,
                onClick = onPlayPause,
            )
            MiniPlayerSymbolButton(
                icon = "skip_next",
                slot = "playback.skip",
                label = stringResource(R.string.player_next),
                enabled = isInteractive && canSkipNext,
                isPrimary = false,
                onClick = onNext,
            )
        } else {
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp), enabled = isInteractive && durationMs > 0L) {
                Icon(
                    painter = materialSymbolPainterResource(
                        if (isPlaying) "pause" else "play_arrow",
                        if (isPlaying) "playback.pause" else "playback.play",
                        filled = true,
                    ),
                    contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(48.dp), enabled = isInteractive && canSkipNext) {
                Icon(
                    painter = materialSymbolPainterResource(
                        "skip_next",
                        "playback.skip",
                        filled = true,
                    ),
                    contentDescription = stringResource(R.string.player_next),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerSymbolButton(
    icon: String,
    slot: String,
    label: String,
    enabled: Boolean,
    isPrimary: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(if (isPrimary) RoundedCornerShape(18.dp) else CircleShape)
                .background(
                    if (isPrimary) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.38f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = materialSymbolPainterResource(name = icon, slot = slot, filled = true),
                contentDescription = null,
                tint = if (isPrimary) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private data class PlayerCarouselItem(val entryId: Long?, val track: HomeTrack)

internal fun <T> playerCarouselItemAtOrNull(items: List<T>, index: Int): T? = items.getOrNull(index)

private fun buildPlayerCarouselItems(track: HomeTrack, queueState: QueueState?): List<PlayerCarouselItem> {
    val entries = queueState?.let { state ->
        val historyEnd = (state.historyCursor + 1).coerceIn(0, state.history.size)
        state.history.take(historyEnd) + state.playbackUpcomingEntries
    }.orEmpty()
    val items = entries.map { PlayerCarouselItem(it.id, it.track) }
    val currentEntryId = queueState?.currentEntry?.id
    return when {
        items.isEmpty() -> listOf(PlayerCarouselItem(currentEntryId, track))
        currentEntryId != null && items.none { it.entryId == currentEntryId } ->
            listOf(PlayerCarouselItem(currentEntryId, track)) + items
        currentEntryId == null && items.none { it.track.id == track.id } ->
            listOf(PlayerCarouselItem(null, track)) + items
        else -> items
    }
}

@Composable
private fun ExpandedPlayerContent(
    track: HomeTrack,
    useRoundedTypography: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
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
    onOpenMoreOptions: () -> Unit,
    panel: String,
    onPanelChange: (String) -> Unit,
    queueVisible: () -> Unit,
    audioVisible: () -> Unit,
    onAlbum: (suspend (HomeTrack) -> Boolean)?,
    onArtist: ((String) -> Unit)?,
    lyrics: List<LyricsLine>,
    lyricsLoading: Boolean,
    motionArtwork: LyricspornMotionArtwork?,
    queueState: QueueState?,
    onTrack: ((HomeTrack) -> Unit)?,
    expansionProgress: Float,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val contentModifier = if (isLandscape && panel != "lyrics") {
        modifier.statusBarsPadding().navigationBarsPadding()
    } else {
        modifier.navigationBarsPadding()
    }

    Column(modifier = contentModifier) {
        if (!isLandscape || panel == "lyrics") {
            FullPlayerToolbar(
                title = if (panel == "lyrics") "LYRICS" else "NOW PLAYING",
                useRoundedTypography = useRoundedTypography,
                expansionProgress = expansionProgress,
                isLyrics = panel == "lyrics",
                onCollapse = onCollapse,
                onBackToPlayer = { onPanelChange("player") },
                onOpenMoreOptions = onOpenMoreOptions,
            )
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
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        } else if (isLandscape) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FullPlayerAlbumCarousel(
                    track = track,
                    isPlaying = isPlaying,
                    queueState = queueState,
                    onTrack = onTrack,
                    onAlbum = onAlbum,
                    motionArtwork = motionArtwork,
                    expansionProgress = expansionProgress,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            alpha = fullPlayerSectionAlpha(expansionProgress, 0.08f)
                        },
                )
                Spacer(modifier = Modifier.width(9.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FullPlayerSongMetadata(
                        track = track,
                        useRoundedTypography = useRoundedTypography,
                        isPlaying = isPlaying,
                        onArtist = onArtist,
                        onLyrics = { onPanelChange("lyrics") },
                        onQueue = queueVisible,
                        modifier = Modifier.graphicsLayer {
                            alpha = fullPlayerSectionAlpha(expansionProgress, 0.20f)
                        },
                    )
                    PlayerSeekBar(
                        track = track,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        audioQuality = audioQuality,
                        switchingQualityFormat = switchingQualityFormat,
                        onQualityVariantSelected = onQualityVariantSelected,
                        onSeek = onSeek,
                        onAudioQualityClick = audioVisible,
                        isDark = androidx.compose.foundation.isSystemInDarkTheme(),
                        activeTrackColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        thumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        timeTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        timeTextFontFamily = if (useRoundedTypography) RoundedSans else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 70.dp)
                            .graphicsLayer {
                                alpha = fullPlayerSectionAlpha(expansionProgress, 0.08f)
                            },
                    )
                    FullPlayerPlaybackControls(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        canSkipPrevious = canSkipPrevious,
                        canSkipNext = queueState?.hasNext == true,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onShuffle = onShuffle,
                        onRepeat = onRepeat,
                        expansionProgress = expansionProgress,
                        modifier = Modifier.graphicsLayer {
                            alpha = fullPlayerSectionAlpha(expansionProgress, 0.42f)
                        },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                FullPlayerAlbumCarousel(
                    track = track,
                    isPlaying = isPlaying,
                    queueState = queueState,
                    onTrack = onTrack,
                    onAlbum = onAlbum,
                    motionArtwork = motionArtwork,
                    expansionProgress = expansionProgress,
                    modifier = Modifier.graphicsLayer {
                        alpha = fullPlayerSectionAlpha(expansionProgress, 0.08f)
                    },
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = fullPlayerSectionAlpha(expansionProgress, 0.20f)
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FullPlayerSongMetadata(
                        track = track,
                        useRoundedTypography = useRoundedTypography,
                        isPlaying = isPlaying,
                        onArtist = onArtist,
                        onLyrics = { onPanelChange("lyrics") },
                        onQueue = queueVisible,
                    )
                    PlayerSeekBar(
                        track = track,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        audioQuality = audioQuality,
                        switchingQualityFormat = switchingQualityFormat,
                        onQualityVariantSelected = onQualityVariantSelected,
                        onSeek = onSeek,
                        onAudioQualityClick = audioVisible,
                        isDark = androidx.compose.foundation.isSystemInDarkTheme(),
                        activeTrackColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        thumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        timeTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        timeTextFontFamily = if (useRoundedTypography) RoundedSans else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 70.dp)
                            .graphicsLayer {
                                alpha = fullPlayerSectionAlpha(expansionProgress, 0.08f)
                            },
                    )
                }

                FullPlayerPlaybackControls(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = queueState?.hasNext == true,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onShuffle = onShuffle,
                    onRepeat = onRepeat,
                    expansionProgress = expansionProgress,
                    modifier = Modifier.graphicsLayer {
                        alpha = fullPlayerSectionAlpha(expansionProgress, 0.42f)
                    },
                )
            }
        }
    }
}

private fun fullPlayerSectionAlpha(expansionProgress: Float, startThreshold: Float): Float =
    ((expansionProgress - startThreshold) / (1f - startThreshold).coerceAtLeast(0.001f))
        .coerceIn(0f, 1f)

@Composable
private fun FullPlayerPlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    expansionProgress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FullPlayerTransportControls(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        FullPlayerToggleRow(
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            onShuffle = onShuffle,
            onRepeat = onRepeat,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp, max = 86.dp)
                .padding(horizontal = 26.dp)
                .padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun FullPlayerToolbar(
    title: String,
    useRoundedTypography: Boolean,
    expansionProgress: Float,
    isLyrics: Boolean,
    onCollapse: () -> Unit,
    onBackToPlayer: () -> Unit,
    onOpenMoreOptions: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    androidx.compose.material3.TopAppBar(
        modifier = Modifier.graphicsLayer {
            alpha = expansionProgress.coerceIn(0f, 1f)
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = if (useRoundedTypography) RoundedSans else MaterialTheme.typography.labelLarge.fontFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                ),
                maxLines = 1,
            )
        },
        navigationIcon = {
            FilledTonalIconButton(
                onClick = if (isLyrics) onBackToPlayer else onCollapse,
                modifier = Modifier.padding(start = 4.dp).size(48.dp),
            ) {
                Icon(
                    painter = materialSymbolPainterResource(
                        if (isLyrics) "arrow_back" else "keyboard_arrow_down",
                    ),
                    contentDescription = if (isLyrics) "Back to player" else "Collapse player",
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        actions = {
            if (!isLyrics) {
                FilledTonalIconButton(
                    onClick = onOpenMoreOptions,
                    modifier = Modifier.padding(end = 14.dp).size(48.dp),
                ) {
                    Icon(
                        painter = materialSymbolPainterResource("more_vert"),
                        contentDescription = "More options",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            titleContentColor = colorScheme.onPrimaryContainer,
            navigationIconContentColor = colorScheme.primary,
            actionIconContentColor = colorScheme.primary,
        ),
    )
}

@Composable
private fun FullPlayerSongMetadata(
    track: HomeTrack,
    useRoundedTypography: Boolean,
    isPlaying: Boolean,
    onArtist: ((String) -> Unit)?,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = if (useRoundedTypography) RoundedSans else MaterialTheme.typography.headlineSmall.fontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onPrimaryContainer,
                ),
                maxLines = 1,
                modifier = Modifier.basicMarquee(
                    iterations = if (isPlaying) Int.MAX_VALUE else 0,
                ),
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = if (useRoundedTypography) RoundedSans else MaterialTheme.typography.titleMedium.fontFamily,
                    letterSpacing = 0.sp,
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                ),
                maxLines = 1,
                modifier = Modifier
                    .clickable(
                        enabled = onArtist != null,
                        role = Role.Button,
                        onClickLabel = "Open artist",
                    ) { onArtist?.invoke(track.artist) }
                    .basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
            )
        }

        val lyricsInteractionSource = remember { MutableInteractionSource() }
        val queueInteractionSource = remember { MutableInteractionSource() }
        ButtonGroup(
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
            },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val lyricsModifier = Modifier.animateWidth(lyricsInteractionSource)
            val queueModifier = Modifier.animateWidth(queueInteractionSource)
            customItem(
                buttonGroupContent = {
                    FilledTonalIconButton(
                        onClick = onLyrics,
                        modifier = lyricsModifier,
                        shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                        interactionSource = lyricsInteractionSource,
                    ) {
                        Icon(
                            painter = materialSymbolPainterResource("lyrics"),
                            contentDescription = "Lyrics",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text("Lyrics") },
                        leadingIcon = {
                            Icon(
                                painter = materialSymbolPainterResource("lyrics"),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onLyrics()
                            menuState.dismiss()
                        },
                    )
                },
            )
            customItem(
                buttonGroupContent = {
                    FilledTonalIconButton(
                        onClick = onQueue,
                        modifier = queueModifier,
                        shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                        interactionSource = queueInteractionSource,
                    ) {
                        Icon(
                            painter = materialSymbolPainterResource("queue_music"),
                            contentDescription = "Queue",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text("Queue") },
                        leadingIcon = {
                            Icon(
                                painter = materialSymbolPainterResource("queue_music"),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onQueue()
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun FullPlayerAlbumCarousel(
    track: HomeTrack,
    isPlaying: Boolean,
    queueState: QueueState?,
    onTrack: ((HomeTrack) -> Unit)?,
    onAlbum: (suspend (HomeTrack) -> Boolean)?,
    motionArtwork: LyricspornMotionArtwork?,
    expansionProgress: Float,
    modifier: Modifier = Modifier,
) {
    val items = remember(queueState, track.id) { buildPlayerCarouselItems(track, queueState) }
    val currentEntryId = queueState?.currentEntry?.id
    val currentPage = remember(items, currentEntryId, track.id) {
        items.indexOfFirst { currentEntryId != null && it.entryId == currentEntryId }
            .takeIf { it >= 0 }
            ?: items.indexOfLast { it.track.id == track.id }.takeIf { it >= 0 }
            ?: 0
    }
    val carouselState = rememberCarouselState(initialItem = currentPage, itemCount = { items.size })
    var programmaticScroll by remember(carouselState) { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val albumArtScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.95f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "fullPlayerAlbumArtScale",
    )
    LaunchedEffect(currentPage, items) {
        if (carouselState.currentItem != currentPage) {
            programmaticScroll = true
            try {
                carouselState.scrollToItem(currentPage)
            } finally {
                programmaticScroll = false
            }
        }
    }
    LaunchedEffect(carouselState, items, currentPage, currentEntryId, track.id) {
        var observedScroll = false
        snapshotFlow { carouselState.isScrollInProgress to carouselState.currentItem }
            .distinctUntilChanged()
            .collectLatest { (isScrolling, page) ->
                if (isScrolling) {
                    observedScroll = true
                } else if (observedScroll) {
                    observedScroll = false
                    val selected = items.getOrNull(page)
                    if (!programmaticScroll && page != currentPage && selected != null) {
                        hapticFeedback.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                        )
                        onTrack?.invoke(selected.track)
                    }
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        val itemWidth = maxWidth
        RoundedHorizontalMultiBrowseCarousel(
            state = carouselState,
            carouselWidth = itemWidth,
            itemSpacing = 8.dp,
            itemCornerRadius = 18.dp,
            carouselStyle = "no_peek",
            suppressNoPeekSettleCorrection = programmaticScroll,
            itemKey = { index -> playerCarouselItemAtOrNull(items, index)?.entryId ?: index },
            modifier = Modifier
                .fillMaxWidth()
                .height(itemWidth)
                .graphicsLayer {
                    scaleX = albumArtScale
                    scaleY = albumArtScale
                },
        ) { page ->
            playerCarouselItemAtOrNull(items, page)?.let { item ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(1f)
                        .clickable(
                            enabled = page == carouselState.currentItem && onAlbum != null,
                            role = Role.Button,
                            onClickLabel = "Open album",
                        ) { scope.launch { onAlbum?.invoke(item.track) } },
                ) {
                    AsyncImage(
                        model = item.track.artworkUrl,
                        contentDescription = item.track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (
                        motionArtwork != null &&
                        item.track.id == track.id &&
                        page == carouselState.currentItem &&
                        expansionProgress >= MotionArtworkDisplayProgress
                    ) {
                        MotionArtworkVideo(
                            artwork = motionArtwork,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = ((expansionProgress - MotionArtworkDisplayProgress) /
                                        (1f - MotionArtworkDisplayProgress))
                                        .coerceIn(0f, 1f)
                                },
                        )
                    }
                }
            }
        }
    }
}
