package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.model.ContactConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    repository: ChatRepository,
    onAddContact: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenDebug: () -> Unit
) {
    val contactsState = remember { mutableStateOf<Map<String, ContactConfig>>(emptyMap()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        contactsState.value = repository.readContacts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EncryptionChat") },
                actions = {
                    IconButton(onClick = onOpenDebug) {
                        Icon(imageVector = Icons.Default.BugReport, contentDescription = "Debug")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(contactsState.value.toList()) { (uid, contact) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onOpenChat(uid) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = contact.Remark)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(uid))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("UID 已复制")
                                    }
                                }
                        ) {
                            Text(text = uid)
                        }
                    }
                }
            }
        }
    }
}
