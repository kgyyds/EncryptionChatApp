package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kgapp.encryptionchat.util.ApiSettingsPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val storedBaseUrl = ApiSettingsPreferences.baseUrl.collectAsState()
    val inputState = remember { mutableStateOf(storedBaseUrl.value) }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(storedBaseUrl.value) {
        if (inputState.value != storedBaseUrl.value) {
            inputState.value = storedBaseUrl.value
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("API 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "服务器地址",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                    OutlinedTextField(
                        value = inputState.value,
                        onValueChange = { inputState.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        singleLine = true,
                        placeholder = { Text(ApiSettingsPreferences.DEFAULT_BASE_URL) }
                    )
                    Button(
                        onClick = {
                            val trimmed = inputState.value.trim()
                            val next = if (trimmed.isBlank()) {
                                ApiSettingsPreferences.DEFAULT_BASE_URL
                            } else {
                                trimmed
                            }
                            ApiSettingsPreferences.setBaseUrl(context, next)
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
