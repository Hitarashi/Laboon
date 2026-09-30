package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.playback.QueueManagerImpl
import org.shilpo.laboon.playback.RepeatMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class QueueManagerTest {

    private fun testTrack(id: String, title: String, artist: String = "Test Artist") =
        HomeTrack(id = id, title = title, artist = artist)

    @Test
    fun testPlayInitializesSingleOrderedQueue() {
        val qm = QueueManagerImpl()
        val t1 = testTrack("1", "Song 1")
        val t2 = testTrack("2", "Song 2")
        val context = listOf(t1, t2)

        qm.play(t1, context)

        val state = qm.state.value
        assertEquals(listOf(t1, t2), state.items)
        assertEquals(0, state.currentIndex)
        assertEquals(t1, state.currentTrack)
        assertEquals(1, state.upNextCount)
        assertTrue(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun testPlayNextInsertsDirectlyAfterCurrent() {
        val qm = QueueManagerImpl()
        val current = testTrack("0", "Playing")
        qm.play(current)

        val next1 = testTrack("1", "Next 1")
        val next2 = testTrack("2", "Next 2")

        qm.playNext(next1)
        qm.playNext(next2)


        assertEquals(listOf(current, next2, next1), qm.state.value.items)
        assertEquals(next2, qm.peekNext())
    }

    @Test
    fun testAddToQueueAppendsToEnd() {
        val qm = QueueManagerImpl()
        val current = testTrack("0", "Playing")
        qm.play(current)

        val t1 = testTrack("1", "Q1")
        val t2 = testTrack("2", "Q2")

        qm.addToQueue(t1)
        qm.addToQueue(t2)

        assertEquals(listOf(current, t1, t2), qm.state.value.items)
        assertEquals(2, qm.state.value.upNextCount)
    }

    @Test
    fun testPlayNextMovesQueuedTrackInsteadOfDuplicating() {
        val qm = QueueManagerImpl()
        val current = testTrack("0", "Playing")
        val staged = testTrack("1", "Staged")
        val queued = testTrack("2", "Queued")
        qm.play(current)
        qm.playNext(staged)
        qm.addToQueue(queued)

        qm.playNext(queued)

        assertEquals(listOf(current, queued, staged), qm.state.value.items)
        assertEquals(3, qm.state.value.items.size)
        assertEquals(queued, qm.peekNext())
    }

    @Test
    fun testAddToQueueSkipsTrackAlreadyQueued() {
        val qm = QueueManagerImpl()
        val current = testTrack("0", "Playing")
        val queued = testTrack("1", "Queued")
        qm.play(current)
        qm.addToQueue(queued)

        qm.addToQueue(queued.copy(id = "duplicate", title = "Queued (Remastered)"))

        assertEquals(listOf(current, queued), qm.state.value.items)
    }

    @Test
    fun testAdvanceFollowsTheOrderedQueue() {
        val qm = QueueManagerImpl()
        val c1 = testTrack("1", "Context 1")
        val c2 = testTrack("2", "Context 2")
        val upNextTrack = testTrack("u1", "Up Next")

        qm.play(c1, listOf(c1, c2))
        qm.addToQueue(upNextTrack)

        assertEquals(c2, qm.peekNext())
        assertEquals(c2, qm.advanceToNext())
        assertEquals(c2, qm.state.value.currentTrack)

        assertEquals(upNextTrack, qm.advanceToNext())
        assertEquals(upNextTrack, qm.state.value.currentTrack)
        assertEquals(0, qm.state.value.upNextCount)

        assertNull(qm.advanceToNext())
    }

    @Test
    fun testAdvanceContinuesIntoAppendedDiscovery() {
        val qm = QueueManagerImpl()
        val c1 = testTrack("1", "Single Song")
        qm.play(c1, listOf(c1))

        val d1 = testTrack("d1", "Discovered 1")
        val d2 = testTrack("d2", "Discovered 2")
        qm.appendDiscovery(listOf(d1, d2))

        assertEquals(d1, qm.peekNext())
        assertEquals(d1, qm.advanceToNext())
        assertEquals(listOf(c1, d1, d2), qm.state.value.items)

        assertEquals(d2, qm.advanceToNext())
        assertNull(qm.advanceToNext())
    }

    @Test
    fun testAppendDiscoverySkipsAlreadyQueuedTracks() {
        val qm = QueueManagerImpl()
        val c1 = testTrack("1", "First")
        val c2 = testTrack("2", "Second")
        qm.play(c1, listOf(c1, c2))
        qm.advanceToNext()

        val d1 = testTrack("d1", "Discovered")
        qm.appendDiscovery(listOf(c1, d1))

        assertEquals(listOf(c1, c2, d1), qm.state.value.items)
    }

    @Test
    fun testAdvancePreviousFromDiscoveryBackToLastUserAddedItem() {
        val qm = QueueManagerImpl()
        val current = testTrack("1", "First")
        val userAdded = testTrack("2", "Queued By User")
        qm.play(current, listOf(current))
        qm.addToQueue(userAdded)
        qm.advanceToNext()

        val discovered = testTrack("d1", "Discovered")
        qm.appendDiscovery(listOf(discovered))
        assertEquals(discovered, qm.advanceToNext())

        assertEquals(userAdded, qm.advanceToPrevious())
        assertEquals(userAdded, qm.state.value.currentTrack)
        assertEquals(current, qm.advanceToPrevious())
        assertEquals(current, qm.state.value.currentTrack)
    }

    @Test
    fun testRepeatAllWrapsToStart() {
        val qm = QueueManagerImpl()
        val t1 = testTrack("1", "One")
        val t2 = testTrack("2", "Two")
        qm.play(t1, listOf(t1, t2))
        qm.cycleRepeatMode()
        assertEquals(RepeatMode.ALL, qm.state.value.repeatMode)

        assertEquals(t2, qm.advanceToNext())
        assertEquals(t1, qm.advanceToNext())
        assertEquals(t1, qm.state.value.currentTrack)
    }

    @Test
    fun testRepeatAllStillPlaysAppendedDiscoveryBeforeWrapping() {
        val qm = QueueManagerImpl()
        val t1 = testTrack("1", "Only Song")
        qm.play(t1, listOf(t1))
        qm.cycleRepeatMode()

        val discovered = testTrack("d1", "Discovered")
        qm.appendDiscovery(listOf(discovered))

        assertEquals(discovered, qm.advanceToNext())
        assertEquals(t1, qm.advanceToNext())
        assertEquals(0, qm.state.value.currentIndex)
    }

    @Test
    fun testRepeatOneKeepsCurrent() {
        val qm = QueueManagerImpl()
        val current = testTrack("1", "Single")
        val other = testTrack("2", "Other")
        qm.play(current, listOf(current))
        qm.addToQueue(other)
        qm.cycleRepeatMode()
        qm.cycleRepeatMode()
        assertEquals(RepeatMode.ONE, qm.state.value.repeatMode)

        assertEquals(current, qm.peekNext())
        assertEquals(current, qm.advanceToNext())
        assertEquals(current, qm.state.value.currentTrack)

        qm.cycleRepeatMode()
        assertEquals(RepeatMode.OFF, qm.state.value.repeatMode)
    }

    @Test
    fun testShuffleKeepsCurrentAndHistoryThenRestoresOrder() {
        val qm = QueueManagerImpl()
        val tracks = (0 until 8).map { testTrack(it.toString(), "Song $it") }
        qm.play(tracks[0], tracks)
        qm.advanceToNext()
        qm.advanceToNext()
        val currentIndex = qm.state.value.currentIndex
        val played = qm.state.value.items.take(currentIndex + 1)

        qm.toggleShuffle()

        val shuffled = qm.state.value
        assertTrue(shuffled.isShuffle)
        assertEquals(played, shuffled.items.take(currentIndex + 1))
        assertEquals(8, shuffled.items.size)
        assertEquals(5, shuffled.upNextCount)
        assertEquals(
            tracks.toSet(),
            shuffled.items.toSet(),
        )

        qm.toggleShuffle()

        val restored = qm.state.value
        assertFalse(restored.isShuffle)
        assertNull(restored.preShuffleOrder)
        assertEquals(tracks, restored.items)
        assertEquals(currentIndex, restored.currentIndex)
    }

    @Test
    fun testShuffleKeepsItemsAddedWhileShuffled() {
        val qm = QueueManagerImpl()
        val tracks = (0 until 6).map { testTrack(it.toString(), "Song $it") }
        val late = testTrack("late", "Added Later")
        qm.play(tracks[0], tracks)
        qm.toggleShuffle()
        qm.addToQueue(late)

        qm.toggleShuffle()

        assertEquals(tracks + late, qm.state.value.items)
    }

    @Test
    fun testPlayDoesNotLeakPreviousSessionStagedItems() {
        val qm = QueueManagerImpl()
        val old1 = testTrack("old1", "Old 1")
        val old2 = testTrack("old2", "Old 2")
        qm.play(old1, listOf(old1, old2))
        qm.playNext(testTrack("staged", "Staged From Previous Session"))
        qm.addToQueue(testTrack("queued", "Queued From Previous Session"))
        qm.appendDiscovery(listOf(testTrack("disc", "Suggested Earlier")))

        val fresh = testTrack("fresh1", "Fresh 1")
        val fresh2 = testTrack("fresh2", "Fresh 2")
        qm.play(fresh, listOf(fresh, fresh2))

        val state = qm.state.value
        assertEquals(listOf(fresh, fresh2), state.items)
        assertEquals(0, state.currentIndex)
        assertEquals(listOf(fresh2), state.upcoming)
    }

    @Test
    fun testMoveRemoveAndClearUpcoming() {
        val qm = QueueManagerImpl()
        val current = testTrack("0", "Playing")
        qm.play(current, listOf(current))
        val t1 = testTrack("1", "S1")
        val t2 = testTrack("2", "S2")
        val t3 = testTrack("3", "S3")

        qm.addToQueue(t1)
        qm.addToQueue(t2)
        qm.addToQueue(t3)

        qm.moveUpNext(0, 2)
        assertEquals(listOf(current, t2, t3, t1), qm.state.value.items)

        qm.removeUpNext(1)
        assertEquals(listOf(current, t2, t1), qm.state.value.items)

        qm.clearUpNext()
        assertEquals(listOf(current), qm.state.value.items)
        assertTrue(qm.state.value.upcoming.isEmpty())
    }

    @Test
    fun testRecentHistoryKeysCoverPlayedTracksOnly() {
        val qm = QueueManagerImpl()
        val played = testTrack("1", "Played")
        val alsoPlayed = testTrack("2", "Also Played")
        val upcoming = testTrack("3", "Still Queued")
        qm.play(played, listOf(played, alsoPlayed, upcoming))
        qm.advanceToNext()

        val keys = qm.getRecentHistoryKeys()

        assertTrue(keys.contains(TrackIdentity.keyOf(played)))
        assertTrue(keys.contains(TrackIdentity.keyOf(alsoPlayed)))
        assertFalse(keys.contains(TrackIdentity.keyOf(upcoming)))

        assertTrue(keys.contains("played:::test artist"))
    }

    @Test
    fun testRecentHistoryKeysAreCapped() {
        val qm = QueueManagerImpl()
        val tracks = (0 until QueueManagerImpl.RECENT_HISTORY_LIMIT + 40).map {
            testTrack(it.toString(), "Song $it")
        }
        qm.play(tracks.first(), tracks)
        tracks.drop(1).forEach { qm.advanceToNext() }

        val keys = qm.getRecentHistoryKeys()

        assertTrue(keys.size <= QueueManagerImpl.RECENT_HISTORY_LIMIT * 2)
        assertFalse(keys.contains(TrackIdentity.keyOf(tracks.first())))
        assertTrue(keys.contains(TrackIdentity.keyOf(tracks.last())))
    }

    @Test
    fun testConcurrentDiscoveryAppendsAndHistoryReadsStayConsistent() {
        val qm = QueueManagerImpl()
        val seed = testTrack("seed", "Seed")
        qm.play(seed, listOf(seed))
        val perThread = 25
        val threads = 4
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)

        repeat(threads) { thread ->
            pool.execute {
                start.await()
                repeat(perThread) { index ->
                    qm.appendDiscovery(
                        listOf(testTrack("t${thread}_$index", "Discovered ${thread}_$index"))
                    )
                    qm.getRecentHistoryKeys()
                    qm.advanceToNext()
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))

        val state = qm.state.value
        assertEquals(1 + threads * perThread, state.items.size)
        assertEquals(state.items.size, state.items.distinctBy { TrackIdentity.keyOf(it) }.size)
        assertTrue(state.items.any { it.title == "Seed" })
    }
}
