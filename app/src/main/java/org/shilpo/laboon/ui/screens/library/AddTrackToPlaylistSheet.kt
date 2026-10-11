@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.SavedPlaylist
import org.shilpo.laboon.home.previewArtworkUrls
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.theme.RoundedSans

@Composable
internal fun AddTrackToPlaylistSheet(
    playlists: List<SavedPlaylist>,
    membershipPlaylistIds: Set<String>,
    selectedPlaylistIds: Set<String>,
    isLoading: Boolean,
    isSaving: Boolean,
    canSave: Boolean,
    canManagePlaylists: Boolean,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onSetPlaylistSelected: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists
        else playlists.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val hasSelectionChanges = selectedPlaylistIds != membershipPlaylistIds
    val canSubmit = canSave && !isLoading && !isSaving &&
        (selectedPlaylistIds.isNotEmpty() || hasSelectionChanges)
    val fabAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (canSubmit) 1f else 0.4f,
        label = "playlistPickerSaveAlpha",
    )
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.library_playlist_picker_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = RoundedSans,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(stringResource(R.string.library_playlist_picker_search_hint))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = CircleShape,
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    painter = materialSymbolPainterResource("close"),
                                    contentDescription = stringResource(
                                        R.string.library_playlist_picker_clear_search,
                                    ),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    },
                )

                FilledTonalButton(
                    onClick = if (canManagePlaylists) onCreatePlaylist else onOpenSettings,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 10.dp, end = 16.dp)
                        .height(42.dp),
                    shape = RoundedCornerShape(26.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(
                        painter = materialSymbolPainterResource("playlist_add"),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.library_action_new),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }

                when {
                    isLoading -> Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }

                    !canManagePlaylists -> Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.library_playlist_connect_account),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    filteredPlaylists.isEmpty() -> Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(
                                if (playlists.isEmpty()) R.string.library_playlist_none_available
                                else R.string.library_playlist_picker_no_matches,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 8.dp,
                            end = 12.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredPlaylists, key = SavedPlaylist::id) { playlist ->
                            PlaylistPickerRow(
                                playlist = playlist,
                                checked = playlist.id in selectedPlaylistIds,
                                enabled = !isLoading && !isSaving,
                                onClick = {
                                    onSetPlaylistSelected(playlist.id, playlist.id !in selectedPlaylistIds)
                                },
                                onCheckedChange = { checked ->
                                    onSetPlaylistSelected(playlist.id, checked)
                                },
                            )
                        }
                    }
                }
            }

            MediumExtendedFloatingActionButton(
                onClick = { if (canSubmit) onSave() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 18.dp, end = 8.dp)
                    .graphicsLayer { alpha = fabAlpha },
                shape = CircleShape,
                icon = {
                    if (isSaving) {
                        ContainedLoadingIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            painter = materialSymbolPainterResource("save", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                text = {
                    Text(stringResource(R.string.library_playlist_picker_save))
                },
            )
        }
    }
}

@Composable
private fun PlaylistPickerRow(
    playlist: SavedPlaylist,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaylistPickerArtwork(playlist)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RoundedSans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.library_playlist_picker_track_count,
                        playlist.trackCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun PlaylistPickerArtwork(playlist: SavedPlaylist) {
    val artworkUrls = playlist.previewArtworkUrls
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUrls.isNotEmpty()) {
            PlaylistArtworkCollage(artworkUrls)
        } else {
            Icon(
                painter = materialSymbolPainterResource("queue_music", filled = true),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
