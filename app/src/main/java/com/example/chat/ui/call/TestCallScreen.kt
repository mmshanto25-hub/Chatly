package com.example.chat.ui.call

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Call
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
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.tasks.await
import java.util.Locale

const val AGORA_APP_ID = "babf1a2037454642a16ea4570feb3f75"
const val AGORA_TOKEN = "007eJxTYNj3VF190r6K7TEFipMaKu8XLLxnWCgjutJqpcGCk9MXLtylwJCUmJRmmGhkYGxuYmpiZmKUaGiWmmhiam6QlppknGZuWhP9PKshkJFhxmZRBkYoBPFZGEpSi0sYGACApiAo"
const val AGORA_CHANNEL = "test"

@Composable
fun TestCallScreen(
    channelName: String = AGORA_CHANNEL,
    callType: String = "video",
    onNavBack: () -> Unit
) {
    val context = LocalContext.current

    val isAudioCall = callType.equals("audio", ignoreCase = true)
    var isJoined by remember { mutableStateOf(false) }
    var remoteUid by remember { mutableStateOf<Int?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoDisabled by remember(callType) { mutableStateOf(isAudioCall) }
    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }

    var otherPersonName by remember { mutableStateOf("User") }
    var otherPersonPhoto by remember { mutableStateOf("") }

    // Fetch other user's name and photo from Firestore call document
    LaunchedEffect(channelName) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("calls")
                .whereEqualTo("channelName", channelName)
                .get()
                .await()
            val doc = snapshot.documents.firstOrNull()
            if (doc != null) {
                val callerId = doc.getString("callerId") ?: ""
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                if (callerId == currentUid) {
                    otherPersonName = doc.getString("receiverName") ?: "User"
                    otherPersonPhoto = doc.getString("receiverPhoto") ?: ""
                } else {
                    otherPersonName = doc.getString("callerName") ?: "User"
                    otherPersonPhoto = doc.getString("callerPhoto") ?: ""
                }
            }
        } catch (_: Exception) {}
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!audioGranted || (!isAudioCall && !cameraGranted)) {
            Toast.makeText(context, "Permissions are required for call", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(callType) {
        val audioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        val cameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (audioPermission != PackageManager.PERMISSION_GRANTED || (!isAudioCall && cameraPermission != PackageManager.PERMISSION_GRANTED)) {
            val perms = if (isAudioCall) arrayOf(Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            permissionLauncher.launch(perms)
        }

        try {
            val config = RtcEngineConfig().apply {
                mContext = context
                mAppId = AGORA_APP_ID
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        remoteUid = uid
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        if (remoteUid == uid) {
                            remoteUid = null
                        }
                    }

                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        isJoined = true
                    }
                }
            }
            val engine = RtcEngine.create(config)
            if (isAudioCall) {
                engine.disableVideo()
                engine.muteLocalVideoStream(true)
                isVideoDisabled = true
            } else {
                engine.enableVideo()
                engine.startPreview()
                isVideoDisabled = false
            }
            rtcEngine = engine

            // Auto-join channel immediately without requiring manual button tap
            try {
                engine.joinChannel(AGORA_TOKEN, channelName, "", 0)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to initialize Agora Engine: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                rtcEngine?.leaveChannel()
                rtcEngine?.stopPreview()
                RtcEngine.destroy()
            } catch (_: Exception) {}
        }
    }

    fun leaveCall() {
        val engine = rtcEngine ?: return
        try {
            engine.leaveChannel()
            isJoined = false
            remoteUid = null
            onNavBack()
        } catch (_: Exception) {
            onNavBack()
        }
    }

    if (isAudioCall) {
        AudioCallScreen(
            otherPersonName = otherPersonName,
            otherPersonPhoto = otherPersonPhoto,
            isJoined = isJoined,
            remoteUid = remoteUid,
            isMuted = isMuted,
            onToggleMute = {
                isMuted = !isMuted
                rtcEngine?.muteLocalAudioStream(isMuted)
            },
            onEndCall = { leaveCall() },
            onNavBack = { leaveCall() }
        )
    } else {
        VideoCallScreen(
            otherPersonName = otherPersonName,
            otherPersonPhoto = otherPersonPhoto,
            isJoined = isJoined,
            remoteUid = remoteUid,
            isMuted = isMuted,
            isVideoDisabled = isVideoDisabled,
            rtcEngine = rtcEngine,
            onToggleMute = {
                isMuted = !isMuted
                rtcEngine?.muteLocalAudioStream(isMuted)
            },
            onToggleVideo = {
                isVideoDisabled = !isVideoDisabled
                rtcEngine?.muteLocalVideoStream(isVideoDisabled)
            },
            onSwitchCamera = {
                rtcEngine?.switchCamera()
            },
            onEndCall = { leaveCall() },
            onNavBack = { leaveCall() }
        )
    }
}

@Composable
fun VideoCallScreen(
    otherPersonName: String,
    otherPersonPhoto: String,
    isJoined: Boolean,
    remoteUid: Int?,
    isMuted: Boolean,
    isVideoDisabled: Boolean,
    rtcEngine: RtcEngine?,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onNavBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full Screen Remote Video View
        if (remoteUid != null && rtcEngine != null) {
            AndroidView(
                factory = { ctx ->
                    val surfaceView = SurfaceView(ctx)
                    rtcEngine.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid))
                    surfaceView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
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
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        text = if (isJoined) "Waiting for $otherPersonName..." else "Connecting...",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Top Bar: Square shape box for name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onNavBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Square shape box for name at the top
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.height(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = otherPersonName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(40.dp))
        }

        // Small PiP Local Video Preview (Top Right Corner)
        if (rtcEngine != null && !isVideoDisabled) {
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 150.dp)
                    .padding(top = 80.dp, end = 16.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.DarkGray)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val surfaceView = SurfaceView(ctx)
                        rtcEngine.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                        surfaceView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom Circular Controls (WhatsApp / IMO style)
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            // Mute Button
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(if (isMuted) Color.Red else Color.White.copy(alpha = 0.25f))
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Camera On/Off Button
            IconButton(
                onClick = onToggleVideo,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(if (isVideoDisabled) Color.Red else Color.White.copy(alpha = 0.25f))
            ) {
                Icon(
                    imageVector = if (isVideoDisabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                    contentDescription = "Camera",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Switch Camera
            IconButton(
                onClick = onSwitchCamera,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Icon(
                    imageVector = Icons.Default.SwitchCamera,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // End Call (Red)
            IconButton(
                onClick = onEndCall,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun AudioCallScreen(
    otherPersonName: String,
    otherPersonPhoto: String,
    isJoined: Boolean,
    remoteUid: Int?,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
    onNavBack: () -> Unit
) {
    var isSpeakerOn by remember { mutableStateOf(true) }
    val context = LocalContext.current

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
                text = if (isJoined && remoteUid != null) "Connected"
                       else if (isJoined) "Ringing..."
                       else "Connecting...",
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
