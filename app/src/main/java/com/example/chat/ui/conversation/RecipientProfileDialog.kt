package com.example.chat.ui.conversation

import android.Manifest

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.chat.core.utils.CloudinaryUploader
import com.example.chat.data.model.Message
import com.example.chat.data.model.User
import com.example.chat.ui.chats.ChatListViewModel
import com.example.chat.ui.conversation.components.*
import com.example.chat.ui.contacts.formatLastSeen
import com.example.chat.ui.chats.MessageStatusTicks
import com.example.chat.ui.profile.StoryHighlightsRow
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientProfileDialog(
    isVisible: Boolean,
    recipientId: String,
    recipientName: String,
    currentUserId: String,
    chatListViewModel: ChatListViewModel,
    onNavToChat: (conversationId: String, recipientId: String, recipientName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var recipientGalleryPics by remember { mutableStateOf<List<String>>(emptyList()) }
    var fullScreenGalleryIndex by remember { mutableStateOf<Int?>(null) }

    // Recipient Profile Details Dialog (for direct chat)
    if (isVisible && recipientId.isNotBlank()) {
        var recipientUser by remember { mutableStateOf<User?>(null) }
        var isLoadingRecipient by remember { mutableStateOf(true) }
        var selectedGalleryFolder by remember { mutableStateOf("all") } // "all", "profile", "cover"

        LaunchedEffect(recipientId) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(recipientId)
                    .update("profileVisitorsCount",  FieldValue.increment(1)).await()

                val doc = db.collection("users").document(recipientId).get().await()
                val u = doc.toObject(User::class.java)
                recipientUser = u
                if (u != null) {
                    recipientGalleryPics = (listOf(u.photoUrl) + u.profilePictures + listOf(u.coverPhotoUrl) + u.coverPictures).filter { it.isNotBlank() }.distinct()
                }
            } catch (_: Exception) {}
            isLoadingRecipient = false
        }

        Dialog(
            onDismissRequest = { onDismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(recipientUser?.displayName ?: recipientName, fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { onDismiss() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                ) { padding ->
                    if (isLoadingRecipient) {
                        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (recipientUser != null) {
                        val u = recipientUser!!
                        val profilePics = (listOf(u.photoUrl) + u.profilePictures).filter { it.isNotBlank() }.distinct()
                        val coverPics = (listOf(u.coverPhotoUrl) + u.coverPictures).filter { it.isNotBlank() }.distinct()
                        val displayedPics = when(selectedGalleryFolder) {
                            "profile" -> profilePics
                            "cover" -> coverPics
                            else -> (profilePics + coverPics).distinct()
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                // Cover Picture & Profile Avatar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .align(Alignment.TopCenter)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (u.coverPhotoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = u.coverPhotoUrl,
                                                contentDescription = "Cover Picture",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text("🌅 Cover Photo", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(3.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (profilePics.isNotEmpty()) {
                                                    recipientGalleryPics = displayedPics
                                                    fullScreenGalleryIndex = 0
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            if (u.photoUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = u.photoUrl,
                                                    contentDescription = "Profile Picture",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = u.displayName.take(1).uppercase(),
                                                        fontSize = 40.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = u.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                                if (u.username.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "@${u.username}",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = u.email,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )


                                Spacer(modifier = Modifier.height(20.dp))

                                StoryHighlightsRow(
                                    ownerId = u.uid,
                                    ownerName = u.displayName,
                                    ownerPhoto = u.photoUrl,
                                    viewerId = currentUserId,
                                    viewModel = chatListViewModel,
                                    onNavToChat = onNavToChat,
                                    ownerUser = u
                                )

                                // Folder Tabs
                                ScrollableTabRow(
                                    selectedTabIndex = when(selectedGalleryFolder) { "profile" -> 1; "cover" -> 2; else -> 0 },
                                    edgePadding = 0.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Tab(selected = selectedGalleryFolder == "all", onClick = { selectedGalleryFolder = "all" }, text = { Text("🖼️ All (${(profilePics + coverPics).distinct().size})") })
                                    Tab(selected = selectedGalleryFolder == "profile", onClick = { selectedGalleryFolder = "profile" }, text = { Text("👤 Profile (${profilePics.size})") })
                                    Tab(selected = selectedGalleryFolder == "cover", onClick = { selectedGalleryFolder = "cover" }, text = { Text("🌅 Cover (${coverPics.size})") })
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                            }

                            if (displayedPics.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No pictures in gallery.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                val rows = displayedPics.chunked(3)
                                items(rows) { rowImages ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (imgUrl in rowImages) {
                                        val originalIndex = displayedPics.indexOf(imgUrl)
                                            Box(
                                            modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        recipientGalleryPics = displayedPics
                                                        fullScreenGalleryIndex = originalIndex
                                                    },
                                                contentAlignment = Alignment.TopEnd
                                            ) {
                                                AsyncImage(
                                                    model = imgUrl,
                                                    contentDescription = "Gallery Image",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                        repeat(3 - rowImages.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            Text("User profile not found.")
                        }
                    }
                }
            }
        }
    }

    // Full Screen Gallery Dialog with Horizontal Pager & Download Button
    if (fullScreenGalleryIndex != null && recipientGalleryPics.isNotEmpty()) {
        val pagerState = rememberPagerState(initialPage = fullScreenGalleryIndex!!) { recipientGalleryPics.size }
        Dialog(
            onDismissRequest = { fullScreenGalleryIndex = null },
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
                        val imgUrl = recipientGalleryPics[pageIndex]
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = "Fullscreen Picture",
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
                        IconButton(onClick = { fullScreenGalleryIndex = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                        }

                        val currentImageUrl = recipientGalleryPics[pagerState.currentPage]
                        Button(
                            onClick = {
                                downloadImage(context, currentImageUrl)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Download ⬇️")
                        }
                    }
                }
            }
        }
    }
}
