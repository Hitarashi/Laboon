package org.shilpo.laboon.rip

import org.json.JSONObject
import org.shilpo.laboon.net.objOrNull
import org.shilpo.laboon.net.stringOrNull

data class RipTaskDownloadLane(
    val stage: String,
    val title: String? = null,
    val artist: String? = null,
    val bytesDone: Long? = null,
    val bytesTotal: Long? = null,
    val percent: Float? = null,
    val speedBytesPerSec: Long = 0L,
    val lastProgressMs: Long = 0L,
    val codec: String? = null,
    val trackIndex: Int? = null,
    val totalTracks: Int? = null,
) {
    companion object {
        fun fromJson(
            json: JSONObject,
            previous: RipTaskDownloadLane? = null,
            now: Long = System.currentTimeMillis(),
            timeDeltaMs: Long = 0L,
        ): RipTaskDownloadLane {
            val stage = json.optString("stage", "resolving_metadata")
            val title = json.stringOrNull("title")
            val artist = json.stringOrNull("artist")
            val bytesDone =
                if (json.has("bytes_done") && !json.isNull("bytes_done")) json.optLong("bytes_done") else null
            val bytesTotal =
                if (json.has("bytes_total") && !json.isNull("bytes_total")) json.optLong("bytes_total") else null
            val percent =
                if (json.has("percent") && !json.isNull("percent")) json.optDouble("percent")
                    .toFloat() else null

            val codec = json.stringOrNull("codec")
            val trackIndex =
                if (json.has("track_index") && !json.isNull("track_index")) json.optInt("track_index") else null
            val totalTracks =
                if (json.has("total_tracks") && !json.isNull("total_tracks")) json.optInt("total_tracks") else null

            var speed = previous?.speedBytesPerSec ?: 0L
            var lastProgress = previous?.lastProgressMs ?: now

            if (bytesDone != null && previous?.bytesDone != null) {
                val byteDelta = bytesDone - previous.bytesDone
                if (byteDelta > 0) {
                    val elapsedMs =
                        if (timeDeltaMs > 0L) timeDeltaMs else (now - previous.lastProgressMs).coerceAtLeast(
                            60L
                        )
                    val instantSpeed = (byteDelta * 1000L) / elapsedMs
                    speed = if (previous.speedBytesPerSec > 0) {
                        (previous.speedBytesPerSec * 0.75f + instantSpeed * 0.25f).toLong()
                    } else {
                        instantSpeed
                    }
                    lastProgress = now
                } else if (byteDelta == 0L && previous.speedBytesPerSec > 0) {
                    val stalledMs = now - previous.lastProgressMs
                    if (stalledMs > 1500L) {
                        speed = (previous.speedBytesPerSec * 0.7f).toLong()
                        if (speed < 5000L || stalledMs > 3500L) speed = 0L
                    }
                    lastProgress = previous.lastProgressMs
                }
            } else if (bytesDone != null && previous?.bytesDone == null) {
                lastProgress = now
            } else if (bytesDone == null) {
                speed = 0L
                lastProgress = now
            }

            return RipTaskDownloadLane(
                stage = stage,
                title = title,
                artist = artist,
                bytesDone = bytesDone,
                bytesTotal = bytesTotal,
                percent = percent,
                speedBytesPerSec = speed,
                lastProgressMs = lastProgress,
                codec = codec,
                trackIndex = trackIndex,
                totalTracks = totalTracks,
            )
        }
    }
}

data class RipTaskUploadLane(
    val stage: String,
    val title: String? = null,
    val artist: String? = null,
    val bytesDone: Long? = null,
    val bytesTotal: Long? = null,
    val percent: Float? = null,
    val speedBytesPerSec: Long = 0L,
    val lastProgressMs: Long = 0L,
    val codec: String? = null,
    val trackIndex: Int? = null,
    val totalTracks: Int? = null,
) {
    companion object {
        fun fromJson(
            json: JSONObject,
            previous: RipTaskUploadLane? = null,
            now: Long = System.currentTimeMillis(),
            timeDeltaMs: Long = 0L,
        ): RipTaskUploadLane {
            val stage = json.optString("stage", "uploading_track")
            val title = json.stringOrNull("title")
            val artist = json.stringOrNull("artist")
            val bytesDone =
                if (json.has("bytes_done") && !json.isNull("bytes_done")) json.optLong("bytes_done") else null
            val bytesTotal =
                if (json.has("bytes_total") && !json.isNull("bytes_total")) json.optLong("bytes_total") else null
            val percent =
                if (json.has("percent") && !json.isNull("percent")) json.optDouble("percent")
                    .toFloat() else null
            val codec = json.stringOrNull("codec")
            val trackIndex =
                if (json.has("track_index") && !json.isNull("track_index")) json.optInt("track_index") else null
            val totalTracks =
                if (json.has("total_tracks") && !json.isNull("total_tracks")) json.optInt("total_tracks") else null

            var speed = previous?.speedBytesPerSec ?: 0L
            var lastProgress = previous?.lastProgressMs ?: now

            if (bytesDone != null && previous?.bytesDone != null) {
                val byteDelta = bytesDone - previous.bytesDone
                if (byteDelta > 0) {
                    val elapsedMs =
                        if (timeDeltaMs > 0L) timeDeltaMs else (now - previous.lastProgressMs).coerceAtLeast(
                            60L
                        )
                    val instantSpeed = (byteDelta * 1000L) / elapsedMs
                    speed = if (previous.speedBytesPerSec > 0) {
                        (previous.speedBytesPerSec * 0.75f + instantSpeed * 0.25f).toLong()
                    } else {
                        instantSpeed
                    }
                    lastProgress = now
                } else if (byteDelta == 0L && previous.speedBytesPerSec > 0) {
                    val stalledMs = now - previous.lastProgressMs
                    if (stalledMs > 1500L) {
                        speed = (previous.speedBytesPerSec * 0.7f).toLong()
                        if (speed < 5000L || stalledMs > 3500L) speed = 0L
                    }
                    lastProgress = previous.lastProgressMs
                }
            } else if (bytesDone != null && previous?.bytesDone == null) {
                lastProgress = now
            } else if (bytesDone == null) {
                speed = 0L
                lastProgress = now
            }

            return RipTaskUploadLane(
                stage = stage,
                title = title,
                artist = artist,
                bytesDone = bytesDone,
                bytesTotal = bytesTotal,
                percent = percent,
                speedBytesPerSec = speed,
                lastProgressMs = lastProgress,
                codec = codec,
                trackIndex = trackIndex,
                totalTracks = totalTracks,
            )
        }
    }
}

data class RipTaskSnapshot(
    val taskId: String,
    val provider: String,
    val sourceTrackId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Int? = null,
    val jobStage: String? = null,
    val download: RipTaskDownloadLane? = null,
    val upload: RipTaskUploadLane? = null,
    val percent: Float? = null,
    val resultTrackId: Int? = null,
    val isCached: Boolean? = null,
    val completed: Boolean = false,
    val error: String? = null,
    val ownerId: Long? = null,
    val isOwner: Boolean = false,
    val isAlbum: Boolean = false,
    val currentTrackTitle: String? = null,
    val currentTrackArtist: String? = null,
    val currentTrackIndex: Int? = null,
    val totalTracks: Int? = null,
    val completedTracks: Int? = null,
    val failedTracks: Int? = null,
    val lastUpdatedMs: Long = System.currentTimeMillis(),
) {
    companion object {
        fun fromJson(json: JSONObject, previous: RipTaskSnapshot? = null): RipTaskSnapshot {
            val now = System.currentTimeMillis()

            val downloadLane = json.objOrNull("download")?.let {
                RipTaskDownloadLane.fromJson(it, previous?.download, now)
            }
            val uploadLane = json.objOrNull("upload")?.let {
                RipTaskUploadLane.fromJson(it, previous?.upload, now)
            }

            return RipTaskSnapshot(
                taskId = json.optString("task_id"),
                provider = json.optString("provider", "apple"),
                sourceTrackId = json.optString("source_track_id"),
                title = json.stringOrNull("title"),
                artist = json.stringOrNull("artist"),
                album = json.stringOrNull("album"),
                duration = if (json.has("duration") && !json.isNull("duration")) json.optInt("duration") else null,
                jobStage = json.stringOrNull("job_stage"),
                download = downloadLane,
                upload = uploadLane,
                percent = if (json.has("percent") && !json.isNull("percent")) json.optDouble("percent")
                    .toFloat() else null,
                resultTrackId = if (json.has("result_track_id") && !json.isNull("result_track_id")) json.optInt(
                    "result_track_id"
                ) else null,
                isCached = if (json.has("is_cached") && !json.isNull("is_cached")) json.optBoolean("is_cached") else null,
                completed = json.optBoolean("completed", false),
                error = json.stringOrNull("error"),
                ownerId = if (json.has("owner_id") && !json.isNull("owner_id")) json.optLong("owner_id") else null,
                isOwner = json.optBoolean("is_owner", false),
                isAlbum = json.optBoolean("is_album", false),
                currentTrackTitle = json.stringOrNull("current_track_title"),
                currentTrackArtist = json.stringOrNull("current_track_artist"),
                currentTrackIndex = if (json.has("current_track_index") && !json.isNull("current_track_index")) json.optInt(
                    "current_track_index"
                ) else null,
                totalTracks = if (json.has("total_tracks") && !json.isNull("total_tracks")) json.optInt(
                    "total_tracks"
                ) else null,
                completedTracks = if (json.has("completed_tracks") && !json.isNull("completed_tracks")) json.optInt(
                    "completed_tracks"
                ) else null,
                failedTracks = if (json.has("failed_tracks") && !json.isNull("failed_tracks")) json.optInt(
                    "failed_tracks"
                ) else null,
                lastUpdatedMs = now,
            )
        }
    }
}

enum class RipWsStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class RipVisualizerState(
    val wsStatus: RipWsStatus = RipWsStatus.DISCONNECTED,
    val activeTasks: List<RipTaskSnapshot> = emptyList(),
    val serverUrl: String = "",
    val errorMessage: String? = null,
)
