package com.example.chat.ui.conversation

import android.Manifest

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.chat.core.utils.CloudinaryUploader
import com.example.chat.data.model.Message
import com.example.chat.data.model.User
import com.example.chat.ui.chats.ChatListViewModel
import com.example.chat.ui.conversation.components.*
import com.example.chat.ui.contacts.formatLastSeen
import com.example.chat.ui.chats.MessageStatusTicks
import com.example.chat.ui.profile.StoryHighlightsRow
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Calendar
import java.util.Locale

@Composable
fun ConversationMessageList(
    uiState: ConversationUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    currentUserId: String,
    recipientId: String,
    themeColor: String,
    allChatImages: List<String>,
    context: android.content.Context,
    onReplyToMessage: (com.example.chat.data.model.Message) -> Unit,
    onOpenImageGallery: (Pair<List<String>, Int>) -> Unit,
    onOpenAlbum: (List<String>) -> Unit
) {
        val bubbleColorMe = getThemeBubbleColor(themeColor, true)
        val bubbleColorOther = getThemeBubbleColor(themeColor, false)
        val bgColor = getThemeBackgroundColor(themeColor)

        val reversedMessages = remember(uiState.messages) { uiState.messages.reversed() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(
                    items = reversedMessages,
                    key = { it.messageId },
                    contentType = { if (it.messageType == "system") "system" else if (it.senderId == currentUserId) "me" else "other" }
                ) { msg ->
                    val isMe = (msg.senderId == currentUserId)
                    val isSystem = (msg.messageType == "system")

                    if (isSystem) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = msg.text,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(msg) {
                                    detectHorizontalDragGestures { _, dragAmount ->
                                        if (dragAmount > 40) {
                                            onReplyToMessage(msg)
                                        }
                                    }
                                },
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (!isMe) {
                                val senderPhoto = uiState.conversation?.participantPhotos?.get(msg.senderId) ?: ""
                                val senderName = uiState.conversation?.participantNames?.get(msg.senderId) ?: "User"
                                Surface(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    if (senderPhoto.isNotBlank()) {
                                        AsyncImage(
                                            model = senderPhoto,
                                            contentDescription = "Sender Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = senderName.take(1).uppercase(Locale.getDefault()),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // If media group / album message
                            if (msg.messageType == "media_group" && msg.attachmentUrls.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.width(260.dp)
                                ) {
                                    MediaGroupGrid(
                                        urls = msg.attachmentUrls,
                                        onImageClick = { url ->
                                            val idx = msg.attachmentUrls.indexOf(url).coerceAtLeast(0)
                                            onOpenImageGallery(Pair(msg.attachmentUrls, idx))
                                        },
                                        onMoreClick = {
                                            onOpenAlbum(msg.attachmentUrls)
                                        }
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = DateFormat.format("HH:mm", Calendar.getInstance().apply { timeInMillis = msg.createdAt }).toString(),
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            } else if (msg.messageType == "image" && msg.attachmentUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .width(240.dp)
                                        .height(240.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF1E222A))
                                        .clickable {
                                            val idx = allChatImages.indexOf(msg.attachmentUrl).coerceAtLeast(0)
                                            onOpenImageGallery(Pair(allChatImages, idx))
                                        }
                                ) {
                                    AsyncImage(
                                        model = msg.attachmentUrl,
                                        contentDescription = "Image attachment",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = DateFormat.format("HH:mm", Calendar.getInstance().apply { timeInMillis = msg.createdAt }).toString(),
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            } else if (msg.messageType == "video" && msg.attachmentUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .width(240.dp)
                                        .height(240.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF1E222A))
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(msg.attachmentUrl)).apply {
                                                setDataAndType(Uri.parse(msg.attachmentUrl), "video/*")
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                ) {
                                    AsyncImage(
                                        model = msg.attachmentUrl,
                                        contentDescription = "Video thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Play button overlay in center
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(48.dp),
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.6f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play Video",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    // Download button overlay at top-right
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(32.dp)
                                            .clickable {
                                                downloadMedia(context, msg.attachmentUrl, true)
                                            },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.6f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("⬇", color = Color.White, fontSize = 14.sp)
                                        }
                                    }
                                    // Timestamp overlay at bottom-right
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = DateFormat.format("HH:mm", Calendar.getInstance().apply { timeInMillis = msg.createdAt }).toString(),
                                                fontSize = 10.sp,
                                                color = Color.White
                                            )
                                            if (isMe) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                val status = if (msg.readBy.contains(recipientId) || msg.status == "seen" || msg.status == "read") {
                                                    "seen"
                                                } else if (msg.status == "delivered" || uiState.recipientIsOnline) {
                                                    "delivered"
                                                } else {
                                                    "sent"
                                                }
                                                MessageStatusTicks(status = status, size = 11.dp)
                                            }
                                        }
                                    }
                                }
                            } else if (msg.messageType.startsWith("call", ignoreCase = true)) {
                                CallMessageBubble(msg = msg, isMe = isMe, themeColor = themeColor)
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isMe) bubbleColorMe else bubbleColorOther,
                                    modifier = if (msg.messageType == "audio") Modifier.width(260.dp) else Modifier.widthIn(max = 280.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        if (msg.replyToText.isNotBlank()) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 6.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (themeColor == "dark") Color(0xFF3E4A56) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                            ) {
                                                Column(modifier = Modifier.padding(6.dp)) {
                                                    Text(
                                                        text = "↩ ${msg.replyToSenderName} replied",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (themeColor == "dark") Color(0xFF90CAF9) else MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = msg.replyToText,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (themeColor == "dark") Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        if (msg.messageType == "audio" && msg.attachmentUrl.isNotBlank()) {
                                            VoiceMessagePlayer(audioUrl = msg.attachmentUrl)
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        if (msg.text.isNotBlank() && msg.messageType == "text") {
                                            Text(
                                                text = msg.text,
                                                fontSize = 15.sp,
                                                color = if (themeColor == "dark") Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.align(Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = DateFormat.format("HH:mm", Calendar.getInstance().apply { timeInMillis = msg.createdAt }).toString(),
                                                fontSize = 10.sp,
                                                color = if (themeColor == "dark") Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (isMe) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                val status = if (msg.readBy.contains(recipientId) || msg.status == "seen" || msg.status == "read") {
                                                    "seen"
                                                } else if (msg.status == "delivered" || uiState.recipientIsOnline) {
                                                    "delivered"
                                                } else {
                                                    "sent"
                                                }
                                                MessageStatusTicks(status = status, size = 11.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
}
