package com.example.chat.ui.conversation

import android.content.Context
import android.media.MediaRecorder
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
import java.io.File

data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val conversation: Conversation? = null,
    val recipientPhoto: String = "",
    val recipientPresence: String = "offline",
    val recipientLastSeen: Long = 0L,
    val isLoading: Boolean = true,
    val messageInput: String = "",
    val isUploading: Boolean = false,
    val uploadProgress: Int = 0,
    val replyingToMessage: Message? = null,
    val isRecordingVoice: Boolean = false
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

    fun setReplyingToMessage(message: Message?) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun sendMessage() {
        val text = uiState.value.messageInput.trim()
        if (text.isBlank() || currentUserId.isBlank()) return

        val replyMsg = uiState.value.replyingToMessage
        val replyText = replyMsg?.text ?: ""
        val replySenderName = if (replyMsg != null) {
            val conv = uiState.value.conversation
            val name = conv?.participantNames?.get(replyMsg.senderId) ?: "User"
            val nick = conv?.nicknames?.get(replyMsg.senderId) ?: ""
            if (nick.isNotBlank()) nick else name
        } else ""

        viewModelScope.launch {
            _uiState.update { it.copy(messageInput = "", replyingToMessage = null) }
            chatRepository.sendMessage(conversationId, currentUserId, text, replyToText = replyText, replyToSenderName = replySenderName)
        }
    }

    fun sendMessageText(text: String) {
        if (text.isBlank() || currentUserId.isBlank()) return
        viewModelScope.launch {
            chatRepository.sendMessage(conversationId, currentUserId, text)
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

    fun createGroup(groupName: String, selectedUserIds: List<String>, groupPhotoUrl: String = "", onCreated: (groupConvId: String, groupName: String) -> Unit) {
        val conv = uiState.value.conversation
        val currentParticipants = conv?.participantIds ?: listOf(currentUserId)
        val allParticipants = (currentParticipants + selectedUserIds).distinct()
        viewModelScope.launch {
            val result = chatRepository.createGroupConversation(currentUserId, groupName, allParticipants, groupPhotoUrl)
            result.getOrNull()?.let { newConvId ->
                onCreated(newConvId, groupName)
            }
        }
    }

    fun updateNicknames(recipientId: String, myNickname: String, recipientNickname: String, myName: String, recipientOriginalName: String) {
        viewModelScope.launch {
            try {
                val myTrimmed = myNickname.trim()
                val recipientTrimmed = recipientNickname.trim()

                val updates = mutableMapOf<String, Any>()
                if (myTrimmed.isNotBlank()) {
                    updates["nicknames.$currentUserId"] = myTrimmed
                }
                if (recipientTrimmed.isNotBlank() && recipientId.isNotBlank()) {
                    updates["nicknames.$recipientId"] = recipientTrimmed
                }

                if (updates.isNotEmpty()) {
                    firestore.collection("conversations")
                        .document(conversationId)
                        .update(updates)
                        .await()

                    val systemText = buildString {
                        if (myTrimmed.isNotBlank() && recipientTrimmed.isBlank()) {
                            append("$myName changed their nickname to $myTrimmed.")
                        } else if (recipientTrimmed.isNotBlank() && myTrimmed.isBlank()) {
                            append("$myName changed $recipientOriginalName's nickname to $recipientTrimmed.")
                        } else if (myTrimmed.isNotBlank() && recipientTrimmed.isNotBlank()) {
                            append("$myName changed their nickname to $myTrimmed and $recipientOriginalName's nickname to $recipientTrimmed.")
                        }
                    }.trim()

                    if (systemText.isNotBlank()) {
                        chatRepository.sendMessage(
                            conversationId = conversationId,
                            senderId = currentUserId,
                            text = systemText,
                            messageType = "system"
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun addMemberToGroup(userIdToAdd: String, userName: String, userPhoto: String, adminName: String, onAdded: () -> Unit) {
        viewModelScope.launch {
            try {
                firestore.collection("conversations").document(conversationId).update(
                    mapOf(
                        "participantIds" to com.google.firebase.firestore.FieldValue.arrayUnion(userIdToAdd),
                        "participantNames.$userIdToAdd" to userName,
                        "participantPhotos.$userIdToAdd" to userPhoto
                    )
                ).await()

                chatRepository.sendMessage(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    text = "$adminName added $userName to the group.",
                    messageType = "system"
                )
                onAdded()
            } catch (_: Exception) {}
        }
    }

    fun updateGroupMemberNickname(targetUserId: String, targetName: String, nickname: String, adminName: String) {
        viewModelScope.launch {
            try {
                val trimmed = nickname.trim()
                firestore.collection("conversations")
                    .document(conversationId)
                    .update("nicknames.$targetUserId", trimmed)
                    .await()

                val sysText = if (trimmed.isBlank()) {
                    "$adminName removed nickname for $targetName."
                } else {
                    "$adminName changed $targetName's nickname to $trimmed."
                }
                chatRepository.sendMessage(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    text = sysText,
                    messageType = "system"
                )
            } catch (_: Exception) {}
        }
    }

    fun updateGroupPhoto(newPhotoUrl: String, adminName: String) {
        viewModelScope.launch {
            try {
                firestore.collection("conversations")
                    .document(conversationId)
                    .update(
                        mapOf(
                            "groupPhoto" to newPhotoUrl,
                            "participantPhotos.groupPhoto" to newPhotoUrl
                        )
                    )
                    .await()

                chatRepository.sendMessage(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    text = "$adminName changed the group picture.",
                    messageType = "system"
                )
            } catch (_: Exception) {}
        }
    }

    fun updateGroupName(newGroupName: String, adminName: String) {
        viewModelScope.launch {
            try {
                val trimmed = newGroupName.trim()
                if (trimmed.isBlank()) return@launch
                firestore.collection("conversations")
                    .document(conversationId)
                    .update(
                        mapOf(
                            "groupName" to trimmed,
                            "participantNames.groupName" to trimmed
                        )
                    )
                    .await()

                chatRepository.sendMessage(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    text = "$adminName changed the group name to $trimmed.",
                    messageType = "system"
                )
            } catch (_: Exception) {}
        }
    }

    fun sendMediaMessage(context: Context, uri: Uri, type: String) {
        sendMultipleMediaMessages(context, listOf(uri))
    }

    fun sendMultipleMediaMessages(context: Context, uris: List<Uri>) {
        if (currentUserId.isBlank() || uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, uploadProgress = 0) }
            try {
                val total = uris.size
                val imageVideoUris = mutableListOf<Uri>()
                val audioUris = mutableListOf<Uri>()

                uris.forEach { uri ->
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    val path = uri.toString().lowercase()
                    val type = if (mimeType.startsWith("audio", ignoreCase = true) || path.endsWith(".3gp") || path.endsWith(".m4a") || path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".aac")) {
                        "audio"
                    } else {
                        "image_or_video"
                    }
                    if (type == "audio") {
                        audioUris.add(uri)
                    } else {
                        imageVideoUris.add(uri)
                    }
                }

                // 1. Send audio files individually
                for (audioUri in audioUris) {
                    uploadAndSendSingleItem(context, audioUri, "audio")
                }

                // 2. Bundle image/video files into a media_group if > 1, or single if 1
                if (imageVideoUris.isNotEmpty()) {
                    val downloadUrls = mutableListOf<String>()
                    imageVideoUris.forEachIndexed { index, uri ->
                        val progressPercent = ((index + 1) * 100) / total
                        _uiState.update { it.copy(uploadProgress = progressPercent) }

                        val downloadUrl = CloudinaryUploader.uploadToCloudinary(
                            context = context,
                            imageUri = uri,
                            cloudName = "tmtqcamm",
                            uploadPreset = "chatly_unsigned"
                        )
                        if (downloadUrl != null) {
                            downloadUrls.add(downloadUrl)
                        }
                    }

                    if (downloadUrls.isNotEmpty()) {
                        val isMultiple = downloadUrls.size > 1
                        val firstUriStr = imageVideoUris.firstOrNull()?.toString()?.lowercase() ?: ""
                        val isVideo = firstUriStr.contains("video") || firstUriStr.endsWith(".mp4") || firstUriStr.endsWith(".mov") || firstUriStr.endsWith(".mkv") || firstUriStr.endsWith(".webm")

                        val messageType = if (isMultiple) "media_group" else if (isVideo) "video" else "image"
                        val textSummary = if (isMultiple) "📷 Media Album (${downloadUrls.size})" else if (isVideo) "📹 Video" else "📷 Photo"

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
                            messageType = messageType,
                            attachmentUrl = downloadUrls.firstOrNull() ?: "",
                            attachmentUrls = if (isMultiple) downloadUrls else emptyList(),
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
                }
            } catch (_: Exception) {
            } finally {
                _uiState.update { it.copy(isUploading = false, uploadProgress = 0) }
            }
        }
    }

    private suspend fun uploadAndSendSingleItem(context: Context, uri: Uri, type: String) {
        try {
            val downloadUrl = CloudinaryUploader.uploadToCloudinary(
                context = context,
                imageUri = uri,
                cloudName = "tmtqcamm",
                uploadPreset = "chatly_unsigned"
            )

            if (downloadUrl != null) {
                val textSummary = when (type) {
                    "image" -> "📷 Photo"
                    "video" -> "📹 Video"
                    "audio" -> "🎤 Voice message"
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
        } catch (_: Exception) {}
    }

    private var mediaRecorder: MediaRecorder? = null
    var audioFile: File? = null
        private set

    fun startRecordingVoice(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRecordingVoice = true) }
                audioFile = File.createTempFile("voice_", ".3gp", context.cacheDir)
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(audioFile!!.absolutePath)
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isRecordingVoice = false) }
            }
        }
    }

    fun stopAndSendVoiceMessage(context: Context) {
        viewModelScope.launch {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                _uiState.update { it.copy(isRecordingVoice = false) }
                audioFile?.let { file ->
                    if (file.exists() && file.length() > 0) {
                        viewModelScope.launch {
                            _uiState.update { it.copy(isUploading = true, uploadProgress = 0) }
                            uploadAndSendSingleItem(context, Uri.fromFile(file), "audio")
                            _uiState.update { it.copy(isUploading = false, uploadProgress = 0) }
                        }
                    }
                }
            } catch (_: Exception) {
                mediaRecorder = null
                _uiState.update { it.copy(isRecordingVoice = false) }
            }
        }
    }
}
