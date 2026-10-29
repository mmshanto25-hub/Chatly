package com.example.chat.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.model.Conversation
import com.example.chat.data.model.User
import com.example.chat.data.repository.AuthRepository
import com.example.chat.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatListUiState(
    val currentUser: User? = null,
    val conversations: List<Conversation> = emptyList(),
    val filteredConversations: List<Conversation> = emptyList(),
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

            if (currentUserId.isNotBlank()) {
                chatRepository.getConversationsFlow(currentUserId).collect { convs ->
                    val filtered = applyFilter(convs, _uiState.value.selectedFilter, _uiState.value.showArchived, _uiState.value.searchQuery)
                    _uiState.update { it.copy(conversations = convs, filteredConversations = filtered, isLoading = false) }
                }
            }
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
            conversations.filter { it.isArchived }
        } else {
            conversations.filter { !it.isArchived }
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
            chatRepository.togglePin(conversationId, currentPin)
        }
    }

    fun toggleArchive(conversationId: String, currentArchive: Boolean) {
        viewModelScope.launch {
            chatRepository.toggleArchive(conversationId, currentArchive)
        }
    }

    fun toggleMute(conversationId: String, currentMute: Boolean) {
        viewModelScope.launch {
            chatRepository.toggleMute(conversationId, currentMute)
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }
}
