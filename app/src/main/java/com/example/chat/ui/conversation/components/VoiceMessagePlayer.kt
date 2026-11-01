package com.example.chat.ui.conversation.components

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun VoiceMessagePlayer(audioUrl: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var totalDuration by remember { mutableStateOf(0) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(audioUrl) {
        try {
            mediaPlayer.setDataSource(audioUrl)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener { mp ->
                totalDuration = mp.duration
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                currentPosition = 0
                try { mediaPlayer.seekTo(0) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        onDispose {
            try {
                mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                currentPosition = mediaPlayer.currentPosition
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(200)
        }
    }

    val displayTime = if (isPlaying || currentPosition > 0) {
        formatMillis(currentPosition)
    } else if (totalDuration > 0) {
        formatMillis(totalDuration)
    } else {
        "0:05"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
    ) {
        IconButton(
            onClick = {
                try {
                    if (isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                    } else {
                        mediaPlayer.start()
                        isPlaying = true
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot play audio: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val barHeights = listOf(
                10, 16, 22, 14, 26, 18, 12, 24, 20, 16, 28, 14, 22, 18, 26, 12, 20, 16, 24, 14, 18, 12
            )
            barHeights.forEachIndexed { index, height ->
                val active = (currentPosition.toFloat() / (totalDuration.takeIf { it > 0 } ?: 1)) >= (index.toFloat() / barHeights.size)
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(height.dp)
                        .clip(RoundedCornerShape(1.25.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        }

        Text(
            text = displayTime,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun formatMillis(millis: Int): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
