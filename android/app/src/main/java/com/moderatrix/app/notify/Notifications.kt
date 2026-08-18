package com.moderatrix.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.moderatrix.app.MainActivity
import android.app.PendingIntent
import android.content.Intent

const val CHANNEL_ID = "moderatrix_reminders"
const val SYNC_CHANNEL_ID = "moderatrix_sync_status"
const val NOTIFICATION_ID_REMINDER = 1001
const val NOTIFICATION_ID_SYNC_STALE = 1002

object Notifications {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Moderatrix Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders to log your activities and vitals"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    SYNC_CHANNEL_ID,
                    "Moderatrix Sync Status",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Warns when the app hasn't been able to sync with the server"
                }
            )
        }
    }

    fun showReminder(context: Context, title: String, text: String) {
        show(context, CHANNEL_ID, NOTIFICATION_ID_REMINDER, title, text)
    }

    fun showSyncStale(context: Context, title: String, text: String) {
        show(context, SYNC_CHANNEL_ID, NOTIFICATION_ID_SYNC_STALE, title, text)
    }

    private fun show(context: Context, channelId: String, notificationId: Int, title: String, text: String) {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }
}
