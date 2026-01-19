package com.kgapp.encryptionchat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.ui.viewmodel.RecentViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentScreen(
    repository: ChatRepository,
    onOpenChat: (String) -> Unit
) {
    val viewModel: RecentViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val deleteTarget = remember { mutableStateOf<RecentViewModel.RecentItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    if (deleteTarget.value != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget.value = null },
            title = { Text("删除聊天记录") },
            text = { Text("确认删除该聊天记录吗？联系人不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleteTarget.value
                    if (target != null) {
                        viewModel.deleteChat(target.uid)
                        Toast.makeText(context, "已删除聊天记录", Toast.LENGTH_SHORT).show()
                    }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(12.dp)
    ) {
        items(state.value.items, key = { it.uid }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .combinedClickable(
                        onClick = { onOpenChat(item.uid) },
                        onLongClick = { deleteTarget.value = item }
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(text = item.remark, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = item.lastTime,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Text(
                        text = item.lastText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = item.uid,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
