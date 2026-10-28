package com.example.chat.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.model.User
import com.example.chat.data.repository.AuthRepository
import com.example.chat.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UserSearchUiState(
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class UserSearchViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()
    private val currentUserId = authRepository.currentUserId ?: ""

    private val _uiState = MutableStateFlow(UserSearchUiState())
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isNotBlank()) {
            searchUsers(query)
        } else {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    private fun searchUsers(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val users = chatRepository.searchUsers(query, currentUserId)
            _uiState.update { it.copy(searchResults = users, isLoading = false) }
        }
    }

    fun startConversation(recipient: User, onConversationReady: (conversationId: String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = chatRepository.getOrCreateConversation(
                currentUserId = currentUserId,
                recipientId = recipient.uid,
                recipientName = recipient.displayName,
                recipientPhoto = recipient.photoUrl
            )
            if (result.isSuccess) {
                val conversationId = result.getOrNull() ?: ""
                onConversationReady(conversationId)
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to start conversation") }
            }
        }
    }
}
