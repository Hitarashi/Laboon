package org.shilpo.laboon.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.AudioPipelineDetails
import org.shilpo.laboon.playback.OutputDeviceType
import org.shilpo.laboon.ui.design.MiniPlayerSpacing

@Composable
internal fun AudioInfoDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    pipeline: AudioPipelineDetails?,
    track: HomeTrack? = null,
    durationMs: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val theme = org.shilpo.laboon.theme.LocalVisualTheme.current
    if (theme?.definition?.screens?.containsKey("audioInfo") == true) {
        if (!isOpen) return
        BackHandler(onBack = onDismiss)
        val details = pipeline ?: AudioPipelineDetails()
        org.shilpo.laboon.theme.ThemeRouteContent(
            theme = theme,
            screenName = "audioInfo",
            presentation = org.shilpo.laboon.theme.renderer.VisualThemePresentation(
                values = mapOf(
                    "track.title" to track?.title.orEmpty(),
                    "track.artist" to track?.artist.orEmpty(),
                    "track.artworkUrl" to track?.artworkUrl.orEmpty(),
                    "audio.codec" to details.trackCodec.orEmpty(),
                    "audio.container" to details.container.orEmpty(),
                    "audio.bitDepth" to details.bitDepth.orEmpty(),
                    "audio.sampleRateHz" to details.sampleRateHz?.toString().orEmpty(),
                    "audio.bitrateKbps" to details.bitrateKbps?.toString().orEmpty(),
                    "audio.channels" to details.channelCount?.toString().orEmpty(),
                    "audio.decoder" to details.decoderName.orEmpty(),
                    "audio.outputEngine" to details.outputEngine,
                    "audio.device" to details.deviceName,
                    "audio.protocol" to details.deviceProtocol.orEmpty(),
                    "audio.latencyMs" to details.latencyMs?.toString().orEmpty(),
                    "audio.resampled" to details.isResampled.toString(),
                )
            ),
            availableActions = setOf(org.shilpo.laboon.theme.contract.VisualThemeAction.BACK),
            onAction = { _, _ -> onDismiss() },
            modifier = modifier.fillMaxSize(),
            fallback = {},
        )
        return
    }
    if (!isOpen) return

    val details = pipeline ?: AudioPipelineDetails()
    val hasTrack = track != null
    val headerShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 12.dp,
        bottomEnd = 12.dp,
    )
    val detailsShape = RoundedCornerShape(
        topStart = if (hasTrack) 12.dp else 28.dp,
        topEnd = if (hasTrack) 12.dp else 28.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp,
    )
    var dialogBounds by remember { mutableStateOf<Rect?>(null) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    rootCoordinates = coordinates
                }
                .pointerInput(dialogBounds, rootCoordinates) {
                    detectTapGestures { tapPosition ->
                        val positionInRoot = rootCoordinates?.localToRoot(tapPosition)
                        if (positionInRoot != null && dialogBounds?.contains(positionInRoot) != true) {
                            onDismiss()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.9f)
                    .onGloballyPositioned { coordinates ->
                        dialogBounds = coordinates.boundsInRoot()
                    }
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MiniPlayerSpacing),
            ) {
                if (track != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = headerShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 1.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (!track.artworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = track.artworkUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_song_wave),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle = buildString {
                                    append(track.artist)
                                    if (!track.album.isNullOrBlank()) {
                                        append(" • ")
                                        append(track.album)
                                    }
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (durationMs > 0L) {
                                    Text(
                                        text = formatDuration(durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = detailsShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                    ) {
                        val isPipelineLocked = details.isLocked &&
                                (track == null || details.trackId == null || details.trackId == track.id)
                        val rawCodec = details.trackCodec
                            ?.takeIf { it != "AUDIO" && it != "HIRES" && it != "HI-RES" }
                            ?: when {
                                track?.codec?.contains("alac", ignoreCase = true) == true -> "ALAC"
                                track?.codec?.contains("flac", ignoreCase = true) == true ||
                                        track?.codec?.contains("lossless", ignoreCase = true) == true ||
                                        track?.codec?.contains("hires", ignoreCase = true) == true -> "FLAC"
                                track?.codec?.contains("aac", ignoreCase = true) == true ||
                                        track?.codec?.contains("mp4a", ignoreCase = true) == true -> "AAC"
                                track?.codec?.contains("opus", ignoreCase = true) == true -> "OPUS"
                                track?.codec?.contains("mp3", ignoreCase = true) == true -> "MP3"
                                else -> "FLAC"
                            }

                        val effectiveBitDepth = details.bitDepth?.takeIf { it != "Float32" }
                            ?: if (details.decodedFormat?.contains("24", ignoreCase = true) == true ||
                                details.decodedFormat?.contains("Float", ignoreCase = true) == true
                            ) "24-bit" else "16-bit"
                        val effectiveSr = details.sampleRateHz ?: details.inputSampleRateHz ?: 44100
                        val effectiveKbps = details.bitrateKbps
                            ?: ((effectiveSr * (if (effectiveBitDepth.contains("24")) 24 else 16) *
                                    (details.channelCount ?: 2) * 0.62) / 1000).toInt()

                        val trackTitle = if (isPipelineLocked) {
                            val khz = effectiveSr / 1000f
                            val formattedRate = if (khz % 1f == 0f) {
                                "${khz.toInt()} kHz"
                            } else {
                                "%.1f kHz".format(khz)
                            }
                            "$rawCodec • $effectiveBitDepth • $formattedRate • $effectiveKbps kbps"
                        } else {
                            "$rawCodec • Analyzing stream..."
                        }
                        val trackSubtitle = buildString {
                            val channels = details.channelCount ?: 2
                            append(if (channels > 2) "$channels Channels" else "2 Channels")
                            val container = details.container
                                ?.takeIf { it !in setOf("AUDIO", "HIRES", "HI-RES") }
                                ?: rawCodec
                            append(" • $container")
                        }
                        PipelineStageItem(
                            iconRes = R.drawable.ic_song_wave,
                            title = "Track Stream",
                            primaryLine = trackTitle,
                            secondaryLine = trackSubtitle,
                            isLast = false,
                        )

                        PipelineStageItem(
                            iconRes = R.drawable.ic_pipeline_decoder,
                            title = "Decoder",
                            primaryLine = if (isPipelineLocked) {
                                details.decoderName ?: "MediaCodec Decoder"
                            } else {
                                "Initializing decoder..."
                            },
                            secondaryLine = if (isPipelineLocked) {
                                details.decodedFormat ?: "PCM 16-bit"
                            } else {
                                "Waiting for audio sink..."
                            },
                            isLast = false,
                        )

                        val inputRate = details.inputSampleRateHz ?: 44100
                        val outputRate = details.outputSampleRateHz ?: 48000
                        val inputKhz = formatSampleRate(inputRate)
                        val outputKhz = formatSampleRate(outputRate)
                        val resampleText = if (inputRate == outputRate) {
                            "Bit-perfect $inputKhz"
                        } else {
                            "$inputKhz → $outputKhz"
                        }
                        PipelineStageItem(
                            iconRes = R.drawable.ic_pipeline_resampler,
                            title = "Resampler & Processing",
                            primaryLine = if (isPipelineLocked) {
                                resampleText
                            } else {
                                "Configuring audio pipeline..."
                            },
                            secondaryLine = details.processingMode
                                ?: "DefaultAudioSink • Float32 Output",
                            isLast = false,
                        )

                        val bufferText = if (isPipelineLocked) {
                            "Buffer ~${details.latencyMs ?: 64}ms (${details.bufferSizeFrames ?: 3072} frames)"
                        } else {
                            "AudioTrack buffer configuring..."
                        }
                        PipelineStageItem(
                            iconRes = R.drawable.ic_pipeline_engine,
                            title = "Output Engine",
                            primaryLine = details.outputEngine,
                            secondaryLine = bufferText,
                            isLast = false,
                        )

                        val deviceIconRes = when (details.deviceType) {
                            OutputDeviceType.OVER_EAR -> R.drawable.ic_device_over_ear
                            OutputDeviceType.EARBUDS -> R.drawable.ic_device_earbuds
                            OutputDeviceType.PHONE_SPEAKER -> R.drawable.ic_device_speaker
                            OutputDeviceType.BLUETOOTH_SPEAKER -> R.drawable.ic_device_bt_speaker
                            OutputDeviceType.USB_DAC -> R.drawable.ic_device_usb_dac
                            OutputDeviceType.CAR_AUDIO -> R.drawable.ic_device_car
                            OutputDeviceType.MONITOR_HDMI -> R.drawable.ic_device_monitor
                            OutputDeviceType.OTHER -> R.drawable.ic_device_speaker
                        }
                        PipelineStageItem(
                            iconRes = deviceIconRes,
                            title = "Output Device",
                            primaryLine = details.deviceName,
                            secondaryLine = details.deviceProtocol ?: "Default Audio Device",
                            isLast = true,
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private fun formatSampleRate(sampleRateHz: Int): String {
    val khz = sampleRateHz / 1000f
    return if (khz % 1f == 0f) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
}

@Composable
private fun PipelineStageItem(
    iconRes: Int,
    title: String,
    primaryLine: String,
    secondaryLine: String,
    isLast: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = title,
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (!isLast) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(colors.outlineVariant, RoundedCornerShape(1.dp))
                )
                Spacer(modifier = Modifier.height(3.dp))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                letterSpacing = (-0.1).sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = primaryLine,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Text(
                text = secondaryLine,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
