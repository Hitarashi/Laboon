package org.shilpo.laboon.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.lyricsporn.LyricspornBrowseGrouping
import org.shilpo.laboon.lyricsporn.LyricspornBrowseSection
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.search.SearchRepository

data class BrowseSectionData(
    val section: LyricspornBrowseSection,
    val tracks: List<HomeTrack> = emptyList(),
    val albums: List<HomeAlbum> = emptyList(),
    val playlists: List<HomePlaylist> = emptyList(),
)

data class BrowseState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val grouping: LyricspornBrowseGrouping? = null,
    val sections: List<BrowseSectionData> = emptyList(),
)

class BrowseRepository(
    private val sessionStore: SessionStore,
    private val searchRepository: SearchRepository,
) {
    private var cachedState: BrowseState? = null

    fun getCachedState(): BrowseState? = cachedState

    suspend fun fetchBrowse(
        storefront: String = LyricspornClient.currentStorefront(),
        forceRefresh: Boolean = false,
        includeMotionArtwork: Boolean = false,
    ): Result<BrowseState> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedState != null && cachedState?.grouping?.storefront == storefront) {
            return@withContext Result.success(cachedState!!)
        }

        val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl ?: "https://lyricsporn.netlify.app/api/v1"
        android.util.Log.d("BrowseRepository", "fetchBrowse: apiBaseUrl=$apiBaseUrl, storefront=$storefront")
        val grouping = LyricspornClient.getBrowse(
            apiBaseUrl = apiBaseUrl,
            storefront = storefront,
            artworkSize = 600,
            includeMotionArtwork = includeMotionArtwork,
        )
        if (grouping == null) {
            android.util.Log.e("BrowseRepository", "fetchBrowse: grouping is null from LyricspornClient")
            return@withContext Result.failure(IllegalStateException("Failed to load browse groupings from Apple Music"))
        }

        val sectionDataList = grouping.sections.map { section ->
            when (section.layout) {
                "tracks" -> {
                    val rawTracks = section.items.map { it.toHomeTrack() }
                    val enrichedTracks = runCatching {
                        searchRepository.enrichAvailability(rawTracks)
                    }.getOrDefault(rawTracks)
                    BrowseSectionData(
                        section = section,
                        tracks = enrichedTracks,
                    )
                }
                "albums" -> {
                    BrowseSectionData(
                        section = section,
                        albums = section.items.map { it.toHomeAlbum() },
                    )
                }
                "playlists" -> {
                    BrowseSectionData(
                        section = section,
                        playlists = section.items.map { it.toHomePlaylist() },
                    )
                }
                else -> {
                    val tracks = if (section.items.any { it.type == "song" }) {
                        val rawTracks = section.items.filter { it.type == "song" }.map { it.toHomeTrack() }
                        runCatching { searchRepository.enrichAvailability(rawTracks) }.getOrDefault(rawTracks)
                    } else emptyList()
                    val albums = section.items.filter { it.type == "album" }.map { it.toHomeAlbum() }
                    val playlists = section.items.filter { it.type == "playlist" || it.type == "station" }.map { it.toHomePlaylist() }
                    BrowseSectionData(
                        section = section,
                        tracks = tracks,
                        albums = albums,
                        playlists = playlists,
                    )
                }
            }
        }

        val newState = BrowseState(
            isLoading = false,
            isRefreshing = false,
            error = null,
            grouping = grouping,
            sections = sectionDataList,
        )
        cachedState = newState
        Result.success(newState)
    }

    private fun LyricspornCatalogItem.toHomeTrack(): HomeTrack = HomeTrack(
        id = "apple_$id",
        title = name,
        artist = artistName.orEmpty(),
        album = albumName,
        artworkUrl = artworkUrl,
        source = null,
        isrc = isrc,
        providerTrackId = id,
        durationMs = durationMs,
    )

    private fun LyricspornCatalogItem.toHomeAlbum(): HomeAlbum = HomeAlbum(
        id = id,
        title = name,
        artist = artistName.orEmpty(),
        artworkUrl = artworkUrl,
        appleCatalogId = id,
    )

    private fun LyricspornCatalogItem.toHomePlaylist(): HomePlaylist = HomePlaylist(
        id = id,
        title = name,
        curator = artistName,
        artworkUrl = artworkUrl,
        appleCatalogId = id,
    )
}
