package com.example.chat.ui.navigation

import android.media.Ringtone
import android.media.RingtoneManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.chat.data.model.Message
import com.example.chat.ui.auth.AuthViewModel
import com.example.chat.ui.auth.LoginScreen
import com.example.chat.ui.auth.RegisterScreen
import com.example.chat.ui.chats.ChatListScreen
import com.example.chat.ui.chats.ChatListViewModel
import com.example.chat.ui.contacts.UserSearchScreen
import com.example.chat.ui.contacts.UserSearchViewModel
import com.example.chat.ui.conversation.ConversationScreen
import com.example.chat.ui.conversation.ConversationViewModel
import com.example.chat.ui.profile.ProfileScreen
import com.example.chat.ui.settings.SettingsScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

object RingtoneHelper {
    private var ringtone: Ringtone? = null

    fun startRinging(context: android.content.Context) {
        try {
            if (ringtone?.isPlaying == true) return
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(context, notificationUri)?.apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRinging() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun ChatNavGraph() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()

    val startDestination = if (authViewModel.isUserLoggedIn()) "chats" else "login"

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable("login") {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavMain = {
                        navController.navigate("chats") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onNavRegister = { navController.navigate("register") },
                )
            }
            composable("register") {
                RegisterScreen(
                    viewModel = authViewModel,
                    onNavMain = {
                        navController.navigate("chats") {
                            popUpTo("register") { inclusive = true }
                        }
                    },
                    onNavLogin = { navController.popBackStack() }
                )
            }
            composable("chats") {
                val chatListViewModel: ChatListViewModel = viewModel()
                ChatListScreen(
                    viewModel = chatListViewModel,
                    onNavToChat = { convId, recipientId, recipientName ->
                        navController.navigate("conversation/$convId/$recipientId/$recipientName")
                    },
                    onNavToSearch = { navController.navigate("contacts") },
                    onNavToProfile = { navController.navigate("profile") },
                    onNavToSettings = { navController.navigate("settings") },
                    onNavToLogin = {
                        navController.navigate("login") {
                            popUpTo("chats") { inclusive = true }
                        }
                    },
                    onSwitchAccount = {
                        navController.navigate("chats") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable("contacts") {
                val searchViewModel: UserSearchViewModel = viewModel()
                UserSearchScreen(
                    viewModel = searchViewModel,
                    onNavBack = { navController.popBackStack() },
                    onNavToChat = { convId, recipientId, recipientName ->
                        navController.navigate("conversation/$convId/$recipientId/$recipientName")
                    },
                    onNavToCall = { channelName, callType ->
                        navController.navigate("call/$channelName?callType=$callType")
                    }
                )
            }
            composable(
                route = "conversation/{conversationId}/{recipientId}/{recipientName}",
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.StringType },
                    navArgument("recipientId") { type = NavType.StringType },
                    navArgument("recipientName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                val recipientId = backStackEntry.arguments?.getString("recipientId") ?: ""
                val recipientName = backStackEntry.arguments?.getString("recipientName") ?: "Chat"
                val conversationViewModel = rememberInstanceViewModel(conversationId)
                val chatsBackStackEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("chats")
                }
                val chatListViewModel: ChatListViewModel = viewModel(chatsBackStackEntry)

                ConversationScreen(
                    viewModel = conversationViewModel,
                    chatListViewModel = chatListViewModel,
                    recipientName = recipientName,
                    onNavBack = { navController.popBackStack() },
                    onNavToChat = { convId, recId, name ->
                        navController.navigate("conversation/$convId/$recId/$name")
                    },
                    onNavToCall = { callType ->
                        val myId = conversationViewModel.currentUserId
                        val firestore = FirebaseFirestore.getInstance()

                        CoroutineScope(Dispatchers.Main).launch {
                            var channelName = ""
                            try {
                                kotlinx.coroutines.withContext(Dispatchers.IO) {
                                    val callRef = firestore.collection("calls").document()
                                    val callId = callRef.id
                                    channelName = "test_${myId}_${recipientId}_${callId}"

                                    val recipientDoc = try {
                                        firestore.collection("users").document(recipientId).get().await()
                                    } catch (_: Exception) {
                                        null
                                    }
                                    val recName = recipientDoc?.getString("displayName") ?: recipientName
                                    val recPhoto = recipientDoc?.getString("photoUrl") ?: ""

                                    val currentUserDoc = try {
                                        firestore.collection("users").document(myId).get().await()
                                    } catch (_: Exception) {
                                        null
                                    }
                                    val myName = currentUserDoc?.getString("displayName") ?: "User"
                                    val myPhoto = currentUserDoc?.getString("photoUrl") ?: ""

                                    val callData = mapOf(
                                        "callId" to callId,
                                        "channelName" to channelName,
                                        "callerId" to myId,
                                        "receiverId" to recipientId,
                                        "receiverName" to recName,
                                        "receiverPhoto" to recPhoto,
                                        "callerName" to myName,
                                        "callerPhoto" to myPhoto,
                                        "callType" to callType,
                                        "status" to "calling",
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                    callRef.set(callData).await()

                                    val msgRef = firestore.collection("conversations")
                                        .document(conversationId)
                                        .collection("messages")
                                        .document()

                                    val callMsgText = if (callType == "video") "Video call" else "Audio call"
                                    val msg = Message(
                                        messageId = msgRef.id,
                                        conversationId = conversationId,
                                        senderId = myId,
                                        text = callMsgText,
                                        messageType = if (callType == "video") "call_video" else "call_audio",
                                        attachmentUrl = callType,
                                        createdAt = System.currentTimeMillis()
                                    )
                                    msgRef.set(msg).await()

                                    firestore.collection("conversations")
                                        .document(conversationId)
                                        .update(
                                            mapOf(
                                                "lastMessage" to callMsgText,
                                                "lastMessageAt" to System.currentTimeMillis(),
                                                "lastMessageSenderId" to myId,
                                                "updatedAt" to System.currentTimeMillis()
                                            )
                                        ).await()
                                }
                            } catch (_: Exception) {}

                            if (channelName.isNotBlank()) {
                                navController.navigate("call/$channelName?callType=$callType")
                            }
                        }
                    }
                )
            }
            composable(
                route = "call/{channelName}?callType={callType}",
                arguments = listOf(
                    navArgument("channelName") { type = NavType.StringType },
                    navArgument("callType") { type = NavType.StringType; defaultValue = "video" }
                )
            ) { backStackEntry ->
                val channelName = backStackEntry.arguments?.getString("channelName") ?: "test"
                val callType = backStackEntry.arguments?.getString("callType") ?: "video"
                com.example.chat.ui.call.TestCallScreen(
                    channelName = channelName,
                    callType = callType,
                    onNavBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate("chats")
                        }
                    }
                )
            }
            composable("profile") { backStackEntry ->
                val chatsBackStackEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("chats")
                }
                val chatListViewModel: ChatListViewModel = viewModel(chatsBackStackEntry)
                ProfileScreen(
                    viewModel = chatListViewModel,
                    onNavBack = { navController.popBackStack() },
                    onNavToChat = { conversationId, recipientId, recipientName ->
                        navController.navigate("conversation/$conversationId/$recipientId/$recipientName")
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onNavBack = { navController.popBackStack() },
                    onNavToLogin = {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        GlobalIncomingCallListener(
            onAcceptCall = { channelName, callType ->
                navController.navigate("call/$channelName?callType=$callType")
            }
        )
    }
}

@Composable
fun GlobalIncomingCallListener(
    onAcceptCall: (channelName: String, callType: String) -> Unit
) {
    var currentUserId by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid ?: "") }

    DisposableEffect(Unit) {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            currentUserId = auth.currentUser?.uid ?: ""
        }
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        onDispose {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        }
    }

    if (currentUserId.isBlank()) return

    val db = remember { FirebaseFirestore.getInstance() }
    var incomingCall by remember { mutableStateOf<Map<String, Any>?>(null) }
    var activeDocId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(currentUserId) {
        val listener = db.collection("calls")
            .whereEqualTo("receiverId", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    incomingCall = null
                    activeDocId = null
                    return@addSnapshotListener
                }

                // Find any call document where status == "calling"
                val callingDoc = snapshot.documents.firstOrNull { doc ->
                    doc.getString("status") == "calling"
                }

                if (callingDoc != null) {
                    activeDocId = callingDoc.id
                    incomingCall = callingDoc.data
                } else {
                    // If no active calling document exists (caller cancelled/cut the call),
                    // automatically dismiss ringing screen and stop ringtone!
                    RingtoneHelper.stopRinging()
                    incomingCall = null
                    activeDocId = null
                }
            }

        onDispose {
            RingtoneHelper.stopRinging()
            listener.remove()
        }
    }

    val call = incomingCall
    val docId = activeDocId
    if (call != null && docId != null) {
        val callerName = call["callerName"] as? String ?: "Someone"
        val callerPhoto = call["callerPhoto"] as? String ?: ""
        val callType = call["callType"] as? String ?: "audio"
        val channelName = call["channelName"] as? String ?: "test"

        FullScreenIncomingCall(
            callerName = callerName,
            callerPhoto = callerPhoto,
            callType = callType,
            onAccept = {
                RingtoneHelper.stopRinging()
                db.collection("calls").document(docId).update("status", "accepted")
                incomingCall = null
                activeDocId = null
                onAcceptCall(channelName, callType)
            },
            onDecline = {
                RingtoneHelper.stopRinging()
                db.collection("calls").document(docId).update("status", "rejected")
                incomingCall = null
                activeDocId = null
            }
        )
    }
}

@Composable
fun FullScreenIncomingCall(
    callerName: String,
    callerPhoto: String,
    callType: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = callType.equals("video", ignoreCase = true)

    DisposableEffect(Unit) {
        RingtoneHelper.startRinging(context)
        onDispose {
            RingtoneHelper.stopRinging()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 12.dp
                ) {
                    if (callerPhoto.isNotBlank()) {
                        AsyncImage(
                            model = callerPhoto,
                            contentDescription = "Caller Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = callerName.take(1).uppercase(Locale.getDefault()),
                                fontWeight = FontWeight.Bold,
                                fontSize = 56.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = callerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isVideo) "Incoming Video Call..." else "Incoming Audio Call...",
                    fontSize = 16.sp,
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline (Red)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Decline", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                // Accept (Green)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Accept",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Accept", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun rememberInstanceViewModel(conversationId: String): ConversationViewModel {
    return viewModel(key = conversationId) {
        ConversationViewModel(conversationId)
    }
}
