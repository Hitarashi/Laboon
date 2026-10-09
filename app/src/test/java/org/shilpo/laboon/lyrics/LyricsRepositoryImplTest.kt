package org.shilpo.laboon.lyrics

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.lyricsporn.LyricspornCatalogItem
import org.shilpo.laboon.net.HttpError
import org.shilpo.laboon.net.HttpErrorKind
import org.shilpo.laboon.net.HttpOutcome

class LyricsRepositoryImplTest {
    private val track = LyricsLookup(
        title = "Test Song",
        artists = listOf("Test Artist"),
        appleTrackId = "12345",
    )

    @Test
    fun `confirmed no lyrics response returns not found`() = runBlocking {
        val repository = repository(
            getTrackLyrics = { _, _ -> HttpOutcome.Success(noLyricsResponse()) },
        )

        assertEquals(LyricsLookupResult.NotFound, repository.lookup(track))
    }

    @Test
    fun `track request failure returns failed`() = runBlocking {
        val repository = repository(
            getTrackLyrics = { _, _ -> HttpOutcome.Failure(networkFailure()) },
        )

        assertEquals(LyricsLookupResult.Failed, repository.lookup(track))
    }

    @Test
    fun `track not found response returns not found`() = runBlocking {
        val repository = repository(
            getTrackLyrics = { _, _ ->
                HttpOutcome.Failure(
                    HttpError(
                        kind = HttpErrorKind.STATUS,
                        statusCode = 404,
                        message = "track not found",
                    ),
                )
            },
        )

        assertEquals(LyricsLookupResult.NotFound, repository.lookup(track))
    }

    @Test
    fun `empty catalog search returns not found`() = runBlocking {
        val repository = repository(
            searchSongs = { _, _ -> HttpOutcome.Success(emptyList()) },
        )

        assertEquals(LyricsLookupResult.NotFound, repository.lookup(track.copy(appleTrackId = null)))
    }

    @Test
    fun `catalog search failure returns failed`() = runBlocking {
        val repository = repository(
            searchSongs = { _, _ -> HttpOutcome.Failure(networkFailure()) },
        )

        assertEquals(LyricsLookupResult.Failed, repository.lookup(track.copy(appleTrackId = null)))
    }

    @Test
    fun `a failed lookup is retried and can later return lyrics`() = runBlocking {
        var requests = 0
        val repository = repository(
            getTrackLyrics = { _, _ ->
                requests += 1
                if (requests == 1) {
                    HttpOutcome.Failure(networkFailure())
                } else {
                    HttpOutcome.Success(availableLyricsResponse())
                }
            },
        )

        assertEquals(LyricsLookupResult.Failed, repository.lookup(track))
        val retry = repository.lookup(track)

        assertTrue(retry is LyricsLookupResult.Found)
        assertEquals(2, requests)
    }

    private fun repository(
        searchSongs: suspend (String?, String) -> HttpOutcome<List<LyricspornCatalogItem>> =
            { _, _ -> HttpOutcome.Success(listOf(catalogItem)) },
        getTrackLyrics: suspend (String?, String) -> HttpOutcome<JSONObject> =
            { _, _ -> HttpOutcome.Success(availableLyricsResponse()) },
    ) = LyricsRepositoryImpl(
        lyricspornApiUrlProvider = { "https://lyrics.example.test/api/v1" },
        searchSongsForLyrics = searchSongs,
        getTrackLyrics = getTrackLyrics,
    )

    private fun networkFailure() = HttpError(
        kind = HttpErrorKind.NETWORK,
        message = "network unavailable",
    )

    private fun noLyricsResponse() = JSONObject(
        """{"lyrics":{"status":"not_found","formats":{"json":{"status":"unavailable","reason":"No lyrics were found for this track."}}}}""",
    )

    private fun availableLyricsResponse() = JSONObject(
        """{"lyrics":{"status":"available","formats":{"json":{"status":"available","content":{"format":"lrc","syncLevel":"line","provider":"Test Provider","plainText":"First line\nSecond line","lines":[{"text":"First line","startMs":0,"endMs":1000},{"text":"Second line","startMs":1000,"endMs":2000}]}}}}}""",
    )

    private val catalogItem = LyricspornCatalogItem(
        id = "12345",
        type = "song",
        name = "Test Song",
    )
}
