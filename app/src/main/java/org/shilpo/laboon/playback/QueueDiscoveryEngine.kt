package org.shilpo.laboon.playback

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeFeedRepository
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.LastFmApi
import org.shilpo.laboon.home.LastFmArtistCandidate
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
    val source: String,
    val tier: DiscoveryTier,
    val rank: Int,
    val similarity: Double? = null,
    val reason: String,
    val supportingSources: Set<String> = setOf("Last.fm"),
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

/** Builds a playable queue from Last.fm similarity and listening-history data. */
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
        if (limit <= 0 || seeds.isEmpty()) return@withContext QueueDiscoveryResult(emptyList(), 0, 0, limit)
        val credentials = sessionStore.getLastFmCredentials()
        val apiKey = credentials?.apiKey?.trim()?.takeIf(String::isNotEmpty)
            ?: return@withContext QueueDiscoveryResult(emptyList(), 0, 1, limit)

        val stats = RequestStats()
        val taste = try {
            tasteRepository.fetchTasteProfile()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            stats.failures.incrementAndGet()
            logFailure("lastfm-profile", error)
            UserTasteProfile()
        }
        val uniqueSeeds = seeds.distinctBy(TrackIdentity::keyOf).take(MAX_SEEDS)
        val excluded = excludeKeys + uniqueSeeds.mapTo(HashSet(), TrackIdentity::keyOf)
        val selected = mutableListOf<HomeTrack>()
        var previousArtist = uniqueSeeds.lastOrNull()?.artist.orEmpty()

        suspend fun addTier(candidates: List<DiscoveryCandidate>, tier: DiscoveryTier) {
            val remaining = limit - selected.size
            if (remaining <= 0 || candidates.isEmpty()) return
            val ranked = rankCandidates(
                candidates = candidates,
                taste = taste,
                excludedKeys = excluded + selected.map(TrackIdentity::keyOf),
                limit = remaining * CANDIDATE_MULTIPLIER,
            ).filter { it.tier == tier }
            val resolved = resolveCandidates(ranked, remaining, stats)
            val spaced = alternateArtists(resolved, previousArtist).take(remaining)
            if (spaced.isNotEmpty()) {
                selected += spaced
                previousArtist = spaced.last().artist
                onPlayableBatch(spaced)
            }
        }

        val trackMatches = coroutineScope {
            uniqueSeeds.map { seed ->
                async { similarTrackCandidates(seed, apiKey, stats) }
            }.awaitAll().flatten()
        }
        addTier(trackMatches, DiscoveryTier.RELATED_RECORDING)

        if (selected.size < limit) {
            val relatedArtists = coroutineScope {
                uniqueSeeds.map { seed ->
                    async { similarArtists(seed.artist, apiKey, stats) }
                }.awaitAll().flatten()
            }
            val artists = (uniqueSeeds.map { it.artist } + relatedArtists.map(LastFmArtistCandidate::name))
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy(String::lowercase)
                .take(MAX_RELATED_ARTISTS)
            val artistTracks = coroutineScope {
                artists.map { artist ->
                    async { topTracks(artist, apiKey, stats) }
                }.awaitAll().flatten()
            }
            addTier(artistTracks, DiscoveryTier.RELATED_ARTIST)
        }

        if (selected.size < limit) {
            val personal = (taste.topTracks + taste.lovedTracks)
                .distinctBy(TrackIdentity::keyOf)
                .mapIndexed { index, track ->
                    candidate(
                        track = track,
                        source = "Last.fm listening history",
                        tier = DiscoveryTier.PERSONAL,
                        rank = index,
                        similarity = null,
                        reason = "From your Last.fm listening history",
                    )
                }
            addTier(personal, DiscoveryTier.PERSONAL)
        }

        QueueDiscoveryResult(
            tracks = selected,
            successfulResponses = stats.successes.get(),
            failedResponses = stats.failures.get(),
            requestedCount = limit,
        )
    }

    suspend fun discoverNextTracks(
        seed: HomeTrack,
        excludeKeys: Set<String> = emptySet(),
        limit: Int = 6,
        onPlayableBatch: suspend (List<HomeTrack>) -> Unit = {},
    ): QueueDiscoveryResult = discoverNextTracks(listOf(seed), excludeKeys, limit, onPlayableBatch)

    private suspend fun similarTrackCandidates(
        seed: HomeTrack,
        apiKey: String,
        stats: RequestStats,
    ): List<DiscoveryCandidate> {
        val url = LastFmApi.similarTracksUrl(seed, apiKey, limit = 20)
        return cachedValue("track:${stableHash(url)}", SIMILAR_TTL_MS) {
            val body = getText(url, stats) ?: return@cachedValue null
            parse(body, "lastfm-similar-tracks", stats, LastFmApi::parseSimilarTracks)
                ?.mapIndexed { index, item ->
                    candidate(
                        track = item.track,
                        source = "Last.fm similar tracks",
                        tier = DiscoveryTier.RELATED_RECORDING,
                        rank = index,
                        similarity = item.similarity,
                        reason = "Similar to a song in this listening session",
                    )
                }
        }.orEmpty()
    }

    private suspend fun similarArtists(
        artist: String,
        apiKey: String,
        stats: RequestStats,
    ): List<LastFmArtistCandidate> {
        val url = LastFmApi.similarArtistsUrl(artist, apiKey)
        return cachedValue("artist:${stableHash(url)}", SIMILAR_TTL_MS) {
            val body = getText(url, stats) ?: return@cachedValue null
            parse(body, "lastfm-similar-artists", stats, LastFmApi::parseSimilarArtists)
        }.orEmpty()
    }

    private suspend fun topTracks(
        artist: String,
        apiKey: String,
        stats: RequestStats,
    ): List<DiscoveryCandidate> {
        val url = LastFmApi.topTracksUrl(artist, apiKey, limit = 8)
        return cachedValue("top:${stableHash(url)}", SIMILAR_TTL_MS) {
            val body = getText(url, stats) ?: return@cachedValue null
            parse(body, "lastfm-artist-top-tracks", stats, LastFmApi::parseTopTracks)
                ?.mapIndexed { index, item ->
                    candidate(
                        track = item.track,
                        source = "Last.fm related artist tracks",
                        tier = DiscoveryTier.RELATED_ARTIST,
                        rank = index,
                        similarity = item.similarity,
                        reason = "A popular track by ${item.track.artist}",
                    )
                }
        }.orEmpty()
    }

    private suspend fun <T : Any> parse(
        body: String,
        tag: String,
        stats: RequestStats,
        parser: (String) -> T,
    ): T? = try {
        parser(body)
    } catch (error: Exception) {
        stats.failures.incrementAndGet()
        logFailure(tag, error)
        null
    }

    private suspend fun getText(url: String, stats: RequestStats): String? =
        when (val response = http.get(url, mapOf("Accept" to "application/json"))) {
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

    private suspend fun resolveCandidates(
        candidates: List<DiscoveryCandidate>,
        limit: Int,
        stats: RequestStats,
    ): List<HomeTrack> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()
        val resolved = mutableListOf<Pair<HomeTrack, Double>>()
        for (batch in candidates.chunked(CATALOG_BATCH_SIZE)) {
            if (resolved.size >= limit) break
            val catalogTracks = try {
                searchRepository.resolveCatalogBatch(batch.map(DiscoveryCandidate::track))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                stats.failures.incrementAndGet()
                logFailure("catalog-resolution", error)
                emptyList()
            }
            val enriched = try {
                searchRepository.enrichAvailability(catalogTracks)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logFailure("catalog-availability", error)
                catalogTracks
            }
            val candidatesByIdentity = batch.associateBy { TrackIdentity.keyOf(it.track) }
            enriched.forEach { track ->
                if (track.providerTrackId.isNullOrBlank()) return@forEach
                val match = candidatesByIdentity[TrackIdentity.keyOf(track)] ?: return@forEach
                resolved += track to candidateScore(match)
            }
        }
        return resolved.sortedByDescending(Pair<HomeTrack, Double>::second)
            .distinctBy { TrackIdentity.keyOf(it.first) }
            .take(limit)
            .map(Pair<HomeTrack, Double>::first)
    }

    internal fun rankCandidates(
        candidates: List<DiscoveryCandidate>,
        taste: UserTasteProfile,
        excludedKeys: Set<String>,
        limit: Int,
    ): List<DiscoveryCandidate> {
        if (limit <= 0) return emptyList()
        val merged = mutableListOf<DiscoveryCandidate>()
        for (candidate in candidates) {
            val track = candidate.track
            if (track.title.isBlank() || track.artist.isBlank()) continue
            if (TrackIdentity.keyOf(track) in excludedKeys) continue
            val matchIndex = merged.indexOfFirst { TrackIdentity.isSameTrack(it.track, track) }
            if (matchIndex < 0) {
                merged += candidate
            } else {
                val existing = merged[matchIndex]
                merged[matchIndex] = existing.copy(
                    supportingSources = existing.supportingSources + candidate.supportingSources,
                    similarity = maxOfNullable(existing.similarity, candidate.similarity),
                    rank = minOf(existing.rank, candidate.rank),
                )
            }
        }
        val lovedKeys = taste.lovedTracks.mapTo(HashSet(), TrackIdentity::keyOf)
        val topTrackRank = taste.topTracks.mapIndexed { index, track -> TrackIdentity.keyOf(track) to index }
            .toMap()
        val topArtistRank = taste.topArtists.mapIndexed { index, artist -> artist.name.lowercase() to index }
            .toMap()
        return merged.sortedByDescending { candidate ->
            val key = TrackIdentity.keyOf(candidate.track)
            val love = if (key in lovedKeys) 2.0 else 0.0
            val trackPreference = topTrackRank[key]?.let { 1.0 / (it + 1.0) } ?: 0.0
            val artistPreference = topArtistRank[candidate.track.artist.lowercase()]
                ?.let { 1.0 / (it + 1.0) } ?: 0.0
            love + trackPreference * 0.4 + artistPreference * 0.3 + candidateScore(candidate)
        }.take(limit)
    }

    private fun alternateArtists(tracks: List<HomeTrack>, initialArtist: String): List<HomeTrack> {
        if (tracks.size < 2) return tracks
        val pending = tracks.toMutableList()
        val result = mutableListOf<HomeTrack>()
        var previous = initialArtist.takeIf(String::isNotBlank)?.let(TrackIdentity::normalizedArtist)
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
        return similarity + candidate.supportingSources.size
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
        source = source,
        tier = tier,
        rank = rank,
        similarity = similarity,
        reason = reason,
    )

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
        logInfo("Queue discovery failed ($label): ${url.substringBefore("api_key=").take(150)}")
    }

    private fun logFailure(tag: String, error: Throwable) {
        logInfo("Queue discovery '$tag' failed: ${error.javaClass.simpleName}: ${error.message}")
    }

    private fun logInfo(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private class RequestStats {
        val successes = AtomicInteger()
        val failures = AtomicInteger()
    }

    private companion object {
        val http = HttpJsonClient(connectTimeoutMs = 6_000, readTimeoutMs = 6_000)
        const val TAG = "QueueDiscovery"
        const val MAX_SEEDS = 4
        const val MAX_RELATED_ARTISTS = 6
        const val CATALOG_BATCH_SIZE = 8
        const val CANDIDATE_MULTIPLIER = 3
        const val SIMILAR_TTL_MS = 6 * 60 * 60 * 1_000L
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
            val key = TrackIdentity.keyOf(track)
            if (seen.add(key)) prioritized.add(track)
        }
    }
    return prioritized
}

private object DiscoveryCache {
    private data class Entry(val expiresAtMs: Long, val value: Any)

    private val entries = ConcurrentHashMap<String, Entry>()

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
        if (entries.size > 500) {
            entries.entries.removeIf { it.value.expiresAtMs <= System.currentTimeMillis() }
        }
        entries[key] = Entry(System.currentTimeMillis() + ttlMs, value)
    }
}

private fun maxOfNullable(first: Double?, second: Double?): Double? = when {
    first == null -> second
    second == null -> first
    else -> maxOf(first, second)
}
