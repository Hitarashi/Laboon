package org.shilpo.laboon

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.shilpo.laboon.auth.AuthClient
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.AuthUser
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.auth.UnauthorizedException
import java.net.InetSocketAddress

class AuthValidationTest {

    private lateinit var server: HttpServer
    private lateinit var sessionStore: SessionStore
    private lateinit var authClient: AuthClient
    private var serverUrl: String = ""

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.start()
        serverUrl = "http://localhost:${server.address.port}"

        sessionStore = SessionStore(FakeKeyValueStore())
        sessionStore.saveSession(
            AuthSession(
                serverUrl = serverUrl,
                token = "test-token",
                refreshToken = "refresh-token",
                expiresAtUnix = 1234567890L,
                user = AuthUser(
                    telegramId = 42,
                    name = "Tester",
                    username = "testuser",
                    firstName = null,
                    lastName = null,
                ),
            ),
        )
        authClient = AuthClient(sessionStore)
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun validateSession_whenServerReturns200_returnsSuccessAndPreservesSession() = runBlocking {
        server.createContext("/api/v1/auth/me") { exchange ->
            assertEquals("Bearer test-token", exchange.requestHeaders.getFirst("Authorization"))
            val response = """{"user":{"telegram_id":42,"username":"testuser","name":"Tester"}}"""
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.write(response.toByteArray())
            exchange.close()
        }

        val result = authClient.validateSession(serverUrl, "test-token")

        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrNull()?.telegramId)
        assertEquals("testuser", result.getOrNull()?.username)
        assertTrue(sessionStore.hasSession())
    }

    @Test
    fun validateSession_whenServerReturns401_clearsSessionAndReturnsUnauthorizedException() =
        runBlocking {
            server.createContext("/api/v1/auth/me") { exchange ->
                val response = """{"error":"Unauthorized","message":"Session record not found"}"""
                exchange.sendResponseHeaders(401, response.toByteArray().size.toLong())
                exchange.responseBody.write(response.toByteArray())
                exchange.close()
            }

            val result = authClient.validateSession(serverUrl, "test-token")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is UnauthorizedException)
            assertFalse(sessionStore.hasSession())
        }

    @Test
    fun validateSession_whenServerReturns500_preservesLocalSession() = runBlocking {
        server.createContext("/api/v1/auth/me") { exchange ->
            exchange.sendResponseHeaders(500, 0)
            exchange.close()
        }

        val result = authClient.validateSession(serverUrl, "test-token")

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is UnauthorizedException)
        assertTrue(sessionStore.hasSession())
    }
}
