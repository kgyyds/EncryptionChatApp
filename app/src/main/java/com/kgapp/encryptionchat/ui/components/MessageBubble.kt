package com.kgapp.encryptionchat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    text: String,
    speaker: Int,
    timestamp: String?,
    avatarText: String?,
    onLongPress: (() -> Unit)? = null
) {
    val isMine = speaker == 0
    val isSystem = speaker == 2
    val colors = MaterialTheme.colorScheme
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
        horizontalArrangement = when {
            isSystem -> Arrangement.Center
            isMine -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isSystem && !isMine) {
            Avatar(
                text = avatarText,
                backgroundColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(14.dp),
            border = border,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = { onLongPress?.invoke() }
            )
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
        if (!isSystem && isMine) {
            Spacer(modifier = Modifier.size(8.dp))
            Avatar(
                text = avatarText,
                backgroundColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun Avatar(
    text: String?,
    backgroundColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .padding(0.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(color = backgroundColor, shape = CircleShape) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text?.trim().orEmpty().ifBlank { "?" }.take(1),
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
