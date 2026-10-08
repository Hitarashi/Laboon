package org.shilpo.laboon.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.AudioPipelineDetails
import org.shilpo.laboon.playback.OutputDeviceType
import org.shilpo.laboon.ui.design.LiquidGlassBackdropState
import org.shilpo.laboon.ui.design.LiquidGlassSurface
import org.shilpo.laboon.ui.design.MiniPlayerSpacing
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp as lerpFloat

@Composable
internal fun AudioInfoDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    pipeline: AudioPipelineDetails?,
    track: HomeTrack? = null,
    durationMs: Long = 0L,
    originBounds: Rect? = null,
    backdropState: LiquidGlassBackdropState? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
    onProgress: ((Float) -> Unit)? = null,
) {
    val theme = org.shilpo.laboon.theme.LocalVisualTheme.current
    if (theme?.definition?.screens?.containsKey("audioInfo") == true) {
        if (!isOpen) return
        BackHandler(onBack = onDismiss)
        LaunchedEffect(isOpen) { onProgress?.invoke(1f) }
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
            onAction = { _, _ -> onDismiss() }, modifier = modifier.fillMaxSize(), fallback = {},
        )
        return
    }
    if (theme?.definition?.screens?.containsKey("audioInfo") != true) {
        if (!isOpen) return
        LaunchedEffect(isOpen) { onProgress?.invoke(1f) }
        val details = pipeline ?: AudioPipelineDetails()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Audio pipeline") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    track?.let { Text(it.title, style = MaterialTheme.typography.titleMedium) }
                    Text("${details.trackCodec.orEmpty()} · ${details.bitDepth.orEmpty()} bit · ${details.sampleRateHz ?: 0} Hz")
                    Text("${details.container.orEmpty()} · ${details.bitrateKbps ?: 0} kbps")
                    Text("Decoder: ${details.decoderName.orEmpty()}")
                    Text(details.outputEngine)
                    Text(details.deviceName)
                    Text("Output: ${details.outputSampleRateHz ?: 0} Hz · ${details.channelCount ?: 0} channels")
                    Text("Latency: ${details.latencyMs ?: 0} ms")
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } },
        )
        return
    }
    val animatable = remember { Animatable(0f) }
    var dialogSize by remember { mutableStateOf<IntSize?>(null) }
    var boxBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(isOpen, dialogSize != null, boxBounds != null) {
        if (isOpen) {
            if (dialogSize != null && boxBounds != null) {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = 380f,
                    ),
                )
            }
        } else {
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 200,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    val progress = animatable.value

    LaunchedEffect(progress) {
        onProgress?.invoke(progress)
    }

    if (!isOpen && progress <= 0.001f) return

    BackHandler(enabled = isOpen) {
        onDismiss()
    }

    val density = LocalDensity.current
    val startRadius = with(density) {
        originBounds?.let { (it.height / 2f).toDp() } ?: 14.dp
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .onGloballyPositioned { coordinates ->
                boxBounds = coordinates.boundsInRoot()
            },
        contentAlignment = Alignment.Center,
    ) {
        val details = pipeline ?: AudioPipelineDetails()
        val targetCenter = boxBounds?.center ?: originBounds?.center ?: Offset.Zero
        val targetW = dialogSize?.width?.toFloat() ?: with(density) { 380.dp.toPx() }
        val targetH = dialogSize?.height?.toFloat() ?: with(density) { 500.dp.toPx() }

        val scaleX: Float
        val scaleY: Float
        val transX: Float
        val transY: Float

        if (originBounds != null && boxBounds != null && dialogSize != null) {
            val originCenter = originBounds.center
            val originW = originBounds.width
            val originH = originBounds.height

            scaleX = lerpFloat(originW / targetW, 1f, progress)
            scaleY = lerpFloat(originH / targetH, 1f, progress)
            transX = lerpFloat(originCenter.x - targetCenter.x, 0f, progress)
            transY = lerpFloat(originCenter.y - targetCenter.y, 0f, progress)
        } else {
            scaleX = lerpFloat(0.70f, 1f, progress)
            scaleY = lerpFloat(0.70f, 1f, progress)
            transX = 0f
            transY = lerpFloat(with(density) { 90.dp.toPx() }, 0f, progress)
        }

        val contentAlpha = ((progress - 0.20f) / 0.80f).coerceIn(0f, 1f)
        val dialogAlpha = if (originBounds != null) {
            1f
        } else {
            ((progress - 0.06f) / 0.18f).coerceIn(0f, 1f)
        }

        val hasTrack = track != null
        val headerRadius = lerpDp(startRadius, 28.dp, progress)
        val segmentRadius = lerpDp(startRadius, 12.dp, progress)
        val detailsRadius = lerpDp(startRadius, 35.dp, progress)
        val headerShape = RoundedCornerShape(
            topStart = headerRadius,
            topEnd = headerRadius,
            bottomStart = segmentRadius,
            bottomEnd = segmentRadius,
        )
        val detailsShape = RoundedCornerShape(
            topStart = if (hasTrack) segmentRadius else detailsRadius,
            topEnd = if (hasTrack) segmentRadius else detailsRadius,
            bottomStart = detailsRadius,
            bottomEnd = detailsRadius,
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .onSizeChanged { size ->
                    dialogSize = size
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    this.translationX = transX
                    this.translationY = transY
                    this.alpha = dialogAlpha
                },
            verticalArrangement = Arrangement.spacedBy(MiniPlayerSpacing),
        ) {
            if (hasTrack) {
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backdropState = backdropState,
                    shape = headerShape,
                    cornerRadius = headerRadius,
                    topRadius = headerRadius,
                    bottomRadius = segmentRadius,
                    tintColor = MaterialTheme.colorScheme.surfaceContainer,
                    tintAlpha = 0.85f,
                    shadowElevation = lerpDp(2.dp, 6.dp, progress),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .graphicsLayer { alpha = contentAlpha },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                        .background(
                                            if (isDark) Color.White.copy(alpha = 0.08f)
                                            else Color.Black.copy(alpha = 0.05f)
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_song_wave),
                                        contentDescription = null,
                                        tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(
                                            alpha = 0.6f
                                        ),
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
                                    color = if (isDark) Color.White else Color(0xFF191C1E),
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
                                    color = if (isDark) Color.White.copy(alpha = 0.70f) else Color(
                                        0xFF43474E
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (durationMs > 0L) {
                                    Text(
                                        text = formatDuration(durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDark) Color.White.copy(alpha = 0.50f) else Color(
                                            0xFF5A5D63
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                backdropState = backdropState,
                shape = detailsShape,
                cornerRadius = detailsRadius,
                topRadius = if (hasTrack) segmentRadius else detailsRadius,
                bottomRadius = detailsRadius,
                tintColor = MaterialTheme.colorScheme.surfaceContainer,
                tintAlpha = 0.85f,
                shadowElevation = lerpDp(2.dp, 6.dp, progress),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .graphicsLayer { alpha = contentAlpha },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val isPipelineLocked =
                            details.isLocked && (track == null || details.trackId == null || details.trackId == track.id)
                        val rawCodec =
                            details.trackCodec?.takeIf { it != "AUDIO" && it != "HIRES" && it != "HI-RES" }
                                ?: when {
                                    track?.codec?.contains(
                                        "alac",
                                        ignoreCase = true
                                    ) == true -> "ALAC"

                                    track?.codec?.contains("flac", ignoreCase = true) == true ||
                                            track?.codec?.contains(
                                                "lossless",
                                                ignoreCase = true
                                            ) == true ||
                                            track?.codec?.contains(
                                                "hires",
                                                ignoreCase = true
                                            ) == true -> "FLAC"

                                    track?.codec?.contains(
                                        "aac",
                                        ignoreCase = true
                                    ) == true || track?.codec?.contains(
                                        "mp4a",
                                        ignoreCase = true
                                    ) == true -> "AAC"

                                    track?.codec?.contains(
                                        "opus",
                                        ignoreCase = true
                                    ) == true -> "OPUS"

                                    track?.codec?.contains(
                                        "mp3",
                                        ignoreCase = true
                                    ) == true -> "MP3"

                                    else -> "FLAC"
                                }

                        val effectiveBitDepth = details.bitDepth?.takeIf { it != "Float32" }
                            ?: if (details.decodedFormat?.contains(
                                    "24",
                                    ignoreCase = true
                                ) == true
                            ) "24-bit"
                            else if (details.decodedFormat?.contains(
                                    "Float",
                                    ignoreCase = true
                                ) == true
                            ) "24-bit"
                            else "16-bit"
                        val effectiveSr = details.sampleRateHz ?: details.inputSampleRateHz ?: 44100
                        val effectiveKbps = details.bitrateKbps
                            ?: ((effectiveSr * (if (effectiveBitDepth.contains("24")) 24 else 16) * (details.channelCount
                                ?: 2) * 0.62) / 1000).toInt()

                        val trackTitle = if (isPipelineLocked) {
                            buildString {
                                append(rawCodec)
                                append(" • ")
                                append(effectiveBitDepth)
                                val khz = effectiveSr / 1000f
                                val formatted =
                                    if (khz % 1.0f == 0.0f) "${khz.toInt()} kHz" else "%.1f kHz".format(
                                        khz
                                    )
                                append(" • ")
                                append(formatted)
                                append(" • ")
                                append("$effectiveKbps kbps")
                            }
                        } else {
                            "$rawCodec • Analyzing stream..."
                        }
                        val trackSubtitle = buildString {
                            val ch = details.channelCount ?: 2
                            append(if (ch > 2) "$ch Channels" else "2 Channels")
                            val cont =
                                details.container?.takeIf { it != "AUDIO" && it != "HIRES" && it != "HI-RES" }
                                    ?: rawCodec
                            append(" • $cont")
                        }
                        PipelineStageItem(
                            iconRes = R.drawable.ic_song_wave,
                            title = "Track Stream",
                            primaryLine = trackTitle,
                            secondaryLine = trackSubtitle,
                            isLast = false,
                            isDark = isDark,
                        )

                        PipelineStageItem(
                            iconRes = R.drawable.ic_pipeline_decoder,
                            title = "Decoder",
                            primaryLine = if (isPipelineLocked) (details.decoderName
                                ?: "MediaCodec Decoder") else "Initializing decoder...",
                            secondaryLine = if (isPipelineLocked) (details.decodedFormat
                                ?: "PCM 16-bit") else "Waiting for audio sink...",
                            isLast = false,
                            isDark = isDark,
                        )

                        val inSr = details.inputSampleRateHz ?: 44100
                        val outSr = details.outputSampleRateHz ?: 48000
                        val inKhz =
                            if ((inSr / 1000f) % 1.0f == 0f) "${inSr / 1000} kHz" else "%.1f kHz".format(
                                inSr / 1000f
                            )
                        val outKhz =
                            if ((outSr / 1000f) % 1.0f == 0f) "${outSr / 1000} kHz" else "%.1f kHz".format(
                                outSr / 1000f
                            )
                        val resampleText = if (inSr == outSr) {
                            "Bit-perfect $inKhz"
                        } else {
                            "$inKhz → $outKhz"
                        }

                        PipelineStageItem(
                            iconRes = R.drawable.ic_pipeline_resampler,
                            title = "Resampler & Processing",
                            primaryLine = if (isPipelineLocked) resampleText else "Configuring audio pipeline...",
                            secondaryLine = details.processingMode
                                ?: "DefaultAudioSink • Float32 Output",
                            isLast = false,
                            isDark = isDark,
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
                            isDark = isDark,
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
                            isDark = isDark,
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

@Composable
private fun PipelineStageItem(
    iconRes: Int,
    title: String,
    primaryLine: String,
    secondaryLine: String,
    isLast: Boolean = false,
    modifier: Modifier = Modifier,
    isDark: Boolean,
) {
    val iconColor = if (isDark) Color.White else Color(0xFF191C1E)

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
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (!isLast) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(iconColor, RoundedCornerShape(1.dp))
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
                color = iconColor,
                letterSpacing = (-0.1).sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = primaryLine,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF2C2F33),
            )
            Text(
                text = secondaryLine,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF5A5D63),
            )
        }
    }
}
