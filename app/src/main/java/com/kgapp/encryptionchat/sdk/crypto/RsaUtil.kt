package com.kgapp.encryptionchat.sdk.crypto

import com.kgapp.encryptionchat.data.crypto.CryptoManager

class RsaUtil(private val crypto: CryptoManager) {
    fun sign(canonicalData: String): String {
        return crypto.signDataJson(canonicalData)
    }

    fun encryptForPeer(pubPemBase64: String, plainBytes: ByteArray): String {
        return crypto.encryptBytesWithPublicPemBase64(pubPemBase64, plainBytes)
    }

    fun decryptFromBase64(ciphertextBase64: String): ByteArray? {
        return crypto.decryptBytesFromBase64(ciphertextBase64)
    }
}
