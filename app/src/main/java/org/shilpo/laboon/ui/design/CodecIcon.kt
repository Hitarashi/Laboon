package org.shilpo.laboon.ui.design

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.R

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
