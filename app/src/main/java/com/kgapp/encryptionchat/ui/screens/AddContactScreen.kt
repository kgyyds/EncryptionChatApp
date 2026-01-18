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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.ui.viewmodel.ContactsViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    repository: ChatRepository,
    onBack: () -> Unit
) {
    val viewModel: ContactsViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val remarkState = remember { mutableStateOf("") }
    val pubKeyState = remember { mutableStateOf("") }
    val pwdState = remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    val remark = remarkState.value.trim()
                    val pubKey = pubKeyState.value.trim()
                    val pwd = pwdState.value.trim()
                    if (remark.isBlank() || pubKey.isBlank() || pwd.isBlank()) {
                        Toast.makeText(context, "请填写完整信息", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!pubKey.contains("BEGIN PUBLIC KEY") || !pubKey.contains("END PUBLIC KEY")) {
                        Toast.makeText(context, "公钥格式不正确", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.addContact(remark, pubKey, pwd) { _ ->
                        Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                        remarkState.value = ""
                        pubKeyState.value = ""
                        pwdState.value = ""
                        onBack()
                    }
                },
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("保存")
            }
        }
    }
}
