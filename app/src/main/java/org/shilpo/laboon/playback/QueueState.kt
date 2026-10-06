package org.shilpo.laboon.playback

import org.shilpo.laboon.home.HomeTrack

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

data class QueueState(
    val items: List<HomeTrack> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val isAutoplayEnabled: Boolean = true,

    val preShuffleOrder: List<HomeTrack>? = null,
) {
    val currentTrack: HomeTrack? get() = items.getOrNull(currentIndex)

    val upcoming: List<HomeTrack>
        get() = if (currentIndex < 0) emptyList() else items.drop(currentIndex + 1)

    val upNextCount: Int get() = (items.size - currentIndex - 1).coerceAtLeast(0)

    val hasNext: Boolean
        get() = repeatMode != RepeatMode.OFF || upNextCount > 0 || isAutoplayEnabled

    val hasPrevious: Boolean get() = currentIndex > 0
}
