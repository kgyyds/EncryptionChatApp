package com.kgapp.encryptionchat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kgapp.encryptionchat.util.ThemeMode

@Composable
fun EncryptionChatTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF00D4FF),
            secondary = Color(0xFF7B61FF),
            background = Color(0xFF0B0B0F),
            surface = Color(0xFF14141A),
            surfaceVariant = Color(0xFF1C1C24),
            onPrimary = Color.Black,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFE0E0E0)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF07C160),
            secondary = Color(0xFF4CAF50),
            background = Color(0xFFF6F7F9),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE9EBEF),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF111318),
            onSurface = Color(0xFF111318),
            onSurfaceVariant = Color(0xFF2E3138)
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
