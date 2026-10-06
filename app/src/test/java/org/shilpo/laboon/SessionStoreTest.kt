package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.AuthUser
import org.shilpo.laboon.auth.KeyValueStore
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.auth.ListenBrainzCredentials
import org.shilpo.laboon.auth.OnboardingProgress
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.auth.normalizedText

class SessionStoreTest {

    @Test
    fun saveSession_thenGetSession_roundTripsEveryField() {
        val store = SessionStore(FakeKeyValueStore())
        val session = session()

        store.saveSession(session)

        assertEquals(session, store.getSession())
        assertTrue(store.hasSession())
    }

    @Test
    fun getSession_aFreshStore_reportsNoSession() {
        val store = SessionStore(FakeKeyValueStore())

        assertNull(store.getSession())
        assertFalse(store.hasSession())
    }

    @Test
    fun getLastFmCredentials_aFreshStore_reportsNoCredentials() {
        val store = SessionStore(FakeKeyValueStore())

        assertNull(store.getLastFmCredentials())
    }

    @Test
    fun saveLastFmCredentials_thenGetLastFmCredentials_roundTripsEveryField() {
        val store = SessionStore(FakeKeyValueStore())
        val credentials = LastFmCredentials(
            connected = true,
            username = "ada",
            sessionKey = "session-key",
            apiKey = "api-key",
            apiSecret = "api-secret",
        )

        store.saveLastFmCredentials(credentials)

        assertEquals(credentials, store.getLastFmCredentials())
    }

    @Test
    fun getListenBrainzCredentials_aFreshStore_reportsNoCredentials() {
        val store = SessionStore(FakeKeyValueStore())

        assertNull(store.getListenBrainzCredentials())
    }

    @Test
    fun saveListenBrainzCredentials_thenGetListenBrainzCredentials_roundTripsEveryField() {
        val store = SessionStore(FakeKeyValueStore())
        val credentials = ListenBrainzCredentials(
            connected = true,
            username = "ada",
            token = "lb-token",
        )

        store.saveListenBrainzCredentials(credentials)

        assertEquals(credentials, store.getListenBrainzCredentials())
    }

    @Test
    fun getSession_aNewStoreOverTheSameKeyValueStore_seesTheSavedSession() {
        val keyValueStore = FakeKeyValueStore()
        val session = session()

        SessionStore(keyValueStore).saveSession(session)

        assertEquals(session, SessionStore(keyValueStore).getSession())
    }

    @Test
    fun getSession_telegramIdZero_isTreatedAsAbsent() {
        val store = SessionStore(FakeKeyValueStore())
        store.saveSession(session().copy(user = session().user.copy(telegramId = AuthUser.NO_TELEGRAM_ID)))

        assertNull(store.getSession())
        assertFalse(store.hasSession())
    }

    @Test
    fun getSession_missingServerUrl_returnsNull() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        store.saveSession(session())
        keyValueStore.putString("server_url", "")

        assertNull(store.getSession())
    }

    @Test
    fun getSession_missingToken_returnsNull() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        store.saveSession(session())
        keyValueStore.remove("token")

        assertNull(store.getSession())
    }

    @Test
    fun getSession_missingRefreshToken_returnsNull() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        store.saveSession(session())
        keyValueStore.remove("refresh_token")

        assertNull(store.getSession())
    }

    @Test
    fun signOut_wipesTheSessionAndTheLastFmCredentials() {
        val store = SessionStore(FakeKeyValueStore())
        val credentials = LastFmCredentials(
            connected = true,
            username = "ada",
            sessionKey = "session-key",
            apiKey = "api-key",
            apiSecret = "api-secret",
        )
        store.saveSession(session())
        store.saveLastFmCredentials(credentials)

        store.signOut()

        assertNull(store.getSession())
        assertFalse(store.hasSession())
        assertNull(store.getLastFmCredentials())
    }

    @Test
    fun clear_leavesTheStoreEmpty() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        val onboarding = OnboardingProgress(keyValueStore)
        store.saveSession(session())
        store.saveLastFmCredentials(LastFmCredentials(true, "ada", "sk", "ak", "as"))
        store.saveListenBrainzCredentials(ListenBrainzCredentials(true, "ada", "token"))
        onboarding.markPermissionsCompleted()

        keyValueStore.clear()

        assertTrue(keyValueStore.stored.isEmpty())
        assertNull(store.getSession())
        assertNull(store.getLastFmCredentials())
        assertNull(store.getListenBrainzCredentials())
        assertFalse(onboarding.hasCompletedPermissions)
    }

    @Test
    fun signOut_leavesTheOnboardingMarkerIntact() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        val onboarding = OnboardingProgress(keyValueStore)
        store.saveSession(session())
        onboarding.markPermissionsCompleted()

        store.signOut()

        assertFalse(store.hasSession())
        assertTrue(onboarding.hasCompletedPermissions)
    }

    @Test
    fun reset_clearsTheOnboardingMarker() {
        val keyValueStore = FakeKeyValueStore()
        val onboarding = OnboardingProgress(keyValueStore)
        onboarding.markPermissionsCompleted()

        onboarding.reset()

        assertFalse(onboarding.hasCompletedPermissions)
        assertFalse(keyValueStore.contains(ONBOARDING_KEY))
    }

    @Test
    fun reset_leavesTheSessionIntact() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        val onboarding = OnboardingProgress(keyValueStore)
        store.saveSession(session())
        onboarding.markPermissionsCompleted()

        onboarding.reset()

        assertFalse(onboarding.hasCompletedPermissions)
        assertTrue(store.hasSession())
    }

    @Test
    fun signOut_removesEveryKeyTheSessionStoreOwns() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        store.saveSession(session())
        store.saveLastFmCredentials(LastFmCredentials(true, "ada", "sk", "ak", "as"))
        store.saveListenBrainzCredentials(ListenBrainzCredentials(true, "ada", "token"))
        assertEquals(
            "the fixture must actually populate every key the class owns",
            OWNED_KEYS.toSet(),
            keyValueStore.stored.keys,
        )

        store.signOut()

        assertTrue("no key may survive sign-out", keyValueStore.stored.isEmpty())
    }

    @Test
    fun signOut_aStoreThatNeverHadLastFmCredentials_stillComesOutClean() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        store.saveSession(session())

        store.signOut()

        assertNull(store.getSession())
        assertNull(store.getLastFmCredentials())
        assertNull(store.getListenBrainzCredentials())
        assertTrue(keyValueStore.stored.isEmpty())
    }

    @Test
    fun signOut_calledTwice_isHarmless() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        store.saveSession(session())
        store.saveLastFmCredentials(LastFmCredentials(true, "ada", "sk", "ak", "as"))
        store.saveListenBrainzCredentials(ListenBrainzCredentials(true, "ada", "token"))

        store.signOut()
        store.signOut()

        assertNull(store.getSession())
        assertNull(store.getLastFmCredentials())
        assertNull(store.getListenBrainzCredentials())
        assertTrue(keyValueStore.stored.isEmpty())
    }

    @Test
    fun signingOutTouchesNothingButTheKeyRemovals() {
        val keyValueStore = RecordingKeyValueStore()
        val store = SessionStore(keyValueStore)
        val onboarding = OnboardingProgress(keyValueStore)
        store.saveSession(session())
        store.saveLastFmCredentials(LastFmCredentials(true, "ada", "sk", "ak", "as"))
        store.saveListenBrainzCredentials(ListenBrainzCredentials(true, "ada", "token"))
        onboarding.markPermissionsCompleted()
        keyValueStore.calls.clear()

        store.signOut()
        onboarding.reset()

        assertEquals(
            "sign-out and reset are pure key removals: no write, no read, no clear, no network",
            (OWNED_KEYS + ONBOARDING_KEY).map { "remove:$it" }.sorted(),
            keyValueStore.calls.sorted(),
        )
    }

    @Test
    fun theFullLogoutSequence_leavesTheStoreAsIfItHadNeverBeenUsed() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        val onboarding = OnboardingProgress(keyValueStore)
        store.saveSession(session())
        store.saveLastFmCredentials(LastFmCredentials(true, "ada", "sk", "ak", "as"))
        store.saveListenBrainzCredentials(ListenBrainzCredentials(true, "ada", "token"))
        onboarding.markPermissionsCompleted()

        store.signOut()
        onboarding.reset()

        assertNull(store.getSession())
        assertFalse(store.hasSession())
        assertNull(store.getLastFmCredentials())
        assertNull(store.getListenBrainzCredentials())
        assertFalse(onboarding.hasCompletedPermissions)
        assertTrue("not one key may survive logout", keyValueStore.stored.isEmpty())
    }

    @Test
    fun hasCompletedPermissions_defaultsToFalseUntilPermissionsAreGranted() {
        val keyValueStore = FakeKeyValueStore()
        val onboarding = OnboardingProgress(keyValueStore)

        assertFalse(onboarding.hasCompletedPermissions)

        onboarding.markPermissionsCompleted()

        assertTrue(onboarding.hasCompletedPermissions)
        assertTrue(
            "a marker written elsewhere is visible to a new reader",
            OnboardingProgress(keyValueStore).hasCompletedPermissions
        )
    }

    @Test
    fun theOnboardingMarkerAndTheSessionAreIndependentOnTheSameKeyValueStore() {
        val keyValueStore = FakeKeyValueStore()
        val store = SessionStore(keyValueStore)
        val onboarding = OnboardingProgress(keyValueStore)

        onboarding.markPermissionsCompleted()
        assertTrue(onboarding.hasCompletedPermissions)
        assertFalse("a marker must not conjure a session", store.hasSession())

        store.saveSession(session())
        assertTrue(store.hasSession())
        assertTrue("a session must not conjure a marker", onboarding.hasCompletedPermissions)
    }

    @Test
    fun saveSession_aLiteralNullUsername_cannotSurviveTheRoundTrip() {
        val store = SessionStore(FakeKeyValueStore())

        store.saveSession(
            session().copy(
                user = session().user.copy(
                    name = "null",
                    username = "NULL",
                    firstName = "NuLl",
                    lastName = "nUlL",
                ),
            ),
        )

        val user = requireNotNull(store.getSession()).user
        assertNull(user.name)
        assertNull(user.username)
        assertNull(user.firstName)
        assertNull(user.lastName)
    }

    @Test
    fun saveLastFmCredentials_aLiteralNullField_cannotSurviveTheRoundTrip() {
        val store = SessionStore(FakeKeyValueStore())

        store.saveLastFmCredentials(
            LastFmCredentials(
                connected = true,
                username = "null",
                sessionKey = "NULL",
                apiKey = "NuLl",
                apiSecret = "nUlL",
            ),
        )

        val credentials = requireNotNull(store.getLastFmCredentials())
        assertTrue(credentials.connected)
        assertNull(credentials.username)
        assertNull(credentials.sessionKey)
        assertNull(credentials.apiKey)
        assertNull(credentials.apiSecret)
    }

    @Test
    fun normalizedText_literalNullAndBlankText_becomeNull() {
        assertNull("null".normalizedText())
        assertNull("NULL".normalizedText())
        assertNull("NuLl".normalizedText())
        assertNull("  null  ".normalizedText())
        assertNull("".normalizedText())
        assertNull("   ".normalizedText())
        assertNull((null as String?).normalizedText())
    }

    @Test
    fun normalizedText_surroundingWhitespace_isTrimmedButKeptValues() {
        assertEquals("ada", "  ada  ".normalizedText())
        assertEquals("nulls", "nulls".normalizedText())
    }

    @Test
    fun normalized_authUserAndSession_normaliseTheUserFields() {
        val user = AuthUser(
            telegramId = 1L,
            name = " null ",
            username = "",
            firstName = "Ada",
            lastName = "Lovelace",
        )

        val normalizedUser = user.normalized()

        assertNull(normalizedUser.name)
        assertNull(normalizedUser.username)
        assertEquals("Ada", normalizedUser.firstName)
        assertEquals("Lovelace", normalizedUser.lastName)
        assertTrue("a normalising user stays bound", normalizedUser.isBound)
    }

    @Test
    fun normalized_lastFmCredentials_normalisesEveryField() {
        val credentials = LastFmCredentials(
            connected = false,
            username = "null",
            sessionKey = "key",
            apiKey = null,
            apiSecret = "",
        )

        val normalizedCredentials = credentials.normalized()

        assertNull(normalizedCredentials.username)
        assertEquals("key", normalizedCredentials.sessionKey)
        assertNull(normalizedCredentials.apiKey)
        assertNull(normalizedCredentials.apiSecret)
    }

    @Test
    fun isBound_onlyTheZeroTelegramIdIsTheSentinel() {
        assertFalse(AuthUser(AuthUser.NO_TELEGRAM_ID, null, null, null, null).isBound)
        assertTrue(AuthUser(1L, null, null, null, null).isBound)
        assertTrue(AuthUser(-1L, null, null, null, null).isBound)
    }

    @Test
    fun getLastFmCredentials_onlyEverDisconnected_returnsCredentialsRatherThanNull() {
        val store = SessionStore(FakeKeyValueStore())

        store.saveLastFmCredentials(
            LastFmCredentials(
                connected = false,
                username = null,
                sessionKey = null,
                apiKey = null,
                apiSecret = null
            )
        )

        val credentials = requireNotNull(store.getLastFmCredentials())
        assertFalse(credentials.connected)
    }

    private fun session() = AuthSession(
        serverUrl = "https://stream.example.com",
        token = "token",
        refreshToken = "refresh-token",
        expiresAtUnix = 1_700_000_000L,
        lyricspornApiUrl = "https://lyrics.example.com",
        user = AuthUser(
            telegramId = 42L,
            name = "Ada Lovelace",
            username = "ada",
            firstName = "Ada",
            lastName = "Lovelace",
        ),
    )

    private companion object {
        val SESSION_KEYS = listOf(
            "server_url",
            "token",
            "refresh_token",
            "expires_at",
            "lyricsporn_api_url",
            "tg_id",
            "user_name",
            "username",
            "first_name",
            "last_name",
        )

        val LASTFM_KEYS = listOf(
            "lastfm_connected",
            "lastfm_username",
            "lastfm_session_key",
            "lastfm_api_key",
            "lastfm_api_secret",
        )

        val LISTENBRAINZ_KEYS = listOf(
            "listenbrainz_connected",
            "listenbrainz_username",
            "listenbrainz_token",
        )

        val OWNED_KEYS = SESSION_KEYS + LASTFM_KEYS + LISTENBRAINZ_KEYS

        const val ONBOARDING_KEY = "permissions_completed"
    }
}

private class RecordingKeyValueStore : KeyValueStore {

    private val entries = mutableMapOf<String, Any?>()
    val calls = mutableListOf<String>()

    override fun getString(key: String): String? {
        calls += "getString:$key"
        return entries[key] as? String
    }

    override fun getLong(key: String): Long {
        calls += "getLong:$key"
        return entries[key] as? Long ?: 0L
    }

    override fun getBoolean(key: String): Boolean {
        calls += "getBoolean:$key"
        return entries[key] as? Boolean ?: false
    }

    override fun contains(key: String): Boolean {
        calls += "contains:$key"
        return entries.containsKey(key)
    }

    override fun putString(key: String, value: String) {
        calls += "putString:$key"
        entries[key] = value
    }

    override fun putLong(key: String, value: Long) {
        calls += "putLong:$key"
        entries[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        calls += "putBoolean:$key"
        entries[key] = value
    }

    override fun remove(key: String) {
        calls += "remove:$key"
        entries.remove(key)
    }

    override fun clear() {
        calls += "clear"
        entries.clear()
    }
}
