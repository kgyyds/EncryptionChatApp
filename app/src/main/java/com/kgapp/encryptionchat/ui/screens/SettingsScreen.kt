package com.kgapp.encryptionchat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.ui.viewmodel.SettingsViewModel
import com.kgapp.encryptionchat.util.MessagePullPreferences
import com.kgapp.encryptionchat.util.PullMode
import com.kgapp.encryptionchat.util.TimeDisplayPreferences
import com.kgapp.encryptionchat.util.TimeDisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: ChatRepository,
    messageSyncManager: MessageSyncManager,
    onOpenKeyManagement: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenSecurity: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val pullMode = MessagePullPreferences.mode.collectAsStateWithLifecycle()
    val timeMode = TimeDisplayPreferences.mode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SectionTitle("安全")
                SettingsRow(
                    title = "安全",
                    subtitle = "胁迫模式与解锁",
                    icon = Icons.Outlined.Security,
                    onClick = onOpenSecurity
                )
                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                SettingsRow(
                    title = "密钥管理",
                    subtitle = if (state.value.hasPrivateKey && state.value.hasPublicKey) "已配置" else "未配置",
                    icon = Icons.Outlined.Security,
                    onClick = onOpenKeyManagement
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SectionTitle("外观")
                SettingsRow(
                    title = "主题设置",
                    subtitle = "跟随系统/暗色/亮色",
                    icon = Icons.Outlined.Palette,
                    onClick = onOpenThemeSettings
                )
                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                TimeDisplayRow(
                    title = "消息时间显示",
                    selected = timeMode.value,
                    onSelect = { mode -> TimeDisplayPreferences.setMode(context, mode) }
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SectionTitle("消息拉取")
                PullModeRow(
                    title = "手动刷新",
                    selected = pullMode.value == PullMode.MANUAL,
                    onSelect = {
                        MessagePullPreferences.setMode(context, PullMode.MANUAL)
                        messageSyncManager.updateMode(PullMode.MANUAL, null)
                    }
                )
                PullModeRow(
                    title = "聊天 SSE",
                    selected = pullMode.value == PullMode.CHAT_SSE,
                    onSelect = {
                        MessagePullPreferences.setMode(context, PullMode.CHAT_SSE)
                        messageSyncManager.updateMode(PullMode.CHAT_SSE, null)
                    }
                )
                PullModeRow(
                    title = "全局 SSE",
                    selected = pullMode.value == PullMode.GLOBAL_SSE,
                    onSelect = {
                        MessagePullPreferences.setMode(context, PullMode.GLOBAL_SSE)
                        messageSyncManager.updateMode(PullMode.GLOBAL_SSE, null)
                    }
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SectionTitle("关于")
                SettingsRow(
                    title = "关于",
                    subtitle = "开发中",
                    icon = Icons.Outlined.Info,
                    onClick = { Toast.makeText(context, "开发中", Toast.LENGTH_SHORT).show() }
                )
            }
            Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = title)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall)
        }
        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
private fun PullModeRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun TimeDisplayRow(
    title: String,
    selected: TimeDisplayMode,
    onSelect: (TimeDisplayMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.padding(top = 8.dp)) {
            TimeDisplayOption("相对时间", selected == TimeDisplayMode.RELATIVE) {
                onSelect(TimeDisplayMode.RELATIVE)
            }
            TimeDisplayOption("绝对时间", selected == TimeDisplayMode.ABSOLUTE) {
                onSelect(TimeDisplayMode.ABSOLUTE)
            }
            TimeDisplayOption("自动", selected == TimeDisplayMode.AUTO) {
                onSelect(TimeDisplayMode.AUTO)
            }
        }
    }
}

@Composable
private fun TimeDisplayOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onSelect)
    }
}
