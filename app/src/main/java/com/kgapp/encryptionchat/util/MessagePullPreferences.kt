package com.kgapp.encryptionchat.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PullMode(val storageValue: Int) {
    MANUAL(0),
    CHAT_SSE(1),
    GLOBAL_SSE(2);

    companion object {
        fun fromStorage(value: Int): PullMode = entries.firstOrNull { it.storageValue == value } ?: CHAT_SSE
    }
}

object MessagePullPreferences {
    private const val PREFS_NAME = "message_pull_prefs"
    private const val KEY_PULL_MODE = "pull_mode"
    private val _mode = MutableStateFlow(PullMode.CHAT_SSE)
    val mode: StateFlow<PullMode> = _mode.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _mode.value = PullMode.fromStorage(prefs.getInt(KEY_PULL_MODE, PullMode.CHAT_SSE.storageValue))
    }

    fun setMode(context: Context, mode: PullMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PULL_MODE, mode.storageValue)
            .apply()
        _mode.value = mode
    }
}
