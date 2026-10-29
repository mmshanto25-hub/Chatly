package com.example.chat.data.model

data class Conversation(
    val conversationId: String = "",
    val type: String = "direct",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastMessage: String = "",
    val lastMessageAt: Long = System.currentTimeMillis(),
    val lastMessageSenderId: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val nicknames: Map<String, String> = emptyMap(),
    val themeColor: String = "blue"
)
