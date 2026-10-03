package org.shilpo.laboon.home

data class TrackFormatVariant(
    val format: String,
    val backendTrackId: Int,
    val fileSizeBytes: Long? = null,
)

data class HomeTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val playCount: Long = 0,
    val source: String? = null,
    val streamUrl: String? = null,
    val backendTrackId: Int? = null,
    val isCached: Boolean = false,
    val codec: String? = null,
    val mbid: String? = null,
    val isrc: String? = null,
    val providerTrackId: String? = null,
    val availableFormats: List<String> = emptyList(),
    val availableVariants: List<TrackFormatVariant> = emptyList(),
    val durationMs: Long? = null,
)

data class HomeArtist(
    val id: String,
    val name: String,
    val playCount: Long = 0,
    val imageUrl: String? = null,
)

data class HomeAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val playCount: Long = 0,
)

enum class SectionLoadState {
    IDLE,
    LOADING,
    LOADED,
    ERROR,
}

data class SectionState<T>(
    val status: SectionLoadState = SectionLoadState.IDLE,
    val items: List<T> = emptyList(),
)

data class HomeFeedState(
    val rotation: SectionState<HomeTrack> = SectionState(),
    val recommended: SectionState<HomeTrack> = SectionState(),
    val topArtists: SectionState<HomeArtist> = SectionState(),
    val topAlbums: SectionState<HomeAlbum> = SectionState(),
    val topTracks: SectionState<HomeTrack> = SectionState(),
    val regionalTrending: SectionState<HomeTrack> = SectionState(),
    val globalTrending: SectionState<HomeTrack> = SectionState(),
    val regionName: String = "Regional",
    val weeklyPicks: SectionState<HomeTrack> = SectionState(),
) {
    val isInitialLoading: Boolean
        get() = rotation.status == SectionLoadState.LOADING ||
                recommended.status == SectionLoadState.LOADING ||
                topArtists.status == SectionLoadState.LOADING ||
                topAlbums.status == SectionLoadState.LOADING

    val isAllEmpty: Boolean
        get() = rotation.items.isEmpty() &&
                recommended.items.isEmpty() &&
                topArtists.items.isEmpty() &&
                topAlbums.items.isEmpty() &&
                topTracks.items.isEmpty() &&
                regionalTrending.items.isEmpty() &&
                globalTrending.items.isEmpty() &&
                weeklyPicks.items.isEmpty()

    val yourRotation: List<HomeTrack> get() = rotation.items
    val recommendedTracks: List<HomeTrack> get() = recommended.items
    val trendingSongs: List<HomeTrack> get() = regionalTrending.items
    val regionalTrendingSongs: List<HomeTrack> get() = regionalTrending.items
    val globalTrendingSongs: List<HomeTrack> get() = globalTrending.items
}

object HomeFeedDefaults {
    val defaultFeed = HomeFeedState(
        rotation = SectionState(status = SectionLoadState.LOADING),
        recommended = SectionState(status = SectionLoadState.LOADING),
        topArtists = SectionState(status = SectionLoadState.LOADING),
        topAlbums = SectionState(status = SectionLoadState.LOADING),
    )
}
