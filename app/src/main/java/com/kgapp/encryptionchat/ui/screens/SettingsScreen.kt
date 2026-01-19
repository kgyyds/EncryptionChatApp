package com.kgapp.encryptionchat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: ChatRepository,
    onOpenKeyManagement: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text("设置") })
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenKeyManagement() }
            ) {
                RowItem(
                    title = "密钥管理",
                    subtitle = if (state.value.hasPrivateKey && state.value.hasPublicKey) "已配置" else "未配置",
                    icon = Icons.Outlined.Security
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { Toast.makeText(context, "开发中", Toast.LENGTH_SHORT).show() }
            ) {
                RowItem(
                    title = "外观",
                    subtitle = "开发中",
                    icon = Icons.Outlined.Palette
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { Toast.makeText(context, "开发中", Toast.LENGTH_SHORT).show() }
            ) {
                RowItem(
                    title = "关于",
                    subtitle = "开发中",
                    icon = Icons.Outlined.Info
                )
            }
        }
    }
}

@Composable
private fun RowItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
