package org.shilpo.laboon.rip

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.KeyValueStore

/**
 * Persistent record of provider track ids the server has already been asked about and
 * answered *positively* for ("this track is ripped, here are its formats").
 *
 * Negative verdicts ("looked up, not cached") are deliberately never persisted: they go
 * stale the moment a rip finishes, so they must be re-checked on the next scan. Positive
 * verdicts only change if the server's cache is wiped, which the app models as an explicit
 * [invalidate] call.
 */
class AutoRipCache(private val store: KeyValueStore) {

    private val lock = Any()
    private var entries: MutableMap<String, List<String>>? = null

    fun isCached(providerTrackId: String): Boolean =
        synchronized(lock) { load()[providerTrackId]?.isNotEmpty() == true }

    fun cachedFormats(providerTrackId: String): List<String> =
        synchronized(lock) { load()[providerTrackId].orEmpty() }

    /** Ids we have a positive verdict for. Anything else is unknown and gets looked up. */
    fun knownCachedIds(candidates: Collection<String>): Set<String> = synchronized(lock) {
        val cached = load()
        candidates.filterTo(LinkedHashSet()) { cached[it]?.isNotEmpty() == true }
    }

    fun rememberCached(providerTrackId: String, formats: List<String>) {
        val id = providerTrackId.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?: return
        if (formats.isEmpty()) return
        synchronized(lock) {
            val current = load()
            current[id] = formats.toList()
            save(current)
        }
    }

    fun rememberCached(formatsById: Map<String, List<String>>) {
        if (formatsById.isEmpty()) return
        synchronized(lock) {
            val current = load()
            var changed = false
            for ((id, formats) in formatsById) {
                val key = id.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: continue
                if (formats.isEmpty()) continue
                current[key] = formats.toList()
                changed = true
            }
            if (changed) save(current)
        }
    }

    /** Drops positive verdicts so the next scan re-looks-them-up (album reload, refresh). */
    fun invalidate(providerTrackIds: Collection<String>) {
        if (providerTrackIds.isEmpty()) return
        synchronized(lock) {
            val current = load()
            var changed = false
            for (id in providerTrackIds) {
                if (current.remove(id.trim()) != null) changed = true
            }
            if (changed) save(current)
        }
    }

    fun clear() {
        synchronized(lock) {
            entries = mutableMapOf()
            store.remove(KEY_CACHE)
        }
    }

    private fun load(): MutableMap<String, List<String>> {
        entries?.let { return it }
        val raw = store.getString(KEY_CACHE)
        val loaded = mutableMapOf<String, List<String>>()
        if (raw != null) {
            val root = runCatching { JSONObject(raw) }.getOrNull()
            if (root?.optInt("version") == CACHE_VERSION) {
                val array = root.optJSONArray("entries")
                for (index in 0 until (array?.length() ?: 0)) {
                    val entry = array.optJSONObject(index) ?: continue
                    val id = entry.optString("id").trim()
                    if (id.isEmpty() || !id.all(Char::isDigit)) continue
                    val formats = entry.optJSONArray("formats")?.let { values ->
                        buildList {
                            for (formatIndex in 0 until values.length()) {
                                values.optString(formatIndex).trim()
                                    .takeIf(String::isNotEmpty)
                                    ?.let(::add)
                            }
                        }
                    }.orEmpty()
                    if (formats.isEmpty()) continue
                    loaded[id] = formats
                    if (loaded.size >= MAX_ENTRIES) break
                }
            }
        }
        entries = loaded
        return loaded
    }

    private fun save(current: MutableMap<String, List<String>>) {
        val trimmed: Map<String, List<String>> = if (current.size > MAX_ENTRIES) {
            current.entries.toList().takeLast(MAX_ENTRIES)
                .associate { it.key to it.value }
        } else {
            current
        }
        entries = LinkedHashMap(trimmed)
        if (trimmed.isEmpty()) {
            store.remove(KEY_CACHE)
            return
        }
        store.putString(
            KEY_CACHE,
            JSONObject().apply {
                put("version", CACHE_VERSION)
                put("entries", JSONArray().apply {
                    trimmed.forEach { (id, formats) ->
                        put(JSONObject().apply {
                            put("id", id)
                            put("formats", JSONArray(formats))
                        })
                    }
                })
            }.toString(),
        )
    }

    private companion object {
        const val KEY_CACHE = "auto_rip_cache_v1"
        const val CACHE_VERSION = 1
        const val MAX_ENTRIES = 500
    }
}
