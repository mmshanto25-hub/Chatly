package com.example.chat.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.chat.core.utils.CloudinaryUploader
import com.example.chat.data.repository.AuthRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavBack: () -> Unit
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    val currentUserId = authRepository.currentUserId ?: ""
    var userEmail by remember { mutableStateOf(authRepository.currentUserEmail ?: "") }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var profilePictures by remember { mutableStateOf<List<String>>(emptyList()) }
    var isGridView by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var fullScreenImageIndex by remember { mutableStateOf<Int?>(null) }

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
                    val downloadUrl = CloudinaryUploader.uploadToCloudinary(context, uri, cloudinaryCloudName, cloudinaryUploadPreset)
                    if (downloadUrl != null) {
                        val userRef = firestore.collection("users").document(currentUserId)
                        userRef.update(
                            mapOf(
                                "photoUrl" to downloadUrl,
                                "profilePictures" to FieldValue.arrayUnion(downloadUrl)
                            )
                        ).await()

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
                        profilePictures = (listOf(downloadUrl) + profilePictures).distinct()
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

    fun setAsCurrentProfile(url: String) {
        scope.launch {
            isSaving = true
            try {
                val userRef = firestore.collection("users").document(currentUserId)
                userRef.update("photoUrl", url).await()

                val convQuery = firestore.collection("conversations")
                    .whereArrayContains("participantIds", currentUserId)
                    .get()
                    .await()

                if (!convQuery.isEmpty) {
                    val batch = firestore.batch()
                    for (doc in convQuery.documents) {
                        batch.update(doc.reference, "participantPhotos.$currentUserId", url)
                    }
                    batch.commit().await()
                }

                photoUrl = url
                saveMessage = "Current profile picture updated!"
            } catch (e: Exception) {
                saveMessage = "Failed: ${e.localizedMessage}"
            } finally {
                isSaving = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val user = authRepository.getCurrentUserData()
        if (user != null) {
            displayName = user.displayName.ifBlank { "Talky User" }
            username = user.username.ifBlank { user.email.substringBefore("@") }
            userEmail = user.email
            photoUrl = user.photoUrl
            profilePictures = (listOf(user.photoUrl) + user.profilePictures).filter { it.isNotBlank() }.distinct()
        } else {
            displayName = "Talky User"
            username = "user"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (username.isNotBlank()) username else "Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                
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
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
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

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; saveMessage = null },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; saveMessage = null },
                    label = { Text("Username") },
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

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val newName = displayName.trim()
                        val newUsername = username.trim().removePrefix("@")
                        if (currentUserId.isNotBlank() && newName.isNotBlank()) {
                            scope.launch {
                                isSaving = true
                                try {
                                    val userRef = firestore.collection("users").document(currentUserId)
                                    val userData = mapOf(
                                        "uid" to currentUserId,
                                        "displayName" to newName,
                                        "username" to newUsername,
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

                                    username = newUsername
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

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile Gallery (${profilePictures.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { isGridView = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isGridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Grid", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { isGridView = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isGridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("List", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (profilePictures.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No uploaded pictures yet. Tap your avatar above to add some!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (isGridView) {
                    val rows = profilePictures.chunked(3)
                    items(rows) { rowImages ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (imgUrl in rowImages) {
                                val isSelected = (imgUrl == photoUrl)
                                val originalIndex = profilePictures.indexOf(imgUrl)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { fullScreenImageIndex = originalIndex },
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    AsyncImage(
                                        model = imgUrl,
                                        contentDescription = "Gallery Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (isSelected) {
                                        Surface(
                                            modifier = Modifier.padding(4.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "✓",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(3 - rowImages.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(profilePictures) { imgUrl ->
                        val isSelected = (imgUrl == photoUrl)
                        val originalIndex = profilePictures.indexOf(imgUrl)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { fullScreenImageIndex = originalIndex },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AsyncImage(
                                        model = imgUrl,
                                        contentDescription = "Gallery Image",
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Column {
                                        Text(
                                            text = if (isSelected) "Current Profile Picture" else "Uploaded Picture",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Tap to view full screen",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Badge {
                                        Text("Active")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (fullScreenImageIndex != null && profilePictures.isNotEmpty()) {
        val pagerState = rememberPagerState(initialPage = fullScreenImageIndex!!) { profilePictures.size }
        Dialog(
            onDismissRequest = { fullScreenImageIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val imgUrl = profilePictures[pageIndex]
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = "Fullscreen Profile Picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { fullScreenImageIndex = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                        }

                        val currentImageUrl = profilePictures[pagerState.currentPage]
                        val isActive = (currentImageUrl == photoUrl)
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "Active Profile",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    setAsCurrentProfile(currentImageUrl)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Set as Active Profile")
                            }
                        }
                    }
                }
            }
        }
    }
}
