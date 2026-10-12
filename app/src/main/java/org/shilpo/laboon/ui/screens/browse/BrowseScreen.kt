@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.browse

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.shilpo.laboon.R
import org.shilpo.laboon.home.BrowseSectionData
import org.shilpo.laboon.home.BrowseState
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomePlaylist
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyricsporn.LyricspornHeroItem
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.FloatingCombinedClearance
import org.shilpo.laboon.ui.design.TrackCodecBadges
import org.shilpo.laboon.ui.screens.home.HomeDailyMixSongOptionsSheet
import org.shilpo.laboon.ui.screens.player.queue.PlayingEqIcon
import java.util.Locale

private enum class BrowseCategoryFilter(val label: String, val icon: String?) {
    ALL("All", null),
    SONGS("Songs", "music_note"),
    ALBUMS("Albums", "album"),
    PLAYLISTS("Playlists", "queue_music"),
}

@Composable
fun BrowseScreen(
    browseState: BrowseState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onTrackClick: (HomeTrack) -> Unit,
    onPlayTrack: (HomeTrack, List<HomeTrack>) -> Unit,
    onPlayNextTrack: (HomeTrack) -> Unit,
    onAddTrackToQueue: (HomeTrack) -> Unit,
    onAddToPlaylist: (HomeTrack) -> Unit,
    onOpenAlbumForTrack: ((HomeTrack) -> Unit)? = null,
    onDownloadTrack: (HomeTrack) -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    onLoadTrackGenres: (suspend (HomeTrack) -> List<String>)? = null,
) {
    val context = LocalContext.current
    val topClearance = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp
    val bottomClearance = FloatingCombinedClearance +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp

    val pullState = rememberPullToRefreshState()
    var selectedFilter by remember { mutableStateOf(BrowseCategoryFilter.ALL) }
    var trackForOptions by remember { mutableStateOf<HomeTrack?>(null) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topClearance),
            )
        },
    ) {
        if (browseState.isLoading && browseState.grouping == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topClearance, bottom = bottomClearance),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Finding what's new...",
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (browseState.error != null && browseState.grouping == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topClearance, bottom = bottomClearance)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(28.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = materialSymbolPainterResource("error"),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                        Text(
                            text = "Couldn't load browse content",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = browseState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        FilledTonalButton(
                            onClick = onRefresh,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                painter = materialSymbolPainterResource("refresh"),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.home_retry))
                        }
                    }
                }
            }
        } else {
            val grouping = browseState.grouping
            val allSections = browseState.sections

            val filteredSections = remember(allSections, selectedFilter) {
                when (selectedFilter) {
                    BrowseCategoryFilter.ALL -> allSections
                    BrowseCategoryFilter.SONGS -> allSections.filter {
                        it.section.layout == "tracks" || it.tracks.isNotEmpty()
                    }
                    BrowseCategoryFilter.ALBUMS -> allSections.filter {
                        it.section.layout == "albums" || it.albums.isNotEmpty()
                    }
                    BrowseCategoryFilter.PLAYLISTS -> allSections.filter {
                        it.section.layout == "playlists" || it.playlists.isNotEmpty()
                    }
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(top = topClearance, bottom = bottomClearance),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                // Screen Title Header
                item(key = "browse_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = grouping?.title?.ifBlank { stringResource(R.string.browse_title) }
                                ?: stringResource(R.string.browse_title),
                            style = MaterialTheme.typography.headlineLargeEmphasized.copy(
                                fontSize = 34.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Storefront badge pill
                        val storefront = grouping?.storefront?.uppercase().orEmpty()
                        if (storefront.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 1.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        painter = materialSymbolPainterResource("language"),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = storefront,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Horizontal Category Filter Chips
                item(key = "browse_category_chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(BrowseCategoryFilter.entries) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = {
                                    Text(
                                        text = filter.label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                },
                                leadingIcon = filter.icon?.let { iconName ->
                                    {
                                        Icon(
                                            painter = materialSymbolPainterResource(iconName),
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }

                // Top Hero Flowcase Carousel (shown on "All" or "Songs")
                val heroes = grouping?.hero.orEmpty()
                if (heroes.isNotEmpty() && (selectedFilter == BrowseCategoryFilter.ALL || selectedFilter == BrowseCategoryFilter.SONGS)) {
                    item(key = "browse_hero_flowcase") {
                        BrowseHeroCarousel(
                            heroItems = heroes,
                            onHeroClick = { hero ->
                                val targetAlbumId = hero.destination?.takeIf { it.type == "album" }?.id
                                    ?: hero.target?.takeIf { it.type == "album" }?.id
                                if (targetAlbumId != null) {
                                    onAlbumClick(targetAlbumId)
                                } else if (hero.target != null && hero.target.type == "song") {
                                    val track = HomeTrack(
                                        id = "apple_${hero.target.id}",
                                        title = hero.target.name,
                                        artist = hero.target.artistName.orEmpty(),
                                        album = hero.target.albumName,
                                        artworkUrl = hero.target.artworkUrl ?: hero.artworkUrl,
                                        providerTrackId = hero.target.id,
                                        durationMs = hero.target.durationMs,
                                    )
                                    onTrackClick(track)
                                }
                            },
                        )
                    }
                }

                if (filteredSections.isEmpty()) {
                    item(key = "browse_empty_filtered") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp, horizontal = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.browse_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Editorial Sections
                items(
                    items = filteredSections,
                    key = { "section_${it.section.id}" },
                ) { sectionData ->
                    when (sectionData.section.layout) {
                        "tracks" -> {
                            BrowseTracksSection(
                                title = sectionData.section.title,
                                tracks = sectionData.tracks,
                                currentTrackId = currentTrackId,
                                isPlaying = isPlaying,
                                onTrackClick = onTrackClick,
                                onPlayTrack = onPlayTrack,
                                onOpenOptionsSheet = { trackForOptions = it },
                                onDownloadTrack = onDownloadTrack,
                            )
                        }
                        "albums" -> {
                            BrowseAlbumsSection(
                                title = sectionData.section.title,
                                albums = sectionData.albums,
                                onAlbumClick = onAlbumClick,
                            )
                        }
                        "playlists" -> {
                            BrowsePlaylistsSection(
                                title = sectionData.section.title,
                                playlists = sectionData.playlists,
                                onPlaylistClick = { playlist ->
                                    val albumId = playlist.appleCatalogId ?: playlist.id
                                    onAlbumClick(albumId)
                                },
                            )
                        }
                        else -> {
                            if (sectionData.tracks.isNotEmpty()) {
                                BrowseTracksSection(
                                    title = sectionData.section.title,
                                    tracks = sectionData.tracks,
                                    currentTrackId = currentTrackId,
                                    isPlaying = isPlaying,
                                    onTrackClick = onTrackClick,
                                    onPlayTrack = onPlayTrack,
                                    onOpenOptionsSheet = { trackForOptions = it },
                                    onDownloadTrack = onDownloadTrack,
                                )
                            } else if (sectionData.albums.isNotEmpty()) {
                                BrowseAlbumsSection(
                                    title = sectionData.section.title,
                                    albums = sectionData.albums,
                                    onAlbumClick = onAlbumClick,
                                )
                            } else if (sectionData.playlists.isNotEmpty()) {
                                BrowsePlaylistsSection(
                                    title = sectionData.section.title,
                                    playlists = sectionData.playlists,
                                    onPlaylistClick = { playlist ->
                                        val albumId = playlist.appleCatalogId ?: playlist.id
                                        onAlbumClick(albumId)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rich track options bottom sheet (replaces crude DropdownMenu)
    trackForOptions?.let { track ->
        HomeDailyMixSongOptionsSheet(
            track = track,
            onDismiss = { trackForOptions = null },
            onPlay = {
                trackForOptions = null
                onTrackClick(track)
            },
            onPlayNext = {
                trackForOptions = null
                onPlayNextTrack(track)
            },
            onAddToQueue = {
                trackForOptions = null
                onAddTrackToQueue(track)
            },
            onAddToPlaylist = {
                trackForOptions = null
                onAddToPlaylist(track)
            },
            onOpenAlbum = if (track.album != null && onOpenAlbumForTrack != null) {
                {
                    trackForOptions = null
                    onOpenAlbumForTrack(track)
                }
            } else null,
            onShare = {
                trackForOptions = null
                shareTrack(context, track)
            },
            onLoadTrackGenres = onLoadTrackGenres ?: { emptyList() },
            onDownload = {
                trackForOptions = null
                onDownloadTrack(track)
            },
        )
    }
}

@Composable
private fun BrowseHeroCarousel(
    heroItems: List<LyricspornHeroItem>,
    onHeroClick: (LyricspornHeroItem) -> Unit,
) {
    if (heroItems.isEmpty()) return
    val carouselState = rememberCarouselState { heroItems.size }

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 320.dp,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
    ) { itemIndex ->
        val item = heroItems[itemIndex]
        BrowseHeroCard(
            item = item,
            onClick = { onHeroClick(item) },
            modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge),
        )
    }
}

@Composable
private fun BrowseHeroCard(
    item: LyricspornHeroItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(
            model = item.artworkUrl,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Layered gradient scrim for high-contrast legible text
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.5f),
                        0.32f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.4f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                ),
        )

        // Editorial badge pill / LIVE badge at top-start
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isLive = item.badge?.contains("LIVE", ignoreCase = true) == true
            if (isLive) {
                Surface(
                    color = Color(0xFFE50914),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.browse_live_badge),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            val badge = item.badge?.trim().orEmpty()
            if (badge.isNotBlank() && !isLive) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = badge.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // Bottom Content: Headline, description, and quick-action button
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            val title = item.title?.trim().orEmpty()
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val description = item.description?.trim().orEmpty()
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick action button
            val isSong = item.target?.type == "song"
            FilledTonalButton(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.92f),
                    contentColor = Color.Black,
                ),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(
                    painter = materialSymbolPainterResource(if (isSong) "play_arrow" else "explore"),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSong) "Play" else "Explore",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun BrowseTracksSection(
    title: String,
    tracks: List<HomeTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (HomeTrack) -> Unit,
    onPlayTrack: (HomeTrack, List<HomeTrack>) -> Unit,
    onOpenOptionsSheet: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
) {
    if (tracks.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        BrowseSectionHeader(title = title, count = tracks.size)

        Spacer(modifier = Modifier.height(12.dp))

        // Multi-row horizontal chunking (4 songs per column)
        val columns = remember(tracks) { tracks.chunked(4) }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(columns) { columnTracks ->
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.width(330.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        columnTracks.forEachIndexed { index, track ->
                            val isCurrentSong = currentTrackId != null && track.id == currentTrackId

                            BrowseTrackRow(
                                track = track,
                                isCurrentSong = isCurrentSong,
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track, tracks) },
                                onOptionsClick = { onOpenOptionsSheet(track) },
                                onDownload = { onDownloadTrack(track) },
                            )
                            if (index < columnTracks.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 68.dp, end = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseTrackRow(
    track: HomeTrack,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 52dp squircle artwork
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentScale = ContentScale.Crop,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isCurrentSong) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (isCurrentSong && isPlaying) {
                    PlayingEqIcon(
                        modifier = Modifier.size(width = 16.dp, height = 14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        isPlaying = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (track.durationMs != null && track.durationMs > 0) {
                    Text(
                        text = "• ${formatDuration(track.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }

                TrackCodecBadges(
                    track = track,
                    modifier = Modifier.padding(start = 2.dp),
                    height = 9.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Download checkmark or cloud download
        if (track.isCached) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = materialSymbolPainterResource("check"),
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = materialSymbolPainterResource("cloud_download"),
                    contentDescription = "Download track",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // 3-dots more options button
        IconButton(
            onClick = onOptionsClick,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = materialSymbolPainterResource("more_vert"),
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BrowseAlbumsSection(
    title: String,
    albums: List<HomeAlbum>,
    onAlbumClick: (String) -> Unit,
) {
    if (albums.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        BrowseSectionHeader(title = title, count = albums.size)

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(albums) { album ->
                BrowseMediaCard(
                    title = album.title,
                    subtitle = album.artist,
                    artworkUrl = album.artworkUrl,
                    onClick = { onAlbumClick(album.appleCatalogId ?: album.id) },
                    onQuickPlay = { onAlbumClick(album.appleCatalogId ?: album.id) },
                )
            }
        }
    }
}

@Composable
private fun BrowsePlaylistsSection(
    title: String,
    playlists: List<HomePlaylist>,
    onPlaylistClick: (HomePlaylist) -> Unit,
) {
    if (playlists.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        BrowseSectionHeader(title = title, count = playlists.size)

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(playlists) { playlist ->
                BrowseMediaCard(
                    title = playlist.title,
                    subtitle = playlist.curator ?: "Curated Playlist",
                    artworkUrl = playlist.artworkUrl,
                    onClick = { onPlaylistClick(playlist) },
                    onQuickPlay = { onPlaylistClick(playlist) },
                )
            }
        }
    }
}

@Composable
private fun BrowseMediaCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // Quick-play button overlay on artwork
            FilledTonalIconButton(
                onClick = onQuickPlay,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    painter = materialSymbolPainterResource("play_arrow"),
                    contentDescription = "Play $title",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BrowseSectionHeader(
    title: String,
    count: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (count != null && count > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Icon(
            painter = materialSymbolPainterResource("chevron_right"),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%d:%02d", min, sec)
}

private fun shareTrack(context: Context, track: HomeTrack) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}")
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share track"))
}
