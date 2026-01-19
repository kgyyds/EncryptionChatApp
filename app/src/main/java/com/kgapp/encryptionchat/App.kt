package com.kgapp.encryptionchat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun EncryptionChatTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFF00D4FF),
        secondary = Color(0xFF7B61FF),
        background = Color(0xFF0B0B0F),
        surface = Color(0xFF0F0F12),
        onPrimary = Color.Black,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
