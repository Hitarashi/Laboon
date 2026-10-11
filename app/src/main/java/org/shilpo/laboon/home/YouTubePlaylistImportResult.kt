package org.shilpo.laboon.home

sealed interface YouTubePlaylistImportResult {
    data class Completed(
        val playlist: ListenBrainzPlaylist?,
        val sourceTrackCount: Int,
        val importedTrackCount: Int,
        val skippedTracks: List<YouTubePlaylistSkippedTrack>,
        val isTruncated: Boolean,
    ) : YouTubePlaylistImportResult

    data object ListenBrainzNotConnected : YouTubePlaylistImportResult
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
        LISTENBRAINZ_LOOKUP_FAILED,
        NO_LISTENBRAINZ_MATCH,
    }
}

internal fun skippedYouTubePlaylistTracks(
    sourceTracks: List<YouTubePlaylistTrack>,
    appleMatchedSourceIndices: Set<Int>,
    resolvedSourceIndices: Set<Int>,
    lookupFailedSourceIndices: Set<Int>,
): List<YouTubePlaylistSkippedTrack> = sourceTracks.mapIndexedNotNull { index, track ->
    if (index in resolvedSourceIndices) return@mapIndexedNotNull null

    val reason = when {
        index !in appleMatchedSourceIndices -> YouTubePlaylistSkippedTrack.Reason.NO_APPLE_MATCH
        index in lookupFailedSourceIndices -> YouTubePlaylistSkippedTrack.Reason.LISTENBRAINZ_LOOKUP_FAILED
        else -> YouTubePlaylistSkippedTrack.Reason.NO_LISTENBRAINZ_MATCH
    }
    YouTubePlaylistSkippedTrack(track, reason)
}
