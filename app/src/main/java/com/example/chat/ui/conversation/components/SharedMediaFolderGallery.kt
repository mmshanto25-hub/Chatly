package com.example.chat.ui.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.chat.data.model.Message

data class MediaItem(
    val url: String,
    val type: String, // "image", "video", "audio"
    val timestamp: Long = 0L
)

@Composable
fun SharedMediaFolderGallery(
    messages: List<Message>,
    onMediaClick: (String, String) -> Unit
) {
    var selectedFolder by remember { mutableStateOf("all") } // "all", "images", "videos", "audio"
    var viewMode by remember { mutableStateOf("grid") } // "grid", "list", "slide"

    val allMediaItems = remember(messages) {
        messages.flatMap { msg ->
            when (msg.messageType) {
                "media_group" -> msg.attachmentUrls.map { MediaItem(it, "image", msg.createdAt) }
                "image" -> if (msg.attachmentUrl.isNotBlank()) listOf(MediaItem(msg.attachmentUrl, "image", msg.createdAt)) else emptyList()
                "video" -> if (msg.attachmentUrl.isNotBlank()) listOf(MediaItem(msg.attachmentUrl, "video", msg.createdAt)) else emptyList()
                "audio" -> if (msg.attachmentUrl.isNotBlank()) listOf(MediaItem(msg.attachmentUrl, "audio", msg.createdAt)) else emptyList()
                else -> emptyList()
            }
        }.distinctBy { it.url }.reversed()
    }

    val displayedItems = remember(allMediaItems, selectedFolder) {
        when (selectedFolder) {
            "images" -> allMediaItems.filter { it.type == "image" }
            "videos" -> allMediaItems.filter { it.type == "video" }
            "audio" -> allMediaItems.filter { it.type == "audio" }
            else -> allMediaItems
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = when(selectedFolder) { "images" -> 1; "videos" -> 2; "audio" -> 3; else -> 0 },
            edgePadding = 16.dp
        ) {
            Tab(selected = selectedFolder == "all", onClick = { selectedFolder = "all" }, text = { Text("📁 All (${allMediaItems.size})") })
            Tab(selected = selectedFolder == "images", onClick = { selectedFolder = "images" }, text = { Text("📷 Images (${allMediaItems.count { it.type == "image" }})") })
            Tab(selected = selectedFolder == "videos", onClick = { selectedFolder = "videos" }, text = { Text("🎥 Videos (${allMediaItems.count { it.type == "video" }})") })
            Tab(selected = selectedFolder == "audio", onClick = { selectedFolder = "audio" }, text = { Text("🎤 Audio (${allMediaItems.count { it.type == "audio" }})") })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${displayedItems.size} items", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { viewMode = "grid" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewMode == "grid") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (viewMode == "grid") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("Grid", fontSize = 11.sp) }

                Button(
                    onClick = { viewMode = "list" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewMode == "list") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (viewMode == "list") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("List", fontSize = 11.sp) }

                Button(
                    onClick = { viewMode = "slide" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewMode == "slide") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (viewMode == "slide") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("Slide", fontSize = 11.sp) }
            }
        }

        if (displayedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("No media found in this folder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (viewMode) {
                    "grid" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val rows = displayedItems.chunked(3)
                            items(rows) { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (item in rowItems) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1E222A))
                                                .clickable { onMediaClick(item.url, item.type) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.type == "audio") {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(4.dp)
                                                ) {
                                                    Text("🎤", fontSize = 24.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("Audio", fontSize = 10.sp, color = Color.White, maxLines = 1)
                                                }
                                            } else {
                                                AsyncImage(
                                                    model = item.url,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                if (item.type == "video") {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color.Black.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    "list" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayedItems) { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onMediaClick(item.url, item.type) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1E222A)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.type == "audio") {
                                                Text("🎤", fontSize = 24.sp)
                                            } else {
                                                AsyncImage(
                                                    model = item.url,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                if (item.type == "video") {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color.Black.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = when (item.type) {
                                                    "video" -> "🎥 Video File"
                                                    "audio" -> "🎤 Voice Message / Audio"
                                                    else -> "📷 Photo Image"
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Tap to view / play",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "slide" -> {
                        val pagerState = rememberPagerState { displayedItems.size }
                        val context = LocalContext.current
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                val item = displayedItems[pageIndex]
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.type == "audio") {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                                .padding(16.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(20.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("🎤 Voice Message / Audio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.height(16.dp))
                                                VoiceMessagePlayer(audioUrl = item.url)
                                            }
                                        }
                                    } else if (item.type == "video") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable { onMediaClick(item.url, item.type) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = item.url,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Black.copy(alpha = 0.6f),
                                                modifier = Modifier.size(64.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                                }
                                            }
                                        }
                                    } else {
                                        AsyncImage(
                                            model = item.url,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }

                            // Top overlay with Page Indicator and Download button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .align(Alignment.TopCenter),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Black.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${pagerState.currentPage + 1} / ${displayedItems.size}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }

                                val currentItem = displayedItems.getOrNull(pagerState.currentPage)
                                if (currentItem != null) {
                                    Button(
                                        onClick = {
                                            downloadMedia(context, currentItem.url, currentItem.type == "video")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Download")
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
