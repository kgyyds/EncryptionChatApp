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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
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
import com.kgapp.encryptionchat.ui.components.MessageBubble
import com.kgapp.encryptionchat.ui.viewmodel.ChatViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: ChatRepository,
    uid: String,
    onBack: () -> Unit
) {
    val viewModel: ChatViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val inputState = remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAtBottom = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = state.value.messages.size
            total == 0 || lastVisible >= total - 2
        }
    }

    LaunchedEffect(uid) {
        viewModel.load(uid)
        scope.launch {
            val message = viewModel.readNewMessages()
            if (!message.isNullOrBlank()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(state.value.messages.size) {
        if (state.value.messages.isNotEmpty() && isAtBottom.value) {
            listState.animateScrollToItem(state.value.messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                            val message = viewModel.readNewMessages()
                            if (!message.isNullOrBlank()) {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
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
                    label = { Text("输入消息") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00D4FF),
                        unfocusedBorderColor = Color(0xFF4B4B5A),
                        focusedContainerColor = Color(0xFF0F0F12),
                        unfocusedContainerColor = Color(0xFF0F0F12),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Button(
                    onClick = {
                        val text = inputState.value.trim()
                        if (text.isBlank()) {
                            Toast.makeText(context, "请输入内容", Toast.LENGTH_SHORT).show()
                            return@Button
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
