package com.kgapp.encryptionchat.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.kgapp.encryptionchat.MainActivity
import com.kgapp.encryptionchat.R

class AppNotifier(private val context: Context) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ✅ 消息通知 Channel（高优先级，必横幅）
        val msgChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "消息通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "新消息提醒"
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(msgChannel)

        // ✅ 前台服务 Channel（低优先级，不打扰）
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "后台同步",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "后台保持消息同步"
            setSound(null, null)
        }
        manager.createNotificationChannel(serviceChannel)
    }

    fun notifyMessage(
        fromUid: String,
        fromName: String,
        preview: String,
        ts: Long
    ) {
        // Android 13+ 通知权限
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        ensureChannels()

        val person = Person.Builder()
            .setName(fromName)
            .build()

        val style = NotificationCompat.MessagingStyle(person)
            .addMessage(preview, ts * 1000L, person)

        val notif = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(fromName)
            .setContentText(preview)                 // ✅ 状态栏预览
            .setStyle(style)                         // ✅ 展开像 QQ
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // ✅ < Android 8
            .setDefaults(NotificationCompat.DEFAULT_ALL)   // ✅ 声音/震动/横幅
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(ts * 1000L)
            .setContentIntent(messagePendingIntent(fromUid))
            .build()

        // ✅ 每个会话一个 id，绝不和前台服务冲突
        val notifId = ("msg_$fromUid").hashCode()

        NotificationManagerCompat.from(context).notify(notifId, notif)
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

    private fun messagePendingIntent(fromUid: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHAT_UID, fromUid)
        }
        return PendingIntent.getActivity(
            context,
            fromUid.hashCode(),
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
        const val EXTRA_OPEN_CHAT_UID = "open_chat_uid"

        // ✅ v2：强制新建，避免旧 channel 被系统静默
        private const val CHANNEL_MESSAGES = "channel_messages_v2"
        private const val CHANNEL_SERVICE = "channel_service"
    }
}