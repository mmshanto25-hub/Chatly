package com.example.chat.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.model.Conversation
import com.example.chat.data.model.User
import com.example.chat.data.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class UserSearchUiState(
    val searchQuery: String = "",
    val allUsers: List<User> = emptyList(),
    val searchResults: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class UserSearchViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = authRepository.currentUserId ?: ""

    private val _uiState = MutableStateFlow(UserSearchUiState())
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()

    init {
        loadAllUsers()
    }

    private fun loadAllUsers() {
        _uiState.update { it.copy(isLoading = true) }
        firestore.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = error.localizedMessage) }
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val users = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                    .filter { it.uid != currentUserId }
                _uiState.update { state ->
                    val trimmed = state.searchQuery.trim().lowercase(Locale.getDefault())
                    val filtered = if (trimmed.isBlank()) {
                        users
                    } else {
                        users.filter { user ->
                            user.displayName.lowercase(Locale.getDefault()).contains(trimmed) ||
                            user.email.lowercase(Locale.getDefault()).contains(trimmed)
                        }
                    }
                    state.copy(allUsers = users, searchResults = filtered, isLoading = false)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        val trimmed = query.trim().lowercase(Locale.getDefault())
        val filtered = if (trimmed.isBlank()) {
            uiState.value.allUsers
        } else {
            uiState.value.allUsers.filter { user ->
                user.displayName.lowercase(Locale.getDefault()).contains(trimmed) ||
                user.email.lowercase(Locale.getDefault()).contains(trimmed)
            }
        }
        _uiState.update { it.copy(searchResults = filtered) }
    }

    fun startConversation(recipient: User, onConversationReady: (conversationId: String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val recipientDoc = try {
                    firestore.collection("users").document(recipient.uid).get(com.google.firebase.firestore.Source.SERVER).await()
                } catch (_: Exception) {
                    firestore.collection("users").document(recipient.uid).get().await()
                }
                if (!recipientDoc.exists()) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "This user has been deleted and cannot be messaged.") }
                    return@launch
                }
                val querySnapshot = firestore.collection("conversations")
                    .whereArrayContains("participantIds", currentUserId)
                    .get()
                    .await()

                var existingConvId: String? = null
                for (doc in querySnapshot.documents) {
                    val participants = doc["participantIds"] as? List<*>
                    if (participants != null && participants.contains(recipient.uid) && participants.size == 2) {
                        existingConvId = doc.getString("conversationId") ?: doc.id
                        break
                    }
                }

                if (existingConvId != null) {
                    onConversationReady(existingConvId)
                    return@launch
                }

                val convRef = firestore.collection("conversations").document()
                val conversationId = convRef.id

                val currentUserDoc = firestore.collection("users").document(currentUserId).get().await()
                val currentUserName = currentUserDoc.getString("displayName") ?: "User"
                val currentUserPhoto = currentUserDoc.getString("photoUrl") ?: ""

                val conversation = Conversation(
                    conversationId = conversationId,
                    type = "direct",
                    participantIds = listOf(currentUserId, recipient.uid),
                    participantNames = mapOf(
                        currentUserId to currentUserName,
                        recipient.uid to recipient.displayName
                    ),
                    participantPhotos = mapOf(
                        currentUserId to currentUserPhoto,
                        recipient.uid to recipient.photoUrl
                    ),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    lastMessage = "Started a conversation",
                    lastMessageAt = System.currentTimeMillis(),
                    lastMessageSenderId = currentUserId
                )

                convRef.set(conversation).await()
                onConversationReady(conversationId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to start conversation: ${e.localizedMessage}") }
            }
        }
    }

    fun initiateCall(recipient: User, callType: String = "audio", onCallReady: (channelName: String) -> Unit) {
        viewModelScope.launch {
            try {
                val channelName = "test_${currentUserId}_${recipient.uid}"
                val callRef = firestore.collection("calls").document()
                val callId = callRef.id

                val currentUserDoc = try {
                    firestore.collection("users").document(currentUserId).get().await()
                } catch (_: Exception) {
                    null
                }
                val myName = currentUserDoc?.getString("displayName") ?: "User"
                val myPhoto = currentUserDoc?.getString("photoUrl") ?: ""

                val recipientDoc = try {
                    firestore.collection("users").document(recipient.uid).get().await()
                } catch (_: Exception) {
                    null
                }
                val recipientName = recipientDoc?.getString("displayName") ?: recipient.displayName
                val recipientPhoto = recipientDoc?.getString("photoUrl") ?: recipient.photoUrl

                val callData = mapOf(
                    "callId" to callId,
                    "channelName" to channelName,
                    "callerId" to currentUserId,
                    "callerName" to myName,
                    "callerPhoto" to myPhoto,
                    "receiverId" to recipient.uid,
                    "receiverName" to recipientName,
                    "receiverPhoto" to recipientPhoto,
                    "callType" to callType,
                    "status" to "calling",
                    "timestamp" to System.currentTimeMillis()
                )

                callRef.set(callData).await()
                onCallReady(channelName)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to initiate call: ${e.localizedMessage}") }
            }
        }
    }
}
