@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineStart
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
import org.shilpo.laboon.lyrics.LyricsTranslator
import org.shilpo.laboon.lyricsporn.LyricspornMotionArtwork
import org.shilpo.laboon.playback.AudioQualityInfo
import org.shilpo.laboon.playback.DiscoveryStatus
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.playback.SpectrumFrame
import org.shilpo.laboon.theme.LocalVisualTheme
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.screens.player.lyrics.LyricsDisplayOptions
import org.shilpo.laboon.ui.screens.player.lyrics.LyricsOptionsSheet
import org.shilpo.laboon.ui.screens.player.lyrics.LyricsScreen
import org.shilpo.laboon.ui.screens.player.lyrics.LyricsSyncControls
import org.shilpo.laboon.ui.screens.player.lyrics.usesSynchronizedLyrics
import org.shilpo.laboon.ui.screens.player.queue.QueueScreen
import org.shilpo.laboon.playback.PlaybackManagerHolder
import org.shilpo.laboon.ui.screens.home.HomeDailyMixSongOptionsSheet
import org.shilpo.laboon.ui.design.theme.RoundedSans
import org.shilpo.laboon.ui.design.MiniPlayerHeight
import org.shilpo.laboon.ui.design.NavigationBarMaxWidth
import org.shilpo.laboon.ui.design.PixelMiniPlayerHeight
import org.shilpo.laboon.ui.design.PixelPlayerFacingCornerRadius
import org.shilpo.laboon.ui.design.PixelPlayerOuterCornerRadius

private const val MotionArtworkRequestProgress = 0.97f
private const val MotionArtworkDisplayProgress = 0.96f

private data class PlayerMorphBounds(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
)

private fun lerpPlayerMorphBounds(
    start: PlayerMorphBounds,
    end: PlayerMorphBounds,
    fraction: Float,
) = PlayerMorphBounds(
    x = lerp(start.x, end.x, fraction),
    y = lerp(start.y, end.y, fraction),
    width = lerp(start.width, end.width, fraction),
    height = lerp(start.height, end.height, fraction),
)

@Composable
private fun Modifier.capturePlayerMorphBounds(
    playerContentCoordinates: LayoutCoordinates?,
    onBoundsChanged: (PlayerMorphBounds) -> Unit,
    visualOffsetY: Dp = 0.dp,
): Modifier {
    val density = LocalDensity.current
    return onGloballyPositioned { coordinates ->
        val rootCoordinates = playerContentCoordinates ?: return@onGloballyPositioned
        val position = rootCoordinates.localPositionOf(coordinates, Offset.Zero)
        val bounds = with(density) {
            PlayerMorphBounds(
                x = position.x.toDp(),
                y = position.y.toDp() - visualOffsetY,
                width = coordinates.size.width.toDp(),
                height = coordinates.size.height.toDp(),
            )
        }
        onBoundsChanged(bounds)
    }
}

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
    lyrics: List<LyricsLine>, lyricsLoading: Boolean, lyricsFailed: Boolean,
    onRetryLyrics: (() -> Unit)?,
    spectrum: SpectrumFrame,
    motionArtwork: LyricspornMotionArtwork?, onRequestMotionArtwork: (() -> Unit)?,
    collapsedBottomChromeClearance: Dp,
    navigationBarHiddenProgress: Float,
    onDownloadTrack: ((HomeTrack) -> Unit)? = null,
    onLoadTrackGenres: (suspend (HomeTrack) -> List<String>)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf("player") }
    var audioVisible by rememberSaveable { mutableStateOf(false) }
    var trackForOptions by remember { mutableStateOf<HomeTrack?>(null) }
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
        val panelTransitionProgress = remember {
            Animatable(if (panel == "player") 0f else 1f)
        }
        val panelTransitionAnimation = remember {
            tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
        }
        val currentPanelForGesture = rememberUpdatedState(panel)
        val currentExpandedForGesture = rememberUpdatedState(expanded)
        val currentOnRequestMotionArtwork = rememberUpdatedState(onRequestMotionArtwork)
        val currentOnDismiss = rememberUpdatedState(onDismiss)
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

        fun settlePanelTransition(open: Boolean) {
            scope.launch {
                panelTransitionProgress.animateTo(
                    targetValue = if (open) 1f else 0f,
                    animationSpec = panelTransitionAnimation,
                )
                if (!open) panel = "player"
            }
        }

        fun openPlayerPanel(targetPanel: String) {
            if (panel == "player") {
                panel = targetPanel
                scope.launch {
                    panelTransitionProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = panelTransitionAnimation,
                    )
                }
            } else {
                panel = targetPanel
            }
        }

        fun closePlayerPanel() {
            if (panel != "player") settlePanelTransition(open = false)
        }

        val verticalDragHandler = remember(
            density,
            motionController,
            expansionFraction,
            sheetTranslationY,
            collapsedY,
            panelTransitionProgress,
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
                    if (currentExpandedForGesture.value) {
                        PlayerSheetTargetState.EXPANDED
                    } else {
                        PlayerSheetTargetState.COLLAPSED
                    }
                },
                isQueueOrLyricsPanelOpenProvider = { currentPanelForGesture.value != "player" },
                onPanelDragStart = {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        panelTransitionProgress.stop()
                    }
                },
                onBeginQueuePanelDrag = { panel = "lyrics" },
                onPanelDragProgress = { progress ->
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        panelTransitionProgress.snapTo(progress)
                    }
                },
                onPanelDragSettle = ::settlePanelTransition,
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
                onDismissMiniPlayer = { currentOnDismiss.value() },
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
        val panelProgress = panelTransitionProgress.value.coerceIn(0f, 1f)
        val panelContentTranslationY = maxHeight * (1f - panelProgress)
        var playerContentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var miniMetadataBounds by remember(track.id) { mutableStateOf<PlayerMorphBounds?>(null) }
        var fullMetadataBounds by remember(track.id) { mutableStateOf<PlayerMorphBounds?>(null) }
        var settledFullMetadataBounds by remember(track.id) { mutableStateOf<PlayerMorphBounds?>(null) }
        var miniTransportBounds by remember(track.id) { mutableStateOf<PlayerMorphBounds?>(null) }
        var fullTransportBounds by remember(track.id) { mutableStateOf<PlayerMorphBounds?>(null) }
        var settledFullTransportBounds by remember(track.id) { mutableStateOf<PlayerMorphBounds?>(null) }
        var fullPlayerPanelGroupBounds by remember(track.id) { mutableStateOf<PlayerMorphBounds?>(null) }
        var panelHeaderGroupBounds by remember(track.id) { mutableStateOf<PlayerMorphBounds?>(null) }
        val hasPanelGroupMorphBounds = fullPlayerPanelGroupBounds != null && panelHeaderGroupBounds != null
        val isPanelGroupMorphing = panelProgress > 0f && panelProgress < 1f && hasPanelGroupMorphBounds
        val panelGroupMorphBounds = if (isPanelGroupMorphing) {
            lerpPlayerMorphBounds(
                start = requireNotNull(fullPlayerPanelGroupBounds).copy(height = 40.dp),
                end = requireNotNull(panelHeaderGroupBounds).copy(height = 40.dp),
                fraction = panelProgress,
            )
        } else {
            null
        }
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val miniArtworkSize = if (usesPixelPlayerChrome) 44.dp else 48.dp
        val miniArtworkX = if (usesPixelPlayerChrome) 10.dp else 12.dp
        val miniArtworkY = (miniPlayerHeight - miniArtworkSize) / 2
        val toolbarBottom = statusBarInset + 56.dp
        val queueY = maxHeight - navigationBarInset - 12.dp - 48.dp
        val fullTransportY = queueY - 14.dp - 86.dp
        val fullSeekY = fullTransportY - 36.dp - 84.dp
        val fullMetadataY = fullSeekY - 16.dp - 58.dp
        val fullArtAvailableHeight = if (isLandscape) {
            maxHeight - statusBarInset - navigationBarInset - 16.dp
        } else {
            (fullMetadataY - toolbarBottom - 12.dp).coerceAtLeast(160.dp)
        }
        val landscapeCarouselWidth = ((maxWidth - 48.dp - 9.dp) / 2).coerceAtLeast(0.dp)
        val fullArtworkSize = if (isLandscape) {
            minOf(landscapeCarouselWidth, fullArtAvailableHeight, 380.dp)
        } else {
            minOf((maxWidth - 48.dp).coerceAtLeast(0.dp), fullArtAvailableHeight, 380.dp)
        }
        val fullArtworkX = if (isLandscape) 24.dp else (maxWidth - fullArtworkSize) / 2
        val fullArtworkY = if (isLandscape) {
            statusBarInset + 8.dp
        } else {
            toolbarBottom + (fullArtAvailableHeight - fullArtworkSize) / 2
        }
        val artworkX = lerp(miniArtworkX, fullArtworkX, expansionProgress)
        val artworkY = lerp(miniArtworkY, fullArtworkY, expansionProgress)
        val artworkSize = lerp(miniArtworkSize, fullArtworkSize, expansionProgress)
        val artworkCornerRadius = lerp(miniArtworkSize / 2, 18.dp, expansionProgress)
        val artworkShape = RoundedCornerShape(artworkCornerRadius)
        val sharedArtworkAlpha = if (panel == "player") {
            1f - ((expansionProgress - 0.995f) / 0.005f).coerceIn(0f, 1f)
        } else {
            0f
        }
        val legacyControlsWidth = minOf((maxWidth - 48.dp).coerceAtLeast(0.dp), 440.dp)
        val legacyControlsX = (maxWidth - legacyControlsWidth) / 2
        val legacyMetadataBounds = if (isLandscape) {
            val rightColumnX = 24.dp + landscapeCarouselWidth + 9.dp
            val rightColumnWidth = landscapeCarouselWidth
            val interItemSpace = (
                maxHeight - statusBarInset - navigationBarInset - 316.dp
                ).coerceAtLeast(0.dp) / 4
            PlayerMorphBounds(
                x = rightColumnX,
                y = statusBarInset + interItemSpace,
                width = (rightColumnWidth - 116.dp).coerceAtLeast(0.dp),
                height = 58.dp,
            )
        } else {
            PlayerMorphBounds(
                x = legacyControlsX,
                y = fullMetadataY,
                width = legacyControlsWidth,
                height = 58.dp,
            )
        }
        val legacyTransportBounds = if (isLandscape) {
            val rightColumnX = 24.dp + landscapeCarouselWidth + 9.dp
            val interItemSpace = (
                maxHeight - statusBarInset - navigationBarInset - 316.dp
                ).coerceAtLeast(0.dp) / 4
            PlayerMorphBounds(
                x = rightColumnX + 12.dp,
                y = statusBarInset + interItemSpace + 70.dp + interItemSpace + 70.dp + interItemSpace + 8.dp,
                width = (landscapeCarouselWidth - 24.dp).coerceAtLeast(0.dp),
                height = 80.dp,
            )
        } else {
            PlayerMorphBounds(
                x = legacyControlsX + 12.dp,
                y = fullTransportY,
                width = (legacyControlsWidth - 24.dp).coerceAtLeast(0.dp),
                height = 80.dp,
            )
        }
        val endpointSettleProgress = ((expansionProgress - 0.97f) / 0.03f).coerceIn(0f, 1f)
        val fullMetadataTarget = settledFullMetadataBounds ?: fullMetadataBounds?.let {
            lerpPlayerMorphBounds(legacyMetadataBounds, it, endpointSettleProgress)
        } ?: legacyMetadataBounds
        val fullTransportTarget = settledFullTransportBounds ?: fullTransportBounds?.let {
            lerpPlayerMorphBounds(legacyTransportBounds, it, endpointSettleProgress)
        } ?: legacyTransportBounds
        val hasMorphBounds = miniMetadataBounds != null && miniTransportBounds != null
        val sharedPlayerElementsAlpha = if (
            usesPixelPlayerChrome && panel == "player" && hasMorphBounds
        ) {
            1f - ((expansionProgress - 0.97f) / 0.03f).coerceIn(0f, 1f)
        } else {
            0f
        }
        val metadataMorphBounds = miniMetadataBounds?.let { mini ->
            lerpPlayerMorphBounds(mini, fullMetadataTarget, expansionProgress)
        }
        val transportMorphBounds = miniTransportBounds?.let { mini ->
            lerpPlayerMorphBounds(mini, fullTransportTarget, expansionProgress)
        }
        val collapsedArtworkScale by animateFloatAsState(
            targetValue = if (isPlaying) 0.8f else 1f,
            animationSpec = tween(durationMillis = 450),
            label = "miniArtworkPlayingScale",
        )
        val artworkScale = collapsedArtworkScale + (1f - collapsedArtworkScale) * expansionProgress
        SideEffect { onExpansionChange?.invoke(expansionProgress) }
        BackHandler(
            enabled = trackForOptions == null &&
                (expanded || expansionProgress > 0.01f || panel != "player"),
        ) {
            if (panel != "player") {
                closePlayerPanel()
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                if (coordinates !== playerContentCoordinates) {
                                    playerContentCoordinates = coordinates
                                }
                            },
                    ) {
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
                            onOpenMoreOptions = { trackForOptions = track },
                            panel = "player",
                            onPanelChange = { targetPanel -> openPlayerPanel(targetPanel) },
                            audioVisible = { audioVisible = true },
                            panelControlAlpha = if (isPanelGroupMorphing) 0f else 1f,
                            panelTranslationY = 0.dp,
                            onPlayerControlBoundsChanged = { fullPlayerPanelGroupBounds = it },
                            onPanelControlBoundsChanged = {},
                            onAlbum = onAlbum,
                            onArtist = onArtist,
                            lyrics = lyrics,
                            lyricsLoading = lyricsLoading,
                            lyricsFailed = lyricsFailed,
                            onRetryLyrics = onRetryLyrics,
                            spectrum = spectrum,
                            motionArtwork = motionArtwork,
                            sharedArtworkAlpha = sharedArtworkAlpha,
                            sharedPlayerElementsAlpha = sharedPlayerElementsAlpha,
                            playerContentCoordinates = playerContentCoordinates,
                            onFullMetadataBoundsChanged = { bounds ->
                                if (expansionProgress >= 0.97f) fullMetadataBounds = bounds
                                if (expansionProgress >= 0.9995f) settledFullMetadataBounds = bounds
                            },
                            onFullTransportBoundsChanged = { bounds ->
                                if (expansionProgress >= 0.97f) fullTransportBounds = bounds
                                if (expansionProgress >= 0.9995f) settledFullTransportBounds = bounds
                            },
                            queueState = queueState,
                            onTrack = onTrack,
                            onQueueEntry = onQueueEntry,
                            onRemove = onRemove,
                            onMove = onMove,
                            onPromote = onPromote,
                            onClear = onClear,
                            onRetry = onRetry,
                            isDiscovering = isDiscovering,
                            discoveryStatus = discoveryStatus,
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

                        if (panel != "player" || panelProgress > 0f) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationY = (1f - panelProgress) * size.height
                                    }
                                    .zIndex(3f),
                                shape = RoundedCornerShape(
                                    topStart = lerp(28.dp, 0.dp, panelProgress),
                                    topEnd = lerp(28.dp, 0.dp, panelProgress),
                                ),
                                color = if (usesPixelPlayerChrome) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                shadowElevation = lerp(12.dp, 0.dp, panelProgress),
                            ) {
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
                                    onOpenMoreOptions = { trackForOptions = track },
                                    panel = panel,
                                    onPanelChange = { targetPanel ->
                                        if (targetPanel == "player") closePlayerPanel()
                                        else panel = targetPanel
                                    },
                                    audioVisible = { audioVisible = true },
                                    panelControlAlpha = if (isPanelGroupMorphing) 0f else 1f,
                                    panelTranslationY = panelContentTranslationY,
                                    onPlayerControlBoundsChanged = {},
                                    onPanelControlBoundsChanged = { panelHeaderGroupBounds = it },
                                    onAlbum = onAlbum,
                                    onArtist = onArtist,
                                    lyrics = lyrics,
                                    lyricsLoading = lyricsLoading,
                                    lyricsFailed = lyricsFailed,
                                    onRetryLyrics = onRetryLyrics,
                                    spectrum = spectrum,
                                    motionArtwork = motionArtwork,
                                    sharedArtworkAlpha = sharedArtworkAlpha,
                                    sharedPlayerElementsAlpha = sharedPlayerElementsAlpha,
                                    playerContentCoordinates = playerContentCoordinates,
                                    onFullMetadataBoundsChanged = {},
                                    onFullTransportBoundsChanged = {},
                                    queueState = queueState,
                                    onTrack = onTrack,
                                    onQueueEntry = onQueueEntry,
                                    onRemove = onRemove,
                                    onMove = onMove,
                                    onPromote = onPromote,
                                    onClear = onClear,
                                    onRetry = onRetry,
                                    isDiscovering = isDiscovering,
                                    discoveryStatus = discoveryStatus,
                                    expansionProgress = expansionProgress,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        panelGroupMorphBounds?.let { bounds ->
                            Box(
                                modifier = Modifier
                                    .offset(x = bounds.x, y = bounds.y)
                                    .width(bounds.width)
                                    .height(40.dp)
                                    .zIndex(4f),
                            ) {
                                LyricsQueueButtonGroup(
                                    selectedPanel = if (panelProgress > 0.5f) panel else null,
                                    onSelectLyrics = { panel = "lyrics" },
                                    onSelectQueue = { panel = "queue" },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                )
                            }
                        }

                        MiniPlayerContent(
                            track = track,
                            isPlaying = isPlaying,
                            durationMs = durationMs,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = queueState?.hasNext == true,
                            isInteractive = expansionProgress <= 0.01f,
                            isPixelChrome = usesPixelPlayerChrome,
                            sharedArtworkAlpha = sharedArtworkAlpha,
                            sharedPlayerElementsAlpha = sharedPlayerElementsAlpha,
                            playerContentCoordinates = playerContentCoordinates,
                            onMiniMetadataBoundsChanged = { miniMetadataBounds = it },
                            onMiniTransportBoundsChanged = { miniTransportBounds = it },
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

                        Box(
                            modifier = Modifier
                                .offset(x = artworkX, y = artworkY)
                                .size(artworkSize)
                                .graphicsLayer {
                                    alpha = sharedArtworkAlpha
                                    scaleX = artworkScale
                                    scaleY = artworkScale
                                }
                                .shadow(
                                    elevation = lerp(0.dp, 20.dp, expansionProgress),
                                    shape = artworkShape,
                                    clip = false,
                                )
                                .clip(artworkShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    shape = artworkShape,
                                )
                                .zIndex(2f),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(R.drawable.app_icon_small),
                                error = painterResource(R.drawable.app_icon_small),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        if (sharedPlayerElementsAlpha > 0f && metadataMorphBounds != null) {
                            MorphingPlayerMetadata(
                                track = track,
                                isPlaying = isPlaying,
                                useRoundedTypography = usesPixelPlayerChrome,
                                progress = expansionProgress,
                                bounds = metadataMorphBounds,
                                alpha = sharedPlayerElementsAlpha,
                            )
                        }
                        if (sharedPlayerElementsAlpha > 0f && transportMorphBounds != null) {
                            MorphingPlayerTransport(
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                canSkipPrevious = canSkipPrevious,
                                canSkipNext = queueState?.hasNext == true,
                                durationMs = durationMs,
                                progress = expansionProgress,
                                bounds = transportMorphBounds,
                                alpha = sharedPlayerElementsAlpha,
                                onPrevious = onPrevious,
                                onPlayPause = onPlayPause,
                                onNext = onNext,
                            )
                        }
                }
            }
        }
    }
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
    trackForOptions?.let { optTrack ->
        HomeDailyMixSongOptionsSheet(
            track = optTrack,
            onDismiss = { trackForOptions = null },
            onPlay = {
                if (onTrack != null) {
                    onTrack(optTrack)
                } else {
                    PlaybackManagerHolder.getInstanceOrNull()?.play(optTrack)
                }
                trackForOptions = null
            },
            onPlayNext = {
                PlaybackManagerHolder.getInstanceOrNull()?.playNext(optTrack)
                trackForOptions = null
            },
            onAddToQueue = {
                PlaybackManagerHolder.getInstanceOrNull()?.addToQueue(optTrack)
                trackForOptions = null
            },
            onLoadTrackGenres = { opt ->
                onLoadTrackGenres?.invoke(opt) ?: emptyList()
            },
            onDownload = {
                onDownloadTrack?.invoke(optTrack)
                trackForOptions = null
            },
        )
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
    sharedArtworkAlpha: Float,
    sharedPlayerElementsAlpha: Float,
    playerContentCoordinates: LayoutCoordinates?,
    onMiniMetadataBoundsChanged: (PlayerMorphBounds) -> Unit,
    onMiniTransportBoundsChanged: (PlayerMorphBounds) -> Unit,
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
                .graphicsLayer { alpha = 1f - sharedArtworkAlpha }
                .clip(if (isPixelChrome) CircleShape else MaterialTheme.shapes.medium),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = if (isPixelChrome) 0.dp else 12.dp)
                .graphicsLayer { alpha = 1f - sharedPlayerElementsAlpha }
                .capturePlayerMorphBounds(playerContentCoordinates, onMiniMetadataBoundsChanged),
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
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = 1f - sharedPlayerElementsAlpha }
                    .capturePlayerMorphBounds(playerContentCoordinates, onMiniTransportBoundsChanged),
            ) {
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
            }
        } else {
            Row(modifier = Modifier.graphicsLayer { alpha = 1f - sharedPlayerElementsAlpha }) {
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

@Composable
private fun MorphingPlayerMetadata(
    track: HomeTrack,
    isPlaying: Boolean,
    useRoundedTypography: Boolean,
    progress: Float,
    bounds: PlayerMorphBounds,
    alpha: Float,
) {
    val colorScheme = MaterialTheme.colorScheme
    val miniTitleStyle = MaterialTheme.typography.titleSmall.copy(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )
    val fullTitleStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = if (useRoundedTypography) RoundedSans else MaterialTheme.typography.headlineSmall.fontFamily,
        fontWeight = FontWeight.Bold,
    )
    val miniArtistStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, letterSpacing = 0.sp)
    val fullArtistStyle = MaterialTheme.typography.titleMedium.copy(
        fontFamily = if (useRoundedTypography) RoundedSans else MaterialTheme.typography.titleMedium.fontFamily,
        letterSpacing = 0.sp,
    )
    val titleColor = lerpColor(colorScheme.onPrimaryContainer, colorScheme.onPrimaryContainer, progress)
    val artistColor = lerpColor(
        colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
        colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        progress,
    )
    val titleFontSize = lerpTextUnit(miniTitleStyle.fontSize, fullTitleStyle.fontSize, progress)
    val titleLineHeight = lerpTextUnit(miniTitleStyle.lineHeight, fullTitleStyle.lineHeight, progress)
    val artistFontSize = lerpTextUnit(miniArtistStyle.fontSize, fullArtistStyle.fontSize, progress)
    val artistLineHeight = lerpTextUnit(miniArtistStyle.lineHeight, fullArtistStyle.lineHeight, progress)
    val titleLetterSpacing = lerpTextUnit(miniTitleStyle.letterSpacing, fullTitleStyle.letterSpacing, progress)
    val fontFamily = if (progress < 0.5f) miniTitleStyle.fontFamily else fullTitleStyle.fontFamily
    val artistFontFamily = if (progress < 0.5f) miniArtistStyle.fontFamily else fullArtistStyle.fontFamily

    Column(
        modifier = Modifier
            .offset(x = bounds.x, y = bounds.y)
            .size(width = bounds.width, height = bounds.height)
            .graphicsLayer { this.alpha = alpha }
            .zIndex(3f),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = track.title,
            style = miniTitleStyle.copy(
                fontFamily = fontFamily,
                fontSize = titleFontSize,
                lineHeight = titleLineHeight,
                letterSpacing = titleLetterSpacing,
                fontWeight = if (progress < 0.5f) FontWeight.SemiBold else FontWeight.Bold,
                color = titleColor,
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
        )
        Text(
            text = track.artist,
            style = miniArtistStyle.copy(
                fontFamily = artistFontFamily,
                fontSize = artistFontSize,
                lineHeight = artistLineHeight,
                color = artistColor,
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
        )
    }
}

@Composable
private fun MorphingPlayerTransport(
    isPlaying: Boolean,
    isBuffering: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    durationMs: Long,
    progress: Float,
    bounds: PlayerMorphBounds,
    alpha: Float,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val innerPadding = lerp(4.dp, 0.dp, progress)
    val previousLabel = stringResource(R.string.player_previous)
    val nextLabel = stringResource(R.string.player_next)
    val playLabel = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play)
    val skipIconSize = lerp(22.dp, 32.dp, progress)
    val playIconSize = lerp(22.dp, 36.dp, progress)
    val playCornerRadius = lerp(18.dp, if (isPlaying) 26.dp else 60.dp, progress)

    Row(
        modifier = Modifier
            .offset(x = bounds.x, y = bounds.y)
            .size(width = bounds.width, height = bounds.height)
            .graphicsLayer { this.alpha = alpha }
            .zIndex(4f),
        horizontalArrangement = Arrangement.spacedBy(lerp(0.dp, 6.dp, progress)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val previousContainer = lerpColor(
            colors.onPrimary.copy(alpha = if (canSkipPrevious) 1f else 0.38f),
            colors.primary,
            progress,
        )
        val previousContent = lerpColor(
            colors.primary.copy(alpha = if (canSkipPrevious) 1f else 0.38f),
            colors.onPrimary.copy(alpha = if (canSkipPrevious) 1f else 0.38f),
            progress,
        )
        MorphingTransportButton(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(innerPadding),
            icon = "skip_previous",
            slot = "playback.previous",
            label = previousLabel,
            enabled = canSkipPrevious,
            containerColor = previousContainer,
            contentColor = previousContent,
            shape = CircleShape,
            iconSize = skipIconSize,
            onClick = onPrevious,
        )

        MorphingTransportButton(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(innerPadding),
            icon = if (isPlaying) "pause" else "play_arrow",
            slot = if (isPlaying) "playback.pause" else "playback.play",
            label = playLabel,
            enabled = durationMs > 0L,
            containerColor = lerpColor(colors.primary, colors.tertiaryFixedDim, progress),
            contentColor = lerpColor(colors.onPrimary, colors.onTertiaryFixed, progress),
            shape = RoundedCornerShape(playCornerRadius),
            iconSize = playIconSize,
            isBuffering = isBuffering && progress >= 0.5f,
            onClick = onPlayPause,
        )

        val nextContainer = lerpColor(
            colors.onPrimary.copy(alpha = if (canSkipNext) 1f else 0.38f),
            colors.primary,
            progress,
        )
        val nextContent = lerpColor(
            colors.primary.copy(alpha = if (canSkipNext) 1f else 0.38f),
            colors.onPrimary.copy(alpha = if (canSkipNext) 1f else 0.38f),
            progress,
        )
        MorphingTransportButton(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(innerPadding),
            icon = "skip_next",
            slot = "playback.skip",
            label = nextLabel,
            enabled = canSkipNext,
            containerColor = nextContainer,
            contentColor = nextContent,
            shape = CircleShape,
            iconSize = skipIconSize,
            onClick = onNext,
        )
    }
}

@Composable
private fun MorphingTransportButton(
    modifier: Modifier,
    icon: String,
    slot: String,
    label: String,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    iconSize: Dp,
    isBuffering: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (isBuffering) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(38.dp),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.16f),
                )
            } else {
                Icon(
                    painter = materialSymbolPainterResource(name = icon, slot = slot, filled = true),
                    contentDescription = label,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

private fun lerpTextUnit(start: androidx.compose.ui.unit.TextUnit, end: androidx.compose.ui.unit.TextUnit, fraction: Float) =
    (start.value + (end.value - start.value) * fraction).sp

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
    audioVisible: () -> Unit,
    panelControlAlpha: Float,
    panelTranslationY: Dp,
    onPlayerControlBoundsChanged: (PlayerMorphBounds) -> Unit,
    onPanelControlBoundsChanged: (PlayerMorphBounds) -> Unit,
    onAlbum: (suspend (HomeTrack) -> Boolean)?,
    onArtist: ((String) -> Unit)?,
    lyrics: List<LyricsLine>,
    lyricsLoading: Boolean,
    lyricsFailed: Boolean,
    onRetryLyrics: (() -> Unit)?,
    spectrum: SpectrumFrame,
    motionArtwork: LyricspornMotionArtwork?,
    sharedArtworkAlpha: Float,
    sharedPlayerElementsAlpha: Float,
    playerContentCoordinates: LayoutCoordinates?,
    onFullMetadataBoundsChanged: (PlayerMorphBounds) -> Unit,
    onFullTransportBoundsChanged: (PlayerMorphBounds) -> Unit,
    queueState: QueueState?,
    onTrack: ((HomeTrack) -> Unit)?,
    onQueueEntry: ((Long) -> Unit)?,
    onRemove: ((Int) -> Unit)?,
    onMove: ((Int, Int) -> Unit)?,
    onPromote: ((Long) -> Unit)?,
    onClear: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    isDiscovering: Boolean,
    discoveryStatus: DiscoveryStatus,
    expansionProgress: Float,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isQueueOrLyricsPanel = panel == "lyrics" || panel == "queue"
    val artists = remember(track.artist) { track.artist.splitArtistsByDelimiters() }
    var showArtistPicker by rememberSaveable(track.id) { mutableStateOf(false) }
    val artistPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val onSongMetadataArtistClick: () -> Unit = {
        if (artists.size > 1) {
            showArtistPicker = true
        } else {
            onCollapse()
            onArtist?.invoke(artists.firstOrNull() ?: track.artist)
        }
    }
    val contentModifier = if (isLandscape) {
        modifier.statusBarsPadding().navigationBarsPadding()
    } else if (isQueueOrLyricsPanel) {
        modifier.statusBarsPadding()
    } else {
        modifier.navigationBarsPadding()
    }

    Column(modifier = contentModifier) {
        if (!isLandscape && !isQueueOrLyricsPanel) {
            FullPlayerToolbar(
                title = "NOW PLAYING",
                useRoundedTypography = useRoundedTypography,
                expansionProgress = expansionProgress,
                isLyrics = false,
                onCollapse = onCollapse,
                onBackToPlayer = { onPanelChange("player") },
                onOpenMoreOptions = onOpenMoreOptions,
            )
        }

        if (isQueueOrLyricsPanel) {
            QueueLyricsTabScreen(
                track = track,
                queueState = queueState,
                selectedPanel = panel,
                useRoundedTypography = useRoundedTypography,
                onPanelChange = onPanelChange,
                panelControlAlpha = panelControlAlpha,
                panelTranslationY = panelTranslationY,
                playerContentCoordinates = playerContentCoordinates,
                onPanelControlBoundsChanged = onPanelControlBoundsChanged,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                audioQuality = audioQuality,
                switchingQualityFormat = switchingQualityFormat,
                onQualityVariantSelected = onQualityVariantSelected,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                isShuffle = isShuffle,
                repeatMode = repeatMode,
                onShuffle = onShuffle,
                onRepeat = onRepeat,
                lyrics = lyrics,
                lyricsLoading = lyricsLoading,
                lyricsFailed = lyricsFailed,
                onRetryLyrics = onRetryLyrics,
                spectrum = spectrum,
                onQueueEntry = onQueueEntry,
                onRemove = onRemove,
                onMove = onMove,
                onPromote = onPromote,
                onClear = onClear,
                onRetry = onRetry,
                isDiscovering = isDiscovering,
                discoveryStatus = discoveryStatus,
                onOpenMoreOptions = onOpenMoreOptions,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                    sharedArtworkAlpha = sharedArtworkAlpha,
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
                        onArtistClick = onSongMetadataArtistClick,
                        onLyrics = { onPanelChange("lyrics") },
                        onQueue = { onPanelChange("queue") },
                        panelControlAlpha = panelControlAlpha,
                        onPanelControlBoundsChanged = onPlayerControlBoundsChanged,
                        sharedPlayerElementsAlpha = sharedPlayerElementsAlpha,
                        playerContentCoordinates = playerContentCoordinates,
                        onMorphTextBoundsChanged = onFullMetadataBoundsChanged,
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
                        sharedPlayerElementsAlpha = sharedPlayerElementsAlpha,
                        playerContentCoordinates = playerContentCoordinates,
                        onMorphTransportBoundsChanged = onFullTransportBoundsChanged,
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
                    sharedArtworkAlpha = sharedArtworkAlpha,
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
                        onArtistClick = onSongMetadataArtistClick,
                        onLyrics = { onPanelChange("lyrics") },
                        onQueue = { onPanelChange("queue") },
                        panelControlAlpha = panelControlAlpha,
                        onPanelControlBoundsChanged = onPlayerControlBoundsChanged,
                        sharedPlayerElementsAlpha = sharedPlayerElementsAlpha,
                        playerContentCoordinates = playerContentCoordinates,
                        onMorphTextBoundsChanged = onFullMetadataBoundsChanged,
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
                    sharedPlayerElementsAlpha = sharedPlayerElementsAlpha,
                    playerContentCoordinates = playerContentCoordinates,
                    onMorphTransportBoundsChanged = onFullTransportBoundsChanged,
                    modifier = Modifier.graphicsLayer {
                        alpha = fullPlayerSectionAlpha(expansionProgress, 0.42f)
                    },
                )
            }
        }
    }

    if (showArtistPicker && artists.isNotEmpty()) {
        PlayerArtistPickerBottomSheet(
            artists = artists,
            sheetState = artistPickerSheetState,
            useRoundedTypography = useRoundedTypography,
            onDismiss = { showArtistPicker = false },
            onArtistClick = { selectedArtist ->
                showArtistPicker = false
                onCollapse()
                onArtist?.invoke(selectedArtist)
            },
        )
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
    sharedPlayerElementsAlpha: Float,
    playerContentCoordinates: LayoutCoordinates?,
    onMorphTransportBoundsChanged: (PlayerMorphBounds) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            FullPlayerTransportControls(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - sharedPlayerElementsAlpha }
                    .capturePlayerMorphBounds(playerContentCoordinates, onMorphTransportBoundsChanged),
            )
        }
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
private fun QueueLyricsTabScreen(
    track: HomeTrack,
    queueState: QueueState?,
    selectedPanel: String,
    useRoundedTypography: Boolean,
    onPanelChange: (String) -> Unit,
    panelControlAlpha: Float,
    panelTranslationY: Dp,
    playerContentCoordinates: LayoutCoordinates?,
    onPanelControlBoundsChanged: (PlayerMorphBounds) -> Unit,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    audioQuality: AudioQualityInfo?,
    switchingQualityFormat: String?,
    onQualityVariantSelected: ((TrackFormatVariant) -> Unit)?,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    lyrics: List<LyricsLine>,
    lyricsLoading: Boolean,
    lyricsFailed: Boolean,
    onRetryLyrics: (() -> Unit)?,
    spectrum: SpectrumFrame,
    onQueueEntry: ((Long) -> Unit)?,
    onRemove: ((Int) -> Unit)?,
    onMove: ((Int, Int) -> Unit)?,
    onPromote: ((Long) -> Unit)?,
    onClear: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    isDiscovering: Boolean,
    discoveryStatus: DiscoveryStatus,
    onOpenMoreOptions: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isQueueSelected = selectedPanel == "queue"
    val queueCount = queueState?.playbackUpcomingEntries?.size ?: 0
    val scope = rememberCoroutineScope()
    val lyricsListState = rememberLazyListState()
    var showSyncedLyrics by rememberSaveable(track.id) { mutableStateOf(true) }
    var lyricsSyncOffsetMs by rememberSaveable(track.id) { mutableStateOf(0L) }
    var showRomanization by rememberSaveable(track.id) { mutableStateOf(true) }
    var showTranslation by rememberSaveable(track.id) { mutableStateOf(false) }
    var lyricsAlignment by rememberSaveable(track.id) { mutableStateOf("center") }
    var keepLyricsScreenOn by rememberSaveable(track.id) { mutableStateOf(true) }
    var showSyncControls by rememberSaveable(track.id) { mutableStateOf(false) }
    var translatedLines by remember(track.id, lyrics) { mutableStateOf<List<LyricsLine>?>(null) }
    var isTranslating by remember(track.id, lyrics) { mutableStateOf(false) }
    var showLyricsOptions by rememberSaveable(track.id) { mutableStateOf(false) }
    var showQueueOptions by rememberSaveable(track.id) { mutableStateOf(false) }
    var trackForOptions by remember { mutableStateOf<HomeTrack?>(null) }
    var showShareDialog by rememberSaveable(track.id) { mutableStateOf(false) }
    val lyricsOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasRomanizedLyrics = remember(lyrics) { lyrics.any { !it.romanization.isNullOrBlank() } }
    val lyricsHaveTiming = remember(lyrics) { usesSynchronizedLyrics(lyrics, showSyncedLyrics = true) }
    val onToggleTranslation: () -> Unit = {
        val next = !showTranslation
        showTranslation = next
        if (
            next && translatedLines == null &&
            lyrics.none { it.translations.isNotEmpty() } && !isTranslating
        ) {
            isTranslating = true
            scope.launch {
                try {
                    translatedLines = LyricsTranslator.translateLines(lyrics)
                } finally {
                    isTranslating = false
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isQueueSelected) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Next up",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = if (useRoundedTypography) RoundedSans
                            else MaterialTheme.typography.headlineMedium.fontFamily,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "$queueCount tracks lined up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(48.dp),
                    color = colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.app_icon_small),
                            error = painterResource(R.drawable.app_icon_small),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = if (useRoundedTypography) RoundedSans
                                    else MaterialTheme.typography.titleSmall.fontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.basicMarquee(
                                    iterations = if (isPlaying) Int.MAX_VALUE else 0,
                                ),
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.basicMarquee(
                                    iterations = if (isPlaying) Int.MAX_VALUE else 0,
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            LyricsQueueButtonGroup(
                selectedPanel = selectedPanel,
                onSelectLyrics = { onPanelChange("lyrics") },
                onSelectQueue = { onPanelChange("queue") },
                modifier = Modifier
                    .capturePlayerMorphBounds(
                        playerContentCoordinates = playerContentCoordinates,
                        onBoundsChanged = onPanelControlBoundsChanged,
                        visualOffsetY = panelTranslationY,
                    )
                    .graphicsLayer { alpha = panelControlAlpha },
            )
        }

        if (isQueueSelected) {
            QueueScreen(
                queueState = queueState ?: QueueState.withCurrent(track),
                onQueueEntryClick = onQueueEntry ?: {},
                onRemoveUpNext = onRemove ?: {},
                onMoveUpNext = onMove ?: { _, _ -> },
                onPromoteAutoplay = onPromote ?: {},
                onClearUpcoming = onClear ?: {},
                onRetryDiscovery = onRetry ?: {},
                isDiscovering = isDiscovering,
                discoveryStatus = discoveryStatus,
                queueFractionProvider = { panelControlAlpha },
                showHeader = false,
                currentTrack = queueState?.currentTrack ?: track,
                isShuffle = isShuffle,
                repeatMode = repeatMode,
                onShuffle = onShuffle,
                onRepeat = onRepeat,
                isPlaying = isPlaying,
                showFloatingControls = true,
                onMoreOptions = { showQueueOptions = true },
                onTrackMoreOptions = { optTrack -> trackForOptions = optTrack },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            LyricsScreen(
                track = track,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                lyricsLines = lyrics,
                lyricsLoading = lyricsLoading,
                lyricsFailed = lyricsFailed,
                onSeek = onSeek,
                lyricsFractionProvider = { panelControlAlpha },
                lazyListState = lyricsListState,
                isPlaying = isPlaying,
                showSyncedLyrics = showSyncedLyrics,
                displayOptions = LyricsDisplayOptions(
                    syncOffsetMs = lyricsSyncOffsetMs,
                    showRomanization = showRomanization,
                    showTranslation = showTranslation,
                    translatedLines = translatedLines,
                    showShareDialog = showShareDialog,
                    alignment = lyricsAlignment,
                    keepScreenOn = keepLyricsScreenOn,
                ),
                onDismissShareDialog = { showShareDialog = false },
                onRetryLyrics = onRetryLyrics,
                spectrum = spectrum,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
            AnimatedVisibility(
                visible = showSyncControls && showSyncedLyrics && lyricsHaveTiming,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                LyricsSyncControls(
                    offsetMs = lyricsSyncOffsetMs,
                    onOffsetChange = { lyricsSyncOffsetMs = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                )
            }
            LyricsPlaybackSeekRow(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            LyricsPanelFooter(
                hasTiming = lyricsHaveTiming,
                showSyncedLyrics = showSyncedLyrics,
                onShowSyncedLyricsChange = { showSyncedLyrics = it },
                onBackToPlayer = { onPanelChange("player") },
                onMore = { showLyricsOptions = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        }

        if (showLyricsOptions) {
            LyricsOptionsSheet(
                sheetState = lyricsOptionsSheetState,
                onDismissRequest = { showLyricsOptions = false },
                hasLyrics = lyrics.isNotEmpty(),
                hasTimedLyrics = lyricsHaveTiming,
                isSyncControlsVisible = showSyncControls,
                onToggleSyncControls = {
                    showLyricsOptions = false
                    showSyncControls = !showSyncControls
                },
                alignment = lyricsAlignment,
                onAlignmentChange = { lyricsAlignment = it },
                hasRomanization = hasRomanizedLyrics,
                showRomanization = showRomanization,
                onShowRomanizationChange = { showRomanization = it },
                hasTranslation = lyrics.isNotEmpty(),
                showTranslation = showTranslation,
                onShowTranslationChange = { checked ->
                    if (checked != showTranslation) onToggleTranslation()
                },
                keepScreenOn = keepLyricsScreenOn,
                onKeepScreenOnChange = { keepLyricsScreenOn = it },
                onShareLyrics = {
                    showLyricsOptions = false
                    showShareDialog = true
                },
                isShuffle = isShuffle,
                repeatMode = repeatMode,
                onShuffle = onShuffle,
                onRepeat = onRepeat,
            )
        }

        trackForOptions?.let { optTrack ->
            HomeDailyMixSongOptionsSheet(
                track = optTrack,
                onDismiss = { trackForOptions = null },
                onPlay = {
                    PlaybackManagerHolder.getInstanceOrNull()?.play(optTrack)
                    trackForOptions = null
                },
                onPlayNext = {
                    PlaybackManagerHolder.getInstanceOrNull()?.playNext(optTrack)
                    trackForOptions = null
                },
                onAddToQueue = {
                    PlaybackManagerHolder.getInstanceOrNull()?.addToQueue(optTrack)
                    trackForOptions = null
                },
                onDownload = {
                    trackForOptions = null
                },
            )
        }

        if (showQueueOptions) {
            ModalBottomSheet(onDismissRequest = { showQueueOptions = false }) {
                TextButton(
                    enabled = queueCount > 0 && onClear != null,
                    onClick = {
                        onClear?.invoke()
                        showQueueOptions = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Icon(
                        painter = materialSymbolPainterResource("clear_all"),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Clear queue")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LyricsPlaybackSeekRow(
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val safeDuration = durationMs.coerceAtLeast(0L)
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    val (smoothProgressFraction, _) = rememberSmoothProgress(
        isPlayingProvider = { isPlaying },
        currentPositionProvider = {
            if (isSeeking) (seekPosition * safeDuration).toLong() else currentPositionMs
        },
        totalDuration = safeDuration,
        isVisible = true,
    )
    val playPauseCornerRadius by animateDpAsState(
        targetValue = if (isPlaying) 18.dp else 50.dp,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
        label = "lyricsPlayPauseShape",
    )
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(78.dp),
            shape = RoundedCornerShape(playPauseCornerRadius),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.tertiaryFixedDim,
                contentColor = colors.onTertiaryFixed,
            ),
        ) {
            Crossfade(
                targetState = isBuffering,
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "lyricsPlayPauseBuffering",
            ) { buffering ->
                if (buffering) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        color = colors.onTertiaryFixed,
                        trackColor = colors.onTertiaryFixed.copy(alpha = 0.16f),
                    )
                } else {
                    AnimatedContent(
                        targetState = isPlaying,
                        label = "lyricsPlayPauseIcon",
                    ) { playing ->
                        Icon(
                            painter = materialSymbolPainterResource(
                                if (playing) "pause" else "play_arrow",
                                filled = true,
                            ),
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
            shape = CircleShape,
            color = colors.surfaceContainerLowest,
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                WavySliderExpressive(
                    value = { if (isSeeking) seekPosition else smoothProgressFraction.value },
                    onValueChange = { fraction ->
                        isSeeking = true
                        seekPosition = fraction
                    },
                    onValueCommit = { fraction ->
                        isSeeking = false
                        onSeek(fraction)
                    },
                    enabled = safeDuration > 0L,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.primary.copy(alpha = 0.2f),
                    thumbColor = colors.primary,
                    isPlaying = isPlaying,
                    wavelength = 30.dp,
                    semanticsLabel = "Playback position",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsPanelFooter(
    hasTiming: Boolean,
    showSyncedLyrics: Boolean,
    onShowSyncedLyricsChange: (Boolean) -> Unit,
    onBackToPlayer: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(
            onClick = onBackToPlayer,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.surfaceContainerLowest,
                contentColor = colors.onSurface,
            ),
        ) {
            Icon(
                painter = materialSymbolPainterResource("arrow_back"),
                contentDescription = "Back to player",
                modifier = Modifier.size(24.dp),
            )
        }
        ButtonGroup(
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            toggleableItem(
                checked = showSyncedLyrics && hasTiming,
                label = "Synced",
                onCheckedChange = { if (it) onShowSyncedLyricsChange(true) },
                weight = 1f,
                enabled = hasTiming,
            )
            toggleableItem(
                checked = !showSyncedLyrics || !hasTiming,
                label = "Static",
                onCheckedChange = { if (it) onShowSyncedLyricsChange(false) },
                weight = 1f,
            )
        }
        FilledTonalIconButton(
            onClick = onMore,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.surfaceContainerLowest,
                contentColor = colors.onSurface,
            ),
        ) {
            Icon(
                painter = materialSymbolPainterResource("more_vert"),
                contentDescription = "More lyrics options",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun LyricsQueueButtonGroup(
    selectedPanel: String?,
    onSelectLyrics: () -> Unit,
    onSelectQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val lyricsInteractionSource = remember { MutableInteractionSource() }
    val queueInteractionSource = remember { MutableInteractionSource() }
    ButtonGroup(
        modifier = modifier,
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        customItem(
            buttonGroupContent = {
                FilledTonalToggleButton(
                    checked = selectedPanel == "lyrics",
                    onCheckedChange = { checked -> if (checked) onSelectLyrics() },
                    modifier = Modifier.animateWidth(lyricsInteractionSource),
                    buttonSize = ToggleButtonSize.Small,
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    interactionSource = lyricsInteractionSource,
                    colors = FilledTonalToggleButtonDefaults.colors(
                        containerColor = colorScheme.surfaceContainerHigh,
                        contentColor = colorScheme.onSurfaceVariant,
                        checkedContainerColor = colorScheme.primary,
                        checkedContentColor = colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
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
                        onSelectLyrics()
                        menuState.dismiss()
                    },
                )
            },
        )
        customItem(
            buttonGroupContent = {
                FilledTonalToggleButton(
                    checked = selectedPanel == "queue",
                    onCheckedChange = { checked -> if (checked) onSelectQueue() },
                    modifier = Modifier.animateWidth(queueInteractionSource),
                    buttonSize = ToggleButtonSize.Small,
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    interactionSource = queueInteractionSource,
                    colors = FilledTonalToggleButtonDefaults.colors(
                        containerColor = colorScheme.surfaceContainerHigh,
                        contentColor = colorScheme.onSurfaceVariant,
                        checkedContainerColor = colorScheme.primary,
                        checkedContentColor = colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
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
                        onSelectQueue()
                        menuState.dismiss()
                    },
                )
            },
        )
    }
}

@Composable
private fun FullPlayerSongMetadata(
    track: HomeTrack,
    useRoundedTypography: Boolean,
    isPlaying: Boolean,
    onArtistClick: (() -> Unit)?,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    panelControlAlpha: Float,
    onPanelControlBoundsChanged: (PlayerMorphBounds) -> Unit,
    sharedPlayerElementsAlpha: Float,
    playerContentCoordinates: LayoutCoordinates?,
    onMorphTextBoundsChanged: (PlayerMorphBounds) -> Unit,
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
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = 1f - sharedPlayerElementsAlpha }
                .capturePlayerMorphBounds(playerContentCoordinates, onMorphTextBoundsChanged),
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
                        enabled = onArtistClick != null,
                        role = Role.Button,
                        onClickLabel = "Open artist",
                    ) { onArtistClick?.invoke() }
                    .basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
            )
        }

        LyricsQueueButtonGroup(
            selectedPanel = null,
            onSelectLyrics = onLyrics,
            onSelectQueue = onQueue,
            modifier = Modifier
                .capturePlayerMorphBounds(
                    playerContentCoordinates = playerContentCoordinates,
                    onBoundsChanged = onPanelControlBoundsChanged,
                )
                .graphicsLayer { alpha = panelControlAlpha },
        )
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
    sharedArtworkAlpha: Float,
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
                        .graphicsLayer {
                            alpha = if (page == carouselState.currentItem) {
                                1f - sharedArtworkAlpha
                            } else {
                                1f
                            }
                        }
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
