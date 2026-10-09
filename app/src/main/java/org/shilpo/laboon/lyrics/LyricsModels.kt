package org.shilpo.laboon.lyrics

data class LyricsLookup(
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationSeconds: Long? = null,
    val appleTrackId: String? = null,
    val youtubeVideoId: String? = null,
) {
    val artistString: String get() = artists.joinToString(", ")
}

data class LyricsWord(
    val text: String,
    val startMs: Long,
    val endMs: Long? = null,
)

data class LyricsTranslation(
    val language: String,
    val text: String,
)

data class LyricsLine(
    val text: String,
    val startMs: Long,
    val endMs: Long = 0L,
    val words: List<LyricsWord> = emptyList(),
    val backgroundWords: List<LyricsWord> = emptyList(),
    val alignment: String? = null,
    val agent: String? = null,
    val singer: String? = null,
    val translations: List<LyricsTranslation> = emptyList(),
    val romanization: String? = null,
    val isInstrumental: Boolean = false,
)

data class LyricsResult(
    val provider: String?,
    val attribution: String? = null,
    val format: LyricsFormat,
    val syncLevel: LyricsSyncLevel,
    val plainText: String,
    val lines: List<LyricsLine>,
)

sealed interface LyricsLookupResult {
    data class Found(val lyrics: LyricsResult) : LyricsLookupResult
    data object NotFound : LyricsLookupResult
    data object Failed : LyricsLookupResult
}

enum class LyricsFormat { Plain, Lrc, Elrc }
enum class LyricsSyncLevel { Word, Line, Plain }

interface LyricsRepository {
    suspend fun lookup(track: LyricsLookup): LyricsLookupResult
}
