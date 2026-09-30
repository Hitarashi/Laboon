package org.shilpo.laboon.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object SongCache {
    private const val MAX_CACHE_BYTES = 1024L * 1024L * 1024L
    private val lock = Any()
    private var cacheInstance: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        return synchronized(lock) {
            cacheInstance ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, "song_cache")
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                SimpleCache(cacheDir, evictor, databaseProvider).also {
                    cacheInstance = it
                }
            }
        }
    }
}
