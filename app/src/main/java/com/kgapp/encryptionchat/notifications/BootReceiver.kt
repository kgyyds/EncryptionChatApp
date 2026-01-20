package com.kgapp.encryptionchat.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kgapp.encryptionchat.util.NotificationPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!NotificationPreferences.isBackgroundReceiveEnabled(context)) return
        if (!NotificationPreferences.isStartOnBootEnabled(context)) return
        MessageSyncService.start(context)
    }
}
