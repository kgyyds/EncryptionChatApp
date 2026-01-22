package com.kgapp.encryptionchat.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DebugPreferences {
    private const val PREFS_NAME = "debug_prefs"
    private const val KEY_DEBUG_ENABLED = "debug_enabled"
    private const val KEY_DEBUG_DETAILED = "debug_detailed"
    private const val KEY_MAX_LOGS = "debug_max_logs"

    @Volatile
    private var initialized = false

    private val _debugEnabled = MutableStateFlow(false)
    val debugEnabled: StateFlow<Boolean> = _debugEnabled.asStateFlow()

    private val _detailedLogs = MutableStateFlow(false)
    val detailedLogs: StateFlow<Boolean> = _detailedLogs.asStateFlow()

    private val _maxLogs = MutableStateFlow(2000)
    val maxLogs: StateFlow<Int> = _maxLogs.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _debugEnabled.value = prefs.getBoolean(KEY_DEBUG_ENABLED, false)
        _detailedLogs.value = prefs.getBoolean(KEY_DEBUG_DETAILED, false)
        _maxLogs.value = prefs.getInt(KEY_MAX_LOGS, 2000).coerceAtLeast(100)
        initialized = true
    }

    fun isDebugEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEBUG_ENABLED, false)

    fun isDetailedLogs(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEBUG_DETAILED, false)

    fun getMaxLogs(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_LOGS, 2000)
            .coerceAtLeast(100)

    fun setDebugEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEBUG_ENABLED, enabled)
            .apply()
        _debugEnabled.value = enabled
    }

    fun setDetailedLogs(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEBUG_DETAILED, enabled)
            .apply()
        _detailedLogs.value = enabled
    }

    fun setMaxLogs(context: Context, value: Int) {
        val normalized = value.coerceAtLeast(100)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MAX_LOGS, normalized)
            .apply()
        _maxLogs.value = normalized
    }
}
