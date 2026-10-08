package org.shilpo.laboon.theme.contract

const val CURRENT_THEME_CONTRACT_VERSION: Int = 1
const val MINIMUM_SUPPORTED_ANDROID_API: Int = 29

/** Metadata an installed visual extension publishes in its packaged manifest resource. */
data class ThemeManifest(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val contractVersion: Int,
    val minimumAndroidApi: Int,
    val definitionAsset: String,
)

data class ThemeDefinition(
    val schemaVersion: Int,
    val colorSource: ThemeColorSource = ThemeColorSource.SYSTEM,
    val lightColors: Map<String, Long> = emptyMap(),
    val darkColors: Map<String, Long> = emptyMap(),
    val shapeDp: Map<String, Float> = emptyMap(),
    val typography: Map<String, ThemeTextStyle> = emptyMap(),
    val motion: ThemeMotion = ThemeMotion(),
    val effects: Map<String, ThemeEffect> = emptyMap(),
    val screens: Map<String, VisualNode> = emptyMap(),
    val options: List<ThemeOption> = emptyList(),
    /** Maps stable Laboon icon slots to names from the bundled Material Symbols Rounded catalog. */
    val iconOverrides: Map<String, String> = emptyMap(),
)

/** Stable icon slots used by Laboon-owned UI. Extensions replace glyphs without replacing actions. */
object ThemeIconSlots {
    val supported: Set<String> = setOf(
        "navigation.home",
        "navigation.search",
        "navigation.library",
        "playback.play",
        "playback.pause",
        "playback.skip",
        "playback.previous",
        "playback.shuffle",
        "playback.repeat",
        "playback.repeatOne",
        "playback.queue",
        "playback.lyrics",
        "playback.favorite",
        "playback.playlistAdd",
        "playback.waveform",
        "media.artwork",
        "metadata.duration",
        "metadata.album",
        "metadata.artist",
        "metadata.source",
        "action.more",
        "action.chevron",
        "action.chevronDouble",
        "action.back",
        "action.forward",
        "action.edit",
        "action.close",
        "action.check",
        "action.download",
        "action.share",
        "action.openLink",
        "action.history",
        "action.info",
        "action.options",
        "action.delete",
        "action.setTone",
        "action.lookup",
        "action.visibility",
        "action.visibilityOff",
        "action.send",
        "action.add",
        "action.remove",
        "action.translate",
        "action.stepMinus",
        "action.stepPlus",
        "action.refresh",
        "queue.playNext",
        "queue.add",
        "queue.moveEarlier",
        "queue.moveLater",
        "queue.promote",
        "queue.remove",
        "settings.appearance",
        "settings.audioProcessing",
        "settings.lyrics",
        "settings.storage",
        "settings.about",
        "settings.services",
        "permission.notifications",
        "permission.storage",
        "permission.bluetoothConnect",
        "permission.bluetoothScan",
        "permission.battery",
        "permission.network",
        "permission.vibration",
        "permission.install",
        "permission.granted",
        "device.monitor",
        "device.car",
        "device.usbAudio",
        "device.bluetoothSpeaker",
        "device.speaker",
        "device.earbuds",
        "device.headphones",
        "device.other",
        "pipeline.decoder",
        "pipeline.engine",
        "pipeline.resampler",
        "rip.archive",
        "rip.decrypt",
        "rip.flash",
        "rip.hourglass",
        "rip.link",
        "rip.resolving",
        "rip.tag",
        "codec.highResolution",
        "codec.lossless",
        "codec.surround",
        "account.profile",
    )
}

/** Material Symbols included in the host's compact, offline Rounded icon font subset. */
object MaterialSymbolCatalog {
    val supported: Set<String> = setOf(
        "home",
        "search",
        "library_music",
        "play_arrow",
        "pause",
        "skip_next",
        "skip_previous",
        "shuffle",
        "repeat",
        "repeat_one",
        "more_vert",
        "expand_more",
        "expand_less",
        "chevron_left",
        "chevron_right",
        "close",
        "check",
        "cloud_download",
        "share",
        "open_in_new",
        "history",
        "info",
        "visibility",
        "visibility_off",
        "lyrics",
        "queue_music",
        "graphic_eq",
        "add",
        "remove",
        "translate",
        "send",
        "palette",
        "tune",
        "hard_drive",
        "description",
        "notifications",
        "folder_open",
        "bluetooth_connected",
        "bluetooth_searching",
        "battery_saver",
        "wifi",
        "vibration",
        "check_circle",
        "headphones",
        "earbuds",
        "speaker",
        "bluetooth_audio",
        "usb",
        "directions_car",
        "monitor",
        "equalizer",
        "music_note",
        "album",
        "person",
        "settings",
        "audio_file",
        "timer",
        "high_res",
        "surround_sound",
        "network_cell",
        "download",
        "playlist_play",
        "playlist_add",
        "manage_search",
        "mic",
        "volume_up",
        "language",
        "refresh",
        "delete",
        "delete_forever",
        "bookmark",
        "favorite",
        "queue_play_next",
        "play_circle",
        "pause_circle",
        "stop_circle",
        "arrow_back",
        "arrow_forward",
        "arrow_back_ios_new",
        "logout",
        "auto_awesome",
        "music_off",
        "sort",
        "filter_list",
        "search_off",
        "library_books",
        "menu",
        "cloud",
        "launch",
        "edit",
        "library_add",
        "battery_charging_full",
        "notifications_active",
        "storage",
        "signal_cellular_alt",
        "touch_app",
        "system_update_alt",
        "settings_suggest",
        "link",
        "bolt",
        "schedule",
        "travel_explore",
        "history_toggle_off",
        "speed",
        "compare_arrows",
        "local_library",
        "person_search",
        "devices",
        "other_houses",
        "archive",
        "lock_open",
        "flash_on",
        "hourglass_top",
        "sell",
        "keyboard_double_arrow_down",
        "keyboard_arrow_up",
        "keyboard_arrow_down",
        "star",
        "install_mobile",
        "file_download",
        "sync",
        "arrow_upward",
        "arrow_downward",
        "fast_forward",
        "library_add_check",
        "track_changes",
    )
}

const val MAX_THEME_ICON_OVERRIDES: Int = 128

enum class ThemeIconOverrideIssue { TooManyOverrides, UnknownSlot, UnknownSymbol }

data class ThemeIconOverrideValidation(val issues: Set<ThemeIconOverrideIssue>) {
    val isValid: Boolean get() = issues.isEmpty()
}

object ThemeIconOverrideValidator {
    fun validate(overrides: Map<String, String>): ThemeIconOverrideValidation =
        ThemeIconOverrideValidation(buildSet {
            if (overrides.size > MAX_THEME_ICON_OVERRIDES) {
                add(ThemeIconOverrideIssue.TooManyOverrides)
            }
            if (overrides.keys.any { it !in ThemeIconSlots.supported }) {
                add(ThemeIconOverrideIssue.UnknownSlot)
            }
            if (overrides.values.any { it !in MaterialSymbolCatalog.supported }) {
                add(ThemeIconOverrideIssue.UnknownSymbol)
            }
        })
}

enum class ThemeColorSource { SYSTEM, ARTWORK }

data class ThemeTextStyle(
    val fontAsset: String? = null,
    val sizeSp: Float? = null,
    val weight: Int? = null,
    val letterSpacingSp: Float? = null,
)

data class ThemeMotion(
    val durationScale: Float = 1f,
    val springDampingRatio: Float = 0.82f,
    val springStiffness: Float = 420f,
    val keyframes: Map<String, ThemeKeyframeAnimation> = emptyMap(),
)

/** Normalized progress from the current property value to its new host-bound value. */
data class ThemeKeyframe(val fraction: Float, val progress: Float)

data class ThemeKeyframeAnimation(
    val durationMs: Int,
    val frames: List<ThemeKeyframe>,
)

object ThemeKeyframeValidator {
    fun isValid(animation: ThemeKeyframeAnimation): Boolean =
        animation.durationMs in 1..10_000 && animation.frames.size in 2..64 &&
                animation.frames.first() == ThemeKeyframe(0f, 0f) &&
                animation.frames.last() == ThemeKeyframe(1f, 1f) &&
                animation.frames.all { it.fraction in 0f..1f && it.progress in -1f..2f } &&
                animation.frames.zipWithNext()
                    .all { (first, next) -> first.fraction < next.fraction }
}

data class ThemeEffect(
    val shaderAsset: String,
    val uniforms: Map<String, Float> = emptyMap(),
)

data class ThemeOption(
    val id: String,
    val title: String,
    val description: String = "",
    val defaultValue: String,
    val type: ThemeOptionType = ThemeOptionType.CHOICE,
    val choices: List<String> = emptyList(),
    val minimum: Float? = null,
    val maximum: Float? = null,
    val step: Float? = null,
)

enum class ThemeOptionType { SWITCH, CHOICE, SLIDER }

/** Semantic actions a visual definition may request. The host decides which are available. */
enum class VisualThemeAction(val id: String) {
    BACK("back"),
    CONTINUE("continue"),
    OPEN_TELEGRAM("openTelegram"),
    CONNECT_LAST_FM("connectLastFm"),
    CONNECT_LISTENBRAINZ("connectListenBrainz"),
    REQUEST_PERMISSION("requestPermission"),
    OPEN_SETTINGS("openSettings"),
    OPEN_SETTINGS_CATEGORY("openSettingsCategory"),
    OPEN_HOME("openHome"),
    OPEN_SEARCH("openSearch"),
    OPEN_LIBRARY("openLibrary"),
    PLAY_PAUSE("playPause"),
    OPEN_PLAYER("openPlayer"),
    PLAY_TRACK("playTrack"),
    PLAY_NEXT("playNext"),
    ADD_TO_QUEUE("addToQueue"),
    OPEN_ALBUM("openAlbum"),
    OPEN_ARTIST("openArtist"),
    RIP_TRACK("ripTrack"),
    TOGGLE_SHUFFLE("toggleShuffle"),
    CYCLE_REPEAT("cycleRepeat"),
    PREVIOUS_TRACK("previousTrack"),
    NEXT_TRACK("nextTrack"),
    SEEK("seek"),
    OPEN_QUEUE("openQueue"),
    OPEN_LYRICS("openLyrics"),
    OPEN_AUDIO_INFO("openAudioInfo"),
    OPEN_LYRICS_SHARE("openLyricsShare"),
    TOGGLE_LYRIC_LINE("toggleLyricLine"),
    SET_LYRICS_SHARE_OPTION("setLyricsShareOption"),
    COPY_LYRICS("copyLyrics"),
    SHARE_LYRICS("shareLyrics"),
    REFRESH("refresh"),
    RETRY("retry"),
    DISMISS_PLAYER("dismissPlayer"),
    OPEN_THEME_PICKER("openThemePicker"),
    PREVIEW_THEME("previewTheme"),
    APPLY_THEME("applyTheme"),
    RESTORE_DEFAULT("restoreDefault"),
    SIGN_OUT("signOut"),
    SELECT_AUDIO_QUALITY("selectAudioQuality"),
    SEARCH("search"),
    OPEN_VISUALIZER("openVisualizer"),
    CANCEL_RIP_TASK("cancelRipTask"),
    PLAY_QUEUE_ENTRY("playQueueEntry"),
    REMOVE_QUEUE_ENTRY("removeQueueEntry"),
    MOVE_QUEUE_ENTRY("moveQueueEntry"),
    CLEAR_QUEUE("clearQueue"),
    PROMOTE_AUTOPLAY("promoteAutoplay"),
    RETRY_DISCOVERY("retryDiscovery"),
    OPEN_PROJECT_LINK("openProjectLink"),
    OPEN_CONTRIBUTOR_PROFILE("openContributorProfile"),
    COPY_BUILD_INFO("copyBuildInfo");

    companion object {
        fun fromId(id: String): VisualThemeAction? = entries.firstOrNull { it.id == id }
    }
}

/** Bounded, declarative node tree. Node types and bindings are interpreted by the host. */
data class VisualNode(
    val type: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<VisualNode> = emptyList(),
)

enum class ThemeManifestIssue {
    InvalidId,
    EmptyName,
    EmptyAuthor,
    EmptyVersion,
    UnsupportedContractVersion,
    InvalidMinimumAndroidApi,
    UnsupportedAndroidVersion,
    MetadataTooLong,
    UnsafeDefinitionAsset,
}

data class ThemeManifestValidation(
    val issues: Set<ThemeManifestIssue>,
) {
    val isValid: Boolean get() = issues.isEmpty()
}

/** Validates the small public contract before a host opens any extension assets. */
object ThemeManifestValidator {
    private val themeIdPattern = Regex("[A-Za-z][A-Za-z0-9_.-]{2,127}")
    private val safeAssetPathPattern = Regex("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)*")

    fun validate(
        manifest: ThemeManifest,
        androidApi: Int,
        supportedContractVersion: Int = CURRENT_THEME_CONTRACT_VERSION,
    ): ThemeManifestValidation {
        val issues = buildSet {
            if (!themeIdPattern.matches(manifest.id)) add(ThemeManifestIssue.InvalidId)
            if (manifest.name.isBlank()) add(ThemeManifestIssue.EmptyName)
            if (manifest.author.isBlank()) add(ThemeManifestIssue.EmptyAuthor)
            if (manifest.version.isBlank()) add(ThemeManifestIssue.EmptyVersion)
            if (manifest.name.length > 1_024 || manifest.author.length > 1_024 || manifest.version.length > 1_024) {
                add(ThemeManifestIssue.MetadataTooLong)
            }
            if (manifest.contractVersion != supportedContractVersion) {
                add(ThemeManifestIssue.UnsupportedContractVersion)
            }
            if (manifest.minimumAndroidApi < MINIMUM_SUPPORTED_ANDROID_API) {
                add(ThemeManifestIssue.InvalidMinimumAndroidApi)
            } else if (androidApi < manifest.minimumAndroidApi) {
                add(ThemeManifestIssue.UnsupportedAndroidVersion)
            }
            if (!isSafeAssetPath(manifest.definitionAsset)) {
                add(ThemeManifestIssue.UnsafeDefinitionAsset)
            }
        }
        return ThemeManifestValidation(issues)
    }

    fun isSafeAssetPath(path: String): Boolean =
        safeAssetPathPattern.matches(path) && path.split('/').none { it == "." || it == ".." }
}
