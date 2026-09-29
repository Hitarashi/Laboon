package org.shilpo.laboon.auth

import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
            try {
                val cleanUrl = serverUrl.trimEnd('/')
                val endpoint = URL("$cleanUrl/api/v1/auth/exchange")
                val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                val requestJson = JSONObject().apply {
                    put("code", code)
                    put("device_name", deviceName())
                    put("platform", "android")
                }

                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(requestJson.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseText = stream?.let {
                    BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                }.orEmpty()

                if (responseCode !in 200..299) {
                    val message = try {
                        val errJson = JSONObject(responseText)
                        errJson.optString("message").ifBlank {
                            errJson.optString("error")
                                .ifBlank { "Authentication failed ($responseCode)" }
                        }
                    } catch (_: Exception) {
                        "Authentication failed ($responseCode)"
                    }
                    return@withContext Result.failure(Exception(message))
                }

                val responseJson = JSONObject(responseText)
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
                Result.failure(e)
            }
        }

    suspend fun validateSession(serverUrl: String, token: String): Result<AuthUser> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = serverUrl.trimEnd('/')
                val endpoint = URL("$cleanUrl/api/v1/auth/me")
                val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = connection.responseCode
                if (responseCode == 401) {
                    sessionStore.signOut()
                    return@withContext Result.failure(UnauthorizedException())
                }
                if (responseCode !in 200..299) {
                    return@withContext Result.failure(Exception("Session check failed ($responseCode)"))
                }

                val responseText = connection.inputStream.let {
                    BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                }

                val json = JSONObject(responseText)
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
                Result.failure(e)
            }
        }

    suspend fun fetchLastFmStatus(serverUrl: String, token: String): Result<LastFmCredentials> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = serverUrl.trimEnd('/')
                val endpoint = URL("$cleanUrl/api/v1/integrations/lastfm/status")
                val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = connection.responseCode
                if (responseCode == 401) {
                    sessionStore.signOut()
                    return@withContext Result.failure(UnauthorizedException())
                }
                if (responseCode !in 200..299) {
                    return@withContext Result.failure(Exception("Failed to check Last.fm status: $responseCode"))
                }

                val responseText = connection.inputStream.let {
                    BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                }

                val json = JSONObject(responseText)
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
                Result.failure(e)
            }
        }

    suspend fun loginLastFm(
        serverUrl: String,
        token: String,
        username: String,
        password: String,
    ): Result<LastFmCredentials> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trimEnd('/')
            val endpoint = URL("$cleanUrl/api/v1/integrations/lastfm/login")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val requestJson = JSONObject().apply {
                put("username", username)
                put("password", password)
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()

            if (responseCode == 401) {
                sessionStore.signOut()
                return@withContext Result.failure(UnauthorizedException())
            }

            if (responseCode !in 200..299) {
                val message = try {
                    val errJson = JSONObject(responseText)
                    errJson.optString("message").ifBlank {
                        errJson.optString("error")
                            .ifBlank { "Last.fm authentication failed ($responseCode)" }
                    }
                } catch (_: Exception) {
                    "Last.fm authentication failed ($responseCode)"
                }
                return@withContext Result.failure(Exception(message))
            }

            val json = JSONObject(responseText)
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
            Result.failure(e)
        }
    }

    suspend fun fetchListenBrainzStatus(
        serverUrl: String,
        token: String,
    ): Result<ListenBrainzCredentials> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trimEnd('/')
            val endpoint = URL("$cleanUrl/api/v1/integrations/listenbrainz/status")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode == 401) {
                sessionStore.signOut()
                return@withContext Result.failure(UnauthorizedException())
            }
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Failed to check ListenBrainz status: $responseCode"))
            }

            val responseText = connection.inputStream.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }

            val json = JSONObject(responseText)
            val credentials = ListenBrainzCredentials(
                connected = json.optBoolean("connected", false),
                username = json.optString("username").normalizedText(),
                token = json.optString("token").normalizedText(),
            ).normalized()

            sessionStore.saveListenBrainzCredentials(credentials)
            Result.success(credentials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginListenBrainz(
        serverUrl: String,
        token: String,
        listenbrainzToken: String,
    ): Result<ListenBrainzCredentials> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trimEnd('/')
            val endpoint = URL("$cleanUrl/api/v1/integrations/listenbrainz/login")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val requestJson = JSONObject().apply {
                put("token", listenbrainzToken)
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()

            if (responseCode == 401) {
                sessionStore.signOut()
                return@withContext Result.failure(UnauthorizedException())
            }

            if (responseCode !in 200..299) {
                val message = try {
                    val errJson = JSONObject(responseText)
                    errJson.optString("message").ifBlank {
                        errJson.optString("error")
                            .ifBlank { "ListenBrainz authentication failed ($responseCode)" }
                    }
                } catch (_: Exception) {
                    "ListenBrainz authentication failed ($responseCode)"
                }
                return@withContext Result.failure(Exception(message))
            }

            val json = JSONObject(responseText)
            val credentials = ListenBrainzCredentials(
                connected = json.optBoolean("connected", true),
                username = json.optString("username").normalizedText(),
                token = json.optString("token").normalizedText() ?: listenbrainzToken,
            ).normalized()

            sessionStore.saveListenBrainzCredentials(credentials)
            Result.success(credentials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deviceName(): String = listOfNotNull(
        Build.MANUFACTURER?.replaceFirstChar { it.uppercase() },
        Build.MODEL,
    ).joinToString(" ").trim().ifBlank { DEFAULT_DEVICE_NAME }

    private companion object {
        const val DEFAULT_DEVICE_NAME = "Android Device"
    }
}
