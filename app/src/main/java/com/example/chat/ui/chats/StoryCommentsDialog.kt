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
private fun StoryCommentRow(
    comment: StoryComment,
    isFocused: Boolean,
    replyToName: String?,
    viewerId: String,
    modifier: Modifier = Modifier,
    onReply: (StoryComment) -> Unit,
    onLove: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            if (comment.senderPhoto.isNotBlank()) {
                AsyncImage(
                    model = comment.senderPhoto,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = comment.senderName.take(1).uppercase(Locale.getDefault()),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = comment.senderName.ifBlank { "User" },
                fontWeight = FontWeight.SemiBold
            )
            if (replyToName != null) {
                Text(
                    text = "Replying to $replyToName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = comment.text)
            TextButton(
                onClick = { onReply(comment) },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Reply", style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onLove) {
                Icon(
                    imageVector = if (viewerId in comment.lovedBy) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    contentDescription = if (viewerId in comment.lovedBy) {
                        "Remove love from comment"
                    } else {
                        "Love comment"
                    },
                    tint = if (viewerId in comment.lovedBy) {
                        Color(0xFFFF5A70)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (comment.lovedBy.isNotEmpty()) {
                Text(
                    text = comment.lovedBy.distinct().size.toString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

fun rootCommentFor(
    comment: StoryComment,
    commentsById: Map<String, StoryComment>
): StoryComment {
    var root = comment
    val visited = mutableSetOf(comment.commentId)
    while (root.parentCommentId.isNotBlank() && visited.add(root.parentCommentId)) {
        root = commentsById[root.parentCommentId] ?: break
    }
    return root
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCommentsDialog(
    storyId: String,
    viewerId: String,
    availableUsers: List<User>,
    focusCommentId: String,
    comments: List<StoryComment>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onToggleCommentLove: (String, StoryComment) -> Unit,
    onSendComment: (String, String, String, List<String>, () -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var replyTarget by remember(storyId) { mutableStateOf<StoryComment?>(null) }
    var showMentionPicker by remember(storyId) { mutableStateOf(false) }
    var showMentionSearch by remember(storyId) { mutableStateOf(false) }
    var mentionSearchQuery by remember(storyId) { mutableStateOf("") }
    val selectedMentionIds = remember(storyId) { mutableStateListOf<String>() }
    val commentsListState = rememberLazyListState()
    val commentsById = remember(comments) { comments.associateBy { it.commentId } }
    val rootComments = remember(comments) {
        comments.filter { comment ->
            rootCommentFor(comment, commentsById).commentId == comment.commentId
        }
    }
    val focusRootCommentId = commentsById[focusCommentId]
        ?.let { rootCommentFor(it, commentsById).commentId }

    LaunchedEffect(comments, focusCommentId) {
        val targetComment = commentsById[focusCommentId]
        val rootCommentId = targetComment?.let { rootCommentFor(it, commentsById).commentId }
        val targetIndex = rootComments.indexOfFirst { it.commentId == rootCommentId }
        if (targetIndex >= 0) commentsListState.animateScrollToItem(targetIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close comments")
                    }
                    Text(
                        text = "Comments",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                HorizontalDivider()
                if (comments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No comments yet. Be the first to comment.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = commentsListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(rootComments, key = { it.commentId }) { rootComment ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StoryCommentRow(
                                    comment = rootComment,
                                    isFocused = rootComment.commentId == focusRootCommentId,
                                    replyToName = null,
                                    viewerId = viewerId,
                                    onReply = { comment ->
                                        replyTarget?.senderId
                                            ?.takeIf { it != comment.senderId }
                                            ?.let(selectedMentionIds::remove)
                                        replyTarget = comment
                                        if (comment.senderId != viewerId && comment.senderId !in selectedMentionIds) {
                                            selectedMentionIds.add(comment.senderId)
                                        }
                                        val mention = "@${comment.senderName.ifBlank { "User" }} "
                                        onDraftChange(if (draft.isBlank()) mention else "$draft $mention")
                                    },
                                    onLove = { onToggleCommentLove(storyId, rootComment) }
                                )
                                comments
                                    .filter { it.commentId != rootComment.commentId }
                                    .filter { rootCommentFor(it, commentsById).commentId == rootComment.commentId }
                                    .sortedBy { it.createdAt }
                                    .forEach { reply ->
                                        val replyTo = commentsById[reply.replyToCommentId]
                                            ?: rootComment
                                        StoryCommentRow(
                                            comment = reply,
                                            isFocused = reply.commentId == focusCommentId,
                                            replyToName = replyTo.senderName.ifBlank { "comment" },
                                            viewerId = viewerId,
                                            modifier = Modifier.padding(start = 32.dp),
                                            onReply = { comment ->
                                                replyTarget?.senderId
                                                    ?.takeIf { it != comment.senderId }
                                                    ?.let(selectedMentionIds::remove)
                                                replyTarget = comment
                                                if (comment.senderId != viewerId && comment.senderId !in selectedMentionIds) {
                                                    selectedMentionIds.add(comment.senderId)
                                                }
                                                val mention = "@${comment.senderName.ifBlank { "User" }} "
                                                onDraftChange(if (draft.isBlank()) mention else "$draft $mention")
                                            },
                                            onLove = { onToggleCommentLove(storyId, reply) }
                                        )
                                    }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .padding(bottom = 56.dp)
                ) {
                    replyTarget?.let { target ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Replying to ${target.senderName.ifBlank { "comment" }}",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(onClick = {
                                selectedMentionIds.remove(target.senderId)
                                replyTarget = null
                                onDraftChange("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                showMentionSearch = false
                                mentionSearchQuery = ""
                                showMentionPicker = true
                            }
                        ) {
                            Text("@", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            placeholder = {
                                Text(
                                    if (replyTarget == null) "Write a comment..." else "Write a reply..."
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                val text = draft.trim()
                                val replyRootId = replyTarget
                                    ?.let { rootCommentFor(it, commentsById).commentId }
                                    .orEmpty()
                                onSendComment(
                                    text,
                                    replyRootId,
                                    replyTarget?.commentId.orEmpty(),
                                    selectedMentionIds.toList()
                                ) {
                                    replyTarget = null
                                    selectedMentionIds.clear()
                                }
                            },
                            enabled = draft.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Post comment",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMentionPicker) {
        val mentionableUsers = availableUsers
            .filter { it.uid.isNotBlank() && it.uid != viewerId }
            .filter { user ->
                val query = mentionSearchQuery.trim()
                query.isBlank() ||
                    user.displayName.contains(query, ignoreCase = true) ||
                    user.username.contains(query, ignoreCase = true)
            }
            .sortedBy { it.displayName.ifBlank { it.username }.lowercase(Locale.getDefault()) }
        ModalBottomSheet(
            onDismissRequest = { showMentionPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mention someone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        showMentionSearch = !showMentionSearch
                        if (!showMentionSearch) mentionSearchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (showMentionSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showMentionSearch) "Close user search" else "Search users"
                        )
                    }
                }
                if (showMentionSearch) {
                    OutlinedTextField(
                        value = mentionSearchQuery,
                        onValueChange = { mentionSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true,
                        placeholder = { Text("Search people") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    )
                }
                if (mentionableUsers.isEmpty()) {
                    Text(
                        text = if (availableUsers.any { it.uid.isNotBlank() && it.uid != viewerId }) {
                            "No users found."
                        } else {
                            "No users available to mention."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(mentionableUsers, key = { it.uid }) { user ->
                            val name = user.displayName.ifBlank { user.username }.ifBlank { "User" }
                            Surface(
                                onClick = {
                                    if (user.uid !in selectedMentionIds) {
                                        selectedMentionIds.add(user.uid)
                                        onDraftChange("${draft.trimEnd()} @$name ")
                                    }
                                    showMentionPicker = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        if (user.photoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = user.photoUrl,
                                                contentDescription = "$name profile photo",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = name.take(1).uppercase(Locale.getDefault()),
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
