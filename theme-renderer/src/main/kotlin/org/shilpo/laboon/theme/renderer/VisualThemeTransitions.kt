@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package org.shilpo.laboon.theme.renderer

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

internal data class VisualThemeTransitionEnvironment(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
)

internal val LocalVisualThemeTransitions =
    staticCompositionLocalOf<VisualThemeTransitionEnvironment?> { null }

/** Host-owned transition scopes; extensions only declare matching element keys. */
@Composable
fun VisualThemeTransitionContext(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalVisualThemeTransitions provides if (enabled) {
            VisualThemeTransitionEnvironment(sharedTransitionScope, animatedVisibilityScope)
        } else null,
        content = content,
    )
}
