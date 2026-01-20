package com.kgapp.encryptionchat.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgapp.encryptionchat.notifications.MessageSyncService
import com.kgapp.encryptionchat.security.SecuritySettings
import com.kgapp.encryptionchat.util.NotificationPreferences
import com.kgapp.encryptionchat.util.NotificationPreviewMode
import com.kgapp.encryptionchat.data.sync.MessageSyncRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val enableNotifications = NotificationPreferences.enableNotifications.collectAsStateWithLifecycle()
    val enableBackground = NotificationPreferences.enableBackgroundReceive.collectAsStateWithLifecycle()
    val startOnBoot = NotificationPreferences.startOnBoot.collectAsStateWithLifecycle()
    val previewMode = NotificationPreferences.previewMode.collectAsStateWithLifecycle()
    val appLockEnabled = remember { mutableStateOf(SecuritySettings.readConfig(context).appLockEnabled) }

    LaunchedEffect(Unit) {
        appLockEnabled.value = SecuritySettings.readConfig(context).appLockEnabled
    }

    val permissionGranted = remember { mutableStateOf(hasPostNotificationsPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted.value = granted
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("通知设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                    SectionTitle("通知权限")
                    if (!permissionGranted.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        RowItem(
                            title = "需要通知权限",
                            subtitle = "开启后可接收消息提醒"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = {
                                NotificationPreferences.setAskedPostNotifications(context, true)
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) {
                                Text("申请权限")
                            }
                            Button(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                )
                            }) {
                                Text("打开系统设置")
                            }
                        }
                    } else {
                        RowItem(title = "通知权限已开启", subtitle = "系统已允许通知")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors
            ) {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                    SectionTitle("通知开关")
                    RowItem(
                        title = "启用通知",
                        subtitle = "收到新消息时提醒",
                        trailing = {
                            Switch(
                                checked = enableNotifications.value,
                                onCheckedChange = { NotificationPreferences.setEnableNotifications(context, it) }
                            )
                        }
                    )
                    RowItem(
                        title = "后台接收消息",
                        subtitle = "通过前台服务保持消息同步",
                        trailing = {
                            Switch(
                                checked = enableBackground.value,
                                onCheckedChange = { enabled ->
                                    NotificationPreferences.setEnableBackgroundReceive(context, enabled)
                                    if (enabled) {
                                        MessageSyncService.start(context)
                                        MessageSyncRegistry.stopAppBroadcast()
                                    } else {
                                        MessageSyncService.stop(context)
                                        MessageSyncRegistry.ensureAppBroadcast()
                                    }
                                }
                            )
                        }
                    )
                    RowItem(
                        title = "开机自启动",
                        subtitle = "重启后自动启动后台接收",
                        trailing = {
                            Switch(
                                checked = startOnBoot.value,
                                enabled = enableBackground.value,
                                onCheckedChange = { NotificationPreferences.setStartOnBoot(context, it) }
                            )
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors
            ) {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                    SectionTitle("通知预览")
                    if (appLockEnabled.value) {
                        RowItem(
                            title = "应用锁已启用",
                            subtitle = "通知将隐藏消息内容"
                        )
                    } else {
                        OptionRow(
                            title = "显示消息预览",
                            selected = previewMode.value == NotificationPreviewMode.SHOW_PREVIEW,
                            onSelect = { NotificationPreferences.setPreviewMode(context, NotificationPreviewMode.SHOW_PREVIEW) }
                        )
                        OptionRow(
                            title = "仅显示标题",
                            selected = previewMode.value == NotificationPreviewMode.TITLE_ONLY,
                            onSelect = { NotificationPreferences.setPreviewMode(context, NotificationPreviewMode.TITLE_ONLY) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowItem(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun OptionRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = title, modifier = Modifier.padding(start = 8.dp))
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

private fun hasPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
