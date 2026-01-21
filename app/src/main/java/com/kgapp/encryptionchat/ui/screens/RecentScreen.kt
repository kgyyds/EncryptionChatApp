package com.kgapp.encryptionchat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.ui.viewmodel.RecentViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.util.UnreadCounter

private data class RecentActionSheetState(
    val selected: RecentViewModel.RecentItem? = null,
    val isSheetVisible: Boolean = false,
    val isConfirmVisible: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentScreen(
    repository: ChatRepository,
    messageSyncManager: MessageSyncManager,
    onOpenChat: (String) -> Unit
) {
    val viewModel: RecentViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val unreadCounts = UnreadCounter.counts.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val actionSheetState = remember { mutableStateOf(RecentActionSheetState()) }

    LaunchedEffect(Unit) { viewModel.refresh() }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
            contentPadding = PaddingValues(12.dp)
        ) {
            items(state.value.items, key = { it.uid }) { item ->
                val displayName = item.remark.ifBlank { item.uid }
                val unread = unreadCounts.value[item.uid] ?: 0

                RecentItemRow(
                    modifier = Modifier,
                    item = item,
                    displayName = displayName,
                    unread = unread,
                    onOpenChat = { onOpenChat(item.uid) },
                    onOpenMenu = {
                        actionSheetState.value =
                            RecentActionSheetState(selected = item, isSheetVisible = true)
                    }
                )

                Divider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            }
        }
    }

    val currentSheetState = actionSheetState.value
    val selectedItem = currentSheetState.selected
    if (currentSheetState.isSheetVisible && selectedItem != null) {
        val title = selectedItem.remark.ifBlank { selectedItem.uid }
        RecentActionSheet(
            title = title,
            pinned = selectedItem.pinned,
            onDismiss = {
                actionSheetState.value = RecentActionSheetState()
            },
            onTogglePinned = {
                viewModel.togglePinned(selectedItem.uid)
                actionSheetState.value = RecentActionSheetState()
            },
            onDelete = {
                actionSheetState.value = currentSheetState.copy(
                    isSheetVisible = false,
                    isConfirmVisible = true
                )
            }
        )
    }

    if (currentSheetState.isConfirmVisible && selectedItem != null) {
        AlertDialog(
            onDismissRequest = {
                actionSheetState.value = RecentActionSheetState()
            },
            title = { Text("删除聊天记录") },
            text = { Text("确认删除该联系人的全部聊天记录？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(selectedItem.uid)
                    UnreadCounter.clear(context, selectedItem.uid)
                    Toast.makeText(context, "已删除聊天记录", Toast.LENGTH_SHORT).show()
                    actionSheetState.value = RecentActionSheetState()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { actionSheetState.value = RecentActionSheetState() }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentItemRow(
    modifier: Modifier = Modifier,
    item: RecentViewModel.RecentItem,
    displayName: String,
    unread: Int,
    onOpenChat: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = onOpenChat,
                onLongClick = onOpenMenu
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarPlaceholder(
            text = displayName,
            unreadCount = unread,
            modifier = Modifier.size(44.dp)
        )

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = displayName, style = MaterialTheme.typography.titleMedium)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentActionSheet(
    title: String,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
            Divider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))

            ActionSheetItem(
                text = if (pinned) "取消置顶" else "置顶",
                icon = if (pinned) Icons.Default.RemoveCircleOutline else Icons.Default.PushPin,
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onTogglePinned
            )

            ActionSheetItem(
                text = "删除聊天",
                icon = Icons.Default.Delete,
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )

            ActionSheetItem(
                text = "取消",
                icon = Icons.Default.Close,
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onDismiss
            )

            Spacer(modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ActionSheetItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}

@Composable
private fun AvatarPlaceholder(
    text: String,
    unreadCount: Int,
    modifier: Modifier = Modifier
) {
    val initial = text.trim().firstOrNull()?.toString()?.uppercase().orEmpty()
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
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

        if (unreadCount > 0) {
            val badgeText = if (unreadCount > 99) "99+" else unreadCount.toString()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}
