package com.example.chat.ui.chats

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.core.utils.CloudinaryUploader
import com.example.chat.core.utils.FcmTokenManager
import com.example.chat.data.local.NotificationLocalManager
import com.example.chat.data.local.SavedAccountManager
import com.example.chat.data.model.AppNotification
import com.example.chat.data.model.Conversation
import com.example.chat.data.model.Story
import com.example.chat.data.model.StoryComment
import com.example.chat.data.model.User
import com.example.chat.data.repository.AuthRepository
import com.example.chat.data.repository.ChatRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatListViewModel : ViewModel() {
    internal val authRepository = AuthRepository()
    internal val chatRepository = ChatRepository()

    val currentUserId: String = authRepository.currentUserId ?: ""

    internal val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        FcmTokenManager.syncCurrentUserToken()
        loadData()
        loadStories()
        loadNotifications()
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                _uiState.update { state ->
                    state.copy(
                        filteredConversations = sortConversations(
                            applyFilter(
                                state.conversations,
                                state.selectedFilter,
                                state.showArchived,
                                state.searchQuery
                            ),
                            state.usersMap,
                            state.activeStories
                        ),
                        activeStories = sortStories(state.activeStories, state.usersMap)
                    )
                }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUserData()
            _uiState.update { it.copy(currentUser = user) }

            FirebaseFirestore.getInstance().collection("users").addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val map = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                        .associateBy { it.uid }
                    _uiState.update { state ->
                        state.copy(
                            usersMap = map,
                            filteredConversations = sortConversations(
                                applyFilter(
                                    state.conversations,
                                    state.selectedFilter,
                                    state.showArchived,
                                    state.searchQuery
                                ),
                                map,
                                state.activeStories
                            ),
                            activeStories = sortStories(state.activeStories, map)
                        )
                    }
                }
            }

            if (currentUserId.isNotBlank()) {
                chatRepository.getConversationsFlow(currentUserId).collect { convs ->
                    _uiState.update { state ->
                        val filtered = applyFilter(
                            convs,
                            state.selectedFilter,
                            state.showArchived,
                            state.searchQuery
                        )
                        state.copy(
                            conversations = convs,
                            filteredConversations = sortConversations(
                                filtered,
                                state.usersMap,
                                state.activeStories
                            ),
                            isLoading = false
                        )
                    }
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
                    val stories = sortStories(otherStories, it.usersMap)
                    it.copy(
                        activeStories = stories,
                        myStories = myStories,
                        filteredConversations = sortConversations(
                            applyFilter(
                                it.conversations,
                                it.selectedFilter,
                                it.showArchived,
                                it.searchQuery
                            ),
                            it.usersMap,
                            stories
                        )
                    )
                }
            }
    }

    fun loadStoryForNotification(
        notification: AppNotification,
        onLoaded: (UserStories) -> Unit,
        onError: (String) -> Unit
    ) {
        if (notification.storyId.isBlank()) {
            onError("This notification doesn't have a linked story.")
            return
        }
        viewModelScope.launch {
            try {
                val story = FirebaseFirestore.getInstance()
                    .collection("stories")
                    .document(notification.storyId)
                    .get()
                    .await()
                    .toObject(Story::class.java)
                    ?: throw IllegalStateException("The story is no longer available.")
                val user = _uiState.value.usersMap[story.userId]
                onLoaded(
                    UserStories(
                        userId = story.userId,
                        userName = user?.displayName?.ifBlank { user.username }
                            ?: story.userName.ifBlank { "User" },
                        userPhoto = user?.photoUrl?.ifBlank { story.userPhoto } ?: story.userPhoto,
                        stories = listOf(story)
                    )
                )
            } catch (error: Exception) {
                Log.e("ChatListViewModel", "Failed to open story from notification", error)
                onError(error.localizedMessage ?: "Couldn't open the story.")
            }
        }
    }

    private fun loadNotifications() {
        if (currentUserId.isBlank()) return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUserId)
            .collection("notifications")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatListViewModel", "Failed to load notifications", error)
                    _uiState.update { it.copy(notificationError = error.localizedMessage ?: "Couldn't load notifications.") }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val notifications = snapshot.documents
                        .mapNotNull { it.toObject(AppNotification::class.java)?.copy(notificationId = it.id) }
                        .map { notification ->
                            val isLocallyRead = NotificationLocalManager.isNotificationRead(
                                userId = currentUserId,
                                notificationId = notification.notificationId,
                                createdAt = notification.createdAt
                            )
                            if (isLocallyRead) notification.copy(isRead = true) else notification
                        }
                        .sortedByDescending { it.createdAt }
                    _uiState.update { it.copy(notifications = notifications, notificationError = null) }
                }
            }
    }

    private suspend fun createNotification(
        recipientId: String,
        type: String,
        message: String,
        storyId: String = "",
        commentId: String = "",
        recipientCommentId: String = "",
        conversationId: String = ""
    ) {
        if (recipientId.isBlank() || recipientId == currentUserId) return
        val actor = _uiState.value.currentUser
        val ref = FirebaseFirestore.getInstance()
            .collection("users")
            .document(recipientId)
            .collection("notifications")
            .document()
        ref.set(
            AppNotification(
                notificationId = ref.id,
                type = type,
                actorId = currentUserId,
                actorName = actor?.displayName?.ifBlank { actor.username } ?: "User",
                actorPhoto = actor?.photoUrl ?: "",
                message = message,
                createdAt = System.currentTimeMillis(),
                storyId = storyId,
                commentId = commentId,
                recipientCommentId = recipientCommentId,
                conversationId = conversationId
            )
        ).await()
    }

    fun markAllNotificationsRead() {
        if (currentUserId.isBlank()) return
        val currentNotifications = _uiState.value.notifications
        val unread = currentNotifications.filterNot { it.isRead }

        // Persist read state in SharedPreferences
        NotificationLocalManager.markAllRead(
            userId = currentUserId,
            notifications = currentNotifications
        )

        // Set all to read in UI state immediately (badge becomes 0)
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map { it.copy(isRead = true) },
                notificationError = null
            )
        }

        if (unread.isEmpty()) return

        // Sync read state to Firestore in background without reverting local state on error
        viewModelScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance()
                unread.chunked(450).forEach { page ->
                    val batch = firestore.batch()
                    page.forEach { notification ->
                        val ref = firestore.collection("users")
                            .document(currentUserId)
                            .collection("notifications")
                            .document(notification.notificationId)
                        batch.update(ref, "isRead", true)
                    }
                    batch.commit().await()
                }
            } catch (error: Exception) {
                Log.w("ChatListViewModel", "Failed to sync notifications read status to Firestore (local state preserved)", error)
            }
        }
    }

    fun markNotificationAsRead(notificationId: String) {
        if (notificationId.isBlank() || currentUserId.isBlank()) return
        NotificationLocalManager.markNotificationRead(
            userId = currentUserId,
            notificationId = notificationId
        )
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map {
                    if (it.notificationId == notificationId) it.copy(isRead = true) else it
                }
            )
        }
        viewModelScope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId)
                    .collection("notifications")
                    .document(notificationId)
                    .update("isRead", true)
                    .await()
            } catch (error: Exception) {
                Log.w("ChatListViewModel", "Failed to sync notification $notificationId to Firestore", error)
            }
        }
    }

    private fun sortStories(stories: List<UserStories>, users: Map<String, User>): List<UserStories> {
        return sortChatListStories(stories, users)
    }

    private fun sortConversations(
        conversations: List<Conversation>,
        users: Map<String, User>,
        stories: List<UserStories>
    ): List<Conversation> {
        return sortChatListConversations(conversations, users, stories, currentUserId)
    }

    fun uploadStory(context: Context, uri: Uri, caption: String = "") {
        if (currentUserId.isBlank()) {
            _uiState.update { it.copy(storyUploadError = "Sign in to upload a story.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingStory = true, storyUploadError = null) }
            try {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val path = uri.toString().lowercase()
                val isVideo = mimeType.startsWith("video", ignoreCase = true) ||
                        path.endsWith(".mp4") || path.endsWith(".mov") ||
                        path.endsWith(".mkv") || path.endsWith(".webm")
                val mediaType = if (isVideo) "video" else "image"

                if (isVideo) {
                    val retriever = MediaMetadataRetriever()
                    val durationMs = try {
                        retriever.setDataSource(context, uri)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()
                            ?: throw IllegalArgumentException("Couldn't read the video duration.")
                    } finally {
                        retriever.release()
                    }
                    require(durationMs <= Story.MAX_VIDEO_DURATION_MS) {
                        "Video stories must be 60 seconds or shorter."
                    }
                }

                val downloadUrl = CloudinaryUploader.uploadToCloudinary(
                    context = context,
                    imageUri = uri,
                    cloudName = "tmtqcamm",
                    uploadPreset = "chatly_unsigned"
                )
                if (downloadUrl == null) {
                    throw IllegalStateException("Couldn't upload the story. Please try again.")
                }

                val db = FirebaseFirestore.getInstance()
                val storyRef = db.collection("stories").document()
                val user = _uiState.value.currentUser
                val now = System.currentTimeMillis()

                val story = Story(
                    storyId = storyRef.id,
                    userId = currentUserId,
                    userName = user?.displayName ?: "User",
                    userPhoto = user?.photoUrl ?: "",
                    mediaUrl = downloadUrl,
                    mediaType = mediaType,
                    caption = caption,
                    createdAt = now,
                    expiresAt = now + Story.ACTIVE_DURATION_MS
                )
                storyRef.set(story).await()
            } catch (error: Exception) {
                Log.e("ChatListViewModel", "Failed to upload story", error)
                _uiState.update {
                    it.copy(
                        storyUploadError = error.message
                            ?: "Couldn't upload the story. Please try again."
                    )
                }
            } finally {
                _uiState.update { it.copy(isUploadingStory = false) }
            }
        }
    }

    fun clearStoryUploadError() {
        _uiState.update { it.copy(storyUploadError = null) }
    }

    fun setStoryNote(text: String, onError: (String) -> Unit) {
        if (currentUserId.isBlank()) {
            onError("Sign in to add a note.")
            return
        }
        val note = text.trim().take(MAX_STORY_NOTE_LENGTH)
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val expiresAt = if (note.isBlank()) 0L else {
                    now + STORY_NOTE_DURATION_MS
                }
                val updateMap = mutableMapOf<String, Any>(
                    "storyNote" to note,
                    "storyNoteExpiresAt" to expiresAt,
                    "storyNoteCreatedAt" to if (note.isBlank()) 0L else now,
                    "storyNoteViewedBy" to emptyList<String>(),
                    "storyNoteLovedBy" to emptyList<String>()
                )
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId)
                    .update(updateMap)
                    .await()
                _uiState.update { state ->
                    val user = state.currentUser
                    val updatedUser = user?.copy(
                        storyNote = note,
                        storyNoteExpiresAt = expiresAt,
                        storyNoteCreatedAt = if (note.isBlank()) 0L else now,
                        storyNoteViewedBy = emptyList(),
                        storyNoteLovedBy = emptyList()
                    )
                    state.copy(
                        currentUser = updatedUser,
                        usersMap = if (updatedUser != null) state.usersMap + (currentUserId to updatedUser) else state.usersMap
                    )
                }
            } catch (error: Exception) {
                Log.e("ChatListViewModel", "Failed to update story note", error)
                onError(error.localizedMessage ?: "Couldn't update your note.")
            }
        }
    }

    fun recordStoryNoteView(targetUserId: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank() || targetUserId == currentUserId) return
        val targetUser = _uiState.value.usersMap[targetUserId] ?: return
        if (currentUserId in targetUser.storyNoteViewedBy) return

        val newViewedBy = (targetUser.storyNoteViewedBy + currentUserId).distinct()
        _uiState.update { state ->
            val updatedUser = targetUser.copy(storyNoteViewedBy = newViewedBy)
            state.copy(usersMap = state.usersMap + (targetUserId to updatedUser))
        }
        viewModelScope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(targetUserId)
                    .update("storyNoteViewedBy", FieldValue.arrayUnion(currentUserId))
                    .await()
            } catch (e: Exception) {
                Log.w("ChatListViewModel", "Failed to update storyNoteViewedBy in Firestore", e)
            }
        }
    }

    fun toggleStoryNoteLove(targetUserId: String, noteText: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return
        val targetUser = _uiState.value.usersMap[targetUserId] ?: return
        val isLoved = currentUserId in targetUser.storyNoteLovedBy
        val newLovedBy = if (isLoved) {
            targetUser.storyNoteLovedBy - currentUserId
        } else {
            (targetUser.storyNoteLovedBy + currentUserId).distinct()
        }
        _uiState.update { state ->
            val updatedUser = targetUser.copy(storyNoteLovedBy = newLovedBy)
            state.copy(usersMap = state.usersMap + (targetUserId to updatedUser))
        }
        viewModelScope.launch {
            try {
                val operation = if (isLoved) {
                    FieldValue.arrayRemove(currentUserId)
                } else {
                    FieldValue.arrayUnion(currentUserId)
                }
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(targetUserId)
                    .update("storyNoteLovedBy", operation)
                    .await()

                if (!isLoved && targetUserId != currentUserId) {
                    createNotification(
                        recipientId = targetUserId,
                        type = "message",
                        message = "reacted ❤️ to your note: \"\""
                    )
                }
            } catch (e: Exception) {
                Log.w("ChatListViewModel", "Failed to update storyNoteLovedBy in Firestore", e)
            }
        }
    }

    fun replyToStoryNote(
        recipientUser: User,
        noteText: String,
        replyMessage: String,
        onError: (String) -> Unit
    ) {
        val trimmedText = replyMessage.trim()
        if (currentUserId.isBlank() || trimmedText.isBlank()) return
        viewModelScope.launch {
            val name = recipientUser.displayName.ifBlank { recipientUser.username }.ifBlank { "User" }
            val conversationResult = chatRepository.getOrCreateConversation(
                currentUserId = currentUserId,
                recipientId = recipientUser.uid,
                recipientName = name,
                recipientPhoto = recipientUser.photoUrl
            )
            val conversationId = conversationResult.getOrElse {
                onError(it.localizedMessage ?: "Couldn't open the conversation.")
                return@launch
            }
            val messageResult = chatRepository.sendMessage(
                conversationId = conversationId,
                senderId = currentUserId,
                text = trimmedText,
                replyToText = noteText,
                replyToSenderName = name
            )
            messageResult.fold(
                onSuccess = {
                    createNotification(
                        recipientId = recipientUser.uid,
                        type = "message",
                        message = trimmedText,
                        conversationId = conversationId
                    )
                },
                onFailure = { error ->
                    onError(error.localizedMessage ?: "Couldn't send your message.")
                }
            )
        }
    }

    fun openConversationWithUser(
        recipientUser: User,
        onConversationReady: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (currentUserId.isBlank() || recipientUser.uid.isBlank()) {
            onError("Couldn't open this conversation.")
            return
        }
        viewModelScope.launch {
            val name = recipientUser.displayName.ifBlank { recipientUser.username }.ifBlank { "User" }
            val result = chatRepository.getOrCreateConversation(
                currentUserId = currentUserId,
                recipientId = recipientUser.uid,
                recipientName = name,
                recipientPhoto = recipientUser.photoUrl
            )
            result.fold(
                onSuccess = onConversationReady,
                onFailure = { error ->
                    onError(error.localizedMessage ?: "Couldn't open the conversation.")
                }
            )
        }
    }

    fun markStoryViewed(storyId: String) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            try {
                FirebaseFirestore.getInstance().collection("stories").document(storyId)
                    .update("viewedBy", FieldValue.arrayUnion(currentUserId))
                    .await()
            } catch (error: Exception) {
                Log.e("ChatListViewModel", "Failed to mark story as viewed", error)
            }
        }
    }

    fun deleteStory(
        story: Story,
        keepInHighlights: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (currentUserId.isBlank() || story.userId != currentUserId) {
            onError("You can only delete your own stories.")
            return
        }
        viewModelScope.launch {
            try {
                val storyRef = FirebaseFirestore.getInstance()
                    .collection("stories")
                    .document(story.storyId)
                if (keepInHighlights) {
                    storyRef.update("expiresAt", System.currentTimeMillis()).await()
                } else {
                    val comments = storyRef.collection("comments").get().await()
                    comments.documents.chunked(450).forEach { documents ->
                        val batch = FirebaseFirestore.getInstance().batch()
                        documents.forEach { document -> batch.delete(document.reference) }
                        batch.commit().await()
                    }
                    storyRef.delete().await()
                }
                onSuccess()
            } catch (error: Exception) {
                Log.e("ChatListViewModel", "Failed to delete story", error)
                onError(error.localizedMessage ?: "Couldn't delete the story.")
            }
        }
    }

    fun toggleStoryLove(story: Story, onError: (String) -> Unit) {
        if (currentUserId.isBlank() || story.userId == currentUserId) return
        viewModelScope.launch {
            try {
                val storyRef = FirebaseFirestore.getInstance()
                    .collection("stories")
                    .document(story.storyId)
                var addedLove = false
                FirebaseFirestore.getInstance().runTransaction { transaction ->
                    val snapshot = transaction.get(storyRef)
                    val lovedBy = snapshot.get("lovedBy") as? List<*> ?: emptyList<Any>()
                    addedLove = currentUserId !in lovedBy
                    transaction.update(
                        storyRef,
                        "lovedBy",
                        if (addedLove) FieldValue.arrayUnion(currentUserId)
                        else FieldValue.arrayRemove(currentUserId)
                    )
                }.await()

                if (addedLove) {
                    sendStoryMessage(story, "❤️ Reacted to your story", {}, onError)
                }
            } catch (error: Exception) {
                Log.e("ChatListViewModel", "Failed to react to story", error)
                onError(error.localizedMessage ?: "Couldn't react to this story.")
            }
        }
    }

    fun addStoryComment(
        story: Story,
        text: String,
        parentCommentId: String,
        replyToCommentId: String,
        mentionIds: List<String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmedText = text.trim()
        if (currentUserId.isBlank() || trimmedText.isBlank()) return
        viewModelScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance()
                val storyRef = firestore.collection("stories").document(story.storyId)
                val existingComments = storyRef.collection("comments").get().await()
                val commentIdsBySender = existingComments.documents.mapNotNull { document ->
                    document.toObject(StoryComment::class.java)?.senderId
                        ?.takeIf(String::isNotBlank)
                        ?.let { it to document.id }
                }.groupBy({ it.first }, { it.second })

                val currentUser = _uiState.value.currentUser
                val commentsRef = storyRef.collection("comments")
                val commentRef = commentsRef.document()
                val comment = StoryComment(
                    commentId = commentRef.id,
                    senderId = currentUserId,
                    senderName = currentUser?.displayName?.ifBlank { currentUser.username } ?: "User",
                    senderPhoto = currentUser?.photoUrl ?: "",
                    text = trimmedText,
                    createdAt = System.currentTimeMillis(),
                    parentCommentId = parentCommentId,
                    replyToCommentId = replyToCommentId,
                    mentionIds = mentionIds.distinct().filter { it != currentUserId }
                )
                firestore.batch().set(commentRef, comment).commit().await()

                val replyTarget = existingComments.documents
                    .firstOrNull { it.id == replyToCommentId }
                    ?.toObject(StoryComment::class.java)
                val recipients = buildSet {
                    add(story.userId)
                    addAll(commentIdsBySender.keys)
                    add(replyTarget?.senderId.orEmpty())
                    addAll(comment.mentionIds)
                }.filter { it.isNotBlank() && it != currentUserId }

                recipients.forEach { recipientId ->
                    val isMentioned = recipientId in comment.mentionIds
                    val isReplyTarget = recipientId == replyTarget?.senderId
                    val type = when {
                        isMentioned -> "story_mention"
                        isReplyTarget -> "story_comment_reply"
                        else -> "story_comment"
                    }
                    val message = when {
                        isMentioned -> "mentioned you in a story comment: $trimmedText"
                        isReplyTarget -> "replied to your comment: $trimmedText"
                        else -> "commented on a story discussion: $trimmedText"
                    }
                    try {
                        val recipientCommentId = commentIdsBySender[recipientId]?.firstOrNull()
                            ?: if (recipientId == replyTarget?.senderId) replyToCommentId else ""
                        createNotification(
                            recipientId = recipientId,
                            type = type,
                            message = message,
                            storyId = story.storyId,
                            commentId = commentRef.id,
                            recipientCommentId = recipientCommentId
                        )
                    } catch (error: Exception) {
                        Log.e("ChatListViewModel", "Failed to notify story discussion participant", error)
                    }
                }
                onSuccess()
            } catch (error: Exception) {
                Log.e("ChatListViewModel", "Failed to comment on story", error)
                onError(error.localizedMessage ?: "Couldn't send your comment.")
            }
        }
    }

    fun toggleCommentLove(
        storyId: String,
        comment: com.example.chat.data.model.StoryComment,
        onError: (String) -> Unit
    ) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            try {
                val commentRef = FirebaseFirestore.getInstance()
                    .collection("stories")
                    .document(storyId)
                    .collection("comments")
                    .document(comment.commentId)
                val addedLove = FirebaseFirestore.getInstance().runTransaction { transaction ->
                    val snapshot = transaction.get(commentRef)
                    val lovedBy = snapshot.get("lovedBy") as? List<*> ?: emptyList<Any>()
                    val addLove = currentUserId !in lovedBy
                    transaction.update(
                        commentRef,
                        "lovedBy",
                        if (addLove) FieldValue.arrayUnion(currentUserId)
                        else FieldValue.arrayRemove(currentUserId)
                    )
                    addLove
                }.await()

                if (addedLove && comment.senderId != currentUserId) {
                    try {
                        createNotification(
                            recipientId = comment.senderId,
                            type = "comment_love",
                            message = "loved your comment: ${comment.text}",
                            storyId = storyId,
                            commentId = comment.commentId
                        )
                    } catch (error: Exception) {
                        Log.e("ChatListViewModel", "Failed to notify comment author about reaction", error)
                    }
                }
            } catch (error: Exception) {
                Log.e("ChatListViewModel", "Failed to react to story comment", error)
                onError(error.localizedMessage ?: "Couldn't react to this comment.")
            }
        }
    }

    fun sendStoryMessage(
        story: Story,
        text: String,
        onConversationReady: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmedText = text.trim()
        if (currentUserId.isBlank() || trimmedText.isBlank()) return
        viewModelScope.launch {
            val conversationResult = chatRepository.getOrCreateConversation(
                currentUserId = currentUserId,
                recipientId = story.userId,
                recipientName = story.userName,
                recipientPhoto = story.userPhoto
            )
            val conversationId = conversationResult.getOrElse {
                onError(it.localizedMessage ?: "Couldn't open the conversation.")
                return@launch
            }
            val messageResult = chatRepository.sendMessage(
                conversationId = conversationId,
                senderId = currentUserId,
                text = "Reply to story: $trimmedText",
                replyToText = story.caption.ifBlank { story.mediaUrl },
                replyToSenderName = story.userName
            )
            messageResult.fold(
                onSuccess = { onConversationReady(conversationId) },
                onFailure = { error ->
                    onError(error.localizedMessage ?: "Couldn't send your message.")
                }
            )
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

    companion object {
        const val MAX_STORY_NOTE_LENGTH = 60
        const val STORY_NOTE_DURATION_MS = 24 * 60 * 60 * 1000L
    }

    fun setFilter(filter: String) {
        _uiState.update { state ->
            val filtered = sortConversations(
                applyFilter(state.conversations, filter, state.showArchived, state.searchQuery),
                state.usersMap,
                state.activeStories
            )
            state.copy(selectedFilter = filter, filteredConversations = filtered)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = sortConversations(
                applyFilter(state.conversations, state.selectedFilter, state.showArchived, query),
                state.usersMap,
                state.activeStories
            )
            state.copy(searchQuery = query, filteredConversations = filtered)
        }
    }


    fun toggleShowArchived() {
        _uiState.update { state ->
            val newArchivedState = !state.showArchived
            val filtered = sortConversations(
                applyFilter(state.conversations, state.selectedFilter, newArchivedState, state.searchQuery),
                state.usersMap,
                state.activeStories
            )
            state.copy(showArchived = newArchivedState, filteredConversations = filtered)
        }
    }

    private fun applyFilter(conversations: List<Conversation>, filter: String, showArchived: Boolean, query: String): List<Conversation> {
        return filterChatListConversations(conversations, filter, showArchived, query, currentUserId)
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
