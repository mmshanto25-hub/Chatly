package com.example.chat.ui.chats

import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.chatly.app.R
import com.example.chat.data.local.SavedAccountManager
import com.example.chat.data.model.AppNotification
import com.example.chat.data.model.Conversation
import com.example.chat.data.model.Story
import com.example.chat.data.model.StoryComment
import com.example.chat.data.model.User
import com.example.chat.ui.chats.UserStories
import com.example.chat.ui.conversation.RecipientProfileDialog
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onNavToChat: (conversationId: String, recipientId: String, recipientName: String) -> Unit,
    onNavToSearch: () -> Unit,
    onNavToProfile: () -> Unit,
    onNavToSettings: () -> Unit,
    onNavToLogin: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentUserId = viewModel.currentUserId
    val now = System.currentTimeMillis()
    val storyUserIds = uiState.activeStories.mapTo(HashSet()) { it.userId }
    val onlineUserIds = uiState.usersMap.values
        .filter { it.uid != currentUserId && it.isCurrentlyOnline(now) }
        .mapTo(HashSet()) { it.uid }
    val noteUserIds = uiState.usersMap.values
        .filter {
            it.uid != currentUserId &&
                it.storyNote.isNotBlank() &&
                it.storyNoteExpiresAt > now
        }
        .mapTo(HashSet()) { it.uid }
    val onlineUsersWithoutStories = uiState.usersMap.values
        .filter {
            it.uid !in storyUserIds &&
                (it.uid in onlineUserIds || it.uid in noteUserIds)
        }
        .sortedWith(
            compareByDescending<User> { it.lastSeenTimeMillis() }
                .thenBy { it.displayName.lowercase(Locale.getDefault()) }
        )
        .map { user ->
            UserStories(
                userId = user.uid,
                userName = user.displayName.ifBlank { user.username },
                userPhoto = user.photoUrl,
                stories = emptyList()
            )
        }

    LaunchedEffect(uiState.storyUploadError) {
        uiState.storyUploadError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearStoryUploadError()
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedConversationForMenu by remember { mutableStateOf<Conversation?>(null) }
    var showAccountBottomSheet by remember { mutableStateOf(false) }

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var isRegisteringAccount by remember { mutableStateOf(false) }

    var addEmailInput by remember { mutableStateOf("") }
    var addPasswordInput by remember { mutableStateOf("") }

    var regFullNameInput by remember { mutableStateOf("") }
    var regEmailInput by remember { mutableStateOf("") }
    var regPasswordInput by remember { mutableStateOf("") }
    var regConfirmPasswordInput by remember { mutableStateOf("") }

    var activeViewingAllStories by remember { mutableStateOf<List<UserStories>?>(null) }
    var activeViewingUserIndex by remember { mutableStateOf(0) }
    var activeViewingStoryIndex by remember { mutableStateOf(0) }
    var openStoryCommentsOnLaunch by remember { mutableStateOf(false) }
    var notificationCommentId by remember { mutableStateOf("") }
    var storyForViewers by remember { mutableStateOf<Story?>(null) }
    var profileUserId by remember { mutableStateOf("") }
    var profileUserName by remember { mutableStateOf("") }
    var showUserProfile by remember { mutableStateOf(false) }

    LaunchedEffect(showNotifications) {
        if (showNotifications) {
            viewModel.markAllNotificationsRead()
        }
    }

    fun closeNotifications() {
        showNotifications = false
        viewModel.markAllNotificationsRead()
    }

    fun openUserProfile(userId: String, userName: String) {
        if (userId.isBlank()) return
        if (userId == currentUserId) {
            onNavToProfile()
        } else {
            profileUserId = userId
            profileUserName = userName
            showUserProfile = true
        }
    }

    val storyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadStory(context, uri)
            Toast.makeText(context, "Uploading story...", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.setSearchQuery(it)
                            },
                            placeholder = { Text("Search conversations...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.chat_logo),
                                contentDescription = "Talky Logo",
                                modifier = Modifier.size(36.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = "Talky",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearching = !isSearching
                        if (!isSearching) {
                            searchQuery = ""
                            viewModel.setSearchQuery("")
                        }
                    }) {
                        Icon(
                            imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    BadgedBox(
                        badge = {
                            val unreadCount = uiState.notifications.count { !it.isRead }
                            if (unreadCount > 0) {
                                Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = {
                            showNotifications = true
                            viewModel.markAllNotificationsRead()
                        }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }

                    IconButton(onClick = onNavToProfile) {
                        Surface(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            if (uiState.currentUser?.photoUrl?.isNotBlank() == true) {
                                AsyncImage(
                                    model = uiState.currentUser?.photoUrl,
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showMenu = false
                                onNavToSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Switch Account") },
                            onClick = {
                                showMenu = false
                                showAccountBottomSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (uiState.showArchived) "Hide Archived Chats" else "Archived Chats") },
                            onClick = {
                                showMenu = false
                                viewModel.toggleShowArchived()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            onClick = {
                                showMenu = false
                                viewModel.logout {
                                    onNavToLogin()
                                }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavToSearch,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Telegram Style Stories Row
            StoriesRow(
                myPhotoUrl = uiState.currentUser?.photoUrl ?: "",
                myStories = uiState.myStories,
                myStoryNote = uiState.usersMap[currentUserId]?.storyNote
                    ?: uiState.currentUser?.storyNote.orEmpty(),
                myStoryNoteExpiresAt = uiState.usersMap[currentUserId]?.storyNoteExpiresAt
                    ?: uiState.currentUser?.storyNoteExpiresAt
                    ?: 0L,
                myUser = uiState.usersMap[currentUserId] ?: uiState.currentUser,
                users = uiState.usersMap.values.toList(),
                usersMap = uiState.usersMap,
                currentUserId = currentUserId,
                activeStories = uiState.activeStories,
                onlineUsersWithoutStories = onlineUsersWithoutStories,
                onlineUserIds = onlineUserIds,
                onSaveMyNote = { note ->
                    viewModel.setStoryNote(note) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
                onAddStoryClick = {
                    storyPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onStoryClick = { userIdx, storyIdx ->
                    val combined = mutableListOf<UserStories>()
                    if (uiState.myStories.isNotEmpty()) {
                        combined.add(
                            UserStories(
                                userId = currentUserId,
                                userName = uiState.currentUser?.displayName ?: "My Story",
                                userPhoto = uiState.currentUser?.photoUrl ?: "",
                                stories = uiState.myStories
                            )
                        )
                    }
                    combined.addAll(uiState.activeStories)
                    activeViewingAllStories = combined
                    activeViewingUserIndex = userIdx
                    activeViewingStoryIndex = storyIdx
                },
                onReplyToNote = { recipientUser, noteText, replyMessage ->
                    viewModel.replyToStoryNote(
                        recipientUser = recipientUser,
                        noteText = noteText,
                        replyMessage = replyMessage,
                        onError = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                onOpenConversation = { recipientUser ->
                    viewModel.openConversationWithUser(
                        recipientUser = recipientUser,
                        onConversationReady = { conversationId ->
                            val name = recipientUser.displayName.takeIf { it.isNotBlank() }
                                ?: recipientUser.username.takeIf { it.isNotBlank() }
                                ?: "User"
                            onNavToChat(conversationId, recipientUser.uid, name)
                        },
                        onError = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                onToggleNoteLove = { targetUserId, noteText ->
                    viewModel.toggleStoryNoteLove(targetUserId, noteText)
                },
                onRecordNoteView = { targetUserId ->
                    viewModel.recordStoryNoteView(targetUserId)
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

            // Filters Row
            val filters = listOf("All", "Personal", "Groups")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = {
                            selectedFilter = filter
                            viewModel.setFilter(filter)
                        },
                        label = { Text(filter) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredConversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.showArchived) "No Archived Chats" else "No Conversations Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.showArchived) "Archived conversations will appear here." else "Tap the search button or FAB to start chatting.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.filteredConversations,
                        key = { it.conversationId },
                        contentType = { "conversation_item" }
                    ) { conversation ->
                        val recipientId = conversation.participantIds.firstOrNull { it != currentUserId } ?: ""
                        val title = conversation.getTitle(currentUserId)

                        ConversationItem(
                            conversation = conversation,
                            recipientId = recipientId,
                            recipientName = title,
                            currentUserId = currentUserId,
                            recipientUser = uiState.usersMap[recipientId],
                            onClick = {
                                onNavToChat(conversation.conversationId, recipientId, title)
                            },
                            onLongClick = {
                                selectedConversationForMenu = conversation
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    if (activeViewingAllStories != null) {
        FullStoryViewerDialog(
            allUserStories = activeViewingAllStories!!,
            initialUserIndex = activeViewingUserIndex,
            initialStoryIndex = activeViewingStoryIndex,
            viewerId = currentUserId,
            availableUsers = uiState.usersMap.values.toList(),
            openCommentsOnStart = openStoryCommentsOnLaunch,
            focusCommentId = notificationCommentId,
            onMarkViewed = viewModel::markStoryViewed,
            onToggleLove = { story ->
                viewModel.toggleStoryLove(story) { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
            onAddComment = { story, text, parentCommentId, replyToCommentId, mentionIds, onSuccess ->
                viewModel.addStoryComment(
                    story = story,
                    text = text,
                    parentCommentId = parentCommentId,
                    replyToCommentId = replyToCommentId,
                    mentionIds = mentionIds,
                    onSuccess = onSuccess,
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onToggleCommentLove = { storyId, comment ->
                viewModel.toggleCommentLove(storyId, comment) { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
            onSendMessage = { story, text ->
                viewModel.sendStoryMessage(
                    story = story,
                    text = text,
                    onConversationReady = { conversationId ->
                        activeViewingAllStories = null
                        onNavToChat(conversationId, story.userId, story.userName)
                    },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onOpenInsights = { story -> storyForViewers = story },
            onOpenUserProfile = ::openUserProfile,
            onOpenConversation = { story ->
                val recipient = uiState.usersMap[story.userId] ?: User(
                    uid = story.userId,
                    displayName = story.userName,
                    photoUrl = story.userPhoto
                )
                viewModel.openConversationWithUser(
                    recipientUser = recipient,
                    onConversationReady = { conversationId ->
                        activeViewingAllStories = null
                        onNavToChat(conversationId, story.userId, story.userName)
                    },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDeleteStory = { story, keepInHighlights, onSuccess ->
                viewModel.deleteStory(
                    story = story,
                    keepInHighlights = keepInHighlights,
                    onSuccess = {
                        onSuccess()
                    },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = {
                activeViewingAllStories = null
                openStoryCommentsOnLaunch = false
                notificationCommentId = ""
            }
        )
    }

    RecipientProfileDialog(
        isVisible = showUserProfile,
        recipientId = profileUserId,
        recipientName = profileUserName,
        currentUserId = currentUserId,
        chatListViewModel = viewModel,
        onNavToChat = onNavToChat,
        onDismiss = { showUserProfile = false }
    )

    storyForViewers?.let { story ->
        StoryViewersBottomSheet(
            story = story,
            users = uiState.usersMap,
            onDismiss = { storyForViewers = null }
        )
    }

    if (showNotifications) {
        NotificationsDialog(
            notifications = uiState.notifications,
            error = uiState.notificationError,
            onDismiss = ::closeNotifications,
            onOpenNotification = { notification ->
                viewModel.markNotificationAsRead(notification.notificationId)
                when {
                    notification.storyId.isNotBlank() -> {
                        viewModel.loadStoryForNotification(
                            notification = notification,
                            onLoaded = { storyGroup ->
                                closeNotifications()
                                activeViewingAllStories = listOf(storyGroup)
                                activeViewingUserIndex = 0
                                activeViewingStoryIndex = 0
                                openStoryCommentsOnLaunch = true
                                notificationCommentId = notification.commentId
                            },
                            onError = { message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                    notification.conversationId.isNotBlank() -> {
                        closeNotifications()
                        onNavToChat(
                            notification.conversationId,
                            notification.actorId,
                            notification.actorName
                        )
                    }
                }
            }
        )
    }

    if (selectedConversationForMenu != null) {
        val conv = selectedConversationForMenu!!
        val isPinned = conv.isPinnedFor(currentUserId)
        val isArchived = conv.isArchivedFor(currentUserId)
        val isMuted = conv.isMutedFor(currentUserId)

        AlertDialog(
            onDismissRequest = { selectedConversationForMenu = null },
            title = { Text("Conversation Options") },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.togglePin(conv.conversationId, isPinned)
                        selectedConversationForMenu = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isPinned) "Unpin Conversation" else "Pin Conversation", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    TextButton(onClick = {
                        viewModel.toggleArchive(conv.conversationId, isArchived)
                        selectedConversationForMenu = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isArchived) "Unarchive Conversation" else "Archive Conversation", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    TextButton(onClick = {
                        viewModel.toggleMute(conv.conversationId, isMuted)
                        selectedConversationForMenu = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isMuted) "Unmute Notifications" else "Mute Notifications", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    TextButton(onClick = {
                        viewModel.deleteConversationForUser(conv.conversationId)
                        selectedConversationForMenu = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete Conversation", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedConversationForMenu = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAccountBottomSheet) {
        AccountSwitcherBottomSheet(
            onDismiss = { showAccountBottomSheet = false },
            onSwitchAccount = { account ->
                viewModel.switchAccount(
                    context,
                    account.email,
                    account.password,
                    onSwitched = { onSwitchAccount() },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                )
            },
            onAddNewAccount = {
                showAccountBottomSheet = false
                isRegisteringAccount = false
                addEmailInput = ""
                addPasswordInput = ""
                showAddAccountDialog = true
            },
            currentEmail = uiState.currentUser?.email ?: ""
        )
    }

    // Add New Account / Create New Account Dialogs
    if (showAddAccountDialog) {
        if (!isRegisteringAccount) {
            AlertDialog(
                onDismissRequest = { showAddAccountDialog = false },
                shape = RoundedCornerShape(24.dp),
                title = {
                    Column {
                        Text("Add New Account", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Enter credentials for the new account:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = addEmailInput,
                            onValueChange = { addEmailInput = it },
                            label = { Text("Email or Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = addPasswordInput,
                            onValueChange = { addPasswordInput = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Don't have an account? Register here",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable {
                                    isRegisteringAccount = true
                                    regFullNameInput = ""
                                    regEmailInput = addEmailInput
                                    regPasswordInput = addPasswordInput
                                    regConfirmPasswordInput = ""
                                }
                                .padding(vertical = 4.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (addEmailInput.isBlank() || addPasswordInput.isBlank()) {
                                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.switchAccount(
                                context,
                                addEmailInput,
                                addPasswordInput,
                                onSwitched = {
                                    showAddAccountDialog = false
                                    onSwitchAccount()
                                },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Log In & Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAccountDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showAddAccountDialog = false },
                shape = RoundedCornerShape(24.dp),
                title = {
                    Column {
                        Text("Create New Account", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Enter details to register a new account:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = regFullNameInput,
                            onValueChange = { regFullNameInput = it },
                            label = { Text("Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = regEmailInput,
                            onValueChange = { regEmailInput = it },
                            label = { Text("Email or Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = regPasswordInput,
                            onValueChange = { regPasswordInput = it },
                            label = { Text("Password (min 6 chars)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = regConfirmPasswordInput,
                            onValueChange = { regConfirmPasswordInput = it },
                            label = { Text("Confirm Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (regFullNameInput.isBlank() || regEmailInput.isBlank() || regPasswordInput.isBlank()) {
                                Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (regPasswordInput != regConfirmPasswordInput) {
                                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.registerNewAccountAndSwitch(
                                context,
                                regFullNameInput,
                                regEmailInput,
                                regPasswordInput,
                                onSwitched = {
                                    showAddAccountDialog = false
                                    onSwitchAccount()
                                },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Register & Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAccountDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
