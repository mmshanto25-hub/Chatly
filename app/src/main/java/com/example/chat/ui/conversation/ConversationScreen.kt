package com.example.chat.ui.conversation

import android.Manifest
import android.app.DownloadManager
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
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
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
        "dark" -> if (isMe) Color(0xFF4E6178) else Color(0xFF313D4A)
        "green" -> if (isMe) Color(0xFFC8E6C9) else Color(0xFFE8F5E9)
        "orange" -> if (isMe) Color(0xFFFFE0B2) else Color(0xFFFFF3E0)
        "purple" -> if (isMe) Color(0xFFE1BEE7) else Color(0xFFF3E5F5)
        "pink" -> if (isMe) Color(0xFFF8BBD0) else Color(0xFFFCE4EC)
        else -> if (isMe) Color(0xFFD1E4FF) else Color(0xFFEFEFEF)
    }
}

fun getThemeBackgroundColor(theme: String): Color {
    return when (theme) {
        "dark" -> Color(0xFF222831)
        "green" -> Color(0xFFF1F8E9)
        "orange" -> Color(0xFFFFF8E1)
        "purple" -> Color(0xFFFCE4EC)
        "pink" -> Color(0xFFFFEBEE)
        else -> Color(0xFFF7F9FC)
    }
}

fun getThemeTopAppBarColor(theme: String): Color {
    return when (theme) {
        "dark" -> Color(0xFF2E3842)
        "green" -> Color(0xFFA5D6A7)
        "orange" -> Color(0xFFFFCC80)
        "purple" -> Color(0xFFE1BEE7)
        "pink" -> Color(0xFFF8BBD0)
        else -> Color(0xFFD1E4FF)
    }
}

fun getThemeTopAppBarContentColor(theme: String): Color {
    return when (theme) {
        "dark" -> Color.White
        else -> Color(0xFF1A1A1A)
    }
}

fun getThemeBottomBarColor(theme: String): Color {
    return when (theme) {
        "dark" -> Color(0xFF2E3842)
        "green" -> Color(0xFFC8E6C9)
        "orange" -> Color(0xFFFFE0B2)
        "purple" -> Color(0xFFE8D5F5)
        "pink" -> Color(0xFFFCE4EC)
        else -> Color(0xFFF7F9FC)
    }
}

fun getThemeBottomBarContentColor(theme: String): Color {
    return when (theme) {
        "dark" -> Color.White
        else -> Color(0xFF1A1A1A)
    }
}

fun downloadMedia(context: Context, mediaUrl: String, isVideo: Boolean) {
    try {
        val ext = if (isVideo) "mp4" else "jpg"
        val title = if (isVideo) "Video File" else "Image File"
        val request = DownloadManager.Request(Uri.parse(mediaUrl))
            .setTitle(title)
            .setDescription("Downloading $title...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Chatly_${System.currentTimeMillis()}.$ext")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "Downloading $title to Downloads...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to download: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun downloadImage(context: Context, imageUrl: String) {
    downloadMedia(context, imageUrl, false)
}

@OptIn(ExperimentalMaterial3Api::class)
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

    var fullScreenImageGallery by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showRecipientProfileDialog by remember { mutableStateOf(false) }
    var showGroupDetailsDialog by remember { mutableStateOf(false) }
    var showChatInfoDialog by remember { mutableStateOf(false) }
    var currentUserName by remember { mutableStateOf("User") }
    var availableUsersForGroup by remember { mutableStateOf<List<User>>(emptyList()) }
    var isFetchingUsers by remember { mutableStateOf(false) }

    var recipientGalleryPics by remember { mutableStateOf<List<String>>(emptyList()) }
    var fullScreenGalleryIndex by remember { mutableStateOf<Int?>(null) }
    var activeAlbumGridUrls by remember { mutableStateOf<List<String>?>(null) }

    val allChatImages = remember(uiState.messages) {
        uiState.messages.flatMap { msg ->
            if (msg.messageType == "media_group" && msg.attachmentUrls.isNotEmpty()) {
                msg.attachmentUrls
            } else if (msg.messageType == "image" && msg.attachmentUrl.isNotBlank()) {
                listOf(msg.attachmentUrl)
            } else {
                emptyList()
            }
        }.distinct().reversed()
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(currentUserId).get().await()
                currentUserName = doc.getString("displayName") ?: "User"
            } catch (_: Exception) {}
        }
    }

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
            // Audio permission granted
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val cappedUris = uris.take(20)
            viewModel.sendMultipleMediaMessages(context, cappedUris)
        }
    }

    if (showChatInfoDialog && conversation?.type != "group") {
        ChatInfoDialog(
            recipientName = displayName,
            recipientPhoto = uiState.recipientPhoto,
            presence = uiState.recipientPresence,
            lastSeen = uiState.recipientLastSeen,
            messages = uiState.messages,
            onDismiss = { showChatInfoDialog = false },
            onOpenNickname = { showNicknameDialog = true },
            onOpenTheme = { showThemeDialog = true },
            onOpenCreateGroup = {
                showCreateGroupDialog = true
                isFetchingUsers = true
                viewModel.fetchAvailableUsers { users ->
                    availableUsersForGroup = users.filter { it.uid != recipientId }
                    isFetchingUsers = false
                }
            },
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
                    try {
                        MediaPlayer().apply {
                            setDataSource(url)
                            prepareAsync()
                            setOnPreparedListener { it.start() }
                        }
                        Toast.makeText(context, "Playing audio...", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, "Cannot play audio", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showChatInfoDialog = false
                    val idx = allChatImages.indexOf(url).coerceAtLeast(0)
                    fullScreenImageGallery = Pair(allChatImages, idx)
                }
            }
        )
    }

    if (activeAlbumGridUrls != null) {
        AlbumGridDialog(
            urls = activeAlbumGridUrls!!,
            onDismiss = { activeAlbumGridUrls = null },
            onImageClick = { idx: Int ->
                val urls = activeAlbumGridUrls!!
                activeAlbumGridUrls = null
                fullScreenImageGallery = Pair(urls, idx)
            }
        )
    }
    if (fullScreenImageGallery != null) {
        val (images, initialIdx) = fullScreenImageGallery!!
        val pagerState = rememberPagerState(initialPage = initialIdx) { images.size }
        Dialog(
            onDismissRequest = { fullScreenImageGallery = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val imgUrl = images[pageIndex]
                        FullScreenImageItem(imgUrl = imgUrl)
                    }

                    // Top Bar (Close & Download)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { fullScreenImageGallery = null },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }

                        val currentImageUrl = images.getOrNull(pagerState.currentPage) ?: ""
                        Button(
                            onClick = {
                                if (currentImageUrl.isNotBlank()) {
                                    downloadImage(context, currentImageUrl)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Download")
                        }
                    }
                }
            }
        }
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

    // Group Chat Details Dialog (Members, Add Member, Nicknames, Media, Theme, Group Photo change, Group Name change)
    if (showGroupDetailsDialog && conversation?.type == "group") {
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
                                                showGroupDetailsDialog = false
                                                val idx = allChatImages.indexOf(url).coerceAtLeast(0)
                                                fullScreenImageGallery = Pair(allChatImages, idx)
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

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
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
                                conversation.groupPhoto.ifBlank { conversation.participantPhotos["groupPhoto"] ?: conversation.participantPhotos.values.firstOrNull { it.isNotBlank() } ?: "" }
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
                            val textColor = getThemeTopAppBarContentColor(themeColor)
                            Text(text = displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                            val statusText = if (conversation?.type == "group") "${conversation.participantIds.size} members" else formatLastSeen(uiState.recipientPresence, uiState.recipientLastSeen)
                            val statusColor = if (uiState.recipientPresence.equals("online", true)) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f)
                            Text(text = statusText, fontSize = 12.sp, color = statusColor)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = getThemeTopAppBarContentColor(themeColor))
                    }
                },
                actions = {
                    IconButton(onClick = { /* Call action */ }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = getThemeTopAppBarContentColor(themeColor))
                    }
                    IconButton(
                        onClick = {
                            if (conversation?.type == "group") {
                                showGroupDetailsDialog = true
                            } else {
                                showChatInfoDialog = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Chat Info",
                            tint = getThemeTopAppBarContentColor(themeColor)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = getThemeTopAppBarColor(themeColor),
                    titleContentColor = getThemeTopAppBarContentColor(themeColor),
                    navigationIconContentColor = getThemeTopAppBarContentColor(themeColor),
                    actionIconContentColor = getThemeTopAppBarContentColor(themeColor)
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                color = getThemeBottomBarColor(themeColor),
                contentColor = getThemeBottomBarContentColor(themeColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isRecordingVoice) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Red.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("🔴 Recording voice note... Release to send", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                            }
                        }
                    }

                    if (uiState.replyingToMessage != null) {
                        val replyMsg = uiState.replyingToMessage!!
                        val replySenderName = if (replyMsg.senderId == currentUserId) "You" else (conversation?.participantNames?.get(replyMsg.senderId) ?: "User")
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (themeColor == "dark") Color(0xFF3E4A56) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Replying to $replySenderName", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (themeColor == "dark") Color(0xFF90CAF9) else MaterialTheme.colorScheme.primary)
                                    Text(replyMsg.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (themeColor == "dark") Color.White else MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { viewModel.setReplyingToMessage(null) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel Reply", modifier = Modifier.size(16.dp), tint = if (themeColor == "dark") Color.White else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    if (uiState.isUploading) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Sending media...", fontSize = 12.sp, color = getThemeBottomBarContentColor(themeColor), fontWeight = FontWeight.Bold)
                                Text("${uiState.uploadProgress}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = getThemeBottomBarContentColor(themeColor))
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
                        // Camera Button
                        IconButton(onClick = { mediaPickerLauncher.launch("*/*") }) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📷", fontSize = 22.sp)
                            }
                        }

                        // Microphone Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                viewModel.startRecordingVoice(context)
                                                try {
                                                    tryAwaitRelease()
                                                } finally {
                                                    viewModel.stopAndSendVoiceMessage(context)
                                                }
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎤", fontSize = 20.sp)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        OutlinedTextField(
                            value = uiState.messageInput,
                            onValueChange = { viewModel.updateMessageInput(it) },
                            placeholder = { Text("Message...", color = if (themeColor == "dark") Color.LightGray else Color.Gray) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp, max = 120.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = if (themeColor == "dark") Color(0xFF313D4A) else Color.White,
                                unfocusedContainerColor = if (themeColor == "dark") Color(0xFF313D4A) else Color.White,
                                focusedTextColor = if (themeColor == "dark") Color.White else Color.Black,
                                unfocusedTextColor = if (themeColor == "dark") Color.White else Color.Black,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage() })
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send Button on the right
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

        val reversedMessages = remember(uiState.messages) { uiState.messages.reversed() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(
                    items = reversedMessages,
                    key = { it.messageId }
                ) { msg ->
                    val isMe = (msg.senderId == currentUserId)
                    val isSystem = (msg.messageType == "system")

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
                                .pointerInput(msg) {
                                    detectHorizontalDragGestures { _, dragAmount ->
                                        if (dragAmount > 40) {
                                            viewModel.setReplyingToMessage(msg)
                                        }
                                    }
                                },
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

                            // If media group / album message
                            if (msg.messageType == "media_group" && msg.attachmentUrls.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.width(260.dp)
                                ) {
                                    MediaGroupGrid(
                                        urls = msg.attachmentUrls,
                                        onImageClick = { url ->
                                            val idx = msg.attachmentUrls.indexOf(url).coerceAtLeast(0)
                                            fullScreenImageGallery = Pair(msg.attachmentUrls, idx)
                                        },
                                        onMoreClick = {
                                            activeAlbumGridUrls = msg.attachmentUrls
                                        }
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = DateFormat.format("HH:mm", Calendar.getInstance().apply { timeInMillis = msg.createdAt }).toString(),
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            } else if (msg.messageType == "image" && msg.attachmentUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .width(240.dp)
                                        .height(240.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF1E222A))
                                        .clickable {
                                            val idx = allChatImages.indexOf(msg.attachmentUrl).coerceAtLeast(0)
                                            fullScreenImageGallery = Pair(allChatImages, idx)
                                        }
                                ) {
                                    AsyncImage(
                                        model = msg.attachmentUrl,
                                        contentDescription = "Image attachment",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = DateFormat.format("HH:mm", Calendar.getInstance().apply { timeInMillis = msg.createdAt }).toString(),
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            } else if (msg.messageType == "video" && msg.attachmentUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .width(240.dp)
                                        .height(240.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF1E222A))
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(msg.attachmentUrl)).apply {
                                                setDataAndType(Uri.parse(msg.attachmentUrl), "video/*")
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                ) {
                                    AsyncImage(
                                        model = msg.attachmentUrl,
                                        contentDescription = "Video thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Play button overlay in center
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(48.dp),
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.6f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play Video",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    // Download button overlay at top-right
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(32.dp)
                                            .clickable {
                                                downloadMedia(context, msg.attachmentUrl, true)
                                            },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.6f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("⬇", color = Color.White, fontSize = 14.sp)
                                        }
                                    }
                                    // Timestamp overlay at bottom-right
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = DateFormat.format("HH:mm", Calendar.getInstance().apply { timeInMillis = msg.createdAt }).toString(),
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isMe) bubbleColorMe else bubbleColorOther,
                                    modifier = if (msg.messageType == "audio") Modifier.width(260.dp) else Modifier.widthIn(max = 280.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        if (msg.replyToText.isNotBlank()) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 6.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (themeColor == "dark") Color(0xFF3E4A56) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                            ) {
                                                Column(modifier = Modifier.padding(6.dp)) {
                                                    Text(
                                                        text = "↩ ${msg.replyToSenderName} replied",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (themeColor == "dark") Color(0xFF90CAF9) else MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = msg.replyToText,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (themeColor == "dark") Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        if (msg.messageType == "audio" && msg.attachmentUrl.isNotBlank()) {
                                            VoiceMessagePlayer(audioUrl = msg.attachmentUrl)
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        if (msg.text.isNotBlank() && msg.messageType == "text") {
                                            Text(
                                                text = msg.text,
                                                fontSize = 15.sp,
                                                color = if (themeColor == "dark") Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = DateFormat.format("HH:mm", Calendar.getInstance().apply { timeInMillis = msg.createdAt }).toString(),
                                            fontSize = 10.sp,
                                            color = if (themeColor == "dark") Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.align(Alignment.End)
                                        )
                                    }
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

data class MediaItem(
    val url: String,
    val type: String, // "image", "video", "audio"
    val timestamp: Long = 0L
)

@Composable
fun SharedMediaFolderGallery(
    messages: List<Message>,
    onMediaClick: (String, String) -> Unit
) {
    var selectedFolder by remember { mutableStateOf("all") } // "all", "images", "videos", "audio"
    var viewMode by remember { mutableStateOf("grid") } // "grid", "list", "slide"

    val allMediaItems = remember(messages) {
        messages.flatMap { msg ->
            when (msg.messageType) {
                "media_group" -> msg.attachmentUrls.map { MediaItem(it, "image", msg.createdAt) }
                "image" -> if (msg.attachmentUrl.isNotBlank()) listOf(MediaItem(msg.attachmentUrl, "image", msg.createdAt)) else emptyList()
                "video" -> if (msg.attachmentUrl.isNotBlank()) listOf(MediaItem(msg.attachmentUrl, "video", msg.createdAt)) else emptyList()
                "audio" -> if (msg.attachmentUrl.isNotBlank()) listOf(MediaItem(msg.attachmentUrl, "audio", msg.createdAt)) else emptyList()
                else -> emptyList()
            }
        }.distinctBy { it.url }.reversed()
    }

    val displayedItems = remember(allMediaItems, selectedFolder) {
        when (selectedFolder) {
            "images" -> allMediaItems.filter { it.type == "image" }
            "videos" -> allMediaItems.filter { it.type == "video" }
            "audio" -> allMediaItems.filter { it.type == "audio" }
            else -> allMediaItems
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = when(selectedFolder) { "images" -> 1; "videos" -> 2; "audio" -> 3; else -> 0 },
            edgePadding = 16.dp
        ) {
            Tab(selected = selectedFolder == "all", onClick = { selectedFolder = "all" }, text = { Text("📁 All (${allMediaItems.size})") })
            Tab(selected = selectedFolder == "images", onClick = { selectedFolder = "images" }, text = { Text("📷 Images (${allMediaItems.count { it.type == "image" }})") })
            Tab(selected = selectedFolder == "videos", onClick = { selectedFolder = "videos" }, text = { Text("🎥 Videos (${allMediaItems.count { it.type == "video" }})") })
            Tab(selected = selectedFolder == "audio", onClick = { selectedFolder = "audio" }, text = { Text("🎤 Audio (${allMediaItems.count { it.type == "audio" }})") })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${displayedItems.size} items", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { viewMode = "grid" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewMode == "grid") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (viewMode == "grid") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("Grid", fontSize = 11.sp) }

                Button(
                    onClick = { viewMode = "list" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewMode == "list") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (viewMode == "list") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("List", fontSize = 11.sp) }

                Button(
                    onClick = { viewMode = "slide" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewMode == "slide") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (viewMode == "slide") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("Slide", fontSize = 11.sp) }
            }
        }

        if (displayedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("No media found in this folder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (viewMode) {
                    "grid" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val rows = displayedItems.chunked(3)
                            items(rows) { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (item in rowItems) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1E222A))
                                                .clickable { onMediaClick(item.url, item.type) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.type == "audio") {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(4.dp)
                                                ) {
                                                    Text("🎤", fontSize = 24.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("Audio", fontSize = 10.sp, color = Color.White, maxLines = 1)
                                                }
                                            } else {
                                                AsyncImage(
                                                    model = item.url,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                if (item.type == "video") {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color.Black.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    "list" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayedItems) { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onMediaClick(item.url, item.type) },
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
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1E222A)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.type == "audio") {
                                                Text("🎤", fontSize = 24.sp)
                                            } else {
                                                AsyncImage(
                                                    model = item.url,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                if (item.type == "video") {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color.Black.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = when (item.type) {
                                                    "video" -> "🎥 Video File"
                                                    "audio" -> "🎤 Voice Message / Audio"
                                                    else -> "📷 Photo Image"
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Tap to view / play",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "slide" -> {
                        val pagerState = rememberPagerState { displayedItems.size }
                        val context = LocalContext.current
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                val item = displayedItems[pageIndex]
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.type == "audio") {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                                .padding(16.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(20.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("🎤 Voice Message / Audio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.height(16.dp))
                                                VoiceMessagePlayer(audioUrl = item.url)
                                            }
                                        }
                                    } else if (item.type == "video") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable { onMediaClick(item.url, item.type) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = item.url,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Black.copy(alpha = 0.6f),
                                                modifier = Modifier.size(64.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                                }
                                            }
                                        }
                                    } else {
                                        AsyncImage(
                                            model = item.url,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }

                            // Top overlay with Page Indicator and Download button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .align(Alignment.TopCenter),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Black.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${pagerState.currentPage + 1} / ${displayedItems.size}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }

                                val currentItem = displayedItems.getOrNull(pagerState.currentPage)
                                if (currentItem != null) {
                                    Button(
                                        onClick = {
                                            downloadMedia(context, currentItem.url, currentItem.type == "video")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Download")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoDialog(
    recipientName: String,
    recipientPhoto: String,
    presence: String,
    lastSeen: Long,
    messages: List<Message>,
    onDismiss: () -> Unit,
    onOpenNickname: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onMediaClick: (String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Shared Media, 1: Chat Options

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(recipientName, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
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
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                if (recipientPhoto.isNotBlank()) {
                                    AsyncImage(model = recipientPhoto, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(recipientName.take(1).uppercase(), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = recipientName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(text = formatLastSeen(presence, lastSeen), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Shared Media") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Chat Options") })
                    }

                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        when (selectedTab) {
                            0 -> {
                                SharedMediaFolderGallery(messages = messages, onMediaClick = onMediaClick)
                            }
                            1 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Chat Settings & Options", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenNickname()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("✏️ Edit Nickname")
                                    }

                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenTheme()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("🎨 Change Chat Theme")
                                    }

                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenCreateGroup()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("👥 Create Group with $recipientName")
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

@Composable
fun FullScreenImageItem(imgUrl: String) {
    var scale by remember(imgUrl) { mutableStateOf(1f) }
    var offset by remember(imgUrl) { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(transformState),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imgUrl,
            contentDescription = "Fullscreen Image",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun MediaGroupGrid(urls: List<String>, onImageClick: (String) -> Unit, onMoreClick: () -> Unit) {
    val displayUrls = urls.take(4)
    val extraCount = urls.size - 4

    Surface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(260.dp)
            .height(260.dp),
        color = Color(0xFF1E222A)
    ) {
        if (displayUrls.size == 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onImageClick(displayUrls[0]) }
            ) {
                AsyncImage(
                    model = displayUrls[0],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        } else if (displayUrls.size == 2) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onImageClick(displayUrls[0]) }
                ) {
                    AsyncImage(
                        model = displayUrls[0],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onImageClick(displayUrls[1]) }
                ) {
                    AsyncImage(
                        model = displayUrls[1],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        } else {
            // 3 or 4 items (2x2 grid)
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onImageClick(displayUrls[0]) }
                    ) {
                        AsyncImage(
                            model = displayUrls[0],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onImageClick(displayUrls[1]) }
                    ) {
                        AsyncImage(
                            model = displayUrls[1],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onImageClick(displayUrls[2]) }
                    ) {
                        AsyncImage(
                            model = displayUrls[2],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                if (extraCount > 0) {
                                    onMoreClick()
                                } else {
                                    onImageClick(displayUrls[3])
                                }
                            }
                    ) {
                        if (displayUrls.size > 3) {
                            AsyncImage(
                                model = displayUrls[3],
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (extraCount > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { onMoreClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+$extraCount",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceMessagePlayer(audioUrl: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var totalDuration by remember { mutableStateOf(0) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(audioUrl) {
        try {
            mediaPlayer.setDataSource(audioUrl)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener { mp ->
                totalDuration = mp.duration
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                currentPosition = 0
                try { mediaPlayer.seekTo(0) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        onDispose {
            try {
                mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                currentPosition = mediaPlayer.currentPosition
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(200)
        }
    }

    val displayTime = if (isPlaying || currentPosition > 0) {
        formatMillis(currentPosition)
    } else if (totalDuration > 0) {
        formatMillis(totalDuration)
    } else {
        "0:05"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
    ) {
        IconButton(
            onClick = {
                try {
                    if (isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                    } else {
                        mediaPlayer.start()
                        isPlaying = true
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot play audio: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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

        // Soundwave bars filling the available width evenly
        Row(
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val barHeights = listOf(
                10, 16, 22, 14, 26, 18, 12, 24, 20, 16, 28, 14, 22, 18, 26, 12, 20, 16, 24, 14, 18, 12
            )
            barHeights.forEachIndexed { index, height ->
                val active = (currentPosition.toFloat() / (totalDuration.takeIf { it > 0 } ?: 1)) >= (index.toFloat() / barHeights.size)
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(height.dp)
                        .clip(RoundedCornerShape(1.25.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        }

        Text(
            text = displayTime,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun formatMillis(millis: Int): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumGridDialog(
    urls: List<String>,
    onDismiss: () -> Unit,
    onImageClick: (Int) -> Unit
) {
    var viewMode by remember { mutableStateOf("grid") } // "grid", "list", "slide"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .heightIn(min = 250.dp, max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Media Album (${urls.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            Row(
                                modifier = Modifier.padding(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { viewMode = "grid" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (viewMode == "grid") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (viewMode == "grid") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) { Text("Grid", fontSize = 10.sp) }

                                Button(
                                    onClick = { viewMode = "list" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (viewMode == "list") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (viewMode == "list") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) { Text("List", fontSize = 10.sp) }

                                Button(
                                    onClick = { viewMode = "slide" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (viewMode == "slide") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (viewMode == "slide") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) { Text("Slide", fontSize = 10.sp) }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                }
            ) { padding ->
                when (viewMode) {
                    "grid" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val rows = urls.chunked(3)
                            items(rows.size) { rowIndex ->
                                val rowItems = rows[rowIndex]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for ((colIndex, url) in rowItems.withIndex()) {
                                        val absoluteIndex = rowIndex * 3 + colIndex
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1E222A))
                                                .clickable { onImageClick(absoluteIndex) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = url,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    "list" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(urls.size) { idx ->
                                val url = urls[idx]
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onImageClick(idx) },
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
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1E222A)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = url,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "📷 Photo #${idx + 1}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Tap to view full screen",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "slide" -> {
                        val pagerState = rememberPagerState { urls.size }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                val url = urls[pageIndex]
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onImageClick(pageIndex) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${urls.size}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
