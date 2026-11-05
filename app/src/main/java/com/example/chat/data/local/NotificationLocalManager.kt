package com.example.chat.data.local

import android.content.Context
import com.example.chat.ChatApplication
import com.example.chat.data.model.AppNotification

object NotificationLocalManager {
    private const val PREFS_PREFIX = "chatly_notifications_"
    private const val KEY_READ_IDS = "read_notification_ids"
    private const val KEY_LAST_READ_TIMESTAMP = "last_read_timestamp"

    private fun getPrefs(context: Context, userId: String) =
        context.getSharedPreferences("${PREFS_PREFIX}$userId", Context.MODE_PRIVATE)

    fun getReadNotificationIds(
        context: Context = ChatApplication.instance,
        userId: String
    ): Set<String> {
        if (userId.isBlank()) return emptySet()
        return getPrefs(context, userId).getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
    }
    fun getLastReadTimestamp(
        context: Context = ChatApplication.instance,
        userId: String
    ): Long {
        if (userId.isBlank()) return 0L
        return getPrefs(context, userId).getLong(KEY_LAST_READ_TIMESTAMP, 0L)
    }

    fun isNotificationRead(
        context: Context = ChatApplication.instance,
        userId: String,
        notificationId: String,
        createdAt: Long
    ): Boolean {
        if (userId.isBlank()) return false
        val prefs = getPrefs(context, userId)
        val readIds = prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
        if (notificationId.isNotBlank() && readIds.contains(notificationId)) {
            return true
        }
        val lastReadTimestamp = prefs.getLong(KEY_LAST_READ_TIMESTAMP, 0L)
        if (lastReadTimestamp > 0L && createdAt > 0L && createdAt <= lastReadTimestamp) {
            return true
        }
        return false
    }

    fun markAllRead(
        context: Context = ChatApplication.instance,
        userId: String,
        notifications: List<AppNotification>
    ) {
        if (userId.isBlank()) return
        val prefs = getPrefs(context, userId)
        val existingIds = prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
        val updatedIds = HashSet(existingIds)

        var maxCreatedAt = 0L
        for (notif in notifications) {
            if (notif.notificationId.isNotBlank()) {
                updatedIds.add(notif.notificationId)
            }
            if (notif.createdAt > maxCreatedAt) {
                maxCreatedAt = notif.createdAt
            }
        }
        val now = System.currentTimeMillis()
        val newLastReadTimestamp = maxOf(now, maxCreatedAt)
        val existingLastRead = prefs.getLong(KEY_LAST_READ_TIMESTAMP, 0L)
        val finalLastReadTimestamp = maxOf(existingLastRead, newLastReadTimestamp)

        prefs.edit()
            .putStringSet(KEY_READ_IDS, updatedIds)
            .putLong(KEY_LAST_READ_TIMESTAMP, finalLastReadTimestamp)
            .apply()
    }

    fun markNotificationRead(
        context: Context = ChatApplication.instance,
        userId: String,
        notificationId: String
    ) {
        if (userId.isBlank() || notificationId.isBlank()) return
        val prefs = getPrefs(context, userId)
        val existingIds = prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
        val updatedIds = HashSet(existingIds).apply { add(notificationId) }
        prefs.edit()
            .putStringSet(KEY_READ_IDS, updatedIds)
            .apply()
    }
}
