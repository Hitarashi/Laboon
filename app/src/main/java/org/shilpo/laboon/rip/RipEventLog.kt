package org.shilpo.laboon.rip

import org.json.JSONObject

/**
 * Turns a rip websocket event into a flat, loggable description.
 *
 * The user-facing question this answers is "when we get rip done, what data do we actually
 * get?", so every field the server might send is surfaced rather than only the ones the UI
 * happens to read. Pure and side-effect free so it can be unit tested without a socket.
 */
object RipEventLog {

    private const val MAX_RAW_CHARS = 1200

    /** Scalar fields worth capturing, in a stable order. */
    private val SCALAR_KEYS = listOf(
        "task_id",
        "provider",
        "source_track_id",
        "result_track_id",
        "track_id",
        "status",
        "job_stage",
        "stage",
        "completed",
        "is_cached",
        "is_album",
        "is_owner",
        "owner_id",
        "percent",
        "codec",
        "format",
        "formats",
        "available_formats",
        "file_path",
        "filepath",
        "path",
        "output_path",
        "local_path",
        "file_url",
        "url",
        "stream_url",
        "playback_url",
        "result_url",
        "size_bytes",
        "file_size_bytes",
        "duration",
        "title",
        "artist",
        "album",
        "created_at",
        "updated_at",
        "completed_at",
        "finished_at",
        "started_at",
        "error",
        "message",
    )

    /** Nested objects summarised as `label(key=value, ...)`. */
    private val NESTED_KEYS = listOf("download", "upload", "result", "track", "file")

    /**
     * Describes [payload] for a given ws [type]. Returns a single string suitable for Logcat.
     * Missing fields are omitted, so a terse server response stays terse.
     */
    fun describe(type: String, payload: JSONObject?): String {
        if (payload == null) return "type=$type payload=<absent>"
        val parts = ArrayList<String>(SCALAR_KEYS.size + NESTED_KEYS.size)
        parts += "type=$type"

        for (key in SCALAR_KEYS) {
            val value = readable(payload, key) ?: continue
            parts += "$key=$value"
        }
        for (key in NESTED_KEYS) {
            val nested = payload.optJSONObject(key) ?: continue
            val summary = describeObject(nested)
            if (summary.isNotEmpty()) parts += "$key{$summary}"
        }
        return parts.joinToString(" ")
    }

    /** Raw payload (length-capped) so an unexpected shape is still visible in logs. */
    fun raw(type: String, payload: JSONObject?): String {
        val text = payload?.toString() ?: return "type=$type payload=<absent>"
        val capped =
            if (text.length <= MAX_RAW_CHARS) text else text.take(MAX_RAW_CHARS) + "...(truncated)"
        return "type=$type raw=$capped"
    }

    private fun describeObject(obj: JSONObject): String {
        val parts = ArrayList<String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = readable(obj, key) ?: continue
            parts += "$key=$value"
        }
        return parts.joinToString(", ")
    }

    /**
     * Reads [key] as a non-null, non-empty scalar. Nested objects/arrays are summarised rather
     * than dumped so one noisy field cannot flood the log line.
     */
    private fun readable(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return when (val value = obj.opt(key)) {
            is String -> value.trim().takeIf(String::isNotEmpty)
            is Boolean, is Number -> value.toString()
            is JSONObject -> describeObject(value).takeIf(String::isNotEmpty)
            else -> value.toString().trim().takeIf(String::isNotEmpty)
        }
    }
}
