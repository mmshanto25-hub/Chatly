package com.example.chat.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.data.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavBack: () -> Unit,
    onNavToLogin: () -> Unit
) {
    val authRepository = remember { AuthRepository() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    val currentUserId = authRepository.currentUserId ?: ""
    var userEmail by remember { mutableStateOf(authRepository.currentUserEmail ?: "") }
    var displayName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val user = authRepository.getCurrentUserData()
        if (user != null) {
            displayName = user.displayName.ifBlank { "Chatly User" }
            userEmail = user.email
        } else {
            displayName = "Chatly User"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (displayName.isNotBlank()) displayName.take(1).uppercase() else "U",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; saveMessage = null },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = userEmail,
                onValueChange = {},
                label = { Text("Email Address") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (saveMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = saveMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val newName = displayName.trim()
                    if (currentUserId.isNotBlank() && newName.isNotBlank()) {
                        scope.launch {
                            isSaving = true
                            try {
                                // 1. Update or create user profile document in Firestore using merge
                                val userRef = firestore.collection("users").document(currentUserId)
                                val userData = mapOf(
                                    "uid" to currentUserId,
                                    "displayName" to newName,
                                    "email" to userEmail
                                )
                                userRef.set(userData, SetOptions.merge()).await()

                                // 2. Update participant name in all existing conversations
                                val convQuery = firestore.collection("conversations")
                                    .whereArrayContains("participantIds", currentUserId)
                                    .get()
                                    .await()

                                if (!convQuery.isEmpty) {
                                    val batch = firestore.batch()
                                    for (doc in convQuery.documents) {
                                        batch.update(doc.reference, "participantNames.$currentUserId", newName)
                                    }
                                    batch.commit().await()
                                }

                                saveMessage = "Profile updated successfully!"
                            } catch (e: Exception) {
                                saveMessage = "Failed to update: ${e.localizedMessage}"
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save Changes", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        authRepository.logout()
                        onNavToLogin()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Sign Out", fontSize = 16.sp, color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}
