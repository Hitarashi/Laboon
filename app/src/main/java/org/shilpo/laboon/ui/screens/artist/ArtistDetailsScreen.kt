@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.artist

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.home.ArtistDetailsResult
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyricsporn.LyricspornArtist
import org.shilpo.laboon.ui.design.CodecIcon
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private const val PLAYING_TRACK_WAVEFORM_BAR_COUNT = 4
private val PLAYING_TRACK_WAVEFORM_BAR_WIDTH = 2.dp
private val PLAYING_TRACK_WAVEFORM_BAR_SPACING = 2.dp
private val PLAYING_TRACK_WAVEFORM_WIDTH =
    (PLAYING_TRACK_WAVEFORM_BAR_WIDTH + PLAYING_TRACK_WAVEFORM_BAR_SPACING) *
            PLAYING_TRACK_WAVEFORM_BAR_COUNT
private val PLAYING_TRACK_WAVEFORM_MAX_BAR_HEIGHT = 18.dp

@Composable
fun ArtistDetailsScreen(
    state: ArtistDetailsUiState,
    bottomClearance: Dp,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStartPlayback: (HomeTrack, List<HomeTrack>) -> Unit,
    onPlayNext: (HomeTrack) -> Unit,
    onAddToQueue: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    isDownloadPending: (HomeTrack) -> Boolean,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    onOpenTrackAlbum: ((HomeTrack) -> Unit)? = null,
) {
    val artist = (state as? ArtistDetailsUiState.Loaded)?.artist
    val context = LocalContext.current
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = TopAppBarDefaults.windowInsets,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = artist?.name ?: "Artist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(90f),
                        )
                    }
                },
                actions = {
                    if (artist?.url != null) {
                        IconButton(
                            onClick = { openArtistInAppleMusic(context, artist.url) },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stage_link),
                                contentDescription = "Open in Apple Music",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                when (state) {
                    ArtistDetailsUiState.Loading -> ArtistLoadingContent(
                        bottomClearance = bottomClearance,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    ArtistDetailsUiState.NotFound -> ArtistMessageContent(
                        title = "Artist not found",
                        message = "This artist isn’t available in the current Apple Music storefront.",
                        action = "Go back",
                        onAction = onBack,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is ArtistDetailsUiState.Failed -> {
                        val message = when (state.reason) {
                            ArtistDetailsResult.FailureReason.API_UNAVAILABLE ->
                                "Connect to Lyricsporn to load artist details."

                            ArtistDetailsResult.FailureReason.NETWORK ->
                                "Check your connection and try again."

                            ArtistDetailsResult.FailureReason.SERVER,
                            ArtistDetailsResult.FailureReason.INVALID_RESPONSE ->
                                "Artist details couldn’t be loaded right now."
                        }
                        ArtistMessageContent(
                            title = "Couldn’t load artist",
                            message = message,
                            action = "Try again",
                            onAction = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    is ArtistDetailsUiState.Loaded -> ArtistContent(
                        artist = state.artist,
                        topSongs = state.topSongs,
                        latestRelease = state.latestRelease,
                        albums = state.albums,
                        singles = state.singles,
                        similarArtists = state.similarArtists,
                        bottomClearance = bottomClearance,
                        maxWidth = maxWidth,
                        onStartPlayback = onStartPlayback,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onDownloadTrack = onDownloadTrack,
                        isDownloadPending = isDownloadPending,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        onOpenTrackAlbum = onOpenTrackAlbum,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistLoadingContent(
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 1040.dp)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Loading artist…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(bottomClearance))
    }
}

@Composable
private fun ArtistMessageContent(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
            Text(action)
        }
    }
}

@Composable
private fun ArtistContent(
    artist: LyricspornArtist,
    topSongs: List<HomeTrack>,
    latestRelease: HomeAlbum?,
    albums: List<HomeAlbum>,
    singles: List<HomeAlbum>,
    similarArtists: List<HomeArtist>,
    bottomClearance: Dp,
    maxWidth: Dp,
    onStartPlayback: (HomeTrack, List<HomeTrack>) -> Unit,
    onPlayNext: (HomeTrack) -> Unit,
    onAddToQueue: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    isDownloadPending: (HomeTrack) -> Boolean,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    currentTrackId: String?,
    isPlaying: Boolean,
    onOpenTrackAlbum: ((HomeTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val availableTopSongs = remember(topSongs) { topSongs.filter(HomeTrack::isPlayable) }
    val displayTopSongs = remember(topSongs) { topSongs.take(10) }
    val editorialNotes = remember(artist.id, artist.editorialNotes) {
        artist.editorialNotes.cleanEditorialDescription()
    }

    LazyColumn(
        modifier = modifier
            .widthIn(max = 1040.dp)
            .fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 16.dp,
            end = 20.dp,
            bottom = bottomClearance + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "artist_hero") {
            ArtistHero(
                artist = artist,
                hasPlayableTracks = availableTopSongs.isNotEmpty() || topSongs.isNotEmpty(),
                onPlay = {
                    val tracksToPlay =
                        if (availableTopSongs.isNotEmpty()) availableTopSongs else topSongs
                    tracksToPlay.firstOrNull()?.let { onStartPlayback(it, tracksToPlay) }
                },
                onShuffle = {
                    val pool = if (availableTopSongs.isNotEmpty()) availableTopSongs else topSongs
                    val shuffled = pool.shuffled()
                    shuffled.firstOrNull()?.let { onStartPlayback(it, shuffled) }
                },
            )
        }

        if (latestRelease != null) {
            item(key = "latest_release_heading") {
                SectionHeading(text = "Latest Release")
            }
            item(key = "latest_release_card") {
                LatestReleaseCard(
                    album = latestRelease,
                    onClick = {
                        val albumId = latestRelease.appleCatalogId ?: latestRelease.id
                        onOpenAlbum(albumId)
                    },
                )
            }
        }

        if (displayTopSongs.isNotEmpty()) {
            item(key = "top_songs_heading") {
                SectionHeading(text = "Top Songs")
            }
            item(key = "top_songs_list") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    displayTopSongs.forEachIndexed { index, track ->
                        ArtistTrackRow(
                            index = index,
                            count = displayTopSongs.size,
                            track = track,
                            isCurrentTrack = track.id == currentTrackId,
                            isPlaying = isPlaying,
                            pending = isDownloadPending(track),
                            onClick = {
                                val contextPool =
                                    if (availableTopSongs.isNotEmpty()) availableTopSongs else topSongs
                                if (track.isPlayable) {
                                    onStartPlayback(track, contextPool)
                                } else {
                                    onStartPlayback(track, topSongs)
                                }
                            },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onDownload = { onDownloadTrack(track) },
                            onOpenAlbum = if (onOpenTrackAlbum != null) {
                                { onOpenTrackAlbum(track) }
                            } else if (track.providerTrackId != null || track.album != null) {
                                {
                                    val albumId = track.providerTrackId ?: track.id
                                    onOpenAlbum(albumId)
                                }
                            } else null,
                        )
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item(key = "albums_heading") {
                SectionHeading(text = "Albums")
            }
            item(key = "albums_row") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(albums, key = HomeAlbum::id) { album ->
                        ArtistAlbumCard(
                            album = album,
                            onClick = {
                                onOpenAlbum(album.appleCatalogId ?: album.id)
                            },
                        )
                    }
                }
            }
        }

        if (singles.isNotEmpty()) {
            item(key = "singles_heading") {
                SectionHeading(text = "Singles & EPs")
            }
            item(key = "singles_row") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(singles, key = HomeAlbum::id) { single ->
                        ArtistAlbumCard(
                            album = single,
                            onClick = {
                                onOpenAlbum(single.appleCatalogId ?: single.id)
                            },
                        )
                    }
                }
            }
        }

        if (editorialNotes != null) {
            item(key = "editorial_notes_heading") {
                SectionHeading(text = "About")
            }
            item(key = "editorial_notes_card") {
                ArtistEditorialCard(description = editorialNotes)
            }
        }

        if (similarArtists.isNotEmpty()) {
            item(key = "similar_artists_heading") {
                SectionHeading(text = "Similar Artists")
            }
            item(key = "similar_artists_row") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(similarArtists, key = HomeArtist::id) { similar ->
                        SimilarArtistCard(
                            artist = similar,
                            onClick = {
                                onOpenArtist(similar.name, similar.appleCatalogId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ArtistHero(
    artist: LyricspornArtist,
    hasPlayableTracks: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(128.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!artist.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(artist.artworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.app_icon_small),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }

        Text(
            text = artist.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.9f),
        )

        if (artist.genres.isNotEmpty()) {
            Text(
                text = artist.genres.joinToString(" • "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPlay,
                enabled = hasPlayableTracks,
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "Play Top Songs")
            }

            FilledTonalButton(
                onClick = onShuffle,
                enabled = hasPlayableTracks,
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "Shuffle")
            }
        }
    }
}

@Composable
private fun LatestReleaseCard(
    album: HomeAlbum,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (!album.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(album.artworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.app_icon_small),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "LATEST RELEASE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Latest • ${album.artist}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistTrackRow(
    index: Int,
    count: Int,
    track: HomeTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    pending: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onOpenAlbum: (() -> Unit)?,
) {
    var menuExpanded by remember(track.id) { mutableStateOf(false) }

    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
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
                    Icon(
                        painter = painterResource(R.drawable.app_icon_small),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp),
                    )
                }

                if (isCurrentTrack) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isPlaying) {
                            ArtistWaveform()
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_play),
                                contentDescription = "Playing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = track.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentTrack) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.contentRating.equals("explicit", ignoreCase = true)) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "E",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        },
        supportingContent = {
            val duration = track.durationMs
                ?.takeIf { it > 0L }
                ?.let(::formatTrackDuration)
            val albumSubtitle = track.album ?: track.artist
            val qualityCodec = track.codec?.takeIf(::isQualityCodec)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = albumSubtitle,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (qualityCodec != null) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CodecIcon(
                        codec = qualityCodec,
                        height = 12.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (duration != null) {
                    Text(
                        text = " • $duration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (!track.isPlayable) {
                    IconButton(
                        onClick = onDownload,
                        enabled = !pending,
                    ) {
                        if (pending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_cloud_download),
                                contentDescription = "Download ${track.title}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play Next") },
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Queue") },
                            onClick = {
                                menuExpanded = false
                                onAddToQueue()
                            },
                        )
                        if (!track.isPlayable) {
                            DropdownMenuItem(
                                text = { Text("Download") },
                                onClick = {
                                    menuExpanded = false
                                    onDownload()
                                },
                            )
                        }
                        if (onOpenAlbum != null) {
                            DropdownMenuItem(
                                text = { Text("Go to Album") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenAlbum()
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ArtistAlbumCard(
    album: HomeAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(124.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (!album.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(album.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.app_icon_small),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SimilarArtistCard(
    artist: HomeArtist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (!artist.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(artist.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.app_icon_small),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ArtistEditorialCard(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (expanded) "Show less" else "Read more")
            }
        }
    }
}

@Composable
private fun ArtistWaveform() {
    val transition = rememberInfiniteTransition(label = "artistWaveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "artistWaveformPhase",
    )
    Row(
        modifier = Modifier
            .width(PLAYING_TRACK_WAVEFORM_WIDTH)
            .height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(
            PLAYING_TRACK_WAVEFORM_BAR_SPACING,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PLAYING_TRACK_WAVEFORM_BAR_COUNT) { index ->
            Box(
                modifier = Modifier
                    .width(PLAYING_TRACK_WAVEFORM_BAR_WIDTH)
                    .height(PLAYING_TRACK_WAVEFORM_MAX_BAR_HEIGHT)
                    .graphicsLayer {
                        val barPhase = (phase + index * 0.25f) % 1f
                        val amplitude = 0.4f + 0.6f * sin(barPhase * (2f * PI.toFloat()))
                        scaleY = 0.2f + 0.8f * amplitude
                    }
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

private fun formatTrackDuration(durationMs: Long): String {
    val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L % 60L
    return "$totalMinutes:${seconds.toString().padStart(2, '0')}"
}

private fun isQualityCodec(codec: String): Boolean {
    return when (val normalized = codec.trim().lowercase(Locale.ROOT)) {
        "alac", "flac", "lossless", "ec-3", "ec3", "atmos", "dolby", "dolby_atmos" -> true
        else -> normalized.contains("dolby") || normalized.contains("atmos")
    }
}

private fun openArtistInAppleMusic(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
}

private fun String?.cleanEditorialDescription(): String? {
    val source = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return HtmlCompat.fromHtml(source, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00a0', ' ')
        .trim()
        .takeIf(String::isNotEmpty)
}
