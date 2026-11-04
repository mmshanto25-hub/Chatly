package com.example.chat.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun SettingsScreen(
    onNavBack: () -> Unit,
    onNavToLogin: () -> Unit
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val authRepository = remember { AuthRepository() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    val currentUserId = authRepository.currentUserId ?: ""
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf(authRepository.currentUserEmail ?: "") }
    var isSavingInfo by remember { mutableStateOf(false) }
    var saveInfoMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val user = authRepository.getCurrentUserData()
        if (user != null) {
            displayName = user.displayName
            username = user.username.ifBlank { user.email.substringBefore("@") }
            userEmail = user.email
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Account Information Section
            item {
                Text(
                    text = "Account Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; saveInfoMessage = null },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; saveInfoMessage = null },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = userEmail,
                    onValueChange = { userEmail = it; saveInfoMessage = null },
                    label = { Text("Email Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (saveInfoMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = saveInfoMessage!!,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val newName = displayName.trim()
                        val newUsername = username.trim().removePrefix("@")
                        val newEmail = userEmail.trim()
                        if (currentUserId.isNotBlank() && newName.isNotBlank()) {
                            scope.launch {
                                isSavingInfo = true
                                try {
                                    val userRef = firestore.collection("users").document(currentUserId)
                                    val userData = mapOf(
                                        "displayName" to newName,
                                        "username" to newUsername,
                                        "email" to newEmail
                                    )
                                    userRef.set(userData, SetOptions.merge()).await()

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

                                    username = newUsername
                                    saveInfoMessage = "Account information updated successfully!"
                                } catch (e: Exception) {
                                    saveInfoMessage = "Failed to update: ${e.localizedMessage}"
                                } finally {
                                    isSavingInfo = false
                                }
                            }
                        }
                    },
                    enabled = !isSavingInfo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSavingInfo) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save Account Info")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Preferences Section
            item {
                Text(
                    text = "Preferences",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Push Notifications", fontSize = 16.sp)
                        Text("Receive notifications for new messages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))
            }

            // About Section
            item {
                Text(
                    text = "About",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("App Name: Talky", fontSize = 15.sp)
                Text("Version: 1.0.0", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Delete Account Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteDialog = true }
                        .padding(vertical = 8.dp)
                ) {
                    Column {
                        Text("Delete Account", fontSize = 16.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text("Permanently delete your account and all data", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (deleteError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(deleteError!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text("Delete Account", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete your account? This will permanently delete your profile, messages, and remove you from all conversations. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        deleteError = null
                        scope.launch {
                            val result = authRepository.deleteAccount()
                            isDeleting = false
                            if (result.isSuccess) {
                                showDeleteDialog = false
                                onNavToLogin()
                            } else {
                                deleteError = result.exceptionOrNull()?.localizedMessage ?: "Failed to delete account"
                                showDeleteDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
