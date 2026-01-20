package com.kgapp.encryptionchat.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object MessageSyncRegistry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var manager: MessageSyncManager? = null

    fun bind(manager: MessageSyncManager) {
        this.manager = manager
    }

    fun stopAppBroadcast() {
        val target = manager ?: return
        scope.launch {
            target.stopBroadcastSse()
        }
    }

    fun ensureAppBroadcast() {
        val target = manager ?: return
        scope.launch {
            target.ensureBroadcastSseRunning()
        }
    }
}
