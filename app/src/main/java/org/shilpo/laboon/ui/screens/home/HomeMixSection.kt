@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeFeedState
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.ui.design.SkeletonTextLine
import org.shilpo.laboon.ui.design.painterResource

internal fun homeMixTracks(feed: HomeFeedState): List<HomeTrack> =
    listOf(feed.rotation, feed.recommended, feed.topTracks, feed.weeklyPicks)
        .firstOrNull { it.items.isNotEmpty() }
        ?.items.orEmpty()
        .distinctBy(HomeTrack::id)

@Composable
internal fun HomeMixSection(
    tracks: List<HomeTrack>,
    onPlayMix: (List<HomeTrack>) -> Unit,
    onTrackClick: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val playableTracks = remember(tracks) { tracks.filter(HomeTrack::isPlayable) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeMixHeader(
                    tracks = tracks,
                    isLoading = isLoading,
                    onShuffle = { onPlayMix(playableTracks) },
                    canShuffle = playableTracks.isNotEmpty(),
                    modifier = Modifier.weight(0.9f),
                )
                HomeArtworkCollage(
                    tracks = tracks,
                    onTrackClick = onTrackClick,
                    onDownloadTrack = onDownloadTrack,
                    modifier = Modifier.weight(1.1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeMixHeader(
                    tracks = tracks,
                    isLoading = isLoading,
                    onShuffle = { onPlayMix(playableTracks) },
                    canShuffle = playableTracks.isNotEmpty(),
                )
                HomeArtworkCollage(
                    tracks = tracks,
                    onTrackClick = onTrackClick,
                    onDownloadTrack = onDownloadTrack,
                )
            }
        }
    }
}

@Composable
private fun HomeMixHeader(
    tracks: List<HomeTrack>,
    isLoading: Boolean,
    onShuffle: () -> Unit,
    canShuffle: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 236.dp)
            .padding(start = 28.dp, end = 28.dp, top = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.home_your_mix),
                style = MaterialTheme.typography.displayLargeEmphasized.copy(
                    fontSize = 64.sp,
                    lineHeight = 62.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 280.dp),
            )
            if (isLoading && tracks.isEmpty()) {
                androidx.compose.material3.LoadingIndicator()
            } else if (tracks.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.home_mix_now_playing,
                        tracks.first().title,
                        tracks.first().artist,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            } else {
                Text(
                    text = stringResource(R.string.home_your_mix_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (canShuffle) {
            LargeFloatingActionButton(
                onClick = onShuffle,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = stringResource(R.string.home_shuffle_mix),
                    modifier = Modifier.size(36.dp),
                )
            }
        } else if (!isLoading && tracks.isNotEmpty()) {
            if (tracks.any { !it.isPlayable && !it.providerTrackId.isNullOrBlank() }) {
                Text(
                    text = stringResource(R.string.home_mix_download_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun HomeArtworkCollage(
    tracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val covers = remember(tracks) {
        tracks.distinctBy { it.artworkUrl?.takeIf(String::isNotBlank) ?: it.id }.take(5)
    }
    val cookie = MaterialShapes.Cookie6Sided.toShape()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val unit = maxWidth.coerceAtMost(420.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = unit, height = unit * 1.04f)) {
                HomeCollageCover(
                    track = covers.getOrNull(0),
                    shape = CircleShape,
                    rotation = 45f,
                    onTrackClick = onTrackClick,
                    onDownloadTrack = onDownloadTrack,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = unit * 0.02f)
                        .size(width = unit * 0.36f, height = unit * 0.62f),
                )
                HomeCollageCover(
                    track = covers.getOrNull(1),
                    shape = CircleShape,
                    onTrackClick = onTrackClick,
                    onDownloadTrack = onDownloadTrack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = unit * 0.02f, y = unit * 0.04f)
                        .size(unit * 0.24f),
                )
                HomeCollageCover(
                    track = covers.getOrNull(2),
                    shape = CircleShape,
                    onTrackClick = onTrackClick,
                    onDownloadTrack = onDownloadTrack,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -unit * 0.02f, y = unit * 0.32f)
                        .size(unit * 0.24f),
                )
                HomeCollageCover(
                    track = covers.getOrNull(3),
                    shape = MaterialTheme.shapes.extraLarge,
                    rotation = -18f,
                    onTrackClick = onTrackClick,
                    onDownloadTrack = onDownloadTrack,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = unit * 0.07f, y = -unit * 0.07f)
                        .size(unit * 0.34f),
                )
                HomeCollageCover(
                    track = covers.getOrNull(4),
                    shape = cookie,
                    onTrackClick = onTrackClick,
                    onDownloadTrack = onDownloadTrack,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(unit * 0.56f),
                )
            }
        }
    }
}

@Composable
private fun HomeCollageCover(
    track: HomeTrack?,
    shape: Shape,
    onTrackClick: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
) {
    val actionable = track != null && (track.isPlayable || !track.providerTrackId.isNullOrBlank())
    val label = track?.let {
        stringResource(
            if (it.isPlayable) R.string.home_play_track else R.string.home_download_track,
            it.title,
            it.artist,
        )
    }
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (track != null) {
                    Modifier
                        .semantics { contentDescription = label.orEmpty() }
                        .clickable(enabled = actionable, role = Role.Button) {
                            if (track.isPlayable) onTrackClick(track) else onDownloadTrack(track)
                        }
                } else Modifier
            ),
    ) {
        if (track != null) {
            HomeArtwork(
                artworkUrl = track.artworkUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
