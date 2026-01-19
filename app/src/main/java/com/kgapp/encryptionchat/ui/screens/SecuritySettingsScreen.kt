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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kgapp.encryptionchat.security.AuthMode
import com.kgapp.encryptionchat.security.DuressAction
import com.kgapp.encryptionchat.security.SecuritySettings
import androidx.compose.foundation.layout.Row

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = remember { mutableStateOf(SecuritySettings.readConfig(context)) }
    val normalPin = remember { mutableStateOf("") }
    val duressPin = remember { mutableStateOf("") }
    val confirmWipe = remember { mutableStateOf(false) }
    val wipeConfirmText = remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("安全") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                RowItem(
                    title = "启用应用锁",
                    trailing = {
                        Switch(
                            checked = config.value.appLockEnabled,
                            onCheckedChange = {
                                SecuritySettings.setAppLockEnabled(context, it)
                                config.value = SecuritySettings.readConfig(context)
                            }
                        )
                    }
                )
                RowItem(
                    title = "启用胁迫模式",
                    trailing = {
                        Switch(
                            checked = config.value.duressEnabled,
                            onCheckedChange = {
                                SecuritySettings.setDuressEnabled(context, it)
                                config.value = SecuritySettings.readConfig(context)
                            }
                        )
                    }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SectionTitle("认证方式")
                OptionRow(
                    title = "系统认证",
                    selected = config.value.authMode == AuthMode.SYSTEM,
                    onSelect = {
                        SecuritySettings.setAuthMode(context, AuthMode.SYSTEM)
                        config.value = SecuritySettings.readConfig(context)
                    }
                )
                OptionRow(
                    title = "App 内 PIN",
                    selected = config.value.authMode == AuthMode.PIN,
                    onSelect = {
                        SecuritySettings.setAuthMode(context, AuthMode.PIN)
                        config.value = SecuritySettings.readConfig(context)
                    }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SectionTitle("PIN 设置")
                OutlinedTextField(
                    value = normalPin.value,
                    onValueChange = { normalPin.value = it },
                    label = { Text("正常 PIN") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = duressPin.value,
                    onValueChange = { duressPin.value = it },
                    label = { Text("胁迫 PIN") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(
                    onClick = {
                        if (normalPin.value.isBlank() && duressPin.value.isBlank()) {
                            Toast.makeText(context, "请输入 PIN", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        SecuritySettings.savePins(context, normalPin.value, duressPin.value)
                        normalPin.value = ""
                        duressPin.value = ""
                        Toast.makeText(context, "PIN 已保存", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("保存 PIN")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SectionTitle("触发动作")
                OptionRow(
                    title = "伪装模式",
                    selected = config.value.duressAction == DuressAction.DECOY,
                    onSelect = {
                        SecuritySettings.setDuressAction(context, DuressAction.DECOY)
                        config.value = SecuritySettings.readConfig(context)
                    }
                )
                OptionRow(
                    title = "隐藏模式",
                    selected = config.value.duressAction == DuressAction.HIDE,
                    onSelect = {
                        SecuritySettings.setDuressAction(context, DuressAction.HIDE)
                        config.value = SecuritySettings.readConfig(context)
                    }
                )
                OptionRow(
                    title = "擦除模式（默认关闭）",
                    selected = config.value.duressAction == DuressAction.WIPE,
                    onSelect = {
                        confirmWipe.value = true
                    }
                )
                if (confirmWipe.value) {
                    Text(
                        text = "输入 WIPE 以确认启用擦除模式",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = wipeConfirmText.value,
                        onValueChange = { wipeConfirmText.value = it },
                        label = { Text("确认文本") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    Button(
                        onClick = {
                            if (wipeConfirmText.value.trim() != "WIPE") {
                                Toast.makeText(context, "确认文本不正确", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            SecuritySettings.setDuressAction(context, DuressAction.WIPE)
                            confirmWipe.value = false
                            wipeConfirmText.value = ""
                            config.value = SecuritySettings.readConfig(context)
                            Toast.makeText(context, "已启用擦除模式", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("确认启用")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
private fun OptionRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun RowItem(title: String, trailing: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        trailing()
    }
}
