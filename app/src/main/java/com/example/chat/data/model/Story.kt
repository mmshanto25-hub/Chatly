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
    val expiresAt: Long = System.currentTimeMillis() + ACTIVE_DURATION_MS,
    val viewedBy: List<String> = emptyList(),
    val lovedBy: List<String> = emptyList()
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt

    companion object {
        const val ACTIVE_DURATION_MS = 24 * 60 * 60 * 1000L
        const val IMAGE_DISPLAY_DURATION_MS = 30_000L
        const val MAX_VIDEO_DURATION_MS = 60_000L
    }
}

data class StoryComment(
    val commentId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhoto: String = "",
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lovedBy: List<String> = emptyList(),
    val parentCommentId: String = "",
    val replyToCommentId: String = "",
    val mentionIds: List<String> = emptyList()
)
