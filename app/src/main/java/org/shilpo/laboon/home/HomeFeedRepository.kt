package org.shilpo.laboon.home

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.auth.ListenBrainzCredentials
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.lyricsporn.LyricspornCatalogMatch
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.net.HttpError
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.fold
import org.shilpo.laboon.net.objAtOrNull
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchRepositoryImpl
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

class HomeFeedRepository(
    private val sessionStore: SessionStore,
    private val artworkResolver: LyricspornClient = LyricspornClient,
    private val availabilityResolver: SearchRepository = SearchRepositoryImpl(sessionStore),
) {
    private val recentTracksMutex = Mutex()
    private val listenBrainzTrendingMutex = Mutex()
    private val accountCacheMutex = Mutex()
    private var rawCacheAccountKey: String? = null
    private var cachedRecentTracks: List<HomeTrack>? = null
    private var cachedRecentTracksAtMs = 0L
    private var cachedListenBrainzTrending: List<HomeTrack>? = null

    suspend fun clearCache() {
        recentTracksMutex.withLock { cachedRecentTracks = null; cachedRecentTracksAtMs = 0L }
        listenBrainzTrendingMutex.withLock { cachedListenBrainzTrending = null }
        tasteProfileMutex.withLock { tasteProfileCache.clear() }
    }

    /**
     * Reads the account signals used by both home recommendations and continuous queue discovery.
     * Successful account profiles are shared for 15 minutes across repository instances.
     */
    suspend fun fetchTasteProfile(forceRefresh: Boolean = false): UserTasteProfile =
        withContext(Dispatchers.IO) {
            val lastFm = sessionStore.getLastFmCredentials()
            val listenBrainz = sessionStore.getListenBrainzCredentials()
            val cacheKey = tasteProfileKey(lastFm, listenBrainz)
            tasteProfileMutex.withLock {
                tasteProfileCache.keys.removeIf { it != cacheKey }
                val now = System.currentTimeMillis()
                tasteProfileCache[cacheKey]
                    ?.takeIf { !forceRefresh && now - it.createdAtMs < TASTE_PROFILE_TTL_MS }
                    ?.let { return@withLock it.profile }

                val profile = coroutineScope {
                    val recent = async { getRawRecentTracks() }
                    val topTracks = async {
                        val (lastFmTracks, listenBrainzTracks) = coroutineScope {
                            async { fetchLastFmTopTracks(lastFm) } to
                                    async { fetchListenBrainzTopRecordings(listenBrainz) }
                        }
                        lastFmTracks.await() + listenBrainzTracks.await()
                    }
                    val topArtists = async {
                        val (lastFmArtists, listenBrainzArtists) = coroutineScope {
                            async { fetchLastFmTopArtists(lastFm) } to
                                    async { fetchListenBrainzTopArtists(listenBrainz) }
                        }
                        lastFmArtists.await() + listenBrainzArtists.await()
                    }
                    val loved = async { fetchLastFmLovedTracks(lastFm) }
                    val feedback = async { fetchListenBrainzFeedback(listenBrainz) }
                    UserTasteProfile(
                        recentTracks = dedupeRecentTracks(recent.await()),
                        topTracks = topTracks.await(),
                        topArtists = topArtists.await(),
                        lovedTracks = loved.await().distinctBy(TrackIdentity::keyOf),
                        lovedRecordingMbids = feedback.await()
                            .filterValues { it == "love" }
                            .keys + loved.await().mapNotNull { it.mbid },
                        dislikedRecordingMbids = feedback.await()
                            .filterValues { it == "hate" }
                            .keys,
                    )
                }
                tasteProfileCache[cacheKey] = CachedTasteProfile(now, profile)
                profile
            }
        }

    private suspend fun getRawRecentTracks(): List<HomeTrack> {
        ensureAccountCacheCurrent()
        return recentTracksMutex.withLock {
            cachedRecentTracks?.takeIf { System.currentTimeMillis() - cachedRecentTracksAtMs < TASTE_PROFILE_TTL_MS }
                ?.let { return@withLock it }
            val lastFmCreds = sessionStore.getLastFmCredentials()
            val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
            val (lastFmTracks, listenBrainzTracks) = coroutineScope {
                val lastFm = async { fetchLastFmRecentTracks(lastFmCreds) }
                val listenBrainz = async { fetchListenBrainzRecentListens(listenBrainzCreds) }
                lastFm.await() to listenBrainz.await()
            }
            val combinedRecent = dedupeRecentTracks(lastFmTracks + listenBrainzTracks)
                .take(MAX_RECENT_TRACKS)
            cachedRecentTracks = combinedRecent
            cachedRecentTracksAtMs = System.currentTimeMillis()
            combinedRecent
        }
    }

    private suspend fun getRawListenBrainzTrending(): List<HomeTrack> {
        ensureAccountCacheCurrent()
        return listenBrainzTrendingMutex.withLock {
            cachedListenBrainzTrending?.let { return@withLock it }
            val listenBrainzTrending = fetchListenBrainzTrendingRecordings()
            cachedListenBrainzTrending = listenBrainzTrending
            listenBrainzTrending
        }
    }

    private suspend fun ensureAccountCacheCurrent() {
        val currentKey = tasteProfileKey(
            sessionStore.getLastFmCredentials(),
            sessionStore.getListenBrainzCredentials(),
        )
        accountCacheMutex.withLock {
            if (rawCacheAccountKey == currentKey) return@withLock
            recentTracksMutex.withLock { cachedRecentTracks = null; cachedRecentTracksAtMs = 0L }
            listenBrainzTrendingMutex.withLock { cachedListenBrainzTrending = null }
            rawCacheAccountKey = currentKey
        }
    }

    fun getDisplayRegion(): String? {
        val defaultLocale = Locale.getDefault()
        val country = defaultLocale.country.trim()
        if (country.isEmpty()) return null
        val englishCountry = defaultLocale.getDisplayCountry(Locale.ENGLISH).trim()
        if (englishCountry.isEmpty()) return null
        return defaultLocale.displayCountry.trim().ifEmpty { englishCountry }
    }

    private fun getQueryRegion(): String? {
        val defaultLocale = Locale.getDefault()
        val country = defaultLocale.country.trim()
        if (country.isEmpty()) return null
        val englishCountry = defaultLocale.getDisplayCountry(Locale.ENGLISH).trim()
        return englishCountry.ifEmpty { null }
    }

    suspend fun fetchRotation(): List<HomeTrack> = withContext(Dispatchers.IO) {
        resolveTracks(fetchTasteProfile().recentTracks)
    }

    suspend fun fetchRecommended(): List<HomeTrack> = withContext(Dispatchers.IO) {
        val taste = fetchTasteProfile()
        val recent = taste.recentTracks
        val topTracks = if (recent.size < MAX_RECOMMENDATION_SEEDS) taste.topTracks else emptyList()
        val topArtists = taste.topArtists
        val candidateSeeds = (recent + topTracks).take(MAX_RECOMMENDATION_SEEDS)
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
        val (lastFmRecs, listenBrainzRecs) = coroutineScope {
            val lastFm = async {
                fetchLastFmSimilarTracks(
                    lastFmCreds,
                    candidateSeeds,
                    topArtists.firstOrNull()?.name
                )
            }
            val listenBrainz = async { fetchListenBrainzRecommendations(listenBrainzCreds) }
            lastFm.await() to listenBrainz.await()
        }
        val rotationKeys = recent.map { TrackIdentity.keyOf(it) }.toSet()
        val rawRecommended = (lastFmRecs + listenBrainzRecs)
            .distinctBy { TrackIdentity.keyOf(it) }
            .filterNot { rotationKeys.contains(TrackIdentity.keyOf(it)) }
            .take(MAX_RECOMMENDATION_CANDIDATES)
        resolveTracks(rawRecommended).take(10)
    }

    suspend fun fetchTopArtists(): List<HomeArtist> = withContext(Dispatchers.IO) {
        val rawArtists = fetchTasteProfile().topArtists
            .distinctBy { it.name.lowercase().trim() }
            .take(10)
        resolveArtists(rawArtists)
    }

    suspend fun fetchTopAlbums(): List<HomeAlbum> = withContext(Dispatchers.IO) {
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
        val (lastFmTopAlbums, listenBrainzTopReleases) = coroutineScope {
            val lastFm = async { fetchLastFmTopAlbums(lastFmCreds) }
            val listenBrainz = async { fetchListenBrainzTopReleases(listenBrainzCreds) }
            lastFm.await() to listenBrainz.await()
        }
        val combinedTopAlbums = (lastFmTopAlbums + listenBrainzTopReleases)
            .distinctBy { TrackIdentity.keyOf(null, it.title, it.artist) }
            .take(10)
        resolveAlbums(combinedTopAlbums)
    }

    suspend fun fetchTopTracks(): List<HomeTrack> = withContext(Dispatchers.IO) {
        val rawTopTracks = fetchTasteProfile().topTracks
            .distinctBy(TrackIdentity::keyOf)
            .take(MAX_TOP_TRACK_CANDIDATES)
        resolveTracks(rawTopTracks).take(10)
    }

    suspend fun fetchRegionalTrending(region: String? = null): List<HomeTrack> =
        withContext(Dispatchers.IO) {
            val targetRegion = region?.trim()?.ifEmpty { null } ?: getQueryRegion()
            ?: return@withContext emptyList()
            val lastFmCreds = sessionStore.getLastFmCredentials()
            val regionalTrending =
                fetchLastFmRegionalTrendingTracks(lastFmCreds, targetRegion)
            resolveTracks(regionalTrending).take(6)
        }

    suspend fun fetchGlobalTrending(): List<HomeTrack> = withContext(Dispatchers.IO) {
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val (lastFmTrending, listenBrainzTrending) = coroutineScope {
            val lastFm = async { fetchLastFmTrendingTracks(lastFmCreds) }
            val listenBrainz = async { getRawListenBrainzTrending() }
            lastFm.await() to listenBrainz.await()
        }
        val globalTrending = (lastFmTrending + listenBrainzTrending)
            .distinctBy { TrackIdentity.keyOf(it) }
            .take(MAX_TRENDING_CANDIDATES)
        resolveTracks(globalTrending).take(6)
    }

    suspend fun fetchWeeklyPicks(): List<HomeTrack> = withContext(Dispatchers.IO) {
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val topArtists = fetchTasteProfile().topArtists
        val weeklyCandidateArtists = topArtists.drop(1).take(3).ifEmpty { topArtists.take(3) }
        val recent = getRawRecentTracks()
        val rotationKeys = recent.map { TrackIdentity.keyOf(it) }.toSet()
        val rawWeekly = fetchWeeklyDiscoveries(lastFmCreds, weeklyCandidateArtists)
            .distinctBy { TrackIdentity.keyOf(it) }
            .filterNot { rotationKeys.contains(TrackIdentity.keyOf(it)) }
            .take(MAX_WEEKLY_CANDIDATES)
        resolveTracks(rawWeekly).take(8)
    }

    private suspend fun resolveTracks(tracks: List<HomeTrack>): List<HomeTrack> {
        val catalogResolved = availabilityResolver.resolveCatalogBatch(tracks)
        return availabilityResolver.enrichAvailability(catalogResolved)
    }

    private suspend fun resolveAlbums(albums: List<HomeAlbum>): List<HomeAlbum> = coroutineScope {
        val lyricspornApiUrl = sessionStore.getSession()?.lyricspornApiUrl
        albums.map { album ->
            async {
                val resolved = artworkResolver.resolveAlbumCatalogItem(
                    apiBaseUrl = lyricspornApiUrl,
                    title = album.title,
                    artist = album.artist,
                )
                val exactAlbum = resolved
                    ?.takeIf(LyricspornCatalogMatch::isExactIdentity)
                    ?.item
                album.copy(
                    artworkUrl = album.artworkUrl ?: resolved?.item?.artworkUrl,
                    appleCatalogId = exactAlbum?.id,
                )
            }
        }.awaitAll().filter { !it.appleCatalogId.isNullOrBlank() }
    }

    private suspend fun resolveArtists(artists: List<HomeArtist>): List<HomeArtist> =
        coroutineScope {
            val lyricspornApiUrl = sessionStore.getSession()?.lyricspornApiUrl
            artists.map { artist ->
                async {
                    val resolved =
                        artworkResolver.resolveArtistArtwork(lyricspornApiUrl, artist.name)
                    artist.copy(imageUrl = resolved)
                }
            }.awaitAll()
        }

    private suspend fun fetchLastFmRecentTracks(creds: LastFmCredentials?): List<HomeTrack> {
        val username = creds?.username?.trim().orEmpty()
        if (username.isEmpty()) return emptyList()

        val apiKey = creds?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        val endpoint =
            "https://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&user=$username&api_key=$apiKey&format=json&limit=25"

        val root = fetchJson(endpoint) ?: return emptyList()
        val trackArray = root.objOrNull("recenttracks")?.arrOrNull("track") ?: return emptyList()

        val result = mutableListOf<HomeTrack>()
        for (i in 0 until trackArray.length()) {
            val obj = trackArray.objAtOrNull(i) ?: continue
            val name = obj.optString("name").trim()
            val artistObj = obj.objOrNull("artist")
            val artist = (artistObj?.optString("#text") ?: obj.optString("artist")).trim()
            if (name.isEmpty() || artist.isEmpty()) continue

            val album = obj.objOrNull("album")?.optString("#text")?.trim()

            result.add(
                HomeTrack(
                    id = "lfm-rec-$i",
                    title = name,
                    artist = artist,
                    album = album,
                    artworkUrl = null,
                    mbid = obj.stringOrNull("mbid"),
                    source = "Last.fm",
                    listenedAtMs = obj.objOrNull("date")?.optString("uts")
                        ?.toLongOrNull()?.times(1_000L),
                )
            )
        }
        return result
    }

    private suspend fun fetchLastFmTopTracks(creds: LastFmCredentials?): List<HomeTrack> {
        val username = creds?.username?.trim().orEmpty()
        if (username.isEmpty()) return emptyList()

        val apiKey = creds?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        val endpoint =
            "https://ws.audioscrobbler.com/2.0/?method=user.gettoptracks&user=$username&api_key=$apiKey&format=json&limit=15"

        val root = fetchJson(endpoint) ?: return emptyList()
        val trackArray = root.objOrNull("toptracks")?.arrOrNull("track") ?: return emptyList()

        val result = mutableListOf<HomeTrack>()
        for (i in 0 until trackArray.length()) {
            val obj = trackArray.objAtOrNull(i) ?: continue
            val name = obj.optString("name").trim()
            val artistObj = obj.objOrNull("artist")
            val artist = (artistObj?.optString("name") ?: obj.optString("artist")).trim()
            if (name.isEmpty() || artist.isEmpty()) continue

            val playCount = obj.optString("playcount").toLongOrNull() ?: 0L
            val album = obj.objOrNull("album")?.let { albumObj ->
                albumObj.optString("#text").trim()
                    .ifEmpty { albumObj.optString("name").trim() }
                    .takeIf(String::isNotEmpty)
            }

            result.add(
                HomeTrack(
                    id = "lfm-top-trk-$i",
                    title = name,
                    artist = artist,
                    album = album,
                    playCount = playCount,
                    artworkUrl = null,
                    mbid = obj.stringOrNull("mbid"),
                    source = "Last.fm",
                )
            )
        }
        return result
    }

    private suspend fun fetchLastFmTopArtists(creds: LastFmCredentials?): List<HomeArtist> {
        val username = creds?.username?.trim().orEmpty()
        if (username.isEmpty()) return emptyList()

        val apiKey = creds?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        val endpoint =
            "https://ws.audioscrobbler.com/2.0/?method=user.gettopartists&user=$username&api_key=$apiKey&format=json&limit=15"

        val root = fetchJson(endpoint) ?: return emptyList()
        val artistArray = root.objOrNull("topartists")?.arrOrNull("artist")
            ?: return emptyList()

        val result = mutableListOf<HomeArtist>()
        for (i in 0 until artistArray.length()) {
            val obj = artistArray.objAtOrNull(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isEmpty()) continue

            val playCount = obj.optString("playcount").toLongOrNull() ?: 0L

            result.add(
                HomeArtist(
                    id = "lfm-art-$i",
                    name = name,
                    playCount = playCount,
                    imageUrl = null,
                    mbid = obj.stringOrNull("mbid"),
                    source = "Last.fm",
                )
            )
        }
        return result
    }

    private suspend fun fetchLastFmTopAlbums(creds: LastFmCredentials?): List<HomeAlbum> {
        val username = creds?.username?.trim().orEmpty()
        if (username.isEmpty()) return emptyList()

        val apiKey = creds?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        val endpoint =
            "https://ws.audioscrobbler.com/2.0/?method=user.gettopalbums&user=$username&api_key=$apiKey&format=json&limit=15"

        val root = fetchJson(endpoint) ?: return emptyList()
        val albumArray = root.objOrNull("topalbums")?.arrOrNull("album") ?: return emptyList()

        val result = mutableListOf<HomeAlbum>()
        for (i in 0 until albumArray.length()) {
            val obj = albumArray.objAtOrNull(i) ?: continue
            val title = obj.optString("name").trim()
            val artistObj = obj.objOrNull("artist")
            val artist = (artistObj?.optString("name") ?: obj.optString("artist")).trim()
            if (title.isEmpty() || artist.isEmpty()) continue

            val playCount = obj.optString("playcount").toLongOrNull() ?: 0L

            result.add(
                HomeAlbum(
                    id = "lfm-alb-$i",
                    title = title,
                    artist = artist,
                    playCount = playCount,
                    artworkUrl = null,
                )
            )
        }
        return result
    }

    private suspend fun fetchListenBrainzRecentListens(creds: ListenBrainzCredentials?): List<HomeTrack> {
        val username = creds?.username?.trim().orEmpty()
        if (username.isEmpty()) return emptyList()

        val endpoint =
            "https://api.listenbrainz.org/1/user/$username/listens?count=$MAX_RECENT_TRACKS"

        val root = fetchJson(endpoint, creds?.token) ?: return emptyList()
        val listensArray = root.objOrNull("payload")?.arrOrNull("listens") ?: return emptyList()

        val result = mutableListOf<HomeTrack>()
        for (i in 0 until listensArray.length()) {
            val obj = listensArray.objAtOrNull(i) ?: continue
            val meta = obj.objOrNull("track_metadata") ?: continue
            val title = meta.optString("track_name").trim()
            val artist = meta.optString("artist_name").trim()
            if (title.isEmpty() || artist.isEmpty()) continue

            val album = meta.optString("release_name").trim().ifEmpty { null }

            result.add(
                HomeTrack(
                    id = "lb-rec-$i",
                    title = title,
                    artist = artist,
                    album = album,
                    artworkUrl = null,
                    mbid = meta.objOrNull("mbid_mapping")?.stringOrNull("recording_mbid")
                        ?: meta.objOrNull("additional_info")?.stringOrNull("recording_mbid"),
                    artistMbid = meta.objOrNull("mbid_mapping")?.arrOrNull("artist_mbids")
                        ?.optString(0)?.takeIf(String::isNotBlank),
                    source = "ListenBrainz",
                    listenedAtMs = obj.optLong("listened_at")
                        .takeIf { it > 0L }?.times(1_000L),
                )
            )
        }
        return result
    }

    private suspend fun fetchListenBrainzTopRecordings(creds: ListenBrainzCredentials?): List<HomeTrack> {
        val username = creds?.username?.trim().orEmpty()
        if (username.isEmpty()) return emptyList()

        val endpoint =
            "https://api.listenbrainz.org/1/stats/user/$username/recordings?range=all_time&count=15"

        val root = fetchJson(endpoint, creds?.token) ?: return emptyList()
        val recArray = root.objOrNull("payload")?.arrOrNull("recordings") ?: return emptyList()

        val result = mutableListOf<HomeTrack>()
        for (i in 0 until recArray.length()) {
            val obj = recArray.objAtOrNull(i) ?: continue
            val title = obj.optString("track_name").trim()
            val artist = obj.optString("artist_name").trim()
            if (title.isEmpty() || artist.isEmpty()) continue

            val album = obj.optString("release_name").trim().ifEmpty { null }
            val playCount = obj.optLong("listen_count", 0L)

            result.add(
                HomeTrack(
                    id = "lb-top-rec-$i",
                    title = title,
                    artist = artist,
                    album = album,
                    playCount = playCount,
                    artworkUrl = null,
                    mbid = obj.stringOrNull("recording_mbid"),
                    source = "ListenBrainz",
                )
            )
        }
        return result
    }

    private suspend fun fetchListenBrainzTopArtists(creds: ListenBrainzCredentials?): List<HomeArtist> {
        val username = creds?.username?.trim().orEmpty()
        if (username.isEmpty()) return emptyList()

        val endpoint =
            "https://api.listenbrainz.org/1/stats/user/$username/artists?range=all_time&count=15"

        val root = fetchJson(endpoint, creds?.token) ?: return emptyList()
        val artArray = root.objOrNull("payload")?.arrOrNull("artists") ?: return emptyList()

        val result = mutableListOf<HomeArtist>()
        for (i in 0 until artArray.length()) {
            val obj = artArray.objAtOrNull(i) ?: continue
            val name = obj.optString("artist_name").trim()
            if (name.isEmpty()) continue

            val playCount = obj.optLong("listen_count", 0L)

            result.add(
                HomeArtist(
                    id = "lb-top-art-$i",
                    name = name,
                    playCount = playCount,
                    imageUrl = null,
                    mbid = obj.stringOrNull("artist_mbid"),
                    source = "ListenBrainz",
                )
            )
        }
        return result
    }

    private suspend fun fetchLastFmLovedTracks(creds: LastFmCredentials?): List<HomeTrack> {
        val username = creds?.username?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        val apiKey = creds.apiKey?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        val body =
            fetchJson(LastFmApi.lovedTracksUrl(username, apiKey, limit = 100)) ?: return emptyList()
        return runCatching { LastFmApi.parseLovedTracks(body.toString()) }
            .getOrElse { emptyList() }
    }

    private suspend fun fetchListenBrainzFeedback(
        creds: ListenBrainzCredentials?,
    ): Map<String, String> {
        val username = creds?.username?.trim()?.takeIf(String::isNotEmpty) ?: return emptyMap()
        val root = fetchJson(ListenBrainzLabs.feedbackUrl(username, count = 500), creds.token)
            ?: return emptyMap()
        return ListenBrainzLabs.parseFeedback(root)
    }

    private fun dedupeRecentTracks(tracks: List<HomeTrack>): List<HomeTrack> {
        val result = mutableListOf<HomeTrack>()
        for (track in tracks.sortedByDescending { it.listenedAtMs ?: Long.MIN_VALUE }) {
            val duplicateIndex = result.indexOfFirst { existing ->
                val sameRecording =
                    if (!track.mbid.isNullOrBlank() && !existing.mbid.isNullOrBlank()) {
                        track.mbid.equals(existing.mbid, ignoreCase = true)
                    } else {
                        TrackIdentity.normalizedTitle(track.title) == TrackIdentity.normalizedTitle(
                            existing.title
                        ) &&
                                TrackIdentity.normalizedArtist(track.artist) == TrackIdentity.normalizedArtist(
                            existing.artist
                        )
                    }
                val left = track.listenedAtMs
                val right = existing.listenedAtMs
                sameRecording && (left == null || right == null ||
                        kotlin.math.abs(left - right) <= RECENT_LISTEN_DEDUPE_WINDOW_MS)
            }
            if (duplicateIndex < 0) result += track
            else {
                val existing = result[duplicateIndex]
                if (existing.mbid.isNullOrBlank() && !track.mbid.isNullOrBlank()) {
                    result[duplicateIndex] = existing.copy(mbid = track.mbid)
                }
            }
        }
        return result.take(MAX_RECENT_TRACKS * 2)
    }

    private fun tasteProfileKey(
        lastFm: LastFmCredentials?,
        listenBrainz: ListenBrainzCredentials?,
    ): String = listOf(
        lastFm?.username.orEmpty(), lastFm?.apiKey.orEmpty(),
        listenBrainz?.username.orEmpty(), listenBrainz?.token.orEmpty(),
    ).joinToString("|") { value ->
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private suspend fun fetchListenBrainzTopReleases(creds: ListenBrainzCredentials?): List<HomeAlbum> {
        val username = creds?.username?.trim().orEmpty()
        if (username.isEmpty()) return emptyList()

        val endpoint =
            "https://api.listenbrainz.org/1/stats/user/$username/releases?range=all_time&count=15"

        val root = fetchJson(endpoint, creds?.token) ?: return emptyList()
        val relArray = root.objOrNull("payload")?.arrOrNull("releases") ?: return emptyList()

        val result = mutableListOf<HomeAlbum>()
        for (i in 0 until relArray.length()) {
            val obj = relArray.objAtOrNull(i) ?: continue
            val title = obj.optString("release_name").trim()
            val artist = obj.optString("artist_name").trim()
            if (title.isEmpty() || artist.isEmpty()) continue

            val playCount = obj.optLong("listen_count", 0L)

            result.add(
                HomeAlbum(
                    id = "lb-top-rel-$i",
                    title = title,
                    artist = artist,
                    playCount = playCount,
                    artworkUrl = null,
                )
            )
        }
        return result
    }

    private suspend fun fetchLastFmSimilarTracks(
        creds: LastFmCredentials?,
        seedTracks: List<HomeTrack>,
        topArtistName: String? = null,
    ): List<HomeTrack> {
        val apiKey = creds?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        val result = mutableListOf<HomeTrack>()

        for (seed in seedTracks) {
            val endpoint = LastFmApi.similarTracksUrl(seed, apiKey, limit = 6)
            val tracks = executeLastFmTrackList(endpoint, "similartracks", "track")
            for (t in tracks) {
                if (result.none {
                        it.title.equals(
                            t.title,
                            ignoreCase = true
                        ) && it.artist.equals(t.artist, ignoreCase = true)
                    }) {
                    result.add(t)
                }
            }
            if (result.size >= 8) break
        }

        if (result.isEmpty() && !topArtistName.isNullOrBlank()) {
            val similarEndpoint = LastFmApi.similarArtistsUrl(
                topArtistName,
                artistMbid = null,
                apiKey = apiKey,
                limit = 4,
            )
            val similarArtists = executeLastFmArtistList(similarEndpoint)
            for (simArtist in similarArtists) {
                val topTracksEndpoint = LastFmApi.topTracksUrl(
                    simArtist.name,
                    simArtist.mbid,
                    apiKey,
                    limit = 2,
                )
                val tracks = executeLastFmTrackList(topTracksEndpoint, "toptracks", "track")
                for (t in tracks) {
                    if (result.none {
                            it.title.equals(
                                t.title,
                                ignoreCase = true
                            ) && it.artist.equals(t.artist, ignoreCase = true)
                        }) {
                        result.add(t)
                    }
                }
            }
        }

        return result
    }

    private suspend fun executeLastFmTrackList(
        endpoint: String,
        containerKey: String,
        listKey: String,
    ): List<HomeTrack> {
        val root = fetchJson(endpoint) ?: return emptyList()
        return runCatching {
            LastFmApi.parseTrackList(root.toString(), containerKey, listKey)
                .map(LastFmTrackCandidate::track)
        }.getOrElse { emptyList() }
    }

    private suspend fun executeLastFmArtistList(endpoint: String): List<LastFmArtistCandidate> {
        val root = fetchJson(endpoint) ?: return emptyList()
        return runCatching { LastFmApi.parseSimilarArtists(root.toString()) }
            .getOrElse { emptyList() }
    }

    private suspend fun fetchListenBrainzRecommendations(creds: ListenBrainzCredentials?): List<HomeTrack> {
        val credentials = creds ?: return emptyList()
        val username = credentials.username?.trim().orEmpty()
        if (username.isEmpty()) return emptyList()

        val recommendationRoot = fetchJson(
            ListenBrainzLabs.cfRecommendationsUrl(username, count = 15),
            credentials.token,
        ) ?: return emptyList()
        val recommendations = ListenBrainzLabs.parseCfRecommendations(recommendationRoot)
        val recordingMbids = recommendations.map(ListenBrainzCfRecommendation::recordingMbid)
        if (recordingMbids.isEmpty()) return emptyList()

        val metadataRoot = fetchJson(
            ListenBrainzLabs.metadataRecordingUrl(recordingMbids),
            credentials.token,
        )
        val metadata = metadataRoot?.let {
            ListenBrainzLabs.parseRecordingMetadata(it, recordingMbids)
        }.orEmpty().toMutableMap()

        val missingMetadataMbids = recordingMbids.filter { mbid ->
            val item = metadata[mbid]
            item == null || item.title.isNullOrBlank() || item.artistName.isNullOrBlank()
        }
        if (missingMetadataMbids.isNotEmpty()) {
            val lookupRows = fetchJsonArray(
                ListenBrainzLabs.recordingMbidLookupUrl(missingMetadataMbids),
                credentials.token,
            )?.let(ListenBrainzLabs::parseRecordingMbidLookup).orEmpty()
            lookupRows.forEach { row ->
                val requestedId = missingMetadataMbids.firstOrNull { it == row.recordingMbid }
                    ?: return@forEach
                val primary = metadata[requestedId]
                metadata[requestedId] = row.copy(
                    recordingMbid = requestedId,
                    title = primary?.title ?: row.title,
                    artistName = primary?.artistName ?: row.artistName,
                    artistMbids = primary?.artistMbids?.takeIf { it.isNotEmpty() }
                        ?: row.artistMbids,
                    releaseName = primary?.releaseName ?: row.releaseName,
                    durationMs = primary?.durationMs ?: row.durationMs,
                )
            }
        }

        return recommendations.mapNotNull { recommendation ->
            val item = metadata[recommendation.recordingMbid] ?: return@mapNotNull null
            val title = item.title?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val artist = item.artistName?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            HomeTrack(
                id = "lb-rec-cf-${recommendation.recordingMbid}",
                title = title,
                artist = artist,
                album = item.releaseName,
                artworkUrl = null,
                source = "ListenBrainz",
                mbid = recommendation.recordingMbid,
                artistMbid = item.artistMbids.firstOrNull(),
                durationMs = item.durationMs,
            )
        }
    }

    private suspend fun fetchLastFmTrendingTracks(creds: LastFmCredentials?): List<HomeTrack> {
        val apiKey = creds?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        val endpoint =
            "https://ws.audioscrobbler.com/2.0/?method=chart.gettoptracks&api_key=$apiKey&format=json&limit=10"
        return executeLastFmTrackList(endpoint, "tracks", "track")
    }

    private suspend fun fetchListenBrainzTrendingRecordings(): List<HomeTrack> {
        val endpoint =
            "https://api.listenbrainz.org/1/stats/sitewide/recordings?range=this_week&count=10"

        val root = fetchJson(endpoint) ?: return emptyList()
        val recArray = root.objOrNull("payload")?.arrOrNull("recordings") ?: return emptyList()

        val result = mutableListOf<HomeTrack>()
        for (i in 0 until recArray.length()) {
            val obj = recArray.objAtOrNull(i) ?: continue
            val title = obj.optString("track_name").trim()
            val artist = obj.optString("artist_name").trim()
            if (title.isEmpty() || artist.isEmpty()) continue

            val album = obj.optString("release_name").trim().ifEmpty { null }
            val playCount = obj.optLong("listen_count", 0L)

            result.add(
                HomeTrack(
                    id = "lb-trend-$i",
                    title = title,
                    artist = artist,
                    album = album,
                    playCount = playCount,
                    artworkUrl = null,
                )
            )
        }
        return result
    }

    private suspend fun fetchLastFmRegionalTrendingTracks(
        creds: LastFmCredentials?,
        country: String
    ): List<HomeTrack> {
        val apiKey = creds?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        val encodedCountry =
            java.net.URLEncoder.encode(country.trim(), StandardCharsets.UTF_8.name())
        val endpoint =
            "https://ws.audioscrobbler.com/2.0/?method=geo.gettoptracks&country=$encodedCountry&api_key=$apiKey&format=json&limit=10"
        return executeLastFmTrackList(endpoint, "tracks", "track")
    }

    private suspend fun fetchWeeklyDiscoveries(
        creds: LastFmCredentials?,
        seedArtists: List<HomeArtist>,
    ): List<HomeTrack> {
        if (seedArtists.isEmpty()) return emptyList()
        val apiKey = creds?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        val result = mutableListOf<HomeTrack>()

        for (artist in seedArtists.take(3)) {
            val similarEndpoint = LastFmApi.similarArtistsUrl(
                artist.name,
                artist.mbid,
                apiKey,
                limit = 3,
            )
            val similarArtists = executeLastFmArtistList(similarEndpoint)
            for (simArtist in similarArtists) {
                val topTracksEndpoint = LastFmApi.topTracksUrl(
                    simArtist.name,
                    simArtist.mbid,
                    apiKey,
                    limit = 2,
                )
                val tracks = executeLastFmTrackList(topTracksEndpoint, "toptracks", "track")
                for (t in tracks) {
                    if (result.none {
                            it.title.equals(
                                t.title,
                                ignoreCase = true
                            ) && it.artist.equals(t.artist, ignoreCase = true)
                        }) {
                        result.add(t)
                    }
                }
                if (result.size >= MAX_WEEKLY_CANDIDATES) break
            }
            if (result.size >= MAX_WEEKLY_CANDIDATES) break
        }

        return result
    }

    private suspend fun fetchJson(url: String, token: String? = null): JSONObject? {
        val headers = buildMap {
            put("Accept", "application/json")
            if (url.contains("listenbrainz.org", ignoreCase = true)) {
                put("User-Agent", ListenBrainzRequestPolicy.USER_AGENT)
            }
            if (!token.isNullOrBlank()) {
                put("Authorization", "Token ${token.trim()}")
            }
        }
        val response = if (url.contains("listenbrainz.org", ignoreCase = true)) {
            ListenBrainzRequestPolicy.execute { http.getJson(url, headers) }
        } else {
            http.getJson(url, headers)
        }
        return response.fold(
            onSuccess = { it },
            onFailure = { error ->
                logWarning(
                    "Feed request failed (${describe(error)}) for ${redactApiKey(url)}: " +
                            error.message
                )
                null
            },
        )
    }

    private suspend fun fetchJsonArray(url: String, token: String? = null): JSONArray? {
        val headers = buildMap {
            put("Accept", "application/json")
            if (url.contains("listenbrainz.org", ignoreCase = true)) {
                put("User-Agent", ListenBrainzRequestPolicy.USER_AGENT)
            }
            if (!token.isNullOrBlank()) {
                put("Authorization", "Token ${token.trim()}")
            }
        }
        val response = if (url.contains("listenbrainz.org", ignoreCase = true)) {
            ListenBrainzRequestPolicy.execute { http.get(url, headers) }
        } else {
            http.get(url, headers)
        }
        return response.fold(
            onSuccess = { body ->
                runCatching { JSONArray(body) }.getOrElse {
                    logWarning("Malformed JSON array from ${redactApiKey(url)}: ${it.message.orEmpty()}")
                    null
                }
            },
            onFailure = { error ->
                logWarning(
                    "Feed request failed (${describe(error)}) for ${redactApiKey(url)}: " +
                            error.message,
                )
                null
            },
        )
    }

    private fun describe(error: HttpError): String = when {
        error.kind == HttpErrorKind.STATUS &&
                error.message.contains("rate limited", ignoreCase = true) -> "rate limited"

        error.statusCode == 401 -> "unauthorized"
        error.statusCode == 403 -> "forbidden"
        error.kind == HttpErrorKind.TIMEOUT -> "timed out"
        error.kind == HttpErrorKind.NETWORK -> "network unavailable"
        error.kind == HttpErrorKind.MALFORMED -> "malformed body"
        else -> "unexpected error"
    }

    private fun redactApiKey(url: String): String = url.replace(API_KEY_QUERY, "api_key=***")

    private fun logWarning(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private companion object {
        private val http = HttpJsonClient()
        private val tasteProfileMutex = Mutex()
        private val tasteProfileCache = mutableMapOf<String, CachedTasteProfile>()
        private val API_KEY_QUERY = Regex("api_key=[^&]*", RegexOption.IGNORE_CASE)
        private const val TAG = "HomeFeed"
        private const val MAX_RECENT_TRACKS = 25
        private const val MAX_RECOMMENDATION_SEEDS = 5
        private const val MAX_RECOMMENDATION_CANDIDATES = 20
        private const val MAX_TOP_TRACK_CANDIDATES = 20
        private const val MAX_TRENDING_CANDIDATES = 20
        private const val MAX_WEEKLY_CANDIDATES = 16
        private const val TASTE_PROFILE_TTL_MS = 15 * 60 * 1_000L
        private const val RECENT_LISTEN_DEDUPE_WINDOW_MS = 120_000L
    }

    private data class CachedTasteProfile(
        val createdAtMs: Long,
        val profile: UserTasteProfile,
    )
}
