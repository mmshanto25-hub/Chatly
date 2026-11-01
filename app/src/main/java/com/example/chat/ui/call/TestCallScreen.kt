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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

const val AGORA_APP_ID = "babf1a2037454642a16ea4570feb3f75"
const val AGORA_TOKEN = "007eJxTYNj3VF190r6K7TEFipMaKu8XLLxnWCgjutJqpcGCk9MXLtylwJCUmJRmmGhkYGxuYmpiZmKUaGiWmmhiam6QlppknGZuWhP9PKshkJFhxmZRBkYoBPFZGEpSi0sYGACApiAo"
const val AGORA_CHANNEL = "test"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestCallScreen(
    onNavBack: () -> Unit
) {
    val context = LocalContext.current

    var isJoined by remember { mutableStateOf(false) }
    var remoteUid by remember { mutableStateOf<Int?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoDisabled by remember { mutableStateOf(false) }
    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!audioGranted || !cameraGranted) {
            Toast.makeText(context, "Camera & Audio permissions are required for call", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val audioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        val cameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (audioPermission != PackageManager.PERMISSION_GRANTED || cameraPermission != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            )
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
            engine.enableVideo()
            engine.startPreview()
            rtcEngine = engine
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

    fun joinCall() {
        val engine = rtcEngine ?: return
        try {
            engine.joinChannel(AGORA_TOKEN, AGORA_CHANNEL, "", 0)
        } catch (e: Exception) {
            Toast.makeText(context, "Error joining call: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun leaveCall() {
        val engine = rtcEngine ?: return
        try {
            engine.leaveChannel()
            isJoined = false
            remoteUid = null
            Toast.makeText(context, "Left call", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error leaving call: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Agora Test Call", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Channel: $AGORA_CHANNEL", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            // Remote Video View
            if (remoteUid != null && rtcEngine != null) {
                AndroidView(
                    factory = { ctx ->
                        val surfaceView = SurfaceView(ctx)
                        rtcEngine?.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid!!))
                        surfaceView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E222A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isJoined) "Waiting for remote user to join..." else "Tap Join Call to start",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            // Local Video View
            if (rtcEngine != null && !isVideoDisabled) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 160.dp)
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.DarkGray)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val surfaceView = SurfaceView(ctx)
                            rtcEngine?.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                            surfaceView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Call Controls at Bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isJoined) {
                    Button(
                        onClick = { joinCall() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Join Call", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Toggle Mute
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                rtcEngine?.muteLocalAudioStream(isMuted)
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) Color.Red else Color.DarkGray)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = Color.White
                            )
                        }

                        // Leave Call Button
                        IconButton(
                            onClick = { leaveCall() },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Leave Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Toggle Camera
                        IconButton(
                            onClick = {
                                isVideoDisabled = !isVideoDisabled
                                rtcEngine?.muteLocalVideoStream(isVideoDisabled)
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isVideoDisabled) Color.Red else Color.DarkGray)
                        ) {
                            Icon(
                                imageVector = if (isVideoDisabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                contentDescription = "Video",
                                tint = Color.White
                            )
                        }

                        // Switch Camera
                        IconButton(
                            onClick = {
                                rtcEngine?.switchCamera()
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwitchCamera,
                                contentDescription = "Switch Camera",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
