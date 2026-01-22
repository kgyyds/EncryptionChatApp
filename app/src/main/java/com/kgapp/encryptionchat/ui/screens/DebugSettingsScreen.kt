package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kgapp.encryptionchat.util.DebugPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val debugEnabled = DebugPreferences.debugEnabled.collectAsState()
    val detailedLogs = DebugPreferences.detailedLogs.collectAsState()
    val maxLogs = DebugPreferences.maxLogs.collectAsState()
    val maxLogsText = remember { mutableStateOf(maxLogs.value.toString()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Debug 诊断") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("调试模式", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("调试模式", modifier = Modifier.weight(1f))
                        Switch(
                            checked = debugEnabled.value,
                            onCheckedChange = { DebugPreferences.setDebugEnabled(context, it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("记录详细日志", modifier = Modifier.weight(1f))
                        Switch(
                            checked = detailedLogs.value,
                            onCheckedChange = { DebugPreferences.setDetailedLogs(context, it) }
                        )
                    }
                    OutlinedTextField(
                        value = maxLogsText.value,
                        onValueChange = { value ->
                            maxLogsText.value = value.filter { it.isDigit() }.take(6)
                            val parsed = maxLogsText.value.toIntOrNull() ?: maxLogs.value
                            DebugPreferences.setMaxLogs(context, parsed)
                        },
                        label = { Text("最大日志条数") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            }
        }
    }
}
