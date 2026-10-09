@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
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
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.FloatingNavBarClearance
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
    fontFamily = FontFamily(
        Font(
            resId = R.font.gsans_flex_full,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(800),
                FontVariation.width(97f),
                FontVariation.slant(0f),
                FontVariation.Setting("ROND", 46f),
                FontVariation.Setting("XTRA", 520f),
                FontVariation.Setting("YOPQ", 90f),
                FontVariation.Setting("YTLC", 505f),
            ),
        ),
    ),
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
    val cachedSongs = remember(librarySongsCache) {
        librarySongsCache.loadLibrarySongs().orEmpty()
    }
    var pageStates by remember(librarySongsCache) {
        mutableStateOf(
            if (cachedSongs.isEmpty()) emptyMap() else mapOf(
                "songs" to LibraryPageState(items = cachedSongs.map(HomeTrack::toLibraryEntry)),
            ),
        )
    }
    var selectedPlaylist by remember { mutableStateOf<ListenBrainzPlaylist?>(null) }
    var playlistDetailState by remember { mutableStateOf(LibraryPageState(isLoading = false)) }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var isCreatingPlaylist by remember { mutableStateOf(false) }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    var playlistOptions by remember { mutableStateOf<List<ListenBrainzPlaylist>>(emptyList()) }
    var isLoadingPlaylistOptions by remember { mutableStateOf(false) }
    var trackToAddToPlaylist by remember { mutableStateOf<HomeTrack?>(null) }
    var selectedTrackForOptions by remember { mutableStateOf<HomeTrack?>(null) }
    var selectedTrackOptionsQueue by remember { mutableStateOf<List<HomeTrack>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }

    val activeCategory = categories.getOrNull(pagerState.currentPage) ?: categories.first()
    val activePlaylist = selectedPlaylist.takeIf { activeCategory.id == "playlists" }
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
        if (previous != null && categoryId != "songs" && !isExplicitRefresh) return@LaunchedEffect
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
        val freshSongs = loadedItems.mapNotNull(LibraryEntry::track)
        if (categoryId == "songs" && freshSongs.isNotEmpty()) {
            librarySongsCache.saveLibrarySongs(freshSongs)
        }
        val visibleItems = previous?.items
            ?.takeIf { loadedItems.isEmpty() && it.isNotEmpty() }
            ?: loadedItems
        pageStates = pageStates + (categoryId to LibraryPageState(items = visibleItems))
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
                            onLocate = {
                                locateTargetPageKey = activePageKey
                                locateRequest++
                            },
                            onToggleLayout = { isGridLayout = !isGridLayout },
                            onSort = { sort = it },
                            onOpenSongsSortSheet = { showSongsSortSheet = true },
                        )

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
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
                                onItemClick = { entry ->
                                    when {
                                        entry.track != null -> onPlayTrack(
                                            entry.track,
                                            items.mapNotNull(LibraryEntry::track),
                                        )
                                        entry.album != null -> onOpenAlbum(entry.album)
                                        entry.artist != null -> onOpenArtist(entry.artist)
                                        entry.playlist != null -> selectedPlaylist = entry.playlist
                                    }
                                },
                                onTrackMoreOptions = { track, queue ->
                                    selectedTrackForOptions = track
                                    selectedTrackOptionsQueue = queue
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
                                    pageStates = pageStates + ("playlists" to LibraryPageState(
                                        items = listOf(entry) + existing.filterNot { it.id == entry.id },
                                    ))
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
                                                pageStates = pageStates + ("playlists" to playlistPage.copy(
                                                    items = playlistPage.items.map {
                                                        if (it.id == playlist.id) updatedPlaylist.toLibraryEntry() else it
                                                    },
                                                ))
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
    onLocate: () -> Unit,
    onToggleLayout: () -> Unit,
    onSort: (LibrarySort) -> Unit,
    onOpenSongsSortSheet: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val outerCorner = 26.dp
    val innerCorner = 8.dp
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
            shape = CircleShape,
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
            )
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
            icon = if (isGridLayout) "menu" else "library_books",
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
private fun LibraryCategoryPage(
    items: List<LibraryEntry>,
    isActivePage: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    emptyMessage: Int,
    isGridLayout: Boolean,
    sort: LibrarySort,
    isSongsTab: Boolean,
    songsSortOption: SongsLibrarySortOption,
    songsSortDescending: Boolean,
    currentTrack: HomeTrack?,
    isPlaying: Boolean,
    locateRequest: Int,
    onLocateVisibilityChanged: (Boolean) -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    onItemClick: (LibraryEntry) -> Unit,
    onTrackMoreOptions: (HomeTrack, List<HomeTrack>) -> Unit,
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clickable(onClick = onClick)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryArtwork(item = item, index = index, size = 54.dp)
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
        }
    }
}

@Composable
private fun LibraryGridItem(
    item: LibraryEntry,
    index: Int,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            LibraryGridArtwork(item = item, index = index)
            Text(
                text = item.title,
                modifier = Modifier.padding(top = 10.dp),
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
    }
}

@Composable
private fun LibraryArtwork(
    item: LibraryEntry,
    index: Int,
    size: androidx.compose.ui.unit.Dp,
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
            .clip(MaterialTheme.shapes.medium)
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
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
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
    "artists" -> repository.fetchTopArtists().map(HomeArtist::toLibraryEntry)
    "playlists" -> repository.fetchListenBrainzPlaylists().map(ListenBrainzPlaylist::toLibraryEntry)
    "recent" -> repository.fetchRotation().map(HomeTrack::toLibraryEntry)
    "loved" -> repository.fetchLibraryLovedTracks().map(HomeTrack::toLibraryEntry)
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
