package com.example.chat.ui.profile

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat.data.model.Story
import com.example.chat.data.model.User
import com.example.chat.ui.chats.ChatListViewModel
import com.example.chat.ui.chats.FullStoryViewerDialog
import com.example.chat.ui.chats.MyNoteDetailsBottomSheet
import com.example.chat.ui.chats.StoryNoteEditorSheet
import com.example.chat.ui.chats.StoryViewersBottomSheet
import com.example.chat.ui.chats.UserStories
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

@Composable
fun StoryHighlightsRow(
    ownerId: String,
    ownerName: String,
    ownerPhoto: String,
    viewerId: String,
    viewModel: ChatListViewModel,
    onNavToChat: (conversationId: String, recipientId: String, recipientName: String) -> Unit,
    ownerUser: User? = null,
    onSaveNote: ((String) -> Unit)? = null,
    showCurrentStoryAndNote: Boolean = false
) {
    val context = LocalContext.current
    val chatUiState by viewModel.uiState.collectAsState()
    var stories by remember(ownerId) { mutableStateOf<List<Story>>(emptyList()) }
    var selectedStoryId by remember(ownerId) { mutableStateOf<String?>(null) }
    var storyViewerStories by remember(ownerId) { mutableStateOf<List<Story>>(emptyList()) }
    var storyForViewers by remember(ownerId) { mutableStateOf<Story?>(null) }
    var editingNote by remember { mutableStateOf<String?>(null) }
    var showingNoteDetails by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val isOwner = viewerId == ownerId
    val activeStories = remember(stories, now) {
        stories.filter { it.expiresAt > now }.sortedBy { it.createdAt }
    }
    val highlightedStories = remember(stories, now) {
        stories.filter { it.expiresAt <= now }.sortedByDescending { it.createdAt }
    }
    val noteOwner = chatUiState.usersMap[ownerId]
        ?: ownerUser
        ?: chatUiState.currentUser?.takeIf { it.uid == ownerId }
    val activeNote = noteOwner?.storyNote.orEmpty()
        .takeIf { it.isNotBlank() && (noteOwner?.storyNoteExpiresAt ?: 0L) > now }
        .orEmpty()
    val isNoteLoved = viewerId in (noteOwner?.storyNoteLovedBy ?: emptyList())
    val isUploadingStory = isOwner && chatUiState.isUploadingStory
    val storyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && isOwner) viewModel.uploadStory(context, uri)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    LaunchedEffect(chatUiState.storyUploadError) {
        chatUiState.storyUploadError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearStoryUploadError()
        }
    }

    DisposableEffect(ownerId) {
        if (ownerId.isBlank()) {
            onDispose { }
        } else {
            val listener = FirebaseFirestore.getInstance()
                .collection("stories")
                .whereEqualTo("userId", ownerId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Toast.makeText(
                            context,
                            error.localizedMessage ?: "Couldn't load story highlights.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else if (snapshot != null) {
                        stories = snapshot.documents
                            .mapNotNull { it.toObject(Story::class.java) }
                            .sortedByDescending { it.createdAt }
                    }
                }
            onDispose { listener.remove() }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        if (showCurrentStoryAndNote && (activeNote.isNotBlank() || isOwner)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isOwner) "Your Note" else "Note", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (isOwner) {
                    TextButton(onClick = { editingNote = activeNote }) {
                        Text(if (activeNote.isBlank()) "Add note" else "Edit")
                    }
                }
            }
            if (activeNote.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isOwner) showingNoteDetails = true
                            else viewModel.recordStoryNoteView(ownerId)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(activeNote, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        if (!isOwner) {
                            IconButton(onClick = { viewModel.toggleStoryNoteLove(ownerId, activeNote) }) {
                                Icon(
                                    imageVector = if (isNoteLoved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Love note",
                                    tint = if (isNoteLoved) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (isOwner) {
                Text(
                    text = "Share a note that stays visible for 24 hours.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showCurrentStoryAndNote && (activeStories.isNotEmpty() || isOwner)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Current Story", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (isOwner) {
                    TextButton(
                        onClick = {
                            storyPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        enabled = !isUploadingStory
                    ) {
                        if (isUploadingStory) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Add story")
                        }
                    }
                }
            }
            if (activeStories.isEmpty()) {
                Text(
                    text = "You don't have an active story.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(activeStories, key = { "current-${it.storyId}" }) { story ->
                        StoryThumbnail(
                            story = story,
                            dateLabel = DateFormat.format("HH:mm", story.createdAt).toString(),
                            onClick = {
                                storyViewerStories = activeStories
                                selectedStoryId = story.storyId
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (highlightedStories.isNotEmpty()) {
            Text("Highlights", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(highlightedStories, key = { "highlight-${it.storyId}" }) { story ->
                    StoryThumbnail(
                        story = story,
                        dateLabel = DateFormat.format("MMM d", story.createdAt).toString(),
                        onClick = {
                            storyViewerStories = highlightedStories.sortedBy { it.createdAt }
                            selectedStoryId = story.storyId
                        }
                    )
                }
            }
        }
    }

    if (showCurrentStoryAndNote && showingNoteDetails && isOwner && noteOwner != null && activeNote.isNotBlank()) {
        MyNoteDetailsBottomSheet(
            myUser = noteOwner,
            usersMap = chatUiState.usersMap,
            onDismiss = { showingNoteDetails = false },
            onEditClick = {
                showingNoteDetails = false
                editingNote = activeNote
            },
            onDeleteClick = {
                showingNoteDetails = false
                onSaveNote?.invoke("")
            }
        )
    }

    if (showCurrentStoryAndNote) editingNote?.let { initialNote ->
        StoryNoteEditorSheet(
            initialNote = initialNote,
            onSave = { note ->
                onSaveNote?.invoke(note)
                editingNote = null
            },
            onDismiss = { editingNote = null }
        )
    }

    val selectedId = selectedStoryId
    if (selectedId != null) {
        val selectedIndex = storyViewerStories.indexOfFirst { it.storyId == selectedId }
        if (selectedIndex >= 0) {
            FullStoryViewerDialog(
                allUserStories = listOf(
                    UserStories(
                        userId = ownerId,
                        userName = ownerName,
                        userPhoto = ownerPhoto,
                        stories = storyViewerStories
                    )
                ),
                initialStoryIndex = selectedIndex,
                viewerId = viewerId,
                onMarkViewed = viewModel::markStoryViewed,
                onToggleLove = { story ->
                    viewModel.toggleStoryLove(story) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
                availableUsers = chatUiState.usersMap.values.toList(),
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
                            selectedStoryId = null
                            onNavToChat(conversationId, story.userId, story.userName)
                        },
                        onError = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                onOpenInsights = { story -> storyForViewers = story },
                onOpenConversation = { story ->
                    val recipient = chatUiState.usersMap[story.userId] ?: User(
                        uid = story.userId,
                        displayName = story.userName,
                        photoUrl = story.userPhoto
                    )
                    viewModel.openConversationWithUser(
                        recipientUser = recipient,
                        onConversationReady = { conversationId ->
                            selectedStoryId = null
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
                onDismiss = { selectedStoryId = null }
            )
        }
    }

    storyForViewers?.let { story ->
        StoryViewersBottomSheet(
            story = story,
            users = chatUiState.usersMap,
            onDismiss = { storyForViewers = null }
        )
    }
}

@Composable
private fun StoryThumbnail(story: Story, dateLabel: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
        ) {
            AsyncImage(
                model = story.mediaUrl,
                contentDescription = if (story.mediaType.equals("video", ignoreCase = true)) {
                    "Video story"
                } else {
                    "Story"
                },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (story.mediaType.equals("video", ignoreCase = true)) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).size(22.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video story",
                            tint = Color.White
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = dateLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
