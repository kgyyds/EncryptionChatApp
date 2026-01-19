package com.kgapp.encryptionchat.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.kgapp.encryptionchat.security.AuthMode
import com.kgapp.encryptionchat.security.DuressAction
import com.kgapp.encryptionchat.security.SecuritySettings
import com.kgapp.encryptionchat.security.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val config = remember { mutableStateOf(SecuritySettings.readConfig(context)) }
    val pinInput = remember { mutableStateOf("") }

    BackHandler {
        activity?.finish()
    }

    LaunchedEffect(config.value.duressEnabled) {
        if (!config.value.duressEnabled) {
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
            if (config.value.authMode == AuthMode.SYSTEM && activity != null) {
                Button(
                    onClick = {
                        triggerBiometric(activity) { success ->
                            if (success) {
                                SessionState.unlockNormal()
                                onUnlocked()
                            } else {
                                Toast.makeText(context, "认证失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("使用系统认证解锁")
                }
            }
            if (config.value.authMode == AuthMode.PIN || config.value.duressEnabled) {
                OutlinedTextField(
                    value = pinInput.value,
                    onValueChange = { pinInput.value = it },
                    label = { Text("PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(
                    onClick = {
                        val pin = pinInput.value.trim()
                        if (pin.isBlank()) {
                            Toast.makeText(context, "请输入 PIN", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
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
                                Toast.makeText(context, "PIN 错误", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("解锁")
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
