package org.shilpo.laboon.lyrics

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class PersistedLyrics(
    val value: LyricsResult,
    val expiresAt: Long,
)

internal class LyricsDiskCache(
    context: Context,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val directory = File(context.filesDir, "lyrics-cache")

    suspend fun get(key: String): PersistedLyrics? = withContext(Dispatchers.IO) {
        val file = entryFile(key)
        if (!file.isFile) return@withContext null

        try {
            val entry = JSONObject(file.readText())
            val expiresAt = entry.optLong("expiresAt", 0L)
            if (entry.optInt("version", -1) != CACHE_FORMAT_VERSION || expiresAt <= nowMs()) {
                file.delete()
                return@withContext null
            }

            val lyrics = entry.optJSONObject("lyrics")?.toLyricsResult()
            if (lyrics == null) {
                file.delete()
                return@withContext null
            }

            file.setLastModified(nowMs())
            PersistedLyrics(lyrics, expiresAt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    suspend fun put(key: String, value: LyricsResult, expiresAt: Long) =
        withContext(Dispatchers.IO) {
            try {
                if (!directory.isDirectory && !directory.mkdirs()) return@withContext

                val target = entryFile(key)
                val temporary = File.createTempFile("lyrics-", ".tmp", directory)
                try {
                    temporary.writeText(
                        JSONObject()
                            .put("version", CACHE_FORMAT_VERSION)
                            .put("expiresAt", expiresAt)
                            .put("lyrics", value.toJson())
                            .toString(),
                    )
                    if (!temporary.renameTo(target)) {
                        target.delete()
                        check(temporary.renameTo(target))
                    }
                    target.setLastModified(nowMs())
                    trimCache(target)
                } finally {
                    temporary.delete()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {

            }
        }

    private fun entryFile(key: String): File = File(directory, "${key.sha256()}.json")

    private fun trimCache(current: File) {
        val files = directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?: return
        val excess = files.size - MAX_CACHE_ENTRIES
        if (excess <= 0) return

        files.asSequence()
            .filterNot { it.absolutePath == current.absolutePath }
            .sortedBy(File::lastModified)
            .take(excess)
            .forEach(File::delete)
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val CACHE_FORMAT_VERSION = 1
        const val MAX_CACHE_ENTRIES = 256
    }
}

private fun LyricsResult.toJson(): JSONObject = JSONObject().apply {
    put("provider", provider ?: JSONObject.NULL)
    put("attribution", attribution ?: JSONObject.NULL)
    put("format", format.name)
    put("syncLevel", syncLevel.name)
    put("plainText", plainText)
    put("lines", JSONArray().apply { lines.forEach { put(it.toJson()) } })
}

private fun LyricsLine.toJson(): JSONObject = JSONObject().apply {
    put("text", text)
    put("startMs", startMs)
    put("endMs", endMs)
    put("words", JSONArray().apply { words.forEach { put(it.toJson()) } })
    put("backgroundWords", JSONArray().apply { backgroundWords.forEach { put(it.toJson()) } })
    put("alignment", alignment ?: JSONObject.NULL)
    put("agent", agent ?: JSONObject.NULL)
    put("singer", singer ?: JSONObject.NULL)
    put("translations", JSONArray().apply { translations.forEach { put(it.toJson()) } })
    put("romanization", romanization ?: JSONObject.NULL)
    put("isInstrumental", isInstrumental)
}

private fun LyricsWord.toJson(): JSONObject = JSONObject().apply {
    put("text", text)
    put("startMs", startMs)
    put("endMs", endMs ?: JSONObject.NULL)
}

private fun LyricsTranslation.toJson(): JSONObject = JSONObject().apply {
    put("language", language)
    put("text", text)
}

private fun JSONObject.toLyricsResult(): LyricsResult? {
    val format =
        runCatching { LyricsFormat.valueOf(optString("format")) }.getOrNull() ?: return null
    val syncLevel = runCatching { LyricsSyncLevel.valueOf(optString("syncLevel")) }.getOrNull()
        ?: return null
    return LyricsResult(
        provider = nullableString("provider"),
        attribution = nullableString("attribution"),
        format = format,
        syncLevel = syncLevel,
        plainText = optString("plainText"),
        lines = optJSONArray("lines")?.toLyricsLines().orEmpty(),
    )
}

private fun JSONArray.toLyricsLines(): List<LyricsLine> = buildList {
    for (index in 0 until length()) {
        val line = optJSONObject(index) ?: continue
        add(
            LyricsLine(
                text = line.optString("text"),
                startMs = line.optLong("startMs"),
                endMs = line.optLong("endMs"),
                words = line.optJSONArray("words")?.toLyricsWords().orEmpty(),
                backgroundWords = line.optJSONArray("backgroundWords")?.toLyricsWords().orEmpty(),
                alignment = line.nullableString("alignment"),
                agent = line.nullableString("agent"),
                singer = line.nullableString("singer"),
                translations = line.optJSONArray("translations")?.toLyricsTranslations().orEmpty(),
                romanization = line.nullableString("romanization"),
                isInstrumental = line.optBoolean("isInstrumental"),
            ),
        )
    }
}

private fun JSONArray.toLyricsWords(): List<LyricsWord> = buildList {
    for (index in 0 until length()) {
        val word = optJSONObject(index) ?: continue
        add(
            LyricsWord(
                text = word.optString("text"),
                startMs = word.optLong("startMs"),
                endMs = if (word.isNull("endMs")) null else word.optLong("endMs"),
            ),
        )
    }
}

private fun JSONArray.toLyricsTranslations(): List<LyricsTranslation> = buildList {
    for (index in 0 until length()) {
        val translation = optJSONObject(index) ?: continue
        add(
            LyricsTranslation(
                language = translation.optString("language"),
                text = translation.optString("text"),
            ),
        )
    }
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeUnless { it == "null" }
