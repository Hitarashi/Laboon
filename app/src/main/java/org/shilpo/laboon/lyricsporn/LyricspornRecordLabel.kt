package org.shilpo.laboon.lyricsporn

import org.shilpo.laboon.home.HomeAlbum
import org.shilpo.laboon.home.HomeArtist

data class LyricspornRecordLabel(
    val id: String,
    val name: String,
    val url: String? = null,
    val description: String? = null,
    val artworkUrl: String? = null,
    val editorialArtworkUrl: String? = null,
    val latestReleases: List<HomeAlbum> = emptyList(),
    val topReleases: List<HomeAlbum> = emptyList(),
    val artists: List<HomeArtist> = emptyList(),
)
