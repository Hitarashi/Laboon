@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.home.HomeFeedDefaults
import org.shilpo.laboon.home.HomeFeedState
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.SectionLoadState
import org.shilpo.laboon.navigation.MainTab
import org.shilpo.laboon.ui.design.FloatingCombinedClearance

@Composable
internal fun HomeContent(
    onOpenSettings: () -> Unit,
    onNavigate: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    feedState: HomeFeedState = HomeFeedDefaults.defaultFeed,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onLoadTopTracks: () -> Unit = {},
    onLoadTrending: () -> Unit = {},
    onLoadWeeklyPicks: () -> Unit = {},
    onPlayMix: (List<HomeTrack>) -> Unit = {},
    onTrackClick: (HomeTrack) -> Unit = {},
    onPlayDailyMixTrack: (HomeTrack, List<HomeTrack>) -> Unit = { track, _ -> onTrackClick(track) },
    onPlayNextTrack: (HomeTrack) -> Unit = {},
    onAddTrackToQueue: (HomeTrack) -> Unit = {},
    onDownloadTrack: (HomeTrack) -> Unit = {},
    onLoadTrackGenres: suspend (HomeTrack) -> List<String> = { emptyList() },
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    onOpenDailyMix: () -> Unit = {},
    onArtistClick: (HomeArtist) -> Unit = {},
    onAlbumClick: (HomeAlbum) -> Unit = {},
) {
    val mix = remember(feedState) { homeMixTracks(feedState) }
    val matchedAlbums = remember(feedState.topAlbums.items) {
        feedState.topAlbums.items.filter { !it.appleCatalogId.isNullOrBlank() }
    }
    var globalTrending by rememberSaveable { mutableStateOf(false) }
    val topClearance = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val bottomClearance = FloatingCombinedClearance +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val pullState = rememberPullToRefreshState()

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
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.widthIn(max = 1040.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(top = topClearance, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = "your_mix", contentType = "mix") {
                if (mix.isNotEmpty() || feedState.isInitialLoading) {
                    HomeMixSection(
                        tracks = mix,
                        isLoading = feedState.isInitialLoading,
                        onPlayMix = onPlayMix,
                        onTrackClick = onTrackClick,
                        onDownloadTrack = onDownloadTrack,
                    )
                } else {
                    HomeEmptyMix(onSearch = { onNavigate(MainTab.Search) }, onOpenSettings = onOpenSettings)
                }
            }
            item(key = "daily_mix", contentType = "daily_mix") {
                HomeFeedSection(
                    title = stringResource(R.string.home_daily_mix),
                    status = feedState.recommended.status,
                    hasItems = feedState.recommended.items.isNotEmpty(),
                    onRetry = onRefresh,
                ) {
                    HomeDailyMixPanel(
                        tracks = feedState.recommended.items,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        onPlayTrack = onPlayDailyMixTrack,
                        onPlayNext = onPlayNextTrack,
                        onAddToQueue = onAddTrackToQueue,
                        onDownloadTrack = onDownloadTrack,
                        onLoadTrackGenres = onLoadTrackGenres,
                        onOpenDailyMix = onOpenDailyMix,
                    )
                }
            }
            item(key = "rotation", contentType = "pills") {
                HomeFeedSection(
                    title = stringResource(R.string.home_your_rotation),
                    status = feedState.rotation.status,
                    hasItems = feedState.rotation.items.isNotEmpty(),
                    onRetry = onRefresh,
                ) {
                    HomeTrackPills(
                        title = stringResource(R.string.home_your_rotation),
                        subtitle = stringResource(R.string.home_your_rotation_subtitle),
                        tracks = feedState.rotation.items,
                        onTrackClick = onTrackClick,
                        onDownloadTrack = onDownloadTrack,
                    )
                }
            }
            item(key = "top_artists", contentType = "artists") {
                HomeFeedSection(
                    title = stringResource(R.string.home_top_artists),
                    status = feedState.topArtists.status,
                    hasItems = feedState.topArtists.items.isNotEmpty(),
                    onRetry = onRefresh,
                ) {
                    HomeArtistCarousel(
                        title = stringResource(R.string.home_top_artists),
                        artists = feedState.topArtists.items,
                        onArtistClick = onArtistClick,
                    )
                }
            }
            item(key = "top_albums", contentType = "albums") {
                HomeFeedSection(
                    title = stringResource(R.string.home_top_albums),
                    status = feedState.topAlbums.status,
                    hasItems = matchedAlbums.isNotEmpty(),
                    onRetry = onRefresh,
                ) {
                    HomeAlbumCarousel(
                        title = stringResource(R.string.home_top_albums),
                        albums = matchedAlbums,
                        onAlbumClick = onAlbumClick,
                    )
                }
            }
            item(key = "top_tracks", contentType = "tracks") {
                LaunchedEffect(Unit) { onLoadTopTracks() }
                HomeFeedSection(
                    title = stringResource(R.string.home_top_tracks),
                    status = feedState.topTracks.status,
                    hasItems = feedState.topTracks.items.isNotEmpty(),
                    onRetry = onLoadTopTracks,
                ) {
                    HomeTrackCarousel(
                        title = stringResource(R.string.home_top_tracks),
                        tracks = feedState.topTracks.items,
                        onTrackClick = onTrackClick,
                        onDownloadTrack = onDownloadTrack,
                    )
                }
            }
            item(key = "trending", contentType = "trending") {
                LaunchedEffect(Unit) { onLoadTrending() }
                val hasRegional = feedState.regionalTrending.items.isNotEmpty()
                val showGlobal = globalTrending || !hasRegional
                HomeFeedSection(
                    title = stringResource(R.string.home_trending),
                    status = when {
                        feedState.regionalTrending.status == SectionLoadState.LOADING ||
                            feedState.globalTrending.status == SectionLoadState.LOADING -> SectionLoadState.LOADING
                        feedState.regionalTrending.status == SectionLoadState.ERROR ||
                            feedState.globalTrending.status == SectionLoadState.ERROR -> SectionLoadState.ERROR
                        else -> SectionLoadState.LOADED
                    },
                    hasItems = hasRegional || feedState.globalTrending.items.isNotEmpty(),
                    onRetry = onLoadTrending,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HomeSectionHeading(
                            stringResource(R.string.home_trending),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        if (hasRegional && feedState.globalTrending.items.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                                FilledTonalToggleButton(
                                    checked = !showGlobal,
                                    onCheckedChange = { globalTrending = false },
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = feedState.regionName.ifBlank { stringResource(R.string.home_trending_regional) },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                FilledTonalToggleButton(
                                    checked = showGlobal,
                                    onCheckedChange = { globalTrending = true },
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.home_trending_global))
                                }
                            }
                        }
                        AnimatedContent(targetState = showGlobal, label = "homeTrending") { global ->
                            val tracks = if (global) feedState.globalTrending.items else feedState.regionalTrending.items
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    tracks.forEachIndexed { index, track ->
                                        HomeSongRow(track, onTrackClick, onDownloadTrack)
                                        if (index < tracks.lastIndex) {
                                            androidx.compose.material3.HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "weekly_picks", contentType = "tracks") {
                LaunchedEffect(Unit) { onLoadWeeklyPicks() }
                HomeFeedSection(
                    title = stringResource(R.string.home_weekly_picks),
                    status = feedState.weeklyPicks.status,
                    hasItems = feedState.weeklyPicks.items.isNotEmpty(),
                    onRetry = onLoadWeeklyPicks,
                ) {
                    HomeTrackCarousel(
                        title = stringResource(R.string.home_weekly_picks),
                        tracks = feedState.weeklyPicks.items,
                        onTrackClick = onTrackClick,
                        onDownloadTrack = onDownloadTrack,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFeedSection(
    title: String,
    status: SectionLoadState,
    hasItems: Boolean,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (hasItems) {
        content()
    } else if (status == SectionLoadState.LOADING || status == SectionLoadState.ERROR) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeSectionHeading(title)
                if (status == SectionLoadState.LOADING) {
                    LoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text(
                        stringResource(R.string.home_section_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
                }
            }
        }
    }
}

@Composable
private fun HomeEmptyMix(onSearch: () -> Unit, onOpenSettings: () -> Unit) {
    val titleStyle = rememberYourMixTitleStyle()
    val subtitleStyle = rememberYourMixSubtitleStyle()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.home_your_mix),
            style = titleStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.home_mix_empty),
            style = subtitleStyle,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 8.dp),
        )
        FilledTonalButton(onClick = onSearch) { Text(stringResource(R.string.home_find_music)) }
        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.home_connect_listening)) }
    }
}
