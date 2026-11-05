package com.example.chat.data.model

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val username: String = "",
    val photoUrl: String = "",
    val coverPhotoUrl: String = "",
    val profilePictures: List<String> = emptyList(),
    val coverPictures: List<String> = emptyList(),
    val stories: List<String> = emptyList(),
    val profileVisitorsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false,
    val lastSeenAt: Any? = null,
    val storyNote: String = "",
    val storyNoteExpiresAt: Long = 0L,
    val storyNoteCreatedAt: Long = 0L,
    val storyNoteViewedBy: List<String> = emptyList(),
    val storyNoteLovedBy: List<String> = emptyList()
)
