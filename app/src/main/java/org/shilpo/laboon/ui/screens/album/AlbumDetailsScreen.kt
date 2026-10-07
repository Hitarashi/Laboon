@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.album

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.home.AlbumDetailsResult
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyricsporn.LyricspornAlbum
import org.shilpo.laboon.lyricsporn.LyricspornAlbumVersion
import org.shilpo.laboon.ui.design.CodecIcon
import org.shilpo.laboon.ui.design.TrackCodecBadges
import org.shilpo.laboon.ui.design.getTrackQualityCodecs
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private const val PLAYING_TRACK_WAVEFORM_BAR_COUNT = 5
private val PLAYING_TRACK_WAVEFORM_BAR_WIDTH = 2.dp
private val PLAYING_TRACK_WAVEFORM_BAR_SPACING = 2.dp
private val PLAYING_TRACK_WAVEFORM_WIDTH =
    (PLAYING_TRACK_WAVEFORM_BAR_WIDTH + PLAYING_TRACK_WAVEFORM_BAR_SPACING) *
            PLAYING_TRACK_WAVEFORM_BAR_COUNT
private val PLAYING_TRACK_WAVEFORM_MAX_BAR_HEIGHT = 22.dp

@Composable
fun AlbumDetailsScreen(
    state: AlbumDetailsUiState,
    bottomClearance: Dp,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStartPlayback: (HomeTrack, List<HomeTrack>) -> Unit,
    onPlayNext: (HomeTrack) -> Unit,
    onAddToQueue: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    isDownloadPending: (HomeTrack) -> Boolean,
    onOpenAlbumVersion: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenRecordLabel: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
) {
    val album = (state as? AlbumDetailsUiState.Loaded)?.album
    val context = LocalContext.current
    val albumUrl = remember(album?.url) { canonicalAppleMusicAlbumUrl(album?.url) }
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = TopAppBarDefaults.windowInsets,
        topBar = {
            TopAppBar(
                title = { Text("Album", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(90f),
                        )
                    }
                },
                actions = {
                    if (album != null && albumUrl != null) {
                        IconButton(
                            onClick = { shareAlbum(context, album.name, albumUrl) },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_share_album),
                                contentDescription = "Share album",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(
                            onClick = { openAlbumInAppleMusic(context, albumUrl) },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stage_link),
                                contentDescription = "Open album in Apple Music",
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
                    AlbumDetailsUiState.Loading -> LoadingContent(
                        bottomClearance = bottomClearance,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    AlbumDetailsUiState.NotFound -> MessageContent(
                        title = "Album not found",
                        message = "This album isn’t available in the current Apple Music storefront.",
                        action = "Try again",
                        onAction = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is AlbumDetailsUiState.Failed -> {
                        val message = when (state.reason) {
                            AlbumDetailsResult.FailureReason.API_UNAVAILABLE ->
                                "Connect to Lyricsporn to load album details."

                            AlbumDetailsResult.FailureReason.NETWORK ->
                                "Check your connection and try again."

                            AlbumDetailsResult.FailureReason.SERVER,
                            AlbumDetailsResult.FailureReason.INVALID_RESPONSE ->
                                "Album details couldn’t be loaded right now."
                        }
                        MessageContent(
                            title = "Couldn’t load album",
                            message = message,
                            action = "Try again",
                            onAction = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    is AlbumDetailsUiState.Loaded -> AlbumContent(
                        album = state.album,
                        tracks = state.tracks,
                        bottomClearance = bottomClearance,
                        maxWidth = maxWidth,
                        onStartPlayback = onStartPlayback,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onDownloadTrack = onDownloadTrack,
                        isDownloadPending = isDownloadPending,
                        onOpenAlbumVersion = onOpenAlbumVersion,
                        onOpenArtist = onOpenArtist,
                        onOpenRecordLabel = onOpenRecordLabel,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(
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
        Text("Loading album…", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(bottomClearance))
    }
}

@Composable
private fun MessageContent(
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
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
            Text(action)
        }
    }
}

@Composable
private fun AlbumContent(
    album: LyricspornAlbum,
    tracks: List<HomeTrack>,
    bottomClearance: Dp,
    maxWidth: Dp,
    onStartPlayback: (HomeTrack, List<HomeTrack>) -> Unit,
    onPlayNext: (HomeTrack) -> Unit,
    onAddToQueue: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    isDownloadPending: (HomeTrack) -> Boolean,
    onOpenAlbumVersion: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenRecordLabel: (String, String?) -> Unit,
    currentTrackId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val availableTracks = remember(tracks) { tracks.filter(HomeTrack::isPlayable) }
    val cachedFormats = remember(tracks) {
        tracks.asSequence()
            .flatMap { it.availableFormats.asSequence() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy(::cacheFormatSortOrder)
            .toList()
    }
    val albumTrackUrls = remember(album.tracks) {
        album.tracks.associate { it.id to it.url }
    }
    val albumDescription = remember(album.id, album.editorialNotes) {
        album.editorialNotes.cleanEditorialDescription()
    }
    val footerMetadata = albumFooterMetadata(album)

    LazyColumn(
        modifier = modifier
            .widthIn(max = 1040.dp)
            .fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = bottomClearance + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "album_summary") {
            AlbumHero(
                album = album,
                availableCount = availableTracks.size,
                cachedFormats = cachedFormats,
                maxWidth = maxWidth,
                onOpenArtist = onOpenArtist,
                onPlay = {
                    if (availableTracks.isNotEmpty()) {
                        onStartPlayback(availableTracks.first(), availableTracks)
                    }
                },
                onShuffle = {
                    val shuffled = availableTracks.shuffled()
                    shuffled.firstOrNull()?.let { onStartPlayback(it, shuffled) }
                },
            )
        }

        if (albumDescription != null) {
            item(key = "album_description") {
                AlbumDescription(albumDescription)
            }
        }

        item(key = "tracks_heading") {
            Text(
                text = "Tracks",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        item(key = "album_tracks") {
            if (tracks.isEmpty()) {
                Text(
                    "The track listing isn’t available for this album.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    tracks.forEachIndexed { index, track ->
                        val shareUrl = track.providerTrackId?.let { trackId ->
                            appleMusicTrackShareUrl(
                                trackUrl = albumTrackUrls[trackId],
                                albumUrl = album.url,
                                trackCatalogId = trackId,
                            )
                        }
                        AlbumTrackRow(
                            index = index,
                            count = tracks.size,
                            track = track,
                            isCurrentTrack = track.id == currentTrackId,
                            isPlaying = isPlaying,
                            pending = isDownloadPending(track),
                            shareUrl = shareUrl,
                            onClick = {
                                if (track.isPlayable) onStartPlayback(
                                    track,
                                    availableTracks
                                )
                            },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onDownload = { onDownloadTrack(track) },
                        )
                    }
                }
            }
        }

        if (footerMetadata.hasContent) {
            item(key = "album_metadata_footer") {
                AlbumMetadataFooter(
                    metadata = footerMetadata,
                    onOpenRecordLabel = onOpenRecordLabel,
                )
            }
        }

        if (album.otherVersions.isNotEmpty()) {
            item(key = "album_versions") {
                AlbumVersions(
                    versions = album.otherVersions,
                    onOpenAlbumVersion = onOpenAlbumVersion,
                )
            }
        }
    }
}

@Composable
private fun AlbumDescription(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }
    var hasMore by remember(description) { mutableStateOf(false) }

    Text(
        text = description,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = expanded || hasMore,
                onClickLabel = if (expanded) "Collapse description" else "Expand description",
            ) { expanded = !expanded },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layoutResult ->
            if (!expanded) hasMore = layoutResult.hasVisualOverflow
        },
    )
}

private fun String?.cleanEditorialDescription(): String? {
    val source = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return HtmlCompat.fromHtml(source, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00a0', ' ')
        .trim()
        .takeIf(String::isNotEmpty)
}

@Composable
private fun AlbumHero(
    album: LyricspornAlbum,
    availableCount: Int,
    cachedFormats: List<String>,
    maxWidth: Dp,
    onOpenArtist: (String, String?) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val totalDuration = album.totalDurationMs?.let(::formatAlbumDuration)
    val releaseYear = album.releaseDate?.take(4)?.takeIf { it.length == 4 }
    val wideLayout = maxWidth >= 640.dp
    val artworkSize = minOf(280.dp, maxWidth - 40.dp)

    if (wideLayout) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArtwork(album, Modifier.size(artworkSize))
            AlbumTextDetails(
                album = album,
                releaseYear = releaseYear,
                cachedFormats = cachedFormats,
                totalDuration = totalDuration,
                availableCount = availableCount,
                onOpenArtist = onOpenArtist,
                onPlay = onPlay,
                onShuffle = onShuffle,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlbumArtwork(album, Modifier.size(artworkSize))
            Spacer(Modifier.height(20.dp))
            AlbumTextDetails(
                album = album,
                releaseYear = releaseYear,
                cachedFormats = cachedFormats,
                totalDuration = totalDuration,
                availableCount = availableCount,
                onOpenArtist = onOpenArtist,
                onPlay = onPlay,
                onShuffle = onShuffle,
                modifier = Modifier.fillMaxWidth(),
                centered = true,
            )
        }
    }
}

@Composable
private fun AlbumArtwork(album: LyricspornAlbum, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (!album.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(album.artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "${album.name} album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text("♪", style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}

@Composable
private fun AlbumTextDetails(
    album: LyricspornAlbum,
    releaseYear: String?,
    cachedFormats: List<String>,
    totalDuration: String?,
    availableCount: Int,
    onOpenArtist: (String, String?) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    val tracksAvailable = availableCount > 0
    val titleAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val genres = album.genres.filterNot { it.equals("Music", ignoreCase = true) }
    val artistName = album.artistName?.trim()?.takeIf(String::isNotEmpty)
    val actionButtonHeight = ButtonDefaults.MediumContainerHeight
    val actionButtonShapes = ButtonDefaults.shapesFor(actionButtonHeight)
    val actionButtonContentPadding = ButtonDefaults.contentPaddingFor(
        buttonHeight = actionButtonHeight,
        hasStartIcon = true,
    )
    val actionButtonIconSize = ButtonDefaults.iconSizeFor(actionButtonHeight)
    val actionButtonIconSpacing = ButtonDefaults.iconSpacingFor(actionButtonHeight)
    val actionButtonTextStyle = ButtonDefaults.textStyleFor(actionButtonHeight)

    Column(
        modifier = modifier,
        horizontalAlignment = titleAlignment,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = artistName ?: "Unknown artist",
            modifier = if (artistName != null) {
                Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = "Open artist $artistName",
                ) {
                    val appleArtistId = album.artistUrl?.substringAfterLast("/")
                        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
                    onOpenArtist(artistName, appleArtistId)
                }
            } else {
                Modifier
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (artistName != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textDecoration = if (artistName != null) TextDecoration.Underline else TextDecoration.None,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (releaseYear != null || cachedFormats.isNotEmpty() || totalDuration != null) {
            AlbumMetadata(
                releaseYear = releaseYear,
                cachedFormats = cachedFormats,
                totalDuration = totalDuration,
                centered = centered,
            )
        }
        if (genres.isNotEmpty()) {
            Text(
                text = genres.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onPlay,
                enabled = tracksAvailable,
                shapes = actionButtonShapes,
                modifier = Modifier.height(actionButtonHeight),
                contentPadding = actionButtonContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(actionButtonIconSize),
                )
                Spacer(Modifier.width(actionButtonIconSpacing))
                Text(
                    if (tracksAvailable && availableCount < (album.trackCount
                            ?: album.tracks.size)
                    ) {
                        "Play available"
                    } else {
                        "Play all"
                    },
                    style = actionButtonTextStyle,
                )
            }
            FilledTonalButton(
                onClick = onShuffle,
                enabled = tracksAvailable,
                shapes = actionButtonShapes,
                modifier = Modifier.height(actionButtonHeight),
                contentPadding = actionButtonContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = null,
                    modifier = Modifier.size(actionButtonIconSize),
                )
                Spacer(Modifier.width(actionButtonIconSpacing))
                Text("Shuffle", style = actionButtonTextStyle)
            }
        }
    }
}

@Composable
private fun AlbumMetadata(
    releaseYear: String?,
    cachedFormats: List<String>,
    totalDuration: String?,
    centered: Boolean,
) {
    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var hasPreviousItem = false
        if (releaseYear != null) {
            Text(
                text = releaseYear,
                style = MaterialTheme.typography.bodyMedium,
                color = metadataColor,
            )
            hasPreviousItem = true
        }
        cachedFormats.forEach { format ->
            if (hasPreviousItem) MetadataSeparator(metadataColor)
            CodecIcon(
                codec = format,
                height = 14.dp,
                tint = metadataColor,
            )
            hasPreviousItem = true
        }
        if (totalDuration != null) {
            if (hasPreviousItem) MetadataSeparator(metadataColor)
            Text(
                text = totalDuration,
                style = MaterialTheme.typography.bodyMedium,
                color = metadataColor,
            )
        }
    }
}

@Composable
private fun MetadataSeparator(color: androidx.compose.ui.graphics.Color) {
    Text(
        text = "·",
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

private fun cacheFormatSortOrder(format: String): Int = when (format.lowercase(Locale.ROOT)) {
    "ec-3", "ec3", "atmos", "dolby", "dolby_atmos" -> 0
    "alac", "flac", "lossless" -> 1
    "aac" -> 2
    else -> 3
}

@Composable
private fun AlbumTrackRow(
    index: Int,
    count: Int,
    track: HomeTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    pending: Boolean,
    shareUrl: String?,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
) {
    var menuExpanded by remember(track.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val rowHeightModifier = if (LocalDensity.current.fontScale <= 1f) {
        Modifier.heightIn(max = 64.dp)
    } else {
        Modifier
    }

    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowHeightModifier
            .fillMaxWidth()
            .clickable(enabled = track.isPlayable, onClick = onClick),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        leadingContent = {
            if (isCurrentTrack) {
                Box(
                    modifier = Modifier.width(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        PlayingTrackWaveform()
                    } else {
                        TrackNumber(index)
                    }
                }
            } else {
                TrackNumber(index)
            }
        },
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = track.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
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
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
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
                ?.let(::formatAlbumDuration)
            val subtitle = duration ?: track.artist
            val codecs = remember(track) { getTrackQualityCodecs(track) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subtitle,
                    modifier = if (duration == null) Modifier.weight(1f) else Modifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (duration != null && codecs.isNotEmpty()) {
                    MetadataSeparator(MaterialTheme.colorScheme.onSurfaceVariant)
                    TrackCodecBadges(
                        track = track,
                        height = 12.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_download),
                            contentDescription = if (pending) {
                                "${track.title} download in progress"
                            } else {
                                "Download ${track.title}"
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (pending) 0.38f else 1f,
                            ),
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = "Actions for ${track.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (shareUrl != null) {
                            DropdownMenuItem(
                                text = { Text("Share song") },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_share_album),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    shareAppleMusicLink(
                                        context = context,
                                        title = track.title,
                                        url = shareUrl,
                                        chooserTitle = "Share song",
                                    )
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            enabled = track.isPlayable,
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            enabled = track.isPlayable,
                            onClick = {
                                menuExpanded = false
                                onAddToQueue()
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun TrackNumber(index: Int) {
    Text(
        text = (index + 1).toString().padStart(2, '0'),
        modifier = Modifier.width(32.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlayingTrackWaveform() {
    val transition = rememberInfiniteTransition(label = "playingTrackWaveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "playingTrackWaveformPhase",
    )
    Row(
        modifier = Modifier
            .width(PLAYING_TRACK_WAVEFORM_WIDTH)
            .height(24.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Now playing"
            },
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
                        val barPhase = (phase + index * 0.2f) % 1f
                        val amplitude = 0.5f +
                                0.5f * sin(barPhase * (2f * PI.toFloat()))
                        scaleY = 0.14f + 0.86f * amplitude
                    }
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

private fun isTrackQualityCodec(codec: String): Boolean {
    return when (val normalized = codec.trim().lowercase(Locale.ROOT)) {
        "alac", "flac", "lossless", "ec-3", "ec3", "atmos", "dolby", "dolby_atmos" -> true
        else -> normalized.contains("dolby") || normalized.contains("atmos")
    }
}

@Composable
private fun AlbumVersions(
    versions: List<LyricspornAlbumVersion>,
    onOpenAlbumVersion: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Other versions · ${versions.size}",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(versions, key = LyricspornAlbumVersion::id) { version ->
                VersionCard(version, onClick = { onOpenAlbumVersion(version.id) })
            }
        }
    }
}

@Composable
private fun VersionCard(
    version: LyricspornAlbumVersion,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(264.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                if (!version.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(version.artworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${version.name} artwork",
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    version.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    version.releaseDate?.take(4),
                    version.trackCount?.let { "$it tracks" },
                ).joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal data class AlbumFooterMetadata(
    val releaseDate: String?,
    val trackSummary: String?,
    val copyright: String?,
    val recordLabel: String?,
    val recordLabelId: String? = null,
) {
    val hasContent: Boolean
        get() = releaseDate != null || trackSummary != null || copyright != null ||
                recordLabel != null
}

internal fun albumFooterMetadata(album: LyricspornAlbum): AlbumFooterMetadata {
    val trackCount = album.trackCount?.takeIf { it > 0 }
        ?: album.tracks.size.takeIf { it > 0 }
    val duration = album.totalDurationMs?.let(::formatAlbumDurationWords)
    val songCount = trackCount?.let { count ->
        "$count ${if (count == 1) "song" else "songs"}"
    }
    val trackSummary = when {
        songCount != null && duration != null -> "$songCount, $duration"
        songCount != null -> songCount
        else -> duration
    }

    return AlbumFooterMetadata(
        releaseDate = album.releaseDate?.trim()?.takeIf(String::isNotEmpty)
            ?.let(::formatAlbumReleaseDate),
        trackSummary = trackSummary,
        copyright = album.copyright?.trim()?.takeIf(String::isNotEmpty),
        recordLabel = album.recordLabel?.trim()?.takeIf(String::isNotEmpty),
        recordLabelId = album.recordLabelId?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun formatAlbumReleaseDate(releaseDate: String): String {
    val datePart = releaseDate.take(10)
    return runCatching {
        LocalDate.parse(datePart).format(
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
        )
    }.getOrDefault(releaseDate)
}

private fun formatAlbumDurationWords(durationMs: Long): String? {
    if (durationMs <= 0L) return null

    val totalMinutes = durationMs / 60_000L
    if (totalMinutes == 0L) return null
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return buildList {
        if (hours > 0L) add("$hours ${if (hours == 1L) "hour" else "hours"}")
        if (minutes > 0L) add("$minutes ${if (minutes == 1L) "minute" else "minutes"}")
    }.joinToString(" ")
}

internal fun canonicalAppleMusicAlbumUrl(url: String?): String? {
    val value = url?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    return value.takeIf {
        uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("music.apple.com", ignoreCase = true) &&
                uri.userInfo == null
    }
}

private fun shareAlbum(context: Context, albumName: String, albumUrl: String) {
    shareAppleMusicLink(context, albumName, albumUrl, "Share album")
}

private fun shareAppleMusicLink(
    context: Context,
    title: String,
    url: String,
    chooserTitle: String,
) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    }
    try {
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
}

internal fun appleMusicTrackShareUrl(
    trackUrl: String?,
    albumUrl: String?,
    trackCatalogId: String?,
): String? {
    val trackId = trackCatalogId?.trim()?.takeIf { id ->
        id.isNotEmpty() && id.all { it in '0'..'9' }
    } ?: return null

    val canonicalTrackUrl = canonicalAppleMusicAlbumUrl(trackUrl)
    if (canonicalTrackUrl != null && isCanonicalTrackUrl(canonicalTrackUrl, trackId)) {
        val uri = runCatching { URI(canonicalTrackUrl) }.getOrNull() ?: return null
        return if (uri.rawQuery?.let(::hasTrackIdQueryParameter) == true) {
            withAppleMusicTrackId(canonicalTrackUrl, trackId)
        } else {
            canonicalTrackUrl
        }
    }

    val canonicalAlbumUrl = canonicalAppleMusicAlbumUrl(albumUrl)
        ?.takeIf(::isCanonicalAlbumUrl)
        ?: return null
    return withAppleMusicTrackId(canonicalAlbumUrl, trackId)
}

private fun isCanonicalAlbumUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.rawPath.orEmpty().split('/').any { it == "album" }
}

private fun isCanonicalTrackUrl(url: String, trackId: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val pathSegments = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
    val isAlbumOrSongPath = pathSegments.contains("album") || pathSegments.contains("song")
    if (!isAlbumOrSongPath) return false

    val trackIds = queryParameterValues(uri.rawQuery, "i")
    return if (trackIds.isNotEmpty()) {
        trackIds.all { it == trackId }
    } else {
        pathSegments.contains("song") && pathSegments.lastOrNull() == trackId
    }
}

private fun hasTrackIdQueryParameter(rawQuery: String): Boolean =
    queryParameterValues(rawQuery, "i").isNotEmpty()

private fun queryParameterValues(rawQuery: String?, name: String): List<String> =
    rawQuery.orEmpty()
        .split('&')
        .mapNotNull { parameter ->
            val rawName = parameter.substringBefore('=')
            val decodedName = runCatching {
                java.net.URLDecoder.decode(rawName, Charsets.UTF_8.name())
            }.getOrNull()
            if (decodedName != name) return@mapNotNull null
            val rawValue = parameter.substringAfter('=', missingDelimiterValue = "")
            runCatching {
                java.net.URLDecoder.decode(rawValue, Charsets.UTF_8.name())
            }.getOrNull()
        }

private fun withAppleMusicTrackId(url: String, trackId: String): String {
    val fragmentIndex = url.indexOf('#')
    val resourceUrl = if (fragmentIndex >= 0) url.substring(0, fragmentIndex) else url
    val fragment = if (fragmentIndex >= 0) url.substring(fragmentIndex) else ""
    val queryIndex = resourceUrl.indexOf('?')
    val baseUrl = if (queryIndex >= 0) resourceUrl.substring(0, queryIndex) else resourceUrl
    val rawQuery = if (queryIndex >= 0) resourceUrl.substring(queryIndex + 1) else ""
    val retainedParameters = rawQuery.split('&').filter { parameter ->
        parameter.isNotEmpty() && queryParameterValues(parameter, "i").isEmpty()
    }
    val updatedQuery = (retainedParameters + "i=$trackId").joinToString("&")
    return "$baseUrl?$updatedQuery$fragment"
}

private fun openAlbumInAppleMusic(context: Context, albumUrl: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(albumUrl)))
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
}

@Composable
private fun AlbumMetadataFooter(
    metadata: AlbumFooterMetadata,
    onOpenRecordLabel: (String, String?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HorizontalDivider(
            thickness = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metadata.releaseDate?.let { value ->
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            metadata.trackSummary?.let { value ->
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            metadata.copyright?.let { value ->
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        metadata.recordLabel?.let { value ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "RECORD LABEL",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "Open record label $value",
                    ) { onOpenRecordLabel(value, metadata.recordLabelId) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }
}

private fun formatAlbumDuration(durationMs: Long): String {
    val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L % 60L
    return "$totalMinutes:${seconds.toString().padStart(2, '0')}"
}
