package org.shilpo.laboon.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackAvailability
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.search.CachedTrackAvailability
import java.util.concurrent.atomic.AtomicLong

interface QueueManager {
    val state: StateFlow<QueueState>
    fun play(track: HomeTrack, contextTracks: List<HomeTrack>? = null)
    fun updateCurrentTrack(track: HomeTrack)
    fun applyAvailability(availability: Map<String, CachedTrackAvailability>)
    fun selectCurrentById(mediaId: String): HomeTrack?
    fun playEntry(entryId: Long): HomeTrack?
    fun playNext(track: HomeTrack)
    fun addToQueue(track: HomeTrack)
    fun promoteAutoplayToManual(entryId: Long)
    fun removeUpNext(index: Int)
    fun moveUpNext(fromIndex: Int, toIndex: Int)
    fun clearUpNext()
    fun retryAutoplay()
    fun cycleRepeatMode()
    fun toggleShuffle()
    fun peekNextEntry(): QueueEntry?
    fun advanceToNext(): HomeTrack?
    fun advanceToPrevious(): HomeTrack?
    fun appendDiscovery(tracks: List<HomeTrack>)
    fun discoverySeedTracks(): List<HomeTrack>
    fun getRecentHistoryKeys(): Set<String>
    fun release()
}

class QueueManagerImpl(
    persistence: QueuePersistence? = null,
    writeScope: CoroutineScope? = null,
) : QueueManager {

    private val _state = MutableStateFlow(persistence?.load() ?: QueueState())
    override val state: StateFlow<QueueState> = _state.asStateFlow()

    private val flushScope: CoroutineScope? = when {
        persistence == null -> null
        writeScope != null -> writeScope
        else -> CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    private val ownedFlushScope: CoroutineScope? = flushScope?.takeIf { writeScope == null }
    private val writer: QueuePersistenceWriter? = persistence?.let { store ->
        flushScope?.let { QueuePersistenceWriter(store, it) }
    }
    private val nextEntryId = AtomicLong(
        (_state.value.history + _state.value.upcomingEntries + _state.value.contextEntries)
            .maxOfOrNull(QueueEntry::id)?.plus(1L) ?: 1L,
    )

    override fun play(track: HomeTrack, contextTracks: List<HomeTrack>?) {
        val sourceContext = contextTracks?.takeIf(List<HomeTrack>::isNotEmpty) ?: listOf(track)
        val context = sourceContext.toMutableList()
        var selectedIndex = context.indexOfFirst { it.id == track.id }
        if (selectedIndex < 0) {
            selectedIndex = context.indexOfFirst { TrackIdentity.isSameTrack(it, track) }
        }
        if (selectedIndex < 0) {
            context.add(0, track)
            selectedIndex = 0
        }
        val uniqueContext = dedupeContext(context)
        selectedIndex = uniqueContext.indexOfFirst { it.id == track.id }
            .takeIf { it >= 0 }
            ?: uniqueContext.indexOfFirst { TrackIdentity.isSameTrack(it, track) }.coerceAtLeast(0)
        val contextEntries = uniqueContext.mapIndexed { index, item ->
            newEntry(item, QueueOrigin.CONTEXT, index)
        }
        val selectedEntry = contextEntries[selectedIndex].copy(track = track)

        mutate { old ->
            val manuals = old.upcomingEntries.filter { it.origin == QueueOrigin.MANUAL }
            val history = (old.history + selectedEntry).takeLast(MAX_HISTORY_ENTRIES)
            val cursor = history.lastIndex
            val contextTail = contextEntries.drop(selectedIndex + 1)
            val contextQueue = if (old.isShuffle) contextTail.shuffled() else contextTail
            old.copy(
                history = history,
                historyCursor = cursor,
                upcomingEntries = manuals + contextQueue,
                contextEntries = contextEntries,
                sessionSeedEntryId = selectedEntry.id,
                autoplaySuppressed = false,
                dismissedAutoplayKeys = emptySet(),
            )
        }
    }

    override fun updateCurrentTrack(track: HomeTrack) {
        mutate { current ->
            val index = current.historyCursor
            val entry = current.history.getOrNull(index) ?: return@mutate current
            if (!TrackIdentity.isSameTrack(entry.track, track)) return@mutate current
            current.copy(
                history = current.history.mapIndexed { i, item ->
                    if (i == index) item.copy(track = track) else item
                },
                contextEntries = current.contextEntries.map { item ->
                    if (item.id == entry.id) item.copy(track = track) else item
                },
            )
        }
    }

    override fun applyAvailability(availability: Map<String, CachedTrackAvailability>) {
        if (availability.isEmpty()) return
        mutate { current ->
            val entries = current.history + current.upcomingEntries + current.contextEntries
            val distinctEntries = entries.distinctBy(QueueEntry::id)
            val updatedTracks = distinctEntries.zip(
                TrackAvailability.apply(distinctEntries.map(QueueEntry::track), availability),
            ).associate { (entry, track) -> entry.id to track }

            fun QueueEntry.withAvailability(): QueueEntry {
                val updated = updatedTracks[id] ?: return this
                return if (updated == track) this else copy(track = updated)
            }
            current.copy(
                history = current.history.map(QueueEntry::withAvailability),
                upcomingEntries = current.upcomingEntries.map(QueueEntry::withAvailability),
                contextEntries = current.contextEntries.map(QueueEntry::withAvailability),
            )
        }
    }

    override fun selectCurrentById(mediaId: String): HomeTrack? {
        val entryId = mediaId
            .takeIf { it.startsWith(QUEUE_MEDIA_ID_PREFIX) }
            ?.removePrefix(QUEUE_MEDIA_ID_PREFIX)
            ?.toLongOrNull()
            ?: return null
        return selectEntryAsCurrent(entryId)
    }

    override fun playEntry(entryId: Long): HomeTrack? {
        var selected: HomeTrack? = null
        val after = _state.updateAndGet { current ->
            val historyIndex = current.history.indexOfFirst { it.id == entryId }
            if (historyIndex >= 0) {
                selected = current.history[historyIndex].track
                return@updateAndGet current.copy(historyCursor = historyIndex)
            }
            val queueIndex = current.upcomingEntries.indexOfFirst { it.id == entryId }
            if (queueIndex < 0) return@updateAndGet current
            val chosen = current.upcomingEntries[queueIndex]
            val history = (current.history + chosen).takeLast(MAX_HISTORY_ENTRIES)
            selected = chosen.track
            current.copy(
                history = history,
                historyCursor = history.lastIndex,
                upcomingEntries = current.upcomingEntries.filterNot { it.id == entryId },
                skippedHistoryEntryIds = current.skippedHistoryEntryIds - entryId,
            )
        }
        if (selected != null) persist(after)
        return selected
    }

    override fun playNext(track: HomeTrack) {
        mutate { current ->
            current.copy(
                upcomingEntries = listOf(
                    newEntry(
                        track,
                        QueueOrigin.MANUAL
                    )
                ) + current.upcomingEntries
            )
        }
    }

    override fun addToQueue(track: HomeTrack) {
        mutate { current ->
            val manualCount = current.upcomingEntries.count { it.origin == QueueOrigin.MANUAL }
            current.copy(
                upcomingEntries = current.upcomingEntries.toMutableList().apply {
                    add(manualCount, newEntry(track, QueueOrigin.MANUAL))
                },
            )
        }
    }

    override fun promoteAutoplayToManual(entryId: Long) {
        mutate { current ->
            val index = current.upcomingEntries.indexOfFirst { it.id == entryId }
            if (index < 0 || current.upcomingEntries[index].origin != QueueOrigin.AUTOPLAY) {
                return@mutate current
            }
            val entry = current.upcomingEntries[index].copy(origin = QueueOrigin.MANUAL)
            current.copy(
                upcomingEntries = current.upcomingEntries.toMutableList().apply {
                    removeAt(index)
                    add(count { it.origin == QueueOrigin.MANUAL }, entry)
                },
            )
        }
    }

    override fun removeUpNext(index: Int) {
        mutate { current ->
            if (index < 0) return@mutate current
            val forwardCount = current.forwardHistory.size
            if (index < forwardCount) {
                val historyIndex = current.historyCursor + 1 + index
                val entry = current.history.getOrNull(historyIndex) ?: return@mutate current
                current.copy(skippedHistoryEntryIds = current.skippedHistoryEntryIds + entry.id)
            } else {
                val queueIndex = index - forwardCount
                val entry = current.upcomingEntries.getOrNull(queueIndex) ?: return@mutate current
                current.copy(
                    upcomingEntries = current.upcomingEntries.toMutableList()
                        .apply { removeAt(queueIndex) },
                    dismissedAutoplayKeys = if (entry.origin == QueueOrigin.AUTOPLAY) {
                        current.dismissedAutoplayKeys + setOf(
                            TrackIdentity.keyOf(entry.track),
                            TrackIdentity.keyOf(null, entry.track.title, entry.track.artist),
                        )
                    } else current.dismissedAutoplayKeys,
                )
            }
        }
    }

    override fun moveUpNext(fromIndex: Int, toIndex: Int) {
        mutate { current ->
            val forwardCount = current.forwardHistory.size
            val from = fromIndex - forwardCount
            val to = toIndex - forwardCount
            if (from !in current.upcomingEntries.indices || to !in current.upcomingEntries.indices) {
                return@mutate current
            }
            val working = current.upcomingEntries.toMutableList()
            val moved = working.removeAt(from)
            val targetOrigin = working.getOrNull(to)?.origin ?: moved.origin
            val promoted =
                moved.origin == QueueOrigin.AUTOPLAY && targetOrigin != QueueOrigin.AUTOPLAY
            val entry = if (promoted) moved.copy(origin = QueueOrigin.MANUAL) else moved
            val group = if (promoted) QueueOrigin.MANUAL else moved.origin
            val groupIndices = working.indices.filter { working[it].origin == group }
            val insertion = when {
                groupIndices.isEmpty() -> working.size
                targetOrigin == group -> groupIndices.firstOrNull { it >= to }
                    ?: (groupIndices.last() + 1)

                group == QueueOrigin.MANUAL -> groupIndices.last() + 1
                else -> groupIndices.last() + 1
            }.coerceIn(0, working.size)
            working.add(insertion, entry)
            current.copy(upcomingEntries = working)
        }
    }

    override fun clearUpNext() {
        mutate { current ->
            current.copy(upcomingEntries = emptyList(), autoplaySuppressed = true)
        }
    }

    override fun retryAutoplay() {
        mutate { current ->
            if (!current.autoplaySuppressed) current else current.copy(autoplaySuppressed = false)
        }
    }

    override fun cycleRepeatMode() {
        mutate { current ->
            val next = when (current.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            current.copy(repeatMode = next)
        }
    }

    override fun toggleShuffle() {
        mutate { current ->
            if (!current.isShuffle) {
                val manuals = current.upcomingEntries.filter { it.origin == QueueOrigin.MANUAL }
                val context = current.upcomingEntries
                    .filter { it.origin == QueueOrigin.CONTEXT }
                    .shuffled()
                val autoplay = current.upcomingEntries.filter { it.origin == QueueOrigin.AUTOPLAY }
                current.copy(isShuffle = true, upcomingEntries = manuals + context + autoplay)
            } else {
                val manuals = current.upcomingEntries.filter { it.origin == QueueOrigin.MANUAL }
                val context = current.upcomingEntries
                    .filter { it.origin == QueueOrigin.CONTEXT }
                    .sortedWith(compareBy<QueueEntry> { it.contextOrder ?: Int.MAX_VALUE })
                val autoplay = current.upcomingEntries.filter { it.origin == QueueOrigin.AUTOPLAY }
                current.copy(isShuffle = false, upcomingEntries = manuals + context + autoplay)
            }
        }
    }

    override fun peekNextEntry(): QueueEntry? {
        var current = _state.value
        if (current.currentTrack == null) return null
        if (current.repeatMode == RepeatMode.ALL && current.forwardHistory.isEmpty() &&
            current.contextUpcomingEntries.isEmpty() && current.contextEntries.isNotEmpty()
        ) {
            current = _state.updateAndGet { state -> state.withRepeatedContextBeforeAutoplay() }
            persist(current)
        }
        current.forwardHistory.firstOrNull()?.let { return it }
        if (current.repeatMode == RepeatMode.ONE) return current.currentEntry
        current.upcomingEntries.firstOrNull()?.let { return it }
        return null
    }

    override fun advanceToNext(): HomeTrack? {
        var selected: HomeTrack? = null
        val after = _state.updateAndGet { original ->
            if (original.currentTrack == null) return@updateAndGet original
            val nextHistoryIndex = original.history.indices.firstOrNull { index ->
                index > original.historyCursor && original.history[index].id !in original.skippedHistoryEntryIds
            }
            if (nextHistoryIndex != null) {
                selected = original.history[nextHistoryIndex].track
                return@updateAndGet original.copy(historyCursor = nextHistoryIndex)
            }

            var current = original
            if (current.repeatMode == RepeatMode.ALL && current.contextUpcomingEntries.isEmpty()) {
                current = current.withRepeatedContextBeforeAutoplay()
            }
            val nextEntry = current.upcomingEntries.firstOrNull()
            if (nextEntry == null) {
                if (current.repeatMode == RepeatMode.ALL && current.contextEntries.isNotEmpty()) {
                    current = current.withRepeatedContextBeforeAutoplay(force = true)
                }
            }
            val chosen = current.upcomingEntries.firstOrNull() ?: return@updateAndGet original
            val history = (current.history + chosen).takeLast(MAX_HISTORY_ENTRIES)
            selected = chosen.track
            var after = current.copy(
                history = history,
                historyCursor = history.lastIndex,
                upcomingEntries = current.upcomingEntries.drop(1),
                skippedHistoryEntryIds = current.skippedHistoryEntryIds - chosen.id,
            )
            if (after.repeatMode == RepeatMode.ALL && after.contextUpcomingEntries.isEmpty()) {
                after = after.withRepeatedContextBeforeAutoplay()
            }
            after
        }
        if (selected != null) persist(after)
        return selected
    }

    override fun advanceToPrevious(): HomeTrack? {
        var selected: HomeTrack? = null
        val after = _state.updateAndGet { current ->
            val previousIndex = current.history.indices.lastOrNull { index ->
                index < current.historyCursor && current.history[index].id !in current.skippedHistoryEntryIds
            } ?: return@updateAndGet current
            selected = current.history[previousIndex].track
            current.copy(historyCursor = previousIndex)
        }
        if (selected != null) persist(after)
        return selected
    }

    override fun appendDiscovery(tracks: List<HomeTrack>) {
        if (tracks.isEmpty()) return
        mutate { current ->
            if (current.autoplaySuppressed) return@mutate current
            val known = current.upcomingEntries.mapTo(HashSet()) { TrackIdentity.keyOf(it.track) }
            known += getRecentHistoryKeys(current)
            known += current.dismissedAutoplayKeys
            val fresh = tracks.filter { track ->
                track.title.isNotBlank() && track.artist.isNotBlank() && known.add(
                    TrackIdentity.keyOf(
                        track
                    )
                )
            }
            if (fresh.isEmpty()) return@mutate current
            current.copy(
                upcomingEntries = current.upcomingEntries + fresh.map {
                    newEntry(
                        it,
                        QueueOrigin.AUTOPLAY
                    )
                },
            )
        }
    }

    override fun discoverySeedTracks(): List<HomeTrack> {
        val current = _state.value
        val seed = current.sessionSeedEntryId?.let { id ->
            current.contextEntries.firstOrNull { it.id == id }?.track
                ?: current.history.firstOrNull { it.id == id }?.track
        }
        val listened = current.history.takeLast(3).map(QueueEntry::track)
        val seen = HashSet<String>()
        return (listOfNotNull(seed) + listened).filter { seen.add(TrackIdentity.keyOf(it)) }
    }

    override fun getRecentHistoryKeys(): Set<String> = getRecentHistoryKeys(_state.value)

    override fun release() {
        writer?.flush(_state.value)
        ownedFlushScope?.cancel()
    }

    private fun selectEntryAsCurrent(entryId: Long): HomeTrack? {
        var selected: HomeTrack? = null
        val after = _state.updateAndGet { current ->
            val historyIndex = current.history.indexOfFirst { it.id == entryId }
            if (historyIndex >= 0) {
                selected = current.history[historyIndex].track
                return@updateAndGet current.copy(historyCursor = historyIndex)
            }
            val queueIndex = current.upcomingEntries.indexOfFirst { it.id == entryId }
            if (queueIndex < 0) return@updateAndGet current
            val chosen = current.upcomingEntries.take(queueIndex + 1)
            selected = chosen.last().track
            val history = (current.history + chosen).takeLast(MAX_HISTORY_ENTRIES)
            current.copy(
                history = history,
                historyCursor = history.lastIndex,
                upcomingEntries = current.upcomingEntries.drop(queueIndex + 1),
            )
        }
        if (selected != null) persist(after)
        return selected
    }

    private fun QueueState.withRepeatedContextBeforeAutoplay(force: Boolean = false): QueueState {
        if ((!force && contextUpcomingEntries.isNotEmpty()) || contextEntries.isEmpty()) return this
        val manuals = upcomingEntries.filter { it.origin == QueueOrigin.MANUAL }
        val auto = upcomingEntries.filter { it.origin == QueueOrigin.AUTOPLAY }
        val repeated = contextEntries.map { original ->
            newEntry(original.track, QueueOrigin.CONTEXT, original.contextOrder)
        }
        return copy(upcomingEntries = manuals + repeated + auto)
    }

    private fun getRecentHistoryKeys(state: QueueState): Set<String> {
        val keys = LinkedHashSet<String>(RECENT_HISTORY_LIMIT)
        state.history.takeLast(RECENT_HISTORY_LIMIT)
            .forEach { entry -> keys.add(TrackIdentity.keyOf(entry.track)) }
        return keys
    }

    private fun mutate(transform: (QueueState) -> QueueState) {
        val after = _state.updateAndGet(transform)
        persist(after)
    }

    private fun persist(state: QueueState) {
        writer?.schedule(state)
    }

    private fun newEntry(
        track: HomeTrack,
        origin: QueueOrigin,
        contextOrder: Int? = null,
    ): QueueEntry = QueueEntry(nextEntryId.getAndIncrement(), track, origin, contextOrder)

    private fun dedupeContext(tracks: List<HomeTrack>): List<HomeTrack> {
        val seen = HashSet<String>(tracks.size)
        return tracks.filter { seen.add(TrackIdentity.keyOf(it)) }
    }

    companion object {
        const val RECENT_HISTORY_LIMIT = 80
        const val MAX_HISTORY_ENTRIES = 200
        const val QUEUE_MEDIA_ID_PREFIX = "queue-entry:"
    }
}
