package org.shilpo.laboon.search

import android.media.MediaCodecList
import android.media.MediaFormat

/** Ignore the bundled FFmpeg fallback when deciding whether ec-3 is a device-native default. */
internal object PlatformAudioCodecSupport {
    private val dolbySupported by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codecInfo ->
                !codecInfo.isEncoder && runCatching {
                    codecInfo.supportedTypes.any { mimeType ->
                        mimeType.equals(MediaFormat.MIMETYPE_AUDIO_EAC3, ignoreCase = true)
                    }
                }.getOrDefault(false)
            }
        }.getOrDefault(false)
    }

    fun supportsDolby(): Boolean = dolbySupported
}
