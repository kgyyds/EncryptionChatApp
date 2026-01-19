package com.kgapp.encryptionchat.util

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class ChatBackgroundOption(
    val id: String,
    val label: String,
    val brush: Brush
)

object ChatBackgrounds {
    fun options(): List<ChatBackgroundOption> = listOf(
        ChatBackgroundOption(
            id = "default",
            label = "默认",
            brush = Brush.verticalGradient(
                listOf(Color(0xFFF6F7FB), Color(0xFFF6F7FB))
            )
        ),
        ChatBackgroundOption(
            id = "sky",
            label = "晴空",
            brush = Brush.verticalGradient(
                listOf(Color(0xFFE3F2FD), Color(0xFFFFFFFF))
            )
        ),
        ChatBackgroundOption(
            id = "peach",
            label = "蜜桃",
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFF0E5), Color(0xFFFFFBF7))
            )
        ),
        ChatBackgroundOption(
            id = "forest",
            label = "森林",
            brush = Brush.verticalGradient(
                listOf(Color(0xFFE8F5E9), Color(0xFFFFFFFF))
            )
        )
    )

    fun brushFor(id: String): Brush {
        return options().firstOrNull { it.id == id }?.brush ?: options().first().brush
    }
}
