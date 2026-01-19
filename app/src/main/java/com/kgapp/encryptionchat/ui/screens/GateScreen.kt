package com.kgapp.encryptionchat.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.kgapp.encryptionchat.security.AuthMode
import com.kgapp.encryptionchat.security.DuressAction
import com.kgapp.encryptionchat.security.SecuritySettings
import com.kgapp.encryptionchat.security.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val config = remember { mutableStateOf(SecuritySettings.readConfig(context)) }
    val pinInput = remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val promptShown = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler {
        activity?.finish()
    }

    LaunchedEffect(config.value.duressEnabled) {
        if (!config.value.duressEnabled) {
            SessionState.unlockNormal()
            onUnlocked()
        }
    }

    LaunchedEffect(config.value.authMode, config.value.duressEnabled) {
        if (config.value.duressEnabled && config.value.authMode == AuthMode.SYSTEM && activity != null && !promptShown.value) {
            promptShown.value = true
            val canAuth = BiometricManager.from(activity).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                triggerBiometric(activity) { success ->
                    if (success) {
                        SessionState.unlockNormal()
                        onUnlocked()
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("认证失败") }
                    }
                }
            } else {
                SecuritySettings.setAuthMode(context, AuthMode.PIN)
                config.value = SecuritySettings.readConfig(context)
                scope.launch { snackbarHostState.showSnackbar("请先设置系统锁屏或生物识别") }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("解锁") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (config.value.authMode == AuthMode.PIN || config.value.duressEnabled) {
                PinDots(pinInput.value, modifier = Modifier.fillMaxWidth())
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
                            SecuritySettings.verifyDuressPin(currentConfig, pin) -> {
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
                                scope.launch { snackbarHostState.showSnackbar("PIN 错误") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun triggerBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onResult(true)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onResult(false)
        }

        override fun onAuthenticationFailed() {
            onResult(false)
        }
    })
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("验证身份")
        .setSubtitle("使用系统认证解锁")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    prompt.authenticate(promptInfo)
}

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
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(" ")
                            }
                        }
                        "⌫" -> {
                            FilledTonalButton(
                                onClick = onDelete,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text("删除")
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = { onDigit(label) },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
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
