package com.example.chat.ui.chats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.local.SavedAccountManager
import com.example.chat.data.model.Conversation
import com.example.chat.data.model.User
import com.example.chat.data.repository.AuthRepository
import com.example.chat.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class ChatListUiState(
    val currentUser: User? = null,
    val conversations: List<Conversation> = emptyList(),
    val filteredConversations: List<Conversation> = emptyList(),
    val usersMap: Map<String, User> = emptyMap(),
    val selectedFilter: String = "All",
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    val isLoading: Boolean = true
)

class ChatListViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()

    val currentUserId: String = authRepository.currentUserId ?: ""

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        loadData()
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
