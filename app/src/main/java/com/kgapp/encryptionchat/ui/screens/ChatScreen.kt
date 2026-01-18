package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.model.ChatMessage
import com.kgapp.encryptionchat.ui.components.MessageBubble
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: ChatRepository,
    uid: String,
    onBack: () -> Unit
) {
    val historyState = remember { mutableStateOf<List<Pair<String, ChatMessage>>>(emptyList()) }
    val remarkState = remember { mutableStateOf(uid) }
    val inputState = remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun loadHistory() {
        scope.launch {
            val contacts = repository.readContacts()
            remarkState.value = contacts[uid]?.Remark ?: uid
            val history = repository.readChatHistory(uid)
            historyState.value = history
                .toList()
                .sortedBy { it.first.toLongOrNull() ?: 0L }
        }
    }

    LaunchedEffect(uid) {
        loadHistory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${remarkState.value}\n$uid") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val result = repository.readChat(uid)
                            if (result.handshakeFailed) {
                                snackbarHostState.showSnackbar("握手密码错误")
                            } else if (!result.success) {
                                snackbarHostState.showSnackbar(result.message ?: "拉取失败")
                            }
                            loadHistory()
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyState.value) { (ts, message) ->
                    MessageBubble(
                        text = message.text,
                        isMine = message.Spokesman == 0,
                        timestamp = ts
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputState.value,
                    onValueChange = { inputState.value = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("输入消息") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
                )
                Button(
                    onClick = {
                        scope.launch {
                            val text = inputState.value.trim()
                            if (text.isBlank()) {
                                snackbarHostState.showSnackbar("请输入内容")
                                return@launch
                            }
                            val result = repository.sendChat(uid, text)
                            if (!result.success) {
                                snackbarHostState.showSnackbar(result.message ?: "发送失败")
                            } else {
                                inputState.value = ""
                            }
                            loadHistory()
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}
