@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.ArtworkUrlHelper
import org.shilpo.laboon.playback.AudioQualityInfo
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.playback.SpectrumFrame
import org.shilpo.laboon.ui.design.AudioQualityBadge
import org.shilpo.laboon.ui.design.liquidGlassBackdropProducer
import org.shilpo.laboon.ui.design.rememberLiquidGlassBackdropState
import kotlin.math.abs

private val FullPlayerCookieMorph = Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided)

private data class FullPlayerCookieMorphShape(
    val morphProgress: Float,
    val rotationAngle: Float = 0f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = FullPlayerCookieMorph.toPath(morphProgress.coerceIn(0f, 1f)).asComposePath()
        val matrix = Matrix()
        val bounds = FullPlayerCookieMorph.calculateBounds()
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

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun FullPlayerScreen(
    track: HomeTrack,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    progress: Float = 0f,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    audioQuality: AudioQualityInfo? = null,
    isShuffle: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    spectrum: SpectrumFrame = SpectrumFrame(),
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: () -> Unit = {},
    isDark: Boolean = isSystemInDarkTheme(),
) {
    BackHandler(enabled = true) {
        onCollapse()
    }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val collapseOffsetY = remember { Animatable(0f) }
    val collapseThreshold = with(density) { 100.dp.toPx() }

    var showAudioInfo by remember { mutableStateOf(false) }
    var audioBadgeBounds by remember { mutableStateOf<Rect?>(null) }
    var audioDialogProgress by remember { mutableFloatStateOf(0f) }
    val playerBackdropState = rememberLiquidGlassBackdropState()
    val playerBackdropLayer = rememberGraphicsLayer()

    LaunchedEffect(track.id) {
        collapseOffsetY.snapTo(0f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = collapseOffsetY.value
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {},
                    onDragCancel = {
                        coroutineScope.launch {
                            collapseOffsetY.animateTo(
                                0f,
                                spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
                            )
                        }
                    },
                    onDragEnd = {
                        if (collapseOffsetY.value > collapseThreshold) {
                            coroutineScope.launch {
                                collapseOffsetY.snapTo(0f)
                                onCollapse()
                            }
                        } else {
                            coroutineScope.launch {
                                collapseOffsetY.animateTo(
                                    0f,
                                    spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
                                )
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0 || collapseOffsetY.value > 0) {
                            change.consume()
                            coroutineScope.launch {
                                val nextY = (collapseOffsetY.value + dragAmount).coerceAtLeast(0f)
                                collapseOffsetY.snapTo(nextY)
                            }
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdropProducer(playerBackdropState, playerBackdropLayer),
        ) {
            DancingGlowBackground(
                artworkUrl = track.artworkUrl,
                spectrum = spectrum,
                isPlaying = isPlaying,
                isDark = isDark,
                modifier = Modifier.fillMaxSize(),
            )

            FullPlayerLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                toolbar = {
                    FullPlayerToolbar(
                        albumName = track.album,
                        onCollapse = onCollapse,
                        onMoreClick = onMoreClick,
                        isDark = isDark,
                    )
                },
                cover = {
                    FullPlayerCoverCard(
                        track = track,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                    )
                },
                controls = {
                    FullPlayerControls(
                        track = track,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        progress = progress,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        audioQuality = audioQuality,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        onPlayPauseClick = onPlayPauseClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        onSeek = onSeek,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeatMode = onCycleRepeatMode,
                        onOpenQueue = onOpenQueue,
                        onAudioQualityClick = { showAudioInfo = true },
                        onAudioQualityPositioned = { coords -> audioBadgeBounds = coords },
                        audioBadgeAlpha = if (showAudioInfo) 0f else (1f - (audioDialogProgress / 0.08f)).coerceIn(
                            0f,
                            1f
                        ),
                        isDark = isDark,
                    )
                },
            )
        }

        AudioInfoDialog(
            isOpen = showAudioInfo,
            onDismiss = { showAudioInfo = false },
            pipeline = audioQuality?.pipelineDetails,
            quality = audioQuality,
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
private fun FullPlayerLayout(
    modifier: Modifier = Modifier,
    toolbar: @Composable () -> Unit,
    cover: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    Layout(
        content = {
            toolbar()
            cover()
            controls()
        },
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            val toolbarMeasurable = measurables[0]
            val coverMeasurable = measurables[1]
            val controlsMeasurable = measurables[2]

            val maxW = constraints.maxWidth
            val maxH = constraints.maxHeight

            val toolbarPlaceable = toolbarMeasurable.measure(
                Constraints(minWidth = 0, maxWidth = maxW, minHeight = 0, maxHeight = maxH),
            )
            val toolbarH = toolbarPlaceable.height

            val controlsPlaceable = controlsMeasurable.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = maxW,
                    minHeight = 0,
                    maxHeight = (maxH - toolbarH).coerceAtLeast(0),
                ),
            )
            val controlsH = controlsPlaceable.height

            val availableCoverH = (maxH - toolbarH - controlsH).coerceAtLeast(0)
            val coverSide = minOf(maxW, availableCoverH)
            val coverPlaceable = coverMeasurable.measure(
                Constraints.fixed(coverSide, coverSide),
            )
            val coverH = coverPlaceable.height

            val toolbarX = (maxW - toolbarPlaceable.width) / 2
            val coverX = (maxW - coverPlaceable.width) / 2
            val coverY = toolbarH + (availableCoverH - coverH) / 2
            val controlsX = (maxW - controlsPlaceable.width) / 2
            val controlsY = maxH - controlsH

            layout(maxW, maxH) {
                coverPlaceable.placeRelative(x = coverX, y = coverY)
                controlsPlaceable.placeRelative(x = controlsX, y = controlsY)
                toolbarPlaceable.placeRelative(x = toolbarX, y = 0)
            }
        },
    )
}

@Composable
private fun FullPlayerToolbar(
    albumName: String?,
    onCollapse: () -> Unit,
    onMoreClick: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                .border(
                    BorderStroke(
                        0.5.dp,
                        if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
                    ), CircleShape
                )
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
                fontSize = if (albumName != null) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!albumName.isNullOrBlank()) {
                Text(
                    text = albumName,
                    color = if (isDark) Color.White.copy(alpha = 0.90f) else Color(0xFF191C1E),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                .border(
                    BorderStroke(
                        0.5.dp,
                        if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
                    ), CircleShape
                )
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

@Composable
private fun FullPlayerCoverCard(
    track: HomeTrack,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    val maxTensionOffsetPx = with(density) { 56.dp.toPx() }
    val snapThresholdPx = with(density) { 72.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f)
                .graphicsLayer {
                    translationX = offsetX.value
                }
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(24.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.65f),
                )
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulatedDragX = 0f
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (abs(accumulatedDragX) > snapThresholdPx) {
                                    if (accumulatedDragX > 0) {
                                        onPreviousClick()
                                    } else {
                                        onNextClick()
                                    }
                                }
                                accumulatedDragX = 0f
                                offsetX.animateTo(
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
                                accumulatedDragX = 0f
                                offsetX.animateTo(
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
                                accumulatedDragX += dragAmount
                                val fraction =
                                    (abs(accumulatedDragX) / (size.width.toFloat() * 1.5f)).coerceIn(
                                        0f,
                                        1f,
                                    )
                                val tension = lerp(0f, maxTensionOffsetPx, fraction)
                                val finalOffset =
                                    if (accumulatedDragX > 0) tension else -tension
                                offsetX.snapTo(finalOffset)
                            }
                        },
                    )
                }
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val highResUrl = remember(track.artworkUrl) {
                ArtworkUrlHelper.toHighQuality(track.artworkUrl)
            }
            if (!highResUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(highResUrl)
                        .crossfade(true)
                        .build(),
                    placeholder = painterResource(R.drawable.app_icon_small),
                    error = painterResource(R.drawable.app_icon_small),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.app_icon_small),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(72.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullPlayerControls(
    track: HomeTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Float,
    currentPositionMs: Long,
    durationMs: Long,
    audioQuality: AudioQualityInfo? = null,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onAudioQualityClick: () -> Unit = {},
    onAudioQualityPositioned: ((Rect) -> Unit)? = null,
    audioBadgeAlpha: Float = 1f,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .widthIn(max = 440.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF191C1E),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color.White.copy(alpha = 0.75f) else Color(0xFF43474E),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .basicMarquee(),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        var isSeeking by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableFloatStateOf(0f) }

        val (smoothProgressFraction, displayedPosition) = rememberSmoothProgress(
            isPlayingProvider = { isPlaying },
            currentPositionProvider = {
                if (isSeeking) (seekPosition * durationMs.coerceAtLeast(0L)).toLong() else currentPositionMs
            },
            totalDuration = durationMs.coerceAtLeast(0L),
            isVisible = true,
        )

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
            enabled = durationMs > 0L,
            activeTrackColor = if (isDark) Color.White else Color(0xFF191C1E),
            inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.24f) else Color.Black.copy(
                alpha = 0.16f
            ),
            thumbColor = if (isDark) Color.White else Color(0xFF191C1E),
            isPlaying = isPlaying,
            isVisible = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val displayMs = if (isSeeking) {
                (seekPosition * durationMs).toLong()
            } else {
                displayedPosition.value
            }
            Text(
                text = formatMs(displayMs),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
            )

            AudioQualityBadge(
                quality = audioQuality,
                fallbackCodec = track.codec,
                track = track,
                isDark = isDark,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = audioBadgeAlpha
                    }
                    .then(
                        if (onAudioQualityPositioned != null) {
                            Modifier.onGloballyPositioned { coords ->
                                onAudioQualityPositioned(coords.boundsInRoot())
                            }
                        } else Modifier
                    ),
                onClick = onAudioQualityClick,
            )

            Text(
                text = formatMs(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FullPlayerTransportControls(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            onPlayPauseClick = onPlayPauseClick,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeatMode = onCycleRepeatMode,
            isDark = isDark,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenQueue,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_queue_music),
                    contentDescription = "Queue",
                    tint = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun FullPlayerTransportControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val motionScheme = MaterialTheme.motionScheme
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

    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "fullPlayerCookieMorphProgress",
    )

    val animatedCookieShape = remember(morphProgress, rotationAnimatable.value) {
        FullPlayerCookieMorphShape(
            morphProgress = morphProgress,
            rotationAngle = rotationAnimatable.value,
        )
    }

    val activeAccent = MaterialTheme.colorScheme.primary
    val inactiveTint =
        if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.45f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
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
                tint = if (isShuffle) activeAccent else inactiveTint,
                modifier = Modifier.size(24.dp),
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(86.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    clip = false,
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.85f),
                            if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.50f),
                        ),
                    ),
                    shape = CircleShape,
                )
                .border(
                    BorderStroke(
                        0.5.dp,
                        Brush.verticalGradient(
                            colors = listOf(
                                if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(
                                    alpha = 0.12f
                                ),
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(
                                    alpha = 0.04f
                                ),
                                Color.Transparent,
                            ),
                        ),
                    ),
                    shape = CircleShape,
                )
                .clip(CircleShape)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
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
                    tint = if (isDark) Color.White else Color(0xFF191C1E),
                    modifier = Modifier
                        .size(30.dp)
                        .rotate(180f),
                )
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
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
                    label = "fullPlayerBufferingCrossfade",
                ) { buffering ->
                    if (buffering) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(54.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(
                                    elevation = 4.dp,
                                    shape = animatedCookieShape,
                                    clip = false,
                                )
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            if (isDark) Color.White.copy(alpha = 0.95f) else Color(
                                                0xFF191C1E
                                            ),
                                            if (isDark) Color.White.copy(alpha = 0.75f) else Color(
                                                0xFF2C3135
                                            ),
                                        ),
                                    ),
                                    shape = animatedCookieShape,
                                )
                                .border(
                                    width = 0.5.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            if (isDark) Color.White else Color.Black.copy(alpha = 0.20f),
                                            if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(
                                                alpha = 0.05f
                                            ),
                                        ),
                                    ),
                                    shape = animatedCookieShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Crossfade(
                                targetState = isPlaying,
                                animationSpec = motionScheme.fastEffectsSpec(),
                                label = "fullPlayerPlayPauseCrossfade",
                            ) { playing ->
                                Icon(
                                    painter = painterResource(
                                        if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                                    ),
                                    contentDescription = if (playing) "Pause" else "Play",
                                    modifier = Modifier.size(34.dp),
                                    tint = if (isDark) Color.Black.copy(alpha = 0.85f) else Color.White,
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
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
                    tint = if (isDark) Color.White else Color(0xFF191C1E),
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        val repeatIconRes = if (repeatMode == RepeatMode.ONE) {
            R.drawable.ic_repeat_one
        } else {
            R.drawable.ic_repeat
        }

        Box(
            modifier = Modifier
                .size(44.dp)
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
                tint = if (repeatMode != RepeatMode.OFF) activeAccent else inactiveTint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
