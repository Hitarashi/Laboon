package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.net.stringOrNull

data class SavedPlaylist(
    val id: String,
    val name: String,
    val isFavorites: Boolean,
    val sourceUrl: String? = null,
    val trackCount: Int = 0,
    val previewTrackIds: List<String> = emptyList(),
    val previewArtworkByTrackId: Map<String, String> = emptyMap(),
)

val SavedPlaylist.previewArtworkUrls: List<String>
    get() = previewTrackIds.mapNotNull(previewArtworkByTrackId::get)

data class SavedPlaylistDetail(
    val playlist: SavedPlaylist,
    val trackIds: List<String>,
)

/** JSON contract for the app-owned playlist endpoints. */
internal object UserPlaylistApi {
    fun parsePlaylists(array: JSONArray): List<SavedPlaylist> = buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let(::parsePlaylist)?.let(::add)
        }
    }

    fun parseDetail(root: JSONObject): SavedPlaylistDetail? {
        val playlist = root.optJSONObject("playlist")?.let(::parsePlaylist) ?: return null
        val rows = root.optJSONArray("track_ids") ?: JSONArray()
        val trackIds = buildList {
            for (index in 0 until rows.length()) {
                rows.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
        return SavedPlaylistDetail(playlist, trackIds)
    }

    fun importBody(name: String, sourceUrl: String, trackIds: List<String>): String =
        JSONObject().apply {
            put("name", name)
            put("source_url", sourceUrl)
            put("track_ids", trackIds.toJsonArray())
        }.toString()

    fun createBody(name: String): String = JSONObject().put("name", name).toString()

    fun renameBody(name: String): String = JSONObject().put("name", name).toString()

    fun replaceTracksBody(trackIds: List<String>): String =
        JSONObject().put("track_ids", trackIds.toJsonArray()).toString()

    fun parsePlaylist(root: JSONObject): SavedPlaylist? {
        val id = root.stringOrNull("id")?.takeIf(String::isNotBlank) ?: return null
        val name = root.stringOrNull("name")?.takeIf(String::isNotBlank) ?: return null
        return SavedPlaylist(
            id = id,
            name = name,
            isFavorites = root.optBoolean("is_favorites"),
            sourceUrl = root.stringOrNull("source_url")?.takeIf(String::isNotBlank),
            trackCount = root.optInt("track_count", 0).coerceAtLeast(0),
            previewTrackIds = root.optJSONArray("preview_track_ids")?.let { rows ->
                buildList {
                    for (index in 0 until rows.length()) {
                        rows.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }.orEmpty(),
        )
    }

    private fun List<String>.toJsonArray(): JSONArray = JSONArray().also { array ->
        forEach { trackId -> array.put(trackId) }
    }
}
