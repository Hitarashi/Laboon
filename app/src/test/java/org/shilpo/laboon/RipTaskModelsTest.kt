package org.shilpo.laboon

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.rip.RipTaskDownloadLane
import org.shilpo.laboon.rip.RipTaskSnapshot
import org.shilpo.laboon.rip.RipTaskUploadLane

class RipTaskModelsTest {

    @Test
    fun parseDownloadLane_calculatesDerivedSpeed() {
        val prevJson = JSONObject(
            """
            {
                "stage": "downloading",
                "bytes_done": 1048576,
                "bytes_total": 10485760,
                "percent": 10.0
            }
        """.trimIndent()
        )
        val prevLane = RipTaskDownloadLane.fromJson(prevJson)

        val nextJson = JSONObject(
            """
            {
                "stage": "downloading",
                "bytes_done": 3145728,
                "bytes_total": 10485760,
                "percent": 30.0
            }
        """.trimIndent()
        )
        val nextLane =
            RipTaskDownloadLane.fromJson(nextJson, previous = prevLane, timeDeltaMs = 500L)

        assertEquals("downloading", nextLane.stage)
        assertEquals(3145728L, nextLane.bytesDone)
        assertEquals(10485760L, nextLane.bytesTotal)
        assertEquals(30.0f, nextLane.percent)
        assertEquals(4194304L, nextLane.speedBytesPerSec)
    }

    @Test
    fun parseUploadLane_handlesNullBytesGracefully() {
        val json = JSONObject(
            """
            {
                "stage": "building_archive",
                "bytes_done": null,
                "bytes_total": null,
                "percent": null
            }
        """.trimIndent()
        )
        val lane = RipTaskUploadLane.fromJson(json)

        assertEquals("building_archive", lane.stage)
        assertNull(lane.bytesDone)
        assertNull(lane.bytesTotal)
        assertNull(lane.percent)
        assertEquals(0L, lane.speedBytesPerSec)
    }

    @Test
    fun parseRipTaskSnapshot_allFieldsPopulateCorrectly() {
        val json = JSONObject(
            """
            {
                "task_id": "task_abc123",
                "provider": "apple",
                "source_track_id": "1440857781",
                "codec": "alac",
                "title": "Anti-Hero",
                "artist": "Taylor Swift",
                "album": "Midnights",
                "duration": 200,
                "job_stage": "resolving",
                "download": {
                    "stage": "downloading",
                    "title": "Anti-Hero",
                    "artist": "Taylor Swift",
                    "bytes_done": 4000000,
                    "bytes_total": 10000000,
                    "percent": 40.0,
                    "codec": "alac",
                    "track_index": 3,
                    "total_tracks": 13
                },
                "upload": {
                    "stage": "uploading_track",
                    "title": "Anti-Hero",
                    "artist": "Taylor Swift",
                    "bytes_done": 2000000,
                    "bytes_total": 10000000,
                    "percent": 20.0,
                    "codec": "alac",
                    "track_index": 2,
                    "total_tracks": 13
                },
                "percent": 35.5,
                "result_track_id": 42,
                "is_cached": false,
                "completed": false,
                "error": null,
                "is_owner": true,
                "is_album": true,
                "current_track_title": "Anti-Hero",
                "current_track_artist": "Taylor Swift",
                "current_track_index": 3,
                "total_tracks": 13,
                "completed_tracks": 2,
                "failed_tracks": 1
            }
        """.trimIndent()
        )

        val snapshot = RipTaskSnapshot.fromJson(json)

        assertEquals("task_abc123", snapshot.taskId)
        assertEquals("apple", snapshot.provider)
        assertEquals("1440857781", snapshot.sourceTrackId)
        assertEquals("Anti-Hero", snapshot.title)
        assertEquals("Taylor Swift", snapshot.artist)
        assertEquals("Midnights", snapshot.album)
        assertEquals(200, snapshot.duration)
        assertEquals("resolving", snapshot.jobStage)
        assertNotNull(snapshot.download)
        assertEquals("downloading", snapshot.download?.stage)
        assertEquals("alac", snapshot.download?.codec)
        assertEquals(3, snapshot.download?.trackIndex)
        assertEquals(13, snapshot.download?.totalTracks)
        assertNotNull(snapshot.upload)
        assertEquals("uploading_track", snapshot.upload?.stage)
        assertEquals("alac", snapshot.upload?.codec)
        assertEquals(2, snapshot.upload?.trackIndex)
        assertEquals(13, snapshot.upload?.totalTracks)
        assertEquals(35.5f, snapshot.percent)
        assertEquals(42, snapshot.resultTrackId)
        assertTrue(snapshot.isOwner)
        assertTrue(snapshot.isAlbum)
        assertEquals(3, snapshot.currentTrackIndex)
        assertEquals(13, snapshot.totalTracks)
        assertEquals(2, snapshot.completedTracks)
        assertEquals(1, snapshot.failedTracks)
    }
}
