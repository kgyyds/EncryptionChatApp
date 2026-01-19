package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.ui.components.MessageBubble
import com.kgapp.encryptionchat.ui.viewmodel.ChatViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.util.MessagePullPreferences
import com.kgapp.encryptionchat.util.PullMode
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: ChatRepository,
    messageSyncManager: MessageSyncManager,
    uid: String,
    onBack: () -> Unit
) {
    val viewModel: ChatViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val pullMode = MessagePullPreferences.mode.collectAsStateWithLifecycle()
    val inputState = remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showEmptyToast = remember(uid) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val isAtBottom = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = state.value.messages.size
            total == 0 || lastVisible >= total - 2
        }
    }

    LaunchedEffect(uid) {
        viewModel.load(uid)
        messageSyncManager.updateMode(pullMode.value, uid)
    }

    LaunchedEffect(pullMode.value, uid) {
        messageSyncManager.updateMode(pullMode.value, uid)
    }

    LaunchedEffect(uid) {
        messageSyncManager.updates.collect { updatedUid ->
            if (updatedUid == uid) {
                viewModel.refresh()
            }
        }
    }

    DisposableEffect(uid, pullMode.value) {
        onDispose {
            if (pullMode.value == PullMode.CHAT_SSE) {
                scope.launch { messageSyncManager.stopSse() }
            } else {
                messageSyncManager.updateMode(pullMode.value, null)
            }
        }
    }

    LaunchedEffect(state.value.hasHistory, state.value.messages.size, uid) {
        if (!state.value.hasHistory && state.value.messages.isEmpty() && !showEmptyToast.value) {
            Toast.makeText(context, "暂无记录", Toast.LENGTH_SHORT).show()
            showEmptyToast.value = true
        }
    }

    LaunchedEffect(state.value.messages.size) {
        if (state.value.messages.isNotEmpty() && isAtBottom.value) {
            listState.animateScrollToItem(state.value.messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = state.value.remark)
                        Text(
                            text = uid,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (pullMode.value != PullMode.GLOBAL_SSE) {
                        IconButton(onClick = {
                            scope.launch {
                                val message = messageSyncManager.refreshOnce(uid)
                                if (!message.isNullOrBlank()) {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurfaceVariant,
                    actionIconContentColor = colors.onSurfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.value.messages, key = { it.ts + ":" + it.speaker }) { message ->
                    MessageBubble(
                        text = message.text,
                        speaker = message.speaker,
                        timestamp = message.timeLabel
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
                    placeholder = { Text("输入消息", color = colors.onSurfaceVariant) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.outlineVariant,
                        unfocusedBorderColor = colors.outlineVariant,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        cursorColor = colors.primary,
                        focusedPlaceholderColor = colors.onSurfaceVariant,
                        unfocusedPlaceholderColor = colors.onSurfaceVariant
                    )
                )
                FilledIconButton(
                    onClick = {
                        val text = inputState.value.trim()
                        if (text.isBlank()) {
                            Toast.makeText(context, "请输入内容", Toast.LENGTH_SHORT).show()
                            return@FilledIconButton
                        }
                        scope.launch {
                            val message = viewModel.sendMessage(text)
                            if (!message.isNullOrBlank()) {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            } else {
                                inputState.value = ""
                            }
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}
