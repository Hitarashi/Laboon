package org.shilpo.laboon.ui.screens.home

import androidx.compose.runtime.compositionLocalOf
import org.shilpo.laboon.home.HomeTrack

internal data class TrackFavoriteActions(
    val enabled: Boolean,
    val loadMembership: suspend (HomeTrack) -> Boolean?,
    val setMembership: suspend (HomeTrack, Boolean) -> Boolean,
    val favoriteTrackIds: Set<String> = emptySet(),
)

internal val LocalTrackFavoriteActions = compositionLocalOf<TrackFavoriteActions?> { null }

internal fun HomeTrack.isEffectiveFavorite(actions: TrackFavoriteActions?): Boolean {
    val id = providerTrackId?.takeIf(String::isNotBlank)
    if (id != null && actions != null && actions.favoriteTrackIds.isNotEmpty()) {
        return id in actions.favoriteTrackIds
    }
    return isFavorite == true
}
