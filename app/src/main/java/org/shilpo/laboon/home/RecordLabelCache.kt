package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.KeyValueStore
import org.shilpo.laboon.lyricsporn.LyricspornRecordLabel
import java.util.concurrent.ConcurrentHashMap

class RecordLabelCache(private val store: KeyValueStore? = null) {
    private val memoryCache = ConcurrentHashMap<String, LyricspornRecordLabel>()
    private val memoryNameToId = ConcurrentHashMap<String, String>()

    fun load(cacheKey: String): LyricspornRecordLabel? {
        memoryCache[cacheKey]?.let { return it }
        val store = store ?: return null
        val cache = store.getString(KEY_RECORD_LABEL_CACHE)
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?: return null
        if (cache.optInt("version") != CACHE_VERSION) return null
        val entries = cache.optJSONArray("entries") ?: return null

        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            if (entry.readString("key") != cacheKey) continue
            val label = runCatching { entry.toRecordLabel() }.getOrNull()
            if (label != null) {
                memoryCache[cacheKey] = label
                return label
            }
        }
        return null
    }

    fun save(cacheKey: String, label: LyricspornRecordLabel) {
        memoryCache[cacheKey] = label
        val store = store ?: return
        runCatching {
            val previousCache = store.getString(KEY_RECORD_LABEL_CACHE)
                ?.let { raw -> JSONObject(raw) }
            val previousEntries = previousCache
                ?.takeIf { it.optInt("version") == CACHE_VERSION }
                ?.optJSONArray("entries")
            val entries = JSONArray().put(label.toCacheJson(cacheKey))
            if (previousEntries != null) {
                for (index in 0 until previousEntries.length()) {
                    if (entries.length() >= MAX_LABELS) break
                    val entry = previousEntries.optJSONObject(index) ?: continue
                    if (entry.readString("key") == cacheKey) continue
                    entries.put(entry)
                }
            }
            store.putString(
                KEY_RECORD_LABEL_CACHE,
                JSONObject()
                    .put("version", CACHE_VERSION)
                    .put("entries", entries)
                    .toString(),
            )
        }
    }

    fun loadResolvedId(normalizedName: String): String? {
        memoryNameToId[normalizedName]?.let { return it }
        val store = store ?: return null
        val map = store.getString(KEY_RECORD_LABEL_NAME_TO_ID)
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?: return null
        val id = map.readString(normalizedName)
        if (id != null) {
            memoryNameToId[normalizedName] = id
        }
        return id
    }

    fun saveResolvedId(normalizedName: String, appleLabelId: String) {
        memoryNameToId[normalizedName] = appleLabelId
        val store = store ?: return
        runCatching {
            val map = store.getString(KEY_RECORD_LABEL_NAME_TO_ID)
                ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
                ?: JSONObject()
            map.put(normalizedName, appleLabelId)
            store.putString(KEY_RECORD_LABEL_NAME_TO_ID, map.toString())
        }
    }

    fun clearMemory() {
        memoryCache.clear()
        memoryNameToId.clear()
    }

    private fun LyricspornRecordLabel.toCacheJson(cacheKey: String) = JSONObject().apply {
        put("key", cacheKey)
        put("id", id)
        put("name", name)
        putNullable("url", url)
        putNullable("description", description)
        putNullable("artworkUrl", artworkUrl)
        putNullable("editorialArtworkUrl", editorialArtworkUrl)
        put("latestReleases", JSONArray().apply {
            latestReleases.forEach { put(it.toCacheJson()) }
        })
        put("topReleases", JSONArray().apply {
            topReleases.forEach { put(it.toCacheJson()) }
        })
        put("artists", JSONArray().apply {
            artists.forEach { put(it.toCacheJson()) }
        })
    }

    private fun JSONObject.toRecordLabel(): LyricspornRecordLabel? {
        val id = readString("id") ?: return null
        val name = readString("name") ?: return null
        return LyricspornRecordLabel(
            id = id,
            name = name,
            url = readString("url"),
            description = readString("description"),
            artworkUrl = readString("artworkUrl"),
            editorialArtworkUrl = readString("editorialArtworkUrl"),
            latestReleases = optJSONArray("latestReleases").toHomeAlbums(),
            topReleases = optJSONArray("topReleases").toHomeAlbums(),
            artists = optJSONArray("artists").toHomeArtists(),
        )
    }

    private fun HomeAlbum.toCacheJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        putNullable("artworkUrl", artworkUrl)
        put("playCount", playCount)
        putNullable("appleCatalogId", appleCatalogId)
    }

    private fun JSONObject.toHomeAlbum(): HomeAlbum? {
        val id = readString("id") ?: return null
        val title = readString("title") ?: return null
        val artist = readString("artist") ?: return null
        return HomeAlbum(
            id = id,
            title = title,
            artist = artist,
            artworkUrl = readString("artworkUrl"),
            playCount = optLong("playCount", 0L),
            appleCatalogId = readString("appleCatalogId"),
        )
    }

    private fun JSONArray?.toHomeAlbums(): List<HomeAlbum> = buildList {
        val array = this@toHomeAlbums ?: return@buildList
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.toHomeAlbum()?.let(::add)
        }
    }

    private fun HomeArtist.toCacheJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        putNullable("imageUrl", imageUrl)
        putNullable("appleCatalogId", appleCatalogId)
    }

    private fun JSONObject.toHomeArtist(): HomeArtist? {
        val id = readString("id") ?: return null
        val name = readString("name") ?: return null
        return HomeArtist(
            id = id,
            name = name,
            imageUrl = readString("imageUrl"),
            appleCatalogId = readString("appleCatalogId"),
        )
    }

    private fun JSONArray?.toHomeArtists(): List<HomeArtist> = buildList {
        val array = this@toHomeArtists ?: return@buildList
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.toHomeArtist()?.let(::add)
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value != null) put(key, value)
    }

    private fun JSONObject.readString(key: String): String? =
        optString(key).takeIf { has(key) && !isNull(key) && it.isNotBlank() }

    private companion object {
        const val KEY_RECORD_LABEL_CACHE = "record_label_cache_v2"
        const val KEY_RECORD_LABEL_NAME_TO_ID = "record_label_name_to_id_v2"
        const val CACHE_VERSION = 2
        const val MAX_LABELS = 16
    }
}
