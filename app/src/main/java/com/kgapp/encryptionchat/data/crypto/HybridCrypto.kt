package com.kgapp.encryptionchat.data.crypto

import com.kgapp.encryptionchat.sdk.crypto.AesGcm
import com.kgapp.encryptionchat.sdk.crypto.AesGcmPayload
import com.kgapp.encryptionchat.sdk.crypto.RsaUtil

data class HybridPayload(
    val key: String,
    val msg: String
)

class HybridCrypto(private val crypto: CryptoManager) {
    private val aesGcm = AesGcm()
    private val rsaUtil = RsaUtil(crypto)

    fun encryptOutgoingPlaintext(
        fromUid: String,
        toUid: String,
        ts: Long,
        peerPublicPemBase64: String,
        plaintext: String
    ): HybridPayload? {
        return try {
            val payload = aesGcm.encrypt(plaintext, buildAad(fromUid, toUid, ts)) ?: return null
            val keyBlobJson = org.json.JSONObject().apply {
                put("k", payload.keyBase64)
                put("iv", payload.ivBase64)
                put("tag", payload.tagBase64)
            }.toString()
            val encryptedKey = rsaUtil.encryptForPeer(
                peerPublicPemBase64,
                keyBlobJson.toByteArray(Charsets.UTF_8)
            )
            if (encryptedKey.isBlank()) return null
            HybridPayload(
                key = encryptedKey,
                msg = payload.cipherTextBase64
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
    ): String? {
        val keyBlobBytes = rsaUtil.decryptFromBase64(keyBase64) ?: return null
        val keyBlob = try {
            org.json.JSONObject(String(keyBlobBytes, Charsets.UTF_8))
        } catch (ex: Exception) {
            return null
        }
        val aesPayload = AesGcmPayload(
            keyBase64 = keyBlob.optString("k", ""),
            ivBase64 = keyBlob.optString("iv", ""),
            tagBase64 = keyBlob.optString("tag", ""),
            cipherTextBase64 = msgBase64
        )
        if (aesPayload.keyBase64.isBlank() || aesPayload.ivBase64.isBlank() || aesPayload.tagBase64.isBlank()) {
            return null
        }
        return aesGcm.decrypt(
            keyBase64 = aesPayload.keyBase64,
            ivBase64 = aesPayload.ivBase64,
            tagBase64 = aesPayload.tagBase64,
            cipherTextBase64 = aesPayload.cipherTextBase64,
            aad = buildAad(fromUid, toUid, ts)
        )
    }

    private fun buildAad(fromUid: String, toUid: String, ts: Long): ByteArray {
        return "$AAD_PREFIX|from=$fromUid|to=$toUid|ts=$ts".toByteArray(Charsets.UTF_8)
    }

    companion object {
        private const val AAD_PREFIX = "v2"
    }
}
