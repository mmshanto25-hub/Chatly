package com.example.chat.ui.conversation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.chat.data.model.Message
import com.example.chat.data.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val clipboardManager = LocalClipboardManager.current
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
    var availableUsersForGroup by remember { mutableStateOf<List<User>>(emptyList()) }
    var isFetchingUsers by remember { mutableStateOf(false) }
    var selectedMessageForMenu by remember { mutableStateOf<Message?>(null) }

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
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachmentOption(icon = "🖼️", label = "Photo") {
                        showBottomSheet = false
                        imagePickerLauncher.launch("image/*")
                    }
                    AttachmentOption(icon = "📹", label = "Video") {
                        showBottomSheet = false
                        videoPickerLauncher.launch("video/*")
                    }
                    AttachmentOption(icon = "🎤", label = "Voice") {
                        showBottomSheet = false
                        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            showVoiceRecorder = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showVoiceRecorder) {
        VoiceRecorderDialog(
            onDismiss = { showVoiceRecorder = false },
            onSendRecording = { audioUri ->
                viewModel.sendMediaMessage(context, audioUri, "audio")
            }
        )
    }

    // Full Screen Image Viewer Dialog
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

    // Edit Nickname Dialog
    if (showNicknameDialog && recipientId.isNotBlank()) {
        var nicknameInput by remember { mutableStateOf(customNickname.ifBlank { recipientName }) }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("Edit Nickname", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = { nicknameInput = it },
                    label = { Text("Nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateNickname(recipientId, nicknameInput)
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

    // Create Group Dialog
    if (showCreateGroupDialog) {
        var groupNameInput by remember { mutableStateOf("Group with $displayName") }
        val selectedUserIds = remember { mutableStateListOf<String>() }

        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create Group", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
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
                        viewModel.createGroup(gName, selectedUserIds.toList()) { newGroupConvId, finalGroupName ->
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
                    if (msg.text.isNotBlank()) {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(msg.text))
                                selectedMessageForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📋 Copy Text", fontSize = 16.sp)
                        }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            if (uiState.recipientPhoto.isNotBlank()) {
                                AsyncImage(
                                    model = uiState.recipientPhoto,
                                    contentDescription = "Recipient Profile Picture",
                                    modifier = Modifier.fillMaxSize()
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
                            val statusText = formatLastSeen(uiState.recipientPresence, uiState.recipientLastSeen)
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
                Column {
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
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showBottomSheet = true }
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Attach Media",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = uiState.messageInput,
                            onValueChange = { viewModel.updateMessageInput(it) },
                            placeholder = { Text("Message...") },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { viewModel.sendMessage() }
                            )
                        )
                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(getThemeBackgroundColor(themeColor))
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            val pinnedMsgText = uiState.conversation?.pinnedMessageText ?: ""
            val pinnedMsgId = uiState.conversation?.pinnedMessageId ?: ""

            if (pinnedMsgText.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (pinnedMsgId.isNotBlank()) {
                                val targetIndex = uiState.messages.indexOfFirst { it.messageId == pinnedMsgId }
                                if (targetIndex >= 0) {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(targetIndex)
                                    }
                                }
                            }
                        },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📌", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Pinned Message", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = pinnedMsgText,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (pinnedMsgId.isNotBlank()) {
                                    viewModel.togglePinMessage(pinnedMsgId, pinnedMsgText, true)
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Unpin", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(32.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No messages here yet...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Send a message or greeting below to start conversation.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(uiState.messages) { message ->
                            val isMe = message.senderId == currentUserId
                            MessageBubble(
                                message = message,
                                isMe = isMe,
                                themeColor = themeColor,
                                onImageClick = { url -> fullScreenImageUrl = url },
                                onVideoClick = { videoUrl ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                                            setDataAndType(Uri.parse(videoUrl), "video/*")
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                },
                                onLongClick = {
                                    selectedMessageForMenu = message
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceRecorderDialog(
    onDismiss: () -> Unit,
    onSendRecording: (Uri) -> Unit
) {
    val context = LocalContext.current
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            audioFile = file
            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
        } catch (_: Exception) {
            onDismiss()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                delay(1000)
                secondsElapsed++
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) {}
            onDismiss()
        },
        title = { Text("Recording Voice Message...", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", secondsElapsed / 60, secondsElapsed % 60),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        mediaRecorder?.stop()
                        mediaRecorder?.release()
                    } catch (_: Exception) {}
                    isRecording = false
                    if (audioFile != null && audioFile!!.exists() && audioFile!!.length() > 0L) {
                        onSendRecording(Uri.fromFile(audioFile))
                    }
                    onDismiss()
                }
            ) {
                Text("Send Voice")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    try {
                        mediaRecorder?.stop()
                        mediaRecorder?.release()
                    } catch (_: Exception) {}
                    isRecording = false
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AttachmentOption(icon: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = icon, fontSize = 24.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun VoiceMessagePlayer(audioUrl: String, textColor: Color) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    DisposableEffect(audioUrl) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying && mediaPlayer != null) {
            while (isPlaying && mediaPlayer != null) {
                try {
                    val current = mediaPlayer?.currentPosition ?: 0
                    val total = mediaPlayer?.duration ?: 1
                    if (total > 0) {
                        progress = current.toFloat() / total.toFloat()
                    }
                } catch (_: Exception) {}
                delay(100)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp).width(200.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer?.pause()
                    isPlaying = false
                } else {
                    if (mediaPlayer == null) {
                        try {
                            mediaPlayer = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .build()
                                )
                                setDataSource(context, Uri.parse(audioUrl))
                                prepareAsync()
                                setOnPreparedListener {
                                    it.start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    progress = 0f
                                    try { seekTo(0) } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {
                            isPlaying = false
                        }
                    } else {
                        mediaPlayer?.start()
                        isPlaying = true
                    }
                }
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = textColor
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isPlaying) "Playing voice..." else "🎵 Voice Message",
                fontSize = 11.sp,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    themeColor: String,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val bubbleColor = getThemeBubbleColor(themeColor, isMe)
    val textColor = if (themeColor == "dark") Color.White else Color.DarkGray
    val alignment = if (isMe) Alignment.End else Alignment.Start

    val timeString = remember(message.createdAt) {
        val calendar = Calendar.getInstance().apply { timeInMillis = message.createdAt }
        DateFormat.format("HH:mm", calendar).toString()
    }

    val isMedia = message.messageType == "image" || message.messageType == "video"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
                .padding(if (isMedia) 4.dp else 10.dp)
                .widthIn(max = 260.dp)
        ) {
            Column {
                if (message.isPinned) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text("📌", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Pinned",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }

                if (message.messageType == "image" && message.attachmentUrl.isNotBlank()) {
                    AsyncImage(
                        model = message.attachmentUrl,
                        contentDescription = "Shared Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageClick(message.attachmentUrl) },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (message.messageType == "video" && message.attachmentUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onVideoClick(message.attachmentUrl) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (message.messageType == "audio" && message.attachmentUrl.isNotBlank()) {
                    VoiceMessagePlayer(audioUrl = message.attachmentUrl, textColor = textColor)
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (message.messageType == "document" && message.attachmentUrl.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text("📄", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Document File", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (!isMedia && message.messageType != "audio" || (message.text.isNotBlank() && message.text != "📷 Photo" && message.text != "📹 Video" && message.text != "🎤 Voice message")) {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = timeString,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 4.dp)
                )
            }
        }
    }
}
