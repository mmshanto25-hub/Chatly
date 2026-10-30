package com.example.chat.core.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chatly.app.R
import com.example.chat.MainActivity

object ActiveChatTracker {
    var activeConversationId: String? = null
}

object NotificationHelper {
    private const val CHANNEL_ID = "talky_messages_channel"
    private const val CHANNEL_NAME = "Talky Messages"
    private const val TAG = "NotificationHelper"

    fun showMessageNotification(context: Context, title: String, messageBody: String, conversationId: String, recipientId: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New message notifications for Talky"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("conversationId", conversationId)
                putExtra("recipientId", recipientId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                conversationId.hashCode(),
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
                .build()

            notificationManager.notify(conversationId.hashCode(), notification)
            Log.d(TAG, "Notification sent successfully for conversation: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification", e)
        }
    }
}
