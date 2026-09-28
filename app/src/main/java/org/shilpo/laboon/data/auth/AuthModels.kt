package org.shilpo.laboon.data.auth

data class AuthConnectionPayload(
    val serverUrl: String,
    val code: String,
)

data class AuthUser(
    val telegramId: Long,
    val name: String?,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
)

data class AuthSession(
    val serverUrl: String,
    val token: String,
    val refreshToken: String,
    val expiresAtUnix: Long,
    val user: AuthUser,
)

data class LastFmCredentials(
    val connected: Boolean,
    val username: String?,
    val sessionKey: String?,
    val apiKey: String?,
    val apiSecret: String?,
)
