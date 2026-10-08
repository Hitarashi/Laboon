package org.shilpo.laboon.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.ui.design.painterResource

private val ButtonClickAreaSize = 48.dp
private val BottomBarIconSize = 28.dp
private val ButtonSpacing = 120.dp

@Composable
fun PlayerBottomBar(
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val inactiveButtonColor =
        if (isDark) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.75f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AiryIconButton(
            iconRes = R.drawable.ic_player_lyrics,
            contentDescription = "Lyrics",
            tint = inactiveButtonColor,
            size = BottomBarIconSize,
            onClick = onOpenLyrics,
        )
        AiryIconButton(
            iconRes = R.drawable.ic_player_queue,
            contentDescription = "Queue",
            tint = inactiveButtonColor,
            size = BottomBarIconSize,
            onClick = onOpenQueue,
        )
    }
}

@Composable
private fun AiryIconButton(
    iconRes: Int,
    contentDescription: String?,
    tint: Color,
    size: Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(ButtonClickAreaSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}
