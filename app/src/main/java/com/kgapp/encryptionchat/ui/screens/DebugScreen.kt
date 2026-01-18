package com.kgapp.encryptionchat.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kgapp.encryptionchat.data.ChatRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    repository: ChatRepository,
    onBack: () -> Unit
) {
    val selfNameState = remember { mutableStateOf("") }
    val pemTextState = remember { mutableStateOf("") }
    val pemB64LengthState = remember { mutableStateOf(0) }
    val configState = remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        selfNameState.value = repository.getSelfName() ?: ""
        pemTextState.value = repository.getPublicPemText() ?: ""
        pemB64LengthState.value = repository.getPemBase64()?.length ?: 0
        configState.value = repository.readContactsRaw()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug") },
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
            Text(text = "self_name (md5): ${selfNameState.value}")
            Text(text = "pem_b64 length: ${pemB64LengthState.value}")
            Text(text = "公钥 PEM (前几行):")
            Text(text = pemTextState.value.lineSequence().take(5).joinToString("\n"))
            Text(text = "contacts/config.json:")
            Text(text = configState.value)
            Button(
                onClick = {
                    Log.d("EncryptionChat", "self_name=${selfNameState.value}")
                    Log.d("EncryptionChat", "public_pem=${pemTextState.value}")
                    Log.d("EncryptionChat", "contacts_config=${configState.value}")
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(imageVector = Icons.Default.Description, contentDescription = "Log")
                Text(text = "导出到 Logcat", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
