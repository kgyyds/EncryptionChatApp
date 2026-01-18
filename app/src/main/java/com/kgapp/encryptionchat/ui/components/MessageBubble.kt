package com.kgapp.encryptionchat.ui.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MessageBubble(text: String, speaker: Int, timestamp: String?) {
    val isMine = speaker == 0
    val isSystem = speaker == 2
    val alignment = when {
        isSystem -> Arrangement.Center
        isMine -> Arrangement.End
        else -> Arrangement.Start
    }
    val strokeColor = when {
        isSystem -> Color(0xFF5B5B5B)
        isMine -> Color(0xFF00D4FF)
        else -> Color(0xFF7B61FF)
    }
    val backgroundColor = Color(0xFF0F0F12)

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
            border = androidx.compose.foundation.BorderStroke(1.dp, strokeColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = text,
                    style = if (isSystem) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                if (!timestamp.isNullOrBlank()) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB0B0B0),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
