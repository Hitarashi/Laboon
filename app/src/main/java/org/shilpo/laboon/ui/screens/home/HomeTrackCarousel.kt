@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.ui.design.CodecIcon
import org.shilpo.laboon.ui.design.LiquidGlassPlayButton
import org.shilpo.laboon.ui.design.ProviderIcon
import org.shilpo.laboon.ui.design.liquidGlassBackdropProducer
import org.shilpo.laboon.ui.design.rememberLiquidGlassBackdropState

@Composable
fun HomeTrackCarousel(
    title: String,
    tracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onPlayTrack: ((HomeTrack) -> Unit)? = null,
    onLongClickTrack: ((HomeTrack) -> Unit)? = null,
) {
    if (tracks.isEmpty()) return

    val carouselState = rememberCarouselState { tracks.size }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
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

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 186.dp,
            itemSpacing = 10.dp,
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(206.dp),
        ) { index ->
            val track = tracks[index]
            HomeTrackCard(
                track = track,
                onClick = { onTrackClick(track) },
                onPlayClick = {
                    if (onPlayTrack != null) {
                        onPlayTrack(track)
                    } else {
                        onTrackClick(track)
                    }
                },
                onLongClick = if (onLongClickTrack != null) {
                    { onLongClickTrack(track) }
                } else null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun CarouselItemScope.HomeTrackCard(
    track: HomeTrack,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val cardBackdropState = rememberLiquidGlassBackdropState()
    val cardBackdropLayer = rememberGraphicsLayer()

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .maskClip(MaterialTheme.shapes.extraLarge)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .liquidGlassBackdropProducer(cardBackdropState, cardBackdropLayer),
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(track.artworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.app_icon_small),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }


                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.72f to Color.Black.copy(alpha = 0.50f),
                                1.0f to Color.Black.copy(alpha = 0.90f),
                            )
                        ),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        ProviderIcon(
                            provider = track.source,
                            height = 9.dp,
                            tint = Color.White.copy(alpha = 0.75f),
                        )
                        CodecIcon(
                            codec = track.codec,
                            height = 9.dp,
                            tint = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }

                LiquidGlassPlayButton(
                    onClick = onPlayClick,
                    backdropState = cardBackdropState,
                    size = 38.dp,
                    iconSize = 19.dp,
                )
            }
        }
    }
}
