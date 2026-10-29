package com.example.chat.data.repository

import com.example.chat.data.model.Conversation
import com.example.chat.data.model.Message
import com.example.chat.data.model.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale

class ChatRepository {
    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    }

    suspend fun searchUsers(query: String, currentUserId: String): List<User> {
        val db = firestore ?: return emptyList()
        return try {
            val snapshot = db.collection("users").get().await()
            val trimmedQuery = query.trim().lowercase(Locale.getDefault())
            snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                .filter { user ->
                    user.uid != currentUserId && (
                        user.displayName.lowercase(Locale.getDefault()).contains(trimmedQuery) ||
                        user.email.lowercase(Locale.getDefault()).contains(trimmedQuery)
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAllUsers(currentUserId: String): List<User> {
        val db = firestore ?: return emptyList()
        return try {
            val snapshot = db.collection("users").get().await()
            snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                .filter { it.uid != currentUserId }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUser(uid: String): User? {
        val db = firestore ?: return null
        return try {
            db.collection("users").document(uid).get().await().toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getOrCreateConversation(currentUserId: String, recipientId: String, recipientName: String, recipientPhoto: String): Result<String> {
        val db = firestore ?: return Result.failure(Exception("Firestore not initialized"))
        return try {
            val querySnapshot = db.collection("conversations")
                .whereArrayContains("participantIds", currentUserId)
                .get()
                .await()

            var existingConvId: String? = null
            for (doc in querySnapshot.documents) {
                val participants = doc.get("participantIds") as? List<*>
                val isDirect = doc.getString("type") ?: "direct"
                if (isDirect == "direct" && participants != null && participants.contains(recipientId) && participants.size == 2) {
                    existingConvId = doc.getString("conversationId") ?: doc.id
                    break
                }
            }

            if (existingConvId != null) {
                return Result.success(existingConvId)
            }

            val convRef = db.collection("conversations").document()
            val conversationId = convRef.id

            val currentUserDoc = db.collection("users").document(currentUserId).get().await()
            val currentUserName = currentUserDoc.getString("displayName") ?: "User"
            val currentUserPhoto = currentUserDoc.getString("photoUrl") ?: ""

            val conversation = Conversation(
                conversationId = conversationId,
                type = "direct",
                participantIds = listOf(currentUserId, recipientId),
                participantNames = mapOf(
                    currentUserId to currentUserName,
                    recipientId to recipientName
                ),
                participantPhotos = mapOf(
                    currentUserId to currentUserPhoto,
                    recipientId to recipientPhoto
                ),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastMessage = "Started a conversation",
                lastMessageAt = System.currentTimeMillis(),
                lastMessageSenderId = currentUserId,
                isPinned = false,
                isArchived = false,
                isMuted = false
            )

            convRef.set(conversation).await()
            Result.success(conversationId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGroupConversation(currentUserId: String, groupName: String, participantUids: List<String>): Result<String> {
        val db = firestore ?: return Result.failure(Exception("Firestore not initialized"))
        return try {
            val convRef = db.collection("conversations").document()
            val conversationId = convRef.id

            val allUids = (participantUids + currentUserId).distinct()

            val namesMap = mutableMapOf<String, String>()
            val photosMap = mutableMapOf<String, String>()

            for (uid in allUids) {
                val userDoc = db.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    val name = userDoc.getString("displayName") ?: "User"
                    val photo = userDoc.getString("photoUrl") ?: ""
                    namesMap[uid] = name
                    photosMap[uid] = photo
                }
            }

            val finalGroupName = groupName.trim().ifBlank { "Group Chat" }
            namesMap["groupName"] = finalGroupName

            val conversation = Conversation(
                conversationId = conversationId,
                type = "group",
                groupName = finalGroupName,
                participantIds = allUids,
                participantNames = namesMap,
                participantPhotos = photosMap,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastMessage = "Group created",
                lastMessageAt = System.currentTimeMillis(),
                lastMessageSenderId = currentUserId,
                isPinned = false,
                isArchived = false,
                isMuted = false
            )

            convRef.set(conversation).await()
            Result.success(conversationId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getConversationsFlow(userId: String): Flow<List<Conversation>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val conversations = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Conversation::class.java)?.copy(conversationId = doc.id)
                }?.sortedWith(
                    compareByDescending<Conversation> { it.isPinnedFor(userId) }
                        .thenByDescending { it.lastMessageAt }
                ) ?: emptyList()
                trySend(conversations)
            }
        awaitClose { listener.remove() }
    }

    fun getMessagesFlow(conversationId: String): Flow<List<Message>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(messageId = doc.id)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(conversationId: String, senderId: String, text: String, messageType: String = "text", attachmentUrl: String = ""): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firestore not initialized"))
        return try {
            val msgRef = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document()

            val messageId = msgRef.id
            val message = Message(
                messageId = messageId,
                conversationId = conversationId,
                senderId = senderId,
                text = text,
                messageType = messageType,
                attachmentUrl = attachmentUrl,
                createdAt = System.currentTimeMillis()
            )

            msgRef.set(message).await()

            val summary = if (messageType == "image") "📷 Photo" else if (messageType == "video") "🎥 Video" else if (messageType == "audio") "🎤 Voice message" else text

            db.collection("conversations")
                .document(conversationId)
                .update(
                    mapOf(
                        "lastMessage" to summary,
                        "lastMessageAt" to System.currentTimeMillis(),
                        "lastMessageSenderId" to senderId,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun togglePin(conversationId: String, userId: String, currentPinState: Boolean) {
        val db = firestore ?: return
        try {
            val newPinState = !currentPinState
            if (newPinState) {
                db.collection("conversations").document(conversationId).update(
                    mapOf(
                        "pinnedBy" to FieldValue.arrayUnion(userId),
                        "isPinned" to true
                    )
                ).await()
            } else {
                db.collection("conversations").document(conversationId).update(
                    mapOf(
                        "pinnedBy" to FieldValue.arrayRemove(userId),
                        "isPinned" to false
                    )
                ).await()
            }
        } catch (_: Exception) {}
    }

    suspend fun toggleArchive(conversationId: String, userId: String, currentArchiveState: Boolean) {
        val db = firestore ?: return
        try {
            val newArchiveState = !currentArchiveState
            if (newArchiveState) {
                db.collection("conversations").document(conversationId).update(
                    mapOf(
                        "archivedBy" to FieldValue.arrayUnion(userId),
                        "isArchived" to true
                    )
                ).await()
            } else {
                db.collection("conversations").document(conversationId).update(
                    mapOf(
                        "archivedBy" to FieldValue.arrayRemove(userId),
                        "isArchived" to false
                    )
                ).await()
            }
        } catch (_: Exception) {}
    }

    suspend fun toggleMute(conversationId: String, userId: String, currentMuteState: Boolean) {
        val db = firestore ?: return
        try {
            val newMuteState = !currentMuteState
            if (newMuteState) {
                db.collection("conversations").document(conversationId).update(
                    mapOf(
                        "mutedBy" to FieldValue.arrayUnion(userId),
                        "isMuted" to true
                    )
                ).await()
            } else {
                db.collection("conversations").document(conversationId).update(
                    mapOf(
                        "mutedBy" to FieldValue.arrayRemove(userId),
                        "isMuted" to false
                    )
                ).await()
            }
        } catch (_: Exception) {}
    }

    suspend fun togglePinMessage(conversationId: String, messageId: String, isCurrentlyPinned: Boolean, messageText: String) {
        val db = firestore ?: return
        try {
            val newPinnedState = !isCurrentlyPinned
            db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .update("isPinned", newPinnedState)
                .await()

            if (newPinnedState) {
                db.collection("conversations")
                    .document(conversationId)
                    .update(
                        mapOf(
                            "pinnedMessageId" to messageId,
                            "pinnedMessageText" to messageText
                        )
                    ).await()
            } else {
                val convDoc = db.collection("conversations").document(conversationId).get().await()
                if (convDoc.getString("pinnedMessageId") == messageId) {
                    db.collection("conversations")
                        .document(conversationId)
                        .update(
                            mapOf(
                                "pinnedMessageId" to "",
                                "pinnedMessageText" to ""
                            )
                        ).await()
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun deleteMessage(conversationId: String, messageId: String) {
        val db = firestore ?: return
        try {
            db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
        } catch (_: Exception) {}
    }
}
