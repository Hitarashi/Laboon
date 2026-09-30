package org.shilpo.laboon

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.search.SearchRepositoryImpl

class SearchRepositoryBatchTest {

    private val repository = SearchRepositoryImpl(SessionStore(FakeKeyValueStore()))

    private fun track(id: String, streamUrl: String? = null) = HomeTrack(
        id = id,
        title = "Song $id",
        artist = "Artist $id",
        streamUrl = streamUrl,
    )

    @Test
    fun `an empty candidate list resolves to nothing`() = runBlocking {
        assertEquals(emptyList<HomeTrack>(), repository.resolvePlaybackBatch(emptyList()))
    }

    @Test
    fun `a candidate that already knows its stream is kept as is`() = runBlocking {
        val known = track("known", streamUrl = "https://example.com/stream.m3u8")

        val resolved = repository.resolvePlaybackBatch(listOf(known))

        assertEquals(listOf(known), resolved)
    }

    @Test
    fun `a candidate that cannot be resolved is dropped rather than returned unplayable`() =
        runBlocking {

            val unresolvable = track("unresolvable")

            assertEquals(
                emptyList<HomeTrack>(),
                repository.resolvePlaybackBatch(listOf(unresolvable))
            )
        }

    @Test
    fun `only the resolvable candidates survive, in order`() = runBlocking {
        val first = track("first", streamUrl = "https://example.com/1.m3u8")
        val second = track("second")
        val third = track("third", streamUrl = "https://example.com/3.m3u8")

        val resolved = repository.resolvePlaybackBatch(listOf(first, second, third))

        assertEquals(listOf(first, third), resolved)
    }

    @Test
    fun `a single unresolvable track still resolves to nothing`() = runBlocking {
        assertEquals(emptyList<HomeTrack>(), repository.resolvePlaybackBatch(listOf(track("only"))))
    }
}
