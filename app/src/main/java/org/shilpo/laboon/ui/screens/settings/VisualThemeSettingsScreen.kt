package org.shilpo.laboon.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.shilpo.laboon.theme.InstalledVisualTheme
import org.shilpo.laboon.theme.LaboonExpressiveTheme
import org.shilpo.laboon.theme.LocalVisualTheme
import org.shilpo.laboon.theme.LocalVisualThemeController
import org.shilpo.laboon.theme.ThemeCatalogState
import org.shilpo.laboon.theme.VisualThemeController
import org.shilpo.laboon.theme.contract.ThemeOption
import org.shilpo.laboon.theme.contract.ThemeOptionType
import org.shilpo.laboon.theme.renderer.VisualThemePresentation
import org.shilpo.laboon.ui.design.ScreenScaffold
import kotlin.math.roundToInt

private val ThemePickerTopBarClearance = 112.dp

@Composable
internal fun VisualThemeSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = LocalVisualThemeController.current
        ?: androidx.compose.runtime.remember(context) { VisualThemeController(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var previewTheme by remember { mutableStateOf<InstalledVisualTheme?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(controller) { controller.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        coroutineScope.launch { controller.refresh() }
    }

    ScreenScaffold(
        topBar = { SettingsTopAppBar(title = "Appearance & Themes", onBack = onBack) },
    ) {
        org.shilpo.laboon.theme.ThemeRouteContent(
            theme = LocalVisualTheme.current, screenName = "themePicker",
            presentation = VisualThemePresentation(
                values = mapOf(
                    "themes.selectedId" to state.selectedThemeId.orEmpty(),
                    "themes.errorCount" to state.errorCount.toString()
                ),
                collections = mapOf("themes.installed" to state.themes.map { theme ->
                    mapOf(
                        "theme.id" to theme.manifest.id,
                        "theme.name" to theme.manifest.name,
                        "theme.author" to theme.manifest.author,
                        "theme.version" to theme.manifest.version,
                        "theme.minimumApi" to theme.manifest.minimumAndroidApi.toString(),
                        "theme.selected" to (theme.manifest.id == state.selectedThemeId).toString(),
                    )
                }),
            ),
            availableActions = setOf(
                org.shilpo.laboon.theme.contract.VisualThemeAction.BACK,
                org.shilpo.laboon.theme.contract.VisualThemeAction.PREVIEW_THEME,
                org.shilpo.laboon.theme.contract.VisualThemeAction.RESTORE_DEFAULT
            ),
            onAction = { action, parameters ->
                when (action) {
                    org.shilpo.laboon.theme.contract.VisualThemeAction.BACK -> onBack()
                    org.shilpo.laboon.theme.contract.VisualThemeAction.RESTORE_DEFAULT -> controller.select(
                        null
                    )

                    org.shilpo.laboon.theme.contract.VisualThemeAction.PREVIEW_THEME -> state.themes.firstOrNull { it.manifest.id == parameters["theme.id"] }
                        ?.let { theme ->
                            previewError = controller.validateEffects(theme); previewTheme = theme
                        }

                    else -> Unit
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = ThemePickerTopBarClearance),
            fallback = {
                ThemeChoices(
                    state = state,
                    controller = controller,
                    onPreview = { theme ->
                        previewError = theme?.let(controller::validateEffects)
                        previewTheme = theme
                    },
                    onSelect = controller::select,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = ThemePickerTopBarClearance),
                )
            },
        )
    }

    previewTheme?.let { theme ->
        val previewScreen =
            if ("home" in theme.definition.screens) "home" else theme.definition.screens.keys.firstOrNull()
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { previewTheme = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(theme.manifest.name, style = MaterialTheme.typography.headlineSmall)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (previewError == null && previewScreen != null) {
                            LaboonExpressiveTheme(theme = theme) {
                                CompositionLocalProvider(
                                    LocalVisualTheme provides theme,
                                    LocalVisualThemeController provides controller
                                ) {
                                    org.shilpo.laboon.theme.ThemeRouteContent(
                                        theme = theme,
                                        screenName = previewScreen,
                                        presentation = VisualThemePresentation(
                                            values = mapOf(
                                                "screen.name" to previewScreen,
                                                "screen.tab" to "home",
                                                "screen.loading" to "false",
                                                "track.title" to "A sample track",
                                                "track.artist" to "Sample artist",
                                                "player.visible" to "true",
                                                "player.isPlaying" to "false",
                                                "player.progress" to "0.25"
                                            ) +
                                                    theme.definition.options.associate {
                                                        "option.${it.id}" to controller.optionValue(
                                                            theme,
                                                            it
                                                        )
                                                    },
                                            collections = mapOf(
                                                "tracks" to listOf(
                                                    mapOf(
                                                        "track.id" to "sample",
                                                        "track.title" to "A sample track",
                                                        "track.artist" to "Sample artist"
                                                    )
                                                )
                                            ),
                                        ),
                                        availableActions = emptySet(),
                                        onAction = { _, _ -> },
                                        modifier = Modifier.fillMaxSize(),
                                        fallback = {},
                                    )
                                }
                            }
                        } else Text(
                            previewError
                                ?: "This extension supplies theme tokens without screen overrides.",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "${theme.manifest.author} · ${theme.manifest.version} · minimum API ${theme.manifest.minimumAndroidApi}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.Button(
                            enabled = previewError == null,
                            onClick = {
                                previewError = controller.apply(theme)
                                if (previewError == null) previewTheme = null
                            }) { Text("Apply") }
                        TextButton(onClick = { previewTheme = null }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VisualThemeRecoveryDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = LocalVisualThemeController.current
        ?: remember(context) { VisualThemeController(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaboonExpressiveTheme(theme = null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Laboon appearance recovery") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose a compatible extension or restore Laboon's built-in Material 3 Expressive appearance.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            ThemeChoice(
                                title = "Laboon · Material 3 Expressive",
                                supportingText = "Built in",
                                selected = state.selectedThemeId == null,
                                onClick = {
                                    controller.select(null)
                                    onDismiss()
                                },
                            )
                        }
                        items(state.themes, key = { "recovery:${it.manifest.id}" }) { theme ->
                            ThemeChoice(
                                title = theme.manifest.name,
                                supportingText = "${theme.manifest.author} · Android ${theme.manifest.minimumAndroidApi}+",
                                selected = state.selectedThemeId == theme.manifest.id,
                                onClick = {
                                    errorMessage = controller.apply(theme)
                                    if (errorMessage == null) onDismiss()
                                },
                            )
                        }
                    }
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.select(null)
                        onDismiss()
                    },
                ) { Text("Restore stock") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
    }
}

@Composable
private fun ThemeChoices(
    state: ThemeCatalogState,
    controller: VisualThemeController,
    onPreview: (InstalledVisualTheme?) -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.recoveredFromFailure) {
            item {
                Text(
                    "Laboon restored its built-in appearance after a visual interface failure.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        item {
            Text(
                text = "Choose one installed visual extension. Laboon uses its built-in Material 3 Expressive theme when none is selected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ThemeChoice(
                title = "Laboon · Material 3 Expressive",
                supportingText = "Built in",
                selected = state.selectedThemeId == null,
                onClick = { onSelect(null) },
            )
        }
        items(state.themes, key = { it.manifest.id }) { theme ->
            ThemeChoice(
                title = theme.manifest.name,
                supportingText = "${theme.manifest.author} · ${theme.manifest.version} · Android ${theme.manifest.minimumAndroidApi}+",
                selected = state.selectedThemeId == theme.manifest.id,
                onClick = { onPreview(theme) },
            )
        }
        val selectedTheme = state.themes.firstOrNull { it.manifest.id == state.selectedThemeId }
        if (selectedTheme?.definition?.options?.isNotEmpty() == true) {
            item {
                Text(
                    text = "${selectedTheme.manifest.name} options",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(
                selectedTheme.definition.options,
                key = { "${selectedTheme.manifest.id}:${it.id}" }) { option ->
                ThemeOptionControl(
                    theme = selectedTheme,
                    option = option,
                    controller = controller,
                )
            }
        }
        if (state.themes.isEmpty()) {
            item {
                Text(
                    text = "No compatible visual extensions are installed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.errorCount > 0) {
            item {
                Text(
                    text = "${state.errorCount} installed extension(s) could not be loaded. Laboon kept the working theme active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun ThemeOptionsContent(modifier: Modifier = Modifier) {
    val controller = LocalVisualThemeController.current ?: return
    val state by controller.state.collectAsStateWithLifecycle()
    val theme = state.themes.firstOrNull { it.manifest.id == state.selectedThemeId } ?: return
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(theme.definition.options, key = { it.id }) { option ->
            ThemeOptionControl(
                theme,
                option,
                controller
            )
        }
    }
}

@Composable
internal fun ThemeOptionControl(
    theme: InstalledVisualTheme,
    option: ThemeOption,
    controller: VisualThemeController,
) {
    var value by remember(theme.manifest.id, option.id) {
        mutableStateOf(controller.optionValue(theme, option))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (option.type) {
                ThemeOptionType.SWITCH -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.title, style = MaterialTheme.typography.titleSmall)
                        if (option.description.isNotBlank()) Text(
                            option.description,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = value == "true",
                        onCheckedChange = { checked ->
                            value = checked.toString()
                            controller.setOptionValue(theme, option, value)
                        },
                    )
                }

                ThemeOptionType.CHOICE -> {
                    var expanded by remember(theme.manifest.id, option.id) { mutableStateOf(false) }
                    Text(option.title, style = MaterialTheme.typography.titleSmall)
                    if (option.description.isNotBlank()) Text(
                        option.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box {
                        TextButton(onClick = { expanded = true }) { Text(value) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            option.choices.forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice) },
                                    onClick = {
                                        value = choice
                                        controller.setOptionValue(theme, option, choice)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                ThemeOptionType.SLIDER -> {
                    val minimum = requireNotNull(option.minimum)
                    val maximum = requireNotNull(option.maximum)
                    val step = option.step?.takeIf { it > 0f }
                    val intervals =
                        step?.let { ((maximum - minimum) / it).roundToInt().coerceAtLeast(1) }
                    val visibleSteps = intervals?.minus(1)?.takeIf { it in 1..20 } ?: 0
                    val current = value.toFloatOrNull()?.coerceIn(minimum, maximum) ?: minimum
                    val sliderState = rememberSliderState(
                        value = current,
                        steps = visibleSteps,
                        trackRange = minimum..maximum,
                    )
                    LaunchedEffect(current) {
                        sliderState.value = current
                    }
                    Text(option.title, style = MaterialTheme.typography.titleSmall)
                    if (option.description.isNotBlank()) Text(
                        option.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(value, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        state = sliderState,
                        onValueChange = { rawValue ->
                            sliderState.value = rawValue
                            val adjustedValue = step?.let { increment ->
                                val index = ((rawValue - minimum) / increment).roundToInt()
                                minimum + index * increment
                            } ?: rawValue
                            val boundedValue = adjustedValue.coerceIn(minimum, maximum)
                            sliderState.value = boundedValue
                            value = boundedValue.toString()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onValueChangeFinished = { controller.setOptionValue(theme, option, value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeChoice(
    title: String,
    supportingText: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
