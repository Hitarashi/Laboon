package org.shilpo.laboon.playback

import org.shilpo.laboon.home.HomeTrack

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

enum class QueueOrigin {
    MANUAL,
    CONTEXT,
    AUTOPLAY,
}

/** A queue row keeps its identity even when the same recording is added more than once. */
data class QueueEntry(
    val id: Long,
    val track: HomeTrack,
    val origin: QueueOrigin,
    /** Position in [QueueState.contextEntries], used to restore context order after shuffle. */
    val contextOrder: Int? = null,
)

/**
 * Queue state separates songs that have actually played from songs still waiting to play.
 * [historyCursor] can move backwards and forwards through play history without rewriting it;
 * newly queued entries remain in [upcomingEntries].
 */
data class QueueState(
    val history: List<QueueEntry> = emptyList(),
    val historyCursor: Int = -1,
    val upcomingEntries: List<QueueEntry> = emptyList(),
    val contextEntries: List<QueueEntry> = emptyList(),
    val sessionSeedEntryId: Long? = null,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val autoplaySuppressed: Boolean = false,
    val dismissedAutoplayKeys: Set<String> = emptySet(),
    val skippedHistoryEntryIds: Set<Long> = emptySet(),
) {
    val currentEntry: QueueEntry? get() = history.getOrNull(historyCursor)
    val currentTrack: HomeTrack? get() = currentEntry?.track
    val hasPrevious: Boolean
        get() = history.indices.any { index ->
            index < historyCursor && history[index].id !in skippedHistoryEntryIds
        }

    /** Songs played earlier than the current cursor that Next should retrace first. */
    val forwardHistory: List<QueueEntry>
        get() = if (historyCursor < 0) emptyList() else history.drop(historyCursor + 1)
            .filterNot { it.id in skippedHistoryEntryIds }

    /** Visible upcoming playback order, including forward history after pressing Previous. */
    val playbackUpcomingEntries: List<QueueEntry>
        get() = forwardHistory + upcomingEntries

    val upcoming: List<HomeTrack> get() = playbackUpcomingEntries.map(QueueEntry::track)
    val upNextCount: Int get() = playbackUpcomingEntries.size

    val manualEntries: List<QueueEntry>
        get() = upcomingEntries.filter { it.origin == QueueOrigin.MANUAL }

    val contextUpcomingEntries: List<QueueEntry>
        get() = upcomingEntries.filter { it.origin == QueueOrigin.CONTEXT }

    val autoplayEntries: List<QueueEntry>
        get() = upcomingEntries.filter { it.origin == QueueOrigin.AUTOPLAY }

    val hasNext: Boolean
        get() = currentTrack != null && (
                upNextCount > 0 ||
                        (repeatMode == RepeatMode.ALL && contextEntries.isNotEmpty())
                )

    companion object {
        fun withCurrent(track: HomeTrack): QueueState {
            val current =
                QueueEntry(id = 1L, track = track, origin = QueueOrigin.CONTEXT, contextOrder = 0)
            return QueueState(
                history = listOf(current),
                historyCursor = 0,
                contextEntries = listOf(current),
                sessionSeedEntryId = current.id,
            )
        }
    }
}
