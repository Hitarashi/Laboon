package org.shilpo.laboon.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.shilpo.laboon.theme.contract.ThemeOption

internal class VisualThemeController(context: android.content.Context) {
    private val registry = VisualThemeRegistry(context)
    private val _state = MutableStateFlow(ThemeCatalogState())
    val state: StateFlow<ThemeCatalogState> = _state.asStateFlow()
    private val assetCache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > 16
    }
    private val typefaceCache = HashMap<String, android.graphics.Typeface>()

    suspend fun refresh() {
        synchronized(assetCache) { assetCache.clear() }
        synchronized(typefaceCache) { typefaceCache.clear() }
        val catalog = registry.discover()
        val validThemes =
            withContext(Dispatchers.Default) { catalog.themes.filter { validateEffects(it) == null } }
        val selectedStillValid = validThemes.any { it.manifest.id == catalog.selectedThemeId }
        if (catalog.selectedThemeId != null && !selectedStillValid) registry.select(null)
        _state.value = catalog.copy(
            themes = validThemes,
            selectedThemeId = catalog.selectedThemeId.takeIf { selectedStillValid },
            errorCount = catalog.errorCount + (catalog.themes.size - validThemes.size),
        )
    }

    fun select(themeId: String?) {
        if (themeId != null && _state.value.themes.none { it.manifest.id == themeId }) return
        registry.select(themeId)
        _state.value = _state.value.copy(selectedThemeId = themeId, recoveredFromFailure = false)
    }

    fun readAsset(theme: InstalledVisualTheme, path: String): ByteArray = synchronized(assetCache) {
        val key = "${theme.packageName}:${theme.packageRevision}:${theme.manifest.version}:$path"
        assetCache[key] ?: registry.openAsset(theme, path).also { assetCache[key] = it }
    }

    fun optionValue(theme: InstalledVisualTheme, option: ThemeOption): String =
        registry.optionValue(theme, option)

    fun loadTypeface(theme: InstalledVisualTheme, path: String): android.graphics.Typeface =
        synchronized(typefaceCache) {
            val key =
                "${theme.packageName}:${theme.packageRevision}:${theme.manifest.version}:$path"
            typefaceCache[key] ?: registry.loadTypeface(theme, path).also {
                if (typefaceCache.size >= 64) typefaceCache.clear()
                typefaceCache[key] = it
            }
        }

    fun setOptionValue(theme: InstalledVisualTheme, option: ThemeOption, value: String) {
        registry.setOptionValue(theme, option, value)
        _state.value = _state.value.copy(revision = _state.value.revision + 1L)
    }

    fun validateEffects(theme: InstalledVisualTheme): String? {
        if (theme.definition.effects.isEmpty()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return "This extension's visual effects require Android 13 or later."
        }
        return theme.definition.effects.entries.firstNotNullOfOrNull { (id, effect) ->
            val source =
                runCatching { readAsset(theme, effect.shaderAsset).toString(Charsets.UTF_8) }
                    .getOrElse { return "The extension references an unreadable shader asset." }
            if (!validateAgslShader(source, effect.uniforms, id == "glassSurface")) {
                "The extension contains a shader that could not be compiled or has an incompatible input interface."
            } else null
        }
    }

    fun apply(theme: InstalledVisualTheme): String? {
        val error = validateEffects(theme)
        if (error != null) return error
        select(theme.manifest.id)
        return null
    }
}

internal val LocalVisualThemeController =
    staticCompositionLocalOf<VisualThemeController?> { null }

internal val LocalVisualTheme =
    staticCompositionLocalOf<InstalledVisualTheme?> { null }

internal val LocalVisualThemeRevision = staticCompositionLocalOf { 0L }

@Composable
internal fun currentVisualThemeController(): VisualThemeController? =
    LocalVisualThemeController.current
