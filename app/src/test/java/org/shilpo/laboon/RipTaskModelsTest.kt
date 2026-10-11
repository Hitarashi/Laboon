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
                "source_track_id": "1440857781",
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

    @Test
    fun parseUploadLane_populatesWorkerIdAndDcId() {
        val json = JSONObject(
            """
            {
                "stage": "uploading_track",
                "title": "Lavender Haze",
                "bytes_done": 1048576,
                "bytes_total": 5242880,
                "percent": 20.0,
                "worker_id": 3,
                "dc_id": 1
            }
        """.trimIndent()
        )
        val lane = RipTaskUploadLane.fromJson(json)

        assertEquals("uploading_track", lane.stage)
        assertEquals(3, lane.workerId)
        assertEquals(1, lane.dcId)
    }

    @Test
    fun parseRipTaskSnapshot_concurrentUploads_matchesTrackIndexForSmoothSpeed() {
        val step1Json = JSONObject(
            """
            {
                "task_id": "task_1",
                "source_track_id": "album_1",
                "uploads": [
                    {
                        "stage": "uploading_track",
                        "title": "Track 1",
                        "track_index": 1,
                        "bytes_done": 1000000,
                        "bytes_total": 5000000,
                        "worker_id": 1,
                        "dc_id": 1
                    },
                    {
                        "stage": "uploading_track",
                        "title": "Track 2",
                        "track_index": 2,
                        "bytes_done": 500000,
                        "bytes_total": 4000000,
                        "worker_id": 2,
                        "dc_id": 5
                    }
                ]
            }
        """.trimIndent()
        )
        val snapshot1 = RipTaskSnapshot.fromJson(step1Json)
        assertEquals(2, snapshot1.uploads.size)
        assertEquals(1, snapshot1.uploads[0].workerId)
        assertEquals(1, snapshot1.uploads[0].dcId)
        assertEquals(2, snapshot1.uploads[1].workerId)
        assertEquals(5, snapshot1.uploads[1].dcId)

        val step2Json = JSONObject(
            """
            {
                "task_id": "task_1",
                "source_track_id": "album_1",
                "uploads": [
                    {
                        "stage": "uploading_track",
                        "title": "Track 1",
                        "track_index": 1,
                        "bytes_done": 2000000,
                        "bytes_total": 5000000,
                        "worker_id": 1,
                        "dc_id": 1
                    },
                    {
                        "stage": "uploading_track",
                        "title": "Track 2",
                        "track_index": 2,
                        "bytes_done": 1500000,
                        "bytes_total": 4000000,
                        "worker_id": 2,
                        "dc_id": 5
                    }
                ]
            }
        """.trimIndent()
        )
        val snapshot2 = RipTaskSnapshot.fromJson(step2Json, previous = snapshot1)
        assertEquals(2, snapshot2.uploads.size)

        // Both lanes should have positive smooth speeds calculated without interfering with each other
        val lane1 = snapshot2.uploads[0]
        val lane2 = snapshot2.uploads[1]
        assertEquals("Track 1", lane1.title)
        assertEquals(2000000L, lane1.bytesDone)
        assertTrue("lane 1 should have positive speed", lane1.speedBytesPerSec > 0)

        assertEquals("Track 2", lane2.title)
        assertEquals(1500000L, lane2.bytesDone)
        assertTrue("lane 2 should have positive speed", lane2.speedBytesPerSec > 0)
    }
}
