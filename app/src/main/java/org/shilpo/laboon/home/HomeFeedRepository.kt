package org.shilpo.laboon.home

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.LastFmCredentials
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
    private val youtubePlaylistClient: YouTubePlaylistClient = YouTubePlaylistClient(),
) {
    private val youtubePlaylistTrackResolver = YouTubePlaylistTrackResolver(
        songLinkLookup = SongLinkScraper()::findAppleTrack,
        lyricspornLookup = { apiBaseUrl, track ->
            val query = listOf(track.title, track.artist)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .joinToString(" ")
            val candidates = artworkResolver.searchCatalog(
                apiBaseUrl = apiBaseUrl,
                term = query,
                types = "songs",
                limit = 25,
            ).songs
            YouTubePlaylistTrackMatcher.bestCandidate(track, candidates)
        },
    )
    private val recentTracksMutex = Mutex()
    private val accountCacheMutex = Mutex()
    private var rawCacheAccountKey: String? = null
    private var cachedRecentTracks: List<HomeTrack>? = null
    private var cachedRecentTracksAtMs = 0L
    private val playlistClient = UserPlaylistClient(sessionStore)
    private val favoritePlaylistMutex = Mutex()
    private var favoritePlaylistAccountKey: String? = null
    private var favoritePlaylistInitialized = false
    private var cachedFavoritePlaylist: SavedPlaylistDetail? = null
    private val _favoriteTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteTrackIds: StateFlow<Set<String>> = _favoriteTrackIds.asStateFlow()

    suspend fun clearCache() {
        recentTracksMutex.withLock { cachedRecentTracks = null; cachedRecentTracksAtMs = 0L }
        tasteProfileMutex.withLock { tasteProfileCache.clear() }
    }

    /**
     * Reads the account signals used by both home recommendations and continuous queue discovery.
     * Successful account profiles are shared for 15 minutes across repository instances.
     */
    suspend fun fetchTasteProfile(forceRefresh: Boolean = false): UserTasteProfile =
        withContext(Dispatchers.IO) {
            val lastFm = sessionStore.getLastFmCredentials()
            val cacheKey = tasteProfileKey(lastFm)
            tasteProfileMutex.withLock {
                tasteProfileCache.keys.removeIf { it != cacheKey }
                val now = System.currentTimeMillis()
                tasteProfileCache[cacheKey]
                    ?.takeIf { !forceRefresh && now - it.createdAtMs < TASTE_PROFILE_TTL_MS }
                    ?.let { return@withLock it.profile }

                val profile = coroutineScope {
                    val recent = async { getRawRecentTracks(forceRefresh) }
                    val topTracks = async { fetchLastFmTopTracks(lastFm) }
                    val topArtists = async { fetchLastFmTopArtists(lastFm) }
                    val loved = async { fetchLastFmLovedTracks(lastFm) }
                    UserTasteProfile(
                        recentTracks = dedupeRecentTracks(recent.await()),
                        topTracks = topTracks.await(),
                        topArtists = topArtists.await(),
                        lovedTracks = loved.await().distinctBy(TrackIdentity::keyOf),
                    )
                }
                tasteProfileCache[cacheKey] = CachedTasteProfile(now, profile)
                profile
            }
        }

    private suspend fun getRawRecentTracks(forceRefresh: Boolean = false): List<HomeTrack> {
        ensureAccountCacheCurrent()
        return recentTracksMutex.withLock {
            cachedRecentTracks?.takeIf {
                !forceRefresh && System.currentTimeMillis() - cachedRecentTracksAtMs < TASTE_PROFILE_TTL_MS
            }
                ?.let { return@withLock it }
            val lastFmCreds = sessionStore.getLastFmCredentials()
            val recent = dedupeRecentTracks(fetchLastFmRecentTracks(lastFmCreds))
                .take(MAX_RECENT_TRACKS)
            cachedRecentTracks = recent
            cachedRecentTracksAtMs = System.currentTimeMillis()
            recent
        }
    }

    private suspend fun ensureAccountCacheCurrent() {
        val currentKey = tasteProfileKey(
            sessionStore.getLastFmCredentials(),
        )
        accountCacheMutex.withLock {
            if (rawCacheAccountKey == currentKey) return@withLock
            recentTracksMutex.withLock { cachedRecentTracks = null; cachedRecentTracksAtMs = 0L }
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

    suspend fun fetchLibraryRecentTracks(forceRefresh: Boolean = false): List<HomeTrack> =
        withContext(Dispatchers.IO) {
            resolveTracks(getRawRecentTracks(forceRefresh)).take(MAX_RECENT_TRACKS)
        }

    /** Library songs combine the user's recent listening with their most-played tracks. */
    suspend fun fetchLibrarySongs(forceRefresh: Boolean = false): List<HomeTrack> =
        withContext(Dispatchers.IO) {
            val sourceTracks = if (forceRefresh) {
                val lastFm = sessionStore.getLastFmCredentials()
                coroutineScope {
                    listOf(
                        async { fetchLastFmTopTracks(lastFm) },
                        async { fetchLastFmRecentTracks(lastFm) },
                    ).awaitAll().flatten()
                }
            } else {
                val profile = fetchTasteProfile()
                profile.topTracks + profile.recentTracks
            }
            resolveTracks(
                sourceTracks
                    .distinctBy(TrackIdentity::keyOf)
                    .take(MAX_LIBRARY_TRACKS),
            )
    }

    /** Returns user favorite tracks from peerless-server (and Last.fm if connected) resolved to playable catalog entries. */
    suspend fun fetchLibraryLovedTracks(forceRefresh: Boolean = false): List<HomeTrack> =
        withContext(Dispatchers.IO) {
            val serverFavoriteTracks = if (sessionStore.hasSession()) {
                sessionStore.getSession()?.let { session ->
                    val playlistId = "favorites-${session.user.telegramId}"
                    fetchUserPlaylistTracks(playlistId)
                } ?: emptyList()
            } else {
                emptyList()
            }

            val serverTrackIds = serverFavoriteTracks.mapNotNull { it.providerTrackId }.toSet()
            if (serverTrackIds.isNotEmpty()) {
                _favoriteTrackIds.value = serverTrackIds
            }

            val lastFmCreds = sessionStore.getLastFmCredentials()
            val lastFmTracks = if (lastFmCreds != null) {
                val tracks = if (forceRefresh) {
                    fetchLastFmLovedTracks(lastFmCreds)
                } else {
                    fetchTasteProfile().lovedTracks
                }
                resolveTracks(tracks.distinctBy(TrackIdentity::keyOf).take(MAX_LIBRARY_TRACKS))
            } else {
                emptyList()
            }

            val combined = if (serverFavoriteTracks.isNotEmpty()) {
                val existingKeys = serverFavoriteTracks.map(TrackIdentity::keyOf).toSet()
                serverFavoriteTracks + lastFmTracks.filterNot { TrackIdentity.keyOf(it) in existingKeys }
            } else {
                lastFmTracks
            }
            combined.map { it.copy(isFavorite = true) }
        }

    fun canManageUserPlaylists(): Boolean = sessionStore.hasSession()

    suspend fun isUserFavoriteTrack(track: HomeTrack): Boolean? = withContext(Dispatchers.IO) {
        val trackId = track.providerTrackId?.takeIf(String::isNotBlank) ?: return@withContext null
        if (_favoriteTrackIds.value.isNotEmpty()) {
            return@withContext trackId in _favoriteTrackIds.value
        }
        favoritePlaylistMutex.withLock {
            cachedFavoritePlaylist?.let { detail ->
                return@withLock trackId in detail.trackIds
            }
            if (track.isFavorite != null) {
                return@withLock track.isFavorite
            }
            loadFavoritePlaylistDetail(forceRefresh = false)?.let { detail ->
                trackId in detail.trackIds
            }
        }
    }

    suspend fun setUserFavoriteTrack(track: HomeTrack, shouldBeFavorite: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val trackId = track.providerTrackId?.takeIf(String::isNotBlank)
                ?: return@withContext false
            val session = sessionStore.getSession() ?: return@withContext false
            val playlistId = "favorites-${session.user.telegramId}"

            val previousTrackIds = favoritePlaylistMutex.withLock {
                loadFavoritePlaylistDetail(forceRefresh = false)?.trackIds
                    ?: _favoriteTrackIds.value.toList()
            }
            val alreadyFavorite = trackId in previousTrackIds
            if (alreadyFavorite == shouldBeFavorite) return@withContext true

            val updatedTrackIds = if (shouldBeFavorite) {
                previousTrackIds + trackId
            } else {
                previousTrackIds.filterNot { it == trackId }
            }

            // Optimistic update in-memory
            favoritePlaylistMutex.withLock {
                cachedFavoritePlaylist?.let { current ->
                    cachedFavoritePlaylist = current.copy(trackIds = updatedTrackIds)
                }
            }
            _favoriteTrackIds.value = updatedTrackIds.toSet()

            val result = playlistClient.replaceTracks(playlistId, updatedTrackIds)
            if (result.isSuccess) {
                val updated = result.getOrThrow()
                favoritePlaylistMutex.withLock {
                    cachedFavoritePlaylist = updated
                }
                _favoriteTrackIds.value = updated.trackIds.toSet()
                true
            } else {
                // Revert on failure
                logWarning("Could not update favorites: ${result.exceptionOrNull()?.message.orEmpty()}")
                favoritePlaylistMutex.withLock {
                    cachedFavoritePlaylist?.let { current ->
                        cachedFavoritePlaylist = current.copy(trackIds = previousTrackIds)
                    }
                }
                _favoriteTrackIds.value = previousTrackIds.toSet()
                false
            }
        }

    private suspend fun loadFavoritePlaylistDetail(
        forceRefresh: Boolean,
    ): SavedPlaylistDetail? {
        val session = sessionStore.getSession() ?: return null
        val accountKey = "${session.serverUrl.trimEnd('/')}|${session.user.telegramId}"
        if (favoritePlaylistAccountKey != accountKey) {
            favoritePlaylistAccountKey = accountKey
            favoritePlaylistInitialized = false
            cachedFavoritePlaylist = null
        }

        if (!favoritePlaylistInitialized) {
            playlistClient.list().getOrElse { error ->
                logWarning("Could not initialize favorites: ${error.message.orEmpty()}")
                return null
            }
            favoritePlaylistInitialized = true
        }

        if (!forceRefresh) cachedFavoritePlaylist?.let { return it }

        val playlistId = "favorites-${session.user.telegramId}"
        val detail = playlistClient.get(playlistId).getOrElse { error ->
            logWarning("Could not load favorites: ${error.message.orEmpty()}")
            return null
        }
        if (!detail.playlist.isFavorites) {
            logWarning("Favorites playlist response was not marked as favorites")
            return null
        }
        cachedFavoritePlaylist = detail
        _favoriteTrackIds.value = detail.trackIds.toSet()
        return detail
    }

    suspend fun fetchUserPlaylists(): List<SavedPlaylist> {
        val playlists = playlistClient.list().getOrElse { error ->
            logWarning("Could not load playlists: ${error.message.orEmpty()}")
            return emptyList()
        }
        val previewTrackIds = playlists.flatMap(SavedPlaylist::previewTrackIds).distinct()
        val catalogItems = artworkResolver.fetchTrackCatalogItems(
            sessionStore.getSession()?.lyricspornApiUrl,
            previewTrackIds,
        )
        return playlists.map { playlist ->
            playlist.copy(
                previewArtworkByTrackId = playlist.previewTrackIds.mapNotNull { trackId ->
                    catalogItems[trackId]?.artworkUrl?.let { artworkUrl -> trackId to artworkUrl }
                }.toMap(),
            )
        }
    }

    suspend fun fetchUserPlaylistMembership(
        playlistIds: List<String>,
        trackId: String,
    ): Set<String>? = coroutineScope {
        if (trackId.isBlank()) return@coroutineScope emptySet()
        val ids = playlistIds.distinct()
        if (ids.isEmpty()) return@coroutineScope emptySet()

        val details = ids.map { playlistId ->
            async(Dispatchers.IO) { playlistId to playlistClient.get(playlistId) }
        }.awaitAll()
        val failedCount = details.count { (_, detail) -> detail.isFailure }
        if (failedCount > 0) {
            logWarning("Could not check membership in $failedCount playlist(s)")
            return@coroutineScope null
        }
        details.mapNotNullTo(linkedSetOf()) { (playlistId, detail) ->
            playlistId.takeIf { trackId in detail.getOrThrow().trackIds }
        }
    }

    suspend fun createUserPlaylist(title: String): SavedPlaylist? =
        playlistClient.create(title.trim()).getOrElse { error ->
            logWarning("Could not create playlist: ${error.message.orEmpty()}")
            null
        }

    suspend fun mergeUserPlaylists(
        sourcePlaylistIds: List<String>,
        destinationPlaylistId: String?,
        newPlaylistName: String?,
    ): SavedPlaylistDetail? {
        val sourceIds = sourcePlaylistIds.map(String::trim)
        val destinationId = destinationPlaylistId?.trim()?.takeIf(String::isNotEmpty)
        val destinationName = newPlaylistName?.trim()?.takeIf(String::isNotEmpty)

        if (sourceIds.size !in 2..MAX_CUSTOM_USER_PLAYLISTS ||
            sourceIds.any(String::isEmpty) ||
            sourceIds.distinct().size != sourceIds.size
        ) {
            logWarning("Playlist merge needs two to seven distinct source playlists")
            return null
        }
        if ((destinationId == null) == (destinationName == null)) {
            logWarning("Playlist merge needs either a new name or an existing destination")
            return null
        }

        val playlistIdsToRead = (sourceIds + listOfNotNull(destinationId)).distinct()
        val readResults = coroutineScope {
            playlistIdsToRead.map { playlistId ->
                async(Dispatchers.IO) { playlistId to playlistClient.get(playlistId) }
            }.awaitAll()
        }
        val failedRead = readResults.firstOrNull { (_, result) -> result.isFailure }
        if (failedRead != null) {
            logWarning(
                "Could not read playlist ${failedRead.first} for merge: " +
                    failedRead.second.exceptionOrNull()?.message.orEmpty(),
            )
            return null
        }

        val detailsById = readResults.associate { (playlistId, result) ->
            playlistId to result.getOrThrow()
        }
        if (detailsById.values.any { it.playlist.isFavorites }) {
            logWarning("Favorites cannot be merged as a custom playlist")
            return null
        }

        val sourceTrackIds = sourceIds
            .filterNot { it == destinationId }
            .map { playlistId -> detailsById.getValue(playlistId).trackIds }
        val mergedTrackIds = combinePlaylistTrackIds(
            destinationTrackIds = destinationId?.let { detailsById.getValue(it).trackIds },
            sourceTrackIds = sourceTrackIds,
        )
        if (mergedTrackIds.size > MAX_USER_PLAYLIST_TRACKS) {
            logWarning("Merged playlist would exceed $MAX_USER_PLAYLIST_TRACKS tracks")
            return null
        }

        if (destinationId != null) {
            return playlistClient.replaceTracks(destinationId, mergedTrackIds).getOrElse { error ->
                logWarning("Could not save merged playlist: ${error.message.orEmpty()}")
                null
            }
        }

        val createdPlaylist = playlistClient.create(destinationName!!).getOrElse { error ->
            logWarning("Could not create merged playlist: ${error.message.orEmpty()}")
            return null
        }
        return playlistClient.replaceTracks(createdPlaylist.id, mergedTrackIds).getOrElse { error ->
            val cleanup = playlistClient.delete(createdPlaylist.id)
            if (cleanup.isFailure) {
                logWarning("Could not clean up incomplete merged playlist ${createdPlaylist.id}")
            }
            logWarning("Could not save merged playlist: ${error.message.orEmpty()}")
            null
        }
    }

    suspend fun renameUserPlaylist(playlistId: String, title: String): SavedPlaylist? =
        playlistClient.rename(playlistId, title.trim()).getOrElse { error ->
            logWarning("Could not rename playlist: ${error.message.orEmpty()}")
            null
        }

    suspend fun deleteUserPlaylist(playlistId: String): Boolean =
        playlistClient.delete(playlistId).onFailure { error ->
            logWarning("Could not delete playlist: ${error.message.orEmpty()}")
        }.isSuccess

    suspend fun importPublicYouTubePlaylist(url: String): YouTubePlaylistImportResult =
        withContext(Dispatchers.IO) {
            val playlistId = YouTubePlaylistApi.playlistIdFromUrl(url)
                ?: return@withContext YouTubePlaylistImportResult.InvalidYouTubeLink
            val sourcePlaylist = youtubePlaylistClient.fetchPublicPlaylist(url)
                ?: return@withContext YouTubePlaylistImportResult.PlaylistUnavailable
            if (sourcePlaylist.tracks.isEmpty()) {
                return@withContext YouTubePlaylistImportResult.PlaylistHasNoTracks
            }

            val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
            val rawAppleMatchedTracks = coroutineScope {
                sourcePlaylist.tracks.mapIndexed { index, sourceTrack ->
                    async {
                        val match = youtubePlaylistTrackResolver.resolve(apiBaseUrl, sourceTrack)
                            ?: return@async null
                        index to HomeTrack(
                            id = "youtube-import-${sourceTrack.videoId ?: index}",
                            title = match.title,
                            artist = match.artist,
                            album = match.album,
                            artworkUrl = match.artworkUrl,
                            source = "YouTube Music",
                            isrc = match.isrc,
                            providerTrackId = match.appleTrackId,
                            durationMs = match.durationMs ?: sourceTrack.durationMs,
                        )
                    }
                }.awaitAll().filterNotNull()
            }
            val resolvedSourceIndices = rawAppleMatchedTracks.map { it.first }.toSet()
            val skippedTracks = skippedYouTubePlaylistTracks(
                sourceTracks = sourcePlaylist.tracks,
                resolvedSourceIndices = resolvedSourceIndices,
            )
            val trackIds = rawAppleMatchedTracks.mapNotNull { (_, track) ->
                track.providerTrackId?.takeIf(String::isNotBlank)
            }
            if (trackIds.isEmpty()) {
                return@withContext YouTubePlaylistImportResult.Completed(
                    playlist = null,
                    sourceTrackCount = sourcePlaylist.tracks.size,
                    importedTrackCount = 0,
                    skippedTracks = skippedTracks,
                    isTruncated = sourcePlaylist.isTruncated,
                )
            }
            val sourceUrl = "https://music.youtube.com/playlist?list=$playlistId"
            val importedPlaylist = playlistClient.import(sourcePlaylist.title, sourceUrl, trackIds)
                .getOrElse { error ->
                    logWarning("Could not save imported playlist: ${error.message.orEmpty()}")
                    return@withContext YouTubePlaylistImportResult.CouldNotCreatePlaylist
                }
            val previewTrackIds = importedPlaylist.previewTrackIds.ifEmpty { trackIds.take(4) }
            val previewArtworkByTrackId = rawAppleMatchedTracks.mapNotNull { (_, track) ->
                val trackId = track.providerTrackId ?: return@mapNotNull null
                val artworkUrl = track.artworkUrl?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                trackId to artworkUrl
            }.filter { (trackId, _) -> trackId in previewTrackIds }.toMap()
            YouTubePlaylistImportResult.Completed(
                playlist = importedPlaylist.copy(
                    previewTrackIds = previewTrackIds,
                    previewArtworkByTrackId = previewArtworkByTrackId,
                ),
                sourceTrackCount = sourcePlaylist.tracks.size,
                importedTrackCount = trackIds.size,
                skippedTracks = skippedTracks,
                isTruncated = sourcePlaylist.isTruncated,
            )
        }

    suspend fun fetchUserPlaylistTracks(playlistId: String): List<HomeTrack> =
        withContext(Dispatchers.IO) {
            val detail = playlistClient.get(playlistId).getOrElse { error ->
                logWarning("Could not load playlist tracks: ${error.message.orEmpty()}")
                return@withContext emptyList()
            }
            val isFavorites = detail.playlist.isFavorites || playlistId.startsWith("favorites-")
            if (isFavorites) {
                _favoriteTrackIds.value = detail.trackIds.toSet()
            }
            val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl
            val catalogItems = artworkResolver.fetchTrackCatalogItems(apiBaseUrl, detail.trackIds)
            val rawTracks = detail.trackIds.mapIndexedNotNull { index, trackId ->
                val track = catalogItems[trackId]
                HomeTrack(
                    id = "playlist-${detail.playlist.id}-$index",
                    title = track?.name ?: "Track $trackId",
                    artist = track?.artistName?.trim()?.takeIf(String::isNotEmpty).orEmpty(),
                    album = track?.albumName,
                    artworkUrl = track?.artworkUrl,
                    source = "Laboon playlist",
                    providerTrackId = trackId,
                    isrc = track?.isrc,
                    durationMs = track?.durationMs ?: 0L,
                    isFavorite = isFavorites,
                )
            }
            availabilityResolver.enrichAvailability(rawTracks)
        }

    suspend fun addTrackToUserPlaylist(playlistId: String, track: HomeTrack): Boolean {
        val detail = playlistClient.get(playlistId).getOrNull() ?: return false
        val trackId = track.providerTrackId?.takeIf(String::isNotBlank) ?: return false
        if (trackId in detail.trackIds) return true
        val updated = detail.trackIds + trackId
        return playlistClient.replaceTracks(playlistId, updated).isSuccess
    }

    suspend fun setUserPlaylistTrackMembership(
        playlistId: String,
        track: HomeTrack,
        shouldContainTrack: Boolean,
    ): SavedPlaylistDetail? {
        val trackId = track.providerTrackId?.takeIf(String::isNotBlank) ?: return null
        val current = playlistClient.get(playlistId).getOrElse { error ->
            logWarning("Could not load playlist membership: ${error.message.orEmpty()}")
            return null
        }
        val alreadyContainsTrack = trackId in current.trackIds
        if (alreadyContainsTrack == shouldContainTrack) return current

        val updatedTrackIds = if (shouldContainTrack) {
            current.trackIds + trackId
        } else {
            current.trackIds.filterNot { it == trackId }
        }
        return playlistClient.replaceTracks(playlistId, updatedTrackIds).getOrElse { error ->
            logWarning("Could not update playlist membership: ${error.message.orEmpty()}")
            null
        }
    }

    suspend fun replaceUserPlaylistTracks(
        playlistId: String,
        trackIds: List<String>,
    ): SavedPlaylistDetail? = playlistClient.replaceTracks(playlistId, trackIds)
        .getOrElse { error ->
            logWarning("Could not update playlist tracks: ${error.message.orEmpty()}")
            null
        }

    suspend fun removeTrackFromUserPlaylist(playlistId: String, trackId: String): Boolean {
        val detail = playlistClient.get(playlistId).getOrNull() ?: return false
        return playlistClient.replaceTracks(
            playlistId,
            detail.trackIds.filterNot { it == trackId },
        ).isSuccess
    }

    suspend fun fetchRecommended(): List<HomeTrack> = withContext(Dispatchers.IO) {
        val taste = fetchTasteProfile()
        val recent = taste.recentTracks
        val topTracks = if (recent.size < MAX_RECOMMENDATION_SEEDS) taste.topTracks else emptyList()
        val candidates = (recent + topTracks).take(MAX_RECOMMENDATION_SEEDS)
        val recommendations = fetchLastFmSimilarTracks(
            sessionStore.getLastFmCredentials(), candidates, taste.topArtists.firstOrNull()?.name,
        )
        val rotationKeys = recent.map(TrackIdentity::keyOf).toSet()
        resolveTracks(recommendations.distinctBy(TrackIdentity::keyOf)
            .filterNot { TrackIdentity.keyOf(it) in rotationKeys }
            .take(MAX_RECOMMENDATION_CANDIDATES)).take(10)
    }

    suspend fun fetchTopArtists(forceRefresh: Boolean = false): List<HomeArtist> =
        withContext(Dispatchers.IO) {
            val artists = (if (forceRefresh) fetchLastFmTopArtists(sessionStore.getLastFmCredentials())
                else fetchTasteProfile().topArtists)
                .distinctBy { it.name.lowercase().trim() }.take(10)
            resolveArtists(artists)
        }

    suspend fun fetchTopAlbums(): List<HomeAlbum> = withContext(Dispatchers.IO) {
        val albums = fetchLastFmTopAlbums(sessionStore.getLastFmCredentials())
            .distinctBy { TrackIdentity.keyOf(it.title, it.artist) }.take(10)
        resolveAlbums(albums)
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
        resolveTracks(fetchLastFmTrendingTracks(sessionStore.getLastFmCredentials()).take(6))
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







    private suspend fun fetchLastFmLovedTracks(creds: LastFmCredentials?): List<HomeTrack> {
        val username = creds?.username?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        val apiKey = creds.apiKey?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        val body =
            fetchJson(LastFmApi.lovedTracksUrl(username, apiKey, limit = 100)) ?: return emptyList()
        return runCatching { LastFmApi.parseLovedTracks(body.toString()) }
            .getOrElse { emptyList() }
    }



    private fun dedupeRecentTracks(tracks: List<HomeTrack>): List<HomeTrack> {
        val seen = HashSet<String>()
        return tracks.sortedByDescending { it.listenedAtMs ?: Long.MIN_VALUE }
            .filter { seen.add(TrackIdentity.keyOf(it)) }
    }

    private fun tasteProfileKey(lastFm: LastFmCredentials?): String = listOf(
        lastFm?.username.orEmpty(), lastFm?.sessionKey.orEmpty(), lastFm?.apiKey.orEmpty(),
    ).joinToString("|")



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
                apiKey = apiKey,
                limit = 4,
            )
            val similarArtists = executeLastFmArtistList(similarEndpoint)
            for (simArtist in similarArtists) {
                val topTracksEndpoint = LastFmApi.topTracksUrl(
                    simArtist.name,
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



    private suspend fun fetchLastFmTrendingTracks(creds: LastFmCredentials?): List<HomeTrack> {
        val apiKey = creds?.apiKey?.trim()?.ifEmpty { null } ?: return emptyList()
        val endpoint =
            "https://ws.audioscrobbler.com/2.0/?method=chart.gettoptracks&api_key=$apiKey&format=json&limit=10"
        return executeLastFmTrackList(endpoint, "tracks", "track")
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
                apiKey,
                limit = 3,
            )
            val similarArtists = executeLastFmArtistList(similarEndpoint)
            for (simArtist in similarArtists) {
                val topTracksEndpoint = LastFmApi.topTracksUrl(
                    simArtist.name,
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

    private suspend fun fetchJson(url: String): JSONObject? {
        val response = http.getJson(url, mapOf("Accept" to "application/json"))
        return response.fold(
            onSuccess = { it },
            onFailure = { error ->
                logWarning("Feed request failed (${describe(error)}) for ${redactApiKey(url)}: ${error.message}")
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
        private const val LISTEN_BRAINZ_READ_TIMEOUT_MS = 30_000L
        private const val MAX_RECENT_TRACKS = 25
        private const val MAX_RECOMMENDATION_SEEDS = 5
        private const val MAX_RECOMMENDATION_CANDIDATES = 20
        private const val MAX_TOP_TRACK_CANDIDATES = 20
        private const val MAX_LIBRARY_TRACKS = 30
        private const val MAX_CUSTOM_USER_PLAYLISTS = 7
        private const val MAX_USER_PLAYLIST_TRACKS = 1_000
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
