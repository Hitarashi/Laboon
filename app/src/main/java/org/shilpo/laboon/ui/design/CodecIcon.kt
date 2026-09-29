package org.shilpo.laboon.ui.design

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R

@Composable
fun CodecIcon(
    codec: String?,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    tint: Color = LocalContentColor.current,
) {
    val normalized = codec?.trim()?.lowercase() ?: return
    when (normalized) {
        "alac" -> {
            val width = height * (15f / 9f)
            Icon(
                painter = painterResource(R.drawable.ic_codec_alac),
                contentDescription = "ALAC",
                tint = tint,
                modifier = modifier.size(width = width, height = height),
            )
        }

        "ec-3", "ec3", "atmos", "dolby", "dolby_atmos" -> {
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
