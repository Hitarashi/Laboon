package org.shilpo.laboon.home

data class HomeTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val playCount: Long = 0,
    val source: String? = null,
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
    val state: SectionLoadState = SectionLoadState.IDLE,
    val data: List<T> = emptyList(),
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
        get() = rotation.state == SectionLoadState.LOADING ||
                recommended.state == SectionLoadState.LOADING ||
                topArtists.state == SectionLoadState.LOADING ||
                topAlbums.state == SectionLoadState.LOADING

    val isAllEmpty: Boolean
        get() = rotation.data.isEmpty() &&
                recommended.data.isEmpty() &&
                topArtists.data.isEmpty() &&
                topAlbums.data.isEmpty() &&
                topTracks.data.isEmpty() &&
                regionalTrending.data.isEmpty() &&
                globalTrending.data.isEmpty() &&
                weeklyPicks.data.isEmpty()

    val yourRotation: List<HomeTrack> get() = rotation.data
    val recommendedTracks: List<HomeTrack> get() = recommended.data
    val trendingSongs: List<HomeTrack> get() = regionalTrending.data
    val regionalTrendingSongs: List<HomeTrack> get() = regionalTrending.data
    val globalTrendingSongs: List<HomeTrack> get() = globalTrending.data
}

object HomeFeedDefaults {
    val defaultFeed = HomeFeedState(
        rotation = SectionState(state = SectionLoadState.LOADING),
        recommended = SectionState(state = SectionLoadState.LOADING),
        topArtists = SectionState(state = SectionLoadState.LOADING),
        topAlbums = SectionState(state = SectionLoadState.LOADING),
    )
}
