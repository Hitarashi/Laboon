package org.shilpo.laboon.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import org.shilpo.laboon.theme.renderer.LocalThemeIconOverrides
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import androidx.compose.ui.res.painterResource as androidPainterResource

private data class IconSymbol(
    val slot: String,
    val symbol: String,
    val filled: Boolean = false,
    val preserveDefaultDrawable: Boolean = false,
)

private val symbolsByDrawableName = mapOf(
    "ic_nav_home" to IconSymbol("navigation.home", "home"),
    "ic_nav_home_fill" to IconSymbol("navigation.home", "home", filled = true),
    "ic_nav_search" to IconSymbol("navigation.search", "search"),
    "ic_nav_search_fill" to IconSymbol("navigation.search", "search", filled = true),
    "ic_nav_library" to IconSymbol("navigation.library", "library_music"),
    "ic_nav_library_fill" to IconSymbol("navigation.library", "library_music", filled = true),
    "ic_play" to IconSymbol("playback.play", "play_arrow", filled = true),
    "ic_pause" to IconSymbol("playback.pause", "pause", filled = true),
    "ic_skip" to IconSymbol("playback.skip", "skip_next", filled = true),
    "ic_shuffle" to IconSymbol("playback.shuffle", "shuffle"),
    "ic_repeat" to IconSymbol("playback.repeat", "repeat"),
    "ic_repeat_one" to IconSymbol("playback.repeatOne", "repeat_one", filled = true),
    "ic_player_queue" to IconSymbol("playback.queue", "queue_music"),
    "ic_player_lyrics" to IconSymbol("playback.lyrics", "lyrics"),
    "ic_song_wave" to IconSymbol("playback.waveform", "graphic_eq"),
    "ic_clear" to IconSymbol("action.close", "close"),
    "ic_chevron" to IconSymbol("action.chevron", "expand_more"),
    "ic_chevron_double" to IconSymbol("action.chevronDouble", "keyboard_double_arrow_down"),
    "ic_check" to IconSymbol("action.check", "check"),
    "ic_cloud_download" to IconSymbol("action.download", "cloud_download"),
    "ic_share_album" to IconSymbol("action.share", "share"),
    "ic_stage_link" to IconSymbol("action.openLink", "open_in_new"),
    "ic_clockwise_clock" to IconSymbol("action.history", "history"),
    "ic_info_circle" to IconSymbol("action.info", "info"),
    "ic_eye" to IconSymbol("action.visibility", "visibility"),
    "ic_eye_close" to IconSymbol("action.visibilityOff", "visibility_off"),
    "ic_send" to IconSymbol("action.send", "send"),
    "ic_stepper_plus" to IconSymbol("action.stepPlus", "add"),
    "ic_stepper_minus" to IconSymbol("action.stepMinus", "remove"),
    "ic_lyrics_romanization" to IconSymbol("action.translate", "language"),
    "ic_lyrics_translation" to IconSymbol("action.translate", "translate"),
    "ic_more_vert" to IconSymbol("action.more", "more_vert"),
    "ic_appearance_palette" to IconSymbol("settings.appearance", "palette"),
    "ic_pipeline_resampler" to IconSymbol("pipeline.resampler", "tune"),
    "ic_pipeline_decoder" to IconSymbol("pipeline.decoder", "audio_file"),
    "ic_pipeline_engine" to IconSymbol("pipeline.engine", "settings_suggest"),
    "ic_services_scrobble" to IconSymbol("settings.services", "sync"),
    "ic_perm_notification" to IconSymbol("permission.notifications", "notifications"),
    "ic_perm_storage" to IconSymbol("permission.storage", "audio_file"),
    "ic_perm_bt_connect" to IconSymbol("permission.bluetoothConnect", "bluetooth_connected"),
    "ic_perm_bt_scan" to IconSymbol("permission.bluetoothScan", "bluetooth_searching"),
    "ic_perm_battery" to IconSymbol("permission.battery", "battery_saver"),
    "ic_perm_network" to IconSymbol("permission.network", "wifi"),
    "ic_perm_audio_vibe" to IconSymbol("permission.vibration", "vibration"),
    "ic_perm_install" to IconSymbol("permission.install", "install_mobile"),
    "ic_perm_check_badge" to IconSymbol("permission.granted", "check_circle", filled = true),
    "ic_device_monitor" to IconSymbol("device.monitor", "monitor"),
    "ic_device_car" to IconSymbol("device.car", "directions_car"),
    "ic_device_usb_dac" to IconSymbol("device.usbAudio", "usb"),
    "ic_device_bt_speaker" to IconSymbol("device.bluetoothSpeaker", "bluetooth_audio"),
    "ic_device_speaker" to IconSymbol("device.speaker", "speaker"),
    "ic_device_earbuds" to IconSymbol("device.earbuds", "earbuds"),
    "ic_device_over_ear" to IconSymbol("device.headphones", "headphones"),
    "ic_codec_hires" to IconSymbol(
        "codec.highResolution",
        "high_res",
        preserveDefaultDrawable = true
    ),
    "ic_codec_lossless" to IconSymbol(
        "codec.lossless",
        "audio_file",
        preserveDefaultDrawable = true
    ),
    "ic_codec_dolby" to IconSymbol(
        "codec.surround",
        "surround_sound",
        preserveDefaultDrawable = true
    ),
    "ic_stage_archive" to IconSymbol("rip.archive", "archive"),
    "ic_stage_decrypt" to IconSymbol("rip.decrypt", "lock_open"),
    "ic_stage_flash" to IconSymbol("rip.flash", "flash_on"),
    "ic_stage_hourglass" to IconSymbol("rip.hourglass", "hourglass_top"),
    "ic_stage_resolving" to IconSymbol("rip.resolving", "search"),
    "ic_stage_tag" to IconSymbol("rip.tag", "sell"),
    "ic_user_headshot" to IconSymbol("account.profile", "person", filled = true),
)

/**
 * App-wide painter entry point. Known action and status drawables resolve to Material Symbols and
 * the active extension's semantic override. Logos and codec marks retain their bundled art unless
 * an extension explicitly overrides a codec slot.
 */
@Composable
fun painterResource(@DrawableRes id: Int): Painter {
    val context = LocalContext.current
    val resourceName = context.resources.getResourceEntryName(id)
    val icon = symbolsByDrawableName[resourceName] ?: return androidPainterResource(id)
    if (icon.preserveDefaultDrawable && icon.slot !in LocalThemeIconOverrides.current) {
        return androidPainterResource(id)
    }
    return materialSymbolPainterResource(
        name = icon.symbol,
        slot = icon.slot,
        filled = icon.filled,
    )
}
