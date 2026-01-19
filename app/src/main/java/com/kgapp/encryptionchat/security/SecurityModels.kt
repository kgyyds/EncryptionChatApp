package com.kgapp.encryptionchat.security

enum class AuthMode(val storageValue: Int) {
    SYSTEM(0),
    PIN(1);

    companion object {
        fun fromStorage(value: Int): AuthMode = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

enum class DuressAction(val storageValue: Int) {
    DECOY(0),
    HIDE(1),
    WIPE(2);

    companion object {
        fun fromStorage(value: Int): DuressAction = entries.firstOrNull { it.storageValue == value } ?: DECOY
    }
}

enum class SessionMode {
    NORMAL,
    DURESS
}

data class SecurityConfig(
    val duressEnabled: Boolean,
    val authMode: AuthMode,
    val duressAction: DuressAction,
    val normalPinHash: String?,
    val duressPinHash: String?
)
