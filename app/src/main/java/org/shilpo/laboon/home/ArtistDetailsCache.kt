package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.KeyValueStore
import org.shilpo.laboon.lyricsporn.LyricspornArtist
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem

class ArtistDetailsCache(private val store: KeyValueStore) {

    fun load(cacheKey: String): ArtistDetailsResult.Success? {
        val cache = store.getString(KEY_ARTIST_DETAILS_CACHE)
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?: return null
        if (cache.optInt("version") != CACHE_VERSION) return null
        val entries = cache.optJSONArray("entries") ?: return null

        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            if (entry.readString("key") != cacheKey) continue
            return runCatching { entry.toArtistDetailsSuccess() }.getOrNull()
        }
        return null
    }

    fun save(cacheKey: String, result: ArtistDetailsResult.Success) {
        runCatching {
            val previousCache = store.getString(KEY_ARTIST_DETAILS_CACHE)
                ?.let { raw -> JSONObject(raw) }
            val previousEntries = previousCache
                ?.takeIf { it.optInt("version") == CACHE_VERSION }
                ?.optJSONArray("entries")
            val entries = JSONArray().put(result.toCacheJson(cacheKey))
            if (previousEntries != null) {
                for (index in 0 until previousEntries.length()) {
                    if (entries.length() >= MAX_ARTISTS) break
                    val entry = previousEntries.optJSONObject(index) ?: continue
                    if (entry.readString("key") == cacheKey) continue
                    entries.put(entry)
                }
            }
            store.putString(
                KEY_ARTIST_DETAILS_CACHE,
                JSONObject()
                    .put("version", CACHE_VERSION)
                    .put("entries", entries)
                    .toString(),
            )
        }
    }

    fun loadResolvedId(normalizedName: String): String? {
        val map = store.getString(KEY_ARTIST_NAME_TO_ID)
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?: return null
        return map.readString(normalizedName)
    }

    fun saveResolvedId(normalizedName: String, appleArtistId: String) {
        runCatching {
            val map = store.getString(KEY_ARTIST_NAME_TO_ID)
                ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
                ?: JSONObject()
            map.put(normalizedName, appleArtistId)
            store.putString(KEY_ARTIST_NAME_TO_ID, map.toString())
        }
    }

    private fun ArtistDetailsResult.Success.toCacheJson(cacheKey: String) = JSONObject().apply {
        put("key", cacheKey)
        put("artist", artist.toCacheJson())
        put("topSongs", JSONArray().apply {
            this@toCacheJson.topSongs.forEach { put(it.toCacheJson()) }
        })
        putNullable("latestRelease", latestRelease?.toCacheJson())
        put("albums", JSONArray().apply {
            this@toCacheJson.albums.forEach { put(it.toCacheJson()) }
        })
        put("singles", JSONArray().apply {
            this@toCacheJson.singles.forEach { put(it.toCacheJson()) }
        })
        put("similarArtists", JSONArray().apply {
            this@toCacheJson.similarArtists.forEach { put(it.toCacheJson()) }
        })
    }

    private fun JSONObject.toArtistDetailsSuccess(): ArtistDetailsResult.Success {
        val artist = optJSONObject("artist")?.toArtist() ?: error("Missing cached artist")
        val topSongs = optJSONArray("topSongs").toHomeTracks()
        val latestRelease = optJSONObject("latestRelease")?.toHomeAlbum()
        val albums = optJSONArray("albums").toHomeAlbums()
        val singles = optJSONArray("singles").toHomeAlbums()
        val similarArtists = optJSONArray("similarArtists").toHomeArtists()
        return ArtistDetailsResult.Success(
            artist = artist,
            topSongs = topSongs,
            latestRelease = latestRelease,
            albums = albums,
            singles = singles,
            similarArtists = similarArtists,
        )
    }

    private fun LyricspornArtist.toCacheJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        putNullable("url", url)
        putNullable("artworkUrl", artworkUrl)
        put("genres", JSONArray().apply { genres.forEach { put(it) } })
        putNullable("editorialNotes", editorialNotes)
        put("topSongs", JSONArray().apply { topSongs.forEach { put(it.toCacheJson()) } })
        putNullable("latestRelease", latestRelease?.toCacheJson())
        put("fullAlbums", JSONArray().apply { fullAlbums.forEach { put(it.toCacheJson()) } })
        put("singles", JSONArray().apply { singles.forEach { put(it.toCacheJson()) } })
        put(
            "similarArtists",
            JSONArray().apply { similarArtists.forEach { put(it.toCacheJson()) } })
    }

    private fun JSONObject.toArtist(): LyricspornArtist? {
        val id = readString("id") ?: return null
        val name = readString("name") ?: return null
        return LyricspornArtist(
            id = id,
            name = name,
            url = readString("url"),
            artworkUrl = readString("artworkUrl"),
            genres = optJSONArray("genres").toStringList(),
            editorialNotes = readString("editorialNotes"),
            topSongs = optJSONArray("topSongs").toCatalogItems(),
            latestRelease = optJSONObject("latestRelease")?.toCatalogItem(),
            fullAlbums = optJSONArray("fullAlbums").toCatalogItems(),
            singles = optJSONArray("singles").toCatalogItems(),
            similarArtists = optJSONArray("similarArtists").toCatalogItems(),
        )
    }

    private fun LyricspornCatalogItem.toCacheJson() = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("name", name)
        putNullable("artistName", artistName)
        putNullable("albumName", albumName)
        putNullable("artworkUrl", artworkUrl)
        putNullable("durationMs", durationMs)
        putNullable("isrc", isrc)
    }

    private fun JSONObject.toCatalogItem(): LyricspornCatalogItem? {
        val id = readString("id") ?: return null
        val type = readString("type") ?: "unknown"
        val name = readString("name") ?: return null
        return LyricspornCatalogItem(
            id = id,
            type = type,
            name = name,
            artistName = readString("artistName"),
            albumName = readString("albumName"),
            artworkUrl = readString("artworkUrl"),
            durationMs = readLong("durationMs"),
            isrc = readString("isrc"),
        )
    }

    private fun JSONArray?.toCatalogItems(): List<LyricspornCatalogItem> = buildList {
        val array = this@toCatalogItems ?: return@buildList
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.toCatalogItem()?.let(::add)
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
        put("playCount", playCount)
        putNullable("imageUrl", imageUrl)
        putNullable("appleCatalogId", appleCatalogId)
    }

    private fun JSONObject.toHomeArtist(): HomeArtist? {
        val id = readString("id") ?: return null
        val name = readString("name") ?: return null
        return HomeArtist(
            id = id,
            name = name,
            playCount = optLong("playCount", 0L),
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

    private fun JSONArray?.toStringList(): List<String> = buildList {
        val array = this@toStringList ?: return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value != null) put(key, value)
    }

    private fun JSONObject.putNullable(key: String, value: JSONObject?) {
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
        const val KEY_ARTIST_DETAILS_CACHE = "artist_details_cache_v1"
        const val KEY_ARTIST_NAME_TO_ID = "artist_name_to_id_v1"
        const val CACHE_VERSION = 1
        const val MAX_ARTISTS = 16
    }
}
