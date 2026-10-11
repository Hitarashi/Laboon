package org.shilpo.laboon.home

import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem
import java.util.Locale
import kotlin.math.max

internal data class YouTubePlaylistCandidateMatch(
    val item: LyricspornCatalogItem,
    val confidence: Double,
)

internal object YouTubePlaylistTrackMatcher {
    const val MIN_CONFIDENCE = 0.83
    private const val MIN_TITLE_SIMILARITY = 0.82
    private const val MIN_ARTIST_SIMILARITY = 0.72
    private const val AMBIGUOUS_SCORE_MARGIN = 0.025
    private const val SAME_RECORDING_DURATION_DELTA_MS = 1_500L

    private val featureSuffix = Regex("\\b(?:feat(?:uring)?|ft)\\.?\\s+.+$", RegexOption.IGNORE_CASE)
    private val versionMarkers = listOf(
        "live",
        "remix",
        "acoustic",
        "instrumental",
        "karaoke",
        "cover",
        "demo",
        "sped up",
        "slowed",
        "nightcore",
        "piano version",
        "radio edit",
        "extended mix",
        "reverb",
    )

    fun bestCandidate(
        source: YouTubePlaylistTrack,
        candidates: List<LyricspornCatalogItem>,
    ): YouTubePlaylistCandidateMatch? {
        val sourceTitle = comparableTitle(source.title)
        if (sourceTitle.isBlank() || normalize(source.artist).isBlank()) return null

        val ranked = candidates
            .asSequence()
            .filter { it.type == "song" && it.id.isNumericAppleId() }
            .distinctBy(LyricspornCatalogItem::id)
            .mapNotNull { candidate -> score(source, sourceTitle, candidate) }
            .sortedByDescending(ScoredCandidate::confidence)
            .toList()
        val best = ranked.firstOrNull() ?: return null
        if (best.confidence < MIN_CONFIDENCE) return null

        val runnerUp = ranked.getOrNull(1)
        if (runnerUp != null &&
            best.confidence - runnerUp.confidence < AMBIGUOUS_SCORE_MARGIN &&
            materiallyDifferent(best, runnerUp)
        ) {
            return null
        }
        return YouTubePlaylistCandidateMatch(best.item, best.confidence)
    }

    fun isLikelySameRecording(
        sourceTitle: String,
        sourceArtist: String,
        candidateTitle: String,
        candidateArtist: String,
    ): Boolean {
        val source = comparableTitle(sourceTitle)
        val candidate = comparableTitle(candidateTitle)
        if (source.isBlank() || candidate.isBlank()) return false
        if (!sameCreativeVersion(sourceTitle, candidateTitle)) return false

        val titleLooksSame = similarity(source, candidate) >= MIN_TEXT_TITLE_SIMILARITY ||
                (minOf(source.length, candidate.length) >= MIN_LONG_TITLE_LENGTH &&
                        characterSimilarity(source, candidate) >= MIN_LONG_TITLE_CHARACTER_SIMILARITY)
        return titleLooksSame &&
                artistSimilarity(sourceArtist, candidateArtist) >= MIN_TEXT_ARTIST_SIMILARITY
    }

    private fun score(
        source: YouTubePlaylistTrack,
        sourceTitle: String,
        candidate: LyricspornCatalogItem,
    ): ScoredCandidate? {
        val candidateTitle = comparableTitle(candidate.name)
        val candidateArtist = candidate.artistName.orEmpty()
        if (candidateTitle.isBlank() || candidateArtist.isBlank()) return null
        if (!sameCreativeVersion(source.title, candidate.name)) return null

        val titleSimilarity = similarity(sourceTitle, candidateTitle)
        val artistSimilarity = artistSimilarity(source.artist, candidateArtist)
        if (titleSimilarity < MIN_TITLE_SIMILARITY || artistSimilarity < MIN_ARTIST_SIMILARITY) {
            return null
        }
        if (!YouTubePlaylistApi.durationsAreCompatible(source.durationMs, candidate.durationMs)) {
            return null
        }

        val durationScore = durationScore(source.durationMs, candidate.durationMs)
        val albumScore = albumScore(source.album, candidate.albumName)
        val confidence = 0.52 * titleSimilarity +
                0.33 * artistSimilarity +
                0.12 * durationScore +
                0.03 * albumScore
        return ScoredCandidate(
            item = candidate,
            confidence = confidence,
            normalizedTitle = candidateTitle,
            primaryArtist = primaryArtist(candidateArtist),
            durationMs = candidate.durationMs,
        )
    }

    private fun materiallyDifferent(first: ScoredCandidate, second: ScoredCandidate): Boolean =
        first.normalizedTitle != second.normalizedTitle ||
                first.primaryArtist != second.primaryArtist ||
                (first.durationMs != null && second.durationMs != null &&
                        kotlin.math.abs(first.durationMs - second.durationMs) > SAME_RECORDING_DURATION_DELTA_MS)

    private fun sameCreativeVersion(sourceTitle: String, candidateTitle: String): Boolean {
        val sourceMarkers = creativeVersionMarkers(sourceTitle)
        val candidateMarkers = creativeVersionMarkers(candidateTitle)
        return sourceMarkers == candidateMarkers
    }

    private fun creativeVersionMarkers(value: String): Set<String> {
        val normalized = normalize(value)
        return versionMarkers.filter { marker ->
            Regex("(?:^| )${Regex.escape(marker)}(?: |$)").containsMatchIn(normalized)
        }.toSet()
    }

    private fun artistSimilarity(source: String, candidate: String): Double {
        val normalizedSource = normalize(source)
        val normalizedCandidate = normalize(candidate)
        if (normalizedSource == normalizedCandidate) return 1.0
        val sourcePrimary = primaryArtist(source)
        val candidatePrimary = primaryArtist(candidate)
        if (sourcePrimary.isNotBlank() && sourcePrimary == candidatePrimary) return 0.92
        return similarity(normalizedSource, normalizedCandidate)
    }

    private fun primaryArtist(artist: String): String = artist
        .split(Regex("\\b(?:feat(?:uring)?|ft)\\.?\\b|\\s+(?:&|and|x)\\s+|,|;"))
        .firstOrNull()
        .orEmpty()
        .let(::normalize)

    private fun albumScore(sourceAlbum: String?, candidateAlbum: String?): Double {
        val source = normalize(sourceAlbum.orEmpty())
        val candidate = normalize(candidateAlbum.orEmpty())
        if (source.isBlank() || candidate.isBlank()) return 0.5
        if (source == candidate) return 1.0
        return if (source in candidate || candidate in source) 0.7 else 0.0
    }

    private fun durationScore(sourceMs: Long?, candidateMs: Long?): Double {
        if (sourceMs == null || candidateMs == null) return 0.6
        val difference = kotlin.math.abs(sourceMs - candidateMs)
        return when {
            difference <= 1_000L -> 1.0
            difference <= 3_000L -> 0.92
            else -> 0.82
        }
    }

    private fun similarity(first: String, second: String): Double {
        if (first == second) return 1.0
        if (first.isBlank() || second.isBlank()) return 0.0
        val firstTokens = first.split(' ').filter(String::isNotBlank).toSet()
        val secondTokens = second.split(' ').filter(String::isNotBlank).toSet()
        val intersection = firstTokens.intersect(secondTokens).size.toDouble()
        val tokenDice = if (firstTokens.isEmpty() || secondTokens.isEmpty()) 0.0 else {
            2.0 * intersection / (firstTokens.size + secondTokens.size)
        }
        val editSimilarity = characterSimilarity(first, second)
        return 0.6 * tokenDice + 0.4 * editSimilarity
    }

    private fun characterSimilarity(first: String, second: String): Double =
        1.0 - levenshteinDistance(first, second).toDouble() /
                max(first.length, second.length).coerceAtLeast(1)

    private fun levenshteinDistance(first: String, second: String): Int {
        var previous = IntArray(second.length + 1) { it }
        for (firstIndex in first.indices) {
            val current = IntArray(second.length + 1)
            current[0] = firstIndex + 1
            for (secondIndex in second.indices) {
                val substitutionCost = if (first[firstIndex] == second[secondIndex]) 0 else 1
                current[secondIndex + 1] = minOf(
                    current[secondIndex] + 1,
                    previous[secondIndex + 1] + 1,
                    previous[secondIndex] + substitutionCost,
                )
            }
            previous = current
        }
        return previous[second.length]
    }

    private fun comparableTitle(value: String): String = normalize(value.replace(featureSuffix, ""))

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun String.isNumericAppleId(): Boolean = isNotBlank() && all(Char::isDigit)

    private const val MIN_TEXT_TITLE_SIMILARITY = 0.82
    private const val MIN_LONG_TITLE_LENGTH = 12
    private const val MIN_LONG_TITLE_CHARACTER_SIMILARITY = 0.90
    private const val MIN_TEXT_ARTIST_SIMILARITY = 0.72

    private data class ScoredCandidate(
        val item: LyricspornCatalogItem,
        val confidence: Double,
        val normalizedTitle: String,
        val primaryArtist: String,
        val durationMs: Long?,
    )
}
