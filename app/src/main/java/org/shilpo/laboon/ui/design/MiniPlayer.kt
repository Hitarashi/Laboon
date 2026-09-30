@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import kotlin.math.roundToInt

private val CookieMorph = Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided)

private data class CookieMorphShape(
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
fun MiniPlayer(
    track: HomeTrack,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    progress: Float = 0f,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    backdropState: LiquidGlassBackdropState? = null,
) {
    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val offsetXAnimatable = remember { Animatable(0f) }
    val swipeThreshold = with(density) { 56.dp.toPx() }
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

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
        label = "cookieMorphProgress",
    )

    val textMeasurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleSmall
    val artistStyle = MaterialTheme.typography.bodySmall
    val hasMarqueeOverflow = remember(track.title, track.artist, density) {
        val titleWidth = textMeasurer.measure(track.title, titleStyle).size.width
        val artistWidth = textMeasurer.measure(track.artist, artistStyle).size.width
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

    val miniPlayerShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 12.dp,
        bottomEnd = 12.dp,
    )
    val artworkShape = CircleShape

    val isDark = isSystemInDarkTheme()
    val controlIconTint = if (isDark) {
        Color.White
    } else {
        Color.Black.copy(alpha = 0.85f)
    }
    val pillGradient = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.12f),
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.50f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.75f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f),
            )
        },
    )
    val borderBrush = Brush.verticalGradient(
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
    )
    val playButtonGradient = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.18f),
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.50f),
            )
        },
    )
    val playButtonBorderBrush = Brush.verticalGradient(
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
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .widthIn(max = NavigationBarMaxWidth)
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            backdropState = backdropState,
            shape = miniPlayerShape,
            cornerRadius = 28.dp,
            topRadius = 28.dp,
            bottomRadius = 12.dp,
            shadowElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(offsetXAnimatable.value.roundToInt(), 0) }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragCancel = {
                                    coroutineScope.launch {
                                        offsetXAnimatable.animateTo(0f, springSpec)
                                    }
                                },
                                onDragEnd = {
                                    val currentOffset = offsetXAnimatable.value
                                    if (currentOffset > swipeThreshold) {
                                        onPreviousClick()
                                    } else if (currentOffset < -swipeThreshold) {
                                        onNextClick()
                                    }
                                    coroutineScope.launch {
                                        offsetXAnimatable.animateTo(0f, springSpec)
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        offsetXAnimatable.snapTo(offsetXAnimatable.value + dragAmount)
                                    }
                                },
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart)
                            .padding(start = 60.dp, end = 140.dp)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                val leftEdgeColor =
                                    Color.Black.copy(alpha = 1f - leftFadeAlpha.value)
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0.0f to leftEdgeColor,
                                        0.10f to Color.Black,
                                        0.90f to Color.Black,
                                        1.0f to Color.Transparent,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0),
                            )
                            ProviderIcon(
                                provider = track.source,
                                height = 9.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                            CodecIcon(
                                codec = track.codec,
                                height = 9.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterStart)
                            .clip(artworkShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), artworkShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .height(52.dp)
                            .shadow(
                                elevation = 2.dp,
                                shape = CircleShape,
                                clip = false,
                            )
                            .background(
                                brush = pillGradient,
                                shape = CircleShape,
                            )
                            .border(
                                width = 0.5.dp,
                                brush = borderBrush,
                                shape = CircleShape,
                            )
                            .clip(CircleShape)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onPreviousClick,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_previous),
                                contentDescription = "Previous",
                                modifier = Modifier.size(20.dp),
                                tint = controlIconTint,
                            )
                        }

                        PlayPauseButton(
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            morphProgress = morphProgress,
                            rotationAngle = { rotationAnimatable.value },
                            playButtonGradient = playButtonGradient,
                            playButtonBorderBrush = playButtonBorderBrush,
                            controlIconTint = controlIconTint,
                            onPlayPauseClick = onPlayPauseClick,
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onNextClick,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_next),
                                contentDescription = "Next",
                                modifier = Modifier.size(20.dp),
                                tint = controlIconTint,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    morphProgress: Float,
    rotationAngle: () -> Float,
    playButtonGradient: Brush,
    playButtonBorderBrush: Brush,
    controlIconTint: Color,
    onPlayPauseClick: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val angle = rotationAngle()
    val animatedCookieShape = remember(morphProgress, angle) {
        CookieMorphShape(
            morphProgress = morphProgress,
            rotationAngle = angle,
        )
    }

    Box(
        modifier = Modifier
            .size(42.dp)
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
            label = "miniPlayerBufferingCrossfade",
        ) { buffering ->
            if (buffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
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
                                elevation = 2.dp,
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
                        label = "miniPlayerPlayPauseCrossfade",
                    ) { playing ->
                        Icon(
                            painter = painterResource(
                                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                            ),
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(22.dp),
                            tint = controlIconTint,
                        )
                    }
                }
            }
        }
    }
}

