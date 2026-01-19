package com.kgapp.encryptionchat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
fun MessageBubble(text: String, speaker: Int, timestamp: String?) {
    val isMine = speaker == 0
    val isSystem = speaker == 2
    val colors = MaterialTheme.colorScheme
    val alignment = when {
        isSystem -> Arrangement.Center
        isMine -> Arrangement.End
        else -> Arrangement.Start
    }
    val backgroundColor = when {
        isSystem -> colors.surfaceVariant
        isMine -> colors.primaryContainer
        else -> colors.secondaryContainer
    }
    val contentColor = when {
        isSystem -> colors.onSurfaceVariant
        isMine -> colors.onPrimaryContainer
        else -> colors.onSecondaryContainer
    }
    val isDark = colors.surface.luminance() < 0.5f
    val border = if (isDark && !isSystem) {
        BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f))
    } else {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = alignment,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(14.dp),
            border = border
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = text,
                    style = if (isSystem) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                if (!timestamp.isNullOrBlank()) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
