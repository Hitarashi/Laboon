package org.shilpo.laboon.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.KeyValueStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
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
    private const val LEGACY_ITEM_FIELDS = 13
    private const val PREVIOUS_ITEM_FIELDS = 14
    private const val ITEM_FIELDS = 15
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
                append('\n').append(encodeTrack(track))
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

        val decoded = lines.drop(1).map { line -> decodeTrack(line) ?: return QueueState() }
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

    fun encodeTrack(track: HomeTrack): String = listOf(
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
        encodeOptional(track.isrc),
        encodeOptional(track.providerTrackId),
        encodeOptional(track.availableVariants.takeIf { it.isNotEmpty() }?.let(::encodeVariants)),
    ).joinToString("\t")

    fun decodeTrack(line: String): HomeTrack? {
        val fields = line.split('\t')
        if (fields.size != LEGACY_ITEM_FIELDS && fields.size != PREVIOUS_ITEM_FIELDS &&
            fields.size != ITEM_FIELDS
        ) return null

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
            isrc = decodeOptional(values[12]),
            providerTrackId = values.getOrNull(13)?.let(::decodeOptional),
            availableFormats = values.getOrNull(14)
                ?.let(::decodeOptional)
                ?.let(::decodeVariants)
                ?.map(TrackFormatVariant::format)
                .orEmpty(),
            availableVariants = values.getOrNull(14)
                ?.let(::decodeOptional)
                ?.let(::decodeVariants)
                .orEmpty(),
        )
    }

    private fun encodeVariants(variants: List<TrackFormatVariant>): String =
        JSONArray().apply {
            variants.forEach { variant ->
                put(JSONObject().apply {
                    put("format", variant.format)
                    put("backendTrackId", variant.backendTrackId)
                    variant.fileSizeBytes?.let { put("fileSizeBytes", it) }
                })
            }
        }.toString()

    private fun decodeVariants(raw: String): List<TrackFormatVariant> =
        runCatching { JSONArray(raw) }.getOrNull()?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val variant = array.optJSONObject(index) ?: continue
                    val format = variant.optString("format").takeIf { it.isNotBlank() }
                        ?: continue
                    val backendTrackId = variant.optInt("backendTrackId")
                        .takeIf { variant.has("backendTrackId") && it > 0 } ?: continue
                    val fileSizeBytes = variant.optLong("fileSizeBytes")
                        .takeIf { variant.has("fileSizeBytes") && it > 0L }
                    add(
                        TrackFormatVariant(
                            format = format,
                            backendTrackId = backendTrackId,
                            fileSizeBytes = fileSizeBytes,
                        )
                    )
                }
            }
        }.orEmpty()

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
