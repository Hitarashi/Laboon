@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import kotlinx.coroutines.delay

private enum class TransportButton { PREVIOUS, PLAY_PAUSE, NEXT }

/** Pixel Player's expanding three-button transport row, rendered with Laboon's M3 symbols. */
@Composable
internal fun FullPlayerTransportControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastClicked by remember { mutableStateOf<TransportButton?>(null) }
    val latestIsPlayingProvider by rememberUpdatedState(isPlaying)
    val latestLastClicked by rememberUpdatedState(lastClicked)
    val playPauseLocked = lastClicked == TransportButton.NEXT || lastClicked == TransportButton.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(lastClicked) {
        if (lastClicked != null) {
            delay(220L)
            lastClicked = null
        }
    }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }
        if (latestLastClicked != TransportButton.PLAY_PAUSE) delay(220L)
        if (!latestIsPlayingProvider) pendingPlayPauseState = false
    }
    LaunchedEffect(playPauseLocked, pendingPlayPauseState) {
        if (!playPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    val playCorner by animateDpAsState(
        targetValue = if (playPauseVisualState) 26.dp else 60.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "fullPlayerPlayButtonCorner",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton.PREVIOUS.let { button ->
            val weight by animateFloatAsState(
                targetValue = when (lastClicked) {
                    button -> 1.1f
                    null -> 1f
                    else -> 0.65f
                },
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "fullPlayerPreviousWeight",
            )
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        enabled = canSkipPrevious,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.player_previous),
                        onClick = {
                            lastClicked = button
                            onPrevious()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = materialSymbolPainterResource(
                        "skip_previous",
                        "playback.previous",
                        filled = true,
                    ),
                    contentDescription = stringResource(R.string.player_previous),
                    tint = MaterialTheme.colorScheme.onPrimary.copy(
                        alpha = if (canSkipPrevious) 1f else 0.38f,
                    ),
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        TransportButton.PLAY_PAUSE.let { button ->
            val weight by animateFloatAsState(
                targetValue = when (lastClicked) {
                    button -> 1.1f
                    null -> 1f
                    else -> 0.65f
                },
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "fullPlayerPlayWeight",
            )
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(playCorner))
                    .background(MaterialTheme.colorScheme.tertiaryFixedDim)
                    .clickable(
                        enabled = true,
                        role = Role.Button,
                        onClickLabel = stringResource(
                            if (isPlaying) R.string.player_pause else R.string.player_play,
                        ),
                        onClick = {
                            lastClicked = button
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPlayPause()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = isBuffering,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "fullPlayerBuffering",
                ) { buffering ->
                    if (buffering) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(38.dp),
                            color = MaterialTheme.colorScheme.onTertiaryFixed,
                            trackColor = MaterialTheme.colorScheme.onTertiaryFixed.copy(alpha = 0.16f),
                        )
                    } else {
                        Icon(
                            painter = materialSymbolPainterResource(
                                if (playPauseVisualState) "pause" else "play_arrow",
                                if (playPauseVisualState) "playback.pause" else "playback.play",
                                filled = true,
                            ),
                            contentDescription = stringResource(
                                if (playPauseVisualState) R.string.player_pause else R.string.player_play,
                            ),
                            tint = MaterialTheme.colorScheme.onTertiaryFixed,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }

        TransportButton.NEXT.let { button ->
            val weight by animateFloatAsState(
                targetValue = when (lastClicked) {
                    button -> 1.1f
                    null -> 1f
                    else -> 0.65f
                },
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "fullPlayerNextWeight",
            )
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        enabled = canSkipNext,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.player_next),
                        onClick = {
                            lastClicked = button
                            onNext()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = materialSymbolPainterResource(
                        "skip_next",
                        "playback.skip",
                        filled = true,
                    ),
                    contentDescription = stringResource(R.string.player_next),
                    tint = MaterialTheme.colorScheme.onPrimary.copy(
                        alpha = if (canSkipNext) 1f else 0.38f,
                    ),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/** M3E segmented toggles matching Pixel Player's shuffle/repeat/favorite footer. */
@Composable
internal fun FullPlayerToggleRow(
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp, max = 86.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f),
                shape = RoundedCornerShape(60.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(60.dp)),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerToggleSegment(
                modifier = Modifier.weight(1f),
                selected = isShuffle,
                enabled = true,
                selectedContainer = MaterialTheme.colorScheme.primaryFixed,
                selectedContent = MaterialTheme.colorScheme.onPrimaryFixed,
                icon = "shuffle",
                slot = "playback.shuffle",
                label = "Shuffle",
                onClick = onShuffle,
            )
            PlayerToggleSegment(
                modifier = Modifier.weight(1f),
                selected = repeatMode != RepeatMode.OFF,
                enabled = true,
                selectedContainer = MaterialTheme.colorScheme.secondaryFixed,
                selectedContent = MaterialTheme.colorScheme.onSecondaryFixed,
                icon = if (repeatMode == RepeatMode.ONE) "repeat_one" else "repeat",
                slot = if (repeatMode == RepeatMode.ONE) "playback.repeatOne" else "playback.repeat",
                label = "Repeat",
                onClick = onRepeat,
            )
            PlayerToggleSegment(
                modifier = Modifier.weight(1f),
                selected = false,
                enabled = false,
                selectedContainer = MaterialTheme.colorScheme.tertiaryFixed,
                selectedContent = MaterialTheme.colorScheme.onTertiaryFixed,
                icon = "favorite",
                slot = "playback.favorite",
                label = "Favorite",
                onClick = {},
            )
        }
    }
}

@Composable
private fun PlayerToggleSegment(
    modifier: Modifier,
    selected: Boolean,
    enabled: Boolean,
    selectedContainer: androidx.compose.ui.graphics.Color,
    selectedContent: androidx.compose.ui.graphics.Color,
    icon: String,
    slot: String,
    label: String,
    onClick: () -> Unit,
) {
    val container = if (selected) selectedContainer
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val content = if (selected) selectedContent
    else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 60.dp else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "fullPlayerToggleCornerRadius",
    )
    Surface(
        modifier = modifier.fillMaxHeight(),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = materialSymbolPainterResource(
                    name = icon,
                    slot = slot,
                ),
                contentDescription = label,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
