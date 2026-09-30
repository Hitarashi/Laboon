package org.shilpo.laboon.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.shilpo.laboon.auth.KeyValueStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackIdentity


class QueuePersistence(private val store: KeyValueStore) {

    fun load(): QueueState = QueuePersistenceCodec.decode(store.getString(STORAGE_KEY))

    fun save(state: QueueState) {
        store.putString(STORAGE_KEY, QueuePersistenceCodec.encode(state))
    }

    fun clear() {
        store.remove(STORAGE_KEY)
    }

    private companion object {
        const val STORAGE_KEY = "playback_queue_v1"
    }
}


class QueuePersistenceWriter(
    private val persistence: QueuePersistence,
    private val scope: CoroutineScope,
    private val delayMs: Long = DEFAULT_FLUSH_DELAY_MS,
) {
    private val lock = Any()
    private var pending: Job? = null

    fun schedule(state: QueueState) {
        synchronized(lock) {
            pending?.cancel()
            pending = scope.launch {
                delay(delayMs)
                persistence.save(state)
            }
        }
    }


    fun flush(state: QueueState) {
        synchronized(lock) {
            pending?.cancel()
            pending = null
        }
        if (state.items.isEmpty() && state.currentIndex < 0) {
            persistence.clear()
        } else {
            persistence.save(state)
        }
    }
}

private const val DEFAULT_FLUSH_DELAY_MS = 750L


object QueuePersistenceCodec {

    private const val VERSION = "v1"
    private const val HEADER_FIELDS = 6
    private const val ITEM_FIELDS = 12
    private const val MAX_ITEMS = 200

    fun encode(state: QueueState): String {
        val items = boundedItems(state)
        val currentIndex = state.currentIndex.takeIf { it in items.indices } ?: -1
        return buildString {
            append(VERSION).append('\t')
            append(currentIndex).append('\t')
            append(state.repeatMode.ordinal).append('\t')
            append(state.isShuffle.asToken()).append('\t')
            append(state.isAutoplayEnabled.asToken()).append('\t')
            append(items.size)
            items.forEach { track ->
                append('\n').append(encodeItem(track))
            }
        }
    }

    fun decode(raw: String?): QueueState {
        val lines = raw?.split('\n')?.filter { it.isNotEmpty() } ?: return QueueState()
        if (lines.isEmpty()) return QueueState()
        val header = lines.first().split('\t')
        if (header.size != HEADER_FIELDS || header[0] != VERSION) return QueueState()

        val currentIndex = header[1].toIntOrNull() ?: return QueueState()
        val repeatMode = header[2].toIntOrNull()?.let { RepeatMode.entries.getOrNull(it) }
            ?: return QueueState()
        val isShuffle = header[3].asFlag() ?: return QueueState()
        val isAutoplayEnabled = header[4].asFlag() ?: return QueueState()
        val itemCount = header[5].toIntOrNull()?.takeIf { it >= 0 } ?: return QueueState()
        if (itemCount != lines.size - 1) return QueueState()

        val decoded = lines.drop(1).map { line -> decodeItem(line) ?: return QueueState() }
        val items = dedupe(decoded)
        val currentKey = decoded.getOrNull(currentIndex)?.let { TrackIdentity.keyOf(it) }
        val restoredIndex = currentKey
            ?.let { key -> items.indexOfFirst { TrackIdentity.keyOf(it) == key } }
            ?.takeIf { it >= 0 }
            ?: -1

        return QueueState(
            items = items,
            currentIndex = restoredIndex,
            repeatMode = repeatMode,
            isShuffle = isShuffle,
            isAutoplayEnabled = isAutoplayEnabled,

            preShuffleOrder = null,
        )
    }


    private fun boundedItems(state: QueueState): List<HomeTrack> {
        if (state.items.size <= MAX_ITEMS) return state.items
        val head = (state.currentIndex + 1).coerceIn(0, MAX_ITEMS)
        return state.items.take(head) + state.items.drop(head).take(MAX_ITEMS - head)
    }

    private fun encodeItem(track: HomeTrack): String = listOf(
        escape(track.id),
        escape(track.title),
        escape(track.artist),
        encodeOptional(track.album),
        encodeOptional(track.artworkUrl),
        track.playCount.toString(),
        encodeOptional(track.source),
        encodeOptional(track.streamUrl),
        encodeOptional(track.backendTrackId?.toString()),
        track.isCached.asToken(),
        encodeOptional(track.codec),
        encodeOptional(track.mbid),
    ).joinToString("\t")

    private fun decodeItem(line: String): HomeTrack? {
        val fields = line.split('\t')
        if (fields.size != ITEM_FIELDS) return null


        val values = fields.map { unescape(it) ?: return null }
        val id = values[0].takeIf { it.isNotBlank() } ?: return null
        val title = values[1].takeIf { it.isNotBlank() } ?: return null
        val artist = values[2].takeIf { it.isNotBlank() } ?: return null
        val playCount = values[5].toLongOrNull() ?: return null
        val isCached = values[9].asFlag() ?: return null
        return HomeTrack(
            id = id,
            title = title,
            artist = artist,
            album = decodeOptional(values[3]),
            artworkUrl = decodeOptional(values[4]),
            playCount = playCount,
            source = decodeOptional(values[6]),
            streamUrl = decodeOptional(values[7]),
            backendTrackId = decodeOptional(values[8])?.toIntOrNull(),
            isCached = isCached,
            codec = decodeOptional(values[10]),
            mbid = decodeOptional(values[11]),
        )
    }

    private fun dedupe(tracks: List<HomeTrack>): List<HomeTrack> {
        val seen = HashSet<String>(tracks.size)
        return tracks.filter { seen.add(TrackIdentity.keyOf(it)) }
    }

    private fun encodeOptional(value: String?): String =
        if (value == null) "0" else "1" + escape(value)

    private fun decodeOptional(value: String): String? =
        if (value == "0") null else value.removePrefix("1")

    private fun Boolean.asToken(): String = if (this) "1" else "0"

    private fun String.asFlag(): Boolean? = when (this) {
        "0" -> false
        "1" -> true
        else -> null
    }

    private fun escape(value: String): String {
        val out = StringBuilder(value.length)
        value.forEach { char ->
            when (char) {
                '\\' -> out.append("\\\\")
                '\t' -> out.append("\\t")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                else -> out.append(char)
            }
        }
        return out.toString()
    }


    private fun unescape(value: String): String? {
        if (!value.contains('\\')) return value
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\') {
                out.append(char)
                index++
                continue
            }
            when (val escaped = value.getOrNull(index + 1)) {
                '\\' -> out.append('\\')
                't' -> out.append('\t')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                else -> return null
            }
            index += 2
        }
        return out.toString()
    }
}
