package org.shilpo.laboon.auth

interface KeyValueStore {
    fun getString(key: String): String?
    fun getLong(key: String): Long
    fun getBoolean(key: String): Boolean
    fun contains(key: String): Boolean
    fun putString(key: String, value: String)
    fun putLong(key: String, value: Long)
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun clear()
}
