package org.shilpo.laboon.auth

import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.shilpo.laboon.net.HttpError
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class UnauthorizedException(message: String = "Session expired or revoked") : Exception(message)

class AuthClient(
    private val sessionStore: SessionStore,
) {

    fun parseConnectionUri(uri: Uri): AuthConnectionPayload? = parseConnectionUri(uri.toString())

    fun parseConnectionUri(uri: String): AuthConnectionPayload? {
        val parameters = queryParameters(uri)

        val dataParam = parameters["data"]
        if (!dataParam.isNullOrBlank()) {
            parsePayloadData(dataParam)?.let { return it }
        }

        val codeParam = parameters["code"] ?: parameters["c"]
        val serverParam = parameters["server"] ?: parameters["s"]

        if (!codeParam.isNullOrBlank() && !serverParam.isNullOrBlank()) {
            return AuthConnectionPayload(
                serverUrl = serverParam.trimEnd('/'),
                code = codeParam.trim(),
            )
        }

        return null
    }

    fun parsePayloadData(raw: String): AuthConnectionPayload? {
        val decodedBytes = runCatching {
            java.util.Base64.getDecoder().decode(raw.trim())
        }.getOrNull() ?: return null

        val jsonString = String(decodedBytes, StandardCharsets.UTF_8)
        val server = extractJsonField(jsonString, "s") ?: return null
        val code = extractJsonField(jsonString, "c") ?: return null
        return AuthConnectionPayload(
            serverUrl = server.trimEnd('/'),
            code = code.trim(),
        )
    }

    private fun queryParameters(uri: String): Map<String, String> {
        val query = uri.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        if (query.isEmpty()) return emptyMap()
        val parameters = LinkedHashMap<String, String>()
        query.split('&').forEach { parameter ->
            val name = parameter.substringBefore('=')
            if (name.isEmpty()) return@forEach
            parameters.getOrPut(percentDecode(name)) {
                percentDecode(parameter.substringAfter('=', missingDelimiterValue = ""))
            }
        }
        return parameters
    }

    private fun percentDecode(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        value
    }

    private fun extractJsonField(json: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        val match = regex.find(json)?.groupValues?.get(1)?.trim()
        if (!match.isNullOrBlank()) return match
        return try {
            val obj = JSONObject(json)
            obj.optString(key).takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun exchangeCode(serverUrl: String, code: String): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            val cleanUrl = serverUrl.trimEnd('/')
            val endpoint = "$cleanUrl/api/v1/auth/exchange"
            val requestJson = JSONObject().apply {
                put("code", code)
                put("device_name", deviceName())
                put("platform", "android")
            }

            val response = when (val outcome = postJson(endpoint, requestJson)) {
                is HttpOutcome.Failure -> {
                    logFailure("code exchange", endpoint, outcome.error)
                    return@withContext Result.failure(
                        Exception("${EXCHANGE_FAILED} (${statusLabel(outcome.error)})")
                    )
                }

                is HttpOutcome.Success -> outcome.value
            }

            if (response.statusCode !in 200..299) {
                logStatusFailure("code exchange", endpoint, response.statusCode)
                return@withContext Result.failure(
                    Exception(friendlyMessage(response.body, EXCHANGE_FAILED, response.statusCode))
                )
            }

            try {
                val responseJson = JSONObject(response.body)
                val token = responseJson.getString("token")
                val refreshToken = responseJson.optString("refresh_token", token)
                val expiresAtUnix = responseJson.optLong("expires_at_unix", 0L)

                val userJson = responseJson.getJSONObject("user")
                val user = AuthUser(
                    telegramId = userJson.getLong("telegram_id"),
                    name = userJson.optString("name").normalizedText(),
                    username = userJson.optString("username").normalizedText(),
                    firstName = userJson.optString("first_name").normalizedText(),
                    lastName = userJson.optString("last_name").normalizedText(),
                )

                val session = AuthSession(
                    serverUrl = cleanUrl,
                    token = token,
                    refreshToken = refreshToken,
                    expiresAtUnix = expiresAtUnix,
                    user = user,
                ).normalized()

                sessionStore.saveSession(session)
                Result.success(session)
            } catch (e: Exception) {
                logFailure("code exchange", endpoint, malformed(endpoint, e))
                Result.failure(e)
            }
        }

    suspend fun validateSession(serverUrl: String, token: String): Result<AuthUser> =
        withContext(Dispatchers.IO) {
            val cleanUrl = serverUrl.trimEnd('/')
            val endpoint = "$cleanUrl/api/v1/auth/me"

            val json = when (val outcome = http.getJson(endpoint, bearerHeaders(token))) {
                is HttpOutcome.Failure -> {
                    logFailure("session check", endpoint, outcome.error)
                    if (outcome.error.statusCode == HTTP_UNAUTHORIZED) {
                        sessionStore.signOut()
                        return@withContext Result.failure(UnauthorizedException())
                    }
                    return@withContext Result.failure(
                        Exception("Session check failed (${statusLabel(outcome.error)})")
                    )
                }

                is HttpOutcome.Success -> outcome.value
            }

            try {
                val userJson = json.optJSONObject("user")
                val user = if (userJson != null) {
                    AuthUser(
                        telegramId = userJson.getLong("telegram_id"),
                        name = userJson.optString("name").normalizedText(),
                        username = userJson.optString("username").normalizedText(),
                        firstName = userJson.optString("first_name").normalizedText(),
                        lastName = userJson.optString("last_name").normalizedText(),
                    )
                } else {
                    sessionStore.getSession()?.user ?: return@withContext Result.failure(
                        Exception("Missing user object in response")
                    )
                }

                Result.success(user)
            } catch (e: Exception) {
                logFailure("session check", endpoint, malformed(endpoint, e))
                Result.failure(e)
            }
        }

    suspend fun fetchLastFmStatus(serverUrl: String, token: String): Result<LastFmCredentials> =
        withContext(Dispatchers.IO) {
            val cleanUrl = serverUrl.trimEnd('/')
            val endpoint = "$cleanUrl/api/v1/integrations/lastfm/status"

            val json = when (val outcome = http.getJson(endpoint, bearerHeaders(token))) {
                is HttpOutcome.Failure -> {
                    logFailure("Last.fm status", endpoint, outcome.error)
                    if (outcome.error.statusCode == HTTP_UNAUTHORIZED) {
                        sessionStore.signOut()
                        return@withContext Result.failure(UnauthorizedException())
                    }
                    return@withContext Result.failure(
                        Exception("Failed to check Last.fm status: ${statusLabel(outcome.error)}")
                    )
                }

                is HttpOutcome.Success -> outcome.value
            }

            try {
                val credentials = LastFmCredentials(
                    connected = json.optBoolean("connected", false),
                    username = json.optString("username").normalizedText(),
                    sessionKey = json.optString("session_key").normalizedText(),
                    apiKey = json.optString("api_key").normalizedText(),
                    apiSecret = json.optString("api_secret").normalizedText(),
                ).normalized()

                sessionStore.saveLastFmCredentials(credentials)
                Result.success(credentials)
            } catch (e: Exception) {
                logFailure("Last.fm status", endpoint, malformed(endpoint, e))
                Result.failure(e)
            }
        }

    suspend fun loginLastFm(
        serverUrl: String,
        token: String,
        username: String,
        password: String,
    ): Result<LastFmCredentials> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trimEnd('/')
        val endpoint = "$cleanUrl/api/v1/integrations/lastfm/login"
        val requestJson = JSONObject().apply {
            put("username", username)
            put("password", password)
        }

        val response = when (val outcome = postJson(endpoint, requestJson, token)) {
            is HttpOutcome.Failure -> {
                logFailure("Last.fm login", endpoint, outcome.error)
                return@withContext Result.failure(
                    Exception("${LASTFM_LOGIN_FAILED} (${statusLabel(outcome.error)})")
                )
            }

            is HttpOutcome.Success -> outcome.value
        }

        if (response.statusCode == HTTP_UNAUTHORIZED) {
            logStatusFailure("Last.fm login", endpoint, response.statusCode)
            sessionStore.signOut()
            return@withContext Result.failure(UnauthorizedException())
        }

        if (response.statusCode !in 200..299) {
            logStatusFailure("Last.fm login", endpoint, response.statusCode)
            return@withContext Result.failure(
                Exception(
                    friendlyMessage(response.body, LASTFM_LOGIN_FAILED, response.statusCode)
                )
            )
        }

        try {
            val json = JSONObject(response.body)
            val credentials = LastFmCredentials(
                connected = json.optBoolean("connected", true),
                username = json.optString("username").normalizedText() ?: username,
                sessionKey = json.optString("session_key").normalizedText(),
                apiKey = json.optString("api_key").normalizedText(),
                apiSecret = json.optString("api_secret").normalizedText(),
            ).normalized()

            sessionStore.saveLastFmCredentials(credentials)
            Result.success(credentials)
        } catch (e: Exception) {
            logFailure("Last.fm login", endpoint, malformed(endpoint, e))
            Result.failure(e)
        }
    }

    suspend fun fetchListenBrainzStatus(
        serverUrl: String,
        token: String,
    ): Result<ListenBrainzCredentials> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trimEnd('/')
        val endpoint = "$cleanUrl/api/v1/integrations/listenbrainz/status"

        val json = when (val outcome = http.getJson(endpoint, bearerHeaders(token))) {
            is HttpOutcome.Failure -> {
                logFailure("ListenBrainz status", endpoint, outcome.error)
                if (outcome.error.statusCode == HTTP_UNAUTHORIZED) {
                    sessionStore.signOut()
                    return@withContext Result.failure(UnauthorizedException())
                }
                return@withContext Result.failure(
                    Exception(
                        "Failed to check ListenBrainz status: ${statusLabel(outcome.error)}"
                    )
                )
            }

            is HttpOutcome.Success -> outcome.value
        }

        try {
            val credentials = ListenBrainzCredentials(
                connected = json.optBoolean("connected", false),
                username = json.optString("username").normalizedText(),
                token = json.optString("token").normalizedText(),
            ).normalized()

            sessionStore.saveListenBrainzCredentials(credentials)
            Result.success(credentials)
        } catch (e: Exception) {
            logFailure("ListenBrainz status", endpoint, malformed(endpoint, e))
            Result.failure(e)
        }
    }

    suspend fun loginListenBrainz(
        serverUrl: String,
        token: String,
        listenbrainzToken: String,
    ): Result<ListenBrainzCredentials> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trimEnd('/')
        val endpoint = "$cleanUrl/api/v1/integrations/listenbrainz/login"
        val requestJson = JSONObject().apply {
            put("token", listenbrainzToken)
        }

        val response = when (val outcome = postJson(endpoint, requestJson, token)) {
            is HttpOutcome.Failure -> {
                logFailure("ListenBrainz login", endpoint, outcome.error)
                return@withContext Result.failure(
                    Exception("${LISTENBRAINZ_LOGIN_FAILED} (${statusLabel(outcome.error)})")
                )
            }

            is HttpOutcome.Success -> outcome.value
        }

        if (response.statusCode == HTTP_UNAUTHORIZED) {
            logStatusFailure("ListenBrainz login", endpoint, response.statusCode)
            sessionStore.signOut()
            return@withContext Result.failure(UnauthorizedException())
        }

        if (response.statusCode !in 200..299) {
            logStatusFailure("ListenBrainz login", endpoint, response.statusCode)
            return@withContext Result.failure(
                Exception(
                    friendlyMessage(
                        response.body,
                        LISTENBRAINZ_LOGIN_FAILED,
                        response.statusCode,
                    )
                )
            )
        }

        try {
            val json = JSONObject(response.body)
            val credentials = ListenBrainzCredentials(
                connected = json.optBoolean("connected", true),
                username = json.optString("username").normalizedText(),
                token = json.optString("token").normalizedText() ?: listenbrainzToken,
            ).normalized()

            sessionStore.saveListenBrainzCredentials(credentials)
            Result.success(credentials)
        } catch (e: Exception) {
            logFailure("ListenBrainz login", endpoint, malformed(endpoint, e))
            Result.failure(e)
        }
    }

    private fun deviceName(): String = listOfNotNull(
        Build.MANUFACTURER?.replaceFirstChar { it.uppercase() },
        Build.MODEL,
    ).joinToString(" ").trim().ifBlank { DEFAULT_DEVICE_NAME }

    private fun bearerHeaders(token: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/json",
    )


    private suspend fun postJson(
        url: String,
        payload: JSONObject,
        token: String? = null,
    ): HttpOutcome<JsonResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()

        try {
            okHttp.newCall(request).execute().use { response ->
                HttpOutcome.Success(
                    JsonResponse(response.code, response.body?.string().orEmpty())
                )
            }
        } catch (e: Throwable) {
            HttpOutcome.Failure(classify(url, e))
        }
    }

    private fun classify(url: String, e: Throwable): HttpError = when (e) {
        is SocketTimeoutException, is InterruptedIOException ->
            HttpError(HttpErrorKind.TIMEOUT, message = "Timed out talking to $url", cause = e)

        is UnknownHostException ->
            HttpError(HttpErrorKind.NETWORK, message = "Unknown host for $url", cause = e)

        is ConnectException ->
            HttpError(
                HttpErrorKind.NETWORK,
                message = "Connection refused for $url: ${e.message.orEmpty()}",
                cause = e,
            )

        is IOException ->
            HttpError(
                HttpErrorKind.NETWORK,
                message = "Network failure for $url: ${e.message.orEmpty()}",
                cause = e,
            )

        is IllegalArgumentException ->
            HttpError(
                HttpErrorKind.MALFORMED,
                message = "Malformed request url $url: ${e.message.orEmpty()}",
                cause = e,
            )

        else ->
            HttpError(
                HttpErrorKind.MALFORMED,
                message = "Unexpected failure talking to $url: ${e.message.orEmpty()}",
                cause = e,
            )
    }

    private fun malformed(url: String, error: Throwable): HttpError = HttpError(
        kind = HttpErrorKind.MALFORMED,
        message = "Malformed body from $url: ${error.message.orEmpty()}",
        cause = error,
    )


    private fun describe(error: HttpError): String = when {
        error.kind == HttpErrorKind.STATUS &&
                error.message.contains("rate limited", ignoreCase = true) -> "rate limited"

        error.statusCode == HTTP_UNAUTHORIZED -> "unauthorized"
        error.statusCode == HTTP_FORBIDDEN -> "forbidden"
        error.kind == HttpErrorKind.TIMEOUT -> "timed out"
        error.kind == HttpErrorKind.NETWORK -> "network unavailable"
        error.kind == HttpErrorKind.MALFORMED -> "malformed body"
        else -> "unexpected error"
    }

    private fun logFailure(operation: String, url: String, error: HttpError) {
        logWarning("$operation failed (${describe(error)}) for $url: ${error.message}")
    }

    private fun logStatusFailure(operation: String, url: String, statusCode: Int) {
        val reason = when (statusCode) {
            HTTP_UNAUTHORIZED -> "unauthorized"
            HTTP_FORBIDDEN -> "forbidden"
            HTTP_TOO_MANY_REQUESTS -> "rate limited"
            else -> "http error"
        }
        logWarning("$operation failed ($reason) for $url: HTTP $statusCode")
    }


    private fun logWarning(message: String) {
        // android.util.Log is a throwing stub under JVM unit tests, so a log line must never be the thing that fails a request.
        runCatching { Log.w(TAG, message) }
    }


    private fun statusLabel(error: HttpError): String = when {
        error.statusCode != null -> error.statusCode.toString()
        error.kind == HttpErrorKind.TIMEOUT -> "timeout"
        error.kind == HttpErrorKind.NETWORK -> "network error"
        else -> "no response"
    }

    private fun friendlyMessage(body: String, fallback: String, statusCode: Int): String = try {
        val errJson = JSONObject(body)
        errJson.optString("message").ifBlank {
            errJson.optString("error").ifBlank { "$fallback ($statusCode)" }
        }
    } catch (_: Exception) {
        "$fallback ($statusCode)"
    }


    private class JsonResponse(val statusCode: Int, val body: String)

    private companion object {
        const val DEFAULT_DEVICE_NAME = "Android Device"
        const val EXCHANGE_FAILED = "Authentication failed"
        const val LASTFM_LOGIN_FAILED = "Last.fm authentication failed"
        const val LISTENBRAINZ_LOGIN_FAILED = "ListenBrainz authentication failed"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val TAG = "Auth"

        val JSON_MEDIA_TYPE = "application/json".toMediaType()


        val okHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val http: HttpJsonClient = HttpJsonClient(client = okHttp)
    }
}
