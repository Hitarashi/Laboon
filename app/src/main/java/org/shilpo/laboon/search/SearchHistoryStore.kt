package org.shilpo.laboon.search

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.KeyValueStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import java.util.Locale

class SearchHistoryStore(private val store: KeyValueStore) {

    fun load(): List<String> {
        val raw = store.getString(KEY_SEARCH_HISTORY) ?: return emptyList()
        val entries = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val seen = mutableSetOf<String>()
        return buildList {
            for (index in 0 until entries.length()) {
                val query = entries.optString(index).trim()
                val key = query.lowercase(Locale.ROOT)
                if (query.isNotEmpty() && seen.add(key)) add(query)
                if (size == MAX_HISTORY_SIZE) break
            }
        }
    }

    fun add(query: String): List<String> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return load()

        val history = (listOf(trimmedQuery) + load().filterNot {
            it.equals(trimmedQuery, ignoreCase = true)
        }).take(MAX_HISTORY_SIZE)
        save(history)
        return history
    }

    fun remove(query: String): List<String> {
        val history = load().filterNot { it.equals(query, ignoreCase = true) }
        save(history)
        return history
    }

    fun loadTracks(): List<HomeTrack> {
        val raw = store.getString(KEY_TRACK_HISTORY) ?: return emptyList()
        val entries = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until entries.length()) {
                val track = entries.optJSONObject(index)?.toHomeTrack() ?: continue
                if (none { trackKey(it) == trackKey(track) }) add(track)
                if (size == MAX_HISTORY_SIZE) break
            }
        }
    }

    fun addTrack(track: HomeTrack): List<HomeTrack> {
        val key = trackKey(track)
        val history = (listOf(track) + loadTracks().filterNot { trackKey(it) == key })
            .take(MAX_HISTORY_SIZE)
        saveTracks(history)
        return history
    }

    fun removeTrack(track: HomeTrack): List<HomeTrack> {
        val key = trackKey(track)
        val history = loadTracks().filterNot { trackKey(it) == key }
        saveTracks(history)
        return history
    }

    fun clear() {
        store.remove(KEY_SEARCH_HISTORY)
        store.remove(KEY_TRACK_HISTORY)
    }

    private fun save(history: List<String>) {
        if (history.isEmpty()) {
            clear()
        } else {
            store.putString(KEY_SEARCH_HISTORY, JSONArray(history).toString())
        }
    }

    private fun saveTracks(tracks: List<HomeTrack>) {
        if (tracks.isEmpty()) {
            store.remove(KEY_TRACK_HISTORY)
        } else {
            store.putString(
                KEY_TRACK_HISTORY,
                JSONArray().apply { tracks.forEach { put(it.toJson()) } }.toString(),
            )
        }
    }

    private fun trackKey(track: HomeTrack): String =
        track.providerTrackId?.let { "apple:$it" } ?: track.id

    private fun HomeTrack.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        album?.let { put("album", it) }
        artworkUrl?.let { put("artworkUrl", it) }
        put("playCount", playCount)
        source?.let { put("source", it) }
        backendTrackId?.let { put("backendTrackId", it) }
        put("isCached", isCached)
        codec?.let { put("codec", it) }
        mbid?.let { put("mbid", it) }
        isrc?.let { put("isrc", it) }
        providerTrackId?.let { put("providerTrackId", it) }
        put("availableFormats", JSONArray(availableFormats))
        put("availableVariants", JSONArray().apply {
            availableVariants.forEach { variant ->
                put(JSONObject().apply {
                    put("format", variant.format)
                    put("backendTrackId", variant.backendTrackId)
                    variant.fileSizeBytes?.let { put("fileSizeBytes", it) }
                })
            }
        })
        durationMs?.let { put("durationMs", it) }
    }

    private fun JSONObject.toHomeTrack(): HomeTrack? {
        val id = optString("id").takeIf(String::isNotBlank) ?: return null
        val title = optString("title").takeIf(String::isNotBlank) ?: return null
        val artist = optString("artist").takeIf(String::isNotBlank) ?: return null
        val variants = optJSONArray("availableVariants")?.let { values ->
            buildList {
                for (index in 0 until values.length()) {
                    val variant = values.optJSONObject(index) ?: continue
                    val format = variant.optString("format").takeIf(String::isNotBlank)
                        ?: continue
                    val backendTrackId = variant.optInt("backendTrackId")
                        .takeIf { variant.has("backendTrackId") && it > 0 } ?: continue
                    add(
                        TrackFormatVariant(
                            format = format,
                            backendTrackId = backendTrackId,
                            fileSizeBytes = variant.optLong("fileSizeBytes")
                                .takeIf { variant.has("fileSizeBytes") && it > 0L },
                        )
                    )
                }
            }
        }.orEmpty()
        val formats = optJSONArray("availableFormats")?.let { values ->
            buildList {
                for (index in 0 until values.length()) {
                    values.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.orEmpty()
        return HomeTrack(
            id = id,
            title = title,
            artist = artist,
            album = optString("album").takeIf(String::isNotBlank),
            artworkUrl = optString("artworkUrl").takeIf(String::isNotBlank),
            playCount = optLong("playCount", 0L),
            source = optString("source").takeIf(String::isNotBlank),
            backendTrackId = optInt("backendTrackId")
                .takeIf { has("backendTrackId") && it > 0 },
            isCached = optBoolean("isCached", false),
            codec = optString("codec").takeIf(String::isNotBlank),
            mbid = optString("mbid").takeIf(String::isNotBlank),
            isrc = optString("isrc").takeIf(String::isNotBlank),
            providerTrackId = optString("providerTrackId").takeIf(String::isNotBlank),
            availableFormats = formats.ifEmpty { variants.map(TrackFormatVariant::format) },
            availableVariants = variants,
            durationMs = optLong("durationMs").takeIf { has("durationMs") && it > 0L },
        )
    }

    private companion object {
        const val KEY_SEARCH_HISTORY = "search_history_v1"
        const val KEY_TRACK_HISTORY = "search_track_history_v1"
        const val MAX_HISTORY_SIZE = 12
    }
}
