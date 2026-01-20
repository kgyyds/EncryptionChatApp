package com.kgapp.encryptionchat.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kgapp.encryptionchat.security.DuressAction
import com.kgapp.encryptionchat.security.SecuritySettings
import com.kgapp.encryptionchat.security.SessionState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val config = remember { mutableStateOf(SecuritySettings.readConfig(context)) }
    val pinInput = remember { mutableStateOf("") }

    BackHandler {
        (context as? android.app.Activity)?.finish()
    }

    LaunchedEffect(config.value.appLockEnabled) {
        if (!config.value.appLockEnabled) {
            Log.d(TAG, "App lock disabled, unlocking")
            SessionState.unlockNormal()
            onUnlocked()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("解锁") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (config.value.appLockEnabled) {
                PinDots(pinInput.value, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.weight(1f))
                PinKeypad(
                    onDigit = { digit ->
                        if (pinInput.value.length < 6) {
                            pinInput.value += digit
                        }
                    },
                    onDelete = {
                        if (pinInput.value.isNotEmpty()) {
                            pinInput.value = pinInput.value.dropLast(1)
                        }
                    },
                    onClear = { pinInput.value = "" }
                )
                LaunchedEffect(pinInput.value) {
                    if (pinInput.value.length == 6) {
                        delay(200)
                        val pin = pinInput.value.trim()
                        val currentConfig = SecuritySettings.readConfig(context)
                        when {
                            SecuritySettings.verifyNormalPin(currentConfig, pin) -> {
                                SessionState.unlockNormal()
                                onUnlocked()
                            }
                            currentConfig.duressEnabled && SecuritySettings.verifyDuressPin(currentConfig, pin) -> {
                                val action = currentConfig.duressAction
                                if (action == DuressAction.WIPE) {
                                    SecuritySettings.wipeSensitiveData(context)
                                    SessionState.unlockDuress(DuressAction.DECOY)
                                } else {
                                    SessionState.unlockDuress(action)
                                }
                                onUnlocked()
                            }
                            else -> {
                                pinInput.value = ""
                                Toast.makeText(context, "PIN 错误", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val TAG = "GateScreen"

@Composable
private fun PinDots(value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(6) { index ->
            val filled = index < value.length
            val color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(12.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { label ->
                    when (label) {
                        "" -> {
                            FilledTonalButton(
                                onClick = {},
                                enabled = false,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Text(" ")
                            }
                        }
                        "⌫" -> {
                            FilledTonalButton(
                                onClick = onDelete,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Text("删除")
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = { onDigit(label) },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
        FilledTonalButton(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("清空")
        }
    }
}
