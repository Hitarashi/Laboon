package org.shilpo.laboon.data.auth

import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class AuthRepository(
    private val storage: AuthSessionStore,
) {

    fun parseConnectionUri(uri: Uri): AuthConnectionPayload? {
        val dataParam = uri.getQueryParameter("data")
        if (!dataParam.isNullOrBlank()) {
            val payload = parsePayloadData(dataParam)
            if (payload != null) return payload
        }

        val codeParam = uri.getQueryParameter("code") ?: uri.getQueryParameter("c")
        val serverParam = uri.getQueryParameter("server") ?: uri.getQueryParameter("s")

        if (!codeParam.isNullOrBlank() && !serverParam.isNullOrBlank()) {
            return AuthConnectionPayload(
                serverUrl = serverParam.trimEnd('/'),
                code = codeParam.trim(),
            )
        }

        return null
    }

    fun parsePayloadData(raw: String): AuthConnectionPayload? {
        return try {
            val trimmed = raw.trim()
            val decodedBytes = try {
                java.util.Base64.getDecoder().decode(trimmed)
            } catch (_: Throwable) {
                Base64.decode(trimmed, Base64.DEFAULT)
            }
            val jsonString = String(decodedBytes, StandardCharsets.UTF_8)
            val server = extractJsonField(jsonString, "s") ?: return null
            val code = extractJsonField(jsonString, "c") ?: return null
            AuthConnectionPayload(
                serverUrl = server.trimEnd('/'),
                code = code.trim(),
            )
        } catch (_: Exception) {
            null
        }
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

                val deviceName = listOfNotNull(
                    Build.MANUFACTURER?.replaceFirstChar { it.uppercase() },
                    Build.MODEL
                ).joinToString(" ").trim()

                val requestJson = JSONObject().apply {
                    put("code", code)
                    put("device_name", deviceName.ifBlank { "Android Device" })
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
                    name = userJson.optCleanString("name"),
                    username = userJson.optCleanString("username"),
                    firstName = userJson.optCleanString("first_name"),
                    lastName = userJson.optCleanString("last_name"),
                )

                val session = AuthSession(
                    serverUrl = cleanUrl,
                    token = token,
                    refreshToken = refreshToken,
                    expiresAtUnix = expiresAtUnix,
                    user = user,
                )

                storage.saveSession(session)
                Result.success(session)
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
                    username = json.optCleanString("username"),
                    sessionKey = json.optCleanString("session_key"),
                    apiKey = json.optCleanString("api_key"),
                    apiSecret = json.optCleanString("api_secret"),
                )

                storage.saveLastFmCredentials(credentials)
                Result.success(credentials)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun JSONObject.optCleanString(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key).trim()
        return if (value.isEmpty() || value.equals("null", ignoreCase = true)) null else value
    }
}
