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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.ui.design.TrackCodecBadges
import org.shilpo.laboon.ui.design.painterResource

@Composable
internal fun HomeArtwork(artworkUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val request = remember(context, artworkUrl) {
        ImageRequest.Builder(context).data(artworkUrl).crossfade(true).build()
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.app_icon_small),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun HomeSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun HomeRecommendationPanel(
    title: String,
    subtitle: String,
    tracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
) {
    val featured = remember(tracks) { tracks.take(3) }
    val cookie = MaterialShapes.Cookie6Sided.toShape()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                featured.forEachIndexed { index, track ->
                    HomeArtwork(
                        artworkUrl = track.artworkUrl,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(when (index) {
                                0 -> CircleShape
                                1 -> MaterialTheme.shapes.extraLarge
                                else -> cookie
                            }),
                    )
                }
            }
            HomeSectionHeading(title = title, modifier = Modifier.padding(8.dp), subtitle = subtitle)
            Column {
                tracks.take(5).forEachIndexed { index, track ->
                    HomeSongRow(
                        track = track,
                        onTrackClick = onTrackClick,
                        onDownloadTrack = onDownloadTrack,
                    )
                    if (index < minOf(tracks.size, 5) - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )
                    }
                }
            }
            if (tracks.size > 5) {
                HomeTrackPills(
                    title = null,
                    tracks = tracks.drop(5),
                    onTrackClick = onTrackClick,
                    onDownloadTrack = onDownloadTrack,
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }
    }
}

@Composable
internal fun HomeSongRow(
    track: HomeTrack,
    onTrackClick: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playable = track.isPlayable
    val enabled = playable || !track.providerTrackId.isNullOrBlank()
    val action = {
        if (playable) onTrackClick(track) else if (enabled) onDownloadTrack(track)
    }
    val rowShape = MaterialTheme.shapes.medium
    Surface(
        color = Color.Transparent,
        shape = rowShape,
        modifier = modifier.fillMaxWidth().clip(rowShape).clickable(
            enabled = enabled,
            role = Role.Button,
            onClickLabel = stringResource(
                if (playable) R.string.home_play_track else R.string.home_download_track,
                track.title,
                track.artist,
            ),
            onClick = action,
        ),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 76.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeArtwork(
                artworkUrl = track.artworkUrl,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TrackCodecBadges(track = track, height = 12.dp)
            }
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (playable) R.drawable.ic_play else R.drawable.ic_cloud_download),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun HomeTrackPills(
    title: String?,
    tracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    subtitle: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val columns = remember(tracks) { tracks.chunked(3) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (title != null) {
            HomeSectionHeading(title, Modifier.padding(horizontal = 24.dp), subtitle)
        }
        LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(columns, key = { it.first().id }) { column ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    column.forEach { track ->
                        val enabled = track.isPlayable || !track.providerTrackId.isNullOrBlank()
                        Surface(
                            onClick = {
                                if (track.isPlayable) onTrackClick(track) else onDownloadTrack(track)
                            },
                            enabled = enabled,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 64.dp)
                                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HomeArtwork(track.artworkUrl, Modifier.size(40.dp).clip(CircleShape))
                                Column(modifier = Modifier.width(136.dp)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmallEmphasized,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    painter = painterResource(
                                        if (track.isPlayable) R.drawable.ic_play else R.drawable.ic_cloud_download,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
