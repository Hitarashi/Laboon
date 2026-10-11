package org.shilpo.laboon.home

/** Combines playlist contents in order, keeping the first occurrence of each Apple catalog ID. */
internal fun combinePlaylistTrackIds(
    destinationTrackIds: List<String>?,
    sourceTrackIds: List<List<String>>,
): List<String> = buildList {
    destinationTrackIds?.let(::addAll)
    sourceTrackIds.forEach(::addAll)
}.distinct()
