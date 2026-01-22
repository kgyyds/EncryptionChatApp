package com.kgapp.encryptionchat.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.ui.components.MessageBubble
import com.kgapp.encryptionchat.ui.viewmodel.ChatViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.util.DebugLevel
import com.kgapp.encryptionchat.util.DebugLog
import com.kgapp.encryptionchat.util.DebugPreferences
import com.kgapp.encryptionchat.util.TimeDisplayPreferences
import com.kgapp.encryptionchat.util.TimeFormatter
import com.kgapp.encryptionchat.util.UnreadCounter
import kotlinx.coroutines.launch

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
    val timeMode = TimeDisplayPreferences.mode.collectAsStateWithLifecycle()
    val debugEnabled = DebugPreferences.debugEnabled.collectAsStateWithLifecycle()
    val logEvents = DebugLog.eventsFlow.collectAsStateWithLifecycle()

    val inputState = remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current

    val showEmptyToast = remember(uid) { mutableStateOf(false) }
    val menuExpanded = remember { mutableStateOf(false) }
    val selectedMessage = remember { mutableStateOf<ChatViewModel.UiMessage?>(null) }
    val showDebugSheet = remember { mutableStateOf(false) }
    val selectedTag = remember { mutableStateOf("ALL") }
    val filterMenuExpanded = remember { mutableStateOf(false) }

    val selfAvatar = remember(state.value.selfName) {
        state.value.selfName.trim().ifBlank { "我" }
    }
    val otherAvatar = remember(state.value.remark) {
        state.value.remark.trim().ifBlank { "对" }
    }

    // reverseLayout = true 时：index 0 在底部
    val isAtBottom = remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 1 }
    }

    LaunchedEffect(uid) {
        viewModel.load(uid)
        messageSyncManager.startChatSse(uid)
        UnreadCounter.clear(context, uid)
    }

    LaunchedEffect(uid) {
        messageSyncManager.updates.collect { updatedUid ->
            if (updatedUid == uid) {
                DebugLog.append(
                    context = context,
                    level = DebugLevel.INFO,
                    tag = "UI",
                    chatUid = uid,
                    eventName = "receive_update",
                    message = "refresh"
                )
                viewModel.refresh()
                UnreadCounter.clear(context, uid)
            }
        }
    }

    DisposableEffect(uid) {
        onDispose {
            scope.launch {
                messageSyncManager.stopChatSse()
            }
        }
    }

    LaunchedEffect(state.value.hasHistory, state.value.messages.size, uid) {
        if (!state.value.hasHistory && state.value.messages.isEmpty() && !showEmptyToast.value) {
            Toast.makeText(context, "暂无记录", Toast.LENGTH_SHORT).show()
            showEmptyToast.value = true
        }
    }

    // 新消息到来且在底部 → 自动滚回底部（reverseLayout = true 用 0）
    LaunchedEffect(state.value.messages.size) {
        if (state.value.messages.isNotEmpty() && isAtBottom.value) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                    if (debugEnabled.value) {
                        IconButton(onClick = { showDebugSheet.value = true }) {
                            Icon(imageVector = Icons.Default.BugReport, contentDescription = "Debug")
                        }
                    }
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
                // ✅ 比 imePadding() 更“硬”，对一些 ROM 更稳
                .windowInsetsPadding(WindowInsets.ime)
                .navigationBarsPadding()
                .background(colors.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.value.messages,
                    key = { it.ts + ":" + it.speaker }
                ) { message ->
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
                                    scope.launch { viewModel.deleteMessage(message.ts) }
                                }
                            )
                        }
                    }
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

    if (showDebugSheet.value && debugEnabled.value) {
        val tags = listOf("ALL") + logEvents.value.map { it.tag }.distinct().sorted()
        val filteredLogs = logEvents.value.filter { event ->
            event.chatUid == null || event.chatUid == uid
        }.filter { event ->
            selectedTag.value == "ALL" || event.tag == selectedTag.value
        }
        val lastEventTime = messageSyncManager.lastChatEventTime(uid)
        val connectionStatus = if (messageSyncManager.isChatSseActive(uid)) "已连接" else "未连接"
        AlertDialog(
            onDismissRequest = { showDebugSheet.value = false },
            title = { Text("Debug 诊断") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("联系人: $uid")
                    Text("本地消息数: ${state.value.messages.size}")
                    Text("连接状态: $connectionStatus")
                    Text("最后消息时间: ${state.value.messages.firstOrNull()?.ts ?: "-"}")
                    Text("最后接收时间: ${lastEventTime?.let { it.toString() } ?: "-"}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("筛选标签: ${selectedTag.value}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { filterMenuExpanded.value = true }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Filter")
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded.value,
                            onDismissRequest = { filterMenuExpanded.value = false }
                        ) {
                            tags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text(tag) },
                                    onClick = {
                                        selectedTag.value = tag
                                        filterMenuExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        filteredLogs.takeLast(40).forEach { event ->
                            Text(
                                text = "[${event.level}] ${event.tag} ${event.eventName} ${event.message}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        DebugLog.clear()
                        selectedTag.value = "ALL"
                    }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Text("清空")
                    }
                    TextButton(onClick = {
                        val dump = DebugLog.dumpText()
                        clipboardManager.setText(AnnotatedString(dump))
                        Toast.makeText(context, "已复制日志", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("复制")
                    }
                    TextButton(onClick = {
                        val file = DebugLog.exportToFile(context)
                        Toast.makeText(context, "日志已导出: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    }) {
                        Text("导出")
                    }
                    TextButton(onClick = {
                        val dump = DebugLog.dumpText()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, dump)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享日志"))
                    }) {
                        Text("分享")
                    }
                    TextButton(onClick = {
                        scope.launch {
                            messageSyncManager.stopChatSse()
                            messageSyncManager.startChatSse(uid)
                        }
                    }) {
                        Text("重连 SSE")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDebugSheet.value = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
