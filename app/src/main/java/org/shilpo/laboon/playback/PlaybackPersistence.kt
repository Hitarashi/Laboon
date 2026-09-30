package org.shilpo.laboon.playback

import org.shilpo.laboon.auth.KeyValueStore
import org.shilpo.laboon.home.HomeTrack

class PlaybackPersistence(private val store: KeyValueStore) {

    fun saveLastTrack(track: HomeTrack) {
        val encoded = QueuePersistenceCodec.encodeTrack(track)
        store.putString(KEY_LAST_TRACK, encoded)
    }

    fun getLastTrack(): HomeTrack? {
        val raw = store.getString(KEY_LAST_TRACK) ?: return null
        return QueuePersistenceCodec.decodeTrack(raw)
    }

    fun clearLastTrack() {
        store.remove(KEY_LAST_TRACK)
    }

    fun setPlayerDismissed(dismissed: Boolean) {
        store.putBoolean(KEY_PLAYER_DISMISSED, dismissed)
    }

    fun isPlayerDismissed(): Boolean {
        if (!store.contains(KEY_PLAYER_DISMISSED)) {
            return true
        }
        return store.getBoolean(KEY_PLAYER_DISMISSED)
    }

    fun saveLastPosition(positionMs: Long) {
        store.putLong(KEY_LAST_POSITION, positionMs)
    }

    fun getLastPosition(): Long = store.getLong(KEY_LAST_POSITION)

    fun saveLastDuration(durationMs: Long) {
        store.putLong(KEY_LAST_DURATION, durationMs)
    }

    fun getLastDuration(): Long = store.getLong(KEY_LAST_DURATION)

    private companion object {
        const val KEY_LAST_TRACK = "playback_last_track_v1"
        const val KEY_PLAYER_DISMISSED = "playback_player_dismissed_v1"
        const val KEY_LAST_POSITION = "playback_last_position_v1"
        const val KEY_LAST_DURATION = "playback_last_duration_v1"
    }
}
