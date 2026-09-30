package org.shilpo.laboon.ui.design.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

object ArtworkColorExtractor {
    private val memoryCache = LruCache<String, Color>(50)

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
        val r = (seedColor.red * 255).toInt()
        val g = (seedColor.green * 255).toInt()
        val b = (seedColor.blue * 255).toInt()
        val hsl = ArtworkColorExtractor.rgbToHsl(r, g, b)
        val h = hsl[0]
        val s = hsl[1].coerceIn(0.25f, 0.85f)
        val sSec = s * 0.40f
        val hTert = (h + 60f) % 360f
        val sTert = s * 0.60f
        val sNeut = (s * 0.08f).coerceAtMost(0.12f)
        val sNeutVar = (s * 0.16f).coerceAtMost(0.20f)

        return if (isDark) {
            val primary = Color.hsl(h, s, 0.80f)
            darkColorScheme(
                primary = primary,
                onPrimary = Color.hsl(h, s, 0.20f),
                primaryContainer = Color.hsl(h, s, 0.30f),
                onPrimaryContainer = Color.hsl(h, s, 0.90f),
                inversePrimary = Color.hsl(h, s, 0.40f),
                secondary = Color.hsl(h, sSec, 0.80f),
                onSecondary = Color.hsl(h, sSec, 0.20f),
                secondaryContainer = Color.hsl(h, sSec, 0.30f),
                onSecondaryContainer = Color.hsl(h, sSec, 0.90f),
                tertiary = Color.hsl(hTert, sTert, 0.80f),
                onTertiary = Color.hsl(hTert, sTert, 0.20f),
                tertiaryContainer = Color.hsl(hTert, sTert, 0.30f),
                onTertiaryContainer = Color.hsl(hTert, sTert, 0.90f),
                background = Color.hsl(h, sNeut, 0.06f),
                onBackground = Color.hsl(h, sNeut, 0.90f),
                surface = Color.hsl(h, sNeut, 0.06f),
                onSurface = Color.hsl(h, sNeut, 0.90f),
                surfaceVariant = Color.hsl(h, sNeutVar, 0.25f),
                onSurfaceVariant = Color.hsl(h, sNeutVar, 0.80f),
                surfaceTint = primary,
                inverseSurface = Color.hsl(h, sNeut, 0.90f),
                inverseOnSurface = Color.hsl(h, sNeut, 0.20f),
                surfaceDim = Color.hsl(h, sNeut, 0.06f),
                surfaceBright = Color.hsl(h, sNeut, 0.24f),
                surfaceContainerLowest = Color.hsl(h, sNeut, 0.04f),
                surfaceContainerLow = Color.hsl(h, sNeut, 0.10f),
                surfaceContainer = Color.hsl(h, sNeut, 0.12f),
                surfaceContainerHigh = Color.hsl(h, sNeut, 0.17f),
                surfaceContainerHighest = Color.hsl(h, sNeut, 0.22f),
                outline = Color.hsl(h, sNeutVar, 0.60f),
                outlineVariant = Color.hsl(h, sNeutVar, 0.30f),
                scrim = Color(0xFF000000),
            )
        } else {
            val primary = Color.hsl(h, s, 0.40f)
            lightColorScheme(
                primary = primary,
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color.hsl(h, s * 0.70f, 0.90f),
                onPrimaryContainer = Color.hsl(h, s, 0.10f),
                inversePrimary = Color.hsl(h, s, 0.80f),
                secondary = Color.hsl(h, sSec, 0.40f),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color.hsl(h, sSec * 0.70f, 0.90f),
                onSecondaryContainer = Color.hsl(h, sSec, 0.10f),
                tertiary = Color.hsl(hTert, sTert, 0.40f),
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color.hsl(hTert, sTert * 0.70f, 0.90f),
                onTertiaryContainer = Color.hsl(hTert, sTert, 0.10f),
                background = Color.hsl(h, sNeut, 0.98f),
                onBackground = Color.hsl(h, sNeut, 0.10f),
                surface = Color.hsl(h, sNeut, 0.98f),
                onSurface = Color.hsl(h, sNeut, 0.10f),
                surfaceVariant = Color.hsl(h, sNeutVar, 0.90f),
                onSurfaceVariant = Color.hsl(h, sNeutVar, 0.30f),
                surfaceTint = primary,
                inverseSurface = Color.hsl(h, sNeut, 0.20f),
                inverseOnSurface = Color.hsl(h, sNeut, 0.95f),
                surfaceDim = Color.hsl(h, sNeut, 0.87f),
                surfaceBright = Color.hsl(h, sNeut, 0.98f),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color.hsl(h, sNeut, 0.96f),
                surfaceContainer = Color.hsl(h, sNeut, 0.94f),
                surfaceContainerHigh = Color.hsl(h, sNeut, 0.92f),
                surfaceContainerHighest = Color.hsl(h, sNeut, 0.90f),
                outline = Color.hsl(h, sNeutVar, 0.50f),
                outlineVariant = Color.hsl(h, sNeutVar, 0.80f),
                scrim = Color(0xFF000000),
            )
        }
    }
}

@Composable
fun animateColorScheme(target: ColorScheme): ColorScheme {
    val primary by animateColorAsState(target.primary, label = "color_primary")
    val onPrimary by animateColorAsState(target.onPrimary, label = "color_onPrimary")
    val primaryContainer by animateColorAsState(
        target.primaryContainer,
        label = "color_primaryContainer"
    )
    val onPrimaryContainer by animateColorAsState(
        target.onPrimaryContainer,
        label = "color_onPrimaryContainer"
    )
    val inversePrimary by animateColorAsState(target.inversePrimary, label = "color_inversePrimary")
    val secondary by animateColorAsState(target.secondary, label = "color_secondary")
    val onSecondary by animateColorAsState(target.onSecondary, label = "color_onSecondary")
    val secondaryContainer by animateColorAsState(
        target.secondaryContainer,
        label = "color_secondaryContainer"
    )
    val onSecondaryContainer by animateColorAsState(
        target.onSecondaryContainer,
        label = "color_onSecondaryContainer"
    )
    val tertiary by animateColorAsState(target.tertiary, label = "color_tertiary")
    val onTertiary by animateColorAsState(target.onTertiary, label = "color_onTertiary")
    val tertiaryContainer by animateColorAsState(
        target.tertiaryContainer,
        label = "color_tertiaryContainer"
    )
    val onTertiaryContainer by animateColorAsState(
        target.onTertiaryContainer,
        label = "color_onTertiaryContainer"
    )
    val background by animateColorAsState(target.background, label = "color_background")
    val onBackground by animateColorAsState(target.onBackground, label = "color_onBackground")
    val surface by animateColorAsState(target.surface, label = "color_surface")
    val onSurface by animateColorAsState(target.onSurface, label = "color_onSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, label = "color_surfaceVariant")
    val onSurfaceVariant by animateColorAsState(
        target.onSurfaceVariant,
        label = "color_onSurfaceVariant"
    )
    val surfaceTint by animateColorAsState(target.surfaceTint, label = "color_surfaceTint")
    val inverseSurface by animateColorAsState(target.inverseSurface, label = "color_inverseSurface")
    val inverseOnSurface by animateColorAsState(
        target.inverseOnSurface,
        label = "color_inverseOnSurface"
    )
    val outline by animateColorAsState(target.outline, label = "color_outline")
    val outlineVariant by animateColorAsState(target.outlineVariant, label = "color_outlineVariant")
    val scrim by animateColorAsState(target.scrim, label = "color_scrim")
    val surfaceBright by animateColorAsState(target.surfaceBright, label = "color_surfaceBright")
    val surfaceDim by animateColorAsState(target.surfaceDim, label = "color_surfaceDim")
    val surfaceContainer by animateColorAsState(
        target.surfaceContainer,
        label = "color_surfaceContainer"
    )
    val surfaceContainerHigh by animateColorAsState(
        target.surfaceContainerHigh,
        label = "color_surfaceContainerHigh"
    )
    val surfaceContainerHighest by animateColorAsState(
        target.surfaceContainerHighest,
        label = "color_surfaceContainerHighest"
    )
    val surfaceContainerLow by animateColorAsState(
        target.surfaceContainerLow,
        label = "color_surfaceContainerLow"
    )
    val surfaceContainerLowest by animateColorAsState(
        target.surfaceContainerLowest,
        label = "color_surfaceContainerLowest"
    )
    val error by animateColorAsState(target.error, label = "color_error")
    val onError by animateColorAsState(target.onError, label = "color_onError")
    val errorContainer by animateColorAsState(target.errorContainer, label = "color_errorContainer")
    val onErrorContainer by animateColorAsState(
        target.onErrorContainer,
        label = "color_onErrorContainer"
    )

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
    )
}
