@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.player

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.lyricsporn.LyricspornMotionArtwork
import org.shilpo.laboon.playback.ArtworkUrlHelper
import org.shilpo.laboon.playback.AudioQualityInfo
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.playback.SpectrumFrame
import org.shilpo.laboon.ui.design.LiquidGlassBackdropState
import org.shilpo.laboon.ui.design.LiquidGlassSurface
import org.shilpo.laboon.ui.design.MiniPlayerHeight
import org.shilpo.laboon.ui.design.MiniPlayerSpacing
import org.shilpo.laboon.ui.design.NavigationBarBottomPadding
import org.shilpo.laboon.ui.design.NavigationBarHeight
import org.shilpo.laboon.ui.design.NavigationBarMaxWidth
import org.shilpo.laboon.ui.design.liquidGlassBackdropProducer
import org.shilpo.laboon.ui.design.rememberLiquidGlassBackdropState
import kotlin.math.abs
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp as lerpFloat

private const val SettleDurationMs = 400
private const val MotionArtworkRequestProgress = 0.97f
private const val MotionArtworkDisplayProgress = 0.96f
private val SettleEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val CookieMorph = Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided)

private data class MorphingPlayerCookieShape(
    val morphProgress: Float,
    val rotationAngle: Float = 0f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = CookieMorph.toPath(morphProgress.coerceIn(0f, 1f)).asComposePath()
        val matrix = Matrix()
        val bounds = CookieMorph.calculateBounds()
        val boundsWidth = bounds[2] - bounds[0]
        val boundsHeight = bounds[3] - bounds[1]

        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)

        if (rotationAngle != 0f) {
            val rotMatrix = Matrix()
            rotMatrix.resetToPivotedTransform(
                pivotX = size.width / 2f,
                pivotY = size.height / 2f,
                rotationZ = rotationAngle,
            )
            path.transform(rotMatrix)
        }

        return Outline.Generic(path)
    }
}

@Composable
fun MorphingPlayerSheet(
    track: HomeTrack,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    playbackProgress: Float = 0f,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    audioQuality: AudioQualityInfo? = null,
    switchingQualityFormat: String? = null,
    onQualityVariantSelected: ((TrackFormatVariant) -> Unit)? = null,
    isShuffle: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    spectrum: SpectrumFrame = SpectrumFrame(),
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backdropState: LiquidGlassBackdropState? = null,
    albumDockProgress: Float = 0f,
    onMoreClick: () -> Unit = {},
    onExpansionProgressChange: ((Float) -> Unit)? = null,
    queueState: QueueState? = null,
    onRemoveUpNext: ((Int) -> Unit)? = null,
    onMoveUpNext: ((Int, Int) -> Unit)? = null,
    onTrackClick: ((HomeTrack) -> Unit)? = null,
    onOpenAlbum: (suspend (HomeTrack) -> Boolean)? = null,
    lyricsLines: List<LyricsLine> = emptyList(),
    lyricsLoading: Boolean = false,
    motionArtwork: LyricspornMotionArtwork? = null,
    onRequestMotionArtwork: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val isDark = isSystemInDarkTheme()

    val progressAnimatable = remember { Animatable(0f) }
    val progress = progressAnimatable.value.coerceIn(0f, 1f)
    val currentOnRequestMotionArtwork = rememberUpdatedState(onRequestMotionArtwork)
    LaunchedEffect(track.id) {
        snapshotFlow { progressAnimatable.value }
            .first { it >= MotionArtworkRequestProgress }
        currentOnRequestMotionArtwork.value?.invoke()
    }
    var activePanel by remember { mutableStateOf<PlayerPanelTab?>(null) }
    val activePanelProvider = rememberUpdatedState(activePanel)
    val panelFraction = remember { Animatable(0f) }
    var isPanelClosing by remember { mutableStateOf(false) }
    val panelSpringSpec = remember {
        spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    }
    val openPanel: (PlayerPanelTab) -> Unit = { panel ->
        isPanelClosing = false
        activePanel = panel
        coroutineScope.launch { panelFraction.animateTo(1f, panelSpringSpec) }
    }
    val closePanel: () -> Unit = {
        isPanelClosing = true
        coroutineScope.launch {
            panelFraction.animateTo(0f, panelSpringSpec)
            activePanel = null
            isPanelClosing = false
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { progressAnimatable.value.coerceIn(0f, 1f) }
            .collect { p ->
                onExpansionProgressChange?.invoke(p)
                if (p == 0f) {
                    if (panelFraction.value > 0f) panelFraction.snapTo(0f)
                    activePanel = null
                }
            }
    }

    val settleSpec = remember(SettleDurationMs) {
        tween<Float>(
            durationMillis = SettleDurationMs,
            easing = SettleEasing,
        )
    }

    val settleSpringSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    }

    BackHandler(enabled = progress >= 0.5f && activePanel == null) {
        coroutineScope.launch {
            progressAnimatable.animateTo(0f, settleSpec)
        }
    }

    val dismissOffsetYAnimatable = remember { Animatable(0f) }
    val dismissAlphaAnimatable = remember { Animatable(1f) }
    val miniSwipeOffsetX = remember { Animatable(0f) }
    var showAudioInfo by remember { mutableStateOf(false) }
    var audioBadgeBounds by remember { mutableStateOf<Rect?>(null) }
    var audioDialogProgress by remember { mutableFloatStateOf(0f) }
    val playerBackdropState = rememberLiquidGlassBackdropState()
    val playerBackdropLayer = rememberGraphicsLayer()

    val rotationAnimatable = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = rotationAnimatable.value % 360f
                rotationAnimatable.snapTo(current)
                rotationAnimatable.animateTo(
                    targetValue = current + 360f,
                    animationSpec = tween(
                        durationMillis = 45000,
                        easing = LinearEasing,
                    ),
                )
            }
        }
    }

    val cookieMorphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "morphingCookieMorphProgress",
    )

    val textMeasurer = rememberTextMeasurer()
    val miniTitleStyle = MaterialTheme.typography.titleSmall
    val miniArtistStyle = MaterialTheme.typography.bodySmall
    val hasMarqueeOverflow = remember(track.title, track.artist, density) {
        val titleWidth = textMeasurer.measure(track.title, miniTitleStyle).size.width
        val artistWidth = textMeasurer.measure(track.artist, miniArtistStyle).size.width
        val maxAvailableWidthPx =
            with(density) { (NavigationBarMaxWidth - 24.dp - 60.dp - 140.dp).toPx() }
        titleWidth > maxAvailableWidthPx || artistWidth > maxAvailableWidthPx
    }

    val leftFadeAlpha = remember { Animatable(0f) }
    LaunchedEffect(track, isPlaying, hasMarqueeOverflow) {
        if (isPlaying && hasMarqueeOverflow) {
            leftFadeAlpha.snapTo(0f)
            delay(1200)
            leftFadeAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400),
            )
        } else {
            leftFadeAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200),
            )
        }
    }

    val coverOffsetX = remember { Animatable(0f) }
    var coverAccumulatedDragX by remember { mutableFloatStateOf(0f) }
    val coverSnapThreshold = with(density) { 72.dp.toPx() }
    val maxCoverTension = with(density) { 56.dp.toPx() }

    LaunchedEffect(track.id) {
        coverOffsetX.snapTo(0f)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        val statusBarTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        val navBarBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }

        val pillWidth = minOf(screenWidth - 32.dp, NavigationBarMaxWidth)
        val pillHeight = MiniPlayerHeight
        val pillX = (screenWidth - pillWidth) / 2
        val floatingNavBarClearance = NavigationBarHeight * (1f - albumDockProgress)
        val pillBottomOffset =
            navBarBottom + NavigationBarBottomPadding + MiniPlayerSpacing + floatingNavBarClearance
        val pillY = screenHeight - pillBottomOffset - pillHeight

        val sheetWidth = lerp(pillWidth, screenWidth, progress)
        val sheetHeight = lerp(pillHeight, screenHeight, progress)
        val sheetX = lerp(pillX, 0.dp, progress)
        val sheetY = lerp(pillY, 0.dp, progress)
        val sheetTopRadius = lerp(28.dp, 0.dp, progress)
        val sheetBottomRadius = lerp(lerp(12.dp, 28.dp, albumDockProgress), 0.dp, progress)
        val sheetShape = RoundedCornerShape(
            topStart = sheetTopRadius,
            topEnd = sheetTopRadius,
            bottomStart = sheetBottomRadius,
            bottomEnd = sheetBottomRadius,
        )

        val fullAlpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
        val miniAlpha = ((0.4f - progress) / 0.4f).coerceIn(0f, 1f)
        val fullControlsProgress = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
        val fullControlsScale = lerpFloat(0.40f, 1.0f, fullControlsProgress)
        val fullControlsAlpha = fullControlsProgress
        val fullControlsCounterY = with(density) { -sheetY.toPx() }
        val controllerIconTint = lerpColor(
            if (isDark) Color.White else Color.Black.copy(alpha = 0.85f),
            if (isDark) Color.White else Color(0xFF191C1E),
            progress,
        )
        val inactiveControllerIconTint = controllerIconTint.copy(
            alpha = controllerIconTint.alpha * 0.72f,
        )
        val playIconTint = if (isDark) Color.Black.copy(alpha = 0.85f) else Color.White

        val effectiveFullControlsAlpha = fullControlsAlpha
        val effectiveFullControlsCounterY = fullControlsCounterY

        val toolbarHeight = 56.dp
        val toolbarTop = statusBarTop
        val toolbarBottom = toolbarTop + toolbarHeight

        val fullControlsWidth = minOf(screenWidth - 48.dp, 440.dp)
        val fullControlsX = (screenWidth - fullControlsWidth) / 2

        val queueButtonSize = 48.dp
        val queueY = screenHeight - navBarBottom - 12.dp - queueButtonSize

        val transportHeight = 86.dp
        val fullCapsuleY = queueY - 14.dp - transportHeight
        val fullCapsuleWidth = minOf(250.dp, fullControlsWidth - 100.dp)
        val fullCapsuleX = (screenWidth - fullCapsuleWidth) / 2

        val seekHeight = 84.dp
        val seekY = fullCapsuleY - 36.dp - seekHeight

        val fullMetaHeight = 58.dp
        val fullMetaY = seekY - 16.dp - fullMetaHeight

        val availableCoverHeight = (fullMetaY - toolbarBottom - 12.dp).coerceAtLeast(160.dp)
        val fullArtSize =
            minOf(screenWidth - 48.dp, availableCoverHeight, 380.dp).coerceAtLeast(160.dp)
        val fullArtX = (screenWidth - fullArtSize) / 2
        val fullArtY = toolbarBottom + (availableCoverHeight - fullArtSize) / 2

        val miniArtSize = 48.dp
        val miniArtX = 12.dp
        val miniArtY = (pillHeight - miniArtSize) / 2

        val miniCapsuleWidth = 132.dp
        val miniCapsuleHeight = 52.dp
        val miniCapsuleX = pillWidth - 12.dp - miniCapsuleWidth
        val miniCapsuleY = (pillHeight - miniCapsuleHeight) / 2

        val miniMetaX = 72.dp
        val miniMetaY = 14.dp
        val miniMetaWidth =
            (pillWidth - 12.dp - miniCapsuleWidth - 8.dp - miniMetaX).coerceAtLeast(0.dp)

        val artSize = lerp(miniArtSize, fullArtSize, progress)
        val artX = lerp(miniArtX, fullArtX, progress)
        val artY = lerp(miniArtY, fullArtY, progress)
        val artRadius = lerp(miniArtSize / 2, 24.dp, progress)
        val artElevation = lerp(0.dp, 20.dp, progress)

        val capsuleWidth = lerp(miniCapsuleWidth, fullCapsuleWidth, progress)
        val capsuleHeight = lerp(miniCapsuleHeight, transportHeight, progress)
        val capsuleX = lerp(miniCapsuleX, fullCapsuleX, progress)
        val capsuleY = lerp(miniCapsuleY, fullCapsuleY, progress)
        val capsuleElevation = lerp(2.dp, 4.dp, progress)

        val metaX = lerp(miniMetaX, fullControlsX, progress)
        val metaY = lerp(miniMetaY, fullMetaY, progress)
        val metaWidth = lerp(miniMetaWidth, fullControlsWidth, progress)
        val titleFontSize = lerp(14.sp, 22.sp, progress)
        val titleLineHeight = lerp(18.sp, 28.sp, progress)
        val artistFontSize = lerp(12.sp, 14.sp, progress)
        val titleFontWeight = if (progress > 0.5f) FontWeight.Bold else FontWeight.SemiBold

        val playButtonSize = lerp(42.dp, 68.dp, progress)
        val playIconSize = lerp(22.dp, 34.dp, progress)
        val bufferingSize = lerp(36.dp, 54.dp, progress)
        val skipButtonSize = lerp(36.dp, 52.dp, progress)
        val skipIconSize = lerp(20.dp, 30.dp, progress)

        val swipeThreshold = with(density) { 56.dp.toPx() }
        val dismissThreshold = with(density) { 48.dp.toPx() }
        val slop = with(density) { 8.dp.toPx() }
        val velocityFlickThreshold = with(density) { 200.dp.toPx() }
        val dragRangePx = with(density) { pillY.toPx() }.coerceAtLeast(1f)

        val dismissY = dismissOffsetYAnimatable.value
        val dismissAlpha = dismissAlphaAnimatable.value
        val swipeX = miniSwipeOffsetX.value

        Box(
            modifier = Modifier
                .offset(
                    x = sheetX + with(density) { swipeX.toDp() },
                    y = sheetY + with(density) { dismissY.toDp() },
                )
                .size(width = sheetWidth, height = sheetHeight)
                .graphicsLayer {
                    alpha = dismissAlpha
                }
                .pointerInput(track.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = down.uptimeMillis
                        var totalX = 0f
                        var totalY = 0f
                        var dragDir = 0
                        val initialProg = progressAnimatable.value
                        val initialActivePanel = activePanelProvider.value
                        val initialPanelFraction = panelFraction.value
                        val panelDragRangePx = size.height.toFloat().coerceAtLeast(1f)
                        var panelDragActive = initialActivePanel != null
                        var panelClosingGesture = false
                        val dragSamples = mutableListOf<Pair<Long, Float>>()
                        dragSamples.add(downTime to 0f)
                        var dragCompleted = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.changedToUp()) {
                                val wasConsumed = change.isConsumed
                                dragCompleted = true
                                if (dragDir == 0) {
                                    if (!wasConsumed && initialProg < 0.05f) {
                                        change.consume()
                                        coroutineScope.launch {
                                            progressAnimatable.animateTo(1f, settleSpec)
                                        }
                                    }
                                } else {
                                    change.consume()
                                    when (dragDir) {
                                        1 -> {
                                            val currentOffset = miniSwipeOffsetX.value
                                            if (currentOffset > swipeThreshold || totalX > swipeThreshold) {
                                                onPreviousClick()
                                            } else if (currentOffset < -swipeThreshold || totalX < -swipeThreshold) {
                                                onNextClick()
                                            }
                                            coroutineScope.launch {
                                                miniSwipeOffsetX.animateTo(0f, settleSpringSpec)
                                            }
                                        }

                                        2 -> {
                                            val currentOffsetY = dismissOffsetYAnimatable.value
                                            if (currentOffsetY > dismissThreshold || totalY > dismissThreshold) {
                                                coroutineScope.launch {
                                                    launch {
                                                        dismissOffsetYAnimatable.animateTo(
                                                            currentOffsetY + 160.dp.toPx(),
                                                            tween(
                                                                durationMillis = 180,
                                                                easing = LinearEasing
                                                            ),
                                                        )
                                                    }
                                                    launch {
                                                        dismissAlphaAnimatable.animateTo(
                                                            0f,
                                                            tween(
                                                                durationMillis = 180,
                                                                easing = LinearEasing
                                                            ),
                                                        )
                                                    }
                                                    delay(180)
                                                    onDismiss()
                                                }
                                            } else {
                                                coroutineScope.launch {
                                                    launch {
                                                        dismissOffsetYAnimatable.animateTo(
                                                            0f,
                                                            settleSpringSpec
                                                        )
                                                    }
                                                    launch {
                                                        dismissAlphaAnimatable.animateTo(
                                                            1f,
                                                            settleSpringSpec
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        3 -> {
                                            val now = SystemClock.uptimeMillis()
                                            val velocityY =
                                                if (dragSamples.size >= 2 && now - dragSamples.last().first <= 120L) {
                                                    val oldest = dragSamples.first()
                                                    val newest = dragSamples.last()
                                                    val dt = (newest.first - oldest.first) / 1000f
                                                    if (dt > 0.008f) {
                                                        (newest.second - oldest.second) / dt
                                                    } else {
                                                        0f
                                                    }
                                                } else {
                                                    0f
                                                }
                                            val currentProg = progressAnimatable.value
                                            val opensLyrics = initialProg >= 0.95f &&
                                                    initialActivePanel == null &&
                                                    (totalY <= -swipeThreshold || velocityY < -velocityFlickThreshold)
                                            if (panelDragActive) {
                                                val shouldClosePanel =
                                                    if (initialActivePanel == null) {
                                                        panelFraction.value < 0.35f &&
                                                                totalY > -swipeThreshold &&
                                                                velocityY >= -velocityFlickThreshold
                                                    } else {
                                                        totalY >= swipeThreshold ||
                                                                velocityY > velocityFlickThreshold ||
                                                                panelFraction.value <= 0.65f
                                                    }
                                                val targetFraction =
                                                    if (shouldClosePanel) 0f else 1f
                                                isPanelClosing = shouldClosePanel
                                                coroutineScope.launch {
                                                    panelFraction.animateTo(
                                                        targetFraction,
                                                        panelSpringSpec
                                                    )
                                                    if (targetFraction == 0f) {
                                                        activePanel = null
                                                        isPanelClosing = false
                                                    }
                                                }
                                            } else if (opensLyrics) {
                                                openPanel(PlayerPanelTab.Lyrics)
                                            } else {
                                                val target =
                                                    if (velocityY < -velocityFlickThreshold) {
                                                        1f
                                                    } else if (velocityY > velocityFlickThreshold) {
                                                        0f
                                                    } else if (initialProg < 0.5f) {
                                                        if (currentProg >= 0.35f) 1f else 0f
                                                    } else {
                                                        if (currentProg <= 0.65f) 0f else 1f
                                                    }
                                                coroutineScope.launch {
                                                    progressAnimatable.animateTo(
                                                        target,
                                                        settleSpec,
                                                    )
                                                }
                                            }
                                        }

                                        4 -> {
                                            val targetPanel = when {
                                                initialActivePanel == PlayerPanelTab.Lyrics && totalX <= -swipeThreshold ->
                                                    PlayerPanelTab.Queue

                                                initialActivePanel == PlayerPanelTab.Queue && totalX >= swipeThreshold ->
                                                    PlayerPanelTab.Lyrics

                                                else -> null
                                            }
                                            targetPanel?.let(openPanel)
                                        }
                                    }
                                }
                                break
                            }

                            if (change.isConsumed && dragDir == 0) {
                                break
                            }

                            val dragAmount = change.positionChange()
                            totalX += dragAmount.x
                            totalY += dragAmount.y
                            val now = change.uptimeMillis
                            dragSamples.add(now to totalY)
                            while (dragSamples.size > 1 && now - dragSamples.first().first > 120L) {
                                dragSamples.removeAt(0)
                            }

                            if (dragDir == 0) {
                                val absX = abs(totalX)
                                val absY = abs(totalY)
                                if (absX > slop || absY > slop) {
                                    if (initialProg <= 0.05f) {
                                        if (absX >= absY) {
                                            dragDir = 1
                                        } else if (totalY > 0f) {
                                            dragDir = 2
                                        } else {
                                            dragDir = 3
                                        }
                                    } else if (initialActivePanel != null && absX >= absY) {
                                        dragDir = 4
                                    } else {
                                        dragDir = 3
                                    }
                                }
                            }

                            if (dragDir != 0) {
                                change.consume()
                                when (dragDir) {
                                    1 -> {
                                        coroutineScope.launch {
                                            miniSwipeOffsetX.snapTo(totalX)
                                        }
                                    }

                                    2 -> {
                                        coroutineScope.launch {
                                            val nextY = totalY.coerceAtLeast(0f)
                                            dismissOffsetYAnimatable.snapTo(nextY)
                                            val fade =
                                                (1f - (nextY / (dismissThreshold * 1.5f))).coerceIn(
                                                    0.2f,
                                                    1f
                                                )
                                            dismissAlphaAnimatable.snapTo(fade)
                                        }
                                    }

                                    3 -> {
                                        if (initialActivePanel != null) {
                                            if (totalY > 0f) panelClosingGesture = true
                                            isPanelClosing = panelClosingGesture
                                            val nextFraction =
                                                (initialPanelFraction - (totalY / panelDragRangePx))
                                                    .coerceIn(0f, 1f)
                                            coroutineScope.launch {
                                                panelFraction.snapTo(
                                                    nextFraction
                                                )
                                            }
                                        } else if (initialProg >= 0.95f &&
                                            (totalY < 0f || panelDragActive)
                                        ) {
                                            if (!panelDragActive) {
                                                panelDragActive = true
                                                isPanelClosing = false
                                                activePanel = PlayerPanelTab.Lyrics
                                            }
                                            if (totalY > 0f) panelClosingGesture = true
                                            isPanelClosing = panelClosingGesture
                                            val nextFraction = (-totalY / panelDragRangePx)
                                                .coerceIn(0f, 1f)
                                            coroutineScope.launch {
                                                panelFraction.snapTo(
                                                    nextFraction
                                                )
                                            }
                                        } else {
                                            val minTotalDragY = -(1f - initialProg) * dragRangePx
                                            val maxTotalDragY = initialProg * dragRangePx
                                            val clampedTotalDragY =
                                                totalY.coerceIn(minTotalDragY, maxTotalDragY)
                                            val nextProgress =
                                                (initialProg - (clampedTotalDragY / dragRangePx)).coerceIn(
                                                    0f,
                                                    1f
                                                )
                                            coroutineScope.launch {
                                                progressAnimatable.snapTo(
                                                    nextProgress
                                                )
                                            }
                                        }
                                    }

                                    4 -> Unit
                                }
                            }
                        }

                        if (!dragCompleted && dragDir != 0) {
                            when (dragDir) {
                                1 -> {
                                    coroutineScope.launch {
                                        miniSwipeOffsetX.animateTo(0f, settleSpringSpec)
                                    }
                                }

                                2 -> {
                                    coroutineScope.launch {
                                        launch {
                                            dismissOffsetYAnimatable.animateTo(
                                                0f,
                                                settleSpringSpec
                                            )
                                        }
                                        launch {
                                            dismissAlphaAnimatable.animateTo(
                                                1f,
                                                settleSpringSpec
                                            )
                                        }
                                    }
                                }

                                3 -> {
                                    if (panelDragActive) {
                                        val shouldOpenPanel = panelFraction.value >= 0.35f
                                        val targetFraction = if (shouldOpenPanel) 1f else 0f
                                        isPanelClosing = !shouldOpenPanel
                                        coroutineScope.launch {
                                            panelFraction.animateTo(targetFraction, panelSpringSpec)
                                            if (targetFraction == 0f) {
                                                activePanel = null
                                                isPanelClosing = false
                                            }
                                        }
                                    } else {
                                        val target =
                                            if (progressAnimatable.value >= 0.5f) 1f else 0f
                                        coroutineScope.launch {
                                            progressAnimatable.animateTo(target, settleSpec)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxSize(),
                backdropState = backdropState,
                shape = sheetShape,
                cornerRadius = sheetTopRadius,
                topRadius = sheetTopRadius,
                bottomRadius = sheetBottomRadius,
                shadowElevation = lerp(6.dp, 0.dp, progress),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .liquidGlassBackdropProducer(playerBackdropState, playerBackdropLayer),
                ) {
                    if (fullAlpha > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = fullAlpha }
                                .background(MaterialTheme.colorScheme.background),
                        )
                    }

                    if (miniAlpha > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = miniAlpha }
                                .border(
                                    BorderStroke(
                                        0.5.dp,
                                        Brush.verticalGradient(
                                            colors = if (isDark) {
                                                listOf(
                                                    Color.White.copy(alpha = 0.22f),
                                                    Color.White.copy(alpha = 0.05f),
                                                    Color.Transparent,
                                                )
                                            } else {
                                                listOf(
                                                    Color.White.copy(alpha = 0.40f),
                                                    Color.White.copy(alpha = 0.10f),
                                                    Color.Transparent,
                                                )
                                            },
                                        ),
                                    ),
                                    sheetShape,
                                ),
                        )
                    }

                    val miniIndicatorAlpha =
                        (miniAlpha * (1f - (progress / 0.15f))).coerceIn(0f, 1f)
                    val miniArtworkScale by animateFloatAsState(
                        targetValue = if (isPlaying) 0.80f else 1.0f,
                        animationSpec = tween(durationMillis = 450),
                        label = "morphingMiniArtworkScale",
                    )
                    val effectiveArtScale = lerpFloat(miniArtworkScale, 1.0f, progress)
                    val animatedWavyAmplitude by animateFloatAsState(
                        targetValue = if (isPlaying) 1f else 0f,
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                        label = "morphingWavyAmplitude",
                    )

                    Box(
                        modifier = Modifier
                            .offset(x = artX, y = artY)
                            .size(artSize)
                            .graphicsLayer {
                                translationX = coverOffsetX.value
                                scaleX = effectiveArtScale
                                scaleY = effectiveArtScale
                            }
                            .shadow(
                                elevation = artElevation,
                                shape = RoundedCornerShape(artRadius),
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.45f),
                                spotColor = Color.Black.copy(alpha = 0.65f),
                            )
                            .then(
                                if (progress > 0.8f && activePanel == null) {
                                    Modifier.pointerInput(track.id) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                coverAccumulatedDragX = 0f
                                            },
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    if (abs(coverAccumulatedDragX) > coverSnapThreshold) {
                                                        if (coverAccumulatedDragX > 0) {
                                                            onPreviousClick()
                                                        } else {
                                                            onNextClick()
                                                        }
                                                    }
                                                    coverAccumulatedDragX = 0f
                                                    coverOffsetX.animateTo(
                                                        0f,
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessMediumLow,
                                                        ),
                                                    )
                                                }
                                            },
                                            onDragCancel = {
                                                coroutineScope.launch {
                                                    coverAccumulatedDragX = 0f
                                                    coverOffsetX.animateTo(
                                                        0f,
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessMediumLow,
                                                        ),
                                                    )
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                coroutineScope.launch {
                                                    coverAccumulatedDragX += dragAmount
                                                    val fraction =
                                                        (abs(coverAccumulatedDragX) / (size.width.toFloat() * 1.5f)).coerceIn(
                                                            0f,
                                                            1f
                                                        )
                                                    val tension =
                                                        lerpFloat(0f, maxCoverTension, fraction)
                                                    val finalOffset =
                                                        if (coverAccumulatedDragX > 0) tension else -tension
                                                    coverOffsetX.snapTo(finalOffset)
                                                }
                                            },
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .clip(RoundedCornerShape(artRadius))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Color.White.copy(alpha = lerpFloat(0.20f, 0.18f, progress)),
                                ),
                                RoundedCornerShape(artRadius),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val lowResArtworkUrl = remember(track.artworkUrl) {
                            ArtworkUrlHelper.toLowQuality(track.artworkUrl)
                        }
                        val highResArtworkUrl = remember(track.artworkUrl) {
                            ArtworkUrlHelper.toHighQuality(track.artworkUrl)
                        }
                        if (!lowResArtworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(lowResArtworkUrl)
                                    .crossfade(true)
                                    .build(),
                                placeholder = painterResource(R.drawable.app_icon_small),
                                error = painterResource(R.drawable.app_icon_small),
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (!highResArtworkUrl.isNullOrBlank() && progress > 0.01f) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(highResArtworkUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = ((progress - 0.01f) / 0.20f).coerceIn(0f, 1f)
                                        },
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.app_icon_small),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(
                                    alpha = lerpFloat(
                                        0.6f,
                                        0.7f,
                                        progress
                                    )
                                ),
                                modifier = Modifier.size(lerp(24.dp, 72.dp, progress)),
                            )
                        }
                        if (motionArtwork != null && progress >= MotionArtworkDisplayProgress) {
                            MotionArtworkVideo(
                                artwork = motionArtwork,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = ((progress - MotionArtworkDisplayProgress) /
                                                (1f - MotionArtworkDisplayProgress))
                                            .coerceIn(0f, 1f)
                                    },
                            )
                        }
                    }

                    if (miniIndicatorAlpha > 0.001f) {
                        CircularWavyProgressIndicator(
                            progress = { playbackProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .offset(x = artX - 3.dp, y = artY - 3.dp)
                                .size(artSize + 6.dp)
                                .graphicsLayer {
                                    alpha = miniIndicatorAlpha
                                    translationX = coverOffsetX.value
                                },
                            color = controllerIconTint,
                            trackColor = if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.12f
                            ),
                            stroke = remember(density) {
                                Stroke(
                                    width = with(density) { 2.5.dp.toPx() },
                                    cap = StrokeCap.Round
                                )
                            },
                            trackStroke = remember(density) {
                                Stroke(
                                    width = with(density) { 2.5.dp.toPx() },
                                    cap = StrokeCap.Round
                                )
                            },
                            amplitude = { p -> if (p > 0f) animatedWavyAmplitude else 0f },
                            wavelength = WavyProgressIndicatorDefaults.CircularWavelength,
                            waveSpeed = if (isPlaying) WavyProgressIndicatorDefaults.CircularWavelength / 2f else 0.dp,
                        )
                    }

                    val effectiveLeftFade = leftFadeAlpha.value * miniAlpha
                    Column(
                        modifier = Modifier
                            .offset(x = metaX, y = metaY)
                            .width(metaWidth)
                            .then(
                                if (effectiveLeftFade > 0.01f) {
                                    Modifier
                                        .graphicsLayer {
                                            compositingStrategy = CompositingStrategy.Offscreen
                                        }
                                        .drawWithContent {
                                            drawContent()
                                            val leftEdgeColor =
                                                Color.Black.copy(alpha = 1f - effectiveLeftFade)
                                            drawRect(
                                                brush = Brush.horizontalGradient(
                                                    0.0f to leftEdgeColor,
                                                    0.10f to Color.Black,
                                                    0.90f to Color.Black,
                                                    1.0f to Color.Transparent,
                                                ),
                                                blendMode = BlendMode.DstIn,
                                            )
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val titleColor = lerpColor(
                            if (isDark) Color.White else Color.Black.copy(alpha = 0.85f),
                            if (isDark) Color.White else Color(0xFF191C1E),
                            progress,
                        )
                        Text(
                            text = track.title,
                            fontSize = titleFontSize,
                            lineHeight = titleLineHeight,
                            fontWeight = titleFontWeight,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
                        )
                        val artistColor = lerpColor(
                            if (isDark) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            if (isDark) Color.White.copy(alpha = 0.75f) else Color(0xFF43474E),
                            progress,
                        )
                        Text(
                            text = track.artist,
                            fontSize = artistFontSize,
                            color = artistColor,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .padding(
                                    top = lerp(
                                        0.dp,
                                        4.dp,
                                        progress
                                    ).coerceAtLeast(0.dp)
                                )
                                .basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
                        )
                    }

                    if (fullControlsAlpha > 0.001f) {
                        val audioBadgeAlpha =
                            if (showAudioInfo) 0f else (1f - (audioDialogProgress / 0.08f)).coerceIn(
                                0f,
                                1f
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
                            onAudioQualityClick = { showAudioInfo = true },
                            onAudioQualityPositioned = { coords -> audioBadgeBounds = coords },
                            audioBadgeAlpha = audioBadgeAlpha,
                            isDark = isDark,
                            modifier = Modifier
                                .offset(x = fullControlsX, y = seekY)
                                .width(fullControlsWidth)
                                .height(seekHeight)
                                .graphicsLayer {
                                    alpha = fullControlsAlpha
                                    scaleX = fullControlsScale
                                    scaleY = fullControlsScale
                                    translationY = fullControlsCounterY
                                },
                        )

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = fullControlsX,
                                    y = fullCapsuleY + (transportHeight - 44.dp) / 2
                                )
                                .size(44.dp)
                                .graphicsLayer {
                                    alpha = effectiveFullControlsAlpha
                                    scaleX = fullControlsScale
                                    scaleY = fullControlsScale
                                    translationY = effectiveFullControlsCounterY
                                }
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onToggleShuffle,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shuffle),
                                contentDescription = "Shuffle",
                                tint = if (isShuffle) controllerIconTint else inactiveControllerIconTint,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        val repeatIconRes = if (repeatMode == RepeatMode.ONE) {
                            R.drawable.ic_repeat_one
                        } else {
                            R.drawable.ic_repeat
                        }
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = fullControlsX + fullControlsWidth - 44.dp,
                                    y = fullCapsuleY + (transportHeight - 44.dp) / 2
                                )
                                .size(44.dp)
                                .graphicsLayer {
                                    alpha = effectiveFullControlsAlpha
                                    scaleX = fullControlsScale
                                    scaleY = fullControlsScale
                                    translationY = effectiveFullControlsCounterY
                                }
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onCycleRepeatMode,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(repeatIconRes),
                                contentDescription = "Repeat",
                                tint = if (repeatMode != RepeatMode.OFF) {
                                    controllerIconTint
                                } else {
                                    inactiveControllerIconTint
                                },
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        PlayerBottomBar(
                            onOpenLyrics = {
                                openPanel(PlayerPanelTab.Lyrics)
                            },
                            onOpenQueue = {
                                openPanel(PlayerPanelTab.Queue)
                            },
                            isDark = isDark,
                            modifier = Modifier
                                .offset(x = 0.dp, y = queueY)
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = effectiveFullControlsAlpha
                                    scaleX = fullControlsScale
                                    scaleY = fullControlsScale
                                    translationY = effectiveFullControlsCounterY
                                },
                        )

                        FullPlayerToolbar(
                            albumName = track.album,
                            onOpenAlbum = if (onOpenAlbum != null && !track.album.isNullOrBlank()) {
                                {
                                    val openAlbum = onOpenAlbum
                                    coroutineScope.launch {
                                        if (openAlbum(track)) {
                                            progressAnimatable.animateTo(0f, settleSpec)
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                            onCollapse = {
                                coroutineScope.launch {
                                    progressAnimatable.animateTo(0f, settleSpec)
                                }
                            },
                            onMoreClick = onMoreClick,
                            isDark = isDark,
                            modifier = Modifier
                                .offset(x = 0.dp, y = toolbarTop)
                                .fillMaxWidth()
                                .height(toolbarHeight)
                                .graphicsLayer {
                                    alpha = effectiveFullControlsAlpha
                                    scaleX = fullControlsScale
                                    scaleY = fullControlsScale
                                    translationY = effectiveFullControlsCounterY
                                },
                        )
                    }

                    val capsuleGradient = Brush.verticalGradient(
                        colors = listOf(
                            lerpColor(
                                if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(
                                    alpha = 0.75f
                                ),
                                if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(
                                    alpha = 0.85f
                                ),
                                progress,
                            ),
                            lerpColor(
                                if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                                    alpha = 0.50f
                                ) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f),
                                if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(
                                    alpha = 0.50f
                                ),
                                progress,
                            ),
                        ),
                    )
                    val capsuleBorderBrush = Brush.verticalGradient(
                        colors = listOf(
                            lerpColor(
                                if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(
                                    alpha = 0.40f
                                ),
                                if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(
                                    alpha = 0.12f
                                ),
                                progress,
                            ),
                            lerpColor(
                                if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(
                                    alpha = 0.10f
                                ),
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(
                                    alpha = 0.04f
                                ),
                                progress,
                            ),
                            Color.Transparent,
                        ),
                    )

                    Row(
                        modifier = Modifier
                            .offset(x = capsuleX, y = capsuleY)
                            .size(width = capsuleWidth, height = capsuleHeight)
                            .shadow(
                                elevation = capsuleElevation,
                                shape = CircleShape,
                                clip = false,
                            )
                            .background(
                                brush = capsuleGradient,
                                shape = CircleShape,
                            )
                            .border(
                                width = 0.5.dp,
                                brush = capsuleBorderBrush,
                                shape = CircleShape,
                            )
                            .clip(CircleShape)
                            .padding(horizontal = lerp(6.dp, 16.dp, progress).coerceAtLeast(0.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(skipButtonSize)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onPreviousClick,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip),
                                contentDescription = "Previous",
                                modifier = Modifier
                                    .size(skipIconSize)
                                    .rotate(180f),
                                tint = controllerIconTint,
                            )
                        }

                        val playButtonGradient = Brush.verticalGradient(
                            colors = listOf(
                                if (isDark) Color.White.copy(alpha = 0.95f) else Color(0xFF191C1E),
                                if (isDark) Color.White.copy(alpha = 0.75f) else Color(0xFF2C3135),
                            ),
                        )
                        val playButtonBorderBrush = Brush.verticalGradient(
                            colors = listOf(
                                if (isDark) Color.White else Color.Black.copy(alpha = 0.20f),
                                if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(
                                    alpha = 0.05f
                                ),
                                Color.Transparent,
                            ),
                        )
                        val animatedCookieShape =
                            remember(cookieMorphProgress, rotationAnimatable.value) {
                                MorphingPlayerCookieShape(
                                    morphProgress = cookieMorphProgress,
                                    rotationAngle = rotationAnimatable.value,
                                )
                            }

                        Box(
                            modifier = Modifier
                                .size(playButtonSize)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onPlayPauseClick,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Crossfade(
                                targetState = isBuffering,
                                animationSpec = motionScheme.fastEffectsSpec(),
                                label = "playButtonBufferingCrossfade",
                            ) { buffering ->
                                if (buffering) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularWavyProgressIndicator(
                                            modifier = Modifier.size(bufferingSize),
                                            color = controllerIconTint,
                                            trackColor = Color.Transparent,
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .shadow(
                                                    elevation = lerp(2.dp, 4.dp, progress),
                                                    shape = animatedCookieShape,
                                                    clip = false,
                                                )
                                                .background(
                                                    brush = playButtonGradient,
                                                    shape = animatedCookieShape,
                                                )
                                                .border(
                                                    width = 0.5.dp,
                                                    brush = playButtonBorderBrush,
                                                    shape = animatedCookieShape,
                                                ),
                                        )
                                        Crossfade(
                                            targetState = isPlaying,
                                            animationSpec = motionScheme.fastEffectsSpec(),
                                            label = "playPauseIconCrossfade",
                                        ) { playing ->
                                            Icon(
                                                painter = painterResource(
                                                    if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                                                ),
                                                contentDescription = if (playing) "Pause" else "Play",
                                                modifier = Modifier.size(playIconSize),
                                                tint = playIconTint,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(skipButtonSize)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onNextClick,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip),
                                contentDescription = "Next",
                                modifier = Modifier.size(skipIconSize),
                                tint = controllerIconTint,
                            )
                        }
                    }
                }
            }
        }

        PlayerOverlayPanels(
            state = PlayerOverlayState(
                track = track,
                audioQuality = audioQuality,
                switchingQualityFormat = switchingQualityFormat,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                isShuffle = isShuffle,
                repeatMode = repeatMode,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                queueState = queueState,
                lyricsLines = lyricsLines,
                lyricsLoading = lyricsLoading,
                spectrum = spectrum,
            ),
            actions = PlayerOverlayActions(
                onPlayPause = onPlayPauseClick,
                onPrevious = onPreviousClick,
                onNext = onNextClick,
                onSeek = onSeek,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeatMode = onCycleRepeatMode,
                onAudioQualityClick = { showAudioInfo = true },
                onQualityVariantSelected = onQualityVariantSelected ?: {},
                onTrackClick = { selected -> onTrackClick?.invoke(selected) },
                onRemoveUpNext = { index -> onRemoveUpNext?.invoke(index) },
                onMoveUpNext = { from, to -> onMoveUpNext?.invoke(from, to) },
            ),
            selectedPanel = activePanel,
            panelFractionProvider = { panelFraction.value },
            onSelectPanel = openPanel,
            onClosePanel = closePanel,
            onAudioQualityPositioned = { audioBadgeBounds = it },
            isDark = isDark,
            backdropState = playerBackdropState,
            handleSwipeDismiss = false,
        )

        AudioInfoDialog(
            isOpen = showAudioInfo,
            onDismiss = { showAudioInfo = false },
            pipeline = audioQuality?.pipelineDetails,
            track = track,
            durationMs = durationMs,
            originBounds = audioBadgeBounds,
            backdropState = playerBackdropState,
            isDark = isDark,
            onProgress = { audioDialogProgress = it },
        )
    }
}

@Composable
private fun FullPlayerToolbar(
    albumName: String?,
    onOpenAlbum: (() -> Unit)?,
    onCollapse: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCollapse,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = "Collapse",
                tint = if (isDark) Color.White else Color(0xFF191C1E),
                modifier = Modifier.size(22.dp),
            )
        }

        val hasAlbum = !albumName.isNullOrBlank()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "NOW PLAYING",
                color = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.60f),
                fontSize = if (hasAlbum) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasAlbum) {
                Text(
                    text = albumName,
                    color = if (isDark) Color.White.copy(alpha = 0.90f) else Color(0xFF191C1E),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onOpenAlbum != null) {
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                onClickLabel = "Open album",
                                onClick = onOpenAlbum,
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMoreClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = "More",
                tint = if (isDark) Color.White else Color(0xFF191C1E),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
