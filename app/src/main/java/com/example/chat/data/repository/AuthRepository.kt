package com.example.chat.data.repository

import android.util.Log
import com.example.chat.core.utils.FcmTokenManager
import com.example.chat.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (_: Exception) {
        null
    }

    val currentUserId: String?
        get() = try { auth?.currentUser?.uid } catch (_: Exception) { null }

    val currentUserEmail: String?
        get() = try { auth?.currentUser?.email } catch (_: Exception) { null }

    fun isUserLoggedIn(): Boolean = try { auth?.currentUser != null } catch (_: Exception) { false }

    suspend fun login(emailOrUsername: String, password: String): Result<Unit> {
        val a = auth ?: return Result.failure(Exception("Firebase Auth is not initialized. Please add your google-services.json file."))
        val previousUserId = currentUserId
        return try {
            val email = if (!emailOrUsername.contains("@")) {
                "${emailOrUsername.trim()}@talky.com"
            } else {
                emailOrUsername.trim()
            }
            a.signInWithEmailAndPassword(email, password).await()
            ensureUserExistsInFirestore()
            val signedInUserId = currentUserId
            if (previousUserId != null && previousUserId != signedInUserId) {
                try {
                    FcmTokenManager.removeCurrentUserToken(previousUserId)
                } catch (error: Exception) {
                    Log.e("AuthRepository", "Failed to remove notification token from previous account", error)
                }
            }
            FcmTokenManager.syncCurrentUserToken()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(displayName: String, emailOrUsername: String, password: String): Result<Unit> {
        val a = auth ?: return Result.failure(Exception("Firebase Auth is not initialized. Please add your google-services.json file."))
        val db = firestore ?: return Result.failure(Exception("Firestore is not initialized. Please add your google-services.json file."))
        return try {
            val email = if (!emailOrUsername.contains("@")) {
                "${emailOrUsername.trim()}@talky.com"
            } else {
                emailOrUsername.trim()
            }
            val username = if (emailOrUsername.contains("@")) {
                emailOrUsername.substringBefore("@")
            } else {
                emailOrUsername.trim()
            }

            val authResult = a.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("User ID is null")
            val user = User(
                uid = uid,
                displayName = displayName,
                email = email,
                username = username,
                createdAt = System.currentTimeMillis(),
                isOnline = true,
                lastSeenAt = null
            )
            db.collection("users").document(uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        val uid = currentUserId
        if (uid != null) {
            try {
                FcmTokenManager.removeCurrentUserToken(uid)
            } catch (error: Exception) {
                Log.e("AuthRepository", "Failed to remove notification token during logout", error)
            }
        }
        try {
            if ((uid != null) && (firestore != null)) {
                firestore.collection("users").document(uid).update(
                    mapOf(
                        "isOnline" to false,
                        "lastSeenAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                ).await()
            }
        } catch (_: Exception) {}
        try {
            auth?.signOut()
        } catch (_: Exception) {}
    }

    suspend fun getCurrentUserData(): User? {
        return ensureUserExistsInFirestore()
    }

    suspend fun ensureUserExistsInFirestore(): User? {
        val uid = currentUserId ?: return null
        val db = firestore ?: return null
        val email = currentUserEmail ?: ""
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            if (snapshot.exists()) {
                snapshot.toObject(User::class.java)
            } else {
                val displayName = auth?.currentUser?.displayName.orEmpty().ifBlank { email.substringBefore("@") }
                val username = email.substringBefore("@")
                val newUser = User(
                    uid = uid,
                    displayName = displayName,
                    email = email,
                    username = username,
                    createdAt = System.currentTimeMillis(),
                    isOnline = true,
                    lastSeenAt = null
                )
                db.collection("users").document(uid).set(newUser).await()
                newUser
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        val a = auth ?: return Result.failure(Exception("Firebase Auth is not initialized."))
        val db = firestore ?: return Result.failure(Exception("Firestore is not initialized."))
        val uid = currentUserId ?: return Result.failure(Exception("No current user logged in."))
        return try {
            db.collection("users").document(uid).delete().await()

            val convQuery = db.collection("conversations")
                .whereArrayContains("participantIds", uid)
                .get()
                .await()

            val batch = db.batch()
            for (doc in convQuery.documents) {
                val type = doc.getString("type") ?: "direct"
                val participants = doc["participantIds"] as? List<*>
                if ((type == "direct") || (participants?.size == 2)) {
                    batch.delete(doc.reference)
                } else {
                    batch.update(doc.reference, "participantIds", com.google.firebase.firestore.FieldValue.arrayRemove(uid))
                }
            }
            batch.commit().await()

            a.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
