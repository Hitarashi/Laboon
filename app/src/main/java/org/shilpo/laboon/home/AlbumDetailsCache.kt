package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.KeyValueStore
import org.shilpo.laboon.lyricsporn.LyricspornAlbum
import org.shilpo.laboon.lyricsporn.LyricspornAlbumTrack
import org.shilpo.laboon.lyricsporn.LyricspornAlbumVersion

class AlbumDetailsCache(private val store: KeyValueStore) {

    fun load(cacheKey: String): AlbumDetailsResult.Success? {
        val cache = store.getString(KEY_ALBUM_DETAILS_CACHE)
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?: return null
        if (cache.optInt("version") != CACHE_VERSION) return null
        val entries = cache.optJSONArray("entries") ?: return null

        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            if (entry.readString("key") != cacheKey) continue
            return runCatching { entry.toAlbumDetailsSuccess() }.getOrNull()
        }
        return null
    }

    fun save(cacheKey: String, result: AlbumDetailsResult.Success) {
        runCatching {
            val previousCache = store.getString(KEY_ALBUM_DETAILS_CACHE)
                ?.let { raw -> JSONObject(raw) }
            val previousEntries = previousCache
                ?.takeIf { it.optInt("version") == CACHE_VERSION }
                ?.optJSONArray("entries")
            val entries = JSONArray().put(result.toCacheJson(cacheKey))
            if (previousEntries != null) {
                for (index in 0 until previousEntries.length()) {
                    if (entries.length() >= MAX_ALBUMS) break
                    val entry = previousEntries.optJSONObject(index) ?: continue
                    if (entry.readString("key") == cacheKey) continue
                    entries.put(entry)
                }
            }
            store.putString(
                KEY_ALBUM_DETAILS_CACHE,
                JSONObject()
                    .put("version", CACHE_VERSION)
                    .put("entries", entries)
                    .toString(),
            )
        }
    }

    private fun AlbumDetailsResult.Success.toCacheJson(cacheKey: String) = JSONObject().apply {
        put("key", cacheKey)
        put("album", album.toCacheJson())
        put("tracks", JSONArray().apply {
            this@toCacheJson.tracks.forEach { put(it.toCacheJson()) }
        })
    }

    private fun JSONObject.toAlbumDetailsSuccess(): AlbumDetailsResult.Success {
        val album = optJSONObject("album")?.toAlbum() ?: error("Missing cached album")
        val tracks = optJSONArray("tracks").toHomeTracks()
        return AlbumDetailsResult.Success(album, tracks)
    }

    private fun LyricspornAlbum.toCacheJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        putNullable("artistName", artistName)
        putNullable("artistUrl", artistUrl)
        putNullable("url", url)
        putNullable("artworkUrl", artworkUrl)
        put("genres", JSONArray().apply { genres.forEach { put(it) } })
        putNullable("releaseDate", releaseDate)
        putNullable("trackCount", trackCount)
        putNullable("contentRating", contentRating)
        putNullable("copyright", copyright)
        putNullable("recordLabel", recordLabel)
        putNullable("editorialNotes", editorialNotes)
        put("tracks", JSONArray().apply { tracks.forEach { put(it.toCacheJson()) } })
        put("otherVersions", JSONArray().apply {
            otherVersions.forEach { put(it.toCacheJson()) }
        })
    }

    private fun JSONObject.toAlbum(): LyricspornAlbum? {
        val id = readString("id") ?: return null
        val name = readString("name") ?: return null
        return LyricspornAlbum(
            id = id,
            name = name,
            artistName = readString("artistName"),
            artistUrl = readString("artistUrl"),
            url = readString("url"),
            artworkUrl = readString("artworkUrl"),
            genres = optJSONArray("genres").toStringList(),
            releaseDate = readString("releaseDate"),
            trackCount = readInt("trackCount"),
            contentRating = readString("contentRating"),
            copyright = readString("copyright"),
            recordLabel = readString("recordLabel"),
            editorialNotes = readString("editorialNotes"),
            tracks = optJSONArray("tracks").toAlbumTracks(),
            otherVersions = optJSONArray("otherVersions").toAlbumVersions(),
        )
    }

    private fun LyricspornAlbumTrack.toCacheJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        putNullable("artistName", artistName)
        putNullable("albumName", albumName)
        putNullable("artworkUrl", artworkUrl)
        putNullable("durationMs", durationMs)
        putNullable("isrc", isrc)
        putNullable("contentRating", contentRating)
        putNullable("url", url)
    }

    private fun JSONObject.toAlbumTrack(): LyricspornAlbumTrack? {
        val id = readString("id") ?: return null
        val name = readString("name") ?: return null
        return LyricspornAlbumTrack(
            id = id,
            name = name,
            artistName = readString("artistName"),
            albumName = readString("albumName"),
            artworkUrl = readString("artworkUrl"),
            durationMs = readLong("durationMs"),
            isrc = readString("isrc"),
            contentRating = readString("contentRating"),
            url = readString("url"),
        )
    }

    private fun LyricspornAlbumVersion.toCacheJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        putNullable("artistName", artistName)
        putNullable("artworkUrl", artworkUrl)
        putNullable("releaseDate", releaseDate)
        putNullable("trackCount", trackCount)
        putNullable("contentRating", contentRating)
    }

    private fun JSONObject.toAlbumVersion(): LyricspornAlbumVersion? {
        val id = readString("id") ?: return null
        val name = readString("name") ?: return null
        return LyricspornAlbumVersion(
            id = id,
            name = name,
            artistName = readString("artistName"),
            artworkUrl = readString("artworkUrl"),
            releaseDate = readString("releaseDate"),
            trackCount = readInt("trackCount"),
            contentRating = readString("contentRating"),
        )
    }

    private fun JSONArray?.toAlbumTracks(): List<LyricspornAlbumTrack> = buildList {
        val array = this@toAlbumTracks ?: return@buildList
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.toAlbumTrack()?.let(::add)
        }
    }

    private fun JSONArray?.toAlbumVersions(): List<LyricspornAlbumVersion> = buildList {
        val array = this@toAlbumVersions ?: return@buildList
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.toAlbumVersion()?.let(::add)
        }
    }

    private fun HomeTrack.toCacheJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        putNullable("album", album)
        putNullable("artworkUrl", artworkUrl)
        put("playCount", playCount)
        putNullable("source", source)
        putNullable("streamUrl", streamUrl)
        putNullable("backendTrackId", backendTrackId)
        put("isCached", isCached)
        putNullable("codec", codec)
        putNullable("mbid", mbid)
        putNullable("isrc", isrc)
        putNullable("providerTrackId", providerTrackId)
        put("availableFormats", JSONArray().apply { availableFormats.forEach { put(it) } })
        put("availableVariants", JSONArray().apply {
            availableVariants.forEach { variant ->
                put(JSONObject().apply {
                    put("format", variant.format)
                    put("backendTrackId", variant.backendTrackId)
                    putNullable("fileSizeBytes", variant.fileSizeBytes)
                })
            }
        })
        putNullable("durationMs", durationMs)
        putNullable("contentRating", contentRating)
    }

    private fun JSONObject.toHomeTrack(): HomeTrack? {
        val id = readString("id") ?: return null
        val title = readString("title") ?: return null
        val artist = readString("artist") ?: return null
        val variants = optJSONArray("availableVariants").toTrackVariants()
        return HomeTrack(
            id = id,
            title = title,
            artist = artist,
            album = readString("album"),
            artworkUrl = readString("artworkUrl"),
            playCount = optLong("playCount", 0L),
            source = readString("source"),
            streamUrl = readString("streamUrl"),
            backendTrackId = readInt("backendTrackId"),
            isCached = optBoolean("isCached", false),
            codec = readString("codec"),
            mbid = readString("mbid"),
            isrc = readString("isrc"),
            providerTrackId = readString("providerTrackId"),
            availableFormats = optJSONArray("availableFormats").toStringList()
                .ifEmpty { variants.map(TrackFormatVariant::format) },
            availableVariants = variants,
            durationMs = readLong("durationMs"),
            contentRating = readString("contentRating"),
        )
    }

    private fun JSONArray?.toHomeTracks(): List<HomeTrack> = buildList {
        val array = this@toHomeTracks ?: return@buildList
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.toHomeTrack()?.let(::add)
        }
    }

    private fun JSONArray?.toTrackVariants(): List<TrackFormatVariant> = buildList {
        val array = this@toTrackVariants ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val format = item.readString("format") ?: continue
            val backendTrackId = item.readInt("backendTrackId") ?: continue
            add(
                TrackFormatVariant(
                    format = format,
                    backendTrackId = backendTrackId,
                    fileSizeBytes = item.readLong("fileSizeBytes"),
                ),
            )
        }
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        val array = this@toStringList ?: return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value != null) put(key, value)
    }

    private fun JSONObject.putNullable(key: String, value: Long?) {
        if (value != null) put(key, value)
    }

    private fun JSONObject.putNullable(key: String, value: Int?) {
        if (value != null) put(key, value)
    }

    private fun JSONObject.readString(key: String): String? =
        optString(key).takeIf { has(key) && !isNull(key) && it.isNotBlank() }

    private fun JSONObject.readLong(key: String): Long? =
        optLong(key).takeIf { has(key) && !isNull(key) }

    private fun JSONObject.readInt(key: String): Int? =
        optInt(key).takeIf { has(key) && !isNull(key) }

    private companion object {
        const val KEY_ALBUM_DETAILS_CACHE = "album_details_cache_v2"
        const val CACHE_VERSION = 2
        const val MAX_ALBUMS = 12
    }
}
