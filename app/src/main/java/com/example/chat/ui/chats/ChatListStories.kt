package com.example.chat.ui.chats

import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.chat.data.model.Story
import com.example.chat.data.model.User
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun StoriesRow(
    myPhotoUrl: String,
    myStories: List<Story>,
    myStoryNote: String,
    myStoryNoteExpiresAt: Long,
    myUser: User? = null,
    users: List<User>,
    usersMap: Map<String, User> = emptyMap(),
    currentUserId: String = "",
    activeStories: List<UserStories>,
    onlineUsersWithoutStories: List<UserStories>,
    onlineUserIds: Set<String>,
    onSaveMyNote: (String) -> Unit,
    onAddStoryClick: () -> Unit,
    onStoryClick: (userIndex: Int, storyIndex: Int) -> Unit,
    onReplyToNote: (recipientUser: User, noteText: String, replyMessage: String) -> Unit = { _, _, _ -> },
    onOpenConversation: (User) -> Unit = {},
    onToggleNoteLove: (targetUserId: String, noteText: String) -> Unit = { _, _ -> },
    onRecordNoteView: (targetUserId: String) -> Unit = {}
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var editingNote by remember { mutableStateOf<String?>(null) }
    var viewingMyNoteDetails by remember { mutableStateOf(false) }
    var viewingFriendNote by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val activeMyNote = myStoryNote.takeIf {
        it.isNotBlank() && myStoryNoteExpiresAt > now
    }.orEmpty()

    val effectiveUsersMap = remember(users, usersMap) {
        if (usersMap.isNotEmpty()) usersMap else users.associateBy { it.uid }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
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
                Box(
                    modifier = Modifier.size(width = 68.dp, height = 68.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .align(Alignment.BottomCenter)
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
                            .align(Alignment.BottomEnd)
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

                    if (activeMyNote.isNotBlank()) {
                        StoryNoteBubble(
                            note = activeMyNote,
                            lovedCount = myUser?.storyNoteLovedBy?.size ?: 0,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-12).dp),
                            onClick = { viewingMyNoteDetails = true }
                        )
                    } else {
                        Surface(
                            onClick = { editingNote = "" },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-3).dp)
                                .size(24.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add a note",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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
            val user = effectiveUsersMap[userStory.userId] ?: users.firstOrNull { it.uid == userStory.userId }
            val note = user?.storyNote.orEmpty()
                .takeIf { it.isNotBlank() && (user?.storyNoteExpiresAt ?: 0L) > now }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onStoryClick(userIndex, 0) }
            ) {
                Box(
                    modifier = Modifier.size(width = 68.dp, height = 68.dp),
                    contentAlignment = Alignment.BottomCenter
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
                    if (userStory.userId in onlineUserIds) {
                        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                            OnlineStatusBadge()
                        }
                    }
                    if (note != null) {
                        StoryNoteBubble(
                            note = note,
                            lovedCount = user?.storyNoteLovedBy?.size ?: 0,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-12).dp),
                            onClick = {
                                if (user != null) {
                                    viewingFriendNote = user
                                    onRecordNoteView(user.uid)
                                }
                            }
                        )
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

        // Online Users without active stories
        items(onlineUsersWithoutStories, key = { "online_${it.userId}" }) { userStory ->
            val userDetails = effectiveUsersMap[userStory.userId] ?: users.firstOrNull { it.uid == userStory.userId }
            val note = userDetails?.storyNote.orEmpty()
                .takeIf { it.isNotBlank() && (userDetails?.storyNoteExpiresAt ?: 0L) > now }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(width = 68.dp, height = 68.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Surface(
                        modifier = Modifier
                            .size(60.dp)
                            .border(
                                BorderStroke(
                                    2.dp,
                                    if (userStory.userId in onlineUserIds) {
                                        Color(0xFF22C55E)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                CircleShape
                            )
                            .padding(3.dp)
                            .clip(CircleShape)
                            .then(
                                if (userDetails != null) {
                                    Modifier.clickable {
                                        onOpenConversation(userDetails)
                                    }
                                } else {
                                    Modifier
                                }
                            ),
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
                    if (userStory.userId in onlineUserIds) {
                        OnlineStatusBadge()
                    }
                    if (note != null) {
                        StoryNoteBubble(
                            note = note,
                            lovedCount = userDetails?.storyNoteLovedBy?.size ?: 0,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-12).dp),
                            onClick = {
                                if (userDetails != null) {
                                    viewingFriendNote = userDetails
                                    onRecordNoteView(userDetails.uid)
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userStory.userName.substringBefore(" "),
                    modifier = Modifier.clickable {
                        userDetails?.let(onOpenConversation)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // Sheet for my own note details (views, loves, who viewed and reacted)
    if (viewingMyNoteDetails && myUser != null) {
        MyNoteDetailsBottomSheet(
            myUser = myUser,
            usersMap = effectiveUsersMap,
            onDismiss = { viewingMyNoteDetails = false },
            onEditClick = {
                viewingMyNoteDetails = false
                editingNote = activeMyNote
            },
            onDeleteClick = {
                viewingMyNoteDetails = false
                onSaveMyNote("")
            }
        )
    }

    // Dialog for replying to a friend's note and reacting with love
    viewingFriendNote?.let { friend ->
        FriendNoteReplyDialog(
            user = friend,
            currentUserId = currentUserId,
            onDismiss = { viewingFriendNote = null },
            onSendReply = { replyText ->
                onReplyToNote(friend, friend.storyNote, replyText)
                viewingFriendNote = null
            },
            onOpenConversation = {
                onOpenConversation(friend)
                viewingFriendNote = null
            },
            onToggleLove = {
                onToggleNoteLove(friend.uid, friend.storyNote)
            }
        )
    }

    // Sheet for creating / editing a note
    editingNote?.let { initialNote ->
        StoryNoteEditorSheet(
            initialNote = initialNote,
            onSave = { note ->
                onSaveMyNote(note)
                editingNote = null
            },
            onDismiss = { editingNote = null }
        )
    }
}

@Composable
private fun StoryNoteBubble(
    note: String,
    lovedCount: Int = 0,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .widthIn(max = 96.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = note,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (lovedCount > 0) {
                Text(
                    text = "❤️",
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun FriendNoteReplyDialog(
    user: User,
    currentUserId: String,
    onDismiss: () -> Unit,
    onOpenConversation: () -> Unit,
    onSendReply: (String) -> Unit,
    onToggleLove: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    var isLoved by remember(user.uid, user.storyNoteLovedBy) {
        mutableStateOf(currentUserId in user.storyNoteLovedBy)
    }
    var loveCount by remember(user.uid, user.storyNoteLovedBy) {
        mutableStateOf(user.storyNoteLovedBy.distinct().size)
    }
    val name = user.displayName.takeIf { it.isNotBlank() }
        ?: user.username.takeIf { it.isNotBlank() }
        ?: "User"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Avatar with online status
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenConversation),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        if (user.photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = user.photoUrl,
                                contentDescription = name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    name.take(1).uppercase(Locale.getDefault()),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                    if (user.isOnline) {
                        OnlineStatusBadge()
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = name,
                    modifier = Modifier.clickable(onClick = onOpenConversation),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Thought Bubble for Note
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = user.storyNote,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reactions row: Circular Love button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isLoved) Color(0xFFFFEBEF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isLoved) BorderStroke(1.5.dp, Color(0xFFEF4444)) else null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .clickable {
                                isLoved = !isLoved
                                if (isLoved) loveCount++ else if (loveCount > 0) loveCount--
                                onToggleLove()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isLoved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Love",
                                tint = if (isLoved) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (loveCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$loveCount",
                            color = if (isLoved) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reply text input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Reply to ${name.substringBefore(" ")}...", fontSize = 14.sp) },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onSendReply(replyText)
                            }
                        },
                        enabled = replyText.isNotBlank(),
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (replyText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (replyText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyNoteDetailsBottomSheet(
    myUser: User,
    usersMap: Map<String, User>,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Note",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onEditClick) {
                        Text("Edit")
                    }
                    TextButton(
                        onClick = onDeleteClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
            }

            // Note display bubble
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = myUser.storyNote,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Stats row
            val viewers = myUser.storyNoteViewedBy.distinct()
            val lovedBy = myUser.storyNoteLovedBy.toSet()

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Views", modifier = Modifier.size(16.dp))
                        Text(
                            text = "${viewers.size} ${if (viewers.size == 1) "view" else "views"}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (lovedBy.isNotEmpty()) Color(0xFFFFEBEF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Loves",
                            tint = if (lovedBy.isNotEmpty()) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${lovedBy.size} ${if (lovedBy.size == 1) "love" else "loves"}",
                            color = if (lovedBy.isNotEmpty()) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Text(
                text = "Viewers & Reactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (viewers.isEmpty()) {
                Text(
                    text = "No views yet. When friends view your note, they'll appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(viewers, key = { it }) { viewerId ->
                        val viewerUser = usersMap[viewerId]
                        val viewerName = viewerUser?.displayName?.takeIf { it.isNotBlank() }
                            ?: viewerUser?.username?.takeIf { it.isNotBlank() }
                            ?: "User"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                if (!viewerUser?.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = viewerUser.photoUrl,
                                        contentDescription = viewerName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            viewerName.take(1).uppercase(Locale.getDefault()),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = viewerName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )

                            if (viewerId in lovedBy) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFFEBEF),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("❤️", fontSize = 12.sp)
                                        Text(
                                            "Loved",
                                            color = Color(0xFFEF4444),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
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
fun StoryNoteEditorSheet(
    initialNote: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember(initialNote) { mutableStateOf(initialNote) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Share a note",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Your note will be visible for 24 hours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(ChatListViewModel.MAX_STORY_NOTE_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What's on your mind?") },
                supportingText = {
                    Text("${note.length}/${ChatListViewModel.MAX_STORY_NOTE_LENGTH}")
                },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (initialNote.isNotBlank()) {
                    TextButton(onClick = { onSave("") }) {
                        Text("Remove")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(note) },
                    enabled = note.isNotBlank()
                ) {
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun OnlineStatusBadge() {
    Surface(
        modifier = Modifier
            .size(14.dp)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        shape = CircleShape,
        color = Color(0xFF22C55E)
    ) {}
}

fun User.isCurrentlyOnline(now: Long): Boolean {
    val lastSeen = lastSeenTimeMillis()
    return isOnline || (lastSeen > 0L && now - lastSeen in 0..90_000L)
}

fun User.lastSeenTimeMillis(): Long = when (val lastSeen = lastSeenAt) {
    is Timestamp -> lastSeen.toDate().time
    is java.util.Date -> lastSeen.time
    is Number -> lastSeen.toLong()
    else -> 0L
}
