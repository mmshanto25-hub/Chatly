package com.example.chat.data.model

data class Message(
    val messageId: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val text: String = "",
    val messageType: String = "text",
    val createdAt: Long = System.currentTimeMillis(),
    val readBy: List<String> = emptyList(),
    val attachmentUrl: String = ""
)
