@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.screens.player.FullPlayerToggleRow
import java.util.Locale

@Composable
internal fun LyricsOptionsSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    hasLyrics: Boolean,
    hasTimedLyrics: Boolean,
    isSyncControlsVisible: Boolean,
    onToggleSyncControls: () -> Unit,
    alignment: String,
    onAlignmentChange: (String) -> Unit,
    hasRomanization: Boolean,
    showRomanization: Boolean,
    onShowRomanizationChange: (Boolean) -> Unit,
    hasTranslation: Boolean,
    showTranslation: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onShareLyrics: () -> Unit,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val itemBackgroundColor = colors.onSurface.copy(alpha = 0.08f)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentWindowInsets = { WindowInsets(top = 0, bottom = 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + navigationBarsPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LyricsOptionsSection(title = "Lyrics") {
                LyricsOptionAction(
                    text = "Share lyrics",
                    icon = "share",
                    enabled = hasLyrics,
                    shape = lyricsOptionShape(index = 0, count = 1),
                    backgroundColor = itemBackgroundColor,
                    onClick = onShareLyrics,
                )
            }

            LyricsOptionsSection(title = "Appearance") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(itemBackgroundColor)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Alignment",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    LyricsAlignmentGroup(
                        alignment = alignment,
                        onAlignmentChange = onAlignmentChange,
                    )
                }
            }

            val controlCount =
                (if (hasTimedLyrics) 1 else 0) +
                    (if (hasRomanization) 1 else 0) +
                    (if (hasTranslation) 1 else 0) + 1
            var controlIndex = 0

            LyricsOptionsSection(title = "Controls") {
                if (hasTimedLyrics) {
                    LyricsOptionAction(
                        text = if (isSyncControlsVisible) "Hide sync controls" else "Adjust sync",
                        icon = "tune",
                        shape = lyricsOptionShape(controlIndex++, controlCount),
                        backgroundColor = itemBackgroundColor,
                        onClick = onToggleSyncControls,
                    )
                }
                if (hasRomanization) {
                    LyricsOptionSwitch(
                        text = "Show romanization",
                        icon = "abc",
                        checked = showRomanization,
                        shape = lyricsOptionShape(controlIndex++, controlCount),
                        backgroundColor = itemBackgroundColor,
                        onCheckedChange = onShowRomanizationChange,
                    )
                }
                if (hasTranslation) {
                    LyricsOptionSwitch(
                        text = "Show translation",
                        icon = "translate",
                        checked = showTranslation,
                        shape = lyricsOptionShape(controlIndex++, controlCount),
                        backgroundColor = itemBackgroundColor,
                        onCheckedChange = onShowTranslationChange,
                    )
                }
                LyricsOptionSwitch(
                    text = "Keep screen on",
                    icon = "brightness_high",
                    checked = keepScreenOn,
                    shape = lyricsOptionShape(controlIndex, controlCount),
                    backgroundColor = itemBackgroundColor,
                    onCheckedChange = onKeepScreenOnChange,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                FullPlayerToggleRow(
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    onShuffle = onShuffle,
                    onRepeat = onRepeat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 66.dp, max = 86.dp)
                        .padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsOptionsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLargeEmphasized,
        )
        content()
    }
}

@Composable
private fun LyricsOptionAction(
    text: String,
    icon: String,
    enabled: Boolean = true,
    shape: RoundedCornerShape,
    backgroundColor: Color,
    onClick: () -> Unit = {},
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    ListItem(
        leadingContent = {
            Icon(
                painter = materialSymbolPainterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = contentColor,
            leadingIconColor = contentColor,
        ),
    ) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LyricsOptionSwitch(
    text: String,
    icon: String,
    checked: Boolean,
    shape: RoundedCornerShape,
    backgroundColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        leadingContent = {
            Icon(
                painter = materialSymbolPainterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = colors.onSurface,
            leadingIconColor = colors.onSurface,
        ),
    ) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LyricsAlignmentGroup(
    alignment: String,
    onAlignmentChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        AlignmentButton(
            selected = alignment == "left",
            icon = "format_align_left",
            label = "Align left",
            position = AlignmentButtonPosition.LEADING,
            containerColor = colors.surfaceContainerHighest,
            selectedContainerColor = colors.primary,
            contentColor = colors.onSurfaceVariant,
            selectedContentColor = colors.onPrimary,
            onClick = { onAlignmentChange("left") },
        )
        AlignmentButton(
            selected = alignment == "center",
            icon = "format_align_center",
            label = "Align center",
            position = AlignmentButtonPosition.MIDDLE,
            containerColor = colors.surfaceContainerHighest,
            selectedContainerColor = colors.primary,
            contentColor = colors.onSurfaceVariant,
            selectedContentColor = colors.onPrimary,
            onClick = { onAlignmentChange("center") },
        )
        AlignmentButton(
            selected = alignment == "right",
            icon = "format_align_right",
            label = "Align right",
            position = AlignmentButtonPosition.TRAILING,
            containerColor = colors.surfaceContainerHighest,
            selectedContainerColor = colors.primary,
            contentColor = colors.onSurfaceVariant,
            selectedContentColor = colors.onPrimary,
            onClick = { onAlignmentChange("right") },
        )
    }
}

private enum class AlignmentButtonPosition { LEADING, MIDDLE, TRAILING }

private fun androidx.compose.material3.ButtonGroupScope.AlignmentButton(
    selected: Boolean,
    icon: String,
    label: String,
    position: AlignmentButtonPosition,
    containerColor: Color,
    selectedContainerColor: Color,
    contentColor: Color,
    selectedContentColor: Color,
    onClick: () -> Unit,
) {
    customItem(
        buttonGroupContent = {
            val shapes = when (position) {
                AlignmentButtonPosition.LEADING -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                AlignmentButtonPosition.MIDDLE -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                AlignmentButtonPosition.TRAILING -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            }
            FilledTonalToggleButton(
                checked = selected,
                onCheckedChange = { if (it) onClick() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                buttonSize = ToggleButtonSize.Small,
                shapes = shapes,
                colors = FilledTonalToggleButtonDefaults.colors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                    checkedContainerColor = selectedContainerColor,
                    checkedContentColor = selectedContentColor,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Icon(
                    painter = materialSymbolPainterResource(icon),
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        menuContent = { menuState ->
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    onClick()
                    menuState.dismiss()
                },
            )
        },
    )
}

@Composable
internal fun LyricsSyncControls(
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val resetContainer = if (offsetMs == 0L) colors.surfaceContainerLowest else colors.primary
    val resetContent = if (offsetMs == 0L) colors.onSurfaceVariant else colors.onPrimary

    Row(
        modifier = modifier
            .height(52.dp)
            .background(colors.surfaceContainerLowest, CircleShape)
            .padding(4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LyricsSyncButton(
            text = "−0.5s",
            onClick = { onOffsetChange(offsetMs - 500L) },
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            modifier = Modifier.weight(1f),
        )
        LyricsSyncButton(
            text = "−0.1s",
            onClick = { onOffsetChange(offsetMs - 100L) },
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            modifier = Modifier.weight(1f),
        )
        LyricsSyncButton(
            text = String.format(Locale.ROOT, "%.1fs", offsetMs / 1000.0),
            onClick = { onOffsetChange(0L) },
            enabled = offsetMs != 0L,
            containerColor = resetContainer,
            contentColor = resetContent,
            modifier = Modifier.weight(1.3f),
        )
        LyricsSyncButton(
            text = "+0.1s",
            onClick = { onOffsetChange(offsetMs + 100L) },
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            modifier = Modifier.weight(1f),
        )
        LyricsSyncButton(
            text = "+0.5s",
            onClick = { onOffsetChange(offsetMs + 500L) },
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.LyricsSyncButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color,
    contentColor: Color,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun lyricsOptionShape(index: Int, count: Int): RoundedCornerShape = RoundedCornerShape(
    topStart = if (index == 0) 18.dp else 8.dp,
    topEnd = if (index == 0) 18.dp else 8.dp,
    bottomStart = if (index == count - 1) 24.dp else 8.dp,
    bottomEnd = if (index == count - 1) 24.dp else 8.dp,
)
