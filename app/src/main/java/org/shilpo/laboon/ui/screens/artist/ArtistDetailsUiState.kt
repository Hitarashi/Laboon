package org.shilpo.laboon.ui.screens.artist

import org.shilpo.laboon.home.ArtistDetailsResult
import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyricsporn.LyricspornArtist

sealed interface ArtistDetailsUiState {
    data object Loading : ArtistDetailsUiState

    data class Loaded(
        val artist: LyricspornArtist,
        val topSongs: List<HomeTrack>,
        val latestRelease: HomeAlbum?,
        val albums: List<HomeAlbum>,
        val singles: List<HomeAlbum>,
        val similarArtists: List<HomeArtist>,
    ) : ArtistDetailsUiState

    data object NotFound : ArtistDetailsUiState

    data class Failed(val reason: ArtistDetailsResult.FailureReason) : ArtistDetailsUiState
}

fun ArtistDetailsResult.toUiState(): ArtistDetailsUiState = when (this) {
    is ArtistDetailsResult.Success -> ArtistDetailsUiState.Loaded(
        artist = artist,
        topSongs = topSongs,
        latestRelease = latestRelease,
        albums = albums,
        singles = singles,
        similarArtists = similarArtists,
    )

    ArtistDetailsResult.NotFound -> ArtistDetailsUiState.NotFound
    is ArtistDetailsResult.Failure -> ArtistDetailsUiState.Failed(reason)
}
