package org.shilpo.laboon.home

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.shilpo.laboon.net.HttpOutcome

/** Shared identification, pacing, and basic 429 recovery for ListenBrainz and Labs requests. */
object ListenBrainzRequestPolicy {
    const val USER_AGENT = "Laboon/1.0 (Android music client)"
    private const val MIN_REQUEST_INTERVAL_MS = 1_000L
    private const val RATE_LIMIT_RETRY_DELAY_MS = 1_200L

    private val mutex = Mutex()
    private var lastRequestAtMs = 0L

    suspend fun <T> execute(request: suspend () -> HttpOutcome<T>): HttpOutcome<T> =
        mutex.withLock {
            val waitMs = MIN_REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAtMs)
            if (waitMs > 0L) delay(waitMs)
            var response = request()
            lastRequestAtMs = System.currentTimeMillis()
            if (response is HttpOutcome.Failure && response.error.statusCode == 429) {
                delay(RATE_LIMIT_RETRY_DELAY_MS)
                response = request()
                lastRequestAtMs = System.currentTimeMillis()
            }
            response
        }
}
