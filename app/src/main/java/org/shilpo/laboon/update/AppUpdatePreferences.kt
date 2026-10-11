package org.shilpo.laboon.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppUpdatePreferences(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    )

    var lastCheckTimestamp: Long
        get() = preferences.getLong(KEY_LAST_CHECK_TIMESTAMP, 0L)
        set(value) = preferences.edit { putLong(KEY_LAST_CHECK_TIMESTAMP, value) }

    var lastNotifiedVersion: String?
        get() = preferences.getString(KEY_LAST_NOTIFIED_VERSION, null)
        set(value) = preferences.edit {
            if (value == null) {
                remove(KEY_LAST_NOTIFIED_VERSION)
            } else {
                putString(KEY_LAST_NOTIFIED_VERSION, value)
            }
        }

    var autoCheckEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTO_CHECK_ENABLED, true)
        set(value) = preferences.edit { putBoolean(KEY_AUTO_CHECK_ENABLED, value) }

    fun shouldAutoCheck(cooldownMs: Long = DEFAULT_COOLDOWN_MS): Boolean {
        if (!autoCheckEnabled) return false
        val last = lastCheckTimestamp
        if (last <= 0L) return true
        val elapsed = System.currentTimeMillis() - last
        return elapsed >= cooldownMs || elapsed < 0L
    }

    fun recordCheckTime(timestamp: Long = System.currentTimeMillis()) {
        lastCheckTimestamp = timestamp
    }

    companion object {
        const val PREFERENCES_NAME = "laboon_updates"
        const val KEY_LAST_CHECK_TIMESTAMP = "last_check_timestamp"
        const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
        const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
        const val DEFAULT_COOLDOWN_MS = 12 * 60 * 60 * 1000L
    }
}
