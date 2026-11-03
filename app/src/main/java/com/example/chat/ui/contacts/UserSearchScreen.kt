package com.example.chat.ui.contacts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat.data.model.User
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchScreen(
    viewModel: UserSearchViewModel,
    onNavBack: () -> Unit,
    onNavToChat: (conversationId: String, recipientId: String, recipientName: String) -> Unit,
    onNavToCall: (channelName: String, callType: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    val pageTitle = when (uiState.selectedTab) {
        0 -> "Contacts"
        1 -> "Call List"
        else -> "Users List"
    }

    val pageSubtitle = when (uiState.selectedTab) {
        0 -> "${uiState.chatContactsFiltered.size} contacts"
        1 -> "${uiState.callLogsFiltered.size} call records"
        else -> "${uiState.searchResults.size} registered users"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(pageTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = pageSubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Contacts") },
                    label = { Text("Contacts") }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.Call, contentDescription = "Call List") },
                    label = { Text("Call List") }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.Group, contentDescription = "User List") },
                    label = { Text("User List") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = {
                    Text(
                        when (uiState.selectedTab) {
                            0 -> "Search contacts by name or email"
                            1 -> "Search call history by name"
                            else -> "Search users by name or email"
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState.selectedTab) {
                // Tab 0: Contacts (previously chatted users)
                0 -> {
                    if (uiState.chatContactsFiltered.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No previous contacts found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                items = uiState.chatContactsFiltered,
                                key = { it.uid },
                                contentType = { "user_item" }
                            ) { user ->
                                UserItem(
                                    user = user,
                                    onMessageClick = {
                                        viewModel.startConversation(user) { conversationId ->
                                            onNavToChat(conversationId, user.uid, user.displayName)
                                        }
                                    },
                                    onAudioCallClick = {
                                        viewModel.initiateCall(user, callType = "audio") { channelName ->
                                            onNavToCall(channelName, "audio")
                                        }
                                    },
                                    onVideoCallClick = {
                                        viewModel.initiateCall(user, callType = "video") { channelName ->
                                            onNavToCall(channelName, "video")
                                        }
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                            }
                        }
                    }
                }

                // Tab 1: Call List
                1 -> {
                    if (uiState.callLogsFiltered.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No call history found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                items = uiState.callLogsFiltered,
                                key = { it.callId },
                                contentType = { "call_log_item" }
                            ) { log ->
                                CallLogItem(
                                    callLog = log,
                                    onCallClick = {
                                        viewModel.initiateCallByLog(log) { channelName ->
                                            onNavToCall(channelName, log.callType)
                                        }
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                            }
                        }
                    }
                }

                // Tab 2: Registered Users List (Default)
                else -> {
                    if (uiState.isLoading && uiState.allUsers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (uiState.searchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No registered users found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                items = uiState.searchResults,
                                key = { it.uid },
                                contentType = { "user_item" }
                            ) { user ->
                                UserItem(
                                    user = user,
                                    onMessageClick = {
                                        viewModel.startConversation(user) { conversationId ->
                                            onNavToChat(conversationId, user.uid, user.displayName)
                                        }
                                    },
                                    onAudioCallClick = {
                                        viewModel.initiateCall(user, callType = "audio") { channelName ->
                                            onNavToCall(channelName, "audio")
                                        }
                                    },
                                    onVideoCallClick = {
                                        viewModel.initiateCall(user, callType = "video") { channelName ->
                                            onNavToCall(channelName, "video")
                                        }
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatLastSeen(timestamp: Long): String {
    if (timestamp <= 0L) return "recently"
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 0L) return "just now"

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

@Composable
fun CallLogItem(
    callLog: CallHistoryLog,
    onCallClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCallClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            if (callLog.otherPersonPhoto.isNotBlank()) {
                AsyncImage(
                    model = callLog.otherPersonPhoto,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = callLog.otherPersonName.take(1).uppercase(Locale.getDefault()),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = callLog.otherPersonName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (callLog.isOutgoing) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (callLog.status == "rejected" || callLog.status == "declined") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${if (callLog.callType == "video") "Video" else "Audio"} Call • ${callLog.status.replaceFirstChar { it.uppercase() }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onCallClick) {
            Icon(
                imageVector = if (callLog.callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = "Re-call",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun UserItem(
    user: User,
    onMessageClick: () -> Unit,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit
) {
    val isOnline = user.isOnline || user.presence.equals("online", ignoreCase = true)
    val lastSeenTimestamp = if (user.lastSeen > 0L) user.lastSeen else user.lastSeenAt

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onMessageClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with Online / Offline Status Dot at bottom-right
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (user.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.displayName.take(1).uppercase(Locale.getDefault()),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Online indicator green dot or Offline grey dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF22C55E) else Color(0xFF9CA3AF))
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.surface), CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Display Name & Presence text below user name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (isOnline) {
                Text(
                    text = "Online",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF22C55E)
                )
            } else {
                Text(
                    text = "Last seen ${formatLastSeen(lastSeenTimestamp)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-4).dp)
        ) {
            IconButton(onClick = onAudioCallClick) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Audio Call",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onVideoCallClick) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video Call",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onMessageClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Message",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
