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
fun ConversationScreen(
    viewModel: ConversationViewModel,
    chatListViewModel: ChatListViewModel,
    recipientName: String,
    onNavBack: () -> Unit,
    onNavToChat: (conversationId: String, recipientId: String, recipientName: String) -> Unit,
    onNavToCall: ((callType: String) -> Unit)? = null
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
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showRecipientProfileDialog by remember { mutableStateOf(false) }
    var profileRecipientId by remember { mutableStateOf("") }
    var profileRecipientName by remember { mutableStateOf("") }
    var showGroupDetailsDialog by remember { mutableStateOf(false) }
    var showChatInfoDialog by remember { mutableStateOf(false) }
    var currentUserName by remember { mutableStateOf("User") }
    var availableUsersForGroup by remember { mutableStateOf<List<User>>(emptyList()) }
    var isFetchingUsers by remember { mutableStateOf(false) }

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
            onDismiss = { activeAlbumGridUrls = null }
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
                            onNavToChat(newGroupConvId, "group", finalGroupName)
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

    GroupDetailsDialog(
        isVisible = showGroupDetailsDialog,
        conversation = conversation,
        displayName = displayName,
        currentUserId = currentUserId,
        currentUserName = currentUserName,
        uiState = uiState,
        viewModel = viewModel,
        availableUsersForGroup = availableUsersForGroup,
        isFetchingUsers = isFetchingUsers,
        allChatImages = allChatImages,
        onFetchAvailableUsers = { excludedUserIds ->
            isFetchingUsers = true
            viewModel.fetchAvailableUsers { users ->
                availableUsersForGroup = users.filter { it.uid !in excludedUserIds }
                isFetchingUsers = false
            }
        },
        onOpenImageGallery = { fullScreenImageGallery = it },
        onDismiss = { showGroupDetailsDialog = false }
    )

    RecipientProfileDialog(
        isVisible = showRecipientProfileDialog,
        recipientId = profileRecipientId,
        recipientName = profileRecipientName,
        currentUserId = currentUserId,
        chatListViewModel = chatListViewModel,
        onNavToChat = onNavToChat,
        onDismiss = { showRecipientProfileDialog = false }
    )

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
                                profileRecipientId = recipientId
                                profileRecipientName = displayName
                                showRecipientProfileDialog = true
                            }
                        }
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
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

                            if (conversation?.type != "group" && recipientId.isNotBlank()) {
                                val lastSeen = uiState.recipientLastSeenAt
                                val timeMs = when (lastSeen) {
                                    is Timestamp -> lastSeen.toDate().time
                                    is java.util.Date -> lastSeen.time
                                    is Number -> lastSeen.toLong()
                                    else -> 0L
                                }
                                val isRecentlyActive = timeMs > 0L && (System.currentTimeMillis() - timeMs < 90_000L)
                                val isOnline = uiState.recipientIsOnline || isRecentlyActive
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) Color(0xFF22C55E) else Color(0xFF9CA3AF))
                                        .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface), CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val textColor = getThemeTopAppBarContentColor(themeColor)
                            Text(text = displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                            if (conversation?.type == "group") {
                                Text(text = "${conversation.participantIds.size} members", fontSize = 12.sp, color = textColor.copy(alpha = 0.7f))
                            } else {
                                val lastSeen = uiState.recipientLastSeenAt
                                val timeMs = when (lastSeen) {
                                    is Timestamp -> lastSeen.toDate().time
                                    is java.util.Date -> lastSeen.time
                                    is Number -> lastSeen.toLong()
                                    else -> 0L
                                }
                                val isRecentlyActive = timeMs > 0L && (System.currentTimeMillis() - timeMs < 90_000L)
                                val isOnline = uiState.recipientIsOnline || isRecentlyActive
                                val statusText = if (isOnline) "Online" else formatLastSeen(lastSeen)
                                val statusColor = if (isOnline) Color(0xFF22C55E) else textColor.copy(alpha = 0.7f)
                                Text(text = statusText, fontSize = 11.sp, color = statusColor)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = getThemeTopAppBarContentColor(themeColor))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onNavToCall?.invoke("video")
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = getThemeTopAppBarContentColor(themeColor))
                    }
                    IconButton(onClick = {
                        onNavToCall?.invoke("audio")
                    }) {
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
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    ) { padding ->
        ConversationMessageList(
            uiState = uiState,
            listState = listState,
            padding = padding,
            currentUserId = currentUserId,
            recipientId = recipientId,
            themeColor = themeColor,
            allChatImages = allChatImages,
            context = context,
            onReplyToMessage = viewModel::setReplyingToMessage,
            onReactToMessage = viewModel::reactToMessage,
            onOpenImageGallery = { fullScreenImageGallery = it },
            onOpenAlbum = { activeAlbumGridUrls = it },
            onOpenUserProfile = { userId, userName ->
                if (userId.isNotBlank()) {
                    profileRecipientId = userId
                    profileRecipientName = userName
                    showRecipientProfileDialog = true
                }
            }
        )
    }
}
