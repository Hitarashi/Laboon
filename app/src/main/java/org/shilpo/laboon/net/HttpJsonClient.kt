package org.shilpo.laboon.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class HttpJsonClient(
    private val connectTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val client: OkHttpClient? = null,
) {

    private val httpClient: OkHttpClient = client ?: pooledClient(connectTimeoutMs, readTimeoutMs)

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpOutcome<String> = execute(url) {
        Request.Builder()
            .url(url)
            .get()
            .withHeaders(headers)
            .build()
    }

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): HttpOutcome<String> = execute(url) {
        val body = FormBody.Builder()
            .apply { form.forEach { (name, value) -> add(name, value) } }
            .build()
        Request.Builder()
            .url(url)
            .post(body)
            .withHeaders(headers)
            .build()
    }

    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpOutcome<String> = execute(url) {
        Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_BODY_MEDIA_TYPE))
            .withHeaders(headers)
            .build()
    }

    suspend fun getJson(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpOutcome<JSONObject> = when (val outcome = get(url, headers)) {
        is HttpOutcome.Failure -> outcome
        is HttpOutcome.Success -> parseJson(url, outcome.value)
    }

    private fun Request.Builder.withHeaders(headers: Map<String, String>): Request.Builder {
        header("Accept", JSON_MEDIA_TYPE)
        headers.forEach { (name, value) -> header(name, value) }
        return this
    }

    private suspend fun execute(url: String, requestFactory: () -> Request): HttpOutcome<String> =
        withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(requestFactory()).execute().use { response ->
                    val body = response.body.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext statusFailure(url, response.code, body)
                    }
                    HttpOutcome.Success(body)
                }
            } catch (e: SocketTimeoutException) {
                HttpOutcome.Failure(
                    HttpError(
                        HttpErrorKind.TIMEOUT,
                        message = "Timed out talking to $url",
                        cause = e
                    )
                )
            } catch (e: InterruptedIOException) {
                HttpOutcome.Failure(
                    HttpError(
                        HttpErrorKind.TIMEOUT,
                        message = "Interrupted talking to $url",
                        cause = e
                    )
                )
            } catch (e: UnknownHostException) {
                HttpOutcome.Failure(
                    HttpError(HttpErrorKind.NETWORK, message = "Unknown host for $url", cause = e)
                )
            } catch (e: ConnectException) {
                HttpOutcome.Failure(
                    HttpError(
                        HttpErrorKind.NETWORK,
                        message = "Connection refused for $url: ${e.message.orEmpty()}",
                        cause = e,
                    )
                )
            } catch (e: IOException) {
                HttpOutcome.Failure(
                    HttpError(
                        HttpErrorKind.NETWORK,
                        message = "Network failure for $url: ${e.message.orEmpty()}",
                        cause = e,
                    )
                )
            } catch (e: IllegalArgumentException) {
                HttpOutcome.Failure(
                    HttpError(
                        HttpErrorKind.MALFORMED,
                        message = "Malformed request url $url: ${e.message.orEmpty()}",
                        cause = e,
                    )
                )
            }
        }

    private fun parseJson(url: String, body: String): HttpOutcome<JSONObject> = try {
        HttpOutcome.Success(JSONObject(body))
    } catch (e: JSONException) {
        HttpOutcome.Failure(
            HttpError(
                kind = HttpErrorKind.MALFORMED,
                message = "Malformed JSON from $url: ${body.trim().take(MAX_BODY_SNIPPET)}",
                cause = e,
            )
        )
    }

    private fun statusFailure(url: String, code: Int, body: String): HttpOutcome.Failure {
        val headline = if (code == HTTP_TOO_MANY_REQUESTS) {
            "HTTP $code rate limited"
        } else {
            "HTTP $code"
        }
        val snippet = body.trim().take(MAX_BODY_SNIPPET)
        val message =
            if (snippet.isEmpty()) "$headline from $url" else "$headline from $url | $snippet"
        return HttpOutcome.Failure(
            HttpError(
                HttpErrorKind.STATUS,
                statusCode = code,
                message = message
            )
        )
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 8_000L
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val JSON_MEDIA_TYPE = "application/json"
        private val JSON_BODY_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_BODY_SNIPPET = 200

        private val pooledClient: OkHttpClient = newClient(DEFAULT_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)

        private fun newClient(connectTimeoutMs: Long, readTimeoutMs: Long): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build()

        private fun pooledClient(connectTimeoutMs: Long, readTimeoutMs: Long): OkHttpClient =
            if (connectTimeoutMs == DEFAULT_TIMEOUT_MS && readTimeoutMs == DEFAULT_TIMEOUT_MS) {
                pooledClient
            } else {
                newClient(connectTimeoutMs, readTimeoutMs)
            }
    }
}
