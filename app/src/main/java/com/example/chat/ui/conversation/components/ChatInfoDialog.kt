package com.example.chat.ui.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.chat.data.model.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoDialog(
    recipientName: String,
    recipientPhoto: String,
    messages: List<Message>,
    onDismiss: () -> Unit,
    onOpenNickname: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onMediaClick: (String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Shared Media, 1: Chat Options

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(recipientName, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                if (recipientPhoto.isNotBlank()) {
                                    AsyncImage(model = recipientPhoto, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(recipientName.take(1).uppercase(), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = recipientName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Shared Media") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Chat Options") })
                    }

                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        when (selectedTab) {
                            0 -> {
                                SharedMediaFolderGallery(messages = messages, onMediaClick = onMediaClick)
                            }
                            1 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Chat Settings & Options", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenNickname()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("✏️ Edit Nickname")
                                    }

                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenTheme()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("🎨 Change Chat Theme")
                                    }

                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenCreateGroup()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("👥 Create Group with $recipientName")
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
