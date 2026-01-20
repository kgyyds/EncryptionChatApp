package com.kgapp.encryptionchat.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ApiSettingsPreferences {
    private const val PREFS_NAME = "api_settings_prefs"
    private const val KEY_BASE_URL = "api_base_url"
    const val DEFAULT_BASE_URL = "http://47.113.126.123:8890/api/api2.php"

    private val _baseUrl = MutableStateFlow(DEFAULT_BASE_URL)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _baseUrl.value = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, baseUrl: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, baseUrl)
            .apply()
        _baseUrl.value = baseUrl
    }
}
