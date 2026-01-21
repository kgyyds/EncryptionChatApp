package com.kgapp.encryptionchat.data.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class HybridPayload(
    val key: String,
    val iv: String,
    val tag: String,
    val msg: String
)

class HybridCrypto(private val crypto: CryptoManager) {
    private val secureRandom = SecureRandom()

    fun encryptOutgoingPlaintext(
        fromUid: String,
        toUid: String,
        ts: Long,
        peerPublicPemBase64: String,
        plaintext: String
    ): HybridPayload? {
        return try {
            val aesKey = ByteArray(KEY_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), spec)
            cipher.updateAAD(buildAad(fromUid, toUid, ts))
            val cipherWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            if (cipherWithTag.size <= TAG_LENGTH_BYTES) return null
            val cipherText = cipherWithTag.copyOfRange(0, cipherWithTag.size - TAG_LENGTH_BYTES)
            val tag = cipherWithTag.copyOfRange(cipherWithTag.size - TAG_LENGTH_BYTES, cipherWithTag.size)
            val encryptedKey = crypto.encryptBytesWithPublicPemBase64(peerPublicPemBase64, aesKey)
            if (encryptedKey.isBlank()) return null
            HybridPayload(
                key = encryptedKey,
                iv = encodeBase64(iv),
                tag = encodeBase64(tag),
                msg = encodeBase64(cipherText)
            )
        } catch (ex: Exception) {
            null
        }
    }

    fun decryptIncomingCipher(
        fromUid: String,
        toUid: String,
        ts: Long,
        keyBase64: String,
        ivBase64: String,
        tagBase64: String,
        msgBase64: String
    ): String? {
        val aesKey = crypto.decryptBytesFromBase64(keyBase64) ?: return null
        val iv = decodeBase64(ivBase64)
        val tag = decodeBase64(tagBase64)
        val cipherText = decodeBase64(msgBase64)
        if (iv.size != IV_LENGTH_BYTES || tag.size != TAG_LENGTH_BYTES) return null
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        return try {
            val combined = cipherText + tag
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), spec)
            cipher.updateAAD(buildAad(fromUid, toUid, ts))
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

    private fun buildAad(fromUid: String, toUid: String, ts: Long): ByteArray {
        return "v1|from=$fromUid|to=$toUid|ts=$ts".toByteArray(Charsets.UTF_8)
    }

    companion object {
        private const val KEY_LENGTH_BYTES = 32
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BYTES = 16
        private const val TAG_LENGTH_BITS = 128
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
