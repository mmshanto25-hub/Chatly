package com.example.chat.data.model

data class Conversation(
    val conversationId: String = "",
    val type: String = "direct",
    val groupName: String = "",
    val groupPhoto: String = "",
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
    val pinnedBy: List<String> = emptyList(),
    val archivedBy: List<String> = emptyList(),
    val mutedBy: List<String> = emptyList(),
    val deletedFor: List<String> = emptyList(),
    val pinnedMessageId: String = "",
    val pinnedMessageText: String = "",
    val nicknames: Map<String, String> = emptyMap(),
    val themeColor: String = "blue"
) {
    fun isPinnedFor(userId: String): Boolean = isPinned || pinnedBy.contains(userId)
    fun isArchivedFor(userId: String): Boolean = isArchived || archivedBy.contains(userId)
    fun isMutedFor(userId: String): Boolean = isMuted || mutedBy.contains(userId)

    fun getTitle(currentUserId: String): String {
        if (type == "group") {
            if (groupName.isNotBlank()) return groupName
            val storedGroupName = participantNames["groupName"]
            if (!storedGroupName.isNullOrBlank()) return storedGroupName
            val otherNames = participantNames.filterKeys { it != currentUserId && it != "groupName" }.values
            if (otherNames.isNotEmpty()) return otherNames.joinToString(", ")
            return "Group Chat"
        } else {
            val recipientId = participantIds.firstOrNull { it != currentUserId } ?: ""
            return participantNames[recipientId] ?: "Chat Partner"
        }
    }
}
