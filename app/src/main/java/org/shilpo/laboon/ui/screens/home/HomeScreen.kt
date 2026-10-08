@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class
)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.auth.SharedPreferencesKeyValueStore
import org.shilpo.laboon.home.AlbumDetailsCache
import org.shilpo.laboon.home.AlbumDetailsRepository
import org.shilpo.laboon.home.ArtistDetailsCache
import org.shilpo.laboon.home.ArtistDetailsRepository
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.home.HomeFeedCache
import org.shilpo.laboon.home.HomeFeedDefaults
import org.shilpo.laboon.home.HomeFeedRepository
import org.shilpo.laboon.home.HomeFeedState
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.RecordLabelCache
import org.shilpo.laboon.home.RecordLabelRepository
import org.shilpo.laboon.home.SectionLoadState
import org.shilpo.laboon.home.SectionState
import org.shilpo.laboon.navigation.MainTab
import org.shilpo.laboon.navigation.RouteDirection
import org.shilpo.laboon.navigation.RouteEvent
import org.shilpo.laboon.navigation.RouteState
import org.shilpo.laboon.navigation.tabTransitionDirection
import org.shilpo.laboon.playback.PlaybackManager
import org.shilpo.laboon.playback.PlaybackManagerHolder
import org.shilpo.laboon.playback.PlaybackPersistence
import org.shilpo.laboon.playback.PlaybackState
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.rip.AutoRipCoordinator
import org.shilpo.laboon.rip.AutoRipSource
import org.shilpo.laboon.rip.RipConnectionHolder
import org.shilpo.laboon.rip.RipConnectionHolderInstance
import org.shilpo.laboon.search.SearchFilter
import org.shilpo.laboon.search.SearchRepositoryImpl
import org.shilpo.laboon.search.SearchResults
import org.shilpo.laboon.theme.LocalVisualMotionScale
import org.shilpo.laboon.theme.LocalVisualTheme
import org.shilpo.laboon.theme.LocalVisualThemeController
import org.shilpo.laboon.theme.contract.VisualThemeAction
import org.shilpo.laboon.theme.renderer.VisualDefinitionRenderer
import org.shilpo.laboon.theme.renderer.VisualThemePresentation
import org.shilpo.laboon.ui.design.FloatingCombinedClearance
import org.shilpo.laboon.ui.design.FloatingNavBar
import org.shilpo.laboon.ui.design.LiquidGlassBackdropState
import org.shilpo.laboon.ui.design.LiquidGlassSurface
import org.shilpo.laboon.ui.design.MiniPlayerHeight
import org.shilpo.laboon.ui.design.MiniPlayerSpacing
import org.shilpo.laboon.ui.design.NavigationBarBottomPadding
import org.shilpo.laboon.ui.design.NavigationBarHeight
import org.shilpo.laboon.ui.design.PredictiveBackSpec
import org.shilpo.laboon.ui.design.PredictiveBackSurface
import org.shilpo.laboon.ui.design.SkeletonSegmentedList
import org.shilpo.laboon.ui.design.SkeletonTrackCarousel
import org.shilpo.laboon.ui.design.UserAvatar
import org.shilpo.laboon.ui.design.liquidGlassBackdropProducer
import org.shilpo.laboon.ui.design.rememberLiquidGlassBackdropState
import org.shilpo.laboon.ui.design.rememberPredictiveBackState
import org.shilpo.laboon.ui.design.userDisplayName
import org.shilpo.laboon.ui.screens.album.AlbumDetailsScreen
import org.shilpo.laboon.ui.screens.album.AlbumDetailsUiState
import org.shilpo.laboon.ui.screens.album.AlbumRelatedDestination
import org.shilpo.laboon.ui.screens.album.AlbumRelatedPlaceholderScreen
import org.shilpo.laboon.ui.screens.album.albumArtistDestination
import org.shilpo.laboon.ui.screens.album.toUiState
import org.shilpo.laboon.ui.screens.artist.ArtistDetailsScreen
import org.shilpo.laboon.ui.screens.artist.ArtistDetailsUiState
import org.shilpo.laboon.ui.screens.artist.toUiState
import org.shilpo.laboon.ui.screens.label.RecordLabelScreen
import org.shilpo.laboon.ui.screens.label.RecordLabelUiState
import org.shilpo.laboon.ui.screens.label.toUiState
import org.shilpo.laboon.ui.screens.library.LibraryScreen
import org.shilpo.laboon.ui.screens.player.MorphingPlayerSheet
import org.shilpo.laboon.ui.screens.rip.RipVisualizerScreen
import org.shilpo.laboon.ui.screens.search.SearchScreen
import org.shilpo.laboon.ui.screens.settings.SettingsCategory
import org.shilpo.laboon.ui.screens.settings.SettingsScreen
import org.shilpo.laboon.ui.screens.settings.contractId
import org.shilpo.laboon.ui.screens.settings.settingsCategoryFromContractId

data class ArtistDestination(
    val name: String,
    val appleArtistId: String? = null,
)

data class RecordLabelDestination(
    val name: String,
    val appleLabelId: String? = null,
)

internal sealed interface OverlayDestination {
    val key: String

    data class Album(val appleAlbumId: String) : OverlayDestination {
        override val key: String = "album_$appleAlbumId"
    }

    data class Artist(val destination: ArtistDestination) : OverlayDestination {
        override val key: String = "artist_${destination.appleArtistId ?: destination.name}"
    }

    data class AlbumRelated(val destination: AlbumRelatedDestination) : OverlayDestination {
        override val key: String = "related_${destination.identity}_${destination.title}"
    }

    data class RecordLabel(val destination: RecordLabelDestination) : OverlayDestination {
        override val key: String = "label_${destination.appleLabelId ?: destination.name}"
    }
}

internal data class OverlayEntry(
    val id: Long,
    val destination: OverlayDestination,
)

@Composable
fun HomeScreen(
    state: RouteState,
    onEvent: (RouteEvent) -> Unit,
    session: AuthSession?,
    repository: HomeFeedRepository,
    modifier: Modifier = Modifier,
    onActiveTrackChange: ((HomeTrack?, Boolean) -> Unit)? = null,
    onDisconnect: () -> Unit = {},
    openThemeSelection: Boolean = false,
    onThemeSelectionOpened: () -> Unit = {},
) {
    val currentTab = state.currentTab
    val showSettings = state.settingsVisible

    val context = LocalContext.current
    val keyValueStore = remember(context) { SharedPreferencesKeyValueStore(context) }
    val homeFeedCache = remember(keyValueStore) { HomeFeedCache(keyValueStore) }
    val playbackPersistence =
        remember(keyValueStore) { PlaybackPersistence(keyValueStore) }

    val initialCachedFeed = remember { homeFeedCache.load() }
    var feedState by remember { mutableStateOf(initialCachedFeed ?: HomeFeedDefaults.defaultFeed) }
    var playerExpansionProgress by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showThemeQueue by rememberSaveable { mutableStateOf(false) }
    var showThemeLyrics by rememberSaveable { mutableStateOf(false) }
    var showThemeAudioInfo by rememberSaveable { mutableStateOf(false) }
    var customSettingsCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(openThemeSelection) {
        if (openThemeSelection) {
            customSettingsCategoryId = SettingsCategory.APPEARANCE.contractId
            onThemeSelectionOpened()
        }
    }

    val sessionStore = remember(keyValueStore) { SessionStore(keyValueStore) }
    val albumDetailsRepository = remember(sessionStore, keyValueStore) {
        AlbumDetailsRepository(sessionStore, AlbumDetailsCache(keyValueStore))
    }

    val ripConnection = remember(context, sessionStore) {
        RipConnectionHolderInstance.getInstance(context, sessionStore)
    }
    val ripWsClient = ripConnection.ripClient
    val autoRipCoordinator = ripConnection.autoRip
    val ripState by ripWsClient.state.collectAsState()
    val searchRepository = remember(sessionStore) { SearchRepositoryImpl(sessionStore) }
    var themeSearchQuery by rememberSaveable { mutableStateOf("") }
    var themeSearchFilter by rememberSaveable { mutableStateOf(SearchFilter.TOP_RESULTS.apiType) }
    var themeSearchResults by remember { mutableStateOf(SearchResults()) }
    var themeSearchLoading by remember { mutableStateOf(false) }
    var themeSearchError by remember { mutableStateOf("") }
    var themeSearchGeneration by remember { mutableIntStateOf(0) }
    var showRipVisualizer by rememberSaveable { mutableStateOf(false) }
    var overlayStack by remember { mutableStateOf<List<OverlayEntry>>(emptyList()) }
    var nextOverlayId by remember { mutableLongStateOf(1L) }
    val artistDetailsRepository = remember(sessionStore, keyValueStore) {
        ArtistDetailsRepository(sessionStore, ArtistDetailsCache(keyValueStore))
    }
    val recordLabelRepository = remember(sessionStore, keyValueStore) {
        RecordLabelRepository(sessionStore, RecordLabelCache(keyValueStore))
    }
    val isAlbumOverlayVisible = overlayStack.isNotEmpty()

    val openArtist: (String, String?) -> Unit = { name, appleId ->
        overlayStack = overlayStack + OverlayEntry(
            id = nextOverlayId++,
            destination = OverlayDestination.Artist(ArtistDestination(name, appleId)),
        )
    }
    val popOverlay: () -> Unit = {
        if (overlayStack.isNotEmpty()) {
            overlayStack = overlayStack.dropLast(1)
        }
    }
    val closeArtist: () -> Unit = { popOverlay() }

    val openAlbum: (String) -> Unit = { appleAlbumId ->
        overlayStack = overlayStack + OverlayEntry(
            id = nextOverlayId++,
            destination = OverlayDestination.Album(appleAlbumId),
        )
    }
    val closeAlbum: () -> Unit = {
        overlayStack = emptyList()
    }
    val openAlbumArtist: (String) -> Unit = { name ->
        albumArtistDestination(name)?.let { dest ->
            overlayStack = overlayStack + OverlayEntry(
                id = nextOverlayId++,
                destination = OverlayDestination.AlbumRelated(dest),
            )
        }
    }
    val openAlbumRecordLabel: (String, String?) -> Unit = { name, explicitLabelId ->
        val cleanName = name.trim()
        val cleanId = explicitLabelId?.trim()?.takeIf { it.isNotEmpty() }
            ?: cleanName.takeIf { it.removePrefix("apple_").all(Char::isDigit) }
                ?.removePrefix("apple_")
        if (cleanName.isNotEmpty() || cleanId != null) {
            overlayStack = overlayStack + OverlayEntry(
                id = nextOverlayId++,
                destination = OverlayDestination.RecordLabel(
                    RecordLabelDestination(
                        name = cleanName.ifEmpty { "Record Label" },
                        appleLabelId = cleanId,
                    )
                ),
            )
        }
    }
    val onAlbumClick: (HomeAlbum) -> Unit = { album ->
        album.appleCatalogId
            ?.takeIf(String::isNotBlank)
            ?.let(openAlbum)
    }


    LaunchedEffect(ripConnection) {
        ripConnection.syncWithSession()
    }

    val playbackManager = remember(context, sessionStore) {
        PlaybackManagerHolder.getInstance(
            context.applicationContext,
            sessionStore,
            autoRipCoordinator = autoRipCoordinator,
            completedRipTrackIds = ripConnection.completedRipTrackIds,
        )
    }
    val playbackState by playbackManager.state.collectAsState()
    val queueState by playbackManager.queueManager.state.collectAsState()
    val spectrumState by playbackManager.spectrumState.collectAsState()


    val feedTracks = remember(feedState) {
        listOf(
            feedState.rotation.items,
            feedState.recommended.items,
            feedState.topTracks.items,
            feedState.regionalTrending.items,
            feedState.globalTrending.items,
            feedState.weeklyPicks.items,
        ).flatten()
    }
    LaunchedEffect(autoRipCoordinator, feedTracks) {
        autoRipCoordinator.observe(AutoRipSource.HOME_FEED, feedTracks)
    }
    val matchedTopAlbums = remember(feedState.topAlbums.items) {
        feedState.topAlbums.items.filter { !it.appleCatalogId.isNullOrBlank() }
    }
    LaunchedEffect(autoRipCoordinator, matchedTopAlbums) {
        autoRipCoordinator.observeAlbums(AutoRipSource.HOME_FEED, matchedTopAlbums)
    }
    LaunchedEffect(ripConnection) {
        launch {
            ripConnection.completedRipTrackIds.collect { providerTrackId ->
                val lookup = ripConnection.autoRip.refreshAvailabilityNow(providerTrackId)
                val availability = lookup.cached
                if (availability.isEmpty()) return@collect
                feedState = feedState.withAvailability(availability)
                homeFeedCache.save(feedState)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        playbackManager.syncWithCurrentPlayer()
    }

    var isPlayerDismissed by rememberSaveable { mutableStateOf(playbackPersistence.isPlayerDismissed()) }
    var fallbackTrack by remember { mutableStateOf<HomeTrack?>(playbackPersistence.getLastTrack()) }
    var searchFocusTrigger by remember { mutableIntStateOf(0) }

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
        if (cached != null) {
            launch {
                val availability = feedState.resolveAvailability(searchRepository)
                if (availability.isEmpty()) return@launch
                feedState = feedState.withAvailability(availability)
                homeFeedCache.save(feedState)
            }
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

    val ripVisualizerBackState = rememberPredictiveBackState(
        enabled = showRipVisualizer,
        onBack = { showRipVisualizer = false },
    )

    val overlayBackState = rememberPredictiveBackState(
        enabled = overlayStack.isNotEmpty() && !showRipVisualizer,
        onBack = popOverlay,
    )

    val homeBackState = rememberPredictiveBackState(
        enabled = state.canGoBackWithinHome && overlayStack.isEmpty() && !showRipVisualizer,
        onBack = { onEvent(RouteEvent.BackPressed) },
    )

    val tabIsBackTarget = !showSettings && currentTab != MainTab.Home
    val settingsProgress = homeBackState.progressFor(showSettings)
    val visualizerProgress = ripVisualizerBackState.progressFor(showRipVisualizer)

    val motionScheme = MaterialTheme.motionScheme
    val albumChromeTransition = updateTransition(
        targetState = isAlbumOverlayVisible,
        label = "albumBottomChrome",
    )
    val albumDockProgress by albumChromeTransition.animateFloat(
        transitionSpec = { motionScheme.defaultSpatialSpec() },
        label = "albumDockProgress",
    ) { albumOpen -> if (albumOpen) 1f else 0f }

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
    val albumBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val albumBottomChromeClearance = if (activeTrack != null) {
        NavigationBarHeight * (1f - albumDockProgress) +
                NavigationBarBottomPadding + MiniPlayerSpacing + MiniPlayerHeight
    } else {
        (NavigationBarHeight + NavigationBarBottomPadding) * (1f - albumDockProgress)
    }
    val albumBottomClearance = albumBottomInset + albumBottomChromeClearance
    val homeScrollState = rememberLazyListState()

    LaunchedEffect(homeScrollState, liquidGlassBackdropState) {
        snapshotFlow { homeScrollState.firstVisibleItemIndex to homeScrollState.firstVisibleItemScrollOffset }
            .collect {
                liquidGlassBackdropState.invalidate()
            }
    }

    LaunchedEffect(currentTab) {
        liquidGlassBackdropState.invalidate()
        delay(50)
        liquidGlassBackdropState.invalidate()
        delay(200)
        liquidGlassBackdropState.invalidate()
        delay(200)
        liquidGlassBackdropState.invalidate()
    }

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

    val visualTheme = LocalVisualTheme.current
    val visualThemeController = LocalVisualThemeController.current
    val extensionScreenName = when {
        showThemeQueue -> "queue"
        showRipVisualizer -> "visualizer"
        showSettings -> customSettingsCategoryId?.let { "settings.$it" } ?: "settings"
        overlayStack.lastOrNull()?.destination is OverlayDestination.AlbumRelated -> "albumRelated"
        overlayStack.lastOrNull()?.destination is OverlayDestination.Album -> "album"
        overlayStack.lastOrNull()?.destination is OverlayDestination.Artist -> "artist"
        overlayStack.lastOrNull()?.destination is OverlayDestination.RecordLabel -> "label"
        activeTrack != null && playerExpansionProgress > 0.55f -> "player"
        currentTab == MainTab.Search -> "search"
        currentTab == MainTab.Library -> "library"
        else -> "home"
    }
    androidx.activity.compose.BackHandler(enabled = showThemeQueue || showThemeLyrics || showThemeAudioInfo) {
        when {
            showThemeAudioInfo -> showThemeAudioInfo = false
            showThemeLyrics -> showThemeLyrics = false
            else -> showThemeQueue = false
        }
    }
    val extensionAlbumDestination =
        overlayStack.lastOrNull()?.destination as? OverlayDestination.Album
    val extensionArtistDestination =
        overlayStack.lastOrNull()?.destination as? OverlayDestination.Artist
    val extensionLabelDestination =
        overlayStack.lastOrNull()?.destination as? OverlayDestination.RecordLabel
    var extensionAlbumState by remember(extensionAlbumDestination) {
        mutableStateOf<AlbumDetailsUiState>(AlbumDetailsUiState.Loading)
    }
    var extensionArtistState by remember(extensionArtistDestination) {
        mutableStateOf<ArtistDetailsUiState>(ArtistDetailsUiState.Loading)
    }
    var extensionLabelState by remember(extensionLabelDestination) {
        mutableStateOf<RecordLabelUiState>(RecordLabelUiState.Loading)
    }
    var extensionDetailRevision by remember { mutableIntStateOf(0) }
    val extensionAboutRepository =
        remember { org.shilpo.laboon.ui.screens.about.GithubAboutRepository() }
    var extensionAboutData by remember {
        mutableStateOf<org.shilpo.laboon.ui.screens.about.AboutData?>(
            null
        )
    }
    LaunchedEffect(extensionScreenName, visualTheme?.manifest?.id) {
        if (extensionScreenName == "settings.about" && visualTheme?.definition?.screens?.containsKey(
                extensionScreenName
            ) == true
        ) {
            extensionAboutData = extensionAboutRepository.getAboutData()
        }
    }
    LaunchedEffect(
        visualTheme?.manifest?.id,
        extensionScreenName,
        extensionAlbumDestination,
        extensionArtistDestination,
        extensionLabelDestination,
        extensionDetailRevision,
    ) {
        when (extensionScreenName) {
            "album" -> extensionAlbumDestination?.let { destination ->
                if (visualTheme?.definition?.screens?.containsKey("album") == true) {
                    extensionAlbumState =
                        albumDetailsRepository.getCachedAlbum(destination.appleAlbumId)
                            ?.toUiState() ?: AlbumDetailsUiState.Loading
                    extensionAlbumState =
                        albumDetailsRepository.getAlbum(destination.appleAlbumId).toUiState()
                }
            }

            "artist" -> extensionArtistDestination?.let { destination ->
                if (visualTheme?.definition?.screens?.containsKey("artist") == true) {
                    val artistId = destination.destination.appleArtistId
                        ?: artistDetailsRepository.resolveArtistId(destination.destination.name)
                    extensionArtistState =
                        artistId?.let { artistDetailsRepository.getArtist(it).toUiState() }
                            ?: ArtistDetailsUiState.NotFound
                }
            }

            "label" -> extensionLabelDestination?.let { destination ->
                if (visualTheme?.definition?.screens?.containsKey("label") == true) {
                    val labelId =
                        destination.destination.appleLabelId ?: destination.destination.name
                    extensionLabelState =
                        recordLabelRepository.getCachedRecordLabel(labelId)?.toUiState()
                            ?: RecordLabelUiState.Loading
                    extensionLabelState = recordLabelRepository.getRecordLabel(labelId)?.toUiState()
                        ?: RecordLabelUiState.NotFound
                }
            }
        }
    }
    val customScreen = visualTheme?.definition?.screens?.containsKey(extensionScreenName) == true
    val extensionMotionScale = LocalVisualMotionScale.current

    if (customScreen) {
        val activeVisualTheme = checkNotNull(visualTheme)
        val loadedExtensionAlbum = extensionAlbumState as? AlbumDetailsUiState.Loaded
        val loadedExtensionArtist = extensionArtistState as? ArtistDetailsUiState.Loaded
        val loadedExtensionLabel = extensionLabelState as? RecordLabelUiState.Loaded
        val selectedSettingsCategory = settingsCategoryFromContractId(customSettingsCategoryId)
        val presentationValues = buildMap {
            put("screen.name", extensionScreenName)
            put(
                "theme.optionsRevision",
                org.shilpo.laboon.theme.LocalVisualThemeRevision.current.toString()
            )
            put("screen.tab", currentTab.name.lowercase())
            put(
                "screen.loading",
                when (extensionScreenName) {
                    "album" -> extensionAlbumState is AlbumDetailsUiState.Loading
                    "artist" -> extensionArtistState is ArtistDetailsUiState.Loading
                    "label" -> extensionLabelState is RecordLabelUiState.Loading
                    else -> isRefreshing || feedState.rotation.status == SectionLoadState.LOADING
                }.toString(),
            )
            put("screen.error", playbackState.error.orEmpty())
            put("search.query", themeSearchQuery)
            put("search.filter", themeSearchFilter)
            put("search.loading", themeSearchLoading.toString())
            put("search.error", themeSearchError)
            put(
                "search.resultCount",
                (themeSearchResults.tracks.size + themeSearchResults.albums.size + themeSearchResults.artists.size + themeSearchResults.topResults.size).toString()
            )
            put("rip.activeCount", ripState.activeTasks.size.toString())
            val relatedDestination =
                (overlayStack.lastOrNull()?.destination as? OverlayDestination.AlbumRelated)?.destination
            put("related.name", relatedDestination?.name.orEmpty())
            put("related.title", relatedDestination?.title.orEmpty())
            put("related.identity", relatedDestination?.identity.orEmpty())
            put("user.displayName", userDisplayName(session?.user).orEmpty())
            put("player.visible", (activeTrack != null).toString())
            put("player.isPlaying", activeIsPlaying.toString())
            put("player.isBuffering", playbackState.isBuffering.toString())
            put("player.progress", activeProgress.toString())
            put("player.expansionProgress", playerExpansionProgress.toString())
            put("player.artworkMorphProgress", if (activeIsPlaying) "1" else "0")
            put("player.positionMs", currentPositionMs.toString())
            put("player.durationMs", currentDurationMs.toString())
            put("playback.canSkipPrevious", queueState.hasPrevious.toString())
            put("playback.canSkipNext", queueState.hasNext.toString())
            put("playback.audioQuality.codec", playbackState.audioQuality?.codec.orEmpty())
            put("playback.audioQuality.lossless", playbackState.audioQuality?.isLossless.toString())
            put("playback.audioQuality.hiRes", playbackState.audioQuality?.isHiRes.toString())
            put("playback.switchingQualityFormat", playbackState.switchingQualityFormat.orEmpty())
            put("playback.lyrics.loading", playbackState.lyricsLoading.toString())
            put("playback.lyrics.provider", playbackState.lyricsProvider.orEmpty())
            put("spectrum.bass", spectrumState.bass.toString())
            put("spectrum.mid", spectrumState.mid.toString())
            put("spectrum.treble", spectrumState.treble.toString())
            put("spectrum.amplitude", spectrumState.amplitude.toString())
            put("spectrum.beatPulse", spectrumState.beatPulse.toString())
            put("track.title", activeTrack?.title.orEmpty())
            put("track.artist", activeTrack?.artist.orEmpty())
            put("track.album", activeTrack?.album.orEmpty())
            put("track.artworkUrl", activeTrack?.artworkUrl.orEmpty())
            put("album.title", loadedExtensionAlbum?.album?.name.orEmpty())
            put("album.artist", loadedExtensionAlbum?.album?.artistName.orEmpty())
            put("album.artworkUrl", loadedExtensionAlbum?.album?.artworkUrl.orEmpty())
            put("album.releaseDate", loadedExtensionAlbum?.album?.releaseDate.orEmpty())
            put("album.trackCount", loadedExtensionAlbum?.album?.trackCount?.toString().orEmpty())
            put(
                "album.loading",
                (extensionScreenName == "album" && extensionAlbumState is AlbumDetailsUiState.Loading).toString()
            )
            put(
                "artist.loading",
                (extensionScreenName == "artist" && extensionArtistState is ArtistDetailsUiState.Loading).toString()
            )
            put(
                "label.loading",
                (extensionScreenName == "label" && extensionLabelState is RecordLabelUiState.Loading).toString()
            )
            put(
                "album.error", when (extensionAlbumState) {
                    AlbumDetailsUiState.NotFound -> "Album not found"
                    is AlbumDetailsUiState.Failed -> "Unable to load album"
                    else -> ""
                }
            )
            put(
                "album.hasError",
                (extensionAlbumState is AlbumDetailsUiState.NotFound || extensionAlbumState is AlbumDetailsUiState.Failed).toString()
            )
            put("artist.name", loadedExtensionArtist?.artist?.name.orEmpty())
            put("artist.artworkUrl", loadedExtensionArtist?.artist?.artworkUrl.orEmpty())
            put(
                "artist.error", when (extensionArtistState) {
                    ArtistDetailsUiState.NotFound -> "Artist not found"
                    is ArtistDetailsUiState.Failed -> "Unable to load artist"
                    else -> ""
                }
            )
            put(
                "artist.hasError",
                (extensionArtistState is ArtistDetailsUiState.NotFound || extensionArtistState is ArtistDetailsUiState.Failed).toString()
            )
            put("label.name", loadedExtensionLabel?.label?.name.orEmpty())
            put("label.artworkUrl", loadedExtensionLabel?.label?.artworkUrl.orEmpty())
            put(
                "label.error", when (extensionLabelState) {
                    RecordLabelUiState.NotFound -> "Record label not found"
                    RecordLabelUiState.Failed -> "Unable to load record label"
                    else -> ""
                }
            )
            put(
                "label.hasError",
                (extensionLabelState is RecordLabelUiState.NotFound || extensionLabelState is RecordLabelUiState.Failed).toString()
            )
            put("queue.count", queueState.upNextCount.toString())
            put("queue.shuffle", queueState.isShuffle.toString())
            put("queue.repeatMode", queueState.repeatMode.name.lowercase())
            put("settings.categoryCount", SettingsCategory.entries.size.toString())
            put("settings.categoryId", customSettingsCategoryId.orEmpty())
            put("settings.title", selectedSettingsCategory?.title.orEmpty())
            put("settings.subtitle", selectedSettingsCategory?.subtitle.orEmpty())
            put("about.version", org.shilpo.laboon.BuildConfig.VERSION_NAME)
            put("about.releaseTag", extensionAboutData?.releaseTag.orEmpty())
            put("about.loading", (extensionAboutData == null).toString())
            activeVisualTheme.definition.options.forEach { option ->
                put(
                    "option.${option.id}",
                    visualThemeController?.optionValue(activeVisualTheme, option)
                        ?: option.defaultValue
                )
            }
        }
        val extensionDetailTracks = when (extensionScreenName) {
            "album" -> loadedExtensionAlbum?.tracks.orEmpty()
            "artist" -> loadedExtensionArtist?.topSongs.orEmpty()
            else -> emptyList()
        }
        val presentationTracks = when (extensionScreenName) {
            "album", "artist" -> extensionDetailTracks
            else -> feedTracks
        }
        val trackItems = presentationTracks.map { track ->
            mapOf(
                "track.id" to track.id,
                "track.title" to track.title,
                "track.artist" to track.artist,
                "track.album" to track.album.orEmpty(),
                "track.artworkUrl" to track.artworkUrl.orEmpty(),
                "track.durationMs" to track.durationMs?.toString().orEmpty(),
            )
        }
        val presentationArtists = when (extensionScreenName) {
            "artist" -> loadedExtensionArtist?.similarArtists.orEmpty()
            "label" -> loadedExtensionLabel?.artists.orEmpty()
            else -> feedState.topArtists.items
        }
        val artistItems = presentationArtists.map { artist ->
            mapOf(
                "artist.id" to artist.id,
                "artist.name" to artist.name,
                "artist.artworkUrl" to artist.imageUrl.orEmpty(),
                "artist.appleCatalogId" to artist.appleCatalogId.orEmpty(),
            )
        }
        val presentationAlbums = when (extensionScreenName) {
            "album" -> loadedExtensionAlbum?.let { loaded ->
                loaded.album.otherVersions.map { version ->
                    HomeAlbum(
                        id = "apple_${version.id}",
                        title = version.name,
                        artist = version.artistName ?: loaded.album.artistName.orEmpty(),
                        artworkUrl = version.artworkUrl ?: loaded.album.artworkUrl,
                        appleCatalogId = version.id,
                    )
                }
            }.orEmpty()

            "artist" -> listOfNotNull(loadedExtensionArtist?.latestRelease) +
                    loadedExtensionArtist?.albums.orEmpty() + loadedExtensionArtist?.singles.orEmpty()

            "label" -> loadedExtensionLabel?.let { it.latestReleases + it.topReleases }.orEmpty()
            else -> feedState.topAlbums.items
        }
        val albumItems = presentationAlbums.map { album ->
            mapOf(
                "album.id" to album.id,
                "album.title" to album.title,
                "album.artist" to album.artist,
                "album.artworkUrl" to album.artworkUrl.orEmpty(),
                "album.appleCatalogId" to album.appleCatalogId.orEmpty(),
            )
        }
        val queueItems = queueState.playbackUpcomingEntries.mapIndexed { index, entry ->
            mapOf(
                "queue.entryId" to entry.id.toString(),
                "queue.index" to index.toString(),
                "queue.origin" to entry.origin.name.lowercase(),
                "track.id" to entry.track.id,
                "track.title" to entry.track.title,
                "track.artist" to entry.track.artist,
                "track.album" to entry.track.album.orEmpty(),
                "track.artworkUrl" to entry.track.artworkUrl.orEmpty(),
            )
        }
        val spectrumBandItems = spectrumState.bands.mapIndexed { index, level ->
            mapOf("spectrum.index" to index.toString(), "spectrum.level" to level.toString())
        }
        val lyricLineItems = playbackState.lyricsLines.mapIndexed { index, line ->
            mapOf(
                "lyrics.index" to index.toString(),
                "lyrics.text" to line.text,
                "lyrics.startMs" to line.startMs.toString(),
                "lyrics.endMs" to line.endMs.toString(),
                "lyrics.singer" to line.singer.orEmpty(),
                "lyrics.isInstrumental" to line.isInstrumental.toString(),
            )
        }
        val qualityVariantItems = activeTrack?.availableVariants.orEmpty().map { variant ->
            mapOf(
                "quality.format" to variant.format,
                "quality.fileSizeBytes" to variant.fileSizeBytes?.toString().orEmpty(),
            )
        }
        val ripTaskItems = ripState.activeTasks.map { task ->
            mapOf(
                "rip.taskId" to task.taskId,
                "rip.title" to (task.title ?: task.currentTrackTitle).orEmpty(),
                "rip.artist" to (task.artist ?: task.currentTrackArtist).orEmpty(),
                "rip.album" to task.album.orEmpty(),
                "rip.artworkUrl" to task.artworkUrl.orEmpty(),
                "rip.stage" to (task.upload?.stage ?: task.download?.stage
                ?: task.jobStage).orEmpty(),
                "rip.progress" to ((task.percent ?: 0f).coerceIn(0f, 100f) / 100f).toString(),
                "rip.error" to task.error.orEmpty(),
                "rip.isAlbum" to task.isAlbum.toString(),
            )
        }
        val settingsCategoryItems = SettingsCategory.entries.map { category ->
            mapOf(
                "settings.category" to category.contractId,
                "settings.title" to category.title,
                "settings.subtitle" to category.subtitle,
            )
        }
        val themeSearchTrackItems =
            (themeSearchResults.tracks + themeSearchResults.musicVideos).distinctBy(HomeTrack::id)
                .map { track ->
                    mapOf(
                        "track.id" to track.id,
                        "track.title" to track.title,
                        "track.artist" to track.artist,
                        "track.album" to track.album.orEmpty(),
                        "track.artworkUrl" to track.artworkUrl.orEmpty(),
                        "track.durationMs" to track.durationMs?.toString().orEmpty(),
                    )
                }
        val themeSearchAlbumItems = themeSearchResults.albums.map { album ->
            mapOf(
                "album.id" to album.id,
                "album.title" to album.title,
                "album.artist" to album.artist,
                "album.artworkUrl" to album.artworkUrl.orEmpty(),
                "album.appleCatalogId" to album.appleCatalogId.orEmpty(),
            )
        }
        val themeSearchArtistItems = themeSearchResults.artists.map { artist ->
            mapOf(
                "artist.id" to artist.id,
                "artist.name" to artist.name,
                "artist.artworkUrl" to artist.imageUrl.orEmpty(),
                "artist.appleCatalogId" to artist.appleCatalogId.orEmpty(),
            )
        }
        val themeSearchTopItems = themeSearchResults.topResults.map { result ->
            mapOf(
                "catalog.id" to result.id,
                "catalog.type" to result.type,
                "catalog.name" to result.name,
                "catalog.artist" to result.artistName.orEmpty(),
                "catalog.album" to result.albumName.orEmpty(),
                "catalog.artworkUrl" to result.artworkUrl.orEmpty(),
            )
        }
        val themeSearchPlaylistItems = themeSearchResults.playlists.map { playlist ->
            mapOf(
                "playlist.id" to playlist.id,
                "playlist.title" to playlist.title,
                "playlist.curator" to playlist.curator.orEmpty(),
                "playlist.artworkUrl" to playlist.artworkUrl.orEmpty(),
                "playlist.appleCatalogId" to playlist.appleCatalogId.orEmpty(),
            )
        }
        val themeSearchStationItems = themeSearchResults.stations.map { station ->
            mapOf(
                "station.id" to station.id,
                "station.title" to station.title,
                "station.artworkUrl" to station.artworkUrl.orEmpty(),
                "station.appleCatalogId" to station.appleCatalogId.orEmpty(),
            )
        }
        val availableThemeActions = setOf(
            VisualThemeAction.BACK,
            VisualThemeAction.OPEN_SETTINGS,
            VisualThemeAction.OPEN_HOME,
            VisualThemeAction.OPEN_SEARCH,
            VisualThemeAction.OPEN_LIBRARY,
            VisualThemeAction.OPEN_QUEUE,
            VisualThemeAction.OPEN_LYRICS,
            VisualThemeAction.OPEN_AUDIO_INFO,
            VisualThemeAction.PLAY_PAUSE,
            VisualThemeAction.OPEN_PLAYER,
            VisualThemeAction.PLAY_TRACK,
            VisualThemeAction.PLAY_NEXT,
            VisualThemeAction.ADD_TO_QUEUE,
            VisualThemeAction.OPEN_ALBUM,
            VisualThemeAction.OPEN_ARTIST,
            VisualThemeAction.RIP_TRACK,
            VisualThemeAction.TOGGLE_SHUFFLE,
            VisualThemeAction.CYCLE_REPEAT,
            VisualThemeAction.PREVIOUS_TRACK,
            VisualThemeAction.NEXT_TRACK,
            VisualThemeAction.SEEK,
            VisualThemeAction.SELECT_AUDIO_QUALITY,
            VisualThemeAction.REFRESH,
            VisualThemeAction.RETRY,
            VisualThemeAction.DISMISS_PLAYER,
            VisualThemeAction.OPEN_THEME_PICKER,
            VisualThemeAction.SIGN_OUT,
            VisualThemeAction.SEARCH,
            VisualThemeAction.OPEN_VISUALIZER,
            VisualThemeAction.CANCEL_RIP_TASK,
            VisualThemeAction.PLAY_QUEUE_ENTRY,
            VisualThemeAction.REMOVE_QUEUE_ENTRY,
            VisualThemeAction.MOVE_QUEUE_ENTRY,
            VisualThemeAction.CLEAR_QUEUE,
            VisualThemeAction.PROMOTE_AUTOPLAY,
            VisualThemeAction.RETRY_DISCOVERY,
        ).let { actions ->
            buildSet {
                addAll(actions)
                if (showSettings) add(VisualThemeAction.OPEN_SETTINGS_CATEGORY)
                if (extensionScreenName == "settings.about") {
                    add(VisualThemeAction.OPEN_PROJECT_LINK)
                    add(VisualThemeAction.OPEN_CONTRIBUTOR_PROFILE)
                    add(VisualThemeAction.COPY_BUILD_INFO)
                }
                if (activeTrack == null) {
                    removeAll(
                        setOf(
                            VisualThemeAction.PLAY_PAUSE,
                            VisualThemeAction.SEEK,
                            VisualThemeAction.DISMISS_PLAYER,
                            VisualThemeAction.SELECT_AUDIO_QUALITY,
                            VisualThemeAction.RETRY_DISCOVERY,
                            VisualThemeAction.OPEN_LYRICS,
                            VisualThemeAction.OPEN_AUDIO_INFO
                        )
                    )
                }
                if (!queueState.hasPrevious) remove(VisualThemeAction.PREVIOUS_TRACK)
                if (!queueState.hasNext) remove(VisualThemeAction.NEXT_TRACK)
            }
        }
        val currentPresentation = VisualThemePresentation(
            values = presentationValues,
            collections = mapOf(
                "tracks" to trackItems,
                "artists" to artistItems,
                "albums" to albumItems,
                "queue" to queueItems,
                "spectrum.bands" to spectrumBandItems,
                "playback.lyrics" to lyricLineItems,
                "playback.qualityVariants" to qualityVariantItems,
                "rip.tasks" to ripTaskItems,
                "settingsCategories" to settingsCategoryItems,
                "settings.features" to selectedSettingsCategory?.plannedFeatures.orEmpty()
                    .map { mapOf("settings.feature" to it) },
                "about.changelog" to extensionAboutData?.changelogItems.orEmpty()
                    .map { mapOf("about.change" to it) },
                "about.contributors" to extensionAboutData?.let { data ->
                    (listOf(data.leadDeveloper) + data.contributors).distinctBy { it.login }
                        .map { contributor ->
                            mapOf(
                                "contributor.login" to contributor.login,
                                "contributor.name" to (contributor.name ?: contributor.login),
                                "contributor.avatarUrl" to contributor.avatarUrl,
                                "contributor.role" to contributor.role
                            )
                        }
                }.orEmpty(),
                "search.tracks" to themeSearchTrackItems,
                "search.albums" to themeSearchAlbumItems,
                "search.artists" to themeSearchArtistItems,
                "search.topResults" to themeSearchTopItems,
                "search.playlists" to themeSearchPlaylistItems,
                "search.stations" to themeSearchStationItems,
            ),
            inputValues = mapOf("search.query" to themeSearchQuery),
        )
        val presentationSnapshots = remember(activeVisualTheme.manifest.id) {
            object : LinkedHashMap<String, VisualThemePresentation>(4, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VisualThemePresentation>?): Boolean =
                    size > 4
            }
        }
        androidx.compose.runtime.SideEffect {
            presentationSnapshots[extensionScreenName] = currentPresentation
        }
        Box(modifier = Modifier.fillMaxSize()) {
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = extensionScreenName,
                    modifier = modifier.fillMaxSize(),
                    transitionSpec = {
                        val motion = activeVisualTheme.definition.motion
                        val scale = extensionMotionScale
                        val animationSpec = if (scale <= 0f) {
                            snap()
                        } else {
                            spring<Float>(
                                dampingRatio = motion.springDampingRatio,
                                stiffness = (motion.springStiffness / (scale * scale)).coerceIn(
                                    1f,
                                    10_000f
                                ),
                            )
                        }
                        (fadeIn(animationSpec = animationSpec) + androidx.compose.animation.scaleIn(
                            initialScale = 0.96f,
                            animationSpec = animationSpec,
                        )) togetherWith (fadeOut(animationSpec = animationSpec) + androidx.compose.animation.scaleOut(
                            targetScale = 0.98f,
                            animationSpec = animationSpec,
                        ))
                    },
                    label = "extensionScreenTransition",
                ) { targetScreen ->
                    org.shilpo.laboon.theme.renderer.VisualThemeTransitionContext(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedContent,
                        enabled = extensionMotionScale > 0f,
                    ) {
                        org.shilpo.laboon.theme.ThemeBackdropContext {
                            VisualDefinitionRenderer(
                                definition = activeVisualTheme.definition,
                                screenName = targetScreen,
                                presentation = if (targetScreen == extensionScreenName) currentPresentation
                                else presentationSnapshots[targetScreen] ?: currentPresentation,
                                availableActions = if (targetScreen == extensionScreenName) availableThemeActions else emptySet(),
                                motionScale = extensionMotionScale,
                                onAction = { action, parameters ->
                                    when (action) {
                                        VisualThemeAction.BACK -> when {
                                            showSettings && customSettingsCategoryId != null -> customSettingsCategoryId =
                                                null

                                            showSettings -> onEvent(RouteEvent.SettingsClosed)
                                            showThemeQueue -> showThemeQueue = false
                                            showRipVisualizer -> showRipVisualizer = false
                                            overlayStack.isNotEmpty() -> popOverlay()
                                            playerExpansionProgress > 0.55f -> playerExpansionProgress =
                                                0f

                                            else -> onEvent(RouteEvent.BackPressed)
                                        }

                                        VisualThemeAction.OPEN_SETTINGS -> onEvent(RouteEvent.SettingsOpened)
                                        VisualThemeAction.OPEN_THEME_PICKER -> {
                                            customSettingsCategoryId =
                                                SettingsCategory.APPEARANCE.contractId
                                            onEvent(RouteEvent.SettingsOpened)
                                        }

                                        VisualThemeAction.OPEN_SETTINGS_CATEGORY -> {
                                            settingsCategoryFromContractId(parameters["settings.category"])?.let { category ->
                                                customSettingsCategoryId = category.contractId
                                                onEvent(RouteEvent.SettingsOpened)
                                            }
                                        }

                                        VisualThemeAction.OPEN_HOME -> onEvent(
                                            RouteEvent.TabSelected(
                                                MainTab.Home
                                            )
                                        )

                                        VisualThemeAction.OPEN_SEARCH -> onEvent(
                                            RouteEvent.TabSelected(
                                                MainTab.Search
                                            )
                                        )

                                        VisualThemeAction.OPEN_LIBRARY -> onEvent(
                                            RouteEvent.TabSelected(
                                                MainTab.Library
                                            )
                                        )

                                        VisualThemeAction.OPEN_QUEUE -> showThemeQueue = true
                                        VisualThemeAction.OPEN_LYRICS -> showThemeLyrics = true
                                        VisualThemeAction.OPEN_AUDIO_INFO -> showThemeAudioInfo =
                                            true

                                        VisualThemeAction.OPEN_VISUALIZER -> showRipVisualizer =
                                            true

                                        VisualThemeAction.OPEN_PLAYER -> playerExpansionProgress =
                                            1f

                                        VisualThemeAction.PLAY_PAUSE -> activeTrack?.let { track ->
                                            if (playbackState.currentTrack != null) playbackManager.togglePlayPause()
                                            else playbackManager.play(
                                                track,
                                                startPositionMs = savedPosition.takeIf { it > 0L })
                                        }

                                        VisualThemeAction.PLAY_TRACK,
                                        VisualThemeAction.PLAY_NEXT,
                                        VisualThemeAction.ADD_TO_QUEUE,
                                        VisualThemeAction.RIP_TRACK -> {
                                            val trackId = parameters["track.id"]
                                            val track =
                                                (extensionDetailTracks + feedTracks + themeSearchResults.tracks + themeSearchResults.musicVideos + queueState.upcoming + listOfNotNull(
                                                    activeTrack
                                                ))
                                                    .firstOrNull { it.id == trackId }
                                            when (action) {
                                                VisualThemeAction.PLAY_TRACK -> track?.let {
                                                    val searchContext =
                                                        (themeSearchResults.tracks + themeSearchResults.musicVideos)
                                                            .distinctBy(HomeTrack::id)
                                                    val contextTracks = when {
                                                        it in searchContext -> searchContext
                                                        it in extensionDetailTracks -> extensionDetailTracks
                                                        else -> null
                                                    }
                                                    if (contextTracks != null) playbackManager.play(
                                                        it,
                                                        contextTracks = contextTracks
                                                    )
                                                    else playbackManager.play(it)
                                                }

                                                VisualThemeAction.PLAY_NEXT -> track?.let(
                                                    playbackManager::playNext
                                                )

                                                VisualThemeAction.ADD_TO_QUEUE -> track?.let(
                                                    playbackManager::addToQueue
                                                )

                                                VisualThemeAction.RIP_TRACK -> track?.let(
                                                    ripWsClient::startRip
                                                )

                                                else -> Unit
                                            }
                                        }

                                        VisualThemeAction.OPEN_ARTIST -> {
                                            val name = parameters["artist.name"]
                                            if (!name.isNullOrBlank()) openArtist(
                                                name,
                                                parameters["artist.appleCatalogId"]
                                            )
                                        }

                                        VisualThemeAction.OPEN_ALBUM -> {
                                            parameters["album.appleCatalogId"]?.takeIf(String::isNotBlank)
                                                ?.let(openAlbum)
                                        }

                                        VisualThemeAction.TOGGLE_SHUFFLE -> playbackManager.queueManager.toggleShuffle()
                                        VisualThemeAction.CYCLE_REPEAT -> playbackManager.queueManager.cycleRepeatMode()
                                        VisualThemeAction.PREVIOUS_TRACK -> playbackManager.skipToPrevious()
                                        VisualThemeAction.NEXT_TRACK -> playbackManager.skipToNext()
                                        VisualThemeAction.REFRESH -> when (extensionScreenName) {
                                            "album" -> extensionAlbumDestination?.let { destination ->
                                                coroutineScope.launch {
                                                    extensionAlbumState =
                                                        AlbumDetailsUiState.Loading
                                                    extensionAlbumState =
                                                        albumDetailsRepository.refreshAlbum(
                                                            destination.appleAlbumId
                                                        ).toUiState()
                                                }
                                            }

                                            "artist" -> extensionArtistDestination?.let { destination ->
                                                coroutineScope.launch {
                                                    extensionArtistState =
                                                        ArtistDetailsUiState.Loading
                                                    val appleArtistId =
                                                        destination.destination.appleArtistId
                                                            ?: artistDetailsRepository.resolveArtistId(
                                                                destination.destination.name
                                                            )
                                                    extensionArtistState = appleArtistId?.let {
                                                        artistDetailsRepository.getArtist(
                                                            it,
                                                            forceRefresh = true
                                                        ).toUiState()
                                                    } ?: ArtistDetailsUiState.NotFound
                                                }
                                            }

                                            "label" -> extensionLabelDestination?.let { destination ->
                                                coroutineScope.launch {
                                                    extensionLabelState = RecordLabelUiState.Loading
                                                    val labelId =
                                                        destination.destination.appleLabelId
                                                            ?: destination.destination.name
                                                    extensionLabelState =
                                                        recordLabelRepository.getRecordLabel(
                                                            labelId,
                                                            forceRefresh = true,
                                                        )?.toUiState()
                                                            ?: RecordLabelUiState.NotFound
                                                }
                                            }

                                            else -> onRefresh()
                                        }

                                        VisualThemeAction.RETRY -> when (extensionScreenName) {
                                            "album" -> {
                                                extensionAlbumState = AlbumDetailsUiState.Loading
                                                extensionDetailRevision++
                                            }

                                            "artist" -> {
                                                extensionArtistState = ArtistDetailsUiState.Loading
                                                extensionDetailRevision++
                                            }

                                            "label" -> {
                                                extensionLabelState = RecordLabelUiState.Loading
                                                extensionDetailRevision++
                                            }

                                            else -> playbackManager.retryDiscovery()
                                        }

                                        VisualThemeAction.DISMISS_PLAYER -> {
                                            isPlayerDismissed = true
                                            fallbackTrack = null
                                            playbackPersistence.setPlayerDismissed(true)
                                            playbackManager.dismiss()
                                            onActiveTrackChange?.invoke(null, true)
                                        }

                                        VisualThemeAction.SIGN_OUT -> onDisconnect()
                                        VisualThemeAction.SELECT_AUDIO_QUALITY -> {
                                            val track = activeTrack
                                            val variant =
                                                parameters["quality.format"]?.let { format ->
                                                    track?.availableVariants?.firstOrNull { it.format == format }
                                                }
                                            if (track != null && variant != null) {
                                                playbackManager.switchQualityVariant(track, variant)
                                            }
                                        }

                                        VisualThemeAction.CANCEL_RIP_TASK -> {
                                            parameters["rip.taskId"]?.takeIf(String::isNotBlank)
                                                ?.let(ripWsClient::cancelTask)
                                        }

                                        VisualThemeAction.PLAY_QUEUE_ENTRY -> {
                                            parameters["queue.entryId"]?.toLongOrNull()
                                                ?.let(playbackManager::playQueueEntry)
                                        }

                                        VisualThemeAction.REMOVE_QUEUE_ENTRY -> {
                                            parameters["queue.index"]?.toIntOrNull()
                                                ?.takeIf { it in queueState.playbackUpcomingEntries.indices }
                                                ?.let(playbackManager.queueManager::removeUpNext)
                                        }

                                        VisualThemeAction.MOVE_QUEUE_ENTRY -> {
                                            val from = parameters["queue.index"]?.toIntOrNull()
                                            val direction = parameters["queue.direction"]
                                            val to =
                                                from?.let { index -> index + if (direction == "up") -1 else 1 }
                                            if (direction in setOf(
                                                    "up",
                                                    "down"
                                                ) && from != null && from in queueState.playbackUpcomingEntries.indices && to != null && to in queueState.playbackUpcomingEntries.indices
                                            ) {
                                                playbackManager.queueManager.moveUpNext(from, to)
                                            }
                                        }

                                        VisualThemeAction.CLEAR_QUEUE -> playbackManager.queueManager.clearUpNext()
                                        VisualThemeAction.PROMOTE_AUTOPLAY -> {
                                            parameters["queue.entryId"]?.toLongOrNull()
                                                ?.let(playbackManager.queueManager::promoteAutoplayToManual)
                                        }

                                        VisualThemeAction.RETRY_DISCOVERY -> playbackManager.retryDiscovery()
                                        VisualThemeAction.OPEN_PROJECT_LINK -> {
                                            val url = when (parameters["project.linkId"]) {
                                                "repository" -> "https://github.com/hitarashi/Laboon"
                                                "issues" -> "https://github.com/hitarashi/Laboon/issues"
                                                else -> null
                                            }
                                            url?.let {
                                                runCatching {
                                                    context.startActivity(
                                                        android.content.Intent(
                                                            android.content.Intent.ACTION_VIEW,
                                                            android.net.Uri.parse(it)
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        VisualThemeAction.OPEN_CONTRIBUTOR_PROFILE -> {
                                            val data = extensionAboutData
                                            val contributor =
                                                (data?.contributors.orEmpty() + listOfNotNull(data?.leadDeveloper))
                                                    .firstOrNull { it.login == parameters["contributor.login"] }
                                            contributor?.htmlUrl?.takeIf { it.startsWith("https://github.com/") }
                                                ?.let {
                                                    runCatching {
                                                        context.startActivity(
                                                            android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse(it)
                                                            )
                                                        )
                                                    }
                                                }
                                        }

                                        VisualThemeAction.COPY_BUILD_INFO -> {
                                            val clipboard =
                                                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            clipboard?.primaryClip =
                                                android.content.ClipData.newPlainText(
                                                    "Laboon version",
                                                    org.shilpo.laboon.BuildConfig.VERSION_NAME
                                                )
                                        }

                                        VisualThemeAction.SEARCH -> {
                                            val query = parameters["search.query"].orEmpty().trim()
                                            if (query.isNotEmpty()) {
                                                val filter = SearchFilter.ALL.firstOrNull {
                                                    it.apiType == parameters["search.filter"] ||
                                                            it.name.equals(
                                                                parameters["search.filter"],
                                                                ignoreCase = true
                                                            )
                                                } ?: SearchFilter.TOP_RESULTS
                                                themeSearchQuery = query
                                                themeSearchFilter = filter.apiType
                                                themeSearchError = ""
                                                themeSearchResults = SearchResults()
                                                themeSearchLoading = true
                                                val generation = ++themeSearchGeneration
                                                coroutineScope.launch {
                                                    val result = runCatching {
                                                        searchRepository.search(query, filter)
                                                    }
                                                    if (generation == themeSearchGeneration) {
                                                        result.fold(
                                                            onSuccess = { themeSearchResults = it },
                                                            onFailure = {
                                                                themeSearchError =
                                                                    "Search failed. Try again."
                                                            },
                                                        )
                                                        themeSearchLoading = false
                                                    }
                                                }
                                            }
                                        }

                                        VisualThemeAction.OPEN_TELEGRAM,
                                        VisualThemeAction.PREVIEW_THEME,
                                        VisualThemeAction.APPLY_THEME,
                                        VisualThemeAction.RESTORE_DEFAULT,
                                        VisualThemeAction.OPEN_LYRICS_SHARE,
                                        VisualThemeAction.TOGGLE_LYRIC_LINE,
                                        VisualThemeAction.SET_LYRICS_SHARE_OPTION,
                                        VisualThemeAction.COPY_LYRICS,
                                        VisualThemeAction.SHARE_LYRICS,
                                        VisualThemeAction.CONNECT_LAST_FM,
                                        VisualThemeAction.CONNECT_LISTENBRAINZ,
                                        VisualThemeAction.REQUEST_PERMISSION,
                                        VisualThemeAction.CONTINUE,
                                        VisualThemeAction.SEEK -> Unit
                                    }
                                },
                                onSeek = playbackManager::seekTo,
                                onFieldChange = { field, value ->
                                    if (field == "search.query") themeSearchQuery = value.take(500)
                                },
                                onArtwork = { artwork, artworkModifier ->
                                    AsyncImage(
                                        model = artwork,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = artworkModifier,
                                    )
                                },
                                onAsset = { assetPath, assetModifier ->
                                    val bitmap = remember(
                                        activeVisualTheme.packageName,
                                        activeVisualTheme.packageRevision,
                                        assetPath,
                                        visualThemeController
                                    ) {
                                        runCatching {
                                            val bytes = visualThemeController?.readAsset(
                                                activeVisualTheme,
                                                assetPath
                                            )
                                                ?: return@runCatching null
                                            org.shilpo.laboon.theme.decodeThemeBitmap(bytes)
                                        }.getOrNull()
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = assetModifier,
                                        )
                                    } else {
                                        Surface(
                                            modifier = assetModifier,
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ) {}
                                    }
                                },
                                onEffect = { effectId, effectModifier, content ->
                                    org.shilpo.laboon.theme.ThemeEffectSurface(
                                        effectId = effectId,
                                        modifier = effectModifier,
                                        content = content,
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding(),
                                onHostControl = { id, controlModifier ->
                                    if (id == "themeOptions") org.shilpo.laboon.ui.screens.settings.ThemeOptionsContent(
                                        controlModifier
                                    )
                                },
                                onBackdrop = { backdropModifier, content ->
                                    org.shilpo.laboon.theme.ThemeBackdropCapture(
                                        backdropModifier,
                                        content
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

    } else {
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
                        .then(

                            if (!isAlbumOverlayVisible) {
                                Modifier.liquidGlassBackdropProducer(
                                    state = liquidGlassBackdropState,
                                    layer = liquidGlassBackdropLayer,
                                    backgroundColor = MaterialTheme.colorScheme.background,
                                )
                            } else {
                                Modifier
                            },
                        ),
                    transitionSpec = {
                        val forward =
                            tabTransitionDirection(
                                initialState,
                                targetState
                            ) == RouteDirection.Forward
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
                            onDownloadTrack = { track -> ripWsClient.startRip(track) },
                            onArtistClick = { artist ->
                                openArtist(
                                    artist.name,
                                    artist.appleCatalogId
                                )
                            },
                            onAlbumClick = onAlbumClick,
                            lazyListState = homeScrollState,
                            modifier = Modifier.fillMaxSize(),
                        )

                        MainTab.Search -> SearchScreen(
                            backdropState = liquidGlassBackdropState,
                            bottomClearance = albumBottomClearance,
                            searchFocusTrigger = searchFocusTrigger,
                            onTrackClick = { track ->
                                playbackManager.play(track)
                            },
                            onAlbumClick = onAlbumClick,
                            onArtistClick = { artist ->
                                openArtist(
                                    artist.name,
                                    artist.appleCatalogId
                                )
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
                            ripTasks = ripState.activeTasks,
                            pendingRipTrackIds = ripState.pendingTrackIds,
                            onRipTrack = { track -> ripWsClient.startRip(track) },
                            onOpenRipVisualizer = { showRipVisualizer = true },
                            searchRepository = searchRepository,
                            onObservedTracks = { tracks ->
                                autoRipCoordinator.observe(AutoRipSource.SEARCH, tracks)
                            },
                            onObservedAlbums = { albums ->
                                autoRipCoordinator.observeAlbums(AutoRipSource.SEARCH, albums)
                            },
                            ripCompletions = ripConnection.completedRipTrackIds,
                            resolveAvailability = { providerTrackId ->
                                ripConnection.autoRip.refreshAvailabilityNow(providerTrackId).cached
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
                visible = currentTab == MainTab.Home && !isAlbumOverlayVisible,
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

            overlayStack.forEachIndexed { index, entry ->
                val isTop = index == overlayStack.lastIndex
                key(entry.id) {
                    PredictiveBackSurface(
                        state = overlayBackState,
                        spec = PredictiveBackSpec.HomeSettings,
                        active = isTop,
                    ) { surfaceModifier ->
                        when (val dest = entry.destination) {
                            is OverlayDestination.Album -> {
                                val isAlbumRipping =
                                    dest.appleAlbumId in ripState.pendingAlbumIds ||
                                            ripState.activeTasks.any { it.isAlbum && it.sourceTrackId == dest.appleAlbumId }
                                AlbumOverlayHost(
                                    appleAlbumId = dest.appleAlbumId,
                                    albumDetailsRepository = albumDetailsRepository,
                                    playbackManager = playbackManager,
                                    playbackState = playbackState,
                                    queueState = queueState,
                                    autoRipCoordinator = autoRipCoordinator,
                                    ripConnection = ripConnection,
                                    albumBottomClearance = albumBottomClearance,
                                    isTop = isTop,
                                    liquidGlassBackdropState = liquidGlassBackdropState,
                                    liquidGlassBackdropLayer = liquidGlassBackdropLayer,
                                    onBack = popOverlay,
                                    onOpenAlbumVersion = openAlbum,
                                    onOpenArtist = openArtist,
                                    onOpenRecordLabel = openAlbumRecordLabel,
                                    onDownloadTrack = { track -> ripWsClient.startRip(track) },
                                    isDownloadPending = { track ->
                                        val id = track.providerTrackId?.takeIf(String::isNotBlank)
                                            ?: track.id
                                        (isAlbumRipping && !track.isPlayable) ||
                                                id in ripState.pendingTrackIds ||
                                                track.id in ripState.pendingTrackIds ||
                                                ripState.activeTasks.any { it.sourceTrackId == id || it.sourceTrackId == track.id }
                                    },
                                    modifier = surfaceModifier.fillMaxSize(),
                                )
                            }

                            is OverlayDestination.Artist -> {
                                ArtistOverlayHost(
                                    destination = dest.destination,
                                    artistDetailsRepository = artistDetailsRepository,
                                    albumDetailsRepository = albumDetailsRepository,
                                    playbackManager = playbackManager,
                                    playbackState = playbackState,
                                    autoRipCoordinator = autoRipCoordinator,
                                    ripConnection = ripConnection,
                                    albumBottomClearance = albumBottomClearance,
                                    isTop = isTop,
                                    liquidGlassBackdropState = liquidGlassBackdropState,
                                    liquidGlassBackdropLayer = liquidGlassBackdropLayer,
                                    onBack = popOverlay,
                                    onOpenAlbum = openAlbum,
                                    onOpenArtist = openArtist,
                                    onDownloadTrack = { track -> ripWsClient.startRip(track) },
                                    isDownloadPending = { track ->
                                        val id = track.providerTrackId?.takeIf(String::isNotBlank)
                                            ?: track.id
                                        id in ripState.pendingTrackIds ||
                                                track.id in ripState.pendingTrackIds ||
                                                ripState.activeTasks.any { it.sourceTrackId == id || it.sourceTrackId == track.id }
                                    },
                                    modifier = surfaceModifier.fillMaxSize(),
                                )
                            }

                            is OverlayDestination.AlbumRelated -> {
                                AlbumRelatedPlaceholderScreen(
                                    destination = dest.destination,
                                    onBack = popOverlay,
                                    modifier = surfaceModifier.fillMaxSize(),
                                )
                            }

                            is OverlayDestination.RecordLabel -> {
                                RecordLabelOverlayHost(
                                    destination = dest.destination,
                                    recordLabelRepository = recordLabelRepository,
                                    autoRipCoordinator = autoRipCoordinator,
                                    albumBottomClearance = albumBottomClearance,
                                    isTop = isTop,
                                    liquidGlassBackdropState = liquidGlassBackdropState,
                                    liquidGlassBackdropLayer = liquidGlassBackdropLayer,
                                    onBack = popOverlay,
                                    onOpenAlbum = openAlbum,
                                    onOpenArtist = openArtist,
                                    modifier = surfaceModifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }

            albumChromeTransition.AnimatedVisibility(
                visible = { albumOpen -> !albumOpen },
                enter = slideInVertically(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    initialOffsetY = { it },
                ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                exit = slideOutVertically(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    targetOffsetY = { it },
                ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FloatingNavBar(
                    selectedTab = currentTab,
                    onTabSelected = {
                        closeAlbum()
                        if (it == MainTab.Search && currentTab == MainTab.Search) {
                            searchFocusTrigger++
                        } else {
                            onEvent(RouteEvent.TabSelected(it))
                        }
                    },
                    hasMiniPlayerAbove = activeTrack != null && !isAlbumOverlayVisible,
                    backdropState = liquidGlassBackdropState,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = NavigationBarBottomPadding)
                        .graphicsLayer {
                            translationY =
                                with(density) { (playerExpansionProgress * 120.dp.toPx()) }
                        },
                )
            }

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
                        canSkipPrevious = queueState.hasPrevious,
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
                        albumDockProgress = albumDockProgress,
                        onExpansionProgressChange = { progress ->
                            playerExpansionProgress = progress
                        },
                        queueState = queueState,
                        isDiscovering = playbackState.isDiscovering,
                        discoveryStatus = playbackState.discoveryStatus,
                        onRemoveUpNext = { index ->
                            playbackManager.queueManager.removeUpNext(index)
                        },
                        onMoveUpNext = { from, to ->
                            playbackManager.queueManager.moveUpNext(from, to)
                        },
                        onTrackClick = { t ->
                            playbackManager.play(
                                t,
                                contextTracks = queueState.contextEntries.map { it.track },
                            )
                        },
                        onQueueEntryClick = playbackManager::playQueueEntry,
                        onPromoteAutoplay = playbackManager.queueManager::promoteAutoplayToManual,
                        onClearUpcoming = playbackManager.queueManager::clearUpNext,
                        onRetryDiscovery = playbackManager::retryDiscovery,
                        onOpenAlbum = { albumTrack ->
                            val albumId = albumDetailsRepository.resolveAlbumIdForTrack(albumTrack)
                            if (albumId == null) {
                                false
                            } else {
                                openAlbum(albumId)
                                true
                            }
                        },
                        onArtistClick = { artistName -> openArtist(artistName, null) },
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
                        openThemeSelection = openThemeSelection,
                        onThemeSelectionOpened = onThemeSelectionOpened,
                        initialCategoryId = customSettingsCategoryId,
                        onCategoryChanged = { customSettingsCategoryId = it },
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
    if (showThemeQueue && visualTheme?.definition?.screens?.containsKey("queue") != true) {
        org.shilpo.laboon.ui.screens.queue.QueueBottomSheet(
            queueState = queueState,
            onDismiss = { showThemeQueue = false },
            onTrackClick = { playbackManager.play(it) },
            onRemoveUpNext = playbackManager.queueManager::removeUpNext,
            onMoveUpNext = playbackManager.queueManager::moveUpNext,
            onClearUpNext = playbackManager.queueManager::clearUpNext,
            onToggleShuffle = playbackManager.queueManager::toggleShuffle,
            onCycleRepeatMode = playbackManager.queueManager::cycleRepeatMode,
            onQueueEntryClick = playbackManager::playQueueEntry,
            onPromoteAutoplay = playbackManager.queueManager::promoteAutoplayToManual,
            onRetryDiscovery = playbackManager::retryDiscovery,
            isDiscovering = playbackState.isDiscovering,
            discoveryStatus = playbackState.discoveryStatus,
        )
    }
    if (showThemeLyrics && activeTrack != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showThemeLyrics = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                org.shilpo.laboon.ui.screens.player.lyrics.LyricsScreen(
                    track = activeTrack,
                    currentPositionMs = currentPositionMs,
                    durationMs = currentDurationMs,
                    lyricsLines = playbackState.lyricsLines,
                    lyricsLoading = playbackState.lyricsLoading,
                    onSeek = playbackManager::seekTo,
                    isPlaying = activeIsPlaying,
                    spectrum = spectrumState,
                )
            }
        }
    }
    if (showThemeAudioInfo && activeTrack != null) {
        org.shilpo.laboon.ui.screens.player.AudioInfoDialog(
            isOpen = true, onDismiss = { showThemeAudioInfo = false }, track = activeTrack,
            pipeline = playbackState.audioQuality?.pipelineDetails, durationMs = currentDurationMs,
        )
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
    onDownloadTrack: (HomeTrack) -> Unit = {},
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
            val matchedTopAlbums = feedState.topAlbums.items
                .filter { !it.appleCatalogId.isNullOrBlank() }

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
                            onDownloadTrack = onDownloadTrack,
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
                            onDownloadTrack = onDownloadTrack,
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
                } else if (feedState.topAlbums.status == SectionLoadState.LOADED && matchedTopAlbums.isNotEmpty()) {
                    item(key = "top_albums_live") {
                        HomeAlbumCarousel(
                            title = stringResource(R.string.home_top_albums),
                            subtitle = stringResource(R.string.home_top_albums_subtitle),
                            albums = matchedTopAlbums,
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
                                    onDownloadTrack = onDownloadTrack,
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
                                                        onClick = {
                                                            if (track.isPlayable) {
                                                                onTrackClick(track)
                                                            } else if (!track.providerTrackId.isNullOrBlank()) {
                                                                onDownloadTrack(track)
                                                            }
                                                        },
                                                        enabled = track.isPlayable ||
                                                                !track.providerTrackId.isNullOrBlank(),
                                                        modifier = Modifier.size(36.dp),
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(
                                                                if (track.isPlayable) {
                                                                    R.drawable.ic_play
                                                                } else {
                                                                    R.drawable.ic_cloud_download
                                                                },
                                                            ),
                                                            contentDescription = if (track.isPlayable) {
                                                                "Play"
                                                            } else if (!track.providerTrackId.isNullOrBlank()) {
                                                                "Add to rip"
                                                            } else {
                                                                "Track unavailable"
                                                            },
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
                                    onDownloadTrack = onDownloadTrack,
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

@Composable
private fun AlbumOverlayHost(
    appleAlbumId: String,
    albumDetailsRepository: AlbumDetailsRepository,
    playbackManager: PlaybackManager,
    playbackState: PlaybackState,
    queueState: QueueState,
    autoRipCoordinator: AutoRipCoordinator,
    ripConnection: RipConnectionHolder?,
    albumBottomClearance: Dp,
    isTop: Boolean,
    liquidGlassBackdropState: LiquidGlassBackdropState,
    liquidGlassBackdropLayer: GraphicsLayer,
    onBack: () -> Unit,
    onOpenAlbumVersion: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenRecordLabel: (String, String?) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    isDownloadPending: (HomeTrack) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var albumLoadAttempt by remember(appleAlbumId) { mutableIntStateOf(0) }
    var isRefreshingAlbum by remember(appleAlbumId) { mutableStateOf(false) }

    val cachedInitial = remember(appleAlbumId) {
        albumDetailsRepository.getCachedAlbum(appleAlbumId)?.toUiState()
    }
    var albumDetailsState by remember(appleAlbumId) {
        mutableStateOf<AlbumDetailsUiState>(cachedInitial ?: AlbumDetailsUiState.Loading)
    }

    LaunchedEffect(appleAlbumId, albumLoadAttempt) {
        if (albumDetailsState !is AlbumDetailsUiState.Loaded) {
            val cached = albumDetailsRepository.getCachedAlbum(appleAlbumId)
            if (cached != null) {
                albumDetailsState = cached.toUiState()
            }
        }
        val fetched = albumDetailsRepository.getAlbum(appleAlbumId).toUiState()
        albumDetailsState = fetched
    }

    val albumTracks = remember(albumDetailsState) {
        (albumDetailsState as? AlbumDetailsUiState.Loaded)?.tracks.orEmpty()
    }
    val loadedAlbum = remember(albumDetailsState) {
        (albumDetailsState as? AlbumDetailsUiState.Loaded)?.album?.let { album ->
            listOf(
                HomeAlbum(
                    id = "apple_${album.id}",
                    title = album.name,
                    artist = album.artistName.orEmpty(),
                    artworkUrl = album.artworkUrl,
                    appleCatalogId = album.id,
                )
            )
        }.orEmpty()
    }
    LaunchedEffect(autoRipCoordinator, loadedAlbum) {
        autoRipCoordinator.observeAlbums(AutoRipSource.ALBUM_DETAILS, loadedAlbum)
    }
    DisposableEffect(autoRipCoordinator, appleAlbumId) {
        onDispose {
            autoRipCoordinator.observeAlbums(AutoRipSource.ALBUM_DETAILS, emptyList())
        }
    }

    if (ripConnection != null) {
        LaunchedEffect(ripConnection, appleAlbumId) {
            launch {
                ripConnection.completedRipTrackIds.collect { providerTrackId ->
                    val lookup = ripConnection.autoRip.refreshAvailabilityNow(providerTrackId)
                    val availability = lookup.cached
                    if (availability.isNotEmpty()) {
                        albumDetailsState = albumDetailsState.withAvailability(availability)
                    }
                }
            }
            launch {
                ripConnection.completedRipAlbumIds.collect { completedAlbumId ->
                    val loadedState = albumDetailsState as? AlbumDetailsUiState.Loaded
                    val currentAlbumId = loadedState?.album?.id ?: appleAlbumId
                    if (currentAlbumId == completedAlbumId) {
                        val refreshed =
                            albumDetailsRepository.refreshAlbum(completedAlbumId).toUiState()
                        if (refreshed is AlbumDetailsUiState.Loaded ||
                            albumDetailsState !is AlbumDetailsUiState.Loaded
                        ) {
                            albumDetailsState = refreshed
                        }
                    }
                }
            }
        }
    }

    val finalModifier = if (isTop) {
        modifier.liquidGlassBackdropProducer(
            liquidGlassBackdropState,
            liquidGlassBackdropLayer,
        )
    } else modifier

    AlbumDetailsScreen(
        state = albumDetailsState,
        bottomClearance = albumBottomClearance,
        isRefreshing = isRefreshingAlbum,
        currentTrackId = playbackState.currentTrack?.id,
        isPlaying = playbackState.isPlaying,
        onBack = onBack,
        onRetry = { albumLoadAttempt += 1 },
        onRefresh = {
            if (!isRefreshingAlbum) {
                autoRipCoordinator.invalidateAlbums(listOf(appleAlbumId))
                autoRipCoordinator.invalidate(
                    albumTracks.mapNotNull(HomeTrack::providerTrackId)
                )
                coroutineScope.launch {
                    isRefreshingAlbum = true
                    try {
                        val refreshed =
                            albumDetailsRepository.refreshAlbum(appleAlbumId).toUiState()
                        if (refreshed is AlbumDetailsUiState.Loaded ||
                            albumDetailsState !is AlbumDetailsUiState.Loaded
                        ) {
                            albumDetailsState = refreshed
                        }
                        autoRipCoordinator.requestScan()
                    } finally {
                        isRefreshingAlbum = false
                    }
                }
            }
        },
        onStartPlayback = { track, contextTracks ->
            playbackManager.play(track, contextTracks = contextTracks)
        },
        onPlayNext = { track -> playbackManager.playNext(track) },
        onAddToQueue = { track -> playbackManager.addToQueue(track) },
        onDownloadTrack = onDownloadTrack,
        isDownloadPending = isDownloadPending,
        onOpenAlbumVersion = onOpenAlbumVersion,
        onOpenArtist = onOpenArtist,
        onOpenRecordLabel = onOpenRecordLabel,
        modifier = finalModifier,
    )
}

@Composable
private fun ArtistOverlayHost(
    destination: ArtistDestination,
    artistDetailsRepository: ArtistDetailsRepository,
    albumDetailsRepository: AlbumDetailsRepository,
    playbackManager: PlaybackManager,
    playbackState: PlaybackState,
    autoRipCoordinator: AutoRipCoordinator,
    ripConnection: RipConnectionHolder?,
    albumBottomClearance: Dp,
    isTop: Boolean,
    liquidGlassBackdropState: LiquidGlassBackdropState,
    liquidGlassBackdropLayer: GraphicsLayer,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    isDownloadPending: (HomeTrack) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var artistLoadAttempt by remember(destination) { mutableIntStateOf(0) }
    var isRefreshingArtist by remember(destination) { mutableStateOf(false) }

    val cachedInitial = remember(destination) {
        val appleId = destination.appleArtistId
        if (appleId != null) {
            artistDetailsRepository.getCachedArtist(appleId)?.toUiState()
        } else null
    }

    var artistDetailsState by remember(destination) {
        mutableStateOf<ArtistDetailsUiState>(cachedInitial ?: ArtistDetailsUiState.Loading)
    }

    LaunchedEffect(destination, artistLoadAttempt) {
        val appleArtistId = destination.appleArtistId
            ?: artistDetailsRepository.resolveArtistId(destination.name)
        if (appleArtistId != null) {
            if (artistDetailsState !is ArtistDetailsUiState.Loaded) {
                val cached = artistDetailsRepository.getCachedArtist(appleArtistId)
                if (cached != null) {
                    artistDetailsState = cached.toUiState()
                }
            }
            val result = artistDetailsRepository.getArtist(appleArtistId)
            artistDetailsState = result.toUiState()
        } else {
            artistDetailsState = ArtistDetailsUiState.NotFound
        }
    }

    val topSongs = remember(artistDetailsState) {
        (artistDetailsState as? ArtistDetailsUiState.Loaded)?.topSongs.orEmpty()
    }
    LaunchedEffect(autoRipCoordinator, topSongs) {
        autoRipCoordinator.observe(AutoRipSource.ARTIST_DETAILS, topSongs)
    }
    DisposableEffect(autoRipCoordinator, destination) {
        onDispose {
            autoRipCoordinator.observe(AutoRipSource.ARTIST_DETAILS, emptyList())
        }
    }
    val allArtistAlbums = remember(artistDetailsState) {
        (artistDetailsState as? ArtistDetailsUiState.Loaded)?.let { loaded ->
            listOfNotNull(loaded.latestRelease) + loaded.albums + loaded.singles
        }.orEmpty()
    }

    if (ripConnection != null) {
        LaunchedEffect(ripConnection) {
            launch {
                ripConnection.completedRipTrackIds.collect { providerTrackId ->
                    val lookup = ripConnection.autoRip.refreshAvailabilityNow(providerTrackId)
                    val availability = lookup.cached
                    if (availability.isNotEmpty()) {
                        artistDetailsState = artistDetailsState.withAvailability(availability)
                    }
                }
            }
            launch {
                ripConnection.completedRipAlbumIds.collect { completedAlbumId ->
                    val loadedState = artistDetailsState as? ArtistDetailsUiState.Loaded
                    if (loadedState != null) {
                        val hasAlbum = allArtistAlbums.any {
                            it.appleCatalogId == completedAlbumId ||
                                    it.id == "apple_$completedAlbumId" ||
                                    it.id == completedAlbumId
                        }
                        if (hasAlbum) {
                            val appleArtistId = destination.appleArtistId
                                ?: artistDetailsRepository.resolveArtistId(destination.name)
                            if (appleArtistId != null) {
                                val refreshed = artistDetailsRepository
                                    .getArtist(appleArtistId, forceRefresh = true)
                                    .toUiState()
                                if (refreshed is ArtistDetailsUiState.Loaded) {
                                    artistDetailsState = refreshed
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val finalModifier = if (isTop) {
        modifier.liquidGlassBackdropProducer(
            liquidGlassBackdropState,
            liquidGlassBackdropLayer,
        )
    } else modifier

    ArtistDetailsScreen(
        state = artistDetailsState,
        bottomClearance = albumBottomClearance,
        isRefreshing = isRefreshingArtist,
        currentTrackId = playbackState.currentTrack?.id,
        isPlaying = playbackState.isPlaying,
        onBack = onBack,
        onRetry = { artistLoadAttempt += 1 },
        onRefresh = {
            if (!isRefreshingArtist) {
                autoRipCoordinator.invalidate(
                    topSongs.mapNotNull(HomeTrack::providerTrackId)
                )
                coroutineScope.launch {
                    isRefreshingArtist = true
                    try {
                        val appleArtistId = destination.appleArtistId
                            ?: artistDetailsRepository.resolveArtistId(destination.name)
                        if (appleArtistId != null) {
                            val refreshed = artistDetailsRepository
                                .getArtist(appleArtistId, forceRefresh = true)
                                .toUiState()
                            artistDetailsState = refreshed
                            autoRipCoordinator.requestScan()
                        }
                    } finally {
                        isRefreshingArtist = false
                    }
                }
            }
        },
        onStartPlayback = { track, contextTracks ->
            playbackManager.play(track, contextTracks = contextTracks)
        },
        onPlayNext = { track -> playbackManager.playNext(track) },
        onAddToQueue = { track -> playbackManager.addToQueue(track) },
        onDownloadTrack = onDownloadTrack,
        isDownloadPending = isDownloadPending,
        onOpenAlbum = onOpenAlbum,
        onOpenTrackAlbum = { track ->
            coroutineScope.launch {
                val albumId = albumDetailsRepository.resolveAlbumIdForTrack(track)
                if (albumId != null) {
                    onOpenAlbum(albumId)
                }
            }
        },
        onOpenArtist = onOpenArtist,
        modifier = finalModifier,
    )
}

@Composable
private fun RecordLabelOverlayHost(
    destination: RecordLabelDestination,
    recordLabelRepository: RecordLabelRepository,
    autoRipCoordinator: AutoRipCoordinator,
    albumBottomClearance: Dp,
    isTop: Boolean,
    liquidGlassBackdropState: LiquidGlassBackdropState,
    liquidGlassBackdropLayer: GraphicsLayer,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var labelLoadAttempt by remember(destination) { mutableIntStateOf(0) }
    var isRefreshingLabel by remember(destination) { mutableStateOf(false) }

    val cachedInitial = remember(destination) {
        val key = destination.appleLabelId ?: destination.name
        recordLabelRepository.getCachedRecordLabel(key)?.toUiState()
    }

    var recordLabelState by remember(destination) {
        mutableStateOf<RecordLabelUiState>(cachedInitial ?: RecordLabelUiState.Loading)
    }

    val allReleases = remember(recordLabelState) {
        (recordLabelState as? RecordLabelUiState.Loaded)?.let { loaded ->
            (loaded.latestReleases + loaded.topReleases).distinctBy { it.id }
        }.orEmpty()
    }

    LaunchedEffect(autoRipCoordinator, allReleases) {
        if (allReleases.isNotEmpty()) {
            autoRipCoordinator.observeAlbums(AutoRipSource.RECORD_LABEL, allReleases)
        }
    }

    DisposableEffect(autoRipCoordinator, destination) {
        onDispose {
            autoRipCoordinator.observeAlbums(AutoRipSource.RECORD_LABEL, emptyList())
        }
    }

    LaunchedEffect(destination, labelLoadAttempt) {
        val key = destination.appleLabelId ?: destination.name
        if (recordLabelState !is RecordLabelUiState.Loaded) {
            val cached = recordLabelRepository.getCachedRecordLabel(key)
            if (cached != null) {
                recordLabelState = cached.toUiState()
            }
        }
        val label = recordLabelRepository.getRecordLabel(key)
        if (label != null) {
            recordLabelState = label.toUiState()
        } else {
            recordLabelState = RecordLabelUiState.NotFound
        }
    }

    val finalModifier = if (isTop) {
        modifier.liquidGlassBackdropProducer(
            liquidGlassBackdropState,
            liquidGlassBackdropLayer,
        )
    } else modifier

    RecordLabelScreen(
        state = recordLabelState,
        bottomClearance = albumBottomClearance,
        isRefreshing = isRefreshingLabel,
        onBack = onBack,
        onRetry = { labelLoadAttempt += 1 },
        onRefresh = {
            if (!isRefreshingLabel) {
                coroutineScope.launch {
                    isRefreshingLabel = true
                    try {
                        val albumCatalogIds = allReleases.mapNotNull { it.appleCatalogId }
                        if (albumCatalogIds.isNotEmpty()) {
                            autoRipCoordinator.invalidateAlbums(albumCatalogIds)
                            autoRipCoordinator.requestScan()
                        }
                        val key = destination.appleLabelId ?: destination.name
                        val refreshed =
                            recordLabelRepository.getRecordLabel(key, forceRefresh = true)
                        if (refreshed != null) {
                            recordLabelState = refreshed.toUiState()
                        }
                    } finally {
                        isRefreshingLabel = false
                    }
                }
            }
        },
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onPlayReleases = { releases ->
            releases.firstOrNull()?.let { album ->
                onOpenAlbum(album.appleCatalogId ?: album.id)
            }
        },
        onShuffleReleases = { releases ->
            releases.shuffled().firstOrNull()?.let { album ->
                onOpenAlbum(album.appleCatalogId ?: album.id)
            }
        },
        modifier = finalModifier,
    )
}
