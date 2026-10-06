package org.shilpo.laboon.home

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import java.util.Locale

class HomeFeedRepository(
    private val sessionStore: SessionStore,
    private val artworkResolver: LyricspornClient = LyricspornClient,
    private val availabilityResolver: SearchRepository = SearchRepositoryImpl(sessionStore),
) {
    private val recentTracksMutex = Mutex()
    private val topTracksMutex = Mutex()
    private val topArtistsMutex = Mutex()
    private val listenBrainzTrendingMutex = Mutex()
    private var cachedRecentTracks: List<HomeTrack>? = null
    private var cachedTopTracks: List<HomeTrack>? = null
    private var cachedTopArtists: List<HomeArtist>? = null
    private var cachedListenBrainzTrending: List<HomeTrack>? = null

    suspend fun clearCache() {
        recentTracksMutex.withLock { cachedRecentTracks = null }
        topTracksMutex.withLock { cachedTopTracks = null }
        topArtistsMutex.withLock { cachedTopArtists = null }
        listenBrainzTrendingMutex.withLock { cachedListenBrainzTrending = null }
    }

    private suspend fun getRawRecentTracks(): List<HomeTrack> = recentTracksMutex.withLock {
        cachedRecentTracks?.let { return@withLock it }
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
        val (lastFmTracks, listenBrainzTracks) = coroutineScope {
            val lastFm = async { fetchLastFmRecentTracks(lastFmCreds) }
            val listenBrainz = async { fetchListenBrainzRecentListens(listenBrainzCreds) }
            lastFm.await() to listenBrainz.await()
        }
        val combinedRecent = (lastFmTracks + listenBrainzTracks)
            .distinctBy { TrackIdentity.keyOf(it) }
            .take(MAX_RECENT_TRACKS)
        cachedRecentTracks = combinedRecent
        combinedRecent
    }

    private suspend fun getRawTopTracks(): List<HomeTrack> = topTracksMutex.withLock {
        cachedTopTracks?.let { return@withLock it }
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
        val (lastFmTopTracks, listenBrainzTopRecordings) = coroutineScope {
            val lastFm = async { fetchLastFmTopTracks(lastFmCreds) }
            val listenBrainz = async { fetchListenBrainzTopRecordings(listenBrainzCreds) }
            lastFm.await() to listenBrainz.await()
        }
        val combinedTopTracks = (lastFmTopTracks + listenBrainzTopRecordings)
            .distinctBy { TrackIdentity.keyOf(it) }
        cachedTopTracks = combinedTopTracks
        combinedTopTracks
    }

    private suspend fun getRawTopArtists(): List<HomeArtist> = topArtistsMutex.withLock {
        cachedTopArtists?.let { return@withLock it }
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
        val (lastFmTopArtists, listenBrainzTopArtists) = coroutineScope {
            val lastFm = async { fetchLastFmTopArtists(lastFmCreds) }
            val listenBrainz = async { fetchListenBrainzTopArtists(listenBrainzCreds) }
            lastFm.await() to listenBrainz.await()
        }
        val combinedTopArtists = (lastFmTopArtists + listenBrainzTopArtists)
            .distinctBy { it.name.lowercase().trim() }
        cachedTopArtists = combinedTopArtists
        combinedTopArtists
    }

    private suspend fun getRawListenBrainzTrending(): List<HomeTrack> =
        listenBrainzTrendingMutex.withLock {
            cachedListenBrainzTrending?.let { return@withLock it }
            val listenBrainzTrending = fetchListenBrainzTrendingRecordings()
            cachedListenBrainzTrending = listenBrainzTrending
            listenBrainzTrending
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
        val recent = getRawRecentTracks()
        resolveTracks(recent)
    }

    suspend fun fetchRecommended(): List<HomeTrack> = withContext(Dispatchers.IO) {
        val recent = getRawRecentTracks()
        val topTracks = if (recent.size < MAX_RECOMMENDATION_SEEDS) {
            getRawTopTracks()
        } else {
            emptyList()
        }
        val topArtists = getRawTopArtists()
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
        val artists = getRawTopArtists()
        val rawArtists = artists.take(10)
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
        val topTracks = getRawTopTracks()
        val rawTopTracks = topTracks.take(MAX_TOP_TRACK_CANDIDATES)
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
        val topArtists = getRawTopArtists()
        val weeklyCandidateArtists = topArtists.drop(1).take(3).ifEmpty { topArtists.take(3) }
        val recent = getRawRecentTracks()
        val rotationKeys = recent.map { TrackIdentity.keyOf(it) }.toSet()
        val rawWeekly = fetchWeeklyDiscoveries(lastFmCreds, weeklyCandidateArtists)
            .distinctBy { TrackIdentity.keyOf(it) }
            .filterNot { rotationKeys.contains(TrackIdentity.keyOf(it)) }
            .take(MAX_WEEKLY_CANDIDATES)
        resolveTracks(rawWeekly).take(8)
    }

    private suspend fun resolveTracks(tracks: List<HomeTrack>): List<HomeTrack> = coroutineScope {
        val lyricspornApiUrl = sessionStore.getSession()?.lyricspornApiUrl
        val catalogResolved = tracks.map { track ->
            async {
                val catalogMatch = artworkResolver.resolveTrackCatalogItem(
                    apiBaseUrl = lyricspornApiUrl,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                )
                val exactItem = catalogMatch
                    ?.takeIf(LyricspornCatalogMatch::isExactIdentity)
                    ?.item
                    ?: return@async null
                track.copy(
                    artworkUrl = track.artworkUrl ?: exactItem.artworkUrl,
                    isrc = track.isrc ?: exactItem.isrc,
                    providerTrackId = exactItem.id,
                    durationMs = track.durationMs ?: exactItem.durationMs,
                )
            }
        }.awaitAll().filterNotNull()
        availabilityResolver.enrichAvailability(catalogResolved)
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
                )
            )
        }
        return result
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
            val encodedTrack =
                java.net.URLEncoder.encode(seed.title.trim(), StandardCharsets.UTF_8.name())
            val encodedArtist =
                java.net.URLEncoder.encode(seed.artist.trim(), StandardCharsets.UTF_8.name())
            val endpoint =
                "https://ws.audioscrobbler.com/2.0/?method=track.getsimilar&track=$encodedTrack&artist=$encodedArtist&api_key=$apiKey&format=json&limit=6"

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
            val encodedArtist =
                java.net.URLEncoder.encode(topArtistName.trim(), StandardCharsets.UTF_8.name())
            val similarEndpoint =
                "https://ws.audioscrobbler.com/2.0/?method=artist.getsimilar&artist=$encodedArtist&api_key=$apiKey&format=json&limit=4"
            val similarArtists = executeLastFmArtistList(similarEndpoint)
            for (simArtist in similarArtists) {
                val encSimArtist =
                    java.net.URLEncoder.encode(simArtist.trim(), StandardCharsets.UTF_8.name())
                val topTracksEndpoint =
                    "https://ws.audioscrobbler.com/2.0/?method=artist.gettoptracks&artist=$encSimArtist&api_key=$apiKey&format=json&limit=2"
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
        val trackArray = root.objOrNull(containerKey)?.arrOrNull(listKey) ?: return emptyList()
        val result = mutableListOf<HomeTrack>()
        for (i in 0 until trackArray.length()) {
            val obj = trackArray.objAtOrNull(i) ?: continue
            val name = obj.optString("name").trim()
            val artistObj = obj.objOrNull("artist")
            val artist = (artistObj?.optString("name") ?: obj.optString("artist")).trim()
            val album = obj.objOrNull("album")?.let { albumObj ->
                albumObj.optString("#text").trim()
                    .ifEmpty { albumObj.optString("name").trim() }
                    .takeIf(String::isNotEmpty)
            }
            if (name.isNotEmpty() && artist.isNotEmpty()) {
                result.add(
                    HomeTrack(
                        id = "lfm-rec-$name-$artist",
                        title = name,
                        artist = artist,
                        album = album,
                        artworkUrl = null,
                        mbid = obj.stringOrNull("mbid"),
                    )
                )
            }
        }
        return result
    }

    private suspend fun executeLastFmArtistList(endpoint: String): List<String> {
        val root = fetchJson(endpoint) ?: return emptyList()
        val artistArray = root.objOrNull("similarartists")?.arrOrNull("artist")
            ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until artistArray.length()) {
            val obj = artistArray.objAtOrNull(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isNotEmpty()) {
                result.add(name)
            }
        }
        return result
    }

    private suspend fun fetchListenBrainzRecommendations(creds: ListenBrainzCredentials?): List<HomeTrack> {
        val username = creds?.username?.trim().orEmpty()
        if (username.isEmpty()) return emptyList()

        val endpoint =
            "https://api.listenbrainz.org/1/cf/recommendation/user/$username/recording?count=15"

        val root = fetchJson(endpoint, creds?.token) ?: return emptyList()
        val mbids = root.objOrNull("payload")?.arrOrNull("mbids") ?: return emptyList()

        val mbidList = mutableListOf<String>()
        for (i in 0 until mbids.length()) {
            val obj = mbids.objAtOrNull(i) ?: continue
            val mbid = obj.optString("recording_mbid").trim()
            if (mbid.isNotEmpty()) {
                mbidList.add(mbid)
            }
        }
        if (mbidList.isEmpty()) return emptyList()

        val metaEndpoint =
            "https://api.listenbrainz.org/1/metadata/recording/?recording_mbids=${
                mbidList.joinToString(
                    ","
                )
            }&inc=artist"
        val metaRoot = fetchJson(metaEndpoint) ?: return emptyList()

        val result = mutableListOf<HomeTrack>()
        var idx = 0
        for (key in metaRoot.keys()) {
            val item = metaRoot.objOrNull(key) ?: continue
            val recObj = item.objOrNull("recording")
            val artObj = item.objOrNull("artist")
            val title = recObj?.optString("name")?.trim().orEmpty()
            val artist = artObj?.optString("name")?.trim().orEmpty()
            if (title.isNotEmpty() && artist.isNotEmpty()) {
                result.add(
                    HomeTrack(
                        id = "lb-rec-cf-$idx",
                        title = title,
                        artist = artist,
                        artworkUrl = null,
                    )
                )
                idx++
            }
        }
        return result
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
            val encodedArtist =
                java.net.URLEncoder.encode(artist.name.trim(), StandardCharsets.UTF_8.name())
            val similarEndpoint =
                "https://ws.audioscrobbler.com/2.0/?method=artist.getsimilar&artist=$encodedArtist&api_key=$apiKey&format=json&limit=3"
            val similarArtists = executeLastFmArtistList(similarEndpoint)
            for (simArtist in similarArtists) {
                val encSimArtist =
                    java.net.URLEncoder.encode(simArtist.trim(), StandardCharsets.UTF_8.name())
                val topTracksEndpoint =
                    "https://ws.audioscrobbler.com/2.0/?method=artist.gettoptracks&artist=$encSimArtist&api_key=$apiKey&format=json&limit=2"
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
            if (!token.isNullOrBlank()) {
                put("Authorization", "Token ${token.trim()}")
            }
        }
        return http.getJson(url, headers).fold(
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
        private val API_KEY_QUERY = Regex("api_key=[^&]*", RegexOption.IGNORE_CASE)
        private const val TAG = "HomeFeed"
        private const val MAX_RECENT_TRACKS = 25
        private const val MAX_RECOMMENDATION_SEEDS = 5
        private const val MAX_RECOMMENDATION_CANDIDATES = 20
        private const val MAX_TOP_TRACK_CANDIDATES = 20
        private const val MAX_TRENDING_CANDIDATES = 20
        private const val MAX_WEEKLY_CANDIDATES = 16
    }
}
