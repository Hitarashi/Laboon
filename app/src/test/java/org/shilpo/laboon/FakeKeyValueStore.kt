package org.shilpo.laboon

import org.shilpo.laboon.auth.KeyValueStore

class FakeKeyValueStore : KeyValueStore {

    private val entries = mutableMapOf<String, Any?>()

    val stored: Map<String, Any?> = entries

    override fun getString(key: String): String? = entries[key] as? String

    override fun getLong(key: String): Long = entries[key] as? Long ?: 0L

    override fun getBoolean(key: String): Boolean = entries[key] as? Boolean ?: false

    override fun contains(key: String): Boolean = entries.containsKey(key)

    override fun putString(key: String, value: String) {
        entries[key] = value
    }

    override fun putLong(key: String, value: Long) {
        entries[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        entries[key] = value
    }

    override fun remove(key: String) {
        entries.remove(key)
    }

    override fun clear() {
        entries.clear()
    }
}
