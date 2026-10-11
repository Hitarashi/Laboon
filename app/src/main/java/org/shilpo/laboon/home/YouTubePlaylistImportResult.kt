package org.shilpo.laboon.home

sealed interface YouTubePlaylistImportResult {
    data class Completed(
        val playlist: SavedPlaylist?,
        val sourceTrackCount: Int,
        val importedTrackCount: Int,
        val skippedTracks: List<YouTubePlaylistSkippedTrack>,
        val isTruncated: Boolean,
    ) : YouTubePlaylistImportResult

    data object InvalidYouTubeLink : YouTubePlaylistImportResult
    data object PlaylistUnavailable : YouTubePlaylistImportResult
    data object PlaylistHasNoTracks : YouTubePlaylistImportResult
    data object CouldNotCreatePlaylist : YouTubePlaylistImportResult
}

data class YouTubePlaylistSkippedTrack(
    val track: YouTubePlaylistTrack,
    val reason: Reason,
) {
    enum class Reason {
        NO_APPLE_MATCH,
    }
}

internal fun skippedYouTubePlaylistTracks(
    sourceTracks: List<YouTubePlaylistTrack>,
    resolvedSourceIndices: Set<Int>,
): List<YouTubePlaylistSkippedTrack> = sourceTracks.mapIndexedNotNull { index, track ->
    if (index in resolvedSourceIndices) return@mapIndexedNotNull null
    YouTubePlaylistSkippedTrack(track, YouTubePlaylistSkippedTrack.Reason.NO_APPLE_MATCH)
}
