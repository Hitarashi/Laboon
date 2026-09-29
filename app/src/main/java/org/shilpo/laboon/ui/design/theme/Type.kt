package org.shilpo.laboon.ui.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.shilpo.laboon.R

val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex, FontWeight.Normal),
    Font(R.font.google_sans_flex, FontWeight.Medium),
    Font(R.font.google_sans_flex, FontWeight.SemiBold),
    Font(R.font.google_sans_flex, FontWeight.Bold),
)

private val defaultTypography = Typography()

// The emphasis half below must stay in sync with the base half's fontFamily.
val AppTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = GoogleSansFlex),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = GoogleSansFlex),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = GoogleSansFlex),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = GoogleSansFlex),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = GoogleSansFlex),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = GoogleSansFlex),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = GoogleSansFlex),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = GoogleSansFlex),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = GoogleSansFlex),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = GoogleSansFlex),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = GoogleSansFlex),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = GoogleSansFlex),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = GoogleSansFlex),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = GoogleSansFlex),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = GoogleSansFlex),
    displayLargeEmphasized = defaultTypography.displayLargeEmphasized.copy(fontFamily = GoogleSansFlex),
    displayMediumEmphasized = defaultTypography.displayMediumEmphasized.copy(fontFamily = GoogleSansFlex),
    displaySmallEmphasized = defaultTypography.displaySmallEmphasized.copy(fontFamily = GoogleSansFlex),
    headlineLargeEmphasized = defaultTypography.headlineLargeEmphasized.copy(fontFamily = GoogleSansFlex),
    headlineMediumEmphasized = defaultTypography.headlineMediumEmphasized.copy(fontFamily = GoogleSansFlex),
    headlineSmallEmphasized = defaultTypography.headlineSmallEmphasized.copy(fontFamily = GoogleSansFlex),
    titleLargeEmphasized = defaultTypography.titleLargeEmphasized.copy(fontFamily = GoogleSansFlex),
    titleMediumEmphasized = defaultTypography.titleMediumEmphasized.copy(fontFamily = GoogleSansFlex),
    titleSmallEmphasized = defaultTypography.titleSmallEmphasized.copy(fontFamily = GoogleSansFlex),
    bodyLargeEmphasized = defaultTypography.bodyLargeEmphasized.copy(fontFamily = GoogleSansFlex),
    bodyMediumEmphasized = defaultTypography.bodyMediumEmphasized.copy(fontFamily = GoogleSansFlex),
    bodySmallEmphasized = defaultTypography.bodySmallEmphasized.copy(fontFamily = GoogleSansFlex),
    labelLargeEmphasized = defaultTypography.labelLargeEmphasized.copy(fontFamily = GoogleSansFlex),
    labelMediumEmphasized = defaultTypography.labelMediumEmphasized.copy(fontFamily = GoogleSansFlex),
    labelSmallEmphasized = defaultTypography.labelSmallEmphasized.copy(fontFamily = GoogleSansFlex),
)
