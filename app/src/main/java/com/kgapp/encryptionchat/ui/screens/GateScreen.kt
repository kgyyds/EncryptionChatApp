package com.kgapp.encryptionchat.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kgapp.encryptionchat.security.AuthMode
import com.kgapp.encryptionchat.security.DuressAction
import com.kgapp.encryptionchat.security.SecuritySettings
import com.kgapp.encryptionchat.security.SessionState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val config = remember { mutableStateOf(SecuritySettings.readConfig(context)) }
    val pinInput = remember { mutableStateOf("") }
    val inFlight = remember { mutableStateOf(false) }
    val systemAuthState = remember { mutableStateOf(SystemAuthState.Idle) }
    val systemAuthMessage = remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        inFlight.value = false
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Device credential success")
            SessionState.unlockNormal()
            onUnlocked()
        } else {
            systemAuthState.value = SystemAuthState.Error
            systemAuthMessage.value = "认证失败，请重试"
            Log.d(TAG, "Device credential canceled or failed")
            Toast.makeText(context, "认证失败", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        activity?.finish()
    }

    LaunchedEffect(config.value.duressEnabled) {
        if (!config.value.duressEnabled) {
            Log.d(TAG, "Duress disabled, unlocking")
            SessionState.unlockNormal()
            onUnlocked()
        }
    }

    fun triggerSystemAuth() {
        if (activity == null || !config.value.duressEnabled || config.value.authMode != AuthMode.SYSTEM) return
        if (inFlight.value) return
        inFlight.value = true
        systemAuthState.value = SystemAuthState.InProgress
        systemAuthMessage.value = ""
        Log.d(TAG, "Trigger system auth")
        val biometricManager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuth = biometricManager.canAuthenticate(authenticators)
        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            triggerBiometric(
                activity,
                authenticators,
                onSuccess = {
                    inFlight.value = false
                    Log.d(TAG, "Biometric success")
                    SessionState.unlockNormal()
                    onUnlocked()
                },
                onError = { error ->
                    inFlight.value = false
                    systemAuthState.value = SystemAuthState.Error
                    systemAuthMessage.value = "认证失败，请重试"
                    Log.d(TAG, "Biometric error: $error")
                    Toast.makeText(context, "认证失败", Toast.LENGTH_SHORT).show()
                },
                onFailedAttempt = {
                    Log.d(TAG, "Biometric failed attempt")
                }
            )
            return
        }
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isDeviceSecure == true) {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent("验证身份", "使用锁屏密码解锁")
            if (intent != null) {
                Log.d(TAG, "Fallback to device credential")
                keyguardLauncher.launch(intent)
                return
            }
        }
        inFlight.value = false
        systemAuthState.value = SystemAuthState.Unavailable
        systemAuthMessage.value = "未设置系统锁屏，无法使用系统认证"
        Log.d(TAG, "System auth unavailable: $canAuth")
        Toast.makeText(context, "未设置系统锁屏，无法使用系统认证", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(config.value.authMode, config.value.duressEnabled, activity) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (config.value.authMode == AuthMode.SYSTEM && config.value.duressEnabled && !SessionState.unlocked.value) {
                triggerSystemAuth()
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (config.value.authMode == AuthMode.PIN) {
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
                                Toast.makeText(context, "PIN 错误", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            if (config.value.authMode == AuthMode.SYSTEM) {
                val statusText = when (systemAuthState.value) {
                    SystemAuthState.InProgress -> "正在请求系统验证"
                    SystemAuthState.Error -> "验证失败"
                    SystemAuthState.Unavailable -> "系统认证不可用"
                    SystemAuthState.Idle -> "等待系统验证"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium
                )
                if (systemAuthMessage.value.isNotBlank()) {
                    Text(
                        text = systemAuthMessage.value,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                FilledTonalButton(
                    onClick = { triggerSystemAuth() },
                    enabled = !inFlight.value
                ) {
                    Text(if (inFlight.value) "正在验证…" else "重试系统验证")
                }
            }
        }
    }
}

private fun triggerBiometric(
    activity: FragmentActivity,
    authenticators: Int,
    onSuccess: () -> Unit,
    onError: (CharSequence) -> Unit,
    onFailedAttempt: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onError(errString)
        }

        override fun onAuthenticationFailed() {
            onFailedAttempt()
        }
    })
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("验证身份")
        .setSubtitle("使用系统认证解锁")
        .setAllowedAuthenticators(authenticators)
        .build()
    prompt.authenticate(promptInfo)
}

private enum class SystemAuthState {
    Idle,
    InProgress,
    Error,
    Unavailable
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
