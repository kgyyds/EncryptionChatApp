package com.kgapp.encryptionchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.api.ChatApi
import com.kgapp.encryptionchat.data.crypto.CryptoManager
import com.kgapp.encryptionchat.data.storage.FileStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = FileStorage(this)
        val crypto = CryptoManager(storage)
        val api = ChatApi()
        val repository = ChatRepository(storage, crypto, api)
        setContent {
            EncryptionChatTheme {
                EncryptionChatApp(repository)
            }
        }
    }
}
