package com.example.chat.ui.chats

import com.example.chat.data.model.AppNotification
import com.example.chat.data.model.Conversation
import com.example.chat.data.model.Story
import com.example.chat.data.model.User

data class UserStories(
    val userId: String,
    val userName: String,
    val userPhoto: String,
    val stories: List<Story>
)
data class ChatListUiState(
    val currentUser: User? = null,
    val conversations: List<Conversation> = emptyList(),
    val filteredConversations: List<Conversation> = emptyList(),
    val usersMap: Map<String, User> = emptyMap(),
    val activeStories: List<UserStories> = emptyList(),
    val myStories: List<Story> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val notificationError: String? = null,
    val selectedFilter: String = "All",
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    val isLoading: Boolean = true,
    val isUploadingStory: Boolean = false,
    val storyUploadError: String? = null
)
