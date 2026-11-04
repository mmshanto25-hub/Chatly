package com.example.chat.ui.profile

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import com.example.chat.ui.chats.ChatListViewModel
import com.example.chat.ui.chats.FullStoryViewerDialog
import com.example.chat.ui.chats.StoryViewersBottomSheet
import com.example.chat.ui.chats.UserStories
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun StoryHighlightsRow(
    ownerId: String,
    ownerName: String,
    ownerPhoto: String,
    viewerId: String,
    viewModel: ChatListViewModel,
    onNavToChat: (conversationId: String, recipientId: String, recipientName: String) -> Unit
) {
    val context = LocalContext.current
    val chatUiState by viewModel.uiState.collectAsState()
    var stories by remember(ownerId) { mutableStateOf<List<Story>>(emptyList()) }
    var selectedStoryId by remember(ownerId) { mutableStateOf<String?>(null) }
    var storyForViewers by remember(ownerId) { mutableStateOf<Story?>(null) }

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
        Text("Highlights", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(10.dp))
        if (stories.isEmpty()) {
            Text(
                text = "Stories will be saved here as highlights.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(stories, key = { it.storyId }) { story ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(68.dp)
                            .clickable { selectedStoryId = story.storyId }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                    CircleShape
                                )
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    CircleShape
                                )
                        ) {
                            AsyncImage(
                                model = story.mediaUrl,
                                contentDescription = if (
                                    story.mediaType.equals("video", ignoreCase = true)
                                ) {
                                    "Video story highlight"
                                } else {
                                    "Story highlight"
                                },
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (story.mediaType.equals("video", ignoreCase = true)) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(22.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Video highlight",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DateFormat.format("MMM d", story.createdAt).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    val chronologicalStories = remember(stories) { stories.sortedBy { it.createdAt } }
    val selectedId = selectedStoryId
    if (selectedId != null) {
        val selectedIndex = chronologicalStories.indexOfFirst { it.storyId == selectedId }
        if (selectedIndex >= 0) {
            FullStoryViewerDialog(
                allUserStories = listOf(
                    UserStories(
                        userId = ownerId,
                        userName = ownerName,
                        userPhoto = ownerPhoto,
                        stories = chronologicalStories
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
