package org.shilpo.laboon.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import java.util.Locale

@Composable
fun TrackCodecBadges(
    track: HomeTrack,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    tint: Color = LocalContentColor.current,
    horizontalSpacing: Dp = 5.dp,
) {
    val codecs = getTrackQualityCodecs(track)
    if (codecs.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
    ) {
        codecs.forEach { codec ->
            CodecIcon(
                codec = codec,
                height = height,
                tint = tint,
            )
        }
    }
}

fun getTrackQualityCodecs(track: HomeTrack): List<String> {
    val rawFormats = when {
        track.availableFormats.isNotEmpty() -> track.availableFormats
        track.availableVariants.isNotEmpty() -> track.availableVariants.map { it.format }
        else -> listOfNotNull(track.codec)
    }
    if (rawFormats.isEmpty()) return emptyList()

    val result = linkedSetOf<String>()
    for (f in rawFormats) {
        val norm = f.trim().lowercase(Locale.ROOT)
        when {
            norm == "ec-3" || norm == "ec3" || norm.contains("dolby") || norm.contains("atmos") -> {
                result.add("ec-3")
            }

            norm.contains("hires") || norm == "hi-res" || norm.contains("24-") -> {
                result.add("hires")
            }

            norm == "alac" || norm == "flac" || norm == "lossless" -> {
                result.add("lossless")
            }

            norm == "aac" || norm.startsWith("mp4a") -> {
                result.add("aac")
            }

            else -> {
                result.add(f)
            }
        }
    }

    if (result.contains("lossless") || result.contains("hires")) {
        result.remove("aac")
    }

    return result.sortedBy { codec ->
        when (codec.lowercase(Locale.ROOT)) {
            "hires" -> 0
            "lossless", "alac", "flac" -> 1
            "ec-3", "ec3", "atmos", "dolby", "dolby_atmos" -> 2
            else -> 3
        }
    }
}

@Composable
fun CodecIcon(
    codec: String?,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    tint: Color = LocalContentColor.current,
) {
    val normalized = codec?.trim()?.lowercase() ?: return
    when {
        normalized == "aac" || normalized.startsWith("mp4a") -> {
            Text(
                text = "AAC",
                color = tint,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = modifier,
            )
        }

        normalized.contains("hires") || normalized == "hi-res" || normalized.contains("24-") -> {
            Icon(
                painter = painterResource(R.drawable.ic_codec_hires),
                contentDescription = "Hi-Res",
                tint = tint,
                modifier = modifier.size(height),
            )
        }

        normalized == "lossless" || normalized == "alac" || normalized == "flac" -> {
            val width = height * (15f / 9f)
            Icon(
                painter = painterResource(R.drawable.ic_codec_lossless),
                contentDescription = "Lossless",
                tint = tint,
                modifier = modifier.size(width = width, height = height),
            )
        }

        normalized == "ec-3" || normalized == "ec3" || normalized == "atmos" || normalized == "dolby" || normalized == "dolby_atmos" -> {
            val width = height * (103f / 73f)
            Icon(
                painter = painterResource(R.drawable.ic_codec_dolby),
                contentDescription = "Dolby",
                tint = tint,
                modifier = modifier.size(width = width, height = height),
            )
        }
    }
}
