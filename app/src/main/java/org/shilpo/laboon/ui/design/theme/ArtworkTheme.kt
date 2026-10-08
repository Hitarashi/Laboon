package org.shilpo.laboon.ui.design.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class ArtworkSpatialPalette(
    val top: Color,
    val center: Color,
    val bottom: Color,
    val accent: Color,
    val dominant: Color,
)

object ArtworkColorExtractor {
    private val memoryCache = LruCache<String, Color>(50)
    private val spatialCache = LruCache<String, ArtworkSpatialPalette>(50)

    fun getCachedSpatialPalette(artworkUrl: String?): ArtworkSpatialPalette? {
        if (artworkUrl.isNullOrBlank()) return null
        return spatialCache[artworkUrl]
    }

    suspend fun extractSpatialPalette(
        context: Context,
        artworkUrl: String?
    ): ArtworkSpatialPalette? {
        if (artworkUrl.isNullOrBlank()) return null

        val cached = spatialCache[artworkUrl]
        if (cached != null) return cached

        return withContext(Dispatchers.IO) {
            try {
                val imageLoader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(64, 64)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.image as? BitmapImage)?.bitmap ?: return@withContext null
                    val scaled = if (bitmap.width != 48 || bitmap.height != 48) {
                        Bitmap.createScaledBitmap(bitmap, 48, 48, false)
                    } else bitmap
                    val allPixels = IntArray(48 * 48)
                    scaled.getPixels(allPixels, 0, 48, 0, 0, 48, 48)

                    val topPixels = allPixels.copyOfRange(0, 48 * 16)
                    val centerPixels = allPixels.copyOfRange(48 * 16, 48 * 32)
                    val bottomPixels = allPixels.copyOfRange(48 * 32, 48 * 48)

                    val top = extractSeedFromPixels(topPixels) ?: averageColorFromPixels(topPixels)
                    val center =
                        extractSeedFromPixels(centerPixels) ?: averageColorFromPixels(centerPixels)
                    val bottom =
                        extractSeedFromPixels(bottomPixels) ?: averageColorFromPixels(bottomPixels)

                    val dominant = extractSeedFromPixels(allPixels) ?: center
                    val dominantHsl = rgbToHsl(
                        (dominant.red * 255).toInt(),
                        (dominant.green * 255).toInt(),
                        (dominant.blue * 255).toInt(),
                    )
                    val dominantHue = dominantHsl[0]

                    val accent = extractAccentFromPixels(allPixels, dominantHue, top, bottom)

                    val palette = ArtworkSpatialPalette(
                        top = top,
                        center = center,
                        bottom = bottom,
                        accent = accent,
                        dominant = dominant,
                    )
                    spatialCache.put(artworkUrl, palette)
                    palette
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun averageColorFromPixels(pixels: IntArray): Color {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xFF
            if (a < 128) continue
            rSum += (pixel ushr 16) and 0xFF
            gSum += (pixel ushr 8) and 0xFF
            bSum += pixel and 0xFF
            count++
        }
        return if (count > 0) {
            Color(
                red = (rSum / count).toFloat() / 255f,
                green = (gSum / count).toFloat() / 255f,
                blue = (bSum / count).toFloat() / 255f,
            )
        } else {
            Color.Gray
        }
    }

    private fun extractAccentFromPixels(
        pixels: IntArray,
        dominantHue: Float,
        fallbackTop: Color,
        fallbackBottom: Color,
    ): Color {
        val binScores = FloatArray(36)
        val binHueSum = FloatArray(36)
        val binSatSum = FloatArray(36)

        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xFF
            if (a < 128) continue
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            val hsl = rgbToHsl(r, g, b)
            val h = hsl[0]
            val s = hsl[1]
            val l = hsl[2]

            if (s < 0.15f || l < 0.10f || l > 0.90f) continue

            val weight = s * (1f - abs(2f * l - 1f))
            val binIndex = ((h / 10f).toInt()).coerceIn(0, 35)

            binScores[binIndex] += weight
            binHueSum[binIndex] += h * weight
            binSatSum[binIndex] += s * weight
        }

        var secondBestBin = -1
        var secondMaxScore = 0f
        for (i in 0 until 36) {
            if (binScores[i] <= 0f) continue
            val binHue = binHueSum[i] / binScores[i]
            val diff = abs(binHue - dominantHue) % 360f
            val dist = if (diff > 180f) 360f - diff else diff
            if (dist >= 30f && binScores[i] > secondMaxScore) {
                secondMaxScore = binScores[i]
                secondBestBin = i
            }
        }

        if (secondBestBin != -1 && secondMaxScore > 0f) {
            val accentHue = binHueSum[secondBestBin] / binScores[secondBestBin]
            val accentSat =
                (binSatSum[secondBestBin] / binScores[secondBestBin]).coerceIn(0.40f, 0.90f)
            return Color.hsl(accentHue, accentSat, 0.50f)
        }

        val topHsl = rgbToHsl(
            (fallbackTop.red * 255).toInt(),
            (fallbackTop.green * 255).toInt(),
            (fallbackTop.blue * 255).toInt(),
        )
        val topDiff = abs(topHsl[0] - dominantHue) % 360f
        val topDist = if (topDiff > 180f) 360f - topDiff else topDiff
        return if (topDist >= 20f) fallbackTop else fallbackBottom
    }

    suspend fun extractSeedColor(context: Context, artworkUrl: String?): Color? {
        if (artworkUrl.isNullOrBlank()) return null

        val cached = memoryCache[artworkUrl]
        if (cached != null) return cached

        return withContext(Dispatchers.IO) {
            try {
                val imageLoader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(64, 64)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.image as? BitmapImage)?.bitmap ?: return@withContext null
                    val scaled = if (bitmap.width > 48 || bitmap.height > 48) {
                        Bitmap.createScaledBitmap(bitmap, 48, 48, false)
                    } else bitmap
                    val pixels = IntArray(scaled.width * scaled.height)
                    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                    val seed = extractSeedFromPixels(pixels)
                    if (seed != null) {
                        memoryCache.put(artworkUrl, seed)
                    }
                    seed
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun extractSeedFromPixels(pixels: IntArray): Color? {
        val binScores = FloatArray(36)
        val binHueSum = FloatArray(36)
        val binSatSum = FloatArray(36)

        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xFF
            if (a < 128) continue
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            val hsl = rgbToHsl(r, g, b)
            val h = hsl[0]
            val s = hsl[1]
            val l = hsl[2]

            if (s < 0.15f || l < 0.10f || l > 0.90f) continue

            val weight = s * (1f - abs(2f * l - 1f))
            val binIndex = ((h / 10f).toInt()).coerceIn(0, 35)

            binScores[binIndex] += weight
            binHueSum[binIndex] += h * weight
            binSatSum[binIndex] += s * weight
        }

        var bestBin = -1
        var maxScore = 0f
        for (i in 0 until 36) {
            if (binScores[i] > maxScore) {
                maxScore = binScores[i]
                bestBin = i
            }
        }

        if (bestBin == -1 || maxScore <= 0f) return null

        val seedHue = binHueSum[bestBin] / binScores[bestBin]
        val seedSat = (binSatSum[bestBin] / binScores[bestBin]).coerceIn(0.40f, 0.90f)
        return Color.hsl(seedHue, seedSat, 0.50f)
    }

    fun rgbToHsl(red: Int, green: Int, blue: Int): FloatArray {
        val r = red / 255f
        val g = green / 255f
        val b = blue / 255f

        val max = maxOf(r, maxOf(g, b))
        val min = minOf(r, minOf(g, b))
        val delta = max - min

        val l = (max + min) / 2f
        val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))

        val h = when {
            delta == 0f -> 0f
            max == r -> (((g - b) / delta) % 6f) * 60f
            max == g -> (((b - r) / delta) + 2f) * 60f
            else -> (((r - g) / delta) + 4f) * 60f
        }.let { if (it < 0f) it + 360f else it }

        return floatArrayOf(h, s, l)
    }
}

object ArtworkColorSchemeGenerator {
    fun generateColorScheme(seedColor: Color, isDark: Boolean): ColorScheme {
        return runCatching {
            SchemeTonalSpot(Hct.fromInt(seedColor.toArgb()), isDark, 0.0)
                .toComposeColorScheme()
        }.getOrElse {
            if (isDark) darkColorScheme() else lightColorScheme()
        }
    }
}

private fun DynamicScheme.toComposeColorScheme(): ColorScheme = ColorScheme(
    primary = Color(getPrimary()),
    onPrimary = Color(getOnPrimary()),
    primaryContainer = Color(getPrimaryContainer()),
    onPrimaryContainer = Color(getOnPrimaryContainer()),
    inversePrimary = Color(getInversePrimary()),
    secondary = Color(getSecondary()),
    onSecondary = Color(getOnSecondary()),
    secondaryContainer = Color(getSecondaryContainer()),
    onSecondaryContainer = Color(getOnSecondaryContainer()),
    tertiary = Color(getTertiary()),
    onTertiary = Color(getOnTertiary()),
    tertiaryContainer = Color(getTertiaryContainer()),
    onTertiaryContainer = Color(getOnTertiaryContainer()),
    background = Color(getBackground()),
    onBackground = Color(getOnBackground()),
    surface = Color(getSurface()),
    onSurface = Color(getOnSurface()),
    surfaceVariant = Color(getSurfaceVariant()),
    onSurfaceVariant = Color(getOnSurfaceVariant()),
    surfaceTint = Color(getSurfaceTint()),
    inverseSurface = Color(getInverseSurface()),
    inverseOnSurface = Color(getInverseOnSurface()),
    error = Color(getError()),
    onError = Color(getOnError()),
    errorContainer = Color(getErrorContainer()),
    onErrorContainer = Color(getOnErrorContainer()),
    outline = Color(getOutline()),
    outlineVariant = Color(getOutlineVariant()),
    scrim = Color(getScrim()),
    surfaceBright = Color(getSurfaceBright()),
    surfaceDim = Color(getSurfaceDim()),
    surfaceContainer = Color(getSurfaceContainer()),
    surfaceContainerHigh = Color(getSurfaceContainerHigh()),
    surfaceContainerHighest = Color(getSurfaceContainerHighest()),
    surfaceContainerLow = Color(getSurfaceContainerLow()),
    surfaceContainerLowest = Color(getSurfaceContainerLowest()),
    primaryFixed = Color(getPrimaryFixed()),
    primaryFixedDim = Color(getPrimaryFixedDim()),
    onPrimaryFixed = Color(getOnPrimaryFixed()),
    onPrimaryFixedVariant = Color(getOnPrimaryFixedVariant()),
    secondaryFixed = Color(getSecondaryFixed()),
    secondaryFixedDim = Color(getSecondaryFixedDim()),
    onSecondaryFixed = Color(getOnSecondaryFixed()),
    onSecondaryFixedVariant = Color(getOnSecondaryFixedVariant()),
    tertiaryFixed = Color(getTertiaryFixed()),
    tertiaryFixedDim = Color(getTertiaryFixedDim()),
    onTertiaryFixed = Color(getOnTertiaryFixed()),
    onTertiaryFixedVariant = Color(getOnTertiaryFixedVariant()),
)

@Composable
fun animateColorScheme(target: ColorScheme): ColorScheme {
    val progress = remember { Animatable(1f) }
    var fromScheme by remember { mutableStateOf(target) }
    var toScheme by remember { mutableStateOf(target) }

    LaunchedEffect(target) {
        if (toScheme == target) return@LaunchedEffect
        fromScheme = lerpColorScheme(fromScheme, toScheme, progress.value)
        toScheme = target
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
        )
    }

    val interpolated by remember {
        derivedStateOf { lerpColorScheme(fromScheme, toScheme, progress.value) }
    }
    return interpolated
}

private fun lerpColorScheme(from: ColorScheme, to: ColorScheme, fraction: Float): ColorScheme =
    to.copy(
        primary = lerp(from.primary, to.primary, fraction),
        onPrimary = lerp(from.onPrimary, to.onPrimary, fraction),
        primaryContainer = lerp(from.primaryContainer, to.primaryContainer, fraction),
        onPrimaryContainer = lerp(from.onPrimaryContainer, to.onPrimaryContainer, fraction),
        inversePrimary = lerp(from.inversePrimary, to.inversePrimary, fraction),
        primaryFixed = lerp(from.primaryFixed, to.primaryFixed, fraction),
        primaryFixedDim = lerp(from.primaryFixedDim, to.primaryFixedDim, fraction),
        onPrimaryFixed = lerp(from.onPrimaryFixed, to.onPrimaryFixed, fraction),
        onPrimaryFixedVariant = lerp(from.onPrimaryFixedVariant, to.onPrimaryFixedVariant, fraction),
        secondary = lerp(from.secondary, to.secondary, fraction),
        onSecondary = lerp(from.onSecondary, to.onSecondary, fraction),
        secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, fraction),
        onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, fraction),
        secondaryFixed = lerp(from.secondaryFixed, to.secondaryFixed, fraction),
        secondaryFixedDim = lerp(from.secondaryFixedDim, to.secondaryFixedDim, fraction),
        onSecondaryFixed = lerp(from.onSecondaryFixed, to.onSecondaryFixed, fraction),
        onSecondaryFixedVariant = lerp(from.onSecondaryFixedVariant, to.onSecondaryFixedVariant, fraction),
        tertiary = lerp(from.tertiary, to.tertiary, fraction),
        onTertiary = lerp(from.onTertiary, to.onTertiary, fraction),
        tertiaryContainer = lerp(from.tertiaryContainer, to.tertiaryContainer, fraction),
        onTertiaryContainer = lerp(from.onTertiaryContainer, to.onTertiaryContainer, fraction),
        tertiaryFixed = lerp(from.tertiaryFixed, to.tertiaryFixed, fraction),
        tertiaryFixedDim = lerp(from.tertiaryFixedDim, to.tertiaryFixedDim, fraction),
        onTertiaryFixed = lerp(from.onTertiaryFixed, to.onTertiaryFixed, fraction),
        onTertiaryFixedVariant = lerp(from.onTertiaryFixedVariant, to.onTertiaryFixedVariant, fraction),
        background = lerp(from.background, to.background, fraction),
        onBackground = lerp(from.onBackground, to.onBackground, fraction),
        surface = lerp(from.surface, to.surface, fraction),
        onSurface = lerp(from.onSurface, to.onSurface, fraction),
        surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, fraction),
        onSurfaceVariant = lerp(from.onSurfaceVariant, to.onSurfaceVariant, fraction),
        surfaceTint = lerp(from.surfaceTint, to.surfaceTint, fraction),
        inverseSurface = lerp(from.inverseSurface, to.inverseSurface, fraction),
        inverseOnSurface = lerp(from.inverseOnSurface, to.inverseOnSurface, fraction),
        error = lerp(from.error, to.error, fraction),
        onError = lerp(from.onError, to.onError, fraction),
        errorContainer = lerp(from.errorContainer, to.errorContainer, fraction),
        onErrorContainer = lerp(from.onErrorContainer, to.onErrorContainer, fraction),
        outline = lerp(from.outline, to.outline, fraction),
        outlineVariant = lerp(from.outlineVariant, to.outlineVariant, fraction),
        scrim = lerp(from.scrim, to.scrim, fraction),
        surfaceBright = lerp(from.surfaceBright, to.surfaceBright, fraction),
        surfaceDim = lerp(from.surfaceDim, to.surfaceDim, fraction),
        surfaceContainer = lerp(from.surfaceContainer, to.surfaceContainer, fraction),
        surfaceContainerHigh = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, fraction),
        surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, fraction),
        surfaceContainerLow = lerp(from.surfaceContainerLow, to.surfaceContainerLow, fraction),
        surfaceContainerLowest = lerp(from.surfaceContainerLowest, to.surfaceContainerLowest, fraction),
    )
