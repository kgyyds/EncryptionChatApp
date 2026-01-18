package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kgapp.encryptionchat.data.ChatRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    repository: ChatRepository,
    onBack: () -> Unit
) {
    val remarkState = remember { mutableStateOf("") }
    val pubKeyState = remember { mutableStateOf("") }
    val pwdState = remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加联系人") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = remarkState.value,
                onValueChange = { remarkState.value = it },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = pubKeyState.value,
                onValueChange = { pubKeyState.value = it },
                label = { Text("对方公钥 PEM") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
            OutlinedTextField(
                value = pwdState.value,
                onValueChange = { pwdState.value = it },
                label = { Text("握手密码") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Button(
                onClick = {
                    scope.launch {
                        val remark = remarkState.value.trim()
                        val pubKey = pubKeyState.value.trim()
                        val pwd = pwdState.value.trim()
                        if (remark.isBlank() || pubKey.isBlank() || pwd.isBlank()) {
                            snackbarHostState.showSnackbar("请填写完整信息")
                            return@launch
                        }
                        if (!pubKey.contains("BEGIN PUBLIC KEY") || !pubKey.contains("END PUBLIC KEY")) {
                            snackbarHostState.showSnackbar("公钥格式不正确")
                            return@launch
                        }
                        val uid = repository.addContact(remark, pubKey, pwd)
                        snackbarHostState.showSnackbar("保存成功: $uid")
                        remarkState.value = ""
                        pubKeyState.value = ""
                        pwdState.value = ""
                    }
                },
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("保存")
            }
        }
    }
}
