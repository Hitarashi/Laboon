package org.shilpo.laboon.scrobble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class LastFmSignerTest {

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun sign_sortsKeysAlphabeticallyAndAppendsSecret() {
        val params = mapOf(
            "zebra" to "stripes",
            "apple" to "red",
            "monkey" to "banana",
        )
        val secret = "my_secret_key"
        // Expected string before MD5: "appleredmonkeybananazebrastripesmy_secret_key"
        val expectedConcat = "appleredmonkeybananazebrastripesmy_secret_key"
        val expectedHash = md5Hex(expectedConcat)

        val signature = LastFmSigner.sign(params, secret)

        assertEquals(expectedHash, signature)
    }

    @Test
    fun sign_ignoresFormatAndCallbackCaseInsensitively() {
        val params = mapOf(
            "format" to "json",
            "FORMAT" to "json",
            "callback" to "jsonp",
            "CALLBACK" to "func",
            "track" to "Hello",
            "artist" to "Adele",
        )
        val secret = "topsecret"
        // Expected string before MD5: "artistAdeletrackHellotopsecret"
        val expectedConcat = "artistAdeletrackHellotopsecret"
        val expectedHash = md5Hex(expectedConcat)

        val signature = LastFmSigner.sign(params, secret)

        assertEquals(expectedHash, signature)
    }

    @Test
    fun sign_emptyParams_justHashesSecret() {
        val params = emptyMap<String, String>()
        val secret = "just_secret"
        val expectedHash = md5Hex("just_secret")

        val signature = LastFmSigner.sign(params, secret)

        assertEquals(expectedHash, signature)
    }

    @Test
    fun sign_producesLowercase32CharHex() {
        val params = mapOf("method" to "track.scrobble")
        val secret = "abc"
        val signature = LastFmSigner.sign(params, secret)

        assertEquals(32, signature.length)
        assertEquals(signature.lowercase(), signature)
    }
}
