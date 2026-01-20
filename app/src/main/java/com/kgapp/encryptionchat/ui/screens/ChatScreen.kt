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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import com.kgapp.encryptionchat.util.TimeDisplayPreferences
import com.kgapp.encryptionchat.util.TimeFormatter
import com.kgapp.encryptionchat.util.UnreadCounter
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

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
    val selfAvatar = remember(state.value.selfName) {
    state.value.selfName.trim().ifBlank { "我" }
}
val otherAvatar = remember(state.value.remark) {
    state.value.remark.trim().ifBlank { "对" }
}
    val timeMode = TimeDisplayPreferences.mode.collectAsStateWithLifecycle()
    val inputState = remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showEmptyToast = remember(uid) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    val menuExpanded = remember { mutableStateOf(false) }
    val selectedMessage = remember { mutableStateOf<ChatViewModel.UiMessage?>(null) }
    val isAtBottom = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = state.value.messages.size
            total == 0 || lastVisible >= total - 2
        }
    }

    LaunchedEffect(uid) {
        viewModel.load(uid)
        messageSyncManager.startChatSse(uid)
        UnreadCounter.clear(context, uid)
    }

    LaunchedEffect(uid) {
        messageSyncManager.updates.collect { updatedUid ->
            if (updatedUid == uid) {
                viewModel.refresh()
                UnreadCounter.clear(context, uid)
            }
        }
    }

    DisposableEffect(uid) {
        onDispose {
            scope.launch {
                messageSyncManager.stopChatSse()
                messageSyncManager.startBroadcastSse()
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
                    .fillMaxWidth()
                    .background(colors.background),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.value.messages, key = { it.ts + ":" + it.speaker }) { message ->
                    val timeLabel = TimeFormatter.formatMessageTimestamp(message.ts, timeMode.value)
                    Box {
                        MessageBubble(
                            text = message.text,
                            speaker = message.speaker,
                            timestamp = timeLabel,
                            avatarText = when (message.speaker) {
                                0 -> selfAvatar
                                1 -> otherAvatar
                                else -> null
                            },
                            onLongPress = {
                                selectedMessage.value = message
                                menuExpanded.value = true
                            }
                        )
                        DropdownMenu(
                            expanded = menuExpanded.value && selectedMessage.value?.ts == message.ts,
                            onDismissRequest = { menuExpanded.value = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制") },
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(message.text))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                    menuExpanded.value = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    menuExpanded.value = false
                                    scope.launch {
                                        viewModel.deleteMessage(message.ts)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .imePadding()
                    .navigationBarsPadding(),
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
