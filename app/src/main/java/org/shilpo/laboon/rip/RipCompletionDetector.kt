package org.shilpo.laboon.rip

import org.json.JSONObject

/**
 * Decides whether an incoming rip websocket event means a rip finished successfully, and
 * returns the provider track id it applies to.
 *
 * Pure so completion can be asserted without a socket. "Successful" deliberately excludes any
 * task carrying an error, so a failed rip never marks a track as playable.
 */
object RipCompletionDetector {

    private val TERMINAL_TYPES = setOf(
        "rip_task_completed",
        "rip_task_finished",
        "rip_task_done",
    )

    private val UPDATE_TYPES = setOf("rip_task_updated", "rip_task_created")

    private val COMPLETION_MARKERS = listOf(
        "complete",
        "completed",
        "done",
        "finished",
        "succeeded",
        "success",
        "uploaded",
    )

    private val FAILURE_MARKERS = listOf(
        "fail",
        "error",
        "cancel",
        "abort",
        "revoke",
    )

    fun isAlbum(task: JSONObject?): Boolean = task?.optBoolean("is_album") == true

    /**
     * Returns the provider track id whose rip completed successfully, or null when the event
     * is not a successful completion (in-progress updates, failures, unusable ids).
     */
    fun completedProviderTrackId(type: String, task: JSONObject?): String? {
        if (task == null) return null
        if (isAlbum(task)) return null
        if (hasFailed(task)) return null
        if (!isCompletion(type, task)) return null
        return normalizeId(task.optString("source_track_id"))
    }

    fun completedProviderAlbumId(type: String, task: JSONObject?): String? {
        if (task == null || !isAlbum(task) || hasFailed(task) || !isCompletion(
                type,
                task
            )
        ) return null
        return normalizeId(task.optString("source_track_id"))
    }

    fun isCompletion(type: String, task: JSONObject?): Boolean {
        if (task == null) return false
        if (type in TERMINAL_TYPES) return true
        if (type !in UPDATE_TYPES) return false
        if (task.optBoolean("completed")) return true
        return listOf("job_stage", "status", "stage")
            .mapNotNull { key ->
                if (!task.has(key) || task.isNull(key)) return@mapNotNull null
                task.optString(key).trim().takeIf(String::isNotEmpty)
            }
            .any { value -> COMPLETION_MARKERS.any { value.contains(it, ignoreCase = true) } }
    }

    private fun hasFailed(task: JSONObject): Boolean {
        for (key in ERROR_KEYS) {
            if (!task.has(key) || task.isNull(key)) continue
            val value = task.optString(key).trim()
            if (value.isNotEmpty()) return true
        }
        return task.optBoolean("failed") || listOf("job_stage", "status", "stage")
            .mapNotNull { key ->
                if (!task.has(key) || task.isNull(key)) return@mapNotNull null
                task.optString(key).trim().takeIf(String::isNotEmpty)
            }
            .any { value -> FAILURE_MARKERS.any { value.contains(it, ignoreCase = true) } }
    }

    private fun normalizeId(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    private val ERROR_KEYS = listOf("error", "error_message", "failure_reason")
}
