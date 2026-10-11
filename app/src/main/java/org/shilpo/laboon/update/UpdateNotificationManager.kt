package org.shilpo.laboon.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.shilpo.laboon.MainActivity
import org.shilpo.laboon.R

object UpdateNotificationManager {

    const val CHANNEL_ID = "laboon_app_updates"
    const val NOTIFICATION_ID = 4001
    private const val REQUEST_CODE_CONTENT = 40011
    private const val REQUEST_CODE_DOWNLOAD = 40012
    private const val REQUEST_CODE_CHANGELOG = 40013

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.update_notification_channel_name)
            val descriptionText = context.getString(R.string.update_notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun notifyIfNewVersion(
        context: Context,
        release: AppRelease,
        preferences: AppUpdatePreferences,
    ): Boolean {
        if (preferences.lastNotifiedVersion == release.versionName) {
            return false
        }

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return false
        }

        createNotificationChannel(context)

        val title = context.getString(R.string.update_notification_title, release.versionName)
        val text = context.getString(R.string.update_notification_text)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val downloadTarget = release.downloadUrl ?: release.htmlUrl
        val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadTarget)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val downloadPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_DOWNLOAD,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val changelogIntent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val changelogPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_CHANGELOG,
            changelogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val bodyPreview = buildString {
            if (release.name.isNotBlank()) {
                append(release.name)
            }
            if (release.body.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(release.body)
            }
        }.ifBlank { text }

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(title)
            .bigText(bodyPreview)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(bigTextStyle)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                0,
                context.getString(R.string.update_action_download),
                downloadPendingIntent,
            )
            .addAction(
                0,
                context.getString(R.string.update_action_view),
                changelogPendingIntent,
            )
            .build()

        return try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            preferences.lastNotifiedVersion = release.versionName
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
