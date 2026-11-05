package com.example.chat.ui.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

@Composable
fun MediaGroupGrid(
    urls: List<String>,
    onImageClick: (String) -> Unit,
    onMoreClick: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val displayUrls = urls.take(4)
    val extraCount = urls.size - 4

    Surface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(260.dp)
            .height(260.dp),
        color = Color(0xFF1E222A)
    ) {
        if (displayUrls.size == 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(displayUrls[0]) {
                        detectTapGestures(
                            onTap = { onImageClick(displayUrls[0]) },
                            onDoubleTap = { onDoubleTap() }
                        )
                    }
            ) {
                AsyncImage(
                    model = displayUrls[0],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        } else if (displayUrls.size == 2) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(displayUrls[0]) {
                            detectTapGestures(
                                onTap = { onImageClick(displayUrls[0]) },
                                onDoubleTap = { onDoubleTap() }
                            )
                        }
                ) {
                    AsyncImage(
                        model = displayUrls[0],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(displayUrls[1]) {
                            detectTapGestures(
                                onTap = { onImageClick(displayUrls[1]) },
                                onDoubleTap = { onDoubleTap() }
                            )
                        }
                ) {
                    AsyncImage(
                        model = displayUrls[1],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(displayUrls[0]) {
                                detectTapGestures(
                                    onTap = { onImageClick(displayUrls[0]) },
                                    onDoubleTap = { onDoubleTap() }
                                )
                            }
                    ) {
                        AsyncImage(
                            model = displayUrls[0],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(displayUrls[1]) {
                                detectTapGestures(
                                    onTap = { onImageClick(displayUrls[1]) },
                                    onDoubleTap = { onDoubleTap() }
                                )
                            }
                    ) {
                        AsyncImage(
                            model = displayUrls[1],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(displayUrls[2]) {
                                detectTapGestures(
                                    onTap = { onImageClick(displayUrls[2]) },
                                    onDoubleTap = { onDoubleTap() }
                                )
                            }
                    ) {
                        AsyncImage(
                            model = displayUrls[2],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(displayUrls[3], extraCount) {
                                detectTapGestures(
                                    onTap = {
                                        if (extraCount > 0) {
                                            onMoreClick()
                                        } else {
                                            onImageClick(displayUrls[3])
                                        }
                                    },
                                    onDoubleTap = { onDoubleTap() }
                                )
                            }
                    ) {
                        if (displayUrls.size > 3) {
                            AsyncImage(
                                model = displayUrls[3],
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (extraCount > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+$extraCount",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumGridDialog(
    urls: List<String>,
    onDismiss: () -> Unit
) {
    var selectedImageIndex by remember(urls) { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = {
            if (selectedImageIndex != null) {
                selectedImageIndex = null
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val imageIndex = selectedImageIndex
            if (imageIndex == null) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Media Album (${urls.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                            navigationIcon = {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val rows = urls.chunked(3)
                        items(rows.size) { rowIndex ->
                            val rowItems = rows[rowIndex]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for ((colIndex, url) in rowItems.withIndex()) {
                                    val absoluteIndex = rowIndex * 3 + colIndex
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E222A))
                                            .clickable { selectedImageIndex = absoluteIndex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                val pagerState = rememberPagerState(initialPage = imageIndex) { urls.size }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        FullScreenImageItem(imgUrl = urls[pageIndex])
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedImageIndex = null },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Back to album", tint = Color.White)
                        }

                        Button(
                            onClick = { downloadImage(context, urls[pagerState.currentPage]) },
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
                            text = "${pagerState.currentPage + 1} / ${urls.size}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
