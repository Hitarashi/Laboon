package org.shilpo.laboon.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.shilpo.laboon.theme.contract.VisualThemeAction
import org.shilpo.laboon.theme.renderer.VisualDefinitionRenderer
import org.shilpo.laboon.theme.renderer.VisualThemePresentation

@Composable
internal fun ThemeRouteContent(
    theme: InstalledVisualTheme?,
    screenName: String,
    presentation: VisualThemePresentation,
    availableActions: Set<VisualThemeAction>,
    onAction: (VisualThemeAction, Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    onFieldChange: (String, String) -> Unit = { _, _ -> },
    onSeek: (Float) -> Unit = {},
    fallback: @Composable () -> Unit,
) {
    if (theme == null || screenName !in theme.definition.screens) {
        fallback()
        return
    }

    val controller = LocalVisualThemeController.current
    ThemeBackdropContext {
        VisualDefinitionRenderer(
            definition = theme.definition,
            screenName = screenName,
            presentation = presentation,
            availableActions = availableActions,
            onAction = onAction,
            onFieldChange = onFieldChange,
            onSeek = onSeek,
            motionScale = LocalVisualMotionScale.current,
            modifier = modifier
                .statusBarsPadding()
                .navigationBarsPadding(),
            onArtwork = { model, imageModifier ->
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier,
                )
            },
            onAsset = { path, imageModifier ->
                val image = remember(
                    theme.packageName,
                    theme.packageRevision,
                    theme.manifest.version,
                    path,
                    controller
                ) {
                    runCatching {
                        val bytes = controller?.readAsset(theme, path) ?: return@runCatching null
                        decodeThemeBitmap(bytes)
                    }.getOrNull()
                }
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = imageModifier,
                    )
                } else {
                    Surface(
                        modifier = imageModifier,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {}
                }
            },
            onEffect = { id, effectModifier, content ->
                ThemeEffectSurface(effectId = id, modifier = effectModifier, content = content)
            },
            onHostControl = { id, controlModifier ->
                if (id == "themeOptions") org.shilpo.laboon.ui.screens.settings.ThemeOptionsContent(
                    controlModifier
                )
            },
            onBackdrop = { backdropModifier, content ->
                ThemeBackdropCapture(
                    backdropModifier,
                    content
                )
            },
        )
    }
}
