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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@Composable
fun StoriesRow(
    myPhotoUrl: String,
    myStories: List<Story>,
    myStoryNote: String,
    myStoryNoteExpiresAt: Long,
    users: List<User>,
    activeStories: List<UserStories>,
    onlineUsersWithoutStories: List<UserStories>,
    onlineUserIds: Set<String>,
    onSaveMyNote: (String) -> Unit,
    onAddStoryClick: () -> Unit,
    onStoryClick: (userIndex: Int, storyIndex: Int) -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var editingNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val activeMyNote = myStoryNote.takeIf {
        it.isNotBlank() && myStoryNoteExpiresAt > now
    }.orEmpty()

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-3).dp),
                            onClick = { editingNote = activeMyNote }
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onStoryClick(userIndex, 0) }
            ) {
                val user = users.firstOrNull { it.uid == userStory.userId }
                val note = user?.storyNote.orEmpty()
                    .takeIf { it.isNotBlank() && (user?.storyNoteExpiresAt ?: 0L) > now }
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
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-3).dp)
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

        items(onlineUsersWithoutStories, key = { "online_${it.userId}" }) { user ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val userDetails = users.firstOrNull { it.uid == user.userId }
                val note = userDetails?.storyNote.orEmpty()
                    .takeIf { it.isNotBlank() && (userDetails?.storyNoteExpiresAt ?: 0L) > now }
                Box(
                    modifier = Modifier.size(width = 68.dp, height = 68.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Surface(
                        modifier = Modifier
                            .size(60.dp)
                            .border(
                                BorderStroke(2.dp, Color(0xFF22C55E)),
                                CircleShape
                            )
                            .padding(3.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        if (user.userPhoto.isNotBlank()) {
                            AsyncImage(
                                model = user.userPhoto,
                                contentDescription = "${user.userName}, online",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = user.userName.take(1).uppercase(Locale.getDefault()),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                    OnlineStatusBadge()
                    if (note != null) {
                        StoryNoteBubble(
                            note = note,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-3).dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = user.userName.substringBefore(" "),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

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
        Text(
            text = note,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryNoteEditorSheet(
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
