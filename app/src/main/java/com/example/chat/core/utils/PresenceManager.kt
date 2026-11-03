package com.example.chat.core.utils

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PresenceManager : DefaultLifecycleObserver {

    companion object {
        private var instance: PresenceManager? = null

        fun init() {
            if (instance == null) {
                val pm = PresenceManager()
                instance = pm
                ProcessLifecycleOwner.get().lifecycle.addObserver(pm)

                FirebaseAuth.getInstance().addAuthStateListener { auth ->
                    if (auth.currentUser != null) {
                        pm.setOnline()
                    }
                }
            }
        }

        fun setUserOnline() {
            instance?.setOnline()
        }

        fun setUserOffline() {
            instance?.setOffline()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        setOnline()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        setOffline()
    }

    fun setOnline() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "isOnline" to true,
            "presence" to "online",
            "lastSeen" to now,
            "lastSeenAt" to now
        )
        db.collection("users").document(uid).update(updates)
            .addOnFailureListener { e ->
                Log.e("PresenceManager", "Failed to update online state: ${e.message}")
            }
    }

    fun setOffline() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "isOnline" to false,
            "presence" to "offline",
            "lastSeen" to now,
            "lastSeenAt" to now
        )
        db.collection("users").document(uid).update(updates)
            .addOnFailureListener { e ->
                Log.e("PresenceManager", "Failed to update offline state: ${e.message}")
            }
    }
}
