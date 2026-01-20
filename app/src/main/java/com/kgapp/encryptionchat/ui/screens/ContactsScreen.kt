package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.ui.viewmodel.ContactsViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import android.widget.Toast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.kgapp.encryptionchat.util.UnreadCounter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    repository: ChatRepository,
    onAddContact: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenKeyManagement: () -> Unit
) {
    val viewModel: ContactsViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val editTarget = remember { mutableStateOf<Pair<String, String>?>(null) }
    val editRemark = remember { mutableStateOf("") }
    val menuTarget = remember { mutableStateOf<String?>(null) }
    val deleteTarget = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("联系人") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .combinedClickable(
                            onClick = {
                                if (!state.value.hasKeys) {
                                    Toast.makeText(context, "请先生成或导入密钥", Toast.LENGTH_SHORT).show()
                                } else {
                                    onAddContact()
                                }
                            },
                            onLongClick = {
                                if (!state.value.hasKeys) {
                                    Toast.makeText(context, "请先生成或导入密钥", Toast.LENGTH_SHORT).show()
                                } else {
                                    onAddContact()
                                }
                            }
                        ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(text = "添加联系人")
                            Text(
                                text = "添加对方 UID / 公钥 / 密码",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                    }
                }
            }
            if (!state.value.hasKeys) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "请先生成或导入密钥")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onOpenKeyManagement) {
                                Text(text = "前往设置")
                            }
                        }
                    }
                }
            }
            items(state.value.contacts, key = { it.first }) { (uid, contact) ->
                val displayName = contact.Remark.ifBlank { uid }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .animateItemPlacement()
                        .combinedClickable(
                            onClick = { onOpenChat(uid) },
                            onLongClick = {
                                menuTarget.value = uid
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarPlaceholder(text = displayName, modifier = Modifier.size(44.dp))
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(text = displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = uid,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuTarget.value == uid,
                        onDismissRequest = { menuTarget.value = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("备注") },
                            onClick = {
                                editTarget.value = uid to displayName
                                editRemark.value = displayName
                                menuTarget.value = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                deleteTarget.value = uid
                                menuTarget.value = null
                            }
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            }
        }
    }

    if (editTarget.value != null) {
        AlertDialog(
            onDismissRequest = { editTarget.value = null },
            title = { Text("编辑备注") },
            text = {
                Column {
                    Text(text = editTarget.value?.first.orEmpty())
                    OutlinedTextField(
                        value = editRemark.value,
                        onValueChange = { editRemark.value = it },
                        label = { Text("备注") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = editTarget.value
                    if (target != null) {
                        val uid = target.first
                        val remark = editRemark.value.trim()
                        viewModel.updateRemark(uid, remark) { success ->
                            if (success) {
                                Toast.makeText(context, "备注已更新", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "更新失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    editTarget.value = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val uid = editTarget.value?.first.orEmpty()
                    if (uid.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(uid))
                        Toast.makeText(context, "UID 已复制", Toast.LENGTH_SHORT).show()
                    }
                    editTarget.value = null
                }) {
                    Text("复制 UID")
                }
            }
        )
    }

    if (deleteTarget.value != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget.value = null },
            title = { Text("删除联系人") },
            text = { Text("确定删除该联系人吗？聊天记录将从列表移除。") },
            confirmButton = {
                TextButton(onClick = {
                    val uid = deleteTarget.value.orEmpty()
                    viewModel.deleteContact(uid) { success ->
                        val message = if (success) "已删除联系人" else "删除失败"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                    UnreadCounter.clear(context, uid)
                    deleteTarget.value = null
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget.value = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun AvatarPlaceholder(text: String, modifier: Modifier = Modifier) {
    val trimmed = text.trim()
    val initial = trimmed.firstOrNull()?.toString()?.uppercase().orEmpty()
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
