package com.kgapp.encryptionchat.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object MessageUpdateBus {
    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val updates: SharedFlow<String> = _updates

    fun emit(uid: String) {
        _updates.tryEmit(uid)
    }
}
