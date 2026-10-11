package org.shilpo.laboon.scrobble

import org.json.JSONObject
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.net.HttpError
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import java.util.UUID

data class QueuedScrobble(
    val id: String = UUID.randomUUID().toString(),
    val artist: String,
    val track: String,
    val album: String? = null,
    val timestampEpochSeconds: Long,
    val durationSeconds: Long? = null,
)

data class ScrobbleResult(
    val accepted: Boolean,
    val ignoredMessage: String? = null,
)

open class LastFmScrobbleClient(
    private val httpJsonClient: HttpJsonClient = HttpJsonClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    open suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String? = null,
        durationSeconds: Long? = null,
        credentials: LastFmCredentials,
    ): HttpOutcome<Boolean> {
        val apiKey = credentials.apiKey.orEmpty()
        val sessionKey = credentials.sessionKey.orEmpty()
        val apiSecret = credentials.apiSecret.orEmpty()

        val params = mutableMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to artist,
            "track" to track,
            "api_key" to apiKey,
            "sk" to sessionKey,
        )
        if (!album.isNullOrBlank()) {
            params["album"] = album
        }
        if (durationSeconds != null && durationSeconds > 0) {
            params["duration"] = durationSeconds.toString()
        }

        val sig = LastFmSigner.sign(params, apiSecret)
        val form = params.toMutableMap().apply {
            put("api_sig", sig)
            put("format", "json")
        }

        return when (val outcome = httpJsonClient.postForm(baseUrl, form)) {
            is HttpOutcome.Failure -> outcome
            is HttpOutcome.Success -> parseNowPlayingResponse(outcome.value)
        }
    }

    open suspend fun scrobble(
        items: List<QueuedScrobble>,
        credentials: LastFmCredentials,
    ): HttpOutcome<List<ScrobbleResult>> {
        if (items.isEmpty()) {
            return HttpOutcome.Success(emptyList())
        }

        val batch = items.take(MAX_BATCH_SIZE)
        val apiKey = credentials.apiKey.orEmpty()
        val sessionKey = credentials.sessionKey.orEmpty()
        val apiSecret = credentials.apiSecret.orEmpty()

        val params = mutableMapOf(
            "method" to "track.scrobble",
            "api_key" to apiKey,
            "sk" to sessionKey,
        )

        for (i in batch.indices) {
            val item = batch[i]
            params["artist[$i]"] = item.artist
            params["track[$i]"] = item.track
            params["timestamp[$i]"] = item.timestampEpochSeconds.toString()
            if (!item.album.isNullOrBlank()) {
                params["album[$i]"] = item.album
            }
            if (item.durationSeconds != null && item.durationSeconds > 0) {
                params["duration[$i]"] = item.durationSeconds.toString()
            }
        }

        val sig = LastFmSigner.sign(params, apiSecret)
        val form = params.toMutableMap().apply {
            put("api_sig", sig)
            put("format", "json")
        }

        return when (val outcome = httpJsonClient.postForm(baseUrl, form)) {
            is HttpOutcome.Failure -> outcome
            is HttpOutcome.Success -> parseScrobbleResponse(outcome.value, batch.size)
        }
    }

    private fun parseNowPlayingResponse(body: String): HttpOutcome<Boolean> {
        return try {
            val json = JSONObject(body)
            if (json.has("error")) {
                val errorCode = json.optInt("error", -1)
                val message = json.optString("message", "Last.fm error $errorCode")
                HttpOutcome.Failure(
                    HttpError(
                        kind = HttpErrorKind.STATUS,
                        statusCode = errorCode,
                        message = message,
                    )
                )
            } else {
                HttpOutcome.Success(true)
            }
        } catch (e: Exception) {
            HttpOutcome.Failure(
                HttpError(
                    kind = HttpErrorKind.MALFORMED,
                    message = "Failed to parse Last.fm now playing response: ${e.message}",
                    cause = e,
                )
            )
        }
    }

    private fun parseScrobbleResponse(
        body: String,
        expectedCount: Int,
    ): HttpOutcome<List<ScrobbleResult>> {
        return try {
            val json = JSONObject(body)
            if (json.has("error")) {
                val errorCode = json.optInt("error", -1)
                val message = json.optString("message", "Last.fm error $errorCode")
                return HttpOutcome.Failure(
                    HttpError(
                        kind = HttpErrorKind.STATUS,
                        statusCode = errorCode,
                        message = message,
                    )
                )
            }

            val scrobblesObj = json.optJSONObject("scrobbles")
            if (scrobblesObj == null) {
                return HttpOutcome.Failure(
                    HttpError(
                        kind = HttpErrorKind.MALFORMED,
                        message = "Missing 'scrobbles' object in response: $body",
                    )
                )
            }

            val results = mutableListOf<ScrobbleResult>()
            val scrobbleArray = scrobblesObj.optJSONArray("scrobble")
            val scrobbleSingle = scrobblesObj.optJSONObject("scrobble")

            if (scrobbleArray != null) {
                for (i in 0 until scrobbleArray.length()) {
                    results.add(parseSingleScrobble(scrobbleArray.optJSONObject(i)))
                }
            } else if (scrobbleSingle != null) {
                results.add(parseSingleScrobble(scrobbleSingle))
            } else {
                val attr = scrobblesObj.optJSONObject("@attr")
                val accepted = attr?.optInt("accepted", 0) ?: 0
                val ignored = attr?.optInt("ignored", 0) ?: 0
                for (i in 0 until accepted) {
                    results.add(ScrobbleResult(accepted = true))
                }
                for (i in 0 until ignored) {
                    results.add(ScrobbleResult(accepted = false, ignoredMessage = "Ignored by Last.fm"))
                }
                if (results.isEmpty()) {
                    for (i in 0 until expectedCount) {
                        results.add(ScrobbleResult(accepted = true))
                    }
                }
            }

            HttpOutcome.Success(results)
        } catch (e: Exception) {
            HttpOutcome.Failure(
                HttpError(
                    kind = HttpErrorKind.MALFORMED,
                    message = "Failed to parse Last.fm scrobble response: ${e.message}",
                    cause = e,
                )
            )
        }
    }

    private fun parseSingleScrobble(obj: JSONObject?): ScrobbleResult {
        if (obj == null) return ScrobbleResult(accepted = true)
        val ignoredMessageObj = obj.optJSONObject("ignoredMessage")
        val code = ignoredMessageObj?.optString("code", "0") ?: "0"
        val text = ignoredMessageObj?.optString("#text").orEmpty()
        val isAccepted = (code == "0" || code.isEmpty()) && text.isBlank()
        return ScrobbleResult(
            accepted = isAccepted,
            ignoredMessage = if (!isAccepted) (text.ifBlank { "Ignored (code $code)" }) else null,
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://ws.audioscrobbler.com/2.0/"
        const val MAX_BATCH_SIZE = 50
    }
}
