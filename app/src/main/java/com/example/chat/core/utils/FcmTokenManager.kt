package com.example.chat.core.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

object FcmTokenManager {
    private const val TAG = "FcmTokenManager"

    fun syncCurrentUserToken() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> saveToken(userId, token) }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to get the FCM token", error)
            }
    }

    fun saveToken(userId: String, token: String) {
        if (userId.isBlank() || token.isBlank()) return
        val tokenId = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("fcmTokens")
            .document(tokenId)
            .set(
                mapOf(
                    "token" to token,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to save the FCM token", error)
            }
    }

    suspend fun removeCurrentUserToken(userId: String) {
        if (userId.isBlank()) return
        val token = FirebaseMessaging.getInstance().token.await()
        val tokenId = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("fcmTokens")
            .document(tokenId)
            .delete()
            .await()
    }
}
