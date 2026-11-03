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
    val profileVisitorsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val presence: String = "offline",
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)
