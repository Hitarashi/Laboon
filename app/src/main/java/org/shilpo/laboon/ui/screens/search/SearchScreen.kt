@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")

package org.shilpo.laboon.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.auth.SharedPreferencesKeyValueStore
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.home.HomePlaylist
import org.shilpo.laboon.home.HomeStation
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackAvailability
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem
import org.shilpo.laboon.rip.RipTaskSnapshot
import org.shilpo.laboon.search.CachedTrackAvailability
import org.shilpo.laboon.search.SearchFilter
import org.shilpo.laboon.search.SearchHistoryStore
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchRepositoryImpl
import org.shilpo.laboon.search.SearchResults
import org.shilpo.laboon.search.SearchSuggestions
import org.shilpo.laboon.ui.design.FloatingCombinedClearance
import org.shilpo.laboon.ui.design.LiquidGlassBackdropState
import org.shilpo.laboon.ui.design.TrackCodecBadges

@Composable
fun SearchScreen(
    backdropState: LiquidGlassBackdropState? = null,
    bottomClearance: Dp = FloatingCombinedClearance,
    onTrackClick: (HomeTrack) -> Unit = {},
    onAlbumClick: (HomeAlbum) -> Unit = {},
    onArtistClick: (HomeArtist) -> Unit = {},
    onPlaylistClick: (HomePlaylist) -> Unit = {},
    onStationClick: (HomeStation) -> Unit = {},
    onPlayWithContext: ((HomeTrack, List<HomeTrack>) -> Unit)? = null,
    onPlayNext: ((HomeTrack) -> Unit)? = null,
    onAddToQueue: ((HomeTrack) -> Unit)? = null,
    ripTasks: List<RipTaskSnapshot> = emptyList(),
    pendingRipTrackIds: Set<String> = emptySet(),
    onRipTrack: (HomeTrack) -> Unit = {},
    onOpenRipVisualizer: () -> Unit = {},
    searchFocusTrigger: Int = 0,
    modifier: Modifier = Modifier,
    searchRepository: SearchRepository? = null,
    onObservedTracks: (List<HomeTrack>) -> Unit = {},
    onObservedAlbums: (List<HomeAlbum>) -> Unit = {},
    ripCompletions: Flow<String>? = null,
    resolveAvailability: (suspend (String) -> Map<String, CachedTrackAvailability>)? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val repository = remember(searchRepository, context) {
        searchRepository
            ?: SearchRepositoryImpl(SessionStore(SharedPreferencesKeyValueStore(context)))
    }
    val searchHistoryStore = remember(context) {
        SearchHistoryStore(SharedPreferencesKeyValueStore(context))
    }

    LaunchedEffect(searchFocusTrigger) {
        if (searchFocusTrigger > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    var selectedFilter by remember { mutableStateOf(SearchFilter.TOP_RESULTS) }
    var query by remember { mutableStateOf("") }
    var searchHistory by remember(searchHistoryStore) {
        mutableStateOf(searchHistoryStore.load())
    }
    var trackHistory by remember(searchHistoryStore) {
        mutableStateOf(searchHistoryStore.loadTracks())
    }
    var searchRequest by remember { mutableStateOf<String?>(null) }
    var isSuggesting by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var hints by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<HomeTrack>>(emptyList()) }
    var albumSuggestions by remember { mutableStateOf<List<HomeAlbum>>(emptyList()) }
    var artistSuggestions by remember { mutableStateOf<List<HomeArtist>>(emptyList()) }
    var results by remember { mutableStateOf<List<HomeTrack>>(emptyList()) }
    var albumResults by remember { mutableStateOf<List<HomeAlbum>>(emptyList()) }
    var artistResults by remember { mutableStateOf<List<HomeArtist>>(emptyList()) }
    var playlistResults by remember { mutableStateOf<List<HomePlaylist>>(emptyList()) }
    var stationResults by remember { mutableStateOf<List<HomeStation>>(emptyList()) }
    var musicVideoResults by remember { mutableStateOf<List<HomeTrack>>(emptyList()) }
    var topResults by remember { mutableStateOf<List<LyricspornCatalogItem>>(emptyList()) }

    fun clearAllResults() {
        searchRequest = null
        isSearching = false
        isSuggesting = false
        hints = emptyList()
        suggestions = emptyList()
        albumSuggestions = emptyList()
        artistSuggestions = emptyList()
        results = emptyList()
        albumResults = emptyList()
        artistResults = emptyList()
        playlistResults = emptyList()
        stationResults = emptyList()
        musicVideoResults = emptyList()
        topResults = emptyList()
    }

    fun submitSearch(term: String = query.trim()) {
        if (term.isBlank()) return
        searchHistory = searchHistoryStore.add(term)
        if (term == searchRequest) {
            focusManager.clearFocus()
            return
        }
        query = term
        searchRequest = term
        isSearching = true
        hints = emptyList()
        suggestions = emptyList()
        albumSuggestions = emptyList()
        artistSuggestions = emptyList()
        results = emptyList()
        albumResults = emptyList()
        artistResults = emptyList()
        playlistResults = emptyList()
        stationResults = emptyList()
        musicVideoResults = emptyList()
        topResults = emptyList()
        focusManager.clearFocus()
    }

    fun recordSearchTrack(track: HomeTrack) {
        query.trim().takeIf(String::isNotEmpty)?.let { term ->
            searchHistory = searchHistoryStore.add(term)
        }
        trackHistory = searchHistoryStore.addTrack(track)
    }

    val normalizedQuery = query.trim()
    LaunchedEffect(normalizedQuery, searchRequest) {
        val term = normalizedQuery
        if (term.length < MIN_SUGGESTION_QUERY_LENGTH || term == searchRequest) {
            hints = emptyList()
            suggestions = emptyList()
            albumSuggestions = emptyList()
            artistSuggestions = emptyList()
            isSuggesting = false
            return@LaunchedEffect
        }

        isSuggesting = true
        delay(SUGGESTION_DEBOUNCE_MS)
        val hintRequest =
            async { runCatching { repository.searchHints(term) }.getOrDefault(emptyList()) }
        val suggestionRequest = async {
            runCatching { repository.searchSuggestions(term) }.getOrDefault(SearchSuggestions())
        }
        hints = hintRequest.await().distinct().take(MAX_HINTS)
        val suggestionsResult = suggestionRequest.await()
        suggestions = suggestionsResult.tracks.distinctBy(HomeTrack::id).take(MAX_TOP_RESULTS)
        albumSuggestions = suggestionsResult.albums.distinctBy(HomeAlbum::id).take(MAX_TOP_RESULTS)
        artistSuggestions =
            suggestionsResult.artists.distinctBy(HomeArtist::id).take(MAX_TOP_RESULTS)
        isSuggesting = false
    }

    LaunchedEffect(searchRequest, selectedFilter) {
        val term = searchRequest ?: return@LaunchedEffect
        isSearching = true
        results = emptyList()
        albumResults = emptyList()
        artistResults = emptyList()
        playlistResults = emptyList()
        stationResults = emptyList()
        musicVideoResults = emptyList()
        topResults = emptyList()
        val found =
            runCatching { repository.search(term, selectedFilter) }.getOrDefault(SearchResults())
        if (searchRequest == term) {
            results = found.tracks.distinctBy(HomeTrack::id)
            albumResults = found.albums.distinctBy(HomeAlbum::id)
            artistResults = found.artists.distinctBy(HomeArtist::id)
            playlistResults = found.playlists.distinctBy(HomePlaylist::id)
            stationResults = found.stations.distinctBy(HomeStation::id)
            musicVideoResults = found.musicVideos.distinctBy(HomeTrack::id)
            topResults = found.topResults.distinctBy(LyricspornCatalogItem::id)
            isSearching = false
        }
    }

    LaunchedEffect(results, musicVideoResults, suggestions, trackHistory) {
        onObservedTracks(
            buildList {
                addAll(results)
                addAll(musicVideoResults)
                addAll(suggestions)
                addAll(trackHistory)
            }.distinctBy(HomeTrack::id)
        )
    }

    LaunchedEffect(albumResults, albumSuggestions) {
        onObservedAlbums(
            buildList {
                addAll(albumResults)
                addAll(albumSuggestions)
            }.distinctBy(HomeAlbum::id)
        )
    }

    LaunchedEffect(ripCompletions) {
        val stream = ripCompletions ?: return@LaunchedEffect
        val lookup = resolveAvailability ?: return@LaunchedEffect
        stream.collect { providerTrackId ->
            val availability = lookup(providerTrackId)
            if (availability.isEmpty()) return@collect
            results = TrackAvailability.apply(results, availability)
            musicVideoResults = TrackAvailability.apply(musicVideoResults, availability)
            suggestions = TrackAvailability.apply(suggestions, availability)
            trackHistory = TrackAvailability.apply(trackHistory, availability)
        }
    }

    val showingSearchResults = searchRequest != null && searchRequest == query.trim()

    val topResultsScrollState = rememberLazyListState()
    val artistsScrollState = rememberLazyListState()
    val albumsScrollState = rememberLazyListState()
    val songsScrollState = rememberLazyListState()
    val playlistsScrollState = rememberLazyListState()
    val stationsScrollState = rememberLazyListState()
    val musicVideosScrollState = rememberLazyListState()
    val suggestionsScrollState = rememberLazyListState()
    val historyScrollState = rememberLazyListState()

    val activeScrollState = when {
        query.isBlank() && (searchHistory.isNotEmpty() || trackHistory.isNotEmpty()) -> historyScrollState
        showingSearchResults -> when (selectedFilter) {
            SearchFilter.TOP_RESULTS -> topResultsScrollState
            SearchFilter.ARTISTS -> artistsScrollState
            SearchFilter.ALBUMS -> albumsScrollState
            SearchFilter.SONGS -> songsScrollState
            SearchFilter.PLAYLISTS -> playlistsScrollState
            SearchFilter.STATIONS -> stationsScrollState
            SearchFilter.MUSIC_VIDEOS -> musicVideosScrollState
        }

        else -> suggestionsScrollState
    }

    LaunchedEffect(activeScrollState, backdropState) {
        if (backdropState == null) return@LaunchedEffect
        snapshotFlow { activeScrollState.firstVisibleItemIndex to activeScrollState.firstVisibleItemScrollOffset }
            .collect {
                backdropState.invalidate()
            }
    }

    LaunchedEffect(
        results,
        albumResults,
        artistResults,
        playlistResults,
        stationResults,
        musicVideoResults,
        suggestions,
        hints,
        selectedFilter,
        showingSearchResults,
        query,
        isSearching,
    ) {
        backdropState?.invalidate()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = query,
            onValueChange = {
                query = it
                isSearching = false
                isSuggesting = false
                hints = emptyList()
                suggestions = emptyList()
                albumSuggestions = emptyList()
                artistSuggestions = emptyList()
                if (it.trim() != searchRequest) {
                    clearAllResults()
                }
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = ""
                            clearAllResults()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clear),
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitSearch(query.trim()) }),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .focusRequester(focusRequester),
        )

        Spacer(modifier = Modifier.height(10.dp))

        SearchFilterRow(
            selectedFilter = selectedFilter,
            onFilterSelected = { filter ->
                if (selectedFilter != filter) {
                    selectedFilter = filter
                    if (query.isNotBlank() && searchRequest != query.trim()) {
                        submitSearch(query.trim())
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        SearchBody(
            topResultsScrollState = topResultsScrollState,
            artistsScrollState = artistsScrollState,
            albumsScrollState = albumsScrollState,
            songsScrollState = songsScrollState,
            playlistsScrollState = playlistsScrollState,
            stationsScrollState = stationsScrollState,
            musicVideosScrollState = musicVideosScrollState,
            suggestionsScrollState = suggestionsScrollState,
            historyScrollState = historyScrollState,
            bottomClearance = bottomClearance,
            isSearching = isSearching,
            query = query,
            searchHistory = searchHistory,
            trackHistory = trackHistory,
            showingSearchResults = showingSearchResults,
            selectedFilter = selectedFilter,
            results = results,
            albumResults = albumResults,
            artistResults = artistResults,
            playlistResults = playlistResults,
            stationResults = stationResults,
            musicVideoResults = musicVideoResults,
            isSuggesting = isSuggesting,
            hints = hints,
            suggestions = suggestions,
            albumSuggestions = albumSuggestions,
            artistSuggestions = artistSuggestions,
            onSubmitSearch = ::submitSearch,
            onRecordSearchTrack = ::recordSearchTrack,
            onTrackClick = onTrackClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlaylistClick = onPlaylistClick,
            onStationClick = onStationClick,
            onPlayWithContext = onPlayWithContext,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            ripTasks = ripTasks,
            pendingRipTrackIds = pendingRipTrackIds,
            onRipTrack = onRipTrack,
            onOpenRipVisualizer = onOpenRipVisualizer,
            onRemoveHistory = { term ->
                searchHistory = searchHistoryStore.remove(term)
            },
            onRemoveTrackHistory = { track ->
                trackHistory = searchHistoryStore.removeTrack(track)
            },
            onClearHistory = {
                searchHistoryStore.clear()
                searchHistory = emptyList()
                trackHistory = emptyList()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun SearchBody(
    topResultsScrollState: LazyListState,
    artistsScrollState: LazyListState,
    albumsScrollState: LazyListState,
    songsScrollState: LazyListState,
    playlistsScrollState: LazyListState,
    stationsScrollState: LazyListState,
    musicVideosScrollState: LazyListState,
    suggestionsScrollState: LazyListState,
    historyScrollState: LazyListState,
    bottomClearance: Dp,
    isSearching: Boolean,
    query: String,
    searchHistory: List<String>,
    trackHistory: List<HomeTrack>,
    showingSearchResults: Boolean,
    selectedFilter: SearchFilter,
    results: List<HomeTrack>,
    albumResults: List<HomeAlbum>,
    artistResults: List<HomeArtist>,
    playlistResults: List<HomePlaylist>,
    stationResults: List<HomeStation>,
    musicVideoResults: List<HomeTrack>,
    isSuggesting: Boolean,
    hints: List<String>,
    suggestions: List<HomeTrack>,
    albumSuggestions: List<HomeAlbum>,
    artistSuggestions: List<HomeArtist>,
    onSubmitSearch: (String) -> Unit,
    onRecordSearchTrack: (HomeTrack) -> Unit,
    onTrackClick: (HomeTrack) -> Unit,
    onAlbumClick: (HomeAlbum) -> Unit,
    onArtistClick: (HomeArtist) -> Unit,
    onPlaylistClick: (HomePlaylist) -> Unit,
    onStationClick: (HomeStation) -> Unit,
    onPlayWithContext: ((HomeTrack, List<HomeTrack>) -> Unit)?,
    onPlayNext: ((HomeTrack) -> Unit)?,
    onAddToQueue: ((HomeTrack) -> Unit)?,
    ripTasks: List<RipTaskSnapshot>,
    pendingRipTrackIds: Set<String>,
    onRipTrack: (HomeTrack) -> Unit,
    onOpenRipVisualizer: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onRemoveTrackHistory: (HomeTrack) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        when {
            isSearching -> SearchLoading()
            query.isBlank() && (searchHistory.isNotEmpty() || trackHistory.isNotEmpty()) ->
                SearchHistoryList(
                    state = historyScrollState,
                    bottomClearance = bottomClearance,
                    history = searchHistory,
                    tracks = trackHistory,
                    onSelect = onSubmitSearch,
                    onTrackClick = onTrackClick,
                    onPlayWithContext = onPlayWithContext,
                    onTrackSelected = onRecordSearchTrack,
                    onRemove = onRemoveHistory,
                    onRemoveTrack = onRemoveTrackHistory,
                    onClear = onClearHistory,
                )

            query.isBlank() -> SearchPlaceholder()
            showingSearchResults -> {
                when (selectedFilter) {
                    SearchFilter.TOP_RESULTS -> {
                        val hasAnyResults = results.isNotEmpty() || albumResults.isNotEmpty() ||
                                artistResults.isNotEmpty() || playlistResults.isNotEmpty() ||
                                stationResults.isNotEmpty() || musicVideoResults.isNotEmpty()
                        if (!hasAnyResults) {
                            SearchEmptyResults(query = query, filter = selectedFilter)
                        } else {
                            LazyColumn(
                                state = topResultsScrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = bottomClearance + 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (artistResults.isNotEmpty()) {
                                    item(key = "artist_results_heading") {
                                        SuggestionHeading(text = "Artists")
                                    }
                                    item(key = "artist_results_row") {
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            items(artistResults, key = HomeArtist::id) { artist ->
                                                SearchArtistCard(
                                                    artist = artist,
                                                    onClick = { onArtistClick(artist) },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (albumResults.isNotEmpty()) {
                                    item(key = "album_results_heading") {
                                        SuggestionHeading(text = "Albums")
                                    }
                                    item(key = "album_results_row") {
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            items(albumResults, key = HomeAlbum::id) { album ->
                                                SearchAlbumCard(
                                                    album = album,
                                                    onClick = { onAlbumClick(album) },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (playlistResults.isNotEmpty()) {
                                    item(key = "playlist_results_heading") {
                                        SuggestionHeading(text = "Playlists")
                                    }
                                    item(key = "playlist_results_row") {
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            items(
                                                playlistResults,
                                                key = HomePlaylist::id
                                            ) { playlist ->
                                                SearchPlaylistCard(
                                                    playlist = playlist,
                                                    onClick = { onPlaylistClick(playlist) },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (stationResults.isNotEmpty()) {
                                    item(key = "station_results_heading") {
                                        SuggestionHeading(text = "Stations")
                                    }
                                    item(key = "station_results_row") {
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            items(
                                                stationResults,
                                                key = HomeStation::id
                                            ) { station ->
                                                SearchStationCard(
                                                    station = station,
                                                    onClick = { onStationClick(station) },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (musicVideoResults.isNotEmpty()) {
                                    item(key = "music_video_results_heading") {
                                        SuggestionHeading(text = "Music Videos")
                                    }
                                    item(key = "music_video_results_row") {
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            items(musicVideoResults, key = HomeTrack::id) { video ->
                                                SearchMusicVideoCard(
                                                    track = video,
                                                    onClick = {
                                                        onRecordSearchTrack(video)
                                                        if (onPlayWithContext != null) {
                                                            onPlayWithContext(
                                                                video,
                                                                musicVideoResults
                                                            )
                                                        } else {
                                                            onTrackClick(video)
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (results.isNotEmpty()) {
                                    item(key = "song_results_heading") {
                                        val hasOtherResults = albumResults.isNotEmpty() ||
                                                artistResults.isNotEmpty() || playlistResults.isNotEmpty() ||
                                                stationResults.isNotEmpty() || musicVideoResults.isNotEmpty()
                                        SuggestionHeading(text = if (hasOtherResults) "Songs" else "Top results")
                                    }
                                    items(results, key = HomeTrack::id) { track ->
                                        SearchTrackRow(
                                            track = track,
                                            contextTracks = results,
                                            onTrackClick = onTrackClick,
                                            onPlayWithContext = onPlayWithContext,
                                            onPlayNext = onPlayNext,
                                            onAddToQueue = onAddToQueue,
                                            ripTasks = ripTasks,
                                            pendingRipTrackIds = pendingRipTrackIds,
                                            onRipTrack = onRipTrack,
                                            onOpenRipVisualizer = onOpenRipVisualizer,
                                            onTrackSelected = onRecordSearchTrack,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SearchFilter.ARTISTS -> {
                        if (artistResults.isEmpty()) {
                            SearchEmptyResults(query = query, filter = selectedFilter)
                        } else {
                            LazyColumn(
                                state = artistsScrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = bottomClearance + 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(artistResults, key = HomeArtist::id) { artist ->
                                    SearchArtistRow(
                                        artist = artist,
                                        onClick = { onArtistClick(artist) },
                                    )
                                }
                            }
                        }
                    }

                    SearchFilter.ALBUMS -> {
                        if (albumResults.isEmpty()) {
                            SearchEmptyResults(query = query, filter = selectedFilter)
                        } else {
                            LazyColumn(
                                state = albumsScrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = bottomClearance + 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(albumResults, key = HomeAlbum::id) { album ->
                                    SearchAlbumRow(
                                        album = album,
                                        onClick = { onAlbumClick(album) },
                                    )
                                }
                            }
                        }
                    }

                    SearchFilter.SONGS -> {
                        if (results.isEmpty()) {
                            SearchEmptyResults(query = query, filter = selectedFilter)
                        } else {
                            LazyColumn(
                                state = songsScrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = bottomClearance + 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(results, key = HomeTrack::id) { track ->
                                    SearchTrackRow(
                                        track = track,
                                        contextTracks = results,
                                        onTrackClick = onTrackClick,
                                        onPlayWithContext = onPlayWithContext,
                                        onPlayNext = onPlayNext,
                                        onAddToQueue = onAddToQueue,
                                        ripTasks = ripTasks,
                                        pendingRipTrackIds = pendingRipTrackIds,
                                        onRipTrack = onRipTrack,
                                        onOpenRipVisualizer = onOpenRipVisualizer,
                                        onTrackSelected = onRecordSearchTrack,
                                    )
                                }
                            }
                        }
                    }

                    SearchFilter.PLAYLISTS -> {
                        if (playlistResults.isEmpty()) {
                            SearchEmptyResults(query = query, filter = selectedFilter)
                        } else {
                            LazyColumn(
                                state = playlistsScrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = bottomClearance + 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(playlistResults, key = HomePlaylist::id) { playlist ->
                                    SearchPlaylistRow(
                                        playlist = playlist,
                                        onClick = { onPlaylistClick(playlist) },
                                    )
                                }
                            }
                        }
                    }

                    SearchFilter.STATIONS -> {
                        if (stationResults.isEmpty()) {
                            SearchEmptyResults(query = query, filter = selectedFilter)
                        } else {
                            LazyColumn(
                                state = stationsScrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = bottomClearance + 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(stationResults, key = HomeStation::id) { station ->
                                    SearchStationRow(
                                        station = station,
                                        onClick = { onStationClick(station) },
                                    )
                                }
                            }
                        }
                    }

                    SearchFilter.MUSIC_VIDEOS -> {
                        if (musicVideoResults.isEmpty()) {
                            SearchEmptyResults(query = query, filter = selectedFilter)
                        } else {
                            LazyColumn(
                                state = musicVideosScrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = bottomClearance + 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(musicVideoResults, key = HomeTrack::id) { track ->
                                    SearchMusicVideoRow(
                                        track = track,
                                        contextTracks = musicVideoResults,
                                        onTrackClick = onTrackClick,
                                        onPlayWithContext = onPlayWithContext,
                                        onPlayNext = onPlayNext,
                                        onAddToQueue = onAddToQueue,
                                        ripTasks = ripTasks,
                                        pendingRipTrackIds = pendingRipTrackIds,
                                        onRipTrack = onRipTrack,
                                        onOpenRipVisualizer = onOpenRipVisualizer,
                                        onTrackSelected = onRecordSearchTrack,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            isSuggesting && hints.isEmpty() && suggestions.isEmpty() && albumSuggestions.isEmpty() && artistSuggestions.isEmpty() -> SearchLoading()
            hints.isEmpty() && suggestions.isEmpty() && albumSuggestions.isEmpty() && artistSuggestions.isEmpty() -> SearchNoSuggestions()
            else -> {
                LazyColumn(
                    state = suggestionsScrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomClearance + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (hints.isNotEmpty()) {
                        item(key = "hints_heading") {
                            SuggestionHeading(text = "Search suggestions")
                        }
                        items(hints, key = { "hint:$it" }) { hint ->
                            SearchHintRow(text = hint, onClick = { onSubmitSearch(hint) })
                        }
                    }
                    if (artistSuggestions.isNotEmpty()) {
                        item(key = "artist_suggestions_heading") {
                            SuggestionHeading(text = "Artists")
                        }
                        items(artistSuggestions, key = HomeArtist::id) { artist ->
                            SearchArtistRow(
                                artist = artist,
                                onClick = {
                                    onSubmitSearch(artist.name)
                                    onArtistClick(artist)
                                },
                            )
                        }
                    }
                    if (albumSuggestions.isNotEmpty()) {
                        item(key = "album_suggestions_heading") {
                            SuggestionHeading(text = "Albums")
                        }
                        items(albumSuggestions, key = HomeAlbum::id) { album ->
                            SearchAlbumRow(
                                album = album,
                                onClick = {
                                    onSubmitSearch(album.title)
                                    onAlbumClick(album)
                                },
                            )
                        }
                    }
                    if (suggestions.isNotEmpty()) {
                        item(key = "top_results_heading") {
                            val hasOtherSuggestions =
                                albumSuggestions.isNotEmpty() || artistSuggestions.isNotEmpty()
                            SuggestionHeading(text = if (hasOtherSuggestions) "Songs" else "Top results")
                        }
                        items(suggestions, key = HomeTrack::id) { track ->
                            SearchTrackRow(
                                track = track,
                                contextTracks = suggestions,
                                onTrackClick = onTrackClick,
                                onPlayWithContext = onPlayWithContext,
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                ripTasks = ripTasks,
                                pendingRipTrackIds = pendingRipTrackIds,
                                onRipTrack = onRipTrack,
                                onOpenRipVisualizer = onOpenRipVisualizer,
                                onTrackSelected = onRecordSearchTrack,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTrackRow(
    track: HomeTrack,
    contextTracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
    onPlayWithContext: ((HomeTrack, List<HomeTrack>) -> Unit)?,
    onPlayNext: ((HomeTrack) -> Unit)?,
    onAddToQueue: ((HomeTrack) -> Unit)?,
    ripTasks: List<RipTaskSnapshot>,
    pendingRipTrackIds: Set<String>,
    onRipTrack: (HomeTrack) -> Unit,
    onOpenRipVisualizer: () -> Unit,
    onTrackSelected: (HomeTrack) -> Unit = {},
    onRemoveFromHistory: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val sourceTrackId = track.providerTrackId
    val ripTask = remember(ripTasks, sourceTrackId) {
        ripTasks.firstOrNull {
            it.sourceTrackId == sourceTrackId
        }
    }
    val isRipPending = sourceTrackId != null && sourceTrackId in pendingRipTrackIds
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                onTrackSelected(track)
                if (onPlayWithContext != null) {
                    onPlayWithContext(track, contextTracks)
                } else {
                    onTrackClick(track)
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
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
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TrackCodecBadges(
                    track = track,
                    height = 10.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }

        if (sourceTrackId != null && (!track.isCached || ripTask != null || isRipPending)) {
            RipSearchAction(
                task = ripTask,
                isPending = isRipPending,
                onDownload = { onRipTrack(track) },
                onOpenProgress = onOpenRipVisualizer,
            )
        }

        if (onRemoveFromHistory != null) {
            IconButton(
                onClick = onRemoveFromHistory,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_clear),
                    contentDescription = "Remove ${track.title} from search history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (onPlayNext != null || onAddToQueue != null) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play Next") },
                            onClick = {
                                menuExpanded = false
                                onPlayNext(track)
                            },
                        )
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Queue") },
                            onClick = {
                                menuExpanded = false
                                onAddToQueue(track)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RipSearchAction(
    task: RipTaskSnapshot?,
    isPending: Boolean,
    onDownload: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val isInProgress = task != null || isPending
    val progress = task?.let {
        (it.percent ?: it.download?.percent ?: it.upload?.percent)
            ?.div(100f)
            ?.coerceIn(0f, 1f)
            ?: if (it.completed) 1f else null
    }
    IconButton(
        onClick = if (isInProgress) onOpenProgress else onDownload,
        modifier = Modifier
            .size(40.dp)
            .semantics {
                contentDescription = when {
                    !isInProgress -> "Add to rip"
                    task?.completed == true -> "Rip complete"
                    progress != null -> "Rip progress ${(progress * 100).toInt()} percent"
                    else -> "Rip in progress"
                }
            },
    ) {
        if (!isInProgress) {
            Icon(
                painter = painterResource(R.drawable.ic_cloud_download),
                contentDescription = "Add to rip",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        } else if (progress != null) {
            val activeColor = MaterialTheme.colorScheme.primary
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(28.dp),
                color = activeColor,
                trackColor = Color.Transparent,
                amplitude = { value -> if (value > 0f) 1f else 0f },
                wavelength = WavyProgressIndicatorDefaults.CircularWavelength,
                waveSpeed = WavyProgressIndicatorDefaults.CircularWavelength / 2f,
            )
        } else {
            val activeColor = MaterialTheme.colorScheme.primary
            CircularWavyProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = activeColor,
                trackColor = Color.Transparent,
                wavelength = WavyProgressIndicatorDefaults.CircularWavelength,
                waveSpeed = WavyProgressIndicatorDefaults.CircularWavelength / 2f,
            )
        }
    }
}

@Composable
private fun SearchAlbumRow(
    album: HomeAlbum,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Album • ${album.artist}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchAlbumCard(
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
private fun SearchArtistRow(
    artist: HomeArtist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
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
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Artist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchArtistCard(
    artist: HomeArtist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
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
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SearchHintRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchHistoryList(
    state: LazyListState,
    bottomClearance: Dp,
    history: List<String>,
    tracks: List<HomeTrack>,
    onSelect: (String) -> Unit,
    onTrackClick: (HomeTrack) -> Unit,
    onPlayWithContext: ((HomeTrack, List<HomeTrack>) -> Unit)?,
    onTrackSelected: (HomeTrack) -> Unit,
    onRemove: (String) -> Unit,
    onRemoveTrack: (HomeTrack) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomClearance + 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "search_history_heading") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (history.isNotEmpty()) "Recent searches" else "Recent tracks",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) {
                    Text("Clear all")
                }
            }
        }
        if (history.isNotEmpty()) {
            items(history, key = { "history:$it" }) { term ->
                SearchHistoryRow(
                    query = term,
                    onClick = { onSelect(term) },
                    onRemove = { onRemove(term) },
                )
            }
        }
        if (tracks.isNotEmpty() && history.isNotEmpty()) {
            item(key = "recent_tracks_heading") {
                SuggestionHeading(text = "Recent tracks")
            }
        }
        if (tracks.isNotEmpty()) {
            items(tracks, key = { "track:${it.providerTrackId ?: it.id}" }) { track ->
                SearchHistoryTrackRow(
                    track = track,
                    contextTracks = tracks,
                    onTrackClick = onTrackClick,
                    onPlayWithContext = onPlayWithContext,
                    onTrackSelected = onTrackSelected,
                    onRemoveFromHistory = { onRemoveTrack(track) },
                )
            }
        }
    }
}

@Composable
private fun SearchHistoryTrackRow(
    track: HomeTrack,
    contextTracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
    onPlayWithContext: ((HomeTrack, List<HomeTrack>) -> Unit)?,
    onTrackSelected: (HomeTrack) -> Unit,
    onRemoveFromHistory: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                onTrackSelected(track)
                if (onPlayWithContext != null) {
                    onPlayWithContext(track, contextTracks)
                } else {
                    onTrackClick(track)
                }
            }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(track.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
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
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onRemoveFromHistory,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_clear),
                contentDescription = "Remove ${track.title} from search history",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SearchHistoryRow(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_clockwise_clock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_clear),
                contentDescription = "Remove $query from search history",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SuggestionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SearchLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 170.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun SearchPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 170.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.search_placeholder_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.search_placeholder_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchEmptyResults(
    query: String,
    filter: SearchFilter = SearchFilter.TOP_RESULTS,
) {
    val categoryName = when (filter) {
        SearchFilter.TOP_RESULTS -> "results"
        SearchFilter.ARTISTS -> "artists"
        SearchFilter.ALBUMS -> "albums"
        SearchFilter.SONGS -> "songs"
        SearchFilter.PLAYLISTS -> "playlists"
        SearchFilter.STATIONS -> "stations"
        SearchFilter.MUSIC_VIDEOS -> "music videos"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 170.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No $categoryName found for \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchFilterRow(
    selectedFilter: SearchFilter,
    onFilterSelected: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = SearchFilter.ALL
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(filters, key = { _, filter -> filter.name }) { index, filter ->
            val isSelected = filter == selectedFilter
            val shapes = when {
                filters.size <= 1 -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == filters.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onFilterSelected(filter) },
                shapes = shapes,
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                icon = if (isSelected) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else null,
            ) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SearchPlaylistCard(
    playlist: HomePlaylist,
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
            if (!playlist.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(playlist.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = playlist.title,
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
            text = playlist.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = playlist.curator ?: "Playlist",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchPlaylistRow(
    playlist: HomePlaylist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (!playlist.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(playlist.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = playlist.title,
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
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (!playlist.curator.isNullOrBlank()) "Playlist • ${playlist.curator}" else "Playlist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchStationCard(
    station: HomeStation,
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
            if (!station.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(station.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = station.title,
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
            text = station.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Station",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchStationRow(
    station: HomeStation,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (!station.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(station.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = station.title,
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
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = station.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Station",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchMusicVideoCard(
    track: HomeTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(96.dp)
                .clip(RoundedCornerShape(12.dp))
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
                    modifier = Modifier.size(36.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchMusicVideoRow(
    track: HomeTrack,
    contextTracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
    onPlayWithContext: ((HomeTrack, List<HomeTrack>) -> Unit)?,
    onPlayNext: ((HomeTrack) -> Unit)?,
    onAddToQueue: ((HomeTrack) -> Unit)?,
    ripTasks: List<RipTaskSnapshot>,
    pendingRipTrackIds: Set<String>,
    onRipTrack: (HomeTrack) -> Unit,
    onOpenRipVisualizer: () -> Unit,
    onTrackSelected: (HomeTrack) -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val sourceTrackId = track.providerTrackId
    val ripTask = remember(ripTasks, sourceTrackId) {
        ripTasks.firstOrNull { it.sourceTrackId == sourceTrackId }
    }
    val isRipPending = sourceTrackId != null && sourceTrackId in pendingRipTrackIds

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                onTrackSelected(track)
                if (onPlayWithContext != null) {
                    onPlayWithContext(track, contextTracks)
                } else {
                    onTrackClick(track)
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 48.dp)
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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Music Video • ${track.artist}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TrackCodecBadges(
                    track = track,
                    height = 10.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }

        if (sourceTrackId != null && (!track.isCached || ripTask != null || isRipPending)) {
            RipSearchAction(
                task = ripTask,
                isPending = isRipPending,
                onDownload = { onRipTrack(track) },
                onOpenProgress = onOpenRipVisualizer,
            )
        }

        if (onPlayNext != null || onAddToQueue != null) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play Next") },
                            onClick = {
                                menuExpanded = false
                                onPlayNext(track)
                            },
                        )
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Queue") },
                            onClick = {
                                menuExpanded = false
                                onAddToQueue(track)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchNoSuggestions() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No suggestions",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val SUGGESTION_DEBOUNCE_MS = 300L
private const val MIN_SUGGESTION_QUERY_LENGTH = 2
private const val MAX_HINTS = 5
private const val MAX_TOP_RESULTS = 5
