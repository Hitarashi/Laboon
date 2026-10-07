package org.shilpo.laboon.ui.screens.label

import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.lyricsporn.LyricspornRecordLabel

sealed interface RecordLabelUiState {
    data object Loading : RecordLabelUiState

    data class Loaded(
        val label: LyricspornRecordLabel,
        val latestReleases: List<HomeAlbum>,
        val topReleases: List<HomeAlbum>,
        val artists: List<HomeArtist> = emptyList(),
    ) : RecordLabelUiState

    data object NotFound : RecordLabelUiState

    data object Failed : RecordLabelUiState
}

fun LyricspornRecordLabel.toUiState(): RecordLabelUiState {
    val resolvedArtists = if (artists.isNotEmpty()) {
        artists
    } else {
        val allReleases = (latestReleases + topReleases).distinctBy { it.id }
        val artistSeparators = Regex("[,/&;、]|(?i)\\b(feat\\.?|ft\\.|with)\\b")
        allReleases.flatMap { album ->
            album.artist.split(artistSeparators)
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.equals("Various Artists", ignoreCase = true) }
        }.distinctBy { it.lowercase() }.map { artistName ->
            HomeArtist(
                id = "label_artist_${artistName.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                name = artistName,
                imageUrl = null,
                appleCatalogId = null,
            )
        }
    }

    return RecordLabelUiState.Loaded(
        label = this,
        latestReleases = latestReleases,
        topReleases = topReleases,
        artists = resolvedArtists,
    )
}
