package com.example.chat.data.model

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val username: String = "",
    val photoUrl: String = "",
    val profilePictures: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val presence: String = "offline"
)
