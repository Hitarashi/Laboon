package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.FakeKeyValueStore

class AlbumDetailsCacheVersionTest {

    @Test
    fun load_ignoresEntriesFromAnOlderVersion() {
        val store = FakeKeyValueStore().apply {
            putString(
                "album_details_cache_v2",
                JSONObject()
                    .put("version", 1)
                    .put(
                        "entries",
                        JSONArray().put(
                            JSONObject().put("key", "album-1"),
                        ),
                    )
                    .toString(),
            )
        }

        assertNull(AlbumDetailsCache(store).load("album-1"))
    }
}
