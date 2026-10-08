package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class LastFmTrackCandidate(
    val track: HomeTrack,
    val similarity: Double? = null,
)

data class LastFmArtistCandidate(
    val name: String,
    val mbid: String? = null,
    val similarity: Double? = null,
)

/** URL and response helpers for the Last.fm methods used by home and queue discovery. */
object LastFmApi {
    private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"

    fun similarTracksUrl(seed: HomeTrack, apiKey: String, limit: Int = 20): String =
        url("track.getsimilar", apiKey, limit).let { base ->
            if (!seed.mbid.isNullOrBlank()) "$base&mbid=${seed.mbid.encoded()}"
            else "$base&track=${seed.title.encoded()}&artist=${seed.artist.encoded()}"
        }

    fun similarArtistsUrl(
        artistName: String,
        artistMbid: String?,
        apiKey: String,
        limit: Int = 12,
    ): String = url("artist.getsimilar", apiKey, limit).let { base ->
        if (!artistMbid.isNullOrBlank()) "$base&mbid=${artistMbid.encoded()}"
        else "$base&artist=${artistName.encoded()}"
    }

    fun topTracksUrl(
        artistName: String,
        artistMbid: String?,
        apiKey: String,
        limit: Int = 8,
    ): String = url("artist.gettoptracks", apiKey, limit).let { base ->
        if (!artistMbid.isNullOrBlank()) "$base&mbid=${artistMbid.encoded()}"
        else "$base&artist=${artistName.encoded()}"
    }

    fun lovedTracksUrl(username: String, apiKey: String, limit: Int = 100): String =
        "$BASE_URL?method=user.getlovedtracks&user=${username.encoded()}" +
                "&api_key=${apiKey.encoded()}&format=json&limit=${limit.coerceIn(1, 200)}"

    private fun url(method: String, apiKey: String, limit: Int): String =
        "$BASE_URL?method=$method&api_key=${apiKey.encoded()}&format=json&limit=${
            limit.coerceIn(
                1,
                200
            )
        }"

    fun parseSimilarTracks(body: String): List<LastFmTrackCandidate> =
        parseTrackArray(JSONObject(body).objOrNull("similartracks")?.opt("track").asArray())

    fun parseTopTracks(body: String): List<LastFmTrackCandidate> =
        parseTrackArray(JSONObject(body).objOrNull("toptracks")?.opt("track").asArray())

    fun parseTrackList(
        body: String,
        containerKey: String,
        listKey: String = "track",
    ): List<LastFmTrackCandidate> =
        parseTrackArray(JSONObject(body).objOrNull(containerKey)?.opt(listKey).asArray())

    fun parseLovedTracks(body: String): List<HomeTrack> =
        parseTrackArray(JSONObject(body).objOrNull("lovedtracks")?.opt("track").asArray())
            .map(LastFmTrackCandidate::track)

    fun parseSimilarArtists(body: String): List<LastFmArtistCandidate> {
        val artists =
            JSONObject(body).objOrNull("similarartists")?.arrOrNull("artist") ?: JSONArray()
        return buildList {
            for (index in 0 until artists.length()) {
                val item = artists.optJSONObject(index) ?: continue
                val name = item.stringOrNull("name")?.takeIf(String::isNotBlank) ?: continue
                add(
                    LastFmArtistCandidate(
                        name = name,
                        mbid = item.stringOrNull("mbid")?.takeIf(String::isNotBlank),
                        similarity = item.optString("match").toDoubleOrNull(),
                    ),
                )
            }
        }
    }

    private fun parseTrackArray(array: JSONArray): List<LastFmTrackCandidate> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val title = item.stringOrNull("name")?.takeIf(String::isNotBlank) ?: continue
            val artistObject = item.objOrNull("artist")
            val artist = artistObject?.stringOrNull("name")
                ?: item.stringOrNull("artist")
                ?: continue
            val mbid = item.stringOrNull("mbid")?.takeIf(String::isNotBlank)
            add(
                LastFmTrackCandidate(
                    track = HomeTrack(
                        id = mbid?.let { "lfm_$it" }
                            ?: "lfm_${title.hashCode()}_${artist.hashCode()}",
                        title = title,
                        artist = artist,
                        source = "Last.fm",
                        mbid = mbid,
                        artistMbid = artistObject?.stringOrNull("mbid")
                            ?.takeIf(String::isNotBlank),
                    ),
                    similarity = item.optString("match").toDoubleOrNull(),
                ),
            )
        }
    }

    private fun Any?.asArray(): JSONArray = when (this) {
        is JSONArray -> this
        is JSONObject -> JSONArray().put(this)
        else -> JSONArray()
    }

    private fun String.encoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}
