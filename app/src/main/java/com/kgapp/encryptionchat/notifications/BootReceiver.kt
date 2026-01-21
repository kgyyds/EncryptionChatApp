package com.kgapp.encryptionchat.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kgapp.encryptionchat.util.NotificationPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!NotificationPreferences.isBackgroundReceiveEnabled(context)) return
        if (!NotificationPreferences.isStartOnBootEnabled(context)) return
        val request = OneTimeWorkRequestBuilder<BootSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BOOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        private const val BOOT_WORK_NAME = "boot_message_sync"
    }
}
