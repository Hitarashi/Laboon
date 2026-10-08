package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.playback.QueueEntry
import org.shilpo.laboon.playback.QueueOrigin
import org.shilpo.laboon.playback.QueuePersistence
import org.shilpo.laboon.playback.QueuePersistenceCodec
import org.shilpo.laboon.playback.QueueState
import org.shilpo.laboon.playback.RepeatMode

class QueuePersistenceTest {

    @Test
    fun v1RoundTripRetainsHistoryEntryIdsOriginsAndSuppression() {
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
            mbid = "recording-1",
            artistMbid = "artist-1",
            isrc = "isrc-1",
            providerTrackId = "apple-1",
            durationMs = 234_000L,
            contentRating = "clean",
        )
        val context = QueueEntry(11L, first, QueueOrigin.CONTEXT, contextOrder = 0)
        val current =
            QueueEntry(12L, first.copy(id = "current"), QueueOrigin.CONTEXT, contextOrder = 1)
        val manual = QueueEntry(
            13L,
            HomeTrack(id = "2", title = "Song\tTwo\nWith Break", artist = "Artist Two"),
            QueueOrigin.MANUAL,
        )
        val autoplay = QueueEntry(14L, HomeTrack("3", "Auto", "Artist"), QueueOrigin.AUTOPLAY)
        val state = QueueState(
            history = listOf(context, current),
            historyCursor = 1,
            upcomingEntries = listOf(manual, autoplay),
            contextEntries = listOf(context, current),
            sessionSeedEntryId = context.id,
            repeatMode = RepeatMode.ALL,
            isShuffle = true,
            autoplaySuppressed = true,
            dismissedAutoplayKeys = setOf("meta:removed:::artist"),
        )

        persistence.save(state)

        val payload = store.stored["playback_queue_v1"] as String
        assertTrue(payload.startsWith("v1\t"))
        assertEquals(11, payload.substringBefore('\n').split('\t').size)

        val restored = persistence.load()
        assertEquals(state, restored)
        assertEquals(current.track, restored.currentTrack)
        assertEquals(
            listOf(QueueOrigin.MANUAL, QueueOrigin.AUTOPLAY),
            restored.upcomingEntries.map { it.origin })
        assertEquals("artist-1", restored.history.first().track.artistMbid)
        assertEquals(234_000L, restored.history.first().track.durationMs)
    }

    @Test
    fun emptyStoreAndClearLoadAnEmptyQueue() {
        val store = FakeKeyValueStore()
        val persistence = QueuePersistence(store)

        assertEquals(QueueState(), persistence.load())
        persistence.save(QueueState.withCurrent(HomeTrack("1", "Song", "Artist")))
        persistence.clear()
        assertEquals(QueueState(), persistence.load())
    }

    @Test
    fun v1RejectsCorruptHeaderAndEntryPayloads() {
        val valid = QueuePersistenceCodec.encode(
            QueueState.withCurrent(HomeTrack("1", "Song", "Artist")),
        )
        val corrupt = listOf(
            "",
            "   ",
            "\n\n",
            "unsupported\t0\t0\t0\t0\t0\t0\t0\t\t0\t0",
            "v1\tnope\t0\t0\t0\t1\t0\t0\t\t0\t0",
            "v1\t0\t9\t0\t0\t1\t0\t0\t\t0\t0",
            valid.substringBefore('\n'),
            valid + "\nmalformed",
        )

        corrupt.forEach {
            assertEquals(
                "payload: $it",
                QueueState(),
                QueuePersistenceCodec.decode(it)
            )
        }
    }

    @Test
    fun v1MalformedRowsStillFallBackToAnEmptyQueue() {
        val valid = QueuePersistenceCodec.encode(
            QueueState.withCurrent(HomeTrack("1", "Song", "Artist")),
        )
        val lines = valid.split('\n').toMutableList()
        val invalidTitle = lines.toMutableList().apply {
            this[1] = this[1].split('\t').toMutableList().apply { this[5] = "" }.joinToString("\t")
        }.joinToString("\n")
        val invalidEscape = lines.toMutableList().apply {
            this[1] =
                this[1].split('\t').toMutableList().apply { this[4] = "\\q" }.joinToString("\t")
        }.joinToString("\n")
        val corrupt = listOf(
            lines.take(2).joinToString("\n"),
            lines.toMutableList().apply { this[1] = "not-enough-fields" }.joinToString("\n"),
            invalidTitle,
            invalidEscape,
        )
        corrupt.forEach { assertEquals(QueueState(), QueuePersistenceCodec.decode(it)) }
    }

    @Test
    fun duplicateTrackEntriesKeepTheirQueueIdentityInV1() {
        val duplicate = HomeTrack("same", "Song", "Artist")
        val current = QueueEntry(1L, duplicate, QueueOrigin.CONTEXT, 0)
        val one = QueueEntry(2L, duplicate, QueueOrigin.MANUAL)
        val two = QueueEntry(3L, duplicate, QueueOrigin.MANUAL)
        val state = QueueState(
            history = listOf(current),
            historyCursor = 0,
            upcomingEntries = listOf(one, two),
            contextEntries = listOf(current),
            sessionSeedEntryId = current.id,
        )

        val restored = QueuePersistenceCodec.decode(QueuePersistenceCodec.encode(state))

        assertEquals(listOf(2L, 3L), restored.upcomingEntries.map { it.id })
        assertEquals(listOf(duplicate, duplicate), restored.upcoming)
    }

    @Test
    fun v1RoundTripKeepsCurrentTrackWhenHistoryCursorIsOlderThanRecentHistoryWindow() {
        val entries = (0 until 120).map { index ->
            QueueEntry(
                id = index + 1L,
                track = HomeTrack("track-$index", "Song $index", "Artist"),
                origin = QueueOrigin.CONTEXT,
                contextOrder = index,
            )
        }
        val state = QueueState(
            history = entries,
            historyCursor = 4,
            contextEntries = entries,
            sessionSeedEntryId = entries.first().id,
        )

        val restored = QueuePersistenceCodec.decode(QueuePersistenceCodec.encode(state))

        assertEquals(entries[4].track, restored.currentTrack)
        assertEquals(4, restored.historyCursor)
        assertEquals(entries, restored.history)
    }
}
