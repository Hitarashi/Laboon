package org.shilpo.laboon.rip

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.auth.SharedPreferencesKeyValueStore
import org.shilpo.laboon.search.SearchRepositoryImpl
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application-scoped owner of the rip websocket and the auto-rip coordinator.
 *
 * Previously the socket lived inside HomeScreen's composition: it was created by `remember`,
 * started by a `DisposableEffect` keyed on session credentials, and stopped when the composable
 * left composition. That tied a long-lived, session-scoped resource to a screen's lifecycle, so
 * navigating away tore down the socket (losing rip events and forcing a reconnect) and a
 * recomposition could churn it.
 *
 * This holder lifts both objects out of the UI. They are created once per process and driven
 * by session availability:
 * - a valid session (server url + token present) starts the socket and the coordinator,
 * - losing or changing the session stops both,
 * - screen navigation does neither.
 *
 * Screens keep calling [observe] / [startRip] / reading [state]; they just no longer own the
 * connection. Follows the existing [org.shilpo.laboon.playback.PlaybackManagerHolder] pattern.
 */
class RipConnectionHolder internal constructor(
    private val appContext: Context,
    val sessionStore: SessionStore,
) {
    val ripClient: RipWebSocketClient = RipWebSocketClient(sessionStore)

    val searchRepository: SearchRepositoryImpl = SearchRepositoryImpl(sessionStore)

    val autoRip: AutoRipCoordinator = AutoRipCoordinator(
        sessionStore = sessionStore,
        availabilityLookup = searchRepository,
        cache = AutoRipCache(SharedPreferencesKeyValueStore(appContext)),
        startRip = ripClient::startRip,

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Provider track ids whose rip completed successfully. Emitted for every successful
     * completion so any holder of app state can re-apply availability without a manual reload.
     */
    val completedRipTrackIds: SharedFlow<String> = ripClient.completedRipTrackIds

    init {
        scope.launch {
            ripClient.completedRipTrackIds.collect { providerTrackId ->
                autoRip.onRipCompleted(providerTrackId)
            }
        }
    }

    /**
     * Reconciles live objects against the current session. Idempotent, so it is safe to call
     * from any number of lifecycle callbacks. Returns true when a change was applied.
     */
    fun syncWithSession(): Boolean {
        val session = sessionStore.getSession()
        val isUsable = session != null &&
                session.serverUrl.isNotBlank() &&
                session.token.isNotBlank()

        return if (isUsable) {
            val changed = started.compareAndSet(false, true)
            if (changed) {
                Log.i(TAG, "session present; starting rip socket + auto-rip coordinator")
                ripClient.start()
                autoRip.start()
            }
            changed
        } else {
            val changed = started.compareAndSet(true, false)
            if (changed) {
                Log.i(TAG, "no usable session; stopping rip socket + auto-rip coordinator")
                autoRip.stop()
                ripClient.stop()
            }
            changed
        }
    }

    /** Stops everything, e.g. on sign-out. */
    fun release() {
        if (started.compareAndSet(true, false)) {
            Log.i(TAG, "releasing rip connection")
            autoRip.stop()
            ripClient.stop()
        }
    }

    private val started = AtomicBoolean(false)

    private companion object {
        const val TAG = "RipConnection"
    }
}

/** Process-wide [RipConnectionHolder], mirroring [org.shilpo.laboon.playback.PlaybackManagerHolder]. */
object RipConnectionHolderInstance {
    private var instance: RipConnectionHolder? = null
    private val lock = Any()

    fun getInstance(context: Context, sessionStore: SessionStore): RipConnectionHolder =
        synchronized(lock) {
            instance ?: RipConnectionHolder(
                context.applicationContext,
                sessionStore,
            ).also { instance = it }
        }

    fun release() {
        synchronized(lock) {
            instance?.release()
            instance = null
        }
    }
}
