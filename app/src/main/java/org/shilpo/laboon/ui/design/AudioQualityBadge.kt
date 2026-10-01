package org.shilpo.laboon.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.AudioQualityInfo
import java.util.Locale

@Composable
fun AudioQualityBadge(
    quality: AudioQualityInfo?,
    fallbackCodec: String? = null,
    track: HomeTrack? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val isStale = track != null && quality?.trackId != null && quality.trackId != track.id
    val isLocked = quality?.isLocked == true && !isStale
    val effectiveCodec = (if (!isStale) quality?.codec else null) ?: track?.codec ?: fallbackCodec
    val normCodec = effectiveCodec?.trim()?.lowercase(Locale.getDefault())

    val isHiRes = if (!isStale && quality != null) {
        quality.isHiRes
    } else {
        normCodec?.let { it.contains("hires") || it == "hi-res" } == true
    }

    val isLossless = if (!isStale && quality != null) {
        quality.isLossless
    } else {
        normCodec?.let {
            it == "lossless" || it == "alac" || it == "flac" || it.contains("lossless") ||
                    it.contains("hires") || it == "hi-res" || it.contains("24-")
        } == true
    }

    val isDolby = if (!isStale && quality != null) {
        quality.isDolby
    } else {
        normCodec?.let { it.contains("dolby") || it.contains("atmos") || it == "ec-3" || it == "ec3" } == true
    }

    val fallbackText = when {
        isHiRes || isLossless || isDolby -> null
        !quality?.codec.isNullOrBlank() && !isStale -> quality.codec
        !normCodec.isNullOrBlank() -> when {
            normCodec.contains("aac") || normCodec.contains("mp4a") -> "AAC"
            normCodec.contains("opus") -> "OPUS"
            normCodec.contains("mp3") || normCodec.contains("mpeg") -> "MP3"
            else -> effectiveCodec.trim().uppercase(Locale.getDefault())
        }

        else -> null
    }

    if (!isHiRes && !isLossless && !isDolby && fallbackText.isNullOrBlank()) {
        return
    }

    val pipeline = if (!isStale) quality?.pipelineDetails else null
    val bitDepthStr = pipeline?.bitDepth?.takeIf { it != "Float32" }
        ?: quality?.takeIf { !isStale }?.bitDepth?.let { "${it}-bit" }
        ?: if (isDolby || isHiRes) "24-bit" else if (isLossless) "16-bit" else null
    val kbps = pipeline?.bitrateKbps
        ?: if (isHiRes) 2840 else if (isLossless) 896 else 320
    val kbpsStr = "${kbps} kbps"

    val contentColor =
        if (isDark) Color.White.copy(alpha = 0.90f) else Color.Black.copy(alpha = 0.85f)
    val pillShape = RoundedCornerShape(percent = 50)

    val glassBackground = Brush.verticalGradient(
        colors = listOf(
            if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f),
            if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f),
        )
    )

    val glassBorder = Brush.verticalGradient(
        colors = listOf(
            if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.12f),
            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f),
        )
    )

    val infiniteTransition = rememberInfiniteTransition(label = "audioBadgePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = modifier
            .clip(pillShape)
            .background(glassBackground)
            .border(width = 0.5.dp, brush = glassBorder, shape = pillShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(horizontal = 9.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.5.dp),
        ) {
            when {
                isHiRes && isLossless -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_codec_hires),
                            contentDescription = "Hi-Res",
                            tint = contentColor,
                            modifier = Modifier.size(11.dp),
                        )
                        val width = 9.dp * (15f / 9f)
                        Icon(
                            painter = painterResource(R.drawable.ic_codec_lossless),
                            contentDescription = "Lossless",
                            tint = contentColor,
                            modifier = Modifier.size(width = width, height = 9.dp),
                        )
                    }
                }

                isHiRes -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_codec_hires),
                        contentDescription = "Hi-Res",
                        tint = contentColor,
                        modifier = Modifier.size(12.dp),
                    )
                }

                isLossless -> {
                    val width = 9.dp * (15f / 9f)
                    Icon(
                        painter = painterResource(R.drawable.ic_codec_lossless),
                        contentDescription = "Lossless",
                        tint = contentColor,
                        modifier = Modifier.size(width = width, height = 9.dp),
                    )
                }

                isDolby -> {
                    val width = 9.dp * (103f / 73f)
                    Icon(
                        painter = painterResource(R.drawable.ic_codec_dolby),
                        contentDescription = "Dolby Atmos",
                        tint = contentColor,
                        modifier = Modifier.size(width = width, height = 9.dp),
                    )
                }

                !fallbackText.isNullOrBlank() -> {
                    Text(
                        text = fallbackText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.4.sp,
                        ),
                        color = contentColor,
                    )
                }
            }

            AnimatedContent(
                targetState = isLocked,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith
                            fadeOut(animationSpec = tween(160))
                },
                label = "audioQualityTransition",
            ) { locked ->
                if (locked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.5.dp),
                    ) {
                        if (!bitDepthStr.isNullOrBlank()) {
                            BadgeDot(contentColor)
                            Text(
                                text = bitDepthStr,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 9.5.sp,
                                    letterSpacing = 0.2.sp,
                                ),
                                color = contentColor,
                            )
                        }

                        BadgeDot(contentColor)
                        Text(
                            text = kbpsStr,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp,
                                letterSpacing = 0.2.sp,
                            ),
                            color = contentColor,
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.5.dp),
                    ) {
                        BadgeDot(contentColor)
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .height(6.5.dp)
                                .graphicsLayer { alpha = pulseAlpha }
                                .background(
                                    color = contentColor.copy(alpha = 0.40f),
                                    shape = RoundedCornerShape(percent = 50),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeDot(color: Color) {
    Box(
        modifier = Modifier
            .size(2.5.dp)
            .background(color.copy(alpha = 0.45f), CircleShape)
    )
}
