package org.shilpo.laboon.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SleepTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SLEEP_TIMER_EXPIRED) {
            SleepTimerManagerHolder.getInstance(context).onTimerExpired()
        }
    }

    companion object {
        const val ACTION_SLEEP_TIMER_EXPIRED = "org.shilpo.laboon.action.SLEEP_TIMER_EXPIRED"

        fun createPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, SleepTimerReceiver::class.java).apply {
                action = ACTION_SLEEP_TIMER_EXPIRED
                setPackage(context.packageName)
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
