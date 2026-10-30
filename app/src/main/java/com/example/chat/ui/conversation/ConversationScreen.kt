package com.example.chat.ui.conversation

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Calendar
import java.util.Locale

fun formatLastSeen(presence: String, lastSeenAt: Long): String {
    if (presence.equals("online", ignoreCase = true)) return "online"
    if (lastSeenAt == 0L) return "offline"
    val diff = System.currentTimeMillis() - lastSeenAt
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "last seen just now"
        minutes < 60 -> "last seen ${minutes}m ago"
        hours < 24 -> "last seen ${hours}h ago"
        days < 7 -> "last seen ${days}d ago"
        else -> "last seen recently"
    }
}

fun getThemeBubbleColor(theme: String, isMe: Boolean): Color {
    return when (theme) {
        "dark" -> if (isMe) Color(0xFF303030) else Color(0xFF1E1E1E)
        "green" -> if (isMe) Color(0xFFC8E6C9) else Color(0xFFE8F5E9)
        "orange" -> if (isMe) Color(0xFFFFE0B2) else Color(0xFFFFF3E0)
        "purple" -> if (isMe) Color(0xFFE1BEE7) else Color(0xFFF3E5F5)
        "pink" -> if (isMe) Color(0xFFF8BBD0) else Color(0xFFFCE4EC)
        else -> if (isMe) Color(0xFFD1E4FF) else Color(0xFFEFEFEF)
    }
}

fun getThemeBackgroundColor(theme: String): Color {
    return when (theme) {
        "dark" -> Color(0xFF121212)
        "green" -> Color(0xFFF1F8E9)
        "orange" -> Color(0xFFFFF8E1)
        "purple" -> Color(0xFFFCE4EC)
        "pink" -> Color(0xFFFFEBEE)
        else -> Color(0xFFF7F9FC)
    }
}

fun downloadImage(context: Context, imageUrl: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(imageUrl))
            .setTitle("Profile Image")
            .setDescription("Downloading image...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Talky_${System.currentTimeMillis()}.jpg")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "Downloading image to Downloads...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to download: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    recipientName: String,
    onNavBack: () -> Unit,
    onNavToChat: ((conversationId: String, recipientId: String, recipientName: String) -> Unit)? = null
) {
    val activeConversationId = viewModel.conversationId
    DisposableEffect(activeConversationId) {
        com.example.chat.core.utils.ActiveChatTracker.activeConversationId = activeConversationId
        onDispose {
            com.example.chat.core.utils.ActiveChatTracker.activeConversationId = null
        }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId = viewModel.currentUserId
    val listState = rememberLazyListState()

    var showBottomSheet by remember { mutableStateOf(false) }
    var showVoiceRecorder by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showRecipientProfileDialog by remember { mutableStateOf(false) }
    var showGroupDetailsDialog by remember { mutableStateOf(false) }
    var showPinnedMessagesDialog by remember { mutableStateOf(false) }
    var currentUserName by remember { mutableStateOf("User") }
    var availableUsersForGroup by remember { mutableStateOf<List<User>>(emptyList()) }
    var isFetchingUsers by remember { mutableStateOf(false) }
    var selectedMessageForMenu by remember { mutableStateOf<Message?>(null) }

    var recipientGalleryPics by remember { mutableStateOf<List<String>>(emptyList()) }
    var fullScreenGalleryIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(currentUserId).get().await()
                currentUserName = doc.getString("displayName") ?: "User"
            } catch (_: Exception) {}
        }
    }

    val sheetState = rememberModalBottomSheetState()

    val conversation = uiState.conversation
    val recipientId = conversation?.participantIds?.firstOrNull { it != currentUserId } ?: ""
    val customNickname = conversation?.nicknames?.get(recipientId) ?: ""
    val displayName = if (conversation?.type == "group") {
        conversation.getTitle(currentUserId)
    } else {
        customNickname.ifBlank { recipientName }
    }
    val themeColor = uiState.conversation?.themeColor ?: "blue"

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showVoiceRecorder = true
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendMediaMessage(context, uri, "image")
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendMediaMessage(context, uri, "video")
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Share Content",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showBottomSheet = false
                            imagePickerLauncher.launch("image/*")
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📷", fontSize = 24.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Photo", fontSize = 12.sp)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showBottomSheet = false
                            videoPickerLauncher.launch("video/*")
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🎥", fontSize = 24.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Video", fontSize = 12.sp)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showBottomSheet = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                showVoiceRecorder = true
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🎤", fontSize = 24.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Voice", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    if (showVoiceRecorder) {
        VoiceRecorderDialog(
            onDismiss = { showVoiceRecorder = false },
            onAudioRecorded = { audioUri ->
                viewModel.sendMediaMessage(context, audioUri, "audio")
                showVoiceRecorder = false
            }
        )
    }

    if (fullScreenImageUrl != null) {
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = fullScreenImageUrl,
                    contentDescription = "Full Screen Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullScreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    // Pinned Messages Dialog
    if (showPinnedMessagesDialog) {
        val pinnedMsgs = uiState.messages.filter { it.isPinned }
        AlertDialog(
            onDismissRequest = { showPinnedMessagesDialog = false },
            title = { Text("Pinned Messages", fontWeight = FontWeight.Bold) },
            text = {
                if (pinnedMsgs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No pinned messages in this chat.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pinnedMsgs) { msg ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("📌 Pinned Message", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(msg.text, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPinnedMessagesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Edit Nickname Dialog
    if (showNicknameDialog && recipientId.isNotBlank()) {
        val myCurrentNickname = conversation?.nicknames?.get(currentUserId) ?: ""
        val recipientCurrentNickname = conversation?.nicknames?.get(recipientId) ?: ""
        var myNicknameInput by remember { mutableStateOf(myCurrentNickname) }
        var recipientNicknameInput by remember { mutableStateOf(recipientCurrentNickname) }

        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("Edit Nicknames", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = myNicknameInput,
                        onValueChange = { myNicknameInput = it },
                        label = { Text("Your Nickname in this chat") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = recipientNicknameInput,
                        onValueChange = { recipientNicknameInput = it },
                        label = { Text("$recipientName's Nickname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateNicknames(recipientId, myNicknameInput, recipientNicknameInput, currentUserName, recipientName)
                        showNicknameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Change Theme Dialog
    if (showThemeDialog) {
        val themes = listOf("blue", "green", "orange", "purple", "pink", "dark")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Change Chat Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    themes.forEach { t ->
                        Button(
                            onClick = {
                                viewModel.updateThemeColor(t)
                                showThemeDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (t) {
                                    "dark" -> Color(0xFF212121)
                                    "green" -> Color(0xFF4CAF50)
                                    "orange" -> Color(0xFFFF9800)
                                    "purple" -> Color(0xFF9C27B0)
                                    "pink" -> Color(0xFFE91E63)
                                    else -> Color(0xFF2196F3)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(t.replaceFirstChar { it.uppercase() }, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create Group Dialog with Group Picture
    if (showCreateGroupDialog) {
        var groupNameInput by remember { mutableStateOf("Group with $displayName") }
        var groupPhotoUrl by remember { mutableStateOf("") }
        var isUploadingGroupPhoto by remember { mutableStateOf(false) }
        val selectedUserIds = remember { mutableStateListOf<String>() }

        val groupImagePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                coroutineScope.launch {
                    isUploadingGroupPhoto = true
                    try {
                        val downloadUrl = CloudinaryUploader.uploadToCloudinary(context, uri, "tmtqcamm", "chatly_unsigned")
                        if (downloadUrl != null) {
                            groupPhotoUrl = downloadUrl
                        }
                    } catch (_: Exception) {}
                    finally {
                        isUploadingGroupPhoto = false
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create Group", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .clickable { groupImagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            if (groupPhotoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = groupPhotoUrl,
                                    contentDescription = "Group Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isUploadingGroupPhoto) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        Text("📷 Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Included Members:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(onClick = {}, label = { Text("You") })
                        AssistChip(onClick = {}, label = { Text(displayName) })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Add more members:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (isFetchingUsers) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    } else if (availableUsersForGroup.isEmpty()) {
                        Text(
                            text = "No other users found.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            items(availableUsersForGroup) { user ->
                                val isSelected = selectedUserIds.contains(user.uid)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) {
                                                selectedUserIds.remove(user.uid)
                                            } else {
                                                selectedUserIds.add(user.uid)
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                selectedUserIds.add(user.uid)
                                            } else {
                                                selectedUserIds.remove(user.uid)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = user.displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = user.email,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val gName = groupNameInput.ifBlank { "Group with $displayName" }
                        viewModel.createGroup(gName, selectedUserIds.toList(), groupPhotoUrl) { newGroupConvId, finalGroupName ->
                            showCreateGroupDialog = false
                            onNavToChat?.invoke(newGroupConvId, "group", finalGroupName)
                        }
                    }
                ) {
                    Text("Create Group")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Group Chat Details Dialog (Members, Add Member, Nicknames, Media, Pinned, Theme, Group Photo change, Group Name change)
    if (showGroupDetailsDialog && conversation?.type == "group") {
        var showAddMemberDialog by remember { mutableStateOf(false) }
        var showEditGroupNameDialog by remember { mutableStateOf(false) }
        var newGroupNameInput by remember { mutableStateOf(displayName) }
        var isUploadingGroupPhoto by remember { mutableStateOf(false) }
        var selectedMemberForNickname by remember { mutableStateOf<Pair<String, String>?>(null) }
        var memberNicknameInput by remember { mutableStateOf("") }
        var selectedTab by remember { mutableStateOf(0) } // 0: Members, 1: Media, 2: Pinned, 3: Theme

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
            onDismissRequest = { showGroupDetailsDialog = false },
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
                                IconButton(onClick = { showGroupDetailsDialog = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
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
                                        val groupPhoto = conversation.groupPhoto.ifBlank { conversation.participantPhotos.values.firstOrNull { it.isNotBlank() } ?: "" }
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
                            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Pinned Msgs") })
                            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Theme") })
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
                                                isFetchingUsers = true
                                                viewModel.fetchAvailableUsers { users ->
                                                    availableUsersForGroup = users.filter { !conversation.participantIds.contains(it.uid) }
                                                    isFetchingUsers = false
                                                }
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
                                    val mediaMessages = uiState.messages.filter { it.messageType == "image" || it.messageType == "video" || it.messageType == "audio" }
                                    if (mediaMessages.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("No shared media found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize().padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(mediaMessages) { msg ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = when(msg.messageType) {
                                                                "image" -> "📷 Photo"
                                                                "video" -> "🎥 Video"
                                                                else -> "🎤 Voice Message"
                                                            },
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        if (msg.messageType == "image" && msg.attachmentUrl.isNotBlank()) {
                                                            AsyncImage(
                                                                model = msg.attachmentUrl,
                                                                contentDescription = null,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(160.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .clickable { fullScreenImageUrl = msg.attachmentUrl },
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else if (msg.messageType == "video") {
                                                            Text("Video attachment", fontSize = 14.sp)
                                                        } else if (msg.messageType == "audio") {
                                                            VoiceMessagePlayer(audioUrl = msg.attachmentUrl)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    val pinnedMessages = uiState.messages.filter { it.isPinned }
                                    if (pinnedMessages.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("No pinned messages.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize().padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(pinnedMessages) { msg ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text("📌 Pinned Message", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(msg.text, fontSize = 15.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                3 -> {
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

    // Recipient Profile Details Dialog (for direct chat)
    if (showRecipientProfileDialog && recipientId.isNotBlank()) {
        var recipientUser by remember { mutableStateOf<User?>(null) }
        var isLoadingRecipient by remember { mutableStateOf(true) }
        var isGridView by remember { mutableStateOf(true) }

        LaunchedEffect(recipientId) {
            try {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("users").document(recipientId).get().await()
                val u = doc.toObject(User::class.java)
                recipientUser = u
                if (u != null) {
                    recipientGalleryPics = (listOf(u.photoUrl) + u.profilePictures).filter { it.isNotBlank() }.distinct()
                }
            } catch (_: Exception) {}
            isLoadingRecipient = false
        }

        Dialog(
            onDismissRequest = { showRecipientProfileDialog = false },
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
                                IconButton(onClick = { showRecipientProfileDialog = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                ) { padding ->
                    if (isLoadingRecipient) {
                        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (recipientUser != null) {
                        val u = recipientUser!!
                        val galleryPics = recipientGalleryPics.ifEmpty { (listOf(u.photoUrl) + u.profilePictures).filter { it.isNotBlank() }.distinct() }

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
                                        .size(110.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (galleryPics.isNotEmpty()) fullScreenGalleryIndex = 0
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
                                                    fontSize = 42.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
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
                                Spacer(modifier = Modifier.height(4.dp))
                                val statusText = formatLastSeen(u.presence, u.lastSeenAt)
                                Text(
                                    text = statusText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (u.presence.equals("online", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(28.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Profile Gallery (${galleryPics.size})",
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

                            if (galleryPics.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No profile pictures in gallery.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                if (isGridView) {
                                    val rows = galleryPics.chunked(3)
                                    items(rows) { rowImages ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            for (imgUrl in rowImages) {
                                                val originalIndex = galleryPics.indexOf(imgUrl)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { fullScreenGalleryIndex = originalIndex },
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
                                } else {
                                    items(galleryPics) { imgUrl ->
                                        val originalIndex = galleryPics.indexOf(imgUrl)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                                .clickable { fullScreenGalleryIndex = originalIndex },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
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
                                                        text = if (imgUrl == u.photoUrl) "Current Profile Picture" else "Uploaded Picture",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                        text = "Tap to view full screen & download",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
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

    // Message Options Dialog
    if (selectedMessageForMenu != null) {
        val msg = selectedMessageForMenu!!
        val isPinned = msg.isPinned
        AlertDialog(
            onDismissRequest = { selectedMessageForMenu = null },
            title = { Text("Message Options", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.togglePinMessage(msg.messageId, msg.text, isPinned)
                            selectedMessageForMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isPinned) "📌 Unpin Message" else "📌 Pin Message", fontSize = 16.sp)
                    }
                    if (msg.senderId == currentUserId) {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessage(msg.messageId)
                                selectedMessageForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🗑️ Delete Message", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMessageForMenu = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            if (conversation?.type == "group") {
                                showGroupDetailsDialog = true
                            } else if (recipientId.isNotBlank()) {
                                showRecipientProfileDialog = true
                            }
                        }
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            val headerPhoto = if (conversation?.type == "group") {
                                conversation.groupPhoto.ifBlank { conversation.participantPhotos.values.firstOrNull { it.isNotBlank() } ?: "" }
                            } else {
                                uiState.recipientPhoto
                            }
                            if (headerPhoto.isNotBlank()) {
                                AsyncImage(
                                    model = headerPhoto,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = displayName.take(1).uppercase(Locale.getDefault()),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            val pinnedText = conversation?.pinnedMessageText
                            if (!pinnedText.isNullOrBlank()) {
                                Text(
                                    text = "📌 $pinnedText",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val statusText = if (conversation?.type == "group") "${conversation.participantIds.size} members" else formatLastSeen(uiState.recipientPresence, uiState.recipientLastSeen)
                            val statusColor = if (uiState.recipientPresence.equals("online", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            Text(text = statusText, fontSize = 12.sp, color = statusColor)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (conversation?.type != "group") {
                        IconButton(onClick = { /* Call action */ }) {
                            Icon(Icons.Default.Call, contentDescription = "Call")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📌 Pinned Messages") },
                                onClick = {
                                    showMenu = false
                                    showPinnedMessagesDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("✏️ Edit Nickname") },
                                onClick = {
                                    showMenu = false
                                    showNicknameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🎨 Change Theme") },
                                onClick = {
                                    showMenu = false
                                    showThemeDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("👥 Create a group with $displayName") },
                                onClick = {
                                    showMenu = false
                                    showCreateGroupDialog = true
                                    isFetchingUsers = true
                                    viewModel.fetchAvailableUsers { users ->
                                        availableUsersForGroup = users.filter { it.uid != recipientId }
                                        isFetchingUsers = false
                                    }
                                }
                            )
                        }
                    } else {
                        // For group chat, action to view Pinned Messages via three-dot menu if desired or keep clean
                        IconButton(onClick = { showPinnedMessagesDialog = true }) {
                            Text("📌", fontSize = 18.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isUploading) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Sending media...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("${uiState.uploadProgress}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { uiState.uploadProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showBottomSheet = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Attachment", tint = MaterialTheme.colorScheme.primary)
                        }

                        OutlinedTextField(
                            value = uiState.messageInput,
                            onValueChange = { viewModel.updateMessageInput(it) },
                            placeholder = { Text("Message...") },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp, max = 120.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage() })
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    ) { padding ->
        val bubbleColorMe = getThemeBubbleColor(themeColor, true)
        val bubbleColorOther = getThemeBubbleColor(themeColor, false)
        val bgColor = getThemeBackgroundColor(themeColor)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                val pinnedMsgId = conversation?.pinnedMessageId
                val pinnedMsgText = conversation?.pinnedMessageText
                if (!pinnedMsgId.isNullOrBlank() && !pinnedMsgText.isNullOrBlank()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📌", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Pinned Message", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(pinnedMsgText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                items(
                    items = uiState.messages,
                    key = { it.messageId }
                ) { msg ->
                    val isMe = (msg.senderId == currentUserId)
                    val isSystem = (msg.messageType == "system")
                    val isPinned = msg.isPinned

                    if (isSystem) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = msg.text,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { selectedMessageForMenu = msg }
                                ),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (!isMe) {
                                val senderPhoto = conversation?.participantPhotos?.get(msg.senderId) ?: ""
                                val senderName = conversation?.participantNames?.get(msg.senderId) ?: "User"
                                Surface(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    if (senderPhoto.isNotBlank()) {
                                        AsyncImage(
                                            model = senderPhoto,
                                            contentDescription = "Sender Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = senderName.take(1).uppercase(Locale.getDefault()),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isMe) bubbleColorMe else bubbleColorOther,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    if (!isMe && conversation?.type == "group") {
                                        val senderName = conversation.participantNames[msg.senderId] ?: "User"
                                        val memberNickname = conversation.nicknames[msg.senderId] ?: ""
                                        val displayNameForSender = if (memberNickname.isNotBlank()) memberNickname else senderName
                                        Text(
                                            text = displayNameForSender,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }

                                    if (isPinned) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("📌 Pinned", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }

                                    if (msg.messageType == "image" && msg.attachmentUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = msg.attachmentUrl,
                                            contentDescription = "Image attachment",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { fullScreenImageUrl = msg.attachmentUrl },
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    } else if (msg.messageType == "video" && msg.attachmentUrl.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.Black)
                                                .clickable {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(msg.attachmentUrl)).apply {
                                                        setDataAndType(Uri.parse(msg.attachmentUrl), "video/*")
                                                    }
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = Color.White, modifier = Modifier.size(48.dp))
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    } else if (msg.messageType == "audio" && msg.attachmentUrl.isNotBlank()) {
                                        VoiceMessagePlayer(audioUrl = msg.attachmentUrl)
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }

                                    if (msg.text.isNotBlank() && msg.messageType == "text") {
                                        Text(
                                            text = msg.text,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = DateFormat.format("HH:mm", Calendar.getInstance().apply { timeInMillis = msg.createdAt }).toString(),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun VoiceRecorderDialog(
    onDismiss: () -> Unit,
    onAudioRecorded: (Uri) -> Unit
) {
    var mediaRecorder: MediaRecorder? by remember { mutableStateOf(null) }
    var audioFile: File? by remember { mutableStateOf(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            audioFile = File.createTempFile("voice_", ".3gp", context.cacheDir)
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = {
            try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) {}
            onDismiss()
        },
        title = { Text("Recording Voice Message", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔴 Recording...", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        mediaRecorder?.stop()
                        mediaRecorder?.release()
                        audioFile?.let {
                            onAudioRecorded(Uri.fromFile(it))
                        }
                    } catch (_: Exception) {
                        onDismiss()
                    }
                }
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    try {
                        mediaRecorder?.stop()
                        mediaRecorder?.release()
                    } catch (_: Exception) {}
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VoiceMessagePlayer(audioUrl: String) {
    val context = LocalContext.current
    val isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }

    DisposableEffect(audioUrl) {
        onDispose {
            try { mediaPlayer?.release() } catch (_: Exception) {}
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    try {
                        mediaPlayer?.pause()
                    } catch (_: Exception) {}
                } else {
                    try {
                        mediaPlayer?.start()
                    } catch (_: Exception) {}
                }
            },
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(
            text = if (isPlaying) "Playing voice message..." else "Voice message 🎤",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
