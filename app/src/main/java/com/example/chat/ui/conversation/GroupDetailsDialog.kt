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
import com.example.chat.data.model.Conversation
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
fun GroupDetailsDialog(
    isVisible: Boolean,
    conversation: Conversation?,
    displayName: String,
    currentUserId: String,
    currentUserName: String,
    uiState: ConversationUiState,
    viewModel: ConversationViewModel,
    availableUsersForGroup: List<User>,
    isFetchingUsers: Boolean,
    allChatImages: List<String>,
    onFetchAvailableUsers: (Set<String>) -> Unit,
    onOpenImageGallery: (Pair<List<String>, Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Group Chat Details Dialog (Members, Add Member, Nicknames, Media, Theme, Group Photo change, Group Name change)
    if (isVisible && conversation?.type == "group") {
        var showAddMemberDialog by remember { mutableStateOf(false) }
        var showEditGroupNameDialog by remember { mutableStateOf(false) }
        var newGroupNameInput by remember { mutableStateOf(displayName) }
        var isUploadingGroupPhoto by remember { mutableStateOf(false) }
        var selectedMemberForNickname by remember { mutableStateOf<Pair<String, String>?>(null) }
        var memberNicknameInput by remember { mutableStateOf("") }
        var selectedTab by remember { mutableStateOf(0) } // 0: Members, 1: Media, 2: Theme

        val groupPhotoPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                coroutineScope.launch {
                    isUploadingGroupPhoto = true
                    try {
                        val downloadUrl = CloudinaryUploader.uploadToCloudinary(context, uri, "tmtqcamm", "chatly_unsigned")
                        if (downloadUrl != null) {
                            viewModel.updateGroupPhoto(downloadUrl, currentUserName)
                        }
                    } catch (_: Exception) {}
                    finally {
                        isUploadingGroupPhoto = false
                    }
                }
            }
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
                            title = { Text(displayName, fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { onDismiss() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .clickable { groupPhotoPicker.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        val groupPhoto = conversation.groupPhoto.ifBlank { conversation.participantPhotos["groupPhoto"] ?: conversation.participantPhotos.values.firstOrNull { it.isNotBlank() } ?: "" }
                                        if (groupPhoto.isNotBlank()) {
                                            AsyncImage(
                                                model = groupPhoto,
                                                contentDescription = "Group Photo",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (isUploadingGroupPhoto) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                } else {
                                                    Text("👥", fontSize = 32.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = displayName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    IconButton(onClick = {
                                        newGroupNameInput = displayName
                                        showEditGroupNameDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Group Name", modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(text = "${conversation.participantIds.size} members", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        ScrollableTabRow(
                            selectedTabIndex = selectedTab,
                            edgePadding = 16.dp
                        ) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Members") })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Shared Media") })
                            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Theme") })
                        }

                        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                            when (selectedTab) {
                                0 -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showAddMemberDialog = true
                                                onFetchAvailableUsers(conversation.participantIds.toSet())
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("➕ Add New Member")
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Group Members", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(conversation.participantIds) { memberUid ->
                                                val memberName = conversation.participantNames[memberUid] ?: "User"
                                                val memberPhoto = conversation.participantPhotos[memberUid] ?: ""
                                                val memberNickname = conversation.nicknames[memberUid] ?: ""

                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                        ) {
                                                            Surface(
                                                                modifier = Modifier.size(40.dp),
                                                                shape = CircleShape,
                                                                color = MaterialTheme.colorScheme.primaryContainer
                                                            ) {
                                                                if (memberPhoto.isNotBlank()) {
                                                                    AsyncImage(model = memberPhoto, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                                } else {
                                                                    Box(contentAlignment = Alignment.Center) {
                                                                        Text(memberName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                            }
                                                            Column {
                                                                Text(
                                                                    text = if (memberNickname.isNotBlank()) "$memberName ($memberNickname)" else memberName,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 15.sp
                                                                )
                                                                if (memberUid == currentUserId) {
                                                                    Text("You", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                                                }
                                                            }
                                                        }

                                                        TextButton(
                                                            onClick = {
                                                                selectedMemberForNickname = Pair(memberUid, memberName)
                                                                memberNicknameInput = memberNickname
                                                            }
                                                        ) {
                                                            Text("Set Nickname")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    SharedMediaFolderGallery(
                                        messages = uiState.messages,
                                        onMediaClick = { url, type ->
                                            if (type == "video") {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(Uri.parse(url), "video/*")
                                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Cannot play video", Toast.LENGTH_SHORT).show()
                                                }
                                            } else if (type == "audio") {
                                                Toast.makeText(context, "Voice message / audio file", Toast.LENGTH_SHORT).show()
                                            } else {
                                                onDismiss()
                                                val idx = allChatImages.indexOf(url).coerceAtLeast(0)
                                                onOpenImageGallery(Pair(allChatImages, idx))
                                            }
                                        }
                                    )
                                }
                                2 -> {
                                    val themes = listOf("blue", "green", "orange", "purple", "pink", "dark")
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("Select Group Chat Theme", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        themes.forEach { t ->
                                            Button(
                                                onClick = {
                                                    viewModel.updateThemeColor(t)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = when (t) {
                                                        "dark" -> Color(0xFF212121)
                                                        "green" -> Color(0xFF4CAF50)
                                                        "orange" -> Color(0xFFFF9800)
                                                        "purple" -> Color(0xFF9C27B0)
                                                        "pink" -> Color(0xFFE91E63)
                                                        else -> Color(0xFF2196F3)
                                                    }
                                                )
                                            ) {
                                                Text(t.replaceFirstChar { it.uppercase() }, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddMemberDialog) {
            AlertDialog(
                onDismissRequest = { showAddMemberDialog = false },
                title = { Text("Add Member to Group", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        if (isFetchingUsers) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (availableUsersForGroup.isEmpty()) {
                            Text("No new users available to add.")
                        } else {
                            LazyColumn {
                                items(availableUsersForGroup) { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.addMemberToGroup(user.uid, user.displayName, user.photoUrl, currentUserName) {
                                                    showAddMemberDialog = false
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            if (user.photoUrl.isNotBlank()) {
                                                AsyncImage(model = user.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            } else {
                                                Box(contentAlignment = Alignment.Center) { Text(user.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                                            }
                                        }
                                        Column {
                                            Text(user.displayName, fontWeight = FontWeight.Bold)
                                            Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAddMemberDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showEditGroupNameDialog) {
            AlertDialog(
                onDismissRequest = { showEditGroupNameDialog = false },
                title = { Text("Edit Group Name", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newGroupNameInput,
                        onValueChange = { newGroupNameInput = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateGroupName(newGroupNameInput, currentUserName)
                            showEditGroupNameDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditGroupNameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (selectedMemberForNickname != null) {
            val (targetUid, targetName) = selectedMemberForNickname!!
            AlertDialog(
                onDismissRequest = { selectedMemberForNickname = null },
                title = { Text("Set Nickname for $targetName", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = memberNicknameInput,
                        onValueChange = { memberNicknameInput = it },
                        label = { Text("Nickname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateGroupMemberNickname(targetUid, targetName, memberNicknameInput, currentUserName)
                            selectedMemberForNickname = null
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedMemberForNickname = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
