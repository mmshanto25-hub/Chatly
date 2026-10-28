package com.example.chat.ui.conversation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.model.Message
import com.example.chat.data.repository.AuthRepository
import com.example.chat.data.repository.ChatRepository
import com.example.chat.ui.profile.uploadImageToCloudinary
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val messageInput: String = "",
    val isUploading: Boolean = false
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

    fun sendMediaMessage(context: Context, uri: Uri, type: String) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            try {
                val downloadUrl = uploadImageToCloudinary(context, uri, "tmtqcamm", "chatly_unsigned")
                if (downloadUrl != null) {
                    val firestore = FirebaseFirestore.getInstance()
                    val msgRef = firestore.collection("conversations")
                        .document(conversationId)
                        .collection("messages")
                        .document()

                    val messageId = msgRef.id
                    val textSummary = if (type == "image") "📷 Photo" else "📹 Video"
                    val message = Message(
                        messageId = messageId,
                        conversationId = conversationId,
                        senderId = currentUserId,
                        text = textSummary,
                        messageType = type,
                        attachmentUrl = downloadUrl,
                        createdAt = System.currentTimeMillis()
                    )

                    msgRef.set(message).await()

                    firestore.collection("conversations")
                        .document(conversationId)
                        .update(
                            mapOf(
                                "lastMessage" to textSummary,
                                "lastMessageAt" to System.currentTimeMillis(),
                                "lastMessageSenderId" to currentUserId,
                                "updatedAt" to System.currentTimeMillis()
                            )
                        ).await()
                }
            } catch (_: Exception) {
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }
}
