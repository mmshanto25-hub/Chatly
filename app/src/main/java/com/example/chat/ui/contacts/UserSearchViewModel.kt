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

data class CallHistoryLog(
    val callId: String = "",
    val channelName: String = "",
    val otherPersonId: String = "",
    val otherPersonName: String = "",
    val otherPersonPhoto: String = "",
    val callType: String = "audio",
    val status: String = "calling",
    val isOutgoing: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

enum class UserSortMode {
    ONLINE_ACTIVITY,
    NAME_A_TO_Z
}

data class UserSearchUiState(
    val searchQuery: String = "",
    val selectedTab: Int = 2, // 0: Contacts (chatted), 1: Call List, 2: User List (default)
    val userSortMode: UserSortMode = UserSortMode.ONLINE_ACTIVITY,
    val allUsers: List<User> = emptyList(),
    val chatContacts: List<User> = emptyList(),
    val callLogs: List<CallHistoryLog> = emptyList(),
    val searchResults: List<User> = emptyList(),
    val chatContactsFiltered: List<User> = emptyList(),
    val callLogsFiltered: List<CallHistoryLog> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class UserSearchViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = authRepository.currentUserId ?: ""

    private val _uiState = MutableStateFlow(UserSearchUiState())
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()
    private var chatContactIds: Set<String> = emptySet()

    init {
        loadAllUsers()
        loadChatContacts()
        loadCallLogs()
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                filterData(uiState.value.searchQuery)
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
        updateSearchQuery(uiState.value.searchQuery)
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
                    state.copy(
                        allUsers = users,
                        chatContacts = users.filter { it.uid in chatContactIds },
                        isLoading = false
                    )
                }
                filterData(uiState.value.searchQuery)
            }
        }
    }

    private fun loadChatContacts() {
        if (currentUserId.isBlank()) return
        firestore.collection("conversations")
            .whereArrayContains("participantIds", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val otherParticipantIds = snapshot.documents.mapNotNull { doc ->
                    val participants = doc["participantIds"] as? List<*>
                    participants?.firstOrNull { it != currentUserId } as? String
                }.distinct()

                chatContactIds = otherParticipantIds.toSet()
                _uiState.update { state ->
                    state.copy(chatContacts = state.allUsers.filter { it.uid in chatContactIds })
                }
                filterData(uiState.value.searchQuery)
            }
    }

    private fun loadCallLogs() {
        if (currentUserId.isBlank()) return
        firestore.collection("calls")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val logs = snapshot.documents.mapNotNull { doc ->
                    val callerId = doc.getString("callerId") ?: ""
                    val receiverId = doc.getString("receiverId") ?: ""
                    if (callerId == currentUserId || receiverId == currentUserId) {
                        val isOutgoing = (callerId == currentUserId)
                        CallHistoryLog(
                            callId = doc.id,
                            channelName = doc.getString("channelName") ?: "",
                            otherPersonId = if (isOutgoing) receiverId else callerId,
                            otherPersonName = if (isOutgoing) (doc.getString("receiverName") ?: "User") else (doc.getString("callerName") ?: "User"),
                            otherPersonPhoto = if (isOutgoing) (doc.getString("receiverPhoto") ?: "") else (doc.getString("callerPhoto") ?: ""),
                            callType = doc.getString("callType") ?: "audio",
                            status = doc.getString("status") ?: "calling",
                            isOutgoing = isOutgoing,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    } else null
                }.sortedByDescending { it.timestamp }

                _uiState.update { it.copy(callLogs = logs) }
                filterData(uiState.value.searchQuery)
            }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterData(query)
    }

    fun setUserSortMode(sortMode: UserSortMode) {
        _uiState.update { it.copy(userSortMode = sortMode) }
        filterData(uiState.value.searchQuery)
    }

    private fun lastSeenTimeMillis(user: User): Long = when (val lastSeen = user.lastSeenAt) {
        is com.google.firebase.Timestamp -> lastSeen.toDate().time
        is java.util.Date -> lastSeen.time
        is Number -> lastSeen.toLong()
        else -> 0L
    }

    private fun isCurrentlyOnline(user: User, now: Long): Boolean {
        val lastSeen = lastSeenTimeMillis(user)
        return user.isOnline || (lastSeen > 0L && now - lastSeen in 0..90_000L)
    }

    private fun userName(user: User): String =
        user.displayName.trim().ifBlank { user.username.ifBlank { user.email } }
            .lowercase(Locale.getDefault())

    private fun sortUsers(users: List<User>, sortMode: UserSortMode): List<User> {
        if (sortMode == UserSortMode.NAME_A_TO_Z) {
            return users.sortedBy(::userName)
        }

        val now = System.currentTimeMillis()
        return users.sortedWith(Comparator { left, right ->
            val leftOnline = isCurrentlyOnline(left, now)
            val rightOnline = isCurrentlyOnline(right, now)
            when {
                leftOnline != rightOnline -> rightOnline.compareTo(leftOnline)
                leftOnline -> {
                    val recentFirst = lastSeenTimeMillis(right).compareTo(lastSeenTimeMillis(left))
                    if (recentFirst != 0) recentFirst else userName(left).compareTo(userName(right))
                }
                else -> userName(left).compareTo(userName(right))
            }
        })
    }

    private fun filterData(query: String) {
        val trimmed = query.trim().lowercase(Locale.getDefault())
        _uiState.update { state ->
            val filteredUsers = if (trimmed.isBlank()) {
                state.allUsers
            } else {
                state.allUsers.filter { user ->
                    user.displayName.lowercase(Locale.getDefault()).contains(trimmed) ||
                    user.email.lowercase(Locale.getDefault()).contains(trimmed)
                }
            }.let { sortUsers(it, state.userSortMode) }

            val filteredContacts = if (trimmed.isBlank()) {
                state.chatContacts
            } else {
                state.chatContacts.filter { user ->
                    user.displayName.lowercase(Locale.getDefault()).contains(trimmed) ||
                    user.email.lowercase(Locale.getDefault()).contains(trimmed)
                }
            }.let { sortUsers(it, state.userSortMode) }

            val filteredLogs = if (trimmed.isBlank()) {
                state.callLogs
            } else {
                state.callLogs.filter { log ->
                    log.otherPersonName.lowercase(Locale.getDefault()).contains(trimmed)
                }
            }

            state.copy(
                searchResults = filteredUsers,
                chatContactsFiltered = filteredContacts,
                callLogsFiltered = filteredLogs
            )
        }
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
                val callRef = firestore.collection("calls").document()
                val callId = callRef.id
                val channelName = "test_${currentUserId}_${recipient.uid}_${callId}"

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

    fun initiateCallByLog(callLog: CallHistoryLog, onCallReady: (channelName: String) -> Unit) {
        val targetUser = User(uid = callLog.otherPersonId, displayName = callLog.otherPersonName, photoUrl = callLog.otherPersonPhoto)
        initiateCall(targetUser, callType = callLog.callType, onCallReady = onCallReady)
    }
}
