@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.painterResource

@Composable
internal fun HomeDailyMixScreen(
    tracks: List<HomeTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onPlayTrack: (HomeTrack, List<HomeTrack>) -> Unit,
    onPlayAll: (List<HomeTrack>) -> Unit,
    onShuffle: (List<HomeTrack>) -> Unit,
    onPlayNext: (HomeTrack) -> Unit,
    onAddToQueue: (HomeTrack) -> Unit,
    onLoadTrackGenres: suspend (HomeTrack) -> List<String>,
    onDownloadTrack: (HomeTrack) -> Unit,
) {
    var selectedTrack by remember { mutableStateOf<HomeTrack?>(null) }
    val playableTracks = remember(tracks) { tracks.filter(HomeTrack::isPlayable) }
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.surface,
        ),
        endY = 1200f,
    )

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = bottomClearance + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "daily_mix_header") {
                HomeDailyMixFullHeader(tracks = tracks)
            }
            item(key = "daily_mix_controls") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { if (playableTracks.isNotEmpty()) onPlayAll(playableTracks) },
                        enabled = playableTracks.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(76.dp),
                        shape = RoundedCornerShape(
                            topStart = 60.dp,
                            topEnd = 14.dp,
                            bottomStart = 60.dp,
                            bottomEnd = 14.dp,
                        ),
                    ) {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        androidx.compose.foundation.layout.Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.home_daily_mix_play))
                    }
                    FilledTonalButton(
                        onClick = { if (playableTracks.isNotEmpty()) onShuffle(playableTracks) },
                        enabled = playableTracks.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(76.dp),
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 60.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 60.dp,
                        ),
                    ) {
                        Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        androidx.compose.foundation.layout.Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.home_daily_mix_shuffle))
                    }
                }
            }
            item(key = "daily_mix_songs") {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    HomeDailyMixSongRows(
                        tracks = tracks,
                        playbackQueue = tracks,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        onPlayTrack = onPlayTrack,
                        onMoreOptions = { selectedTrack = it },
                    )
                }
            }
        }

        FilledIconButton(
            onClick = onBack,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 10.dp, top = 8.dp)
                .clip(CircleShape),
        ) {
            Icon(
                painter = materialSymbolPainterResource(name = "arrow_back", slot = "action.back"),
                contentDescription = stringResource(R.string.home_daily_mix_back),
                modifier = Modifier.size(24.dp),
            )
        }
    }

    selectedTrack?.let { track ->
        HomeDailyMixSongOptionsSheet(
            track = track,
            onDismiss = { selectedTrack = null },
            onPlay = {
                onPlayTrack(track, tracks)
                selectedTrack = null
            },
            onPlayNext = {
                onPlayNext(track)
                selectedTrack = null
            },
            onAddToQueue = {
                onAddToQueue(track)
                selectedTrack = null
            },
            onLoadTrackGenres = onLoadTrackGenres,
            onDownload = {
                onDownloadTrack(track)
                selectedTrack = null
            },
        )
    }
}

@Composable
private fun HomeDailyMixFullHeader(tracks: List<HomeTrack>) {
    val albumArts = remember(tracks) { tracks.map(HomeTrack::artworkUrl).distinct().take(3) }
    Box(
        modifier = Modifier.fillMaxWidth().height(340.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((-80).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            albumArts.forEachIndexed { index, artworkUrl ->
                val size = when (index) {
                    0, 2 -> 180.dp
                    else -> 220.dp
                }
                val rotation = when (index) {
                    0 -> -15f
                    2 -> 15f
                    else -> 0f
                }
                HomeArtwork(
                    artworkUrl = artworkUrl,
                    modifier = Modifier
                        .size(size)
                        .graphicsLayer { rotationZ = rotation }
                        .clip(dailyMixArtworkShape(index, thirdShapeCornerRadius = 30.dp)),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                            androidx.compose.ui.graphics.Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.surface,
                        ),
                        endY = 900f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp, end = 22.dp, bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_daily_mix),
                style = rememberDailyMixTitleStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_daily_mix_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
