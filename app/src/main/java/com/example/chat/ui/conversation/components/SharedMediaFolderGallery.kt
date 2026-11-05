package com.example.chat.ui.conversation.components

import android.media.MediaPlayer
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat.data.model.Message
import kotlinx.coroutines.delay
import java.util.Locale

data class MediaItem(
    val url: String,
    val type: String,
    val timestamp: Long = 0L
)

@Composable
fun SharedMediaFolderGallery(
    messages: List<Message>,
    onMediaClick: (String, String) -> Unit,
    headerContent: LazyListScope.() -> Unit = {}
) {
    var selectedFolder by remember { mutableStateOf("all") }
    var selectedMediaIndex by remember { mutableIntStateOf(-1) }
    var activeAudioUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val allMediaItems = remember(messages) {
        messages.flatMap { msg ->
            when (msg.messageType) {
                "media_group" -> msg.attachmentUrls.map { MediaItem(it, "image", msg.createdAt) }
                "image" -> if (msg.attachmentUrl.isNotBlank()) {
                    listOf(MediaItem(msg.attachmentUrl, "image", msg.createdAt))
                } else {
                    emptyList()
                }
                "video" -> if (msg.attachmentUrl.isNotBlank()) {
                    listOf(MediaItem(msg.attachmentUrl, "video", msg.createdAt))
                } else {
                    emptyList()
                }
                "audio" -> if (msg.attachmentUrl.isNotBlank()) {
                    listOf(MediaItem(msg.attachmentUrl, "audio", msg.createdAt))
                } else {
                    emptyList()
                }
                else -> emptyList()
            }
        }.distinctBy { it.url }.reversed()
    }

    val displayedItems = remember(allMediaItems, selectedFolder) {
        when (selectedFolder) {
            "images" -> allMediaItems.filter { it.type == "image" }
            "videos" -> allMediaItems.filter { it.type == "video" }
            "audio" -> allMediaItems.filter { it.type == "audio" }
            else -> allMediaItems.filter { it.type != "audio" }
        }
    }
    val displayedVisualItems = remember(displayedItems) {
        displayedItems.filter { it.type == "image" || it.type == "video" }
    }

    BackHandler(enabled = selectedMediaIndex >= 0) {
        selectedMediaIndex = -1
    }

    if (selectedMediaIndex >= 0 && selectedMediaIndex < displayedVisualItems.size) {
        val pagerState = rememberPagerState(initialPage = selectedMediaIndex) {
            displayedVisualItems.size
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val item = displayedVisualItems[pageIndex]
                if (item.type == "video") {
                    SharedMediaVideoPlayer(
                        url = item.url,
                        isActive = pageIndex == pagerState.currentPage
                    )
                } else {
                    FullScreenImageItem(imgUrl = item.url)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedMediaIndex = -1 },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Back to shared media", tint = Color.White)
                }

                Button(
                    onClick = {
                        val item = displayedVisualItems[pagerState.currentPage]
                        downloadMedia(context, item.url, item.type == "video")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Download")
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${displayedVisualItems.size}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            headerContent()

            item(key = "shared-media-folders") {
                ScrollableTabRow(
                    selectedTabIndex = when (selectedFolder) {
                        "images" -> 1
                        "videos" -> 2
                        "audio" -> 3
                        else -> 0
                    },
                    edgePadding = 16.dp
                ) {
                    Tab(
                        selected = selectedFolder == "all",
                        onClick = { selectedFolder = "all" },
                        text = { Text("📁 All (${allMediaItems.count { it.type != "audio" }})") }
                    )
                    Tab(
                        selected = selectedFolder == "images",
                        onClick = { selectedFolder = "images" },
                        text = { Text("📷 Images (${allMediaItems.count { it.type == "image" }})") }
                    )
                    Tab(
                        selected = selectedFolder == "videos",
                        onClick = { selectedFolder = "videos" },
                        text = { Text("🎥 Videos (${allMediaItems.count { it.type == "video" }})") }
                    )
                    Tab(
                        selected = selectedFolder == "audio",
                        onClick = { selectedFolder = "audio" },
                        text = { Text("🎤 Audio (${allMediaItems.count { it.type == "audio" }})") }
                    )
                }
            }

            item(key = "shared-media-count") {
                Text(
                    text = "${displayedItems.size} items",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (displayedItems.isEmpty()) {
                item(key = "shared-media-empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No media found in this folder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val rows = displayedItems.chunked(3)
                items(rows.size, key = { it }) { rowIndex ->
                    val rowItems = rows[rowIndex]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            if (item.type == "audio") {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                ) {
                                    SharedMediaAudioCard(
                                        audioUrl = item.url,
                                        isActive = activeAudioUrl == item.url,
                                        onPlayRequested = { activeAudioUrl = item.url },
                                        onPauseRequested = { activeAudioUrl = null },
                                        onPlaybackFinished = {
                                            if (activeAudioUrl == item.url) activeAudioUrl = null
                                        }
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E222A))
                                        .clickable {
                                            selectedMediaIndex = displayedVisualItems.indexOfFirst { it.url == item.url }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
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
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = "Play video",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
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
    }
}

@Composable
private fun SharedMediaAudioCard(
    audioUrl: String,
    isActive: Boolean,
    onPlayRequested: () -> Unit,
    onPauseRequested: () -> Unit,
    onPlaybackFinished: () -> Unit
) {
    val context = LocalContext.current
    val mediaPlayer = remember(audioUrl) { MediaPlayer() }
    var isPrepared by remember(audioUrl) { mutableStateOf(false) }
    var durationMs by remember(audioUrl) { mutableIntStateOf(0) }
    var positionMs by remember(audioUrl) { mutableIntStateOf(0) }

    DisposableEffect(mediaPlayer, audioUrl) {
        try {
            mediaPlayer.setOnPreparedListener { player ->
                durationMs = player.duration.coerceAtLeast(0)
                isPrepared = true
            }
            mediaPlayer.setOnCompletionListener { player ->
                positionMs = 0
                player.seekTo(0)
                onPlaybackFinished()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                isPrepared = false
                onPlaybackFinished()
                Toast.makeText(context, "Unable to play this audio.", Toast.LENGTH_SHORT).show()
                true
            }
            mediaPlayer.setDataSource(audioUrl)
            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to load this audio.", Toast.LENGTH_SHORT).show()
            onPlaybackFinished()
        }

        onDispose {
            try {
                mediaPlayer.stop()
            } catch (_: IllegalStateException) {
            }
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isActive, isPrepared) {
        if (!isPrepared) return@LaunchedEffect
        try {
            if (isActive) {
                if (positionMs >= durationMs && durationMs > 0) {
                    mediaPlayer.seekTo(0)
                    positionMs = 0
                }
                mediaPlayer.start()
                while (isActive && mediaPlayer.isPlaying) {
                    positionMs = mediaPlayer.currentPosition.coerceAtLeast(0)
                    delay(200)
                }
            } else if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                positionMs = mediaPlayer.currentPosition.coerceAtLeast(0)
            }
        } catch (_: IllegalStateException) {
            onPlaybackFinished()
        }
    }

    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                if (!isPrepared) {
                    Toast.makeText(context, "Audio is loading...", Toast.LENGTH_SHORT).show()
                } else if (isActive) {
                    onPauseRequested()
                } else {
                    onPlayRequested()
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1B2332),
        border = BorderStroke(1.dp, Color(0xFF8A78F0).copy(alpha = 0.3f)),
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF28334A), Color(0xFF171D2A))
                    )
                )
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF9B8AFB), Color(0xFF6554D9))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isActive) "Pause audio" else "Play audio",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFB8AEFF),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Audio",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = Color(0xFFAA9CFF),
                    trackColor = Color.White.copy(alpha = 0.18f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatAudioTime(positionMs),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (durationMs > 0) formatAudioTime(durationMs) else "--:--",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun formatAudioTime(durationMs: Int): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun SharedMediaVideoPlayer(url: String, isActive: Boolean) {
    var videoView by remember(url) { mutableStateOf<VideoView?>(null) }
    var isPrepared by remember(url) { mutableStateOf(false) }
    var shouldPlay by remember(url) { mutableStateOf(true) }

    LaunchedEffect(videoView, isActive, isPrepared, shouldPlay) {
        val view = videoView ?: return@LaunchedEffect
        if (isPrepared && isActive && shouldPlay) {
            view.start()
        } else {
            view.pause()
        }
    }

    DisposableEffect(videoView) {
        onDispose {
            videoView?.stopPlayback()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                VideoView(viewContext).also { view ->
                    videoView = view
                    view.setVideoURI(url.toUri())
                    view.setOnPreparedListener { player ->
                        player.isLooping = true
                        isPrepared = true
                    }
                }
            }
        )

        IconButton(
            onClick = { shouldPlay = !shouldPlay },
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
        ) {
            Icon(
                imageVector = if (shouldPlay && isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (shouldPlay && isActive) "Pause video" else "Play video",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
