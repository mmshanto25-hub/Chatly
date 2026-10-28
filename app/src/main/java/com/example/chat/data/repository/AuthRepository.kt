package com.example.chat.data.repository

import com.example.chat.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    }

    val currentUserId: String?
        get() = try { auth?.currentUser?.uid } catch (_: Exception) { null }

    val currentUserEmail: String?
        get() = try { auth?.currentUser?.email } catch (_: Exception) { null }

    fun isUserLoggedIn(): Boolean = try { auth?.currentUser != null } catch (_: Exception) { false }

    suspend fun login(email: String, password: String): Result<Unit> {
        val a = auth ?: return Result.failure(Exception("Firebase Auth is not initialized. Please add your google-services.json file."))
        return try {
            a.signInWithEmailAndPassword(email, password).await()
            updateUserPresence("online")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(displayName: String, email: String, password: String): Result<Unit> {
        val a = auth ?: return Result.failure(Exception("Firebase Auth is not initialized. Please add your google-services.json file."))
        val db = firestore ?: return Result.failure(Exception("Firestore is not initialized. Please add your google-services.json file."))
        return try {
            val authResult = a.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("User ID is null")
            val user = User(
                uid = uid,
                displayName = displayName,
                email = email,
                createdAt = System.currentTimeMillis(),
                lastSeenAt = System.currentTimeMillis(),
                presence = "online"
            )
            db.collection("users").document(uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try {
            val uid = currentUserId
            if (uid != null && firestore != null) {
                firestore.collection("users").document(uid).update(
                    mapOf(
                        "presence" to "offline",
                        "lastSeenAt" to System.currentTimeMillis()
                    )
                ).await()
            }
        } catch (_: Exception) {}
        try {
            auth?.signOut()
        } catch (_: Exception) {}
    }

    suspend fun getCurrentUserData(): User? {
        val uid = currentUserId ?: return null
        val db = firestore ?: return null
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            snapshot.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updateUserPresence(status: String) {
        val uid = currentUserId ?: return
        val db = firestore ?: return
        try {
            db.collection("users").document(uid).update(
                mapOf(
                    "presence" to status,
                    "lastSeenAt" to System.currentTimeMillis()
                )
            ).await()
        } catch (_: Exception) {}
    }
}
