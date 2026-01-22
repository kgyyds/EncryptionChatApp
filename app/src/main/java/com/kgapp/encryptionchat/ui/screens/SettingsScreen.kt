package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.ui.viewmodel.SettingsViewModel
import com.kgapp.encryptionchat.util.TimeDisplayPreferences
import com.kgapp.encryptionchat.util.TimeDisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: ChatRepository,
    onOpenKeyManagement: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenTimeDisplay: () -> Unit,
    onOpenApiSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenDebugSettings: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val timeMode = TimeDisplayPreferences.mode.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        val groupBackground = MaterialTheme.colorScheme.surfaceVariant
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
                colors = cardColors
            ) {
                SectionTitle("安全")
                EntryRow(
                    title = "应用锁与胁迫模式",
                    subtitle = "PIN 解锁与胁迫模式设置",
                    icon = Icons.Outlined.Security,
                    background = groupBackground,
                    onClick = onOpenSecurity
                )
                EntryRow(
                    title = "密钥管理",
                    subtitle = if (state.value.hasPrivateKey && state.value.hasPublicKey) "已配置" else "未配置",
                    icon = Icons.Outlined.Security,
                    background = groupBackground,
                    onClick = onOpenKeyManagement
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors
            ) {
                SectionTitle("外观")
                EntryRow(
                    title = "主题设置",
                    subtitle = "跟随系统/暗色/亮色",
                    icon = Icons.Outlined.Palette,
                    background = groupBackground,
                    onClick = onOpenThemeSettings
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors
            ) {
                SectionTitle("消息时间显示")
                EntryRow(
                    title = "消息时间显示",
                    subtitle = "当前：${timeModeLabel(timeMode.value)}",
                    icon = Icons.Outlined.Palette,
                    background = groupBackground,
                    onClick = onOpenTimeDisplay
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors
            ) {
                SectionTitle("通知")
                EntryRow(
                    title = "通知设置",
                    subtitle = "消息提醒与后台接收",
                    icon = Icons.Outlined.Notifications,
                    background = groupBackground,
                    onClick = onOpenNotificationSettings
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors
            ) {
                SectionTitle("诊断")
                EntryRow(
                    title = "Debug 诊断模式",
                    subtitle = "查看发送/接收链路日志",
                    icon = Icons.Outlined.BugReport,
                    background = groupBackground,
                    onClick = onOpenDebugSettings
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors
            ) {
                SectionTitle("API 设置")
                EntryRow(
                    title = "服务器地址",
                    subtitle = "配置 API Base URL",
                    icon = Icons.Outlined.Palette,
                    background = groupBackground,
                    onClick = onOpenApiSettings
                )
            }
            Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun EntryRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = background)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(text = title, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
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

private fun timeModeLabel(mode: TimeDisplayMode): String = when (mode) {
    TimeDisplayMode.RELATIVE -> "相对时间"
    TimeDisplayMode.ABSOLUTE -> "绝对时间"
    TimeDisplayMode.AUTO -> "自动"
}
