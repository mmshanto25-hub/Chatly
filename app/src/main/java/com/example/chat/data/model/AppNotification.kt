package com.example.chat.data.model

data class AppNotification(
    val notificationId: String = "",
    val type: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val actorPhoto: String = "",
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val storyId: String = "",
    val commentId: String = "",
    val recipientCommentId: String = "",
    val conversationId: String = ""
)
