package org.shilpo.laboon.home

import org.shilpo.laboon.rip.AutoRipPlanner
import org.shilpo.laboon.search.BatchAvailabilityLookup
import org.shilpo.laboon.search.CachedTrackAvailability

/**
 * Applies a fresh server availability answer to tracks that are already held in memory.
 *
 * Lets every state owner (home feed, search results/history/suggestions, album detail, playback
 * queue) mark a freshly ripped track as cached and playable without refetching its whole list.
 */
object TrackAvailability {

    fun apply(tracks: List<HomeTrack>, lookup: BatchAvailabilityLookup): List<HomeTrack> =
        apply(tracks, lookup.cached, lookup.favoriteByTrackId)

    fun apply(
        tracks: List<HomeTrack>,
        cached: Map<String, CachedTrackAvailability>
    ): List<HomeTrack> = apply(tracks, cached, emptyMap())

    private fun apply(
        tracks: List<HomeTrack>,
        cached: Map<String, CachedTrackAvailability>,
        favoriteByTrackId: Map<String, Boolean>,
    ): List<HomeTrack> {
        if (cached.isEmpty() && favoriteByTrackId.isEmpty()) return tracks
        var changed = false
        val updated = tracks.map { track ->
            val id = AutoRipPlanner.normalizeProviderTrackId(track.providerTrackId)
            val availability = id?.let(cached::get)
            val favorite = id?.let(favoriteByTrackId::get)
            if (availability == null && favorite == null) {
                track
            } else {
                val next = if (availability == null) {
                    track.copy(isFavorite = favorite ?: track.isFavorite)
                } else {
                    val preferredTrackId = availability.playbackTrackId
                    track.copy(
                        isFavorite = favorite ?: track.isFavorite,
                        isCached = availability.variants.isNotEmpty(),
                        streamUrl = track.streamUrl.takeIf {
                            preferredTrackId != null && preferredTrackId == track.backendTrackId
                        },
                        codec = availability.preferredCodec,
                        backendTrackId = preferredTrackId,
                        availableFormats = availability.formats,
                        availableVariants = availability.variants,
                    )
                }
                if (next != track) changed = true
                next
            }
        }
        return if (changed) updated else tracks
    }
}
