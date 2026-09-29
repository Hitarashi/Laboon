package org.shilpo.laboon.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.shilpo.laboon.R

class PlaybackService : MediaSessionService() {

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_notification_small)
            }
        setMediaNotificationProvider(notificationProvider)

        PlaybackServiceHolder.service = this
        PlaybackServiceHolder.mediaSession?.let { session ->
            runCatching { addSession(session) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PlaybackServiceHolder.service = this
        PlaybackServiceHolder.mediaSession?.let { session ->
            runCatching { addSession(session) }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        PlaybackServiceHolder.mediaSession

    override fun onDestroy() {
        PlaybackServiceHolder.mediaSession?.let { session ->
            runCatching { removeSession(session) }
        }
        if (PlaybackServiceHolder.service === this) {
            PlaybackServiceHolder.service = null
        }
        super.onDestroy()
    }
}

object PlaybackServiceHolder {
    @Volatile
    var mediaSession: MediaSession? = null

    @Volatile
    var service: PlaybackService? = null
}
