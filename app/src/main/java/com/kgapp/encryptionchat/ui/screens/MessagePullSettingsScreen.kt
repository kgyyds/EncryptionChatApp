package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.util.MessagePullPreferences
import com.kgapp.encryptionchat.util.PullMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagePullSettingsScreen(
    messageSyncManager: MessageSyncManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mode by MessagePullPreferences.mode.collectAsState()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("消息拉取设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant)
                        .padding(vertical = 4.dp)
                ) {
                    PullModeOption(
                        title = "聊天 SSE",
                        selected = mode == PullMode.CHAT_SSE,
                        onSelect = {
                            MessagePullPreferences.setMode(context, PullMode.CHAT_SSE)
                            messageSyncManager.updateMode(PullMode.CHAT_SSE, null)
                        }
                    )
                    PullModeOption(
                        title = "全局 SSE",
                        selected = mode == PullMode.GLOBAL_SSE,
                        onSelect = {
                            MessagePullPreferences.setMode(context, PullMode.GLOBAL_SSE)
                            messageSyncManager.updateMode(PullMode.GLOBAL_SSE, null)
                        }
                    )
                    PullModeOption(
                        title = "手动刷新",
                        selected = mode == PullMode.MANUAL,
                        onSelect = {
                            MessagePullPreferences.setMode(context, PullMode.MANUAL)
                            messageSyncManager.updateMode(PullMode.MANUAL, null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PullModeOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        RadioButton(selected = selected, onClick = onSelect)
    }
}
