package org.shilpo.laboon.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.playback.AudioQualityInfo
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.ui.design.FloatingNavigationBar
import org.shilpo.laboon.ui.design.FloatingNavigationItem
import org.shilpo.laboon.ui.design.LiquidGlassBackdropState
import org.shilpo.laboon.ui.design.LiquidGlassSurface
import org.shilpo.laboon.ui.design.MiniPlayerSpacing
import org.shilpo.laboon.ui.design.NavigationBarHeight
import org.shilpo.laboon.ui.design.NavigationBarMaxWidth
import org.shilpo.laboon.ui.screens.player.lyrics.LyricsScreen
import org.shilpo.laboon.ui.screens.player.queue.QueueScreen

internal enum class PlayerPanelTab {
    Lyrics,
    Queue,
}

internal data class PlayerOverlayState(
    val track: HomeTrack,
    val audioQuality: AudioQualityInfo?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val isShuffle: Boolean,
    val repeatMode: RepeatMode,
    val currentPositionMs: Long,
    val durationMs: Long,
    val queueState: QueueState?,
    val lyricsLines: List<LyricsLine>,
    val lyricsLoading: Boolean,
)

internal data class PlayerOverlayActions(
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Float) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeatMode: () -> Unit,
    val onAudioQualityClick: () -> Unit,
    val onTrackClick: (HomeTrack) -> Unit,
    val onRemoveUpNext: (Int) -> Unit,
    val onMoveUpNext: (Int, Int) -> Unit,
)

@Composable
internal fun PlayerOverlayPanels(
    state: PlayerOverlayState,
    actions: PlayerOverlayActions,
    selectedPanel: PlayerPanelTab?,
    panelFractionProvider: () -> Float,
    onSelectPanel: (PlayerPanelTab) -> Unit,
    onClosePanel: () -> Unit,
    onAudioQualityPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    isDark: Boolean,
    backdropState: LiquidGlassBackdropState?,
    handleSwipeDismiss: Boolean = true,
) {
    BackHandler(enabled = selectedPanel != null) {
        onClosePanel()
    }

    val fraction = panelFractionProvider().coerceIn(0f, 1f)
    val joinRadius = lerp(28.dp, 12.dp, fraction)
    val activePanel = selectedPanel ?: return
    if (fraction <= PanelCompositionThreshold) return
    val density = LocalDensity.current
    val panelSwipeDismissThreshold = with(density) { 72.dp.toPx() }

    val lyricsListState = rememberLazyListState()
    val queueListState = rememberLazyListState()
    val queueState = state.queueState ?: QueueState(items = listOf(state.track), currentIndex = 0)
    val navigationItems = listOf(
        FloatingNavigationItem(
            value = PlayerPanelTab.Lyrics,
            title = stringResource(R.string.player_tab_lyrics),
            iconOutlined = R.drawable.ic_player_lyrics,
            iconFilled = R.drawable.ic_player_lyrics,
        ),
        FloatingNavigationItem(
            value = PlayerPanelTab.Queue,
            title = stringResource(R.string.player_tab_queue),
            iconOutlined = R.drawable.ic_player_queue,
            iconFilled = R.drawable.ic_player_queue,
        ),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .then(
                if (handleSwipeDismiss) {
                    Modifier.pointerInput(activePanel, panelSwipeDismissThreshold) {
                        var totalDragY = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDragY = 0f },
                            onDragEnd = {
                                if (totalDragY >= panelSwipeDismissThreshold) {
                                    onClosePanel()
                                }
                            },
                            onDragCancel = { totalDragY = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                totalDragY += dragAmount
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navigationBarBottom =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val navEntryDistance = with(density) {
            (statusBarTop + 8.dp + NavigationBarHeight + 8.dp).toPx()
        }
        val panelExitClearance = with(density) { (navigationBarBottom + 16.dp).toPx() }
        val navIconAlpha = fraction * fraction * fraction
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(minOf((maxWidth - 32.dp).coerceAtLeast(0.dp), NavigationBarMaxWidth))
                .padding(
                    top = statusBarTop + 8.dp,
                    bottom = navigationBarBottom + 8.dp,
                )
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(MiniPlayerSpacing),
        ) {
            FloatingNavigationBar(
                modifier = Modifier.graphicsLayer {
                    translationY = -navEntryDistance * (1f - fraction)
                },
                items = navigationItems,
                selectedItem = activePanel,
                onItemSelected = onSelectPanel,
                connectedBelow = true,
                connectedBelowFraction = fraction,
                iconAlpha = navIconAlpha,
                labelAlpha = fraction,
                backdropState = backdropState,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = (size.height + panelExitClearance) * (1f - fraction)
                    },
                verticalArrangement = Arrangement.spacedBy(MiniPlayerSpacing),
            ) {
                LiquidGlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    backdropState = backdropState,
                    shape = RoundedCornerShape(
                        topStart = joinRadius,
                        topEnd = joinRadius,
                        bottomStart = joinRadius,
                        bottomEnd = joinRadius,
                    ),
                    cornerRadius = 28.dp,
                    topRadius = joinRadius,
                    bottomRadius = joinRadius,
                    tintAlpha = 0.85f,
                    shadowElevation = 12.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (activePanel) {
                            PlayerPanelTab.Lyrics -> LyricsScreen(
                                track = state.track,
                                currentPositionMs = state.currentPositionMs,
                                durationMs = state.durationMs,
                                lyricsLines = state.lyricsLines,
                                lyricsLoading = state.lyricsLoading,
                                onSeek = actions.onSeek,
                                lyricsFractionProvider = panelFractionProvider,
                                lazyListState = lyricsListState,
                                isPlaying = state.isPlaying,
                            )

                            PlayerPanelTab.Queue -> QueueScreen(
                                queueState = queueState,
                                onTrackClick = actions.onTrackClick,
                                onRemoveUpNext = actions.onRemoveUpNext,
                                onMoveUpNext = actions.onMoveUpNext,
                                queueFractionProvider = panelFractionProvider,
                                lazyListState = queueListState,
                            )
                        }
                    }
                }

                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backdropState = backdropState,
                    shape = RoundedCornerShape(
                        topStart = joinRadius,
                        topEnd = joinRadius,
                        bottomStart = 28.dp,
                        bottomEnd = 28.dp,
                    ),
                    cornerRadius = 28.dp,
                    topRadius = joinRadius,
                    bottomRadius = 28.dp,
                    tintAlpha = 0.85f,
                    shadowElevation = 12.dp,
                ) {
                    PlayerOverlayController(
                        state = state,
                        actions = actions,
                        onAudioQualityPositioned = onAudioQualityPositioned,
                        isDark = isDark,
                    )
                }
            }
        }
    }
}

internal const val PanelCompositionThreshold = 0.005f
