@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.lyricsporn.LyricspornMotionArtwork
import org.shilpo.laboon.playback.AudioQualityInfo
import org.shilpo.laboon.playback.DiscoveryStatus
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode
import org.shilpo.laboon.playback.SpectrumFrame
import org.shilpo.laboon.theme.LocalArtworkColorScheme
import org.shilpo.laboon.ui.design.LiquidGlassBackdropState
import org.shilpo.laboon.ui.design.MiniPlayerSpacing
import org.shilpo.laboon.ui.design.NavigationBarBottomPadding
import org.shilpo.laboon.ui.design.NavigationBarHeight
import org.shilpo.laboon.ui.design.painterResource
import org.shilpo.laboon.ui.design.theme.animateColorScheme

private const val SettleDurationMs = 400
private val SettleEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val CookieMorph = Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided)

private data class MorphingPlayerCookieShape(
    val morphProgress: Float,
    val rotationAngle: Float = 0f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = CookieMorph.toPath(morphProgress.coerceIn(0f, 1f)).asComposePath()
        val matrix = Matrix()
        val bounds = CookieMorph.calculateBounds()
        val boundsWidth = bounds[2] - bounds[0]
        val boundsHeight = bounds[3] - bounds[1]

        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)

        if (rotationAngle != 0f) {
            val rotMatrix = Matrix()
            rotMatrix.resetToPivotedTransform(
                pivotX = size.width / 2f,
                pivotY = size.height / 2f,
                rotationZ = rotationAngle,
            )
            path.transform(rotMatrix)
        }

        return Outline.Generic(path)
    }
}

@Composable
fun MorphingPlayerSheet(
    track: HomeTrack,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    playbackProgress: Float = 0f,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    audioQuality: AudioQualityInfo? = null,
    switchingQualityFormat: String? = null,
    onQualityVariantSelected: ((TrackFormatVariant) -> Unit)? = null,
    isShuffle: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    canSkipPrevious: Boolean = false,
    spectrum: SpectrumFrame = SpectrumFrame(),
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backdropState: LiquidGlassBackdropState? = null,
    albumDockProgress: Float = 0f,
    onMoreClick: () -> Unit = {},
    onExpansionProgressChange: ((Float) -> Unit)? = null,
    queueState: QueueState? = null,
    isDiscovering: Boolean = false,
    discoveryStatus: DiscoveryStatus = DiscoveryStatus.IDLE,
    onRemoveUpNext: ((Int) -> Unit)? = null,
    onMoveUpNext: ((Int, Int) -> Unit)? = null,
    onTrackClick: ((HomeTrack) -> Unit)? = null,
    onQueueEntryClick: ((Long) -> Unit)? = null,
    onPromoteAutoplay: ((Long) -> Unit)? = null,
    onClearUpcoming: (() -> Unit)? = null,
    onRetryDiscovery: (() -> Unit)? = null,
    onOpenAlbum: (suspend (HomeTrack) -> Boolean)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    lyricsLines: List<LyricsLine> = emptyList(),
    lyricsLoading: Boolean = false,
    lyricsFailed: Boolean = false,
    onRetryLyrics: (() -> Unit)? = null,
    motionArtwork: LyricspornMotionArtwork? = null,
    onRequestMotionArtwork: (() -> Unit)? = null,
    onDownloadTrack: ((HomeTrack) -> Unit)? = null,
    onLoadTrackGenres: (suspend (HomeTrack) -> List<String>)? = null,
) {
    val playerColorScheme = animateColorScheme(
        LocalArtworkColorScheme.current ?: MaterialTheme.colorScheme,
    )
    val collapsedBottomChromeClearance = lerp(
        start = NavigationBarHeight + NavigationBarBottomPadding + MiniPlayerSpacing,
        stop = MiniPlayerSpacing,
        fraction = albumDockProgress.coerceIn(0f, 1f),
    )
    MaterialTheme(colorScheme = playerColorScheme) {
        StockPlayerSheet(
            track = track,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            progress = playbackProgress,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            audioQuality = audioQuality,
            switchingQualityFormat = switchingQualityFormat,
            onQualityVariantSelected = onQualityVariantSelected,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            canSkipPrevious = canSkipPrevious,
            onPlayPause = onPlayPauseClick,
            onPrevious = onPreviousClick,
            onNext = onNextClick,
            onSeek = onSeek,
            onShuffle = onToggleShuffle,
            onRepeat = onCycleRepeatMode,
            onDismiss = onDismiss,
            onExpansionChange = onExpansionProgressChange,
            collapsedBottomChromeClearance = collapsedBottomChromeClearance,
            navigationBarHiddenProgress = albumDockProgress,
            queueState = queueState,
            onRemove = onRemoveUpNext,
            onMove = onMoveUpNext,
            onTrack = onTrackClick,
            onQueueEntry = onQueueEntryClick,
            onPromote = onPromoteAutoplay,
            onClear = onClearUpcoming,
            onRetry = onRetryDiscovery,
            isDiscovering = isDiscovering,
            discoveryStatus = discoveryStatus,
            onAlbum = onOpenAlbum,
            onArtist = onArtistClick,
            lyrics = lyricsLines,
            lyricsLoading = lyricsLoading,
            lyricsFailed = lyricsFailed,
            onRetryLyrics = onRetryLyrics,
            spectrum = spectrum,
            motionArtwork = motionArtwork,
            onRequestMotionArtwork = onRequestMotionArtwork,
            onDownloadTrack = onDownloadTrack,
            onLoadTrackGenres = onLoadTrackGenres,
            modifier = modifier,
        )
    }
}

@Composable
private fun FullPlayerToolbar(
    albumName: String?,
    onOpenAlbum: (() -> Unit)?,
    onCollapse: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCollapse,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = "Collapse",
                tint = if (isDark) Color.White else Color(0xFF191C1E),
                modifier = Modifier.size(22.dp),
            )
        }

        val hasAlbum = !albumName.isNullOrBlank()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "NOW PLAYING",
                color = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.60f),
                fontSize = if (hasAlbum) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasAlbum) {
                Text(
                    text = albumName,
                    color = if (isDark) Color.White.copy(alpha = 0.90f) else Color(0xFF191C1E),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onOpenAlbum != null) {
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                onClickLabel = "Open album",
                                onClick = onOpenAlbum,
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMoreClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = "More",
                tint = if (isDark) Color.White else Color(0xFF191C1E),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
