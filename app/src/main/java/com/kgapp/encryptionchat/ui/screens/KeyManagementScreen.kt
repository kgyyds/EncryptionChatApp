package com.kgapp.encryptionchat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyManagementScreen(
    repository: ChatRepository,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val privateInput = remember { mutableStateOf("") }
    val publicInput = remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("密钥管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "密钥状态")
            Text(text = "私钥: ${if (state.value.hasPrivateKey) "已存在" else "未找到"}")
            Text(text = "公钥: ${if (state.value.hasPublicKey) "已存在" else "未找到"}")
            Text(text = "公钥指纹: ${if (state.value.fingerprint.isBlank()) "-" else state.value.fingerprint}")
            if (!state.value.hasPrivateKey || !state.value.hasPublicKey) {
                Text(text = "请先生成或导入密钥")
            }

            Button(
                onClick = {
                    viewModel.generateKeyPair { success ->
                        val message = if (success) "密钥生成成功" else "密钥生成失败"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (success) {
                            onBack()
                        }
                    }
                },
                enabled = !state.value.isGenerating,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = if (state.value.isGenerating) "生成中..." else "生成新密钥对")
            }

            OutlinedTextField(
                value = privateInput.value,
                onValueChange = { privateInput.value = it },
                label = { Text("导入私钥（PKCS#8 PEM）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
            Button(onClick = {
                val text = privateInput.value.trim()
                if (text.isBlank()) {
                    Toast.makeText(context, "请输入私钥内容", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.importPrivateKey(text) { success ->
                    val message = if (success) "私钥导入成功" else "私钥导入失败"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }) {
                Text(text = "导入私钥")
            }

            OutlinedTextField(
                value = publicInput.value,
                onValueChange = { publicInput.value = it },
                label = { Text("导入公钥（PEM）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
            Button(onClick = {
                val text = publicInput.value.trim()
                if (text.isBlank()) {
                    Toast.makeText(context, "请输入公钥内容", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.importPublicKey(text) { success ->
                    val message = if (success) "公钥导入成功" else "公钥导入失败"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }) {
                Text(text = "导入公钥")
            }

            Button(onClick = {
                scope.launch {
                    val pem = viewModel.exportPublicKey()
                    if (pem.isBlank()) {
                        Toast.makeText(context, "没有可导出的公钥", Toast.LENGTH_SHORT).show()
                    } else {
                        clipboard.setText(AnnotatedString(pem))
                        Toast.makeText(context, "公钥已复制", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                Text(text = "导出当前公钥", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
