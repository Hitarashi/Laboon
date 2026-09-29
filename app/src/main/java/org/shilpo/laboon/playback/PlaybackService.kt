package org.shilpo.laboon.playback

import android.app.Service
import android.content.Intent
import android.os.IBinder

class PlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
