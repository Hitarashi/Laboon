package org.shilpo.laboon.theme

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.theme.contract.CURRENT_THEME_CONTRACT_VERSION
import org.shilpo.laboon.theme.contract.MaterialSymbolCatalog
import org.shilpo.laboon.theme.contract.ThemeDefinition
import org.shilpo.laboon.theme.contract.ThemeEffect
import org.shilpo.laboon.theme.contract.ThemeIconOverrideValidator
import org.shilpo.laboon.theme.contract.ThemeIconSlots
import org.shilpo.laboon.theme.contract.ThemeKeyframe
import org.shilpo.laboon.theme.contract.ThemeKeyframeAnimation
import org.shilpo.laboon.theme.contract.ThemeKeyframeValidator
import org.shilpo.laboon.theme.contract.ThemeManifest
import org.shilpo.laboon.theme.contract.ThemeManifestValidator
import org.shilpo.laboon.theme.contract.ThemeMotion
import org.shilpo.laboon.theme.contract.ThemeOption
import org.shilpo.laboon.theme.contract.ThemeOptionType
import org.shilpo.laboon.theme.contract.ThemeTextStyle
import org.shilpo.laboon.theme.contract.VisualNode
import org.shilpo.laboon.theme.contract.VisualThemeAction
import java.io.ByteArrayOutputStream

internal data class InstalledVisualTheme(
    val packageName: String,
    val label: String,
    val manifest: ThemeManifest,
    val definition: ThemeDefinition,
    val packageRevision: Long = 0L,
)

internal data class ThemeCatalogState(
    val themes: List<InstalledVisualTheme> = emptyList(),
    val selectedThemeId: String? = null,
    val errorCount: Int = 0,
    val revision: Long = 0,
    val recoveredFromFailure: Boolean = false,
)

internal class VisualThemeRegistry(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    suspend fun discover(): ThemeCatalogState = withContext(Dispatchers.IO) {
        val discovered = ArrayList<InstalledVisualTheme>()
        var errorCount = 0
        queryThemeActivities().distinctBy { it.activityInfo?.packageName }.forEach { result ->
            val appInfo = result.activityInfo?.applicationInfo ?: return@forEach
            val packageName = appInfo.packageName
            try {
                val resourceId = appInfo.metaData?.getInt(THEME_MANIFEST_METADATA) ?: 0
                require(resourceId != 0) { "Missing visual-theme manifest metadata" }
                val resources = packageManager.getResourcesForApplication(appInfo)
                val manifestJson =
                    resources.openRawResource(resourceId).use { it.readBoundedText() }
                val manifest = parseManifest(JSONObject(manifestJson))
                val validation = ThemeManifestValidator.validate(
                    manifest = manifest,
                    androidApi = Build.VERSION.SDK_INT,
                )
                require(validation.isValid) { validation.issues.joinToString() }
                val definitionJson =
                    resources.assets.open(manifest.definitionAsset).use { it.readBoundedText() }
                val definition = parseDefinition(JSONObject(definitionJson))
                require(definition.effects.isEmpty() || manifest.minimumAndroidApi >= Build.VERSION_CODES.TIRAMISU) {
                    "Extensions containing AGSL effects must declare Android 13 or later"
                }
                validateDefinitionAssets(definition, resources.assets)
                require(definition.effects.isEmpty() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "This visual extension uses AGSL effects, which require Android 13 or later"
                }
                val label = packageManager.getApplicationLabel(appInfo).toString()

                @Suppress("DEPRECATION")
                val packageRevision = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                discovered += InstalledVisualTheme(
                    packageName,
                    label,
                    manifest,
                    definition,
                    packageRevision
                )
            } catch (exception: Exception) {
                errorCount++
                Log.w(TAG, "Skipping invalid visual extension $packageName", exception)
            }
        }

        val duplicateIds = discovered.groupingBy { it.manifest.id }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            errorCount += discovered.count { it.manifest.id in duplicateIds }
            discovered.removeAll { it.manifest.id in duplicateIds }
        }

        val storedSelection = appContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(SELECTED_THEME_KEY, null)
        val selectedThemeId =
            storedSelection?.takeIf { id -> discovered.any { it.manifest.id == id } }
        if (storedSelection != null && selectedThemeId == null) {
            appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit { remove(SELECTED_THEME_KEY) }
        }
        ThemeCatalogState(
            themes = discovered.sortedBy { it.manifest.name.lowercase() },
            selectedThemeId = selectedThemeId,
            errorCount = errorCount,
            recoveredFromFailure = appContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
                .getBoolean("recovered_ui_failure", false),
        )
    }

    fun select(themeId: String?) {
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            if (themeId == null) remove(SELECTED_THEME_KEY) else putString(
                SELECTED_THEME_KEY,
                themeId
            )
            remove("recovered_ui_failure")
        }
    }

    fun optionValue(theme: InstalledVisualTheme, option: ThemeOption): String {
        val preferences =
            appContext.getSharedPreferences(optionPreferencesName(theme), Context.MODE_PRIVATE)
        val value = preferences.getString(option.id, null) ?: return option.defaultValue
        return value.takeIf { isValidOptionValue(option, it) } ?: option.defaultValue
    }

    fun setOptionValue(theme: InstalledVisualTheme, option: ThemeOption, value: String) {
        require(theme.definition.options.any { it.id == option.id }) { "Unknown visual theme option" }
        require(
            isValidOptionValue(
                option,
                value
            )
        ) { "Invalid value for visual theme option ${option.id}" }
        appContext.getSharedPreferences(optionPreferencesName(theme), Context.MODE_PRIVATE)
            .edit { putString(option.id, value) }
    }

    fun openAsset(theme: InstalledVisualTheme, assetPath: String): ByteArray {
        require(ThemeManifestValidator.isSafeAssetPath(assetPath))
        val resources = packageManager.getResourcesForApplication(theme.packageName)
        return resources.assets.open(assetPath).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                totalBytes += read
                require(totalBytes <= MAX_ASSET_BYTES) { "Theme asset exceeds the size limit" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    fun loadTypeface(theme: InstalledVisualTheme, assetPath: String): android.graphics.Typeface {
        requireSafeAssetPath(assetPath)
        return android.graphics.Typeface.createFromAsset(
            packageManager.getResourcesForApplication(theme.packageName).assets,
            assetPath,
        )
    }

    private fun queryThemeActivities(): List<ResolveInfo> {
        val intent = Intent(ACTION_VISUAL_THEME)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryThemeActivitiesApi33(intent)
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun queryThemeActivitiesApi33(intent: Intent): List<ResolveInfo> =
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )

    private fun parseManifest(json: JSONObject): ThemeManifest = ThemeManifest(
        id = json.getString("id"),
        name = json.getString("name"),
        author = json.getString("author"),
        version = json.getString("version"),
        contractVersion = json.getInt("contractVersion"),
        minimumAndroidApi = json.getInt("minimumAndroidApi"),
        definitionAsset = json.getString("definitionAsset"),
    )

    private fun optionPreferencesName(theme: InstalledVisualTheme) =
        "visual_theme_options_${theme.manifest.id}"

    private fun isValidOptionValue(option: ThemeOption, value: String): Boolean =
        when (option.type) {
            ThemeOptionType.SWITCH -> value == "true" || value == "false"
            ThemeOptionType.CHOICE -> value in option.choices
            ThemeOptionType.SLIDER -> value.toFloatOrNull()?.let { number ->
                number.isFinite() && number in requireNotNull(option.minimum)..requireNotNull(option.maximum)
            } == true
        }

    private fun parseDefinition(json: JSONObject): ThemeDefinition {
        require(json.getInt("schemaVersion") == CURRENT_THEME_CONTRACT_VERSION) {
            "Unsupported visual definition version"
        }
        val effects = json.optJSONObject("effects").toNameObjectMap().mapValues { (_, value) ->
            ThemeEffect(
                shaderAsset = value.getString("shaderAsset").also(::requireSafeAssetPath),
                uniforms = value.optJSONObject("uniforms").toFloatMap(),
            )
        }
        require(effects.size <= MAX_EFFECT_COUNT)
        val typography =
            json.optJSONObject("typography").toNameObjectMap().mapValues { (_, value) ->
                ThemeTextStyle(
                    fontAsset = value.optString("fontAsset").takeUnless { it.isBlank() }
                        ?.also(::requireSafeAssetPath),
                    sizeSp = value.optNullableFloat("sizeSp")?.also { require(it in 8f..96f) },
                    weight = value.optNullableInt("weight")?.also { require(it in 100..900) },
                    letterSpacingSp = value.optNullableFloat("letterSpacingSp")
                        ?.also { require(it in -2f..12f) },
                )
            }
        require(typography.size <= MAX_TYPOGRAPHY_COUNT)
        val motionJson = json.optJSONObject("motion") ?: JSONObject()
        val keyframes =
            motionJson.optJSONObject("keyframes").toNameObjectMap().mapValues { (_, animation) ->
                val framesJson = animation.getJSONArray("frames")
                require(framesJson.length() in 2..64)
                val frames = List(framesJson.length()) { index ->
                    val frame = framesJson.getJSONObject(index)
                    ThemeKeyframe(
                        fraction = frame.getDouble("fraction").toFloat()
                            .also { require(it in 0f..1f) },
                        progress = frame.getDouble("progress").toFloat()
                            .also { require(it in -1f..2f) },
                    )
                }
                ThemeKeyframeAnimation(
                    durationMs = animation.getInt("durationMs").also { require(it in 1..10_000) },
                    frames = frames,
                ).also { require(ThemeKeyframeValidator.isValid(it)) { "Invalid keyframe animation" } }
            }
        require(keyframes.size <= 32 && keyframes.keys.all { UNIFORM_NAME_PATTERN.matches(it) && it != "spring" })
        val motion = ThemeMotion(
            durationScale = motionJson.optDouble("durationScale", 1.0).toFloat()
                .also { require(it in 0f..3f) },
            springDampingRatio = motionJson.optDouble("springDampingRatio", 0.82).toFloat()
                .also { require(it in 0.1f..2f) },
            springStiffness = motionJson.optDouble("springStiffness", 420.0).toFloat()
                .also { require(it in 50f..2_000f) },
            keyframes = keyframes,
        )
        val nodeCount = intArrayOf(0)
        val screens = json.optJSONObject("screens").toNameObjectMap().mapValues { (_, value) ->
            parseNode(value, depth = 0, count = nodeCount)
        }
        val iconOverrides = json.optJSONObject("iconOverrides").toStringMap()
        val iconValidation = ThemeIconOverrideValidator.validate(iconOverrides)
        require(iconValidation.isValid) { "Invalid theme icon overrides: ${iconValidation.issues}" }

        fun validateAnimations(node: VisualNode) {
            listOf(
                "alpha",
                "scale",
                "rotation",
                "translationX",
                "translationY",
                "morph"
            ).forEach { property ->
                node.attributes["${property}Animation"]?.let { animation ->
                    require(animation == "spring" || animation in keyframes) { "Unknown keyframe animation: $animation" }
                }
            }
            node.children.forEach(::validateAnimations)
        }
        screens.values.forEach(::validateAnimations)
        fun validateScrollBounds(node: VisualNode, scrolling: Boolean = false) {
            val vertical = node.type in setOf("scroll", "lazyColumn", "lazyGrid")
            val bounded = "heightDp" in node.attributes || "sizeDp" in node.attributes
            require(!scrolling || !vertical || bounded) { "Nested vertical scrolling requires an explicit height" }
            node.children.forEach { child ->
                validateScrollBounds(
                    child,
                    (scrolling || vertical) && !bounded
                )
            }
        }
        screens.values.forEach { validateScrollBounds(it) }
        fun validateBackdrop(node: VisualNode, captured: Boolean = false) {
            require(!captured || (node.attributes["effect"] == null && node.type != "backdrop")) {
                "Backdrop layers cannot contain effects or other backdrop captures"
            }
            node.children.forEach { validateBackdrop(it, captured || node.type == "backdrop") }
        }
        screens.values.forEach { root ->
            var captureCount = 0
            fun countCaptures(node: VisualNode) {
                if (node.type == "backdrop") captureCount++; node.children.forEach(::countCaptures)
            }
            countCaptures(root)
            require(captureCount <= 1) { "A screen can capture one backdrop" }
            validateBackdrop(root)
        }
        require(screens.size <= MAX_SCREEN_COUNT)
        return ThemeDefinition(
            schemaVersion = CURRENT_THEME_CONTRACT_VERSION,
            colorSource = when (json.optString("colorSource", "system")) {
                "system" -> org.shilpo.laboon.theme.contract.ThemeColorSource.SYSTEM
                "artwork" -> org.shilpo.laboon.theme.contract.ThemeColorSource.ARTWORK
                else -> error("Unsupported color source")
            },
            lightColors = json.optJSONObject("lightColors").toColorMap(),
            darkColors = json.optJSONObject("darkColors").toColorMap(),
            shapeDp = json.optJSONObject("shapeDp").toFloatMap().onEach { (_, size) ->
                require(size in 0f..128f)
            },
            typography = typography,
            motion = motion,
            effects = effects,
            screens = screens,
            options = json.optJSONArray("options").toThemeOptions(),
            iconOverrides = iconOverrides,
        )
    }

    private fun parseNode(json: JSONObject, depth: Int, count: IntArray): VisualNode {
        require(depth <= MAX_NODE_DEPTH) { "Visual tree is too deep" }
        count[0]++
        require(count[0] <= MAX_NODE_COUNT) { "Visual tree contains too many nodes" }
        val type = json.getString("type")
        require(type in SUPPORTED_NODE_TYPES) { "Unsupported visual node: $type" }
        val attributes = json.optJSONObject("attributes").toStringMap()
        attributes.values.forEach { require(it.length <= MAX_ATTRIBUTE_LENGTH) }
        if (type == "hostControl") require(attributes["id"] == "themeOptions") { "Unsupported host control" }
        attributes["slot"]?.let { slot ->
            require(slot in ThemeIconSlots.supported) { "Unsupported semantic icon slot: $slot" }
        }
        attributes["symbol"]?.let { symbol ->
            require(symbol in MaterialSymbolCatalog.supported) { "Unsupported Material Symbol: $symbol" }
        }
        val polygon = attributes["polygonPoints"]?.let {
            requireNotNull(
                org.shilpo.laboon.theme.contract.VisualPolygon.parse(it)
            ) { "Invalid polygon" }
        }
        attributes["morphPolygonPoints"]?.let { target ->
            val points =
                requireNotNull(org.shilpo.laboon.theme.contract.VisualPolygon.parse(target)) { "Invalid morph polygon" }
            require(polygon != null && polygon.size == points.size) { "Morph polygons require matching vertex counts" }
        }
        attributes["asset"]?.let(::requireSafeAssetPath)
        attributes["action"]?.let { action ->
            require(VisualThemeAction.fromId(action) != null) { "Unsupported semantic action: $action" }
        }
        attributes["gestureAction"]?.let { action ->
            require(VisualThemeAction.fromId(action) != null) { "Unsupported semantic gesture action: $action" }
        }
        val children = json.optJSONArray("children").toNodeList(depth + 1, count)
        return VisualNode(type = type, attributes = attributes, children = children)
    }

    private fun JSONObject?.toNameObjectMap(): Map<String, JSONObject> = buildMap {
        if (this@toNameObjectMap == null) return@buildMap
        val json = this@toNameObjectMap
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, json.getJSONObject(key))
        }
    }

    private fun JSONObject?.toColorMap(): Map<String, Long> = buildMap {
        if (this@toColorMap == null) return@buildMap
        val json = this@toColorMap
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.getString(key)
            require(COLOR_ROLE_PATTERN.matches(key))
            val color = android.graphics.Color.parseColor(value).toLong() and 0xFFFF_FFFFL
            put(key, color)
        }
    }

    private fun JSONObject?.toFloatMap(): Map<String, Float> = buildMap {
        if (this@toFloatMap == null) return@buildMap
        val json = this@toFloatMap
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.getDouble(key).toFloat()
            require(value.isFinite() && UNIFORM_NAME_PATTERN.matches(key))
            put(key, value)
        }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> = buildMap {
        if (this@toStringMap == null) return@buildMap
        val json = this@toStringMap
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, json.getString(key))
        }
    }

    private fun JSONArray?.toThemeOptions(): List<ThemeOption> {
        if (this == null) return emptyList()
        require(length() <= MAX_OPTION_COUNT)
        val ids = HashSet<String>()
        return List(length()) { index ->
            val item = getJSONObject(index)
            val option = ThemeOption(
                id = item.getString("id"),
                title = item.getString("title"),
                description = item.optString("description"),
                defaultValue = item.getString("defaultValue"),
                type = when (item.optString("type", "choice")) {
                    "switch" -> ThemeOptionType.SWITCH
                    "choice" -> ThemeOptionType.CHOICE
                    "slider" -> ThemeOptionType.SLIDER
                    else -> error("Unsupported theme option type")
                },
                choices = item.optJSONArray("choices").toStringList(),
                minimum = item.optNullableFloat("minimum"),
                maximum = item.optNullableFloat("maximum"),
                step = item.optNullableFloat("step"),
            )
            require(OPTION_ID_PATTERN.matches(option.id) && ids.add(option.id))
            require(option.title.isNotBlank() && option.title.length <= MAX_ATTRIBUTE_LENGTH)
            require(option.choices.size <= MAX_OPTION_COUNT)
            require(option.description.length <= MAX_ATTRIBUTE_LENGTH)
            when (option.type) {
                ThemeOptionType.SWITCH -> require(option.defaultValue == "true" || option.defaultValue == "false")
                ThemeOptionType.CHOICE -> require(option.choices.isNotEmpty() && option.defaultValue in option.choices)
                ThemeOptionType.SLIDER -> {
                    val minimum = requireNotNull(option.minimum)
                    val maximum = requireNotNull(option.maximum)
                    val defaultValue = option.defaultValue.toFloat()
                    require(minimum.isFinite() && maximum.isFinite() && minimum in -100_000f..100_000f)
                    require(maximum > minimum && maximum - minimum <= 100_000f)
                    require(defaultValue.isFinite() && defaultValue in minimum..maximum)
                    val step = option.step
                    require(step == null || (step.isFinite() && step > 0f && step <= maximum - minimum))
                }
            }
            option
        }
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else List(length()) { index -> getString(index) }

    private fun JSONArray?.toNodeList(depth: Int, count: IntArray): List<VisualNode> =
        if (this == null) emptyList() else List(length()) { index ->
            parseNode(
                getJSONObject(index),
                depth,
                count
            )
        }

    private fun JSONObject.optNullableFloat(key: String): Float? =
        if (has(key) && !isNull(key)) getDouble(key).toFloat() else null

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun requireSafeAssetPath(path: String) {
        require(ThemeManifestValidator.isSafeAssetPath(path)) { "Unsafe theme asset path" }
    }

    private fun validateDefinitionAssets(
        definition: ThemeDefinition,
        assets: android.content.res.AssetManager
    ) {
        val imagePaths = HashSet<String>()
        val fontPaths = definition.typography.values.mapNotNull { it.fontAsset }.toSet()
        val paths = buildSet {
            definition.effects.values.forEach { add(it.shaderAsset) }
            definition.typography.values.mapNotNullTo(this) { it.fontAsset }
            fun collect(node: VisualNode) {
                node.attributes["asset"]?.let {
                    add(it)
                    if (node.type == "image") imagePaths.add(it)
                }
                node.children.forEach(::collect)
            }
            definition.screens.values.forEach(::collect)
        }
        require(paths.size <= 128) { "Too many packaged theme assets" }
        var totalBytes = 0L
        var totalImagePixels = 0L
        paths.forEach { assetPath ->
            requireSafeAssetPath(assetPath)
            val bytes = assets.open(assetPath).use { it.readBoundedBytes() }
            totalBytes += bytes.size
            require(totalBytes <= 8 * 1024 * 1024) { "Theme assets exceed the total size limit" }
            if (assetPath in imagePaths) {
                totalImagePixels += themeBitmapPixelCount(bytes)
                require(totalImagePixels <= 16_777_216L) { "Theme images exceed the decoded pixel budget" }
                require(decodeThemeBitmap(bytes) != null) { "Invalid packaged image: $assetPath" }
            }
            if (assetPath in fontPaths) {
                android.graphics.Typeface.createFromAsset(assets, assetPath)
            }
        }
    }

    private fun java.io.InputStream.readBoundedText(): String =
        readBoundedBytes().toString(Charsets.UTF_8)

    private fun java.io.InputStream.readBoundedBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            totalBytes += read
            require(totalBytes <= MAX_ASSET_BYTES) { "Visual theme JSON exceeds the size limit" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val TAG = "VisualThemeRegistry"
        const val ACTION_VISUAL_THEME = "org.shilpo.laboon.action.VISUAL_THEME"
        const val THEME_MANIFEST_METADATA = "org.shilpo.laboon.visualTheme"
        const val PREFERENCES_NAME = "visual_theme"
        const val SELECTED_THEME_KEY = "selected_theme_id"
        const val MAX_ASSET_BYTES = 512 * 1024
        const val MAX_NODE_DEPTH = 24
        const val MAX_NODE_COUNT = 2_000
        const val MAX_ATTRIBUTE_LENGTH = 1_024
        const val MAX_OPTION_COUNT = 32
        const val MAX_SCREEN_COUNT = 64
        const val MAX_EFFECT_COUNT = 16
        const val MAX_TYPOGRAPHY_COUNT = 64
        val COLOR_ROLE_PATTERN = Regex("[A-Za-z][A-Za-z0-9]{0,63}")
        val UNIFORM_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
        val OPTION_ID_PATTERN = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
        val SUPPORTED_NODE_TYPES = setOf(
            "box",
            "column",
            "row",
            "overlay",
            "scroll",
            "horizontalScroll",
            "lazyColumn",
            "lazyRow",
            "lazyGrid",
            "spacer",
            "text",
            "textField",
            "artwork",
            "image",
            "icon",
            "button",
            "iconButton",
            "surface",
            "progress",
            "metadata",
            "control",
            "drawing",
            "hostControl",
            "flowRow",
            "backdrop",
        )
    }
}
