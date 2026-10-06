package org.shilpo.laboon.ui.screens.home

import org.shilpo.laboon.home.HomeFeedState
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.SectionLoadState
import org.shilpo.laboon.home.SectionState
import org.shilpo.laboon.home.TrackAvailability
import org.shilpo.laboon.search.CachedTrackAvailability
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.ui.screens.album.AlbumDetailsUiState

internal fun HomeFeedState.withAvailability(
    availability: Map<String, CachedTrackAvailability>,
): HomeFeedState = copy(
    rotation = rotation.withAvailability(availability),
    recommended = recommended.withAvailability(availability),
    topTracks = topTracks.withAvailability(availability),
    regionalTrending = regionalTrending.withAvailability(availability),
    globalTrending = globalTrending.withAvailability(availability),
    weeklyPicks = weeklyPicks.withAvailability(availability),
)

private fun SectionState<HomeTrack>.withAvailability(
    availability: Map<String, CachedTrackAvailability>,
): SectionState<HomeTrack> = when (status) {
    SectionLoadState.LOADED -> copy(items = TrackAvailability.apply(items, availability))
    else -> this
}

internal fun AlbumDetailsUiState.withAvailability(
    availability: Map<String, CachedTrackAvailability>,
): AlbumDetailsUiState = when (this) {
    is AlbumDetailsUiState.Loaded -> copy(tracks = TrackAvailability.apply(tracks, availability))
    else -> this
}

/**
 * Re-resolves availability for every loaded track section against the server.
 *
 * Feed tracks restored from [org.shilpo.laboon.home.HomeFeedCache] carry whatever `isCached`
 * value was persisted, which goes stale as rips finish on the server. Enriching on the initial
 * load makes restored tracks show their real play state without a pull-to-refresh.
 *
 * Returns the positive verdicts keyed by provider track id so the caller can merge them into
 * whatever state exists at apply time, rather than replacing a snapshot that concurrent section
 * fetches may already have superseded.
 */
internal suspend fun HomeFeedState.resolveAvailability(
    repository: SearchRepository,
): Map<String, CachedTrackAvailability> {
    val loaded =
        listOf(rotation, recommended, topTracks, regionalTrending, globalTrending, weeklyPicks)
            .filter { it.status == SectionLoadState.LOADED }
            .flatMap { it.items }
    if (loaded.isEmpty()) return emptyMap()

    val enriched = repository.enrichAvailability(loaded)
    return loaded.zip(enriched)
        .mapNotNull { (original, updated) ->
            val id = original.providerTrackId ?: return@mapNotNull null
            if (updated.isCached) {
                id to CachedTrackAvailability(
                    variants = updated.availableVariants,
                    preferredCodec = updated.codec,
                    playbackTrackId = updated.backendTrackId,
                )
            } else {
                null
            }
        }
        .toMap()
}
