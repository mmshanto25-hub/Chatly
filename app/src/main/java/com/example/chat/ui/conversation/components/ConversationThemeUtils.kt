package com.example.chat.ui.conversation.components

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.ui.graphics.Color

fun formatLastSeen(presence: String, lastSeenAt: Long): String {
    if (presence.equals("online", ignoreCase = true)) return "online"
    if (lastSeenAt <= 0L) return "offline"
    val diff = System.currentTimeMillis() - lastSeenAt
    if (diff < 0) return "offline"

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    val months = days / 30
    val years = days / 365

    return when {
        minutes < 1 -> "last seen just now"
        minutes < 60 -> "last seen ${minutes}m ago"
        hours < 24 -> "last seen ${hours}h ago"
        days < 7 -> "last seen ${days}d ago"
        weeks < 5 -> "last seen ${weeks}w ago"
        months < 12 -> "last seen ${months}mo ago"
        else -> "last seen ${years}y ago"
    }
}

fun getThemeBubbleColor(theme: String, isMe: Boolean): Color {
    return when (theme) {
        "dark" -> if (isMe) Color(0xFF4E6178) else Color(0xFF313D4A)
        "green" -> if (isMe) Color(0xFFC8E6C9) else Color(0xFFE8F5E9)
        "orange" -> if (isMe) Color(0xFFFFE0B2) else Color(0xFFFFF3E0)
        "purple" -> if (isMe) Color(0xFFE1BEE7) else Color(0xFFF3E5F5)
        "pink" -> if (isMe) Color(0xFFF8BBD0) else Color(0xFFFCE4EC)
        else -> if (isMe) Color(0xFFD1E4FF) else Color(0xFFEFEFEF)
    }
}

fun getThemeBackgroundColor(theme: String): Color {
    return when (theme) {
        "dark" -> Color(0xFF222831)
        "green" -> Color(0xFFF1F8E9)
        "orange" -> Color(0xFFFFF8E1)
        "purple" -> Color(0xFFFCE4EC)
        "pink" -> Color(0xFFFFEBEE)
        else -> Color(0xFFF7F9FC)
    }
}

fun getThemeTopAppBarColor(theme: String): Color {
    return getThemeBackgroundColor(theme)
}

fun getThemeTopAppBarContentColor(theme: String): Color {
    return when (theme) {
        "dark" -> Color.White
        else -> Color(0xFF1A1A1A)
    }
}

fun getThemeBottomBarColor(theme: String): Color {
    return when (theme) {
        "dark" -> Color(0xFF2E3842)
        "green" -> Color(0xFFC8E6C9)
        "orange" -> Color(0xFFFFE0B2)
        "purple" -> Color(0xFFE8D5F5)
        "pink" -> Color(0xFFFCE4EC)
        else -> Color(0xFFF7F9FC)
    }
}

fun getThemeBottomBarContentColor(theme: String): Color {
    return when (theme) {
        "dark" -> Color.White
        else -> Color(0xFF1A1A1A)
    }
}

fun downloadMedia(context: Context, mediaUrl: String, isVideo: Boolean) {
    try {
        val ext = if (isVideo) "mp4" else "jpg"
        val title = if (isVideo) "Video File" else "Image File"
        val request = DownloadManager.Request(Uri.parse(mediaUrl))
            .setTitle(title)
            .setDescription("Downloading $title...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Chatly_${System.currentTimeMillis()}.$ext")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "Downloading $title to Downloads...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to download: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun downloadImage(context: Context, imageUrl: String) {
    downloadMedia(context, imageUrl, false)
}
