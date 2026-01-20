package com.kgapp.encryptionchat.security

import android.content.Context
import android.util.Base64
import com.kgapp.encryptionchat.data.storage.FileStorage
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecuritySettings {
    private const val PREFS_NAME = "security_settings"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_DURESS_ENABLED = "duress_enabled"
    private const val KEY_DURESS_ACTION = "duress_action"
    private const val KEY_NORMAL_PIN_HASH = "normal_pin_hash"
    private const val KEY_DURESS_PIN_HASH = "duress_pin_hash"
    private const val HASH_ITERATIONS = 12000
    private const val HASH_LENGTH = 256

    fun readConfig(context: Context): SecurityConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val appLockEnabled = if (prefs.contains(KEY_APP_LOCK_ENABLED)) {
            prefs.getBoolean(KEY_APP_LOCK_ENABLED, true)
        } else {
            prefs.getBoolean(KEY_DURESS_ENABLED, false)
        }
        return SecurityConfig(
            appLockEnabled = appLockEnabled,
            duressEnabled = prefs.getBoolean(KEY_DURESS_ENABLED, false),
            duressAction = DuressAction.fromStorage(prefs.getInt(KEY_DURESS_ACTION, DuressAction.DECOY.storageValue)),
            normalPinHash = prefs.getString(KEY_NORMAL_PIN_HASH, null),
            duressPinHash = prefs.getString(KEY_DURESS_PIN_HASH, null)
        )
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_LOCK_ENABLED, enabled)
            .apply()
    }

    fun setDuressEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DURESS_ENABLED, enabled)
            .apply()
    }

    fun setDuressAction(context: Context, duressAction: DuressAction) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DURESS_ACTION, duressAction.storageValue)
            .apply()
    }

    fun savePins(context: Context, normalPin: String?, duressPin: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (!normalPin.isNullOrBlank()) {
            editor.putString(KEY_NORMAL_PIN_HASH, hashPin(normalPin))
        }
        if (!duressPin.isNullOrBlank()) {
            editor.putString(KEY_DURESS_PIN_HASH, hashPin(duressPin))
        }
        editor.apply()
    }

    fun verifyNormalPin(config: SecurityConfig, pin: String): Boolean {
        val stored = config.normalPinHash ?: return false
        return verifyPin(pin, stored)
    }

    fun verifyDuressPin(config: SecurityConfig, pin: String): Boolean {
        val stored = config.duressPinHash ?: return false
        return verifyPin(pin, stored)
    }

    fun wipeSensitiveData(context: Context) {
        FileStorage(context).wipeSensitiveData()
    }

    private fun hashPin(pin: String): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = pbkdf2(pin, salt)
        return listOf(
            Base64.encodeToString(salt, Base64.NO_WRAP),
            HASH_ITERATIONS.toString(),
            Base64.encodeToString(hash, Base64.NO_WRAP)
        ).joinToString(":")
    }

    private fun verifyPin(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 3) return false
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val iterations = parts[1].toIntOrNull() ?: return false
        val expected = Base64.decode(parts[2], Base64.NO_WRAP)
        val actual = pbkdf2(pin, salt, iterations)
        return expected.contentEquals(actual)
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int = HASH_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
