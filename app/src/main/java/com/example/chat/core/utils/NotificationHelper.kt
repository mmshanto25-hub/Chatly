package com.example.chat.core.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chatly.app.R
import com.example.chat.MainActivity

object ActiveChatTracker {
    var activeConversationId: String? = null
}

object NotificationHelper {
    private const val CHANNEL_ID = "talky_notifications_v2"
    private const val CHANNEL_NAME = "Talky Notifications"
    private const val TAG = "NotificationHelper"

    fun showMessageNotification(context: Context, title: String, messageBody: String, conversationId: String, recipientId: String) {
        showNotification(
            context = context,
            notificationId = conversationId,
            title = title,
            messageBody = messageBody,
            extras = mapOf(
                "conversationId" to conversationId,
                "recipientId" to recipientId
            )
        )
    }

    fun showAppNotification(
        context: Context,
        notificationId: String,
        title: String,
        messageBody: String,
        storyId: String = "",
        commentId: String = "",
        conversationId: String = ""
    ) {
        showNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            messageBody = messageBody,
            extras = mapOf(
                "storyId" to storyId,
                "commentId" to commentId,
                "conversationId" to conversationId,
                "openNotifications" to true
            )
        )
    }

    private fun showNotification(
        context: Context,
        notificationId: String,
        title: String,
        messageBody: String,
        extras: Map<String, Any>
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Messages and activity notifications for Talky"
                    enableVibration(true)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                extras.forEach { (key, value) ->
                    when (value) {
                        is String -> putExtra(key, value)
                        is Boolean -> putExtra(key, value)
                    }
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smallIconRes = try {
                R.drawable.notification_icon
            } catch (_: Exception) {
                android.R.drawable.ic_dialog_email
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(smallIconRes)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()

            notificationManager.notify(notificationId.hashCode(), notification)
            Log.d(TAG, "Notification shown: $notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification", e)
        }
    }
}
