package com.example.chat.ui.chats

import com.example.chat.data.model.Conversation
import com.example.chat.data.model.Story
import com.example.chat.data.model.User
import java.util.Locale

private fun lastSeenTimeMillis(user: User?): Long = when (val lastSeen = user?.lastSeenAt) {
    is com.google.firebase.Timestamp -> lastSeen.toDate().time
    is java.util.Date -> lastSeen.time
    is Number -> lastSeen.toLong()
    else -> 0L
}

private fun isCurrentlyOnline(user: User?, now: Long): Boolean {
    if (user == null) return false
    val lastSeen = lastSeenTimeMillis(user)
    val recentlyActive = lastSeen > 0L && now - lastSeen in 0..90_000L
    return user.isOnline || recentlyActive
}

internal fun sortChatListStories(
    stories: List<UserStories>,
    users: Map<String, User>
): List<UserStories> {
    val now = System.currentTimeMillis()
    return stories.sortedWith(
        compareByDescending<UserStories> { isCurrentlyOnline(users[it.userId], now) }
            .thenByDescending { it.stories.maxOfOrNull(Story::createdAt) ?: 0L }
            .thenBy { it.userName.lowercase(Locale.getDefault()) }
    )
}

internal fun sortChatListConversations(
    conversations: List<Conversation>,
    users: Map<String, User>,
    stories: List<UserStories>,
    currentUserId: String
): List<Conversation> {
    val storyUserIds = stories.mapTo(HashSet()) { it.userId }
    val now = System.currentTimeMillis()
    return conversations.sortedWith(
        compareByDescending<Conversation> { it.isPinnedFor(currentUserId) }
            .thenByDescending { conversation ->
                conversation.type != "group" &&
                    conversation.participantIds.any { it != currentUserId && it in storyUserIds }
            }
            .thenByDescending { conversation ->
                val recipientId = conversation.participantIds.firstOrNull { it != currentUserId }
                conversation.type != "group" && isCurrentlyOnline(users[recipientId], now)
            }
            .thenByDescending { conversation ->
                val recipientId = conversation.participantIds.firstOrNull { it != currentUserId }
                lastSeenTimeMillis(users[recipientId])
            }
            .thenByDescending { it.lastMessageAt }
    )
}

internal fun filterChatListConversations(
    conversations: List<Conversation>,
    filter: String,
    showArchived: Boolean,
    query: String,
    currentUserId: String
): List<Conversation> {
    val base = if (showArchived) {
        conversations.filter { it.isArchivedFor(currentUserId) }
    } else {
        conversations.filter { !it.isArchivedFor(currentUserId) }
    }

    val filteredByType = when (filter) {
        "Personal" -> base.filter { it.type == "direct" }
        "Groups" -> base.filter { it.type == "group" }
        else -> base
    }

    val trimmedQuery = query.trim().lowercase(Locale.getDefault())
    if (trimmedQuery.isBlank()) return filteredByType

    return filteredByType.filter { conversation ->
        val matchName = conversation.participantNames.values.any {
            it.lowercase(Locale.getDefault()).contains(trimmedQuery)
        }
        val matchMessage = conversation.lastMessage.lowercase(Locale.getDefault()).contains(trimmedQuery)
        matchName || matchMessage
    }
}
