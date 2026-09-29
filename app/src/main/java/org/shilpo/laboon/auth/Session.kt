package org.shilpo.laboon.auth

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
) {
    val isBound: Boolean get() = telegramId != NO_TELEGRAM_ID

    fun normalized(): AuthUser = copy(
        name = name.normalizedText(),
        username = username.normalizedText(),
        firstName = firstName.normalizedText(),
        lastName = lastName.normalizedText(),
    )

    companion object {
        const val NO_TELEGRAM_ID = 0L
    }
}

data class AuthSession(
    val serverUrl: String,
    val token: String,
    val refreshToken: String,
    val expiresAtUnix: Long,
    val user: AuthUser,
) {
    fun normalized(): AuthSession = copy(user = user.normalized())
}

data class LastFmCredentials(
    val connected: Boolean,
    val username: String?,
    val sessionKey: String?,
    val apiKey: String?,
    val apiSecret: String?,
) {
    fun normalized(): LastFmCredentials = copy(
        username = username.normalizedText(),
        sessionKey = sessionKey.normalizedText(),
        apiKey = apiKey.normalizedText(),
        apiSecret = apiSecret.normalizedText(),
    )
}

fun String?.normalizedText(): String? {
    val value = this?.trim().orEmpty()
    if (value.isEmpty() || value.equals("null", ignoreCase = true)) return null
    return value
}
