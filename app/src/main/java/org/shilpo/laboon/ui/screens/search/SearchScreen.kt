@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackAvailability
import org.shilpo.laboon.rip.RipTaskSnapshot
import org.shilpo.laboon.search.CachedTrackAvailability
import org.shilpo.laboon.search.SearchHistoryStore
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchRepositoryImpl
import org.shilpo.laboon.ui.design.CodecIcon

@Composable
fun SearchScreen(
    onTrackClick: (HomeTrack) -> Unit = {},
    onPlayWithContext: ((HomeTrack, List<HomeTrack>) -> Unit)? = null,
    onPlayNext: ((HomeTrack) -> Unit)? = null,
    onAddToQueue: ((HomeTrack) -> Unit)? = null,
    ripTasks: List<RipTaskSnapshot> = emptyList(),
    pendingRipTrackIds: Set<String> = emptySet(),
    onRipTrack: (HomeTrack) -> Unit = {},
    onOpenRipVisualizer: () -> Unit = {},
    modifier: Modifier = Modifier,
    searchRepository: SearchRepository? = null,
    onObservedTracks: (List<HomeTrack>) -> Unit = {},
    ripCompletions: Flow<String>? = null,
    resolveAvailability: (suspend (String) -> Map<String, CachedTrackAvailability>)? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val repository = remember(searchRepository, context) {
        searchRepository
            ?: SearchRepositoryImpl(SessionStore(SharedPreferencesKeyValueStore(context)))
    }
    val searchHistoryStore = remember(context) {
        SearchHistoryStore(SharedPreferencesKeyValueStore(context))
    }

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
    var results by remember { mutableStateOf<List<HomeTrack>>(emptyList()) }

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
        results = emptyList()
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
            isSuggesting = false
            return@LaunchedEffect
        }

        isSuggesting = true
        delay(SUGGESTION_DEBOUNCE_MS)
        val hintRequest =
            async { runCatching { repository.searchHints(term) }.getOrDefault(emptyList()) }
        val songRequest = async {
            runCatching { repository.searchSuggestions(term) }.getOrDefault(emptyList())
        }
        hints = hintRequest.await().distinct().take(MAX_HINTS)
        suggestions = songRequest.await().distinctBy(HomeTrack::id).take(MAX_TOP_RESULTS)
        isSuggesting = false
    }

    LaunchedEffect(searchRequest) {
        val term = searchRequest ?: return@LaunchedEffect
        isSearching = true
        val found = runCatching { repository.search(term) }.getOrDefault(emptyList())
        if (searchRequest == term) {
            results = found.distinctBy(HomeTrack::id)
            isSearching = false
        }
    }



    LaunchedEffect(results, suggestions, trackHistory) {
        onObservedTracks(
            buildList {
                addAll(results)
                addAll(suggestions)
                addAll(trackHistory)
            }.distinctBy(HomeTrack::id)
        )
    }

    LaunchedEffect(ripCompletions) {
        val stream = ripCompletions ?: return@LaunchedEffect
        val lookup = resolveAvailability ?: return@LaunchedEffect
        stream.collect { providerTrackId ->
            val availability = lookup(providerTrackId)
            if (availability.isEmpty()) return@collect
            results = TrackAvailability.apply(results, availability)
            suggestions = TrackAvailability.apply(suggestions, availability)
            trackHistory = TrackAvailability.apply(trackHistory, availability)
        }
    }

    val showingSearchResults = searchRequest != null && searchRequest == query.trim()

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
                if (it.trim() != searchRequest) {
                    searchRequest = null
                    results = emptyList()
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
                            searchRequest = null
                            isSearching = false
                            isSuggesting = false
                            hints = emptyList()
                            suggestions = emptyList()
                            results = emptyList()
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
            keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                isSearching -> SearchLoading()
                query.isBlank() && (searchHistory.isNotEmpty() || trackHistory.isNotEmpty()) ->
                    SearchHistoryList(
                        history = searchHistory,
                        tracks = trackHistory,
                        onSelect = ::submitSearch,
                        onTrackClick = onTrackClick,
                        onPlayWithContext = onPlayWithContext,
                        onTrackSelected = ::recordSearchTrack,
                        onRemove = { term ->
                            searchHistory = searchHistoryStore.remove(term)
                        },
                        onRemoveTrack = { track ->
                            trackHistory = searchHistoryStore.removeTrack(track)
                        },
                        onClear = {
                            searchHistoryStore.clear()
                            searchHistory = emptyList()
                            trackHistory = emptyList()
                        },
                    )

                query.isBlank() -> SearchPlaceholder()
                showingSearchResults && results.isEmpty() -> SearchEmptyResults(query)
                showingSearchResults -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp),
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
                                onTrackSelected = ::recordSearchTrack,
                            )
                        }
                    }
                }

                isSuggesting && hints.isEmpty() && suggestions.isEmpty() -> SearchLoading()
                hints.isEmpty() && suggestions.isEmpty() -> SearchNoSuggestions()
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (hints.isNotEmpty()) {
                            item(key = "hints_heading") {
                                SuggestionHeading(text = "Search suggestions")
                            }
                            items(hints, key = { "hint:$it" }) { hint ->
                                SearchHintRow(text = hint, onClick = { submitSearch(hint) })
                            }
                        }
                        if (suggestions.isNotEmpty()) {
                            item(key = "top_results_heading") {
                                SuggestionHeading(text = "Top results")
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
                                    onTrackSelected = ::recordSearchTrack,
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
            it.provider.equals("apple", ignoreCase = true) && it.sourceTrackId == sourceTrackId
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
                track.availableFormats.forEach { format ->
                    CodecIcon(
                        codec = format,
                        height = 10.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 160.dp),
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
    }
}

@Composable
private fun SearchPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
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
private fun SearchEmptyResults(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No results found for \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
