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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
import com.kgapp.encryptionchat.util.TimeFormatter
import kotlinx.coroutines.launch

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

    LaunchedEffect(uid) {
        viewModel.load(uid)
    }

    LaunchedEffect(state.value.messages.size) {
        if (state.value.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.value.messages.lastIndex)
        }
    }

    Scaffold(
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
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.value.messages) { (ts, message) ->
                    val formattedTime = remember(ts) { TimeFormatter.formatTimestamp(ts) }
                    MessageBubble(
                        text = message.text,
                        isMine = message.Spokesman == 0,
                        timestamp = formattedTime
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
