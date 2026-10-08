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
        apply(tracks, lookup.cached)

    fun apply(
        tracks: List<HomeTrack>,
        cached: Map<String, CachedTrackAvailability>
    ): List<HomeTrack> {
        if (cached.isEmpty()) return tracks
        var changed = false
        val updated = tracks.map { track ->
            val id = AutoRipPlanner.normalizeProviderTrackId(track.providerTrackId)
            val availability = id?.let(cached::get)
            if (availability == null) {
                track
            } else {
                val preferred = availability.preferredCodec
                val preferredTrackId = availability.playbackTrackId
                val next = track.copy(
                    isCached = true,
                    streamUrl = track.streamUrl.takeIf {
                        preferredTrackId != null && preferredTrackId == track.backendTrackId
                    },
                    codec = preferred,
                    backendTrackId = preferredTrackId,
                    availableFormats = availability.formats,
                    availableVariants = availability.variants,
                )
                if (next != track) changed = true
                next
            }
        }
        return if (changed) updated else tracks
    }
}
