package com.kgapp.encryptionchat.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.PendingIntent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kgapp.encryptionchat.MainActivity
import com.kgapp.encryptionchat.R
import com.kgapp.encryptionchat.util.NotificationPreferences
import com.kgapp.encryptionchat.util.NotificationPreviewMode
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class AppNotifier(private val context: Context) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        
        val messageChannel = NotificationChannel(
    CHANNEL_MESSAGES,
    "消息通知",
    NotificationManager.IMPORTANCE_HIGH
).apply {
    description = "新消息提醒"
    enableVibration(true)
    setShowBadge(true)
    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
}
        
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "后台同步",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "后台保持消息同步"
            setSound(null, null)
        }
        manager.createNotificationChannel(messageChannel)
        manager.createNotificationChannel(serviceChannel)
    }

    fun notifyMessage(
        fromUid: String,
        title: String,
        preview: String,
        ts: Long,
        unreadCount: Int,
        locked: Boolean
    ) {
        if (!NotificationPreferences.isNotificationsEnabled(context)) {
            Log.d(TAG, "Notify skipped: notifications disabled")
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.d(TAG, "Notify skipped: system notifications disabled")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Notify skipped: POST_NOTIFICATIONS not granted")
            return
        }
        ensureChannels()

        val showPreview = !locked &&
            NotificationPreferences.getPreviewMode(context) == NotificationPreviewMode.SHOW_PREVIEW

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
    .setSmallIcon(R.mipmap.ic_launcher)
    .setContentTitle(title)
    .setAutoCancel(true)
    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
    .setWhen((ts.takeIf { it > 0L } ?: Instant.now().epochSecond) * 1000L)
    .setNumber(unreadCount)
    .setContentIntent(messagePendingIntent(fromUid))

    // ✅ 关键：保证横幅/响铃/震动（尤其在 Android < 8）
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setDefaults(NotificationCompat.DEFAULT_ALL)
        if (showPreview) {
            val style = NotificationCompat.MessagingStyle(title)
            val history = messageHistory.getOrPut(fromUid) { ArrayDeque() }
            history.addLast(preview)
            while (history.size > MAX_HISTORY) {
                history.removeFirst()
            }
            history.forEach { line ->
                style.addMessage(line, System.currentTimeMillis(), title)
            }
            builder.setStyle(style)
            builder.setContentText(preview)
        } else {
            if (locked) {
                builder.setContentTitle("来自${title}的新消息")
                builder.setContentText("打开应用查看")
            } else {
                builder.setContentText("你有新消息")
            }
        }

        NotificationManagerCompat.from(context).notify(notificationId(fromUid), builder.build())
    }

    fun notifyServiceRunning(): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("后台同步中")
            .setContentText("保持消息实时更新")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainPendingIntent())
            .build()
    }

    fun cancelConversationNotification(fromUid: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(fromUid))
    }

    private fun notificationId(fromUid: String): Int = fromUid.hashCode()

    private fun messagePendingIntent(fromUid: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHAT_UID, fromUid)
        }
        return PendingIntent.getActivity(
            context,
            notificationId(fromUid),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "AppNotifier"
        const val EXTRA_OPEN_CHAT_UID = "open_chat_uid"
        // ✅ 改成 v2，强制系统创建新 channel（旧 channel 的重要性改不了）
        private const val CHANNEL_MESSAGES = "channel_messages_v2"
        private const val CHANNEL_SERVICE = "channel_service"
        private const val GROUP_MESSAGES = "messages_group"
        private const val MAX_HISTORY = 5
        private val messageHistory = ConcurrentHashMap<String, ArrayDeque<String>>()
    }
}
