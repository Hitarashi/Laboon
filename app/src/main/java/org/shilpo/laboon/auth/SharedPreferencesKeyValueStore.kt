package org.shilpo.laboon.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferencesKeyValueStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences

    init {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun getLong(key: String): Long = prefs.getLong(key, 0L)

    override fun getBoolean(key: String): Boolean = prefs.getBoolean(key, false)

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun putString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    override fun putLong(key: String, value: Long) {
        prefs.edit { putLong(key, value) }
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    override fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    override fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val PREFS_NAME = "laboon_auth_storage"
    }
}
