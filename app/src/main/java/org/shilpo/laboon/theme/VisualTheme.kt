package org.shilpo.laboon.theme

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.theme.contract.ThemeTextStyle
import org.shilpo.laboon.theme.renderer.LocalThemeIconOverrides

internal val LocalVisualMotionScale = staticCompositionLocalOf { 1f }

@Composable
internal fun LaboonExpressiveTheme(
    theme: InstalledVisualTheme?,
    artworkUrl: String? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    var artworkSeed by androidx.compose.runtime.remember(theme?.manifest?.id) {
        androidx.compose.runtime.mutableStateOf<Color?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(theme?.definition?.colorSource, artworkUrl) {
        artworkSeed =
            if (theme?.definition?.colorSource == org.shilpo.laboon.theme.contract.ThemeColorSource.ARTWORK) {
                org.shilpo.laboon.ui.design.theme.ArtworkColorExtractor.extractSeedColor(
                    context,
                    artworkUrl
                )
            } else null
    }
    val systemScheme = when {
        artworkSeed != null -> org.shilpo.laboon.ui.design.theme.ArtworkColorSchemeGenerator.generateColorScheme(
            checkNotNull(artworkSeed),
            darkTheme
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(
            context
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    val overrides = if (darkTheme) theme?.definition?.darkColors else theme?.definition?.lightColors
    val colors = systemScheme.withThemeOverrides(overrides.orEmpty())
    val controller = LocalVisualThemeController.current
    val fontFamilies = remember(theme, controller) {
        theme?.definition?.typography.orEmpty().mapNotNull { (style, value) ->
            val path = value.fontAsset ?: return@mapNotNull null
            val typeface = if (theme != null && controller != null) {
                runCatching { controller.loadTypeface(theme, path) }.getOrNull()
            } else null
            typeface?.let { style to FontFamily(it) }
        }.toMap()
    }
    val typography =
        Typography().withThemeOverrides(theme?.definition?.typography.orEmpty(), fontFamilies)
    val shapes = shapesWithOverrides(theme)
    val motionScale = if (ValueAnimator.areAnimatorsEnabled()) {
        theme?.definition?.motion?.durationScale ?: 1f
    } else {
        0f
    }

    CompositionLocalProvider(
        LocalVisualMotionScale provides motionScale,
        LocalThemeIconOverrides provides theme?.definition?.iconOverrides.orEmpty(),
    ) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = androidx.compose.material3.MotionScheme.expressive(),
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

private fun ColorScheme.withThemeOverrides(values: Map<String, Long>): ColorScheme {
    fun role(name: String, fallback: Color) = values[name]?.let { Color(it.toInt()) } ?: fallback
    return copy(
        primary = role("primary", primary),
        onPrimary = role("onPrimary", onPrimary),
        primaryContainer = role("primaryContainer", primaryContainer),
        onPrimaryContainer = role("onPrimaryContainer", onPrimaryContainer),
        inversePrimary = role("inversePrimary", inversePrimary),
        secondary = role("secondary", secondary),
        onSecondary = role("onSecondary", onSecondary),
        secondaryContainer = role("secondaryContainer", secondaryContainer),
        onSecondaryContainer = role("onSecondaryContainer", onSecondaryContainer),
        tertiary = role("tertiary", tertiary),
        onTertiary = role("onTertiary", onTertiary),
        tertiaryContainer = role("tertiaryContainer", tertiaryContainer),
        onTertiaryContainer = role("onTertiaryContainer", onTertiaryContainer),
        background = role("background", background),
        onBackground = role("onBackground", onBackground),
        surface = role("surface", surface),
        onSurface = role("onSurface", onSurface),
        surfaceVariant = role("surfaceVariant", surfaceVariant),
        onSurfaceVariant = role("onSurfaceVariant", onSurfaceVariant),
        inverseSurface = role("inverseSurface", inverseSurface),
        inverseOnSurface = role("inverseOnSurface", inverseOnSurface),
        outline = role("outline", outline),
        outlineVariant = role("outlineVariant", outlineVariant),
        scrim = role("scrim", scrim),
        surfaceBright = role("surfaceBright", surfaceBright),
        surfaceDim = role("surfaceDim", surfaceDim),
        surfaceContainer = role("surfaceContainer", surfaceContainer),
        surfaceContainerLow = role("surfaceContainerLow", surfaceContainerLow),
        surfaceContainerHigh = role("surfaceContainerHigh", surfaceContainerHigh),
        surfaceContainerHighest = role("surfaceContainerHighest", surfaceContainerHighest),
        surfaceTint = role("surfaceTint", surfaceTint),
        error = role("error", error),
        onError = role("onError", onError),
        errorContainer = role("errorContainer", errorContainer),
        onErrorContainer = role("onErrorContainer", onErrorContainer),
    )
}

private fun Typography.withThemeOverrides(
    values: Map<String, ThemeTextStyle>,
    fontFamilies: Map<String, FontFamily>
): Typography {
    fun TextStyle.override(name: String): TextStyle {
        val style = values[name] ?: return this
        return copy(
            fontFamily = fontFamilies[name] ?: fontFamily,
            fontSize = style.sizeSp?.sp ?: fontSize,
            fontWeight = style.weight?.let(::FontWeight) ?: fontWeight,
            letterSpacing = style.letterSpacingSp?.sp ?: letterSpacing,
        )
    }
    return copy(
        displayLarge = displayLarge.override("displayLarge"),
        displayMedium = displayMedium.override("displayMedium"),
        displaySmall = displaySmall.override("displaySmall"),
        headlineLarge = headlineLarge.override("headlineLarge"),
        headlineMedium = headlineMedium.override("headlineMedium"),
        headlineSmall = headlineSmall.override("headlineSmall"),
        titleLarge = titleLarge.override("titleLarge"),
        titleMedium = titleMedium.override("titleMedium"),
        titleSmall = titleSmall.override("titleSmall"),
        bodyLarge = bodyLarge.override("bodyLarge"),
        bodyMedium = bodyMedium.override("bodyMedium"),
        bodySmall = bodySmall.override("bodySmall"),
        labelLarge = labelLarge.override("labelLarge"),
        labelMedium = labelMedium.override("labelMedium"),
        labelSmall = labelSmall.override("labelSmall"),
        displayLargeEmphasized = displayLargeEmphasized.override("displayLargeEmphasized"),
        displayMediumEmphasized = displayMediumEmphasized.override("displayMediumEmphasized"),
        displaySmallEmphasized = displaySmallEmphasized.override("displaySmallEmphasized"),
        headlineLargeEmphasized = headlineLargeEmphasized.override("headlineLargeEmphasized"),
        headlineMediumEmphasized = headlineMediumEmphasized.override("headlineMediumEmphasized"),
        headlineSmallEmphasized = headlineSmallEmphasized.override("headlineSmallEmphasized"),
        titleLargeEmphasized = titleLargeEmphasized.override("titleLargeEmphasized"),
        titleMediumEmphasized = titleMediumEmphasized.override("titleMediumEmphasized"),
        titleSmallEmphasized = titleSmallEmphasized.override("titleSmallEmphasized"),
        bodyLargeEmphasized = bodyLargeEmphasized.override("bodyLargeEmphasized"),
        bodyMediumEmphasized = bodyMediumEmphasized.override("bodyMediumEmphasized"),
        bodySmallEmphasized = bodySmallEmphasized.override("bodySmallEmphasized"),
        labelLargeEmphasized = labelLargeEmphasized.override("labelLargeEmphasized"),
        labelMediumEmphasized = labelMediumEmphasized.override("labelMediumEmphasized"),
        labelSmallEmphasized = labelSmallEmphasized.override("labelSmallEmphasized"),
    )
}

private fun shapesWithOverrides(theme: InstalledVisualTheme?): Shapes {
    val values = theme?.definition?.shapeDp.orEmpty()
    fun shape(token: String, fallback: CornerBasedShape): CornerBasedShape =
        values[token]?.let { RoundedCornerShape(it.dp) } ?: fallback

    val defaults = Shapes()
    return Shapes(
        extraSmall = shape("extraSmall", defaults.extraSmall),
        small = shape("small", defaults.small),
        medium = shape("medium", defaults.medium),
        large = shape("large", defaults.large),
        extraLarge = shape("extraLarge", defaults.extraLarge),
    )
}
