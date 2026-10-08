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

data class ListenBrainzSimilarRecording(
    val track: HomeTrack,
    val similarity: Double? = null,
)

data class ListenBrainzRecordingMetadata(
    val recordingMbid: String,
    val title: String? = null,
    val artistName: String? = null,
    val artistMbids: List<String> = emptyList(),
    val releaseName: String? = null,
    val durationMs: Long? = null,
)

data class ListenBrainzCfRecommendation(
    val recordingMbid: String,
    val score: Double? = null,
)

data class ListenBrainzRadioRecording(
    val recordingMbid: String,
    val artistMbid: String?,
    val artistName: String?,
    val listenCount: Long? = null,
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

    fun artistRadioUrl(
        artistMbid: String,
        mode: String = "medium",
        maxSimilarArtists: Int = 8,
        maxRecordingsPerArtist: Int = 8,
    ): String = "$LISTENBRAINZ_BASE/1/lb-radio/artist/${artistMbid.urlEncoded()}" +
            "?mode=${mode.urlEncoded()}" +
            "&max_similar_artists=${maxSimilarArtists.coerceIn(1, 50)}" +
            "&max_recordings_per_artist=${maxRecordingsPerArtist.coerceIn(1, 50)}"

    fun recordingMbidLookupUrl(recordingMbids: List<String>): String = buildString {
        append(LABS_BASE).append("/recording-mbid-lookup/json")
        recordingMbids.distinct().filter(String::isNotBlank).forEach { mbid ->
            append(if (contains('?')) '&' else '?')
            append("recording_mbid=").append(mbid.urlEncoded())
        }
    }

    fun feedbackUrl(username: String, count: Int = 200, offset: Int = 0): String =
        "$LISTENBRAINZ_BASE/1/feedback/user/${username.urlEncoded()}/get-feedback" +
                "?count=${count.coerceIn(1, 1000)}" +
                (if (offset > 0) "&offset=$offset" else "")

    fun metadataRecordingUrl(recordingMbids: List<String>): String =
        "$LISTENBRAINZ_BASE/1/metadata/recording/?recording_mbids=" +
                recordingMbids.distinct().filter(String::isNotBlank)
                    .joinToString(",") { it.urlEncoded() } + "&inc=artist+release"

    fun metadataLookupUrl(track: HomeTrack): String = buildString {
        append("$LISTENBRAINZ_BASE/1/metadata/lookup/?recording_name=")
            .append(track.title.urlEncoded())
            .append("&artist_name=").append(track.artist.urlEncoded())
        track.album?.takeIf(String::isNotBlank)?.let {
            append("&release_name=").append(it.urlEncoded())
        }
    }

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
        return parseSimilarRecordingCandidates(array).map(ListenBrainzSimilarRecording::track)
    }

    fun parseSimilarRecordingCandidates(array: JSONArray): List<ListenBrainzSimilarRecording> {
        val result = mutableListOf<ListenBrainzSimilarRecording>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val mbid = obj.stringOrNull("recording_mbid") ?: continue
            val title = obj.stringOrNull("recording_name") ?: continue
            val artist = obj.stringOrNull("artist_credit_name") ?: continue
            val artistMbid = obj.stringOrNull("artist_mbid")
                ?: obj.optJSONArray("artist_mbids")?.optString(0)?.takeIf(String::isNotBlank)
                ?: obj.optJSONArray("artist_credit_mbids")?.optString(0)?.takeIf(String::isNotBlank)
            result.add(
                ListenBrainzSimilarRecording(
                    track = HomeTrack(
                        id = "lbsr_$mbid",
                        title = title,
                        artist = artist,
                        album = obj.stringOrNull("release_name"),
                        artworkUrl = null,
                        source = "ListenBrainz",
                        mbid = mbid,
                        artistMbid = artistMbid,
                    ),
                    similarity = obj.optDouble("score", Double.NaN).takeIf { !it.isNaN() },
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
        return parseCfRecommendations(json).map(ListenBrainzCfRecommendation::recordingMbid)
    }

    fun parseCfRecommendations(json: JSONObject): List<ListenBrainzCfRecommendation> {
        val mbids = json.objOrNull("payload")?.arrOrNull("mbids") ?: return emptyList()
        val result = mutableListOf<ListenBrainzCfRecommendation>()
        for (i in 0 until mbids.length()) {
            val item = mbids.optJSONObject(i) ?: continue
            val recordingMbid = item.stringOrNull("recording_mbid") ?: continue
            result += ListenBrainzCfRecommendation(
                recordingMbid = recordingMbid,
                score = item.optDouble("score").takeIf { !it.isNaN() },
            )
        }
        return result
    }

    fun parseArtistRadio(root: JSONObject): List<ListenBrainzRadioRecording> = buildList {
        val artistMbids = root.keys()
        while (artistMbids.hasNext()) {
            val artistMbid = artistMbids.next()
            val recordings = root.optJSONArray(artistMbid) ?: continue
            for (index in 0 until recordings.length()) {
                val item = recordings.optJSONObject(index) ?: continue
                val recordingMbid = item.stringOrNull("recording_mbid") ?: continue
                add(
                    ListenBrainzRadioRecording(
                        recordingMbid = recordingMbid,
                        artistMbid = item.stringOrNull("similar_artist_mbid") ?: artistMbid,
                        artistName = item.stringOrNull("similar_artist_name"),
                        listenCount = item.optLong("total_listen_count").takeIf { it > 0L },
                    ),
                )
            }
        }
    }

    fun parseRecordingMetadata(
        root: JSONObject,
        requestedMbids: List<String>,
    ): Map<String, ListenBrainzRecordingMetadata> = buildMap {
        requestedMbids.distinct().forEach { requestedMbid ->
            val item = root.optJSONObject(requestedMbid) ?: return@forEach
            val artist = item.optJSONObject("artist")
            val artists = artist?.optJSONArray("artists")
            val artistMbids = buildList {
                if (artists != null) {
                    for (index in 0 until artists.length()) {
                        val mbid = artists.optJSONObject(index)?.stringOrNull("artist_mbid")
                        if (!mbid.isNullOrBlank()) add(mbid)
                    }
                }
            }
            val recording = item.optJSONObject("recording")
            val release = item.optJSONObject("release")
            put(
                requestedMbid,
                ListenBrainzRecordingMetadata(
                    recordingMbid = requestedMbid,
                    title = item.stringOrNull("recording_name")
                        ?: recording?.stringOrNull("name"),
                    artistName = item.stringOrNull("artist_credit_name")
                        ?: artist?.stringOrNull("name"),
                    artistMbids = artistMbids,
                    releaseName = item.stringOrNull("release_name")
                        ?: release?.stringOrNull("name"),
                    durationMs = recording?.optLong("length")?.takeIf { it > 0L },
                ),
            )
        }
    }

    fun parseMetadataLookup(root: JSONObject): ListenBrainzRecordingMetadata? {
        val recordingMbid = root.stringOrNull("recording_mbid")
            ?: root.objOrNull("recording")?.stringOrNull("mbid")
            ?: return null
        val artist = root.optJSONObject("artist")
        val artists = artist?.optJSONArray("artists") ?: root.optJSONArray("artists")
        val artistMbids = buildList {
            root.optJSONArray("artist_mbids")?.let { ids ->
                for (index in 0 until ids.length()) ids.optString(index)
                    .takeIf(String::isNotBlank)?.let(::add)
            }
            if (isEmpty() && artists != null) {
                for (index in 0 until artists.length()) {
                    val item = artists.optJSONObject(index) ?: continue
                    (item.stringOrNull("artist_mbid") ?: item.stringOrNull("mbid"))
                        ?.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
        val recording = root.optJSONObject("recording")
        val release = root.optJSONObject("release")
        return ListenBrainzRecordingMetadata(
            recordingMbid = recordingMbid,
            title = root.stringOrNull("recording_name") ?: recording?.stringOrNull("name"),
            artistName = root.stringOrNull("artist_credit_name") ?: artist?.stringOrNull("name"),
            artistMbids = artistMbids,
            releaseName = root.stringOrNull("release_name") ?: release?.stringOrNull("name"),
            durationMs = recording?.optLong("length")?.takeIf { it > 0L },
        )
    }

    fun parseRecordingMbidLookup(array: JSONArray): List<ListenBrainzRecordingMetadata> {
        val result = mutableListOf<ListenBrainzRecordingMetadata>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val recordingMbid = item.stringOrNull("canonical_recording_mbid")
                ?: item.stringOrNull("recording_mbid")
                ?: continue
            val artists = item.optJSONArray("artists")
            val artistMbids = buildList {
                val creditMbids = item.optJSONArray("artist_credit_mbids")
                if (creditMbids != null) {
                    for (artistIndex in 0 until creditMbids.length()) {
                        creditMbids.optString(artistIndex).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                if (isEmpty() && artists != null) {
                    for (artistIndex in 0 until artists.length()) {
                        artists.optJSONObject(artistIndex)?.stringOrNull("artist_mbid")
                            ?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }
            result += ListenBrainzRecordingMetadata(
                recordingMbid = recordingMbid,
                title = item.stringOrNull("recording_name"),
                artistName = item.stringOrNull("artist_credit_name"),
                artistMbids = artistMbids,
                releaseName = item.stringOrNull("release_name"),
                durationMs = item.optLong("length").takeIf { it > 0L },
            )
        }
        return result
    }

    fun parseFeedback(json: JSONObject): Map<String, String> {
        val feedback = json.optJSONArray("feedback") ?: return emptyMap()
        return buildMap {
            for (index in 0 until feedback.length()) {
                val item = feedback.optJSONObject(index) ?: continue
                val mbid = item.stringOrNull("recording_mbid") ?: continue
                val rating =
                    item.stringOrNull("rating")?.lowercase() ?: when (item.optInt("score")) {
                        1 -> "love"
                        -1 -> "hate"
                        else -> continue
                    }
                put(mbid, rating.lowercase())
            }
        }
    }

    fun emptyArray(): JSONArray = JSONArray()

}
