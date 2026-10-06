package org.shilpo.laboon.net

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.objOrNull(key: String): JSONObject? = optJSONObject(key)

fun JSONObject.arrOrNull(key: String): JSONArray? = optJSONArray(key)

fun JSONObject.stringOrNull(key: String): String? {
    if (isNull(key)) return null
    val raw = opt(key)
    val text = when {
        raw is String -> raw
        raw is Number -> raw.toString()
        raw is Boolean -> raw.toString()
        else -> null
    }
    return text?.trim()?.ifEmpty { null }
}

fun JSONArray.objAtOrNull(i: Int): JSONObject? = optJSONObject(i)

fun Any?.asJsonArrayOrNull(): JSONArray? = when (this) {
    is JSONArray -> this
    is JSONObject -> JSONArray().put(this)
    is String -> try {
        JSONArray(this)
    } catch (_: org.json.JSONException) {
        null
    }

    else -> null
}

fun Any?.asJsonObjectOrNull(): JSONObject? = when (this) {
    is JSONObject -> this
    is String -> try {
        JSONObject(this)
    } catch (_: org.json.JSONException) {
        null
    }

    else -> null
}
