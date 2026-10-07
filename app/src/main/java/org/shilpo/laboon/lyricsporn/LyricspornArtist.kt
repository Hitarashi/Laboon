package org.shilpo.laboon.lyricsporn

data class LyricspornArtist(
    val id: String,
    val name: String,
    val url: String? = null,
    val artworkUrl: String? = null,
    val genres: List<String> = emptyList(),
    val editorialNotes: String? = null,
    val topSongs: List<LyricspornCatalogItem> = emptyList(),
    val latestRelease: LyricspornCatalogItem? = null,
    val fullAlbums: List<LyricspornCatalogItem> = emptyList(),
    val singles: List<LyricspornCatalogItem> = emptyList(),
    val similarArtists: List<LyricspornCatalogItem> = emptyList(),
)
