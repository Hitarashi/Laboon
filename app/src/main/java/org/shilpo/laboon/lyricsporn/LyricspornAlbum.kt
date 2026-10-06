package org.shilpo.laboon.lyricsporn

data class LyricspornAlbum(
    val id: String,
    val name: String,
    val artistName: String? = null,
    val artistUrl: String? = null,
    val url: String? = null,
    val artworkUrl: String? = null,
    val genres: List<String> = emptyList(),
    val releaseDate: String? = null,
    val trackCount: Int? = null,
    val contentRating: String? = null,
    val copyright: String? = null,
    val recordLabel: String? = null,
    val editorialNotes: String? = null,
    val tracks: List<LyricspornAlbumTrack> = emptyList(),
    val otherVersions: List<LyricspornAlbumVersion> = emptyList(),
) {
    val totalDurationMs: Long?
        get() = tracks.takeIf { albumTracks ->
            albumTracks.isNotEmpty() && albumTracks.all { it.durationMs != null }
        }?.sumOf { it.durationMs ?: 0L }
}

data class LyricspornAlbumTrack(
    val id: String,
    val name: String,
    val artistName: String? = null,
    val albumName: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val contentRating: String? = null,
    val url: String? = null,
)

data class LyricspornAlbumVersion(
    val id: String,
    val name: String,
    val artistName: String? = null,
    val artworkUrl: String? = null,
    val releaseDate: String? = null,
    val trackCount: Int? = null,
    val contentRating: String? = null,
)
