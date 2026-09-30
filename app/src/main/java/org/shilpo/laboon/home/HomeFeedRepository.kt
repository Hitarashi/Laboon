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
import org.shilpo.laboon.net.HttpError
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.arrOrNull
import org.shilpo.laboon.net.fold
import org.shilpo.laboon.net.objAtOrNull
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull
import java.nio.charset.StandardCharsets
import java.util.Locale

class HomeFeedRepository(
    private val sessionStore: SessionStore,
    private val artworkResolver: BackendArtworkResolver = BackendArtworkResolver,
) {
    private val cacheMutex = Mutex()
    private var cachedRecentTracks: List<HomeTrack>? = null
    private var cachedTopTracks: List<HomeTrack>? = null
    private var cachedTopArtists: List<HomeArtist>? = null
    private var cachedListenBrainzTrending: List<HomeTrack>? = null

    private suspend fun getRawRecentTracks(): List<HomeTrack> {
        cacheMutex.withLock {
            cachedRecentTracks?.let { return it }
        }
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
        val lastFmTracks = fetchLastFmRecentTracks(lastFmCreds)
        val listenBrainzTracks = fetchListenBrainzRecentListens(listenBrainzCreds)
        val combinedRecent = (lastFmTracks + listenBrainzTracks)
            .distinctBy { TrackIdentity.keyOf(it) }
        cacheMutex.withLock {
            cachedRecentTracks = combinedRecent
        }
        return combinedRecent
    }

    private suspend fun getRawTopTracks(): List<HomeTrack> {
        cacheMutex.withLock {
            cachedTopTracks?.let { return it }
        }
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
        val lastFmTopTracks = fetchLastFmTopTracks(lastFmCreds)
        val listenBrainzTopRecordings = fetchListenBrainzTopRecordings(listenBrainzCreds)
        val combinedTopTracks = (lastFmTopTracks + listenBrainzTopRecordings)
            .distinctBy { TrackIdentity.keyOf(it) }
        cacheMutex.withLock {
            cachedTopTracks = combinedTopTracks
        }
        return combinedTopTracks
    }

    private suspend fun getRawTopArtists(): List<HomeArtist> {
        cacheMutex.withLock {
            cachedTopArtists?.let { return it }
        }
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
        val lastFmTopArtists = fetchLastFmTopArtists(lastFmCreds)
        val listenBrainzTopArtists = fetchListenBrainzTopArtists(listenBrainzCreds)
        val combinedTopArtists = (lastFmTopArtists + listenBrainzTopArtists)
            .distinctBy { it.name.lowercase().trim() }
        cacheMutex.withLock {
            cachedTopArtists = combinedTopArtists
        }
        return combinedTopArtists
    }

    private suspend fun getRawListenBrainzTrending(): List<HomeTrack> {
        cacheMutex.withLock {
            cachedListenBrainzTrending?.let { return it }
        }
        val listenBrainzTrending = fetchListenBrainzTrendingRecordings()
        cacheMutex.withLock {
            cachedListenBrainzTrending = listenBrainzTrending
        }
        return listenBrainzTrending
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
        val rawRotation = recent
        val session = sessionStore.getSession()
        resolveTracks(rawRotation, session?.serverUrl, session?.token)
    }

    suspend fun fetchRecommended(): List<HomeTrack> = withContext(Dispatchers.IO) {
        val recent = getRawRecentTracks()
        val topTracks = getRawTopTracks()
        val topArtists = getRawTopArtists()
        val candidateSeeds = (recent + topTracks).take(5)
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
        val lastFmRecs =
            fetchLastFmSimilarTracks(lastFmCreds, candidateSeeds, topArtists.firstOrNull()?.name)
        val listenBrainzRecs = fetchListenBrainzRecommendations(listenBrainzCreds)
        val rotationKeys = recent.map { TrackIdentity.keyOf(it) }.toSet()
        val rawRecommended = (lastFmRecs + listenBrainzRecs)
            .distinctBy { TrackIdentity.keyOf(it) }
            .filterNot { rotationKeys.contains(TrackIdentity.keyOf(it)) }
            .take(10)
        val session = sessionStore.getSession()
        resolveTracks(rawRecommended, session?.serverUrl, session?.token)
    }

    suspend fun fetchTopArtists(): List<HomeArtist> = withContext(Dispatchers.IO) {
        val artists = getRawTopArtists()
        val rawArtists = artists.take(10)
        val session = sessionStore.getSession()
        resolveArtists(rawArtists, session?.serverUrl, session?.token)
    }

    suspend fun fetchTopAlbums(): List<HomeAlbum> = withContext(Dispatchers.IO) {
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val listenBrainzCreds = sessionStore.getListenBrainzCredentials()
        val lastFmTopAlbums = fetchLastFmTopAlbums(lastFmCreds)
        val listenBrainzTopReleases = fetchListenBrainzTopReleases(listenBrainzCreds)
        val combinedTopAlbums = (lastFmTopAlbums + listenBrainzTopReleases)
            .distinctBy { TrackIdentity.keyOf(null, it.title, it.artist) }
            .take(10)
        val session = sessionStore.getSession()
        resolveAlbums(combinedTopAlbums, session?.serverUrl, session?.token)
    }

    suspend fun fetchTopTracks(): List<HomeTrack> = withContext(Dispatchers.IO) {
        val topTracks = getRawTopTracks()
        val rawTopTracks = topTracks.take(10)
        val session = sessionStore.getSession()
        resolveTracks(rawTopTracks, session?.serverUrl, session?.token)
    }

    suspend fun fetchRegionalTrending(region: String? = null): List<HomeTrack> =
        withContext(Dispatchers.IO) {
            val targetRegion = region?.trim()?.ifEmpty { null } ?: getQueryRegion()
            ?: return@withContext emptyList()
            val lastFmCreds = sessionStore.getLastFmCredentials()
            val regionalTrending =
                fetchLastFmRegionalTrendingTracks(lastFmCreds, targetRegion).take(6)
            val session = sessionStore.getSession()
            resolveTracks(regionalTrending, session?.serverUrl, session?.token)
        }

    suspend fun fetchGlobalTrending(): List<HomeTrack> = withContext(Dispatchers.IO) {
        val lastFmCreds = sessionStore.getLastFmCredentials()
        val lastFmTrending = fetchLastFmTrendingTracks(lastFmCreds)
        val listenBrainzTrending = getRawListenBrainzTrending()
        val globalTrending = (lastFmTrending + listenBrainzTrending)
            .distinctBy { TrackIdentity.keyOf(it) }
            .take(6)
        val session = sessionStore.getSession()
        resolveTracks(globalTrending, session?.serverUrl, session?.token)
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
            .take(8)
        val session = sessionStore.getSession()
        resolveTracks(rawWeekly, session?.serverUrl, session?.token)
    }

    private suspend fun resolveTracks(
        tracks: List<HomeTrack>,
        serverUrl: String?,
        token: String?,
    ): List<HomeTrack> = coroutineScope {
        tracks.map { track ->
            async {
                val resolved = artworkResolver.resolveTrackArtwork(
                    serverUrl = serverUrl,
                    token = token,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                )
                track.copy(artworkUrl = resolved ?: track.artworkUrl)
            }
        }.awaitAll()
    }

    private suspend fun resolveAlbums(
        albums: List<HomeAlbum>,
        serverUrl: String?,
        token: String?,
    ): List<HomeAlbum> = coroutineScope {
        albums.map { album ->
            async {
                val resolved = artworkResolver.resolveAlbumArtwork(
                    serverUrl = serverUrl,
                    token = token,
                    title = album.title,
                    artist = album.artist,
                )
                album.copy(artworkUrl = resolved ?: album.artworkUrl)
            }
        }.awaitAll()
    }

    private suspend fun resolveArtists(
        artists: List<HomeArtist>,
        serverUrl: String?,
        token: String?,
    ): List<HomeArtist> = coroutineScope {
        artists.map { artist ->
            async {
                val resolved = artworkResolver.resolveArtistArtwork(
                    serverUrl = serverUrl,
                    token = token,
                    artist = artist.name,
                )
                artist.copy(imageUrl = resolved ?: artist.imageUrl)
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

            result.add(
                HomeTrack(
                    id = "lfm-top-trk-$i",
                    title = name,
                    artist = artist,
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


        val endpoint = "https://api.listenbrainz.org/1/user/$username/listens?count=100"

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
            if (name.isNotEmpty() && artist.isNotEmpty()) {
                result.add(
                    HomeTrack(
                        id = "lfm-rec-$name-$artist",
                        title = name,
                        artist = artist,
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
                if (result.size >= 8) break
            }
            if (result.size >= 8) break
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
        // android.util.Log is a throwing stub under JVM unit tests, so a log line must never be the thing that fails a request.
        runCatching { Log.w(TAG, message) }
    }

    private companion object {
        private val http = HttpJsonClient()
        private val API_KEY_QUERY = Regex("api_key=[^&]*", RegexOption.IGNORE_CASE)
        private const val TAG = "HomeFeed"
    }
}
