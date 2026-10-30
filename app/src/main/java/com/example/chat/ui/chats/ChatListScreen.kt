package com.example.chat.ui.chats

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.chatly.app.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat.data.local.SavedAccount
import com.example.chat.data.local.SavedAccountManager
import com.example.chat.data.model.Conversation
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
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId = viewModel.currentUserId
    var showMenu by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var showAccountSwitcher by remember { mutableStateOf(false) }

    var selectedConversationForMenu by remember { mutableStateOf<Conversation?>(null) }
    var accountToSwitch by remember { mutableStateOf<SavedAccount?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var switchError by remember { mutableStateOf<String?>(null) }
    var isSwitching by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search conversations...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!uiState.showArchived) {
                                Image(
                                    painter = painterResource(id = R.drawable.chat_logo),
                                    contentDescription = "Talky Logo",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                if (uiState.showArchived) "Archived Chats" else "Talky",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.showArchived) {
                        IconButton(onClick = { viewModel.toggleShowArchived() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Inbox")
                        }
                    }
                },
                actions = {
                    if (isSearching) {
                        IconButton(onClick = {
                            isSearching = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search")
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Chats")
                        }
                        val userPhoto = uiState.currentUser?.photoUrl ?: ""
                        IconButton(onClick = onNavToProfile) {
                            if (userPhoto.isNotBlank()) {
                                AsyncImage(
                                    model = userPhoto,
                                    contentDescription = "Profile",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = "Profile")
                            }
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (uiState.showArchived) "Back to Inbox" else "Archived Chats") },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleShowArchived()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showMenu = false
                                    onNavToSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                onClick = {
                                    showMenu = false
                                    viewModel.logout(onNavToLogin)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Switch Account") },
                                onClick = {
                                    showMenu = false
                                    showAccountSwitcher = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        floatingActionButton = {
            if (!uiState.showArchived) {
                FloatingActionButton(
                    onClick = onNavToSearch,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!uiState.showArchived) {
                val filters = listOf("All", "Personal", "Groups")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { filter ->
                        FilterChip(
                            selected = uiState.selectedFilter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter) }
                        )
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
                } else if (uiState.filteredConversations.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (uiState.showArchived) "No Archived Chats" else "No Conversations Found",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.showArchived) "Archived conversations will appear here." else "Tap the search button or plus icon below to start a conversation!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (!uiState.showArchived) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onNavToSearch) {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Find Users")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.filteredConversations,
                            key = { it.conversationId }
                        ) { conversation ->
                            val recipientId = conversation.participantIds.firstOrNull { it != currentUserId } ?: ""
                            val title = conversation.getTitle(currentUserId)

                            ConversationItem(
                                conversation = conversation,
                                recipientId = recipientId,
                                recipientName = title,
                                currentUserId = currentUserId,
                                onClick = {
                                    onNavToChat(conversation.conversationId, recipientId, title)
                                },
                                onLongClick = {
                                    selectedConversationForMenu = conversation
                                }
                            )
                            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    // Long-press Action Menu Modal Bottom Sheet
    if (selectedConversationForMenu != null) {
        val conv = selectedConversationForMenu!!
        val isPinned = conv.isPinnedFor(currentUserId)
        val isArchived = conv.isArchivedFor(currentUserId)
        val isMuted = conv.isMutedFor(currentUserId)

        ModalBottomSheet(
            onDismissRequest = { selectedConversationForMenu = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Conversation Options",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.togglePin(conv.conversationId, isPinned)
                            selectedConversationForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("📌", fontSize = 20.sp)
                    Text(if (isPinned) "Unpin Conversation" else "Pin Conversation", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.toggleArchive(conv.conversationId, isArchived)
                            selectedConversationForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("📂", fontSize = 20.sp)
                    Text(if (isArchived) "Unarchive Conversation" else "Archive Conversation", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.toggleMute(conv.conversationId, isMuted)
                            selectedConversationForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(if (isMuted) "🔔" else "🔕", fontSize = 20.sp)
                    Text(if (isMuted) "Unmute Notifications" else "Mute Notifications", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.deleteConversationForUser(conv.conversationId)
                            selectedConversationForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("🗑️", fontSize = 20.sp)
                    Text("Delete Conversation", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    // Account Switcher Modal Bottom Sheet
    if (showAccountSwitcher) {
        val savedAccounts = remember { SavedAccountManager.getSavedAccounts(context) }
        ModalBottomSheet(
            onDismissRequest = { showAccountSwitcher = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Switch Account",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (savedAccounts.isEmpty()) {
                    Text("No other saved accounts.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedAccounts) { account ->
                            val isCurrent = (account.email.equals(uiState.currentUser?.email, ignoreCase = true))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAccountSwitcher = false
                                        if (account.password.isNotBlank()) {
                                            viewModel.switchAccount(context, account.email, account.password, {
                                                onSwitchAccount()
                                            }, { err ->
                                                switchError = err
                                            })
                                        } else {
                                            accountToSwitch = account
                                            passwordInput = ""
                                            switchError = null
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
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
                                            modifier = Modifier
                                                .size(44.dp)
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
                                                        text = account.displayName.take(1).uppercase(),
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = account.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = account.email,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (isCurrent) {
                                        Badge { Text("Active") }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Password prompt dialog for legacy accounts without saved password
    if (accountToSwitch != null) {
        val acc = accountToSwitch!!
        AlertDialog(
            onDismissRequest = { if (!isSwitching) accountToSwitch = null },
            title = { Text("Enter Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter password for ${acc.email} to switch account:")
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; switchError = null },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (switchError != null) {
                        Text(switchError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordInput.isBlank()) {
                            switchError = "Please enter password"
                            return@Button
                        }
                        isSwitching = true
                        switchError = null
                        viewModel.switchAccount(context, acc.email, passwordInput, {
                            isSwitching = false
                            accountToSwitch = null
                            onSwitchAccount()
                        }, { err ->
                            isSwitching = false
                            switchError = err
                        })
                    },
                    enabled = !isSwitching
                ) {
                    if (isSwitching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Switch")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { accountToSwitch = null },
                    enabled = !isSwitching
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    recipientId: String,
    recipientName: String,
    currentUserId: String,
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

    val photoUrl = conversation.participantPhotos[recipientId] ?: ""
    val isPinned = conversation.isPinnedFor(currentUserId)
    val isMuted = conversation.isMutedFor(currentUserId)

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
                    modifier = Modifier.fillMaxSize()
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
