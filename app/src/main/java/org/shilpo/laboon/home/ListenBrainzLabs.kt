package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


data class SimilarArtist(
    val mbid: String,
    val name: String,
    val score: Int,
)


object ListenBrainzLabs {

    const val LABS_BASE = "https://labs.api.listenbrainz.org"
    const val LISTENBRAINZ_BASE = "https://api.listenbrainz.org"


    const val MAX_SEEDS_PER_REQUEST = 25

    const val DEFAULT_ALGORITHM =
        "session_based_days_9000_session_300_contribution_5_threshold_15_limit_50_skip_30"

    fun similarRecordingsUrl(
        seedMbids: List<String>,
        algorithm: String = DEFAULT_ALGORITHM,
    ): String = seedUrl("/similar-recordings/json", "recording_mbids", seedMbids, algorithm)

    fun similarArtistsUrl(
        seedMbids: List<String>,
        algorithm: String = DEFAULT_ALGORITHM,
    ): String = seedUrl("/similar-artists/json", "artist_mbids", seedMbids, algorithm)

    fun tagSimilarityUrl(tag: String): String =
        "$LABS_BASE/tag-similarity/json?tag=${tag.urlEncoded()}"


    fun cfRecommendationsUrl(
        username: String,
        count: Int = 50,
        offset: Int = 0,
    ): String = "$LISTENBRAINZ_BASE/1/cf/recommendation/user/${username.urlEncoded()}/recording" +
            "?count=${count.coerceIn(1, 1000)}" +
            (if (offset > 0) "&offset=$offset" else "")

    fun listenBrainzUrl(path: String): String = "$LISTENBRAINZ_BASE$path"


    fun chunkSeeds(
        mbids: List<String>,
        size: Int = MAX_SEEDS_PER_REQUEST,
    ): List<List<String>> {
        if (mbids.isEmpty()) return emptyList()
        val step = size.coerceAtLeast(1)
        return mbids.distinct().filter { it.isNotBlank() }.chunked(step)
    }

    // Seeds are repeated query params; a comma joined list is rejected with a 400.
    private fun seedUrl(
        path: String,
        param: String,
        seedMbids: List<String>,
        algorithm: String,
    ): String = buildString {
        append(LABS_BASE).append(path)
        append("?algorithm=").append(algorithm.urlEncoded())

        seedMbids.forEach { append('&').append(param).append('=').append(it.urlEncoded()) }
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    fun parseSimilarRecordings(array: JSONArray): List<HomeTrack> {
        val result = mutableListOf<HomeTrack>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val mbid = obj.stringOrNull("recording_mbid") ?: continue
            val title = obj.stringOrNull("recording_name") ?: continue
            val artist = obj.stringOrNull("artist_credit_name") ?: continue
            result.add(
                HomeTrack(
                    id = "lbsr_$mbid",
                    title = title,
                    artist = artist,
                    album = obj.stringOrNull("release_name"),
                    artworkUrl = null,
                    source = "ListenBrainz",
                    mbid = mbid,
                )
            )
        }
        return result
    }

    fun parseSimilarArtists(array: JSONArray): List<SimilarArtist> {
        val result = mutableListOf<SimilarArtist>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val mbid = obj.stringOrNull("artist_mbid") ?: continue
            val name = obj.stringOrNull("name") ?: continue
            result.add(SimilarArtist(mbid = mbid, name = name, score = obj.optInt("score", 0)))
        }
        return result
    }


    fun parseCfRecordingMbids(json: JSONObject): List<String> {
        val mbids = json.objOrNull("payload")?.arrOrNull("mbids") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until mbids.length()) {
            mbids.optJSONObject(i)?.stringOrNull("recording_mbid")?.let(result::add)
        }
        return result
    }

    fun emptyArray(): JSONArray = JSONArray()


}
