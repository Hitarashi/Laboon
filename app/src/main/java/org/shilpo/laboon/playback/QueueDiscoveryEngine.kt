package org.shilpo.laboon.playback

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.auth.ListenBrainzCredentials
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.ListenBrainzLabs
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.net.HttpError
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.asJsonArrayOrNull
import org.shilpo.laboon.net.fold
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchRepositoryImpl
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class QueueDiscoveryEngine(
    private val sessionStore: SessionStore,
    private val artworkResolver: LyricspornClient = LyricspornClient,
    private val searchRepository: SearchRepository = SearchRepositoryImpl(sessionStore),
) {

    suspend fun discoverNextTracks(
        seed: HomeTrack,
        excludeKeys: Set<String> = emptySet(),
        limit: Int = 10,
        onPlayableBatch: suspend (List<HomeTrack>) -> Unit = {},
    ): List<HomeTrack> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()
        val session = sessionStore.getSession()
        val serverUrl = session?.serverUrl?.trim().orEmpty()
        val token = session?.token?.trim().orEmpty()
        if (serverUrl.isEmpty() || token.isEmpty()) {
            logInfo("Discovery skipped: no connected server.")
            return@withContext emptyList()
        }

        val lastFm = sessionStore.getLastFmCredentials()
        val listenBrainz = sessionStore.getListenBrainzCredentials()
        val seedMbid = seed.mbid?.trim()?.takeIf { it.isNotEmpty() }

        if (seedMbid == null && lastFm?.apiKey.isNullOrBlank() && listenBrainz?.username.isNullOrBlank()) {
            logInfo("Discovery skipped: no recording id, Last.fm key, or ListenBrainz account.")
            return@withContext emptyList()
        }

        val selected = mutableListOf<HomeTrack>()
        val excluded = excludeKeys + TrackIdentity.keyOf(seed)

        suspend fun resolveStage(
            trackSpecific: List<HomeTrack> = emptyList(),
            artistSpecific: List<HomeTrack> = emptyList(),
            personalized: List<HomeTrack> = emptyList(),
        ) {
            val remaining = limit - selected.size
            if (remaining <= 0) return
            val stageExcluded = excluded + selected.map(TrackIdentity::keyOf)
            val candidates = prioritizeDiscoveryTracks(
                trackSpecific = trackSpecific,
                artistSpecific = artistSpecific,
                personalized = personalized,
                excludedKeys = stageExcluded,
                limit = minOf(MAX_CANDIDATES_PER_PHASE, remaining * CANDIDATE_MULTIPLIER),
            )
            val playable = resolveCandidates(
                candidates,
                stageExcluded,
                remaining,
                session?.lyricspornApiUrl,
            )
            if (playable.isNotEmpty()) {
                selected.addAll(playable)
                onPlayableBatch(playable)
            }
        }

        val trackSpecificBranches = coroutineScope {
            listOf(
                async {
                    guard("labs-similar-recordings") {
                        labsSimilarRecordings(listOfNotNull(seedMbid))
                    }
                },
                async { guard("lastfm-similar") { lastFmSimilar(seed, lastFm) } },
            ).awaitAll()
        }
        resolveStage(trackSpecific = interleave(trackSpecificBranches))

        if (selected.size < limit && seedMbid != null && !lastFm?.apiKey.isNullOrBlank()) {
            val artistCandidates = guard("labs-similar-artists") {
                labsSimilarArtistTracks(listOf(seedMbid), lastFm)
            }
            resolveStage(artistSpecific = artistCandidates)
        }

        if (selected.size < limit && !listenBrainz?.username.isNullOrBlank()) {
            val personalCandidates = guard("listenbrainz-cf") {
                cfRecommendations(listenBrainz)
            }
            resolveStage(personalized = personalCandidates)
        }

        if (selected.isEmpty()) {
            logInfo("Discovery found no playable candidates for '${seed.title}'.")
        }
        selected
    }

    private suspend fun resolveCandidates(
        candidates: List<HomeTrack>,
        excluded: Set<String>,
        limit: Int,
        lyricspornApiUrl: String?,
    ): List<HomeTrack> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()
        val playable = mutableListOf<HomeTrack>()
        for (batch in candidates.chunked(RESOLUTION_BATCH_SIZE)) {
            if (playable.size >= limit) break
            val resolved = searchRepository.resolvePlaybackBatch(batch)
            val withArtwork = coroutineScope {
                resolved.map { track ->
                    async {
                        if (!track.artworkUrl.isNullOrBlank()) {
                            track
                        } else {
                            track.copy(
                                artworkUrl = artworkResolver.resolveTrackArtwork(
                                    apiBaseUrl = lyricspornApiUrl,
                                    title = track.title,
                                    artist = track.artist,
                                    album = track.album,
                                ) ?: track.artworkUrl,
                            )
                        }
                    }
                }.awaitAll()
            }
            for (track in withArtwork) {
                if (track.streamUrl.isNullOrBlank()) continue
                val key = TrackIdentity.keyOf(track)
                if (key in excluded || playable.any { TrackIdentity.keyOf(it) == key }) continue
                playable.add(track)
                if (playable.size >= limit) break
            }
        }
        return playable
    }

    private suspend fun labsSimilarRecordings(seedMbids: List<String>): List<HomeTrack> {
        if (seedMbids.isEmpty()) return emptyList()
        val out = mutableListOf<HomeTrack>()
        for (chunk in ListenBrainzLabs.chunkSeeds(seedMbids)) {
            val body = getText(ListenBrainzLabs.similarRecordingsUrl(chunk))
            if (body == null) continue
            try {
                out.addAll(ListenBrainzLabs.parseSimilarRecordings(JSONArray(body)))
            } catch (e: Exception) {
                logFailure("labs-similar-recordings", e)
            }
        }
        return out
    }

    private suspend fun labsSimilarArtistTracks(
        seedMbids: List<String>,
        lastFm: LastFmCredentials?,
    ): List<HomeTrack> {
        val apiKey = lastFm?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        if (seedMbids.isEmpty()) return emptyList()

        val body = getText(ListenBrainzLabs.similarArtistsUrl(seedMbids))
        if (body == null) return emptyList()
        val artists = try {
            ListenBrainzLabs.parseSimilarArtists(JSONArray(body))
        } catch (e: Exception) {
            logFailure("labs-similar-artists", e)
            return emptyList()
        }

        val out = mutableListOf<HomeTrack>()
        for (artist in artists.sortedByDescending { it.score }.take(3)) {
            val url = "https://ws.audioscrobbler.com/2.0/?method=artist.gettoptracks" +
                    "&mbid=${artist.mbid.urlEncoded()}&api_key=$apiKey&format=json&limit=3"
            parseLastFmTracks(getText(url), source = "Last.fm")?.let(out::addAll)
        }
        return out
    }

    private suspend fun lastFmSimilar(seed: HomeTrack, creds: LastFmCredentials?): List<HomeTrack> {
        val apiKey = creds?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        val mbid = seed.mbid?.trim()?.ifEmpty { null }
        val url = if (mbid != null) {
            "https://ws.audioscrobbler.com/2.0/?method=track.getsimilar" +
                    "&mbid=${mbid.urlEncoded()}&api_key=$apiKey&format=json&limit=8"
        } else {
            "https://ws.audioscrobbler.com/2.0/?method=track.getsimilar" +
                    "&track=${seed.title.urlEncoded()}&artist=${seed.artist.urlEncoded()}" +
                    "&api_key=$apiKey&format=json&limit=8"
        }
        return parseLastFmTracks(getText(url), source = "Last.fm").orEmpty()
    }

    private suspend fun cfRecommendations(creds: ListenBrainzCredentials?): List<HomeTrack> {
        val username = creds?.username?.trim()?.ifEmpty { null } ?: return emptyList()
        val body = getText(
            ListenBrainzLabs.cfRecommendationsUrl(username, count = 25),
            listenBrainzToken(),
        ) ?: return emptyList()
        val mbids = try {
            ListenBrainzLabs.parseCfRecordingMbids(JSONObject(body))
        } catch (e: Exception) {
            logFailure("listenbrainz-cf", e)
            return emptyList()
        }
        if (mbids.isEmpty()) return emptyList()
        return expandRecordingMbids(mbids.take(20), creds)
    }

    private suspend fun expandRecordingMbids(
        mbids: List<String>,
        creds: ListenBrainzCredentials?,
    ): List<HomeTrack> {
        val token = creds?.token?.trim()?.orEmpty()
        val out = mutableListOf<HomeTrack>()
        for (chunk in ListenBrainzLabs.chunkSeeds(mbids)) {
            val query = chunk.joinToString("&") { "recording_mbids=${it.urlEncoded()}" }
            val url = ListenBrainzLabs.listenBrainzUrl(
                "/1/metadata/recording/?$query&inc=artist+release"
            )
            val body = getText(url, listenBrainzToken()) ?: continue
            try {

                val root = JSONObject(body)
                for (mbid in chunk) {
                    val obj = root.optJSONObject(mbid) ?: continue
                    val title = obj.stringOrNull("title")
                        ?: obj.stringOrNull("recording_name")
                        ?: continue
                    val artist = obj.stringOrNull("artist_credit_name")
                        ?: firstArtistCreditName(obj)
                        ?: continue
                    val release = obj.objOrNull("release")
                    out.add(
                        HomeTrack(
                            id = "lbmd_$mbid",
                            title = title,
                            artist = artist,
                            album = release?.stringOrNull("title")
                                ?: release?.stringOrNull("release_name"),
                            artworkUrl = null,
                            source = "ListenBrainz",
                            mbid = mbid,
                        )
                    )
                }
            } catch (e: Exception) {
                logFailure("metadata-recording", e)
            }
        }
        return out
    }

    private fun firstArtistCreditName(obj: JSONObject): String? {
        val credits = obj.arrOrNull("artist_credit") ?: return null
        for (i in 0 until credits.length()) {
            val name = credits.optJSONObject(i)?.objOrNull("artist")?.stringOrNull("name")
            if (!name.isNullOrBlank()) return name
        }
        return null
    }

    private fun parseLastFmTracks(body: String?, source: String): List<HomeTrack>? {
        if (body == null) return null
        return try {
            val array =
                JSONObject(body).objOrNull("similartracks")?.get("track").asJsonArrayOrNull()
                    ?: JSONObject(body).objOrNull("toptracks")?.arrOrNull("track")
                    ?: JSONArray()
            val out = mutableListOf<HomeTrack>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.stringOrNull("name") ?: continue
                val artistObj = obj.objOrNull("artist")
                val artist =
                    artistObj?.stringOrNull("name") ?: obj.stringOrNull("artist") ?: continue
                val mbid = obj.stringOrNull("mbid")
                out.add(
                    HomeTrack(
                        id = if (mbid != null) "lfm_$mbid" else "lfm_${title.hashCode()}_${artist.hashCode()}",
                        title = title,
                        artist = artist,
                        source = source,
                        mbid = mbid,
                    )
                )
            }
            out
        } catch (e: Exception) {
            logFailure("lastfm-parse", e)
            null
        }
    }

    private fun interleave(branches: List<List<HomeTrack>>): List<HomeTrack> {
        val out = mutableListOf<HomeTrack>()
        val deepest = branches.maxOfOrNull { it.size } ?: 0
        for (i in 0 until deepest) {
            branches.forEach { branch -> branch.getOrNull(i)?.let(out::add) }
        }
        return out
    }

    private suspend fun guard(tag: String, block: suspend () -> List<HomeTrack>): List<HomeTrack> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure(tag, e)
            emptyList()
        }

    private suspend fun getText(url: String, token: String = ""): String? {
        val headers = if (token.isBlank()) {
            emptyMap()
        } else {
            mapOf("Authorization" to "Token $token")
        }
        return http.get(url, headers).fold(
            onSuccess = { it },
            onFailure = { error ->
                logHttpFailure(url, error)
                null
            },
        )
    }

    private fun listenBrainzToken(): String =
        sessionStore.getListenBrainzCredentials()?.token?.trim().orEmpty()

    private fun String.urlEncoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun describe(error: HttpError): String = when {
        error.kind == HttpErrorKind.STATUS &&
                error.message.contains("rate limited", ignoreCase = true) -> "rate limited"

        error.statusCode == 401 -> "unauthorized"
        error.statusCode == 403 -> "forbidden"
        error.kind == HttpErrorKind.TIMEOUT -> "timed out"
        error.kind == HttpErrorKind.NETWORK -> "network unavailable"
        error.kind == HttpErrorKind.MALFORMED -> "malformed body"
        else -> "error ${error.statusCode ?: "?"}"
    }

    private fun logHttpFailure(url: String, error: HttpError) {
        logInfo("Discovery request failed (${describe(error)}): ${url.take(160)} ${error.message}")
    }

    private fun logFailure(tag: String, e: Exception) {
        logInfo("Discovery branch '$tag' failed: ${e.javaClass.simpleName}: ${e.message}")
    }

    private fun logInfo(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private companion object {
        private val http = HttpJsonClient(connectTimeoutMs = 6_000, readTimeoutMs = 6_000)
        private const val TAG = "Discovery"
        private const val RESOLUTION_BATCH_SIZE = 4
        private const val CANDIDATE_MULTIPLIER = 3
        private const val MAX_CANDIDATES_PER_PHASE = 18
    }
}

internal fun prioritizeDiscoveryTracks(
    trackSpecific: List<HomeTrack>,
    artistSpecific: List<HomeTrack>,
    personalized: List<HomeTrack>,
    excludedKeys: Set<String> = emptySet(),
    limit: Int = 6,
): List<HomeTrack> {
    if (limit <= 0) return emptyList()
    val seen = excludedKeys.toHashSet()
    val prioritized = ArrayList<HomeTrack>(limit)
    for (source in listOf(trackSpecific, artistSpecific, personalized)) {
        for (track in source) {
            if (prioritized.size >= limit) return prioritized
            if (track.title.isBlank() || track.artist.isBlank()) continue
            if (seen.add(TrackIdentity.keyOf(track))) prioritized.add(track)
        }
    }
    return prioritized
}
