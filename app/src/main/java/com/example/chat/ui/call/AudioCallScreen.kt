package com.example.chat.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun AudioCallScreen(
    otherPersonName: String,
    otherPersonPhoto: String,
    callStatus: String,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
    onNavBack: () -> Unit
) {
    var isSpeakerOn by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val isAccepted = callStatus.equals("accepted", ignoreCase = true)
    var secondsElapsed by remember { mutableStateOf(0) }

    LaunchedEffect(isAccepted) {
        if (isAccepted) {
            secondsElapsed = 0
            while (true) {
                delay(1000)
                secondsElapsed++
            }
        }
    }

    val minutes = secondsElapsed / 60
    val seconds = secondsElapsed % 60
    val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E293B),
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            )
            .padding(24.dp)
    ) {
        // Top Back Button
        IconButton(
            onClick = onNavBack,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // Square shape box for name at the top (where red box is in screenshot)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.15f),
            shadowElevation = 4.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = otherPersonName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }

        // Center Profile Picture in Circle shape
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 12.dp
            ) {
                if (otherPersonPhoto.isNotBlank()) {
                    AsyncImage(
                        model = otherPersonPhoto,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = otherPersonName.take(1).uppercase(Locale.getDefault()),
                            fontWeight = FontWeight.Bold,
                            fontSize = 60.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isAccepted) timeFormatted else "Ringing...",
                fontSize = 16.sp,
                color = Color(0xFF9CA3AF),
                fontWeight = FontWeight.Medium
            )
        }

        // Bottom WhatsApp style circular buttons
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
        ) {
            // Speaker Toggle
            IconButton(
                onClick = {
                    isSpeakerOn = !isSpeakerOn
                    Toast.makeText(context, if (isSpeakerOn) "Speaker ON" else "Speaker OFF", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isSpeakerOn) Color.White.copy(alpha = 0.25f) else Color.DarkGray)
            ) {
                Icon(
                    imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = "Speaker",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Mute Toggle
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isMuted) Color.Red else Color.White.copy(alpha = 0.25f))
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // End Call (Red)
            IconButton(
                onClick = onEndCall,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}
