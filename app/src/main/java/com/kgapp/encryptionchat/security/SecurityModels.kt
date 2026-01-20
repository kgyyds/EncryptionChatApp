package com.kgapp.encryptionchat.security

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
    val appLockEnabled: Boolean,
    val duressEnabled: Boolean,
    val duressAction: DuressAction,
    val normalPinHash: String?,
    val duressPinHash: String?
)
