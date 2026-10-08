package org.shilpo.laboon.playback

import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.lyricsporn.LyricspornMotionArtwork

enum class OutputDeviceType {
    PHONE_SPEAKER,
    EARBUDS,
    OVER_EAR,
    BLUETOOTH_SPEAKER,
    USB_DAC,
    CAR_AUDIO,
    MONITOR_HDMI,
    OTHER,
}

data class AudioPipelineDetails(
    val trackId: String? = null,
    val isLocked: Boolean = false,
    val trackCodec: String? = null,
    val container: String? = null,
    val bitDepth: String? = null,
    val sampleRateHz: Int? = null,
    val bitrateKbps: Int? = null,
    val channelCount: Int? = 2,
    val decoderName: String? = null,
    val decodedFormat: String? = null,
    val inputSampleRateHz: Int? = null,
    val outputSampleRateHz: Int? = null,
    val isResampled: Boolean = false,
    val processingMode: String? = null,
    val outputEngine: String = "AudioTrack (Android AudioFlinger)",
    val bufferSizeFrames: Int? = null,
    val latencyMs: Int? = null,
    val deviceName: String = "Built-in Speaker",
    val deviceType: OutputDeviceType = OutputDeviceType.PHONE_SPEAKER,
    val deviceProtocol: String? = null,
)

data class AudioQualityInfo(
    val trackId: String? = null,
    val isLocked: Boolean = false,
    val codec: String,
    val isLossless: Boolean = false,
    val isHiRes: Boolean = false,
    val isDolby: Boolean = false,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val pipelineDetails: AudioPipelineDetails? = null,
)

enum class DiscoveryStatus {
    IDLE,
    LOADING,
    EXHAUSTED,
    FAILED,
}

data class PlaybackState(
    val currentTrack: HomeTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0f,
    val error: String? = null,
    val canSkipNext: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val isDiscovering: Boolean = false,
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.IDLE,
    val switchingQualityFormat: String? = null,
    val audioQuality: AudioQualityInfo? = null,
    val lyricsLines: List<LyricsLine> = emptyList(),
    val lyricsProvider: String? = null,
    val lyricsLoading: Boolean = false,
    val motionArtwork: LyricspornMotionArtwork? = null,
    val motionArtworkTrackId: String? = null,
)
