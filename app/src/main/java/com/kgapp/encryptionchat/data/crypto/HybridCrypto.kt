package com.kgapp.encryptionchat.data.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class HybridPayload(
    val key: String,
    val msg: String
)

data class DecryptResult(
    val plaintext: String?,
    val keyBlobVersion: Int,
    val errorReason: String? = null,
    val throwable: Throwable? = null
)

class HybridCrypto(private val crypto: CryptoManager) {
    private val secureRandom = SecureRandom()
    val keyBlobVersion: Int
        get() = KEY_BLOB_VER

    @Suppress("UNUSED_PARAMETER")
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
            cipher.updateAAD(buildAadV2(fromUid, toUid))
            val cipherWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            if (cipherWithTag.size <= TAG_LENGTH_BYTES) return null
            val cipherText = cipherWithTag.copyOfRange(0, cipherWithTag.size - TAG_LENGTH_BYTES)
            val tag = cipherWithTag.copyOfRange(cipherWithTag.size - TAG_LENGTH_BYTES, cipherWithTag.size)
            val keyBlobJson = org.json.JSONObject().apply {
                put("alg", KEY_BLOB_ALG)
                put("ver", KEY_BLOB_VER)
                put("k", encodeBase64(aesKey))
                put("iv", encodeBase64(iv))
                put("tag", encodeBase64(tag))
            }.toString()
            val encryptedKey = crypto.encryptBytesWithPublicPemBase64(
                peerPublicPemBase64,
                keyBlobJson.toByteArray(Charsets.UTF_8)
            )
            if (encryptedKey.isBlank()) return null
            HybridPayload(
                key = encryptedKey,
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
        msgBase64: String
    ): DecryptResult {
        val keyBlobBytes = crypto.decryptBytesFromBase64(keyBase64)
            ?: return DecryptResult(null, -1, "key_blob_decrypt_failed")
        val keyBlob = try {
            org.json.JSONObject(String(keyBlobBytes, Charsets.UTF_8))
        } catch (ex: Exception) {
            return DecryptResult(null, -1, "key_blob_parse_failed", ex)
        }
        val alg = keyBlob.optString("alg", "")
        val ver = keyBlob.optInt("ver", -1)
        if (alg != KEY_BLOB_ALG) return DecryptResult(null, ver, "key_blob_alg_mismatch")
        if (ver < 1) return DecryptResult(null, ver, "key_blob_version_invalid")
        val aesKey = try {
            decodeBase64(keyBlob.optString("k", ""))
        } catch (ex: Exception) {
            return DecryptResult(null, ver, "key_blob_key_decode_failed", ex)
        }
        val iv = try {
            decodeBase64(keyBlob.optString("iv", ""))
        } catch (ex: Exception) {
            return DecryptResult(null, ver, "key_blob_iv_decode_failed", ex)
        }
        val tag = try {
            decodeBase64(keyBlob.optString("tag", ""))
        } catch (ex: Exception) {
            return DecryptResult(null, ver, "key_blob_tag_decode_failed", ex)
        }
        val cipherText = try {
            decodeBase64(msgBase64)
        } catch (ex: Exception) {
            return DecryptResult(null, ver, "ciphertext_decode_failed", ex)
        }
        if (aesKey.size != KEY_LENGTH_BYTES) return DecryptResult(null, ver, "key_blob_key_size_invalid")
        if (iv.size != IV_LENGTH_BYTES && iv.size != ALT_IV_LENGTH_BYTES) {
            return DecryptResult(null, ver, "key_blob_iv_size_invalid")
        }
        if (tag.size != TAG_LENGTH_BYTES) return DecryptResult(null, ver, "key_blob_tag_size_invalid")
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        return try {
            val combined = cipherText + tag
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), spec)
            val aad = if (ver == 1) {
                buildAadV1(fromUid, toUid, ts)
            } else {
                buildAadV2(fromUid, toUid)
            }
            cipher.updateAAD(aad)
            val plainBytes = cipher.doFinal(combined)
            DecryptResult(String(plainBytes, Charsets.UTF_8), ver)
        } catch (ex: AEADBadTagException) {
            DecryptResult(null, ver, "aead_bad_tag", ex)
        } catch (ex: Exception) {
            DecryptResult(null, ver, "decrypt_exception", ex)
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decodeBase64(value: String): ByteArray {
        return Base64.decode(value, Base64.NO_WRAP)
    }

    private fun buildAadV1(fromUid: String, toUid: String, ts: Long): ByteArray {
        return "v1|from=$fromUid|to=$toUid|ts=$ts".toByteArray(Charsets.UTF_8)
    }

    private fun buildAadV2(fromUid: String, toUid: String): ByteArray {
        return "TV-AAD|v2|from=$fromUid|to=$toUid".toByteArray(Charsets.UTF_8)
    }

    companion object {
        private const val KEY_LENGTH_BYTES = 32
        private const val IV_LENGTH_BYTES = 12
        private const val ALT_IV_LENGTH_BYTES = 16
        private const val TAG_LENGTH_BYTES = 16
        private const val TAG_LENGTH_BITS = 128
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BLOB_ALG = "AES-256-GCM"
        const val KEY_BLOB_VER = 2
    }
}
