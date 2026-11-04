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
