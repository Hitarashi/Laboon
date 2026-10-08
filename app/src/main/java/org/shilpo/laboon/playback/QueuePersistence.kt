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
        if (state.history.isEmpty() && state.upcomingEntries.isEmpty()) {
            persistence.clear()
        } else {
            persistence.save(state)
        }
    }
}

private const val DEFAULT_FLUSH_DELAY_MS = 750L

object QueuePersistenceCodec {

    private const val VERSION = "v1"
    private const val HEADER_FIELDS = 11
    private const val ITEM_FIELDS = 18
    private const val MAX_HISTORY = 200
    private const val MAX_QUEUE_ENTRIES = 200
    private const val MAX_CONTEXT_ENTRIES = 200

    fun encode(state: QueueState): String {
        val boundedHistoryStart = (state.history.size - MAX_HISTORY).coerceAtLeast(0)
        val history = state.history.drop(boundedHistoryStart)
        val cursor = if (state.historyCursor < boundedHistoryStart) {
            -1
        } else {
            (state.historyCursor - boundedHistoryStart).coerceAtMost(history.lastIndex)
        }
        val upcoming = state.upcomingEntries.take(MAX_QUEUE_ENTRIES)
        val context = state.contextEntries.take(MAX_CONTEXT_ENTRIES)
        val skipped = state.skippedHistoryEntryIds.intersect(history.mapTo(HashSet()) { it.id })
        val dismissed = state.dismissedAutoplayKeys.take(MAX_QUEUE_ENTRIES)
        val seedId = state.sessionSeedEntryId?.toString().orEmpty()
        return buildString {
            append(VERSION).append('\t')
            append(cursor).append('\t')
            append(state.repeatMode.ordinal).append('\t')
            append(state.isShuffle.asToken()).append('\t')
            append(state.autoplaySuppressed.asToken()).append('\t')
            append(history.size).append('\t')
            append(upcoming.size).append('\t')
            append(context.size).append('\t')
            append(seedId).append('\t')
            append(skipped.size).append('\t')
            append(dismissed.size)
            history.forEach { entry -> appendEntry('H', entry) }
            upcoming.forEach { entry -> appendEntry('U', entry) }
            context.forEach { entry -> appendEntry('C', entry) }
            skipped.forEach { id -> append("\nS\t").append(id) }
            dismissed.forEach { key -> append("\nD\t").append(escape(key)) }
        }
    }

    fun decode(raw: String?): QueueState {
        val lines = raw?.split('\n')?.filter { it.isNotEmpty() } ?: return QueueState()
        if (lines.isEmpty()) return QueueState()
        val header = lines.first().split('\t')
        return when (header.firstOrNull()) {
            VERSION -> decodeV1(lines, header)
            else -> QueueState()
        }
    }

    private fun decodeV1(lines: List<String>, header: List<String>): QueueState {
        if (header.size != HEADER_FIELDS) return QueueState()
        val historyCursor = header[1].toIntOrNull() ?: return QueueState()
        val repeatMode = header[2].toIntOrNull()?.let { RepeatMode.entries.getOrNull(it) }
            ?: return QueueState()
        val isShuffle = header[3].asFlag() ?: return QueueState()
        val autoplaySuppressed = header[4].asFlag() ?: return QueueState()
        val historyCount =
            header[5].toIntOrNull()?.takeIf { it in 0..MAX_HISTORY } ?: return QueueState()
        val queueCount =
            header[6].toIntOrNull()?.takeIf { it in 0..MAX_QUEUE_ENTRIES } ?: return QueueState()
        val contextCount =
            header[7].toIntOrNull()?.takeIf { it in 0..MAX_CONTEXT_ENTRIES } ?: return QueueState()
        val seedId = header[8].takeIf(String::isNotBlank)?.toLongOrNull()
            ?: if (header[8].isBlank()) null else return QueueState()
        val skippedCount =
            header[9].toIntOrNull()?.takeIf { it in 0..MAX_HISTORY } ?: return QueueState()
        val dismissedCount =
            header[10].toIntOrNull()?.takeIf { it in 0..MAX_QUEUE_ENTRIES } ?: return QueueState()
        if (historyCount > 0 && historyCursor !in 0 until historyCount) return QueueState()
        if (historyCount == 0 && historyCursor != -1) return QueueState()
        if (lines.size != 1 + historyCount + queueCount + contextCount + skippedCount + dismissedCount) {
            return QueueState()
        }

        var lineIndex = 1
        fun decodeEntries(count: Int, expectedType: Char): List<QueueEntry>? {
            val entries = mutableListOf<QueueEntry>()
            repeat(count) {
                val fields = lines.getOrNull(lineIndex++)?.split('\t') ?: return null
                if (fields.size != 4 + ITEM_FIELDS || fields[0].singleOrNull() != expectedType) return null
                val id = fields[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
                val origin = fields[2].toIntOrNull()?.let { QueueOrigin.entries.getOrNull(it) }
                    ?: return null
                val contextOrder = fields[3].takeIf(String::isNotEmpty)?.toIntOrNull()
                    ?: if (fields[3].isEmpty()) null else return null
                val track = decodeTrack(fields.drop(4).joinToString("\t")) ?: return null
                entries += QueueEntry(id, track, origin, contextOrder)
            }
            if (entries.map(QueueEntry::id).distinct().size != entries.size) return null
            return entries
        }

        val history = decodeEntries(historyCount, 'H') ?: return QueueState()
        val upcoming = decodeEntries(queueCount, 'U') ?: return QueueState()
        val context = decodeEntries(contextCount, 'C') ?: return QueueState()
        val skipped = buildSet {
            repeat(skippedCount) {
                val fields = lines.getOrNull(lineIndex++)?.split('\t') ?: return QueueState()
                if (fields.size != 2 || fields[0] != "S") return QueueState()
                add(fields[1].toLongOrNull()?.takeIf { it > 0L } ?: return QueueState())
            }
        }
        val dismissed = buildSet {
            repeat(dismissedCount) {
                val fields = lines.getOrNull(lineIndex++)?.split('\t') ?: return QueueState()
                if (fields.size != 2 || fields[0] != "D") return QueueState()
                add(unescape(fields[1]) ?: return QueueState())
            }
        }
        if (lineIndex != lines.size) return QueueState()
        val existingIds = (history + upcoming + context).mapTo(HashSet(), QueueEntry::id)
        if (seedId != null && seedId !in existingIds) return QueueState()
        return QueueState(
            history = history,
            historyCursor = historyCursor,
            upcomingEntries = upcoming,
            contextEntries = context,
            sessionSeedEntryId = seedId,
            repeatMode = repeatMode,
            isShuffle = isShuffle,
            autoplaySuppressed = autoplaySuppressed,
            dismissedAutoplayKeys = dismissed,
            skippedHistoryEntryIds = skipped.intersect(history.mapTo(HashSet(), QueueEntry::id)),
        )
    }

    private fun StringBuilder.appendEntry(type: Char, entry: QueueEntry) {
        append('\n').append(type).append('\t')
            .append(entry.id).append('\t')
            .append(entry.origin.ordinal).append('\t')
            .append(entry.contextOrder?.toString().orEmpty()).append('\t')
            .append(encodeTrack(entry.track))
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
        encodeOptional(track.artistMbid),
        encodeOptional(track.durationMs?.toString()),
        encodeOptional(track.contentRating),
    ).joinToString("\t")

    fun decodeTrack(line: String): HomeTrack? {
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
            isrc = decodeOptional(values[12]),
            providerTrackId = decodeOptional(values[13]),
            availableFormats = decodeOptional(values[14])
                ?.let(::decodeVariants)
                ?.map(TrackFormatVariant::format)
                .orEmpty(),
            availableVariants = decodeOptional(values[14])
                ?.let(::decodeVariants)
                .orEmpty(),
            artistMbid = decodeOptional(values[15]),
            durationMs = decodeOptional(values[16])?.toLongOrNull(),
            contentRating = decodeOptional(values[17]),
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
