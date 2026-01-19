package com.kgapp.encryptionchat.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimeDisplayMode(val storageValue: Int) {
    RELATIVE(0),
    ABSOLUTE(1),
    AUTO(2);

    companion object {
        fun fromStorage(value: Int): TimeDisplayMode =
            entries.firstOrNull { it.storageValue == value } ?: RELATIVE
    }
}

object TimeDisplayPreferences {
    private const val PREFS_NAME = "time_display_prefs"
    private const val KEY_MODE = "time_display_mode"
    private val _mode = MutableStateFlow(TimeDisplayMode.RELATIVE)
    val mode: StateFlow<TimeDisplayMode> = _mode.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _mode.value = TimeDisplayMode.fromStorage(prefs.getInt(KEY_MODE, TimeDisplayMode.RELATIVE.storageValue))
    }

    fun setMode(context: Context, mode: TimeDisplayMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MODE, mode.storageValue)
            .apply()
        _mode.value = mode
    }
}
