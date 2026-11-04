package com.example.chat.data.model

data class Story(
    val storyId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhoto: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "image",
    val caption: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (70 * 60 * 1000L), // 70 Minutes
    val viewedBy: List<String> = emptyList()
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
}
