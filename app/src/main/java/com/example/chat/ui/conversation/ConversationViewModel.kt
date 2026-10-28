package com.example.chat.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.model.Message
import com.example.chat.data.repository.AuthRepository
import com.example.chat.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val messageInput: String = ""
)

class ConversationViewModel(
    val conversationId: String
) : ViewModel() {
    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()

    val currentUserId: String = authRepository.currentUserId ?: ""

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    init {
        loadMessages()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            chatRepository.getMessagesFlow(conversationId).collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    fun updateMessageInput(input: String) {
        _uiState.update { it.copy(messageInput = input) }
    }

    fun sendMessage() {
        val text = uiState.value.messageInput.trim()
        if (text.isBlank() || currentUserId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(messageInput = "") }
            chatRepository.sendMessage(conversationId, currentUserId, text)
        }
    }
}
