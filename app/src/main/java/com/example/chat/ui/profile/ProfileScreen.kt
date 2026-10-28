package com.example.chat.ui.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat.data.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavBack: () -> Unit,
    onNavToLogin: () -> Unit
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    val currentUserId = authRepository.currentUserId ?: ""
    var userEmail by remember { mutableStateOf(authRepository.currentUserEmail ?: "") }
    var displayName by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // Cloudinary Configuration
    val cloudinaryCloudName = "tmtqcamm"
    val cloudinaryUploadPreset = "chatly_unsigned"

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && currentUserId.isNotBlank()) {
            scope.launch {
                isSaving = true
                saveMessage = "Uploading to Cloudinary..."
                try {
                    val downloadUrl = uploadImageToCloudinary(context, uri, cloudinaryCloudName, cloudinaryUploadPreset)
                    if (downloadUrl != null) {
                        // Update user document
                        val userRef = firestore.collection("users").document(currentUserId)
                        userRef.update("photoUrl", downloadUrl).await()

                        // Update participant photos in all existing conversations
                        val convQuery = firestore.collection("conversations")
                            .whereArrayContains("participantIds", currentUserId)
                            .get()
                            .await()

                        if (!convQuery.isEmpty) {
                            val batch = firestore.batch()
                            for (doc in convQuery.documents) {
                                batch.update(doc.reference, "participantPhotos.$currentUserId", downloadUrl)
                            }
                            batch.commit().await()
                        }

                        photoUrl = downloadUrl
                        saveMessage = "Profile picture updated successfully!"
                    } else {
                        saveMessage = "Failed to upload. Please try again."
                    }
                } catch (_: Exception) {
                    saveMessage = "Error uploading image to Cloudinary."
                } finally {
                    isSaving = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val user = authRepository.getCurrentUserData()
        if (user != null) {
            displayName = user.displayName.ifBlank { "Chatly User" }
            userEmail = user.email
            photoUrl = user.photoUrl
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
            
            // Profile Picture Avatar (Clickable to change)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (displayName.isNotBlank()) displayName.take(1).uppercase() else "U",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap image to change",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )

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
                                val userRef = firestore.collection("users").document(currentUserId)
                                val userData = mapOf(
                                    "uid" to currentUserId,
                                    "displayName" to newName,
                                    "email" to userEmail,
                                    "photoUrl" to photoUrl
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

suspend fun uploadImageToCloudinary(context: Context, imageUri: Uri, cloudName: String, uploadPreset: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext null
            val bytes = inputStream.readBytes()
            inputStream.close()

            val boundary = "Boundary-${System.currentTimeMillis()}"
            val url = URL("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val outputStream = connection.outputStream
            val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

            // Add upload_preset
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n")
            writer.append("$uploadPreset\r\n")

            // Add file part
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"profile.jpg\"\r\n")
            writer.append("Content-Type: image/jpeg\r\n\r\n")
            writer.flush()

            outputStream.write(bytes)
            outputStream.flush()

            writer.append("\r\n--$boundary--\r\n")
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val urlRegex = """"secure_url"\s*:\s*"([^"]+)"""".toRegex()
                val matchResult = urlRegex.find(response)
                matchResult?.groupValues?.get(1)?.replace("\\/", "/")
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
