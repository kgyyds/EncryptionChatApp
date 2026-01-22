package com.kgapp.encryptionchat.data.sync

import android.content.Context
import com.kgapp.encryptionchat.util.DebugLevel
import com.kgapp.encryptionchat.util.DebugLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object MessageUpdateBus {
    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val updates: SharedFlow<String> = _updates

    fun emit(uid: String, context: Context? = null) {
        _updates.tryEmit(uid)
        context?.let {
            DebugLog.append(
                context = it,
                level = DebugLevel.INFO,
                tag = "UI",
                chatUid = uid,
                eventName = "emit_update",
                message = "emit"
            )
        }
    }
}
