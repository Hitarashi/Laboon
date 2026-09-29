package org.shilpo.laboon.ui.design.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

const val LINK_BINS: Int = 16

class Renderer(private val tuning: Tuning = Tuning.Default) {
    val starPath = Path()
    private val defaultLoops = listOf(0 until tuning.slots.logo)

    private var cachedDensity = -1f
    private val slotLookup = arrayOfNulls<Particle>(128)

    private var cachedBinsColor: Color = Color.Unspecified
    private val linkBins = Array(LINK_BINS) { Color.Transparent }

    private var cachedSprite1Color: Color = Color.Unspecified
    private var sprite1: ImageBitmap? = null
    private var cachedSprite2Color: Color = Color.Unspecified
    private var sprite2: ImageBitmap? = null

    private class CachedRadialGradient {
        var brush: Brush? = null
        var radius: Float = -1f
        var color: Color = Color.Unspecified
        var alphaBin: Int = -1
        var center: Offset = Offset.Unspecified
    }

    private val tipGlowCaches = Array(8) { CachedRadialGradient() }
    private val flashGlowCache = CachedRadialGradient()

    private var cachedGlowBrush: Brush? = null
    private var cachedGlowRadius: Float = -1f
    private var cachedGlowColor: Color = Color.Unspecified
    private var cachedGlowStrength: Float = -1f

    private var cachedOutlineSrc: android.graphics.Path? = null
    private var cachedOutline: Path? = null

    private fun outlineOf(src: android.graphics.Path): Path? {
        if (src.isEmpty) return null
        if (cachedOutlineSrc !== src || cachedOutline == null) {
            cachedOutlineSrc = src
            cachedOutline = src.asComposePath()
        }
        return cachedOutline
    }

    private fun getBins(contentColor: Color): Array<Color> {
        if (cachedBinsColor != contentColor) {
            cachedBinsColor = contentColor
            for (i in 0 until LINK_BINS) {
                linkBins[i] = contentColor.copy(alpha = (i + 0.5f) / LINK_BINS.toFloat())
            }
        }
        return linkBins
    }

    private fun glowSprite(base: Color): ImageBitmap {
        val size = 128
        val bmp = ImageBitmap(size, size)
        val c = size / 2f
        val alphas = tuning.look.glow.SPRITE_ALPHAS
        val stops = tuning.look.glow.SPRITE_STOPS.toFloatArray()
        val colors = IntArray(alphas.size) { i -> base.copy(alpha = alphas[i]).toArgb() }
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.RadialGradient(
                c, c, c, colors, stops, android.graphics.Shader.TileMode.CLAMP
            )
        }
        android.graphics.Canvas(bmp.asAndroidBitmap())
            .drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bmp
    }

    private fun spriteFor(color: Color): ImageBitmap {
        if (cachedSprite1Color == color && sprite1 != null) return sprite1!!
        if (cachedSprite2Color == color && sprite2 != null) return sprite2!!
        if (sprite1 == null || cachedSprite1Color == Color.Unspecified) {
            cachedSprite1Color = color
            return glowSprite(color).also { sprite1 = it }
        }
        cachedSprite2Color = color
        return glowSprite(color).also { sprite2 = it }
    }

    var linkStrokeWidthPx: Float = 1.5f
        private set
    var shockwaveStroke: Stroke = Stroke(width = 2f)
        private set
    private var outlineStrokeWidthPx: Float = 2.8f

    fun ensureDensity(density: Float) {
        if (cachedDensity != density) {
            cachedDensity = density
            linkStrokeWidthPx = tuning.look.links.LINE_WIDTH_DP * density
            shockwaveStroke = Stroke(width = tuning.burst.STROKE_WIDTH_DP * density)
            outlineStrokeWidthPx = tuning.look.smooth.STROKE_WIDTH_DP * density
        }
    }

    fun DrawScope.drawSmoothOutline(
        engine: Engine,
        contentColor: Color = Color.White
    ) {
        val smooth = tuning.look.smooth
        if (engine.formStrength <= smooth.FORM_THRESHOLD) return
        if (engine.currentPhase != Phase.Gather && engine.currentPhase != Phase.Ignite) return

        val outline = engine.currentShapeData.outlinePath?.let { outlineOf(it) } ?: return
        val appear =
            ((engine.formStrength - smooth.FORM_THRESHOLD) / smooth.APPEAR_RANGE).coerceIn(0f, 1f)
        val alpha = (appear * smooth.ALPHA_BASE * engine.particleAlpha).coerceIn(0f, 1f)
        if (alpha <= tuning.look.cutoffs.FORM_LINKS) return

        drawPath(
            path = outline,
            color = contentColor.copy(alpha = alpha),
            style = Stroke(
                width = outlineStrokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        )
    }

    fun DrawScope.render(
        engine: Engine,
        isDark: Boolean = true,
        contentColor: Color = Color.White,
        primaryColor: Color = Color.White
    ) {
        ensureDensity(density)
        drawFormationGlow(engine, primaryColor)

        drawSmoothOutline(engine, contentColor)
        drawFormationLinks(engine, contentColor)
        drawParticles(engine, contentColor)
        if (engine.currentPhase == Phase.Ignite) {
            drawIgnite(engine, contentColor, primaryColor)
        }

        drawShockwave(
            shockwave = engine.shockwave,
            color = contentColor,
            isDark = isDark
        )
        drawScreenFlash(engine, primaryColor)
    }

    fun DrawScope.drawFormationGlow(
        engine: Engine,
        primaryColor: Color = Color.White
    ) {
        if (engine.formStrength <= tuning.look.cutoffs.FORM_GLOW || size.height <= 0f) return
        val r = size.height * tuning.look.glow.HEIGHT_FACTOR
        if (r <= 0f) return
        val c = center
        val strength = (engine.formStrength * 20f).toInt() / 20f
        if (cachedGlowBrush == null || cachedGlowRadius != r || cachedGlowColor != primaryColor || cachedGlowStrength != strength) {
            cachedGlowRadius = r
            cachedGlowColor = primaryColor
            cachedGlowStrength = strength
            cachedGlowBrush = Brush.radialGradient(
                0.0f to primaryColor.copy(
                    alpha = (strength * tuning.look.glow.STRENGTH_ALPHA_BASE).coerceIn(
                        0f,
                        1f
                    )
                ),
                tuning.look.glow.STOP_MID to primaryColor.copy(
                    alpha = (strength * tuning.look.glow.STRENGTH_ALPHA_MID).coerceIn(
                        0f,
                        1f
                    )
                ),
                1.0f to Color.Transparent,
                center = c,
                radius = r
            )
        }
        cachedGlowBrush?.let { brush ->
            drawCircle(brush = brush, radius = r, center = c)
        }
    }

    fun DrawScope.drawFormationLinks(
        engine: Engine,
        contentColor: Color = Color.White
    ) {
        if (engine.formStrength <= tuning.look.cutoffs.FORM_LINKS ||
            (engine.currentPhase != Phase.Gather && engine.currentPhase != Phase.Ignite)
        ) return

        val boxSize = Slots.boxSize(
            width = engine.width,
            height = engine.height,
            density = density,
            logoTargetSizeDp = tuning.effects.LOGO_TARGET_SIZE_DP,
        )
        val maxDist = maxOf(boxSize * 0.65f, tuning.effects.LINK_DISTANCE_DP.dp.toPx())
        if (maxDist <= 0f) return

        val maxDistSq = maxDist * maxDist
        val invMaxDist = 1f / maxDist

        val bins = getBins(contentColor)

        for (j in slotLookup.indices) slotLookup[j] = null
        for (p in engine.particles) {
            if (p.isMember && p.slotIndex >= 0 && p.slotIndex < slotLookup.size) {
                slotLookup[p.slotIndex] = p
            }
        }

        val loops = engine.currentShapeData.loops.ifEmpty { defaultLoops }
        val slotCount =
            if (engine.currentShapeData.slots.isNotEmpty()) engine.currentShapeData.slots.size else tuning.slots.logo
        for (range in loops) {
            val count = range.count()
            for (i in 0 until count) {
                val idx1 = range.first + i
                val idx2 = range.first + ((i + 1) % count)
                if (idx1 !in slotLookup.indices || idx2 !in slotLookup.indices) continue
                val p1 = slotLookup[idx1] ?: continue
                val p2 = slotLookup[idx2] ?: continue

                val dx1 = p1.x - p1.targetX
                val dy1 = p1.y - p1.targetY
                val dist1Sq = dx1 * dx1 + dy1 * dy1

                val dx2 = p2.x - p2.targetX
                val dy2 = p2.y - p2.targetY
                val dist2Sq = dx2 * dx2 + dy2 * dy2

                val conv1 = if (dist1Sq < maxDistSq) max(
                    0f,
                    1f - kotlin.math.sqrt(dist1Sq) * invMaxDist
                ) else 0f
                val conv2 = if (dist2Sq < maxDistSq) max(
                    0f,
                    1f - kotlin.math.sqrt(dist2Sq) * invMaxDist
                ) else 0f
                val appearFactor =
                    ((engine.formStrength - tuning.look.links.APPEAR_MIN_FORM) / tuning.look.links.APPEAR_RANGE).coerceIn(
                        0f,
                        1f
                    )
                val rawAlpha =
                    appearFactor * min(conv1, conv2) * tuning.look.links.RAW_ALPHA_FACTOR

                if (rawAlpha > tuning.look.cutoffs.RAW_ALPHA_LINKS) {
                    val segNorm = (idx1 + 0.5f) / slotCount.toFloat()
                    var waveDist = abs(segNorm - engine.pulseWave)
                    if (waveDist > 0.5f) waveDist = 1.0f - waveDist
                    val waveBoost =
                        max(0f, 1f - waveDist / tuning.look.links.WAVE_DIST_FACTOR).pow(2)

                    val bin =
                        ((rawAlpha + waveBoost * tuning.look.links.WAVE_BOOST_BIN).coerceIn(
                            0f,
                            1f
                        ) * LINK_BINS).toInt().coerceIn(0, LINK_BINS - 1)
                    val lineCol = bins[bin]
                    val lineAlpha =
                        min(1f, rawAlpha + waveBoost * tuning.look.links.WAVE_BOOST_ALPHA)

                    drawLine(
                        color = lineCol.copy(alpha = lineAlpha),
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = linkStrokeWidthPx * (1f + waveBoost * tuning.look.links.WAVE_BOOST_WIDTH)
                    )
                }
            }
        }
    }

    fun DrawScope.drawParticles(
        engine: Engine,
        contentColor: Color = Color.White
    ) {
        val particles = engine.particles
        val slotCount =
            if (engine.currentShapeData.slots.isNotEmpty()) engine.currentShapeData.slots.size else tuning.slots.logo
        val maxHalo = tuning.effects.MAX_HALO_DP.dp.toPx()
        val sprite = spriteFor(contentColor)

        for (i in particles.indices) {
            val p = particles[i]
            val glow = engine.formStrength.coerceIn(0f, 1f)
            val memberAlpha =
                tuning.look.halo.MEMBER_ALPHA_BASE * (tuning.look.halo.GLOW_MIX_BASE + tuning.look.halo.GLOW_MIX_FACTOR * glow)
            val floaterAlpha =
                tuning.look.halo.FLOATER_ALPHA_BASE * p.depth * p.lum * (1f - tuning.look.halo.GLOW_MIX_FACTOR * glow)
            val baseAlpha = if (p.isMember) memberAlpha else floaterAlpha
            val alpha = (baseAlpha * engine.particleAlpha).coerceIn(0f, 1f)

            if (alpha > tuning.look.cutoffs.PARTICLE_ALPHA && p.radius > 0f) {
                var waveBoost = 0f
                if (p.isMember && p.slotIndex >= 0 && glow > 0.5f) {
                    val slotNorm = p.slotIndex / slotCount.toFloat()
                    var waveDist = abs(slotNorm - engine.pulseWave)
                    if (waveDist > 0.5f) waveDist = 1.0f - waveDist
                    waveBoost = max(0f, 1f - waveDist / 0.14f).pow(2)
                }

                val center = Offset(p.x, p.y)
                val currentRadius = p.radius * (1f + waveBoost * 0.75f)

                val vOuter =
                    (currentRadius * (tuning.look.smooth.PARTICLE_OUTER_SCALE + p.depth * tuning.look.smooth.PARTICLE_OUTER_DEPTH)).coerceAtMost(
                        maxHalo
                    )
                drawImage(
                    image = sprite,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(128, 128),
                    dstOffset = IntOffset(
                        (center.x - vOuter / 2f).roundToInt(),
                        (center.y - vOuter / 2f).roundToInt()
                    ),
                    dstSize = IntSize(vOuter.roundToInt(), vOuter.roundToInt()),
                    alpha = min(1f, alpha * tuning.look.halo.OUTER_SPRITE_ALPHA)
                )

                val vMid = (currentRadius * 4.5f).coerceAtMost(maxHalo)
                drawImage(
                    image = sprite,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(128, 128),
                    dstOffset = IntOffset(
                        (center.x - vMid / 2f).roundToInt(),
                        (center.y - vMid / 2f).roundToInt()
                    ),
                    dstSize = IntSize(vMid.roundToInt(), vMid.roundToInt()),
                    alpha = min(1f, alpha * 1.0f)
                )

                drawCircle(
                    color = contentColor.copy(alpha = min(1f, alpha * 1.0f)),
                    radius = currentRadius * 0.7f,
                    center = center
                )
            }
        }
    }

    fun DrawScope.drawFourPointStar(
        cx: Float,
        cy: Float,
        radius: Float,
        alpha: Float,
        color: Color = Color.White,
        haloColor: Color = Color.White
    ) {
        if (alpha <= tuning.look.cutoffs.STAR_ALPHA || radius <= 0f) return

        val center = Offset(cx, cy)
        val haloRadius = radius * tuning.look.halo.STAR_BODY_HALO_FACTOR
        val vStarHalo = (haloRadius * 2f).roundToInt()
        val sprite = spriteFor(haloColor)
        drawImage(
            image = sprite,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(128, 128),
            dstOffset = IntOffset(
                (center.x - vStarHalo / 2f).roundToInt(),
                (center.y - vStarHalo / 2f).roundToInt()
            ),
            dstSize = IntSize(vStarHalo, vStarHalo),
            alpha = (alpha * tuning.look.halo.STAR_SPRITE_ALPHA).coerceIn(0f, 1f)
        )

        starPath.reset()
        val inner = radius * 0.08f
        starPath.moveTo(cx, cy - radius)
        starPath.quadraticTo(cx + inner, cy - inner, cx + radius, cy)
        starPath.quadraticTo(cx + inner, cy + inner, cx, cy + radius)
        starPath.quadraticTo(cx - inner, cy + inner, cx - radius, cy)
        starPath.quadraticTo(cx - inner, cy - inner, cx, cy - radius)
        starPath.close()

        drawPath(
            path = starPath,
            color = color.copy(alpha = alpha.coerceIn(0f, 1f))
        )
    }

    fun DrawScope.drawIgnite(
        engine: Engine,
        contentColor: Color = Color.White,
        primaryColor: Color = Color.White
    ) {
        val elapsed = engine.phaseElapsedMs
        val limit = engine.igniteWindowMs
        val stagger = engine.starStaggerMs
        val window = limit * 0.85f
        val starBase = size.height * tuning.look.halo.STAR_BASE_HEIGHT_FACTOR
        val haloRadius = starBase * tuning.look.halo.STAR_HALO_FACTOR
        val tips = engine.currentShapeData.tips
        for (d in tips.indices) {
            val p = ((elapsed - d * stagger) / window).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) continue
            val flare = sin(p * Math.PI.toFloat())
            val tip = tips[d]
            val center = Offset(tip.x, tip.y)
            if (haloRadius > 0f) {
                val rawAlpha = (flare * tuning.look.halo.STAR_FLARE_ALPHA).coerceIn(0f, 1f)
                val alphaBin = (rawAlpha * 20f).toInt()
                if (alphaBin > 0) {
                    val cache = if (d in tipGlowCaches.indices) tipGlowCaches[d] else null
                    val haloBrush = if (cache != null) {
                        if (cache.brush == null || cache.radius != haloRadius || cache.color != primaryColor || cache.alphaBin != alphaBin || cache.center != center) {
                            cache.radius = haloRadius
                            cache.color = primaryColor
                            cache.alphaBin = alphaBin
                            cache.center = center
                            cache.brush = Brush.radialGradient(
                                0.0f to primaryColor.copy(alpha = alphaBin / 20f),
                                1.0f to Color.Transparent,
                                center = center,
                                radius = haloRadius
                            )
                        }
                        cache.brush!!
                    } else {
                        Brush.radialGradient(
                            0.0f to primaryColor.copy(alpha = rawAlpha),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = haloRadius
                        )
                    }
                    drawCircle(
                        brush = haloBrush,
                        radius = haloRadius,
                        center = center
                    )
                }
            }
            drawFourPointStar(
                cx = tip.x,
                cy = tip.y,
                radius = starBase * flare,
                alpha = flare,
                color = contentColor,
                haloColor = primaryColor
            )
        }
    }

    fun DrawScope.drawShockwave(
        shockwave: Shockwave?,
        color: Color = Color.White,
        isDark: Boolean = true
    ) {
        val sw = shockwave ?: return
        if (sw.radius <= 0f || sw.maxRadius <= 0f) return
        val progress = (sw.radius / sw.maxRadius).coerceIn(0f, 1f)
        val effectiveAlpha = (sw.alpha * (1f - progress)).coerceIn(0f, 1f)
        drawCircle(
            color = color.copy(alpha = effectiveAlpha),
            radius = sw.radius,
            center = Offset(sw.x, sw.y),
            style = shockwaveStroke,
            blendMode = if (isDark) BlendMode.Screen else BlendMode.SrcOver
        )
    }

    fun DrawScope.drawScreenFlash(
        engine: Engine,
        primaryColor: Color = Color.White
    ) {
        if (engine.currentPhase != Phase.Burst ||
            engine.phaseElapsedMs > tuning.look.flash.DURATION_MS
        ) return
        val progress =
            (engine.phaseElapsedMs / tuning.look.flash.DURATION_MS).coerceIn(0f, 1f)
        val alpha = tuning.look.flash.MAX_ALPHA * (1f - progress)
        if (alpha <= tuning.look.cutoffs.FLASH_ALPHA) return
        val radius = tuning.look.flash.RADIUS_DP.dp.toPx()
        val alphaBin = (alpha * 20f).toInt()
        val c = center
        if (alphaBin > 0) {
            val brush =
                if (flashGlowCache.brush == null || flashGlowCache.radius != radius || flashGlowCache.color != primaryColor || flashGlowCache.alphaBin != alphaBin || flashGlowCache.center != c) {
                    flashGlowCache.radius = radius
                    flashGlowCache.color = primaryColor
                    flashGlowCache.alphaBin = alphaBin
                    flashGlowCache.center = c
                    Brush.radialGradient(
                        0.0f to primaryColor.copy(alpha = alphaBin / 20f),
                        1.0f to Color.Transparent,
                        center = c,
                        radius = radius,
                    ).also { flashGlowCache.brush = it }
                } else {
                    flashGlowCache.brush!!
                }
            drawCircle(
                brush = brush,
                radius = radius,
                center = c,
            )
        }
    }
}
