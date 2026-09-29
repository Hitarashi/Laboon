@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.auth.SharedPreferencesKeyValueStore
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.home.HomeFeedDefaults
import org.shilpo.laboon.home.HomeFeedRepository
import org.shilpo.laboon.home.HomeFeedState
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.SectionLoadState
import org.shilpo.laboon.home.SectionState
import org.shilpo.laboon.navigation.MainTab
import org.shilpo.laboon.navigation.RouteDirection
import org.shilpo.laboon.navigation.RouteEvent
import org.shilpo.laboon.navigation.RouteState
import org.shilpo.laboon.navigation.tabTransitionDirection
import org.shilpo.laboon.ui.design.FloatingNavBar
import org.shilpo.laboon.ui.design.FloatingNavBarClearance
import org.shilpo.laboon.ui.design.NavigationBarBottomPadding
import org.shilpo.laboon.ui.design.PredictiveBackSpec
import org.shilpo.laboon.ui.design.PredictiveBackSurface
import org.shilpo.laboon.ui.design.SkeletonSegmentedList
import org.shilpo.laboon.ui.design.SkeletonTrackCarousel
import org.shilpo.laboon.ui.design.UserAvatar
import org.shilpo.laboon.ui.design.rememberPredictiveBackState
import org.shilpo.laboon.ui.design.userDisplayName
import org.shilpo.laboon.ui.screens.library.LibraryScreen
import org.shilpo.laboon.ui.screens.search.SearchScreen
import org.shilpo.laboon.ui.screens.settings.SettingsScreen

@Composable
fun HomeScreen(
    state: RouteState,
    onEvent: (RouteEvent) -> Unit,
    session: AuthSession?,
    modifier: Modifier = Modifier,
    onDisconnect: () -> Unit = {},
) {
    val currentTab = state.currentTab
    val showSettings = state.settingsVisible

    val context = LocalContext.current
    val sessionStore = remember { SessionStore(SharedPreferencesKeyValueStore(context)) }
    val repository = remember { HomeFeedRepository(sessionStore) }
    var feedState by remember { mutableStateOf(HomeFeedDefaults.defaultFeed) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(session) {
        val region = repository.getDisplayRegion().orEmpty()
        feedState = HomeFeedDefaults.defaultFeed.copy(regionName = region)
        launch {
            val rot = repository.fetchRotation()
            feedState = feedState.copy(rotation = SectionState(SectionLoadState.LOADED, rot))
        }
        launch {
            val rec = repository.fetchRecommended()
            feedState = feedState.copy(recommended = SectionState(SectionLoadState.LOADED, rec))
        }
        launch {
            val art = repository.fetchTopArtists()
            feedState = feedState.copy(topArtists = SectionState(SectionLoadState.LOADED, art))
        }
        launch {
            val alb = repository.fetchTopAlbums()
            feedState = feedState.copy(topAlbums = SectionState(SectionLoadState.LOADED, alb))
        }
    }

    val loadTopTracks: () -> Unit = {
        if (feedState.topTracks.state == SectionLoadState.IDLE) {
            feedState = feedState.copy(topTracks = SectionState(SectionLoadState.LOADING))
            coroutineScope.launch {
                val tracks = repository.fetchTopTracks()
                feedState =
                    feedState.copy(topTracks = SectionState(SectionLoadState.LOADED, tracks))
            }
        }
    }

    val loadTrending: () -> Unit = {
        if (feedState.regionalTrending.state == SectionLoadState.IDLE) {
            feedState = feedState.copy(
                regionalTrending = SectionState(SectionLoadState.LOADING),
                globalTrending = SectionState(SectionLoadState.LOADING),
            )
            coroutineScope.launch {
                val regionalJob = async { repository.fetchRegionalTrending() }
                val globalJob = async { repository.fetchGlobalTrending() }
                val regional = regionalJob.await()
                val global = globalJob.await()
                feedState = feedState.copy(
                    regionalTrending = SectionState(SectionLoadState.LOADED, regional),
                    globalTrending = SectionState(SectionLoadState.LOADED, global),
                )
            }
        }
    }

    val loadWeeklyPicks: () -> Unit = {
        if (feedState.weeklyPicks.state == SectionLoadState.IDLE) {
            feedState = feedState.copy(weeklyPicks = SectionState(SectionLoadState.LOADING))
            coroutineScope.launch {
                val weekly = repository.fetchWeeklyPicks()
                feedState =
                    feedState.copy(weeklyPicks = SectionState(SectionLoadState.LOADED, weekly))
            }
        }
    }

    val homeBackState = rememberPredictiveBackState(
        enabled = state.canGoBackWithinHome,
        onBack = { onEvent(RouteEvent.BackPressed) },
    )

    val tabIsBackTarget = !showSettings && currentTab != MainTab.Home
    val settingsProgress = homeBackState.progressFor(showSettings)

    val motionScheme = MaterialTheme.motionScheme

    val scrimAlpha by animateFloatAsState(
        targetValue = if (showSettings) (1f - settingsProgress) * 0.4f else 0f,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "settingsScrim",
    )

    Box(modifier = modifier.fillMaxSize()) {
        PredictiveBackSurface(
            state = homeBackState,
            spec = PredictiveBackSpec.HomeTab,
            active = tabIsBackTarget,
        ) { tabSurface ->
            AnimatedContent(
                targetState = currentTab,
                modifier = tabSurface.fillMaxSize(),
                transitionSpec = {
                    val forward =
                        tabTransitionDirection(initialState, targetState) == RouteDirection.Forward
                    val direction = if (forward) 1 else -1
                    (slideInHorizontally(
                        animationSpec = motionScheme.defaultSpatialSpec(),
                        initialOffsetX = { width -> direction * (width / 4) },
                    ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                            (slideOutHorizontally(
                                animationSpec = motionScheme.defaultSpatialSpec(),
                                targetOffsetX = { width -> -direction * (width / 4) },
                            ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec()))
                },
                label = "mainNavTabTransition",
            ) { tab ->
                when (tab) {
                    MainTab.Home -> HomeContent(
                        session = session,
                        onOpenSettings = { onEvent(RouteEvent.SettingsOpened) },
                        onNavigate = { onEvent(RouteEvent.TabSelected(it)) },
                        feedState = feedState,
                        onLoadTopTracks = loadTopTracks,
                        onLoadTrending = loadTrending,
                        onLoadWeeklyPicks = loadWeeklyPicks,
                        modifier = Modifier.fillMaxSize(),
                    )

                    MainTab.Search -> SearchScreen(
                        modifier = Modifier.fillMaxSize(),
                    )

                    MainTab.Library -> LibraryScreen(
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        FloatingNavBar(
            selectedTab = currentTab,
            onTabSelected = { onEvent(RouteEvent.TabSelected(it)) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = NavigationBarBottomPadding),
        )

        if (showSettings || scrimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialOffsetX = { it },
            ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = slideOutHorizontally(
                animationSpec = motionScheme.defaultSpatialSpec(),
                targetOffsetX = { it },
            ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec()),
        ) {
            PredictiveBackSurface(
                state = homeBackState,
                spec = PredictiveBackSpec.HomeSettings,
                active = showSettings,
            ) { settingsSurface ->
                SettingsScreen(
                    session = session,
                    onBack = { onEvent(RouteEvent.SettingsClosed) },
                    onDisconnect = {
                        onEvent(RouteEvent.SettingsClosed)
                        onDisconnect()
                    },
                    modifier = settingsSurface.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    session: AuthSession?,
    onOpenSettings: () -> Unit,
    onNavigate: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    feedState: HomeFeedState = HomeFeedDefaults.defaultFeed,
    onLoadTopTracks: () -> Unit = {},
    onLoadTrending: () -> Unit = {},
    onLoadWeeklyPicks: () -> Unit = {},
    onTrackClick: (HomeTrack) -> Unit = {},
    onArtistClick: (HomeArtist) -> Unit = {},
    onAlbumClick: (HomeAlbum) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme
    var isGlobalTrending by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.app_icon_small),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.home_title),
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(
                                    R.string.home_welcome,
                                    userDisplayName(session?.user)
                                        ?: stringResource(R.string.home_user_fallback),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    UserAvatar(
                        session = session,
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        val navBarBottomInset =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomClearance = FloatingNavBarClearance + navBarBottomInset + 16.dp

        if (feedState.isAllEmpty && !feedState.isInitialLoading && feedState.regionalTrending.state == SectionLoadState.LOADED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = bottomClearance,
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.app_icon_small),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.home_title),
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Start listening to songs or connect your Last.fm / ListenBrainz in Settings to see your personal rotation and charts here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenSettings,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = bottomClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (feedState.rotation.state == SectionLoadState.LOADING) {
                    item(key = "rotation_skeleton") {
                        SkeletonTrackCarousel(
                            titleWidth = 140.dp,
                            subtitleWidth = 200.dp,
                            isArtist = false,
                        )
                    }
                } else if (feedState.rotation.state == SectionLoadState.LOADED && feedState.rotation.data.isNotEmpty()) {
                    item(key = "rotation_live") {
                        HomeTrackCarousel(
                            title = stringResource(R.string.home_your_rotation),
                            subtitle = stringResource(R.string.home_your_rotation_subtitle),
                            tracks = feedState.rotation.data,
                            onTrackClick = onTrackClick,
                        )
                    }
                }

                if (feedState.recommended.state == SectionLoadState.LOADING) {
                    item(key = "recommended_skeleton") {
                        SkeletonTrackCarousel(
                            titleWidth = 150.dp,
                            subtitleWidth = 220.dp,
                            isArtist = false,
                        )
                    }
                } else if (feedState.recommended.state == SectionLoadState.LOADED && feedState.recommended.data.isNotEmpty()) {
                    item(key = "recommended_live") {
                        HomeTrackCarousel(
                            title = stringResource(R.string.home_recommended),
                            subtitle = stringResource(R.string.home_recommended_subtitle),
                            tracks = feedState.recommended.data,
                            onTrackClick = onTrackClick,
                        )
                    }
                }

                if (feedState.topArtists.state == SectionLoadState.LOADING) {
                    item(key = "top_artists_skeleton") {
                        SkeletonTrackCarousel(
                            titleWidth = 120.dp,
                            subtitleWidth = 180.dp,
                            isArtist = true,
                        )
                    }
                } else if (feedState.topArtists.state == SectionLoadState.LOADED && feedState.topArtists.data.isNotEmpty()) {
                    item(key = "top_artists_live") {
                        HomeArtistCarousel(
                            title = stringResource(R.string.home_top_artists),
                            subtitle = stringResource(R.string.home_top_artists_subtitle),
                            artists = feedState.topArtists.data,
                            onArtistClick = onArtistClick,
                        )
                    }
                }

                if (feedState.topAlbums.state == SectionLoadState.LOADING) {
                    item(key = "top_albums_skeleton") {
                        SkeletonTrackCarousel(
                            titleWidth = 130.dp,
                            subtitleWidth = 190.dp,
                            isArtist = false,
                        )
                    }
                } else if (feedState.topAlbums.state == SectionLoadState.LOADED && feedState.topAlbums.data.isNotEmpty()) {
                    item(key = "top_albums_live") {
                        HomeAlbumCarousel(
                            title = stringResource(R.string.home_top_albums),
                            subtitle = stringResource(R.string.home_top_albums_subtitle),
                            albums = feedState.topAlbums.data,
                            onAlbumClick = onAlbumClick,
                        )
                    }
                }

                item(key = "top_tracks") {
                    LaunchedEffect(Unit) {
                        onLoadTopTracks()
                    }
                    when (feedState.topTracks.state) {
                        SectionLoadState.LOADING -> {
                            SkeletonTrackCarousel(
                                titleWidth = 130.dp,
                                subtitleWidth = 190.dp,
                                isArtist = false,
                            )
                        }

                        SectionLoadState.LOADED -> {
                            if (feedState.topTracks.data.isNotEmpty()) {
                                HomeTrackCarousel(
                                    title = stringResource(R.string.home_top_tracks),
                                    subtitle = stringResource(R.string.home_top_tracks_subtitle),
                                    tracks = feedState.topTracks.data,
                                    onTrackClick = onTrackClick,
                                )
                            }
                        }

                        else -> Unit
                    }
                }

                item(key = "trending") {
                    LaunchedEffect(Unit) {
                        onLoadTrending()
                    }
                    if (feedState.regionalTrending.state == SectionLoadState.LOADING || feedState.globalTrending.state == SectionLoadState.LOADING) {
                        SkeletonSegmentedList()
                    } else if (feedState.regionalTrending.state == SectionLoadState.LOADED || feedState.globalTrending.state == SectionLoadState.LOADED) {
                        val hasRegional = feedState.regionalTrending.data.isNotEmpty()
                        val effectiveIsGlobal = isGlobalTrending || !hasRegional
                        val currentTrending = if (effectiveIsGlobal) {
                            feedState.globalTrending.data
                        } else {
                            feedState.regionalTrending.data
                        }
                        if (currentTrending.isNotEmpty() || feedState.globalTrending.data.isNotEmpty() || feedState.regionalTrending.data.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.home_trending),
                                    style = MaterialTheme.typography.titleMediumEmphasized.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                    ),
                                )

                                if (hasRegional) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            ButtonGroupDefaults.ConnectedSpaceBetween
                                        ),
                                    ) {
                                        FilledTonalToggleButton(
                                            checked = !effectiveIsGlobal,
                                            onCheckedChange = { isGlobalTrending = false },
                                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(
                                                text = feedState.regionName.ifBlank {
                                                    stringResource(
                                                        R.string.home_trending_regional
                                                    )
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        FilledTonalToggleButton(
                                            checked = effectiveIsGlobal,
                                            onCheckedChange = { isGlobalTrending = true },
                                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.home_trending_global),
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }

                                AnimatedContent(
                                    targetState = effectiveIsGlobal,
                                    transitionSpec = {
                                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                                                fadeOut(animationSpec = motionScheme.defaultEffectsSpec())
                                    },
                                    label = "trendingSongsTransition",
                                ) { showGlobal ->
                                    val displayList = if (showGlobal) {
                                        feedState.globalTrending.data
                                    } else {
                                        feedState.regionalTrending.data
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                                    ) {
                                        displayList.forEachIndexed { index, track ->
                                            SegmentedListItem(
                                                shapes = ListItemDefaults.segmentedShapes(
                                                    index = index,
                                                    count = displayList.size,
                                                ),
                                                colors = ListItemDefaults.segmentedColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                                leadingContent = {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                                    ) {
                                                        if (!track.artworkUrl.isNullOrBlank()) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(
                                                                    LocalPlatformContext.current
                                                                )
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
                                                                tint = MaterialTheme.colorScheme.primary.copy(
                                                                    alpha = 0.6f
                                                                ),
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .align(Alignment.Center),
                                                            )
                                                        }
                                                    }
                                                },
                                                content = {
                                                    Text(
                                                        text = track.title,
                                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                },
                                                supportingContent = {
                                                    Text(
                                                        text = track.artist,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                },
                                                trailingContent = {
                                                    FilledTonalIconButton(
                                                        onClick = { onTrackClick(track) },
                                                        modifier = Modifier.size(36.dp),
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_play),
                                                            contentDescription = "Play",
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "weekly_picks") {
                    LaunchedEffect(Unit) {
                        onLoadWeeklyPicks()
                    }
                    when (feedState.weeklyPicks.state) {
                        SectionLoadState.LOADING -> {
                            SkeletonTrackCarousel(
                                titleWidth = 140.dp,
                                subtitleWidth = 200.dp,
                                isArtist = false,
                            )
                        }

                        SectionLoadState.LOADED -> {
                            if (feedState.weeklyPicks.data.isNotEmpty()) {
                                HomeTrackCarousel(
                                    title = stringResource(R.string.home_weekly_picks),
                                    subtitle = stringResource(R.string.home_weekly_picks_subtitle),
                                    tracks = feedState.weeklyPicks.data,
                                    onTrackClick = onTrackClick,
                                )
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}


