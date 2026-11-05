package com.example.chat.ui.chats

import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
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
import androidx.compose.material.icons.filled.DeleteOutline
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
fun FullStoryViewerDialog(
    allUserStories: List<UserStories>,
    initialUserIndex: Int = 0,
    initialStoryIndex: Int = 0,
    viewerId: String,
    onMarkViewed: (String) -> Unit,
    onToggleLove: (Story) -> Unit,
    availableUsers: List<User>,
    openCommentsOnStart: Boolean = false,
    focusCommentId: String = "",
    onAddComment: (Story, String, String, String, List<String>, () -> Unit) -> Unit,
    onToggleCommentLove: (String, StoryComment) -> Unit,
    onSendMessage: (Story, String) -> Unit,
    onOpenInsights: (Story) -> Unit,
    onOpenUserProfile: (userId: String, userName: String) -> Unit = { _, _ -> },
    onOpenConversation: (Story) -> Unit = {},
    onDeleteStory: (
        story: Story,
        keepInHighlights: Boolean,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, onSuccess -> onSuccess() },
    onDismiss: () -> Unit
) {
    if (allUserStories.isEmpty()) return
    var visibleUserStories by remember { mutableStateOf(allUserStories) }
    var currentUserIndex by remember {
        mutableIntStateOf(initialUserIndex.coerceIn(0, allUserStories.size - 1))
    }
    val currentUserStories = visibleUserStories.getOrNull(currentUserIndex) ?: visibleUserStories.first()

    var currentStoryIndex by remember {
        mutableIntStateOf(initialStoryIndex.coerceIn(0, currentUserStories.stories.lastIndex))
    }
    val currentStory = currentUserStories.stories.getOrNull(currentStoryIndex) ?: currentUserStories.stories.first()
    var liveStory by remember(currentStory.storyId) { mutableStateOf(currentStory) }
    var messageDraft by remember(currentStory.storyId) { mutableStateOf("") }
    var commentDraft by remember(currentStory.storyId) { mutableStateOf("") }
    var showStoryComments by remember(currentStory.storyId) { mutableStateOf(openCommentsOnStart) }
    var storyComments by remember(currentStory.storyId) { mutableStateOf<List<StoryComment>>(emptyList()) }
    var showDeleteStoryDialog by remember(currentStory.storyId) { mutableStateOf(false) }
    var keepStoryInHighlights by remember(currentStory.storyId) { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isOwnStory = currentUserStories.userId == viewerId

    LaunchedEffect(liveStory.storyId) {
        while (true) {
            delay(60_000L)
            currentTime = System.currentTimeMillis()
        }
    }

    DisposableEffect(currentStory.storyId) {
        val storyRef = FirebaseFirestore.getInstance()
            .collection("stories")
            .document(currentStory.storyId)
        val storyListener = storyRef.addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null) {
                snapshot.toObject(Story::class.java)?.let { liveStory = it }
            }
        }
        val commentsListener = storyRef.collection("comments")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    storyComments = snapshot.documents
                        .mapNotNull { it.toObject(StoryComment::class.java) }
                        .sortedBy { it.createdAt }
                }
            }
        onDispose {
            storyListener.remove()
            commentsListener.remove()
        }
    }

    LaunchedEffect(currentStory.storyId, viewerId, isOwnStory) {
        if (!isOwnStory && viewerId.isNotBlank()) onMarkViewed(currentStory.storyId)
    }

    fun advanceStory() {
        if (currentStoryIndex < currentUserStories.stories.size - 1) {
            currentStoryIndex++
        } else {
            if (currentUserIndex < visibleUserStories.size - 1) {
                currentUserIndex++
                currentStoryIndex = 0
            } else {
                onDismiss()
            }
        }
    }

    fun removeCurrentStoryAndAdvance() {
        val orderedStories = visibleUserStories.flatMap { it.stories }
        val currentIndex = orderedStories.indexOfFirst { it.storyId == liveStory.storyId }
        val nextStory = orderedStories.getOrNull(currentIndex + 1)
        val updatedStories = visibleUserStories.mapNotNull { userStories ->
            val remainingStories = userStories.stories.filterNot { it.storyId == liveStory.storyId }
            if (remainingStories.isEmpty()) null else userStories.copy(stories = remainingStories)
        }
        if (nextStory == null) {
            onDismiss()
            return
        }
        visibleUserStories = updatedStories
        val nextUserIndex = updatedStories.indexOfFirst { group ->
            group.stories.any { it.storyId == nextStory.storyId }
        }
        if (nextUserIndex >= 0) {
            currentUserIndex = nextUserIndex
            currentStoryIndex = updatedStories[nextUserIndex].stories.indexOfFirst {
                it.storyId == nextStory.storyId
            }
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(
        currentUserIndex,
        currentStoryIndex,
        currentStory.storyId,
        showStoryComments,
        showDeleteStoryDialog
    ) {
        if (!showStoryComments && !showDeleteStoryDialog &&
            !liveStory.mediaType.equals("video", ignoreCase = true)
        ) {
            delay(Story.IMAGE_DISPLAY_DURATION_MS)
            advanceStory()
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
                val isVideo = liveStory.mediaType.equals("video", ignoreCase = true) ||
                        liveStory.mediaUrl.lowercase().contains(".mp4") ||
                        liveStory.mediaUrl.lowercase().contains("video") ||
                        liveStory.mediaUrl.lowercase().contains("mov") ||
                        liveStory.mediaUrl.lowercase().contains("webm")

                if (isVideo) {
                    key(liveStory.storyId) {
                        AndroidView(
                            factory = { ctx ->
                                android.widget.VideoView(ctx).apply {
                                    setVideoURI(liveStory.mediaUrl.toUri())
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = false
                                        start()
                                    }
                                    setOnCompletionListener {
                                        advanceStory()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    AsyncImage(
                        model = liveStory.mediaUrl,
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
                                    .clip(CircleShape)
                                    .clickable {
                                        onOpenUserProfile(
                                            currentUserStories.userId,
                                            currentUserStories.userName
                                        )
                                    },
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
                            Column {
                                Text(
                                    text = currentUserStories.userName,
                                    modifier = Modifier.clickable {
                                        onOpenUserProfile(
                                            currentUserStories.userId,
                                            currentUserStories.userName
                                        )
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = storyAgeLabel(liveStory.createdAt, currentTime),
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isOwnStory) {
                                Surface(
                                    onClick = { onOpenConversation(liveStory) },
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.16f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = "Open chat",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }

                            if (isOwnStory) {
                                IconButton(onClick = { showDeleteStoryDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete story",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .padding(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (liveStory.caption.isNotBlank()) {
                        Text(
                            text = liveStory.caption,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                    if (isOwnStory) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                onClick = { onOpenInsights(liveStory) },
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(9.dp),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Visibility,
                                        contentDescription = "View story viewers",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${liveStory.viewedBy.distinct().size} views",
                                color = Color.White,
                                modifier = Modifier
                                    .weight(1f)
                                        .clickable { onOpenInsights(liveStory) }
                            )
                            Surface(
                                onClick = { showStoryComments = true },
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.ChatBubbleOutline,
                                        contentDescription = "View and reply to story comments",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${storyComments.size}", color = Color.White)
                        }
                    } else {
                        storyComments.takeLast(5).forEach { comment ->
                            val senderName = comment.senderName.ifBlank { "User" }
                            val senderPhoto = comment.senderPhoto.ifBlank {
                                availableUsers.firstOrNull { it.uid == comment.senderId }?.photoUrl.orEmpty()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            onOpenUserProfile(comment.senderId, senderName)
                                        },
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    if (senderPhoto.isNotBlank()) {
                                        AsyncImage(
                                            model = senderPhoto,
                                            contentDescription = "$senderName profile photo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = senderName.take(1).uppercase(Locale.getDefault()),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 300.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.Black.copy(alpha = 0.58f))
                                        .padding(horizontal = 11.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = senderName,
                                        modifier = Modifier.clickable {
                                            onOpenUserProfile(comment.senderId, senderName)
                                        },
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = comment.text,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { onToggleLove(liveStory) }
                            ) {
                                Icon(
                                    imageVector = if (viewerId in liveStory.lovedBy) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Love story",
                                    tint = Color(0xFFFF5A70)
                                )
                            }
                            OutlinedTextField(
                                value = messageDraft,
                                onValueChange = { messageDraft = it },
                                placeholder = { Text("Write a message...", color = Color.LightGray) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.LightGray,
                                    cursorColor = Color.White
                                )
                            )
                            IconButton(
                                onClick = { showStoryComments = true }
                            ) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Open story comments", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    if (messageDraft.isNotBlank()) {
                                        onSendMessage(liveStory, messageDraft)
                                    }
                                },
                                enabled = messageDraft.isNotBlank()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message to inbox", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteStoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteStoryDialog = false },
            title = { Text("Delete story?") },
            text = {
                Column {
                    Text("This story will be removed from active stories.")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                keepStoryInHighlights = !keepStoryInHighlights
                            }
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = keepStoryInHighlights,
                            onCheckedChange = { keepStoryInHighlights = it }
                        )
                        Text("Keep in profile highlights")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteStoryDialog = false
                        onDeleteStory(liveStory, keepStoryInHighlights) {
                            removeCurrentStoryAndAdvance()
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteStoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showStoryComments) {
        StoryCommentsDialog(
            storyId = liveStory.storyId,
            viewerId = viewerId,
            availableUsers = availableUsers,
            focusCommentId = focusCommentId,
            comments = storyComments,
            draft = commentDraft,
            onDraftChange = { commentDraft = it },
            onToggleCommentLove = onToggleCommentLove,
            onSendComment = { text, parentCommentId, replyToCommentId, mentionIds, onSuccess ->
                if (text.isNotBlank()) {
                    onAddComment(liveStory, text, parentCommentId, replyToCommentId, mentionIds) {
                        commentDraft = ""
                        onSuccess()
                    }
                }
            },
            onOpenUserProfile = onOpenUserProfile,
            onDismiss = { showStoryComments = false }
        )
    }
}

private fun storyAgeLabel(createdAt: Long, now: Long): String {
    val elapsed = (now - createdAt).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
