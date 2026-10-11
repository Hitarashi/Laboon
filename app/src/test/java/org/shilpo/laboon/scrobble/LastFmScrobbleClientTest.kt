package org.shilpo.laboon.scrobble

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome

class LastFmScrobbleClientTest {

    private class RecordingInterceptor : Interceptor {
        var responseCode: Int = 200
        var responseBody: String = "{}"
        var lastCapturedRequest: Request? = null
        var lastCapturedFormBody: Map<String, String> = emptyMap()

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            lastCapturedRequest = req
            val form = mutableMapOf<String, String>()
            val body = req.body
            if (body is FormBody) {
                for (i in 0 until body.size) {
                    form[body.name(i)] = body.value(i)
                }
            }
            lastCapturedFormBody = form

            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message(if (responseCode == 200) "OK" else "Error")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private val testCredentials = LastFmCredentials(
        connected = true,
        username = "musiclover",
        sessionKey = "test_sk_123",
        apiKey = "test_api_key",
        apiSecret = "test_api_secret",
    )

    @Test
    fun updateNowPlaying_sendsSignedFormAndParsesSuccess() = runBlocking {
        val interceptor = RecordingInterceptor().apply {
            responseBody = """{"nowplaying":{"artist":{"#text":"Radiohead"},"track":{"#text":"Karma Police"}}}"""
        }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = LastFmScrobbleClient(HttpJsonClient(client = okHttpClient))

        val outcome = client.updateNowPlaying(
            artist = "Radiohead",
            track = "Karma Police",
            album = "OK Computer",
            durationSeconds = 261L,
            credentials = testCredentials,
        )

        assertTrue(outcome is HttpOutcome.Success)
        assertEquals(true, (outcome as HttpOutcome.Success).value)

        val form = interceptor.lastCapturedFormBody
        assertEquals("track.updateNowPlaying", form["method"])
        assertEquals("Radiohead", form["artist"])
        assertEquals("Karma Police", form["track"])
        assertEquals("OK Computer", form["album"])
        assertEquals("261", form["duration"])
        assertEquals("test_api_key", form["api_key"])
        assertEquals("test_sk_123", form["sk"])
        assertEquals("json", form["format"])
        assertNotNull(form["api_sig"])

        // Verify signature matches
        val expectedSig = LastFmSigner.sign(form.filterKeys { it != "format" && it != "api_sig" }, "test_api_secret")
        assertEquals(expectedSig, form["api_sig"])
    }

    @Test
    fun updateNowPlaying_returnsFailureOnApiError() = runBlocking {
        val interceptor = RecordingInterceptor().apply {
            responseBody = """{"error": 9, "message": "Invalid session key"}"""
        }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = LastFmScrobbleClient(HttpJsonClient(client = okHttpClient))

        val outcome = client.updateNowPlaying(
            artist = "Radiohead",
            track = "Karma Police",
            credentials = testCredentials,
        )

        assertTrue(outcome is HttpOutcome.Failure)
        val failure = outcome as HttpOutcome.Failure
        assertEquals(HttpErrorKind.STATUS, failure.error.kind)
        assertEquals(9, failure.error.statusCode)
        assertEquals("Invalid session key", failure.error.message)
    }

    @Test
    fun scrobble_emptyList_returnsSuccessWithoutCallingNetwork() = runBlocking {
        val interceptor = RecordingInterceptor()
        val okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = LastFmScrobbleClient(HttpJsonClient(client = okHttpClient))

        val outcome = client.scrobble(emptyList(), testCredentials)

        assertTrue(outcome is HttpOutcome.Success)
        assertEquals(emptyList<ScrobbleResult>(), (outcome as HttpOutcome.Success).value)
        assertNull(interceptor.lastCapturedRequest)
    }

    @Test
    fun scrobble_singleItem_sendsIndexedParamsAndParsesSingleObjectResponse() = runBlocking {
        val interceptor = RecordingInterceptor().apply {
            responseBody = """
                {
                    "scrobbles": {
                        "@attr": {"accepted": 1, "ignored": 0},
                        "scrobble": {
                            "track": {"#text": "Creep"},
                            "artist": {"#text": "Radiohead"},
                            "ignoredMessage": {"code": "0", "#text": ""}
                        }
                    }
                }
            """.trimIndent()
        }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = LastFmScrobbleClient(HttpJsonClient(client = okHttpClient))

        val item = QueuedScrobble(
            artist = "Radiohead",
            track = "Creep",
            album = "Pablo Honey",
            timestampEpochSeconds = 1700000000L,
            durationSeconds = 238L,
        )

        val outcome = client.scrobble(listOf(item), testCredentials)

        assertTrue(outcome is HttpOutcome.Success)
        val results = (outcome as HttpOutcome.Success).value
        assertEquals(1, results.size)
        assertTrue(results[0].accepted)
        assertNull(results[0].ignoredMessage)

        val form = interceptor.lastCapturedFormBody
        assertEquals("track.scrobble", form["method"])
        assertEquals("Radiohead", form["artist[0]"])
        assertEquals("Creep", form["track[0]"])
        assertEquals("Pablo Honey", form["album[0]"])
        assertEquals("1700000000", form["timestamp[0]"])
        assertEquals("238", form["duration[0]"])
        assertEquals("test_api_key", form["api_key"])
        assertEquals("test_sk_123", form["sk"])
        assertEquals("json", form["format"])
    }

    @Test
    fun scrobble_multiItemBatch_parsesArrayResponseWithIgnoredItem() = runBlocking {
        val interceptor = RecordingInterceptor().apply {
            responseBody = """
                {
                    "scrobbles": {
                        "@attr": {"accepted": 1, "ignored": 1},
                        "scrobble": [
                            {
                                "track": {"#text": "Track 1"},
                                "ignoredMessage": {"code": "0", "#text": ""}
                            },
                            {
                                "track": {"#text": "Track 2"},
                                "ignoredMessage": {"code": "1", "#text": "Artist was ignored"}
                            }
                        ]
                    }
                }
            """.trimIndent()
        }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = LastFmScrobbleClient(HttpJsonClient(client = okHttpClient))

        val items = listOf(
            QueuedScrobble(artist = "A1", track = "T1", timestampEpochSeconds = 1700000000L),
            QueuedScrobble(artist = "A2", track = "T2", timestampEpochSeconds = 1700000200L),
        )

        val outcome = client.scrobble(items, testCredentials)

        assertTrue(outcome is HttpOutcome.Success)
        val results = (outcome as HttpOutcome.Success).value
        assertEquals(2, results.size)
        assertTrue(results[0].accepted)
        assertFalse(results[1].accepted)
        assertEquals("Artist was ignored", results[1].ignoredMessage)

        val form = interceptor.lastCapturedFormBody
        assertEquals("A1", form["artist[0]"])
        assertEquals("A2", form["artist[1]"])
    }
}
