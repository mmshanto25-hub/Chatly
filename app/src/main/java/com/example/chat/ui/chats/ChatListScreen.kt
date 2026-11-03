package com.example.chat.ui.chats

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat.data.local.SavedAccount
import com.example.chat.data.local.SavedAccountManager
import com.example.chat.data.model.Conversation
import com.example.chat.data.model.User
import com.google.firebase.Timestamp
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
                        Text(
                            text = "Talky",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

    // Add New Account / Create New Account Dialogs (Screenshots 2 & 3)
    if (showAddAccountDialog) {
        if (!isRegisteringAccount) {
            // Screenshot 2: Add New Account (Log In)
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
            // Screenshot 3: Create New Account (Register)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherBottomSheet(
    onDismiss: () -> Unit,
    onSwitchAccount: (SavedAccount) -> Unit,
    onAddNewAccount: () -> Unit,
    currentEmail: String
) {
    val context = LocalContext.current
    val savedAccounts = SavedAccountManager.getSavedAccounts(context)
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Switch Account",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 350.dp)
            ) {
                items(savedAccounts) { account ->
                    val isActive = account.email.equals(currentEmail, ignoreCase = true)
                    Surface(
                        onClick = {
                            onDismiss()
                            onSwitchAccount(account)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                if (account.photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = account.photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = account.displayName.take(1).uppercase(Locale.getDefault()),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = account.email,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.error
                                ) {
                                    Text(
                                        text = "Active",
                                        color = MaterialTheme.colorScheme.onError,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Add New Account Button
                item {
                    Surface(
                        onClick = {
                            onDismiss()
                            onAddNewAccount()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = "Add New Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
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
    when {
        normalizedStatus == "seen" || normalizedStatus == "read" -> {
            Text(
                text = "✓✓",
                fontSize = (size.value * 0.9).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0284C7),
                modifier = modifier
            )
        }
        normalizedStatus == "delivered" -> {
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
