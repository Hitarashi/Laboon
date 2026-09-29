package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.auth.AuthClient
import org.shilpo.laboon.auth.SessionStore

class AuthPayloadTest {

    private val authClient = AuthClient(SessionStore(FakeKeyValueStore()))

    @Test
    fun parsePayloadData_validJsonBase64_returnsPayload() {
        val sampleBase64 = "eyJzIjoiaHR0cHM6Ly9zdHJlYW0uZXhhbXBsZS5jb20iLCJjIjoiMTIzNDU2In0="

        val payload = authClient.parsePayloadData(sampleBase64)

        assertNotNull(payload)
        assertEquals("https://stream.example.com", payload?.serverUrl)
        assertEquals("123456", payload?.code)
    }

    @Test
    fun parsePayloadData_urlSafeCharactersInTheServerUrl_arePreserved() {
        val encoded = java.util.Base64.getEncoder().encodeToString(
            """{"s":"https://s.example.com/a+b","c":"  7  "}""".toByteArray(),
        )

        val payload = authClient.parsePayloadData(encoded)

        assertEquals("https://s.example.com/a+b", payload?.serverUrl)
        assertEquals("7", payload?.code)
    }

    @Test
    fun parsePayloadData_trailingSlashIsStrippedFromTheServerUrl() {
        val encoded = java.util.Base64.getEncoder().encodeToString(
            """{"s":"https://stream.example.com/","c":"1"}""".toByteArray(),
        )

        assertEquals("https://stream.example.com", authClient.parsePayloadData(encoded)?.serverUrl)
    }

    @Test
    fun parsePayloadData_invalidBase64_returnsNull() {
        assertNull(authClient.parsePayloadData("not_valid_base64_json"))
    }

    @Test
    fun parsePayloadData_base64ContainingAnIllegalCharacter_returnsNull() {
        val valid = "eyJzIjoiaHR0cHM6Ly9zdHJlYW0uZXhhbXBsZS5jb20iLCJjIjoiMTIzNDU2In0="
        val corrupted = valid.take(10) + "!" + valid.drop(10)

        assertNotNull(
            "the same payload without the illegal byte must parse",
            authClient.parsePayloadData(valid)
        )
        assertNull(authClient.parsePayloadData(corrupted))
    }

    @Test
    fun parsePayloadData_truncatedBase64_returnsNull() {
        val valid = "eyJzIjoiaHR0cHM6Ly9zdHJlYW0uZXhhbXBsZS5jb20iLCJjIjoiMTIzNDU2In0="

        assertNotNull(authClient.parsePayloadData(valid))
        assertNull(authClient.parsePayloadData(valid.dropLast(3)))
    }

    @Test
    fun parsePayloadData_validBase64OfTheWrongSchema_returnsNull() {
        val encoded = java.util.Base64.getEncoder().encodeToString(
            """{"token":"123456"}""".toByteArray(),
        )

        assertNull(authClient.parsePayloadData(encoded))
    }

    @Test
    fun parsePayloadData_validBase64WithBlankFields_returnsNull() {
        val missingCode = java.util.Base64.getEncoder().encodeToString(
            """{"s":"https://stream.example.com","c":""}""".toByteArray(),
        )
        val missingServer = java.util.Base64.getEncoder().encodeToString(
            """{"s":"","c":"1"}""".toByteArray(),
        )

        assertNull(authClient.parsePayloadData(missingCode))
        assertNull(authClient.parsePayloadData(missingServer))
    }

    @Test
    fun parsePayloadData_emptyAndWhitespaceInput_returnNull() {
        assertNull(authClient.parsePayloadData(""))
        assertNull(authClient.parsePayloadData("   "))
    }

    @Test
    fun parseConnectionUri_dataQueryParameter_returnsPayload() {
        val encoded = "eyJzIjoiaHR0cHM6Ly9zdHJlYW0uZXhhbXBsZS5jb20iLCJjIjoiMTIzNDU2In0="

        val payload = authClient.parseConnectionUri("laboon://connect?data=$encoded")

        assertEquals("https://stream.example.com", payload?.serverUrl)
        assertEquals("123456", payload?.code)
    }

    @Test
    fun parseConnectionUri_percentEncodedDataQueryParameter_returnsPayload() {
        val encoded = "eyJzIjoiaHR0cHM6Ly9zdHJlYW0uZXhhbXBsZS5jb20iLCJjIjoiMTIzNDU2In0="

        val payload =
            authClient.parseConnectionUri("laboon://connect?data=${encoded.replace("=", "%3D")}")

        assertEquals("https://stream.example.com", payload?.serverUrl)
    }

    @Test
    fun parseConnectionUri_codeAndServerQueryParameters_returnsPayload() {
        val payload = authClient.parseConnectionUri(
            "laboon://connect?code=%20123456%20&server=https%3A%2F%2Fstream.example.com%2F",
        )

        assertEquals("https://stream.example.com", payload?.serverUrl)
        assertEquals("123456", payload?.code)
    }

    @Test
    fun parseConnectionUri_shortQueryParameterNames_returnsPayload() {
        val payload = authClient.parseConnectionUri("laboon://connect?c=1&s=https://s.example.com")

        assertEquals("https://s.example.com", payload?.serverUrl)
        assertEquals("1", payload?.code)
    }

    @Test
    fun parseConnectionUri_codeWithoutServer_returnsNull() {
        assertNull(authClient.parseConnectionUri("laboon://connect?code=123456"))
    }

    @Test
    fun parseConnectionUri_serverWithoutCode_returnsNull() {
        assertNull(authClient.parseConnectionUri("laboon://connect?server=https://s.example.com"))
    }

    @Test
    fun parseConnectionUri_blankValues_returnNull() {
        assertNull(authClient.parseConnectionUri("laboon://connect?code=%20&server=%20"))
        assertNull(authClient.parseConnectionUri("laboon://connect?data="))
    }

    @Test
    fun parseConnectionUri_noQuery_returnsNull() {
        assertNull(authClient.parseConnectionUri("laboon://connect"))
        assertNull(authClient.parseConnectionUri("laboon://connect#data=abc"))
    }

    @Test
    fun parseConnectionUri_garbageInput_returnsNullWithoutThrowing() {
        assertNull(authClient.parseConnectionUri(""))
        assertNull(authClient.parseConnectionUri("not a uri at all"))
        assertNull(authClient.parseConnectionUri("laboon://connect?data=%E0%A4%A&c=&s="))
    }

    @Test
    fun parseConnectionUri_firstValueOfARepeatedParameterWins() {
        val payload = authClient.parseConnectionUri(
            "laboon://connect?code=first&code=second&server=https://s.example.com",
        )

        assertEquals("first", payload?.code)
    }

    @Test
    fun parseConnectionUri_queryIsNotConfusedByTheFragment() {
        val payload = authClient.parseConnectionUri(
            "laboon://connect?code=abc#server=https://evil.example.com",
        )

        assertNull(payload)
    }
}
