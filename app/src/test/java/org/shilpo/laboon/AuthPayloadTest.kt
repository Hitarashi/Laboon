package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.data.auth.AuthRepository
import org.shilpo.laboon.data.auth.AuthSession
import org.shilpo.laboon.data.auth.AuthSessionStore
import org.shilpo.laboon.data.auth.LastFmCredentials

class AuthPayloadTest {

    @Test
    fun parsePayloadData_validJsonBase64_returnsPayload() {
        val repository = AuthRepository(FakeAuthSessionStore())

        val sampleBase64 = "eyJzIjoiaHR0cHM6Ly9zdHJlYW0uZXhhbXBsZS5jb20iLCJjIjoiMTIzNDU2In0="
        val payload = repository.parsePayloadData(sampleBase64)

        assertNotNull(payload)
        assertEquals("https://stream.example.com", payload?.serverUrl)
        assertEquals("123456", payload?.code)
    }

    @Test
    fun parsePayloadData_invalidBase64_returnsNull() {
        val repository = AuthRepository(FakeAuthSessionStore())

        val invalid = "not_valid_base64_json"
        val payload = repository.parsePayloadData(invalid)

        assertNull(payload)
    }

    @Test
    fun lastFmCredentials_nullUsername_isSanitized() {
        val creds = LastFmCredentials(
            connected = false,
            username = "null",
            sessionKey = "null",
            apiKey = "test_key",
            apiSecret = "test_secret",
        )
        val sanitizedUsername = creds.username?.takeIf { !it.equals("null", ignoreCase = true) }
        assertNull(sanitizedUsername)
    }

    private class FakeAuthSessionStore : AuthSessionStore {
        private var session: AuthSession? = null
        private var lastFm: LastFmCredentials? = null

        override fun saveSession(session: AuthSession) {
            this.session = session
        }

        override fun getSession(): AuthSession? = session

        override fun hasSession(): Boolean = session != null

        override fun clearSession() {
            session = null
            lastFm = null
        }

        override fun saveLastFmCredentials(credentials: LastFmCredentials) {
            lastFm = credentials
        }

        override fun getLastFmCredentials(): LastFmCredentials? = lastFm
    }
}
