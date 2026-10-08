package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackIdentity
import org.shilpo.laboon.playback.QueueManagerImpl
import org.shilpo.laboon.playback.QueueOrigin
import org.shilpo.laboon.playback.RepeatMode

class QueueManagerTest {

    private fun track(id: String, title: String, artist: String = "Artist") =
        HomeTrack(id = id, title = title, artist = artist)

    @Test
    fun playCreatesContextAndStableEntryIdentities() {
        val manager = QueueManagerImpl()
        val first = track("1", "First")
        val second = track("2", "Second")

        manager.play(first, listOf(first, second))

        val state = manager.state.value
        assertEquals(first, state.currentTrack)
        assertEquals(listOf(second), state.upcoming)
        assertEquals(listOf(QueueOrigin.CONTEXT), state.upcomingEntries.map { it.origin })
        assertEquals(0, state.historyCursor)
        assertTrue(state.history.single().id > 0L)
        assertTrue(state.history.single().id != state.upcomingEntries.single().id)
    }

    @Test
    fun mediaTransitionSelectionUsesQueueEntryIdsOnly() {
        val manager = QueueManagerImpl()
        val current = track("current", "Current")
        val queued = track("queued", "Queued")
        manager.play(current, listOf(current, queued))
        val queuedEntry = manager.state.value.upcomingEntries.single()

        assertNull(manager.selectCurrentById(queued.id))
        assertEquals(current, manager.state.value.currentTrack)
        assertEquals(listOf(queued), manager.state.value.upcoming)

        assertEquals(
            queued,
            manager.selectCurrentById("${QueueManagerImpl.QUEUE_MEDIA_ID_PREFIX}${queuedEntry.id}"),
        )
        assertEquals(queued, manager.state.value.currentTrack)
    }

    @Test
    fun playNextIsFirstAndAddToQueueAppendsManualEntriesBeforeContext() {
        val manager = QueueManagerImpl()
        val current = track("0", "Current")
        val context = track("c", "Context")
        val added = track("a", "Added")
        val next = track("n", "Next")
        manager.play(current, listOf(current, context))

        manager.addToQueue(added)
        manager.playNext(next)

        assertEquals(listOf(next, added, context), manager.state.value.upcoming)
        assertEquals(
            listOf(QueueOrigin.MANUAL, QueueOrigin.MANUAL, QueueOrigin.CONTEXT),
            manager.state.value.upcomingEntries.map { it.origin },
        )
    }

    @Test
    fun manualDuplicatesAreExplicitAndRemainAheadOfContext() {
        val manager = QueueManagerImpl()
        val current = track("0", "Current")
        val context = track("c", "Context")
        val same = track("m", "Manual")
        manager.play(current, listOf(current, context))

        manager.addToQueue(same)
        manager.addToQueue(same.copy(id = "m2"))

        assertEquals(listOf(same, same.copy(id = "m2"), context), manager.state.value.upcoming)
        assertEquals(
            2,
            manager.state.value.upcomingEntries.count { it.origin == QueueOrigin.MANUAL })
    }

    @Test
    fun startingAnotherContextPreservesManualButReplacesContextAndAutoplay() {
        val manager = QueueManagerImpl()
        val old = track("old", "Old")
        val oldContext = track("oc", "Old Context")
        val manual = track("m", "Manual")
        val suggestion = track("auto", "Earlier Suggestion")
        manager.play(old, listOf(old, oldContext))
        manager.addToQueue(manual)
        manager.appendDiscovery(listOf(suggestion))

        val fresh = track("fresh", "Fresh")
        val freshContext = track("fc", "Fresh Context")
        manager.play(fresh, listOf(fresh, freshContext))

        val state = manager.state.value
        assertEquals(listOf(manual, freshContext), state.upcoming)
        assertEquals(
            listOf(manual),
            state.upcomingEntries.filter { it.origin == QueueOrigin.MANUAL }.map { it.track })
        assertFalse(state.upcoming.any { it == suggestion || it == oldContext })
        assertEquals(fresh, state.contextEntries.first().track)
        assertEquals(fresh, state.currentTrack)
    }

    @Test
    fun previousAlwaysMovesToHistoryAndNextRetracesForwardBeforeQueue() {
        val manager = QueueManagerImpl()
        val one = track("1", "One")
        val two = track("2", "Two")
        val three = track("3", "Three")
        val manual = track("m", "Manual")
        manager.play(one, listOf(one, two, three))
        manager.advanceToNext()
        manager.advanceToNext()
        manager.addToQueue(manual)

        assertEquals(two, manager.advanceToPrevious())
        assertEquals(two, manager.state.value.currentTrack)
        assertTrue(manager.state.value.hasPrevious)
        assertEquals(three, manager.advanceToNext())
        assertEquals(manual, manager.advanceToNext())
        assertFalse(manager.state.value.hasNext)
    }

    @Test
    fun queueEntrySelectionPromotesTheExactStableEntryToCurrent() {
        val manager = QueueManagerImpl()
        val current = track("0", "Current")
        val first = track("1", "Duplicate")
        val second = first.copy(id = "2")
        val tail = track("3", "Tail")
        manager.play(current, listOf(current))
        manager.addToQueue(first)
        manager.addToQueue(second)
        manager.addToQueue(tail)
        val secondEntry = manager.state.value.upcomingEntries[1]

        assertEquals(second, manager.playEntry(secondEntry.id))

        assertEquals(second, manager.state.value.currentTrack)
        assertEquals(listOf(first, tail), manager.state.value.upcoming)
        assertTrue(manager.state.value.hasPrevious)
    }

    @Test
    fun clearUpcomingSuppressesAutoplayUntilPlaybackStartsAgain() {
        val manager = QueueManagerImpl()
        val current = track("0", "Current")
        manager.play(current)
        manager.appendDiscovery(listOf(track("a", "Suggestion")))

        manager.clearUpNext()
        manager.appendDiscovery(listOf(track("b", "Ignored after clear")))

        assertTrue(manager.state.value.autoplaySuppressed)
        assertTrue(manager.state.value.upcomingEntries.isEmpty())

        val fresh = track("fresh", "Fresh")
        manager.play(fresh)
        manager.appendDiscovery(listOf(track("c", "Fresh suggestion")))
        assertFalse(manager.state.value.autoplaySuppressed)
        assertEquals("Fresh suggestion", manager.state.value.upcoming.single().title)
    }

    @Test
    fun removedAutoplaySuggestionsStayExcludedForTheCurrentSession() {
        val manager = QueueManagerImpl()
        manager.play(track("s", "Seed"))
        val removed = track("a", "Removed").copy(mbid = "removed-mbid")
        manager.appendDiscovery(listOf(removed))
        manager.removeUpNext(0)

        manager.appendDiscovery(listOf(removed.copy(id = "a2"), track("b", "Fresh")))

        assertEquals(listOf("Fresh"), manager.state.value.upcoming.map { it.title })
        assertTrue(TrackIdentity.keyOf(removed) in manager.state.value.dismissedAutoplayKeys)
        assertTrue(
            TrackIdentity.keyOf(null, removed.title, removed.artist) in
                    manager.state.value.dismissedAutoplayKeys,
        )
    }

    @Test
    fun discoveryRejectsQueuedAndRecentlyPlayedTracksButKeepsVersionIdentity() {
        val manager = QueueManagerImpl()
        val live = track("live", "Song (Live)")
        val studio = track("studio", "Song")
        manager.play(studio, listOf(studio))

        manager.appendDiscovery(listOf(studio.copy(id = "duplicate"), live))

        assertEquals(listOf(live), manager.state.value.upcoming)
    }

    @Test
    fun repeatAllLoopsContextBeforeDeferringAutoplay() {
        val manager = QueueManagerImpl()
        val one = track("1", "One")
        val two = track("2", "Two")
        val auto = track("a", "Autoplay")
        manager.play(one, listOf(one, two))
        manager.appendDiscovery(listOf(auto))
        manager.cycleRepeatMode()

        assertEquals(two, manager.advanceToNext())
        assertEquals(one, manager.advanceToNext())
        assertEquals(two, manager.advanceToNext())
        assertEquals(listOf(one, two, auto), manager.state.value.upcoming)
    }

    @Test
    fun repeatOneDoesNotBlockAnExplicitNext() {
        val manager = QueueManagerImpl()
        val one = track("1", "One")
        val two = track("2", "Two")
        manager.play(one, listOf(one, two))
        manager.cycleRepeatMode()
        manager.cycleRepeatMode()

        assertEquals(RepeatMode.ONE, manager.state.value.repeatMode)
        assertEquals(two, manager.advanceToNext())
    }

    @Test
    fun repeatOneDoesNotPretendThereIsANextEntryWhenQueueIsEmpty() {
        val manager = QueueManagerImpl()
        val one = track("1", "One")
        manager.play(one)
        manager.cycleRepeatMode()
        manager.cycleRepeatMode()

        assertEquals(RepeatMode.ONE, manager.state.value.repeatMode)
        assertFalse(manager.state.value.hasNext)
    }

    @Test
    fun shuffleOnlyReordersContextAndKeepsManualOrder() {
        val manager = QueueManagerImpl()
        val context = (1..15).map { track("c$it", "Context $it") }
        val manualA = track("m1", "Manual A")
        val manualB = track("m2", "Manual B")
        manager.play(context.first(), context)
        manager.addToQueue(manualA)
        manager.addToQueue(manualB)
        val before = manager.state.value.upcoming

        manager.toggleShuffle()
        val shuffled = manager.state.value

        assertEquals(listOf(manualA, manualB), shuffled.upcoming.take(2))
        assertEquals(context.drop(1).toSet(), shuffled.upcoming.drop(2).toSet())
        assertEquals(before.take(2), shuffled.upcoming.take(2))
        manager.toggleShuffle()
        assertEquals(before, manager.state.value.upcoming)
    }

    @Test
    fun historySeedsUseOriginalSessionSeedAndOnlyPlayedTracks() {
        val manager = QueueManagerImpl()
        val one = track("1", "One")
        val two = track("2", "Two")
        val three = track("3", "Three")
        val future = track("4", "Future")
        manager.play(one, listOf(one, two, three, future))
        manager.advanceToNext()
        manager.advanceToNext()

        assertEquals(listOf(one, two, three), manager.discoverySeedTracks())
        val keys = manager.getRecentHistoryKeys()
        assertTrue(TrackIdentity.keyOf(one) in keys)
        assertTrue(TrackIdentity.keyOf(three) in keys)
        assertFalse(TrackIdentity.keyOf(future) in keys)

        manager.advanceToPrevious()

        assertEquals(listOf(one, two, three), manager.discoverySeedTracks())
        assertTrue(TrackIdentity.keyOf(three) in manager.getRecentHistoryKeys())
    }

    @Test
    fun concurrentDiscoveryAppendsRemainDistinct() {
        val manager = QueueManagerImpl()
        val seed = track("seed", "Seed")
        manager.play(seed)
        val values = (0 until 40).map { track("$it", "Song $it") }
        values.forEach { manager.appendDiscovery(listOf(it)) }

        assertEquals(40, manager.state.value.upcomingEntries.size)
        assertEquals(40, manager.state.value.upcomingEntries.map { it.id }.distinct().size)
    }
}
