package com.example.chat.core.utils

import android.util.Log
import com.example.chat.ChatApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TalkyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FcmTokenManager.saveToken(userId, token)
        } else {
            Log.d(TAG, "Token refreshed before a user signed in; it will be synced after sign-in")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val notificationId = data["notificationId"].orEmpty()
        if (notificationId.isBlank()) {
            Log.w(TAG, "Ignoring an FCM message without a notification ID")
            return
        }
        val recipientId = data["recipientId"].orEmpty()
        if (recipientId.isNotBlank() && FirebaseAuth.getInstance().currentUser?.uid != recipientId) {
            Log.d(TAG, "Ignoring a notification addressed to a different signed-in account")
            return
        }

        NotificationHelper.showAppNotification(
            context = ChatApplication.instance,
            notificationId = notificationId,
            title = data["title"]?.ifBlank { "Talky" } ?: "Talky",
            messageBody = data["body"].orEmpty(),
            storyId = data["storyId"].orEmpty(),
            commentId = data["commentId"].orEmpty(),
            conversationId = data["conversationId"].orEmpty()
        )
    }

    private companion object {
        const val TAG = "TalkyMessagingService"
    }
}
