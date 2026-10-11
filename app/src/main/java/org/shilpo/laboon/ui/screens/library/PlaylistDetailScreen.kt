@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.SavedPlaylist
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.theme.RoundedSans
import org.shilpo.laboon.ui.screens.home.HomeArtwork
import org.shilpo.laboon.ui.screens.home.LocalTrackFavoriteActions
import org.shilpo.laboon.ui.screens.home.isEffectiveFavorite
import org.shilpo.laboon.ui.screens.player.queue.PlayingEqIcon
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun PlaylistDetailScreen(
    playlist: SavedPlaylist,
    tracks: List<HomeTrack>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    currentTrack: HomeTrack?,
    isPlaying: Boolean,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPlayTrack: (HomeTrack, List<HomeTrack>) -> Unit,
    onTrackOptions: (HomeTrack, List<HomeTrack>) -> Unit,
    onReplaceTracks: suspend (List<HomeTrack>) -> Boolean,
    onRenamePlaylist: suspend (String) -> Boolean,
    onDeletePlaylist: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    var orderedTracks by remember(playlist.id, tracks) { mutableStateOf(tracks) }
    var sortOption by remember(playlist.id) { mutableStateOf(SongsLibrarySortOption.DEFAULT_ORDER) }
    var sortDescending by remember(playlist.id) { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(playlist.id, playlist.name) { mutableStateOf(playlist.name) }
    var isSavingTracks by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var removeMode by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }
    var hasReordered by remember { mutableStateOf(false) }

    fun handleBack() {
        if (reorderMode || removeMode) {
            reorderMode = false
            removeMode = false
        } else {
            onBack()
        }
    }

    BackHandler(enabled = reorderMode || removeMode, onBack = ::handleBack)

    LaunchedEffect(playlist.id, tracks) {
        orderedTracks = tracks
    }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            orderedTracks = orderedTracks.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            hasReordered = true
        },
    )

    suspend fun persistTracks(updatedTracks: List<HomeTrack>): Boolean {
        if (isSavingTracks) return false
        isSavingTracks = true
        val success = try {
            onReplaceTracks(updatedTracks)
        } finally {
            isSavingTracks = false
        }
        if (success) orderedTracks = updatedTracks else orderedTracks = tracks
        return success
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && hasReordered) {
            hasReordered = false
            persistTracks(orderedTracks)
        }
    }

    val visibleTracks = remember(orderedTracks, sortOption, sortDescending, reorderMode) {
        when {
            reorderMode || sortOption == SongsLibrarySortOption.DEFAULT_ORDER -> orderedTracks
            sortOption == SongsLibrarySortOption.TITLE -> if (sortDescending) {
                orderedTracks.sortedByDescending { it.title.lowercase() }
            } else {
                orderedTracks.sortedBy { it.title.lowercase() }
            }
            sortOption == SongsLibrarySortOption.ARTIST -> if (sortDescending) {
                orderedTracks.sortedByDescending { it.artist.lowercase() }
            } else {
                orderedTracks.sortedBy { it.artist.lowercase() }
            }
            sortOption == SongsLibrarySortOption.ALBUM -> if (sortDescending) {
                orderedTracks.sortedByDescending { it.album.orEmpty().lowercase() }
            } else {
                orderedTracks.sortedBy { it.album.orEmpty().lowercase() }
            }
            sortOption == SongsLibrarySortOption.DURATION -> {
                val known = orderedTracks.filter { it.durationMs != null }
                val unknown = orderedTracks.filter { it.durationMs == null }
                (if (sortDescending) known.sortedByDescending { it.durationMs }
                else known.sortedBy { it.durationMs }) + unknown
            }
            else -> orderedTracks
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = playlist.name,
                        modifier = Modifier.padding(start = 8.dp),
                        fontFamily = RoundedSans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text = playlistSubtitle(orderedTracks),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = RoundedSans),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 10.dp),
                        onClick = ::handleBack,
                    ) {
                        Icon(
                            painter = materialSymbolPainterResource("arrow_back"),
                            contentDescription = stringResource(R.string.library_playlist_back),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            painter = materialSymbolPainterResource("sort"),
                            contentDescription = stringResource(R.string.library_playlist_sort),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(
                        modifier = Modifier.padding(end = 10.dp),
                        onClick = { showOptionsSheet = true },
                    ) {
                        Icon(
                            painter = materialSymbolPainterResource("more_vert"),
                            contentDescription = stringResource(R.string.library_playlist_options),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        visibleTracks.firstOrNull()?.let { onPlayTrack(it, visibleTracks) }
                    },
                    enabled = visibleTracks.isNotEmpty() && !isSavingTracks,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(
                        topStart = 60.dp,
                        bottomStart = 60.dp,
                        topEnd = 14.dp,
                        bottomEnd = 14.dp,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(materialSymbolPainterResource("play_arrow"), null, Modifier.size(20.dp))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.library_playlist_play), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        val shuffled = visibleTracks.shuffled()
                        shuffled.firstOrNull()?.let { onPlayTrack(it, shuffled) }
                    },
                    enabled = visibleTracks.isNotEmpty() && !isSavingTracks,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(
                        topStart = 14.dp,
                        bottomStart = 14.dp,
                        topEnd = 60.dp,
                        bottomEnd = 60.dp,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(materialSymbolPainterResource("shuffle"), null, Modifier.size(20.dp))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.library_playlist_shuffle), maxLines = 1)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
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
                    when {
                        isLoading && tracks.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(48.dp))
                        }
                        orderedTracks.isEmpty() -> PlaylistEmptyContent(
                            bottomContentPadding = bottomContentPadding,
                        )
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                top = 12.dp,
                                bottom = bottomContentPadding,
                            ),
                        ) {
                            items(visibleTracks, key = HomeTrack::id) { track ->
                                ReorderableItem(
                                    state = reorderableState,
                                    key = track.id,
                                    enabled = reorderMode && !isSavingTracks,
                                ) { isDragging ->
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.04f else 1f,
                                    label = "playlistTrackDragScale",
                                )
                                val trackIsCurrent = currentTrack?.let {
                                    TrackIdentity.isSameTrack(track, it)
                                } == true
                                PlaylistDetailTrackRow(
                                    track = track,
                                    isCurrentSong = trackIsCurrent,
                                    isPlaying = isPlaying && trackIsCurrent,
                                    isRemoveMode = removeMode,
                                    isReorderMode = reorderMode,
                                    onClick = { onPlayTrack(track, visibleTracks) },
                                    onMoreOptionsClick = { onTrackOptions(track, visibleTracks) },
                                    onRemoveClick = {
                                        val index = orderedTracks.indexOfFirst { it.id == track.id }
                                        if (index >= 0) scope.launch {
                                            val updated = orderedTracks.toMutableList().apply { removeAt(index) }
                                            persistTracks(updated)
                                        }
                                    },
                                    dragHandleModifier = if (reorderMode) {
                                        Modifier.draggableHandle()
                                    } else {
                                        Modifier
                                    },
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                )
                                }
                            }
                        }
                }
            }
        }
    }
    }

    if (showSortSheet) {
        SongsLibrarySortBottomSheet(
            selectedOption = sortOption,
            isDescending = sortDescending,
            onDismiss = { showSortSheet = false },
            onOptionSelected = {
                sortOption = it
                removeMode = false
                reorderMode = false
            },
            onDirectionToggle = { sortDescending = !sortDescending },
        )
    }

    if (showOptionsSheet) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.library_playlist_options),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            showOptionsSheet = false
                            removeMode = !removeMode
                            reorderMode = false
                            sortOption = SongsLibrarySortOption.DEFAULT_ORDER
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 66.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            painter = materialSymbolPainterResource("remove"),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.library_playlist_remove_songs),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            showOptionsSheet = false
                            reorderMode = !reorderMode
                            removeMode = false
                            sortOption = SongsLibrarySortOption.DEFAULT_ORDER
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 66.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            painter = materialSymbolPainterResource("drag_indicator"),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.library_playlist_reorder_songs),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!playlist.isFavorites) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                showOptionsSheet = false
                                renameText = playlist.name
                                showRenameDialog = true
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 66.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(
                                painter = materialSymbolPainterResource("edit"),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.library_playlist_rename),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            onClick = {
                                showOptionsSheet = false
                                onDeletePlaylist()
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 66.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(
                                painter = materialSymbolPainterResource("delete"),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.library_playlist_delete),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRenaming) showRenameDialog = false },
            title = { Text(stringResource(R.string.library_playlist_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.library_playlist_name)) },
                    singleLine = true,
                    enabled = !isRenaming,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isRenaming && renameText.isNotBlank() && renameText.trim() != playlist.name,
                    onClick = {
                        scope.launch {
                            isRenaming = true
                            try {
                                if (onRenamePlaylist(renameText.trim())) showRenameDialog = false
                            } finally {
                                isRenaming = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.library_playlist_rename_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }, enabled = !isRenaming) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

}

@Composable
private fun PlaylistDetailTrackRow(
    track: HomeTrack,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    isRemoveMode: Boolean,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    onRemoveClick: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val cornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong) 60.dp else 22.dp,
        label = "playlistTrackCornerRadius",
    )
    val artworkCornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong) 60.dp else 8.dp,
        label = "playlistArtworkCornerRadius",
    )
    val elevation by animateDpAsState(
        targetValue = if (isReorderMode) 4.dp else 1.dp,
        label = "playlistTrackElevation",
    )
    val itemShape = RoundedCornerShape(cornerRadius)
    val moreButtonContainer = if (isCurrentSong) colors.tertiaryContainer else colors.surfaceContainerHigh
    val moreButtonContent = if (isCurrentSong) colors.onTertiaryContainer else colors.onSurface

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(itemShape)
            .clickable(enabled = track.isPlayable && !isRemoveMode, onClick = onClick),
        shape = itemShape,
        color = colors.surfaceContainerLowest,
        tonalElevation = elevation,
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = isReorderMode) {
                IconButton(
                    onClick = {},
                    modifier = dragHandleModifier.size(40.dp),
                ) {
                    Icon(
                        painter = materialSymbolPainterResource("drag_indicator"),
                        contentDescription = stringResource(R.string.library_playlist_drag_song),
                        modifier = Modifier.size(24.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(if (isReorderMode) 6.dp else 12.dp))
                HomeArtwork(
                    artworkUrl = track.artworkUrl,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(artworkCornerRadius)),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = track.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isCurrentSong) colors.primary else colors.onSurface,
                            fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = RoundedSans),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (track.isEffectiveFavorite(LocalTrackFavoriteActions.current)) {
                            Icon(
                                painter = materialSymbolPainterResource("favorite", filled = true),
                                contentDescription = "Favorite",
                                tint = colors.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCurrentSong) colors.primary.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = RoundedSans),
                    )
                }
                if (isCurrentSong && isPlaying) {
                    PlayingEqIcon(
                        modifier = Modifier.padding(start = 8.dp).size(width = 18.dp, height = 16.dp),
                        color = colors.secondary,
                        isPlaying = true,
                    )
                    Spacer(Modifier.width(4.dp))
                    if (!isRemoveMode) Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                FilledIconButton(
                    onClick = onMoreOptionsClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = moreButtonContainer,
                        contentColor = moreButtonContent,
                    ),
                    modifier = Modifier.size(36.dp).padding(end = 14.dp),
                ) {
                    Icon(
                        painter = materialSymbolPainterResource("more_vert"),
                        contentDescription = stringResource(R.string.library_more_options, track.title),
                        modifier = Modifier.size(24.dp),
                    )
                }
                AnimatedVisibility(visible = isRemoveMode) {
                    FilledIconButton(
                        onClick = onRemoveClick,
                        enabled = track.isPlayable,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.surfaceContainer,
                            contentColor = colors.onSurface,
                        ),
                        modifier = Modifier.width(40.dp).height(40.dp).padding(start = 4.dp, end = 8.dp),
                    ) {
                        Icon(
                            painter = materialSymbolPainterResource("close"),
                            contentDescription = stringResource(R.string.library_playlist_remove_song),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistEmptyContent(
    bottomContentPadding: Dp,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = bottomContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = materialSymbolPainterResource("music_off"),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.library_playlist_empty_detail_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.library_playlist_empty_detail_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun playlistSubtitle(tracks: List<HomeTrack>): String {
    val durationSeconds = tracks.sumOf { (it.durationMs ?: 0L).coerceAtLeast(0L) } / 1000L
    val count = tracks.size
    val countText = "$count ${if (count == 1) "song" else "songs"}"
    if (durationSeconds <= 0L) return countText
    val hours = durationSeconds / 3600
    val minutes = durationSeconds % 3600 / 60
    val durationText = if (hours > 0) {
        "$hours hr${if (minutes > 0) " $minutes min" else ""}"
    } else {
        "$minutes min"
    }
    return "$countText • $durationText"
}
