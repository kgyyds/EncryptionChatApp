package com.kgapp.encryptionchat

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.api.Api2Client
import com.kgapp.encryptionchat.data.crypto.CryptoManager
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.data.storage.FileStorage
import com.kgapp.encryptionchat.util.ApiSettingsPreferences
import com.kgapp.encryptionchat.util.ThemeMode
import com.kgapp.encryptionchat.util.ThemePreferences
import com.kgapp.encryptionchat.util.TimeDisplayPreferences
import com.kgapp.encryptionchat.util.UnreadCounter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.initialize(this)
        ApiSettingsPreferences.initialize(this)
        TimeDisplayPreferences.initialize(this)
        UnreadCounter.initialize(this)
        val storage = FileStorage(this)
        val crypto = CryptoManager(storage)
        val api = Api2Client(
    crypto = crypto,
    baseUrlProvider = { ApiSettingsPreferences.getBaseUrl(this) }
)
        val repository = ChatRepository(storage, crypto, api)
        val messageSyncManager = MessageSyncManager(repository, applicationContext, api)
        messageSyncManager.ensureBroadcastSseRunning()
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
                EncryptionChatApp(repository, messageSyncManager)
            }
        }
    }
}
