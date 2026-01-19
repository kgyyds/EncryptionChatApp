package com.kgapp.encryptionchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgapp.encryptionchat.data.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    data class SettingsState(
        val hasPrivateKey: Boolean = false,
        val hasPublicKey: Boolean = false,
        val fingerprint: String = "",
        val publicPemPreview: String = "",
        val isGenerating: Boolean = false
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasPrivate = repository.hasPrivateKey()
            val hasPublic = repository.hasPublicKey()
            val fingerprint = repository.getSelfName()?.take(12).orEmpty()
            val preview = repository.getPublicPemText()?.lineSequence()?.take(3)?.joinToString("\n").orEmpty()
            _state.value = SettingsState(hasPrivate, hasPublic, fingerprint, preview, _state.value.isGenerating)
        }
    }

    fun generateKeyPair(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGenerating = true)
            val success = repository.generateKeyPair()
            refresh()
            _state.value = _state.value.copy(isGenerating = false)
            onComplete(success)
        }
    }

    fun importPrivateKey(pemText: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importPrivatePem(pemText)
            refresh()
            onComplete(success)
        }
    }

    fun importPublicKey(pemText: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importPublicPem(pemText)
            refresh()
            onComplete(success)
        }
    }

    suspend fun exportPublicKey(): String {
        return repository.getPublicPemText().orEmpty()
    }
}
