package com.kgapp.encryptionchat.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionState {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _sessionMode = MutableStateFlow(SessionMode.NORMAL)
    val sessionMode: StateFlow<SessionMode> = _sessionMode.asStateFlow()

    private val _duressAction = MutableStateFlow(DuressAction.DECOY)
    val duressAction: StateFlow<DuressAction> = _duressAction.asStateFlow()

    fun lock() {
        _unlocked.value = false
        _sessionMode.value = SessionMode.NORMAL
        _duressAction.value = DuressAction.DECOY
    }

    fun unlockNormal() {
        _unlocked.value = true
        _sessionMode.value = SessionMode.NORMAL
        _duressAction.value = DuressAction.DECOY
    }

    fun unlockDuress(action: DuressAction) {
        _unlocked.value = true
        _sessionMode.value = SessionMode.DURESS
        _duressAction.value = action
    }
}
