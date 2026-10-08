package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenBrainzLabsTest {

    private val seedA = "5b44c236-cbb0-4e54-a8b1-c344b76e1b8f"
    private val seedB = "72d364a2-3cfc-432d-8430-1b45efb99bca"

    @Test
    fun `similarRecordingsUrl repeats the seed parameter instead of joining with commas`() {
        val url = ListenBrainzLabs.similarRecordingsUrl(listOf(seedA, seedB))

        assertTrue("algorithm must be present: $url", url.contains("algorithm="))
        assertTrue(
            "seeds must be repeated query params: $url",
            url.contains("&recording_mbids=$seedA") && url.contains("&recording_mbids=$seedB"),
        )
        assertTrue("comma joined seeds are rejected by the service: $url", !url.contains(","))
    }

    @Test
    fun `similarArtistsUrl repeats the artist parameter`() {
        val url = ListenBrainzLabs.similarArtistsUrl(listOf(seedA, seedB))

        assertTrue(url.contains("algorithm="))
        assertTrue(url.contains("&artist_mbids=$seedA") && url.contains("&artist_mbids=$seedB"))
        assertTrue(!url.contains(","))
    }

    @Test
    fun `urls use the labs host and the json suffix`() {
        assertTrue(
            ListenBrainzLabs.similarRecordingsUrl(listOf(seedA))
                .startsWith("https://labs.api.listenbrainz.org/similar-recordings/json"),
        )
        assertTrue(
            ListenBrainzLabs.similarArtistsUrl(listOf(seedA))
                .startsWith("https://labs.api.listenbrainz.org/similar-artists/json"),
        )
    }

    @Test
    fun `chunkSeeds splits at the safe request size`() {
        val many = (1..60).map { "mbid-$it" }

        val chunks = ListenBrainzLabs.chunkSeeds(many)

        assertEquals(3, chunks.size)
        assertEquals(25, chunks[0].size)
        assertEquals(25, chunks[1].size)
        assertEquals(10, chunks[2].size)
        assertEquals(many, chunks.flatten())
    }

    @Test
    fun `chunkSeeds drops blanks and returns nothing for an empty list`() {
        assertEquals(emptyList<List<String>>(), ListenBrainzLabs.chunkSeeds(emptyList()))
        assertEquals(
            listOf(listOf("a", "b")),
            ListenBrainzLabs.chunkSeeds(listOf("a", "", "b")),
        )
    }

    @Test
    fun `parseSimilarRecordings keeps the recording id and cover art`() {
        val body = JSONArray().put(
            JSONObject().apply {
                put("recording_mbid", seedA)
                put("recording_name", "Some Resolve")
                put("artist_credit_name", "Röyksopp")
                put("release_name", "Profound Mysteries II")
                put("release_mbid", seedB)
                put("score", 27)
            }
        )

        val tracks = ListenBrainzLabs.parseSimilarRecordings(body)

        assertEquals(1, tracks.size)
        val track = tracks.single()
        assertEquals("Some Resolve", track.title)
        assertEquals("Röyksopp", track.artist)
        assertEquals(seedA, track.mbid)
        assertEquals("Profound Mysteries II", track.album)
        org.junit.Assert.assertNull(track.artworkUrl)
        assertEquals(
            27.0,
            ListenBrainzLabs.parseSimilarRecordingCandidates(body).single().similarity!!,
            0.0
        )
    }

    @Test
    fun `parseSimilarRecordings skips rows without a recording id`() {
        val body = JSONArray()
            .put(JSONObject().put("recording_name", "No id").put("artist_credit_name", "Someone"))
            .put(
                JSONObject().put("recording_mbid", seedA)
                    .put("recording_name", "Has id")
                    .put("artist_credit_name", "Someone")
            )

        val tracks = ListenBrainzLabs.parseSimilarRecordings(body)

        assertEquals(1, tracks.size)
        assertEquals("Has id", tracks.single().title)
    }

    @Test
    fun `parseSimilarArtists keeps the artist id needed to go deeper`() {
        val body = JSONArray().put(
            JSONObject().apply {
                put("artist_mbid", seedA)
                put("name", "Air")
                put("score", 4873)
                put("reference_mbid", seedB)
            }
        )

        val artists = ListenBrainzLabs.parseSimilarArtists(body)

        assertEquals(1, artists.size)
        assertEquals(seedA, artists.single().mbid)
        assertEquals("Air", artists.single().name)
        assertEquals(4873, artists.single().score)
    }

    @Test
    fun `parseCfRecordingMbids reads the bare ids from the payload`() {
        val body = JSONObject().put(
            "payload",
            JSONObject().put(
                "mbids",
                JSONArray()
                    .put(JSONObject().put("recording_mbid", seedA).put("score", 0.08))
                    .put(JSONObject().put("recording_mbid", seedB).put("score", 0.07)),
            )
        )

        assertEquals(listOf(seedA, seedB), ListenBrainzLabs.parseCfRecordingMbids(body))
    }

    @Test
    fun `parseCfRecordingMbids is empty when the payload has no mbids`() {
        assertEquals(emptyList<String>(), ListenBrainzLabs.parseCfRecordingMbids(JSONObject()))
        assertEquals(
            emptyList<String>(),
            ListenBrainzLabs.parseCfRecordingMbids(JSONObject().put("payload", JSONObject())),
        )
    }

    @Test
    fun `cfRecommendationsUrl clamps the count and omits a zero offset`() {
        val url = ListenBrainzLabs.cfRecommendationsUrl("rob", count = 5000)

        assertTrue(url.contains("/1/cf/recommendation/user/rob/recording"))
        assertTrue("count is capped at 1000: $url", url.contains("count=1000"))
        assertTrue(!url.contains("offset"))
    }

    @Test
    fun `metadata endpoints use documented recording ids and separate artist ids`() {
        val lookup = ListenBrainzLabs.metadataLookupUrl(
            HomeTrack("track", "A Song", "An Artist", album = "An Album"),
        )
        assertTrue(lookup.contains("/1/metadata/lookup/"))
        assertTrue(lookup.contains("recording_name=A+Song"))
        assertTrue(lookup.contains("artist_name=An+Artist"))

        val recording = ListenBrainzLabs.recordingMbidLookupUrl(listOf(seedA, seedB))
        assertTrue(recording.startsWith("https://labs.api.listenbrainz.org/recording-mbid-lookup/json"))
        assertTrue(recording.contains("recording_mbid=$seedA"))
        assertTrue(recording.contains("recording_mbid=$seedB"))
        assertTrue(
            ListenBrainzLabs.metadataRecordingUrl(listOf(seedA, seedB))
                .contains("recording_mbids=$seedA,$seedB")
        )
    }

    @Test
    fun `nested recording metadata reads artist ids and release without assuming a title`() {
        val artistMbid = "0d33cc88-28ae-44d5-be7e-7a653e518720"
        val body = JSONObject().put(
            seedA,
            JSONObject()
                .put(
                    "artist", JSONObject()
                        .put("name", "Portishead")
                        .put(
                            "artists",
                            JSONArray().put(JSONObject().put("artist_mbid", artistMbid))
                        )
                )
                .put("release", JSONObject().put("name", "Dummy"))
                .put("recording", JSONObject().put("rels", JSONArray())),
        )

        val metadata = ListenBrainzLabs.parseRecordingMetadata(body, listOf(seedA)).getValue(seedA)

        assertEquals(seedA, metadata.recordingMbid)
        assertEquals(null, metadata.title)
        assertEquals("Portishead", metadata.artistName)
        assertEquals(listOf(artistMbid), metadata.artistMbids)
        assertEquals("Dummy", metadata.releaseName)
    }

    @Test
    fun `recording lookup returns recording and artist mbids independently`() {
        val artistMbid = "artist-mbid"
        val body = JSONArray().put(
            JSONObject()
                .put("recording_mbid", seedA)
                .put("canonical_recording_mbid", seedB)
                .put("recording_name", "Some Resolve")
                .put("artist_credit_name", "Röyksopp")
                .put("artist_credit_mbids", JSONArray().put(artistMbid))
                .put("release_name", "Profound Mysteries")
                .put("length", 210_000),
        )

        val recording = ListenBrainzLabs.parseRecordingMbidLookup(body).single()

        assertEquals(seedB, recording.recordingMbid)
        assertEquals("Some Resolve", recording.title)
        assertEquals("Röyksopp", recording.artistName)
        assertEquals(listOf(artistMbid), recording.artistMbids)
        assertEquals(210_000L, recording.durationMs)
    }

    @Test
    fun `metadata lookup obtains artist ids for a recording matched by title`() {
        val artistMbid = "artist-mbid"
        val metadata = ListenBrainzLabs.parseMetadataLookup(
            JSONObject()
                .put("recording_mbid", seedA)
                .put("recording_name", "Song title")
                .put("artist_credit_name", "Artist name")
                .put("artist_mbids", JSONArray().put(artistMbid))
                .put("release_name", "Release"),
        )

        assertEquals(seedA, metadata?.recordingMbid)
        assertEquals("Song title", metadata?.title)
        assertEquals("Artist name", metadata?.artistName)
        assertEquals(listOf(artistMbid), metadata?.artistMbids)
    }

    @Test
    fun `feedback parsing returns loved and hated ratings and ignores malformed rows`() {
        val body = JSONObject().put(
            "feedback",
            JSONArray()
                .put(JSONObject().put("recording_mbid", seedA).put("rating", "love"))
                .put(JSONObject().put("recording_mbid", seedB).put("rating", "hate"))
                .put(JSONObject().put("rating", "love")),
        )

        assertEquals(mapOf(seedA to "love", seedB to "hate"), ListenBrainzLabs.parseFeedback(body))
        assertEquals(emptyMap<String, String>(), ListenBrainzLabs.parseFeedback(JSONObject()))
    }

    @Test
    fun `recording feedback URL and score response follow the feedback API`() {
        val url = ListenBrainzLabs.feedbackUrl("rob smith", count = 500)
        assertTrue(url.contains("/1/feedback/user/rob+smith/get-feedback"))
        assertTrue(url.contains("count=500"))

        val body = JSONObject().put(
            "feedback",
            JSONArray()
                .put(JSONObject().put("recording_mbid", seedA).put("score", 1))
                .put(JSONObject().put("recording_mbid", seedB).put("score", -1)),
        )
        assertEquals(mapOf(seedA to "love", seedB to "hate"), ListenBrainzLabs.parseFeedback(body))
    }

    @Test
    fun `artist radio URL requires artist mbid and recommendation parser keeps scores`() {
        val url = ListenBrainzLabs.artistRadioUrl(seedA)
        assertTrue(url.contains("/1/lb-radio/artist/$seedA"))
        val recommendations = JSONObject().put(
            "payload",
            JSONObject().put(
                "mbids", JSONArray().put(
                    JSONObject().put("recording_mbid", seedB).put("score", 3.25),
                )
            ),
        )
        assertEquals(
            3.25,
            ListenBrainzLabs.parseCfRecommendations(recommendations).single().score!!,
            0.0
        )
    }
}
