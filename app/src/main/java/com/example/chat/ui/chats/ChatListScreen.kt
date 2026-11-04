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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.chat.data.model.Conversation
import com.example.chat.data.model.Story
import com.example.chat.data.model.User
import com.example.chat.ui.chats.UserStories
import com.google.firebase.Timestamp
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

    var showMenu by remember { mutableStateOf(false) }
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
                activeStories = uiState.activeStories,
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
            onDismiss = { activeViewingAllStories = null }
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

@Composable
fun StoriesRow(
    myPhotoUrl: String,
    myStories: List<Story>,
    activeStories: List<UserStories>,
    onAddStoryClick: () -> Unit,
    onStoryClick: (userIndex: Int, storyIndex: Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "My Story" Item with + badge
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    if (myStories.isNotEmpty()) {
                        onStoryClick(0, 0)
                    } else {
                        onAddStoryClick()
                    }
                }
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = if (myStories.isNotEmpty()) BorderStroke(2.5.dp, Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF0284C7)))) else null
                    ) {
                        if (myPhotoUrl.isNotBlank()) {
                            AsyncImage(
                                model = myPhotoUrl,
                                contentDescription = "My Story",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text("👤", fontSize = 24.sp)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onAddStoryClick() },
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Story",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "My Story",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Active Stories from Other Users
        items(activeStories.size) { index ->
            val userStory = activeStories[index]
            val userIndex = if (myStories.isNotEmpty()) index + 1 else index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onStoryClick(userIndex, 0) }
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .border(
                            BorderStroke(
                                2.5.dp,
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF22C55E),
                                        Color(0xFF0284C7),
                                        Color(0xFF3B82F6)
                                    )
                                )
                            ),
                            CircleShape
                        )
                        .padding(3.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        if (userStory.userPhoto.isNotBlank()) {
                            AsyncImage(
                                model = userStory.userPhoto,
                                contentDescription = userStory.userName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = userStory.userName.take(1).uppercase(Locale.getDefault()),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userStory.userName.substringBefore(" "),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FullStoryViewerDialog(
    allUserStories: List<UserStories>,
    initialUserIndex: Int = 0,
    initialStoryIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (allUserStories.isEmpty()) return
    var currentUserIndex by remember { mutableStateOf(initialUserIndex.coerceIn(0, allUserStories.size - 1)) }
    val currentUserStories = allUserStories.getOrNull(currentUserIndex) ?: allUserStories.first()

    var currentStoryIndex by remember { mutableStateOf(initialStoryIndex.coerceIn(0, currentUserStories.stories.size - 1)) }
    val currentStory = currentUserStories.stories.getOrNull(currentStoryIndex) ?: currentUserStories.stories.first()

    LaunchedEffect(currentUserIndex, currentStoryIndex) {
        delay(5000)
        if (currentStoryIndex < currentUserStories.stories.size - 1) {
            currentStoryIndex++
        } else {
            if (currentUserIndex < allUserStories.size - 1) {
                currentUserIndex++
                currentStoryIndex = 0
            } else {
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val isVideo = currentStory.mediaType.equals("video", ignoreCase = true) ||
                        currentStory.mediaUrl.lowercase().contains(".mp4") ||
                        currentStory.mediaUrl.lowercase().contains("video") ||
                        currentStory.mediaUrl.lowercase().contains("mov") ||
                        currentStory.mediaUrl.lowercase().contains("webm")

                if (isVideo) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setVideoURI(Uri.parse(currentStory.mediaUrl))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = currentStory.mediaUrl,
                        contentDescription = "Story Media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Left/Right Touch navigation
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                if (currentStoryIndex > 0) {
                                    currentStoryIndex--
                                } else if (currentUserIndex > 0) {
                                    currentUserIndex--
                                    currentStoryIndex = allUserStories[currentUserIndex].stories.size - 1
                                }
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                if (currentStoryIndex < currentUserStories.stories.size - 1) {
                                    currentStoryIndex++
                                } else if (currentUserIndex < allUserStories.size - 1) {
                                    currentUserIndex++
                                    currentStoryIndex = 0
                                } else {
                                    onDismiss()
                                }
                            }
                    )
                }

                // Top Progress Bars & User Info (Rendered above touch layer so close button gets clicks)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        currentUserStories.stories.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (index <= currentStoryIndex) Color.White else Color.White.copy(alpha = 0.3f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                if (currentUserStories.userPhoto.isNotBlank()) {
                                    AsyncImage(
                                        model = currentUserStories.userPhoto,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(currentUserStories.userName.take(1).uppercase(Locale.getDefault()), fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = currentUserStories.userName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                if (currentStory.caption.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(24.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = currentStory.caption,
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    recipientId: String,
    recipientName: String,
    currentUserId: String,
    recipientUser: User?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val formattedTime = remember(conversation.lastMessageAt) {
        val calendar = Calendar.getInstance().apply { timeInMillis = conversation.lastMessageAt }
        val now = Calendar.getInstance()
        if (calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
            calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
            DateFormat.format("HH:mm", calendar).toString()
        } else {
            DateFormat.format("MMM dd", calendar).toString()
        }
    }

    val photoUrl = if (conversation.type == "group") {
        conversation.groupPhoto.ifBlank { conversation.participantPhotos["groupPhoto"] ?: "" }
    } else {
        conversation.participantPhotos[recipientId] ?: ""
    }
    val isPinned = conversation.isPinnedFor(currentUserId)
    val isMuted = conversation.isMutedFor(currentUserId)

    val timeMs = when (recipientUser?.lastSeenAt) {
        is Timestamp -> recipientUser.lastSeenAt.toDate().time
        is java.util.Date -> recipientUser.lastSeenAt.time
        is Number -> recipientUser.lastSeenAt.toLong()
        else -> 0L
    }
    val isRecentlyActive = timeMs > 0L && (System.currentTimeMillis() - timeMs < 90_000L)
    val isOnline = (recipientUser?.isOnline == true) || isRecentlyActive

    val unreadCount = conversation.getUnreadCount(currentUserId)
    val hasUnread = unreadCount > 0 && conversation.lastMessageSenderId != currentUserId

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (conversation.type == "group") {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "👥",
                            fontSize = 22.sp
                        )
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = recipientName.take(1).uppercase(Locale.getDefault()),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (conversation.type != "group" && recipientId.isNotBlank()) {
                val shortText = if (isOnline) "" else formatShortLastSeen(recipientUser?.lastSeenAt)
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.surface), CircleShape)
                    )
                } else if (shortText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = shortText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1.8f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = recipientName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPinned) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("📌", fontSize = 12.sp)
                }
                if (isMuted) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "🔕", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = conversation.lastMessage.ifBlank { "Started a conversation" },
                fontSize = 14.sp,
                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier.weight(0.8f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formattedTime,
                fontSize = 12.sp,
                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasUnread) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

fun formatShortLastSeen(timestamp: Any?): String {
    val date = when (timestamp) {
        is Timestamp -> timestamp.toDate()
        is java.util.Date -> timestamp
        is Number -> java.util.Date(timestamp.toLong())
        else -> null
    }
    if (date == null) return ""

    val timeMs = date.time
    val now = System.currentTimeMillis()
    val diff = now - timeMs
    if (diff < 0L) return "now"

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> {
            val sdf = java.text.SimpleDateFormat("d/M", Locale.getDefault())
            sdf.format(date)
        }
    }
}

@Composable
fun MessageStatusTicks(
    status: String,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    val normalizedStatus = status.lowercase(Locale.getDefault())
    when (normalizedStatus) {
        "seen", "read" -> {
            Text(
                text = "✓✓",
                fontSize = (size.value * 0.9).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0284C7),
                modifier = modifier
            )
        }
        "delivered" -> {
            Text(
                text = "✓✓",
                fontSize = (size.value * 0.9).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = modifier
            )
        }
        else -> {
            Text(
                text = "✓",
                fontSize = (size.value * 0.9).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = modifier
            )
        }
    }
}
