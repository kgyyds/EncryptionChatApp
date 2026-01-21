package com.kgapp.encryptionchat.notifications

import android.app.ServiceInfo
import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.kgapp.encryptionchat.util.NotificationPreferences

class BootSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!NotificationPreferences.isBackgroundReceiveEnabled(applicationContext)) {
            return Result.success()
        }
        val notifier = AppNotifier(applicationContext)
        notifier.ensureChannels()
        val notification = notifier.notifyServiceRunning()
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                MessageSyncService.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(MessageSyncService.SERVICE_NOTIFICATION_ID, notification)
        }
        setForeground(info)
        MessageSyncService.start(applicationContext)
        return Result.success()
    }
}
