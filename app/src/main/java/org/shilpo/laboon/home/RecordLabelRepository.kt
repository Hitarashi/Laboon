package org.shilpo.laboon.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.lyricsporn.LyricspornClient
import org.shilpo.laboon.lyricsporn.LyricspornRecordLabel
import org.shilpo.laboon.net.HttpOutcome
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class RecordLabelRepository(
    private val sessionStore: SessionStore,
    private val persistentCache: RecordLabelCache? = RecordLabelCache(),
    private val client: LyricspornClient = LyricspornClient,
) {
    private val labelCache = ConcurrentHashMap<String, LyricspornRecordLabel>()
    private val nameToIdCache = ConcurrentHashMap<String, String>()

    private val artistCache = ConcurrentHashMap<String, HomeArtist>()

    suspend fun resolveRecordLabelId(labelName: String): String? = withContext(Dispatchers.IO) {
        val normalized = labelName.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return@withContext null
        nameToIdCache[normalized]?.let { return@withContext it }
        persistentCache?.loadResolvedId(normalized)?.let {
            nameToIdCache[normalized] = it
            return@withContext it
        }

        val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl ?: return@withContext null
        val resolvedId = client.resolveRecordLabelId(apiBaseUrl, labelName.trim())
        if (resolvedId != null) {
            nameToIdCache[normalized] = resolvedId
            persistentCache?.saveResolvedId(normalized, resolvedId)
        }
        resolvedId
    }

    fun getCachedRecordLabel(labelIdOrName: String): LyricspornRecordLabel? {
        val trimmed = labelIdOrName.trim()
        if (trimmed.isEmpty()) return null
        val cacheKey = recordLabelCacheKey(trimmed)
        labelCache[cacheKey]?.let { return it }
        persistentCache?.load(cacheKey)?.let {
            labelCache[cacheKey] = it
            return it
        }
        if (trimmed.startsWith("apple_")) {
            val unprefixed = trimmed.removePrefix("apple_")
            val unprefixedKey = recordLabelCacheKey(unprefixed)
            labelCache[unprefixedKey]?.let { return it }
            persistentCache?.load(unprefixedKey)?.let {
                labelCache[unprefixedKey] = it
                return it
            }
        }
        return null
    }

    suspend fun getRecordLabel(
        labelIdOrName: String,
        forceRefresh: Boolean = false,
    ): LyricspornRecordLabel? = withContext(Dispatchers.IO) {
        val trimmed = labelIdOrName.trim()
        if (trimmed.isEmpty()) return@withContext null

        if (!forceRefresh) {
            getCachedRecordLabel(trimmed)?.let { return@withContext it }
        }

        val apiBaseUrl = sessionStore.getSession()?.lyricspornApiUrl ?: return@withContext null

        if (isNumericAppleId(trimmed)) {
            val appleLabelId = trimmed.removePrefix("apple_")
            val outcome = client.getRecordLabel(apiBaseUrl, appleLabelId)
            if (outcome is HttpOutcome.Success) {
                val label = enrichArtists(apiBaseUrl, outcome.value)
                cacheLabel(trimmed, label)
                if (trimmed != appleLabelId) {
                    cacheLabel(appleLabelId, label)
                }
                return@withContext label
            }
            return@withContext null
        }

        // It's a name
        val resolvedId = resolveRecordLabelId(trimmed)
        if (resolvedId != null) {
            if (!forceRefresh) {
                getCachedRecordLabel(resolvedId)?.let { cached ->
                    cacheLabel(trimmed, cached)
                    return@withContext cached
                }
            }
            val outcome = client.getRecordLabel(apiBaseUrl, resolvedId)
            if (outcome is HttpOutcome.Success) {
                val label = enrichArtists(apiBaseUrl, outcome.value)
                cacheLabel(resolvedId, label)
                cacheLabel(trimmed, label)
                return@withContext label
            }
        }

        // If ID resolution returns null, creates a fallback LyricspornRecordLabel(id = "fallback", name = labelIdOrName)
        // and queries search catalog for albums under that label or name.
        val searchResults = client.searchCatalog(apiBaseUrl, trimmed, types = "albums", limit = 25)
        val albums = searchResults.albums.map { item ->
            HomeAlbum(
                id = "apple_${item.id}",
                title = item.name,
                artist = item.artistName.orEmpty(),
                artworkUrl = item.artworkUrl,
                appleCatalogId = item.id,
            )
        }
        val fallbackLabel = LyricspornRecordLabel(
            id = "fallback",
            name = trimmed,
            latestReleases = albums,
            topReleases = albums,
        )
        val enrichedFallback = enrichArtists(apiBaseUrl, fallbackLabel)
        cacheLabel(trimmed, enrichedFallback)
        enrichedFallback
    }

    private suspend fun enrichArtists(
        apiBaseUrl: String,
        label: LyricspornRecordLabel,
    ): LyricspornRecordLabel {
        if (label.artists.isNotEmpty()) return label
        val allReleases = (label.latestReleases + label.topReleases).distinctBy { it.id }
        if (allReleases.isEmpty()) return label

        val artistSeparators = Regex("[,/&;、]|(?i)\\b(feat\\.?|ft\\.|with)\\b")
        val uniqueArtistNames = allReleases.flatMap { album ->
            album.artist.split(artistSeparators)
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.equals("Various Artists", ignoreCase = true) }
        }.distinctBy { it.lowercase(Locale.ROOT) }

        if (uniqueArtistNames.isEmpty()) return label

        val topArtistNames = uniqueArtistNames.take(20)
        val resolvedArtists = coroutineScope {
            topArtistNames.map { artistName ->
                async {
                    val normalizedKey = artistName.lowercase(Locale.ROOT)
                    artistCache[normalizedKey]?.let { return@async it }

                    val catalogItem = runCatching {
                        client.resolveArtistCatalogItem(apiBaseUrl, artistName)
                    }.getOrNull()

                    val artist = if (catalogItem != null) {
                        HomeArtist(
                            id = "apple_${catalogItem.id}",
                            name = catalogItem.name.ifBlank { artistName },
                            imageUrl = catalogItem.artworkUrl,
                            appleCatalogId = catalogItem.id,
                        )
                    } else {
                        HomeArtist(
                            id = "label_artist_${normalizedKey.replace(Regex("[^a-z0-9]+"), "_")}",
                            name = artistName,
                            imageUrl = null,
                            appleCatalogId = null,
                        )
                    }
                    artistCache[normalizedKey] = artist
                    artist
                }
            }.awaitAll()
        }

        return label.copy(artists = resolvedArtists)
    }

    private fun cacheLabel(key: String, label: LyricspornRecordLabel) {
        val cacheKey = recordLabelCacheKey(key)
        labelCache[cacheKey] = label
        persistentCache?.save(cacheKey, label)
    }

    private fun isNumericAppleId(value: String): Boolean {
        val clean = value.removePrefix("apple_")
        return clean.isNotEmpty() && clean.all(Char::isDigit)
    }

    private fun recordLabelCacheKey(key: String): String {
        val session = sessionStore.getSession()
        return listOf(
            session?.serverUrl.orEmpty(),
            session?.user?.telegramId?.toString().orEmpty(),
            session?.lyricspornApiUrl.orEmpty(),
            LyricspornClient.currentStorefront(),
            key.trim().lowercase(Locale.ROOT),
        ).joinToString(":")
    }
}
