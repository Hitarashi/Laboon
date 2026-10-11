package org.shilpo.laboon.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore

class ScrobbleQueueTest {

    @Test
    fun enqueue_and_peek_maintainsFifoOrder() {
        val store = FakeKeyValueStore()
        val queue = ScrobbleQueue(store)

        val item1 = QueuedScrobble(
            id = "1",
            artist = "Pink Floyd",
            track = "Time",
            album = "The Dark Side of the Moon",
            timestampEpochSeconds = 1000L,
            durationSeconds = 420L,
        )
        val item2 = QueuedScrobble(
            id = "2",
            artist = "Pink Floyd",
            track = "Money",
            album = "The Dark Side of the Moon",
            timestampEpochSeconds = 1500L,
            durationSeconds = 382L,
        )

        queue.enqueue(item1)
        queue.enqueue(item2)

        assertEquals(2, queue.size())
        val peeked = queue.peek(50)
        assertEquals(listOf(item1, item2), peeked)
    }

    @Test
    fun peek_respectsLimit() {
        val store = FakeKeyValueStore()
        val queue = ScrobbleQueue(store)

        for (i in 1..10) {
            queue.enqueue(QueuedScrobble(id = "$i", artist = "A", track = "T$i", timestampEpochSeconds = i.toLong()))
        }

        val peeked = queue.peek(3)
        assertEquals(3, peeked.size)
        assertEquals("1", peeked[0].id)
        assertEquals("2", peeked[1].id)
        assertEquals("3", peeked[2].id)
    }

    @Test
    fun remove_removesSpecifiedIds() {
        val store = FakeKeyValueStore()
        val queue = ScrobbleQueue(store)

        queue.enqueue(QueuedScrobble(id = "1", artist = "A", track = "T1", timestampEpochSeconds = 1L))
        queue.enqueue(QueuedScrobble(id = "2", artist = "A", track = "T2", timestampEpochSeconds = 2L))
        queue.enqueue(QueuedScrobble(id = "3", artist = "A", track = "T3", timestampEpochSeconds = 3L))

        queue.remove(setOf("1", "3"))

        assertEquals(1, queue.size())
        val remaining = queue.peek(10)
        assertEquals("2", remaining[0].id)
    }

    @Test
    fun capacityLimit_dropsOldestWhenExceeded() {
        val store = FakeKeyValueStore()
        val queue = ScrobbleQueue(store, maxCapacity = 3)

        for (i in 1..5) {
            queue.enqueue(QueuedScrobble(id = "$i", artist = "A", track = "T$i", timestampEpochSeconds = i.toLong()))
        }

        assertEquals(3, queue.size())
        val remaining = queue.peek(10)
        assertEquals(listOf("3", "4", "5"), remaining.map { it.id })
    }

    @Test
    fun persistence_newQueueOverSameStore_reloadsItems() {
        val store = FakeKeyValueStore()
        val queue1 = ScrobbleQueue(store)

        val item = QueuedScrobble(
            id = "persist-id",
            artist = "Daft Punk",
            track = "One More Time",
            album = "Discovery",
            timestampEpochSeconds = 1234567890L,
            durationSeconds = 320L,
        )
        queue1.enqueue(item)

        val queue2 = ScrobbleQueue(store)
        assertEquals(1, queue2.size())
        val loaded = queue2.peek(10)
        assertEquals(listOf(item), loaded)
    }

    @Test
    fun clear_emptiesQueue() {
        val store = FakeKeyValueStore()
        val queue = ScrobbleQueue(store)

        queue.enqueue(QueuedScrobble(id = "1", artist = "A", track = "T1", timestampEpochSeconds = 1L))
        queue.clear()

        assertEquals(0, queue.size())
        assertTrue(queue.peek(10).isEmpty())
    }
}
