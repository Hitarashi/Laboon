package org.shilpo.laboon.ui.screens.search

import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist
import org.shilpo.laboon.home.HomePlaylist
import org.shilpo.laboon.home.HomeStation
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem
import org.shilpo.laboon.search.SearchFilter

data class SearchScreenResumeState(
    val query: String = "",
    val selectedFilter: SearchFilter = SearchFilter.TOP_RESULTS,
    val searchRequest: String? = null,
    val isSuggesting: Boolean = false,
    val isSearching: Boolean = false,
    val hints: List<String> = emptyList(),
    val suggestions: List<HomeTrack> = emptyList(),
    val albumSuggestions: List<HomeAlbum> = emptyList(),
    val artistSuggestions: List<HomeArtist> = emptyList(),
    val results: List<HomeTrack> = emptyList(),
    val albumResults: List<HomeAlbum> = emptyList(),
    val artistResults: List<HomeArtist> = emptyList(),
    val playlistResults: List<HomePlaylist> = emptyList(),
    val stationResults: List<HomeStation> = emptyList(),
    val musicVideoResults: List<HomeTrack> = emptyList(),
    val topResults: List<LyricspornCatalogItem> = emptyList(),
)
