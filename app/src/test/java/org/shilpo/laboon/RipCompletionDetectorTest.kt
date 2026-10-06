package org.shilpo.laboon

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.rip.RipCompletionDetector

class RipCompletionDetectorTest {

    private fun task(vararg pairs: Pair<String, Any?>): JSONObject {
        val json = JSONObject()
        pairs.forEach { (key, value) -> json.put(key, value) }
        return json
    }

    @Test
    fun `a completed flag yields the provider track id`() {
        val result = RipCompletionDetector.completedProviderTrackId(
            "rip_task_updated",
            task("task_id" to "t1", "source_track_id" to "111", "completed" to true),
        )

        assertEquals("111", result)
    }

    @Test
    fun `a terminal event type yields the provider track id`() {
        val result = RipCompletionDetector.completedProviderTrackId(
            "rip_task_completed",
            task("source_track_id" to "222"),
        )

        assertEquals("222", result)
    }

    @Test
    fun `a completion job stage yields the provider track id`() {
        val result = RipCompletionDetector.completedProviderTrackId(
            "rip_task_updated",
            task("source_track_id" to "333", "job_stage" to "completed"),
        )

        assertEquals("333", result)
    }

    @Test
    fun `an in progress update is not a completion`() {
        val result = RipCompletionDetector.completedProviderTrackId(
            "rip_task_updated",
            task("source_track_id" to "444", "job_stage" to "downloading", "completed" to false),
        )

        assertNull(result)
    }

    @Test
    fun `a task carrying an error is never treated as a successful completion`() {
        assertNull(
            RipCompletionDetector.completedProviderTrackId(
                "rip_task_updated",
                task("source_track_id" to "555", "completed" to true, "error" to "decoder failed"),
            )
        )
    }

    @Test
    fun `a failed status is never treated as a successful completion`() {
        assertNull(
            RipCompletionDetector.completedProviderTrackId(
                "rip_task_updated",
                task("source_track_id" to "666", "status" to "failed"),
            )
        )
    }

    @Test
    fun `an empty error field does not block a real completion`() {
        assertEquals(
            "777",
            RipCompletionDetector.completedProviderTrackId(
                "rip_task_updated",
                task("source_track_id" to "777", "completed" to true, "error" to ""),
            ),
        )
    }

    @Test
    fun `a non provider shaped id is rejected`() {
        assertNull(
            RipCompletionDetector.completedProviderTrackId(
                "rip_task_updated",
                task("source_track_id" to "not-an-id", "completed" to true),
            )
        )
    }

    @Test
    fun `a missing task payload is not a completion`() {
        assertNull(RipCompletionDetector.completedProviderTrackId("rip_task_updated", null))
    }

    @Test
    fun `an unknown event type is ignored even when marked complete`() {
        assertNull(
            RipCompletionDetector.completedProviderTrackId(
                "some_other_event",
                task("source_track_id" to "888", "completed" to true),
            )
        )
    }

    @Test
    fun `isCompletion separates terminal types from plain updates`() {
        assertTrue(
            RipCompletionDetector.isCompletion(
                "rip_task_finished",
                task("source_track_id" to "1"),
            )
        )
        assertTrue(
            !RipCompletionDetector.isCompletion(
                "rip_task_updated",
                task("source_track_id" to "1", "job_stage" to "uploading"),
            )
        )
    }
}
