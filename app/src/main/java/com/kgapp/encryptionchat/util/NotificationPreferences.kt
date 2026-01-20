package com.kgapp.encryptionchat.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NotificationPreviewMode(val storageValue: Int) {
    SHOW_PREVIEW(0),
    TITLE_ONLY(1);

    companion object {
        fun fromStorage(value: Int): NotificationPreviewMode =
            entries.firstOrNull { it.storageValue == value } ?: SHOW_PREVIEW
    }
}

object NotificationPreferences {
    private const val PREFS_NAME = "notification_prefs"
    private const val KEY_ENABLE_NOTIFICATIONS = "enable_notifications"
    private const val KEY_ENABLE_BACKGROUND = "enable_background_receive"
    private const val KEY_START_ON_BOOT = "start_on_boot"
    private const val KEY_PREVIEW_MODE = "preview_mode"
    private const val KEY_ASKED_POST_NOTIFICATIONS = "asked_post_notifications"

    @Volatile
    private var initialized = false

    private val _enableNotifications = MutableStateFlow(true)
    val enableNotifications: StateFlow<Boolean> = _enableNotifications.asStateFlow()

    private val _enableBackgroundReceive = MutableStateFlow(false)
    val enableBackgroundReceive: StateFlow<Boolean> = _enableBackgroundReceive.asStateFlow()

    private val _startOnBoot = MutableStateFlow(false)
    val startOnBoot: StateFlow<Boolean> = _startOnBoot.asStateFlow()

    private val _previewMode = MutableStateFlow(NotificationPreviewMode.SHOW_PREVIEW)
    val previewMode: StateFlow<NotificationPreviewMode> = _previewMode.asStateFlow()

    private val _askedPostNotifications = MutableStateFlow(false)
    val askedPostNotifications: StateFlow<Boolean> = _askedPostNotifications.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _enableNotifications.value = prefs.getBoolean(KEY_ENABLE_NOTIFICATIONS, true)
        _enableBackgroundReceive.value = prefs.getBoolean(KEY_ENABLE_BACKGROUND, false)
        _startOnBoot.value = prefs.getBoolean(KEY_START_ON_BOOT, false)
        _previewMode.value = NotificationPreviewMode.fromStorage(
            prefs.getInt(KEY_PREVIEW_MODE, NotificationPreviewMode.SHOW_PREVIEW.storageValue)
        )
        _askedPostNotifications.value = prefs.getBoolean(KEY_ASKED_POST_NOTIFICATIONS, false)
        initialized = true
    }

    fun isBackgroundReceiveEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLE_BACKGROUND, false)

    fun isStartOnBootEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_START_ON_BOOT, false)

    fun isNotificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLE_NOTIFICATIONS, true)

    fun getPreviewMode(context: Context): NotificationPreviewMode =
        NotificationPreviewMode.fromStorage(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_PREVIEW_MODE, NotificationPreviewMode.SHOW_PREVIEW.storageValue)
        )

    fun hasAskedPostNotifications(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASKED_POST_NOTIFICATIONS, false)

    fun setEnableNotifications(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLE_NOTIFICATIONS, enabled)
            .apply()
        _enableNotifications.value = enabled
    }

    fun setEnableBackgroundReceive(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLE_BACKGROUND, enabled)
            .apply()
        _enableBackgroundReceive.value = enabled
    }

    fun setStartOnBoot(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_START_ON_BOOT, enabled)
            .apply()
        _startOnBoot.value = enabled
    }

    fun setPreviewMode(context: Context, mode: NotificationPreviewMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PREVIEW_MODE, mode.storageValue)
            .apply()
        _previewMode.value = mode
    }

    fun setAskedPostNotifications(context: Context, asked: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ASKED_POST_NOTIFICATIONS, asked)
            .apply()
        _askedPostNotifications.value = asked
    }
}
