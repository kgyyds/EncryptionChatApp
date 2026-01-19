package com.kgapp.encryptionchat.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object UnreadCounter {
    private const val PREFS_NAME = "unread_prefs"
    private const val KEY_UNREAD = "unread_map"
    private val json = Json { encodeDefaults = true }
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_UNREAD, "{}").orEmpty()
        val map = runCatching { json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
        _counts.value = map
    }

    fun increment(context: Context, uid: String) {
        val updated = _counts.value.toMutableMap()
        updated[uid] = (updated[uid] ?: 0) + 1
        update(context, updated)
    }

    fun clear(context: Context, uid: String) {
        val updated = _counts.value.toMutableMap()
        if (updated.remove(uid) != null) {
            update(context, updated)
        }
    }

    fun clearAll(context: Context) {
        update(context, emptyMap())
    }

    private fun update(context: Context, map: Map<String, Int>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UNREAD, json.encodeToString(map))
            .apply()
        _counts.value = map
    }
}
