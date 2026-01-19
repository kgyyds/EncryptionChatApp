package com.kgapp.encryptionchat.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val storageValue: Int) {
    SYSTEM(0),
    DARK(1),
    LIGHT(2);

    companion object {
        fun fromStorage(value: Int): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

object ThemePreferences {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    @Volatile
    private var initialized = false
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _themeMode.value = ThemeMode.fromStorage(prefs.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.storageValue))
        initialized = true
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_MODE, mode.storageValue)
            .apply()
        _themeMode.value = mode
    }
}
