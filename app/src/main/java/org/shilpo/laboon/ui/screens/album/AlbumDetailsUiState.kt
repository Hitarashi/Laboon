package org.shilpo.laboon.ui.screens.album

import org.shilpo.laboon.home.AlbumDetailsResult
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyricsporn.LyricspornAlbum

sealed interface AlbumDetailsUiState {
    data object Loading : AlbumDetailsUiState

    data class Loaded(
        val album: LyricspornAlbum,
        val tracks: List<HomeTrack>,
    ) : AlbumDetailsUiState

    data object NotFound : AlbumDetailsUiState

    data class Failed(val reason: AlbumDetailsResult.FailureReason) : AlbumDetailsUiState
}

fun AlbumDetailsResult.toUiState(): AlbumDetailsUiState = when (this) {
    is AlbumDetailsResult.Success -> AlbumDetailsUiState.Loaded(album, tracks)
    AlbumDetailsResult.NotFound -> AlbumDetailsUiState.NotFound
    is AlbumDetailsResult.Failure -> AlbumDetailsUiState.Failed(reason)
}
