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
import org.shilpo.laboon.home.HomeFeedRepository
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.LastFmApi
import org.shilpo.laboon.home.ListenBrainzLabs
import org.shilpo.laboon.home.ListenBrainzRadioRecording
import org.shilpo.laboon.home.ListenBrainzRecordingMetadata
import org.shilpo.laboon.home.ListenBrainzRequestPolicy
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.home.UserTasteProfile
import org.shilpo.laboon.net.HttpError
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchRepositoryImpl
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class DiscoveryTier { RELATED_RECORDING, RELATED_ARTIST, PERSONAL }

data class DiscoveryCandidate(
    val track: HomeTrack,
    val recordingMbid: String? = track.mbid,
    val artistMbids: List<String> = listOfNotNull(track.artistMbid),
    val source: String,
    val tier: DiscoveryTier,
    val rank: Int,
    val similarity: Double? = null,
    val reason: String,
    val supportingSources: Set<String> = setOf(source.substringBefore(' ')),
)

data class QueueDiscoveryResult(
    val tracks: List<HomeTrack>,
    val successfulResponses: Int,
    val failedResponses: Int,
    val requestedCount: Int = 6,
) {
    val failed: Boolean get() = failedResponses > 0 && tracks.size < requestedCount
    val exhausted: Boolean get() = tracks.size < requestedCount && !failed
}

class QueueDiscoveryEngine(
    private val sessionStore: SessionStore,
    private val searchRepository: SearchRepository = SearchRepositoryImpl(sessionStore),
    private val tasteRepository: HomeFeedRepository = HomeFeedRepository(
        sessionStore = sessionStore,
        availabilityResolver = searchRepository,
    ),
) {

    suspend fun discoverNextTracks(
        seeds: List<HomeTrack>,
        excludeKeys: Set<String> = emptySet(),
        limit: Int = 6,
        onPlayableBatch: suspend (List<HomeTrack>) -> Unit = {},
    ): QueueDiscoveryResult = withContext(Dispatchers.IO) {
        if (limit <= 0 || seeds.isEmpty()) return@withContext QueueDiscoveryResult(
            emptyList(),
            0,
            0
        )
        val session = sessionStore.getSession()
        if (session == null || session.serverUrl.isNullOrBlank() || session.token.isNullOrBlank()) {
            logInfo("Discovery skipped: no connected server.")
            return@withContext QueueDiscoveryResult(emptyList(), 0, 0)
        }

        val lastFm = sessionStore.getLastFmCredentials()
        val listenBrainz = sessionStore.getListenBrainzCredentials()
        val accountKey = accountKey(lastFm, listenBrainz)
        DiscoveryCache.activateAccount(accountKey)
        val requestStats = RequestStats()
        val taste = try {
            tasteRepository.fetchTasteProfile()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            requestStats.failures.incrementAndGet()
            logFailure("taste-profile", error)
            UserTasteProfile()
        }
        val uniqueSeeds = seeds.distinctBy(::candidateKey).take(MAX_SEEDS)
        val selected = mutableListOf<HomeTrack>()
        val excluded = excludeKeys + uniqueSeeds.flatMap(::identityKeys)
        var lastArtist = uniqueSeeds.lastOrNull()?.artist.orEmpty()

        suspend fun fillTier(candidates: List<DiscoveryCandidate>, tier: DiscoveryTier) {
            val remaining = limit - selected.size
            if (remaining <= 0 || candidates.isEmpty()) return
            val ranked = rankCandidates(
                candidates = candidates.filter { it.tier == tier },
                taste = taste,
                excludedKeys = excluded + selected.flatMap(::identityKeys),
                dislikedMbids = taste.dislikedRecordingMbids,
                limit = remaining * CANDIDATE_MULTIPLIER,
            )
            val resolved = resolveCandidates(ranked, remaining, requestStats)
            for (track in alternateArtists(resolved, lastArtist).take(remaining)) {
                selected += track
                lastArtist = track.artist
            }
            if (resolved.isNotEmpty()) onPlayableBatch(selected.takeLast(resolved.size))
        }

        val relatedRecordingCandidates = coroutineScope {
            val labs = async { labsSimilarRecordings(uniqueSeeds, requestStats) }
            val lastFmCandidates = async { lastFmSimilarTracks(uniqueSeeds, lastFm, requestStats) }
            labs.await() + lastFmCandidates.await()
        }
        fillTier(relatedRecordingCandidates, DiscoveryTier.RELATED_RECORDING)

        if (selected.size < limit) {
            val (artistIds, artistNames) = seedArtists(uniqueSeeds, listenBrainz, requestStats)
            val relatedArtists = coroutineScope {
                val labs = async { labsSimilarArtists(artistIds, requestStats) }
                val lastFmArtists =
                    async { lastFmSimilarArtists(uniqueSeeds, lastFm, requestStats) }
                labs.await() + lastFmArtists.await()
            }
            val artistsForExpansion = (artistNames + relatedArtists.map { it.name to it.mbid })
                .distinctBy { (name, mbid) -> mbid ?: name.lowercase().trim() }
                .take(MAX_RELATED_ARTISTS)
            val artistCandidates = coroutineScope {
                val lastFmTracks = async {
                    lastFmArtistTracks(artistsForExpansion, lastFm, requestStats)
                }
                val radioTracks = async {
                    listenBrainzArtistRadio(
                        (artistIds + relatedArtists.mapNotNull { it.mbid }).distinct(),
                        listenBrainz,
                        requestStats,
                    )
                }
                lastFmTracks.await() + radioTracks.await()
            }
            fillTier(artistCandidates, DiscoveryTier.RELATED_ARTIST)
        }

        if (selected.size < limit && !listenBrainz?.username.isNullOrBlank()) {
            fillTier(
                cfRecommendations(listenBrainz, requestStats),
                DiscoveryTier.PERSONAL,
            )
        }

        if (selected.isEmpty()) {
            logInfo("Discovery found no catalog matches for '${uniqueSeeds.last().title}'.")
        }
        QueueDiscoveryResult(
            tracks = selected,
            successfulResponses = requestStats.successes.get(),
            failedResponses = requestStats.failures.get(),
            requestedCount = limit,
        )
    }

    suspend fun discoverNextTracks(
        seed: HomeTrack,
        excludeKeys: Set<String> = emptySet(),
        limit: Int = 6,
        onPlayableBatch: suspend (List<HomeTrack>) -> Unit = {},
    ): QueueDiscoveryResult = discoverNextTracks(listOf(seed), excludeKeys, limit, onPlayableBatch)

    private suspend fun labsSimilarRecordings(
        seeds: List<HomeTrack>,
        stats: RequestStats,
    ): List<DiscoveryCandidate> {
        val recordingMbids = seeds.mapNotNull { it.mbid?.takeIf(String::isNotBlank) }.distinct()
        if (recordingMbids.isEmpty()) return emptyList()
        val candidates = mutableListOf<DiscoveryCandidate>()
        for (chunk in ListenBrainzLabs.chunkSeeds(recordingMbids)) {
            val values = cachedValue("similar:lb:${chunk.joinToString()}", SIMILAR_TTL_MS) {
                val body = getText(ListenBrainzLabs.similarRecordingsUrl(chunk), "", stats)
                    ?: return@cachedValue null
                if (body.isBlank()) return@cachedValue emptyList()
                try {
                    ListenBrainzLabs.parseSimilarRecordingCandidates(JSONArray(body))
                        .mapIndexed { index, similar ->
                            candidate(
                                track = similar.track,
                                source = "ListenBrainz similar recordings",
                                tier = DiscoveryTier.RELATED_RECORDING,
                                rank = index,
                                similarity = similar.similarity,
                                reason = "Similar to a song in this listening session",
                            )
                        }
                } catch (error: Exception) {
                    stats.failures.incrementAndGet()
                    logFailure("labs-similar-recordings", error)
                    null
                }
            }
            candidates += values.orEmpty()
        }
        return candidates
    }

    private suspend fun lastFmSimilarTracks(
        seeds: List<HomeTrack>,
        credentials: LastFmCredentials?,
        stats: RequestStats,
    ): List<DiscoveryCandidate> {
        val apiKey = credentials?.apiKey?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        return coroutineScope {
            seeds.map { seed ->
                async {
                    val url = LastFmApi.similarTracksUrl(seed, apiKey, limit = 20)
                    cachedValue("similar:lfm:${stableHash(url)}", SIMILAR_TTL_MS) {
                        val body = getText(url, stats = stats) ?: return@cachedValue null
                        try {
                            LastFmApi.parseSimilarTracks(body).mapIndexed { index, item ->
                                candidate(
                                    track = item.track,
                                    source = "Last.fm similar tracks",
                                    tier = DiscoveryTier.RELATED_RECORDING,
                                    rank = index,
                                    similarity = item.similarity,
                                    reason = "Similar to a song in this listening session",
                                )
                            }
                        } catch (error: Exception) {
                            stats.failures.incrementAndGet()
                            logFailure("lastfm-similar", error)
                            null
                        }
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun seedArtists(
        seeds: List<HomeTrack>,
        credentials: ListenBrainzCredentials?,
        stats: RequestStats,
    ): Pair<List<String>, List<Pair<String, String?>>> {
        val artistIds =
            seeds.mapNotNull { it.artistMbid?.takeIf(String::isNotBlank) }.toMutableSet()
        val names = seeds.map { it.artist to it.artistMbid }.toMutableList()
        for (seed in seeds) {
            val metadata = lookupSeedMetadata(seed, credentials, stats) ?: continue
            artistIds += metadata.artistMbids
            val artistName = metadata.artistName ?: seed.artist
            names += artistName to metadata.artistMbids.firstOrNull()
        }
        return artistIds.toList() to names
    }

    private suspend fun lookupSeedMetadata(
        seed: HomeTrack,
        credentials: ListenBrainzCredentials?,
        stats: RequestStats,
    ): ListenBrainzRecordingMetadata? {
        val cacheKey = "metadata:seed:${candidateKey(seed)}:${accountKey(null, credentials)}"
        val recordingMbid = seed.mbid?.takeIf(String::isNotBlank)
        return cachedValue(cacheKey, METADATA_TTL_MS) {
            val token = credentials?.token.orEmpty()
            if (recordingMbid != null) {
                val body = getText(
                    ListenBrainzLabs.metadataRecordingUrl(listOf(recordingMbid)), token, stats,
                )
                if (!body.isNullOrBlank()) {
                    val parsed = runCatching {
                        val metadata = ListenBrainzLabs.parseRecordingMetadata(
                            JSONObject(body), listOf(recordingMbid),
                        )
                        metadata[recordingMbid]
                    }.getOrNull()
                    if (parsed != null && parsed.artistMbids.isNotEmpty()) return@cachedValue parsed
                }
                val lookupBody = getText(
                    ListenBrainzLabs.recordingMbidLookupUrl(listOf(recordingMbid)), token, stats,
                )
                if (!lookupBody.isNullOrBlank()) {
                    val parsed = runCatching {
                        ListenBrainzLabs.parseRecordingMbidLookup(JSONArray(lookupBody))
                    }.getOrDefault(emptyList()).firstOrNull()
                    if (parsed != null) return@cachedValue parsed
                }
            }
            val lookupBody = getText(ListenBrainzLabs.metadataLookupUrl(seed), token, stats)
                ?: return@cachedValue null
            runCatching { ListenBrainzLabs.parseMetadataLookup(JSONObject(lookupBody)) }
                .onFailure {
                    stats.failures.incrementAndGet()
                    logFailure("metadata-lookup", it)
                }
                .getOrNull()
        }
    }

    private suspend fun labsSimilarArtists(
        artistMbids: List<String>,
        stats: RequestStats,
    ): List<SimilarArtistCandidate> {
        if (artistMbids.isEmpty()) return emptyList()
        val artists = mutableListOf<SimilarArtistCandidate>()
        for (chunk in ListenBrainzLabs.chunkSeeds(artistMbids)) {
            val values = cachedValue("similar:lb-artist:${chunk.joinToString()}", SIMILAR_TTL_MS) {
                val body = getText(ListenBrainzLabs.similarArtistsUrl(chunk), stats = stats)
                    ?: return@cachedValue null
                try {
                    ListenBrainzLabs.parseSimilarArtists(JSONArray(body))
                        .mapIndexed { index, artist ->
                            SimilarArtistCandidate(
                                artist.name,
                                artist.mbid,
                                "ListenBrainz",
                                index,
                                artist.score.toDouble()
                            )
                        }
                } catch (error: Exception) {
                    stats.failures.incrementAndGet()
                    logFailure("labs-similar-artists", error)
                    null
                }
            }
            artists += values.orEmpty()
        }
        return artists
    }

    private suspend fun lastFmSimilarArtists(
        seeds: List<HomeTrack>,
        credentials: LastFmCredentials?,
        stats: RequestStats,
    ): List<SimilarArtistCandidate> {
        val apiKey = credentials?.apiKey?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        return coroutineScope {
            seeds.map { seed ->
                async {
                    val url = LastFmApi.similarArtistsUrl(seed.artist, seed.artistMbid, apiKey)
                    cachedValue("similar:lfm-artist:${stableHash(url)}", SIMILAR_TTL_MS) {
                        val body = getText(url, stats = stats) ?: return@cachedValue null
                        try {
                            LastFmApi.parseSimilarArtists(body).mapIndexed { index, artist ->
                                SimilarArtistCandidate(
                                    artist.name,
                                    artist.mbid,
                                    "Last.fm",
                                    index,
                                    artist.similarity,
                                )
                            }
                        } catch (error: Exception) {
                            stats.failures.incrementAndGet()
                            logFailure("lastfm-similar-artists", error)
                            null
                        }
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun lastFmArtistTracks(
        artists: List<Pair<String, String?>>,
        credentials: LastFmCredentials?,
        stats: RequestStats,
    ): List<DiscoveryCandidate> {
        val apiKey = credentials?.apiKey?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        return coroutineScope {
            artists.map { (name, artistMbid) ->
                async {
                    val url = LastFmApi.topTracksUrl(name, artistMbid, apiKey, limit = 8)
                    cachedValue("similar:lfm-top:${stableHash(url)}", SIMILAR_TTL_MS) {
                        val body = getText(url, stats = stats) ?: return@cachedValue null
                        try {
                            LastFmApi.parseTopTracks(body).mapIndexed { index, item ->
                                val track = item.track.copy(
                                    artistMbid = item.track.artistMbid ?: artistMbid
                                )
                                candidate(
                                    track = track,
                                    source = "Last.fm related artist tracks",
                                    tier = DiscoveryTier.RELATED_ARTIST,
                                    rank = index,
                                    similarity = item.similarity,
                                    reason = "A popular track by ${track.artist}",
                                )
                            }
                        } catch (error: Exception) {
                            stats.failures.incrementAndGet()
                            logFailure("lastfm-artist-top-tracks", error)
                            null
                        }
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun listenBrainzArtistRadio(
        artistMbids: List<String>,
        credentials: ListenBrainzCredentials?,
        stats: RequestStats,
    ): List<DiscoveryCandidate> {
        if (artistMbids.isEmpty()) return emptyList()
        val token = credentials?.token.orEmpty()
        val radioRows = mutableListOf<Pair<String, ListenBrainzRadioRecording>>()
        for (artistMbid in artistMbids.distinct().take(MAX_RADIO_ARTISTS)) {
            val rows = cachedValue("similar:lb-radio:$artistMbid", SIMILAR_TTL_MS) {
                val body = getText(
                    ListenBrainzLabs.artistRadioUrl(artistMbid), token, stats,
                ) ?: return@cachedValue null
                try {
                    ListenBrainzLabs.parseArtistRadio(JSONObject(body))
                } catch (error: Exception) {
                    stats.failures.incrementAndGet()
                    logFailure("listenbrainz-radio", error)
                    null
                }
            }.orEmpty()
            radioRows += rows.map { artistMbid to it }
        }
        val recordingMbids =
            radioRows.map { it.second.recordingMbid }.distinct().take(MAX_METADATA_MBIDS)
        val metadata = expandRecordingMbids(recordingMbids, credentials, stats)
        return radioRows.mapIndexedNotNull { index, (seedArtistMbid, row) ->
            val recording = metadata[row.recordingMbid] ?: return@mapIndexedNotNull null
            val track = recording.toTrack() ?: return@mapIndexedNotNull null
            candidate(
                track = track.copy(
                    artistMbid = recording.artistMbids.firstOrNull() ?: row.artistMbid
                ),
                source = "ListenBrainz artist radio",
                tier = DiscoveryTier.RELATED_ARTIST,
                rank = index,
                similarity = row.listenCount?.toDouble(),
                reason = "ListenBrainz artist radio from ${row.artistName ?: seedArtistMbid}",
            )
        }
    }

    private suspend fun cfRecommendations(
        credentials: ListenBrainzCredentials,
        stats: RequestStats,
    ): List<DiscoveryCandidate> {
        val username =
            credentials.username?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        val cacheKey = "personal:cf:${accountKey(null, credentials)}"
        val recommendations = cachedValue(cacheKey, PERSONAL_TTL_MS) {
            val body = getText(
                ListenBrainzLabs.cfRecommendationsUrl(username, count = 50),
                credentials.token.orEmpty(),
                stats,
            ) ?: return@cachedValue null
            if (body.isBlank()) return@cachedValue emptyList()
            try {
                ListenBrainzLabs.parseCfRecommendations(JSONObject(body))
            } catch (error: Exception) {
                stats.failures.incrementAndGet()
                logFailure("listenbrainz-cf", error)
                null
            }
        }.orEmpty()
        if (recommendations.isEmpty()) return emptyList()
        val metadata = expandRecordingMbids(
            recommendations.take(MAX_CF_RECORDINGS).map { it.recordingMbid },
            credentials,
            stats,
        )
        return recommendations.mapIndexedNotNull { index, recommendation ->
            val track =
                metadata[recommendation.recordingMbid]?.toTrack() ?: return@mapIndexedNotNull null
            candidate(
                track = track,
                source = "ListenBrainz personal recommendations",
                tier = DiscoveryTier.PERSONAL,
                rank = index,
                similarity = recommendation.score,
                reason = "Recommended for your ListenBrainz listening history",
            )
        }
    }

    private suspend fun expandRecordingMbids(
        recordingMbids: List<String>,
        credentials: ListenBrainzCredentials?,
        stats: RequestStats,
    ): Map<String, ListenBrainzRecordingMetadata> {
        val ids = recordingMbids.distinct().filter(String::isNotBlank).take(MAX_METADATA_MBIDS)
        if (ids.isEmpty()) return emptyMap()
        val token = credentials?.token.orEmpty()
        val result = mutableMapOf<String, ListenBrainzRecordingMetadata>()
        for (chunk in ListenBrainzLabs.chunkSeeds(ids)) {
            val expanded =
                cachedValue("metadata:recordings:${chunk.joinToString()}", METADATA_TTL_MS) {
                    val metadataBody =
                        getText(ListenBrainzLabs.metadataRecordingUrl(chunk), token, stats)
                    val nested = if (!metadataBody.isNullOrBlank()) {
                        runCatching {
                            ListenBrainzLabs.parseRecordingMetadata(JSONObject(metadataBody), chunk)
                        }.getOrDefault(emptyMap())
                    } else emptyMap()

                    val lookupBody =
                        getText(ListenBrainzLabs.recordingMbidLookupUrl(chunk), token, stats)
                    val lookup = if (!lookupBody.isNullOrBlank()) {
                        runCatching {
                            ListenBrainzLabs.parseRecordingMbidLookup(JSONArray(lookupBody))
                        }.getOrDefault(emptyList())
                            .associateBy(ListenBrainzRecordingMetadata::recordingMbid)
                    } else emptyMap()
                    if (nested.isEmpty() && lookup.isEmpty()) return@cachedValue null
                    chunk.associateWith { requested ->
                        val primary = nested[requested]
                        val fallback = lookup[requested]
                            ?: lookup.values.firstOrNull { it.recordingMbid == requested }
                        ListenBrainzRecordingMetadata(
                            recordingMbid = requested,
                            title = primary?.title ?: fallback?.title,
                            artistName = primary?.artistName ?: fallback?.artistName,
                            artistMbids = primary?.artistMbids?.takeIf { it.isNotEmpty() }
                                ?: fallback?.artistMbids.orEmpty(),
                            releaseName = primary?.releaseName ?: fallback?.releaseName,
                            durationMs = primary?.durationMs ?: fallback?.durationMs,
                        )
                    }
                }
            result += expanded.orEmpty()
        }
        return result
    }

    private suspend fun resolveCandidates(
        candidates: List<DiscoveryCandidate>,
        limit: Int,
        stats: RequestStats,
    ): List<HomeTrack> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()
        val output = mutableListOf<Pair<HomeTrack, Double>>()
        for (batch in candidates.chunked(CATALOG_BATCH_SIZE)) {
            if (output.size >= limit) break
            val catalogTracks = try {
                searchRepository.resolveCatalogBatch(batch.map(DiscoveryCandidate::track))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                stats.failures.incrementAndGet()
                logFailure("catalog-resolution", error)
                emptyList()
            }
            val candidateByKey = batch.associateBy { candidateKey(it.track) }
            val enriched = try {
                searchRepository.enrichAvailability(catalogTracks)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logFailure("catalog-availability", error)
                catalogTracks
            }
            for (track in enriched) {
                if (track.providerTrackId.isNullOrBlank()) continue
                val candidate = candidateByKey[candidateKey(track)]
                    ?: batch.firstOrNull { sameCandidate(it.track, track) }
                    ?: continue
                output += track to candidateScore(candidate)
            }
        }
        return output.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    internal fun rankCandidates(
        candidates: List<DiscoveryCandidate>,
        taste: UserTasteProfile,
        excludedKeys: Set<String>,
        dislikedMbids: Set<String>,
        limit: Int,
    ): List<DiscoveryCandidate> {
        val merged = mutableListOf<DiscoveryCandidate>()
        val disliked = dislikedMbids.mapTo(HashSet(), String::lowercase)
        for (candidate in candidates) {
            val track = candidate.track
            if (track.title.isBlank() || track.artist.isBlank()) continue
            if (candidate.recordingMbid?.lowercase()?.let(disliked::contains) == true) continue
            if (identityKeys(track).any(excludedKeys::contains)) continue
            val existingIndex = merged.indexOfFirst { sameCandidate(it.track, track) }
            if (existingIndex < 0) merged += candidate
            else {
                val existing = merged[existingIndex]
                merged[existingIndex] = existing.copy(
                    recordingMbid = existing.recordingMbid ?: candidate.recordingMbid,
                    artistMbids = (existing.artistMbids + candidate.artistMbids).distinct(),
                    supportingSources = existing.supportingSources + candidate.supportingSources,
                    similarity = maxOfNullable(existing.similarity, candidate.similarity),
                    rank = minOf(existing.rank, candidate.rank),
                    reason = if (existing.reason == candidate.reason) existing.reason
                    else "Related to your listening and supported by ${existing.supportingSources.size + 1} sources",
                )
            }
        }
        val favoriteKeys = taste.lovedTracks.mapTo(HashSet(), ::candidateKey)
        val favoriteMbids = taste.lovedRecordingMbids.map(String::lowercase).toSet()
        val artistPreferences = mutableMapOf<String, Double>()
        taste.topArtists.groupBy {
            it.source ?: it.id.substringBefore('-')
        }.values.forEach { artists ->
            artists.forEachIndexed { index, artist ->
                val key = artist.name.lowercase().trim()
                val preference = normalizedPreference(index, artists.size)
                artistPreferences[key] = maxOf(artistPreferences[key] ?: 0.0, preference)
            }
        }
        val trackPreferences = mutableMapOf<String, Double>()
        taste.topTracks.groupBy {
            it.source ?: it.id.substringBefore('-')
        }.values.forEach { tracks ->
            tracks.forEachIndexed { index, track ->
                val key = candidateKey(track)
                val preference = normalizedPreference(index, tracks.size)
                trackPreferences[key] = maxOf(trackPreferences[key] ?: 0.0, preference)
            }
        }
        return merged.sortedByDescending { candidate ->
            val isFavorite = candidateKey(candidate.track) in favoriteKeys ||
                    candidate.recordingMbid?.lowercase()?.let(favoriteMbids::contains) == true
            val bothServices = candidate.supportingSources.size > 1
            val trackPreference = trackPreferences[candidateKey(candidate.track)] ?: 0.0
            val artistPreference =
                artistPreferences[candidate.track.artist.lowercase().trim()] ?: 0.0
            (if (isFavorite) 2.0 else 0.0) +
                    (if (bothServices) 1.0 else 0.0) +
                    trackPreference * 0.25 + artistPreference * 0.25 +
                    (candidate.similarity?.takeIf { it in 0.0..1.0 }
                        ?: 1.0 / (candidate.rank + 1.0)) * 0.2
        }.take(limit)
    }

    private fun alternateArtists(tracks: List<HomeTrack>, initialArtist: String): List<HomeTrack> {
        if (tracks.size < 2) return tracks
        val pending = tracks.toMutableList()
        val result = mutableListOf<HomeTrack>()
        var previous =
            initialArtist.takeIf(String::isNotBlank)?.let(TrackIdentity::normalizedArtist)
        while (pending.isNotEmpty()) {
            val nextIndex = pending.indexOfFirst { track ->
                previous == null || TrackIdentity.normalizedArtist(track.artist) != previous
            }.takeIf { it >= 0 } ?: 0
            val next = pending.removeAt(nextIndex)
            result += next
            previous = TrackIdentity.normalizedArtist(next.artist)
        }
        return result
    }

    private fun candidateScore(candidate: DiscoveryCandidate): Double {
        val similarity = candidate.similarity?.takeIf { it in 0.0..1.0 }
            ?: 1.0 / (candidate.rank + 1.0)
        return similarity + candidate.supportingSources.size + (if (candidate.reason.contains(
                "loved",
                true
            )
        ) 2.0 else 0.0)
    }

    private fun candidate(
        track: HomeTrack,
        source: String,
        tier: DiscoveryTier,
        rank: Int,
        similarity: Double?,
        reason: String,
    ) = DiscoveryCandidate(
        track = track.copy(source = source),
        recordingMbid = track.mbid,
        artistMbids = listOfNotNull(track.artistMbid),
        source = source,
        tier = tier,
        rank = rank,
        similarity = similarity,
        reason = reason,
        supportingSources = setOf(if (source.startsWith("Last.fm")) "Last.fm" else "ListenBrainz"),
    )

    private fun ListenBrainzRecordingMetadata.toTrack(): HomeTrack? {
        val title = title?.takeIf(String::isNotBlank) ?: return null
        val artist = artistName?.takeIf(String::isNotBlank) ?: return null
        return HomeTrack(
            id = "lbmd_$recordingMbid",
            title = title,
            artist = artist,
            album = releaseName,
            source = "ListenBrainz",
            mbid = recordingMbid,
            artistMbid = artistMbids.firstOrNull(),
            durationMs = durationMs,
        )
    }

    private suspend fun getText(
        url: String,
        token: String = "",
        stats: RequestStats,
    ): String? {
        val isListenBrainz = url.contains("listenbrainz.org", ignoreCase = true)
        val headers = buildMap {
            put("Accept", "application/json")
            if (isListenBrainz) put("User-Agent", ListenBrainzRequestPolicy.USER_AGENT)
            if (token.isNotBlank()) put("Authorization", "Token $token")
        }
        val response = if (isListenBrainz) {
            ListenBrainzRequestPolicy.execute { http.get(url, headers) }
        } else {
            http.get(url, headers)
        }
        return when (response) {
            is HttpOutcome.Success -> {
                stats.successes.incrementAndGet()
                response.value
            }

            is HttpOutcome.Failure -> {
                stats.failures.incrementAndGet()
                logHttpFailure(url, response.error)
                null
            }
        }
    }

    private suspend fun <T : Any> cachedValue(
        key: String,
        ttlMs: Long,
        loader: suspend () -> T?,
    ): T? {
        DiscoveryCache.get<T>(key)?.let { return it }
        val loaded = loader() ?: return null
        DiscoveryCache.put(key, loaded, ttlMs)
        return loaded
    }

    private fun candidateKey(track: HomeTrack): String =
        TrackIdentity.keyOf(null, track.title, track.artist)

    private fun identityKeys(track: HomeTrack): Set<String> = setOf(
        TrackIdentity.keyOf(track),
        TrackIdentity.keyOf(null, track.title, track.artist),
    )

    private fun sameCandidate(first: HomeTrack, second: HomeTrack): Boolean =
        (!first.mbid.isNullOrBlank() && first.mbid.equals(second.mbid, ignoreCase = true)) ||
                candidateKey(first) == candidateKey(second)

    private fun normalizedPreference(index: Int, size: Int): Double =
        if (size <= 1) 1.0 else 1.0 - index.toDouble() / size.toDouble()

    private fun maxOfNullable(first: Double?, second: Double?): Double? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }

    private fun accountKey(
        lastFm: LastFmCredentials?,
        listenBrainz: ListenBrainzCredentials?
    ): String =
        stableHash(
            listOf(
                lastFm?.username.orEmpty(), lastFm?.apiKey.orEmpty(),
                listenBrainz?.username.orEmpty(), listenBrainz?.token.orEmpty(),
            ).joinToString("|")
        )

    private fun stableHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun logHttpFailure(url: String, error: HttpError) {
        val label = when {
            error.kind == HttpErrorKind.STATUS && error.statusCode == 429 -> "rate limited"
            error.statusCode == 401 -> "unauthorized"
            error.statusCode == 403 -> "forbidden"
            error.kind == HttpErrorKind.TIMEOUT -> "timed out"
            error.kind == HttpErrorKind.NETWORK -> "network unavailable"
            error.kind == HttpErrorKind.MALFORMED -> "malformed response"
            else -> "HTTP ${error.statusCode ?: "error"}"
        }
        logInfo("Discovery request failed ($label): ${url.substringBefore("api_key=").take(150)}")
    }

    private fun logFailure(tag: String, error: Throwable) {
        logInfo("Discovery branch '$tag' failed: ${error.javaClass.simpleName}: ${error.message}")
    }

    private fun logInfo(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private data class SimilarArtistCandidate(
        val name: String,
        val mbid: String?,
        val provider: String,
        val rank: Int,
        val similarity: Double?,
    )

    private class RequestStats {
        val successes = AtomicInteger()
        val failures = AtomicInteger()
    }

    private companion object {
        val http = HttpJsonClient(connectTimeoutMs = 6_000, readTimeoutMs = 6_000)
        const val TAG = "Discovery"
        const val MAX_SEEDS = 4
        const val MAX_RELATED_ARTISTS = 6
        const val MAX_RADIO_ARTISTS = 6
        const val MAX_CF_RECORDINGS = 40
        const val MAX_METADATA_MBIDS = 75
        const val CATALOG_BATCH_SIZE = 8
        const val CANDIDATE_MULTIPLIER = 3
        const val SIMILAR_TTL_MS = 6 * 60 * 60 * 1_000L
        const val METADATA_TTL_MS = SIMILAR_TTL_MS
        const val PERSONAL_TTL_MS = 15 * 60 * 1_000L
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
            val keys = listOf(
                TrackIdentity.keyOf(track),
                TrackIdentity.keyOf(null, track.title, track.artist),
            )
            if (keys.none(seen::contains)) {
                seen.addAll(keys)
                prioritized.add(track)
            }
        }
    }
    return prioritized
}

private object DiscoveryCache {
    private data class Entry(val expiresAtMs: Long, val value: Any)

    private val entries = ConcurrentHashMap<String, Entry>()

    @Volatile
    private var activeAccountKey: String? = null

    fun activateAccount(accountKey: String) {
        if (activeAccountKey == accountKey) return
        synchronized(this) {
            if (activeAccountKey == accountKey) return
            activeAccountKey = accountKey
            entries.keys.removeIf { it.startsWith("personal:") }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String): T? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMs <= System.currentTimeMillis()) {
            entries.remove(key, entry)
            return null
        }
        return entry.value as? T
    }

    fun put(key: String, value: Any, ttlMs: Long) {
        if (entries.size > 500) entries.entries.removeIf { it.value.expiresAtMs <= System.currentTimeMillis() }
        entries[key] = Entry(System.currentTimeMillis() + ttlMs, value)
    }
}
