package com.example.chat.core.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class PresenceManager : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            setOnline()
            handler.postDelayed(this, 30_000L) // Heartbeat every 30 seconds
        }
    }

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
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        setOnline()
        handler.postDelayed(heartbeatRunnable, 30_000L)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        handler.removeCallbacks(heartbeatRunnable)
        setOffline()
    }

    fun setOnline() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val updates = mapOf(
            "isOnline" to true,
            "lastSeenAt" to Timestamp.now()
        )
        db.collection("users").document(uid).set(updates, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e("PresenceManager", "Failed to update online state: ${e.message}")
            }
    }

    fun setOffline() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val updates = mapOf(
            "isOnline" to false,
            "lastSeenAt" to Timestamp.now()
        )
        db.collection("users").document(uid).set(updates, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e("PresenceManager", "Failed to update offline state: ${e.message}")
            }
    }
}
