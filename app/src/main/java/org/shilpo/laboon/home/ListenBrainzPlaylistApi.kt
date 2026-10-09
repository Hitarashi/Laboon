package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ListenBrainzPlaylist(
    val id: String,
    val title: String,
    val creator: String? = null,
    val isPublic: Boolean? = null,
    val trackCount: Int? = null,
)

data class ListenBrainzPlaylistTrack(
    val recordingMbid: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
)

/** URL and JSPF parsing helpers for ListenBrainz's playlist endpoints. */
object ListenBrainzPlaylistApi {
    private const val BASE_URL = "https://api.listenbrainz.org/1"
    private const val PLAYLIST_EXTENSION = "https://musicbrainz.org/doc/jspf#playlist"

    fun playlistsUrl(username: String, count: Int = 100, offset: Int = 0): String =
        "$BASE_URL/user/${username.urlEncoded()}/playlists" +
                "?count=${count.coerceIn(1, 100)}&offset=${offset.coerceAtLeast(0)}"

    fun playlistUrl(playlistId: String, fetchMetadata: Boolean = false): String =
        "$BASE_URL/playlist/${playlistId.urlEncoded()}?fetch_metadata=$fetchMetadata"

    fun createPlaylistUrl(): String = "$BASE_URL/playlist/create"

    fun addItemsUrl(playlistId: String): String =
        "$BASE_URL/playlist/${playlistId.urlEncoded()}/item/add"

    fun createPlaylistBody(title: String, creator: String): String = JSONObject()
        .put(
            "playlist",
            JSONObject()
                .put("title", title.trim())
                .put("creator", creator.trim())
                .put("track", JSONArray()),
        )
        .toString()

    fun addTracksBody(recordingMbids: List<String>): String {
        val tracks = JSONArray()
        recordingMbids.map(String::trim).filter(String::isNotEmpty).forEach { mbid ->
            tracks.put(
                JSONObject().put(
                    "identifier",
                    JSONArray().put("https://musicbrainz.org/recording/$mbid"),
                ),
            )
        }
        return JSONObject().put("playlist", JSONObject().put("track", tracks)).toString()
    }

    fun parsePlaylists(root: JSONObject): List<ListenBrainzPlaylist> {
        val playlists = root.arrOrNull("playlists") ?: return emptyList()
        return buildList {
            for (index in 0 until playlists.length()) {
                val wrapper = playlists.optJSONObject(index) ?: continue
                val playlist = wrapper.objOrNull("playlist") ?: wrapper
                val id = playlist.stringOrNull("playlist_mbid")
                    ?: playlist.stringOrNull("identifier")?.let(::playlistIdFromIdentifier)
                    ?: continue
                val title = playlist.stringOrNull("title")?.trim()?.takeIf(String::isNotEmpty)
                    ?: "Untitled playlist"
                val extension = playlist.objOrNull("extension")?.objOrNull(PLAYLIST_EXTENSION)
                val tracks = playlist.arrOrNull("track")
                val trackCount = playlist.optInt("track_count", -1)
                    .takeIf { it >= 0 }
                    ?: tracks?.length()?.takeIf { it > 0 }
                add(
                    ListenBrainzPlaylist(
                        id = id,
                        title = title,
                        creator = playlist.stringOrNull("creator"),
                        isPublic = extension?.let { if (it.has("public")) it.optBoolean("public") else null },
                        trackCount = trackCount,
                    ),
                )
            }
        }
    }

    fun parseCreatedPlaylist(root: JSONObject): ListenBrainzPlaylist? {
        val playlist = root.objOrNull("playlist") ?: root
        val id = root.stringOrNull("playlist_mbid")
            ?: playlist.stringOrNull("playlist_mbid")
            ?: playlist.stringOrNull("identifier")?.let(::playlistIdFromIdentifier)
            ?: return null
        return ListenBrainzPlaylist(
            id = id,
            title = playlist.stringOrNull("title")?.trim()?.takeIf(String::isNotEmpty)
                ?: root.stringOrNull("title")?.trim()?.takeIf(String::isNotEmpty)
                ?: "Untitled playlist",
            creator = playlist.stringOrNull("creator"),
            isPublic = playlist.objOrNull("extension")?.objOrNull(PLAYLIST_EXTENSION)
                ?.let { if (it.has("public")) it.optBoolean("public") else null },
            trackCount = playlist.optInt("track_count", -1).takeIf { it >= 0 }
                ?: playlist.arrOrNull("track")?.length(),
        )
    }

    fun parsePlaylistTracks(root: JSONObject): List<ListenBrainzPlaylistTrack> {
        val playlist = root.objOrNull("playlist") ?: root
        val tracks = playlist.arrOrNull("track") ?: return emptyList()
        return buildList {
            for (index in 0 until tracks.length()) {
                val track = tracks.optJSONObject(index) ?: continue
                val recordingMbid = track.stringOrNull("recording_mbid")
                    ?: track.arrOrNull("identifier")?.let(::recordingMbidFromIdentifiers)
                    ?: track.stringOrNull("identifier")?.let(::recordingMbidFromIdentifier)
                    ?: continue
                add(
                    ListenBrainzPlaylistTrack(
                        recordingMbid = recordingMbid,
                        title = track.stringOrNull("title"),
                        artist = track.stringOrNull("creator"),
                        album = track.stringOrNull("album"),
                        durationMs = track.optLong("duration", -1L).takeIf { it > 0L },
                    ),
                )
            }
        }
    }

    fun parseRecordingMbids(root: JSONObject): List<String> =
        parsePlaylistTracks(root).map(ListenBrainzPlaylistTrack::recordingMbid)

    private fun recordingMbidFromIdentifiers(identifiers: JSONArray): String? {
        for (index in 0 until identifiers.length()) {
            recordingMbidFromIdentifier(identifiers.optString(index))?.let { return it }
        }
        return null
    }

    private fun recordingMbidFromIdentifier(identifier: String): String? {
        val value = identifier.trim().trimEnd('/')
        if (value.isEmpty()) return null
        val marker = "/recording/"
        if (value.contains(marker, ignoreCase = true)) {
            return value.substringAfterLast(marker).substringBefore('?').takeIf(String::isNotEmpty)
        }
        // Some JSPF producers store the recording MBID directly instead of its canonical URI.
        return value.takeIf { '/' !in it && '?' !in it }
    }

    private fun playlistIdFromIdentifier(identifier: String): String? =
        identifier.trim().trimEnd('/').substringAfterLast('/').substringBefore('?')
            .takeIf(String::isNotEmpty)

    private fun String.urlEncoded(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}
