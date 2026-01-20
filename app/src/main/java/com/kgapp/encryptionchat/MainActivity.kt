package com.kgapp.encryptionchat

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.api.Api4Client
import com.kgapp.encryptionchat.data.crypto.CryptoManager
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.data.sync.MessageSyncRegistry
import com.kgapp.encryptionchat.data.storage.FileStorage
import com.kgapp.encryptionchat.notifications.MessageSyncService
import com.kgapp.encryptionchat.util.ApiSettingsPreferences
import com.kgapp.encryptionchat.util.NotificationPreferences
import com.kgapp.encryptionchat.util.ThemeMode
import com.kgapp.encryptionchat.util.ThemePreferences
import com.kgapp.encryptionchat.util.TimeDisplayPreferences
import com.kgapp.encryptionchat.util.UnreadCounter
import android.Manifest
import android.os.Build

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.initialize(this)
        ApiSettingsPreferences.initialize(this)
        TimeDisplayPreferences.initialize(this)
        UnreadCounter.initialize(this)
        NotificationPreferences.initialize(this)
        val storage = FileStorage(this)
        val crypto = CryptoManager(storage)
        val api = Api4Client(
            crypto = crypto,
            baseUrlProvider = { ApiSettingsPreferences.getBaseUrl(this) }
        )
        val repository = ChatRepository(storage, crypto, api)
        val messageSyncManager = MessageSyncManager(repository, applicationContext, api)
        MessageSyncRegistry.bind(messageSyncManager)
        if (NotificationPreferences.isBackgroundReceiveEnabled(this)) {
            MessageSyncService.start(this)
        } else {
            messageSyncManager.ensureBroadcastSseRunning()
        }
        setContent {
            val themeMode by ThemePreferences.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            SideEffect {
                if (darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.BLACK),
                        navigationBarStyle = SystemBarStyle.dark(Color.BLACK)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
                        navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
                    )
                }
            }
            EncryptionChatTheme(themeMode) {
                NotificationPermissionPrompt()
                EncryptionChatApp(repository, messageSyncManager)
            }
        }
    }
}

@Composable
private fun NotificationPermissionPrompt() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val hasPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val asked = NotificationPreferences.askedPostNotifications.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission.value = granted
    }

    LaunchedEffect(Unit) {
        hasPermission.value = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    if (!hasPermission.value && !asked.value) {
        AlertDialog(
            onDismissRequest = { NotificationPreferences.setAskedPostNotifications(context, true) },
            title = { Text("开启通知") },
            text = { Text("允许通知后可在后台接收消息提醒。") },
            confirmButton = {
                TextButton(onClick = {
                    NotificationPreferences.setAskedPostNotifications(context, true)
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) {
                    Text("去开启")
                }
            },
            dismissButton = {
                TextButton(onClick = { NotificationPreferences.setAskedPostNotifications(context, true) }) {
                    Text("稍后")
                }
            }
        )
    }
}
