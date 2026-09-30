package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.playback.QueuePersistence
import org.shilpo.laboon.playback.QueuePersistenceCodec
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode

class QueuePersistenceTest {

    @Test
    fun testRoundTripKeepsItemsIndexAndToggles() {
        val store = FakeKeyValueStore()
        val persistence = QueuePersistence(store)
        val first = HomeTrack(
            id = "1",
            title = "Song One",
            artist = "Artist One",
            album = "Album",
            artworkUrl = "https://art/1.png",
            playCount = 12L,
            source = "Last.fm",
            streamUrl = "https://stream/1",
            backendTrackId = 7,
            isCached = true,
            codec = "alac",
            mbid = "abc-123",
        )
        val second = HomeTrack(id = "2", title = "Song\tTwo\nWith Break", artist = "Artist Two")
        val state = QueueState(
            items = listOf(first, second),
            currentIndex = 1,
            repeatMode = RepeatMode.ALL,
            isShuffle = true,
            isAutoplayEnabled = false,
        )

        persistence.save(state)

        val restored = persistence.load()
        assertEquals(state.items, restored.items)
        assertEquals(1, restored.currentIndex)
        assertEquals(RepeatMode.ALL, restored.repeatMode)
        assertTrue(restored.isShuffle)
        assertFalse(restored.isAutoplayEnabled)

        assertNull(restored.preShuffleOrder)
    }

    @Test
    fun testEmptyStoreLoadsAnEmptyQueue() {
        val persistence = QueuePersistence(FakeKeyValueStore())

        assertEquals(QueueState(), persistence.load())
    }

    @Test
    fun testClearRemovesTheStoredQueue() {
        val store = FakeKeyValueStore()
        val persistence = QueuePersistence(store)
        persistence.save(QueueState(items = listOf(HomeTrack("1", "Song", "Artist"))))

        persistence.clear()

        assertEquals(QueueState(), persistence.load())
    }

    @Test
    fun testDecodeDropsDuplicateIdentities() {
        val state = QueueState(
            items = listOf(
                HomeTrack(id = "1", title = "Song", artist = "Artist"),
                HomeTrack(id = "other", title = "Song (Remastered 2011)", artist = "Artist"),
            ),
            currentIndex = 1,
        )

        val restored = QueuePersistenceCodec.decode(QueuePersistenceCodec.encode(state))

        assertEquals(1, restored.items.size)
        assertEquals(0, restored.currentIndex)
        assertEquals(TrackIdentity.keyOf(state.items[0]), TrackIdentity.keyOf(restored.items[0]))
    }

    @Test
    fun testDecodeFallsBackToEmptyQueueForCorruptInput() {
        val header = "v1\t0\t0\t0\t1\t1"
        val corruptPayloads = listOf(
            "",
            "   ",
            "\n",
            "\n\n",
            "v2\t0\t0\t0\t1\t1",
            "v1\tnot-a-number\t0\t0\t1\t1",
            "v1\t0\t9\t0\t1\t1",
            "v1\t0\t0\t2\t1\t1",
            "v1\t0\t0\t0\t1\t0\t$header",
            "v1\t0\t0\t0\t1\t2",
            "$header\nnot-enough-fields",

            "$header\n${itemLine("1", "Song", "Artist", "", "", "0", "", "", "", "0", "", "\\")}",
            "$header\n${itemLine("1", "Song", "Artist", "", "", "0", "", "", "", "0", "", "\\q")}",

            "$header\n${itemLine("1", "", "Artist", "", "", "0", "", "", "", "0", "", "")}",
            "$header\n${itemLine("1", "Song", "Artist", "", "", "0", "", "", "", "0", "")}",

            "$header\n${itemLine("1", "Song", "Artist", "", "", "many", "", "", "", "0", "", "")}",
            "$header\n${itemLine("1", "Song", "Artist", "", "", "0", "", "", "", "7", "", "")}",
        )

        corruptPayloads.forEach { payload ->
            assertEquals("payload: $payload", QueueState(), QueuePersistenceCodec.decode(payload))
        }
    }

    @Test
    fun testDecodeFallsBackWhenOnlyTheHeaderSurvives() {
        val valid = QueuePersistenceCodec.encode(
            QueueState(
                items = listOf(HomeTrack(id = "1", title = "Song", artist = "Artist")),
                currentIndex = 0,
            )
        )

        assertEquals(QueueState(), QueuePersistenceCodec.decode(valid.substringBefore('\n')))
    }

    @Test
    fun testDecodeIgnoresATrailingNewline() {
        val valid = QueuePersistenceCodec.encode(
            QueueState(
                items = listOf(HomeTrack(id = "1", title = "Song", artist = "Artist")),
                currentIndex = 0,
            )
        )

        val restored = QueuePersistenceCodec.decode("$valid\n")

        assertEquals(1, restored.items.size)
        assertEquals(0, restored.currentIndex)
    }

    private fun itemLine(vararg fields: String): String = fields.joinToString("\t")
}
