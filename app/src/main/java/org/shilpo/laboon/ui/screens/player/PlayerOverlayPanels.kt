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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.lyrics.LyricsTranslator
import org.shilpo.laboon.playback.AudioQualityInfo
import org.shilpo.laboon.playback.DiscoveryStatus
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.playback.SpectrumFrame
import org.shilpo.laboon.ui.design.FloatingNavigationBar
import org.shilpo.laboon.ui.design.FloatingNavigationItem
import org.shilpo.laboon.ui.design.LiquidGlassBackdropState
import org.shilpo.laboon.ui.design.LiquidGlassSurface
import org.shilpo.laboon.ui.design.MiniPlayerSpacing
import org.shilpo.laboon.ui.design.NavigationBarHeight
import org.shilpo.laboon.ui.design.NavigationBarMaxWidth
import org.shilpo.laboon.ui.screens.player.lyrics.LyricsControlsRow
import org.shilpo.laboon.ui.screens.player.lyrics.LyricsDisplayOptions
import org.shilpo.laboon.ui.screens.player.lyrics.LyricsScreen
import org.shilpo.laboon.ui.screens.player.queue.QueueScreen

internal enum class PlayerPanelTab {
    Lyrics,
    Queue,
}

internal data class PlayerOverlayState(
    val track: HomeTrack,
    val audioQuality: AudioQualityInfo?,
    val switchingQualityFormat: String? = null,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val isShuffle: Boolean,
    val repeatMode: RepeatMode,
    val currentPositionMs: Long,
    val durationMs: Long,
    val queueState: QueueState?,
    val canSkipPrevious: Boolean = false,
    val isDiscovering: Boolean = false,
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.IDLE,
    val lyricsLines: List<LyricsLine>,
    val lyricsLoading: Boolean,
    val lyricsFailed: Boolean = false,
    val spectrum: SpectrumFrame = SpectrumFrame(),
)

internal data class PlayerOverlayActions(
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Float) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeatMode: () -> Unit,
    val onAudioQualityClick: () -> Unit,
    val onQualityVariantSelected: (TrackFormatVariant) -> Unit,
    val onTrackClick: (HomeTrack) -> Unit,
    val onQueueEntryClick: (Long) -> Unit,
    val onPromoteAutoplay: (Long) -> Unit,
    val onRemoveUpNext: (Int) -> Unit,
    val onMoveUpNext: (Int, Int) -> Unit,
    val onClearUpcoming: () -> Unit,
    val onRetryDiscovery: () -> Unit,
    val onRetryLyrics: () -> Unit,
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
    val coroutineScope = rememberCoroutineScope()
    var lyricsSyncOffsetMs by remember { mutableLongStateOf(0L) }
    var showShareDialog by remember(state.track) { mutableStateOf(false) }
    var showRomanization by remember(state.lyricsLines) { mutableStateOf(true) }
    var showTranslation by remember(state.lyricsLines) { mutableStateOf(false) }
    var translatedLines by remember(state.track, state.lyricsLines) {
        mutableStateOf<List<LyricsLine>?>(null)
    }
    var isTranslating by remember(state.track, state.lyricsLines) { mutableStateOf(false) }
    val lyricsHaveTiming = remember(state.lyricsLines) {
        state.lyricsLines.any { line ->
            line.startMs > 0L || line.endMs > 0L ||
                    (line.words + line.backgroundWords).any { word -> word.startMs > 0L }
        }
    }
    val onToggleTranslation: () -> Unit = {
        val next = !showTranslation
        showTranslation = next
        if (
            next && translatedLines == null &&
            state.lyricsLines.none { it.translations.isNotEmpty() } && !isTranslating
        ) {
            isTranslating = true
            coroutineScope.launch {
                try {
                    translatedLines = LyricsTranslator.translateLines(state.lyricsLines)
                } finally {
                    isTranslating = false
                }
            }
        }
    }

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
    val queueState = state.queueState ?: QueueState.withCurrent(state.track)
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
                                lyricsFailed = state.lyricsFailed,
                                onSeek = actions.onSeek,
                                lyricsFractionProvider = panelFractionProvider,
                                lazyListState = lyricsListState,
                                isPlaying = state.isPlaying,
                                displayOptions = LyricsDisplayOptions(
                                    syncOffsetMs = lyricsSyncOffsetMs,
                                    showRomanization = showRomanization,
                                    showTranslation = showTranslation,
                                    translatedLines = translatedLines,
                                    showShareDialog = showShareDialog,
                                ),
                                onDismissShareDialog = { showShareDialog = false },
                                onRetryLyrics = actions.onRetryLyrics,
                                spectrum = state.spectrum,
                            )

                            PlayerPanelTab.Queue -> QueueScreen(
                                queueState = queueState,
                                onQueueEntryClick = actions.onQueueEntryClick,
                                onRemoveUpNext = actions.onRemoveUpNext,
                                onMoveUpNext = actions.onMoveUpNext,
                                onPromoteAutoplay = actions.onPromoteAutoplay,
                                onClearUpcoming = actions.onClearUpcoming,
                                onRetryDiscovery = actions.onRetryDiscovery,
                                isDiscovering = state.isDiscovering,
                                discoveryStatus = state.discoveryStatus,
                                queueFractionProvider = panelFractionProvider,
                                lazyListState = queueListState,
                                isShuffle = state.isShuffle,
                                repeatMode = state.repeatMode,
                                onShuffle = actions.onToggleShuffle,
                                onRepeat = actions.onCycleRepeatMode,
                                isPlaying = state.isPlaying,
                                showFloatingControls = false,
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
                        lyricsTools = if (
                            activePanel == PlayerPanelTab.Lyrics && state.lyricsLines.isNotEmpty()
                        ) {
                            {
                                LyricsControlsRow(
                                    hasTiming = lyricsHaveTiming,
                                    lyricsSyncOffsetMs = lyricsSyncOffsetMs,
                                    onSyncOffsetChange = { lyricsSyncOffsetMs = it },
                                    showRomanization = showRomanization,
                                    onToggleRomanization = {
                                        showRomanization = !showRomanization
                                    },
                                    showTranslation = showTranslation,
                                    isTranslating = isTranslating,
                                    onToggleTranslation = onToggleTranslation,
                                    onShare = { showShareDialog = true },
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

internal const val PanelCompositionThreshold = 0.005f
