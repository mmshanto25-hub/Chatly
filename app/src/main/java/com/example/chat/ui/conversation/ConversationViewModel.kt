package com.example.chat.ui.conversation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.core.utils.CloudinaryUploader
import com.example.chat.data.model.Conversation
import com.example.chat.data.model.Message
import com.example.chat.data.model.User
import com.example.chat.data.repository.AuthRepository
import com.example.chat.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val conversation: Conversation? = null,
    val recipientPhoto: String = "",
    val recipientPresence: String = "offline",
    val recipientLastSeen: Long = 0L,
    val isLoading: Boolean = true,
    val messageInput: String = "",
    val isUploading: Boolean = false,
    val uploadProgress: Int = 0
)

class ConversationViewModel(
    val conversationId: String
) : ViewModel() {
    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()
    private val firestore = FirebaseFirestore.getInstance()

    val currentUserId: String = authRepository.currentUserId ?: ""

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Realtime listener for conversation
            firestore.collection("conversations").document(conversationId)
                .addSnapshotListener { doc, _ ->
                    val conversation = doc?.toObject(Conversation::class.java)
                    if (conversation != null) {
                        val recipientId = conversation.participantIds.firstOrNull { it != currentUserId } ?: ""
                        val recipientPhoto = conversation.participantPhotos[recipientId] ?: ""

                        _uiState.update {
                            it.copy(
                                conversation = conversation,
                                recipientPhoto = recipientPhoto
                            )
                        }

                        if (recipientId.isNotBlank()) {
                            firestore.collection("users").document(recipientId).addSnapshotListener { userDoc, _ ->
                                val user = userDoc?.toObject(User::class.java)
                                if (user != null) {
                                    _uiState.update {
                                        it.copy(
                                            recipientPhoto = user.photoUrl.ifBlank { recipientPhoto },
                                            recipientPresence = user.presence,
                                            recipientLastSeen = user.lastSeenAt
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            chatRepository.getMessagesFlow(conversationId).collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    fun updateMessageInput(input: String) {
        _uiState.update { it.copy(messageInput = input) }
    }

    fun updateNickname(targetUserId: String, nickname: String) {
        viewModelScope.launch {
            try {
                val trimmed = nickname.trim()
                firestore.collection("conversations")
                    .document(conversationId)
                    .update("nicknames.$targetUserId", trimmed)
                    .await()
            } catch (_: Exception) {}
        }
    }

    fun updateThemeColor(theme: String) {
        viewModelScope.launch {
            try {
                firestore.collection("conversations")
                    .document(conversationId)
                    .update("themeColor", theme)
                    .await()
            } catch (_: Exception) {}
        }
    }

    fun sendMessage() {
        val text = uiState.value.messageInput.trim()
        if (text.isBlank() || currentUserId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(messageInput = "") }
            chatRepository.sendMessage(conversationId, currentUserId, text)
        }
    }

    fun sendMessageText(text: String) {
        if (text.isBlank() || currentUserId.isBlank()) return
        viewModelScope.launch {
            chatRepository.sendMessage(conversationId, currentUserId, text)
        }
    }

    fun togglePinMessage(messageId: String, messageText: String, isCurrentlyPinned: Boolean) {
        viewModelScope.launch {
            chatRepository.togglePinMessage(conversationId, messageId, isCurrentlyPinned, messageText)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(conversationId, messageId)
        }
    }

    fun fetchAvailableUsers(onResult: (List<User>) -> Unit) {
        viewModelScope.launch {
            val users = chatRepository.getAllUsers(currentUserId)
            onResult(users)
        }
    }

    fun createGroup(groupName: String, selectedUserIds: List<String>, onCreated: (groupConvId: String, groupName: String) -> Unit) {
        val conv = uiState.value.conversation
        val currentParticipants = conv?.participantIds ?: listOf(currentUserId)
        val allParticipants = (currentParticipants + selectedUserIds).distinct()
        viewModelScope.launch {
            val result = chatRepository.createGroupConversation(currentUserId, groupName, allParticipants)
            result.getOrNull()?.let { newConvId ->
                onCreated(newConvId, groupName)
            }
        }
    }

    fun sendMediaMessage(context: Context, uri: Uri, type: String) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, uploadProgress = 0) }
            try {
                val downloadUrl = CloudinaryUploader.uploadToCloudinary(
                    context = context,
                    imageUri = uri,
                    cloudName = "tmtqcamm",
                    uploadPreset = "chatly_unsigned",
                    onProgress = { percent: Int ->
                        _uiState.update { it.copy(uploadProgress = percent) }
                    }
                )

                if (downloadUrl != null) {
                    val textSummary = when (type) {
                        "image" -> "📷 Photo"
                        "video" -> "📹 Video"
                        "audio" -> "🎤 Voice message"
                        "document" -> "📄 Document"
                        else -> "📎 Attachment"
                    }

                    val msgRef = firestore.collection("conversations")
                        .document(conversationId)
                        .collection("messages")
                        .document()

                    val messageId = msgRef.id
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
                _uiState.update { it.copy(isUploading = false, uploadProgress = 0) }
            }
        }
    }
}
