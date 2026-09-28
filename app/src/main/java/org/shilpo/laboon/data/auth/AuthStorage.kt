package org.shilpo.laboon.data.auth

import android.content.Context
import android.content.SharedPreferences

interface AuthSessionStore {
    fun saveSession(session: AuthSession)
    fun getSession(): AuthSession?
    fun hasSession(): Boolean
    fun clearSession()
    fun saveLastFmCredentials(credentials: LastFmCredentials)
    fun getLastFmCredentials(): LastFmCredentials?
}

class AuthStorage(context: Context) : AuthSessionStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_SERVER_URL, session.serverUrl)
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtUnix)
            .putLong(KEY_TG_ID, session.user.telegramId)
            .putString(
                KEY_USER_NAME,
                session.user.name?.takeIf { !it.equals("null", ignoreCase = true) })
            .putString(
                KEY_USERNAME,
                session.user.username?.takeIf { !it.equals("null", ignoreCase = true) })
            .putString(
                KEY_FIRST_NAME,
                session.user.firstName?.takeIf { !it.equals("null", ignoreCase = true) })
            .putString(
                KEY_LAST_NAME,
                session.user.lastName?.takeIf { !it.equals("null", ignoreCase = true) })
            .apply()
    }

    override fun getSession(): AuthSession? {
        val serverUrl = prefs.getCleanString(KEY_SERVER_URL) ?: return null
        val token = prefs.getCleanString(KEY_TOKEN) ?: return null
        val refreshToken = prefs.getCleanString(KEY_REFRESH_TOKEN) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val telegramId = prefs.getLong(KEY_TG_ID, 0L)
        if (telegramId == 0L) return null

        val user = AuthUser(
            telegramId = telegramId,
            name = prefs.getCleanString(KEY_USER_NAME),
            username = prefs.getCleanString(KEY_USERNAME),
            firstName = prefs.getCleanString(KEY_FIRST_NAME),
            lastName = prefs.getCleanString(KEY_LAST_NAME),
        )

        return AuthSession(
            serverUrl = serverUrl,
            token = token,
            refreshToken = refreshToken,
            expiresAtUnix = expiresAt,
            user = user,
        )
    }

    override fun hasSession(): Boolean = getSession() != null

    override fun clearSession() {
        prefs.edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_TG_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USERNAME)
            .remove(KEY_FIRST_NAME)
            .remove(KEY_LAST_NAME)
            .apply()
    }

    override fun saveLastFmCredentials(credentials: LastFmCredentials) {
        prefs.edit()
            .putBoolean(KEY_LASTFM_CONNECTED, credentials.connected)
            .putString(
                KEY_LASTFM_USERNAME,
                credentials.username?.takeIf { !it.equals("null", ignoreCase = true) })
            .putString(
                KEY_LASTFM_SESSION_KEY,
                credentials.sessionKey?.takeIf { !it.equals("null", ignoreCase = true) })
            .putString(
                KEY_LASTFM_API_KEY,
                credentials.apiKey?.takeIf { !it.equals("null", ignoreCase = true) })
            .putString(
                KEY_LASTFM_API_SECRET,
                credentials.apiSecret?.takeIf { !it.equals("null", ignoreCase = true) })
            .apply()
    }

    override fun getLastFmCredentials(): LastFmCredentials? {
        if (!prefs.contains(KEY_LASTFM_CONNECTED) && !prefs.contains(KEY_LASTFM_API_KEY)) {
            return null
        }
        return LastFmCredentials(
            connected = prefs.getBoolean(KEY_LASTFM_CONNECTED, false),
            username = prefs.getCleanString(KEY_LASTFM_USERNAME),
            sessionKey = prefs.getCleanString(KEY_LASTFM_SESSION_KEY),
            apiKey = prefs.getCleanString(KEY_LASTFM_API_KEY),
            apiSecret = prefs.getCleanString(KEY_LASTFM_API_SECRET),
        )
    }

    private fun SharedPreferences.getCleanString(key: String): String? {
        val value = getString(key, null)?.trim()
        return if (value.isNullOrEmpty() || value.equals("null", ignoreCase = true)) null else value
    }

    companion object {
        private const val PREFS_NAME = "laboon_auth_storage"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_TG_ID = "tg_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USERNAME = "username"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME = "last_name"

        private const val KEY_LASTFM_CONNECTED = "lastfm_connected"
        private const val KEY_LASTFM_USERNAME = "lastfm_username"
        private const val KEY_LASTFM_SESSION_KEY = "lastfm_session_key"
        private const val KEY_LASTFM_API_KEY = "lastfm_api_key"
        private const val KEY_LASTFM_API_SECRET = "lastfm_api_secret"
    }
}
