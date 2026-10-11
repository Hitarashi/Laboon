@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlin.math.PI
import kotlin.math.sin
import coil3.compose.AsyncImage
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.home.HomeFeedCache
import org.shilpo.laboon.home.HomeFeedRepository
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.ListenBrainzPlaylist
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.home.YouTubePlaylistImportResult
import org.shilpo.laboon.home.YouTubePlaylistSkippedTrack
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.FloatingNavBarClearance
import org.shilpo.laboon.ui.design.rememberArtistArtworkShape
import org.shilpo.laboon.ui.design.ScreenScaffold
import org.shilpo.laboon.ui.design.UserAvatar
import org.shilpo.laboon.ui.design.theme.RoundedSans
import org.shilpo.laboon.ui.screens.home.HomeDailyMixSongOptionsSheet
import org.shilpo.laboon.ui.screens.home.HomeDailyMixTrackRow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class LibraryCategory(
    val id: String,
    val title: Int,
    val icon: String,
)

private data class LibraryEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
    val icon: String = "music_note",
    val track: HomeTrack? = null,
    val album: HomeAlbum? = null,
    val artist: HomeArtist? = null,
    val playlist: ListenBrainzPlaylist? = null,
)

private data class LibraryPageState(
    val items: List<LibraryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)

private val InitialLibraryCategories = listOf(
    LibraryCategory("songs", R.string.library_category_songs, "music_note"),
    LibraryCategory("albums", R.string.library_category_albums, "album"),
    LibraryCategory("artists", R.string.library_category_artists, "person"),
    LibraryCategory("playlists", R.string.library_category_playlists, "playlist_play"),
    LibraryCategory("recent", R.string.library_category_recent, "history"),
    LibraryCategory("loved", R.string.library_category_loved, "favorite"),
)

private val LibraryCategoryTitleStyle = TextStyle(
    fontFamily = RoundedSans,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 23.5.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.2).sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

private val LibraryHeaderSplitButtonHeight = 52.dp

private enum class LibrarySort(val label: Int) {
    RECENT(R.string.library_sort_recent),
    TITLE(R.string.library_sort_title),
    ARTIST(R.string.library_sort_artist),
}

private fun sortSongLibraryEntries(
    entries: List<LibraryEntry>,
    option: SongsLibrarySortOption,
    descending: Boolean,
): List<LibraryEntry> {
    if (option == SongsLibrarySortOption.DEFAULT_ORDER) return entries
    if (option == SongsLibrarySortOption.DURATION) {
        val knownDurations = entries.filter { it.track?.durationMs != null }
        val unknownDurations = entries.filter { it.track?.durationMs == null }
        val sortedKnown = if (descending) {
            knownDurations.sortedByDescending { it.track?.durationMs }
        } else {
            knownDurations.sortedBy { it.track?.durationMs }
        }
        return sortedKnown + unknownDurations
    }

    return when (option) {
        SongsLibrarySortOption.DEFAULT_ORDER -> entries
        SongsLibrarySortOption.TITLE -> if (descending) {
            entries.sortedByDescending { it.title.lowercase() }
        } else {
            entries.sortedBy { it.title.lowercase() }
        }
        SongsLibrarySortOption.ARTIST -> if (descending) {
            entries.sortedByDescending { (it.track?.artist ?: it.subtitle).lowercase() }
        } else {
            entries.sortedBy { (it.track?.artist ?: it.subtitle).lowercase() }
        }
        SongsLibrarySortOption.ALBUM -> if (descending) {
            entries.sortedByDescending { it.track?.album.orEmpty().lowercase() }
        } else {
            entries.sortedBy { it.track?.album.orEmpty().lowercase() }
        }
        SongsLibrarySortOption.DURATION -> entries
    }
}

@Composable
fun LibraryScreen(
    session: AuthSession?,
    repository: HomeFeedRepository,
    librarySongsCache: HomeFeedCache,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onPlayTrack: (HomeTrack, List<HomeTrack>) -> Unit = { _, _ -> },
    currentTrack: HomeTrack? = null,
    isPlaying: Boolean = false,
    onPlayNextTrack: (HomeTrack) -> Unit = {},
    onAddToQueueTrack: (HomeTrack) -> Unit = {},
    onLoadTrackGenres: suspend (HomeTrack) -> List<String> = { emptyList() },
    onOpenAlbum: (HomeAlbum) -> Unit = {},
    onOpenArtist: (HomeArtist) -> Unit = {},
    onShuffleAlbum: (HomeAlbum) -> Unit = {},
    onShuffleArtist: (HomeArtist) -> Unit = {},
) {
    var categories by remember { mutableStateOf(InitialLibraryCategories) }
    val pagerState = rememberPagerState(pageCount = { categories.size })
    val scope = rememberCoroutineScope()
    var showTabSwitcher by rememberSaveable { mutableStateOf(false) }
    var showReorderTabs by rememberSaveable { mutableStateOf(false) }
    var showSongsSortSheet by rememberSaveable { mutableStateOf(false) }
    var isGridLayout by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENT) }
    var songsSortOption by rememberSaveable { mutableStateOf(SongsLibrarySortOption.DEFAULT_ORDER) }
    var songsSortDescending by rememberSaveable { mutableStateOf(false) }
    var locateRequest by remember { mutableIntStateOf(0) }
    var locateTargetPageKey by remember { mutableStateOf<String?>(null) }
    var locateVisibilityByPage by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var refreshLibraryRequest by rememberSaveable { mutableIntStateOf(0) }
    var refreshCategoryOnRequest by remember { mutableStateOf<String?>(null) }
    var refreshPlaylistRequest by rememberSaveable { mutableIntStateOf(0) }
    val cachedPageStates = remember(librarySongsCache) {
        buildMap {
            librarySongsCache.loadLibrarySongs()
                ?.takeIf { it.isNotEmpty() }
                ?.let { put("songs", LibraryPageState(items = it.map(HomeTrack::toLibraryEntry))) }
            librarySongsCache.loadLibraryAlbums()
                ?.takeIf { it.isNotEmpty() }
                ?.let { put("albums", LibraryPageState(items = it.map(HomeAlbum::toLibraryEntry))) }
            librarySongsCache.loadLibraryArtists()
                ?.takeIf { it.isNotEmpty() }
                ?.let { put("artists", LibraryPageState(items = it.map(HomeArtist::toLibraryEntry))) }
            librarySongsCache.loadLibraryPlaylists()
                ?.takeIf { it.isNotEmpty() }
                ?.let { put("playlists", LibraryPageState(items = it.map(ListenBrainzPlaylist::toLibraryEntry))) }
            librarySongsCache.loadLibraryRecentTracks()
                ?.takeIf { it.isNotEmpty() }
                ?.let { put("recent", LibraryPageState(items = it.map(HomeTrack::toLibraryEntry))) }
            librarySongsCache.loadLibraryLovedTracks()
                ?.takeIf { it.isNotEmpty() }
                ?.let { put("loved", LibraryPageState(items = it.map(HomeTrack::toLibraryEntry))) }
        }
    }
    var pageStates by remember(librarySongsCache) { mutableStateOf(cachedPageStates) }
    var selectedPlaylist by remember { mutableStateOf<ListenBrainzPlaylist?>(null) }
    var selectedPlaylistIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var playlistDetailState by remember { mutableStateOf(LibraryPageState(isLoading = false)) }
    fun openPlaylist(playlist: ListenBrainzPlaylist) {
        val cachedTracks = librarySongsCache.loadLibraryPlaylistTracks(playlist.id)
        playlistDetailState = LibraryPageState(
            items = cachedTracks.orEmpty().map(HomeTrack::toLibraryEntry),
            isLoading = cachedTracks.isNullOrEmpty(),
        )
        selectedPlaylist = playlist
    }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var isCreatingPlaylist by remember { mutableStateOf(false) }
    var showImportYouTubePlaylist by rememberSaveable { mutableStateOf(false) }
    var youtubePlaylistUrl by rememberSaveable { mutableStateOf("") }
    var isImportingYouTubePlaylist by remember { mutableStateOf(false) }
    var youtubePlaylistImportReport by remember {
        mutableStateOf<YouTubePlaylistImportResult.Completed?>(null)
    }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    var playlistOptions by remember { mutableStateOf<List<ListenBrainzPlaylist>>(emptyList()) }
    var isLoadingPlaylistOptions by remember { mutableStateOf(false) }
    var playlistsToDelete by remember { mutableStateOf<List<ListenBrainzPlaylist>?>(null) }
    var isDeletingPlaylist by remember { mutableStateOf(false) }
    var showPlaylistSelectionOptions by remember { mutableStateOf(false) }
    var trackToAddToPlaylist by remember { mutableStateOf<HomeTrack?>(null) }
    var selectedTrackForOptions by remember { mutableStateOf<HomeTrack?>(null) }
    var selectedTrackOptionsQueue by remember { mutableStateOf<List<HomeTrack>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }

    val activeCategory = categories.getOrNull(pagerState.currentPage) ?: categories.first()
    val activePlaylist = selectedPlaylist.takeIf { activeCategory.id == "playlists" }
    BackHandler(enabled = activePlaylist != null) {
        selectedPlaylist = null
    }
    BackHandler(
        enabled = activeCategory.id == "playlists" &&
            activePlaylist == null &&
            selectedPlaylistIds.isNotEmpty(),
    ) {
        selectedPlaylistIds = emptyList()
    }
    LaunchedEffect(activeCategory.id) {
        if (activeCategory.id != "playlists") selectedPlaylistIds = emptyList()
    }
    val allPlaylists = pageStates["playlists"]?.items.orEmpty().mapNotNull(LibraryEntry::playlist)
    val selectedPlaylists = remember(allPlaylists, selectedPlaylistIds) {
        selectedPlaylistIds.mapNotNull { id -> allPlaylists.firstOrNull { it.id == id } }
    }
    val isPlaylistSelectionMode = activeCategory.id == "playlists" &&
        activePlaylist == null && selectedPlaylists.isNotEmpty()
    val hapticFeedback = LocalHapticFeedback.current
    val activePageKey = activePlaylist?.let { "playlist:${it.id}" } ?: activeCategory.id
    val activePageSupportsLocate = activeCategory.id in setOf("songs", "recent", "loved") ||
        activePlaylist != null
    val showLocateButton = activePageSupportsLocate &&
        locateVisibilityByPage[activePageKey] == true
    val activePageState = pageStates[activeCategory.id] ?: LibraryPageState(isLoading = true)
    val activeItems = if (activePlaylist != null) {
        playlistDetailState.items
    } else {
        activePageState.items
    }
    val headerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomPadding = FloatingNavBarClearance + navBarInset + 20.dp
    val context = LocalContext.current

    LaunchedEffect(activeCategory.id, refreshLibraryRequest) {
        val categoryId = activeCategory.id
        if (categoryId != "playlists") selectedPlaylist = null
        val previous = pageStates[categoryId]
        val isExplicitRefresh = refreshCategoryOnRequest == categoryId
        if (previous == null) {
            pageStates = pageStates + (categoryId to LibraryPageState(isLoading = true))
        } else if (isExplicitRefresh) {
            pageStates = pageStates + (categoryId to previous.copy(
                isLoading = previous.items.isEmpty(),
                isRefreshing = previous.items.isNotEmpty(),
            ))
        }
        val loadedItems = try {
            fetchLibraryCategory(repository, categoryId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        if (loadedItems.isNotEmpty()) {
            when (categoryId) {
                "songs" -> librarySongsCache.saveLibrarySongs(loadedItems.mapNotNull(LibraryEntry::track))
                "albums" -> librarySongsCache.saveLibraryAlbums(loadedItems.mapNotNull(LibraryEntry::album))
                "artists" -> librarySongsCache.saveLibraryArtists(loadedItems.mapNotNull(LibraryEntry::artist))
                "playlists" -> librarySongsCache.saveLibraryPlaylists(
                    loadedItems.mapNotNull(LibraryEntry::playlist),
                )
                "recent" -> librarySongsCache.saveLibraryRecentTracks(
                    loadedItems.mapNotNull(LibraryEntry::track),
                )
                "loved" -> librarySongsCache.saveLibraryLovedTracks(
                    loadedItems.mapNotNull(LibraryEntry::track),
                )
            }
        }
        val visibleItems = previous?.items
            ?.takeIf { loadedItems.isEmpty() && it.isNotEmpty() }
            ?: loadedItems
        pageStates = pageStates + (categoryId to LibraryPageState(items = visibleItems))
        if (categoryId == "playlists" && visibleItems.isNotEmpty()) {
            val visiblePlaylistIds = visibleItems
                .mapNotNull(LibraryEntry::playlist)
                .map(ListenBrainzPlaylist::id)
                .toSet()
            selectedPlaylistIds = selectedPlaylistIds.filter(visiblePlaylistIds::contains)
        }
        if (isExplicitRefresh) refreshCategoryOnRequest = null
    }

    LaunchedEffect(activePageKey) {
        if (locateTargetPageKey != activePageKey) locateTargetPageKey = null
    }

    LaunchedEffect(activePlaylist?.id, refreshPlaylistRequest) {
        val playlist = activePlaylist ?: run {
            playlistDetailState = LibraryPageState()
            return@LaunchedEffect
        }
        val previous = playlistDetailState
        playlistDetailState = previous.copy(
            isLoading = previous.items.isEmpty(),
            isRefreshing = previous.items.isNotEmpty(),
        )
        val loadedTracks = try {
            repository.fetchListenBrainzPlaylistTracks(playlist.id)
                .map(HomeTrack::toLibraryEntry)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val freshTracks = loadedTracks.mapNotNull(LibraryEntry::track)
        if (freshTracks.isNotEmpty()) {
            librarySongsCache.saveLibraryPlaylistTracks(playlist.id, freshTracks)
        }
        playlistDetailState = LibraryPageState(
            items = if (loadedTracks.isEmpty() && previous.items.isNotEmpty()) {
                previous.items
            } else {
                loadedTracks
            },
        )
    }

    LaunchedEffect(showPlaylistPicker) {
        if (!showPlaylistPicker) return@LaunchedEffect
        isLoadingPlaylistOptions = true
        val cachedPlaylists = pageStates["playlists"]?.items?.mapNotNull(LibraryEntry::playlist)
        playlistOptions = if (cachedPlaylists != null) {
            cachedPlaylists
        } else {
            try {
                repository.fetchListenBrainzPlaylists().also { playlists ->
                    if (playlists.isNotEmpty()) librarySongsCache.saveLibraryPlaylists(playlists)
                    pageStates = pageStates + ("playlists" to LibraryPageState(
                        items = playlists.map(ListenBrainzPlaylist::toLibraryEntry),
                    ))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }
        isLoadingPlaylistOptions = false
    }

    ScreenScaffold(
        modifier = modifier,
        background = headerColor,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(headerColor),
            ) {
                LibraryTopBar(
                    title = activePlaylist?.title ?: stringResource(activeCategory.title),
                    titleIcon = activePlaylist?.let { "playlist_play" } ?: activeCategory.icon,
                    isTabSwitcherOpen = showTabSwitcher,
                    isPlaylistDetail = activePlaylist != null,
                    session = session,
                    modifier = Modifier.statusBarsPadding(),
                    onBack = { selectedPlaylist = null },
                    onTabSwitcherChange = { showTabSwitcher = it },
                    onOpenSettings = onOpenSettings,
                )

                LibraryPageIndicator(
                    currentPage = pagerState.currentPage,
                    pageCount = categories.size,
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = isPlaylistSelectionMode,
                            label = "playlistActionModeSwitch",
                            transitionSpec = {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { inSelectionMode ->
                            if (inSelectionMode) {
                                PlaylistSelectionActionRow(
                                    selectedCount = selectedPlaylists.size,
                                    allCount = allPlaylists.size,
                                    onSelectAll = {
                                        selectedPlaylistIds = allPlaylists.map(ListenBrainzPlaylist::id)
                                    },
                                    onDeselect = { selectedPlaylistIds = emptyList() },
                                    onOptions = { showPlaylistSelectionOptions = true },
                                )
                            } else {
                                LibraryActionRow(
                                    isPlaylistTab = activeCategory.id == "playlists" && activePlaylist == null,
                                    isSongsTab = activeCategory.id == "songs" && activePlaylist == null,
                                    isGridLayout = isGridLayout,
                                    sort = sort,
                                    showLocateButton = showLocateButton,
                                    onShuffle = {
                                        when (activeCategory.id) {
                                            "albums" -> activeItems.mapNotNull(LibraryEntry::album)
                                                .randomOrNull()?.let(onShuffleAlbum)
                                            "artists" -> activeItems.mapNotNull(LibraryEntry::artist)
                                                .randomOrNull()?.let(onShuffleArtist)
                                            else -> {
                                                val tracks = activeItems.mapNotNull(LibraryEntry::track)
                                                if (tracks.isNotEmpty()) {
                                                    val shuffled = tracks.shuffled()
                                                    onPlayTrack(shuffled.first(), shuffled)
                                                }
                                            }
                                        }
                                    },
                                    onNewPlaylist = { showCreatePlaylist = true },
                                    onImportPlaylist = { showImportYouTubePlaylist = true },
                                    onLocate = {
                                        locateTargetPageKey = activePageKey
                                        locateRequest++
                                    },
                                    onToggleLayout = { isGridLayout = !isGridLayout },
                                    onSort = { sort = it },
                                    onOpenSongsSortSheet = { showSongsSortSheet = true },
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                key = { page -> categories[page].id },
                            ) { page ->
                            val category = categories[page]
                            val pageState = pageStates[category.id]
                                ?: LibraryPageState(isLoading = category.id == activeCategory.id)
                            val isShowingPlaylistDetail =
                                category.id == "playlists" && activePlaylist != null
                            val pageKey = if (isShowingPlaylistDetail) {
                                "playlist:${activePlaylist.id}"
                            } else {
                                category.id
                            }
                            val items = if (isShowingPlaylistDetail) {
                                playlistDetailState.items
                            } else {
                                pageState.items
                            }
                            LibraryCategoryPage(
                                items = items,
                                isActivePage = pageKey == activePageKey,
                                isLoading = if (isShowingPlaylistDetail) {
                                    playlistDetailState.isLoading
                                } else {
                                    pageState.isLoading
                                },
                                isRefreshing = if (isShowingPlaylistDetail) {
                                    playlistDetailState.isRefreshing
                                } else {
                                    pageState.isRefreshing
                                },
                                emptyMessage = when {
                                    isShowingPlaylistDetail -> R.string.library_playlist_no_tracks
                                    category.id == "playlists" &&
                                            !repository.canManageListenBrainzPlaylists() ->
                                        R.string.library_playlist_connect_listenbrainz
                                    category.id == "playlists" -> R.string.library_playlist_empty
                                    else -> R.string.library_empty
                                },
                                isGridLayout = isGridLayout,
                                isPlaylistSelectionMode = isPlaylistSelectionMode,
                                selectedPlaylistIds = selectedPlaylistIds,
                                sort = sort,
                                isSongsTab = category.id == "songs" && !isShowingPlaylistDetail,
                                songsSortOption = songsSortOption,
                                songsSortDescending = songsSortDescending,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                locateRequest = locateRequest.takeIf {
                                    pageKey == locateTargetPageKey
                                } ?: 0,
                                contentBottomPadding = listBottomPadding,
                                emptyStateBottomPadding = FloatingNavBarClearance + navBarInset,
                                onItemClick = { entry ->
                                    when {
                                        entry.track != null -> onPlayTrack(
                                            entry.track,
                                            items.mapNotNull(LibraryEntry::track),
                                        )
                                        entry.album != null -> onOpenAlbum(entry.album)
                                        entry.artist != null -> onOpenArtist(entry.artist)
                                        entry.playlist != null -> openPlaylist(entry.playlist)
                                    }
                                },
                                onTrackMoreOptions = { track, queue ->
                                    selectedTrackForOptions = track
                                    selectedTrackOptionsQueue = queue
                                },
                                onDeletePlaylist = { playlistsToDelete = listOf(it) },
                                onPlaylistLongPress = { playlist ->
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedPlaylistIds = if (playlist.id in selectedPlaylistIds) {
                                        selectedPlaylistIds.filterNot { it == playlist.id }
                                    } else {
                                        selectedPlaylistIds + playlist.id
                                    }
                                },
                                onPlaylistSelectionToggle = { playlist ->
                                    selectedPlaylistIds = if (playlist.id in selectedPlaylistIds) {
                                        selectedPlaylistIds.filterNot { it == playlist.id }
                                    } else {
                                        selectedPlaylistIds + playlist.id
                                    }
                                },
                                onRefresh = {
                                    if (isShowingPlaylistDetail) {
                                        refreshPlaylistRequest++
                                    } else {
                                        refreshCategoryOnRequest = category.id
                                        refreshLibraryRequest++
                                    }
                                },
                                onLocateVisibilityChanged = { visible ->
                                    val supportsLocate = category.id in setOf(
                                        "songs",
                                        "recent",
                                        "loved",
                                    ) || isShowingPlaylistDetail
                                    if (pageKey == activePageKey && supportsLocate) {
                                        locateVisibilityByPage = locateVisibilityByPage +
                                            (pageKey to visible)
                                    }
                                },
                                onLocateNotFound = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.library_locate_not_found),
                                        )
                                    }
                                },
                                onLocateHandled = {
                                    if (locateTargetPageKey == pageKey) locateTargetPageKey = null
                                },
                                onRetry = {
                                    if (isShowingPlaylistDetail) {
                                        refreshPlaylistRequest++
                                    } else {
                                        refreshCategoryOnRequest = category.id
                                        refreshLibraryRequest++
                                    }
                                },
                            )
                            }
                            PlaylistSelectionCountPill(
                                selectedCount = selectedPlaylists.size.takeIf { isPlaylistSelectionMode } ?: 0,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = listBottomPadding),
            )
        }
    }

    if (showTabSwitcher) {
        LibraryTabsBottomSheet(
            categories = categories,
            selectedIndex = pagerState.currentPage,
            onSelect = { index ->
                showTabSwitcher = false
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            onEditClick = {
                showTabSwitcher = false
                showReorderTabs = true
            },
            onDismiss = { showTabSwitcher = false },
        )
    }

    if (showReorderTabs) {
        LibraryReorderTabsSheet(
            categories = categories,
            onReorder = { reordered ->
                val selectedId = categories.getOrNull(pagerState.currentPage)?.id
                categories = reordered
                val selectedAfterMove = reordered.indexOfFirst { it.id == selectedId }
                if (selectedAfterMove >= 0) {
                    scope.launch { pagerState.scrollToPage(selectedAfterMove) }
                }
            },
            onReset = {
                val selectedId = categories.getOrNull(pagerState.currentPage)?.id
                categories = InitialLibraryCategories
                val selectedAfterReset = categories.indexOfFirst { it.id == selectedId }
                if (selectedAfterReset >= 0) {
                    scope.launch { pagerState.scrollToPage(selectedAfterReset) }
                }
            },
            onDismiss = { showReorderTabs = false },
        )
    }

    playlistsToDelete?.let { pendingPlaylists ->
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingPlaylist) playlistsToDelete = null
            },
            title = {
                Text(
                    stringResource(
                        if (pendingPlaylists.size == 1) {
                            R.string.library_playlist_delete_title
                        } else {
                            R.string.library_playlist_delete_multiple_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    if (pendingPlaylists.size == 1) {
                        stringResource(
                            R.string.library_playlist_delete_message,
                            pendingPlaylists.single().title,
                        )
                    } else {
                        stringResource(
                            R.string.library_playlist_delete_multiple_message,
                            pendingPlaylists.size,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingPlaylist,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = {
                        isDeletingPlaylist = true
                        scope.launch {
                            val deletedIds = mutableSetOf<String>()
                            var failedCount = 0
                            try {
                                pendingPlaylists.forEach { playlist ->
                                    val deleted = try {
                                        repository.deleteListenBrainzPlaylist(playlist.id)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        false
                                    }
                                    if (deleted) deletedIds += playlist.id else failedCount++
                                }
                            } catch (cancelled: CancellationException) {
                                isDeletingPlaylist = false
                                throw cancelled
                            }
                            isDeletingPlaylist = false
                            playlistsToDelete = null
                            if (deletedIds.isNotEmpty()) {
                                val remainingEntries = pageStates["playlists"]?.items.orEmpty()
                                    .filterNot { it.id in deletedIds }
                                pageStates = pageStates + ("playlists" to LibraryPageState(
                                    items = remainingEntries,
                                ))
                                playlistOptions = playlistOptions.filterNot { it.id in deletedIds }
                                librarySongsCache.saveLibraryPlaylists(
                                    remainingEntries.mapNotNull(LibraryEntry::playlist),
                                )
                                deletedIds.forEach(librarySongsCache::removeLibraryPlaylistTracks)
                                selectedPlaylistIds = selectedPlaylistIds.filterNot(deletedIds::contains)
                                if (selectedPlaylist?.id?.let(deletedIds::contains) == true) {
                                    selectedPlaylist = null
                                }
                            }
                            when {
                                failedCount == 0 && pendingPlaylists.size == 1 ->
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.library_playlist_deleted,
                                            pendingPlaylists.single().title,
                                        ),
                                    )
                                failedCount == 0 || deletedIds.isNotEmpty() ->
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.library_playlist_delete_multiple_result,
                                            deletedIds.size,
                                            failedCount,
                                        ),
                                    )
                                else -> snackbarHostState.showSnackbar(
                                    context.getString(R.string.library_playlist_delete_failed),
                                )
                            }
                        }
                    },
                ) {
                    if (isDeletingPlaylist) {
                        ContainedLoadingIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(R.string.library_playlist_delete))
                    }
                }
            },
            dismissButton = {
                TextButton(
                enabled = !isDeletingPlaylist,
                    onClick = { playlistsToDelete = null },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showPlaylistSelectionOptions && selectedPlaylists.isNotEmpty()) {
        PlaylistSelectionOptionsSheet(
            playlists = selectedPlaylists,
            onDismiss = { showPlaylistSelectionOptions = false },
            onDelete = {
                showPlaylistSelectionOptions = false
                playlistsToDelete = selectedPlaylists
            },
        )
    }

    if (showCreatePlaylist) {
        val canManagePlaylists = repository.canManageListenBrainzPlaylists()
        AlertDialog(
            onDismissRequest = {
                if (!isCreatingPlaylist) showCreatePlaylist = false
            },
            title = { Text(stringResource(R.string.library_playlist_create_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canManagePlaylists) {
                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            label = { Text(stringResource(R.string.library_playlist_name)) },
                            singleLine = true,
                            enabled = !isCreatingPlaylist,
                        )
                    } else {
                        Text(stringResource(R.string.library_playlist_connect_listenbrainz))
                    }
                }
            },
            confirmButton = {
                if (canManagePlaylists) {
                    TextButton(
                        enabled = playlistName.isNotBlank() && !isCreatingPlaylist,
                        onClick = {
                            isCreatingPlaylist = true
                            scope.launch {
                                val created = try {
                                    repository.createListenBrainzPlaylist(playlistName)
                                } catch (cancelled: CancellationException) {
                                    isCreatingPlaylist = false
                                    throw cancelled
                                } catch (_: Exception) {
                                    null
                                }
                                isCreatingPlaylist = false
                                if (created == null) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.library_playlist_create_failed),
                                    )
                                } else {
                                    val entry = created.toLibraryEntry()
                                    val existing = pageStates["playlists"]?.items.orEmpty()
                                    val updatedPlaylists = listOf(entry) + existing.filterNot { it.id == entry.id }
                                    pageStates = pageStates + ("playlists" to LibraryPageState(
                                        items = updatedPlaylists,
                                    ))
                                    librarySongsCache.saveLibraryPlaylists(
                                        updatedPlaylists.mapNotNull(LibraryEntry::playlist),
                                    )
                                    playlistName = ""
                                    showCreatePlaylist = false
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.library_playlist_created,
                                            created.title,
                                        ),
                                    )
                                }
                            }
                        },
                    ) {
                        if (isCreatingPlaylist) {
                            ContainedLoadingIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            Text(stringResource(R.string.library_playlist_create))
                        }
                    }
                } else {
                    TextButton(
                        onClick = {
                            showCreatePlaylist = false
                            onOpenSettings()
                        },
                    ) {
                        Text(stringResource(R.string.library_playlist_open_settings))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isCreatingPlaylist,
                    onClick = { showCreatePlaylist = false },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showImportYouTubePlaylist) {
        val canManagePlaylists = repository.canManageListenBrainzPlaylists()
        AlertDialog(
            onDismissRequest = {
                if (!isImportingYouTubePlaylist) showImportYouTubePlaylist = false
            },
            title = { Text(stringResource(R.string.library_playlist_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canManagePlaylists) {
                        OutlinedTextField(
                            value = youtubePlaylistUrl,
                            onValueChange = { youtubePlaylistUrl = it },
                            label = { Text(stringResource(R.string.library_playlist_import_url)) },
                            singleLine = true,
                            enabled = !isImportingYouTubePlaylist,
                        )
                        Text(stringResource(R.string.library_playlist_import_description))
                        if (isImportingYouTubePlaylist) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ContainedLoadingIndicator(modifier = Modifier.size(20.dp))
                                Text(stringResource(R.string.library_playlist_importing))
                            }
                        }
                    } else {
                        Text(stringResource(R.string.library_playlist_connect_listenbrainz))
                    }
                }
            },
            confirmButton = {
                if (canManagePlaylists) {
                    TextButton(
                        enabled = youtubePlaylistUrl.isNotBlank() && !isImportingYouTubePlaylist,
                        onClick = {
                            isImportingYouTubePlaylist = true
                            scope.launch {
                                val result = try {
                                    repository.importPublicYouTubePlaylist(youtubePlaylistUrl)
                                } catch (cancelled: CancellationException) {
                                    isImportingYouTubePlaylist = false
                                    throw cancelled
                                } catch (_: Exception) {
                                    YouTubePlaylistImportResult.PlaylistUnavailable
                                }
                                isImportingYouTubePlaylist = false
                                when (result) {
                                    is YouTubePlaylistImportResult.Completed -> {
                                        result.playlist?.let { playlist ->
                                            val entry = playlist.toLibraryEntry()
                                            val existing = pageStates["playlists"]?.items.orEmpty()
                                            val updatedPlaylists = listOf(entry) + existing.filterNot { it.id == entry.id }
                                            pageStates = pageStates + ("playlists" to LibraryPageState(
                                                items = updatedPlaylists,
                                            ))
                                            librarySongsCache.saveLibraryPlaylists(
                                                updatedPlaylists.mapNotNull(LibraryEntry::playlist),
                                            )
                                        }
                                        youtubePlaylistUrl = ""
                                        showImportYouTubePlaylist = false
                                        youtubePlaylistImportReport = result
                                    }

                                    YouTubePlaylistImportResult.ListenBrainzNotConnected -> {
                                        showImportYouTubePlaylist = false
                                        onOpenSettings()
                                    }

                                    YouTubePlaylistImportResult.InvalidYouTubeLink ->
                                        snackbarHostState.showSnackbar(context.getString(R.string.library_playlist_import_invalid_link))

                                    YouTubePlaylistImportResult.PlaylistUnavailable ->
                                        snackbarHostState.showSnackbar(context.getString(R.string.library_playlist_import_unavailable))

                                    YouTubePlaylistImportResult.PlaylistHasNoTracks ->
                                        snackbarHostState.showSnackbar(context.getString(R.string.library_playlist_import_no_tracks))

                                    YouTubePlaylistImportResult.CouldNotCreatePlaylist ->
                                        snackbarHostState.showSnackbar(context.getString(R.string.library_playlist_import_create_failed))
                                }
                            }
                        },
                    ) {
                        if (isImportingYouTubePlaylist) {
                            ContainedLoadingIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            Text(stringResource(R.string.library_playlist_import))
                        }
                    }
                } else {
                    TextButton(
                        onClick = {
                            showImportYouTubePlaylist = false
                            onOpenSettings()
                        },
                    ) {
                        Text(stringResource(R.string.library_playlist_open_settings))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isImportingYouTubePlaylist,
                    onClick = { showImportYouTubePlaylist = false },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    youtubePlaylistImportReport?.let { report ->
        YouTubePlaylistImportResultsDialog(
            report = report,
            onDismiss = { youtubePlaylistImportReport = null },
        )
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text(stringResource(R.string.library_playlist_add_to_title)) },
            text = {
                when {
                    isLoadingPlaylistOptions -> ContainedLoadingIndicator()
                    !repository.canManageListenBrainzPlaylists() -> Text(
                        stringResource(R.string.library_playlist_connect_listenbrainz),
                    )
                    playlistOptions.isEmpty() -> Text(
                        stringResource(R.string.library_playlist_none_available),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(playlistOptions, key = { _, playlist -> playlist.id }) { _, playlist ->
                            DropdownMenuItem(
                                text = { Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    val track = trackToAddToPlaylist
                                    showPlaylistPicker = false
                                    if (track != null) scope.launch {
                                        val added = repository.addTrackToListenBrainzPlaylist(
                                            playlist.id,
                                            track,
                                        )
                                        if (added) {
                                            val updatedPlaylist = playlist.copy(
                                                trackCount = playlist.trackCount?.plus(1),
                                            )
                                            playlistOptions = playlistOptions.map {
                                                if (it.id == playlist.id) updatedPlaylist else it
                                            }
                                            val playlistPage = pageStates["playlists"]
                                            if (playlistPage != null) {
                                                val updatedPlaylistEntries = playlistPage.items.map {
                                                    if (it.id == playlist.id) updatedPlaylist.toLibraryEntry() else it
                                                }
                                                pageStates = pageStates + ("playlists" to playlistPage.copy(
                                                    items = updatedPlaylistEntries,
                                                ))
                                                librarySongsCache.saveLibraryPlaylists(
                                                    updatedPlaylistEntries.mapNotNull(LibraryEntry::playlist),
                                                )
                                            }
                                        }
                                        snackbarHostState.showSnackbar(
                                            if (added) {
                                                context.getString(
                                                    R.string.library_playlist_added,
                                                    playlist.title,
                                                )
                                            } else {
                                                context.getString(R.string.library_playlist_add_failed)
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showSongsSortSheet) {
        SongsLibrarySortBottomSheet(
            selectedOption = songsSortOption,
            isDescending = songsSortDescending,
            onDismiss = { showSongsSortSheet = false },
            onOptionSelected = { songsSortOption = it },
            onDirectionToggle = { songsSortDescending = !songsSortDescending },
        )
    }

    selectedTrackForOptions?.let { track ->
        HomeDailyMixSongOptionsSheet(
            track = track,
            onDismiss = {
                selectedTrackForOptions = null
                selectedTrackOptionsQueue = emptyList()
            },
            onPlay = {
                onPlayTrack(track, selectedTrackOptionsQueue)
                selectedTrackForOptions = null
                selectedTrackOptionsQueue = emptyList()
            },
            onPlayNext = {
                onPlayNextTrack(track)
                selectedTrackForOptions = null
                selectedTrackOptionsQueue = emptyList()
            },
            onAddToQueue = {
                onAddToQueueTrack(track)
                selectedTrackForOptions = null
                selectedTrackOptionsQueue = emptyList()
            },
            onAddToPlaylist = {
                trackToAddToPlaylist = track
                selectedTrackForOptions = null
                selectedTrackOptionsQueue = emptyList()
                showPlaylistPicker = true
            },
            onLoadTrackGenres = onLoadTrackGenres,
        )
    }
}

@Composable
private fun LibraryTopBar(
    title: String,
    titleIcon: String,
    isTabSwitcherOpen: Boolean,
    isPlaylistDetail: Boolean,
    session: AuthSession?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onTabSwitcherChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settingsLabel = stringResource(R.string.settings_title)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPlaylistDetail) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.size(LibraryHeaderSplitButtonHeight),
            ) {
                Icon(
                    painter = materialSymbolPainterResource("arrow_back"),
                    contentDescription = stringResource(R.string.library_playlist_back),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LibraryCategoryTitleStyle,
            )
        } else SplitButtonLayout(
            modifier = Modifier.height(LibraryHeaderSplitButtonHeight),
            spacing = 4.dp,
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(
                    onClick = { onTabSwitcherChange(true) },
                    modifier = Modifier.height(LibraryHeaderSplitButtonHeight),
                    shapes = SplitButtonDefaults.leadingButtonShapesFor(
                        LibraryHeaderSplitButtonHeight,
                    ),
                    contentPadding = PaddingValues(start = 15.5.dp, end = 18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    elevation = null,
                ) {
                    Icon(
                        painter = materialSymbolPainterResource(titleIcon, filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(
                            SplitButtonDefaults.leadingButtonIconSizeFor(
                                LibraryHeaderSplitButtonHeight,
                            ),
                        ),
                    )
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LibraryCategoryTitleStyle,
                    )
                }
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = isTabSwitcherOpen,
                    onCheckedChange = onTabSwitcherChange,
                    modifier = Modifier
                        .height(LibraryHeaderSplitButtonHeight)
                        .widthIn(min = 56.dp),
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(
                        LibraryHeaderSplitButtonHeight,
                    ),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(
                        LibraryHeaderSplitButtonHeight,
                    ),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    elevation = null,
                ) {
                    Icon(
                        painter = materialSymbolPainterResource("expand_more"),
                        contentDescription = stringResource(R.string.library_choose_category),
                        modifier = Modifier.size(
                            SplitButtonDefaults.trailingButtonIconSizeFor(
                                LibraryHeaderSplitButtonHeight,
                            ),
                        ),
                    )
                }
            },
        )

        Spacer(Modifier.weight(1f))

        UserAvatar(
            session = session,
            onClick = onOpenSettings,
            size = 48.dp,
            modifier = Modifier.semantics { contentDescription = settingsLabel },
        )
    }
}

@Composable
private fun LibraryPageIndicator(
    currentPage: Int,
    pageCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { page ->
            val width by animateDpAsState(
                targetValue = if (page == currentPage) 26.dp else 7.dp,
                label = "libraryPageIndicatorWidth",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 4.dp)
                    .clip(CircleShape)
                    .background(
                        if (page == currentPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun LibraryActionRow(
    isPlaylistTab: Boolean,
    isSongsTab: Boolean,
    isGridLayout: Boolean,
    sort: LibrarySort,
    showLocateButton: Boolean,
    onShuffle: () -> Unit,
    onNewPlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    onLocate: () -> Unit,
    onToggleLayout: () -> Unit,
    onSort: (LibrarySort) -> Unit,
    onOpenSongsSortSheet: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val outerCorner = 26.dp
    val innerCorner = 8.dp
    val newButtonEndCorner by animateDpAsState(
        targetValue = if (isPlaylistTab) innerCorner else outerCorner,
        label = "libraryNewButtonEndCorner",
    )
    val layoutStartCorner by animateDpAsState(
        targetValue = if (showLocateButton) innerCorner else outerCorner,
        label = "libraryLayoutGroupStartCorner",
    )
    val locateGap by animateDpAsState(
        targetValue = if (showLocateButton) 4.dp else 0.dp,
        label = "libraryLocateGroupGap",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = if (isPlaylistTab) onNewPlaylist else onShuffle,
            modifier = Modifier.height(42.dp),
            shape = RoundedCornerShape(
                topStart = outerCorner,
                bottomStart = outerCorner,
                topEnd = newButtonEndCorner,
                bottomEnd = newButtonEndCorner,
            ),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Icon(
                painter = materialSymbolPainterResource(
                    if (isPlaylistTab) "playlist_add" else "shuffle",
                ),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (isPlaylistTab) R.string.library_action_new else R.string.library_action_shuffle,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isPlaylistTab) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onImportPlaylist,
                modifier = Modifier.height(42.dp),
                shape = RoundedCornerShape(
                    topStart = innerCorner,
                    bottomStart = innerCorner,
                    topEnd = outerCorner,
                    bottomEnd = outerCorner,
                ),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Icon(
                    painter = materialSymbolPainterResource("upload_file"),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.library_playlist_import_button),
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        AnimatedVisibility(
            visible = showLocateButton,
            enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut(),
        ) {
            LibraryActionIcon(
                icon = "my_location",
                description = R.string.library_action_locate,
                onClick = onLocate,
                shape = RoundedCornerShape(
                    topStart = outerCorner,
                    bottomStart = outerCorner,
                    topEnd = innerCorner,
                    bottomEnd = innerCorner,
                ),
            )
        }
        Spacer(Modifier.width(locateGap))
        LibraryActionIcon(
            icon = if (isGridLayout) "menu" else "grid_view",
            description = if (isGridLayout) R.string.library_action_list_layout else R.string.library_action_grid_layout,
            onClick = onToggleLayout,
            shape = RoundedCornerShape(
                topStart = layoutStartCorner,
                bottomStart = layoutStartCorner,
                topEnd = innerCorner,
                bottomEnd = innerCorner,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Box {
            LibraryActionIcon(
                icon = "sort",
                description = R.string.library_action_sort,
                onClick = {
                    if (isSongsTab) onOpenSongsSortSheet() else sortMenuExpanded = true
                },
                shape = RoundedCornerShape(
                    topStart = innerCorner,
                    bottomStart = innerCorner,
                    topEnd = outerCorner,
                    bottomEnd = outerCorner,
                ),
            )
            if (!isSongsTab) {
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    LibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.label)) },
                            onClick = {
                                onSort(option)
                                sortMenuExpanded = false
                            },
                            trailingIcon = if (option == sort) {
                                {
                                    Icon(
                                        painter = materialSymbolPainterResource("check"),
                                        contentDescription = null,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryActionIcon(
    icon: String,
    description: Int,
    onClick: () -> Unit,
    shape: Shape,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = shape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(
            painter = materialSymbolPainterResource(icon),
            contentDescription = stringResource(description),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun PlaylistSelectionActionRow(
    selectedCount: Int,
    allCount: Int,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit,
    onOptions: () -> Unit,
) {
    val outerCorner = 26.dp
    val innerCorner = 8.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                enabled = selectedCount < allCount,
                onClick = onSelectAll,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(
                    topStart = outerCorner,
                    bottomStart = outerCorner,
                    topEnd = innerCorner,
                    bottomEnd = innerCorner,
                ),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(
                    painter = materialSymbolPainterResource("select_all"),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.library_playlist_select_all), maxLines = 1)
            }
            FilledTonalButton(
                onClick = onDeselect,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(
                    topStart = innerCorner,
                    bottomStart = innerCorner,
                    topEnd = outerCorner,
                    bottomEnd = outerCorner,
                ),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(
                    painter = materialSymbolPainterResource("deselect"),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.library_playlist_deselect), maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = onOptions,
            modifier = Modifier.width(36.dp).height(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Icon(
                painter = materialSymbolPainterResource("more_vert"),
                contentDescription = stringResource(R.string.library_playlist_selection_options),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PlaylistSelectionBadge(selectionIndex: Int) {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(26.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (selectionIndex + 1).toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun PlaylistSelectionCountPill(
    selectedCount: Int,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        ) + fadeIn() + scaleIn(initialScale = 0.8f),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.padding(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = materialSymbolPainterResource("check_circle"),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = RoundedSans,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun YouTubePlaylistImportResultsDialog(
    report: YouTubePlaylistImportResult.Completed,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_playlist_import_results_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.library_playlist_import_results_summary,
                        report.importedTrackCount,
                        report.skippedTracks.size,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (report.playlist != null) {
                    Text(
                        stringResource(
                            R.string.library_playlist_import_results_created,
                            report.playlist.title,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        stringResource(R.string.library_playlist_import_results_no_playlist),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (report.isTruncated) {
                    Text(
                        stringResource(
                            R.string.library_playlist_import_results_truncated,
                            report.sourceTrackCount,
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (report.skippedTracks.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.library_playlist_import_results_skipped,
                            report.skippedTracks.size,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = report.skippedTracks,
                            key = { index, skipped -> "${skipped.track.videoId ?: skipped.track.title}-$index" },
                        ) { _, skipped ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = skipped.track.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = skipped.track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(skipped.reason.toStringResource()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.library_playlist_import_results_all_matched),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_playlist_import_done))
            }
        },
    )
}

private fun YouTubePlaylistSkippedTrack.Reason.toStringResource(): Int = when (this) {
    YouTubePlaylistSkippedTrack.Reason.NO_APPLE_MATCH ->
        R.string.library_playlist_import_reason_no_apple_match

    YouTubePlaylistSkippedTrack.Reason.LISTENBRAINZ_LOOKUP_FAILED ->
        R.string.library_playlist_import_reason_listenbrainz_failed

    YouTubePlaylistSkippedTrack.Reason.NO_LISTENBRAINZ_MATCH ->
        R.string.library_playlist_import_reason_no_listenbrainz_match
}

@Composable
private fun LibraryPlaylistEmptyState(contentBottomPadding: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, bottom = contentBottomPadding)
            .offset(y = (-114).dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlaylistEmptyWaveDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
            )
            Icon(
                painter = materialSymbolPainterResource("playlist_play", filled = true),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.library_playlist_empty),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.library_playlist_empty_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Matches PixelPlayer's static sine-wave divider above the empty-playlist content. */
@Composable
private fun PlaylistEmptyWaveDivider(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color,
) {
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val width = size.width
        if (width <= 0f) return@Canvas

        val centerY = size.height / 2f
        val amplitude = with(density) { 4.dp.toPx() }
        val strokeWidth = with(density) { 3.dp.toPx() }
        val waves = 7.6f
        val samples = 400
        val path = Path()
        for (index in 0 until samples) {
            val x = width * index / (samples - 1)
            val theta = (x / width) * (2f * PI.toFloat() * waves)
            val y = centerY + amplitude * sin(theta)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            alpha = 0.95f,
        )
    }
}

@Composable
private fun LibraryCategoryPage(
    items: List<LibraryEntry>,
    isActivePage: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    emptyMessage: Int,
    isGridLayout: Boolean,
    isPlaylistSelectionMode: Boolean,
    selectedPlaylistIds: List<String>,
    sort: LibrarySort,
    isSongsTab: Boolean,
    songsSortOption: SongsLibrarySortOption,
    songsSortDescending: Boolean,
    currentTrack: HomeTrack?,
    isPlaying: Boolean,
    locateRequest: Int,
    onLocateVisibilityChanged: (Boolean) -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    emptyStateBottomPadding: androidx.compose.ui.unit.Dp,
    onItemClick: (LibraryEntry) -> Unit,
    onTrackMoreOptions: (HomeTrack, List<HomeTrack>) -> Unit,
    onDeletePlaylist: (ListenBrainzPlaylist) -> Unit,
    onPlaylistLongPress: (ListenBrainzPlaylist) -> Unit,
    onPlaylistSelectionToggle: (ListenBrainzPlaylist) -> Unit,
    onRefresh: () -> Unit,
    onLocateNotFound: () -> Unit,
    onLocateHandled: () -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val pullState = rememberPullToRefreshState()
    val sortedItems = remember(items, sort, isSongsTab, songsSortOption, songsSortDescending) {
        if (isSongsTab) {
            sortSongLibraryEntries(items, songsSortOption, songsSortDescending)
        } else {
            when (sort) {
                LibrarySort.RECENT -> items
                LibrarySort.TITLE -> items.sortedBy { it.title.lowercase() }
                LibrarySort.ARTIST -> items.sortedBy { it.subtitle.lowercase() }
            }
        }
    }
    val currentTrackIndex = remember(sortedItems, currentTrack) {
        sortedItems.indexOfFirst { it.matchesCurrentTrack(currentTrack) }
    }
    val locateVisibilityCallback by rememberUpdatedState(onLocateVisibilityChanged)

    LaunchedEffect(locateRequest, isGridLayout, isLoading, sortedItems, currentTrack) {
        if (locateRequest == 0) return@LaunchedEffect
        if (isLoading && sortedItems.isEmpty()) return@LaunchedEffect
        if (currentTrackIndex < 0) {
            onLocateNotFound()
        } else if (isGridLayout) {
            val firstVisible = gridState.firstVisibleItemIndex
            if (kotlin.math.abs(currentTrackIndex - firstVisible) > 20) {
                gridState.scrollToItem(currentTrackIndex)
            } else {
                gridState.animateScrollToItem(currentTrackIndex)
            }
        } else {
            val firstVisible = listState.firstVisibleItemIndex
            if (kotlin.math.abs(currentTrackIndex - firstVisible) > 20) {
                listState.scrollToItem(currentTrackIndex)
            } else {
                listState.animateScrollToItem(currentTrackIndex)
            }
        }
        onLocateHandled()
    }

    LaunchedEffect(
        currentTrackIndex,
        isGridLayout,
        isLoading,
        sortedItems.size,
        currentTrack,
        isActivePage,
    ) {
        if (!isActivePage || sortedItems.isEmpty() || isLoading) {
            locateVisibilityCallback(false)
            return@LaunchedEffect
        }
        if (currentTrackIndex == -1) {
            locateVisibilityCallback(currentTrack != null)
            return@LaunchedEffect
        }

        if (isGridLayout) {
            snapshotFlow {
                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                visibleItems.isNotEmpty() &&
                    currentTrackIndex in visibleItems.first().index..visibleItems.last().index
            }
                .distinctUntilChanged()
                .collect { isVisible -> locateVisibilityCallback(!isVisible) }
        } else {
            snapshotFlow {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                visibleItems.isNotEmpty() &&
                    currentTrackIndex in visibleItems.first().index..visibleItems.last().index
            }
                .distinctUntilChanged()
                .collect { isVisible -> locateVisibilityCallback(!isVisible) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { locateVisibilityCallback(false) }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ContainedLoadingIndicator()
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.library_loading))
            }
        } else if (sortedItems.isEmpty()) {
            if (emptyMessage == R.string.library_playlist_empty) {
                LibraryPlaylistEmptyState(emptyStateBottomPadding)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = materialSymbolPainterResource("music_off"),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(emptyMessage),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.library_retry))
                    }
                }
            }
        } else if (isGridLayout) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 4.dp,
                    end = 12.dp,
                    bottom = contentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItemsIndexed(items = sortedItems, key = { _, item -> item.id }) { index, item ->
                    LibraryGridItem(
                        item = item,
                        index = index,
                        onClick = { onItemClick(item) },
                        isPlaylistSelectionMode = isPlaylistSelectionMode,
                        selectedPlaylistIndex = selectedPlaylistIds.indexOf(item.id),
                        onPlaylistLongPress = onPlaylistLongPress,
                        onPlaylistSelectionToggle = onPlaylistSelectionToggle,
                        onDeletePlaylist = onDeletePlaylist,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 4.dp,
                    end = 12.dp,
                    bottom = contentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(items = sortedItems, key = { _, item -> item.id }) { index, item ->
                    val track = item.track
                    if (track != null) {
                        LibraryTrackRow(
                            track = track,
                            isCurrentTrack = track.matchesCurrentTrack(currentTrack),
                            isPlaying = isPlaying && track.matchesCurrentTrack(currentTrack),
                            onClick = { onItemClick(item) },
                            onMoreOptions = {
                                onTrackMoreOptions(
                                    track,
                                    sortedItems.mapNotNull(LibraryEntry::track),
                                )
                            },
                        )
                    } else {
                        LibraryListItem(
                            item = item,
                            index = index,
                            onClick = { onItemClick(item) },
                            isPlaylistSelectionMode = isPlaylistSelectionMode,
                            selectedPlaylistIndex = selectedPlaylistIds.indexOf(item.id),
                            onPlaylistLongPress = onPlaylistLongPress,
                            onPlaylistSelectionToggle = onPlaylistSelectionToggle,
                            onDeletePlaylist = onDeletePlaylist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTrackRow(
    track: HomeTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    HomeDailyMixTrackRow(
        track = track,
        isCurrentSong = isCurrentTrack,
        isPlaying = isPlaying,
        onClick = onClick,
        onMoreOptionsClick = onMoreOptions,
        showArtwork = true,
    )
}

private fun HomeTrack.matchesCurrentTrack(currentTrack: HomeTrack?): Boolean {
    currentTrack ?: return false
    val rowMbid = mbid?.trim()?.takeIf(String::isNotEmpty)
    val activeMbid = currentTrack.mbid?.trim()?.takeIf(String::isNotEmpty)
    return if (rowMbid != null && activeMbid != null) {
        rowMbid.equals(activeMbid, ignoreCase = true)
    } else {
        TrackIdentity.normalizedTitle(title) == TrackIdentity.normalizedTitle(currentTrack.title) &&
            TrackIdentity.normalizedArtist(artist) == TrackIdentity.normalizedArtist(currentTrack.artist)
    }
}

private fun LibraryEntry.matchesCurrentTrack(currentTrack: HomeTrack?): Boolean {
    currentTrack ?: return false
    track?.let { return it.matchesCurrentTrack(currentTrack) }
    album?.let { currentAlbum ->
        val activeAlbum = currentTrack.album ?: return false
        return TrackIdentity.normalizedTitle(currentAlbum.title) ==
            TrackIdentity.normalizedTitle(activeAlbum) &&
            TrackIdentity.normalizedArtist(currentAlbum.artist) ==
            TrackIdentity.normalizedArtist(currentTrack.artist)
    }
    artist?.let { currentArtist ->
        return TrackIdentity.normalizedArtist(currentArtist.name) ==
            TrackIdentity.normalizedArtist(currentTrack.artist)
    }
    return false
}

@Composable
private fun LibraryListItem(
    item: LibraryEntry,
    index: Int,
    onClick: () -> Unit,
    isPlaylistSelectionMode: Boolean,
    selectedPlaylistIndex: Int,
    onPlaylistLongPress: (ListenBrainzPlaylist) -> Unit,
    onPlaylistSelectionToggle: (ListenBrainzPlaylist) -> Unit,
    onDeletePlaylist: (ListenBrainzPlaylist) -> Unit,
) {
    val playlist = item.playlist
    val isPlaylistSelected = playlist != null && selectedPlaylistIndex >= 0
    val selectionScale by animateFloatAsState(
        targetValue = if (isPlaylistSelected) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "playlistSelectionScale",
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isPlaylistSelected) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "playlistSelectionBorderWidth",
    )
    val selectionContainerColor by animateColorAsState(
        targetValue = if (isPlaylistSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = 300),
        label = "playlistSelectionContainerColor",
    )
    val selectionBorderColor by animateColorAsState(
        targetValue = if (isPlaylistSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 250),
        label = "playlistSelectionBorderColor",
    )
    val artworkShape = if (item.artist != null) {
        rememberArtistArtworkShape(item.artist.id)
    } else {
        MaterialTheme.shapes.medium
    }
    if (playlist != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = selectionScale
                    scaleY = selectionScale
                }
                .then(
                    if (isPlaylistSelected || selectionBorderWidth > 0.dp) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = selectionBorderColor,
                            shape = MaterialTheme.shapes.large,
                        )
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    onClick = {
                        if (isPlaylistSelectionMode) {
                            onPlaylistSelectionToggle(playlist)
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = { onPlaylistLongPress(playlist) },
                ),
            shape = MaterialTheme.shapes.large,
            color = selectionContainerColor,
        ) {
            LibraryListItemContent(
                item = item,
                index = index,
                onClick = null,
                artworkShape = artworkShape,
                selectedPlaylistIndex = selectedPlaylistIndex,
                isPlaylistSelectionMode = isPlaylistSelectionMode,
                onDeletePlaylist = onDeletePlaylist,
            )
        }
    } else if (item.artist != null) {
        LibraryListItemContent(
            item = item,
            index = index,
            onClick = onClick,
            artworkShape = artworkShape,
            selectedPlaylistIndex = -1,
            isPlaylistSelectionMode = false,
            onDeletePlaylist = onDeletePlaylist,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            LibraryListItemContent(
                item = item,
                index = index,
                onClick = onClick,
                artworkShape = artworkShape,
                selectedPlaylistIndex = -1,
                isPlaylistSelectionMode = false,
                onDeletePlaylist = onDeletePlaylist,
            )
        }
    }
}

@Composable
private fun LibraryListItemContent(
    item: LibraryEntry,
    index: Int,
    onClick: (() -> Unit)?,
    artworkShape: Shape,
    selectedPlaylistIndex: Int,
    isPlaylistSelectionMode: Boolean,
    onDeletePlaylist: (ListenBrainzPlaylist) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryArtwork(item = item, index = index, size = 54.dp, shape = artworkShape)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.playlist?.let { playlist ->
            when {
                selectedPlaylistIndex >= 0 && isPlaylistSelectionMode ->
                    PlaylistSelectionBadge(selectionIndex = selectedPlaylistIndex)
                !isPlaylistSelectionMode -> LibraryPlaylistOptionsButton(
                    playlist = playlist,
                    onDelete = { onDeletePlaylist(playlist) },
                )
            }
        }
    }
}

@Composable
private fun LibraryGridItem(
    item: LibraryEntry,
    index: Int,
    onClick: () -> Unit,
    isPlaylistSelectionMode: Boolean,
    selectedPlaylistIndex: Int,
    onPlaylistLongPress: (ListenBrainzPlaylist) -> Unit,
    onPlaylistSelectionToggle: (ListenBrainzPlaylist) -> Unit,
    onDeletePlaylist: (ListenBrainzPlaylist) -> Unit,
) {
    if (item.artist != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
                .padding(8.dp),
        ) {
            LibraryGridArtwork(
                item = item,
                index = index,
                shape = rememberArtistArtworkShape(item.artist.id),
            )
            LibraryGridItemMetadata(item, -1, false, onDeletePlaylist)
        }
    } else if (item.playlist != null) {
        val playlist = item.playlist
        val isPlaylistSelected = selectedPlaylistIndex >= 0
        val selectionScale by animateFloatAsState(
            targetValue = if (isPlaylistSelected) 0.98f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "playlistGridSelectionScale",
        )
        val selectionBorderWidth by animateDpAsState(
            targetValue = if (isPlaylistSelected) 2.5.dp else 0.dp,
            animationSpec = tween(durationMillis = 250),
            label = "playlistGridSelectionBorderWidth",
        )
        val selectionContainerColor by animateColorAsState(
            targetValue = if (isPlaylistSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            animationSpec = tween(durationMillis = 300),
            label = "playlistGridSelectionContainerColor",
        )
        val selectionBorderColor by animateColorAsState(
            targetValue = if (isPlaylistSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
            animationSpec = tween(durationMillis = 250),
            label = "playlistGridSelectionBorderColor",
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = selectionScale
                    scaleY = selectionScale
                }
                .then(
                    if (isPlaylistSelected || selectionBorderWidth > 0.dp) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = selectionBorderColor,
                            shape = MaterialTheme.shapes.large,
                        )
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    onClick = {
                        if (isPlaylistSelectionMode) {
                            onPlaylistSelectionToggle(playlist)
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = { onPlaylistLongPress(playlist) },
                ),
            shape = MaterialTheme.shapes.large,
            color = selectionContainerColor,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                LibraryGridArtwork(
                    item = item,
                    index = index,
                    shape = MaterialTheme.shapes.medium,
                )
                LibraryGridItemMetadata(
                    item = item,
                    selectedPlaylistIndex = selectedPlaylistIndex,
                    isPlaylistSelectionMode = isPlaylistSelectionMode,
                    onDeletePlaylist = onDeletePlaylist,
                )
            }
        }
    } else {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                LibraryGridArtwork(
                    item = item,
                    index = index,
                    shape = MaterialTheme.shapes.medium,
                )
                LibraryGridItemMetadata(item, -1, false, onDeletePlaylist)
            }
        }
    }
}

@Composable
private fun LibraryGridItemMetadata(
    item: LibraryEntry,
    selectedPlaylistIndex: Int,
    isPlaylistSelectionMode: Boolean,
    onDeletePlaylist: (ListenBrainzPlaylist) -> Unit,
) {
    if (item.playlist != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                LibraryGridItemTitle(item.title)
                LibraryGridItemSubtitle(item.subtitle)
            }
            when {
                selectedPlaylistIndex >= 0 && isPlaylistSelectionMode ->
                    PlaylistSelectionBadge(selectionIndex = selectedPlaylistIndex)
                !isPlaylistSelectionMode -> LibraryPlaylistOptionsButton(
                    playlist = item.playlist,
                    onDelete = { onDeletePlaylist(item.playlist) },
                )
            }
        }
    } else {
        Column(modifier = Modifier.padding(top = 10.dp)) {
            LibraryGridItemTitle(item.title)
            LibraryGridItemSubtitle(item.subtitle)
        }
    }
}

@Composable
private fun LibraryGridItemTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LibraryGridItemSubtitle(subtitle: String) {
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LibraryPlaylistOptionsButton(
    playlist: ListenBrainzPlaylist,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = materialSymbolPainterResource("more_vert"),
                contentDescription = stringResource(R.string.library_more_options, playlist.title),
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_playlist_delete)) },
                leadingIcon = {
                    Icon(
                        painter = materialSymbolPainterResource("delete"),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun PlaylistSelectionOptionsSheet(
    playlists: List<ListenBrainzPlaylist>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val previewPlaylists = playlists.take(4)
    val imageSize = 66.dp
    val overlap = 33.dp
    val stackWidth = if (previewPlaylists.isEmpty()) 0.dp else {
        imageSize + overlap * (previewPlaylists.size - 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(74.dp)
                        .width(stackWidth),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    previewPlaylists.forEachIndexed { index, playlist ->
                        Box(
                            modifier = Modifier
                                .offset(x = overlap * index)
                                .size(imageSize)
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium,
                                )
                                .padding(3.dp),
                        ) {
                            LibraryArtwork(
                                item = playlist.toLibraryEntry(),
                                index = index,
                                size = 60.dp,
                                shape = MaterialTheme.shapes.small,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(
                            if (playlists.size == 1) {
                                R.string.library_playlist_selection_title_single
                            } else {
                                R.string.library_playlist_selection_title_plural
                            },
                            playlists.size,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RoundedSans,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.library_playlist_selected),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = RoundedSans,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 66.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        shape = CircleShape,
                    ) {
                        Icon(
                            painter = materialSymbolPainterResource("delete"),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.library_playlist_delete))
                    }
                    UnsupportedPlaylistAction(
                        label = R.string.library_playlist_export,
                        icon = "download",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UnsupportedPlaylistAction(
                        label = R.string.library_playlist_merge,
                        icon = "merge",
                        modifier = Modifier.weight(1f),
                    )
                    UnsupportedPlaylistAction(
                        label = R.string.library_playlist_share,
                        icon = "share",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnsupportedPlaylistAction(
    label: Int,
    icon: String,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = {},
        enabled = false,
        modifier = modifier.heightIn(min = 66.dp),
        shape = CircleShape,
    ) {
        Icon(
            painter = materialSymbolPainterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
}

@Composable
private fun LibraryArtwork(
    item: LibraryEntry,
    index: Int,
    size: androidx.compose.ui.unit.Dp,
    shape: Shape,
) {
    val container = when (index % 3) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = when (index % 3) {
        0 -> MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (item.artworkUrl.isNullOrBlank()) {
            Icon(
                painter = materialSymbolPainterResource(item.icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(27.dp),
            )
        } else {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LibraryGridArtwork(
    item: LibraryEntry,
    index: Int,
    shape: Shape,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(
                when (index % 3) {
                    0 -> MaterialTheme.colorScheme.primaryContainer
                    1 -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (item.artworkUrl.isNullOrBlank()) {
            Icon(
                painter = materialSymbolPainterResource(item.icon),
                contentDescription = null,
                tint = when (index % 3) {
                    0 -> MaterialTheme.colorScheme.onPrimaryContainer
                    1 -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onTertiaryContainer
                },
                modifier = Modifier.size(44.dp),
            )
        } else {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private suspend fun fetchLibraryCategory(
    repository: HomeFeedRepository,
    categoryId: String,
): List<LibraryEntry> = when (categoryId) {
    "songs" -> repository.fetchLibrarySongs(forceRefresh = true).map(HomeTrack::toLibraryEntry)
    "albums" -> repository.fetchTopAlbums().map(HomeAlbum::toLibraryEntry)
    "artists" -> repository.fetchTopArtists(forceRefresh = true).map(HomeArtist::toLibraryEntry)
    "playlists" -> repository.fetchListenBrainzPlaylists().map(ListenBrainzPlaylist::toLibraryEntry)
    "recent" -> repository.fetchLibraryRecentTracks(forceRefresh = true).map(HomeTrack::toLibraryEntry)
    "loved" -> repository.fetchLibraryLovedTracks(forceRefresh = true).map(HomeTrack::toLibraryEntry)
    else -> emptyList()
}

private fun HomeTrack.toLibraryEntry(): LibraryEntry = LibraryEntry(
    id = id,
    title = title,
    subtitle = artist,
    artworkUrl = artworkUrl,
    icon = "music_note",
    track = this,
)

private fun HomeAlbum.toLibraryEntry(): LibraryEntry = LibraryEntry(
    id = id,
    title = title,
    subtitle = artist,
    artworkUrl = artworkUrl,
    icon = "album",
    album = this,
)

private fun HomeArtist.toLibraryEntry(): LibraryEntry = LibraryEntry(
    id = id,
    title = name,
    subtitle = "${playCount} plays",
    artworkUrl = imageUrl,
    icon = "person",
    artist = this,
)

private fun ListenBrainzPlaylist.toLibraryEntry(): LibraryEntry = LibraryEntry(
    id = id,
    title = title,
    subtitle = listOfNotNull(creator, trackCount?.let { "$it tracks" })
        .joinToString(" · ")
        .ifBlank { "ListenBrainz" },
    icon = "playlist_play",
    playlist = this,
)

@Composable
private fun LibraryTabsBottomSheet(
    categories: List<LibraryCategory>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onEditClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.library_tabs_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.library_tabs_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                gridItemsIndexed(
                    items = categories,
                    key = { index, category -> "${category.id}-$index" },
                ) { index, category ->
                    LibraryTabGridItem(
                        category = category,
                        isSelected = index == selectedIndex,
                        onClick = { onSelect(index) },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp, max = 60.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onEditClick,
                            modifier = Modifier
                                .fillMaxHeight()
                                .align(Alignment.CenterEnd),
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        ) {
                            Icon(
                                painter = materialSymbolPainterResource("edit"),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.library_reorder_tabs))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryReorderTabsSheet(
    categories: List<LibraryCategory>,
    onReorder: (List<LibraryCategory>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var localCategories by remember { mutableStateOf(categories) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(categories) {
        localCategories = categories
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.library_reorder_reset_title)) },
            text = { Text(stringResource(R.string.library_reorder_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        localCategories = InitialLibraryCategories
                        showResetDialog = false
                    },
                ) {
                    Text(stringResource(R.string.library_reorder_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            localCategories = localCategories.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.library_reorder_tabs_title),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = RoundedSans,
                    )
                }
            },
            floatingActionButton = {
                LibraryReorderFloatingToolbar(
                    onReset = { showResetDialog = true },
                    onDone = {
                        scope.launch {
                            isLoading = true
                            delay(700)
                            onReorder(localCategories)
                            isLoading = false
                            onDismiss()
                        }
                    },
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            containerColor = MaterialTheme.colorScheme.surface,
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ContainedLoadingIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.library_reorder_progress))
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = localCategories,
                            key = { _, category -> category.id },
                        ) { _, category ->
                            ReorderableItem(
                                state = reorderableState,
                                key = category.id,
                            ) { isDragging ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape),
                                    shape = CircleShape,
                                    shadowElevation = if (isDragging) 4.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            painter = materialSymbolPainterResource("drag_indicator"),
                                            contentDescription = stringResource(R.string.library_reorder_drag_handle),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .draggableHandle(),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text(
                                            text = stringResource(category.title),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryReorderFloatingToolbar(
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onReset) {
                Icon(
                    painter = materialSymbolPainterResource("restart_alt"),
                    contentDescription = stringResource(R.string.library_reorder_reset),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MediumExtendedFloatingActionButton(
                onClick = onDone,
                shape = CircleShape,
                icon = {
                    Icon(
                        painter = materialSymbolPainterResource("check"),
                        contentDescription = stringResource(R.string.library_done),
                        modifier = Modifier.size(24.dp),
                    )
                },
                text = { Text(stringResource(R.string.library_done)) },
            )
        }
    }
}

@Composable
private fun LibraryTabGridItem(
    category: LibraryCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconContainer = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val iconContent = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = materialSymbolPainterResource(
                        category.icon,
                        filled = category.icon == "favorite",
                    ),
                    contentDescription = null,
                    tint = iconContent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = stringResource(category.title),
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
