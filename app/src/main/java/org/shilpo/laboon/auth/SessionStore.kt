package org.shilpo.laboon.auth

class SessionStore(private val store: KeyValueStore) {

    fun saveSession(session: AuthSession) {
        val normalized = session.normalized()
        store.putString(KEY_SERVER_URL, normalized.serverUrl)
        store.putString(KEY_TOKEN, normalized.token)
        store.putString(KEY_REFRESH_TOKEN, normalized.refreshToken)
        store.putLong(KEY_EXPIRES_AT, normalized.expiresAtUnix)
        store.putLong(KEY_TG_ID, normalized.user.telegramId)
        putStringOrRemove(KEY_USER_NAME, normalized.user.name)
        putStringOrRemove(KEY_USERNAME, normalized.user.username)
        putStringOrRemove(KEY_FIRST_NAME, normalized.user.firstName)
        putStringOrRemove(KEY_LAST_NAME, normalized.user.lastName)
    }

    fun getSession(): AuthSession? {
        val serverUrl = store.getString(KEY_SERVER_URL).normalizedText() ?: return null
        val token = store.getString(KEY_TOKEN).normalizedText() ?: return null
        val refreshToken = store.getString(KEY_REFRESH_TOKEN).normalizedText() ?: return null
        val user = AuthUser(
            telegramId = store.getLong(KEY_TG_ID),
            name = store.getString(KEY_USER_NAME).normalizedText(),
            username = store.getString(KEY_USERNAME).normalizedText(),
            firstName = store.getString(KEY_FIRST_NAME).normalizedText(),
            lastName = store.getString(KEY_LAST_NAME).normalizedText(),
        )
        if (!user.isBound) return null

        return AuthSession(
            serverUrl = serverUrl,
            token = token,
            refreshToken = refreshToken,
            expiresAtUnix = store.getLong(KEY_EXPIRES_AT),
            user = user,
        ).normalized()
    }

    fun hasSession(): Boolean = getSession() != null

    fun signOut() {
        (SESSION_KEYS + LASTFM_KEYS).forEach(store::remove)
    }

    fun saveLastFmCredentials(credentials: LastFmCredentials) {
        val normalized = credentials.normalized()
        store.putBoolean(KEY_LASTFM_CONNECTED, normalized.connected)
        putStringOrRemove(KEY_LASTFM_USERNAME, normalized.username)
        putStringOrRemove(KEY_LASTFM_SESSION_KEY, normalized.sessionKey)
        putStringOrRemove(KEY_LASTFM_API_KEY, normalized.apiKey)
        putStringOrRemove(KEY_LASTFM_API_SECRET, normalized.apiSecret)
    }

    fun getLastFmCredentials(): LastFmCredentials? {
        if (!store.contains(KEY_LASTFM_CONNECTED) && !store.contains(KEY_LASTFM_API_KEY)) return null
        return LastFmCredentials(
            connected = store.getBoolean(KEY_LASTFM_CONNECTED),
            username = store.getString(KEY_LASTFM_USERNAME).normalizedText(),
            sessionKey = store.getString(KEY_LASTFM_SESSION_KEY).normalizedText(),
            apiKey = store.getString(KEY_LASTFM_API_KEY).normalizedText(),
            apiSecret = store.getString(KEY_LASTFM_API_SECRET).normalizedText(),
        ).normalized()
    }

    private fun putStringOrRemove(key: String, value: String?) {
        if (value == null) store.remove(key) else store.putString(key, value)
    }

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_TOKEN = "token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_TG_ID = "tg_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USERNAME = "username"
        const val KEY_FIRST_NAME = "first_name"
        const val KEY_LAST_NAME = "last_name"

        const val KEY_LASTFM_CONNECTED = "lastfm_connected"
        const val KEY_LASTFM_USERNAME = "lastfm_username"
        const val KEY_LASTFM_SESSION_KEY = "lastfm_session_key"
        const val KEY_LASTFM_API_KEY = "lastfm_api_key"
        const val KEY_LASTFM_API_SECRET = "lastfm_api_secret"

        private val SESSION_KEYS = listOf(
            KEY_SERVER_URL,
            KEY_TOKEN,
            KEY_REFRESH_TOKEN,
            KEY_EXPIRES_AT,
            KEY_TG_ID,
            KEY_USER_NAME,
            KEY_USERNAME,
            KEY_FIRST_NAME,
            KEY_LAST_NAME,
        )

        private val LASTFM_KEYS = listOf(
            KEY_LASTFM_CONNECTED,
            KEY_LASTFM_USERNAME,
            KEY_LASTFM_SESSION_KEY,
            KEY_LASTFM_API_KEY,
            KEY_LASTFM_API_SECRET,
        )
    }
}
