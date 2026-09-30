package org.shilpo.laboon.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.playback.SpectrumFrame
import org.shilpo.laboon.ui.design.theme.ArtworkColorExtractor
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private data class ActiveGlowPalette(
    val top: Color,
    val center: Color,
    val bottom: Color,
    val accent: Color,
    val dominant: Color,
    val base: Color,
)

private fun tuneColor(color: Color, saturation: Float, lightness: Float): Color {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    val hsl = ArtworkColorExtractor.rgbToHsl(r, g, b)
    val s = if (hsl[1] < 0.10f) hsl[1] * saturation else saturation
    return Color.hsl(hsl[0], s.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
}

@Composable
fun DancingGlowBackground(
    artworkUrl: String?,
    spectrum: SpectrumFrame,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    var spatialPalette by remember(artworkUrl) {
        mutableStateOf(ArtworkColorExtractor.getCachedSpatialPalette(artworkUrl))
    }

    LaunchedEffect(artworkUrl) {
        spatialPalette = ArtworkColorExtractor.extractSpatialPalette(context, artworkUrl)
    }

    val targetPalette = remember(spatialPalette, isDark) {
        val palette = spatialPalette
        if (palette == null) {
            if (isDark) {
                ActiveGlowPalette(
                    top = Color(0xFF38BDF8),
                    center = Color(0xFFF472B6),
                    bottom = Color(0xFFA78BFA),
                    accent = Color(0xFF818CF8),
                    dominant = Color(0xFF6366F1),
                    base = Color(0xFF0F172A),
                )
            } else {
                ActiveGlowPalette(
                    top = Color(0xFF7DD3FC),
                    center = Color(0xFFF472B6),
                    bottom = Color(0xFFC4B5FD),
                    accent = Color(0xFFA5B4FC),
                    dominant = Color(0xFF818CF8),
                    base = Color(0xFFF8FAFC),
                )
            }
        } else {
            if (isDark) {
                val topColor = tuneColor(palette.top, saturation = 0.85f, lightness = 0.60f)
                val centerColor = tuneColor(palette.center, saturation = 0.90f, lightness = 0.58f)
                val bottomColor = tuneColor(palette.bottom, saturation = 0.85f, lightness = 0.56f)
                val accentColor = tuneColor(palette.accent, saturation = 0.88f, lightness = 0.58f)
                val dominantColor =
                    tuneColor(palette.dominant, saturation = 0.88f, lightness = 0.58f)

                val baseSource = palette.dominant
                val baseR = (baseSource.red * 255).toInt()
                val baseG = (baseSource.green * 255).toInt()
                val baseB = (baseSource.blue * 255).toInt()
                val baseHsl = ArtworkColorExtractor.rgbToHsl(baseR, baseG, baseB)
                val baseSat =
                    if (baseHsl[1] < 0.10f) (baseHsl[1] * 0.35f).coerceAtMost(0.35f) else 0.35f
                val baseColor = Color.hsl(baseHsl[0], baseSat, 0.12f)

                ActiveGlowPalette(
                    top = topColor,
                    center = centerColor,
                    bottom = bottomColor,
                    accent = accentColor,
                    dominant = dominantColor,
                    base = baseColor,
                )
            } else {
                val topColor = tuneColor(palette.top, saturation = 0.75f, lightness = 0.76f)
                val centerColor = tuneColor(palette.center, saturation = 0.80f, lightness = 0.74f)
                val bottomColor = tuneColor(palette.bottom, saturation = 0.75f, lightness = 0.72f)
                val accentColor = tuneColor(palette.accent, saturation = 0.78f, lightness = 0.75f)
                val dominantColor =
                    tuneColor(palette.dominant, saturation = 0.78f, lightness = 0.74f)

                val baseSource = palette.dominant
                val baseR = (baseSource.red * 255).toInt()
                val baseG = (baseSource.green * 255).toInt()
                val baseB = (baseSource.blue * 255).toInt()
                val baseHsl = ArtworkColorExtractor.rgbToHsl(baseR, baseG, baseB)
                val baseSat =
                    if (baseHsl[1] < 0.10f) (baseHsl[1] * 0.20f).coerceAtMost(0.20f) else 0.20f
                val baseColor = Color.hsl(baseHsl[0], baseSat, 0.94f)

                ActiveGlowPalette(
                    top = topColor,
                    center = centerColor,
                    bottom = bottomColor,
                    accent = accentColor,
                    dominant = dominantColor,
                    base = baseColor,
                )
            }
        }
    }

    val animatedTop by animateColorAsState(
        targetValue = targetPalette.top,
        animationSpec = tween(900),
        label = "glow_top",
    )
    val animatedCenter by animateColorAsState(
        targetValue = targetPalette.center,
        animationSpec = tween(900),
        label = "glow_center",
    )
    val animatedBottom by animateColorAsState(
        targetValue = targetPalette.bottom,
        animationSpec = tween(900),
        label = "glow_bottom",
    )
    val animatedAccent by animateColorAsState(
        targetValue = targetPalette.accent,
        animationSpec = tween(900),
        label = "glow_accent",
    )
    val animatedDominant by animateColorAsState(
        targetValue = targetPalette.dominant,
        animationSpec = tween(900),
        label = "glow_dominant",
    )
    val animatedBase by animateColorAsState(
        targetValue = targetPalette.base,
        animationSpec = tween(900),
        label = "glow_base",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "glow_drift_transition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glow_drift_phase",
    )

    val beatPulse = if (isPlaying) spectrum.beatPulse.coerceIn(0f, 1f) else 0f
    val bass = if (isPlaying) spectrum.bass.coerceIn(0f, 1f) else 0f
    val mid = if (isPlaying) spectrum.mid.coerceIn(0f, 1f) else 0f
    val treble = if (isPlaying) spectrum.treble.coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBase),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(56.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas

                drawRect(color = animatedBase)

                val rad = Math.toRadians(phase.toDouble()).toFloat()
                val kick = beatPulse * 28.dp.toPx() + bass * 14.dp.toPx()
                val snare = beatPulse * 16.dp.toPx() + treble * 10.dp.toPx()

                val orb1Center = Offset(
                    x = size.width * (0.50f + 0.18f * cos(rad * 0.8f)) + kick * 0.15f * sin(rad),
                    y = size.height * (0.11f + 0.04f * sin(rad * 0.6f)) - kick * 0.35f,
                )
                val orb1Radius = max(1f, size.width * (0.75f + 0.30f * beatPulse + 0.20f * treble))
                val orb1Alpha = (0.55f + 0.30f * beatPulse).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedTop.copy(alpha = orb1Alpha),
                            animatedTop.copy(alpha = orb1Alpha * 0.50f),
                            Color.Transparent,
                        ),
                        center = orb1Center,
                        radius = orb1Radius,
                    ),
                    radius = orb1Radius,
                    center = orb1Center,
                )

                val orb2Center = Offset(
                    x = size.width * (0.14f + 0.06f * sin(rad * 0.7f)) - kick * 0.55f,
                    y = size.height * (0.38f + 0.06f * cos(rad * 0.9f)) + snare * 0.30f,
                )
                val orb2Radius = max(1f, size.width * (0.70f + 0.35f * beatPulse + 0.25f * bass))
                val orb2Alpha = (0.50f + 0.30f * beatPulse).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedCenter.copy(alpha = orb2Alpha),
                            animatedCenter.copy(alpha = orb2Alpha * 0.50f),
                            Color.Transparent,
                        ),
                        center = orb2Center,
                        radius = orb2Radius,
                    ),
                    radius = orb2Radius,
                    center = orb2Center,
                )

                val orb3Center = Offset(
                    x = size.width * (0.86f + 0.06f * cos(rad * 0.9f + 1.2f)) + kick * 0.55f,
                    y = size.height * (0.42f + 0.05f * sin(rad * 0.8f + 2.0f)) - snare * 0.30f,
                )
                val orb3Radius = max(1f, size.width * (0.72f + 0.35f * beatPulse + 0.20f * mid))
                val orb3Alpha = (0.50f + 0.30f * beatPulse).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedAccent.copy(alpha = orb3Alpha),
                            animatedAccent.copy(alpha = orb3Alpha * 0.50f),
                            Color.Transparent,
                        ),
                        center = orb3Center,
                        radius = orb3Radius,
                    ),
                    radius = orb3Radius,
                    center = orb3Center,
                )

                val orb4Center = Offset(
                    x = size.width * (0.36f + 0.10f * cos(rad * 1.1f + 2.4f)) - kick * 0.25f,
                    y = size.height * (0.66f + 0.04f * sin(rad * 0.7f + 1.0f)) + kick * 0.35f,
                )
                val orb4Radius = max(1f, size.width * (0.75f + 0.35f * beatPulse + 0.25f * bass))
                val orb4Alpha = (0.52f + 0.30f * beatPulse).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedCenter.copy(alpha = orb4Alpha),
                            animatedCenter.copy(alpha = orb4Alpha * 0.50f),
                            Color.Transparent,
                        ),
                        center = orb4Center,
                        radius = orb4Radius,
                    ),
                    radius = orb4Radius,
                    center = orb4Center,
                )

                val orb5Center = Offset(
                    x = size.width * (0.65f + 0.10f * sin(rad * 0.8f + 3.14f)) + kick * 0.25f,
                    y = size.height * (0.85f + 0.04f * cos(rad * 1.0f)) + kick * 0.45f,
                )
                val orb5Radius = max(1f, size.width * (0.75f + 0.35f * beatPulse + 0.25f * bass))
                val orb5Alpha = (0.52f + 0.30f * beatPulse).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedBottom.copy(alpha = orb5Alpha),
                            animatedBottom.copy(alpha = orb5Alpha * 0.50f),
                            Color.Transparent,
                        ),
                        center = orb5Center,
                        radius = orb5Radius,
                    ),
                    radius = orb5Radius,
                    center = orb5Center,
                )

                val orb6Center = Offset(
                    x = size.width * (0.50f + 0.05f * cos(rad * 0.5f)),
                    y = size.height * (0.50f + 0.04f * sin(rad * 0.5f)) - kick * 0.20f,
                )
                val orb6Radius = max(1f, size.width * (0.85f + 0.35f * beatPulse + 0.25f * bass))
                val orb6Alpha = (0.55f + 0.30f * beatPulse).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedDominant.copy(alpha = orb6Alpha),
                            animatedDominant.copy(alpha = orb6Alpha * 0.50f),
                            Color.Transparent,
                        ),
                        center = orb6Center,
                        radius = orb6Radius,
                    ),
                    radius = orb6Radius,
                    center = orb6Center,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDark) {
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.20f),
                            0.25f to Color.Black.copy(alpha = 0.06f),
                            0.65f to Color.Black.copy(alpha = 0.15f),
                            1.0f to Color.Black.copy(alpha = 0.30f),
                        )
                    } else {
                        Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.25f),
                            0.25f to Color.White.copy(alpha = 0.06f),
                            0.65f to Color.White.copy(alpha = 0.18f),
                            1.0f to Color.White.copy(alpha = 0.30f),
                        )
                    },
                ),
        )
    }
}
