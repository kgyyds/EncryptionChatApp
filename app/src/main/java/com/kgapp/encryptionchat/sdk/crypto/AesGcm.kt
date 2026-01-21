package com.kgapp.encryptionchat.sdk.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AesGcmPayload(
    val keyBase64: String,
    val ivBase64: String,
    val tagBase64: String,
    val cipherTextBase64: String
)

class AesGcm(private val secureRandom: SecureRandom = SecureRandom()) {
    fun encrypt(plaintext: String, aad: ByteArray? = null): AesGcmPayload? {
        return try {
            val key = ByteArray(KEY_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            if (aad != null) {
                cipher.updateAAD(aad)
            }
            val cipherWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            if (cipherWithTag.size <= TAG_LENGTH_BYTES) return null
            val cipherText = cipherWithTag.copyOfRange(0, cipherWithTag.size - TAG_LENGTH_BYTES)
            val tag = cipherWithTag.copyOfRange(cipherWithTag.size - TAG_LENGTH_BYTES, cipherWithTag.size)
            AesGcmPayload(
                keyBase64 = encodeBase64(key),
                ivBase64 = encodeBase64(iv),
                tagBase64 = encodeBase64(tag),
                cipherTextBase64 = encodeBase64(cipherText)
            )
        } catch (ex: Exception) {
            null
        }
    }

    fun decrypt(
        keyBase64: String,
        ivBase64: String,
        tagBase64: String,
        cipherTextBase64: String,
        aad: ByteArray? = null
    ): String? {
        val key = decodeBase64(keyBase64)
        val iv = decodeBase64(ivBase64)
        val tag = decodeBase64(tagBase64)
        val cipherText = decodeBase64(cipherTextBase64)
        if (key.size != KEY_LENGTH_BYTES) return null
        if (iv.size != IV_LENGTH_BYTES) return null
        if (tag.size != TAG_LENGTH_BYTES) return null
        return try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            if (aad != null) {
                cipher.updateAAD(aad)
            }
            val combined = cipherText + tag
            val plainBytes = cipher.doFinal(combined)
            String(plainBytes, Charsets.UTF_8)
        } catch (ex: AEADBadTagException) {
            null
        } catch (ex: Exception) {
            null
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decodeBase64(value: String): ByteArray {
        return Base64.decode(value, Base64.NO_WRAP)
    }

    companion object {
        private const val KEY_LENGTH_BYTES = 32
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BYTES = 16
        private const val TAG_LENGTH_BITS = 128
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
