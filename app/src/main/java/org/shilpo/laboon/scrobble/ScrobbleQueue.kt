package org.shilpo.laboon.scrobble

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.KeyValueStore
import org.shilpo.laboon.auth.SharedPreferencesKeyValueStore
import java.util.UUID

class ScrobbleQueue(
    private val store: KeyValueStore,
    private val maxCapacity: Int = DEFAULT_MAX_CAPACITY,
) {
    constructor(
        context: Context,
        prefsName: String = DEFAULT_PREFS_NAME,
        maxCapacity: Int = DEFAULT_MAX_CAPACITY,
    ) : this(
        store = SharedPreferencesKeyValueStore(context, prefsName),
        maxCapacity = maxCapacity,
    )

    @Synchronized
    fun enqueue(item: QueuedScrobble) {
        val list = loadQueue().toMutableList()
        list.add(item)
        val trimmed = if (list.size > maxCapacity) {
            list.takeLast(maxCapacity)
        } else {
            list
        }
        saveQueue(trimmed)
    }

    @Synchronized
    fun peek(limit: Int = 50): List<QueuedScrobble> {
        return loadQueue().take(limit.coerceAtLeast(0))
    }

    @Synchronized
    fun remove(ids: Set<String>) {
        if (ids.isEmpty()) return
        val list = loadQueue()
        val updated = list.filterNot { it.id in ids }
        saveQueue(updated)
    }

    @Synchronized
    fun size(): Int = loadQueue().size

    @Synchronized
    fun clear() {
        store.remove(KEY_QUEUE)
    }

    private fun loadQueue(): List<QueuedScrobble> {
        val raw = store.getString(KEY_QUEUE) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = ArrayList<QueuedScrobble>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    QueuedScrobble(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        artist = obj.getString("artist"),
                        track = obj.getString("track"),
                        album = if (obj.has("album") && !obj.isNull("album")) obj.getString("album") else null,
                        timestampEpochSeconds = obj.getLong("timestampEpochSeconds"),
                        durationSeconds = if (obj.has("durationSeconds") && !obj.isNull("durationSeconds")) {
                            obj.getLong("durationSeconds")
                        } else null,
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveQueue(items: List<QueuedScrobble>) {
        if (items.isEmpty()) {
            store.remove(KEY_QUEUE)
            return
        }
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("artist", item.artist)
            obj.put("track", item.track)
            if (item.album != null) {
                obj.put("album", item.album)
            }
            obj.put("timestampEpochSeconds", item.timestampEpochSeconds)
            if (item.durationSeconds != null) {
                obj.put("durationSeconds", item.durationSeconds)
            }
            array.put(obj)
        }
        store.putString(KEY_QUEUE, array.toString())
    }

    companion object {
        const val DEFAULT_MAX_CAPACITY = 500
        const val DEFAULT_PREFS_NAME = "laboon_scrobble_queue"
        const val KEY_QUEUE = "queued_scrobbles"
    }
}
