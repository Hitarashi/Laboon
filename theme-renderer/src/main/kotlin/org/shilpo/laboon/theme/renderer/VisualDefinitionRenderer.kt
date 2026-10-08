@file:OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package org.shilpo.laboon.theme.renderer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.theme.contract.ThemeDefinition
import org.shilpo.laboon.theme.contract.ThemeMotion
import org.shilpo.laboon.theme.contract.VisualNode
import org.shilpo.laboon.theme.contract.VisualThemeAction
import kotlin.math.abs

/** Data visible to a visual definition. Values are presentation-only strings, never app objects. */
data class VisualThemePresentation(
    val values: Map<String, String> = emptyMap(),
    val collections: Map<String, List<Map<String, String>>> = emptyMap(),
    /** Host-owned transient form input, never substituted into visual text or image bindings. */
    val inputValues: Map<String, String> = emptyMap(),
)

/**
 * Renders a validated declarative screen tree. Extension files never provide executable code;
 * all control actions are mapped by the host through [onAction].
 */
@Composable
fun VisualDefinitionRenderer(
    definition: ThemeDefinition,
    screenName: String = "root",
    presentation: VisualThemePresentation = VisualThemePresentation(),
    onAction: (VisualThemeAction, Map<String, String>) -> Unit,
    availableActions: Set<VisualThemeAction> = emptySet(),
    onSeek: (Float) -> Unit = {},
    onFieldChange: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onArtwork: (@Composable (String?, Modifier) -> Unit)? = null,
    onAsset: (@Composable (String, Modifier) -> Unit)? = null,
    onEffect: (@Composable (String, Modifier, @Composable () -> Unit) -> Unit)? = null,
    onHostControl: (@Composable (String, Modifier) -> Unit)? = null,
    onBackdrop: (@Composable (Modifier, @Composable () -> Unit) -> Unit)? = null,
    motionScale: Float = 1f,
) {
    val root = definition.screens[screenName] ?: return
    CompositionLocalProvider(
        LocalRendererMotion provides RendererMotion(definition.motion, motionScale),
        LocalRendererHostControl provides onHostControl, LocalRendererBackdrop provides onBackdrop
    ) {
        Node(
            node = root,
            baseBindings = presentation.values,
            collections = presentation.collections,
            inputValues = presentation.inputValues,
            onAction = onAction,
            availableActions = availableActions,
            onSeek = onSeek,
            onFieldChange = onFieldChange,
            modifier = modifier,
            onArtwork = onArtwork,
            onAsset = onAsset,
            onEffect = onEffect,
        )
    }
}

private data class RendererMotion(val motion: ThemeMotion = ThemeMotion(), val scale: Float = 1f)

private val LocalRendererMotion = staticCompositionLocalOf { RendererMotion() }
private val LocalRendererHostControl =
    staticCompositionLocalOf<(@Composable (String, Modifier) -> Unit)?> { null }
private val LocalRendererBackdrop =
    staticCompositionLocalOf<(@Composable (Modifier, @Composable () -> Unit) -> Unit)?> { null }

@Composable
private fun Node(
    node: VisualNode,
    baseBindings: Map<String, String>,
    collections: Map<String, List<Map<String, String>>>,
    inputValues: Map<String, String>,
    onAction: (VisualThemeAction, Map<String, String>) -> Unit,
    availableActions: Set<VisualThemeAction>,
    onSeek: (Float) -> Unit,
    onFieldChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onArtwork: (@Composable (String?, Modifier) -> Unit)?,
    onAsset: (@Composable (String, Modifier) -> Unit)?,
    onEffect: (@Composable (String, Modifier, @Composable () -> Unit) -> Unit)?,
) {
    val attributes = node.attributes
    if (attributes["visibleBinding"]?.let {
            bindingValue(
                it,
                baseBindings
            ).toBooleanStrictOrNull()
        } == false) {
        return
    }
    val widthDp = LocalConfiguration.current.screenWidthDp
    val minWidthDp = finiteFloat(attributes["minScreenWidthDp"])?.toInt()
    val maxWidthDp = finiteFloat(attributes["maxScreenWidthDp"])?.toInt()
    if ((minWidthDp != null && widthDp < minWidthDp) ||
        (maxWidthDp != null && widthDp > maxWidthDp)
    ) return
    val gestureAction = attributes["gestureAction"]?.let(VisualThemeAction::fromId)
        ?.takeIf { it in availableActions }
    val gestureAxis = attributes["gestureAxis"]
    val gestureDirection = attributes["gestureDirection"]
    val gestureBinding = attributes["gestureProgressBinding"] ?: "gesture.progress"
    var gestureProgress by remember(node, gestureBinding) { mutableFloatStateOf(0f) }
    val bindings = baseBindings + (gestureBinding to gestureProgress.toString())
    val latestAction by rememberUpdatedState(onAction)
    val latestBindings by rememberUpdatedState(baseBindings)
    val latestInputs by rememberUpdatedState(inputValues)
    val density = LocalDensity.current
    val gestureModifier =
        if (gestureAction != null && gestureAxis in setOf("horizontal", "vertical")) {
            val thresholdDp =
                finiteFloat(attributes["gestureThresholdDp"])?.coerceIn(24f, 512f) ?: 72f
            Modifier
                .semantics {
                    customActions = listOf(
                        androidx.compose.ui.semantics.CustomAccessibilityAction(
                            label = attributes["gestureLabel"] ?: attributes["description"]
                            ?: gestureAction.id,
                            action = {
                                latestAction(
                                    gestureAction,
                                    latestBindings + latestInputs + actionParameters(
                                        attributes,
                                        latestBindings,
                                        latestInputs
                                    )
                                )
                                true
                            },
                        )
                    )
                }
                .pointerInput(
                    node,
                    gestureAction,
                    gestureAxis,
                    gestureDirection,
                    gestureBinding,
                    thresholdDp
                ) {
                    var dragDistance = 0f
                    val thresholdPx = with(density) { thresholdDp.dp.toPx() }
                    detectDragGestures(
                        onDragStart = {
                            dragDistance = 0f
                            gestureProgress = 0f
                        },
                        onDragEnd = {
                            val progress = (dragDistance / thresholdPx).coerceIn(-1f, 1f)
                            val directionMatches = when (gestureDirection) {
                                "positive" -> progress > 0f
                                "negative" -> progress < 0f
                                else -> true
                            }
                            if (abs(progress) >= 0.65f && directionMatches) {
                                latestAction(
                                    gestureAction,
                                    latestBindings + latestInputs + actionParameters(
                                        attributes,
                                        latestBindings,
                                        latestInputs
                                    ) +
                                            (gestureBinding to progress.toString()),
                                )
                            } else {
                                gestureProgress = 0f
                            }
                        },
                        onDragCancel = { gestureProgress = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragDistance += if (gestureAxis == "horizontal") dragAmount.x else dragAmount.y
                            gestureProgress = (dragDistance / thresholdPx).coerceIn(-1f, 1f)
                        },
                    )
                }
        } else {
            Modifier
        }
    val shapeName = bindingValue(attributes["shapeBinding"], bindings)
        .ifBlank { value(attributes["shape"], bindings) }
    val transitionEnvironment = LocalVisualThemeTransitions.current
    val sharedKey = value(attributes["sharedElementKey"], bindings)
    val sharedModifier = if (transitionEnvironment != null && sharedKey.isNotBlank()) {
        with(transitionEnvironment.sharedScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(sharedKey),
                animatedVisibilityScope = transitionEnvironment.visibilityScope,
            )
        }
    } else Modifier
    val nodeModifier = modifier
        .then(sizeModifier(attributes))
        .then(sharedModifier)
        .then(transformationModifier(attributes, bindings))
        .then(gestureModifier)
        .then(
            if (attributes["clip"] == "true") Modifier.clip(
                shape(
                    shapeName,
                    attributes,
                    bindings
                )
            ) else Modifier
        )

    val body: @Composable () -> Unit = {
        when (node.type) {
            "box", "overlay" -> Box(
                modifier = nodeModifier.then(backgroundModifier(attributes, bindings)),
                contentAlignment = alignment(attributes["contentAlignment"]),
            ) {
                node.children.forEach { child ->
                    Node(
                        child,
                        bindings,
                        collections,
                        inputValues,
                        onAction,
                        availableActions,
                        onSeek,
                        onFieldChange,
                        onArtwork = onArtwork,
                        onAsset = onAsset,
                        onEffect = onEffect
                    )
                }
            }

            "column" -> Column(
                modifier = nodeModifier
                    .then(backgroundModifier(attributes, bindings))
                    .padding(dp(attributes["paddingDp"])),
                verticalArrangement = Arrangement.spacedBy(dp(attributes["spacingDp"])),
                horizontalAlignment = horizontalAlignment(attributes["horizontalAlignment"]),
            ) {
                node.children.forEach { child ->
                    val weight = finiteFloat(child.attributes["weight"])
                    val childModifier = weight?.takeIf { it > 0f }?.let {
                        Modifier.weight(it.coerceIn(0.01f, 20f))
                    } ?: Modifier
                    Node(
                        child,
                        bindings,
                        collections,
                        inputValues,
                        onAction,
                        availableActions,
                        onSeek,
                        onFieldChange,
                        modifier = childModifier,
                        onArtwork = onArtwork,
                        onAsset = onAsset,
                        onEffect = onEffect
                    )
                }
            }

            "row" -> Row(
                modifier = nodeModifier
                    .then(backgroundModifier(attributes, bindings))
                    .padding(dp(attributes["paddingDp"])),
                horizontalArrangement = Arrangement.spacedBy(dp(attributes["spacingDp"])),
                verticalAlignment = verticalAlignment(attributes["verticalAlignment"]),
            ) {
                node.children.forEach { child ->
                    val weight = finiteFloat(child.attributes["weight"])
                    val childModifier = weight?.takeIf { it > 0f }?.let {
                        Modifier.weight(it.coerceIn(0.01f, 20f))
                    } ?: Modifier
                    Node(
                        child,
                        bindings,
                        collections,
                        inputValues,
                        onAction,
                        availableActions,
                        onSeek,
                        onFieldChange,
                        modifier = childModifier,
                        onArtwork = onArtwork,
                        onAsset = onAsset,
                        onEffect = onEffect
                    )
                }
            }

            "flowRow" -> androidx.compose.foundation.layout.FlowRow(
                modifier = nodeModifier
                    .then(backgroundModifier(attributes, bindings))
                    .padding(dp(attributes["paddingDp"])),
                horizontalArrangement = Arrangement.spacedBy(dp(attributes["spacingDp"])),
                verticalArrangement = Arrangement.spacedBy(dp(attributes["spacingDp"])),
            ) {
                node.children.forEach { child ->
                    Node(
                        child,
                        bindings,
                        collections,
                        inputValues,
                        onAction,
                        availableActions,
                        onSeek,
                        onFieldChange,
                        onArtwork = onArtwork,
                        onAsset = onAsset,
                        onEffect = onEffect
                    )
                }
            }

            "backdrop" -> {
                val children: @Composable () -> Unit = {
                    Children(
                        node,
                        bindings,
                        collections,
                        inputValues,
                        onAction,
                        availableActions,
                        onSeek,
                        onFieldChange,
                        onArtwork,
                        onAsset,
                        onEffect
                    )
                }
                val capture = LocalRendererBackdrop.current
                if (capture != null) capture(
                    nodeModifier,
                    children
                ) else Box(nodeModifier) { children() }
            }

            "hostControl" -> LocalRendererHostControl.current?.invoke(
                attributes["id"].orEmpty(),
                nodeModifier
            )

            "scroll" -> Column(
                modifier = nodeModifier
                    .verticalScroll(rememberScrollState())
                    .padding(dp(attributes["paddingDp"])),
                verticalArrangement = Arrangement.spacedBy(dp(attributes["spacingDp"])),
            ) {
                node.children.forEach { child ->
                    Node(
                        child,
                        bindings,
                        collections,
                        inputValues,
                        onAction,
                        availableActions,
                        onSeek,
                        onFieldChange,
                        onArtwork = onArtwork,
                        onAsset = onAsset,
                        onEffect = onEffect
                    )
                }
            }

            "horizontalScroll" -> Row(
                modifier = nodeModifier
                    .horizontalScroll(rememberScrollState())
                    .padding(dp(attributes["paddingDp"])),
                horizontalArrangement = Arrangement.spacedBy(dp(attributes["spacingDp"])),
                verticalAlignment = verticalAlignment(attributes["verticalAlignment"]),
            ) {
                node.children.forEach { child ->
                    Node(
                        child,
                        bindings,
                        collections,
                        inputValues,
                        onAction,
                        availableActions,
                        onSeek,
                        onFieldChange,
                        onArtwork = onArtwork,
                        onAsset = onAsset,
                        onEffect = onEffect
                    )
                }
            }

            "lazyColumn" -> {
                val collection = attributes["itemsBinding"]?.let(collections::get).orEmpty()
                val template = node.children.firstOrNull()
                LazyColumn(
                    modifier = nodeModifier.padding(dp(attributes["paddingDp"])),
                    verticalArrangement = Arrangement.spacedBy(dp(attributes["spacingDp"])),
                ) {
                    if (attributes.containsKey("itemsBinding")) {
                        if (template != null) {
                            items(collection) { item ->
                                Node(
                                    template,
                                    bindings + item,
                                    collections,
                                    inputValues,
                                    onAction,
                                    availableActions,
                                    onSeek,
                                    onFieldChange,
                                    onArtwork = onArtwork,
                                    onAsset = onAsset,
                                    onEffect = onEffect
                                )
                            }
                        }
                    } else {
                        node.children.forEach { child ->
                            item {
                                Node(
                                    child,
                                    bindings,
                                    collections,
                                    inputValues,
                                    onAction,
                                    availableActions,
                                    onSeek,
                                    onFieldChange,
                                    onArtwork = onArtwork,
                                    onAsset = onAsset,
                                    onEffect = onEffect
                                )
                            }
                        }
                    }
                }
            }

            "lazyRow" -> {
                val collection = attributes["itemsBinding"]?.let(collections::get).orEmpty()
                val template = node.children.firstOrNull()
                LazyRow(
                    modifier = nodeModifier.padding(dp(attributes["paddingDp"])),
                    horizontalArrangement = Arrangement.spacedBy(dp(attributes["spacingDp"])),
                ) {
                    if (attributes.containsKey("itemsBinding")) {
                        if (template != null) {
                            items(collection) { item ->
                                Node(
                                    template,
                                    bindings + item,
                                    collections,
                                    inputValues,
                                    onAction,
                                    availableActions,
                                    onSeek,
                                    onFieldChange,
                                    onArtwork = onArtwork,
                                    onAsset = onAsset,
                                    onEffect = onEffect
                                )
                            }
                        }
                    } else {
                        node.children.forEach { child ->
                            item {
                                Node(
                                    child,
                                    bindings,
                                    collections,
                                    inputValues,
                                    onAction,
                                    availableActions,
                                    onSeek,
                                    onFieldChange,
                                    onArtwork = onArtwork,
                                    onAsset = onAsset,
                                    onEffect = onEffect
                                )
                            }
                        }
                    }
                }
            }

            "lazyGrid" -> {
                val collection = attributes["itemsBinding"]?.let(collections::get).orEmpty()
                val template = node.children.firstOrNull()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(dp(attributes["minCellWidthDp"]).coerceAtLeast(96.dp)),
                    modifier = nodeModifier.padding(dp(attributes["paddingDp"])),
                    verticalArrangement = Arrangement.spacedBy(
                        dp(
                            attributes["verticalSpacingDp"] ?: attributes["spacingDp"]
                        )
                    ),
                    horizontalArrangement = Arrangement.spacedBy(
                        dp(
                            attributes["horizontalSpacingDp"] ?: attributes["spacingDp"]
                        )
                    ),
                ) {
                    if (attributes.containsKey("itemsBinding")) {
                        if (template != null) {
                            items(collection) { item ->
                                Node(
                                    template,
                                    bindings + item,
                                    collections,
                                    inputValues,
                                    onAction,
                                    availableActions,
                                    onSeek,
                                    onFieldChange,
                                    onArtwork = onArtwork,
                                    onAsset = onAsset,
                                    onEffect = onEffect
                                )
                            }
                        }
                    } else {
                        node.children.forEach { child ->
                            item {
                                Node(
                                    child,
                                    bindings,
                                    collections,
                                    inputValues,
                                    onAction,
                                    availableActions,
                                    onSeek,
                                    onFieldChange,
                                    onArtwork = onArtwork,
                                    onAsset = onAsset,
                                    onEffect = onEffect
                                )
                            }
                        }
                    }
                }
            }

            "spacer" -> Spacer(
                modifier = nodeModifier.then(
                    attributes["sizeDp"]?.let { Modifier.size(dp(it)) }
                        ?: Modifier.height(dp(attributes["heightDp"]))
                ))

            "text", "metadata" -> Text(
                text = attributes["binding"]?.let { bindingValue(it, bindings) }
                    ?: value(attributes["glyph"] ?: attributes["text"], bindings),
                modifier = nodeModifier,
                style = textStyle(attributes["style"]),
                color = color(attributes["color"], bindings),
                fontWeight = attributes["weight"]?.toIntOrNull()?.coerceIn(100, 900)
                    ?.let(::FontWeight),
                fontSize = finiteFloat(attributes["sizeSp"])?.coerceIn(8f, 96f)?.sp
                    ?: TextUnit.Unspecified,
                maxLines = attributes["maxLines"]?.toIntOrNull()?.coerceIn(1, 20) ?: Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )

            "icon" -> {
                val symbol = attributes["symbol"]
                if (symbol != null) {
                    val description = attributes["description"]?.let { value(it, bindings) }
                    Icon(
                        painter = materialSymbolPainterResource(
                            name = symbol,
                            slot = attributes["slot"],
                            filled = attributes["filled"] == "true",
                        ),
                        contentDescription = description?.takeIf(String::isNotBlank),
                        modifier = nodeModifier,
                        tint = color(attributes["color"], bindings),
                    )
                } else {
                    Text(
                        text = attributes["glyph"] ?: attributes["text"].orEmpty(),
                        modifier = nodeModifier.then(attributes["description"]?.let { description ->
                            Modifier.semantics { contentDescription = value(description, bindings) }
                        } ?: Modifier),
                        style = textStyle(attributes["style"]),
                        color = color(attributes["color"], bindings),
                        fontWeight = attributes["weight"]?.toIntOrNull()?.coerceIn(100, 900)
                            ?.let(::FontWeight),
                        fontSize = finiteFloat(attributes["sizeSp"])?.coerceIn(8f, 96f)?.sp
                            ?: TextUnit.Unspecified,
                    )
                }
            }

            "textField" -> {
                val fieldId = attributes["field"]
                if (fieldId != null) {
                    OutlinedTextField(
                        value = inputValues[fieldId].orEmpty(),
                        onValueChange = { onFieldChange(fieldId, it) },
                        modifier = nodeModifier.fillMaxWidth(),
                        label = { Text(value(attributes["label"], bindings)) },
                        placeholder = attributes["placeholder"]?.let { text ->
                            ({
                                Text(
                                    value(
                                        text,
                                        bindings
                                    )
                                )
                            })
                        },
                        singleLine = attributes["singleLine"] != "false",
                        enabled = attributes["enabledBinding"]?.let {
                            bindingValue(
                                it,
                                bindings
                            ).toBooleanStrictOrNull()
                        } != false,
                        visualTransformation = if (attributes["secure"] == "true") PasswordVisualTransformation()
                        else VisualTransformation.None,
                    )
                }
            }

            "artwork" -> {
                val artwork =
                    bindingValue(attributes["binding"], bindings).takeIf(String::isNotBlank)
                val artworkModifier = nodeModifier
                    .clip(shape(shapeName, attributes, bindings))
                    .then(if (attributes["description"] != null) Modifier.semantics {
                        contentDescription = value(attributes["description"], bindings)
                    } else Modifier)
                if (onArtwork != null) onArtwork(artwork, artworkModifier)
                else Surface(
                    modifier = artworkModifier,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {}
            }

            "image" -> {
                val assetPath = attributes["asset"]
                if (assetPath != null && onAsset != null) onAsset(assetPath, nodeModifier)
            }

            "surface" -> Surface(
                modifier = nodeModifier,
                shape = shape(shapeName, attributes, bindings),
                color = color(attributes["color"] ?: "surfaceContainer", bindings),
                tonalElevation = dp(attributes["tonalElevationDp"]),
                shadowElevation = dp(attributes["shadowElevationDp"]),
            ) {
                Children(
                    node,
                    bindings,
                    collections,
                    inputValues,
                    onAction,
                    availableActions,
                    onSeek,
                    onFieldChange,
                    onArtwork,
                    onAsset,
                    onEffect
                )
            }

            "button", "control" -> {
                val action = attributes["action"]?.let(VisualThemeAction::fromId)
                val label = value(
                    attributes["label"] ?: attributes["text"] ?: attributes["binding"],
                    bindings
                )
                val enabled = attributes["enabled"]?.let {
                    value(
                        it,
                        bindings
                    ).toBooleanStrictOrNull()
                } != false &&
                        attributes["enabledBinding"]?.let {
                            bindingValue(
                                it,
                                bindings
                            ).toBooleanStrictOrNull()
                        } != false
                Button(
                    modifier = nodeModifier.semantics {
                        contentDescription = value(attributes["description"] ?: label, bindings)
                    },
                    enabled = action != null && action in availableActions && enabled,
                    onClick = {
                        action?.takeIf { it in availableActions }?.let {
                            onAction(
                                it,
                                bindings + inputValues + actionParameters(
                                    attributes,
                                    bindings,
                                    inputValues
                                )
                            )
                        }
                    },
                ) {
                    Text(label.ifBlank { action?.name.orEmpty() })
                }
            }

            "iconButton" -> {
                val action = attributes["action"]?.let(VisualThemeAction::fromId)
                val label = value(attributes["label"] ?: attributes["description"], bindings)
                val enabled = attributes["enabled"]?.let {
                    value(
                        it,
                        bindings
                    ).toBooleanStrictOrNull()
                } != false &&
                        attributes["enabledBinding"]?.let {
                            bindingValue(
                                it,
                                bindings
                            ).toBooleanStrictOrNull()
                        } != false
                FilledIconButton(
                    modifier = nodeModifier.semantics {
                        contentDescription = label.ifBlank { action?.name.orEmpty() }
                    },
                    enabled = action != null && action in availableActions && enabled,
                    onClick = {
                        action?.takeIf { it in availableActions }?.let {
                            onAction(
                                it,
                                bindings + inputValues + actionParameters(
                                    attributes,
                                    bindings,
                                    inputValues
                                )
                            )
                        }
                    },
                ) {
                    val symbol = attributes["symbol"]
                    if (symbol != null) {
                        Icon(
                            painter = materialSymbolPainterResource(
                                name = symbol,
                                slot = attributes["slot"],
                                filled = attributes["filled"] == "true",
                            ),
                            contentDescription = null,
                        )
                    } else {
                        Text(value(attributes["glyph"], bindings).ifBlank { "•" })
                    }
                }
            }

            "progress" -> {
                val progress =
                    finiteFloat(bindingValue(attributes["binding"], bindings))?.coerceIn(0f, 1f)
                        ?: 0f
                if (attributes["action"] == VisualThemeAction.SEEK.id && VisualThemeAction.SEEK in availableActions) {
                    val sliderState = rememberSliderState(value = progress, trackRange = 0f..1f)
                    LaunchedEffect(progress) { sliderState.value = progress }
                    Slider(
                        state = sliderState,
                        onValueChange = { value ->
                            sliderState.value = value
                            onSeek(value)
                        },
                        modifier = nodeModifier.fillMaxWidth(),
                    )
                } else if (attributes["indeterminate"] == "true") CircularProgressIndicator(
                    modifier = nodeModifier.size(
                        dp(attributes["sizeDp"])
                    )
                )
                else LinearProgressIndicator(
                    progress = { progress },
                    modifier = nodeModifier.fillMaxWidth()
                )
            }

            "drawing" -> {
                val brush = brush(
                    attributes + ("background" to (attributes["color"] ?: "primary")),
                    bindings
                )
                val strokeDp = finiteFloat(attributes["strokeDp"])?.coerceIn(0f, 64f) ?: 0f
                val radiusDp = finiteFloat(attributes["radiusDp"])?.coerceIn(0f, 512f) ?: 0f
                Canvas(nodeModifier) {
                    val style =
                        if (strokeDp > 0f) Stroke(strokeDp.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill
                    when (attributes["drawingKind"]) {
                        "circle" -> drawCircle(brush = brush, style = style)
                        "line" -> drawLine(
                            brush,
                            Offset.Zero,
                            Offset(size.width, size.height),
                            strokeWidth = strokeDp.dp.toPx().coerceAtLeast(1f)
                        )

                        else -> drawRoundRect(
                            brush,
                            cornerRadius = CornerRadius(radiusDp.dp.toPx()),
                            style = style
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    val effectId = attributes["effect"]
    val effectEnabled = attributes["effectEnabledBinding"]?.let {
        bindingValue(
            it,
            bindings
        ).toBooleanStrictOrNull()
    } != false
    if (effectId != null && effectEnabled && onEffect != null) onEffect(
        effectId,
        Modifier,
        body
    ) else body()
}

@Composable
private fun Children(
    node: VisualNode,
    bindings: Map<String, String>,
    collections: Map<String, List<Map<String, String>>>,
    inputValues: Map<String, String>,
    onAction: (VisualThemeAction, Map<String, String>) -> Unit,
    availableActions: Set<VisualThemeAction>,
    onSeek: (Float) -> Unit,
    onFieldChange: (String, String) -> Unit,
    onArtwork: (@Composable (String?, Modifier) -> Unit)?,
    onAsset: (@Composable (String, Modifier) -> Unit)?,
    onEffect: (@Composable (String, Modifier, @Composable () -> Unit) -> Unit)?,
) {
    node.children.forEach { child ->
        Node(
            child,
            bindings,
            collections,
            inputValues,
            onAction,
            availableActions,
            onSeek,
            onFieldChange,
            onArtwork = onArtwork,
            onAsset = onAsset,
            onEffect = onEffect
        )
    }
}

private fun value(template: String?, bindings: Map<String, String>): String =
    org.shilpo.laboon.theme.contract.VisualBindingFormatter.format(template, bindings)

private fun bindingValue(key: String?, bindings: Map<String, String>): String =
    key?.let { bindings[it] ?: value(it, bindings) }.orEmpty()

private fun actionParameters(
    attributes: Map<String, String>,
    bindings: Map<String, String>,
    inputValues: Map<String, String>,
): Map<String, String> = attributes
    .filterKeys { it.startsWith("parameter.") }
    .mapKeys { (key, _) -> key.removePrefix("parameter.") }
    .mapValues { (_, raw) -> bindingValue(raw, bindings + inputValues) }

private fun finiteFloat(raw: String?): Float? = raw?.toFloatOrNull()?.takeIf(Float::isFinite)

private fun sizeModifier(attributes: Map<String, String>): Modifier {
    val width = finiteFloat(attributes["widthDp"])?.coerceIn(0f, 4096f)
    val height = finiteFloat(attributes["heightDp"])?.coerceIn(0f, 4096f)
    val size = finiteFloat(attributes["sizeDp"])?.coerceIn(0f, 4096f)
    var modifier: Modifier = Modifier
    if (attributes["fillMaxSize"] == "true") modifier = modifier.fillMaxSize()
    else {
        if (attributes["fillMaxWidth"] == "true") modifier = modifier.fillMaxWidth()
        if (attributes["fillMaxHeight"] == "true") modifier = modifier.fillMaxHeight()
    }
    if (size != null) modifier = modifier.size(size.dp)
    if (width != null) modifier = modifier.width(width.dp)
    if (height != null) modifier = modifier.height(height.dp)
    return modifier
}

@Composable
private fun backgroundModifier(
    attributes: Map<String, String>,
    bindings: Map<String, String>
): Modifier =
    if (attributes.containsKey("background") || attributes.containsKey("gradient")) {
        Modifier.background(brush(attributes, bindings))
    } else Modifier

@Composable
private fun transformationModifier(
    attributes: Map<String, String>,
    bindings: Map<String, String>
): Modifier {
    fun target(property: String, raw: String = property, fallback: Float): Float =
        finiteFloat(bindingValue(attributes["${property}Binding"], bindings)) ?: finiteFloat(
            attributes[raw]
        ) ?: fallback

    val rotation = animatedProperty(
        "rotation",
        target("rotation", fallback = 0f).coerceIn(-360f, 360f),
        attributes
    ).coerceIn(-360f, 360f)
    val scale = animatedProperty(
        "scale",
        target("scale", fallback = 1f).coerceIn(0.1f, 4f),
        attributes
    ).coerceIn(0.1f, 4f)
    val alpha = animatedProperty(
        "alpha",
        target("alpha", fallback = 1f).coerceIn(0f, 1f),
        attributes
    ).coerceIn(0f, 1f)
    val translationX = animatedProperty(
        "translationX",
        target("translationX", "translationXDp", 0f).coerceIn(-2_000f, 2_000f),
        attributes
    ).coerceIn(-2_000f, 2_000f)
    val translationY = animatedProperty(
        "translationY",
        target("translationY", "translationYDp", 0f).coerceIn(-2_000f, 2_000f),
        attributes
    ).coerceIn(-2_000f, 2_000f)
    return Modifier.graphicsLayer {
        rotationZ = rotation
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
        this.translationX = translationX * density
        this.translationY = translationY * density
    }
}

@Composable
private fun animatedProperty(
    property: String,
    target: Float,
    attributes: Map<String, String>
): Float {
    val animationId = attributes["${property}Animation"] ?: return target
    val environment = LocalRendererMotion.current
    if (environment.scale <= 0f) return target
    val animation = environment.motion.keyframes[animationId]
    if (animationId != "spring" && animation == null) return target
    val initial = finiteFloat(attributes["${property}From"])?.coerceIn(-2_000f, 2_000f) ?: target
    val animated = remember(property, animationId) { Animatable(initial) }
    LaunchedEffect(target, animationId, environment) {
        if (animation != null) {
            val start = animated.value
            val duration = (animation.durationMs * environment.scale).toInt().coerceIn(1, 30_000)
            animated.animateTo(target, keyframes {
                durationMillis = duration
                animation.frames.forEach { frame ->
                    (start + (target - start) * frame.progress) at (duration * frame.fraction).toInt()
                }
            })
        } else {
            animated.animateTo(
                target, spring(
                    dampingRatio = environment.motion.springDampingRatio,
                    stiffness = (environment.motion.springStiffness / (environment.scale * environment.scale)).coerceIn(
                        1f,
                        10_000f
                    ),
                )
            )
        }
    }
    return animated.value
}

private fun dp(raw: String?): androidx.compose.ui.unit.Dp =
    finiteFloat(raw)?.coerceIn(0f, 512f)?.dp ?: 0.dp

@Composable
private fun shape(
    name: String?,
    attributes: Map<String, String>,
    bindings: Map<String, String>
): Shape {
    val polygon =
        remember(attributes["polygonPoints"]) { attributes["polygonPoints"]?.let(org.shilpo.laboon.theme.contract.VisualPolygon::parse) }
    val endPolygon =
        remember(attributes["morphPolygonPoints"]) { attributes["morphPolygonPoints"]?.let(org.shilpo.laboon.theme.contract.VisualPolygon::parse) }
    if (polygon != null) {
        val target =
            finiteFloat(bindingValue(attributes["morphProgressBinding"], bindings))?.coerceIn(
                0f,
                1f
            ) ?: 0f
        val progress = animatedProperty("morph", target, attributes).coerceIn(0f, 1f)
        val end = endPolygon?.takeIf { it.size == polygon.size } ?: polygon
        return androidx.compose.foundation.shape.GenericShape { size, _ ->
            polygon.forEachIndexed { index, start ->
                val x = (start.x + (end[index].x - start.x) * progress) * size.width
                val y = (start.y + (end[index].y - start.y) * progress) * size.height
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
    val morphStart = finiteFloat(attributes["morphStartDp"])?.coerceIn(0f, 512f)
    val morphEnd = finiteFloat(attributes["morphEndDp"])?.coerceIn(0f, 512f)
    val morphProgress = finiteFloat(bindingValue(attributes["morphProgressBinding"], bindings))
    if (morphStart != null && morphEnd != null && morphProgress != null) {
        val progress = morphProgress.coerceIn(0f, 1f)
        val radius = morphStart + (morphEnd - morphStart) * progress
        return RoundedCornerShape(
            animatedProperty("morph", radius, attributes).coerceIn(
                0f,
                512f
            ).dp
        )
    }
    return when (name) {
        "extraSmall" -> MaterialTheme.shapes.extraSmall
        "small" -> MaterialTheme.shapes.small
        "large" -> MaterialTheme.shapes.large
        "extraLarge" -> MaterialTheme.shapes.extraLarge
        "circle" -> androidx.compose.foundation.shape.CircleShape
        else -> MaterialTheme.shapes.medium
    }
}

@Composable
private fun color(name: String?, bindings: Map<String, String>): Color =
    when (val resolved = value(name, bindings)) {
        "primary" -> MaterialTheme.colorScheme.primary
        "onPrimary" -> MaterialTheme.colorScheme.onPrimary
        "primaryContainer" -> MaterialTheme.colorScheme.primaryContainer
        "onPrimaryContainer" -> MaterialTheme.colorScheme.onPrimaryContainer
        "secondary" -> MaterialTheme.colorScheme.secondary
        "onSecondary" -> MaterialTheme.colorScheme.onSecondary
        "secondaryContainer" -> MaterialTheme.colorScheme.secondaryContainer
        "onSecondaryContainer" -> MaterialTheme.colorScheme.onSecondaryContainer
        "tertiary" -> MaterialTheme.colorScheme.tertiary
        "onTertiary" -> MaterialTheme.colorScheme.onTertiary
        "tertiaryContainer" -> MaterialTheme.colorScheme.tertiaryContainer
        "onTertiaryContainer" -> MaterialTheme.colorScheme.onTertiaryContainer
        "surface" -> MaterialTheme.colorScheme.surface
        "onSurface" -> MaterialTheme.colorScheme.onSurface
        "surfaceVariant" -> MaterialTheme.colorScheme.surfaceVariant
        "onSurfaceVariant" -> MaterialTheme.colorScheme.onSurfaceVariant
        "surfaceContainer" -> MaterialTheme.colorScheme.surfaceContainer
        "surfaceContainerLow" -> MaterialTheme.colorScheme.surfaceContainerLow
        "surfaceContainerLowest" -> MaterialTheme.colorScheme.surfaceContainerLowest
        "surfaceContainerHigh" -> MaterialTheme.colorScheme.surfaceContainerHigh
        "surfaceContainerHighest" -> MaterialTheme.colorScheme.surfaceContainerHighest
        "background" -> MaterialTheme.colorScheme.background
        "onBackground" -> MaterialTheme.colorScheme.onBackground
        "error" -> MaterialTheme.colorScheme.error
        "onError" -> MaterialTheme.colorScheme.onError
        "outline" -> MaterialTheme.colorScheme.outline
        "outlineVariant" -> MaterialTheme.colorScheme.outlineVariant
        else -> runCatching { Color(android.graphics.Color.parseColor(resolved)) }
            .getOrElse { MaterialTheme.colorScheme.onSurface }
    }

@Composable
private fun brush(attributes: Map<String, String>, bindings: Map<String, String>): Brush {
    val stops =
        attributes["gradient"]?.split(',')?.take(8).orEmpty().map { color(it.trim(), bindings) }
    return if (stops.size >= 2) Brush.linearGradient(stops) else Brush.linearGradient(
        listOf(color(attributes["background"], bindings), color(attributes["background"], bindings))
    )
}

@Composable
private fun textStyle(name: String?) = when (name) {
    "displayLarge" -> MaterialTheme.typography.displayLarge
    "displayMedium" -> MaterialTheme.typography.displayMedium
    "displaySmall" -> MaterialTheme.typography.displaySmall
    "headlineLarge" -> MaterialTheme.typography.headlineLarge
    "headlineMedium" -> MaterialTheme.typography.headlineMedium
    "headlineSmall" -> MaterialTheme.typography.headlineSmall
    "titleLarge" -> MaterialTheme.typography.titleLarge
    "titleMedium" -> MaterialTheme.typography.titleMedium
    "titleSmall" -> MaterialTheme.typography.titleSmall
    "bodyLarge" -> MaterialTheme.typography.bodyLarge
    "bodyMedium" -> MaterialTheme.typography.bodyMedium
    "bodySmall" -> MaterialTheme.typography.bodySmall
    "labelLarge" -> MaterialTheme.typography.labelLarge
    "labelMedium" -> MaterialTheme.typography.labelMedium
    "labelSmall" -> MaterialTheme.typography.labelSmall
    else -> MaterialTheme.typography.bodyMedium
}

private fun alignment(name: String?): Alignment = when (name) {
    "center" -> Alignment.Center
    "topStart" -> Alignment.TopStart
    "topCenter" -> Alignment.TopCenter
    "topEnd" -> Alignment.TopEnd
    "bottomStart" -> Alignment.BottomStart
    "bottomCenter" -> Alignment.BottomCenter
    "bottomEnd" -> Alignment.BottomEnd
    "centerStart" -> Alignment.CenterStart
    "centerEnd" -> Alignment.CenterEnd
    else -> Alignment.TopStart
}

private fun horizontalAlignment(name: String?): Alignment.Horizontal = when (name) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

private fun verticalAlignment(name: String?): Alignment.Vertical = when (name) {
    "center" -> Alignment.CenterVertically
    "bottom" -> Alignment.Bottom
    else -> Alignment.Top
}
