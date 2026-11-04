package com.example.chat.ui.chats

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.core.utils.CloudinaryUploader
import com.example.chat.data.local.SavedAccountManager
import com.example.chat.data.model.Conversation
import com.example.chat.data.model.Story
import com.example.chat.data.model.User
import com.example.chat.data.repository.AuthRepository
import com.example.chat.data.repository.ChatRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

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
    val selectedFilter: String = "All",
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    val isLoading: Boolean = true,
    val isUploadingStory: Boolean = false
)

class ChatListViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()

    val currentUserId: String = authRepository.currentUserId ?: ""

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        loadData()
        loadStories()
    }

    private fun loadData() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUserData()
            _uiState.update { it.copy(currentUser = user) }

            FirebaseFirestore.getInstance().collection("users").addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val map = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                        .associateBy { it.uid }
                    _uiState.update { it.copy(usersMap = map) }
                }
            }

            if (currentUserId.isNotBlank()) {
                chatRepository.getConversationsFlow(currentUserId).collect { convs ->
                    val filtered = applyFilter(convs, _uiState.value.selectedFilter, _uiState.value.showArchived, _uiState.value.searchQuery)
                    _uiState.update { it.copy(conversations = convs, filteredConversations = filtered, isLoading = false) }
                    markIncomingMessagesAsDelivered(convs)
                }
            }
        }
    }

    private fun loadStories() {
        FirebaseFirestore.getInstance().collection("stories")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val allStories = snapshot.documents.mapNotNull { it.toObject(Story::class.java) }
                    .filter { !it.isExpired() }

                val myStories = allStories.filter { it.userId == currentUserId }
                    .sortedByDescending { it.createdAt }

                val otherStories = allStories.filter { it.userId != currentUserId }
                    .groupBy { it.userId }
                    .map { (userId, userStoryList) ->
                        val sorted = userStoryList.sortedBy { it.createdAt }
                        val first = sorted.first()
                        UserStories(
                            userId = userId,
                            userName = first.userName,
                            userPhoto = first.userPhoto,
                            stories = sorted
                        )
                    }

                _uiState.update {
                    it.copy(
                        activeStories = otherStories,
                        myStories = myStories
                    )
                }
            }
    }

    fun uploadStory(context: Context, uri: Uri, caption: String = "") {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingStory = true) }
            try {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val path = uri.toString().lowercase()
                val isVideo = mimeType.startsWith("video", ignoreCase = true) ||
                        path.endsWith(".mp4") || path.endsWith(".mov") ||
                        path.endsWith(".mkv") || path.endsWith(".webm")
                val mediaType = if (isVideo) "video" else "image"

                val downloadUrl = CloudinaryUploader.uploadToCloudinary(
                    context = context,
                    imageUri = uri,
                    cloudName = "tmtqcamm",
                    uploadPreset = "chatly_unsigned"
                )
                if (downloadUrl != null) {
                    val db = FirebaseFirestore.getInstance()
                    val storyRef = db.collection("stories").document()
                    val user = _uiState.value.currentUser
                    val userName = user?.displayName ?: "User"
                    val userPhoto = user?.photoUrl ?: ""

                    val story = Story(
                        storyId = storyRef.id,
                        userId = currentUserId,
                        userName = userName,
                        userPhoto = userPhoto,
                        mediaUrl = downloadUrl,
                        mediaType = mediaType,
                        caption = caption,
                        createdAt = System.currentTimeMillis(),
                        expiresAt = System.currentTimeMillis() + (70 * 60 * 1000L)
                    )
                    storyRef.set(story).await()

                    // Automatically save to user profile
                    db.collection("users").document(currentUserId)
                        .update("stories", FieldValue.arrayUnion(downloadUrl))
                        .await()

                    val updatedStories = (user?.stories ?: emptyList()) + downloadUrl
                    val updatedUser = user?.copy(stories = updatedStories)
                    _uiState.update { it.copy(currentUser = updatedUser) }
                }
            } catch (_: Exception) {
            } finally {
                _uiState.update { it.copy(isUploadingStory = false) }
            }
        }
    }

    private fun markIncomingMessagesAsDelivered(conversations: List<Conversation>) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance()
                for (conv in conversations) {
                    val undeliveredQuery = firestore.collection("conversations")
                        .document(conv.conversationId)
                        .collection("messages")
                        .whereEqualTo("status", "sent")
                        .get()
                        .await()

                    val batch = firestore.batch()
                    var hasUpdates = false
                    for (doc in undeliveredQuery.documents) {
                        val senderId = doc.getString("senderId") ?: ""
                        if (senderId != currentUserId) {
                            batch.update(doc.reference, "status", "delivered")
                            hasUpdates = true
                        }
                    }
                    if (hasUpdates) {
                        if (conv.lastMessageSenderId != currentUserId && conv.lastMessageStatus == "sent") {
                            val convRef = firestore.collection("conversations").document(conv.conversationId)
                            batch.update(convRef, "lastMessageStatus", "delivered")
                        }
                        batch.commit().await()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun setFilter(filter: String) {
        _uiState.update { state ->
            val filtered = applyFilter(state.conversations, filter, state.showArchived, state.searchQuery)
            state.copy(selectedFilter = filter, filteredConversations = filtered)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyFilter(state.conversations, state.selectedFilter, state.showArchived, query)
            state.copy(searchQuery = query, filteredConversations = filtered)
        }
    }

    fun toggleShowArchived() {
        _uiState.update { state ->
            val newArchivedState = !state.showArchived
            val filtered = applyFilter(state.conversations, state.selectedFilter, newArchivedState, state.searchQuery)
            state.copy(showArchived = newArchivedState, filteredConversations = filtered)
        }
    }

    private fun applyFilter(conversations: List<Conversation>, filter: String, showArchived: Boolean, query: String): List<Conversation> {
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

        return filteredByType.filter { conv ->
            val matchName = conv.participantNames.values.any { it.lowercase(Locale.getDefault()).contains(trimmedQuery) }
            val matchMsg = conv.lastMessage.lowercase(Locale.getDefault()).contains(trimmedQuery)
            matchName || matchMsg
        }
    }

    fun togglePin(conversationId: String, currentPin: Boolean) {
        viewModelScope.launch {
            chatRepository.togglePin(conversationId, currentUserId, currentPin)
        }
    }

    fun toggleArchive(conversationId: String, currentArchive: Boolean) {
        viewModelScope.launch {
            chatRepository.toggleArchive(conversationId, currentUserId, currentArchive)
        }
    }

    fun toggleMute(conversationId: String, currentMute: Boolean) {
        viewModelScope.launch {
            chatRepository.toggleMute(conversationId, currentUserId, currentMute)
        }
    }

    fun deleteConversationForUser(conversationId: String) {
        viewModelScope.launch {
            chatRepository.deleteConversationForUser(conversationId, currentUserId)
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }

    fun switchAccount(context: Context, email: String, password: String, onSwitched: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                val userData = authRepository.getCurrentUserData()
                SavedAccountManager.saveAccount(
                    context,
                    email,
                    userData?.displayName ?: email.substringBefore("@"),
                    userData?.photoUrl ?: "",
                    password
                )
                onSwitched()
            } else {
                onError(result.exceptionOrNull()?.localizedMessage ?: "Failed to switch account")
            }
        }
    }

    fun registerNewAccountAndSwitch(
        context: Context,
        fullName: String,
        emailOrUsername: String,
        password: String,
        onSwitched: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.register(fullName, emailOrUsername, password)
            if (result.isSuccess) {
                val userData = authRepository.getCurrentUserData()
                val email = if (!emailOrUsername.contains("@")) "$emailOrUsername@talky.com" else emailOrUsername
                SavedAccountManager.saveAccount(
                    context,
                    email,
                    fullName,
                    userData?.photoUrl ?: "",
                    password
                )
                onSwitched()
            } else {
                onError(result.exceptionOrNull()?.localizedMessage ?: "Failed to register new account")
            }
        }
    }
}
