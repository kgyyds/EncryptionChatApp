package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kgapp.encryptionchat.ui.components.MessageBubble
import com.kgapp.encryptionchat.security.DuressAction

data class DecoyChatItem(
    val uid: String,
    val name: String,
    val lastText: String,
    val lastTime: String
)

private val decoyChats = listOf(
    DecoyChatItem(uid = "d1", name = "小李", lastText = "今晚一起吃饭吗？", lastTime = "12:48"),
    DecoyChatItem(uid = "d2", name = "快递", lastText = "您的包裹已放在门口。", lastTime = "昨天"),
    DecoyChatItem(uid = "d3", name = "小组", lastText = "周报已更新。", lastTime = "周二")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoyHomeScreen(onOpenChat: (String) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("最近聊天") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(decoyChats) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .clickable { onOpenChat(item.uid) },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = item.lastText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = item.lastTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoyChatScreen(name: String, onBack: () -> Unit) {
    val messages = listOf(
        Triple("好久不见～", 1, "12:41"),
        Triple("最近在忙什么？", 1, "12:42"),
        Triple("刚开完会。", 0, "12:43")
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { (text, speaker, time) ->
                MessageBubble(text = text, speaker = speaker, timestamp = time)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HideHomeScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        android.widget.Toast.makeText(context, "暂时没有聊天记录", android.widget.Toast.LENGTH_SHORT).show()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("最近聊天") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(text = "暂时没有聊天记录", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoyTabs(
    duressAction: DuressAction,
    onOpenChat: (String) -> Unit,
    onBackFromChat: () -> Unit,
    activeChatId: String?
) {
    var selectedTab by rememberSaveable { mutableStateOf("recent") }
    val tabs = listOf("recent", "contacts", "settings")
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val (icon, label) = when (tab) {
                        "recent" -> Icons.Outlined.Chat to "最近聊天"
                        "contacts" -> Icons.Outlined.People to "联系人"
                        "settings" -> Icons.Outlined.Settings to "设置"
                        else -> Icons.Outlined.Chat to "最近聊天"
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when {
                activeChatId != null -> {
                    val name = decoyChats.firstOrNull { it.uid == activeChatId }?.name ?: "聊天"
                    DecoyChatScreen(name = name, onBack = onBackFromChat)
                }
                duressAction == DuressAction.HIDE -> {
                    HideHomeScreen()
                }
                selectedTab == "recent" -> DecoyHomeScreen(onOpenChat)
                selectedTab == "contacts" -> DecoyContactsScreen()
                else -> DecoySettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoyContactsScreen() {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("联系人") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(text = "暂无联系人", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoySettingsScreen() {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(text = "暂无可用设置", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
