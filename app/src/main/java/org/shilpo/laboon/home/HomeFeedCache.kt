package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.KeyValueStore

class HomeFeedCache(private val store: KeyValueStore) {

    fun load(): HomeFeedState? {
        val raw = store.getString(KEY_FEED_CACHE) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val rotation = decodeTracks(json.optJSONArray("rotation"))
            val recommended = decodeTracks(json.optJSONArray("recommended"))
            val topArtists = decodeArtists(json.optJSONArray("topArtists"))
            val topAlbums = decodeAlbums(json.optJSONArray("topAlbums"))
            val topTracks = decodeTracks(json.optJSONArray("topTracks"))
            val regionalTrending = decodeTracks(json.optJSONArray("regionalTrending"))
            val globalTrending = decodeTracks(json.optJSONArray("globalTrending"))
            val weeklyPicks = decodeTracks(json.optJSONArray("weeklyPicks"))
            val regionName = json.optString("regionName", "Regional").ifEmpty { "Regional" }

            val hasAnyData = rotation.isNotEmpty() ||
                    recommended.isNotEmpty() ||
                    topArtists.isNotEmpty() ||
                    topAlbums.isNotEmpty() ||
                    topTracks.isNotEmpty() ||
                    regionalTrending.isNotEmpty() ||
                    globalTrending.isNotEmpty() ||
                    weeklyPicks.isNotEmpty()

            if (!hasAnyData) {
                null
            } else {
                HomeFeedState(
                    rotation = if (rotation.isNotEmpty()) SectionState(
                        SectionLoadState.LOADED,
                        rotation
                    ) else SectionState(),
                    recommended = if (recommended.isNotEmpty()) SectionState(
                        SectionLoadState.LOADED,
                        recommended
                    ) else SectionState(),
                    topArtists = if (topArtists.isNotEmpty()) SectionState(
                        SectionLoadState.LOADED,
                        topArtists
                    ) else SectionState(),
                    topAlbums = if (topAlbums.isNotEmpty()) SectionState(
                        SectionLoadState.LOADED,
                        topAlbums
                    ) else SectionState(),
                    topTracks = if (topTracks.isNotEmpty()) SectionState(
                        SectionLoadState.LOADED,
                        topTracks
                    ) else SectionState(),
                    regionalTrending = if (regionalTrending.isNotEmpty()) SectionState(
                        SectionLoadState.LOADED,
                        regionalTrending
                    ) else SectionState(),
                    globalTrending = if (globalTrending.isNotEmpty()) SectionState(
                        SectionLoadState.LOADED,
                        globalTrending
                    ) else SectionState(),
                    regionName = regionName,
                    weeklyPicks = if (weeklyPicks.isNotEmpty()) SectionState(
                        SectionLoadState.LOADED,
                        weeklyPicks
                    ) else SectionState(),
                )
            }
        }.getOrNull()
    }

    fun save(state: HomeFeedState) {
        runCatching {
            val json = JSONObject().apply {
                put("rotation", encodeTracks(state.rotation.items))
                put("recommended", encodeTracks(state.recommended.items))
                put("topArtists", encodeArtists(state.topArtists.items))
                put("topAlbums", encodeAlbums(state.topAlbums.items))
                put("topTracks", encodeTracks(state.topTracks.items))
                put("regionalTrending", encodeTracks(state.regionalTrending.items))
                put("globalTrending", encodeTracks(state.globalTrending.items))
                put("weeklyPicks", encodeTracks(state.weeklyPicks.items))
                put("regionName", state.regionName)
            }
            store.putString(KEY_FEED_CACHE, json.toString())
        }
    }

    fun clear() {
        store.remove(KEY_FEED_CACHE)
    }

    private fun encodeTracks(tracks: List<HomeTrack>): JSONArray {
        val array = JSONArray()
        tracks.forEach { track ->
            val obj = JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artist", track.artist)
                track.album?.let { put("album", it) }
                track.artworkUrl?.let { put("artworkUrl", it) }
                put("playCount", track.playCount)
                track.source?.let { put("source", it) }
                track.streamUrl?.let { put("streamUrl", it) }
                track.backendTrackId?.let { put("backendTrackId", it) }
                put("isCached", track.isCached)
                track.codec?.let { put("codec", it) }
                track.mbid?.let { put("mbid", it) }
                track.isrc?.let { put("isrc", it) }
                track.providerTrackId?.let { put("providerTrackId", it) }
            }
            array.put(obj)
        }
        return array
    }

    private fun decodeTracks(array: JSONArray?): List<HomeTrack> {
        if (array == null) return emptyList()
        val list = ArrayList<HomeTrack>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: continue
            val artist = obj.optString("artist").takeIf { it.isNotBlank() } ?: continue
            list.add(
                HomeTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = obj.optString("album").takeIf { it.isNotBlank() },
                    artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                    playCount = obj.optLong("playCount", 0L),
                    source = obj.optString("source").takeIf { it.isNotBlank() },
                    streamUrl = obj.optString("streamUrl").takeIf { it.isNotBlank() },
                    backendTrackId = if (obj.has("backendTrackId")) obj.optInt("backendTrackId") else null,
                    isCached = obj.optBoolean("isCached", false),
                    codec = obj.optString("codec").takeIf { it.isNotBlank() },
                    mbid = obj.optString("mbid").takeIf { it.isNotBlank() },
                    isrc = obj.optString("isrc").takeIf { it.isNotBlank() },
                    providerTrackId = obj.optString("providerTrackId").takeIf { it.isNotBlank() },
                )
            )
        }
        return list
    }

    private fun encodeArtists(artists: List<HomeArtist>): JSONArray {
        val array = JSONArray()
        artists.forEach { artist ->
            val obj = JSONObject().apply {
                put("id", artist.id)
                put("name", artist.name)
                put("playCount", artist.playCount)
                artist.imageUrl?.let { put("imageUrl", it) }
            }
            array.put(obj)
        }
        return array
    }

    private fun decodeArtists(array: JSONArray?): List<HomeArtist> {
        if (array == null) return emptyList()
        val list = ArrayList<HomeArtist>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
            list.add(
                HomeArtist(
                    id = id,
                    name = name,
                    playCount = obj.optLong("playCount", 0L),
                    imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() },
                )
            )
        }
        return list
    }

    private fun encodeAlbums(albums: List<HomeAlbum>): JSONArray {
        val array = JSONArray()
        albums.forEach { album ->
            val obj = JSONObject().apply {
                put("id", album.id)
                put("title", album.title)
                put("artist", album.artist)
                album.artworkUrl?.let { put("artworkUrl", it) }
                put("playCount", album.playCount)
            }
            array.put(obj)
        }
        return array
    }

    private fun decodeAlbums(array: JSONArray?): List<HomeAlbum> {
        if (array == null) return emptyList()
        val list = ArrayList<HomeAlbum>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: continue
            val artist = obj.optString("artist").takeIf { it.isNotBlank() } ?: continue
            list.add(
                HomeAlbum(
                    id = id,
                    title = title,
                    artist = artist,
                    artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                    playCount = obj.optLong("playCount", 0L),
                )
            )
        }
        return list
    }

    private companion object {
        const val KEY_FEED_CACHE = "home_feed_cache_v1"
    }
}
