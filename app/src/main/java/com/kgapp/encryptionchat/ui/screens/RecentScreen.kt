package com.kgapp.encryptionchat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.ui.viewmodel.RecentViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.util.MessagePullPreferences
import com.kgapp.encryptionchat.util.PullMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentScreen(
    repository: ChatRepository,
    messageSyncManager: MessageSyncManager,
    onOpenChat: (String) -> Unit
) {
    val viewModel: RecentViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val pullMode = MessagePullPreferences.mode.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val deleteTarget = remember { mutableStateOf<RecentViewModel.RecentItem?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(pullMode.value) {
        messageSyncManager.updateMode(pullMode.value, null)
    }

    LaunchedEffect(Unit) {
        messageSyncManager.updates.collect {
            viewModel.refresh()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("最近聊天") },
                actions = {
                    if (pullMode.value != PullMode.GLOBAL_SSE) {
                        IconButton(onClick = {
                            scope.launch {
                                val message = messageSyncManager.refreshRecentChats()
                                if (!message.isNullOrBlank()) {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                                viewModel.refresh()
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(12.dp)
        ) {
            items(state.value.items, key = { it.uid }) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpenChat(item.uid) },
                            onLongClick = { deleteTarget.value = item }
                        )
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarPlaceholder(text = item.remark, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.remark, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = item.lastText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            text = item.lastTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    DropdownMenu(
                        expanded = deleteTarget.value?.uid == item.uid,
                        onDismissRequest = { deleteTarget.value = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("删除聊天记录") },
                            onClick = {
                                viewModel.deleteChat(item.uid)
                                Toast.makeText(context, "已删除聊天记录", Toast.LENGTH_SHORT).show()
                                deleteTarget.value = null
                            }
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun AvatarPlaceholder(text: String, modifier: Modifier = Modifier) {
    val initial = text.trim().firstOrNull()?.toString()?.uppercase().orEmpty()
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (initial.isBlank()) "?" else initial,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}
