@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.home.HomeAlbum

@Composable
fun HomeAlbumCarousel(
    title: String,
    albums: List<HomeAlbum>,
    onAlbumClick: (HomeAlbum) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onPlayAlbum: ((HomeAlbum) -> Unit)? = null,
    onLongClickAlbum: ((HomeAlbum) -> Unit)? = null,
) {
    if (albums.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeSectionHeading(title, Modifier.padding(horizontal = 24.dp), subtitle)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = HomeAlbum::id) { album ->
                Column(
                    modifier = Modifier.width(168.dp).clip(MaterialTheme.shapes.large)
                        .combinedClickable(
                            onClick = { onAlbumClick(album) },
                            onLongClick = onLongClickAlbum?.let { { it(album) } },
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeArtwork(album.artworkUrl, Modifier.size(168.dp).clip(MaterialTheme.shapes.large))
                    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            album.artist, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
