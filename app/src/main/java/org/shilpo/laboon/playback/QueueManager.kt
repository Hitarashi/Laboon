package org.shilpo.laboon.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackAvailability
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.search.CachedTrackAvailability

interface QueueManager {
    val state: StateFlow<QueueState>
    fun play(track: HomeTrack, contextTracks: List<HomeTrack>? = null)
    fun updateCurrentTrack(track: HomeTrack)

    fun applyAvailability(availability: Map<String, CachedTrackAvailability>)
    fun selectCurrentById(trackId: String): HomeTrack?
    fun playNext(track: HomeTrack)
    fun addToQueue(track: HomeTrack)

    fun removeUpNext(index: Int)
    fun moveUpNext(fromIndex: Int, toIndex: Int)
    fun clearUpNext()
    fun toggleAutoplay()
    fun cycleRepeatMode()
    fun toggleShuffle()
    fun peekNext(): HomeTrack?
    fun advanceToNext(): HomeTrack?
    fun advanceToPrevious(): HomeTrack?
    fun appendDiscovery(tracks: List<HomeTrack>)
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

    override fun play(track: HomeTrack, contextTracks: List<HomeTrack>?) {
        val context = contextTracks?.takeIf { it.isNotEmpty() } ?: listOf(track)
        val items = dedupe(context)
        val key = TrackIdentity.keyOf(track)
        val currentIndex = items.indexOfFirst { TrackIdentity.keyOf(it) == key }
            .takeIf { it >= 0 }
            ?: items.indexOfFirst { it.id == track.id }.takeIf { it >= 0 }
            ?: 0

        mutate { s ->
            s.copy(
                items = items,
                currentIndex = currentIndex,
                preShuffleOrder = if (s.isShuffle) items else null,
            )
        }
    }

    override fun updateCurrentTrack(track: HomeTrack) {
        mutate { state ->
            val index = state.currentIndex
            val current = state.items.getOrNull(index) ?: return@mutate state
            if (!TrackIdentity.isSameTrack(current, track)) return@mutate state

            val updatedItems = state.items.toMutableList().apply { set(index, track) }
            val updatedPreShuffleOrder = state.preShuffleOrder?.map { queuedTrack ->
                if (TrackIdentity.isSameTrack(queuedTrack, track)) track else queuedTrack
            }
            state.copy(items = updatedItems, preShuffleOrder = updatedPreShuffleOrder)
        }
    }

    override fun applyAvailability(availability: Map<String, CachedTrackAvailability>) {
        if (availability.isEmpty()) return
        mutate { state ->
            val updated = TrackAvailability.apply(state.items, availability)
            val updatedPreShuffleOrder = state.preShuffleOrder?.let { order ->
                TrackAvailability.apply(order, availability)
            }
            if (updated === state.items && updatedPreShuffleOrder === state.preShuffleOrder) {
                state
            } else {
                state.copy(items = updated, preShuffleOrder = updatedPreShuffleOrder)
            }
        }
    }

    override fun selectCurrentById(trackId: String): HomeTrack? {
        var selectedTrack: HomeTrack? = null
        mutate { state ->
            val index = state.items.indexOfFirst { it.id == trackId }
            if (index < 0) return@mutate state
            selectedTrack = state.items[index]
            if (index == state.currentIndex) state else state.copy(currentIndex = index)
        }
        return selectedTrack
    }

    override fun playNext(track: HomeTrack) {
        mutate { s -> s.stagedNextTo(track) }
    }

    override fun addToQueue(track: HomeTrack) {
        mutate { s ->
            val key = TrackIdentity.keyOf(track)
            if (s.items.any { TrackIdentity.keyOf(it) == key }) {
                s
            } else {
                s.copy(items = s.items + track)
            }
        }
    }

    override fun removeUpNext(index: Int) {
        mutate { s -> s.withoutUpcomingAt(index) }
    }

    override fun moveUpNext(fromIndex: Int, toIndex: Int) {
        mutate { s -> s.moveUpcoming(fromIndex, toIndex) }
    }

    override fun clearUpNext() {
        mutate { s ->
            val kept =
                if (s.currentIndex in s.items.indices) s.items.take(s.currentIndex + 1) else emptyList()
            s.copy(items = kept, currentIndex = kept.lastIndex)
        }
    }

    override fun toggleAutoplay() {
        mutate { s -> s.copy(isAutoplayEnabled = !s.isAutoplayEnabled) }
    }

    override fun cycleRepeatMode() {
        mutate { s ->
            val next = when (s.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            s.copy(repeatMode = next)
        }
    }

    override fun toggleShuffle() {
        mutate { s ->
            if (!s.isShuffle) {
                val cut = (s.currentIndex + 1).coerceIn(0, s.items.size)
                val head = s.items.subList(0, cut)
                val tail = s.items.subList(cut, s.items.size).shuffled()
                s.copy(isShuffle = true, items = head + tail, preShuffleOrder = s.items)
            } else {
                s.copy(isShuffle = false, items = s.unshuffledOrder(), preShuffleOrder = null)
            }
        }
    }

    override fun peekNext(): HomeTrack? {
        val s = _state.value
        if (s.currentIndex < 0) return null
        if (s.repeatMode == RepeatMode.ONE) return s.currentTrack
        return s.items.getOrNull(s.currentIndex + 1)
            ?: s.items.takeIf { s.repeatMode == RepeatMode.ALL }?.firstOrNull()
    }

    override fun advanceToNext(): HomeTrack? {
        val before = _state.value
        if (before.currentIndex < 0) return null
        if (before.repeatMode == RepeatMode.ONE) return before.currentTrack
        val nextIndex = when {
            before.currentIndex + 1 < before.items.size -> before.currentIndex + 1
            before.repeatMode == RepeatMode.ALL && before.items.isNotEmpty() -> 0
            else -> return null
        }
        val after = _state.updateAndGet { it.copy(currentIndex = nextIndex) }
        persist(after)
        return after.currentTrack
    }

    override fun advanceToPrevious(): HomeTrack? {
        val before = _state.value
        if (before.currentIndex <= 0) return before.currentTrack
        val after = _state.updateAndGet { s -> s.copy(currentIndex = s.currentIndex - 1) }
        persist(after)
        return after.currentTrack
    }

    override fun appendDiscovery(tracks: List<HomeTrack>) {
        if (tracks.isEmpty()) return
        mutate { s ->
            val seen = HashSet<String>(s.items.size + tracks.size)
            s.items.forEach { seen.add(TrackIdentity.keyOf(it)) }
            val fresh = tracks.filter { seen.add(TrackIdentity.keyOf(it)) }
            if (fresh.isEmpty()) s else s.copy(items = s.items + fresh)
        }
    }

    override fun getRecentHistoryKeys(): Set<String> {
        val s = _state.value
        val played = s.items.take((s.currentIndex + 1).coerceIn(0, s.items.size))
        val keys = LinkedHashSet<String>(RECENT_HISTORY_LIMIT * 2)
        played.takeLast(RECENT_HISTORY_LIMIT).forEach { track ->
            keys.add(TrackIdentity.keyOf(track))
            keys.add(legacyKey(track))
        }
        return keys
    }

    override fun release() {
        val state = _state.value
        writer?.flush(state)
        ownedFlushScope?.cancel()
    }

    private inline fun mutate(transform: (QueueState) -> QueueState) {
        _state.update(transform)
        persist(_state.value)
    }

    private fun persist(state: QueueState) {
        writer?.schedule(state)
    }

    private fun QueueState.stagedNextTo(track: HomeTrack): QueueState {
        val key = TrackIdentity.keyOf(track)
        val existing = items.indexOfFirst { TrackIdentity.keyOf(it) == key }
        if (existing == currentIndex) return this
        val working = items.toMutableList()
        val shiftedCurrent = if (existing in 0..currentIndex) currentIndex - 1 else currentIndex
        if (existing >= 0) working.removeAt(existing)
        working.add((shiftedCurrent + 1).coerceIn(0, working.size), track)
        return copy(items = working, currentIndex = shiftedCurrent)
    }

    private fun QueueState.withoutUpcomingAt(index: Int): QueueState {
        val absolute = upcomingIndexOf(index) ?: return this
        val working = items.toMutableList()
        working.removeAt(absolute)
        return copy(items = working)
    }

    private fun QueueState.moveUpcoming(fromIndex: Int, toIndex: Int): QueueState {
        val from = upcomingIndexOf(fromIndex) ?: return this
        val to = upcomingIndexOf(toIndex) ?: return this
        if (from == to) return this
        val working = items.toMutableList()
        working.add(to, working.removeAt(from))
        return copy(items = working)
    }

    private fun QueueState.upcomingIndexOf(index: Int): Int? {
        if (index < 0) return null
        val absolute = currentIndex + 1 + index
        return absolute.takeIf { it in items.indices }
    }

    private fun QueueState.unshuffledOrder(): List<HomeTrack> {
        val saved = preShuffleOrder ?: return items
        val present = items.mapTo(HashSet(items.size)) { TrackIdentity.keyOf(it) }
        val savedKeys = saved.mapTo(HashSet(saved.size)) { TrackIdentity.keyOf(it) }
        return saved.filter { TrackIdentity.keyOf(it) in present } +
                items.filter { TrackIdentity.keyOf(it) !in savedKeys }
    }

    private fun dedupe(tracks: List<HomeTrack>): List<HomeTrack> {
        val seen = HashSet<String>(tracks.size)
        return tracks.filter { seen.add(TrackIdentity.keyOf(it)) }
    }

    private fun legacyKey(track: HomeTrack): String =
        "${track.title.lowercase().trim()}:::${track.artist.lowercase().trim()}"

    companion object {

        const val RECENT_HISTORY_LIMIT = 80
    }
}
