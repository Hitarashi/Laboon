@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.ui.design.TrackCodecBadges
import org.shilpo.laboon.ui.design.painterResource

@Composable
fun HomeTrackCarousel(
    title: String,
    tracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onPlayTrack: ((HomeTrack) -> Unit)? = null,
    onDownloadTrack: ((HomeTrack) -> Unit)? = null,
    onLongClickTrack: ((HomeTrack) -> Unit)? = null,
) {
    if (tracks.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeSectionHeading(title, subtitle, Modifier.padding(horizontal = 24.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks, key = HomeTrack::id) { track ->
                val enabled = track.isPlayable ||
                    (!track.providerTrackId.isNullOrBlank() && onDownloadTrack != null)
                val action = {
                    if (track.isPlayable) (onPlayTrack ?: onTrackClick)(track)
                    else if (enabled) onDownloadTrack?.invoke(track)
                    Unit
                }
                Column(
                    modifier = Modifier.width(168.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box {
                        HomeArtwork(
                            track.artworkUrl,
                            Modifier.size(168.dp).clip(MaterialTheme.shapes.extraLarge)
                                .combinedClickable(
                                    enabled = enabled,
                                    onClick = action,
                                    onClickLabel = stringResource(
                                        if (track.isPlayable) R.string.home_play_track else R.string.home_download_track,
                                        track.title, track.artist,
                                    ),
                                    onLongClick = onLongClickTrack?.let { { it(track) } },
                                ),
                        )
                        FilledTonalIconButton(
                            onClick = action,
                            enabled = enabled,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp),
                        ) {
                            Icon(
                                painterResource(if (track.isPlayable) R.drawable.ic_play else R.drawable.ic_cloud_download),
                                contentDescription = stringResource(
                                    if (track.isPlayable) R.string.home_play_track else R.string.home_download_track,
                                    track.title, track.artist,
                                ),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            track.artist, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        TrackCodecBadges(track, height = 12.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
