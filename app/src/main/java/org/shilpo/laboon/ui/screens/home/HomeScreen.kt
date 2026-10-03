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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import org.shilpo.laboon.home.HomeFeedCache
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
import org.shilpo.laboon.playback.PlaybackManagerHolder
import org.shilpo.laboon.playback.PlaybackPersistence
import org.shilpo.laboon.rip.RipWebSocketClient
import org.shilpo.laboon.ui.design.FloatingCombinedClearance
import org.shilpo.laboon.ui.design.FloatingNavBar
import org.shilpo.laboon.ui.design.LiquidGlassBackdropState
import org.shilpo.laboon.ui.design.LiquidGlassSurface
import org.shilpo.laboon.ui.design.NavigationBarBottomPadding
import org.shilpo.laboon.ui.design.PredictiveBackSpec
import org.shilpo.laboon.ui.design.PredictiveBackSurface
import org.shilpo.laboon.ui.design.SkeletonSegmentedList
import org.shilpo.laboon.ui.design.SkeletonTrackCarousel
import org.shilpo.laboon.ui.design.UserAvatar
import org.shilpo.laboon.ui.design.liquidGlassBackdropProducer
import org.shilpo.laboon.ui.design.rememberLiquidGlassBackdropState
import org.shilpo.laboon.ui.design.rememberPredictiveBackState
import org.shilpo.laboon.ui.design.userDisplayName
import org.shilpo.laboon.ui.screens.library.LibraryScreen
import org.shilpo.laboon.ui.screens.player.MorphingPlayerSheet
import org.shilpo.laboon.ui.screens.rip.RipVisualizerScreen
import org.shilpo.laboon.ui.screens.search.SearchScreen
import org.shilpo.laboon.ui.screens.settings.SettingsScreen

@Composable
fun HomeScreen(
    state: RouteState,
    onEvent: (RouteEvent) -> Unit,
    session: AuthSession?,
    repository: HomeFeedRepository,
    modifier: Modifier = Modifier,
    onActiveTrackChange: ((HomeTrack?, Boolean) -> Unit)? = null,
    onDisconnect: () -> Unit = {},
) {
    val currentTab = state.currentTab
    val showSettings = state.settingsVisible

    val context = LocalContext.current
    val homeFeedCache = remember(context) { HomeFeedCache(SharedPreferencesKeyValueStore(context)) }
    val playbackPersistence =
        remember(context) { PlaybackPersistence(SharedPreferencesKeyValueStore(context)) }

    val initialCachedFeed = remember { homeFeedCache.load() }
    var feedState by remember { mutableStateOf(initialCachedFeed ?: HomeFeedDefaults.defaultFeed) }
    var playerExpansionProgress by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val sessionStore = remember(context) { SessionStore(SharedPreferencesKeyValueStore(context)) }
    val ripWsClient = remember(sessionStore) { RipWebSocketClient(sessionStore) }
    var showRipVisualizer by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(showRipVisualizer) {
        if (showRipVisualizer) {
            ripWsClient.start()
        }
        onDispose {
            ripWsClient.stop()
        }
    }

    val playbackManager = remember(context, sessionStore) {
        PlaybackManagerHolder.getInstance(context.applicationContext, sessionStore)
    }
    val playbackState by playbackManager.state.collectAsState()
    val queueState by playbackManager.queueManager.state.collectAsState()
    val spectrumState by playbackManager.spectrumState.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        playbackManager.syncWithCurrentPlayer()
    }

    var isPlayerDismissed by rememberSaveable { mutableStateOf(playbackPersistence.isPlayerDismissed()) }
    var fallbackTrack by remember { mutableStateOf<HomeTrack?>(playbackPersistence.getLastTrack()) }

    LaunchedEffect(playbackState.currentTrack) {
        val current = playbackState.currentTrack
        if (current != null) {
            isPlayerDismissed = false
            fallbackTrack = current
            playbackPersistence.setPlayerDismissed(false)
            playbackPersistence.saveLastTrack(current)
        }
    }

    LaunchedEffect(playbackState.currentPositionMs) {
        if (playbackState.currentPositionMs > 0) {
            playbackPersistence.saveLastPosition(playbackState.currentPositionMs)
        }
    }

    LaunchedEffect(playbackState.durationMs) {
        if (playbackState.durationMs > 0) {
            playbackPersistence.saveLastDuration(playbackState.durationMs)
        }
    }

    val activeTrack = if (isPlayerDismissed) null else (playbackState.currentTrack ?: fallbackTrack)
    LaunchedEffect(activeTrack, isPlayerDismissed) {
        onActiveTrackChange?.invoke(activeTrack, isPlayerDismissed)
    }
    val activeIsPlaying = playbackState.isPlaying
    val savedPosition = remember(playbackPersistence) { playbackPersistence.getLastPosition() }
    val savedDuration = remember(playbackPersistence) { playbackPersistence.getLastDuration() }
    val currentPositionMs =
        if (playbackState.currentTrack != null) playbackState.currentPositionMs else savedPosition
    val currentDurationMs =
        if (playbackState.currentTrack != null) playbackState.durationMs else savedDuration
    val activeProgress = if (playbackState.currentTrack != null) {
        playbackState.progress
    } else if (savedDuration > 0) {
        (savedPosition.toFloat() / savedDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(session) {
        val region = repository.getDisplayRegion().orEmpty()
        val cached = homeFeedCache.load()
        feedState = if (cached != null) {
            cached.copy(regionName = region)
        } else {
            HomeFeedDefaults.defaultFeed.copy(regionName = region)
        }
        launch {
            val rot = repository.fetchRotation()
            feedState = feedState.copy(rotation = SectionState(SectionLoadState.LOADED, rot))
            homeFeedCache.save(feedState)
        }
        launch {
            val rec = repository.fetchRecommended()
            feedState = feedState.copy(recommended = SectionState(SectionLoadState.LOADED, rec))
            homeFeedCache.save(feedState)
        }
        launch {
            val art = repository.fetchTopArtists()
            feedState = feedState.copy(topArtists = SectionState(SectionLoadState.LOADED, art))
            homeFeedCache.save(feedState)
        }
        launch {
            val alb = repository.fetchTopAlbums()
            feedState = feedState.copy(topAlbums = SectionState(SectionLoadState.LOADED, alb))
            homeFeedCache.save(feedState)
        }
    }

    val onRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            coroutineScope.launch {
                try {
                    repository.clearCache()
                    val rotJob = async { repository.fetchRotation() }
                    val recJob = async { repository.fetchRecommended() }
                    val artJob = async { repository.fetchTopArtists() }
                    val albJob = async { repository.fetchTopAlbums() }
                    val rot = rotJob.await()
                    val rec = recJob.await()
                    val art = artJob.await()
                    val alb = albJob.await()
                    feedState = feedState.copy(
                        rotation = SectionState(SectionLoadState.LOADED, rot),
                        recommended = SectionState(SectionLoadState.LOADED, rec),
                        topArtists = SectionState(SectionLoadState.LOADED, art),
                        topAlbums = SectionState(SectionLoadState.LOADED, alb),
                    )
                    if (feedState.topTracks.status == SectionLoadState.LOADED) {
                        val tracks = repository.fetchTopTracks()
                        feedState = feedState.copy(
                            topTracks = SectionState(
                                SectionLoadState.LOADED,
                                tracks
                            )
                        )
                    }
                    if (feedState.regionalTrending.status == SectionLoadState.LOADED || feedState.globalTrending.status == SectionLoadState.LOADED) {
                        val regJob = async { repository.fetchRegionalTrending() }
                        val globJob = async { repository.fetchGlobalTrending() }
                        val reg = regJob.await()
                        val glob = globJob.await()
                        feedState = feedState.copy(
                            regionalTrending = SectionState(SectionLoadState.LOADED, reg),
                            globalTrending = SectionState(SectionLoadState.LOADED, glob),
                        )
                    }
                    if (feedState.weeklyPicks.status == SectionLoadState.LOADED) {
                        val weekly = repository.fetchWeeklyPicks()
                        feedState = feedState.copy(
                            weeklyPicks = SectionState(
                                SectionLoadState.LOADED,
                                weekly
                            )
                        )
                    }
                    homeFeedCache.save(feedState)
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    val loadTopTracks: () -> Unit = {
        if (feedState.topTracks.status == SectionLoadState.IDLE) {
            feedState = feedState.copy(topTracks = SectionState(SectionLoadState.LOADING))
            coroutineScope.launch {
                val tracks = repository.fetchTopTracks()
                feedState =
                    feedState.copy(topTracks = SectionState(SectionLoadState.LOADED, tracks))
                homeFeedCache.save(feedState)
            }
        }
    }

    val loadTrending: () -> Unit = {
        if (feedState.regionalTrending.status == SectionLoadState.IDLE) {
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
                homeFeedCache.save(feedState)
            }
        }
    }

    val loadWeeklyPicks: () -> Unit = {
        if (feedState.weeklyPicks.status == SectionLoadState.IDLE) {
            feedState = feedState.copy(weeklyPicks = SectionState(SectionLoadState.LOADING))
            coroutineScope.launch {
                val weekly = repository.fetchWeeklyPicks()
                feedState =
                    feedState.copy(weeklyPicks = SectionState(SectionLoadState.LOADED, weekly))
                homeFeedCache.save(feedState)
            }
        }
    }

    val homeBackState = rememberPredictiveBackState(
        enabled = state.canGoBackWithinHome,
        onBack = { onEvent(RouteEvent.BackPressed) },
    )

    val ripVisualizerBackState = rememberPredictiveBackState(
        enabled = showRipVisualizer,
        onBack = { showRipVisualizer = false },
    )

    val tabIsBackTarget = !showSettings && currentTab != MainTab.Home
    val settingsProgress = homeBackState.progressFor(showSettings)
    val visualizerProgress = ripVisualizerBackState.progressFor(showRipVisualizer)

    val motionScheme = MaterialTheme.motionScheme

    val scrimAlpha by animateFloatAsState(
        targetValue = when {
            showRipVisualizer -> (1f - visualizerProgress) * 0.4f
            showSettings -> (1f - settingsProgress) * 0.4f
            else -> 0f
        },
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "settingsScrim",
    )

    val liquidGlassBackdropState = rememberLiquidGlassBackdropState()
    val liquidGlassBackdropLayer = rememberGraphicsLayer()
    val homeScrollState = rememberLazyListState()
    val isHomeScrolled by remember {
        derivedStateOf {
            homeScrollState.firstVisibleItemIndex > 0 || homeScrollState.firstVisibleItemScrollOffset > 16
        }
    }
    val topBarCollapseProgress by animateFloatAsState(
        targetValue = if (isHomeScrolled) 1f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "topBarCollapse",
    )

    Box(modifier = modifier.fillMaxSize()) {
        PredictiveBackSurface(
            state = homeBackState,
            spec = PredictiveBackSpec.HomeTab,
            active = tabIsBackTarget,
        ) { tabSurface ->
            AnimatedContent(
                targetState = currentTab,
                modifier = tabSurface
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .liquidGlassBackdropProducer(
                        liquidGlassBackdropState,
                        liquidGlassBackdropLayer
                    ),
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
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        onLoadTopTracks = loadTopTracks,
                        onLoadTrending = loadTrending,
                        onLoadWeeklyPicks = loadWeeklyPicks,
                        onTrackClick = { track ->
                            playbackManager.play(track)
                        },
                        lazyListState = homeScrollState,
                        modifier = Modifier.fillMaxSize(),
                    )

                    MainTab.Search -> SearchScreen(
                        onTrackClick = { track ->
                            playbackManager.play(track)
                        },
                        onPlayWithContext = { track, results ->
                            playbackManager.play(track, results)
                        },
                        onPlayNext = { track ->
                            playbackManager.playNext(track)
                        },
                        onAddToQueue = { track ->
                            playbackManager.addToQueue(track)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    MainTab.Library -> LibraryScreen(
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val density = LocalDensity.current

        AnimatedVisibility(
            visible = currentTab == MainTab.Home,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.defaultEffectsSpec()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val topBarExitProgress =
                        ((playerExpansionProgress - 0.75f) / 0.25f).coerceIn(0f, 1f)
                    alpha = (1f - topBarExitProgress).coerceIn(0f, 1f)
                    translationY = with(density) { (-topBarExitProgress * 120.dp.toPx()) }
                },
        ) {
            HomeTopBar(
                session = session,
                onOpenSettings = { onEvent(RouteEvent.SettingsOpened) },
                onOpenRipVisualizer = { showRipVisualizer = true },
                backdropState = liquidGlassBackdropState,
                collapseProgress = topBarCollapseProgress,
            )
        }

        FloatingNavBar(
            selectedTab = currentTab,
            onTabSelected = { onEvent(RouteEvent.TabSelected(it)) },
            hasMiniPlayerAbove = activeTrack != null,
            backdropState = liquidGlassBackdropState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = NavigationBarBottomPadding)
                .graphicsLayer {
                    translationY = with(density) { (playerExpansionProgress * 120.dp.toPx()) }
                },
        )

        AnimatedVisibility(
            visible = activeTrack != null,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.defaultEffectsSpec()),
        ) {
            activeTrack?.let { track ->
                MorphingPlayerSheet(
                    track = track,
                    isPlaying = activeIsPlaying,
                    isBuffering = playbackState.isBuffering,
                    playbackProgress = activeProgress,
                    currentPositionMs = currentPositionMs,
                    durationMs = currentDurationMs,
                    audioQuality = playbackState.audioQuality,
                    switchingQualityFormat = playbackState.switchingQualityFormat,
                    onQualityVariantSelected = { variant ->
                        playbackManager.switchQualityVariant(track, variant)
                    },
                    lyricsLines = playbackState.lyricsLines,
                    lyricsLoading = playbackState.lyricsLoading,
                    motionArtwork = playbackState.motionArtwork.takeIf {
                        playbackState.motionArtworkTrackId == track.id
                    },
                    onRequestMotionArtwork = { playbackManager.requestMotionArtwork(track) },
                    isShuffle = queueState.isShuffle,
                    repeatMode = queueState.repeatMode,
                    spectrum = spectrumState,
                    onPlayPauseClick = {
                        if (playbackState.currentTrack != null) {
                            playbackManager.togglePlayPause()
                        } else {
                            val startPos = if (savedPosition > 0L) savedPosition else null
                            playbackManager.play(track, startPositionMs = startPos)
                        }
                    },
                    onPreviousClick = {
                        playbackManager.skipToPrevious()
                    },
                    onNextClick = {
                        playbackManager.skipToNext()
                    },
                    onSeek = { targetProgress ->
                        playbackManager.seekTo(targetProgress)
                    },
                    onToggleShuffle = {
                        playbackManager.queueManager.toggleShuffle()
                    },
                    onCycleRepeatMode = {
                        playbackManager.queueManager.cycleRepeatMode()
                    },
                    onDismiss = {
                        isPlayerDismissed = true
                        fallbackTrack = null
                        playbackPersistence.setPlayerDismissed(true)
                        playbackManager.dismiss()
                        onActiveTrackChange?.invoke(null, true)
                    },
                    backdropState = liquidGlassBackdropState,
                    onExpansionProgressChange = { progress ->
                        playerExpansionProgress = progress
                    },
                    queueState = queueState,
                    onRemoveUpNext = { index ->
                        playbackManager.queueManager.removeUpNext(index)
                    },
                    onMoveUpNext = { from, to ->
                        playbackManager.queueManager.moveUpNext(from, to)
                    },
                    onTrackClick = { t ->
                        playbackManager.play(t, contextTracks = queueState.items)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (showSettings || showRipVisualizer || scrimAlpha > 0.01f) {
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

        AnimatedVisibility(
            visible = showRipVisualizer,
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
                state = ripVisualizerBackState,
                spec = PredictiveBackSpec.HomeSettings,
                active = showRipVisualizer,
            ) { visualizerSurface ->
                RipVisualizerScreen(
                    client = ripWsClient,
                    session = session,
                    onBack = { showRipVisualizer = false },
                    modifier = visualizerSurface.fillMaxSize(),
                )
            }
        }

    }
}

@Composable
private fun HomeTopBar(
    session: AuthSession?,
    onOpenSettings: () -> Unit,
    onOpenRipVisualizer: () -> Unit,
    backdropState: LiquidGlassBackdropState?,
    modifier: Modifier = Modifier,
    collapseProgress: Float = 0f,
) {
    val topPadding = (16 - 8 * collapseProgress).dp
    val bottomPadding = (20 - 8 * collapseProgress).dp
    val iconSize = (42 - 8 * collapseProgress).dp
    val avatarSize = (48 - 10 * collapseProgress).dp
    val iconSpacing = (12 - 2 * collapseProgress).dp
    val titleFontSize = (25 - 5 * collapseProgress).sp
    val titleLineHeight = (30 - 6 * collapseProgress).sp
    val subtitleFontSize = (14 - 2 * collapseProgress).sp

    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        backdropState = backdropState,
        shape = RectangleShape,
        cornerRadius = 0.dp,
        topRadius = 0.dp,
        bottomRadius = 0.dp,
        tintColor = MaterialTheme.colorScheme.background,
        tintAlpha = 0.85f,
        shadowElevation = 0.dp,
        refractIntensity = 0f,
        thicknessDp = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = bottomPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(iconSpacing),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.app_icon_small),
                    contentDescription = "Rip Mission Control",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(iconSize)
                        .clip(CircleShape)
                        .clickable { onOpenRipVisualizer() },
                )
                Column {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.titleLargeEmphasized.copy(
                            fontSize = titleFontSize,
                            lineHeight = titleLineHeight,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.home_welcome,
                            userDisplayName(session?.user)
                                ?: stringResource(R.string.home_user_fallback),
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = subtitleFontSize,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            UserAvatar(
                session = session,
                onClick = onOpenSettings,
                size = avatarSize,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

@Composable
private fun HomeContent(
    session: AuthSession?,
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
    onTrackClick: (HomeTrack) -> Unit = {},
    onArtistClick: (HomeArtist) -> Unit = {},
    onAlbumClick: (HomeAlbum) -> Unit = {},
) {
    val motionScheme = MaterialTheme.motionScheme
    var isGlobalTrending by remember { mutableStateOf(false) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topClearance = statusBarTop + 84.dp
    val navBarBottomInset =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomClearance = FloatingCombinedClearance + navBarBottomInset + 16.dp

    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topClearance),
            )
        },
    ) {
        if (feedState.isAllEmpty && !feedState.isInitialLoading && feedState.regionalTrending.status == SectionLoadState.LOADED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = topClearance + 8.dp,
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
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topClearance + 8.dp,
                    bottom = bottomClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (feedState.rotation.status == SectionLoadState.LOADING) {
                    item(key = "rotation_skeleton") {
                        SkeletonTrackCarousel(
                            titleWidth = 140.dp,
                            subtitleWidth = 200.dp,
                            isArtist = false,
                        )
                    }
                } else if (feedState.rotation.status == SectionLoadState.LOADED && feedState.rotation.items.isNotEmpty()) {
                    item(key = "rotation_live") {
                        HomeTrackCarousel(
                            title = stringResource(R.string.home_your_rotation),
                            subtitle = stringResource(R.string.home_your_rotation_subtitle),
                            tracks = feedState.rotation.items,
                            onTrackClick = onTrackClick,
                        )
                    }
                }

                if (feedState.recommended.status == SectionLoadState.LOADING) {
                    item(key = "recommended_skeleton") {
                        SkeletonTrackCarousel(
                            titleWidth = 150.dp,
                            subtitleWidth = 220.dp,
                            isArtist = false,
                        )
                    }
                } else if (feedState.recommended.status == SectionLoadState.LOADED && feedState.recommended.items.isNotEmpty()) {
                    item(key = "recommended_live") {
                        HomeTrackCarousel(
                            title = stringResource(R.string.home_recommended),
                            subtitle = stringResource(R.string.home_recommended_subtitle),
                            tracks = feedState.recommended.items,
                            onTrackClick = onTrackClick,
                        )
                    }
                }

                if (feedState.topArtists.status == SectionLoadState.LOADING) {
                    item(key = "top_artists_skeleton") {
                        SkeletonTrackCarousel(
                            titleWidth = 120.dp,
                            subtitleWidth = 180.dp,
                            isArtist = true,
                        )
                    }
                } else if (feedState.topArtists.status == SectionLoadState.LOADED && feedState.topArtists.items.isNotEmpty()) {
                    item(key = "top_artists_live") {
                        HomeArtistCarousel(
                            title = stringResource(R.string.home_top_artists),
                            subtitle = stringResource(R.string.home_top_artists_subtitle),
                            artists = feedState.topArtists.items,
                            onArtistClick = onArtistClick,
                        )
                    }
                }

                if (feedState.topAlbums.status == SectionLoadState.LOADING) {
                    item(key = "top_albums_skeleton") {
                        SkeletonTrackCarousel(
                            titleWidth = 130.dp,
                            subtitleWidth = 190.dp,
                            isArtist = false,
                        )
                    }
                } else if (feedState.topAlbums.status == SectionLoadState.LOADED && feedState.topAlbums.items.isNotEmpty()) {
                    item(key = "top_albums_live") {
                        HomeAlbumCarousel(
                            title = stringResource(R.string.home_top_albums),
                            subtitle = stringResource(R.string.home_top_albums_subtitle),
                            albums = feedState.topAlbums.items,
                            onAlbumClick = onAlbumClick,
                        )
                    }
                }

                item(key = "top_tracks") {
                    LaunchedEffect(Unit) {
                        onLoadTopTracks()
                    }
                    when (feedState.topTracks.status) {
                        SectionLoadState.LOADING -> {
                            SkeletonTrackCarousel(
                                titleWidth = 130.dp,
                                subtitleWidth = 190.dp,
                                isArtist = false,
                            )
                        }

                        SectionLoadState.LOADED -> {
                            if (feedState.topTracks.items.isNotEmpty()) {
                                HomeTrackCarousel(
                                    title = stringResource(R.string.home_top_tracks),
                                    subtitle = stringResource(R.string.home_top_tracks_subtitle),
                                    tracks = feedState.topTracks.items,
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
                    if (feedState.regionalTrending.status == SectionLoadState.LOADING || feedState.globalTrending.status == SectionLoadState.LOADING) {
                        SkeletonSegmentedList()
                    } else if (feedState.regionalTrending.status == SectionLoadState.LOADED || feedState.globalTrending.status == SectionLoadState.LOADED) {
                        val hasRegional = feedState.regionalTrending.items.isNotEmpty()
                        val effectiveIsGlobal = isGlobalTrending || !hasRegional
                        val currentTrending = if (effectiveIsGlobal) {
                            feedState.globalTrending.items
                        } else {
                            feedState.regionalTrending.items
                        }
                        if (currentTrending.isNotEmpty() || feedState.globalTrending.items.isNotEmpty() || feedState.regionalTrending.items.isNotEmpty()) {
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
                                        feedState.globalTrending.items
                                    } else {
                                        feedState.regionalTrending.items
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
                    when (feedState.weeklyPicks.status) {
                        SectionLoadState.LOADING -> {
                            SkeletonTrackCarousel(
                                titleWidth = 140.dp,
                                subtitleWidth = 200.dp,
                                isArtist = false,
                            )
                        }

                        SectionLoadState.LOADED -> {
                            if (feedState.weeklyPicks.items.isNotEmpty()) {
                                HomeTrackCarousel(
                                    title = stringResource(R.string.home_weekly_picks),
                                    subtitle = stringResource(R.string.home_weekly_picks_subtitle),
                                    tracks = feedState.weeklyPicks.items,
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
