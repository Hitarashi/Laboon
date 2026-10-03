package org.shilpo.laboon.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object LyricsTranslator {
    private val client = HttpJsonClient()

    suspend fun translate(
        text: String,
        targetLanguage: String = Locale.getDefault().language.ifBlank { "en" },
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
        val url =
            "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=$targetLanguage&q=$encoded"
        when (val outcome = client.get(url)) {
            is HttpOutcome.Success -> parseTranslationResponse(outcome.value)
            is HttpOutcome.Failure -> null
        }
    }

    suspend fun translateLines(
        lines: List<LyricsLine>,
        targetLanguage: String = Locale.getDefault().language.ifBlank { "en" },
    ): List<LyricsLine> = withContext(Dispatchers.IO) {
        val nonInstrumental = lines.filter { !it.isInstrumental && it.text.isNotBlank() }
        if (nonInstrumental.isEmpty()) return@withContext lines

        val joinedText = nonInstrumental.joinToString("\n") { it.text.trim() }
        val translatedJoined = translate(joinedText, targetLanguage)
        val parsedLines = translatedJoined?.lines()?.map { it.trim() }

        val translationsMap: Map<String, String> =
            if (parsedLines != null && parsedLines.size == nonInstrumental.size) {
                nonInstrumental.indices.associate { i ->
                    nonInstrumental[i].text.trim() to parsedLines[i]
                }
            } else {
                coroutineScope {
                    nonInstrumental.map { line ->
                        async {
                            val text = line.text.trim()
                            val tr = translate(text, targetLanguage)
                            text to (tr ?: "")
                        }
                    }.awaitAll().toMap()
                }
            }

        lines.map { line ->
            val textTrimmed = line.text.trim()
            val translationText = translationsMap[textTrimmed]
            if (!translationText.isNullOrBlank() && translationText != textTrimmed) {
                val filtered = line.translations.filter { it.language != targetLanguage }
                line.copy(
                    translations = filtered + LyricsTranslation(
                        targetLanguage,
                        translationText
                    )
                )
            } else {
                line
            }
        }
    }

    private fun parseTranslationResponse(rawJson: String): String? = runCatching {
        val jsonArray = JSONArray(rawJson)
        val sentences = jsonArray.optJSONArray(0) ?: return@runCatching null
        val builder = StringBuilder()
        for (i in 0 until sentences.length()) {
            val sentence = sentences.optJSONArray(i) ?: continue
            val part = sentence.optString(0)
            if (!part.isNullOrEmpty()) {
                builder.append(part)
            }
        }
        builder.toString().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()
}
